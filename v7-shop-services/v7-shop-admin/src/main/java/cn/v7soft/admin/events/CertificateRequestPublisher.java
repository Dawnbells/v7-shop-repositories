package cn.v7soft.admin.events;

import cn.v7soft.admin.events.event.CertificateRequestEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

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
        CertificateRequestEvent event = new CertificateRequestEvent(this, domainId, server);
        publisher.publishEvent(event);
    }
}
