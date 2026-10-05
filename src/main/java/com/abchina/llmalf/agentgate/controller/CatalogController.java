package com.abchina.llmalf.agentgate.controller;

import com.abchina.llmalf.agentgate.common.ResponseBase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 目标版本目录(只读发现).
 *
 * <p>对齐 Python server/routes/catalogs.py:返回 demo 目标版本
 * id/label 列表(契约数据固化,不迁移 demo 引擎)。</p>
 */
@RestController
@RequestMapping("/api")
public class CatalogController {

    /**
     * 目标版本列表.
     *
     * @return 版本 id 与展示名
     */
    @GetMapping("/versions")
    public ResponseBase<List<Map<String, String>>> targetVersions() {
        List<Map<String, String>> versions = new ArrayList<>();
        versions.add(version("loan-agent-v1-risky", "Risky version"));
        versions.add(version("loan-agent-v2-fixed", "Fixed version"));
        return ResponseBase.success(versions);
    }

    private static Map<String, String> version(String id, String label) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("label", label);
        return item;
    }
}
