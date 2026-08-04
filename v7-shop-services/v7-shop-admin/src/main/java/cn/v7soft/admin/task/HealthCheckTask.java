package cn.v7soft.admin.task;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.event.ServerHealthNotificationEvent;
import cn.v7soft.admin.service.IFrontServerService;
import cn.v7soft.admin.service.ISubDomainService;
import cn.v7soft.admin.service.dns.IDnsService;
import cn.v7soft.admin.service.dns.impl.DnsServiceFactory;
import cn.v7soft.admin.service.dto.SubDomainDto;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.CloudPlatformAccount;
import cn.v7soft.dao.entities.primary.DnsSwitchLog;
import cn.v7soft.dao.entities.primary.FrontServer;
import cn.v7soft.dao.entities.primary.SubDomain;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import cn.v7soft.dao.enums.DnsSwitchEventType;
import cn.v7soft.dao.enums.FrontServerHealthStatus;
import cn.v7soft.dao.enums.FrontServerIpRole;
import cn.v7soft.dao.repositories.primary.DnsSwitchLogRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class HealthCheckTask {

    static final int REQUIRED_CONSECUTIVE_RESULTS = 3;

    /** 心跳摘要与失败日志去重的默认周期（轮）。5 秒轮次下约 5 分钟一次。 */
    private static final int DEFAULT_HEARTBEAT_ROUNDS = 60;

    private final Environment environment;
    private final IFrontServerService frontServerService;
    private final ISubDomainService subDomainService;
    private final DnsServiceFactory dnsServiceFactory;
    private final DnsSwitchLogRepository dnsSwitchLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final NodeHealthProbe nodeHealthProbe;
    private final Executor healthCheckExecutor;

    private final Map<Long, ServerRuntimeState> runtimeStates = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 心跳摘要周期（轮）；<=0 表示关闭心跳摘要与失败日志降频 */
    private final int heartbeatRounds;
    /** 已执行的轮次序号，用于心跳降频 */
    private long roundSeq;

    public HealthCheckTask(
            Environment environment,
            IFrontServerService frontServerService,
            ISubDomainService subDomainService,
            DnsServiceFactory dnsServiceFactory,
            DnsSwitchLogRepository dnsSwitchLogRepository,
            ApplicationEventPublisher eventPublisher,
            NodeHealthProbe nodeHealthProbe,
            @Qualifier("healthCheckExecutor") Executor healthCheckExecutor) {
        this.environment = environment;
        this.frontServerService = frontServerService;
        this.subDomainService = subDomainService;
        this.dnsServiceFactory = dnsServiceFactory;
        this.dnsSwitchLogRepository = dnsSwitchLogRepository;
        this.eventPublisher = eventPublisher;
        this.nodeHealthProbe = nodeHealthProbe;
        this.healthCheckExecutor = healthCheckExecutor;
        this.heartbeatRounds = intProperty("front-server.health-check.heartbeat-rounds", DEFAULT_HEARTBEAT_ROUNDS);
    }

    /**
     * 启动时打一次配置横幅：任务有没有启用、按什么参数跑，都必须能从日志直接看出来，
     * 否则「没有任何健康检查日志」既可能是一切正常，也可能是任务压根没启动。
     */
    @PostConstruct
    void logStartupBanner() {
        if (isDevProfile()) {
            log.info("[HealthCheck] 当前激活 dev profile，健康检查任务已禁用（不探测、不改 DNS）");
            return;
        }
        log.info("[HealthCheck] 健康检查任务已启用: 轮次间隔={}ms, 连接超时={}ms, 读取超时={}ms, "
                        + "确认阈值={}次, 心跳摘要每{}轮, 探测地址=http://<IP>/health",
                intProperty("front-server.health-check.interval-ms", 5000),
                intProperty("front-server.health-check.connect-timeout-ms", 3000),
                intProperty("front-server.health-check.read-timeout-ms", 3000),
                REQUIRED_CONSECUTIVE_RESULTS, heartbeatRounds);
    }

    @Scheduled(fixedRateString = "${front-server.health-check.interval-ms:5000}")
    public void healthCheck() {
        if (isDevProfile() || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            executeHealthCheckRound();
        } catch (Exception e) {
            log.error("[HealthCheck] 健康检查轮次执行失败", e);
        } finally {
            running.set(false);
        }
    }

    private void executeHealthCheckRound() {
        long startedAtNanos = System.nanoTime();
        roundSeq++;

        List<FrontServer> activeServers = frontServerService.listFrontServers().stream()
                .filter(server -> server.getId() != null)
                .filter(server -> server.getStatus() == StatusEnum.VALID)
                .filter(this::hasConfiguredIp)
                .toList();

        if (activeServers.isEmpty()) {
            // 一台都没有时同样要留痕，否则「日志里什么都没有」无法与「任务正常但无事发生」区分
            if (isHeartbeatRound()) {
                log.warn("[HealthCheck] 没有可检查的前端服务器：需状态为 VALID 且至少配置一个 IP");
            }
            runtimeStates.clear();
            return;
        }

        Set<Long> activeIds = new HashSet<>();
        Map<Long, FrontServer> serversById = new LinkedHashMap<>();
        Map<ProbeKey, CompletableFuture<HealthProbeResult>> probes = new LinkedHashMap<>();

        for (FrontServer server : activeServers) {
            activeIds.add(server.getId());
            serversById.put(server.getId(), server);
            ServerRuntimeState state = runtimeStates.computeIfAbsent(server.getId(), id -> new ServerRuntimeState());
            state.sync(server);
            for (NodeRuntimeState node : state.nodes()) {
                ProbeKey key = new ProbeKey(server.getId(), node.role());
                probes.put(key, submitProbe(node.ip()));
            }
        }
        runtimeStates.keySet().removeIf(id -> !activeIds.contains(id));

        for (Map.Entry<Long, FrontServer> entry : serversById.entrySet()) {
            FrontServer server = entry.getValue();
            ServerRuntimeState state = runtimeStates.get(entry.getKey());
            try {
                for (NodeRuntimeState node : state.nodes()) {
                    HealthProbeResult result = probes.get(new ProbeKey(server.getId(), node.role())).join();
                    applyProbeResult(server, node, result);
                }
                reconcileDns(server, state);
            } catch (Exception e) {
                log.error("[HealthCheck] 服务器 {} 检查失败，不影响其他服务器", server.getName(), e);
            }
        }

        logRoundSummary(startedAtNanos, probes.size());
        if (isHeartbeatRound()) {
            logHeartbeat(serversById);
        }
    }

    /**
     * 每轮一条紧凑摘要：确认任务在跑、耗时是否逼近轮次间隔。
     * 用 TRACE 而非 DEBUG —— 本项目默认 {@code cn.v7soft: debug}，每 5 秒一条会淹没其他日志。
     */
    private void logRoundSummary(long startedAtNanos, int probeCount) {
        if (!log.isTraceEnabled()) {
            return;
        }
        int healthy = 0;
        int unhealthy = 0;
        int unknown = 0;
        for (ServerRuntimeState state : runtimeStates.values()) {
            for (NodeRuntimeState node : state.nodes()) {
                switch (node.status()) {
                    case HEALTHY -> healthy++;
                    case UNHEALTHY -> unhealthy++;
                    case UNKNOWN -> unknown++;
                }
            }
        }
        log.trace("[HealthCheck] 第{}轮完成 耗时={}ms 服务器={} 探测={} 健康={} 故障={} 未知={}",
                roundSeq, (System.nanoTime() - startedAtNanos) / 1_000_000,
                runtimeStates.size(), probeCount, healthy, unhealthy, unknown);
    }

    /**
     * 心跳摘要，INFO 级、每 heartbeatRounds 轮一条：不依赖任何状态变化就能看出
     * 「每个节点当前判定 + DNS 实际指向 + 期望指向」，是判断是否正常运作的主要依据。
     */
    private void logHeartbeat(Map<Long, FrontServer> serversById) {
        for (Map.Entry<Long, FrontServer> entry : serversById.entrySet()) {
            ServerRuntimeState state = runtimeStates.get(entry.getKey());
            if (state == null) {
                continue;
            }
            StringBuilder nodes = new StringBuilder();
            for (NodeRuntimeState node : state.nodes()) {
                if (!nodes.isEmpty()) {
                    nodes.append(", ");
                }
                nodes.append(node.role().getLabel()).append(' ').append(node.ip())
                        .append('=').append(statusLabel(node.status()));
            }
            String expected = state.highestHealthyNode()
                    .map(node -> node.ip() + "(" + node.role().getLabel() + ")")
                    .orElse("无健康节点");
            log.info("[HealthCheck] 心跳 {} [{}] DNS实际={} DNS期望={}",
                    entry.getValue().getName(), nodes, safeIp(state.lastObservedDnsIp), expected);
        }
    }

    private boolean isHeartbeatRound() {
        // 轮次从 1 开始计数，减 1 后取余：启动后第一轮就打一次，不用等满一个周期；
        // 且 heartbeatRounds=1 时每轮都打（写成 roundSeq % n == 1 在 n=1 时恒不成立）
        return heartbeatRounds > 0 && (roundSeq - 1) % heartbeatRounds == 0;
    }

    private static String statusLabel(FrontServerHealthStatus status) {
        return switch (status) {
            case HEALTHY -> "健康";
            case UNHEALTHY -> "故障";
            case UNKNOWN -> "未知";
        };
    }

    private CompletableFuture<HealthProbeResult> submitProbe(String ip) {
        try {
            return CompletableFuture.supplyAsync(() -> nodeHealthProbe.probe(ip), healthCheckExecutor)
                    .exceptionally(error -> HealthProbeResult.unhealthy(error.getClass().getSimpleName()
                            + (error.getMessage() == null ? "" : ": " + error.getMessage())));
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(HealthProbeResult.unhealthy(
                    e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage())));
        }
    }

    private void applyProbeResult(FrontServer server, NodeRuntimeState node, HealthProbeResult result) {
        logProbeDetail(server, node, result);
        HealthTransition transition = node.record(result);
        if (transition == null) {
            return;
        }
        String detail = result.detail();
        if (transition.to() == FrontServerHealthStatus.HEALTHY) {
            log.info("[HealthCheck] {} {} ({}) 已确认健康", server.getName(), node.role().getLabel(), node.ip());
        } else {
            log.warn("[HealthCheck] {} {} ({}) 已确认故障: {}", server.getName(), node.role().getLabel(), node.ip(), detail);
        }
        recordAndNotify(server, DnsSwitchEventType.HEALTH_STATUS_CHANGED, node.role(), node.ip(),
                transition.from(), transition.to(), node.ip(), node.ip(), REQUIRED_CONSECUTIVE_RESULTS, detail);
    }

    /**
     * 单次探测明细。原来只在跨过 3 次阈值时才有日志，导致两个盲区：
     * 阈值前的失败完全看不见，确认故障后的持续失败也不再打印（没有新 transition）。
     * 这里补上，并按「首次失败 / 失败原因变化 / 每个心跳周期」三个条件去重，避免 5 秒一条。
     */
    private void logProbeDetail(FrontServer server, NodeRuntimeState node, HealthProbeResult result) {
        if (!result.healthy()) {
            if (node.shouldLogFailure(result.detail(), heartbeatRounds)) {
                log.warn("[HealthCheck] {} {} ({}) 探测失败(累计{}次，确认阈值{}次): {}",
                        server.getName(), node.role().getLabel(), node.ip(),
                        node.failureStreak() + 1, REQUIRED_CONSECUTIVE_RESULTS, result.detail());
            }
            return;
        }
        if (node.consumeRecoveryFlag()) {
            log.info("[HealthCheck] {} {} ({}) 探测已恢复成功，需连续{}次才确认健康",
                    server.getName(), node.role().getLabel(), node.ip(), REQUIRED_CONSECUTIVE_RESULTS);
        }
    }

    private void reconcileDns(FrontServer server, ServerRuntimeState state) {
        Optional<NodeRuntimeState> desiredOptional = state.highestHealthyNode();
        if (desiredOptional.isEmpty()) {
            state.pendingSwitch = null;
            if (state.allConfiguredNodesUnhealthy() && !state.noHealthyTargetNotified) {
                state.noHealthyTargetNotified = true;
                String current = safeIp(state.lastObservedDnsIp);
                log.error("[HealthCheck] {} 所有已配置节点均已确认故障，DNS 保持指向 {} 不变", server.getName(), current);
                recordAndNotify(server, DnsSwitchEventType.DNS_NO_HEALTHY_TARGET, null, null,
                        null, null, current, current, null, "所有已配置节点均已确认故障，DNS 保持不变");
            } else if (!state.allConfiguredNodesUnhealthy()) {
                state.noHealthyTargetNotified = false;
            }
            return;
        }

        state.noHealthyTargetNotified = false;
        NodeRuntimeState desired = desiredOptional.get();
        if (state.pendingSwitch != null && !state.pendingSwitch.targetIp.equals(desired.ip())) {
            state.pendingSwitch = null;
        }

        DnsContext dnsContext = resolveDnsContext(server);
        if (dnsContext == null) {
            return;
        }
        String currentIp = dnsContext.dnsService().queryRecord(
                dnsContext.account(), dnsContext.parentDomain().getName(), dnsContext.subDomain().getName());
        if (StrUtil.isBlank(currentIp)) {
            log.warn("[HealthCheck] {} DNS 回查没有拿到记录: {}.{}", server.getName(),
                    dnsContext.subDomain().getName(), dnsContext.parentDomain().getName());
            if (state.pendingSwitch != null && state.pendingSwitch.submitted) {
                recordConfirmationFailureIfNeeded(server, state.pendingSwitch, "DNS 回查失败或没有返回记录");
            }
            return;
        }
        currentIp = currentIp.trim();
        state.lastObservedDnsIp = currentIp;
        log.trace("[HealthCheck] {} DNS 回查: {} 当前={} 期望={}", server.getName(),
                server.getCnameRecord(), currentIp, desired.ip());

        if (currentIp.equals(desired.ip())) {
            if (state.pendingSwitch != null && state.pendingSwitch.targetIp.equals(desired.ip())) {
                PendingDnsSwitch confirmed = state.pendingSwitch;
                state.pendingSwitch = null;
                state.startupDnsChecked = true;
                log.info("[HealthCheck] {} DNS 切换已确认: {} -> {} ({})", server.getName(),
                        safeIp(confirmed.fromIp), desired.ip(), desired.role().getLabel());
                recordAndNotify(server, DnsSwitchEventType.DNS_SWITCH_CONFIRMED, desired.role(), desired.ip(),
                        null, null, confirmed.fromIp, desired.ip(), null, "DNS 回查已指向目标 IP");
            } else if (!state.startupDnsChecked) {
                state.startupDnsChecked = true;
                log.info("[HealthCheck] {} 启动校验正常，DNS 已指向最高优先级健康节点 {} ({})",
                        server.getName(), desired.ip(), desired.role().getLabel());
                recordAndNotify(server, DnsSwitchEventType.DNS_STARTUP_VERIFIED, desired.role(), desired.ip(),
                        null, null, currentIp, desired.ip(), null, "启动校验正常，DNS 已指向最高优先级健康节点");
            }
            return;
        }

        state.startupDnsChecked = true;
        if (state.pendingSwitch == null) {
            state.pendingSwitch = new PendingDnsSwitch(currentIp, desired.ip(), desired.role());
            log.warn("[HealthCheck] {} DNS 指向与期望不一致，准备切换: {} -> {} ({})", server.getName(),
                    currentIp, desired.ip(), desired.role().getLabel());
        }
        PendingDnsSwitch pending = state.pendingSwitch;
        if (pending.submitted) {
            log.debug("[HealthCheck] {} DNS 已提交但回查仍指向 {}，目标 {}，重新提交",
                    server.getName(), currentIp, pending.targetIp);
            recordConfirmationFailureIfNeeded(server, pending,
                    "DNS 当前仍指向 " + currentIp + "，目标为 " + pending.targetIp);
            updateDns(dnsContext, pending.targetIp);
            return;
        }

        boolean submitted = updateDns(dnsContext, pending.targetIp);
        if (submitted) {
            pending.submitted = true;
            pending.submitFailureStreak = 0;
            log.info("[HealthCheck] {} DNS 更新已被平台接受: {} -> {} ({})，等待回查确认", server.getName(),
                    safeIp(pending.fromIp), pending.targetIp, pending.targetRole.getLabel());
            recordAndNotify(server, DnsSwitchEventType.DNS_SWITCH_SUBMITTED, pending.targetRole, pending.targetIp,
                    null, null, pending.fromIp, pending.targetIp, null, "DNS 平台已接受更新请求");
        } else {
            pending.submitFailureStreak++;
            // 前 3 次必打（诊断需要），之后降到每个心跳周期一条，避免 5 秒一条刷屏
            if (pending.submitFailureStreak <= REQUIRED_CONSECUTIVE_RESULTS || isHeartbeatRound()) {
                log.warn("[HealthCheck] {} DNS 更新提交被拒绝(连续{}次): 目标 {} ({})", server.getName(),
                        pending.submitFailureStreak, pending.targetIp, pending.targetRole.getLabel());
            }
            if (pending.submitFailureStreak >= REQUIRED_CONSECUTIVE_RESULTS && !pending.submitFailureNotified) {
                pending.submitFailureNotified = true;
                recordAndNotify(server, DnsSwitchEventType.DNS_SWITCH_SUBMIT_FAILED, pending.targetRole,
                        pending.targetIp, null, null, pending.fromIp, pending.targetIp,
                        pending.submitFailureStreak, "DNS 更新请求连续提交失败");
            }
        }
    }

    private void recordConfirmationFailureIfNeeded(
            FrontServer server, PendingDnsSwitch pending, String detail) {
        pending.confirmFailureStreak++;
        if (pending.confirmFailureStreak >= REQUIRED_CONSECUTIVE_RESULTS && !pending.confirmFailureNotified) {
            pending.confirmFailureNotified = true;
            log.error("[HealthCheck] {} DNS 切换连续{}次回查未生效: 目标 {}，{}", server.getName(),
                    pending.confirmFailureStreak, pending.targetIp, detail);
            recordAndNotify(server, DnsSwitchEventType.DNS_SWITCH_CONFIRM_FAILED, pending.targetRole,
                    pending.targetIp, null, null, pending.fromIp, pending.targetIp,
                    pending.confirmFailureStreak, detail);
        }
    }

    private DnsContext resolveDnsContext(FrontServer server) {
        try {
            Optional<SubDomainDto> relayDomain = subDomainService.findRelayDomainByFullName(server.getCnameRecord());
            if (relayDomain.isEmpty()) {
                log.warn("[HealthCheck] {} 未找到中继域名记录: {}", server.getName(), server.getCnameRecord());
                return null;
            }
            SubDomainDto dto = relayDomain.get();
            SubDomain subDomain = dto.getSubDomain();
            TopLevelDomain parentDomain = dto.getTopLevelDomain();
            CloudPlatformAccount account = parentDomain.getCloudPlatformAccount();
            IDnsService dnsService = dnsServiceFactory.getServiceOrThrow(account.getCloudPlatform());
            return new DnsContext(subDomain, parentDomain, account, dnsService);
        } catch (Exception e) {
            log.error("[HealthCheck] {} 解析 DNS 配置失败", server.getName(), e);
            return null;
        }
    }

    private boolean updateDns(DnsContext context, String targetIp) {
        try {
            return context.dnsService().updateRecord(
                    context.account(), context.parentDomain().getName(), context.subDomain().getName(), targetIp);
        } catch (Exception e) {
            log.error("[HealthCheck] DNS 更新异常，目标 IP={}", targetIp, e);
            return false;
        }
    }

    private void recordAndNotify(
            FrontServer server,
            DnsSwitchEventType eventType,
            FrontServerIpRole ipRole,
            String ipAddress,
            FrontServerHealthStatus fromHealthStatus,
            FrontServerHealthStatus toHealthStatus,
            String fromIp,
            String toIp,
            Integer consecutiveCount,
            String detail) {
        LocalDateTime occurredAt = LocalDateTime.now();
        String safeFromIp = safeIp(fromIp);
        String safeToIp = safeIp(toIp);
        String safeDetail = truncate(detail, 1000);
        try {
            dnsSwitchLogRepository.save(DnsSwitchLog.builder()
                    .frontServerId(server.getId())
                    .companyId(server.getCompanyId())
                    .serverName(server.getName())
                    .cnameRecord(server.getCnameRecord())
                    .fromIp(safeFromIp)
                    .toIp(safeToIp)
                    .switchType(legacySwitchType(eventType))
                    .eventType(eventType)
                    .ipRole(ipRole)
                    .ipAddress(ipAddress)
                    .fromHealthStatus(fromHealthStatus)
                    .toHealthStatus(toHealthStatus)
                    .consecutiveCount(consecutiveCount)
                    .detail(safeDetail)
                    .switchedAt(occurredAt)
                    .acknowledged(eventType != DnsSwitchEventType.DNS_SWITCH_CONFIRMED)
                    .build());
        } catch (Exception e) {
            log.error("[HealthCheck] 保存操作日志失败: server={}, event={}", server.getName(), eventType, e);
        }

        try {
        eventPublisher.publishEvent(ServerHealthNotificationEvent.builder()
                .companyId(server.getCompanyId())
                .serverName(server.getName())
                .cnameRecord(server.getCnameRecord())
                .eventType(eventType)
                .ipRole(ipRole)
                .ipAddress(ipAddress)
                .fromHealthStatus(fromHealthStatus)
                .toHealthStatus(toHealthStatus)
                .fromIp(safeFromIp)
                .toIp(safeToIp)
                .consecutiveCount(consecutiveCount)
                .detail(safeDetail)
                .occurredAt(occurredAt)
                .build());
        } catch (Exception e) {
            log.error("[HealthCheck] 发布通知事件失败: server={}, event={}", server.getName(), eventType, e);
        }
    }

    private boolean hasConfiguredIp(FrontServer server) {
        return StrUtil.isNotBlank(server.getPrimaryIp())
                || StrUtil.isNotBlank(server.getFailoverIp())
                || StrUtil.isNotBlank(server.getFallbackIp());
    }

    private String configuredIp(FrontServer server, FrontServerIpRole role) {
        return switch (role) {
            case PRIMARY -> server.getPrimaryIp();
            case FAILOVER -> server.getFailoverIp();
            case FALLBACK -> server.getFallbackIp();
        };
    }

    private static String legacySwitchType(DnsSwitchEventType eventType) {
        return switch (eventType) {
            case HEALTH_STATUS_CHANGED -> "HEALTH";
            case DNS_SWITCH_SUBMITTED -> "SUBMITTED";
            case DNS_SWITCH_SUBMIT_FAILED -> "SUBMIT_FAILED";
            case DNS_SWITCH_CONFIRMED -> "CONFIRMED";
            case DNS_SWITCH_CONFIRM_FAILED -> "CONFIRM_FAILED";
            case DNS_STARTUP_VERIFIED -> "STARTUP_VERIFIED";
            case DNS_NO_HEALTHY_TARGET -> "NO_HEALTHY_TARGET";
        };
    }

    private static String safeIp(String ip) {
        return StrUtil.blankToDefault(ip, "-");
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public boolean isDevProfile() {
        return List.of(environment.getActiveProfiles()).contains("dev");
    }

    /** 读取整型配置；单测里 Environment 是 mock，取不到值时退回默认值 */
    private int intProperty(String key, int defaultValue) {
        return Optional.ofNullable(environment.getProperty(key, Integer.class)).orElse(defaultValue);
    }

    private record ProbeKey(Long serverId, FrontServerIpRole role) {
    }

    private record DnsContext(
            SubDomain subDomain,
            TopLevelDomain parentDomain,
            CloudPlatformAccount account,
            IDnsService dnsService) {
    }

    /** 包级可见：同包单测需要调用返回该类型的 {@link NodeRuntimeState#record} */
    record HealthTransition(FrontServerHealthStatus from, FrontServerHealthStatus to) {
    }

    private final class ServerRuntimeState {
        private final EnumMap<FrontServerIpRole, NodeRuntimeState> nodeStates =
                new EnumMap<>(FrontServerIpRole.class);
        private String cnameRecord;
        private PendingDnsSwitch pendingSwitch;
        private String lastObservedDnsIp;
        private boolean startupDnsChecked;
        private boolean noHealthyTargetNotified;

        private void sync(FrontServer server) {
            if (!Objects.equals(cnameRecord, server.getCnameRecord())) {
                cnameRecord = server.getCnameRecord();
                pendingSwitch = null;
                startupDnsChecked = false;
                lastObservedDnsIp = null;
            }
            for (FrontServerIpRole role : FrontServerIpRole.values()) {
                String configured = StrUtil.trim(configuredIp(server, role));
                if (StrUtil.isBlank(configured)) {
                    nodeStates.remove(role);
                    continue;
                }
                NodeRuntimeState existing = nodeStates.get(role);
                if (existing == null || !configured.equals(existing.ip())) {
                    nodeStates.put(role, new NodeRuntimeState(role, configured));
                }
            }
        }

        private List<NodeRuntimeState> nodes() {
            return new ArrayList<>(nodeStates.values());
        }

        private Optional<NodeRuntimeState> highestHealthyNode() {
            return nodeStates.values().stream()
                    .filter(node -> node.status == FrontServerHealthStatus.HEALTHY)
                    .min((left, right) -> Integer.compare(
                            left.role().getPriority(), right.role().getPriority()));
        }

        private boolean allConfiguredNodesUnhealthy() {
            return !nodeStates.isEmpty() && nodeStates.values().stream()
                    .allMatch(node -> node.status == FrontServerHealthStatus.UNHEALTHY);
        }
    }

    static final class NodeRuntimeState {
        private final FrontServerIpRole role;
        private final String ip;
        private FrontServerHealthStatus status = FrontServerHealthStatus.UNKNOWN;
        private int successStreak;
        private int failureStreak;
        private String lastLoggedFailureDetail;
        private int roundsSinceFailureLog;
        private boolean failedSinceLastSuccess;

        NodeRuntimeState(FrontServerIpRole role, String ip) {
            this.role = role;
            this.ip = ip;
        }

        FrontServerIpRole role() {
            return role;
        }

        String ip() {
            return ip;
        }

        FrontServerHealthStatus status() {
            return status;
        }

        int failureStreak() {
            return failureStreak;
        }

        /** 失败日志是否允许打印：首次失败、失败原因变化、或距上次打印满一个心跳周期 */
        boolean shouldLogFailure(String detail, int heartbeatRounds) {
            failedSinceLastSuccess = true;
            roundsSinceFailureLog++;
            boolean firstFailure = failureStreak == 0;
            boolean detailChanged = !Objects.equals(lastLoggedFailureDetail, detail);
            boolean due = heartbeatRounds > 0 && roundsSinceFailureLog >= heartbeatRounds;
            if (firstFailure || detailChanged || due) {
                lastLoggedFailureDetail = detail;
                roundsSinceFailureLog = 0;
                return true;
            }
            return false;
        }

        /** 探测成功且此前失败过 → 返回 true 并复位，用于只在「失败转成功」的那一次打恢复日志 */
        boolean consumeRecoveryFlag() {
            if (!failedSinceLastSuccess) {
                return false;
            }
            failedSinceLastSuccess = false;
            lastLoggedFailureDetail = null;
            roundsSinceFailureLog = 0;
            return true;
        }

        HealthTransition record(HealthProbeResult result) {
            if (result.healthy()) {
                successStreak++;
                failureStreak = 0;
                if (successStreak >= REQUIRED_CONSECUTIVE_RESULTS
                        && status != FrontServerHealthStatus.HEALTHY) {
                    FrontServerHealthStatus previous = status;
                    status = FrontServerHealthStatus.HEALTHY;
                    return new HealthTransition(previous, status);
                }
            } else {
                failureStreak++;
                successStreak = 0;
                if (failureStreak >= REQUIRED_CONSECUTIVE_RESULTS
                        && status != FrontServerHealthStatus.UNHEALTHY) {
                    FrontServerHealthStatus previous = status;
                    status = FrontServerHealthStatus.UNHEALTHY;
                    return new HealthTransition(previous, status);
                }
            }
            return null;
        }
    }

    private static final class PendingDnsSwitch {
        private final String fromIp;
        private final String targetIp;
        private final FrontServerIpRole targetRole;
        private boolean submitted;
        private int submitFailureStreak;
        private int confirmFailureStreak;
        private boolean submitFailureNotified;
        private boolean confirmFailureNotified;

        private PendingDnsSwitch(String fromIp, String targetIp, FrontServerIpRole targetRole) {
            this.fromIp = fromIp;
            this.targetIp = targetIp;
            this.targetRole = targetRole;
        }
    }
}
