package com.mediflow.inpatient.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI inpatientOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MediFlow Inpatient Service")
                .version("v1")
                .description("Preliminary foundation; business API awaits its implementation-ready spec."));
    }
}
