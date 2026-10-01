package com.abchina.llmalf.agentgate.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置.
 *
 * <p>开启分页插件,适配 MySQL 方言;单页上限可通过 agentgate.page.max-limit 配置.</p>
 */
@Configuration
public class MyBatisPlusConfig {

    /** 分页单页上限 */
    @Value("${agentgate.page.max-limit:500}")
    private long pageMaxLimit;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pageInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        pageInterceptor.setMaxLimit(pageMaxLimit);
        interceptor.addInnerInterceptor(pageInterceptor);
        return interceptor;
    }
}
