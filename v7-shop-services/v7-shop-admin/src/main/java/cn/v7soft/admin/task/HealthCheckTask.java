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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class HealthCheckTask {

    static final int REQUIRED_CONSECUTIVE_RESULTS = 3;

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
        List<FrontServer> activeServers = frontServerService.listFrontServers().stream()
                .filter(server -> server.getId() != null)
                .filter(server -> server.getStatus() == StatusEnum.VALID)
                .filter(this::hasConfiguredIp)
                .toList();

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

    private void reconcileDns(FrontServer server, ServerRuntimeState state) {
        Optional<NodeRuntimeState> desiredOptional = state.highestHealthyNode();
        if (desiredOptional.isEmpty()) {
            state.pendingSwitch = null;
            if (state.allConfiguredNodesUnhealthy() && !state.noHealthyTargetNotified) {
                state.noHealthyTargetNotified = true;
                String current = safeIp(state.lastObservedDnsIp);
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
            if (state.pendingSwitch != null && state.pendingSwitch.submitted) {
                recordConfirmationFailureIfNeeded(server, state.pendingSwitch, "DNS 回查失败或没有返回记录");
            }
            return;
        }
        currentIp = currentIp.trim();
        state.lastObservedDnsIp = currentIp;

        if (currentIp.equals(desired.ip())) {
            if (state.pendingSwitch != null && state.pendingSwitch.targetIp.equals(desired.ip())) {
                PendingDnsSwitch confirmed = state.pendingSwitch;
                state.pendingSwitch = null;
                state.startupDnsChecked = true;
                recordAndNotify(server, DnsSwitchEventType.DNS_SWITCH_CONFIRMED, desired.role(), desired.ip(),
                        null, null, confirmed.fromIp, desired.ip(), null, "DNS 回查已指向目标 IP");
            } else if (!state.startupDnsChecked) {
                state.startupDnsChecked = true;
                recordAndNotify(server, DnsSwitchEventType.DNS_STARTUP_VERIFIED, desired.role(), desired.ip(),
                        null, null, currentIp, desired.ip(), null, "启动校验正常，DNS 已指向最高优先级健康节点");
            }
            return;
        }

        state.startupDnsChecked = true;
        if (state.pendingSwitch == null) {
            state.pendingSwitch = new PendingDnsSwitch(currentIp, desired.ip(), desired.role());
        }
        PendingDnsSwitch pending = state.pendingSwitch;
        if (pending.submitted) {
            recordConfirmationFailureIfNeeded(server, pending,
                    "DNS 当前仍指向 " + currentIp + "，目标为 " + pending.targetIp);
            updateDns(dnsContext, pending.targetIp);
            return;
        }

        boolean submitted = updateDns(dnsContext, pending.targetIp);
        if (submitted) {
            pending.submitted = true;
            pending.submitFailureStreak = 0;
            recordAndNotify(server, DnsSwitchEventType.DNS_SWITCH_SUBMITTED, pending.targetRole, pending.targetIp,
                    null, null, pending.fromIp, pending.targetIp, null, "DNS 平台已接受更新请求");
        } else {
            pending.submitFailureStreak++;
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

    private record ProbeKey(Long serverId, FrontServerIpRole role) {
    }

    private record DnsContext(
            SubDomain subDomain,
            TopLevelDomain parentDomain,
            CloudPlatformAccount account,
            IDnsService dnsService) {
    }

    private record HealthTransition(FrontServerHealthStatus from, FrontServerHealthStatus to) {
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
