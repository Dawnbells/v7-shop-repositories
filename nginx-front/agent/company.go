package main

// company.go —— 单公司对账：拉 manifest → 校验 → 护栏 → 下载并校验证书。
//
// 可靠性铁律（设计文档 §4.5）：
//   - 最后已知良好：公司 API 超时/5xx/解析失败/数据非法 → 该公司保持现状，绝不清空；
//   - 空域名列表是合法语义（公司清空了），与「请求失败」严格区分，但受删除护栏约束；
//   - 批量删除护栏：单轮删除 > max(DELETE_GUARD_MIN, 总数×DELETE_GUARD_RATIO) → 冻结该公司
//     并持续回报 error，运维 `touch <DATA_DIR>/ack-<公司名>` 解冻放行；
//   - 证书密码学校验：下载后先验 SHA-256 指纹 + x509 解析 + 公私钥配对，验不过整单拒绝
//     （变量证书路径是 nginx -t 的盲区，必须在 agent 层拦坏证书）。

import (
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"sync"
)

// ---------- manifest 数据结构（与 Java FrontAgentManifestService 的输出一一对应） ----------

type ManifestDomain struct {
	Domain          string `json:"domain"`
	ServiceType     string `json:"serviceType"`
	FullchainSha256 string `json:"fullchainSha256"`
	PrivkeySha256   string `json:"privkeySha256"`
}

type Manifest struct {
	Version  string              `json:"version"`
	Services map[string][]string `json:"services"`
	Domains  []ManifestDomain    `json:"domains"`
}

// ---------- 校验用白名单 ----------

// 域名：小写字母/数字/点/连字符，首尾字母数字（与 Java 侧 DOMAIN_PATTERN 一致）
var domainPattern = regexp.MustCompile(`^[a-z0-9]([a-z0-9.-]{0,251}[a-z0-9])?$`)

// 服务类型：进 nginx upstream 标识符，只允许大写字母/数字/下划线
var serviceTypePattern = regexp.MustCompile(`^[A-Z0-9_]+$`)

// upstream 地址：host:port（host 允许域名或 IPv4；端口 1-5 位数字）
var addressPattern = regexp.MustCompile(`^[a-zA-Z0-9.\-_]+:[0-9]{1,5}$`)

// ---------- 对账结果 ----------

// CertPayload 是一张已通过密码学校验的证书内容。
type CertPayload struct {
	Fullchain []byte
	Privkey   []byte
}

// CompanyResult 是一家公司本轮对账的产物，交给主循环统一构建 release。
type CompanyResult struct {
	Company string
	// Changed=false 表示 304 或失败：本轮该公司沿用 active release 里的现有目录
	Changed bool
	// 以下字段仅 Changed=true 时有效
	Version  string
	Services map[string][]string
	Domains  map[string]DomainState
	// Certs：需要写入新 release 的证书内容（域名 → 文件内容）；
	// 不在此 map 里的域名证书未变化，构建时从 active release 硬链接
	Certs map[string]CertPayload
	// Err 非 nil 表示本轮失败（保持最后已知良好），其消息会在下轮轮询回报给 Java
	Err error
}

// companyClient 包装一家公司的 HTTP 访问（token、TLS 选项、超时都在这里配好）。
type companyClient struct {
	cfg    CompanyConfig
	client *http.Client
}

func newCompanyClient(cc CompanyConfig, cfg *Config) (*companyClient, error) {
	transport := &http.Transport{}
	if cc.InsecureSkipVerify {
		transport.TLSClientConfig = &tls.Config{InsecureSkipVerify: true} // 仅调试用
	} else if cc.CAFile != "" {
		pem, err := os.ReadFile(cc.CAFile)
		if err != nil {
			return nil, fmt.Errorf("公司 %s 的 caFile 读取失败: %w", cc.Name, err)
		}
		pool := x509.NewCertPool()
		if !pool.AppendCertsFromPEM(pem) {
			return nil, fmt.Errorf("公司 %s 的 caFile 不是合法 PEM 证书", cc.Name)
		}
		transport.TLSClientConfig = &tls.Config{RootCAs: pool}
	}
	return &companyClient{
		cfg:    cc,
		client: &http.Client{Timeout: cfg.HTTPTimeout, Transport: transport},
	}, nil
}

// get 发起带 Bearer token 的 GET；返回响应体与状态码。
func (c *companyClient) get(path string, query url.Values) (int, []byte, error) {
	u := c.cfg.BaseURL + path
	if len(query) > 0 {
		u += "?" + query.Encode()
	}
	req, err := http.NewRequest(http.MethodGet, u, nil)
	if err != nil {
		return 0, nil, err
	}
	req.Header.Set("Authorization", "Bearer "+c.cfg.Token)
	resp, err := c.client.Do(req)
	if err != nil {
		return 0, nil, err
	}
	// defer：函数返回前一定执行，防止连接泄漏
	defer resp.Body.Close()
	body, err := io.ReadAll(io.LimitReader(resp.Body, 64*1024*1024)) // 64MB 上限防异常超大响应
	if err != nil {
		return resp.StatusCode, nil, err
	}
	return resp.StatusCode, body, nil
}

// fetchManifest 拉取 manifest（顺带完成「轮询即回报」）。
// 返回 (manifest, notModified, error)：304 时 manifest 为 nil 且 notModified=true。
func (c *companyClient) fetchManifest(serverName string, prev *CompanyState) (*Manifest, bool, error) {
	q := url.Values{}
	q.Set("agent", serverName)
	if prev.AppliedVersion != "" {
		q.Set("appliedVersion", prev.AppliedVersion)
	}
	// 把上一轮的处理结果作为回报参数带上（Java 落库 t_front_agent_report）
	status := prev.LastStatus
	if status == "" {
		status = "ok"
	}
	q.Set("status", status)
	if prev.LastError != "" {
		q.Set("message", truncateString(prev.LastError, 900))
	}

	code, body, err := c.get("/front-agent/manifest", q)
	if err != nil {
		return nil, false, fmt.Errorf("请求 manifest 失败: %w", err)
	}
	switch code {
	case http.StatusNotModified:
		return nil, true, nil
	case http.StatusOK:
		var m Manifest
		if err := json.Unmarshal(body, &m); err != nil {
			return nil, false, fmt.Errorf("manifest JSON 解析失败: %w", err)
		}
		return &m, false, nil
	default:
		return nil, false, fmt.Errorf("manifest 返回非预期状态码 %d: %s", code, truncateString(string(body), 200))
	}
}

// validateManifest 对 manifest 做白名单校验（渲染期句法收容的第一道闸）。
// 任何一条不过 → 整单拒绝，保持最后已知良好。
func validateManifest(m *Manifest) error {
	if m.Version == "" {
		return errors.New("manifest 缺少 version")
	}
	for t, addrs := range m.Services {
		if !serviceTypePattern.MatchString(t) {
			return fmt.Errorf("服务类型 %q 非法", t)
		}
		if len(addrs) == 0 {
			return fmt.Errorf("服务类型 %s 地址列表为空", t)
		}
		for _, a := range addrs {
			if !addressPattern.MatchString(a) {
				return fmt.Errorf("服务 %s 的地址 %q 非法（需要 host:port）", t, a)
			}
		}
	}
	seen := map[string]bool{}
	for _, d := range m.Domains {
		if !domainPattern.MatchString(d.Domain) || strings.Contains(d.Domain, "..") {
			return fmt.Errorf("域名 %q 非法", d.Domain)
		}
		if seen[d.Domain] {
			return fmt.Errorf("域名 %q 在 manifest 中重复", d.Domain)
		}
		seen[d.Domain] = true
		if _, ok := m.Services[d.ServiceType]; !ok {
			return fmt.Errorf("域名 %s 引用了未定义地址的服务类型 %s", d.Domain, d.ServiceType)
		}
		if !isHex64(d.FullchainSha256) || !isHex64(d.PrivkeySha256) {
			return fmt.Errorf("域名 %s 的证书指纹非法（需要 64 位 hex）", d.Domain)
		}
	}
	return nil
}

// checkDeleteGuard 批量删除护栏。
// 返回 nil 表示放行；返回 error 表示触发冻结（调用方保持最后已知良好并回报）。
func checkDeleteGuard(cfg *Config, company string, oldDomains map[string]DomainState, newDomains map[string]bool) error {
	if len(oldDomains) == 0 {
		return nil // 首次同步无「删除」概念
	}
	deleted := 0
	for name := range oldDomains {
		if !newDomains[name] {
			deleted++
		}
	}
	threshold := cfg.DeleteGuardMin
	if ratio := int(float64(len(oldDomains)) * cfg.DeleteGuardRatio); ratio > threshold {
		threshold = ratio
	}
	if deleted <= threshold {
		return nil
	}
	// 解冻文件：运维确认后 touch <DATA_DIR>/ack-<公司名> 放行一次（用掉即删）
	ackPath := filepath.Join(cfg.DataDir, "ack-"+company)
	if _, err := os.Stat(ackPath); err == nil {
		_ = os.Remove(ackPath)
		logInfo("公司 %s 批量删除 %d 条已被 ack 文件放行", company, deleted)
		return nil
	}
	return fmt.Errorf("批量删除护栏触发：本轮将删除 %d 条域名（现有 %d 条，阈值 %d）。"+
		"确认无误后在前端机执行 touch %s 解冻", deleted, len(oldDomains), threshold, ackPath)
}

// reconcileCompany 完成一家公司的完整对账，返回结果（绝不 panic，错误都进 Result.Err）。
// activeCompanyDir 是 active release 里该公司的目录（用于复用未变化的证书文件）。
func reconcileCompany(cfg *Config, client *companyClient, prev *CompanyState, activeCompanyDir string) *CompanyResult {
	res := &CompanyResult{Company: client.cfg.Name}

	manifest, notModified, err := client.fetchManifest(cfg.ServerName, prev)
	if err != nil {
		res.Err = err
		return res
	}
	if notModified {
		logDebug("公司 %s manifest 304（版本 %s）", client.cfg.Name, prev.AppliedVersion)
		return res
	}
	if err := validateManifest(manifest); err != nil {
		res.Err = fmt.Errorf("manifest 校验失败（保持现状）: %w", err)
		return res
	}

	// 护栏：比对旧域名集合
	newSet := make(map[string]bool, len(manifest.Domains))
	for _, d := range manifest.Domains {
		newSet[d.Domain] = true
	}
	if err := checkDeleteGuard(cfg, client.cfg.Name, prev.Domains, newSet); err != nil {
		res.Err = err
		return res
	}

	// 找出需要下载的证书：新域名，或指纹有变化，或本地文件缺失
	type job struct{ d ManifestDomain }
	var jobs []job
	for _, d := range manifest.Domains {
		old, existed := prev.Domains[d.Domain]
		unchanged := existed && old.FullchainSha256 == d.FullchainSha256 && old.PrivkeySha256 == d.PrivkeySha256
		if unchanged && certFilesExist(activeCompanyDir, d.Domain) {
			continue
		}
		jobs = append(jobs, job{d})
	}

	// 并发下载（CERT_DOWNLOAD_CONCURRENCY 路），任何一张验不过 → 整单拒绝
	certs := make(map[string]CertPayload, len(jobs))
	var mu sync.Mutex     // 保护 certs/firstErr 的互斥锁（多 goroutine 并发写）
	var wg sync.WaitGroup // 等待全部下载完成
	var firstErr error
	sem := make(chan struct{}, max(1, cfg.CertConcurrency)) // 信号量限制并发数

	for _, j := range jobs {
		wg.Add(1)
		go func(d ManifestDomain) { // go 关键字 = 启动一个并发协程
			defer wg.Done()
			sem <- struct{}{}        // 占一个并发名额
			defer func() { <-sem }() // 干完释放

			payload, err := downloadAndVerifyCert(client, d)
			mu.Lock()
			defer mu.Unlock()
			if err != nil {
				if firstErr == nil {
					firstErr = err
				}
				return
			}
			certs[d.Domain] = payload
		}(j.d)
	}
	wg.Wait()
	if firstErr != nil {
		res.Err = fmt.Errorf("证书下载/校验失败（整单拒绝，保持现状）: %w", firstErr)
		return res
	}

	// 组装结果
	res.Changed = true
	res.Version = manifest.Version
	res.Services = manifest.Services
	res.Certs = certs
	res.Domains = make(map[string]DomainState, len(manifest.Domains))
	for _, d := range manifest.Domains {
		res.Domains[d.Domain] = DomainState{
			ServiceType:     d.ServiceType,
			FullchainSha256: d.FullchainSha256,
			PrivkeySha256:   d.PrivkeySha256,
		}
	}
	if len(jobs) > 0 {
		logInfo("公司 %s 拉取到新版本 %s：域名 %d 个，下载证书 %d 张",
			client.cfg.Name, manifest.Version, len(manifest.Domains), len(jobs))
	}
	return res
}

// downloadAndVerifyCert 下载一张证书并做三重校验：
// SHA-256 指纹与 manifest 一致 → PEM/x509 可解析 → 公私钥配对（tls.X509KeyPair 一并完成后两项）。
func downloadAndVerifyCert(client *companyClient, d ManifestDomain) (CertPayload, error) {
	fullchain, err := downloadCertFile(client, d.Domain, "fullchain.pem", d.FullchainSha256)
	if err != nil {
		return CertPayload{}, err
	}
	privkey, err := downloadCertFile(client, d.Domain, "privkey.pem", d.PrivkeySha256)
	if err != nil {
		return CertPayload{}, err
	}
	// tls.X509KeyPair：解析证书链与私钥并验证两者匹配——坏证书到不了 nginx 面前
	if _, err := tls.X509KeyPair(fullchain, privkey); err != nil {
		return CertPayload{}, fmt.Errorf("域名 %s 证书密码学校验失败: %w", d.Domain, err)
	}
	return CertPayload{Fullchain: fullchain, Privkey: privkey}, nil
}

func downloadCertFile(client *companyClient, domain, file, wantSha string) ([]byte, error) {
	code, body, err := client.get("/front-agent/cert/"+domain+"/"+file, nil)
	if err != nil {
		return nil, fmt.Errorf("下载 %s/%s 失败: %w", domain, file, err)
	}
	if code != http.StatusOK {
		return nil, fmt.Errorf("下载 %s/%s 返回状态码 %d", domain, file, code)
	}
	sum := sha256.Sum256(body)
	if hex.EncodeToString(sum[:]) != strings.ToLower(wantSha) {
		return nil, fmt.Errorf("%s/%s 内容指纹与 manifest 不一致（传输损坏或服务端不一致）", domain, file)
	}
	return body, nil
}

// certFilesExist 检查 active release 里该域名的两个证书文件是否都在（硬链接复用的前提）。
func certFilesExist(companyDir, domain string) bool {
	if companyDir == "" {
		return false
	}
	for _, f := range []string{"fullchain.pem", "privkey.pem"} {
		if _, err := os.Stat(filepath.Join(companyDir, "certs", domain, f)); err != nil {
			return false
		}
	}
	return true
}

// ---------- 小工具 ----------

func isHex64(s string) bool {
	if len(s) != 64 {
		return false
	}
	_, err := hex.DecodeString(s)
	return err == nil
}

func truncateString(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n]
}

// sortedKeys 返回 map 的有序键（渲染确定性的基础设施）。
func sortedKeys[M ~map[string]V, V any](m M) []string {
	keys := make([]string, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	return keys
}
