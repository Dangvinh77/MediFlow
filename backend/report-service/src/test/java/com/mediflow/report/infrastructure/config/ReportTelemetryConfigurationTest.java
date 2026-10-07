package com.mediflow.report.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import com.mediflow.report.application.port.in.ReadReportTelemetryUseCase;
import com.mediflow.report.infrastructure.telemetry.ReportTelemetryCollector;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ReportTelemetryConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ReportTelemetryConfiguration.class)
            .withBean(ReadReportTelemetryUseCase.class, () -> mock(ReadReportTelemetryUseCase.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void defaultDisabled_createsNoSamplerNoMetersAndNoScheduling() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(ReportTelemetryCollector.class)
                    .doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
            assertThat(context.getBean(MeterRegistry.class).getMeters()).isEmpty();
        });
    }

    @Test
    void optIn_registersOneBoundedTaskAndExactDefaults() {
        runner.withPropertyValues("mediflow.report.telemetry.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(ReportTelemetryCollector.class);
            assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).hasSize(1);
            assertThat(context.getBean(ReportTelemetryProperties.class))
                    .isEqualTo(new ReportTelemetryProperties(30000, 900, 3600, 180));
            assertThat(context.getBean(MeterRegistry.class).getMeters()).hasSize(23);
        });
    }

    @Test
    void configuredThresholds_preserveBoundsAndAreActuallyBound() {
        runner.withPropertyValues("mediflow.report.telemetry.enabled=true", "mediflow.report.telemetry.sample-interval-ms=5000",
                "mediflow.report.telemetry.pending-age-seconds=60", "mediflow.report.telemetry.replay-age-seconds=300",
                "mediflow.report.telemetry.stale-after-seconds=10").run(context ->
                    assertThat(context.getBean(ReportTelemetryProperties.class))
                            .isEqualTo(new ReportTelemetryProperties(5000, 60, 300, 10)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sample-interval-ms=4999", "sample-interval-ms=3600001", "pending-age-seconds=59",
            "pending-age-seconds=604801", "replay-age-seconds=299", "replay-age-seconds=2592001",
            "stale-after-seconds=59", "stale-after-seconds=86401"})
    void invalidThresholds_failStartupInsteadOfRunningUnbounded(String property) {
        runner.withPropertyValues("mediflow.report.telemetry.enabled=true", "mediflow.report.telemetry." + property)
                .run(context -> assertThat(context).hasFailed());
    }
}
