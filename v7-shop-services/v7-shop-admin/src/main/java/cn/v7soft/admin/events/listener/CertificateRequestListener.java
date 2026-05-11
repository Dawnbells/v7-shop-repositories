package cn.v7soft.admin.events.listener;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.events.event.CertificateRequestEvent;
import cn.v7soft.admin.service.ICloudPlatformAccountService;
import cn.v7soft.admin.service.ISystemSettingsService;
import cn.v7soft.admin.service.ITopLevelDomainService;
import cn.v7soft.admin.service.ssl.ISslCertificateRequester;
import cn.v7soft.admin.service.ssl.PlaceholderCertHolder;
import cn.v7soft.admin.service.ssl.SslResult;
import cn.v7soft.admin.service.ssl.UnsupportedSslCertificateRequester;
import cn.v7soft.common.utils.SslCertificateUtil;
import cn.v7soft.dao.entities.primary.CloudPlatformAccount;
import cn.v7soft.dao.entities.primary.SSLCertificate;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import cn.v7soft.dao.enums.CertificateRequestStatus;
import kotlin.text.Charsets;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hibernate.Hibernate;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 自动证书请求类
 */
@Slf4j
@Component
@AllArgsConstructor
public class CertificateRequestListener {
    private final ISystemSettingsService systemSettingsService;
    private final ITopLevelDomainService topLevelDomainService;
    private final ICloudPlatformAccountService cloudPlatformAccountService;
    private final PlaceholderCertHolder placeholderCertHolder;

    @EventListener
    @Async("certificateRequestAsyncExecutor")
    public void handleCertificateRequest(CertificateRequestEvent event) throws IOException {
        TopLevelDomain domain = null;
        boolean shouldPushCertificateChange = false;
        try {
            domain = topLevelDomainService.getById(event.getDomainId());
            if (domain.getCloudPlatformAccount() == null) {
                return;
            }
            domain.setCertificateRequestStatus(CertificateRequestStatus.REQUESTING);
            topLevelDomainService.saveAndFlush(domain);

            String sslServer = StrUtil.isBlank(event.getSslServer()) ? systemSettingsService.getSslServer() : event.getSslServer();
            log.debug("handle certificate request: " + domain.getName());
            Hibernate.initialize(domain.getCloudPlatformAccount());
            CloudPlatformAccount cloudPlatformAccount = domain.getCloudPlatformAccount();
            ISslCertificateRequester certificateRequester = cloudPlatformAccountService.getCertificateRequester(cloudPlatformAccount);
            SslResult sslResult = certificateRequester.handleRequestSslCertificate(domain, sslServer);
            log.debug("sslResult = " + JSONUtil.toJsonStr(sslResult));

            SSLCertificate sslCertificate = SSLCertificate.builder()
                    .isCompleted(sslResult.isCompleted())
                    .isSuccess(sslResult.isSuccess())
                    .isError(sslResult.isError())
                    .result(sslResult.getResult())
                    .errorMsg(sslResult.getErrorMsg())
                    .sslPushMsg("")
                    .errLog(sslResult.getErrLog())
                    .certificateExpiryDate(SslCertificateUtil.getExpiryDate(domain))
                    .build();
            domain.setSslCertificate(sslCertificate);

            if (sslResult.isSuccess()) {
                domain.setCertificateRequestStatus(CertificateRequestStatus.FINISH);
            } else {
                domain.setCertificateRequestStatus(CertificateRequestStatus.ERROR);
            }
            topLevelDomainService.saveAndFlush(domain);

            shouldPushCertificateChange = sslResult.isSuccess();
        } catch (Throwable t) {
            log.error("handle certificate request error: ", t);
        } finally {
            log.debug("handle certificate finally push");
            if (domain != null) {
                UnsupportedSslCertificateRequester.builder().placeholderCertHolder(placeholderCertHolder).build().checkAndWriteDefault(domain);

                if (shouldPushCertificateChange) {
                    String sslPushMsg = executePushScript();
                    log.debug("ssl push msg = " + sslPushMsg);
                    SSLCertificate sslCertificate = domain.getSslCertificate();
                    if (sslCertificate == null) {
                        sslCertificate = SSLCertificate.builder().build();
                    }
                    sslCertificate.setSslPushMsg(sslPushMsg);
                    domain.setSslCertificate(sslCertificate);
                }
                if (domain.getCertificateRequestStatus() == CertificateRequestStatus.QUEUE) {
                    domain.setCertificateRequestStatus(CertificateRequestStatus.IDLE);
                } else if (domain.getCertificateRequestStatus() == CertificateRequestStatus.REQUESTING) {
                    domain.setCertificateRequestStatus(CertificateRequestStatus.ERROR);
                }
                topLevelDomainService.saveAndFlush(domain);
            }
        }
    }

    protected String executePushScript() throws IOException {
        Process process = Runtime.getRuntime().exec("sh /scripts/push.sh");
        return IoUtil.read(process.getInputStream(), Charsets.UTF_8);
    }
}
