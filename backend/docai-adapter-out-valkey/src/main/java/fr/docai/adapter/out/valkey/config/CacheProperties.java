package fr.docai.adapter.out.valkey.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Objects;

/**
 * Propriétés de cache mappées depuis application.yml (docai.cache.*).
 * jwt-blacklist et quota exclus — TTL calculé dynamiquement à l'exécution, non mappé ici.
 */
@Validated
@ConfigurationProperties(prefix = "docai.cache")
public record CacheProperties(
    @NotNull @Valid CacheTtlEntry extractionLlm,
    @NotNull @Valid CacheTtlEntry inseeSiret,
    @NotNull @Valid CacheTtlEntry banAddress,
    @NotNull @Valid CacheTtlEntry rpps,
    @NotNull @Valid CacheTtlEntry classification,
    @NotNull @Valid CacheTtlEntry idempotency,
    @NotNull @Valid CacheTtlEntry uploadIdempotency
) {
    public CacheProperties {
        Objects.requireNonNull(extractionLlm,     "docai.cache.extraction-llm must be configured");
        Objects.requireNonNull(inseeSiret,        "docai.cache.insee-siret must be configured");
        Objects.requireNonNull(banAddress,        "docai.cache.ban-address must be configured");
        Objects.requireNonNull(rpps,              "docai.cache.rpps must be configured");
        Objects.requireNonNull(classification,    "docai.cache.classification must be configured");
        Objects.requireNonNull(idempotency,       "docai.cache.idempotency must be configured");
        Objects.requireNonNull(uploadIdempotency, "docai.cache.upload-idempotency must be configured");
    }

    public record CacheTtlEntry(@NotNull Duration ttl) {
        public CacheTtlEntry {
            if (ttl != null && (ttl.isZero() || ttl.isNegative())) {
                throw new IllegalArgumentException("Cache TTL must be strictly positive, got: " + ttl);
            }
        }
    }
}
