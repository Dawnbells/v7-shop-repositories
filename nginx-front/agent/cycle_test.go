package main

// cycle_test.go —— runCycle 端到端测试：模拟两家公司 API + 假 nginx + 临时数据卷，
// 走完「对账 → 渲染 → 构建 → 影子校验 → 切换 → reload → 落状态」的完整链路。
//
// 注意：active 切换依赖符号链接。Windows 上创建符号链接需要开发者模式/管理员权限，
// 探测不可用时跳过本组测试（CI/容器内的 Linux 环境总是可用）。

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

// requireSymlink 探测当前环境能否创建符号链接，不能则跳过测试。
func requireSymlink(t *testing.T) {
	t.Helper()
	dir := t.TempDir()
	target := filepath.Join(dir, "t")
	if err := os.WriteFile(target, []byte("x"), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(target, filepath.Join(dir, "l")); err != nil {
		t.Skipf("当前环境无法创建符号链接（Windows 需开发者模式），跳过: %v", err)
	}
}

// newCycleEnv 准备一次 runCycle 所需的全部环境。
func newCycleEnv(t *testing.T) (*Config, *State, *fakeNginx) {
	t.Helper()
	dataDir := t.TempDir()
	for _, d := range []string{"releases", "logs"} {
		if err := os.MkdirAll(filepath.Join(dataDir, d), 0o755); err != nil {
			t.Fatal(err)
		}
	}
	cfg := testConfig(t, dataDir)
	// 影子校验需要主配置文件：内容里引用 active 路径即可
	nginxConf := filepath.Join(dataDir, "nginx.conf")
	content := "include " + filepath.ToSlash(filepath.Join(dataDir, "active")) + "/_system/main.conf;\n"
	if err := os.WriteFile(nginxConf, []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
	cfg.NginxConf = nginxConf
	// 测试机资源探测无意义，固定配额保证调优渲染确定性
	cfg.CPULimit = 2
	cfg.MemLimitBytes = 4 * 1024 * 1024 * 1024
	cfg.DiskLimitBytes = 80 * 1024 * 1024 * 1024
	return cfg, LoadState(dataDir), &fakeNginx{}
}

func TestRunCycleAppliesAndConverges(t *testing.T) {
	requireSymlink(t)

	srv := newFakeCompanyServer(t, "tok")
	srv.manifest.Services = map[string][]string{"NUXT_MALL": {"10.0.0.5:3000"}}
	srv.setDomain("a.com", "NUXT_MALL")

	cfg, state, nc := newCycleEnv(t)
	clients := []*companyClient{srv.companyClientFor(t, "xyz", cfg)}

	// 第一轮：应当完成全量同步并 reload
	runCycle(cfg, state, clients, nc)
	if nc.reloads != 1 {
		t.Fatalf("首轮应 reload 一次, got %d", nc.reloads)
	}
	if len(nc.tested) != 1 {
		t.Fatalf("切换前必须经过影子 nginx -t")
	}
	cs := state.Company("xyz")
	if cs.AppliedVersion != srv.manifest.Version || cs.LastStatus != "ok" {
		t.Fatalf("状态未正确落库: %+v", cs)
	}
	// active 指向的 release 里应有渲染产物与证书
	active := activeTarget(cfg.DataDir)
	if active == "" {
		t.Fatalf("active 软链未建立")
	}
	for _, f := range []string{
		filepath.Join(active, "_system", "main.conf"),
		filepath.Join(active, "xyz", "routes.map"),
		filepath.Join(active, "xyz", "certs", "a.com", "fullchain.pem"),
	} {
		if _, err := os.Stat(f); err != nil {
			t.Fatalf("release 缺少产物 %s: %v", f, err)
		}
	}

	// 第二轮：数据未变 → 304 → 内容哈希一致 → 零动作收敛（不再 reload）
	runCycle(cfg, state, clients, nc)
	if nc.reloads != 1 {
		t.Fatalf("无变化时不应再 reload, got %d", nc.reloads)
	}

	// 第三轮：state.json 丢失 → 全量重拉 → 哈希与 active 一致 → 仍零 reload（自愈收敛）
	freshState := &State{Companies: map[string]*CompanyState{}}
	runCycle(cfg, freshState, clients, nc)
	if nc.reloads != 1 {
		t.Fatalf("状态丢失自愈不应触发多余 reload, got %d", nc.reloads)
	}
	if freshState.Company("xyz").AppliedVersion != srv.manifest.Version {
		t.Fatalf("自愈后应恢复 appliedVersion")
	}
}

func TestRunCycleCrossCompanyConflict(t *testing.T) {
	requireSymlink(t)

	srvA := newFakeCompanyServer(t, "tokA")
	srvA.manifest.Services = map[string][]string{"NUXT_MALL": {"10.0.0.5:3000"}}
	srvA.setDomain("shared.com", "NUXT_MALL")

	srvB := newFakeCompanyServer(t, "tokB")
	srvB.manifest.Services = map[string][]string{"NUXT_MALL": {"10.0.0.6:3000"}}
	srvB.setDomain("shared.com", "NUXT_MALL") // B 也声称拥有同一域名

	cfg, state, nc := newCycleEnv(t)
	clients := []*companyClient{
		srvA.companyClientFor(t, "aaa", cfg),
		srvB.companyClientFor(t, "bbb", cfg),
	}

	runCycle(cfg, state, clients, nc)

	if state.Company("aaa").LastStatus != "ok" {
		t.Fatalf("先到的公司应正常应用: %+v", state.Company("aaa"))
	}
	bbb := state.Company("bbb")
	if bbb.LastStatus != "error" || !strings.Contains(bbb.LastError, "已被其他公司占用") {
		t.Fatalf("后到的公司应被冲突拒绝: %+v", bbb)
	}
	// 冲突公司的域名不应出现在任何渲染产物里
	routes, _ := os.ReadFile(filepath.Join(activeTarget(cfg.DataDir), "bbb", "routes.map"))
	if strings.Contains(string(routes), "shared.com") {
		t.Fatalf("被拒公司的 routes.map 不应包含冲突域名")
	}
}

// 评审 bug_007 的实际场景：B 此前已合法应用 shared.com（state 里有），
// A 随后才声明同域。旧实现里 B 的 d.domains 仍 = prev.Domains，会把 shared.com
// 照样渲染进 bbb/routes.map，与 aaa 的产生 nginx 重复 key。本用例锁死修复后的行为。
func TestRunCycleCrossCompanyConflictCarryForward(t *testing.T) {
	requireSymlink(t)

	// B 先独占 shared.com 并应用
	srvB := newFakeCompanyServer(t, "tokB")
	srvB.manifest.Services = map[string][]string{"NUXT_MALL": {"10.0.0.6:3000"}}
	srvB.setDomain("shared.com", "NUXT_MALL")

	srvA := newFakeCompanyServer(t, "tokA")
	srvA.manifest.Services = map[string][]string{"NUXT_MALL": {"10.0.0.5:3000"}}
	// A 初始无任何域名

	cfg, state, nc := newCycleEnv(t)
	clients := []*companyClient{
		srvA.companyClientFor(t, "aaa", cfg),
		srvB.companyClientFor(t, "bbb", cfg),
	}

	// 第一轮：A 空、B 拿下 shared.com → B 的 state 持久化含 shared.com
	runCycle(cfg, state, clients, nc)
	if _, ok := state.Company("bbb").Domains["shared.com"]; !ok {
		t.Fatalf("前置条件失败：B 应先合法持有 shared.com")
	}

	// 第二轮：A 现在也声明 shared.com（B 此时对 shared.com 处于 304/carry-forward）
	srvA.setDomain("shared.com", "NUXT_MALL")
	runCycle(cfg, state, clients, nc)

	// A 先到（clients[0]）→ 占有 shared.com
	aaaRoutes, _ := os.ReadFile(filepath.Join(activeTarget(cfg.DataDir), "aaa", "routes.map"))
	if !strings.Contains(string(aaaRoutes), "shared.com") {
		t.Fatalf("先到的 A 应渲染 shared.com:\n%s", aaaRoutes)
	}
	// B 携带的 shared.com 必须被剔除，绝不能同时出现在 bbb/routes.map（否则 nginx 重复 key）
	bbbRoutes, _ := os.ReadFile(filepath.Join(activeTarget(cfg.DataDir), "bbb", "routes.map"))
	if strings.Contains(string(bbbRoutes), "shared.com") {
		t.Fatalf("carry-forward 的冲突域名必须从 B 剔除，实测仍在:\n%s", bbbRoutes)
	}
	if state.Company("bbb").LastStatus != "error" {
		t.Fatalf("被剔除冲突域名的 B 应回报 error: %+v", state.Company("bbb"))
	}
}

func TestRunCycleNginxTestFailureKeepsActive(t *testing.T) {
	requireSymlink(t)

	srv := newFakeCompanyServer(t, "tok")
	srv.manifest.Services = map[string][]string{"NUXT_MALL": {"10.0.0.5:3000"}}
	srv.setDomain("a.com", "NUXT_MALL")

	cfg, state, nc := newCycleEnv(t)
	clients := []*companyClient{srv.companyClientFor(t, "xyz", cfg)}

	// 先正常应用一轮
	runCycle(cfg, state, clients, nc)
	firstActive := activeTarget(cfg.DataDir)

	// 公司加新域名，但让 nginx -t 失败 → active 必须保持不动，错误归因到该公司
	srv.setDomain("b.com", "NUXT_MALL")
	nc.testErr = os.ErrInvalid
	runCycle(cfg, state, clients, nc)

	if activeTarget(cfg.DataDir) != firstActive {
		t.Fatalf("nginx -t 失败时 active 不允许切换")
	}
	if nc.reloads != 1 {
		t.Fatalf("nginx -t 失败不应 reload")
	}
	cs := state.Company("xyz")
	if cs.LastStatus != "error" || !strings.Contains(cs.LastError, "nginx -t") {
		t.Fatalf("校验失败应归因到引入变化的公司: %+v", cs)
	}

	// 修复后下一轮自动应用
	nc.testErr = nil
	runCycle(cfg, state, clients, nc)
	if nc.reloads != 2 || state.Company("xyz").LastStatus != "ok" {
		t.Fatalf("故障恢复后应自动应用: reloads=%d state=%+v", nc.reloads, state.Company("xyz"))
	}
}

func TestRunCycleReloadDebounce(t *testing.T) {
	requireSymlink(t)

	srv := newFakeCompanyServer(t, "tok")
	srv.manifest.Services = map[string][]string{"NUXT_MALL": {"10.0.0.5:3000"}}
	srv.setDomain("a.com", "NUXT_MALL")

	cfg, state, nc := newCycleEnv(t)
	cfg.ReloadMinInterval = time.Hour // 大防抖窗口
	clients := []*companyClient{srv.companyClientFor(t, "xyz", cfg)}

	runCycle(cfg, state, clients, nc) // 首轮（LastReloadAt 为零值，不受防抖约束）
	if nc.reloads != 1 {
		t.Fatalf("首轮应正常 reload")
	}

	srv.setDomain("b.com", "NUXT_MALL") // 防抖窗口内的新变更
	runCycle(cfg, state, clients, nc)
	if nc.reloads != 1 {
		t.Fatalf("防抖窗口内不应 reload, got %d", nc.reloads)
	}
	if state.Company("xyz").AppliedVersion == srv.manifest.Version {
		t.Fatalf("防抖推迟的变更不应提前记账（下轮要重新拉取应用）")
	}
}
