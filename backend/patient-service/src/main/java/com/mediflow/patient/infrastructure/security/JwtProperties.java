package com.mediflow.patient.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT verification config. The secret must match the gateway's (shared HS256 key) and must come
 * from the environment in anything but local development.
 */
@ConfigurationProperties(prefix = "mediflow.jwt")
public record JwtProperties(String secret) {
}
