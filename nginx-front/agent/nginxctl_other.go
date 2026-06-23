//go:build !linux

package main

// nginxctl_other.go —— 非 Linux 平台的占位实现，让代码在 Windows/macOS 上能编译、能跑单测。
// 生产永远跑在 Linux 容器里，不会用到这里。

import "errors"

type unsupportedNginx struct{}

// NewNginxController 在非 Linux 平台返回不可用实现（仅为编译通过）。
func NewNginxController() NginxController {
	return unsupportedNginx{}
}

var errUnsupported = errors.New("非 Linux 平台不支持操作 nginx（仅容器内运行）")

func (unsupportedNginx) TestConfig(string) error { return errUnsupported }
func (unsupportedNginx) Reload() error           { return errUnsupported }
func (unsupportedNginx) ReopenLogs() error       { return errUnsupported }
