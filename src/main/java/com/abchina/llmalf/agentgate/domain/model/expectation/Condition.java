package com.abchina.llmalf.agentgate.domain.model.expectation;

import java.util.Map;

/**
 * 期望条件判别契约.
 *
 * <p>对齐 Python domain/expectation.py::Condition 判别联合
 * (discriminator="kind",7 个条件实现);fromPayload 按 kind 分发,
 * 未知值抛 {@code "unknown condition kind: X"}。</p>
 */
public interface Condition {

    /**
     * 判别 wire 值.
     *
     * @return kind 字符串
     */
    String kind();

    /**
     * 组装 payload 表示(字段顺序对齐 pydantic 声明序).
     *
     * @return JSON 兼容树
     */
    Object toPayload();

    /**
     * 从 payload 解析条件(按 kind 分发).
     *
     * @param payload 条件 payload 对象
     * @return 条件实例
     */
    static Condition fromPayload(Map<String, Object> payload) {
        Object kind = payload == null ? null : payload.get("kind");
        switch (kind == null ? "null" : kind.toString()) {
            case Equals.KIND:
                return Equals.parse(payload);
            case WithinTolerance.KIND:
                return WithinTolerance.parse(payload);
            case WithinRange.KIND:
                return WithinRange.parse(payload);
            case MatchesPattern.KIND:
                return MatchesPattern.parse(payload);
            case OneOf.KIND:
                return OneOf.parse(payload);
            case MustBeMissing.KIND:
                return MustBeMissing.parse(payload);
            case MatchesJsonSchema.KIND:
                return MatchesJsonSchema.parse(payload);
            default:
                throw new IllegalArgumentException("unknown condition kind: " + kind);
        }
    }
}
