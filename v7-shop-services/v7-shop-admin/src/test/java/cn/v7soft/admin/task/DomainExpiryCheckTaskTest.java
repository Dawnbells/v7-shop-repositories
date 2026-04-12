package cn.v7soft.admin.task;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.admin.service.IFrontServerService;
import cn.v7soft.admin.service.INoticeService;
import cn.v7soft.admin.service.ITopLevelDomainService;
import cn.v7soft.common.utils.SslCertificateUtil;
import cn.v7soft.dao.entities.primary.Company;
import cn.v7soft.dao.entities.primary.SSLCertificate;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import cn.v7soft.dao.repositories.primary.TopLevelDomainRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DomainExpiryCheckTaskTest {

    @Mock private Environment environment;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private TopLevelDomainRepository topLevelDomainRepository;
    @Mock private ITopLevelDomainService topLevelDomainService;
    @Mock private IFrontServerService frontServerService;
    @Mock private INoticeService noticeService;
    @Mock private ICompanyService companyService;

    @InjectMocks
    private DomainExpiryCheckTask task;

    private MockedStatic<SslCertificateUtil> sslUtilMock;

    @BeforeEach
    void setUp() {
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        lenient().when(frontServerService.listFrontServers()).thenReturn(Collections.emptyList());
        sslUtilMock = mockStatic(SslCertificateUtil.class);
    }

    @AfterEach
    void tearDown() {
        sslUtilMock.close();
    }

    private TopLevelDomain buildDomain(String name, Long companyId,
                                        LocalDateTime certExpiry, LocalDateTime domainExpiry) {
        SystemUser owner = SystemUser.builder().build();
        setId(owner, 100L);

        SSLCertificate sslCert = certExpiry != null
                ? SSLCertificate.builder()
                    .certificateExpiryDate(certExpiry)
                    .isCompleted(true).isSuccess(true).build()
                : null;

        TopLevelDomain domain = TopLevelDomain.builder()
                .name(name)
                .sslCertificate(sslCert)
                .expiryDate(domainExpiry)
                .deletionNoticeCount(0)
                .build();
        domain.setOwner(owner);
        domain.setCompanyId(companyId);
        setId(domain, 1L);

        sslUtilMock.when(() -> SslCertificateUtil.getExpiryDate(domain)).thenReturn(certExpiry);

        return domain;
    }

    private void setId(Object entity, Long id) {
        try {
            Class<?> clazz = entity.getClass();
            while (clazz != null) {
                try {
                    var field = clazz.getDeclaredField("id");
                    field.setAccessible(true);
                    field.set(entity, id);
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void setupTransactionTemplate() {
        when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(inv -> {
                    TransactionCallback<?> cb = inv.getArgument(0);
                    return cb.doInTransaction(null);
                });
    }

    private void setupCompany(Long companyId) {
        lenient().when(companyService.companyCached(companyId))
                .thenReturn(Company.builder().build());
    }

    // --- 测试用例 ---

    @Test
    @DisplayName("开发环境应跳过巡检")
    void shouldSkipInDevProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        task.checkDomainExpiry();
        verifyNoInteractions(topLevelDomainRepository);
    }

    @Test
    @DisplayName("无域名时应正常完成")
    void shouldHandleEmptyDomainList() {
        when(topLevelDomainRepository.findAllValidDomains()).thenReturn(Collections.emptyList());
        task.checkDomainExpiry();
        verify(topLevelDomainRepository).findAllValidDomains();
        verifyNoInteractions(noticeService);
    }

    @Test
    @DisplayName("证书和域名都正常时不发通知")
    void shouldNotNotifyWhenEverythingNormal() {
        TopLevelDomain domain = buildDomain("normal.com", 1L,
                LocalDateTime.now().plusDays(30), LocalDateTime.now().plusDays(365));
        when(topLevelDomainRepository.findAllValidDomains()).thenReturn(List.of(domain));
        setupTransactionTemplate();
        setupCompany(1L);

        task.checkDomainExpiry();

        verifyNoInteractions(noticeService);
    }

    @Test
    @DisplayName("证书3天内过期应发送即将过期警告")
    void shouldWarnWhenCertExpiringWithin3Days() {
        TopLevelDomain domain = buildDomain("expiring-cert.com", 1L,
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(365));
        when(topLevelDomainRepository.findAllValidDomains()).thenReturn(List.of(domain));
        setupTransactionTemplate();
        setupCompany(1L);

        task.checkDomainExpiry();

        verify(noticeService).createNotice(
                eq("域名证书即将过期"), contains("expiring-cert.com"), eq("DOMAIN"), eq(100L));
    }

    @Test
    @DisplayName("证书已过期不到5天应发送过期通知")
    void shouldNotifyWhenCertExpiredLessThan5Days() {
        TopLevelDomain domain = buildDomain("expired-cert.com", 1L,
                LocalDateTime.now().minusDays(3), LocalDateTime.now().plusDays(365));
        when(topLevelDomainRepository.findAllValidDomains()).thenReturn(List.of(domain));
        setupTransactionTemplate();
        setupCompany(1L);

        task.checkDomainExpiry();

        verify(noticeService).createNotice(
                eq("域名证书已过期"), contains("expired-cert.com"), eq("DOMAIN"), eq(100L));
    }

    @Test
    @DisplayName("证书过期超5天应进入删除流程发送删除预告")
    void shouldEnterDeletionFlowWhenCertExpired5Days() {
        TopLevelDomain domain = buildDomain("old-cert.com", 1L,
                LocalDateTime.now().minusDays(6), LocalDateTime.now().plusDays(365));
        when(topLevelDomainRepository.findAllValidDomains()).thenReturn(List.of(domain));
        setupTransactionTemplate();
        setupCompany(1L);

        task.checkDomainExpiry();

        verify(topLevelDomainRepository).save(domain);
        verify(noticeService).createNotice(
                eq("域名即将被删除"), contains("old-cert.com"), eq("DOMAIN"), eq(100L));
        assertEquals(1, domain.getDeletionNoticeCount());
    }

    @Test
    @DisplayName("通知3次后应执行自动删除")
    void shouldDeleteAfterMaxNotices() {
        TopLevelDomain domain = buildDomain("auto-delete.com", 1L,
                LocalDateTime.now().minusDays(10), LocalDateTime.now().plusDays(365));
        domain.setDeletionNoticeCount(3);
        when(topLevelDomainRepository.findAllValidDomains()).thenReturn(List.of(domain));
        setupTransactionTemplate();
        setupCompany(1L);

        task.checkDomainExpiry();

        verify(topLevelDomainService).delete(1L);
        verify(noticeService).createNotice(
                eq("域名已被自动删除"), contains("auto-delete.com"), eq("DOMAIN"), eq(100L));
    }

    @Test
    @DisplayName("域名注册3天内过期应发送即将过期警告")
    void shouldWarnWhenDomainExpiringWithin3Days() {
        TopLevelDomain domain = buildDomain("domain-expiring.com", 1L,
                LocalDateTime.now().plusDays(30), LocalDateTime.now().plusDays(2));
        when(topLevelDomainRepository.findAllValidDomains()).thenReturn(List.of(domain));
        setupTransactionTemplate();
        setupCompany(1L);

        task.checkDomainExpiry();

        verify(noticeService).createNotice(
                eq("域名即将过期"), contains("domain-expiring.com"), eq("DOMAIN"), eq(100L));
    }

    @Test
    @DisplayName("域名注册已过期不到5天应发送过期通知")
    void shouldNotifyWhenDomainExpiredLessThan5Days() {
        TopLevelDomain domain = buildDomain("domain-expired.com", 1L,
                LocalDateTime.now().plusDays(30), LocalDateTime.now().minusDays(3));
        when(topLevelDomainRepository.findAllValidDomains()).thenReturn(List.of(domain));
        setupTransactionTemplate();
        setupCompany(1L);

        task.checkDomainExpiry();

        verify(noticeService).createNotice(
                eq("域名已过期"), contains("domain-expired.com"), eq("DOMAIN"), eq(100L));
    }

    @Test
    @DisplayName("owner为null时应跳过通知但不报错")
    void shouldHandleNullOwnerGracefully() {
        TopLevelDomain domain = buildDomain("no-owner.com", 1L,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(365));
        domain.setOwner(null);
        when(topLevelDomainRepository.findAllValidDomains()).thenReturn(List.of(domain));
        setupTransactionTemplate();
        setupCompany(1L);

        task.checkDomainExpiry();

        verify(noticeService, never()).createNotice(any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("单个域名处理失败不影响其他域名")
    void shouldContinueAfterSingleDomainFailure() {
        TopLevelDomain domain1 = buildDomain("fail.com", 1L,
                LocalDateTime.now().plusDays(30), LocalDateTime.now().plusDays(365));
        TopLevelDomain domain2 = buildDomain("ok.com", 2L,
                LocalDateTime.now().plusDays(30), LocalDateTime.now().plusDays(365));
        setId(domain2, 2L);

        when(topLevelDomainRepository.findAllValidDomains()).thenReturn(List.of(domain1, domain2));
        setupTransactionTemplate();
        when(companyService.companyCached(1L)).thenThrow(new RuntimeException("模拟异常"));
        setupCompany(2L);

        task.checkDomainExpiry();

        verify(companyService).companyCached(1L);
        verify(companyService).companyCached(2L);
    }

    @Test
    @DisplayName("恢复正常后应重置deletionNoticeCount")
    void shouldResetDeletionNoticeCountWhenRecovered() {
        TopLevelDomain domain = buildDomain("recovered.com", 1L,
                LocalDateTime.now().plusDays(30), LocalDateTime.now().plusDays(365));
        domain.setDeletionNoticeCount(2);
        when(topLevelDomainRepository.findAllValidDomains()).thenReturn(List.of(domain));
        setupTransactionTemplate();
        setupCompany(1L);

        task.checkDomainExpiry();

        verify(topLevelDomainRepository).save(domain);
        assertEquals(0, domain.getDeletionNoticeCount());
    }

    @Test
    @DisplayName("证书过期和域名过期同时发生时应优先处理证书过期")
    void shouldPrioritizeCertExpiryOverDomainExpiry() {
        TopLevelDomain domain = buildDomain("both-expired.com", 1L,
                LocalDateTime.now().minusDays(6), LocalDateTime.now().minusDays(6));
        when(topLevelDomainRepository.findAllValidDomains()).thenReturn(List.of(domain));
        setupTransactionTemplate();
        setupCompany(1L);

        task.checkDomainExpiry();

        verify(noticeService).createNotice(
                eq("域名即将被删除"), contains("证书已过期"), eq("DOMAIN"), eq(100L));
    }
}
