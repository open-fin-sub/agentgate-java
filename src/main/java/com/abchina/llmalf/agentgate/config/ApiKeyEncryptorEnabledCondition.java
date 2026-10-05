package com.abchina.llmalf.agentgate.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 主密钥已配置条件.
 *
 * <p>AGENTGATE_API_KEY_ENCRYPTION_KEY 存在且非空白时装配加密器
 * (系统环境变量与测试属性源均可触发)。</p>
 */
public class ApiKeyEncryptorEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String value = context.getEnvironment()
                .getProperty("AGENTGATE_API_KEY_ENCRYPTION_KEY");
        return value != null && !value.trim().isEmpty();
    }
}
