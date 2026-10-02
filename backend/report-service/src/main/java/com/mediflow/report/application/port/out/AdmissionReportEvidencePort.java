package com.mediflow.report.application.port.out;

import java.util.UUID;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.domain.model.AdmissionReportFact;
import com.mediflow.report.domain.model.AdmissionReportHistory;

/** All methods join the caller transaction. Claim + immutable evidence must commit together. */
public interface AdmissionReportEvidencePort {
    void lockAdmission(UUID admissionId);
    boolean claimDelivery(DecodedCareFinanceEvent event);
    AdmissionReportHistory findHistory(UUID admissionId);
    void store(AdmissionReportFact fact);
}
