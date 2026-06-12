package main

// config.go —— 配置加载与日志工具。
//
// agent 的全部配置来自环境变量（docker compose 从 .env 注入），没有配置文件。
// 这里把每个环境变量解析成强类型字段，非法值在启动时直接报错退出（fail-fast），
// 避免带着错误配置进入主循环。
//
// Go 语法速记（给不熟 Go 的读者）：
//   - `type Xxx struct {...}` 定义结构体；字段首字母大写 = 包外可见（本项目单包，无所谓）。
//   - 函数可以返回多个值，惯例最后一个是 error；调用方用 `if err != nil` 处理。
//   - `:=` 是声明并赋值的简写，只能在函数内用。

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"regexp"
	"strconv"
	"strings"
	"time"
)

// CompanyConfig 是 .env 里 COMPANIES JSON 数组的一个元素：一家公司的接入信息。
type CompanyConfig struct {
	// Name：公司标识，用作落盘目录名与 nginx upstream 名前缀。
	// 只允许小写字母/数字/下划线/连字符（要进 nginx 配置标识符，必须收紧）。
	Name string `json:"name"`
	// BaseURL：该公司 Java 管理后台的基地址，如 https://admin.xyz-example.com
	// （Host 同时决定了 Java 侧解析到哪家公司——租户隔离的关键）。
	BaseURL string `json:"baseUrl"`
	// Token：静态 Bearer token（与该公司 Java 的 FRONT_AGENT_TOKENS 对应）。
	Token string `json:"token"`
	// CAFile：可选，自定义 CA 证书路径（内网/自签场景）。
	CAFile string `json:"caFile,omitempty"`
	// InsecureSkipVerify：可选，跳过 TLS 校验（仅限调试，生产勿用）。
	InsecureSkipVerify bool `json:"insecureSkipVerify,omitempty"`
}

// Config 汇总 agent 的全部运行配置。
type Config struct {
	ServerName        string            // FRONT_SERVER_NAME：本机唯一标识，仅用于回报归属
	PollInterval      time.Duration     // POLL_INTERVAL：轮询周期（默认 15s）
	DataDir           string            // DATA_DIR：持久卷根目录（默认 /data/v7-front）
	Companies         []CompanyConfig   // COMPANIES：公司清单
	NginxConf         string            // NGINX_CONF：nginx 主配置路径（shadow nginx -t 用）
	LogMaxBytes       int64             // NGINX_LOG_MAX_FILE：单个日志文件轮转阈值
	LogKeep           int               // NGINX_LOG_KEEP：轮转保留份数（gzip）
	AccessLog         bool              // NGINX_ACCESS_LOG：access 日志开关（默认 off）
	CPULimit          int               // FRONT_CPU_LIMIT：CPU 配额；0 = 未配置（用宿主核数）
	MemLimitBytes     int64             // FRONT_MEM_LIMIT：内存配额；0 = 未配置（用宿主内存）
	DiskLimitBytes    int64             // FRONT_DISK_LIMIT：磁盘计算配额；0 = 未配置（用卷容量）
	Tune              map[string]string // TUNE_*：调优公式的显式覆盖（键不含 TUNE_ 前缀）
	DeleteGuardMin    int               // DELETE_GUARD_MIN：删除护栏绝对阈值（默认 100）
	DeleteGuardRatio  float64           // DELETE_GUARD_RATIO：删除护栏比例阈值（默认 0.30）
	ReloadMinInterval time.Duration     // RELOAD_MIN_INTERVAL：reload 防抖最小间隔（默认 30s）
	CertConcurrency   int               // CERT_DOWNLOAD_CONCURRENCY：证书并发下载数（默认 32）
	HTTPTimeout       time.Duration     // HTTP_TIMEOUT：单次 API 请求超时（默认 15s）
	ReleaseKeep       int               // RELEASE_KEEP：release 目录保留份数（默认 5）
}

// 公司名白名单：要进目录名和 nginx upstream 标识符
var companyNamePattern = regexp.MustCompile(`^[a-z0-9][a-z0-9_-]*$`)

// LoadConfig 从环境变量装配 Config；任何非法值都返回 error（启动期 fail-fast）。
func LoadConfig() (*Config, error) {
	cfg := &Config{
		ServerName:       strings.TrimSpace(os.Getenv("FRONT_SERVER_NAME")),
		DataDir:          envOr("DATA_DIR", "/data/v7-front"),
		NginxConf:        envOr("NGINX_CONF", "/etc/nginx/nginx.conf"),
		Tune:             map[string]string{},
		DeleteGuardMin:   100,
		DeleteGuardRatio: 0.30,
		CertConcurrency:  32,
		LogKeep:          4,
		ReleaseKeep:      5,
	}
	if cfg.ServerName == "" {
		return nil, fmt.Errorf("必须配置 FRONT_SERVER_NAME（本前端机唯一标识）")
	}

	var err error
	if cfg.PollInterval, err = parseDurationEnv("POLL_INTERVAL", 15*time.Second); err != nil {
		return nil, err
	}
	if cfg.ReloadMinInterval, err = parseDurationEnv("RELOAD_MIN_INTERVAL", 30*time.Second); err != nil {
		return nil, err
	}
	if cfg.HTTPTimeout, err = parseDurationEnv("HTTP_TIMEOUT", 15*time.Second); err != nil {
		return nil, err
	}
	if cfg.LogMaxBytes, err = parseSizeEnv("NGINX_LOG_MAX_FILE", 256*1024*1024); err != nil {
		return nil, err
	}
	if cfg.LogKeep, err = parseIntEnv("NGINX_LOG_KEEP", 4); err != nil {
		return nil, err
	}
	if cfg.CertConcurrency, err = parseIntEnv("CERT_DOWNLOAD_CONCURRENCY", 32); err != nil {
		return nil, err
	}
	if cfg.ReleaseKeep, err = parseIntEnv("RELEASE_KEEP", 5); err != nil {
		return nil, err
	}
	if cfg.DeleteGuardMin, err = parseIntEnv("DELETE_GUARD_MIN", 100); err != nil {
		return nil, err
	}
	if ratio := strings.TrimSpace(os.Getenv("DELETE_GUARD_RATIO")); ratio != "" {
		if cfg.DeleteGuardRatio, err = strconv.ParseFloat(ratio, 64); err != nil {
			return nil, fmt.Errorf("DELETE_GUARD_RATIO 非法: %w", err)
		}
	}

	// access 日志开关：只认 on/off（默认 off，见设计文档「日志封顶」）
	switch strings.ToLower(envOr("NGINX_ACCESS_LOG", "off")) {
	case "on":
		cfg.AccessLog = true
	case "off":
		cfg.AccessLog = false
	default:
		return nil, fmt.Errorf("NGINX_ACCESS_LOG 只接受 on/off")
	}

	if cfg.CPULimit, err = parseIntEnv("FRONT_CPU_LIMIT", 0); err != nil {
		return nil, err
	}
	if cfg.MemLimitBytes, err = parseSizeEnv("FRONT_MEM_LIMIT", 0); err != nil {
		return nil, err
	}
	if cfg.DiskLimitBytes, err = parseSizeEnv("FRONT_DISK_LIMIT", 0); err != nil {
		return nil, err
	}

	// 收集所有 TUNE_ 前缀的覆盖项（如 TUNE_SSL_CERT_CACHE_MAX=4096 → Tune["SSL_CERT_CACHE_MAX"]）
	for _, kv := range os.Environ() {
		if !strings.HasPrefix(kv, "TUNE_") {
			continue
		}
		// SplitN 把 "TUNE_X=v" 按第一个 = 切成两段
		parts := strings.SplitN(kv, "=", 2)
		if len(parts) == 2 && strings.TrimSpace(parts[1]) != "" {
			cfg.Tune[strings.TrimPrefix(parts[0], "TUNE_")] = strings.TrimSpace(parts[1])
		}
	}

	// COMPANIES：JSON 数组
	rawCompanies := strings.TrimSpace(os.Getenv("COMPANIES"))
	if rawCompanies == "" {
		return nil, fmt.Errorf("必须配置 COMPANIES（公司清单 JSON 数组）")
	}
	if err := json.Unmarshal([]byte(rawCompanies), &cfg.Companies); err != nil {
		return nil, fmt.Errorf("COMPANIES 不是合法 JSON: %w", err)
	}
	if len(cfg.Companies) == 0 {
		return nil, fmt.Errorf("COMPANIES 不能为空数组")
	}
	seen := map[string]bool{}
	for i := range cfg.Companies {
		c := &cfg.Companies[i]
		c.Name = strings.TrimSpace(c.Name)
		c.BaseURL = strings.TrimRight(strings.TrimSpace(c.BaseURL), "/")
		c.Token = strings.TrimSpace(c.Token)
		if !companyNamePattern.MatchString(c.Name) {
			return nil, fmt.Errorf("公司名 %q 非法：只允许小写字母/数字/下划线/连字符且以字母数字开头", c.Name)
		}
		if seen[c.Name] {
			return nil, fmt.Errorf("公司名 %q 重复", c.Name)
		}
		seen[c.Name] = true
		if !strings.HasPrefix(c.BaseURL, "http://") && !strings.HasPrefix(c.BaseURL, "https://") {
			return nil, fmt.Errorf("公司 %s 的 baseUrl 必须以 http:// 或 https:// 开头", c.Name)
		}
		if c.Token == "" {
			return nil, fmt.Errorf("公司 %s 缺少 token", c.Name)
		}
	}
	return cfg, nil
}

// ---------- 环境变量解析小工具 ----------

func envOr(key, def string) string {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		return v
	}
	return def
}

func parseIntEnv(key string, def int) (int, error) {
	v := strings.TrimSpace(os.Getenv(key))
	if v == "" {
		return def, nil
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		return 0, fmt.Errorf("%s 非法（需要整数）: %w", key, err)
	}
	return n, nil
}

func parseDurationEnv(key string, def time.Duration) (time.Duration, error) {
	v := strings.TrimSpace(os.Getenv(key))
	if v == "" {
		return def, nil
	}
	d, err := time.ParseDuration(v) // 接受 "15s" "1m30s" 这类写法
	if err != nil {
		return 0, fmt.Errorf("%s 非法（需要时长，如 15s）: %w", key, err)
	}
	return d, nil
}

// parseSizeEnv 解析 "256m"/"8g"/"1024k"/纯数字（字节）这类容量写法。
func parseSizeEnv(key string, def int64) (int64, error) {
	v := strings.ToLower(strings.TrimSpace(os.Getenv(key)))
	if v == "" {
		return def, nil
	}
	n, err := parseSize(v)
	if err != nil {
		return 0, fmt.Errorf("%s 非法: %w", key, err)
	}
	return n, nil
}

func parseSize(v string) (int64, error) {
	v = strings.ToLower(strings.TrimSpace(v))
	if v == "" {
		return 0, fmt.Errorf("空容量值")
	}
	mult := int64(1)
	switch v[len(v)-1] {
	case 'k':
		mult, v = 1024, v[:len(v)-1]
	case 'm':
		mult, v = 1024*1024, v[:len(v)-1]
	case 'g':
		mult, v = 1024*1024*1024, v[:len(v)-1]
	case 't':
		mult, v = 1024*1024*1024*1024, v[:len(v)-1]
	}
	n, err := strconv.ParseInt(strings.TrimSpace(v), 10, 64)
	if err != nil {
		return 0, fmt.Errorf("容量数字部分非法: %w", err)
	}
	return n * mult, nil
}

// ---------- 日志（带级别） ----------
//
// AGENT_LOG_LEVEL=debug|info|error，默认 info。
// 设计约定：info 级只在「状态变化」时输出（应用了新 release、护栏冻结、水位告警），
// 日常 304 轮询走 debug——info 安静但保留排障时间线。

var logLevel = 1 // 0=debug 1=info 2=error

func initLogLevel() {
	switch strings.ToLower(envOr("AGENT_LOG_LEVEL", "info")) {
	case "debug":
		logLevel = 0
	case "error":
		logLevel = 2
	default:
		logLevel = 1
	}
	// 标准库 log 默认输出到 stderr，docker 日志驱动负责收集与限额
	log.SetFlags(log.LstdFlags | log.Lmsgprefix)
}

func logDebug(format string, args ...any) {
	if logLevel <= 0 {
		log.Printf("[DEBUG] "+format, args...)
	}
}

func logInfo(format string, args ...any) {
	if logLevel <= 1 {
		log.Printf("[INFO] "+format, args...)
	}
}

func logError(format string, args ...any) {
	log.Printf("[ERROR] "+format, args...)
}
