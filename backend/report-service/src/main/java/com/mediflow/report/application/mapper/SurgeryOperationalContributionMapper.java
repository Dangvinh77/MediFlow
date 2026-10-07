package com.mediflow.report.application.mapper;

import com.mediflow.report.application.dto.command.carefinance.ApplyOperationalContributionCommand;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.domain.model.OperationalContribution;
import com.mediflow.report.domain.model.OperationalContribution.Metric;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import static com.mediflow.report.application.mapper.OperationalSourceFields.*;

/** No patient, free-text reason or clinical summary is retained in aggregate inputs/journal. */
public class SurgeryOperationalContributionMapper {
    private final ZoneId reportZone;
    public SurgeryOperationalContributionMapper(ZoneId reportZone) { this.reportZone=java.util.Objects.requireNonNull(reportZone); }
    public ApplyOperationalContributionCommand map(DecodedCareFinanceEvent event) {
        var metadata=event.metadata();
        boolean completed="surgery.completed".equals(metadata.eventType());
        if(metadata.version()!=1 || !"surgery-service".equals(metadata.producer())
                || !completed && !"surgery.cancelled".equals(metadata.eventType())
                || !metadata.sourceField().equals(completed?"resultId":"cancellationId")) throw invalid("Surgery envelope");
        var payload=event.payload();
        if(!metadata.sourceId().equals(uuid(payload,metadata.sourceField()))) throw invalid("sourceId");
        if(positiveInteger(payload.get("sourceRevision"),"sourceRevision")!=1) throw invalid("unsupported correction");
        uuid(payload,"surgeryCaseId"); uuid(payload,"surgeryRequestId"); episode(payload);
        positiveInteger(payload.get("caseRevision"),"caseRevision");
        Instant at=instant(payload,completed?"completedAt":"cancelledAt");
        if(metadata.occurredAt().isBefore(at)) throw invalid("occurredAt");
        String category=null;
        if(completed && payload.get("complicationsCategory")!=null) {
            category=text(payload,"complicationsCategory");
            if(!category.matches("[A-Za-z0-9._-]{1,64}")) throw invalid("complicationsCategory");
        }
        if(!completed) {
            category=text(payload,"cancellationStage");
            if(!List.of("BEFORE_PREOP","AFTER_PREOP","BEFORE_START").contains(category)) throw invalid("cancellationStage");
            text(payload,"reasonCode"); // Never journal free-text reason.
        }
        var count=fact(event,completed?Metric.SURGERIES_COMPLETED:Metric.SURGERIES_CANCELLED,BigDecimal.ONE,category,at);
        if(!completed) return new ApplyOperationalContributionCommand(event,count);
        Instant started=instant(payload,"startedAt");
        Instant recorded=instant(payload,"recordedAt");
        if(!at.isAfter(started)||recorded.isBefore(at)) throw invalid("actual result interval");
        long minutes=Duration.between(started,at).toMinutes(); // Canonical whole elapsed minutes, rounded down per operation.
        return new ApplyOperationalContributionCommand(event,List.of(count,
                fact(event,Metric.SURGERY_DURATION_MINUTES,BigDecimal.valueOf(minutes),null,at)));
    }
    private OperationalContribution fact(DecodedCareFinanceEvent event,Metric metric,BigDecimal value,String category,Instant at) {
        var payload=event.payload();
        return new OperationalContribution(event.metadata().eventId(),"SURGERY_OUTCOME",event.metadata().sourceId(),1,metric,
                uuid(payload,"departmentId"),text(payload,"careEpisodeType"),uuid(payload,"careEpisodeId"),
                at.atZone(reportZone).toLocalDate(),value,category,at);
    }
}
