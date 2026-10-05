package com.abchina.llmalf.agentgate.domain.model.expectation;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.Map;

/**
 * 策略期望.
 *
 * <p>对齐 Python domain/expectation.py::PolicyExpectation。</p>
 */
public final class PolicyExpectation extends Expectation {

    public static final String KIND = "policy";

    private final String policyId;

    private PolicyExpectation(String id, String name, String policyId) {
        super(id, name);
        this.policyId = policyId;
    }

    /**
     * 构造策略期望.
     *
     * @param id 期望 id
     * @param name 期望名(可空)
     * @param policyId 策略 id
     * @return 期望实例
     */
    public static PolicyExpectation of(String id, String name, String policyId) {
        return new PolicyExpectation(id, name,
                DomainValidations.requireNonBlank(policyId, "Policy id"));
    }

    static PolicyExpectation parse(Map<String, Object> payload) {
        return of(idOf(payload), nameOf(payload),
                PayloadValues.requiredString(payload, "policy_id"));
    }

    public String policyId() {
        return policyId;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public Object toPayload() {
        Map<String, Object> payload = basePayload();
        payload.put("kind", KIND);
        payload.put("policy_id", policyId);
        return payload;
    }
}
