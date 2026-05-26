package fr.docai.adapter.out.valkey;

import java.time.Duration;

/**
 * ADR-003: jitter ±10% obligatoire sur tout TTL > 1h.
 * Évite le cache stampede (expiration simultanée de milliers d'entrées).
 */
public final class JitterTtl {

    private JitterTtl() {}

    public static Duration withJitter(Duration base) {
        double jitter = 0.9 + (Math.random() * 0.2); // 0.9 à 1.1 (±10%)
        return Duration.ofMillis((long) (base.toMillis() * jitter));
    }
}
