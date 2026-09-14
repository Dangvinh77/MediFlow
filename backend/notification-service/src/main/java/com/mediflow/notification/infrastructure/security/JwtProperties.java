package com.mediflow.notification.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

/**
 * Cấu hình dùng để xác minh JWT tại notification-service (docs/ai/07-security-rbac.md).
 *
 * @param secret khóa bí mật HS256 dùng chung với gateway; phải có ít nhất 32 byte
 */
@ConfigurationProperties(prefix = "mediflow.jwt")
public record JwtProperties(String secret) {

    private static final int MINIMUM_HS256_KEY_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank() || secret.startsWith("${")) {
            throw new IllegalArgumentException("mediflow.jwt.secret không được để trống");
        }
        int secretLength = secret.getBytes(StandardCharsets.UTF_8).length;
        if (secretLength < MINIMUM_HS256_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "mediflow.jwt.secret phải có ít nhất 32 byte để dùng với HS256");
        }
    }
}
