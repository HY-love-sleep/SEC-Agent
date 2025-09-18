package com.cubigdata.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * MCP客户端配置类
 * @author yHong
 * @version 1.0
 * @since 2025/9/15 18:40
 */
@Configuration
public class McpConfig {

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }
}
