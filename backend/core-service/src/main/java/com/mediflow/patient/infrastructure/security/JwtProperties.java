package com.mediflow.patient.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.nio.charset.StandardCharsets;

@ConfigurationProperties(prefix = "mediflow.jwt")
public record JwtProperties(String secret) {
    public JwtProperties {
        if (secret == null || secret.isBlank() || secret.startsWith("${")) {
            throw new IllegalArgumentException("mediflow.jwt.secret phải được cung cấp");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("mediflow.jwt.secret phải có ít nhất 32 byte");
        }
    }
}
