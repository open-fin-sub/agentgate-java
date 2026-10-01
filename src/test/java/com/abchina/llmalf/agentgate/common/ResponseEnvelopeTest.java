package com.abchina.llmalf.agentgate.common;

import com.abchina.llmalf.agentgate.controller.SystemController;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.Collections;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 响应信封与异常映射契约测试.
 *
 * <p>对齐 Python ResponseEnvelopeMiddleware / errors.py 语义:
 * 成功包装 code="0"、错误 code="1" 且 HTTP 状态透传、校验失败 422、
 * 204 与 /v1/traces 跳过信封、兜底异常消息脱敏.</p>
 */
class ResponseEnvelopeTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SystemController(), new FixtureController())
                .setControllerAdvice(new ResponseEnvelopeAdvice(), new GlobalExceptionHandler())
                .build();
    }

    @Test
    void healthIsEnveloped() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.status").value("ok"));
    }

    @Test
    void rawJsonBodyIsWrapped() throws Exception {
        mockMvc.perform(get("/_fixture/raw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.hello").value("world"));
    }

    @Test
    void responseBasePassesThrough() throws Exception {
        mockMvc.perform(get("/_fixture/enveloped"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").value("kept"));
    }

    @Test
    void agentExceptionMapsStatusAndCode() throws Exception {
        mockMvc.perform(get("/_fixture/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("1"))
                .andExpect(jsonPath("$.message").value("数据集不存在: ds-1"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void agentExceptionDetailGoesToData() throws Exception {
        mockMvc.perform(get("/_fixture/detail"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("1"))
                .andExpect(jsonPath("$.message").value("xlsx_validation_failed"))
                .andExpect(jsonPath("$.data.issues").value(2));
    }

    @Test
    void unhandledExceptionIsSanitized() throws Exception {
        mockMvc.perform(get("/_fixture/secret"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("1"))
                .andExpect(jsonPath("$.message")
                        .value("connect failed password=[redacted] host=10.0.0.1"));
    }

    @Test
    void emptyMessageFallsBackToClassName() throws Exception {
        mockMvc.perform(get("/_fixture/empty"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("1"))
                .andExpect(jsonPath("$.message").value("IllegalStateException"));
    }

    @Test
    void validationFailureReturns422() throws Exception {
        mockMvc.perform(post("/_fixture/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("1"))
                .andExpect(jsonPath("$.message").value("name:name 不能为空"));
    }

    @Test
    void noContentBodyStaysEmpty() throws Exception {
        mockMvc.perform(get("/_fixture/no-content"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void otlpTracesPathSkipsEnvelope() throws Exception {
        mockMvc.perform(post("/v1/traces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.accepted").value(true));
    }

    /** 测试专用控制器:构造信封/异常各分支 */
    @RestController
    static class FixtureController {

        @GetMapping("/_fixture/raw")
        public Map<String, Object> raw() {
            return Collections.singletonMap("hello", "world");
        }

        @GetMapping("/_fixture/enveloped")
        public ResponseBase<String> enveloped() {
            return ResponseBase.success("kept");
        }

        @GetMapping("/_fixture/not-found")
        public Map<String, Object> notFound() {
            throw new AgentException(404, "数据集不存在: ds-1");
        }

        @GetMapping("/_fixture/detail")
        public Map<String, Object> detail() {
            throw new AgentException(422, "xlsx_validation_failed",
                    Collections.singletonMap("issues", 2));
        }

        @GetMapping("/_fixture/secret")
        public Map<String, Object> secret() {
            throw new IllegalStateException("connect failed password=abc123 host=10.0.0.1");
        }

        @GetMapping("/_fixture/empty")
        public Map<String, Object> empty() {
            throw new IllegalStateException("");
        }

        @PostMapping("/_fixture/valid")
        public Map<String, Object> valid(@RequestBody @Valid SampleVO vo) {
            return Collections.singletonMap("name", vo.getName());
        }

        @GetMapping("/_fixture/no-content")
        public ResponseEntity<Void> noContent() {
            return ResponseEntity.noContent().build();
        }

        @PostMapping("/v1/traces")
        public Map<String, Object> traces() {
            return Collections.singletonMap("accepted", true);
        }
    }

    @Data
    static class SampleVO {

        @NotBlank(message = "name 不能为空")
        private String name;
    }
}
