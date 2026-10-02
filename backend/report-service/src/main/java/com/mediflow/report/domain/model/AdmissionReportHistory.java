package com.mediflow.report.domain.model;

import java.util.UUID;

import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.AdmissionReportFact.Kind;

/** Close-before-start remains durable pending; a late start completes evidence but never reopens. */
public record AdmissionReportHistory(UUID admissionId, AdmissionReportFact start, AdmissionReportFact close) {
    public enum Status { EMPTY, STARTED, PENDING_START, CLOSED }

    public AdmissionReportHistory {
        if (admissionId == null || (start != null && (start.kind() != Kind.STARTED || !admissionId.equals(start.admissionId())))
                || (close != null && (close.kind() != Kind.CLOSED || !admissionId.equals(close.admissionId())))) {
            throw new IllegalArgumentException("History requires exact admission identities");
        }
        if (start != null && close != null && (!start.patientId().equals(close.patientId())
                || close.businessAt().isBefore(start.businessAt()))) {
            throw conflict("Start and close must match patient and chronological business times");
        }
    }

    public AdmissionReportHistory apply(AdmissionReportFact fact) {
        if (fact == null || !admissionId.equals(fact.admissionId())) throw conflict("Admission identity differs");
        AdmissionReportFact existing = fact.kind() == Kind.STARTED ? start : close;
        if (existing != null && !existing.equals(fact)) {
            throw conflict("Immutable admission fact changed without an accepted correction contract");
        }
        return new AdmissionReportHistory(admissionId, fact.kind() == Kind.STARTED ? fact : start,
                fact.kind() == Kind.CLOSED ? fact : close);
    }

    public Status status() {
        return close == null ? (start == null ? Status.EMPTY : Status.STARTED)
                : (start == null ? Status.PENDING_START : Status.CLOSED);
    }

    /** Close department can only come from this exact matching start, never a patient/REST lookup. */
    public UUID departmentId() { return start == null ? null : start.departmentId(); }

    private static ReportRuleException conflict(String message) {
        return new ReportRuleException("REPORT_ADMISSION_SOURCE_CONFLICT", message);
    }
}
