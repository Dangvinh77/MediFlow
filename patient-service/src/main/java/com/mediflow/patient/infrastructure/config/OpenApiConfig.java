package com.mediflow.patient.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI patientOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("MediFlow Patient Service API")
                .version("v1")
                .description("Hồ sơ bệnh nhân gốc (BENH_NHAN) — master patient index."));
    }
}
