package main

// render_test.go —— 渲染确定性、release 构建与内容寻址测试。

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func sampleDomains() map[string]DomainState {
	return map[string]DomainState{
		"b-shop.com": {ServiceType: "THYMELEAF", FullchainSha256: strings.Repeat("a", 64), PrivkeySha256: strings.Repeat("b", 64)},
		"a-shop.com": {ServiceType: "NUXT_MALL", FullchainSha256: strings.Repeat("c", 64), PrivkeySha256: strings.Repeat("d", 64)},
	}
}

func TestRenderCompanyFilesDeterministicAndCorrect(t *testing.T) {
	services := map[string][]string{"NUXT_MALL": {"10.0.0.5:3000"}, "THYMELEAF": {"10.0.0.5:8080"}}
	first := renderCompanyFiles("/data/v7-front", "xyz", services, sampleDomains())
	second := renderCompanyFiles("/data/v7-front", "xyz", services, sampleDomains())

	for name := range first {
		if first[name] != second[name] {
			t.Fatalf("渲染必须确定性: %s 两次输出不一致", name)
		}
	}

	routes := first["routes.map"]
	// 域名按字典序输出，前导点形式覆盖主域+泛子域
	if !strings.Contains(routes, ".a-shop.com  xyz_NUXT_MALL;") ||
		!strings.Contains(routes, ".b-shop.com  xyz_THYMELEAF;") {
		t.Fatalf("routes.map 内容不符:\n%s", routes)
	}
	if strings.Index(routes, "a-shop") > strings.Index(routes, "b-shop") {
		t.Fatalf("routes.map 必须按域名排序")
	}

	certs := first["certs.map"]
	if !strings.Contains(certs, ".a-shop.com  /data/v7-front/active/xyz/certs/a-shop.com;") {
		t.Fatalf("certs.map 必须指向 active 下的公司证书目录:\n%s", certs)
	}

	caches := first["caches.map"]
	if !strings.Contains(caches, ".b-shop.com  IMAGE_CACHE;") {
		t.Fatalf("legacy 域名应进 IMAGE_CACHE 档:\n%s", caches)
	}
	if strings.Contains(caches, "a-shop.com") {
		t.Fatalf("nuxt 域名不应出现在 caches.map（默认 off）:\n%s", caches)
	}

	upstreams := first["upstreams.conf"]
	if !strings.Contains(upstreams, "upstream xyz_NUXT_MALL {") ||
		!strings.Contains(upstreams, "    server 10.0.0.5:3000;") ||
		!strings.Contains(upstreams, "    keepalive 32;") {
		t.Fatalf("upstreams.conf 内容不符:\n%s", upstreams)
	}
}

func TestBuildReleaseContentAddressing(t *testing.T) {
	dataDir := t.TempDir()
	cert, key := genCertPair(t, "a.com")
	input := releaseInput{
		SystemFiles: map[string]string{"main.conf": "worker_processes 2;\n"},
		Companies: []releaseCompany{{
			Name:  "xyz",
			Files: CompanyFiles{"routes.map": ".a.com  xyz_NUXT_MALL;\n"},
			Domains: map[string]DomainState{
				"a.com": {ServiceType: "NUXT_MALL", FullchainSha256: sha256Hex(cert), PrivkeySha256: sha256Hex(key)},
			},
			NewCerts: map[string]CertPayload{"a.com": {Fullchain: cert, Privkey: key}},
		}},
	}

	b1, h1, err := buildRelease(dataDir, input, "")
	if err != nil {
		t.Fatalf("构建失败: %v", err)
	}
	b2, h2, err := buildRelease(dataDir, input, "")
	if err != nil {
		t.Fatalf("二次构建失败: %v", err)
	}
	if h1 != h2 {
		t.Fatalf("相同输入必须得到相同内容哈希（内容寻址前提）: %s vs %s", h1, h2)
	}
	// .content-hash 不参与哈希但要存在
	for _, b := range []string{b1, b2} {
		data, err := os.ReadFile(filepath.Join(b, ".content-hash"))
		if err != nil || strings.TrimSpace(string(data)) != h1 {
			t.Fatalf(".content-hash 必须记录内容哈希")
		}
	}

	// 改一个文件内容 → 哈希必须变化
	input.SystemFiles["main.conf"] = "worker_processes 4;\n"
	_, h3, err := buildRelease(dataDir, input, "")
	if err != nil {
		t.Fatalf("三次构建失败: %v", err)
	}
	if h3 == h1 {
		t.Fatalf("内容变化后哈希必须变化")
	}
}

func TestBuildReleaseReusesCertsFromActive(t *testing.T) {
	dataDir := t.TempDir()
	cert, key := genCertPair(t, "a.com")
	domains := map[string]DomainState{
		"a.com": {ServiceType: "NUXT_MALL", FullchainSha256: sha256Hex(cert), PrivkeySha256: sha256Hex(key)},
	}

	// 第一次：证书来自下载
	withCerts := releaseInput{
		SystemFiles: map[string]string{"main.conf": "worker_processes 1;\n"},
		Companies: []releaseCompany{{
			Name: "xyz", Files: CompanyFiles{}, Domains: domains,
			NewCerts: map[string]CertPayload{"a.com": {Fullchain: cert, Privkey: key}},
		}},
	}
	first, h1, err := buildRelease(dataDir, withCerts, "")
	if err != nil {
		t.Fatal(err)
	}
	firstFinal, err := finalizeRelease(first, h1)
	if err != nil {
		t.Fatal(err)
	}

	// 第二次：NewCerts 为空，应从上一个 release 硬链接/拷贝复用
	withoutCerts := withCerts
	withoutCerts.Companies = []releaseCompany{{
		Name: "xyz", Files: CompanyFiles{}, Domains: domains, NewCerts: nil,
	}}
	second, h2, err := buildRelease(dataDir, withoutCerts, firstFinal)
	if err != nil {
		t.Fatalf("复用证书构建失败: %v", err)
	}
	defer os.RemoveAll(second)
	if h1 != h2 {
		t.Fatalf("复用后内容应与原 release 完全一致: %s vs %s", h1, h2)
	}
	got, err := os.ReadFile(filepath.Join(second, "xyz", "certs", "a.com", "fullchain.pem"))
	if err != nil || string(got) != string(cert) {
		t.Fatalf("复用的证书内容不一致")
	}
}

func TestHashTreeIgnoresDotFiles(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "a.conf"), []byte("x"), 0o644); err != nil {
		t.Fatal(err)
	}
	h1, err := hashTree(dir)
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, ".content-hash"), []byte("whatever"), 0o644); err != nil {
		t.Fatal(err)
	}
	h2, err := hashTree(dir)
	if err != nil {
		t.Fatal(err)
	}
	if h1 != h2 {
		t.Fatalf("点开头文件不应参与内容哈希")
	}
}

// 评审 bug_001：buildRelease 中途出错时不得残留 .building-* 临时目录
// （pruneReleases 故意跳过点开头目录，泄漏将永不回收并与磁盘水位保护自放大）。
func TestBuildReleaseCleansUpOnError(t *testing.T) {
	dataDir := t.TempDir()
	// 域名证书不在 NewCerts 里，且 activeDir 为空 → linkOrCopy 找不到源文件而失败
	input := releaseInput{
		SystemFiles: map[string]string{"main.conf": "worker_processes 1;\n"},
		Companies: []releaseCompany{{
			Name:     "xyz",
			Files:    CompanyFiles{},
			Domains:  map[string]DomainState{"a.com": {ServiceType: "NUXT_MALL"}},
			NewCerts: nil, // 强制走「从 active 复用」分支
		}},
	}
	_, _, err := buildRelease(dataDir, input, "" /* activeDir 缺失 */)
	if err == nil {
		t.Fatalf("缺少证书来源时 buildRelease 应返回错误")
	}
	entries, _ := os.ReadDir(filepath.Join(dataDir, "releases"))
	for _, e := range entries {
		if strings.HasPrefix(e.Name(), ".building-") {
			t.Fatalf("出错后不应残留临时目录: %s", e.Name())
		}
	}
}

func TestWriteShadowConfReplacesActivePath(t *testing.T) {
	dir := t.TempDir()
	mainConf := filepath.Join(dir, "nginx.conf")
	content := "include /data/v7-front/active/_system/main.conf;\ninclude /data/v7-front/active/*/routes.map;\n"
	if err := os.WriteFile(mainConf, []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
	out := filepath.Join(dir, "shadow.conf")
	if err := writeShadowConf(mainConf, "/data/v7-front", "/data/v7-front/releases/x-abc", out); err != nil {
		t.Fatal(err)
	}
	shadow, _ := os.ReadFile(out)
	if strings.Contains(string(shadow), "/active/") {
		t.Fatalf("影子配置不应再引用 active:\n%s", shadow)
	}
	if !strings.Contains(string(shadow), "/data/v7-front/releases/x-abc/_system/main.conf") {
		t.Fatalf("影子配置应指向候选 release:\n%s", shadow)
	}
}
