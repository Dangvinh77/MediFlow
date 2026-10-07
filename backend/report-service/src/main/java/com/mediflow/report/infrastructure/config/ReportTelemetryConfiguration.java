package com.mediflow.report.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.mediflow.report.application.port.in.ReadReportTelemetryUseCase;
import com.mediflow.report.infrastructure.telemetry.ReportTelemetryCollector;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "mediflow.report.telemetry.enabled", havingValue = "true")
@EnableConfigurationProperties(ReportTelemetryProperties.class)
@EnableScheduling
public class ReportTelemetryConfiguration {
    @Bean
    ReportTelemetryCollector reportTelemetryCollector(ReadReportTelemetryUseCase reader,
            ReportTelemetryProperties properties, MeterRegistry registry) {
        return new ReportTelemetryCollector(reader, properties, Clock.systemUTC(), registry);
    }
}
