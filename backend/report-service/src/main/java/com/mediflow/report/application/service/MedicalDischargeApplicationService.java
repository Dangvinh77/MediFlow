package com.mediflow.report.application.service;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.mapper.MedicalDischargeMapper;
import com.mediflow.report.application.port.in.ApplyOperationalContributionUseCase;
import com.mediflow.report.application.port.in.ProjectMedicalDischargeUseCase;
import com.mediflow.report.application.port.out.AdmissionReportEvidencePort;
import com.mediflow.report.application.port.out.MedicalDischargeStorePort;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MedicalDischargeApplicationService implements ProjectMedicalDischargeUseCase {
    private final AdmissionReportEvidencePort admissions;
    private final MedicalDischargeStorePort store;
    private final ApplyOperationalContributionUseCase operations;
    private final MedicalDischargeMapper mapper;
    public MedicalDischargeApplicationService(AdmissionReportEvidencePort admissions, MedicalDischargeStorePort store,
            ApplyOperationalContributionUseCase operations, ZoneId reportZoneId) {
        this.admissions = admissions; this.store = store; this.operations = operations;
        mapper = new MedicalDischargeMapper(reportZoneId);
    }
    @Override public void receive(DecodedCareFinanceEvent event) {
        var fact = mapper.fact(event); // Validate before locks/claims.
        admissions.lockAdmission(fact.admissionId());
        var history = admissions.findHistory(fact.admissionId());
        if (history.start() != null) fact.elapsed(history);
        else validateClose(fact, history);
        store.accept(event, fact);
        recover(fact.admissionId());
    }
    @Override public void recover(UUID admissionId) {
        admissions.lockAdmission(admissionId);
        var history = admissions.findHistory(admissionId);
        var event = store.find(admissionId);
        if (event.isEmpty()) return;
        var fact = mapper.fact(event.get());
        com.mediflow.report.application.dto.command.carefinance.ApplyOperationalContributionCommand command;
        try {
            if (history.start() == null) {
                validateClose(fact, history);
                return; // Durable pending; exact later start revalidates in its transaction.
            }
            command = mapper.contributions(event.get(), history);
        } catch (com.mediflow.report.domain.exception.ReportRuleException invalidEarlyEvidence) {
            if (!store.pending(admissionId)) throw invalidEarlyEvidence;
            store.reject(admissionId); // Bad early proof must not poison a later valid start.
            return;
        }
        operations.apply(command); // Storage/projector failure is NEVER caught or quarantined.
        store.markApplied(admissionId);
    }

    private static void validateClose(com.mediflow.report.domain.model.MedicalDischargeFact fact,
            com.mediflow.report.domain.model.AdmissionReportHistory history) {
        if (history.close() != null && (!fact.patientId().equals(history.close().patientId())
                || fact.approvedAt().isAfter(history.close().businessAt())))
            throw new com.mediflow.report.domain.exception.ReportRuleException("REPORT_MEDICAL_DISCHARGE_CONFLICT", "Medical discharge conflicts with administrative close");
    }
}
