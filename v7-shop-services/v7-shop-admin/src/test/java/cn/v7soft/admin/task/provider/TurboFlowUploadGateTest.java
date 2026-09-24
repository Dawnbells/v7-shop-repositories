package cn.v7soft.admin.task.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class TurboFlowUploadGateTest {

    private final MutableClock clock = new MutableClock();

    @Test
    void pluginsBehindTheSameIpShareTheSlotsWhileOtherIpsAreUnaffected() {
        TurboFlowUploadGate gate = new TurboFlowUploadGate(2, clock);
        assertNotNull(gate.tryAcquire("1.1.1.1", "a"));
        assertNotNull(gate.tryAcquire("1.1.1.1", "b"));
        assertNull(gate.tryAcquire("1.1.1.1", "c"));
        assertNotNull(gate.tryAcquire("2.2.2.2", "d"));
    }

    @Test
    void retryOfTheSameAssignmentKeepsItsPlaceButGetsANewSlotId() {
        TurboFlowUploadGate gate = new TurboFlowUploadGate(1, clock);
        String first = gate.tryAcquire("1.1.1.1", "a");
        String retry = gate.tryAcquire("1.1.1.1", "a");
        assertNotNull(retry);
        assertNotEquals(first, retry);
        assertNull(gate.tryAcquire("1.1.1.1", "b"));
    }

    @Test
    void lateReleaseOfAnEarlierAttemptDoesNotFreeTheRetrysSlot() {
        TurboFlowUploadGate gate = new TurboFlowUploadGate(1, clock);
        String stale = gate.tryAcquire("1.1.1.1", "a");
        String retry = gate.tryAcquire("1.1.1.1", "a");
        gate.release("a", stale);
        assertNull(gate.tryAcquire("1.1.1.1", "b"));
        gate.release("a", retry);
        assertNotNull(gate.tryAcquire("1.1.1.1", "b"));
    }

    @Test
    void releaseWithoutSlotIdFreesTheAssignmentEvenIfTheEgressIpChanged() {
        TurboFlowUploadGate gate = new TurboFlowUploadGate(1, clock);
        gate.tryAcquire("1.1.1.1", "a");
        gate.release("a", null);
        assertNotNull(gate.tryAcquire("1.1.1.1", "b"));
    }

    @Test
    void abandonedSlotsAreReclaimedAfterTheLeaseForEveryIp() {
        TurboFlowUploadGate gate = new TurboFlowUploadGate(1, clock);
        gate.tryAcquire("1.1.1.1", "crashed-plugin");
        gate.tryAcquire("3.3.3.3", "ip-changed-and-never-came-back");
        clock.advance(TurboFlowUploadGate.SLOT_LEASE.minusSeconds(1));
        assertNull(gate.tryAcquire("1.1.1.1", "b"));
        clock.advance(Duration.ofSeconds(1));
        assertNotNull(gate.tryAcquire("1.1.1.1", "b"));
        // 3.3.3.3 不会再来领，它的过期名额也要被清掉
        assertEquals(1, gate.trackedIpCount());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-23T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
