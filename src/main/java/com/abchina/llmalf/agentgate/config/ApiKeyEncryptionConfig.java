package com.abchina.llmalf.agentgate.config;

import com.abchina.llmalf.agentgate.logic.ApiKeyEncryptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * API Key 加密装配.
 *
 * <p>对齐 Python dependencies:AGENTGATE_API_KEY_ENCRYPTION_KEY 未配置时
 * 不装配(对应 api_keys=None,端点返回 503);经 Spring Environment 读取,
 * 测试属性源可覆盖。</p>
 */
@Configuration
public class ApiKeyEncryptionConfig {

    /**
     * 条件装配加密器(主密钥环境变量已配置且非空白).
     *
     * @param environment Spring 环境(含系统环境变量与测试属性)
     * @return 加密器
     */
    @Bean
    @Conditional(ApiKeyEncryptorEnabledCondition.class)
    public ApiKeyEncryptor apiKeyEncryptor(Environment environment) {
        return ApiKeyEncryptor.fromEnvironment(
                environment.getProperty(ApiKeyEncryptor.MASTER_KEY_ENV));
    }
}
