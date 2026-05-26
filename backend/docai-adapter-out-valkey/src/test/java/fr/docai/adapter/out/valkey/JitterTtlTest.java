package fr.docai.adapter.out.valkey;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class JitterTtlTest {

    private static final double LOWER_BOUND = 0.9;
    private static final double UPPER_BOUND = 1.1;

    @Test
    void withJitter_shouldReturnDurationWithinTenPercentBounds() {
        Duration base = Duration.ofHours(24);

        assertWithinJitterBounds(base, JitterTtl.withJitter(base));
    }

    @RepeatedTest(1000)
    void withJitter_shouldNeverExceedBounds() {
        Duration base = Duration.ofDays(7);

        assertWithinJitterBounds(base, JitterTtl.withJitter(base));
    }

    @Test
    void withJitter_shouldRejectNull() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> JitterTtl.withJitter(null))
            .withMessageContaining("null");
    }

    @Test
    void withJitter_shouldRejectZero() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> JitterTtl.withJitter(Duration.ZERO))
            .withMessageContaining("zero");
    }

    @Test
    void withJitter_shouldRejectNegative() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> JitterTtl.withJitter(Duration.ofSeconds(-1)));
    }

    @Test
    void withJitterFactor_shouldRespectCustomFactor() {
        Duration base = Duration.ofHours(1);
        double factor = 0.20;

        Duration result = JitterTtl.withJitter(base, factor);

        assertThat(result.toMillis())
            .isGreaterThanOrEqualTo((long) (base.toMillis() * (1.0 - factor)))
            .isLessThanOrEqualTo((long) (base.toMillis() * (1.0 + factor)));
    }

    @Test
    void withJitterFactor_shouldRejectOutOfRangeFactor() {
        Duration base = Duration.ofHours(1);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> JitterTtl.withJitter(base, 0.0))
            .withMessageContaining("jitterFactor");

        assertThatIllegalArgumentException()
            .isThrownBy(() -> JitterTtl.withJitter(base, 0.26))
            .withMessageContaining("jitterFactor");

        assertThatIllegalArgumentException()
            .isThrownBy(() -> JitterTtl.withJitter(base, -0.1))
            .withMessageContaining("jitterFactor");
    }

    private static void assertWithinJitterBounds(Duration base, Duration result) {
        assertThat(result.toMillis())
            .isGreaterThanOrEqualTo((long) (base.toMillis() * LOWER_BOUND))
            .isLessThanOrEqualTo((long) (base.toMillis() * UPPER_BOUND));
    }
}
