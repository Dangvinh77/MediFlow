package com.mediflow.report.application.port.in;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.domain.model.AdmissionReportHistory;

/** Offline internal pending/pairing only; does not increment metrics or expose a new API. */
public interface ProjectAdmissionReportEvidenceUseCase {
    AdmissionReportHistory project(DecodedCareFinanceEvent event);
}
