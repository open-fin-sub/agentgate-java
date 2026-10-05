package com.abchina.llmalf.agentgate.domain.model.cases;

/**
 * 用例难度分级.
 *
 * <p>对齐 Python domain/case.py::CaseDifficulty(StrEnum),
 * 非法值消息复刻 pydantic StrEnum 文本。</p>
 */
public enum CaseDifficulty {

    EASY("easy"),
    MEDIUM("medium"),
    HARD("hard");

    private final String wireValue;

    CaseDifficulty(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * payload 序列化用的 wire 值.
     *
     * @return 小写难度名
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * 从 wire 值解析难度.
     *
     * @param value wire 值
     * @return 难度枚举
     */
    public static CaseDifficulty fromWireValue(String value) {
        for (CaseDifficulty difficulty : values()) {
            if (difficulty.wireValue.equals(value)) {
                return difficulty;
            }
        }
        throw new IllegalArgumentException("Input should be 'easy', 'medium' or 'hard'");
    }
}
