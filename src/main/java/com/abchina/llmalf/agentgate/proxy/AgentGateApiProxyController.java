package com.abchina.llmalf.agentgate.proxy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * API 代理 Controller.
 *
 * <p>规范:透明转发前端请求到 Python 后端服务.
 * 前端请求 /race-api/agentgate/** -> 转发到 Python {targetBaseUrl}/{子路径}.
 * 支持 GET/POST/PUT/DELETE,请求头/请求体/查询参数原样透传,Python 响应原样返回.</p>
 */
@Slf4j
@RestController
@RequestMapping("/agentgate")
public class AgentGateApiProxyController {

    /** 请求头黑名单:由 HTTP 客户端自动管理,不可透传 */
    private static final Set<String> SKIP_REQUEST_HEADERS = new HashSet<>(Arrays.asList(
            "host", "content-length", "transfer-encoding", "connection"
    ));

    /** 响应头黑名单:由 Spring/Tomcat 自动管理,不可透传 */
    private static final Set<String> SKIP_RESPONSE_HEADERS = new HashSet<>(Arrays.asList(
            "transfer-encoding", "connection", "content-length"
    ));

    /** 需要读取请求体的 HTTP 方法 */
    private static final Set<String> BODY_METHODS = new HashSet<>(Arrays.asList(
            "POST", "PUT", "PATCH", "DELETE"
    ));

    @Autowired
    private ProxyConfig proxyConfig;

    @Autowired
    @Qualifier("proxyRestTemplate")
    private RestTemplate proxyRestTemplate;

    /**
     * 透明代理转发.
     *
     * <p>匹配 /agentgate 下所有子路径,提取子路径后拼接到 Python 服务地址,
     * 透传请求头和请求体,原样返回 Python 的响应.</p>
     *
     * @param request HTTP 请求
     * @return Python 服务的原始响应(状态码+响应头+响应体)
     */
    @RequestMapping("/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
        String method = request.getMethod();
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();

        String pathAfterContext = requestUri.substring(contextPath.length());
        String subPath = pathAfterContext.substring("/agentgate".length());
        if (subPath.isEmpty()) {
            subPath = "/";
        }

        String targetUrl = proxyConfig.getTargetBaseUrl() + subPath;
        if (request.getQueryString() != null) {
            targetUrl += "?" + request.getQueryString();
        }

        HttpHeaders headers = buildRequestHeaders(request);
        headers.set("user_team_id", "user_team_id");
        headers.set("user_id", "user_id");
        headers.set("user_name", "user_name");

        byte[] body = readRequestBody(request, method);

        HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);
        HttpMethod httpMethod = HttpMethod.resolve(method);

        long start = System.currentTimeMillis();
        log.info("代理请求转发: {} {} -> {}", method, pathAfterContext, targetUrl);

        try {
            ResponseEntity<byte[]> response = proxyRestTemplate.exchange(
                    targetUrl, httpMethod, entity, byte[].class);

            long elapsed = System.currentTimeMillis() - start;
            log.info("代理响应返回: {} {} status={} 耗时={}ms",
                    method, pathAfterContext, response.getStatusCodeValue(), elapsed);

            HttpHeaders responseHeaders = buildResponseHeaders(response.getHeaders());
            return new ResponseEntity<>(response.getBody(), responseHeaders, response.getStatusCode());
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("代理请求异常: {} {} 耗时={}ms error={}",
                    method, pathAfterContext, elapsed, e.getMessage(), e);
            throw new RuntimeException("代理请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建转发请求头(透传,排除黑名单).
     */
    private HttpHeaders buildRequestHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (!SKIP_REQUEST_HEADERS.contains(name.toLowerCase())) {
                headers.set(name, request.getHeader(name));
            }
        }
        return headers;
    }

    /**
     * 读取请求体(仅 POST/PUT/PATCH/DELETE).
     */
    private byte[] readRequestBody(HttpServletRequest request, String method) throws IOException {
        if (method != null && BODY_METHODS.contains(method.toUpperCase())) {
            return StreamUtils.copyToByteArray(request.getInputStream());
        }
        return null;
    }

    /**
     * 构建响应头(透传,排除黑名单).
     */
    private HttpHeaders buildResponseHeaders(HttpHeaders source) {
        HttpHeaders headers = new HttpHeaders();
        source.forEach((String key, List<String> values) -> {
            if (!SKIP_RESPONSE_HEADERS.contains(key.toLowerCase())) {
                headers.put(key, values);
            }
        });
        return headers;
    }
}
