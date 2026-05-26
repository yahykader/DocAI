package fr.docai.adapter.out.valkey.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Propriétés de cache mappées depuis application.yml (docai.cache.*).
 * TTLs dynamiques (jwt-blacklist=expiry JWT, quota=fin de mois) calculés à l'exécution.
 */
@ConfigurationProperties(prefix = "docai.cache")
public record CacheProperties(
    CacheTtlEntry extractionLlm,
    CacheTtlEntry inseeSiret,
    CacheTtlEntry banAddress,
    CacheTtlEntry rpps,
    CacheTtlEntry classification,
    CacheTtlEntry idempotency,
    CacheTtlEntry uploadIdempotency
) {
    public record CacheTtlEntry(Duration ttl) {}
}
