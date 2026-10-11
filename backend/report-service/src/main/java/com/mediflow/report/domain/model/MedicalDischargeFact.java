package com.mediflow.report.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import com.mediflow.report.domain.exception.ReportRuleException;

/** Medical end of an admission, not administrative close, bed release or billable bed days. */
public record MedicalDischargeFact(UUID admissionId, UUID patientId, UUID summaryId,
        UUID approvedBy, Instant approvedAt) {
    public MedicalDischargeFact {
        if (admissionId == null || patientId == null || summaryId == null || approvedBy == null || approvedAt == null)
            throw new IllegalArgumentException("Exact medical discharge proof is required");
    }

    public Duration elapsed(AdmissionReportHistory history) {
        if (history == null || history.start() == null) throw new IllegalArgumentException("Exact admission start is required");
        if (!admissionId.equals(history.admissionId()) || !patientId.equals(history.start().patientId())
                || approvedAt.isBefore(history.start().businessAt())
                || (history.close() != null && (!patientId.equals(history.close().patientId())
                    || approvedAt.isAfter(history.close().businessAt())))) {
            throw new ReportRuleException("REPORT_MEDICAL_DISCHARGE_CONFLICT", "Medical discharge identity or chronology differs");
        }
        return Duration.between(history.start().businessAt(), approvedAt);
    }
}
