package cn.v7soft.admin.task;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.core.env.Environment;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.event.ServerIpSwitchNotificationEvent;
import cn.v7soft.admin.service.IFrontServerService;
import cn.v7soft.admin.service.ISubDomainService;
import cn.v7soft.admin.service.dns.IDnsService;
import cn.v7soft.admin.service.dns.impl.DnsServiceFactory;
import cn.v7soft.admin.service.dto.SubDomainDto;
import cn.v7soft.dao.entities.primary.CloudPlatformAccount;
import cn.v7soft.dao.entities.primary.DnsSwitchLog;
import cn.v7soft.dao.entities.primary.FrontServer;
import cn.v7soft.dao.entities.primary.SubDomain;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import cn.v7soft.dao.repositories.primary.DnsSwitchLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class HealthCheckTask {
    private final Environment environment;
    private final IFrontServerService frontServerService;
    private final ISubDomainService subDomainService;
    private final DnsServiceFactory dnsServiceFactory;
    private final DnsSwitchLogRepository dnsSwitchLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final Map<Long, AtomicInteger> failureCountMap = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 5000)
    public void healthCheck() {
        if (isDevProfile()) {
            return;
        }
        List<FrontServer> frontServers = frontServerService.listFrontServers();

        for (FrontServer frontServer : frontServers) {
            String primaryIp = frontServer.getPrimaryIp();
            String failoverIp = frontServer.getFailoverIp();
            Long serverId = frontServer.getId();
            boolean primaryHealthy = checkPrimaryHealth(frontServer);

            if (primaryHealthy) {
                failureCountMap.computeIfAbsent(serverId, id -> new AtomicInteger(0)).set(0);
                String currentIp = queryCurrentDnsIp(frontServer);
                if (currentIp != null && !currentIp.equals(primaryIp)) {
                    log.info("[HealthCheck] {} 主IP恢复，切回: {} -> {}", frontServer.getName(), currentIp, primaryIp);
                    updateDns(frontServer, primaryIp);
                    saveSwitchLogAndPublishNotification(frontServer, currentIp, primaryIp, "RECOVERY");
                } else {
                    log.info("[HealthCheck] {} ({}) 正常", frontServer.getName(), primaryIp);
                }
            } else {
                int failCount = failureCountMap
                        .computeIfAbsent(serverId, id -> new AtomicInteger(0))
                        .incrementAndGet();
                log.warn("[HealthCheck] {} ({}) 失败 {} 次", frontServer.getName(), primaryIp, failCount);

                if (failCount >= 3) {
                    String currentIp = queryCurrentDnsIp(frontServer);
                    if (currentIp != null && currentIp.equals(primaryIp)) {
                        log.error("[HealthCheck] {} 切换到备用IP: {} -> {}", frontServer.getName(), primaryIp, failoverIp);
                        updateDns(frontServer, failoverIp);
                        saveSwitchLogAndPublishNotification(frontServer, primaryIp, failoverIp, "FAILOVER");
                    }
                }
            }
        }
    }

    private boolean checkPrimaryHealth(FrontServer frontServer) {
        String healthCheckUrl = frontServer.getHealthCheckUrl();
        String primaryIp = frontServer.getPrimaryIp();
        if (StrUtil.isNotBlank(healthCheckUrl)) {
            return checkHttpHealth(healthCheckUrl);
        }
        return checkPingHealth(primaryIp);
    }

    private String queryCurrentDnsIp(FrontServer frontServer) {
        Optional<SubDomainDto> relayDomain = subDomainService.findRelayDomainByFullName(frontServer.getCnameRecord());
        if (relayDomain.isEmpty()) {
            log.warn("[HealthCheck] {} 未找到中继域名记录: {}", frontServer.getName(), frontServer.getCnameRecord());
            return null;
        }
        SubDomainDto dto = relayDomain.get();
        SubDomain subDomain = dto.getSubDomain();
        TopLevelDomain parentDomain = dto.getTopLevelDomain();
        CloudPlatformAccount account = parentDomain.getCloudPlatformAccount();
        IDnsService dnsService = dnsServiceFactory.getServiceOrThrow(account.getCloudPlatform());
        return dnsService.queryRecord(account, parentDomain.getName(), subDomain.getName());
    }

    private void updateDns(FrontServer frontServer, String targetIp) {
        Optional<SubDomainDto> relayDomain = subDomainService.findRelayDomainByFullName(frontServer.getCnameRecord());
        if (relayDomain.isEmpty()) {
            return;
        }
        SubDomainDto dto = relayDomain.get();
        SubDomain subDomain = dto.getSubDomain();
        TopLevelDomain parentDomain = dto.getTopLevelDomain();
        CloudPlatformAccount account = parentDomain.getCloudPlatformAccount();
        IDnsService dnsService = dnsServiceFactory.getServiceOrThrow(account.getCloudPlatform());
        dnsService.updateRecord(account, parentDomain.getName(), subDomain.getName(), targetIp);
    }

    private void saveSwitchLogAndPublishNotification(FrontServer frontServer, String fromIp, String toIp, String switchType) {
        try {
            java.time.LocalDateTime switchedAt = java.time.LocalDateTime.now();
            DnsSwitchLog switchLog = DnsSwitchLog.builder()
                    .serverName(frontServer.getName())
                    .fromIp(fromIp)
                    .toIp(toIp)
                    .switchType(switchType)
                    .switchedAt(switchedAt)
                    .acknowledged(false)
                    .build();
            dnsSwitchLogRepository.save(switchLog);
            eventPublisher.publishEvent(ServerIpSwitchNotificationEvent.of(
                    frontServer.getCompanyId(),
                    frontServer.getName(),
                    frontServer.getCnameRecord(),
                    fromIp,
                    toIp,
                    switchType,
                    switchedAt
            ));
        } catch (Exception e) {
            log.error("[HealthCheck] 保存DNS切换日志失败", e);
        }
    }

    private boolean checkHttpHealth(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkPingHealth(String ipToCheck) {
        try {
            InetAddress inet = InetAddress.getByName(ipToCheck);
            return inet.isReachable(3000);
        } catch (Exception ignored) {
        }
        return false;
    }

    public boolean isDevProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }
}
