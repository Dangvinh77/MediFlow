package com.mediflow.report.infrastructure.config;

import java.time.DateTimeException;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Infrastructure wiring for report-specific runtime configuration. */
@Configuration
public class ReportConfiguration {

    @Bean
    ZoneId reportZoneId(@Value("${mediflow.report.zone-id:Asia/Bangkok}") String configuredZone) {
        try {
            return ZoneId.of(configuredZone);
        } catch (DateTimeException ex) {
            throw new IllegalStateException("Invalid mediflow.report.zone-id: " + configuredZone, ex);
        }
    }
}
