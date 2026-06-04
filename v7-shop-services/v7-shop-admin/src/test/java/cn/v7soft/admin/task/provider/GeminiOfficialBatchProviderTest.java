package cn.v7soft.admin.task.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class GeminiOfficialBatchProviderTest {

    @Test
    void batchJobTimeoutWaitsOneDayBeforeCancelling() throws Exception {
        Field field = GeminiOfficialBatchProvider.class.getDeclaredField("BATCH_JOB_TIMEOUT_MS");
        field.setAccessible(true);

        assertEquals(Duration.ofDays(1).toMillis(), field.getLong(null));
    }
}
