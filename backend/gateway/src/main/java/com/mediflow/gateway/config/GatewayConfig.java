package com.mediflow.gateway.config;

import com.mediflow.gateway.security.JwtProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class GatewayConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
