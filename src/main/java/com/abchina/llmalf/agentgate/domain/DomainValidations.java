package com.abchina.llmalf.agentgate.domain;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * 领域字段校验.
 *
 * <p>对齐 Python domain/base.py::require_non_blank/require_sha256/normalize_utc/utcnow
 * 的语义与错误消息文本(消息会透出到 API 422 响应,必须逐字一致)。</p>
 */
public final class DomainValidations {

    private static final String SHA256_PATTERN = "[0-9a-f]{64}";

    private DomainValidations() {
    }

    /**
     * 校验非空白字符串(Python str.strip() 语义,含 Unicode 空白).
     *
     * @param value 值
     * @param fieldName 字段名
     * @return 原值
     */
    public static String requireNonBlank(String value, String fieldName) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    /**
     * 校验小写 SHA-256 摘要.
     *
     * @param value 值
     * @param fieldName 字段名
     * @return 原值
     */
    public static String requireSha256(String value, String fieldName) {
        if (value == null || !value.matches(SHA256_PATTERN)) {
            throw new IllegalArgumentException(fieldName + " must be a lowercase SHA-256 digest");
        }
        return value;
    }

    /**
     * 当前 UTC 时间戳.
     *
     * @return UTC OffsetDateTime
     */
    public static OffsetDateTime utcNow() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 归一化带时区偏移的时间到 UTC.
     *
     * @param value 带偏移的时间
     * @param fieldName 字段名
     * @return UTC OffsetDateTime
     */
    public static OffsetDateTime normalizeUtc(OffsetDateTime value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must be timezone-aware");
        }
        return value.withOffsetSameInstant(ZoneOffset.UTC);
    }

    /**
     * 归一化带时区的时间到 UTC.
     *
     * @param value 带偏移的时间
     * @param fieldName 字段名
     * @return UTC OffsetDateTime
     */
    public static OffsetDateTime normalizeUtc(ZonedDateTime value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must be timezone-aware");
        }
        return value.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
    }

    /**
     * payload 时间戳序列化.
     *
     * <p>对齐 pydantic model_dump(mode="json") 的 datetime 输出:
     * UTC 归一后 "Z" 后缀,微秒为 0 省略小数部分,否则固定 6 位;
     * 纳秒截断到微秒(Python datetime 精度)。</p>
     *
     * @param value 时间(通常已经 normalizeUtc)
     * @return ISO-8601 字符串
     */
    public static String isoFormat(OffsetDateTime value) {
        StringBuilder buffer = new StringBuilder(32);
        pad(buffer, 4, value.getYear()).append('-');
        pad(buffer, 2, value.getMonthValue()).append('-');
        pad(buffer, 2, value.getDayOfMonth()).append('T');
        pad(buffer, 2, value.getHour()).append(':');
        pad(buffer, 2, value.getMinute()).append(':');
        pad(buffer, 2, value.getSecond());
        long micros = value.getNano() / 1000;
        if (micros > 0) {
            buffer.append('.');
            pad(buffer, 6, micros);
        }
        int offsetSeconds = value.getOffset().getTotalSeconds();
        if (offsetSeconds == 0) {
            buffer.append('Z');
        } else {
            buffer.append(offsetSeconds < 0 ? '-' : '+');
            int absSeconds = Math.abs(offsetSeconds);
            pad(buffer, 2, absSeconds / 3600).append(':');
            pad(buffer, 2, (absSeconds % 3600) / 60);
        }
        return buffer.toString();
    }

    private static StringBuilder pad(StringBuilder buffer, int width, long value) {
        String digits = Long.toString(value);
        for (int i = digits.length(); i < width; i++) {
            buffer.append('0');
        }
        return buffer.append(digits);
    }

    /**
     * Python str.strip() 空白判定(isSpaceChar ∪ isWhitespace,含 NBSP/U+3000 等).
     *
     * @param value 值
     * @return 全空白或 null 为 true
     */
    public static boolean isBlank(String value) {
        if (value == null) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isSpaceChar(c) && !Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }
}
