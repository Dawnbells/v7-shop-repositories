package cn.v7soft.admin.events;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import cn.v7soft.admin.events.event.CertificateRequestEvent;
import cn.v7soft.admin.events.trackers.CertificateQueueTracker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CertificateRequestPublisher {
    private final ApplicationEventPublisher publisher;
    private final CertificateQueueTracker queueTracker;

    public CertificateRequestPublisher(ApplicationEventPublisher publisher, CertificateQueueTracker queueTracker) {
        this.publisher = publisher;
        this.queueTracker = queueTracker;
    }

    public void requestCertificate(long domainId) {
        requestCertificate(domainId, null);
    }

    public void requestCertificate(long domainId, String server) {
        // 必须先入队再发布：发布后执行器线程可能立刻开始处理并 remove，顺序反了会"先移除后入队"导致泄漏
        queueTracker.enqueue(domainId);
        try {
            publisher.publishEvent(new CertificateRequestEvent(this, domainId, server));
        } catch (RuntimeException e) {
            // 提交被拒（如队列满）则回滚出队，避免残留
            queueTracker.remove(domainId);
            throw e;
        }
    }
}
