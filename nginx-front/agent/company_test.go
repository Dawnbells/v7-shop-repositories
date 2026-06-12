package main

// company_test.go —— 对账逻辑测试：覆盖设计文档 §8 要求的全部场景。

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestReconcileInitialSyncDownloadsAndVerifies(t *testing.T) {
	srv := newFakeCompanyServer(t, "tok")
	srv.manifest.Services = map[string][]string{"NUXT_MALL": {"10.0.0.5:3000"}}
	srv.setDomain("a.com", "NUXT_MALL")
	srv.setDomain("b.com", "NUXT_MALL")

	cfg := testConfig(t, t.TempDir())
	client := srv.companyClientFor(t, "xyz", cfg)
	prev := &CompanyState{Domains: map[string]DomainState{}, Services: map[string][]string{}}

	res := reconcileCompany(cfg, client, prev, "")
	if res.Err != nil {
		t.Fatalf("首次同步不应失败: %v", res.Err)
	}
	if !res.Changed || len(res.Domains) != 2 || len(res.Certs) != 2 {
		t.Fatalf("首次同步应下载全部证书: changed=%v domains=%d certs=%d", res.Changed, len(res.Domains), len(res.Certs))
	}
	if srv.lastAgent != "test-front" {
		t.Fatalf("轮询应携带 agent 标识, got %q", srv.lastAgent)
	}
}

func TestReconcile304KeepsState(t *testing.T) {
	srv := newFakeCompanyServer(t, "tok")
	srv.manifest.Services = map[string][]string{"NUXT_MALL": {"10.0.0.5:3000"}}
	srv.setDomain("a.com", "NUXT_MALL")

	cfg := testConfig(t, t.TempDir())
	client := srv.companyClientFor(t, "xyz", cfg)
	prev := &CompanyState{AppliedVersion: srv.manifest.Version, LastStatus: "ok",
		Domains: map[string]DomainState{}, Services: map[string][]string{}}

	res := reconcileCompany(cfg, client, prev, "")
	if res.Err != nil || res.Changed {
		t.Fatalf("版本一致应 304 且无变化: err=%v changed=%v", res.Err, res.Changed)
	}
	if srv.lastApplied != srv.manifest.Version {
		t.Fatalf("轮询应回传 appliedVersion")
	}
}

func TestReconcileRequestFailureKeepsLastKnownGood(t *testing.T) {
	srv := newFakeCompanyServer(t, "tok")
	cfg := testConfig(t, t.TempDir())
	client := srv.companyClientFor(t, "xyz", cfg)
	srv.srv.Close() // 模拟公司 Java 宕机

	res := reconcileCompany(cfg, client, &CompanyState{}, "")
	if res.Err == nil || res.Changed {
		t.Fatalf("请求失败必须报错且不改变状态")
	}
}

func TestReconcileRejectsTamperedCert(t *testing.T) {
	srv := newFakeCompanyServer(t, "tok")
	srv.manifest.Services = map[string][]string{"NUXT_MALL": {"10.0.0.5:3000"}}
	srv.setDomain("a.com", "NUXT_MALL")
	// 篡改服务器上的证书内容（指纹仍是旧的）→ 下载校验必须拒绝
	pair := srv.certs["a.com"]
	pair[0] = append([]byte("tampered\n"), pair[0]...)
	srv.certs["a.com"] = pair

	cfg := testConfig(t, t.TempDir())
	client := srv.companyClientFor(t, "xyz", cfg)
	res := reconcileCompany(cfg, client, &CompanyState{Domains: map[string]DomainState{}}, "")
	if res.Err == nil || !strings.Contains(res.Err.Error(), "指纹") {
		t.Fatalf("内容与指纹不一致必须整单拒绝, got: %v", res.Err)
	}
}

func TestReconcileRejectsMismatchedKeyPair(t *testing.T) {
	srv := newFakeCompanyServer(t, "tok")
	srv.manifest.Services = map[string][]string{"NUXT_MALL": {"10.0.0.5:3000"}}
	srv.setDomain("a.com", "NUXT_MALL")
	// 用另一对的私钥替换（manifest 指纹同步替换成新私钥的，绕过指纹检查）
	_, otherKey := genCertPair(t, "other.com")
	pair := srv.certs["a.com"]
	pair[1] = otherKey
	srv.certs["a.com"] = pair
	srv.manifest.Domains[0].PrivkeySha256 = sha256Hex(otherKey)

	cfg := testConfig(t, t.TempDir())
	client := srv.companyClientFor(t, "xyz", cfg)
	res := reconcileCompany(cfg, client, &CompanyState{Domains: map[string]DomainState{}}, "")
	if res.Err == nil || !strings.Contains(res.Err.Error(), "密码学校验") {
		t.Fatalf("公私钥不配对必须整单拒绝, got: %v", res.Err)
	}
}

func TestValidateManifestWhitelists(t *testing.T) {
	base := func() *Manifest {
		return &Manifest{
			Version:  "v1",
			Services: map[string][]string{"NUXT_MALL": {"10.0.0.5:3000"}},
			Domains: []ManifestDomain{{
				Domain: "a.com", ServiceType: "NUXT_MALL",
				FullchainSha256: strings.Repeat("a", 64), PrivkeySha256: strings.Repeat("b", 64),
			}},
		}
	}
	if err := validateManifest(base()); err != nil {
		t.Fatalf("合法 manifest 不应被拒: %v", err)
	}

	cases := map[string]func(m *Manifest){
		"非法域名":      func(m *Manifest) { m.Domains[0].Domain = "Bad_Domain!" },
		"路径穿越域名":    func(m *Manifest) { m.Domains[0].Domain = "a..com" },
		"未定义的服务类型":  func(m *Manifest) { m.Domains[0].ServiceType = "UNKNOWN" },
		"非法服务地址":    func(m *Manifest) { m.Services["NUXT_MALL"] = []string{"10.0.0.5"} },
		"非法指纹":      func(m *Manifest) { m.Domains[0].FullchainSha256 = "xyz" },
		"重复域名":      func(m *Manifest) { m.Domains = append(m.Domains, m.Domains[0]) },
		"缺 version": func(m *Manifest) { m.Version = "" },
	}
	for name, mutate := range cases {
		m := base()
		mutate(m)
		if err := validateManifest(m); err == nil {
			t.Errorf("%s 应被白名单拒绝", name)
		}
	}
}

func TestDeleteGuardFreezesAndAckUnfreezes(t *testing.T) {
	cfg := testConfig(t, t.TempDir())
	old := map[string]DomainState{}
	for i := 0; i < 1000; i++ {
		old[strings.ToLower("d"+strings.Repeat("x", 2))+string(rune('a'+i%26))+string(rune('a'+(i/26)%26))+string(rune('a'+i/676))+".com"] = DomainState{}
	}
	// 新集合只剩 500 个 → 删除 ~500 > max(100, 300) → 冻结
	newSet := map[string]bool{}
	count := 0
	for name := range old {
		if count >= 500 {
			break
		}
		newSet[name] = true
		count++
	}
	if err := checkDeleteGuard(cfg, "xyz", old, newSet); err == nil {
		t.Fatalf("批量删除必须触发护栏冻结")
	}

	// touch ack 文件 → 放行一次并消费掉 ack
	ack := filepath.Join(cfg.DataDir, "ack-xyz")
	if err := os.WriteFile(ack, nil, 0o644); err != nil {
		t.Fatal(err)
	}
	if err := checkDeleteGuard(cfg, "xyz", old, newSet); err != nil {
		t.Fatalf("ack 文件应放行: %v", err)
	}
	if _, err := os.Stat(ack); !os.IsNotExist(err) {
		t.Fatalf("ack 文件应被消费删除")
	}
}

func TestDeleteGuardAllowsSmallSets(t *testing.T) {
	cfg := testConfig(t, t.TempDir())
	old := map[string]DomainState{"a.com": {}, "b.com": {}, "c.com": {}}
	// 小集合清空（3 条 < 阈值 100）是合法的空列表语义
	if err := checkDeleteGuard(cfg, "xyz", old, map[string]bool{}); err != nil {
		t.Fatalf("小集合清空不应触发护栏: %v", err)
	}
}
