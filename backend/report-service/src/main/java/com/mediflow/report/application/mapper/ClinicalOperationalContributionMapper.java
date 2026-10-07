package com.mediflow.report.application.mapper;

import com.mediflow.report.application.dto.command.carefinance.ApplyOperationalContributionCommand;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.domain.model.OperationalContribution;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;
import static com.mediflow.report.application.mapper.OperationalSourceFields.instant;
import static com.mediflow.report.application.mapper.OperationalSourceFields.invalid;
import static com.mediflow.report.application.mapper.OperationalSourceFields.text;
import static com.mediflow.report.application.mapper.OperationalSourceFields.uuid;

/** Immutable outpatient completion; disposition ADMISSION is not an admission-start fact. */
public class ClinicalOperationalContributionMapper {
    private final ZoneId reportZone;
    public ClinicalOperationalContributionMapper(ZoneId reportZone) {
        this.reportZone = Objects.requireNonNull(reportZone);
    }
    public ApplyOperationalContributionCommand map(DecodedCareFinanceEvent event) {
        var metadata = event.metadata();
        if (metadata.version() != 1 || !"medicalrecord.completed".equals(metadata.eventType())
                || !"clinical-service".equals(metadata.producer()) || !"recordId".equals(metadata.sourceField()))
            throw invalid("Clinical envelope");
        var payload = event.payload();
        if (!metadata.sourceId().equals(uuid(payload, "recordId"))) throw invalid("recordId");
        uuid(payload, "appointmentId"); uuid(payload, "patientId");
        String disposition = text(payload, "disposition");
        if (!Set.of("OUTPATIENT_FOLLOW_UP", "PRESCRIPTION", "ADMISSION", "TRANSFER", "OTHER").contains(disposition)
                || !(payload.get("admissionRequired") instanceof Boolean admissionRequired)
                || admissionRequired != disposition.equals("ADMISSION")) throw invalid("disposition");
        // An additive correction/episode requires a new accepted producer fixture, never a fallback.
        if (payload.containsKey("sourceRevision") || payload.containsKey("careEpisodeType")
                || payload.containsKey("careEpisodeId")) throw invalid("unsupported Clinical revision/episode extension");
        var at = instant(payload, "completedAt");
        if (metadata.occurredAt().isBefore(at)) throw invalid("occurredAt");
        var fact = new OperationalContribution(metadata.eventId(), "MEDICAL_RECORD", metadata.sourceId(), 1,
                OperationalContribution.Metric.COMPLETED_VISITS, uuid(payload, "departmentId"), null, null,
                at.atZone(reportZone).toLocalDate(), BigDecimal.ONE, disposition, at);
        return new ApplyOperationalContributionCommand(event, fact);
    }
}
