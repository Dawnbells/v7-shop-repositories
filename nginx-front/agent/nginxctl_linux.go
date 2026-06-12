//go:build linux

package main

// nginxctl_linux.go —— NginxController 的生产实现（仅 Linux 编译，见首行 build 标签）。
// Windows 上跑 `go test` 时本文件被排除，测试用 fakeNginx 替身。

import (
	"fmt"
	"os/exec"
	"syscall"
)

type realNginx struct{}

// NewNginxController 返回生产实现。
func NewNginxController() NginxController {
	return realNginx{}
}

func (realNginx) TestConfig(confPath string) error {
	// CombinedOutput 同时捕获 stdout+stderr：nginx -t 的诊断信息都在 stderr
	out, err := exec.Command("nginx", "-t", "-c", confPath).CombinedOutput()
	if err != nil {
		return fmt.Errorf("nginx -t 校验失败: %v\n%s", err, string(out))
	}
	return nil
}

func (realNginx) Reload() error {
	// 共享 pid 命名空间里 nginx master 就是 PID 1。
	// 信号发送失败（如 nginx 容器被重建导致命名空间失效）由调用方决定自杀重启（自愈）。
	if err := syscall.Kill(1, syscall.SIGHUP); err != nil {
		return fmt.Errorf("向 nginx master(PID 1) 发 SIGHUP 失败: %w", err)
	}
	return nil
}

func (realNginx) ReopenLogs() error {
	if err := syscall.Kill(1, syscall.SIGUSR1); err != nil {
		return fmt.Errorf("向 nginx master(PID 1) 发 SIGUSR1 失败: %w", err)
	}
	return nil
}
