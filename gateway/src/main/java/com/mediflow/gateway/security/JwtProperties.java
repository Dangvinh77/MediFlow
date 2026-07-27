package com.mediflow.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT config. The secret MUST be provided via env/config (never hard-coded) and be
 * at least 256 bits (32+ chars) for HS256. See docs/ai/07-security-rbac.md.
 */
@ConfigurationProperties(prefix = "mediflow.jwt")
public record JwtProperties(
        String secret,
        long accessTokenMinutes,
        long refreshTokenMinutes
) {
    public JwtProperties {
        if (accessTokenMinutes <= 0) {
            accessTokenMinutes = 30;
        }
        if (refreshTokenMinutes <= 0) {
            refreshTokenMinutes = 60 * 24;
        }
    }
}
