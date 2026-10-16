package com.mediflow.report.application.service;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.mapper.ClinicalOperationalContributionMapper;
import com.mediflow.report.application.mapper.AdmissionStartedContributionMapper;
import com.mediflow.report.application.mapper.LabOperationalContributionMapper;
import com.mediflow.report.application.mapper.PrescriptionOperationalContributionMapper;
import com.mediflow.report.application.mapper.SurgeryOperationalContributionMapper;
import com.mediflow.report.application.port.in.ApplyOperationalContributionUseCase;
import com.mediflow.report.application.port.in.ProjectAdmissionReportEvidenceUseCase;
import com.mediflow.report.application.port.in.ReceiveOperationalReportFactUseCase;
import java.time.ZoneId;
import java.util.Objects;

/** Admission evidence and its start counter commit together; close never becomes medical discharge. */
public class OperationalReportFactReceiver implements ReceiveOperationalReportFactUseCase {
    private final ApplyOperationalContributionUseCase operations;
    private final ProjectAdmissionReportEvidenceUseCase admissions;
    private final ClinicalOperationalContributionMapper clinical;
    private final LabOperationalContributionMapper lab;
    private final PrescriptionOperationalContributionMapper pharmacy;
    private final SurgeryOperationalContributionMapper surgery;
    private final AdmissionStartedContributionMapper starts;
    private final com.mediflow.report.application.port.in.ProjectMedicalDischargeUseCase medicalDischarges;

    public OperationalReportFactReceiver(ApplyOperationalContributionUseCase operations,
            ProjectAdmissionReportEvidenceUseCase admissions, ZoneId reportZone) {
        this(operations, admissions, reportZone, null);
    }

    public OperationalReportFactReceiver(ApplyOperationalContributionUseCase operations,
            ProjectAdmissionReportEvidenceUseCase admissions, ZoneId reportZone,
            com.mediflow.report.application.port.in.ProjectMedicalDischargeUseCase medicalDischarges) {
        this.operations = Objects.requireNonNull(operations);
        this.admissions = Objects.requireNonNull(admissions);
        clinical = new ClinicalOperationalContributionMapper(reportZone);
        lab = new LabOperationalContributionMapper(reportZone);
        pharmacy = new PrescriptionOperationalContributionMapper(reportZone);
        surgery = new SurgeryOperationalContributionMapper(reportZone);
        starts = new AdmissionStartedContributionMapper(reportZone);
        this.medicalDischarges = medicalDischarges;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional("reportTransactionManager")
    public void receive(DecodedCareFinanceEvent event) {
        Objects.requireNonNull(event);
        switch (event.metadata().eventType()) {
            case "medicalrecord.completed" -> operations.apply(clinical.map(event));
            case "lab.result.created" -> operations.apply(lab.map(event));
            case "prescription.filled" -> operations.apply(pharmacy.map(event));
            case "surgery.completed", "surgery.cancelled" -> operations.apply(surgery.map(event));
            case "admission.started" -> {
                var contribution = starts.map(event);
                admissions.project(event); // Validate chronology/patient against any early close before effects.
                if (medicalDischarges != null) medicalDischarges.recover(event.metadata().sourceId());
                operations.apply(contribution);
            }
            case "admission.closed" -> {
                admissions.project(event);
                if (medicalDischarges != null) medicalDischarges.recover(event.metadata().sourceId());
            }
            case "discharge.medically.approved" -> {
                if (medicalDischarges == null) throw new IllegalStateException("Medical discharge projector is not configured");
                medicalDischarges.receive(event);
            }
            default -> throw new IllegalArgumentException("Unsupported operational report fact");
        }
    }
}
