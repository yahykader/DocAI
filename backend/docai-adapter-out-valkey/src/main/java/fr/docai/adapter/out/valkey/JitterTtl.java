package fr.docai.adapter.out.valkey;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ADR-003: jitter ±10% appliqué sur tout TTL — évite le cache stampede.
 */
public final class JitterTtl {

    private static final double DEFAULT_JITTER_FACTOR = 0.10;

    private JitterTtl() {}

    public static Duration withJitter(Duration base) {
        return withJitter(base, DEFAULT_JITTER_FACTOR);
    }

    public static Duration withJitter(Duration base, double jitterFactor) {
        validate(base);
        if (jitterFactor <= 0.0 || jitterFactor > 0.25) {
            throw new IllegalArgumentException(
                "jitterFactor must be in (0.0, 0.25], got: " + jitterFactor);
        }
        double factor = ThreadLocalRandom.current().nextDouble(1.0 - jitterFactor, 1.0 + jitterFactor);
        return Duration.ofMillis((long) (base.toMillis() * factor));
    }

    private static void validate(Duration base) {
        if (base == null) {
            throw new IllegalArgumentException("base duration must not be null");
        }
        if (base.isZero()) {
            throw new IllegalArgumentException("base duration must be strictly positive, got zero");
        }
        if (base.isNegative()) {
            throw new IllegalArgumentException("base duration must be strictly positive, got " + base);
        }
    }
}
