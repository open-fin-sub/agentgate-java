package com.abchina.llmalf.agentgate.controller;

import com.abchina.llmalf.agentgate.integration.OtlpIngest;
import com.abchina.llmalf.agentgate.logic.TraceLogic;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OTLP 接收端点.
 *
 * <p>对齐 Python server/routes/telemetry.py:POST /v1/traces
 * (application/json,202,accepted_spans;**全部响应无信封**,错误体为
 * {"detail": msg};415 非法 content-type、413 超 4MiB、422 解析/结构错误)。</p>
 */
@RestController
public class TelemetryController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TraceLogic traceLogic;
    private final long maxRequestBytes;

    public TelemetryController(TraceLogic traceLogic,
            @Value("${agentgate.telemetry.max-request-bytes:4194304}")
            long maxRequestBytes) {
        this.traceLogic = traceLogic;
        this.maxRequestBytes = maxRequestBytes;
    }

    /**
     * 接收 OTLP JSON 轨迹(响应无信封).
     *
     * @param body 原始请求体
     * @param request HTTP 请求
     * @return 202 + accepted_spans,或裸 detail 错误体
     */
    @PostMapping("/v1/traces")
    public ResponseEntity<Map<String, Object>> receiveTraces(
            @RequestBody String body, HttpServletRequest request) {
        String contentType = request.getHeader("Content-Type");
        String mediaType = contentType == null ? "" : contentType.split(";", 2)[0];
        if (!"application/json".equalsIgnoreCase(mediaType.trim())) {
            return detail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "OTLP receiver accepts application/json only");
        }
        String contentLength = request.getHeader("Content-Length");
        if (contentLength != null) {
            try {
                if (Long.parseLong(contentLength) > maxRequestBytes) {
                    return tooLarge();
                }
            } catch (NumberFormatException e) {
                return detail(HttpStatus.BAD_REQUEST, "invalid Content-Length header");
            }
        }
        if (body != null && body.length() > maxRequestBytes) {
            return tooLarge();
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(body == null ? "" : body);
        } catch (JsonProcessingException e) {
            return detail(HttpStatus.UNPROCESSABLE_ENTITY,
                    pythonJsonError(body, e));
        }
        if (root == null || !root.isObject()) {
            return detail(HttpStatus.UNPROCESSABLE_ENTITY,
                    "OTLP payload must be an object");
        }
        Map<String, Object> payload = MAPPER.convertValue(root, Map.class);
        int accepted;
        try {
            accepted = OtlpIngest.ingest(payload, traceLogic::saveTrace);
        } catch (IllegalArgumentException e) {
            return detail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
        Map<String, Object> success = new LinkedHashMap<>();
        success.put("accepted_spans", accepted);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(success);
    }

    private ResponseEntity<Map<String, Object>> tooLarge() {
        return detail(HttpStatus.PAYLOAD_TOO_LARGE,
                "OTLP request body exceeds the 4 MiB limit");
    }

    private static ResponseEntity<Map<String, Object>> detail(HttpStatus status,
            String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("detail", message);
        return ResponseEntity.status(status).body(body);
    }

    /**
     * 将 Jackson 解析错误映射为 Python json 模块风格消息
     * (覆盖 Expecting value / Extra data 两类常见形态,其余回退 Jackson 消息).
     */
    private static String pythonJsonError(String body,
            JsonProcessingException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        int line = e.getLocation() == null ? 1 : e.getLocation().getLineNr();
        int column = e.getLocation() == null ? 1 : e.getLocation().getColumnNr();
        int charOffset = charOffsetOf(body, line, column);
        if (message.startsWith("Unrecognized token")) {
            // Jackson 定位在 token 之后,Python 报 token 起点:回退 token 长度
            int tokenLength = unrecognizedTokenLength(message);
            int startColumn = Math.max(1, column - tokenLength);
            return "Expecting value: line " + line + " column " + startColumn
                    + " (char " + Math.max(0, charOffset - tokenLength) + ")";
        }
        if (message.startsWith("Unexpected character")
                || message.startsWith("Unexpected end-of-input")) {
            return "Expecting value: line " + line + " column " + column
                    + " (char " + charOffset + ")";
        }
        if (message.startsWith("Trailing token")) {
            return "Extra data: line " + line + " column " + column
                    + " (char " + charOffset + ")";
        }
        return message;
    }

    private static int unrecognizedTokenLength(String message) {
        int start = message.indexOf('\'');
        if (start < 0) {
            return 0;
        }
        int end = message.indexOf('\'', start + 1);
        if (end < 0) {
            return 0;
        }
        return end - start - 1;
    }

    private static int charOffsetOf(String body, int line, int column) {
        if (body == null || line < 1 || column < 1) {
            return 0;
        }
        int offset = 0;
        int currentLine = 1;
        while (currentLine < line && offset < body.length()) {
            if (body.charAt(offset) == '\n') {
                currentLine++;
            }
            offset++;
        }
        return offset + column - 1;
    }
}
