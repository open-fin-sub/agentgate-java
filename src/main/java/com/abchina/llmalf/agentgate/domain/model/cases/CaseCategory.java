package com.abchina.llmalf.agentgate.domain.model.cases;

/**
 * 用例业务分类.
 *
 * <p>对齐 Python domain/case.py::CaseCategory(StrEnum),
 * 非法值消息复刻 pydantic StrEnum 文本。</p>
 */
public enum CaseCategory {

    POSITIVE("positive"),
    NEGATIVE("negative"),
    BOUNDARY("boundary");

    private final String wireValue;

    CaseCategory(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * payload 序列化用的 wire 值.
     *
     * @return 小写分类名
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * 从 wire 值解析分类.
     *
     * @param value wire 值
     * @return 分类枚举
     */
    public static CaseCategory fromWireValue(String value) {
        for (CaseCategory category : values()) {
            if (category.wireValue.equals(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Input should be 'positive', 'negative' or 'boundary'");
    }
}
