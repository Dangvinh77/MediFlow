package com.mediflow.report.infrastructure.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.mediflow.report.application.dto.response.ReportTelemetrySnapshot;
import com.mediflow.report.application.dto.response.ReportTelemetrySnapshot.Replay;
import com.mediflow.report.application.port.in.ReadReportTelemetryUseCase;
import com.mediflow.report.infrastructure.config.ReportTelemetryProperties;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ReportTelemetryCollectorTest {
    private static final Instant NOW = Instant.parse("2026-10-07T04:00:00Z");
    private final ReadReportTelemetryUseCase reader = mock(ReadReportTelemetryUseCase.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MutableClock clock = new MutableClock(NOW);
    private final ReportTelemetryCollector collector = new ReportTelemetryCollector(reader,
            new ReportTelemetryProperties(30000, 900, 3600, 180), clock, registry);

    @Test
    void beforeFirstSample_unknownIsNotHealthyZero() {
        assertThat(value("report.telemetry.available")).isZero();
        assertThat(value("report.telemetry.alert", "signal", "sample_unavailable")).isOne();
        assertThat(value("report.admission.pending")).isNaN();
        assertThat(value("report.replay.remaining", "kind", "cash")).isNaN();
    }

    @Test
    void sample_gaugesAreBoundedAggregateOnlyAndDoNotQueryOnScrape() {
        when(reader.capture()).thenReturn(snapshot(NOW.minusSeconds(900), NOW.minusSeconds(3600)));
        collector.sample();
        assertThat(value("report.telemetry.available")).isOne();
        assertThat(value("report.admission.pending")).isEqualTo(2);
        assertThat(value("report.admission.pending.age.seconds")).isEqualTo(900);
        assertThat(value("report.replay.remaining", "kind", "operational")).isEqualTo(7);
        assertThat(value("report.replay.generations", "kind", "cash", "status", "failed")).isEqualTo(1);
        assertThat(value("report.telemetry.alert", "signal", "pending_admission_age")).isOne();
        assertThat(value("report.telemetry.alert", "signal", "operational_replay_age")).isOne();
        assertThat(value("report.telemetry.alert", "signal", "legacy_unverified_sources")).isOne();
        assertThat(registry.getMeters()).hasSize(23);
        registry.getMeters().forEach(meter -> meter.getId().getTags().forEach(tag -> {
            assertThat(tag.getKey()).isIn("kind", "status", "signal");
            assertThat(tag.getValue()).doesNotContain("2026", "patient", "department", "generationId");
        }));
        org.mockito.Mockito.verify(reader).capture();
    }

    @Test
    void sampleFailure_discardsValuesAndRecoversWithoutLeakingExceptionLabels() {
        when(reader.capture()).thenReturn(snapshot(null, null))
                .thenThrow(new IllegalStateException("secret patient payload"))
                .thenReturn(snapshot(null, null));
        collector.sample();
        collector.sample();
        assertThat(value("report.admission.pending")).isNaN();
        assertThat(value("report.telemetry.available")).isZero();
        assertThat(registry.get("report.telemetry.sample.failures").counter().count()).isOne();
        collector.sample();
        assertThat(value("report.telemetry.available")).isOne();
        assertThat(registry.getMeters()).hasSize(23);
    }

    @Test
    void staleSample_becomesUnavailableEvenWhenSchedulerHasStopped() {
        when(reader.capture()).thenReturn(snapshot(null, null));
        collector.sample();
        clock.now.set(NOW.plusSeconds(179));
        assertThat(value("report.telemetry.available")).isOne();
        clock.now.set(NOW.plusSeconds(180));
        assertThat(value("report.telemetry.available")).isZero();
        assertThat(value("report.admission.pending")).isNaN();
        assertThat(value("report.telemetry.sample.age.seconds")).isEqualTo(180);
        assertThat(value("report.telemetry.alert", "signal", "sample_unavailable")).isOne();
    }

    @Test
    void futureDatabaseClock_cannotKeepSampleFreshForever() {
        var empty = new Replay(0, 0, 0, 0, 0, null);
        when(reader.capture()).thenReturn(new ReportTelemetrySnapshot(NOW.plusSeconds(86400), 0, null, 0, empty, empty));
        collector.sample();
        assertThat(value("report.telemetry.available")).isOne();
        clock.now.set(NOW.plusSeconds(180));
        assertThat(value("report.telemetry.available")).isZero();
        assertThat(value("report.telemetry.sample.age.seconds")).isEqualTo(180);
    }

    @Test
    void sample_businessTimesInFutureNeverCreateNegativeAges() {
        when(reader.capture()).thenReturn(snapshot(NOW.plusSeconds(1), NOW.plusSeconds(1)));
        collector.sample();
        assertThat(value("report.admission.pending.age.seconds")).isZero();
        assertThat(value("report.telemetry.alert", "signal", "pending_admission_age")).isZero();
    }

    @Test
    void thresholds_beforeBoundaryDoNotAlertAndHistoricalFailuresStayExplicit() {
        when(reader.capture()).thenReturn(snapshot(NOW.minusSeconds(899), NOW.minusSeconds(3599)));
        collector.sample();
        assertThat(value("report.telemetry.alert", "signal", "pending_admission_age")).isZero();
        assertThat(value("report.telemetry.alert", "signal", "operational_replay_age")).isZero();
        assertThat(value("report.telemetry.alert", "signal", "cash_replay_failed")).isOne();
    }

    @Test
    void failureLog_containsNoExceptionOrPayload() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ReportTelemetryCollector.class);
        var logs = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        logs.start(); logger.addAppender(logs);
        try {
            when(reader.capture()).thenThrow(new IllegalStateException("patientId=private token=secret"));
            collector.sample();
            assertThat(logs.list).hasSize(1).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("private", "secret", "patientId", "token");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally { logger.detachAppender(logs); logs.stop(); }
    }

    @Test
    void concurrentTriggers_areSerializedAndDoNotRegisterMoreMeters() throws Exception {
        var active = new java.util.concurrent.atomic.AtomicInteger();
        var maximum = new java.util.concurrent.atomic.AtomicInteger();
        when(reader.capture()).thenAnswer(invocation -> {
            maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
            try { Thread.yield(); return snapshot(null, null); }
            finally { active.decrementAndGet(); }
        });
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 100; i++) tasks.add(workers.submit(collector::sample));
            for (var task : tasks) task.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(maximum).hasValue(1);
        assertThat(registry.getMeters()).hasSize(23);
        org.mockito.Mockito.verify(reader, org.mockito.Mockito.times(100)).capture();
    }

    private double value(String name, String... tags) {
        return registry.get(name).tags(tags).gauge().value();
    }

    private static ReportTelemetrySnapshot snapshot(Instant pendingAt, Instant buildingAt) {
        return new ReportTelemetrySnapshot(NOW, pendingAt == null ? 0 : 2, pendingAt, 3,
                new Replay(buildingAt == null ? 0 : 1, 2, 0, buildingAt == null ? 0 : 10,
                        buildingAt == null ? 0 : 3, buildingAt), new Replay(0, 1, 1, 0, 0, null));
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;
        MutableClock(Instant value) { now = new AtomicReference<>(value); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
}
