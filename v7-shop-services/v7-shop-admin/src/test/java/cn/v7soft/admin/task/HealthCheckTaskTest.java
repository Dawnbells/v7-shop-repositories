package cn.v7soft.admin.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;

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
import cn.v7soft.dao.enums.CloudPlatform;
import cn.v7soft.dao.enums.DnsSwitchEventType;
import cn.v7soft.dao.enums.FrontServerHealthStatus;
import cn.v7soft.dao.enums.FrontServerIpRole;
import cn.v7soft.dao.repositories.primary.DnsSwitchLogRepository;

class HealthCheckTaskTest {

    private Environment environment;
    private IFrontServerService frontServerService;
    private ISubDomainService subDomainService;
    private DnsServiceFactory dnsServiceFactory;
    private DnsSwitchLogRepository logRepository;
    private ApplicationEventPublisher eventPublisher;
    private NodeHealthProbe probe;
    private IDnsService dnsService;
    private FrontServerHealthSnapshotHolder snapshotHolder;
    private HealthCheckTask task;
    private FrontServer server;

    @BeforeEach
    void setUp() {
        environment = mock(Environment.class);
        frontServerService = mock(IFrontServerService.class);
        subDomainService = mock(ISubDomainService.class);
        dnsServiceFactory = mock(DnsServiceFactory.class);
        logRepository = mock(DnsSwitchLogRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        probe = mock(NodeHealthProbe.class);
        dnsService = mock(IDnsService.class);
        Executor directExecutor = Runnable::run;

        when(environment.getActiveProfiles()).thenReturn(new String[0]);
        when(logRepository.save(any(DnsSwitchLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CloudPlatformAccount account = CloudPlatformAccount.builder()
                .cloudPlatform(CloudPlatform.ALIYUN)
                .build();
        TopLevelDomain topLevelDomain = TopLevelDomain.builder()
                .name("example.com")
                .cloudPlatformAccount(account)
                .build();
        SubDomain subDomain = SubDomain.builder().name("edge").build();
        when(subDomainService.findRelayDomainByFullName("edge.example.com"))
                .thenReturn(Optional.of(SubDomainDto.builder()
                        .subDomain(subDomain)
                        .topLevelDomain(topLevelDomain)
                        .cloudPlatformAccount(account)
                        .build()));
        when(dnsServiceFactory.getServiceOrThrow(CloudPlatform.ALIYUN)).thenReturn(dnsService);

        server = FrontServer.builder()
                .id(1L)
                .companyId(9L)
                .status(StatusEnum.VALID)
                .name("edge-server")
                .cnameRecord("edge.example.com")
                .primaryIp("10.0.0.1")
                .failoverIp("10.0.0.2")
                .healthCheckUrl("/health")
                .build();
        when(frontServerService.listFrontServers()).thenReturn(List.of(server));

        snapshotHolder = new FrontServerHealthSnapshotHolder();
        task = new HealthCheckTask(environment, frontServerService, subDomainService,
                dnsServiceFactory, logRepository, eventPublisher, probe, directExecutor, snapshotHolder);
    }

    @Test
    void waitsForThreeConsecutiveResultsBeforePublishingState() {
        when(probe.probe(anyString())).thenReturn(HealthProbeResult.healthy("ok"));
        when(dnsService.queryRecord(any(), anyString(), anyString())).thenReturn("10.0.0.1");

        task.healthCheck();
        task.healthCheck();

        verify(logRepository, never()).save(any());
        verify(dnsService, never()).updateRecord(any(), anyString(), anyString(), anyString());

        task.healthCheck();

        verify(logRepository, times(3)).save(any());
        verify(eventPublisher, times(3)).publishEvent(any(Object.class));
        verify(dnsService, never()).updateRecord(any(), anyString(), anyString(), anyString());
    }

    @Test
    void switchesToBackupAfterThreePrimaryFailuresAndConfirmsOnRecheck() {
        when(probe.probe("10.0.0.1")).thenReturn(HealthProbeResult.unhealthy("connection refused"));
        when(probe.probe("10.0.0.2")).thenReturn(HealthProbeResult.healthy("ok"));
        when(dnsService.queryRecord(any(), anyString(), anyString()))
                .thenReturn("10.0.0.1", "10.0.0.2");
        when(dnsService.updateRecord(any(), anyString(), anyString(), anyString())).thenReturn(true);

        task.healthCheck();
        task.healthCheck();
        task.healthCheck();

        verify(dnsService).updateRecord(any(), anyString(), anyString(), org.mockito.ArgumentMatchers.eq("10.0.0.2"));

        task.healthCheck();

        ArgumentCaptor<DnsSwitchLog> captor = ArgumentCaptor.forClass(DnsSwitchLog.class);
        verify(logRepository, atLeastOnce()).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(log -> log.getEventType() == DnsSwitchEventType.DNS_SWITCH_SUBMITTED));
        assertTrue(captor.getAllValues().stream()
                .anyMatch(log -> log.getEventType() == DnsSwitchEventType.DNS_SWITCH_CONFIRMED));
    }

    @Test
    void publishesSnapshotWithThreeFixedNodesAndMarksTheActiveOne() {
        // 这台测试服务器没配 fallbackIp，正好覆盖「未配置的角色也要占位」
        when(probe.probe(anyString())).thenReturn(HealthProbeResult.healthy("ok"));
        when(dnsService.queryRecord(any(), anyString(), anyString())).thenReturn("10.0.0.1");

        task.healthCheck();
        task.healthCheck();
        task.healthCheck();

        FrontServerHealthSnapshot snapshot = snapshotHolder.get();
        assertTrue(snapshot.enabled());
        assertEquals(1, snapshot.servers().size());

        FrontServerHealthSnapshot.ServerHealth serverHealth = snapshot.servers().get(0);
        assertEquals("edge-server", serverHealth.serverName());
        assertEquals("10.0.0.1", serverHealth.dnsIp());

        // 恒定三条、恒定顺序，前端才能固定「从左到右是主/备/兜底」
        List<FrontServerHealthSnapshot.NodeHealth> nodes = serverHealth.nodes();
        assertEquals(List.of(FrontServerIpRole.PRIMARY, FrontServerIpRole.FAILOVER, FrontServerIpRole.FALLBACK),
                nodes.stream().map(FrontServerHealthSnapshot.NodeHealth::role).toList());

        FrontServerHealthSnapshot.NodeHealth primary = nodes.get(0);
        assertEquals(FrontServerHealthStatus.HEALTHY, primary.status());
        assertTrue(primary.configured());
        assertTrue(primary.active());

        FrontServerHealthSnapshot.NodeHealth failover = nodes.get(1);
        assertEquals(FrontServerHealthStatus.HEALTHY, failover.status());
        assertTrue(failover.configured());
        assertFalse(failover.active());

        FrontServerHealthSnapshot.NodeHealth fallback = nodes.get(2);
        assertFalse(fallback.configured());
        assertNull(fallback.ip());
        assertEquals(FrontServerHealthStatus.UNKNOWN, fallback.status());
        assertFalse(fallback.active());
    }

    @Test
    void marksNoNodeActiveWhenDnsLookupReturnsNothing() {
        when(probe.probe(anyString())).thenReturn(HealthProbeResult.healthy("ok"));
        when(dnsService.queryRecord(any(), anyString(), anyString())).thenReturn("");

        task.healthCheck();
        task.healthCheck();
        task.healthCheck();

        // 回查不到记录时宁可一个都不标，也不要把「当前生效」错标到某个节点上
        FrontServerHealthSnapshot.ServerHealth serverHealth = snapshotHolder.get().servers().get(0);
        assertNull(serverHealth.dnsIp());
        assertTrue(serverHealth.nodes().stream().noneMatch(FrontServerHealthSnapshot.NodeHealth::active));
    }

    @Test
    void clearsSnapshotWhenNoServerRemains() {
        when(probe.probe(anyString())).thenReturn(HealthProbeResult.healthy("ok"));
        when(dnsService.queryRecord(any(), anyString(), anyString())).thenReturn("10.0.0.1");
        task.healthCheck();
        assertEquals(1, snapshotHolder.get().servers().size());

        // 服务器被删掉后走的是提前返回那条路径，快照必须跟着清空而不是留着过期的绿点
        when(frontServerService.listFrontServers()).thenReturn(List.of());
        task.healthCheck();

        FrontServerHealthSnapshot snapshot = snapshotHolder.get();
        assertTrue(snapshot.enabled());
        assertTrue(snapshot.servers().isEmpty());
    }
}
