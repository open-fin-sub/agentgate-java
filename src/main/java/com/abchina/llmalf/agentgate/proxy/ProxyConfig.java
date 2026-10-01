package com.abchina.llmalf.agentgate.proxy;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 代理配置.
 *
 * <p>规范:从 application.yml 读取 agentgate.proxy.* 配置,集中管理代理参数.</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "agentgate.proxy")
public class ProxyConfig {

    /** Python 服务目标地址(含协议+主机+端口) */
    private String targetBaseUrl = "http://127.0.0.1:8000";

    /** 连接超时(ms) */
    private int connectTimeout = 5000;

    /** 读取超时(ms) */
    private int readTimeout = 30000;

    /** 连接池最大空闲连接数 */
    private int maxIdleConnections = 20;

    /** 连接池保活时长(分钟) */
    private int keepAliveMinutes = 5;
}
