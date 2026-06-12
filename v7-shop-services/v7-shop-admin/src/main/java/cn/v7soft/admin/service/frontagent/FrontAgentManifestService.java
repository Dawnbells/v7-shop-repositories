package cn.v7soft.admin.service.frontagent;

import cn.v7soft.dao.entities.primary.FrontAgentReport;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import cn.v7soft.dao.enums.NginxConfigType;
import cn.v7soft.dao.repositories.primary.FrontAgentReportRepository;
import cn.v7soft.dao.repositories.primary.TopLevelDomainRepository;
import cn.v7soft.dao.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 前端机 agent 的 manifest 组装与「轮询即回报」落库。
 * <p>
 * manifest = 当前公司（由 FrontAgentInterceptor 按请求 Host 设置租户）所有有效域名的
 * 「域名 → 服务类型 + 证书指纹」清单 + 本实例各服务类型的 upstream 地址。
 * version 是 manifest 内容的 SHA-256，免维护自增版本号：内容相同则版本必相同（304 短路依据）。
 * <p>
 * 设计文档：docs/superpowers/specs/2026-06-12-nginx-config-refactor-design.md §4.4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FrontAgentManifestService {

    /**
     * 域名白名单：只允许小写字母/数字/点/连字符，首尾必须是字母或数字（防路径穿越的第一道闸）
     */
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9.-]{0,251}[a-z0-9])?$");

    private final TopLevelDomainRepository topLevelDomainRepository;
    private final FrontAgentReportRepository frontAgentReportRepository;
    private final FrontAgentProperties properties;
    private final CertFingerprintCache fingerprintCache;

    /**
     * 与全局 Jackson 配置隔离的专用 mapper：manifest 的字节序列参与版本号哈希，必须确定性，
     * 不能被全局序列化配置（non_null、日期格式等）变更影响。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 每租户一份 manifest 快照（多公司同实例时互不串扰）；key 为租户 ID（无租户上下文时为 -1）
     */
    private final ConcurrentHashMap<Long, ManifestSnapshot> snapshots = new ConcurrentHashMap<>();

    /**
     * manifest 快照：version 与 body 严格对应（version 即 body 内 services+domains 的内容指纹）
     */
    public record ManifestSnapshot(String version, String body, long builtAtMillis) {
    }

    /**
     * 获取当前公司的 manifest 快照（带短 TTL 缓存，吸收多台前端机的轮询）。
     */
    public ManifestSnapshot getManifest() {
        Long tenantKey = currentTenantKey();
        long now = System.currentTimeMillis();
        ManifestSnapshot cached = snapshots.get(tenantKey);
        if (cached != null && now - cached.builtAtMillis() < properties.getManifestCacheMillis()) {
            return cached;
        }
        synchronized (this) {
            cached = snapshots.get(tenantKey);
            if (cached != null && System.currentTimeMillis() - cached.builtAtMillis() < properties.getManifestCacheMillis()) {
                return cached;
            }
            ManifestSnapshot built = build();
            snapshots.put(tenantKey, built);
            return built;
        }
    }

    /**
     * 轮询即回报：agent 每次拉取 manifest 都携带「已应用版本 + 结果」，按 agentName upsert（兼作心跳）。
     * 回报失败绝不能影响 manifest 下发——agent 拿不到数据比后台少一条心跳记录严重得多。
     */
    public void report(String agentName, String appliedVersion, String status, String message) {
        try {
            String name = truncate(agentName.trim(), 100);
            FrontAgentReport report = frontAgentReportRepository.findByAgentName(name)
                    .orElseGet(() -> FrontAgentReport.builder().agentName(name).build());
            report.setAppliedVersion(truncate(appliedVersion, 80));
            report.setReportStatus(truncate(status, 16));
            report.setMessage(truncate(message, 1000));
            report.setReportedAt(LocalDateTime.now());
            frontAgentReportRepository.save(report);
        } catch (Exception e) {
            // 极端并发下两台同名 agent 首次上报可能触发唯一键冲突：本轮丢弃，15s 后下一轮自然补上
            log.error("front-agent 回报落库失败: agent={}", agentName, e);
        }
    }

    /**
     * 解析证书文件的安全路径；任何校验不通过返回 null（调用方按 404 处理）。
     * 三道闸：域名白名单正则 → 域名必须存在于当前公司名下（租户过滤自动施加）→ 归一化后必须仍在证书根目录内。
     */
    public Path resolveCertFile(String domain, String fileName) {
        if (domain == null) {
            return null;
        }
        String normalized = domain.trim().toLowerCase(Locale.ROOT);
        if (!DOMAIN_PATTERN.matcher(normalized).matches() || normalized.contains("..")) {
            return null;
        }
        List<TopLevelDomain> matches = topLevelDomainRepository.findValidByName(normalized);
        if (matches.isEmpty()) {
            return null;
        }
        TopLevelDomain found = matches.get(0);
        Path base = Paths.get(properties.getCertsDir()).toAbsolutePath().normalize();
        Path file = base.resolve(String.valueOf(found.getCompanyId())).resolve(normalized).resolve(fileName).normalize();
        if (!file.startsWith(base)) {
            // 纵深防御：正则已挡住穿越，这里再兜一层
            return null;
        }
        return Files.isRegularFile(file) ? file : null;
    }

    /**
     * 构建当前租户的 manifest。结构（字段顺序固定，保证哈希确定性）：
     * {"version":"sha256:..","services":{"NUXT_MALL":["host:port"]},"domains":[{domain,serviceType,fullchainSha256,privkeySha256}]}
     */
    private ManifestSnapshot build() {
        List<TopLevelDomain> domains = new ArrayList<>(topLevelDomainRepository.findAllAgentServableDomains());
        domains.sort(Comparator.comparing(TopLevelDomain::getName));

        List<Map<String, Object>> domainEntries = new ArrayList<>(domains.size());
        for (TopLevelDomain domain : domains) {
            // 与旧 NginxConfigWriter 的默认值保持一致：未指定类型按 THYMELEAF 处理
            NginxConfigType type = domain.getNginxConfigType() == null ? NginxConfigType.THYMELEAF : domain.getNginxConfigType();
            Path certDir = Paths.get(properties.getCertsDir(), String.valueOf(domain.getCompanyId()), domain.getName());
            String fullchainSha = fingerprintCache.sha256(certDir.resolve("fullchain.pem"));
            String privkeySha = fingerprintCache.sha256(certDir.resolve("privkey.pem"));
            if (fullchainSha == null || privkeySha == null) {
                // 证书目录不存在 = 不满足「有效域名」（正常绑定流程会先写占位证书，此处属异常残留）
                log.warn("manifest 跳过缺少证书文件的域名: {} ({})", domain.getName(), certDir);
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("domain", domain.getName());
            entry.put("serviceType", type.name());
            entry.put("fullchainSha256", fullchainSha);
            entry.put("privkeySha256", privkeySha);
            domainEntries.add(entry);
        }

        // TreeMap：服务类型按名称排序，保证序列化顺序确定
        Map<String, Object> services = new TreeMap<>();
        properties.getServices().forEach((type, addresses) -> {
            List<String> cleaned = addresses == null ? List.of()
                    : addresses.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (!cleaned.isEmpty()) {
                services.put(type.name(), cleaned);
            }
        });

        try {
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put("services", services);
            inner.put("domains", domainEntries);
            String innerJson = objectMapper.writeValueAsString(inner);
            String version = "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(innerJson.getBytes(StandardCharsets.UTF_8)));

            Map<String, Object> full = new LinkedHashMap<>();
            full.put("version", version);
            full.putAll(inner);
            return new ManifestSnapshot(version, objectMapper.writeValueAsString(full), System.currentTimeMillis());
        } catch (Exception e) {
            throw new IllegalStateException("构建 front-agent manifest 失败", e);
        }
    }

    /**
     * ConcurrentHashMap 不允许 null key：无租户上下文（理论上不会发生，拦截器保证）按 -1 处理
     */
    private Long currentTenantKey() {
        return Optional.ofNullable(TenantContext.getCurrentTenant()).orElse(-1L);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
