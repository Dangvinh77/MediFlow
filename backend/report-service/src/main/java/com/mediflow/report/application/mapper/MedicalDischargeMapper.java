package com.mediflow.report.application.mapper;

import com.mediflow.report.application.dto.command.carefinance.ApplyOperationalContributionCommand;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.domain.model.AdmissionReportHistory;
import com.mediflow.report.domain.model.MedicalDischargeFact;
import com.mediflow.report.domain.model.OperationalContribution;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Report-local completed-stay duration: floor per stay, never a staffed-bed/census/billing KPI. */
public final class MedicalDischargeMapper {
    private static final Set<String> FIELDS = Set.of("admissionId", "patientId", "summaryId", "approvedBy", "approvedAt");
    private final ZoneId zone;
    public MedicalDischargeMapper(ZoneId zone) { this.zone = Objects.requireNonNull(zone); }

    public MedicalDischargeFact fact(DecodedCareFinanceEvent event) {
        var m = event.metadata();
        var p = event.payload();
        if (!"inpatient-service".equals(m.producer()) || m.version() != 1
                || !"discharge.medically.approved".equals(m.eventType()) || !"admissionId".equals(m.sourceField())
                || !p.keySet().equals(FIELDS)) throw new IllegalArgumentException("Only exact singleton medical-discharge V1 is supported");
        var fact = new MedicalDischargeFact(OperationalSourceFields.uuid(p, "admissionId"),
                OperationalSourceFields.uuid(p, "patientId"), OperationalSourceFields.uuid(p, "summaryId"),
                OperationalSourceFields.uuid(p, "approvedBy"), OperationalSourceFields.instant(p, "approvedAt"));
        if (!fact.admissionId().equals(m.sourceId()) || !fact.approvedAt().equals(m.occurredAt()))
            throw new IllegalArgumentException("Medical discharge source/business time differs");
        return fact;
    }

    public ApplyOperationalContributionCommand contributions(DecodedCareFinanceEvent event, AdmissionReportHistory history) {
        var fact = fact(event);
        var elapsed = fact.elapsed(history);
        var date = fact.approvedAt().atZone(zone).toLocalDate();
        var metrics = List.of(OperationalContribution.Metric.MEDICAL_DISCHARGES,
                OperationalContribution.Metric.MEDICAL_LOS_MINUTES, OperationalContribution.Metric.COMPLETED_STAY_DAYS);
        var values = List.of(1L, elapsed.toMinutes(), elapsed.toDays());
        var contributions = new java.util.ArrayList<OperationalContribution>();
        for (int index = 0; index < metrics.size(); index++) contributions.add(new OperationalContribution(
                event.metadata().eventId(), "ADMISSION", fact.admissionId(), 1, metrics.get(index),
                history.departmentId(), "ADMISSION", fact.admissionId(), date,
                BigDecimal.valueOf(values.get(index)), null, fact.approvedAt()));
        return new ApplyOperationalContributionCommand(event, contributions);
    }
}
