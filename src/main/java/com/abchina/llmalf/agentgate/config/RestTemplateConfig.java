package com.abchina.llmalf.agentgate.config;

import com.abchina.llmalf.agentgate.proxy.ProxyConfig;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

/**
 * RestTemplate 配置(代理专用).
 *
 * <p>规范:底层使用 OkHttp(规范文档 3.2 节直接依赖),配置连接池与超时;
 * 透明错误处理——不因 4xx/5xx 抛异常,由 Controller 原样返回 Python 响应.</p>
 */
@Configuration
public class RestTemplateConfig {

    @Bean("proxyRestTemplate")
    public RestTemplate proxyRestTemplate(ProxyConfig proxyConfig) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(proxyConfig.getConnectTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(proxyConfig.getReadTimeout(), TimeUnit.MILLISECONDS)
                .connectionPool(new ConnectionPool(
                        proxyConfig.getMaxIdleConnections(),
                        proxyConfig.getKeepAliveMinutes(),
                        TimeUnit.MINUTES))
                .build();

        OkHttp3ClientHttpRequestFactory factory = new OkHttp3ClientHttpRequestFactory(okHttpClient);

        RestTemplate restTemplate = new RestTemplate(factory);

        restTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }

            @Override
            public void handleError(ClientHttpResponse response) {
            }
        });

        return restTemplate;
    }
}
