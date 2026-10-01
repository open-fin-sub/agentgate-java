package com.abchina.llmalf.agentgate.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 异常消息安全化工具.
 *
 * <p>语义对齐 Python server/errors.py::_safe_message:
 * 凭据类键值对脱敏、超长截断 500 字符、空消息回退异常类名.</p>
 */
public final class SafeMessages {

    /** 消息最大长度 */
    private static final int MAX_LENGTH = 500;

    /** 凭据类键值脱敏正则(与 Python _CREDENTIAL_PATTERN 一致) */
    private static final Pattern CREDENTIAL_PATTERN =
            Pattern.compile("(?i)(api[_-]?key|authorization|token|secret|password)\\s*[=:]\\s*\\S+");

    private SafeMessages() {
    }

    /**
     * 提取异常的安全消息:空消息回退异常类名,再脱敏截断.
     *
     * @param error 异常
     * @return 脱敏后的安全消息
     */
    public static String of(Throwable error) {
        String text = error.getMessage();
        if (text == null || text.trim().isEmpty()) {
            text = error.getClass().getSimpleName();
        } else {
            text = text.trim();
        }
        return redact(text);
    }

    /**
     * 凭据类键值对脱敏并截断到 500 字符.
     *
     * @param value 原始文本
     * @return 脱敏后的文本
     */
    public static String redact(String value) {
        Matcher matcher = CREDENTIAL_PATTERN.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, "$1=[redacted]");
        }
        matcher.appendTail(buffer);
        String result = buffer.toString();
        if (result.length() > MAX_LENGTH) {
            return result.substring(0, MAX_LENGTH);
        }
        return result;
    }
}
