package com.abchina.llmalf.agentgate.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 领域字段校验语义测试.
 *
 * <p>对齐 Python domain/base.py::require_non_blank/require_sha256/normalize_utc
 * 的判定规则与错误消息文本(消息透出 API 422 响应,须逐字一致)。</p>
 */
class DomainValidationsTest {

    @Test
    void requireNonBlankAcceptsAndReturnsValue() {
        assertEquals("hello", DomainValidations.requireNonBlank("hello", "name"));
        assertEquals(" 中文 ", DomainValidations.requireNonBlank(" 中文 ", "name"));
        assertEquals("0", DomainValidations.requireNonBlank("0", "name"));
        String value = "same-instance";
        assertSame(value, DomainValidations.requireNonBlank(value, "name"));
    }

    @Test
    void requireNonBlankRejectsBlankIncludingUnicodeWhitespace() {
        assertBlankRejected(null);
        assertBlankRejected("");
        assertBlankRejected("   ");
        assertBlankRejected("\t\n\r");
        assertBlankRejected("\u00a0");
        assertBlankRejected("\u3000");
        assertBlankRejected("\u2009\u202f");
    }

    @Test
    void requireSha256AcceptsLowercaseHexOnly() {
        String digest = "78d145429acf033939ca3676748013583b73c65617891b3f6f0cd3cb64fc2dfd";
        assertEquals(digest, DomainValidations.requireSha256(digest, "content_sha256"));

        assertSha256Rejected(null, "content_sha256");
        assertSha256Rejected("", "content_sha256");
        assertSha256Rejected("ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789", "content_sha256");
        assertSha256Rejected("78d145429acf033939ca3676748013583b73c65617891b3f6f0cd3cb64fc2df", "content_sha256");
        assertSha256Rejected("78d145429acf033939ca3676748013583b73c65617891b3f6f0cd3cb64fc2dfdz", "content_sha256");
    }

    @Test
    void normalizeUtcConvertsOffsetsToUtc() {
        OffsetDateTime beijing = OffsetDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.ofHours(8));
        OffsetDateTime normalized = DomainValidations.normalizeUtc(beijing, "created_at");
        assertEquals(ZoneOffset.UTC, normalized.getOffset());
        assertEquals(4, normalized.getHour());
        assertEquals(beijing.toInstant(), normalized.toInstant());

        ZonedDateTime tokyo = ZonedDateTime.of(2026, 10, 1, 13, 30, 0, 0, ZoneOffset.ofHours(9));
        OffsetDateTime fromZoned = DomainValidations.normalizeUtc(tokyo, "updated_at");
        assertEquals(ZoneOffset.UTC, fromZoned.getOffset());
        assertEquals(4, fromZoned.getHour());
        assertEquals(30, fromZoned.getMinute());
    }

    @Test
    void normalizeUtcRejectsMissingTimezone() {
        IllegalArgumentException offsetError = assertThrows(IllegalArgumentException.class,
                () -> DomainValidations.normalizeUtc((OffsetDateTime) null, "created_at"));
        assertEquals("created_at must be timezone-aware", offsetError.getMessage());

        IllegalArgumentException zonedError = assertThrows(IllegalArgumentException.class,
                () -> DomainValidations.normalizeUtc((ZonedDateTime) null, "updated_at"));
        assertEquals("updated_at must be timezone-aware", zonedError.getMessage());
    }

    @Test
    void utcNowIsInUtc() {
        OffsetDateTime now = DomainValidations.utcNow();
        assertEquals(ZoneOffset.UTC, now.getOffset());
        assertTrue(now.toInstant().isAfter(OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()));
    }

    private void assertBlankRejected(String value) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DomainValidations.requireNonBlank(value, "name"));
        assertEquals("name must not be blank", error.getMessage());
    }

    private void assertSha256Rejected(String value, String field) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DomainValidations.requireSha256(value, field));
        assertEquals(field + " must be a lowercase SHA-256 digest", error.getMessage());
    }
}
