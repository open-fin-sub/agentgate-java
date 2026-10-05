package com.abchina.llmalf.agentgate.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 内嵌凭据路径探测语义测试.
 *
 * <p>对齐 Python domain/base.py::find_credential_path:
 * 深度优先前序遍历、键小写化+连字符转下划线、路径格式 {@code a.b}/{@code a[0].b}。</p>
 */
class CredentialPathsTest {

    @Test
    void findsNestedCredentialPaths() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("model", "gpt");
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("client_secret", "s");
        root.put("auth", nested);

        assertEquals(Optional.of("auth.client_secret"), CredentialPaths.find(root));
    }

    @Test
    void pathsIncludeListIndices() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("api_key", "k");
        List<Object> calls = new ArrayList<>();
        calls.add(new LinkedHashMap<>());
        calls.add(inner);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("calls", calls);

        assertEquals(Optional.of("calls[1].api_key"), CredentialPaths.find(root));
    }

    @Test
    void topLevelListUsesBareIndexPrefix() {
        List<Object> root = new ArrayList<>();
        root.add(new LinkedHashMap<>());
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("bearer_token", "t");
        root.add(second);

        assertEquals(Optional.of("[1].bearer_token"), CredentialPaths.find(root));
    }

    @Test
    void keyMatchingIsCaseInsensitiveAndHyphenTolerant() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Api-Key", "k");
        assertEquals(Optional.of("Api-Key"), CredentialPaths.find(root));

        Map<String, Object> root2 = new LinkedHashMap<>();
        root2.put("ACCESS-TOKEN", "t");
        assertEquals(Optional.of("ACCESS-TOKEN"), CredentialPaths.find(root2));
    }

    @Test
    void similarButNonCredentialKeysAreIgnored() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("apikey", "k");
        root.put("my_password", "p");
        root.put("secrets", "s");
        assertEquals(Optional.empty(), CredentialPaths.find(root));
    }

    @Test
    void firstMatchWinsInIterationOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("password", "p");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("api_key", "k");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("x", first);
        root.put("y", second);

        assertEquals(Optional.of("x.password"), CredentialPaths.find(root));
    }

    @Test
    void keyMatchTakesPrecedenceOverEarlierSiblingSubtree() {
        Map<String, Object> deep = new LinkedHashMap<>();
        deep.put("secret_key", "s");
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("inner", deep);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("a", first);
        root.put("authorization", "z");

        assertEquals(Optional.of("a.inner.secret_key"), CredentialPaths.find(root));
    }

    @Test
    void noCredentialsYieldsEmpty() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("name", "n");
        root.put("items", new ArrayList<>());
        assertEquals(Optional.empty(), CredentialPaths.find(root));
        assertEquals(Optional.empty(), CredentialPaths.find(null));
        assertEquals(Optional.empty(), CredentialPaths.find("plain string"));
        assertFalse(CredentialPaths.find(42).isPresent());
    }

    @Test
    void allSevenCredentialKeysAreDetected() {
        String[] keys = {"access_token", "api_key", "authorization", "bearer_token",
                "client_secret", "password", "secret_key"};
        for (String key : keys) {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put(key, "v");
            assertEquals(Optional.of(key), CredentialPaths.find(root), "missed credential key: " + key);
        }
    }
}
