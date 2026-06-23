package main

// state.go —— agent 的持久状态（state.json）。
//
// 状态文件是「重启零下载」的关键：记录每家公司已应用的 manifest 版本与域名清单，
// 重启后带着 appliedVersion 轮询即可拿到 304。状态丢失也能自愈（内容寻址幂等收敛，
// 见 render.go 的 .content-hash 机制），所以这里只追求简单：整个文件原子重写。

import (
	"encoding/json"
	"os"
	"path/filepath"
	"time"
)

// DomainState 记录一个已应用域名的关键信息（用于增量比对与跨公司冲突检查）。
type DomainState struct {
	ServiceType     string `json:"serviceType"`
	FullchainSha256 string `json:"fullchainSha256"`
	PrivkeySha256   string `json:"privkeySha256"`
}

// CompanyState 是一家公司的同步状态。
type CompanyState struct {
	// AppliedVersion：最近一次成功应用的 manifest 版本（轮询时回传换 304）
	AppliedVersion string `json:"appliedVersion"`
	// Services：该版本的服务类型 → upstream 地址列表
	Services map[string][]string `json:"services"`
	// Domains：该版本的域名清单（域名 → 类型与证书指纹）
	Domains map[string]DomainState `json:"domains"`
	// LastStatus / LastError：上一轮的处理结果，下一次轮询作为回报参数带给 Java
	LastStatus string `json:"lastStatus"` // "ok" 或 "error"
	LastError  string `json:"lastError,omitempty"`
	// UpdatedAt：最近一次成功应用时间（仅供人工排障查看）
	UpdatedAt time.Time `json:"updatedAt"`
}

// State 是 state.json 的顶层结构。
type State struct {
	Companies map[string]*CompanyState `json:"companies"`
	// ActiveContentHash：当前 active release 的内容哈希（内容寻址幂等收敛的比对基准）
	ActiveContentHash string `json:"activeContentHash"`
	// LastReloadAt：最近一次 reload 时间（防抖判断）
	LastReloadAt time.Time `json:"lastReloadAt"`
}

func statePath(dataDir string) string {
	return filepath.Join(dataDir, "state.json")
}

// LoadState 读取 state.json；文件缺失/损坏都返回全新空状态（自愈路径，不报错）。
func LoadState(dataDir string) *State {
	s := &State{Companies: map[string]*CompanyState{}}
	data, err := os.ReadFile(statePath(dataDir))
	if err != nil {
		return s
	}
	if err := json.Unmarshal(data, s); err != nil {
		logError("state.json 损坏，按空状态自愈（将全量拉取后做内容哈希收敛）: %v", err)
		return &State{Companies: map[string]*CompanyState{}}
	}
	if s.Companies == nil {
		s.Companies = map[string]*CompanyState{}
	}
	return s
}

// Save 原子写入 state.json：先写临时文件再 rename（POSIX rename 同目录原子），
// 任何时刻磁盘上要么是旧的完整文件、要么是新的完整文件，绝无半写状态。
func (s *State) Save(dataDir string) error {
	data, err := json.MarshalIndent(s, "", "  ")
	if err != nil {
		return err
	}
	tmp := statePath(dataDir) + ".tmp"
	if err := os.WriteFile(tmp, data, 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, statePath(dataDir))
}

// Company 取（或初始化）一家公司的状态。
func (s *State) Company(name string) *CompanyState {
	if cs, ok := s.Companies[name]; ok {
		return cs
	}
	cs := &CompanyState{Domains: map[string]DomainState{}, Services: map[string][]string{}}
	s.Companies[name] = cs
	return cs
}
