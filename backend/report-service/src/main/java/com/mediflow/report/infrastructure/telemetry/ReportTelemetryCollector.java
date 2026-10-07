package com.mediflow.report.infrastructure.telemetry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import com.mediflow.report.application.dto.response.ReportTelemetrySnapshot;
import com.mediflow.report.application.dto.response.ReportTelemetrySnapshot.Replay;
import com.mediflow.report.application.port.in.ReadReportTelemetryUseCase;
import com.mediflow.report.infrastructure.config.ReportTelemetryProperties;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/** Cached aggregate gauges. Scraping never performs DB work or labels individual entities. */
public final class ReportTelemetryCollector {
    private static final Logger LOG = LoggerFactory.getLogger(ReportTelemetryCollector.class);
    private final ReadReportTelemetryUseCase reader;
    private final ReportTelemetryProperties properties;
    private final Clock clock;
    private final Counter failures;
    private final AtomicReference<Sample> sample = new AtomicReference<>(new Sample(null, null));

    public ReportTelemetryCollector(ReadReportTelemetryUseCase reader, ReportTelemetryProperties properties,
            Clock clock, MeterRegistry registry) {
        this.reader = reader;
        this.properties = properties;
        this.clock = clock;
        failures = registry.counter("report.telemetry.sample.failures");
        gauge(registry, "report.telemetry.available", collector -> collector.fresh() == null ? 0 : 1);
        gauge(registry, "report.telemetry.sample.age.seconds", collector -> {
            Instant at = collector.sample.get().lastSuccessAt();
            return at == null ? Double.NaN : collector.age(at);
        });
        data(registry, "report.admission.pending", ReportTelemetrySnapshot::pendingAdmissions);
        data(registry, "report.admission.pending.age.seconds", s -> age(s.oldestPendingObservedAt()));
        data(registry, "report.source.legacy.unverified", ReportTelemetrySnapshot::legacyUnverifiedSources);
        replayGauges(registry, "operational", ReportTelemetrySnapshot::operational);
        replayGauges(registry, "cash", ReportTelemetrySnapshot::cash);
        gauge(registry, "report.telemetry.alert", c -> c.fresh() == null ? 1 : 0, "signal", "sample_unavailable");
        alert(registry, "pending_admission_age", s -> s.pendingAdmissions() > 0
                && age(s.oldestPendingObservedAt()) >= properties.pendingAgeSeconds());
        alert(registry, "operational_replay_age", s -> old(s.operational()));
        alert(registry, "cash_replay_age", s -> old(s.cash()));
        alert(registry, "operational_replay_failed", s -> s.operational().failed() > 0);
        alert(registry, "cash_replay_failed", s -> s.cash().failed() > 0);
        alert(registry, "legacy_unverified_sources", s -> s.legacyUnverifiedSources() > 0);
    }

    /** Fixed delay and synchronization bound collection to one query per process at a time. */
    @Scheduled(fixedDelayString = "${mediflow.report.telemetry.sample-interval-ms:30000}",
            initialDelayString = "${mediflow.report.telemetry.sample-interval-ms:30000}")
    public synchronized void sample() {
        try {
            ReportTelemetrySnapshot snapshot = java.util.Objects.requireNonNull(reader.capture());
            // Freshness is based on local receipt, not a possibly skewed DB clock.
            sample.set(new Sample(snapshot, clock.instant()));
        } catch (RuntimeException failure) {
            sample.updateAndGet(previous -> new Sample(null, previous.lastSuccessAt()));
            failures.increment();
            // Exceptions can contain SQL parameters or payloads. Never log them or use them as tags.
            LOG.warn("Report telemetry sample unavailable; retry on next scheduled collection");
        }
    }

    private void replayGauges(MeterRegistry registry, String kind,
            java.util.function.Function<ReportTelemetrySnapshot, Replay> select) {
        data(registry, "report.replay.generations", s -> select.apply(s).building(), "kind", kind, "status", "building");
        data(registry, "report.replay.generations", s -> select.apply(s).verified(), "kind", kind, "status", "verified");
        data(registry, "report.replay.generations", s -> select.apply(s).failed(), "kind", kind, "status", "failed");
        data(registry, "report.replay.remaining", s -> select.apply(s).remainingInputs(), "kind", kind);
        data(registry, "report.replay.building.age.seconds", s -> age(select.apply(s).oldestBuildingAt()), "kind", kind);
    }

    private void alert(MeterRegistry registry, String signal, java.util.function.Predicate<ReportTelemetrySnapshot> test) {
        data(registry, "report.telemetry.alert", s -> test.test(s) ? 1 : 0, "signal", signal);
    }

    private void data(MeterRegistry registry, String name, ToDoubleFunction<ReportTelemetrySnapshot> value, String... tags) {
        gauge(registry, name, c -> {
            ReportTelemetrySnapshot snapshot = c.fresh();
            return snapshot == null ? Double.NaN : value.applyAsDouble(snapshot);
        }, tags);
    }

    private void gauge(MeterRegistry registry, String name, ToDoubleFunction<ReportTelemetryCollector> value, String... tags) {
        Gauge.builder(name, this, value).tags(tags).register(registry);
    }

    private ReportTelemetrySnapshot fresh() {
        Sample current = sample.get();
        return current.snapshot() != null && age(current.lastSuccessAt()) < properties.staleAfterSeconds()
                ? current.snapshot() : null;
    }

    private boolean old(Replay replay) {
        return replay.building() > 0 && age(replay.oldestBuildingAt()) >= properties.replayAgeSeconds();
    }

    private long age(Instant at) {
        return at == null ? 0 : Math.max(0, Duration.between(at, clock.instant()).getSeconds());
    }

    private record Sample(ReportTelemetrySnapshot snapshot, Instant lastSuccessAt) { }
}
