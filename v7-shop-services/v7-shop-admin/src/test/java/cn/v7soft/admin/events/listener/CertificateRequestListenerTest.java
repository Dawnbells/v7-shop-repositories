package cn.v7soft.admin.events.listener;

import java.io.IOException;
import java.time.LocalDateTime;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import cn.v7soft.admin.events.event.CertificateRequestEvent;
import cn.v7soft.admin.events.trackers.CertificateQueueTracker;
import cn.v7soft.admin.service.ICloudPlatformAccountService;
import cn.v7soft.admin.service.ISystemSettingsService;
import cn.v7soft.admin.service.ITopLevelDomainService;
import cn.v7soft.admin.service.ssl.ISslCertificateRequester;
import cn.v7soft.admin.service.ssl.PlaceholderCertHolder;
import cn.v7soft.admin.service.ssl.SslResult;
import cn.v7soft.common.utils.SslCertificateUtil;
import cn.v7soft.dao.entities.primary.CloudPlatformAccount;
import cn.v7soft.dao.entities.primary.SSLCertificate;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import cn.v7soft.dao.enums.CertificateRequestStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CertificateRequestListenerTest {

    @Mock private ISystemSettingsService systemSettingsService;
    @Mock private ITopLevelDomainService topLevelDomainService;
    @Mock private ICloudPlatformAccountService cloudPlatformAccountService;
    @Mock private PlaceholderCertHolder placeholderCertHolder;
    @Mock private ISslCertificateRequester certificateRequester;
    @Mock private CertificateQueueTracker queueTracker;

    @Spy
    @InjectMocks
    private CertificateRequestListener listener;

    private MockedStatic<Hibernate> hibernateMock;
    private MockedStatic<SslCertificateUtil> sslUtilMock;

    @BeforeEach
    void setUp() {
        hibernateMock = mockStatic(Hibernate.class);
        sslUtilMock = mockStatic(SslCertificateUtil.class);
        sslUtilMock.when(() -> SslCertificateUtil.getRealExpiryDate(any(TopLevelDomain.class)))
                .thenReturn(LocalDateTime.now().plusDays(90));
        lenient().when(cloudPlatformAccountService.getCertificateRequester(any(CloudPlatformAccount.class)))
                .thenReturn(certificateRequester);
        lenient().when(systemSettingsService.getSslServer()).thenReturn("");
    }

    @AfterEach
    void tearDown() {
        hibernateMock.close();
        sslUtilMock.close();
    }

    private TopLevelDomain buildDomain(boolean withCloudPlatform) {
        CloudPlatformAccount account = withCloudPlatform
                ? CloudPlatformAccount.builder().id(1L).build()
                : null;
        TopLevelDomain domain = TopLevelDomain.builder()
                .name("example.com")
                .certificateRequestStatus(CertificateRequestStatus.QUEUE)
                .cloudPlatformAccount(account)
                .build();
        domain.setCompanyId(1L);
        return domain;
    }

    @Test
    @DisplayName("证书申请成功 → 执行 push.sh、状态 FINISH、写入 sslPushMsg")
    void shouldPushWhenCertificateRequestSuccess() throws IOException {
        TopLevelDomain domain = buildDomain(true);
        lenient().when(topLevelDomainService.getById(1L)).thenReturn(domain);

        SslResult success = SslResult.builder()
                .isSuccess(true).isCompleted(true).isError(false)
                .result("Successfully received certificate.").errLog("").errorMsg("")
                .build();
        lenient().when(certificateRequester.handleRequestSslCertificate(any(), any())).thenReturn(success);

        doReturn("push ok").when(listener).executePushScript();

        listener.handleCertificateRequest(new CertificateRequestEvent(this, 1L, null));

        verify(listener, times(1)).executePushScript();
        assertEquals(CertificateRequestStatus.FINISH, domain.getCertificateRequestStatus());
        assertNotNull(domain.getSslCertificate());
        assertEquals("push ok", domain.getSslCertificate().getSslPushMsg());
    }

    @Test
    @DisplayName("证书申请失败 → 不执行 push.sh、状态 ERROR、不写入 certificateExpiryDate")
    void shouldNotPushWhenCertificateRequestFailure() throws IOException {
        TopLevelDomain domain = buildDomain(true);
        lenient().when(topLevelDomainService.getById(1L)).thenReturn(domain);

        SslResult failure = SslResult.builder()
                .isSuccess(false).isCompleted(true).isError(false)
                .result("dns auth failed").errLog("").errorMsg("dns")
                .build();
        lenient().when(certificateRequester.handleRequestSslCertificate(any(), any())).thenReturn(failure);

        listener.handleCertificateRequest(new CertificateRequestEvent(this, 1L, null));

        verify(listener, never()).executePushScript();
        assertEquals(CertificateRequestStatus.ERROR, domain.getCertificateRequestStatus());
        assertNotNull(domain.getSslCertificate());
        assertNull(domain.getSslCertificate().getCertificateExpiryDate());
        sslUtilMock.verify(() -> SslCertificateUtil.getRealExpiryDate(any(TopLevelDomain.class)), never());
    }

    @Test
    @DisplayName("无云平台账户 → 不发起证书申请、不执行 push.sh")
    void shouldSkipRequestAndPushWhenNoCloudPlatform() throws IOException {
        TopLevelDomain domain = buildDomain(false);
        lenient().when(topLevelDomainService.getById(1L)).thenReturn(domain);

        listener.handleCertificateRequest(new CertificateRequestEvent(this, 1L, null));

        verify(certificateRequester, never()).handleRequestSslCertificate(any(), any());
        verify(listener, never()).executePushScript();
    }

    @Test
    @DisplayName("ssl requester 抛异常 → 不 push、状态从 REQUESTING 回滚为 ERROR")
    void shouldNotPushAndMarkErrorWhenRequesterThrows() throws IOException {
        TopLevelDomain domain = buildDomain(true);
        lenient().when(topLevelDomainService.getById(1L)).thenReturn(domain);
        lenient().when(certificateRequester.handleRequestSslCertificate(any(), any()))
                .thenThrow(new RuntimeException("模拟异常"));

        listener.handleCertificateRequest(new CertificateRequestEvent(this, 1L, null));

        verify(listener, never()).executePushScript();
        assertEquals(CertificateRequestStatus.ERROR, domain.getCertificateRequestStatus());
    }

    @Test
    @DisplayName("申请成功但后处理抛异常 → 不 push、状态 ERROR、不写 sslPushMsg")
    void shouldNotPushWhenSuccessButPostProcessingThrows() throws IOException {
        TopLevelDomain domain = buildDomain(true);
        lenient().when(topLevelDomainService.getById(1L)).thenReturn(domain);

        SslResult success = SslResult.builder()
                .isSuccess(true).isCompleted(true).isError(false)
                .result("Successfully received certificate.").errLog("").errorMsg("")
                .build();
        lenient().when(certificateRequester.handleRequestSslCertificate(any(), any())).thenReturn(success);

        sslUtilMock.when(() -> SslCertificateUtil.getRealExpiryDate(any(TopLevelDomain.class)))
                .thenThrow(new RuntimeException("解析证书过期时间失败"));

        listener.handleCertificateRequest(new CertificateRequestEvent(this, 1L, null));

        verify(listener, never()).executePushScript();
        assertEquals(CertificateRequestStatus.ERROR, domain.getCertificateRequestStatus());
        SSLCertificate sslCertificate = domain.getSslCertificate();
        assertTrue(sslCertificate == null || isBlank(sslCertificate.getSslPushMsg()));
    }

    @Test
    @DisplayName("处理开始即从队列跟踪器移除该域名")
    void shouldRemoveFromQueueTrackerAtStart() throws IOException {
        TopLevelDomain domain = buildDomain(true);
        lenient().when(topLevelDomainService.getById(1L)).thenReturn(domain);

        SslResult success = SslResult.builder()
                .isSuccess(true).isCompleted(true).isError(false)
                .result("ok").errLog("").errorMsg("")
                .build();
        lenient().when(certificateRequester.handleRequestSslCertificate(any(), any())).thenReturn(success);
        doReturn("push ok").when(listener).executePushScript();

        listener.handleCertificateRequest(new CertificateRequestEvent(this, 1L, null));

        verify(queueTracker, times(1)).remove(1L);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }
}
