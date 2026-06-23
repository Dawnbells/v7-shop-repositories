//go:build !linux

package main

// disk_other.go —— 非 Linux 平台的磁盘探测桩（仅为本地开发/测试编译通过）。
// 返回 0 让调用方走保守兜底值；水位检查在探测失败时不冻结。

import "errors"

func diskUsage(string) (int64, int64, error) {
	return 0, 0, errors.New("非 Linux 平台不支持磁盘探测")
}

func diskTotalBytes(string) int64 { return 0 }
