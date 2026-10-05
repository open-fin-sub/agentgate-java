package com.abchina.llmalf.agentgate.domain.model.run;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RunStatus 枚举 wire 值测试.
 */
class RunStatusTest {

    @Test
    void wireValuesAreLowercaseStable() {
        assertEquals("scheduled", RunStatus.SCHEDULED.wireValue());
        assertEquals("pending", RunStatus.PENDING.wireValue());
        assertEquals("waiting", RunStatus.WAITING.wireValue());
        assertEquals("running", RunStatus.RUNNING.wireValue());
        assertEquals("completed", RunStatus.COMPLETED.wireValue());
        assertEquals("failed", RunStatus.FAILED.wireValue());
        assertEquals("cancelled", RunStatus.CANCELLED.wireValue());
    }

    @Test
    void fromWireValueRoundTrips() {
        for (RunStatus status : RunStatus.values()) {
            assertSame(status, RunStatus.fromWireValue(status.wireValue()));
        }
    }

    @Test
    void fromWireValueRejectsUnknown() {
        IllegalArgumentException upper = assertThrows(IllegalArgumentException.class,
                () -> RunStatus.fromWireValue("SCHEDULED"));
        assertEquals("unknown RunStatus: SCHEDULED", upper.getMessage());

        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> RunStatus.fromWireValue("paused"));
        assertEquals("unknown RunStatus: paused", unknown.getMessage());
    }
}
