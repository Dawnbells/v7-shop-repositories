//go:build linux

package main

// disk_linux.go —— 磁盘容量探测的 Linux 实现（statfs 系统调用）。

import "syscall"

// diskUsage 返回 path 所在文件系统的 (总容量, 可用容量) 字节数。
func diskUsage(path string) (total int64, avail int64, err error) {
	var st syscall.Statfs_t
	if err := syscall.Statfs(path, &st); err != nil {
		return 0, 0, err
	}
	bsize := int64(st.Bsize)
	return int64(st.Blocks) * bsize, int64(st.Bavail) * bsize, nil
}

// diskTotalBytes 返回数据卷所在盘的总容量（探测失败返回 0，由调用方兜底）。
func diskTotalBytes(path string) int64 {
	total, _, err := diskUsage(path)
	if err != nil {
		return 0
	}
	return total
}
