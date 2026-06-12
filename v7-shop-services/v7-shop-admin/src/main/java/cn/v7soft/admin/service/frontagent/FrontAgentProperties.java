package cn.v7soft.admin.service.frontagent;

import cn.v7soft.dao.enums.NginxConfigType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 前端机 agent 接口配置（application.front-agent.*）。
 * <p>
 * 设计文档：docs/superpowers/specs/2026-06-12-nginx-config-refactor-design.md §4.4
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "application.front-agent")
public class FrontAgentProperties {

    /**
     * 静态 Bearer token 列表（环境变量 FRONT_AGENT_TOKENS，逗号分隔）。
     * 支持多值并存以便双 token 无缝轮换；为空 = 接口整体禁用（一律 401）。
     */
    private List<String> tokens = new ArrayList<>();

    /**
     * 各服务类型的 upstream 地址（host:port，可多个）。
     * 前端 agent 据此渲染 nginx 的 upstream 块；未配置地址的类型，其域名无法被路由。
     */
    private Map<NginxConfigType, List<String>> services = new LinkedHashMap<>();

    /**
     * 证书根目录，约定布局：{certsDir}/{companyId}/{domain}/fullchain.pem|privkey.pem
     */
    private String certsDir = "/www/certs/";

    /**
     * manifest 内存快照的有效期（毫秒）：吸收多台前端机的轮询，避免重复构建
     */
    private long manifestCacheMillis = 3000;
}
