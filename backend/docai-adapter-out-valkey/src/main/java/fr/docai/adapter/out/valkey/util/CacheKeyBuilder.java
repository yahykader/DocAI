package fr.docai.adapter.out.valkey.util;

/**
 * Centralise la construction des clés Valkey — format exact défini par ADR-003.
 * Toute modification de format doit passer par cette classe uniquement.
 * Convention: namespace:subnamespace:identifiant (séparateur ':').
 */
public final class CacheKeyBuilder {

    private static final int MONTHS_IN_YEAR = 12;

    private CacheKeyBuilder() {}

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.contains(":")) {
            throw new IllegalArgumentException(
                name + " must not contain ':' (key namespace separator), got: " + value);
        }
        return value;
    }

    public static String extractionKey(String tenantId, String sha256) {
        return "extraction:" + requireNonBlank(tenantId, "tenantId") + ":" + requireNonBlank(sha256, "sha256");
    }

    public static String inseeSiretKey(String siret) {
        return "insee:siret:" + requireNonBlank(siret, "siret");
    }

    public static String banAddressKey(String hash) {
        return "ban:address:" + requireNonBlank(hash, "hash");
    }

    public static String rppsKey(String numero) {
        return "rpps:" + requireNonBlank(numero, "numero");
    }

    public static String jwtBlacklistKey(String jti) {
        return "jwt:blacklist:" + requireNonBlank(jti, "jti");
    }

    public static String idempotentKafkaKey(String topic, int partition, long offset) {
        return "idempotent:" + requireNonBlank(topic, "topic") + ":" + partition + ":" + offset;
    }

    public static String idempotencyKey(String idempotencyHeader) {
        return "idempotency:" + requireNonBlank(idempotencyHeader, "idempotencyHeader");
    }

    public static String quotaKey(String tenantId, int year, int month) {
        if (month < 1 || month > MONTHS_IN_YEAR) {
            throw new IllegalArgumentException("month must be 1-12, got: " + month);
        }
        return "quota:" + requireNonBlank(tenantId, "tenantId") + ":" + year + "-" + String.format("%02d", month);
    }

    public static String classificationKey(String tenantId, String sha256) {
        return "classification:" + requireNonBlank(tenantId, "tenantId") + ":" + requireNonBlank(sha256, "sha256");
    }
}
