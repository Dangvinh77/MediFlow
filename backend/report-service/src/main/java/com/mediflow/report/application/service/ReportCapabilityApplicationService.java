package com.mediflow.report.application.service;

import com.mediflow.report.application.dto.response.ReportCapabilitiesDTO;
import com.mediflow.report.application.dto.response.ReportCapabilitiesDTO.SourceCapability;
import com.mediflow.report.application.dto.response.ReportCoverageDTO;
import com.mediflow.report.application.dto.response.ReportCoverageDTO.Status;
import com.mediflow.report.application.exception.ReportPeriodValidationException;
import com.mediflow.report.application.port.in.InspectReportCapabilitiesUseCase;
import com.mediflow.report.application.port.out.FinancialSnapshotReadPort;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportCapabilityApplicationService implements InspectReportCapabilitiesUseCase {
    private final FinancialSnapshotReadPort store;
    public ReportCapabilityApplicationService(FinancialSnapshotReadPort store) { this.store = store; }

    @Override public ReportCapabilitiesDTO capabilities() {
        return new ReportCapabilitiesDTO(1, "AVAILABLE_EVIDENCE_ONLY", List.of(
                singleton("medicalrecord.completed", "clinical-service", "recordId"),
                source("lab.result.created", "lab-service", "labId", "RESULT_VERSION_ONE_ONLY"),
                singleton("prescription.filled", "pharmacy-service", "dispenseId"),
                source("surgery.completed", "surgery-service", "resultId", "SOURCE_REVISION_ONE_ONLY"),
                source("surgery.cancelled", "surgery-service", "cancellationId", "SOURCE_REVISION_ONE_ONLY"),
                singleton("admission.started", "inpatient-service", "admissionId"),
                singleton("admission.closed", "inpatient-service", "admissionId"),
                singleton("discharge.medically.approved", "inpatient-service", "admissionId"),
                singleton("payment.completed", "billing-service", "transactionId"),
                singleton("payment.refunded", "billing-service", "refundTransactionId"),
                source("settlement.recognition.completed", "billing-service", "recognitionOperationId", "INITIAL_FINAL_ONE_ONLY"),
                new SourceCapability("settlement.reconciled", "billing-service", 1, "settlementId", "EXPLICIT_CONTIGUOUS_PREDECESSORS", "NON_FINAL_STOCK_ONLY")),
                List.of("PRE_V2_HISTORY_WITHOUT_SOURCE", "POST_FINAL_EARNED_REVERSAL", "OPERATIONAL_CORRECTIONS_AND_LEGACY_IMPORT",
                        "CENSUS_INPATIENT_DAYS", "STAFFED_BED_OCCUPANCY", "UPSTREAM_COMPLETENESS_FROM_BROKER_ACK"));
    }

    @Override
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ, timeout=8)
    public ReportCoverageDTO coverage(LocalDate from, LocalDate to, String currency, String reportZone) {
        if (from == null || to == null || from.getYear() < 2000 || to.getYear() > 2100 || to.isBefore(from)
                || ChronoUnit.DAYS.between(from, to) >= 366 || !"VND".equals(currency) || reportZone == null) throw new ReportPeriodValidationException();
        try { ZoneId.of(reportZone); } catch (java.time.DateTimeException exception) { throw new ReportPeriodValidationException(); }
        var publication = store.publication(currency, reportZone);
        if (publication.isEmpty()) return new ReportCoverageDTO(from, to, Status.UNAVAILABLE_PUBLICATION, null, null, null, null, true);
        var accepted = publication.get();
        if (accepted.publishedAt().isAfter(java.time.Instant.now())) {
            return new ReportCoverageDTO(from, to, Status.UNAVAILABLE_PUBLICATION, null, null, null, null, true);
        }
        return new ReportCoverageDTO(from, to, accepted.covers(from, to, currency, reportZone) ? Status.AVAILABLE_SNAPSHOT : Status.UNAVAILABLE_HISTORY,
                accepted.coveredFrom(), accepted.coveredTo(), accepted.generationId(), accepted.sourceObservedAt(), true);
    }

    private static SourceCapability singleton(String type, String producer, String id) { return source(type, producer, id, "IMMUTABLE_SINGLETON"); }
    private static SourceCapability source(String type, String producer, String id, String revision) {
        return new SourceCapability(type, producer, 1, id, revision, "REJECT_UNSUPPORTED_CORRECTION");
    }
}
