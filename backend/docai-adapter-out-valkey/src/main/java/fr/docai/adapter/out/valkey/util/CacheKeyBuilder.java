package fr.docai.adapter.out.valkey.util;

/**
 * Centralise la construction des clés Valkey — format exact défini par ADR-003.
 * Toute modification de format doit passer par cette classe uniquement.
 */
public final class CacheKeyBuilder {

    private CacheKeyBuilder() {}

    public static String extractionKey(String sha256) {
        return "extraction:" + sha256;
    }

    public static String inseeSiretKey(String siret) {
        return "insee:siret:" + siret;
    }

    public static String banAddressKey(String hash) {
        return "ban:address:" + hash;
    }

    public static String rppsKey(String numero) {
        return "rpps:" + numero;
    }

    public static String jwtBlacklistKey(String jti) {
        return "jwt:blacklist:" + jti;
    }

    public static String idempotentKafkaKey(String topic, int partition, long offset) {
        return "idempotent:" + topic + ":" + partition + ":" + offset;
    }

    public static String idempotencyKey(String idempotencyHeader) {
        return "idempotency:" + idempotencyHeader;
    }

    public static String quotaKey(String tenantId, int year, int month) {
        return "quota:" + tenantId + ":" + year + "-" + String.format("%02d", month);
    }

    public static String classificationKey(String sha256) {
        return "classification:" + sha256;
    }
}
