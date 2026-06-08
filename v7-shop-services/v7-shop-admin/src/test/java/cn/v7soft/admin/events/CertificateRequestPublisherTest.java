package cn.v7soft.admin.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import cn.v7soft.admin.events.event.CertificateRequestEvent;
import cn.v7soft.admin.events.trackers.CertificateQueueTracker;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CertificateRequestPublisherTest {

    @Mock private ApplicationEventPublisher publisher;
    @Mock private CertificateQueueTracker queueTracker;

    @InjectMocks private CertificateRequestPublisher certificateRequestPublisher;

    @Test
    @DisplayName("先入队再发布事件")
    void shouldEnqueueBeforePublish() {
        certificateRequestPublisher.requestCertificate(42L);

        InOrder inOrder = inOrder(queueTracker, publisher);
        inOrder.verify(queueTracker).enqueue(42L);
        inOrder.verify(publisher).publishEvent(any(CertificateRequestEvent.class));
    }

    @Test
    @DisplayName("发布失败时回滚出队并抛出异常")
    void shouldRemoveFromQueueWhenPublishThrows() {
        doThrow(new RuntimeException("队列已满"))
                .when(publisher).publishEvent(any(CertificateRequestEvent.class));

        assertThrows(RuntimeException.class, () -> certificateRequestPublisher.requestCertificate(42L));

        verify(queueTracker).enqueue(42L);
        verify(queueTracker).remove(42L);
    }
}
