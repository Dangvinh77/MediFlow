package com.mediflow.report.infrastructure.security;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Externalized HS256 secret used for defense-in-depth JWT verification. */
@ConfigurationProperties(prefix = "mediflow.jwt")
public record JwtProperties(String secret) {

    private static final int MINIMUM_HS256_KEY_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank() || secret.startsWith("${")) {
            throw new IllegalArgumentException("mediflow.jwt.secret không được để trống");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_HS256_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "mediflow.jwt.secret phải có ít nhất 32 byte để dùng với HS256");
        }
    }
}
