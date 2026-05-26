package fr.docai.adapter.out.valkey;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JitterTtlTest {

    @Test
    void withJitter_shouldReturnDurationWithinTenPercentBounds() {
        Duration base = Duration.ofHours(24);

        Duration result = JitterTtl.withJitter(base);

        long minMillis = (long) (base.toMillis() * 0.9);
        long maxMillis = (long) (base.toMillis() * 1.1);
        assertThat(result.toMillis()).isBetween(minMillis, maxMillis);
    }

    @RepeatedTest(100)
    void withJitter_shouldNeverExceedBounds() {
        Duration base = Duration.ofDays(7);

        Duration result = JitterTtl.withJitter(base);

        assertThat(result.toMillis())
            .isGreaterThanOrEqualTo((long) (base.toMillis() * 0.9))
            .isLessThanOrEqualTo((long) (base.toMillis() * 1.1));
    }

    @Test
    void withJitter_shouldPreserveBaseDurationOrder() {
        Duration short_ = Duration.ofHours(1);
        Duration long_ = Duration.ofDays(30);

        assertThat(JitterTtl.withJitter(short_).toMillis())
            .isLessThan(JitterTtl.withJitter(long_).toMillis());
    }
}
