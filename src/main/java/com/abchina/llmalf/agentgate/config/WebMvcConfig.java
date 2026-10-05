package com.abchina.llmalf.agentgate.config;

import com.abchina.llmalf.agentgate.common.UserContextInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置.
 *
 * <p>CORS 语义对齐 Python app.py:默认允许本地 5173 开发源,全部方法与请求头;
 * 允许源可通过 agentgate.cors.allowed-origins 配置(逗号分隔).
 * 用户上下文拦截器对齐 UserContextMiddleware(header 透传)。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** CORS 允许源(逗号分隔,默认对齐 Python app.py) */
    @Value("${agentgate.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("*")
                .allowedHeaders("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserContextInterceptor()).addPathPatterns("/**");
    }
}
