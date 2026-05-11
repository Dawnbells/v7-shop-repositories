package cn.v7soft.admin.events;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import cn.v7soft.admin.events.event.CertificateRequestEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CertificateRequestPublisher {
    private final ApplicationEventPublisher publisher;

    public CertificateRequestPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void requestCertificate(long domainId) {
        requestCertificate(domainId, null);
    }

    public void requestCertificate(long domainId, String server) {
        publisher.publishEvent(new CertificateRequestEvent(this, domainId, server));
    }
}
