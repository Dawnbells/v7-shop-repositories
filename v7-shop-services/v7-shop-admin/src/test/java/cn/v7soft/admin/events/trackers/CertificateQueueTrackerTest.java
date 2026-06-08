package cn.v7soft.admin.events.trackers;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CertificateQueueTrackerTest {

    @Test
    @DisplayName("按入队顺序给出从1开始的排位")
    void shouldReturnPositionsInEnqueueOrder() {
        CertificateQueueTracker tracker = new CertificateQueueTracker();
        tracker.enqueue(10L);
        tracker.enqueue(20L);
        tracker.enqueue(30L);

        Map<Long, Integer> snapshot = tracker.positionSnapshot();
        assertEquals(Integer.valueOf(1), snapshot.get(10L));
        assertEquals(Integer.valueOf(2), snapshot.get(20L));
        assertEquals(Integer.valueOf(3), snapshot.get(30L));
    }

    @Test
    @DisplayName("移除队首后，后续域名排位前移")
    void shouldShiftPositionsAfterRemoveHead() {
        CertificateQueueTracker tracker = new CertificateQueueTracker();
        tracker.enqueue(10L);
        tracker.enqueue(20L);
        tracker.enqueue(30L);

        tracker.remove(10L);

        Map<Long, Integer> snapshot = tracker.positionSnapshot();
        assertNull(snapshot.get(10L));
        assertEquals(Integer.valueOf(1), snapshot.get(20L));
        assertEquals(Integer.valueOf(2), snapshot.get(30L));
    }

    @Test
    @DisplayName("重复入队同一域名不产生重复、保持原位")
    void shouldDedupeOnDuplicateEnqueue() {
        CertificateQueueTracker tracker = new CertificateQueueTracker();
        tracker.enqueue(10L);
        tracker.enqueue(20L);
        tracker.enqueue(10L);

        Map<Long, Integer> snapshot = tracker.positionSnapshot();
        assertEquals(2, snapshot.size());
        assertEquals(Integer.valueOf(1), snapshot.get(10L));
        assertEquals(Integer.valueOf(2), snapshot.get(20L));
    }

    @Test
    @DisplayName("未入队域名查不到排位（null）")
    void shouldReturnNullForUnknownDomain() {
        CertificateQueueTracker tracker = new CertificateQueueTracker();
        tracker.enqueue(10L);

        assertNull(tracker.positionSnapshot().get(999L));
    }

    @Test
    @DisplayName("入队后移除（回滚）不残留")
    void shouldNotLeakAfterEnqueueThenRemove() {
        CertificateQueueTracker tracker = new CertificateQueueTracker();
        tracker.enqueue(10L);
        tracker.remove(10L);

        assertEquals(0, tracker.positionSnapshot().size());
    }
}
