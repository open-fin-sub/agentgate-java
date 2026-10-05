package com.abchina.llmalf.agentgate.logic;

import com.abchina.llmalf.agentgate.domain.model.credential.ApiKeyMetadata;
import com.abchina.llmalf.agentgate.domain.model.credential.ApiKeyScope;
import com.abchina.llmalf.agentgate.domain.model.target.TargetDescriptor;
import com.abchina.llmalf.agentgate.domain.model.target.TargetRef;
import com.abchina.llmalf.agentgate.domain.model.target.TargetType;
import com.abchina.llmalf.agentgate.domain.model.target.ToolDescriptor;
import com.abchina.llmalf.agentgate.domain.model.trace.SpanStatus;
import com.abchina.llmalf.agentgate.domain.model.trace.Trace;
import com.abchina.llmalf.agentgate.domain.model.trace.TraceSpan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trace/Result/Target/ApiKey 四域 Logic 共库集成测试(随机 id + 事务回滚).
 */
@SpringBootTest
@Transactional
class StorageDomainsLogicIntegrationTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00+00:00");
    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-01-02T00:00:00+00:00");
    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";
    private static final String SPAN = "0123456789abcdef";

    @Autowired
    private TraceLogic traceLogic;

    @Autowired
    private ResultLogic resultLogic;

    @Autowired
    private TargetLogic targetLogic;

    @Autowired
    private ApiKeyLogic apiKeyLogic;

    private static String hexTraceId() {
        String hex = UUID.randomUUID().toString().replace("-", "");
        return hex.substring(0, 32);
    }

    private static Trace traceOf(String traceId, String runId, String caseId) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("k", 1);
        return Trace.of(traceId, runId, caseId,
                Collections.singletonList(TraceSpan.of(traceId, SPAN, null, "操作", "turn", 0,
                        T0, T1, SpanStatus.OK, attributes, null)),
                null, null, null);
    }

    @Test
    void traceSaveGetListWithImmutabilityGuard() {
        String runId = "it-run-" + UUID.randomUUID();
        String traceId = hexTraceId();
        traceLogic.saveTrace(traceOf(traceId, runId, "c1"));
        assertEquals(traceId, traceLogic.getTrace(runId, "c1").traceId());

        traceLogic.saveTrace(traceOf(traceId, runId, "c1"));
        assertEquals("Trace Run and Case identity are immutable",
                assertThrows(IllegalArgumentException.class, () -> traceLogic.saveTrace(
                        traceOf(traceId, runId, "c2"))).getMessage());

        String trace2 = hexTraceId();
        traceLogic.saveTrace(traceOf(trace2, runId, "c2"));
        List<Trace> traces = traceLogic.listTraces(runId);
        assertEquals(2, traces.size());
        assertEquals("c1", traces.get(0).caseId());
        assertNull(traceLogic.getTrace(runId, "c9"));
    }

    @Test
    void targetDescriptorContentAddressing() {
        TargetDescriptor descriptor = TargetDescriptor.of(
                TargetRef.of("it-src-" + UUID.randomUUID(), TargetType.AGENT, "loan", "v1"),
                "名称", null, null, null, null, null, null, null, null, T0, "");
        targetLogic.saveTargetDescriptor(descriptor);
        assertEquals(descriptor.contentSha256(),
                targetLogic.getTargetDescriptor(descriptor.contentSha256()).contentSha256());

        targetLogic.saveTargetDescriptor(descriptor);
        TargetDescriptor renamed = TargetDescriptor.of(descriptor.ref(), "改名", null, null, null, null, null,
                null, null, null, T1, "");
        targetLogic.saveTargetDescriptor(renamed);
        assertEquals("改名", targetLogic.getTargetDescriptor(renamed.contentSha256())
                .displayName());

        TargetDescriptor variant = TargetDescriptor.of(descriptor.ref(), "名称", null, null,
                null, null, null, null, null, null, T1, "");
        targetLogic.saveTargetDescriptor(variant);
        List<TargetDescriptor> byRef = targetLogic.listTargetDescriptors(descriptor.ref());
        assertTrue(byRef.size() >= 2);
        assertTrue(targetLogic.listTargetDescriptors(null).size() >= byRef.size());
    }

    @Test
    void apiKeyLifecycle() {
        String keyId = "it-key-" + UUID.randomUUID();
        ApiKeyMetadata metadata = ApiKeyMetadata.of(keyId, "主力密钥", "openai",
                ApiKeyScope.SHARED, T0, T0);
        assertEquals("encrypted API Key must be a nonblank string",
                assertThrows(IllegalArgumentException.class, () -> apiKeyLogic.saveApiKey(
                        metadata, " ")).getMessage());

        apiKeyLogic.saveApiKey(metadata, "v1.aGVsbG8=");
        assertEquals(keyId, apiKeyLogic.getApiKeyMetadata(keyId).id());
        assertEquals("v1.aGVsbG8=", apiKeyLogic.getEncryptedApiKey(keyId));
        assertTrue(apiKeyLogic.listApiKeyMetadata().stream()
                .anyMatch(item -> item.id().equals(keyId)));

        assertEquals("unknown API Key",
                assertThrows(IllegalArgumentException.class,
                        () -> apiKeyLogic.deleteApiKey("missing")).getMessage());
        apiKeyLogic.deleteApiKey(keyId);
        assertNull(apiKeyLogic.getApiKeyMetadata(keyId));
        assertNull(apiKeyLogic.getEncryptedApiKey(keyId));
    }
}
