package main

// logrotate_test.go —— 日志轮转与保留份数测试。

import (
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
	// error.log 未超限不动
	if _, err := os.Stat(filepath.Join(logsDir, "error.log")); err != nil {
		t.Fatalf("未超限的 error.log 不应被动")
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
