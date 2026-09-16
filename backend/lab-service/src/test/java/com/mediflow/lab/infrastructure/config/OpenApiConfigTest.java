package com.mediflow.lab.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void labOpenApi_describesServiceAndRequiresBearerJwt() {
        OpenAPI openApi = new OpenApiConfig().labOpenApi();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("MediFlow — Lab Service");
        assertThat(openApi.getInfo().getVersion()).isEqualTo("v1");
        assertThat(openApi.getInfo().getDescription()).isNotBlank();
        assertThat(openApi.getSecurity()).hasSize(1);
        assertThat(openApi.getSecurity().getFirst()).containsEntry("bearerAuth", java.util.List.of());

        SecurityScheme bearerAuth = openApi.getComponents().getSecuritySchemes().get("bearerAuth");
        assertThat(bearerAuth).isNotNull();
        assertThat(bearerAuth.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(bearerAuth.getScheme()).isEqualTo("bearer");
        assertThat(bearerAuth.getBearerFormat()).isEqualTo("JWT");
    }
}
