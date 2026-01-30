package cn.v7soft.admin.configurer;

import cn.v7soft.admin.events.CertificateRequestPublisher;
import cn.v7soft.admin.service.ITopLevelDomainService;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import cn.v7soft.dao.enums.CertificateRequestStatus;
import lombok.AllArgsConstructor;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 初始化域名证书申请
 */
@Component
@AllArgsConstructor
public class SslApplicationRunner implements ApplicationRunner {
   private final ITopLevelDomainService topLevelDomainService;
   private final CertificateRequestPublisher certificateRequestPublisher;
    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<TopLevelDomain> allQueueOrRequesting = topLevelDomainService.findAllQueueOrRequesting();
        if (allQueueOrRequesting.isEmpty()) {
            return;
        }
        for (TopLevelDomain topLevelDomain: allQueueOrRequesting) {
            if (topLevelDomain.getCertificateRequestStatus() != CertificateRequestStatus.QUEUE) {
                topLevelDomain.setCertificateRequestStatus(CertificateRequestStatus.QUEUE);
                topLevelDomainService.saveAndFlush(topLevelDomain);
            }
            certificateRequestPublisher.requestCertificate(topLevelDomain.getId());
        }
    }

}
