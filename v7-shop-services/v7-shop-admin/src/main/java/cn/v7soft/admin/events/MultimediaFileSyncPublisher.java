package cn.v7soft.admin.events;

import cn.v7soft.admin.events.event.CertificateRequestEvent;
import cn.v7soft.admin.events.event.MultimediaFileSyncEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class MultimediaFileSyncPublisher {
    private final ApplicationEventPublisher publisher;

    public MultimediaFileSyncPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public boolean submitSync() {
        publisher.publishEvent(new MultimediaFileSyncEvent(this));
        return true;
    }
}
