package cn.v7soft.admin.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

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
import cn.v7soft.dao.repositories.primary.DnsSwitchLogRepository;

class HealthCheckTaskPriorityTest {

    private IFrontServerService frontServerService;
    private DnsSwitchLogRepository logRepository;
    private NodeHealthProbe probe;
    private IDnsService dnsService;
    private HealthCheckTask task;

    @BeforeEach
    void setUp() {
        Environment environment = mock(Environment.class);
        frontServerService = mock(IFrontServerService.class);
        ISubDomainService subDomainService = mock(ISubDomainService.class);
        DnsServiceFactory dnsServiceFactory = mock(DnsServiceFactory.class);
        logRepository = mock(DnsSwitchLogRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
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

        FrontServer server = FrontServer.builder()
                .id(1L)
                .companyId(9L)
                .status(StatusEnum.VALID)
                .name("edge-server")
                .cnameRecord("edge.example.com")
                .primaryIp("10.0.0.1")
                .failoverIp("10.0.0.2")
                .fallbackIp("10.0.0.3")
                .healthCheckUrl("/health")
                .build();
        when(frontServerService.listFrontServers()).thenReturn(List.of(server));

        task = new HealthCheckTask(environment, frontServerService, subDomainService,
                dnsServiceFactory, logRepository, eventPublisher, probe, directExecutor,
                new FrontServerHealthSnapshotHolder());
    }

    @Test
    void prefersBackupOverFallbackAndReturnsToPrimaryAfterRecovery() {
        AtomicReference<Boolean> primaryHealthy = new AtomicReference<>(false);
        when(probe.probe("10.0.0.1")).thenAnswer(invocation -> primaryHealthy.get()
                ? HealthProbeResult.healthy("ok") : HealthProbeResult.unhealthy("down"));
        when(probe.probe("10.0.0.2")).thenReturn(HealthProbeResult.healthy("ok"));
        when(probe.probe("10.0.0.3")).thenReturn(HealthProbeResult.healthy("ok"));

        AtomicReference<String> currentDns = new AtomicReference<>("10.0.0.3");
        when(dnsService.queryRecord(any(), anyString(), anyString())).thenAnswer(invocation -> currentDns.get());
        when(dnsService.updateRecord(any(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            currentDns.set(invocation.getArgument(3));
            return true;
        });

        task.healthCheck();
        task.healthCheck();
        task.healthCheck();
        task.healthCheck();

        primaryHealthy.set(true);
        task.healthCheck();
        task.healthCheck();
        task.healthCheck();

        ArgumentCaptor<String> targets = ArgumentCaptor.forClass(String.class);
        verify(dnsService, org.mockito.Mockito.times(2))
                .updateRecord(any(), anyString(), anyString(), targets.capture());
        assertEquals(List.of("10.0.0.2", "10.0.0.1"), targets.getAllValues());
    }

    @Test
    void keepsDnsUnchangedWhenEveryNodeIsUnhealthy() {
        when(probe.probe(anyString())).thenReturn(HealthProbeResult.unhealthy("down"));

        task.healthCheck();
        task.healthCheck();
        task.healthCheck();

        verify(dnsService, never()).updateRecord(any(), anyString(), anyString(), anyString());
        ArgumentCaptor<DnsSwitchLog> logs = ArgumentCaptor.forClass(DnsSwitchLog.class);
        verify(logRepository, org.mockito.Mockito.times(4)).save(logs.capture());
        assertTrue(logs.getAllValues().stream()
                .anyMatch(log -> log.getEventType() == DnsSwitchEventType.DNS_NO_HEALTHY_TARGET));
    }
}
