package main

// main.go —— agent 主循环：把对账、渲染、构建、校验、切换、reload 编排成一个安全的循环。
//
// 每个轮询周期（默认 15s）做的事，按顺序：
//   1. 日志轮转 + 磁盘水位检查（与配置同步无关的例行维护）
//   2. 并行对账每家公司（拉 manifest / 护栏 / 下载校验证书）——互不影响
//   3. 跨公司冲突检查（同一域名只能属于一家公司，后到者拒绝）
//   4. 把「期望状态」（state ∪ 本轮新结果）整体渲染 → 构建候选 release → 算内容哈希
//   5. 哈希与当前一致 → 零动作收敛（重启/状态丢失自愈的关键路径）
//   6. 有变化 → 防抖检查 → 影子 nginx -t → 原子切 active → SIGHUP reload → 落 state
//
// 任何一步失败都遵守「最后已知良好」：active 不动、nginx 不动，错误进下一轮回报。

import (
	"fmt"
	"os"
	"os/signal"
	"path/filepath"
	"sync"
	"syscall"
	"time"
)

func main() {
	initLogLevel()
	cfg, err := LoadConfig()
	if err != nil {
		logError("配置加载失败: %v", err)
		os.Exit(1)
	}
	logInfo("v7front-agent 启动: server=%s, 公司数=%d, 轮询=%s",
		cfg.ServerName, len(cfg.Companies), cfg.PollInterval)

	// 基础目录与占位证书（nginx 的变量证书路径在握手期才读文件，
	// 所以 nginx 可以先于 agent 启动，这里随后补上即可）
	for _, dir := range []string{"releases", "logs"} {
		if err := os.MkdirAll(filepath.Join(cfg.DataDir, dir), 0o755); err != nil {
			logError("创建目录失败: %v", err)
			os.Exit(1)
		}
	}
	if err := ensurePlaceholderCert(cfg.DataDir); err != nil {
		logError("生成占位证书失败: %v", err)
		os.Exit(1)
	}

	// 每家公司一个带 token/TLS 配置的 HTTP 客户端
	clients := make([]*companyClient, 0, len(cfg.Companies))
	for _, cc := range cfg.Companies {
		client, err := newCompanyClient(cc, cfg)
		if err != nil {
			logError("初始化公司客户端失败: %v", err)
			os.Exit(1)
		}
		clients = append(clients, client)
	}

	state := LoadState(cfg.DataDir)
	nc := NewNginxController()

	// 优雅退出：收到 SIGTERM/SIGINT 时结束循环（compose stop 默认发 SIGTERM）
	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGTERM, syscall.SIGINT)

	ticker := time.NewTicker(cfg.PollInterval)
	defer ticker.Stop()
	for {
		runCycle(cfg, state, clients, nc)
		select {
		case <-stop:
			logInfo("收到退出信号，agent 结束（nginx 不受影响）")
			return
		case <-ticker.C:
		}
	}
}

// runCycle 执行一个完整轮询周期。所有错误都被吞掉转化为状态/日志——主循环永不崩溃。
func runCycle(cfg *Config, state *State, clients []*companyClient, nc NginxController) {
	// 兜底恢复：哪怕有未预期 panic 也只损失一个周期，不杀死 agent
	defer func() {
		if r := recover(); r != nil {
			logError("轮询周期发生未预期 panic（已恢复，下轮重试）: %v", r)
		}
	}()

	// ── 1. 例行维护 ─────────────────────────────────────────────
	rotateLogsIfNeeded(cfg, nc)
	diskErr := checkDiskWatermark(cfg.DataDir)
	if diskErr != nil {
		logError("%v", diskErr)
	}

	activeDir := activeTarget(cfg.DataDir)

	// ── 2. 并行对账每家公司 ─────────────────────────────────────
	// 先串行初始化每家公司的状态条目：state.Company 对 map 有惰性写入，
	// 必须在进入并行段之前完成，否则多 goroutine 并发写 map 是数据竞争
	for _, client := range clients {
		state.Company(client.cfg.Name)
	}
	results := make([]*CompanyResult, len(clients))
	var wg sync.WaitGroup
	for i, client := range clients {
		wg.Add(1)
		go func(i int, client *companyClient) {
			defer wg.Done()
			activeCompanyDir := ""
			if activeDir != "" {
				activeCompanyDir = filepath.Join(activeDir, client.cfg.Name)
			}
			results[i] = reconcileCompany(cfg, client, state.Company(client.cfg.Name), activeCompanyDir)
		}(i, client)
	}
	wg.Wait()

	// ── 3. 组装期望状态（state ∪ 新结果）+ 跨公司冲突检查 ─────────
	desiredAll := make(map[string]*desiredState, len(clients))
	claimed := map[string]string{} // 域名 → 公司（冲突检测）

	for i, client := range clients {
		name := client.cfg.Name
		prev := state.Company(name)
		res := results[i]
		d := &desiredState{services: prev.Services, domains: prev.Domains}

		if res.Err != nil {
			// 失败：保持最后已知良好，错误进回报
			prev.LastStatus, prev.LastError = "error", res.Err.Error()
			logError("公司 %s 本轮失败: %v", name, res.Err)
		} else if res.Changed {
			// 跨公司冲突：检查新清单是否抢占了别家（已登记）的域名
			conflict := ""
			for domain := range res.Domains {
				if owner, ok := claimed[domain]; ok && owner != name {
					conflict = fmt.Sprintf("域名 %s 已属于公司 %s，拒绝本次更新（防配置错误劫持他家域名）", domain, owner)
					break
				}
			}
			if conflict != "" {
				prev.LastStatus, prev.LastError = "error", conflict
				logError("公司 %s: %s", name, conflict)
			} else {
				d.services, d.domains, d.newCerts, d.changed = res.Services, res.Domains, res.Certs, true
			}
		} else {
			// 304：沿用现状，状态恢复 ok
			prev.LastStatus, prev.LastError = "ok", ""
		}

		for domain := range d.domains {
			claimed[domain] = name
		}
		desiredAll[name] = d
	}

	// ── 4. 渲染并构建候选 release ───────────────────────────────
	// 注意：即使本轮没有公司变化，也要构建一次做哈希比对——
	// 这是「调优变化（.env 改动）触发 reload」和「状态丢失自愈」的统一入口。
	cpu, mem, disk := detectResources(cfg)
	tuning := computeTuning(cfg, cpu, mem, disk)
	input := releaseInput{SystemFiles: renderSystemFiles(cfg, tuning)}
	for _, client := range clients {
		name := client.cfg.Name
		d := desiredAll[name]
		input.Companies = append(input.Companies, releaseCompany{
			Name:     name,
			Files:    renderCompanyFiles(cfg.DataDir, name, d.services, d.domains),
			Domains:  d.domains,
			NewCerts: d.newCerts,
		})
	}

	if diskErr != nil {
		// 磁盘水位冻结：不写任何新东西；把水位错误附加到所有公司的回报里
		for _, client := range clients {
			prev := state.Company(client.cfg.Name)
			prev.LastStatus, prev.LastError = "error", diskErr.Error()
		}
		saveState(cfg, state)
		return
	}

	building, hash, err := buildRelease(cfg.DataDir, input, activeDir)
	if err != nil {
		logError("构建候选 release 失败: %v", err)
		saveState(cfg, state)
		return
	}
	// 确保任何提前返回都清掉构建残留（成功路径会先 rename 走，RemoveAll 落空无害）
	defer os.RemoveAll(building)

	// ── 5. 内容寻址幂等收敛 ─────────────────────────────────────
	baseline := state.ActiveContentHash
	if baseline == "" {
		// state.json 丢失/首次运行：以 active 里记录的哈希为基准（自愈路径）
		baseline = readActiveContentHash(cfg.DataDir)
	}
	if hash == baseline {
		// 内容一致：零动作收敛，只把本轮成功公司的版本号记下来（下轮 304）
		adoptResults(state, clients, desiredAll, results)
		state.ActiveContentHash = hash
		saveState(cfg, state)
		logDebug("内容哈希一致（%s），零动作收敛", hash[:8])
		return
	}

	// ── 6. 防抖 → 影子校验 → 切换 → reload ──────────────────────
	if !state.LastReloadAt.IsZero() && time.Since(state.LastReloadAt) < cfg.ReloadMinInterval {
		// 防抖窗口内：本轮放弃应用（状态不动），下轮重新拉取合并后再应用
		logInfo("距上次 reload 不足 %s，本轮变更推迟合并应用", cfg.ReloadMinInterval)
		saveState(cfg, state)
		return
	}

	shadowConf := filepath.Join(os.TempDir(), "v7front-shadow-nginx.conf")
	if err := writeShadowConf(cfg.NginxConf, cfg.DataDir, building, shadowConf); err != nil {
		logError("生成影子配置失败: %v", err)
		saveState(cfg, state)
		return
	}
	if err := nc.TestConfig(shadowConf); err != nil {
		// 校验失败：错误归因到本轮引入变化的公司（其余公司不受影响）
		logError("候选 release 未通过 nginx -t，保持现状: %v", err)
		msg := truncateString("nginx -t 校验失败: "+err.Error(), 900)
		for _, client := range clients {
			if d := desiredAll[client.cfg.Name]; d.changed {
				prev := state.Company(client.cfg.Name)
				prev.LastStatus, prev.LastError = "error", msg
			}
		}
		saveState(cfg, state)
		return
	}

	release, err := finalizeRelease(building, hash)
	if err != nil {
		logError("落定 release 失败: %v", err)
		saveState(cfg, state)
		return
	}
	if err := switchActive(cfg.DataDir, release); err != nil {
		logError("切换 active 失败: %v", err)
		saveState(cfg, state)
		return
	}
	if err := nc.Reload(); err != nil {
		// 发信号失败 = 共享 pid 命名空间失效（nginx 容器被重建）。
		// 按设计自杀退出，restart 策略会拉起新 agent 挂接新命名空间；
		// 故意不更新 state.ActiveContentHash —— 重启后的第一轮会重建并补上这次 reload。
		logError("reload 失败，agent 退出以重新挂接 nginx 进程命名空间: %v", err)
		os.Exit(1)
	}

	// ── 7. 成功收尾：落状态、清旧 release ────────────────────────
	adoptResults(state, clients, desiredAll, results)
	state.ActiveContentHash = hash
	state.LastReloadAt = time.Now()
	saveState(cfg, state)
	pruneReleases(cfg.DataDir, cfg.ReleaseKeep)
	logInfo("已应用 release %s（哈希 %s）并完成 reload", filepath.Base(release), hash[:8])
}

// desiredState 是一家公司本轮的「期望状态」：要么沿用 state（304/失败），要么采用新结果。
type desiredState struct {
	services map[string][]string
	domains  map[string]DomainState
	newCerts map[string]CertPayload
	changed  bool
}

// adoptResults 把本轮成功应用的公司结果写进 state（版本/域名/服务），状态置 ok。
// 失败公司的状态在前面已经写过 error，这里不碰。
func adoptResults(state *State, clients []*companyClient, desiredAll map[string]*desiredState, results []*CompanyResult) {
	for i, client := range clients {
		name := client.cfg.Name
		d := desiredAll[name]
		if d == nil || !d.changed {
			continue
		}
		prev := state.Company(name)
		prev.AppliedVersion = results[i].Version
		prev.Services = d.services
		prev.Domains = d.domains
		prev.LastStatus, prev.LastError = "ok", ""
		prev.UpdatedAt = time.Now()
		logInfo("公司 %s 已应用版本 %s（域名 %d 个）", name, prev.AppliedVersion, len(prev.Domains))
	}
}

func saveState(cfg *Config, state *State) {
	if err := state.Save(cfg.DataDir); err != nil {
		logError("保存 state.json 失败: %v", err)
	}
}
