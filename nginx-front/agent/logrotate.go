package main

// logrotate.go —— nginx 日志封顶轮转 + 磁盘水位保护。
//
// 轮转流程（设计文档 §4.5「日志封顶」）：
//   单文件超过 NGINX_LOG_MAX_FILE → 改名为 <名字>.<时间戳> → 对 nginx 发 USR1 重开句柄
//   → gzip 压缩改名后的文件 → 只保留最近 NGINX_LOG_KEEP 份 .gz。
//   硬上限 ≈ max_file × (keep+1) × 日志路数，计入磁盘预算。
//
// 磁盘水位（设计文档 §4.5「磁盘水位保护」）：
//   数据卷可用空间 < max(2G, 总容量 5%) → 本轮冻结新证书/新 release 写入并回报 error，
//   日志轮转照常运行以释放空间，水位恢复自动解冻。

import (
	"compress/gzip"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"time"
)

// rotateLogsIfNeeded 检查并轮转超限的日志文件；任何错误只记日志不中断主循环。
func rotateLogsIfNeeded(cfg *Config, nc NginxController) {
	logsDir := filepath.Join(cfg.DataDir, "logs")
	rotated := false
	for _, name := range []string{"access.log", "error.log"} {
		path := filepath.Join(logsDir, name)
		info, err := os.Stat(path)
		if err != nil || info.Size() < cfg.LogMaxBytes {
			continue
		}
		ts := time.Now().UTC().Format("20060102T150405")
		rotatedPath := path + "." + ts
		if err := os.Rename(path, rotatedPath); err != nil {
			logError("轮转日志 %s 失败: %v", name, err)
			continue
		}
		rotated = true
		logInfo("日志 %s 达到 %d 字节，已轮转", name, info.Size())
	}
	if !rotated {
		return
	}
	// USR1 让 nginx 重开日志文件（开始写全新的 access.log/error.log）
	if err := nc.ReopenLogs(); err != nil {
		logError("通知 nginx 重开日志失败: %v", err)
	}
	// 压缩所有未压缩的轮转文件，并按保留份数清理
	for _, name := range []string{"access.log", "error.log"} {
		compressRotated(logsDir, name)
		pruneRotated(logsDir, name, cfg.LogKeep)
	}
}

// compressRotated 把 <name>.<时间戳>（未压缩）逐个 gzip 成 .gz 后删除原文件。
func compressRotated(logsDir, name string) {
	matches, _ := filepath.Glob(filepath.Join(logsDir, name+".*"))
	for _, m := range matches {
		if filepath.Ext(m) == ".gz" {
			continue
		}
		if err := gzipFile(m); err != nil {
			logError("压缩 %s 失败: %v", m, err)
			continue
		}
		_ = os.Remove(m)
	}
}

// pruneRotated 只保留最近 keep 份 .gz（文件名含 UTC 时间戳，字典序即时间序）。
func pruneRotated(logsDir, name string, keep int) {
	matches, _ := filepath.Glob(filepath.Join(logsDir, name+".*.gz"))
	sort.Strings(matches)
	if len(matches) <= keep {
		return
	}
	for _, m := range matches[:len(matches)-keep] {
		if err := os.Remove(m); err != nil {
			logError("清理旧日志 %s 失败: %v", m, err)
		}
	}
}

// gzipFile 把 path 压缩为 path+".gz"。
// 原子化：先写 .gz.tmp，全部成功后 rename 到 .gz；任何错误删临时文件后返回。
// 这样 pruneRotated 的 glob `*.gz` 永远只看到完整文件——杜绝半成品 .gz 因时间戳最新
// 被当成「最新有效份」保留、把真正有效的旧日志挤掉（评审 bug_006）。
func gzipFile(path string) (retErr error) {
	in, err := os.Open(path)
	if err != nil {
		return err
	}
	defer in.Close()

	tmp := path + ".gz.tmp"
	out, err := os.Create(tmp)
	if err != nil {
		return err
	}
	// 错误时清掉半成品临时文件，绝不留下能被 glob 命中的 .gz
	defer func() {
		if retErr != nil {
			out.Close()
			_ = os.Remove(tmp)
		}
	}()

	gw := gzip.NewWriter(out)
	if _, err := io.Copy(gw, in); err != nil {
		return err
	}
	if err := gw.Close(); err != nil { // Close 才会把压缩尾块刷出去，必须检查错误
		return err
	}
	if err := out.Close(); err != nil { // 显式 Close 以捕获最终 flush 错误
		return err
	}
	return os.Rename(tmp, path+".gz") // 原子落定：要么完整 .gz，要么没有
}

// checkDiskWatermark 检查数据卷水位。
// 返回 nil = 水位正常；返回 error = 低水位（错误消息会回报给各公司 Java）。
func checkDiskWatermark(dataDir string) error {
	total, avail, err := diskUsage(dataDir)
	if err != nil || total == 0 {
		return nil // 探测失败不冻结（宁可放行，避免误伤）
	}
	watermark := maxInt64(2*1024*1024*1024, total/20) // max(2G, 5%)
	if avail < watermark {
		return fmt.Errorf("磁盘水位保护触发：可用 %s < 阈值 %s，冻结新证书/新 release 写入",
			formatSize(avail), formatSize(watermark))
	}
	return nil
}
