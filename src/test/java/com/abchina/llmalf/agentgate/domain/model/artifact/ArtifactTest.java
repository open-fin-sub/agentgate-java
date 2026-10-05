package com.abchina.llmalf.agentgate.domain.model.artifact;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Artifact 聚焦行为测试.
 */
class ArtifactTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00+00:00");

    private static Artifact artifact(String traceId, String producerName) {
        return Artifact.of("art-1", "run-1", "c1", traceId, ArtifactProducer.TOOL, producerName,
                "screenshot", "shot.png", "image/png", "file:///tmp/shot.png",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                1024, T0, null);
    }

    @Test
    void nullOptionalTextStaysNullButEmptyStringIsRejected() {
        assertNull(artifact(null, null).traceId());
        assertNull(artifact(null, null).producerName());
        assertEquals("Artifact trace_id must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> artifact("", null)).getMessage());
        assertEquals("Artifact producer_name must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> artifact(null, " ")).getMessage());
    }

    @Test
    void metadataIsFrozen() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("k", 1);
        Artifact model = artifact(null, null);
        Artifact withMetadata = Artifact.of("art-1", "run-1", "c1", null,
                ArtifactProducer.TOOL, null, "screenshot", "shot.png", "image/png",
                "file:///tmp/shot.png",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                1024, T0, metadata);
        assertThrows(UnsupportedOperationException.class,
                () -> withMetadata.metadata().put("late", 1));
        assertTrue(model.metadata().isEmpty());
    }

    @Test
    void nullTimestampsFallBackToUtcNow() {
        Artifact model = Artifact.of("art-1", "run-1", "c1", null, ArtifactProducer.HARNESS,
                null, "log", "log.txt", "text/plain", "file:///tmp/log.txt",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                0, null, null);
        assertTrue(!model.createdAt().isAfter(
                OffsetDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(1)));
        assertEquals("2026-01-01T00:00:00Z",
                ((Map<?, ?>) artifact(null, null).toPayload()).get("created_at"));
    }

    @Test
    void producerRoundTrip() {
        assertEquals(ArtifactProducer.AGENT, ArtifactProducer.fromWireValue("agent"));
        assertEquals(ArtifactProducer.HARNESS, ArtifactProducer.fromWireValue("harness"));
        assertEquals("Input should be 'agent', 'tool' or 'harness'",
                assertThrows(IllegalArgumentException.class,
                        () -> ArtifactProducer.fromWireValue("user")).getMessage());
    }

    @Test
    void fromPayloadGeneratesIdAndParsesSize() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("run_id", "run-1");
        payload.put("case_id", "c1");
        payload.put("producer", "tool");
        payload.put("artifact_type", "screenshot");
        payload.put("filename", "shot.png");
        payload.put("media_type", "image/png");
        payload.put("storage_uri", "file:///tmp/shot.png");
        payload.put("sha256", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        payload.put("size_bytes", 2048);
        Artifact model = Artifact.fromPayload(payload);
        assertTrue(model.id().trim().length() > 0);
        assertEquals(2048, model.sizeBytes());
        assertEquals("Field required",
                assertThrows(IllegalArgumentException.class, () -> {
                    Map<String, Object> missing = new HashMap<>(payload);
                    missing.remove("size_bytes");
                    Artifact.fromPayload(missing);
                }).getMessage());
    }
}
