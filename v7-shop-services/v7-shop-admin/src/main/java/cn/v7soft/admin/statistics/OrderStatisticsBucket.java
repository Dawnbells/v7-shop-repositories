package cn.v7soft.admin.statistics;

import java.time.Instant;
import java.time.LocalDateTime;

public record OrderStatisticsBucket(
        String key,
        Instant startInstant,
        Instant endInstant,
        LocalDateTime queryStart,
        LocalDateTime queryEnd,
        boolean partial
) {
}
