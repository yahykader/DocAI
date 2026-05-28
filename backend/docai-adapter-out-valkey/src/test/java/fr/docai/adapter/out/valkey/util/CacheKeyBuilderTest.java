package fr.docai.adapter.out.valkey.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CacheKeyBuilderTest {

    @Test
    void extractionKey_shouldReturnExactFormat() {
        assertThat(CacheKeyBuilder.extractionKey("tenant-A", "abc123")).isEqualTo("extraction:tenant-A:abc123");
    }

    @Test
    void inseeSiretKey_shouldReturnExactFormat() {
        assertThat(CacheKeyBuilder.inseeSiretKey("12345678901234")).isEqualTo("insee:siret:12345678901234");
    }

    @Test
    void banAddressKey_shouldReturnExactFormat() {
        assertThat(CacheKeyBuilder.banAddressKey("deadbeef")).isEqualTo("ban:address:deadbeef");
    }

    @Test
    void rppsKey_shouldReturnExactFormat() {
        assertThat(CacheKeyBuilder.rppsKey("10002345678")).isEqualTo("rpps:10002345678");
    }

    @Test
    void jwtBlacklistKey_shouldReturnExactFormat() {
        assertThat(CacheKeyBuilder.jwtBlacklistKey("jti-xyz")).isEqualTo("jwt:blacklist:jti-xyz");
    }

    @Test
    void idempotentKafkaKey_shouldReturnExactFormat() {
        assertThat(CacheKeyBuilder.idempotentKafkaKey("upload", 2, 42L))
            .isEqualTo("idempotent:upload:2:42");
    }

    @Test
    void idempotentKafkaKey_shouldHandleZeroPartitionAndOffset() {
        assertThat(CacheKeyBuilder.idempotentKafkaKey("upload", 0, 0L))
            .isEqualTo("idempotent:upload:0:0");
    }

    @Test
    void idempotencyKey_shouldReturnExactFormat() {
        assertThat(CacheKeyBuilder.idempotencyKey("req-001")).isEqualTo("idempotency:req-001");
    }

    @Test
    void quotaKey_shouldZeroPadMonth() {
        assertThat(CacheKeyBuilder.quotaKey("tenant-A", 2026, 5)).isEqualTo("quota:tenant-A:2026-05");
    }

    @Test
    void quotaKey_shouldNotPadDoubleDigitMonth() {
        assertThat(CacheKeyBuilder.quotaKey("tenant-A", 2026, 11)).isEqualTo("quota:tenant-A:2026-11");
    }

    @Test
    void quotaKey_shouldHandleJanuary() {
        assertThat(CacheKeyBuilder.quotaKey("t", 2026, 1)).isEqualTo("quota:t:2026-01");
    }

    @Test
    void quotaKey_shouldHandleDecember() {
        assertThat(CacheKeyBuilder.quotaKey("t", 2026, 12)).isEqualTo("quota:t:2026-12");
    }

    @Test
    void classificationKey_shouldReturnExactFormat() {
        assertThat(CacheKeyBuilder.classificationKey("tenant-A", "sha256abc")).isEqualTo("classification:tenant-A:sha256abc");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void jwtBlacklistKey_shouldRejectBlankJti(String jti) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> CacheKeyBuilder.jwtBlacklistKey(jti))
            .withMessageContaining("jti");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void extractionKey_shouldRejectBlankSha256(String sha256) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> CacheKeyBuilder.extractionKey("tenant-A", sha256));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void extractionKey_shouldRejectBlankTenantId(String tenantId) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> CacheKeyBuilder.extractionKey(tenantId, "abc123"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void inseeSiretKey_shouldRejectBlankSiret(String siret) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> CacheKeyBuilder.inseeSiretKey(siret));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void banAddressKey_shouldRejectBlankHash(String hash) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> CacheKeyBuilder.banAddressKey(hash));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void rppsKey_shouldRejectBlankNumero(String numero) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> CacheKeyBuilder.rppsKey(numero));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void idempotentKafkaKey_shouldRejectBlankTopic(String topic) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> CacheKeyBuilder.idempotentKafkaKey(topic, 0, 0L));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void idempotencyKey_shouldRejectBlankHeader(String header) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> CacheKeyBuilder.idempotencyKey(header));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void classificationKey_shouldRejectBlankSha256(String sha256) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> CacheKeyBuilder.classificationKey("tenant-A", sha256));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void classificationKey_shouldRejectBlankTenantId(String tenantId) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> CacheKeyBuilder.classificationKey(tenantId, "sha256abc"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void quotaKey_shouldRejectBlankTenantId(String tenantId) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> CacheKeyBuilder.quotaKey(tenantId, 2026, 5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc:def", "jti:poison"})
    void jwtBlacklistKey_shouldRejectColonInJti(String jti) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> CacheKeyBuilder.jwtBlacklistKey(jti))
            .withMessageContaining(":");
    }

    @ParameterizedTest
    @ValueSource(strings = {"tenant:evil", "a:b"})
    void quotaKey_shouldRejectColonInTenantId(String tenantId) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> CacheKeyBuilder.quotaKey(tenantId, 2026, 5));
    }

    @Test
    void quotaKey_shouldRejectMonthZero() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> CacheKeyBuilder.quotaKey("t", 2026, 0));
    }

    @Test
    void quotaKey_shouldRejectMonthThirteen() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> CacheKeyBuilder.quotaKey("t", 2026, 13));
    }
}
