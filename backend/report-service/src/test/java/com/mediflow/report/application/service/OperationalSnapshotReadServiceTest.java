package com.mediflow.report.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.mediflow.report.application.exception.ReportPeriodValidationException;
import com.mediflow.report.application.exception.ReportProjectionUnavailableException;
import com.mediflow.report.application.port.out.OperationalSnapshotReadPort;
import com.mediflow.report.domain.model.OperationalContribution.Metric;
import com.mediflow.report.domain.model.OperationalReportPublication;

class OperationalSnapshotReadServiceTest {
    private final OperationalSnapshotReadPort store = mock(OperationalSnapshotReadPort.class);
    private final OperationalSnapshotReadService service = new OperationalSnapshotReadService(store, ZoneId.of("Asia/Bangkok"));
    private final LocalDate date = LocalDate.of(2028, 2, 28);
    private final UUID generation = UUID.randomUUID();
    private static final Set<Metric> SURGERY = Set.of(Metric.SURGERIES_COMPLETED, Metric.SURGERIES_CANCELLED, Metric.SURGERY_DURATION_MINUTES);

    @Test void read_absentPublication_unavailableInsteadOfZeros() {
        when(store.publication("SURGERY")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.surgery(date, date, null)).isInstanceOf(ReportProjectionUnavailableException.class);
        verify(store, never()).read(any(), any(), any(), any(), any());
    }
    @Test void read_acceptedEmptyPeriod_zeroFillsLeapDayAndReturnsExplicitSnapshot() {
        publication(SURGERY, "Asia/Bangkok");
        when(store.read(eq(generation), any(), any(), isNull(), eq(SURGERY))).thenReturn(List.of());
        var result = service.surgery(date, date.plusDays(2), null);
        assertThat(result.days()).hasSize(3);
        assertThat(result.days().get(1).date()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(result.days().getFirst().metrics().values()).containsOnly(0L);
        assertThat(result.snapshotOnly()).isTrue();
        assertThat(result.generationId()).isEqualTo(generation);
    }
    @Test void read_values_matchExactDepartmentScopeAndStableDates() {
        publication(SURGERY, "Asia/Bangkok");
        UUID department = UUID.randomUUID();
        when(store.read(generation, date, date.plusDays(1), department, SURGERY)).thenReturn(List.of(
                new OperationalSnapshotReadPort.Value(date.plusDays(1), Metric.SURGERIES_COMPLETED, 3)));
        var result = service.surgery(date, date.plusDays(1), department);
        assertThat(result.days().getFirst().metrics().get("surgeriesCompleted")).isZero();
        assertThat(result.days().get(1).metrics().get("surgeriesCompleted")).isEqualTo(3);
        assertThat(result.departmentId()).isEqualTo(department);
    }
    @Test void read_partialMetricCoverage_cannotFillMissingSourceWithZero() {
        publication(Set.of(Metric.SURGERIES_COMPLETED), "Asia/Bangkok");
        assertThatThrownBy(() -> service.surgery(date, date, null)).isInstanceOf(ReportProjectionUnavailableException.class);
    }
    @Test void read_wrongTimezone_unavailableInsteadOfRebucketing() {
        publication(SURGERY, "UTC");
        assertThatThrownBy(() -> service.surgery(date, date, null)).isInstanceOf(ReportProjectionUnavailableException.class);
    }
    @Test void read_outsideAcceptedHistory_unavailableNotZeros() {
        publication(SURGERY, "Asia/Bangkok");
        assertThatThrownBy(() -> service.surgery(date.minusDays(1), date, null)).isInstanceOf(ReportProjectionUnavailableException.class);
    }
    @Test void read_badPeriod_failsBeforePublicationOrSql() {
        for (var to : List.of(date.minusDays(1), date.plusDays(366))) {
            assertThatThrownBy(() -> service.daily(date, to, null)).isInstanceOf(ReportPeriodValidationException.class);
        }
        assertThatThrownBy(() -> service.daily(null, date, null)).isInstanceOf(ReportPeriodValidationException.class);
        verifyNoInteractions(store);
    }
    @Test void read_duplicateOrInvalidProjectionRows_neverSilentlyAddsOrOverwrites() {
        publication(SURGERY, "Asia/Bangkok");
        var value = new OperationalSnapshotReadPort.Value(date, Metric.SURGERIES_COMPLETED, 1);
        when(store.read(generation, date, date, null, SURGERY)).thenReturn(List.of(value, value));
        assertThatThrownBy(() -> service.surgery(date, date, null)).isInstanceOf(ReportProjectionUnavailableException.class);
        when(store.read(generation, date, date, null, SURGERY)).thenReturn(List.of(new OperationalSnapshotReadPort.Value(date, Metric.SURGERIES_COMPLETED, -1)));
        assertThatThrownBy(() -> service.surgery(date, date, null)).isInstanceOf(ReportProjectionUnavailableException.class);
    }
    private void publication(Set<Metric> metrics, String zone) {
        when(store.publication("SURGERY")).thenReturn(Optional.of(new OperationalReportPublication(generation, date, date.plusDays(10),
                zone, metrics, Instant.parse("2028-03-10T00:00:00Z"), "TEST-ONLY-SOURCE-ACCEPTANCE")));
    }
}
