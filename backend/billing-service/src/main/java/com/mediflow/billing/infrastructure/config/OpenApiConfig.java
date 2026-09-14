package com.mediflow.billing.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata OpenAPI cho billing-service, phục vụ {@code /swagger-ui.html} và {@code /v3/api-docs}
 * (docs/ai/05-api-conventions.md). Khai báo scheme Bearer JWT để có thể "Authorize" và thử endpoint
 * ngay trên Swagger UI — token vẫn phải lấy từ {@code POST /api/v1/auth/login} ở gateway.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI billingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MediFlow — Billing Service")
                        .description("Viện phí, hóa đơn và saga kê đơn → hóa đơn → thanh toán → xuất thuốc.")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
