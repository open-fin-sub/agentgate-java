package com.abchina.llmalf.agentgate.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * 系统探活接口(对齐 Python system.py).
 */
@RestController
public class SystemController {

    /** GET /health:进程健康探测 */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Collections.singletonMap("status", "ok");
    }
}
