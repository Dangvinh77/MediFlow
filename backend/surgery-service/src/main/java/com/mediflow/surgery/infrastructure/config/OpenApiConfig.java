package com.mediflow.surgery.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI surgeryOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MediFlow Surgery Service")
                .version("v1")
                .description("Platform foundation only; business APIs await contract gates."));
    }
}
