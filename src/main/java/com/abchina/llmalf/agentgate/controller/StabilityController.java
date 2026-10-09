package com.abchina.llmalf.agentgate.controller;

import com.abchina.llmalf.agentgate.common.ResponseBase;
import com.abchina.llmalf.agentgate.common.UserContextHolder;
import com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTask;
import com.abchina.llmalf.agentgate.service.impl.StabilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 稳定性实验列表端点(启动归 BJS 适配切片,summary 归 results 域切片).
 *
 * <p>对齐 Python server/routes/stability.py 的 GET 列表
 * (过滤 stability 类型 + 团队可见)。</p>
 */
@RestController
@RequestMapping("/api/stability-experiments")
public class StabilityController {

    private final StabilityService stabilityService;

    public StabilityController(StabilityService stabilityService) {
        this.stabilityService = stabilityService;
    }

    /**
     * 稳定性实验列表.
     *
     * @return 任务 payload 列表(stability 类型)
     */
    @GetMapping
    public ResponseBase<List<Object>> listExperiments() {
        String teamId = UserContextHolder.current().userTeamId();
        List<Object> payloads = new ArrayList<>();
        for (EvaluationTask task : stabilityService.listExperiments(teamId)) {
            payloads.add(task.toPayload());
        }
        return ResponseBase.success(payloads);
    }

    /**
     * 稳定性实验摘要(均值/方差/标准差,未完成不计分).
     *
     * @param taskId 任务 id
     * @return 摘要投影
     */
    @GetMapping("/{taskId}")
    public ResponseBase<Map<String, Object>> summary(@PathVariable("taskId") String taskId) {
        String teamId = UserContextHolder.current().userTeamId();
        return ResponseBase.success(stabilityService.getSummary(taskId, teamId));
    }
}
