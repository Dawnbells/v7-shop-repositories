package main

// nginxctl.go —— 与 nginx 进程交互的抽象。
//
// 为什么抽象成接口：单元测试里不能真的去 exec nginx / 发信号，
// 测试用 fakeNginx 替身验证「调用了什么」，生产用 realNginx（见 nginxctl_linux.go）。
//
// 生产实现的两个事实：
//   1. agent 容器与 nginx 容器共享 pid 命名空间（compose 的 pid: "service:nginx"），
//      nginx master 是该命名空间的 PID 1，发 SIGHUP 即 graceful reload，SIGUSR1 重开日志。
//   2. agent 镜像基于同一个 NGINX_IMAGE 构建，容器里有同版本 nginx 二进制，
//      `nginx -t -c <shadow配置>` 的校验行为与数据面完全一致。

// NginxController 是 agent 对 nginx 的全部操作面。
type NginxController interface {
	// TestConfig 用 nginx -t 校验指定主配置；失败返回含 nginx 输出的 error
	TestConfig(confPath string) error
	// Reload 向 nginx master 发 SIGHUP（graceful reload，不断连接）
	Reload() error
	// ReopenLogs 向 nginx master 发 SIGUSR1（日志轮转后重开文件句柄）
	ReopenLogs() error
}
