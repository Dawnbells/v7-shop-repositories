package cn.v7soft.admin.task;

import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.service.IFrontServerService;
import cn.v7soft.admin.service.ISubDomainService;
import cn.v7soft.admin.service.dns.IDnsService;
import cn.v7soft.admin.service.dto.SubDomainDto;
import cn.v7soft.dao.entities.primary.CloudPlatformAccount;
import cn.v7soft.dao.entities.primary.FrontServer;
import cn.v7soft.dao.entities.primary.SubDomain;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class HealthCheckTask {
    private final Environment environment;
    private final IFrontServerService frontServerService;
    private final ISubDomainService subDomainService;
    private final IDnsService dnsService;

    // 保存每个服务器的失败计数（key = serverId，value = AtomicInteger）
    private final Map<Long, AtomicInteger> failureCountMap = new ConcurrentHashMap<>();

    /**
     * 每 5 秒执行一次健康检查（fixedRate 可能并发执行）
     */
    @Scheduled(fixedRate = 5000)
    public void healthCheck() {
        if (isDevProfile()) {
            return;
        }
        List<FrontServer> frontServers = frontServerService.listFrontServers();

        for (FrontServer frontServer : frontServers) {
            String healthCheckUrl = frontServer.getHealthCheckUrl();
            String primaryIp = frontServer.getPrimaryIp();
            String healthCheck = StrUtil.isNotBlank(healthCheckUrl) ? healthCheckUrl : primaryIp;
            Long serverId = frontServer.getId();
            boolean healthy;
            if (StrUtil.isNotBlank(healthCheckUrl)) {
                healthy = checkHttpHealth(healthCheckUrl);
            } else {
                healthy = checkPingHealth(primaryIp);
            }
            if (healthy) {
                // 成功就清零（用 AtomicInteger.set 保证线程安全）
                failureCountMap.computeIfAbsent(serverId, id -> new AtomicInteger(0)).set(0);
                log.info("[HealthCheck] {} ({}) 正常 ✅", frontServer.getName(), healthCheck);
            } else {
                // 失败计数 +1（线程安全）
                int failCount = failureCountMap
                        .computeIfAbsent(serverId, id -> new AtomicInteger(0))
                        .incrementAndGet();

                log.warn("[HealthCheck] {} ({}) 失败 {} 次 ❌", frontServer.getName(), healthCheck, failCount);

                // 连续失败 3 次，切换 IP（只切一次）
                if (failCount % 10 == 3) {
                    switchIp(frontServer);
                }
            }
        }
    }

    /**
     * 检查 HTTP 健康
     */
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

    /**
     * 检查 Ping 健康
     */
    private boolean checkPingHealth(String ipToCheck) {
        try {
            InetAddress inet = InetAddress.getByName(ipToCheck);
            return inet.isReachable(3000); // 3 秒超时
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * 切换 IP（只切换一次）
     */
    private void switchIp(FrontServer frontServer) {
        String primaryIp = frontServer.getPrimaryIp();
        String failoverIp = frontServer.getFailoverIp();
        frontServer.setIpSwitched(true);       // 标记已经切换过

        log.error("[HealthCheck] {} 切换 IP: {} -> {} 🚀", frontServer.getName(), primaryIp, failoverIp);
        frontServerService.saveAndFlush(frontServer);
        Optional<SubDomainDto> relayDomain = subDomainService.findRelayDomainByFullName(frontServer.getCnameRecord());
        if (relayDomain.isEmpty()) {
            return;
        }
        SubDomainDto subDomainDto = relayDomain.get();
        SubDomain subDomain = subDomainDto.getSubDomain();
        TopLevelDomain parentDomain = subDomainDto.getTopLevelDomain();
        CloudPlatformAccount cloudPlatformAccount = parentDomain.getCloudPlatformAccount();
        dnsService.updateRecord(cloudPlatformAccount, parentDomain.getName(), subDomain.getName(), failoverIp);
    }
    public boolean isDevProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }
}
