package main

// state_config_test.go —— state.json 持久化与 .env 配置解析测试。

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestStateRoundTrip(t *testing.T) {
	dir := t.TempDir()
	s := &State{Companies: map[string]*CompanyState{
		"xyz": {
			AppliedVersion: "sha256:abc",
			Services:       map[string][]string{"NUXT_MALL": {"10.0.0.5:3000"}},
			Domains:        map[string]DomainState{"a.com": {ServiceType: "NUXT_MALL"}},
			LastStatus:     "ok",
			UpdatedAt:      time.Now(),
		},
	}, ActiveContentHash: "deadbeef"}

	if err := s.Save(dir); err != nil {
		t.Fatalf("保存失败: %v", err)
	}
	loaded := LoadState(dir)
	if loaded.ActiveContentHash != "deadbeef" {
		t.Fatalf("ActiveContentHash 丢失")
	}
	cs := loaded.Company("xyz")
	if cs.AppliedVersion != "sha256:abc" || cs.Domains["a.com"].ServiceType != "NUXT_MALL" {
		t.Fatalf("公司状态丢失: %+v", cs)
	}
}

func TestStateCorruptedFileSelfHeals(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "state.json"), []byte("{not json"), 0o644); err != nil {
		t.Fatal(err)
	}
	s := LoadState(dir)
	if s == nil || len(s.Companies) != 0 || s.ActiveContentHash != "" {
		t.Fatalf("损坏的 state.json 应按空状态自愈")
	}
}

func TestLoadConfigFromEnv(t *testing.T) {
	t.Setenv("FRONT_SERVER_NAME", "fsn-01")
	t.Setenv("POLL_INTERVAL", "5s")
	t.Setenv("NGINX_LOG_MAX_FILE", "128m")
	t.Setenv("NGINX_ACCESS_LOG", "on")
	t.Setenv("FRONT_MEM_LIMIT", "4g")
	t.Setenv("TUNE_SSL_CERT_CACHE_MAX", "4096")
	t.Setenv("COMPANIES", `[{"name":"xyz","baseUrl":"https://admin.xyz.com/","token":"tok-1"}]`)

	cfg, err := LoadConfig()
	if err != nil {
		t.Fatalf("合法配置不应失败: %v", err)
	}
	if cfg.ServerName != "fsn-01" || cfg.PollInterval != 5*time.Second {
		t.Fatalf("基础字段解析错误: %+v", cfg)
	}
	if cfg.LogMaxBytes != 128*1024*1024 || !cfg.AccessLog || cfg.MemLimitBytes != 4*1024*1024*1024 {
		t.Fatalf("容量/开关解析错误")
	}
	if cfg.Tune["SSL_CERT_CACHE_MAX"] != "4096" {
		t.Fatalf("TUNE_ 前缀收集失败: %v", cfg.Tune)
	}
	if len(cfg.Companies) != 1 || cfg.Companies[0].BaseURL != "https://admin.xyz.com" {
		t.Fatalf("公司清单解析错误（baseUrl 应去掉尾斜杠）: %+v", cfg.Companies)
	}
}

func TestLoadConfigRejectsBadInput(t *testing.T) {
	t.Setenv("FRONT_SERVER_NAME", "fsn-01")
	t.Setenv("COMPANIES", `[{"name":"Bad Name!","baseUrl":"https://x.com","token":"t"}]`)
	if _, err := LoadConfig(); err == nil {
		t.Fatalf("非法公司名必须被拒绝")
	}

	t.Setenv("COMPANIES", `[{"name":"xyz","baseUrl":"ftp://x.com","token":"t"}]`)
	if _, err := LoadConfig(); err == nil {
		t.Fatalf("非 http(s) baseUrl 必须被拒绝")
	}

	t.Setenv("COMPANIES", `[{"name":"xyz","baseUrl":"https://x.com","token":""}]`)
	if _, err := LoadConfig(); err == nil {
		t.Fatalf("空 token 必须被拒绝")
	}
}

func TestParseSize(t *testing.T) {
	cases := map[string]int64{
		"256m": 256 * 1024 * 1024,
		"8g":   8 * 1024 * 1024 * 1024,
		"10":   10,
		"1k":   1024,
	}
	for in, want := range cases {
		got, err := parseSize(in)
		if err != nil || got != want {
			t.Errorf("parseSize(%q) = %d, %v; want %d", in, got, err, want)
		}
	}
	if _, err := parseSize("abc"); err == nil {
		t.Errorf("非法容量必须报错")
	}
}
