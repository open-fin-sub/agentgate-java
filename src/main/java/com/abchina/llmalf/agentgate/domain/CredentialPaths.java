package com.abchina.llmalf.agentgate.domain;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 内嵌凭据路径探测.
 *
 * <p>对齐 Python domain/base.py::find_credential_path:
 * 按 Map 迭代序深度优先前序遍历,键小写化并连字符转下划线后命中凭据键集合即返回,
 * 路径格式 {@code a.b} / {@code a[0].b}。</p>
 */
public final class CredentialPaths {

    private static final Set<String> CREDENTIAL_KEYS = new HashSet<>(Arrays.asList(
            "access_token",
            "api_key",
            "authorization",
            "bearer_token",
            "client_secret",
            "password",
            "secret_key"));

    private CredentialPaths() {
    }

    /**
     * 探测第一个凭据键路径.
     *
     * @param value JSON 兼容值
     * @return 命中的路径,未命中为空
     */
    public static Optional<String> find(Object value) {
        return find(value, "");
    }

    private static Optional<String> find(Object value, String prefix) {
        if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                String key = (String) entry.getKey();
                String path = prefix.isEmpty() ? key : prefix + "." + key;
                if (CREDENTIAL_KEYS.contains(key.toLowerCase(Locale.ROOT).replace("-", "_"))) {
                    return Optional.of(path);
                }
                Optional<String> found = find(entry.getValue(), path);
                if (found.isPresent()) {
                    return found;
                }
            }
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            for (int i = 0; i < list.size(); i++) {
                Optional<String> found = find(list.get(i), prefix + "[" + i + "]");
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }
}
