package main

// helpers_test.go —— 测试共享基建：假 nginx 控制器、测试证书生成、模拟公司 API。

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/hex"
	"encoding/json"
	"encoding/pem"
	"math/big"
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"
	"time"
)

// fakeNginx 是 NginxController 的测试替身：记录调用，不碰真进程。
type fakeNginx struct {
	mu        sync.Mutex
	tested    []string
	reloads   int
	reopens   int
	testErr   error
	reloadErr error
}

func (f *fakeNginx) TestConfig(conf string) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.tested = append(f.tested, conf)
	return f.testErr
}

func (f *fakeNginx) Reload() error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.reloads++
	return f.reloadErr
}

func (f *fakeNginx) ReopenLogs() error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.reopens++
	return nil
}

// genCertPair 生成一对自签证书+私钥（PEM），供模拟公司 API 下发。
func genCertPair(t *testing.T, cn string) (certPEM, keyPEM []byte) {
	t.Helper()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("生成测试私钥失败: %v", err)
	}
	template := x509.Certificate{
		SerialNumber: big.NewInt(time.Now().UnixNano()),
		Subject:      pkix.Name{CommonName: cn},
		DNSNames:     []string{cn},
		NotBefore:    time.Now().Add(-time.Hour),
		NotAfter:     time.Now().AddDate(1, 0, 0),
		KeyUsage:     x509.KeyUsageDigitalSignature | x509.KeyUsageKeyEncipherment,
		ExtKeyUsage:  []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
	}
	der, err := x509.CreateCertificate(rand.Reader, &template, &template, &key.PublicKey, key)
	if err != nil {
		t.Fatalf("生成测试证书失败: %v", err)
	}
	keyDER, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		t.Fatalf("序列化测试私钥失败: %v", err)
	}
	certPEM = pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der})
	keyPEM = pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: keyDER})
	return certPEM, keyPEM
}

func sha256Hex(b []byte) string {
	sum := sha256.Sum256(b)
	return hex.EncodeToString(sum[:])
}

// fakeCompanyServer 模拟一家公司的 Java API：/front-agent/manifest 与证书下载。
type fakeCompanyServer struct {
	t        *testing.T
	token    string
	manifest Manifest
	// certs：域名 → [fullchain, privkey]
	certs map[string][2][]byte
	// always304：模拟 Java 在 appliedVersion 一致时的 304
	srv *httptest.Server
	// 记录收到的回报参数（轮询即回报的断言点）
	mu          sync.Mutex
	lastAgent   string
	lastApplied string
	lastStatus  string
	lastMessage string
}

func newFakeCompanyServer(t *testing.T, token string) *fakeCompanyServer {
	f := &fakeCompanyServer{t: t, token: token, certs: map[string][2][]byte{}}
	mux := http.NewServeMux()
	mux.HandleFunc("/front-agent/manifest", func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer "+f.token {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		f.mu.Lock()
		f.lastAgent = r.URL.Query().Get("agent")
		f.lastApplied = r.URL.Query().Get("appliedVersion")
		f.lastStatus = r.URL.Query().Get("status")
		f.lastMessage = r.URL.Query().Get("message")
		applied := f.lastApplied
		f.mu.Unlock()
		if applied != "" && applied == f.manifest.Version {
			w.WriteHeader(http.StatusNotModified)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(f.manifest)
	})
	mux.HandleFunc("/front-agent/cert/", func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer "+f.token {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		// 路径形如 /front-agent/cert/<domain>/<file>
		var domain, file string
		rest := r.URL.Path[len("/front-agent/cert/"):]
		for i := 0; i < len(rest); i++ {
			if rest[i] == '/' {
				domain, file = rest[:i], rest[i+1:]
				break
			}
		}
		pair, ok := f.certs[domain]
		if !ok {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		switch file {
		case "fullchain.pem":
			_, _ = w.Write(pair[0])
		case "privkey.pem":
			_, _ = w.Write(pair[1])
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	})
	f.srv = httptest.NewServer(mux)
	t.Cleanup(f.srv.Close)
	return f
}

// setDomain 给模拟服务器登记一个域名（生成证书并写进 manifest）。
func (f *fakeCompanyServer) setDomain(domain, serviceType string) {
	cert, key := genCertPair(f.t, domain)
	f.certs[domain] = [2][]byte{cert, key}
	f.manifest.Domains = append(f.manifest.Domains, ManifestDomain{
		Domain:          domain,
		ServiceType:     serviceType,
		FullchainSha256: sha256Hex(cert),
		PrivkeySha256:   sha256Hex(key),
	})
	f.refreshVersion()
}

func (f *fakeCompanyServer) refreshVersion() {
	// 测试里的版本号不需要与 Java 算法一致，只要随内容变化即可
	payload, _ := json.Marshal(struct {
		S map[string][]string
		D []ManifestDomain
	}{f.manifest.Services, f.manifest.Domains})
	f.manifest.Version = "sha256:" + sha256Hex(payload)
}

// companyClientFor 构造指向模拟服务器的 companyClient。
func (f *fakeCompanyServer) companyClientFor(t *testing.T, name string, cfg *Config) *companyClient {
	t.Helper()
	client, err := newCompanyClient(CompanyConfig{
		Name:    name,
		BaseURL: f.srv.URL,
		Token:   f.token,
	}, cfg)
	if err != nil {
		t.Fatalf("构造公司客户端失败: %v", err)
	}
	return client
}

// testConfig 返回一份指向临时目录的最小配置。
func testConfig(t *testing.T, dataDir string) *Config {
	t.Helper()
	return &Config{
		ServerName:        "test-front",
		PollInterval:      time.Second,
		DataDir:           dataDir,
		NginxConf:         "",
		LogMaxBytes:       256 * 1024 * 1024,
		LogKeep:           4,
		Tune:              map[string]string{},
		DeleteGuardMin:    100,
		DeleteGuardRatio:  0.30,
		ReloadMinInterval: 0, // 测试默认关防抖
		CertConcurrency:   8,
		HTTPTimeout:       5 * time.Second,
		ReleaseKeep:       5,
	}
}
