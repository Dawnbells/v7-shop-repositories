package cn.v7soft.admin.events.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;


@Getter
public class CertificateRequestEvent extends ApplicationEvent {
    private final long domainId;
    private final String sslServer;

    public CertificateRequestEvent(Object source, long domainId, String sslServer) {
        super(source);
        this.domainId = domainId;
        this.sslServer = sslServer;
    }

}
