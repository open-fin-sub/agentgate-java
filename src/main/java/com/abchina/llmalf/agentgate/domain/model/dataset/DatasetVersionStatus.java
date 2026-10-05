package com.abchina.llmalf.agentgate.domain.model.dataset;

/**
 * 数据集版本生命周期状态.
 *
 * <p>对齐 Python domain/dataset.py::DatasetVersionStatus(StrEnum)。</p>
 */
public enum DatasetVersionStatus {

    DRAFT("draft"),
    PUBLISHED("published");

    private final String wireValue;

    DatasetVersionStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * payload 序列化用的 wire 值.
     *
     * @return 小写状态名
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * 从 wire 值解析状态.
     *
     * @param value wire 值
     * @return 状态枚举
     */
    public static DatasetVersionStatus fromWireValue(String value) {
        for (DatasetVersionStatus status : values()) {
            if (status.wireValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Input should be 'draft' or 'published'");
    }
}
