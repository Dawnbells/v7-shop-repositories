package main

// render.go —— 配置片段渲染、release 构建、内容寻址、原子切换。
//
// 核心机制（设计文档 §4.5）：
//   - release 不可变：每次变更产出一个全新目录 releases/<时间戳>-<哈希前8位>/，
//     `active` 软链指向当前生效的 release，切换 = 原子换软链；
//   - 内容寻址幂等收敛：release 内容哈希写在 <release>/.content-hash；
//     新渲染结果哈希 == active 的哈希 → 不建目录、不切换、不 reload（重启/状态丢失自愈）；
//   - 影子校验：把主配置里的 active 路径替换成候选 release 路径生成影子配置，
//     用同版本 nginx -t 校验通过才允许切换；
//   - retention：保留最近 N 个 release，永不删除 active 指向的那个。

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"
)

// legacyImageCacheTypes：走 /static/ 图片缓存（IMAGE_CACHE）的老服务类型。
// 其余类型（NUXT_MALL 等）的 /static/ 缓存档位是 off，不误吃 30 天缓存。
var legacyImageCacheTypes = map[string]bool{"THYMELEAF": true, "VIKE": true}

// CompanyFiles 是一家公司渲染出的全部配置片段（文件名 → 内容）。
type CompanyFiles map[string]string

// renderCompanyFiles 把一家公司的期望状态渲染成 nginx 片段。
// 确定性要求：相同输入必须产出字节级相同的输出（域名排序、服务类型排序），
// 这是内容寻址（哈希比对）成立的前提。
func renderCompanyFiles(dataDir, company string, services map[string][]string, domains map[string]DomainState) CompanyFiles {
	activeBase := filepath.ToSlash(filepath.Join(dataDir, "active"))
	header := "# 本文件由 v7front-agent 渲染，勿手工编辑\n"

	var routes, certs, caches strings.Builder
	routes.WriteString(header)
	certs.WriteString(header)
	caches.WriteString(header)

	for _, name := range sortedKeys(domains) {
		d := domains[name]
		// 前导点写法 ".example.com" 在 nginx hostnames map 里同时匹配
		// example.com 与 *.example.com（与旧模板 server_name %s *.%s 语义一致）
		routes.WriteString(fmt.Sprintf(".%s  %s;\n", name, upstreamName(company, d.ServiceType)))
		certs.WriteString(fmt.Sprintf(".%s  %s/%s/certs/%s;\n", name, activeBase, company, name))
		if legacyImageCacheTypes[d.ServiceType] {
			caches.WriteString(fmt.Sprintf(".%s  IMAGE_CACHE;\n", name))
		}
	}

	var upstreams strings.Builder
	upstreams.WriteString(header)
	for _, t := range sortedKeys(services) {
		upstreams.WriteString(fmt.Sprintf("upstream %s {\n", upstreamName(company, t)))
		for _, addr := range services[t] {
			upstreams.WriteString(fmt.Sprintf("    server %s;\n", addr))
		}
		// keepalive 复用到上游的连接（locations 已配 proxy_http_version 1.1 + Connection ""）
		upstreams.WriteString("    keepalive 32;\n}\n")
	}

	return CompanyFiles{
		"routes.map":     routes.String(),
		"certs.map":      certs.String(),
		"caches.map":     caches.String(),
		"upstreams.conf": upstreams.String(),
	}
}

// upstreamName 生成 nginx upstream 标识符：<公司名>_<服务类型>（两段都过了白名单正则）。
func upstreamName(company, serviceType string) string {
	return company + "_" + serviceType
}

// ---------- release 构建 ----------

// releaseInput 是构建一个 release 的全部素材。
type releaseInput struct {
	// SystemFiles：_system/ 下的调优配置（tuning.go 渲染）
	SystemFiles map[string]string
	// Companies：每家公司的片段 + 证书来源
	Companies []releaseCompany
}

type releaseCompany struct {
	Name  string
	Files CompanyFiles
	// Domains：该公司全部域名（用于决定证书文件清单）
	Domains map[string]DomainState
	// NewCerts：本轮新下载的证书内容；不在其中的域名从 activeDir 硬链接复用
	NewCerts map[string]CertPayload
}

// buildRelease 在 releases/ 下构建候选目录并计算内容哈希。
// 返回 (候选目录路径, 内容哈希, error)。调用方负责后续的幂等比对/校验/切换/清理。
//
// 用命名返回值 + defer：任何中途错误都就地清掉 .building-* 临时目录，
// 不依赖调用方（调用方拿到的是空路径，且 defer 注册在错误检查之后，兜不住）。
// pruneReleases 又故意跳过点开头目录，泄漏的临时目录永远不会被回收——必须在这里自清。
func buildRelease(dataDir string, in releaseInput, activeDir string) (releasePath string, contentHash string, retErr error) {
	releasesDir := filepath.Join(dataDir, "releases")
	if err := os.MkdirAll(releasesDir, 0o755); err != nil {
		return "", "", err
	}
	// 以 .building- 前缀创建，nginx include 的 glob 是 active/ 下的路径，不会误读
	building, err := os.MkdirTemp(releasesDir, ".building-")
	if err != nil {
		return "", "", err
	}
	// 仅错误路径清理；成功路径由 finalizeRelease 先 rename 走，这里的 RemoveAll 落空无害
	defer func() {
		if retErr != nil {
			_ = os.RemoveAll(building)
		}
	}()

	// _system 调优配置
	sysDir := filepath.Join(building, "_system")
	if err := os.MkdirAll(sysDir, 0o755); err != nil {
		return "", "", err
	}
	for _, name := range sortedKeys(in.SystemFiles) {
		if err := os.WriteFile(filepath.Join(sysDir, name), []byte(in.SystemFiles[name]), 0o644); err != nil {
			return "", "", err
		}
	}

	// 各公司片段与证书
	for _, c := range in.Companies {
		companyDir := filepath.Join(building, c.Name)
		if err := os.MkdirAll(companyDir, 0o755); err != nil {
			return "", "", err
		}
		for _, name := range sortedKeys(c.Files) {
			if err := os.WriteFile(filepath.Join(companyDir, name), []byte(c.Files[name]), 0o644); err != nil {
				return "", "", err
			}
		}
		for _, domain := range sortedKeys(c.Domains) {
			certDir := filepath.Join(companyDir, "certs", domain)
			if err := os.MkdirAll(certDir, 0o755); err != nil {
				return "", "", err
			}
			if payload, ok := c.NewCerts[domain]; ok {
				// 新证书：写内容（私钥 0600）
				if err := os.WriteFile(filepath.Join(certDir, "fullchain.pem"), payload.Fullchain, 0o644); err != nil {
					return "", "", err
				}
				if err := os.WriteFile(filepath.Join(certDir, "privkey.pem"), payload.Privkey, 0o600); err != nil {
					return "", "", err
				}
			} else {
				// 未变化：从 active release 硬链接（零拷贝零空间）；跨设备等失败时退化为拷贝
				src := filepath.Join(activeDir, c.Name, "certs", domain)
				for _, f := range []string{"fullchain.pem", "privkey.pem"} {
					if err := linkOrCopy(filepath.Join(src, f), filepath.Join(certDir, f)); err != nil {
						return "", "", fmt.Errorf("复用公司 %s 域名 %s 的证书失败: %w", c.Name, domain, err)
					}
				}
			}
		}
	}

	hash, err := hashTree(building)
	if err != nil {
		return "", "", err
	}
	// 哈希写进 release 自身（不参与哈希计算——hashTree 跳过点开头文件），
	// 这样 state.json 丢失时也能从 active/.content-hash 恢复比对基准
	if err := os.WriteFile(filepath.Join(building, ".content-hash"), []byte(hash), 0o644); err != nil {
		return "", "", err
	}
	return building, hash, nil
}

// finalizeRelease 把候选目录改名为正式 release 名：<UTC时间戳>-<哈希前8位>。
func finalizeRelease(building, hash string) (string, error) {
	final := filepath.Join(filepath.Dir(building),
		time.Now().UTC().Format("20060102T150405")+"-"+hash[:8])
	if err := os.Rename(building, final); err != nil {
		return "", err
	}
	return final, nil
}

// hashTree 计算目录树的内容哈希：按相对路径排序，逐个混入「路径 + 内容」。
// 点开头文件（如 .content-hash）跳过。两棵内容相同的树必然得到相同哈希。
func hashTree(root string) (string, error) {
	var files []string
	err := filepath.Walk(root, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}
		if info.IsDir() || strings.HasPrefix(info.Name(), ".") {
			return nil
		}
		files = append(files, path)
		return nil
	})
	if err != nil {
		return "", err
	}
	sort.Strings(files)

	h := sha256.New()
	for _, f := range files {
		rel, err := filepath.Rel(root, f)
		if err != nil {
			return "", err
		}
		// 路径统一成正斜杠，保证跨平台哈希一致（测试在 Windows 跑，生产在 Linux）
		h.Write([]byte(filepath.ToSlash(rel)))
		h.Write([]byte{0}) // 路径与内容之间的分隔符，防止拼接歧义
		content, err := os.ReadFile(f)
		if err != nil {
			return "", err
		}
		h.Write(content)
		h.Write([]byte{0})
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}

// readActiveContentHash 读取 active release 的内容哈希（无 active 或无哈希文件返回空串）。
func readActiveContentHash(dataDir string) string {
	data, err := os.ReadFile(filepath.Join(dataDir, "active", ".content-hash"))
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(data))
}

// switchActive 原子切换 active 软链：先建临时软链，再 rename 覆盖。
// POSIX rename 对同目录路径是原子的——任何时刻 active 要么指旧、要么指新，没有中间态。
func switchActive(dataDir, releaseDir string) error {
	tmp := filepath.Join(dataDir, ".active-next")
	_ = os.Remove(tmp)
	if err := os.Symlink(releaseDir, tmp); err != nil {
		return err
	}
	active := filepath.Join(dataDir, "active")
	if err := os.Rename(tmp, active); err != nil {
		// Windows 的 rename 无法覆盖已存在的软链（生产 Linux 不会走到这里）。
		// 降级为「先删后改名」——仅本地开发/测试路径，损失原子性可接受。
		if removeErr := os.Remove(active); removeErr != nil {
			return err
		}
		return os.Rename(tmp, active)
	}
	return nil
}

// activeTarget 返回 active 软链当前指向的 release 目录（无 active 返回空串）。
func activeTarget(dataDir string) string {
	target, err := os.Readlink(filepath.Join(dataDir, "active"))
	if err != nil {
		return ""
	}
	return target
}

// writeShadowConf 生成影子主配置：把真实主配置里的 active 路径替换为候选 release 路径。
// 用它跑 nginx -t，校验的就是「切换之后的世界」。
func writeShadowConf(nginxConf, dataDir, candidateDir, outPath string) error {
	content, err := os.ReadFile(nginxConf)
	if err != nil {
		return fmt.Errorf("读取 nginx 主配置失败: %w", err)
	}
	activePath := filepath.ToSlash(filepath.Join(dataDir, "active"))
	shadow := strings.ReplaceAll(string(content), activePath, filepath.ToSlash(candidateDir))
	return os.WriteFile(outPath, []byte(shadow), 0o644)
}

// pruneReleases 删除多余的旧 release：按目录名排序保留最新 keep 个；
// active 指向的目录无论多旧都不删（铁律）。
func pruneReleases(dataDir string, keep int) {
	releasesDir := filepath.Join(dataDir, "releases")
	entries, err := os.ReadDir(releasesDir)
	if err != nil {
		return
	}
	active := filepath.Base(activeTarget(dataDir))
	var names []string
	for _, e := range entries {
		// 跳过构建中的临时目录
		if e.IsDir() && !strings.HasPrefix(e.Name(), ".") {
			names = append(names, e.Name())
		}
	}
	sort.Strings(names) // 名字以 UTC 时间戳开头，字典序即时间序
	if len(names) <= keep {
		return
	}
	for _, name := range names[:len(names)-keep] {
		if name == active {
			continue
		}
		if err := os.RemoveAll(filepath.Join(releasesDir, name)); err != nil {
			logError("清理旧 release %s 失败: %v", name, err)
		} else {
			logDebug("已清理旧 release %s", name)
		}
	}
}

// linkOrCopy 优先硬链接（同卷零拷贝），失败退化为内容拷贝。
func linkOrCopy(src, dst string) error {
	if err := os.Link(src, dst); err == nil {
		return nil
	}
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()
	info, err := in.Stat()
	if err != nil {
		return err
	}
	out, err := os.OpenFile(dst, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, info.Mode().Perm())
	if err != nil {
		return err
	}
	defer out.Close()
	_, err = io.Copy(out, in)
	return err
}
