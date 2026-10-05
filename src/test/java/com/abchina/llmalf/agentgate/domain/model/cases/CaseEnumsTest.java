package com.abchina.llmalf.agentgate.domain.model.cases;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CaseCategory/CaseDifficulty 枚举 wire 值测试.
 */
class CaseEnumsTest {

    @Test
    void categoryWireValuesRoundTrip() {
        assertSame(CaseCategory.POSITIVE, CaseCategory.fromWireValue("positive"));
        assertSame(CaseCategory.NEGATIVE, CaseCategory.fromWireValue("negative"));
        assertSame(CaseCategory.BOUNDARY, CaseCategory.fromWireValue("boundary"));
        assertSame(CaseCategory.POSITIVE, CaseCategory.fromWireValue(CaseCategory.POSITIVE.wireValue()));
    }

    @Test
    void difficultyWireValuesRoundTrip() {
        assertSame(CaseDifficulty.EASY, CaseDifficulty.fromWireValue("easy"));
        assertSame(CaseDifficulty.MEDIUM, CaseDifficulty.fromWireValue("medium"));
        assertSame(CaseDifficulty.HARD, CaseDifficulty.fromWireValue("hard"));
        assertSame(CaseDifficulty.HARD, CaseDifficulty.fromWireValue(CaseDifficulty.HARD.wireValue()));
    }

    @Test
    void unknownValuesCarryPydanticEnumMessages() {
        assertEquals("Input should be 'positive', 'negative' or 'boundary'",
                assertThrows(IllegalArgumentException.class,
                        () -> CaseCategory.fromWireValue("weird")).getMessage());
        assertEquals("Input should be 'positive', 'negative' or 'boundary'",
                assertThrows(IllegalArgumentException.class,
                        () -> CaseCategory.fromWireValue(null)).getMessage());
        assertEquals("Input should be 'positive', 'negative' or 'boundary'",
                assertThrows(IllegalArgumentException.class,
                        () -> CaseCategory.fromWireValue("POSITIVE")).getMessage());

        assertEquals("Input should be 'easy', 'medium' or 'hard'",
                assertThrows(IllegalArgumentException.class,
                        () -> CaseDifficulty.fromWireValue("impossible")).getMessage());
        assertEquals("Input should be 'easy', 'medium' or 'hard'",
                assertThrows(IllegalArgumentException.class,
                        () -> CaseDifficulty.fromWireValue("MEDIUM")).getMessage());
    }
}
