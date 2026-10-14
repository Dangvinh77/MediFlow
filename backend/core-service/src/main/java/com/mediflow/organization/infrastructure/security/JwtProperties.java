package com.mediflow.organization.infrastructure.security;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Shared HS256 secret used to verify gateway-issued and service-issued JWTs. */
@ConfigurationProperties(prefix = "mediflow.jwt")
public record JwtProperties(String secret) {

    public JwtProperties {
        if (secret == null || secret.isBlank() || secret.startsWith("${")) {
            throw new IllegalArgumentException("mediflow.jwt.secret must be provided through MEDIFLOW_JWT_SECRET");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("mediflow.jwt.secret must contain at least 32 bytes for HS256");
        }
    }
}
