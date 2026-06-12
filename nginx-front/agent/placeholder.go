package main

// placeholder.go —— 占位证书生成。
//
// 通用 server 的证书 map default 指向 <DATA_DIR>/placeholder/，
// 未知 SNI 用它完成握手后由 `$backend=""` 规则返回 444。
// agent 启动时确保占位证书存在（nginx 的变量证书路径是握手期才读文件，
// 所以 nginx 可以先启动、agent 随后补上占位证书，首启顺序无依赖）。
// CN 使用 placeholder.invalid，与 Java 侧 PlaceholderCertHolder 的判定习惯一致。

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"math/big"
	"os"
	"path/filepath"
	"time"
)

// ensurePlaceholderCert 确保占位证书存在；已存在则什么都不做。
func ensurePlaceholderCert(dataDir string) error {
	dir := filepath.Join(dataDir, "placeholder")
	certPath := filepath.Join(dir, "fullchain.pem")
	keyPath := filepath.Join(dir, "privkey.pem")
	if fileExists(certPath) && fileExists(keyPath) {
		return nil
	}
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return err
	}

	// 生成 2048 位 RSA 私钥 + 自签证书（有效期 10 年，反正浏览器永远不该信它）
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return err
	}
	template := x509.Certificate{
		SerialNumber: big.NewInt(time.Now().UnixNano()),
		Subject:      pkix.Name{CommonName: "placeholder.invalid"},
		DNSNames:     []string{"placeholder.invalid"},
		NotBefore:    time.Now().Add(-time.Hour),
		NotAfter:     time.Now().AddDate(10, 0, 0),
		KeyUsage:     x509.KeyUsageDigitalSignature | x509.KeyUsageKeyEncipherment,
		ExtKeyUsage:  []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
	}
	der, err := x509.CreateCertificate(rand.Reader, &template, &template, &key.PublicKey, key)
	if err != nil {
		return err
	}

	certPEM := pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der})
	keyPEM := pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: mustMarshalPKCS8(key)})
	if err := os.WriteFile(certPath, certPEM, 0o644); err != nil {
		return err
	}
	if err := os.WriteFile(keyPath, keyPEM, 0o600); err != nil {
		return err
	}
	logInfo("已生成占位证书: %s", dir)
	return nil
}

func mustMarshalPKCS8(key *rsa.PrivateKey) []byte {
	der, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		// 标准库对合法 RSA 私钥不会失败；真失败属编程错误，直接崩溃暴露
		panic(err)
	}
	return der
}

func fileExists(path string) bool {
	_, err := os.Stat(path)
	return err == nil
}
