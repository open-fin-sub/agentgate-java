package com.abchina.llmalf.agentgate.service.impl;

import com.abchina.llmalf.agentgate.domain.model.target.SkillDescriptor;
import com.abchina.llmalf.agentgate.domain.model.target.TargetDescriptor;
import com.abchina.llmalf.agentgate.domain.model.target.TargetRef;
import com.abchina.llmalf.agentgate.domain.model.target.TargetType;
import com.abchina.llmalf.agentgate.domain.model.target.ToolDescriptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * demo Loan Agent 目标目录(固化,对齐 demo/targets.py).
 *
 * <p>demo 执行器归 Python 执行链(不重构);Java 侧仅固化描述符
 * 供 Run 创建时内容寻址解析,并保证 TargetDescriptor 重建与
 * Python 构造的 content_sha256 一致。</p>
 */
public final class DemoCatalog {

    /** demo 目录固定时间戳(对齐 DEMO_CREATED_AT) */
    public static final java.time.OffsetDateTime DEMO_CREATED_AT =
            java.time.OffsetDateTime.parse("2026-01-01T00:00:00Z");

    private DemoCatalog() {
    }

    /**
     * 构造指定版本的 demo 描述符.
     *
     * @param version demo 目标版本
     * @return 描述符
     */
    public static TargetDescriptor descriptor(String version) {
        boolean risky = "loan-agent-v1-risky".equals(version);
        String approvalVersion = risky
                ? "loan-approval-v1-risky" : "loan-approval-v2-fixed";
        String policy = risky
                ? "May approve high-risk applications without human review."
                : "High-risk applications must be sent to human review.";
        Map<String, Object> noMap = Collections.emptyMap();
        List<Map<String, Object>> emptyList = Collections.emptyList();
        Map<String, Object> nothing = new LinkedHashMap<String, Object>();

        List<ToolDescriptor> tools = new ArrayList<>(Arrays.asList(
                tool("credit_inquiry",
                        "Read the risk classification for one loan application."),
                tool("approve_loan",
                        "Approve one eligible loan application."),
                tool("request_human_review",
                        "Send one loan application to human review."),
                tool("repayment_plan",
                        "Calculate a repayment schedule for one application."),
                tool("complaint",
                        "Open a complaint for one application.")));

        List<SkillDescriptor> skills = new ArrayList<>(4);
        skills.add(SkillDescriptor.of("loan_approval", approvalVersion, "Loan Approval",
                "Assess a loan application and choose an approval action.",
                null, null,
                Arrays.asList(tools.get(0), tools.get(1), tools.get(2)), null, null,
                null));
        skills.add(SkillDescriptor.of("repayment_plan", "repayment-plan-v1",
                "Repayment Plan",
                "Calculate repayment installments for a loan application.",
                null, null, Collections.singletonList(tools.get(3)), null, null, null));
        skills.add(SkillDescriptor.of("complaint", "complaint-v1", "Complaint",
                "Record a complaint associated with a loan application.",
                null, null, Collections.singletonList(tools.get(4)), null, null, null));
        skills.add(SkillDescriptor.of("credit_inquiry", "credit-inquiry-v1",
                "Credit Inquiry",
                "Retrieve the risk classification for a loan application.",
                null, null, Collections.singletonList(tools.get(0)), null, null, null));

        return TargetDescriptor.of(
                TargetRef.of("agentgate-demo", TargetType.AGENT, "loan-agent", version),
                "Loan Agent",
                "Deterministic multi-skill Agent used by the AgentGate demo.",
                "Route financial requests to the correct Skill. " + policy,
                null, skills, tools, noMap, noMap, noMap,
                DEMO_CREATED_AT, "");
    }

    private static ToolDescriptor tool(String name, String description) {
        return ToolDescriptor.of(name, description, null, null);
    }
}
