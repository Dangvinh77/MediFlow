package com.mediflow.report.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mediflow.report.application.dto.response.ReportCoverageDTO;
import com.mediflow.report.application.port.out.FinancialSnapshotReadPort;
import com.mediflow.report.application.exception.ReportPeriodValidationException;
import com.mediflow.report.domain.model.FinancialReportPublication;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportCapabilityApplicationServiceTest {

    private final FinancialSnapshotReadPort store = mock(FinancialSnapshotReadPort.class);
    private final ReportCapabilityApplicationService service = new ReportCapabilityApplicationService(store);

    private static final String CURRENCY = "VND";
    private static final String ZONE = "Asia/Bangkok";
    private static final UUID GENERATION = UUID.fromString("00000000-0000-4000-8000-000000000099");
    private static final LocalDate COVERED_FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate COVERED_TO = LocalDate.of(2026, 10, 10);

    private static FinancialReportPublication publication() {
        return new FinancialReportPublication(
                GENERATION, COVERED_FROM, COVERED_TO, CURRENCY, ZONE,
                Instant.parse("2026-10-11T00:00:00Z"),
                Instant.parse("2026-10-11T00:01:00Z"),
                Instant.parse("2026-10-11T00:02:00Z"),
                "approval-2026-10-11");
    }

    @Test
    void capabilities_returnsVersionedSourceContractAndUnsupportedMarkers() {
        var capabilities = service.capabilities();

        assertThat(capabilities.capabilityVersion()).isOne();
        assertThat(capabilities.historyPolicy()).isEqualTo("AVAILABLE_EVIDENCE_ONLY");
        assertThat(capabilities.sources()).hasSize(12);
        assertThat(capabilities.sources()).extracting(source -> source.eventType())
                .containsExactlyInAnyOrder(
                        "medicalrecord.completed", "lab.result.created", "prescription.filled",
                        "surgery.completed", "surgery.cancelled", "admission.started",
                        "admission.closed", "discharge.medically.approved", "payment.completed",
                        "payment.refunded", "settlement.reconciled", "settlement.recognition.completed");
        assertThat(capabilities.sources()).allSatisfy(source -> {
            assertThat(source.envelopeVersion()).isOne();
            assertThat(source.sourceIdField()).isNotBlank();
            assertThat(source.revisionPolicy()).isNotBlank();
            assertThat(source.correctionPolicy()).isNotBlank();
        });
        assertThat(capabilities.unsupportedFeatures())
                .contains("PRE_V2_HISTORY_WITHOUT_SOURCE", "POST_FINAL_EARNED_REVERSAL",
                        "OPERATIONAL_CORRECTIONS_AND_LEGACY_IMPORT");
    }

    @Test
    void coverage_publishedRange_returnsAvailableSnapshotEvidence() {
        when(store.publication(CURRENCY, ZONE)).thenReturn(Optional.of(publication()));

        ReportCoverageDTO result = service.coverage(
                LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 5), CURRENCY, ZONE);

        assertThat(result.status()).isEqualTo(ReportCoverageDTO.Status.AVAILABLE_SNAPSHOT);
        assertThat(result.requestedFrom()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(result.requestedTo()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(result.coveredFrom()).isEqualTo(COVERED_FROM);
        assertThat(result.coveredTo()).isEqualTo(COVERED_TO);
        assertThat(result.generationId()).isEqualTo(GENERATION);
        assertThat(result.sourceObservedAt()).isEqualTo(Instant.parse("2026-10-11T00:00:00Z"));
        assertThat(result.snapshotOnly()).isTrue();
        verify(store).publication(CURRENCY, ZONE);
    }

    @Test
    void coverage_missingPublication_returnsUnavailablePublicationWithoutZeroMetrics() {
        when(store.publication(CURRENCY, ZONE)).thenReturn(Optional.empty());

        ReportCoverageDTO result = service.coverage(
                LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 5), CURRENCY, ZONE);

        assertThat(result.status()).isEqualTo(ReportCoverageDTO.Status.UNAVAILABLE_PUBLICATION);
        assertThat(result.coveredFrom()).isNull();
        assertThat(result.coveredTo()).isNull();
        assertThat(result.generationId()).isNull();
        assertThat(result.sourceObservedAt()).isNull();
        assertThat(result.snapshotOnly()).isTrue();
    }

    @Test
    void coverage_futurePublicationTimestamp_returnsUnavailablePublicationWithoutFutureMetadata() {
        var future = new FinancialReportPublication(
                GENERATION, COVERED_FROM, COVERED_TO, CURRENCY, ZONE,
                Instant.parse("2026-10-11T00:00:00Z"),
                Instant.parse("2026-10-11T00:01:00Z"),
                Instant.now().plusSeconds(3600),
                "approval-future");
        when(store.publication(CURRENCY, ZONE)).thenReturn(Optional.of(future));

        ReportCoverageDTO result = service.coverage(
                LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 5), CURRENCY, ZONE);

        assertThat(result.status()).isEqualTo(ReportCoverageDTO.Status.UNAVAILABLE_PUBLICATION);
        assertThat(result.coveredFrom()).isNull();
        assertThat(result.coveredTo()).isNull();
        assertThat(result.generationId()).isNull();
        assertThat(result.sourceObservedAt()).isNull();
        assertThat(result.snapshotOnly()).isTrue();
    }

    @Test
    void coverageOutsideAcceptedRange_returnsUnavailableHistoryAndExposesKnownCoverage() {
        when(store.publication(CURRENCY, ZONE)).thenReturn(Optional.of(publication()));

        ReportCoverageDTO result = service.coverage(
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 5), CURRENCY, ZONE);

        assertThat(result.status()).isEqualTo(ReportCoverageDTO.Status.UNAVAILABLE_HISTORY);
        assertThat(result.coveredFrom()).isEqualTo(COVERED_FROM);
        assertThat(result.coveredTo()).isEqualTo(COVERED_TO);
        assertThat(result.generationId()).isEqualTo(GENERATION);
        assertThat(result.snapshotOnly()).isTrue();
    }

    @Test
    void coverage_reversedRange_rejectsBeforePublicationLookup() {
        assertThatThrownBy(() -> service.coverage(
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 3), CURRENCY, ZONE))
                .isInstanceOf(ReportPeriodValidationException.class);

        verifyNoInteractions(store);
    }

    @Test
    void coverage_moreThan366Days_rejectsBeforePublicationLookup() {
        assertThatThrownBy(() -> service.coverage(
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2), CURRENCY, ZONE))
                .isInstanceOf(ReportPeriodValidationException.class);

        verifyNoInteractions(store);
    }

    @Test
    void coverage_invalidCurrency_rejectsBeforePublicationLookup() {
        assertThatThrownBy(() -> service.coverage(
                LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 5), "usd", ZONE))
                .isInstanceOf(ReportPeriodValidationException.class);

        verifyNoInteractions(store);
    }

    @Test
    void coverage_invalidZone_rejectsBeforePublicationLookup() {
        assertThatThrownBy(() -> service.coverage(
                LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 5), CURRENCY, "Not/AZone"))
                .isInstanceOf(ReportPeriodValidationException.class);

        verifyNoInteractions(store);
    }
}
