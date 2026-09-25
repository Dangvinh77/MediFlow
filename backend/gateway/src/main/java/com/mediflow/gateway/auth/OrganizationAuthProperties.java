package com.mediflow.gateway.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Timeout and circuit-breaker settings for the Organization auth dependency. */
@ConfigurationProperties(prefix = "mediflow.organization-auth")
public record OrganizationAuthProperties(
        long timeoutMillis,
        int failureRateThreshold,
        int slidingWindowSize,
        int minimumNumberOfCalls,
        long waitDurationSeconds) {

    public OrganizationAuthProperties {
        if (timeoutMillis <= 0) {
            timeoutMillis = 1500;
        }
        if (failureRateThreshold <= 0 || failureRateThreshold > 100) {
            failureRateThreshold = 50;
        }
        if (slidingWindowSize < 2) {
            slidingWindowSize = 10;
        }
        if (minimumNumberOfCalls < 1 || minimumNumberOfCalls > slidingWindowSize) {
            minimumNumberOfCalls = Math.min(5, slidingWindowSize);
        }
        if (waitDurationSeconds <= 0) {
            waitDurationSeconds = 10;
        }
    }
}
