package com.mediflow.report.infrastructure.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/** OpenAPI metadata for the report read-model endpoints. */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "MediFlow Report API",
        version = "v1",
        description = "Read-only hospital reports. Missing departmentId means hospital scope; "
                + "missing data is returned as zero-filled reports."))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, in = SecuritySchemeIn.HEADER,
        scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
