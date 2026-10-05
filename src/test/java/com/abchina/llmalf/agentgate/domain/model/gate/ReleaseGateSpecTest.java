package com.abchina.llmalf.agentgate.domain.model.gate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ReleaseGateSpec 行为测试.
 */
class ReleaseGateSpecTest {

    @Test
    void defaultsAreProtocolConstants() {
        ReleaseGateSpec spec = ReleaseGateSpec.of();
        assertEquals("release-gate", spec.id());
        assertEquals("1", spec.version());
        assertEquals(0.95, spec.minimumScore(), 0.0);
    }

    @Test
    void minimumScoreBounds() {
        assertEquals("Input should be greater than or equal to 0",
                assertThrows(IllegalArgumentException.class,
                        () -> ReleaseGateSpec.of("g", "1", -0.01)).getMessage());
        assertEquals("Input should be less than or equal to 1",
                assertThrows(IllegalArgumentException.class,
                        () -> ReleaseGateSpec.of("g", "1", 1.01)).getMessage());
        ReleaseGateSpec zero = ReleaseGateSpec.of("g", "1", 0.0);
        assertEquals(0.0, zero.minimumScore(), 0.0);
        ReleaseGateSpec one = ReleaseGateSpec.of("g", "1", 1.0);
        assertEquals(1.0, one.minimumScore(), 0.0);
    }

    @Test
    void identityValidation() {
        assertEquals("ReleaseGateSpec id must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> ReleaseGateSpec.of(" ", "1", 0.95)).getMessage());
        assertEquals("ReleaseGateSpec version must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> ReleaseGateSpec.of("g", " ", 0.95)).getMessage());
    }

    @Test
    void fromPayloadAppliesDefaults() {
        ReleaseGateSpec spec = ReleaseGateSpec.fromPayload(new java.util.HashMap<>());
        assertEquals("release-gate", spec.id());
        assertEquals(0.95, spec.minimumScore(), 0.0);
    }
}
