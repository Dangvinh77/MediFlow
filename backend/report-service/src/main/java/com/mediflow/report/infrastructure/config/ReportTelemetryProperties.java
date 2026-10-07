package com.mediflow.report.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Local operational thresholds, not patient/clinical SLAs or accepted source-coverage proof. */
@ConfigurationProperties("mediflow.report.telemetry")
public record ReportTelemetryProperties(@DefaultValue("30000") long sampleIntervalMs,
        @DefaultValue("900") long pendingAgeSeconds, @DefaultValue("3600") long replayAgeSeconds,
        @DefaultValue("180") long staleAfterSeconds) {
    public ReportTelemetryProperties {
        if (sampleIntervalMs < 5000 || sampleIntervalMs > 3600000
                || pendingAgeSeconds < 60 || pendingAgeSeconds > 604800
                || replayAgeSeconds < 300 || replayAgeSeconds > 2592000
                || staleAfterSeconds < (2 * sampleIntervalMs + 999) / 1000 || staleAfterSeconds > 86400) {
            throw new IllegalArgumentException("Invalid bounded Report telemetry thresholds");
        }
    }
}
