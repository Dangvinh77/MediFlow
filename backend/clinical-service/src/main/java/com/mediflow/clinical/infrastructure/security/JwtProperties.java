package com.mediflow.clinical.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

/** Shared HS256 secret used to verify gateway-issued access tokens. */
@ConfigurationProperties(prefix = "mediflow.jwt")
public record JwtProperties(String secret) {

    private static final int MINIMUM_HS256_KEY_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("mediflow.jwt.secret không được để trống");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_HS256_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "mediflow.jwt.secret phải có ít nhất 32 byte để dùng với HS256");
        }
    }
}
