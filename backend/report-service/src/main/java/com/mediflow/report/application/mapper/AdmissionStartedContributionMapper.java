package com.mediflow.report.application.mapper;

import com.mediflow.report.application.dto.command.carefinance.ApplyOperationalContributionCommand;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.domain.model.AdmissionReportFact;
import com.mediflow.report.domain.model.OperationalContribution;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Objects;

/** Counts the immutable admission start, not present occupancy or medical/administrative discharge. */
public final class AdmissionStartedContributionMapper {
    private final AdmissionReportFactMapper facts = new AdmissionReportFactMapper();
    private final ZoneId zone;
    public AdmissionStartedContributionMapper(ZoneId zone) { this.zone = Objects.requireNonNull(zone); }
    public ApplyOperationalContributionCommand map(DecodedCareFinanceEvent event) {
        var start = facts.map(event);
        if (start.kind() != AdmissionReportFact.Kind.STARTED)
            throw new IllegalArgumentException("Only an admission start supplies an admission count");
        return new ApplyOperationalContributionCommand(event, new OperationalContribution(event.metadata().eventId(),
                "ADMISSION", start.admissionId(), 1, OperationalContribution.Metric.ADMISSIONS,
                start.departmentId(), "ADMISSION", start.admissionId(), start.businessAt().atZone(zone).toLocalDate(),
                BigDecimal.ONE, null, start.businessAt()));
    }
}
