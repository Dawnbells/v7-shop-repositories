package main

// tuning.go —— 资源自动调优：按配额（或宿主规格）计算 nginx 尺寸类参数，
// 渲染成 release 的 _system/{main,events,http}.conf。
//
// 取值优先级（设计文档 §4.5「资源限制与自动调优」）：
//   TUNE_* 显式覆盖  >  .env 配额(FRONT_CPU/MEM/DISK_LIMIT)  >  宿主规格(/proc、statfs)
// 公式整体预留约 25% 冗余。容器里读 /proc 看到的就是宿主值，
// 恰好实现「未配置时使用宿主机配置大小」。

import (
	"fmt"
	"os"
	"runtime"
	"strconv"
	"strings"
)

// TuningResult 是调优计算的全部产出（数值留着给日志与测试断言用）。
type TuningResult struct {
	WorkerProcesses    int
	WorkerConnections  int
	WorkerRlimitNofile int
	SSLCertCacheMax    int
	SSLSessionCache    string // 如 "shared:SSL:32m"
	ImageCacheMaxBytes int64
	NuxtCacheMaxBytes  int64
	ImageKeysZoneMB    int
	NuxtKeysZoneMB     int
}

// computeTuning 执行全部公式（输入都是已经确定的资源量，便于单测）。
func computeTuning(cfg *Config, cpu int, memBytes int64, diskBytes int64) TuningResult {
	t := TuningResult{}
	memMB := memBytes / (1024 * 1024)

	// worker 数：CPU≥4 时留 1 核给 agent/系统，否则全用；下限 1
	t.WorkerProcesses = cpu
	if cpu >= 4 {
		t.WorkerProcesses = cpu - 1
	}
	if t.WorkerProcesses < 1 {
		t.WorkerProcesses = 1
	}

	// 连接数：clamp(4096, MEM_MB × 2, 65536)
	t.WorkerConnections = clampInt(int(memMB)*2, 4096, 65536)
	t.WorkerRlimitNofile = t.WorkerConnections * 2

	// 证书缓存分档（per-worker LRU，容纳热点集即可）
	switch {
	case memMB <= 2048:
		t.SSLCertCacheMax = 4096
	case memMB <= 4096:
		t.SSLCertCacheMax = 8192
	case memMB <= 8192:
		t.SSLCertCacheMax = 16384
	default:
		t.SSLCertCacheMax = 32768
	}

	// 会话缓存：≥4G 给 32m，否则 10m
	if memMB >= 4096 {
		t.SSLSessionCache = "shared:SSL:32m"
	} else {
		t.SSLSessionCache = "shared:SSL:10m"
	}

	// 缓存盘预算 =（磁盘配额 − 日志上限 − release/证书 ~2G）× 0.6，image:nuxt = 2:1
	logBudget := cfg.LogMaxBytes * int64(cfg.LogKeep+1) * 2
	budget := int64(float64(diskBytes-logBudget-2*1024*1024*1024) * 0.6)
	t.ImageCacheMaxBytes = maxInt64(budget*2/3, 2*1024*1024*1024) // 下限 2g
	t.NuxtCacheMaxBytes = maxInt64(budget/3, 1*1024*1024*1024)    // 下限 1g

	// keys_zone 随 max_size 等比：每 10g 配 80m，下限 64m
	t.ImageKeysZoneMB = keysZoneMB(t.ImageCacheMaxBytes)
	t.NuxtKeysZoneMB = keysZoneMB(t.NuxtCacheMaxBytes)

	// TUNE_* 显式覆盖（最后套用，覆盖一切公式）
	applyIntOverride(cfg, "WORKER_PROCESSES", &t.WorkerProcesses)
	applyIntOverride(cfg, "WORKER_CONNECTIONS", &t.WorkerConnections)
	applyIntOverride(cfg, "WORKER_RLIMIT_NOFILE", &t.WorkerRlimitNofile)
	applyIntOverride(cfg, "SSL_CERT_CACHE_MAX", &t.SSLCertCacheMax)
	if v, ok := cfg.Tune["SSL_SESSION_CACHE"]; ok {
		t.SSLSessionCache = v
	}
	applySizeOverride(cfg, "IMAGE_CACHE_MAX_SIZE", &t.ImageCacheMaxBytes)
	applySizeOverride(cfg, "NUXT_CACHE_MAX_SIZE", &t.NuxtCacheMaxBytes)
	applyIntOverride(cfg, "IMAGE_CACHE_KEYS_ZONE_MB", &t.ImageKeysZoneMB)
	applyIntOverride(cfg, "NUXT_CACHE_KEYS_ZONE_MB", &t.NuxtKeysZoneMB)
	return t
}

// detectResources 确定三项资源量：.env 配额优先，未配置读宿主。
func detectResources(cfg *Config) (cpu int, memBytes int64, diskBytes int64) {
	cpu = cfg.CPULimit
	if cpu <= 0 {
		cpu = runtime.NumCPU() // 容器未限核时 = 宿主逻辑核数
	}
	memBytes = cfg.MemLimitBytes
	if memBytes <= 0 {
		memBytes = readMemTotal() // /proc/meminfo 的 MemTotal（宿主值）
	}
	if memBytes <= 0 {
		memBytes = 4 * 1024 * 1024 * 1024 // 读不到时按 4G 保守取值
	}
	diskBytes = cfg.DiskLimitBytes
	if diskBytes <= 0 {
		diskBytes = diskTotalBytes(cfg.DataDir) // 数据卷所在盘容量
	}
	if diskBytes <= 0 {
		diskBytes = 80 * 1024 * 1024 * 1024 // 读不到时按 80G 保守取值
	}
	return cpu, memBytes, diskBytes
}

// renderSystemFiles 把调优结果渲染为 _system 三件套。
// 这些文件参与 release 内容哈希：调优没变 → 哈希不变 → 不触发 reload。
func renderSystemFiles(cfg *Config, t TuningResult) map[string]string {
	header := "# 本文件由 v7front-agent 按资源配额渲染，勿手工编辑；改 .env 后 docker compose up -d 生效\n"

	main := header +
		fmt.Sprintf("worker_processes %d;\n", t.WorkerProcesses) +
		fmt.Sprintf("worker_rlimit_nofile %d;\n", t.WorkerRlimitNofile)

	events := header +
		fmt.Sprintf("worker_connections %d;\n", t.WorkerConnections)

	accessLog := "access_log off;\n"
	if cfg.AccessLog {
		// buffer+flush 降低高 QPS 下的写盘频率；vhost 格式含 $host 可按域名检索
		accessLog = fmt.Sprintf("access_log %s/logs/access.log vhost buffer=64k flush=5s;\n",
			strings.TrimRight(cfg.DataDir, "/"))
	}
	httpConf := header +
		fmt.Sprintf("proxy_cache_path /var/cache/nginx/image levels=1:2 keys_zone=IMAGE_CACHE:%dm max_size=%s inactive=30d use_temp_path=off;\n",
			t.ImageKeysZoneMB, formatSize(t.ImageCacheMaxBytes)) +
		fmt.Sprintf("proxy_cache_path /var/cache/nginx/nuxt levels=1:2 keys_zone=NUXT_CACHE:%dm max_size=%s inactive=365d use_temp_path=off;\n",
			t.NuxtKeysZoneMB, formatSize(t.NuxtCacheMaxBytes)) +
		fmt.Sprintf("ssl_certificate_cache max=%d inactive=10m valid=5m;\n", t.SSLCertCacheMax) +
		fmt.Sprintf("ssl_session_cache %s;\n", t.SSLSessionCache) +
		accessLog

	return map[string]string{
		"main.conf":   main,
		"events.conf": events,
		"http.conf":   httpConf,
	}
}

// ---------- 资源探测 ----------

// readMemTotal 解析 /proc/meminfo 的 MemTotal 行（单位 kB）。
func readMemTotal() int64 {
	data, err := os.ReadFile("/proc/meminfo")
	if err != nil {
		return 0
	}
	for _, line := range strings.Split(string(data), "\n") {
		if !strings.HasPrefix(line, "MemTotal:") {
			continue
		}
		fields := strings.Fields(line) // "MemTotal:  4030172 kB" → 3 段
		if len(fields) >= 2 {
			kb, err := strconv.ParseInt(fields[1], 10, 64)
			if err == nil {
				return kb * 1024
			}
		}
	}
	return 0
}

// ---------- 小工具 ----------

func clampInt(v, lo, hi int) int {
	if v < lo {
		return lo
	}
	if v > hi {
		return hi
	}
	return v
}

func maxInt64(a, b int64) int64 {
	if a > b {
		return a
	}
	return b
}

func keysZoneMB(maxSize int64) int {
	mb := int(maxSize / (10 * 1024 * 1024 * 1024) * 80)
	if mb < 64 {
		return 64
	}
	return mb
}

// formatSize 把字节数渲染成 nginx 接受的容量写法（取整到 m 或 g）。
func formatSize(b int64) string {
	const g = 1024 * 1024 * 1024
	if b >= g && b%g == 0 {
		return fmt.Sprintf("%dg", b/g)
	}
	return fmt.Sprintf("%dm", b/(1024*1024))
}

func applyIntOverride(cfg *Config, key string, target *int) {
	if v, ok := cfg.Tune[key]; ok {
		if n, err := strconv.Atoi(v); err == nil && n > 0 {
			*target = n
		} else {
			logError("TUNE_%s=%q 非法，已忽略", key, v)
		}
	}
}

func applySizeOverride(cfg *Config, key string, target *int64) {
	if v, ok := cfg.Tune[key]; ok {
		if n, err := parseSize(v); err == nil && n > 0 {
			*target = n
		} else {
			logError("TUNE_%s=%q 非法，已忽略", key, v)
		}
	}
}
