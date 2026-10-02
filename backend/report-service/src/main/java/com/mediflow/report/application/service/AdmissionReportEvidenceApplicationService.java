package com.mediflow.report.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.mapper.AdmissionReportFactMapper;
import com.mediflow.report.application.port.in.ProjectAdmissionReportEvidenceUseCase;
import com.mediflow.report.application.port.out.AdmissionReportEvidencePort;
import com.mediflow.report.domain.model.AdmissionReportHistory;

@Service
@Transactional
public class AdmissionReportEvidenceApplicationService implements ProjectAdmissionReportEvidenceUseCase {
    private final AdmissionReportEvidencePort evidence;
    private final AdmissionReportFactMapper mapper;

    public AdmissionReportEvidenceApplicationService(AdmissionReportEvidencePort evidence, AdmissionReportFactMapper mapper) {
        this.evidence = evidence;
        this.mapper = mapper;
    }

    @Override
    public AdmissionReportHistory project(DecodedCareFinanceEvent event) {
        var fact = mapper.map(event);
        evidence.lockAdmission(fact.admissionId());
        boolean claimed = evidence.claimDelivery(event);
        var history = evidence.findHistory(fact.admissionId());
        var updated = history.apply(fact); // Even redelivery validates immutable source evidence.
        if (claimed && !updated.equals(history)) evidence.store(fact);
        return updated;
    }
}
