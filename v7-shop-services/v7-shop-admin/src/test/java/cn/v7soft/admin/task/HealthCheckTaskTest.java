package cn.v7soft.admin.task;

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

        task = new HealthCheckTask(environment, frontServerService, subDomainService,
                dnsServiceFactory, logRepository, eventPublisher, probe, directExecutor);
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
}
