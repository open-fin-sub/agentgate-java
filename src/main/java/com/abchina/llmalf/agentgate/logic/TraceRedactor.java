package com.abchina.llmalf.agentgate.logic;

import com.abchina.llmalf.agentgate.domain.FrozenJson;
import com.abchina.llmalf.agentgate.domain.model.trace.Trace;
import com.abchina.llmalf.agentgate.domain.model.trace.TraceSpan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trace 脱敏视图.
 *
 * <p>对齐 Python trace/redaction.py:默认 62 个敏感键(归一化前/后缀匹配)、
 * 关联 UUID 豁免、7 类文本脱敏(私钥/URL 凭据/内联凭据/ bearer/邮箱/银行卡 Luhn)。</p>
 */
public final class TraceRedactor {

    /** 脱敏占位符 */
    public static final String REDACTION_MARKER = "[redacted]";

    private static final Pattern KEY_SEPARATOR = Pattern.compile("[^a-z0-9]+");
    private static final Pattern INLINE_CREDENTIAL = Pattern.compile(
            "(?i)\\b(api[_-]?key|authorization|bearer[_-]?token|client[_-]?secret|"
                    + "password|refresh[_-]?token|secret[_-]?key|token)\\s*([=:])\\s*"
                    + "(?:bearer\\s+)?\\S+");
    private static final Pattern BEARER = Pattern.compile(
            "(?i)\\bbearer\\s+[a-z0-9._~+/=-]+");
    private static final Pattern URL_CREDENTIAL = Pattern.compile(
            "(?i)\\b([a-z][a-z0-9+.-]*://)[^\\s/@:]+:[^\\s/@]+@");
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----.*?-----END [A-Z0-9 ]*PRIVATE KEY-----",
            Pattern.DOTALL);
    private static final Pattern EMAIL = Pattern.compile(
            "(?<![\\w.+-])[\\w.+-]+@[a-z0-9-]+(?:\\.[a-z0-9-]+)+(?![\\w.-])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CARD = Pattern.compile(
            "(?<!\\d)(?:\\d[ -]?){12,18}\\d(?!\\d)");
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
            Pattern.CASE_INSENSITIVE);

    private static final Set<String> CORRELATION_KEYS = new HashSet<>(Arrays
            .asList("request_id", "session_id", "trace_id", "span_id"));

    private static final Set<String> DEFAULT_SENSITIVE_KEYS = buildDefaultKeys();

    private TraceRedactor() {
    }

    /**
     * 生成轨迹脱敏视图.
     *
     * @param trace 原始轨迹
     * @return 脱敏轨迹
     */
    @SuppressWarnings("unchecked")
    public static Trace redact(Trace trace) {
        List<TraceSpan> spans = new ArrayList<>();
        for (TraceSpan span : trace.spans()) {
            spans.add(TraceSpan.of(span.traceId(), span.spanId(), span.parentSpanId(),
                    redactText(span.name()), span.operationType(), span.sequence(),
                    span.startedAt(), span.endedAt(), span.status(),
                    (Map<String, Object>) redactValue(span.attributes()),
                    redactEvents(span.events())));
        }
        return Trace.of(trace.traceId(), trace.runId(), trace.caseId(), spans,
                (Map<String, Object>) redactValue(trace.turnOutcomes()),
                (Map<String, Object>) redactValue(trace.finalOutput()),
                (Map<String, Object>) redactValue(trace.finalState()));
    }

    /**
     * 值级脱敏.
     *
     * @param value JSON 兼容值
     * @return 脱敏值
     */
    public static Object redactValue(Object value) {
        Object frozen = value == null ? null : FrozenJson.freeze(value);
        return redactFrozen(frozen);
    }

    private static Object redactFrozen(Object value) {
        if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object item = entry.getValue();
                if (isSensitiveKey(key)) {
                    result.put(key, REDACTION_MARKER);
                } else if (isCorrelationUuid(key, item)) {
                    result.put(key, item);
                } else {
                    result.put(key, redactFrozen(item));
                }
            }
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                result.add(redactFrozen(item));
            }
            return Collections.unmodifiableList(result);
        }
        if (value instanceof String) {
            return redactText((String) value);
        }
        return value;
    }

    private static List<Map<String, ?>> redactEvents(List<Map<String, Object>> events) {
        List<Map<String, ?>> result = new ArrayList<>(events.size());
        for (Map<String, Object> event : events) {
            result.add((Map<String, ?>) redactValue(event));
        }
        return result;
    }

    static String redactText(String value) {
        value = PRIVATE_KEY.matcher(value).replaceAll(REDACTION_MARKER);
        value = URL_CREDENTIAL.matcher(value).replaceAll("$1[redacted]@");
        Matcher inline = INLINE_CREDENTIAL.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (inline.find()) {
            inline.appendReplacement(buffer,
                    Matcher.quoteReplacement(inline.group(1) + inline.group(2)
                            + REDACTION_MARKER));
        }
        inline.appendTail(buffer);
        value = BEARER.matcher(buffer.toString()).replaceAll(REDACTION_MARKER);
        value = EMAIL.matcher(value).replaceAll(REDACTION_MARKER);
        return redactCards(value);
    }

    private static String redactCards(String value) {
        Matcher matcher = CARD.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String candidate = matcher.group();
            String digits = candidate.replaceAll("[^0-9]", "");
            String replacement = passesLuhn(digits) ? REDACTION_MARKER
                    : Matcher.quoteReplacement(candidate);
            matcher.appendReplacement(buffer, replacement);
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static boolean passesLuhn(String digits) {
        if (digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        int total = 0;
        int parity = digits.length() % 2;
        for (int index = 0; index < digits.length(); index++) {
            int digit = digits.charAt(index) - '0';
            if (index % 2 == parity) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            total += digit;
        }
        return total % 10 == 0;
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = normalizeKey(key);
        for (String sensitive : DEFAULT_SENSITIVE_KEYS) {
            if (normalized.equals(sensitive) || normalized.endsWith("_" + sensitive)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCorrelationUuid(String key, Object value) {
        if (!(value instanceof String)) {
            return false;
        }
        if (!UUID.matcher((String) value).matches()) {
            return false;
        }
        String normalized = normalizeKey(key);
        for (String correlation : CORRELATION_KEYS) {
            if (normalized.equals(correlation) || normalized.endsWith("_" + correlation)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeKey(String key) {
        String lowered = key.toLowerCase(Locale.ROOT);
        String replaced = KEY_SEPARATOR.matcher(lowered).replaceAll("_");
        while (replaced.startsWith("_")) {
            replaced = replaced.substring(1);
        }
        while (replaced.endsWith("_")) {
            replaced = replaced.substring(0, replaced.length() - 1);
        }
        return replaced;
    }

    private static Set<String> buildDefaultKeys() {
        Set<String> keys = new HashSet<>();
        String[] defaults = {
                "access_token", "address", "api_key", "authorization", "balance",
                "bank_account", "bank_account_number", "bearer_token", "birth_date",
                "card_number", "client_secret", "connection_string", "cookie",
                "credential_ref", "credentials", "credit_card", "credit_score",
                "customer_name", "cvc", "cvv", "date_of_birth", "dob", "email",
                "email_address", "environment_secret", "first_name", "full_name",
                "home_address", "iban", "income", "last_name", "loan_amount", "mobile",
                "mobile_number", "national_id", "nric", "passport", "passport_number",
                "password", "phone", "phone_number", "private_key", "refresh_token",
                "routing_number", "salary", "secret", "secret_key", "set_cookie",
                "signing_key", "ssh_key", "ssn", "tax_id", "token", "webhook_secret",
        };
        for (String key : defaults) {
            keys.add(key);
        }
        return Collections.unmodifiableSet(keys);
    }
}
