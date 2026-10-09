package com.abchina.llmalf.agentgate.service.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 用例重排请求.
 */
@Data
public class ReorderCasesRequest {

    /** 用例 id 完整序列 */
    @NotNull(message = "case_ids 不能为空")
    private List<String> caseIds;
}
