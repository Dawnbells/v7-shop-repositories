package main

// logrotate_test.go —— 日志轮转与保留份数测试。

import (
	"compress/gzip"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestRotateLogsCompressesAndSignals(t *testing.T) {
	dataDir := t.TempDir()
	logsDir := filepath.Join(dataDir, "logs")
	if err := os.MkdirAll(logsDir, 0o755); err != nil {
		t.Fatal(err)
	}
	cfg := testConfig(t, dataDir)
	cfg.LogMaxBytes = 10 // 极小阈值便于触发

	if err := os.WriteFile(filepath.Join(logsDir, "access.log"), []byte(strings.Repeat("x", 100)), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(logsDir, "error.log"), []byte("ok"), 0o644); err != nil {
		t.Fatal(err)
	}

	nc := &fakeNginx{}
	rotateLogsIfNeeded(cfg, nc)

	if nc.reopens != 1 {
		t.Fatalf("轮转后必须发一次 USR1 重开句柄, got %d", nc.reopens)
	}
	if _, err := os.Stat(filepath.Join(logsDir, "access.log")); !os.IsNotExist(err) {
		t.Fatalf("原 access.log 应已被改名")
	}
	gz, _ := filepath.Glob(filepath.Join(logsDir, "access.log.*.gz"))
	if len(gz) != 1 {
		t.Fatalf("轮转文件应被 gzip, got %v", gz)
	}
	// 评审 bug_006：原子化后不得残留 .gz.tmp 半成品
	tmp, _ := filepath.Glob(filepath.Join(logsDir, "*.gz.tmp"))
	if len(tmp) != 0 {
		t.Fatalf("不应残留 .gz.tmp 半成品: %v", tmp)
	}
	// error.log 未超限不动
	if _, err := os.Stat(filepath.Join(logsDir, "error.log")); err != nil {
		t.Fatalf("未超限的 error.log 不应被动")
	}
}

// 评审 bug_006：gzipFile 成功后只留完整 .gz，绝无 .gz.tmp；产物可被 gunzip 还原。
func TestGzipFileAtomicAndValid(t *testing.T) {
	dir := t.TempDir()
	src := filepath.Join(dir, "x.log.20260101T000000")
	want := strings.Repeat("hello-log\n", 1000)
	if err := os.WriteFile(src, []byte(want), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := gzipFile(src); err != nil {
		t.Fatalf("gzipFile 失败: %v", err)
	}
	if _, err := os.Stat(src + ".gz.tmp"); !os.IsNotExist(err) {
		t.Fatalf("不应残留 .gz.tmp")
	}
	f, err := os.Open(src + ".gz")
	if err != nil {
		t.Fatalf("应生成 .gz: %v", err)
	}
	defer f.Close()
	gr, err := gzip.NewReader(f)
	if err != nil {
		t.Fatalf(".gz 不可解压（半成品）: %v", err)
	}
	got, _ := io.ReadAll(gr)
	if string(got) != want {
		t.Fatalf("解压内容不一致")
	}
}

func TestPruneRotatedKeepsNewest(t *testing.T) {
	dataDir := t.TempDir()
	logsDir := filepath.Join(dataDir, "logs")
	if err := os.MkdirAll(logsDir, 0o755); err != nil {
		t.Fatal(err)
	}
	for i := 0; i < 7; i++ {
		name := filepath.Join(logsDir, "access.log.2026010"+string(rune('1'+i))+"T000000.gz")
		if err := os.WriteFile(name, []byte("z"), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	pruneRotated(logsDir, "access.log", 4)
	left, _ := filepath.Glob(filepath.Join(logsDir, "access.log.*.gz"))
	if len(left) != 4 {
		t.Fatalf("应只保留 4 份, got %d", len(left))
	}
	// 留下来的必须是最新的（字典序最大的 4 个）
	for _, f := range left {
		if strings.Contains(f, "20260101") || strings.Contains(f, "20260102") || strings.Contains(f, "20260103") {
			t.Fatalf("应删除最旧的份: %v", left)
		}
	}
}
