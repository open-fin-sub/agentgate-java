package com.abchina.llmalf.agentgate.integration;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

/**
 * BJS 任务派发器.
 *
 * <p>对齐 Python integrations/job_dispatchers/bjs_job_dispatcher.py:
 * 提交 URL 严格校验(绝对 HTTP(S)、无凭据/查询/锚点/空白)、
 * 响应 code="0" 且 message="success" 才算成功;
 * 端点不可达时 mock 降级(对齐 Python MOCK 分支,TODO 待行内 BJS 可达后移除)。
 * HTTP 客户端禁用系统代理(对齐 Python 直连语义,避免开发机系统代理劫持行内域名)。</p>
 */
@Slf4j
@Component
public class BjsJobDispatcher {

    /** 提交 URL 环境变量(与 Python .env 同名) */
    public static final String SUBMIT_URL_ENV = "AGENTGATE_BJS_SUBMIT_URL";

    /** 任务 id 环境变量(与 Python .env 同名) */
    public static final String JOB_ID_ENV = "AGENTGATE_BJS_JOB_ID";

    private final String submitUrl;
    private final String jobId;
    private final long timeoutSeconds;
    private final OkHttpClient client;

    private String validatedUrl;

    /**
     * 从配置构造派发器.
     *
     * @param submitUrl 提交 URL(application.yml 经同名环境变量占位注入)
     * @param jobId 任务 id(application.yml 经同名环境变量占位注入)
     * @param timeoutSeconds 超时秒数
     */
    @org.springframework.beans.factory.annotation.Autowired
    public BjsJobDispatcher(
            @Value("${agentgate.bjs.submit-url:}") String submitUrl,
            @Value("${agentgate.bjs.job-id:}") String jobId,
            @Value("${agentgate.bjs.timeout-seconds:10}") long timeoutSeconds) {
        this(submitUrl, jobId, timeoutSeconds, null);
    }

    /**
     * 构造派发器(可注入自定义客户端,测试用).
     *
     * @param submitUrl 提交 URL
     * @param jobId 任务 id
     * @param timeoutSeconds 超时秒数
     * @param client HTTP 客户端(可空则自建)
     */
    public BjsJobDispatcher(String submitUrl, String jobId, long timeoutSeconds,
            OkHttpClient client) {
        this.submitUrl = submitUrl;
        this.jobId = jobId;
        this.timeoutSeconds = timeoutSeconds;
        this.client = client != null ? client : new OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 构造后校验(失败即阻止 Bean 就绪).
     */
    private volatile boolean configured;

    @PostConstruct
    public void validate() {
        if (timeoutSeconds <= 0 || !Double.isFinite(timeoutSeconds)) {
            throw new IllegalArgumentException("timeout_seconds must be positive and finite");
        }
        try {
            this.validatedUrl = validatedSubmitUrl();
            if (jobId == null || jobId.trim().isEmpty()) {
                throw new IllegalArgumentException(JOB_ID_ENV + " must not be blank");
            }
            this.configured = true;
        } catch (IllegalArgumentException e) {
            this.configured = false;
            log.warn("BJS dispatcher disabled ({}); dispatch will 503", e.getMessage());
        }
    }

    /**
     * 是否已配置可用.
     *
     * @return 已配置为 true
     */
    public boolean isConfigured() {
        return configured;
    }

    /**
     * 提交一个 Run 到 BJS.
     *
     * @param runId Run id
     */
    public void submit(String runId) {
        if (!configured) {
            throw new IllegalStateException("BJS dispatcher is not configured");
        }
        validateIdentifier(runId, "run_id");
        String url = validatedSubmitUrl();
        String normalizedJobId = jobId == null ? "" : jobId.trim();
        if (normalizedJobId.isEmpty()) {
            throw new IllegalArgumentException(JOB_ID_ENV + " must not be blank");
        }
        String target = url + (url.contains("?") ? "&" : "?") + "taskId="
                + urlEncode(runId) + "&jobId=" + urlEncode(normalizedJobId);
        Request request = new Request.Builder()
                .url(target)
                .post(RequestBody.create(new byte[0], null))
                .header("Accept", "application/json")
                .build();
        String body;
        try (Response response = client.newCall(request).execute()) {
            body = response.body() == null ? "" : response.body().string();
        } catch (IOException e) {
            log.warn("BJS submission mocked (endpoint unreachable): run_id={}, {}", runId,
                    e.getClass().getSimpleName());
            body = "{\"code\":\"0\",\"message\":\"success\"}";
        }
        com.fasterxml.jackson.databind.JsonNode payload;
        try {
            payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        } catch (IOException e) {
            throw new IllegalStateException("BJS submission returned invalid JSON", e);
        }
        if (payload == null || !payload.isObject()) {
            throw new IllegalStateException("BJS submission returned an invalid response");
        }
        String code = payload.path("code").asText("");
        String message = payload.path("message").asText("");
        if (!"0".equals(code) || !"success".equals(message)) {
            throw new IllegalStateException(
                    "BJS submission rejected: code='" + code + "', message='" + message + "'");
        }
        log.info("BJS submission accepted: run_id={}", runId);
    }

    /**
     * 取消(BJS 无取消 API,仅校验并记录).
     *
     * @param runId Run id
     */
    public void cancel(String runId) {
        validateIdentifier(runId, "run_id");
        log.info("BJS cancellation is unsupported by the remote API: run_id={}", runId);
    }

    private String validatedSubmitUrl() {
        if (submitUrl == null || submitUrl.trim().isEmpty()) {
            throw new IllegalArgumentException(SUBMIT_URL_ENV + " must not be blank");
        }
        String normalized = submitUrl.trim();
        URI uri;
        try {
            uri = new URI(normalized);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(
                    SUBMIT_URL_ENV + " must be a valid HTTP(S) URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(
                java.util.Locale.ROOT);
        int port = uri.getPort();
        boolean hasQuery = uri.getRawQuery() != null;
        boolean hasFragment = uri.getRawFragment() != null;
        boolean hasUserInfo = uri.getRawUserInfo() != null;
        boolean hasWhitespace = false;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isWhitespace(c) || c < 0x20) {
                hasWhitespace = true;
                break;
            }
        }
        if (!("http".equals(scheme) || "https".equals(scheme))
                || uri.getHost() == null
                || (port != -1 && port < 1)
                || hasUserInfo || hasQuery || hasFragment || hasWhitespace) {
            throw new IllegalArgumentException(
                    SUBMIT_URL_ENV + " must be an absolute HTTP(S) URL");
        }
        return normalized;
    }

    private static void validateIdentifier(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 unsupported", e);
        }
    }
}
