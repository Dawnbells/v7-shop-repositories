package main

// tuning_test.go —— 调优公式分档测试（含设计文档「参考机型 2C/4G/80G」的实算断言）。

import (
	"strings"
	"testing"
)

const gib = int64(1024 * 1024 * 1024)

func TestComputeTuningReferenceMachine2C4G80G(t *testing.T) {
	cfg := testConfig(t, t.TempDir()) // LogMaxBytes=256m, LogKeep=4
	r := computeTuning(cfg, 2, 4*gib, 80*gib)

	if r.WorkerProcesses != 2 {
		t.Errorf("2 核应得 workers=2, got %d", r.WorkerProcesses)
	}
	if r.WorkerConnections != 8192 {
		t.Errorf("4G 应得 connections=8192, got %d", r.WorkerConnections)
	}
	if r.WorkerRlimitNofile != 16384 {
		t.Errorf("nofile 应为 connections×2=16384, got %d", r.WorkerRlimitNofile)
	}
	if r.SSLCertCacheMax != 8192 {
		t.Errorf("4G 档证书缓存应为 8192, got %d", r.SSLCertCacheMax)
	}
	if r.SSLSessionCache != "shared:SSL:32m" {
		t.Errorf("≥4G 会话缓存应为 32m, got %s", r.SSLSessionCache)
	}
	// 缓存预算 ≈ (80G − 2.5G 日志 − 2G) × 0.6 ≈ 45G，image:nuxt = 2:1
	if r.ImageCacheMaxBytes < 28*gib || r.ImageCacheMaxBytes > 32*gib {
		t.Errorf("image 缓存应在 ~30G 附近, got %d", r.ImageCacheMaxBytes)
	}
	if r.NuxtCacheMaxBytes < 14*gib || r.NuxtCacheMaxBytes > 16*gib {
		t.Errorf("nuxt 缓存应在 ~15G 附近, got %d", r.NuxtCacheMaxBytes)
	}
}

func TestComputeTuningTiersAndFloors(t *testing.T) {
	cfg := testConfig(t, t.TempDir())

	// 大机：8C16G → workers=7（留 1 核），certcache=32768，connections 封顶逻辑
	big := computeTuning(cfg, 8, 16*gib, 200*gib)
	if big.WorkerProcesses != 7 {
		t.Errorf("8 核应留 1 核得 7, got %d", big.WorkerProcesses)
	}
	if big.SSLCertCacheMax != 32768 {
		t.Errorf(">8G 档证书缓存应为 32768, got %d", big.SSLCertCacheMax)
	}
	if big.WorkerConnections != 32768 {
		t.Errorf("16G 应得 connections=32768, got %d", big.WorkerConnections)
	}

	// 小机：1C1G + 5G 小盘（预算≈0.3G，远低于地板值）→ 全部触发下限
	small := computeTuning(cfg, 1, 1*gib, 5*gib)
	if small.WorkerProcesses != 1 {
		t.Errorf("1 核应得 workers=1")
	}
	if small.WorkerConnections != 4096 {
		t.Errorf("连接数应触发下限 4096, got %d", small.WorkerConnections)
	}
	if small.SSLCertCacheMax != 4096 {
		t.Errorf("≤2G 档证书缓存应为 4096, got %d", small.SSLCertCacheMax)
	}
	if small.SSLSessionCache != "shared:SSL:10m" {
		t.Errorf("<4G 会话缓存应为 10m")
	}
	if small.ImageCacheMaxBytes != 2*gib || small.NuxtCacheMaxBytes != 1*gib {
		t.Errorf("小盘应触发缓存下限 2g/1g, got %d/%d", small.ImageCacheMaxBytes, small.NuxtCacheMaxBytes)
	}
	if small.ImageKeysZoneMB != 64 || small.NuxtKeysZoneMB != 64 {
		t.Errorf("keys_zone 应触发下限 64m")
	}
}

func TestComputeTuningOverrides(t *testing.T) {
	cfg := testConfig(t, t.TempDir())
	cfg.Tune = map[string]string{
		"SSL_CERT_CACHE_MAX":   "4096",
		"WORKER_CONNECTIONS":   "2048",
		"IMAGE_CACHE_MAX_SIZE": "5g",
		"SSL_SESSION_CACHE":    "shared:SSL:64m",
	}
	r := computeTuning(cfg, 2, 4*gib, 80*gib)
	if r.SSLCertCacheMax != 4096 {
		t.Errorf("TUNE_SSL_CERT_CACHE_MAX 覆盖未生效")
	}
	if r.WorkerConnections != 2048 {
		t.Errorf("TUNE_WORKER_CONNECTIONS 覆盖未生效")
	}
	if r.ImageCacheMaxBytes != 5*gib {
		t.Errorf("TUNE_IMAGE_CACHE_MAX_SIZE 覆盖未生效")
	}
	if r.SSLSessionCache != "shared:SSL:64m" {
		t.Errorf("TUNE_SSL_SESSION_CACHE 覆盖未生效")
	}
}

func TestRenderSystemFilesAccessLogSwitch(t *testing.T) {
	cfg := testConfig(t, t.TempDir())
	r := computeTuning(cfg, 2, 4*gib, 80*gib)

	off := renderSystemFiles(cfg, r)
	if !strings.Contains(off["http.conf"], "access_log off;") {
		t.Errorf("默认应 access_log off:\n%s", off["http.conf"])
	}

	cfg.AccessLog = true
	on := renderSystemFiles(cfg, r)
	if !strings.Contains(on["http.conf"], "logs/access.log vhost buffer=64k flush=5s;") {
		t.Errorf("开启后应写卷内 access.log:\n%s", on["http.conf"])
	}
	if !strings.Contains(on["main.conf"], "worker_processes 2;") ||
		!strings.Contains(on["events.conf"], "worker_connections 8192;") {
		t.Errorf("main/events 渲染不符")
	}
	if !strings.Contains(on["http.conf"], "keys_zone=IMAGE_CACHE:") ||
		!strings.Contains(on["http.conf"], "ssl_certificate_cache max=8192") {
		t.Errorf("http.conf 渲染不符:\n%s", on["http.conf"])
	}
}
