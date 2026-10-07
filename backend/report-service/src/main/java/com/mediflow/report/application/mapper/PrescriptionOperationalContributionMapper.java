package com.mediflow.report.application.mapper;

import com.mediflow.report.application.dto.command.carefinance.ApplyOperationalContributionCommand;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.domain.model.OperationalContribution;
import com.mediflow.report.domain.model.OperationalContribution.Metric;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import static com.mediflow.report.application.mapper.OperationalSourceFields.*;

/** V1 one-slip-per-prescription immutable fill; never persist names, dosage or patient narratives. */
public class PrescriptionOperationalContributionMapper {
    private final ZoneId reportZone;
    public PrescriptionOperationalContributionMapper(ZoneId reportZone) { this.reportZone=java.util.Objects.requireNonNull(reportZone); }
    public ApplyOperationalContributionCommand map(DecodedCareFinanceEvent event) {
        var metadata=event.metadata();
        if(metadata.version()!=1 || !"prescription.filled".equals(metadata.eventType())
                || !"pharmacy-service".equals(metadata.producer()) || !"dispenseId".equals(metadata.sourceField())) throw invalid("Pharmacy envelope");
        var payload=event.payload();
        if(!metadata.sourceId().equals(uuid(payload,"dispenseId"))) throw invalid("dispenseId");
        if(!uuid(payload,"prescriptionId").equals(uuid(payload,"sourceId")) || !"PRESCRIPTION".equals(text(payload,"sourceType"))) throw invalid("prescription source");
        episode(payload);
        String context=text(payload,"careContext");
        if(!context.equals(text(payload,"careEpisodeType").equals("ADMISSION")?"ADMISSION":"OUTPATIENT")) throw invalid("careContext");
        Instant at=instant(payload,"filledAt");
        if(metadata.occurredAt().isBefore(at)) throw invalid("occurredAt");
        if(!(payload.get("items") instanceof List<?> items)||items.isEmpty()) throw invalid("items");
        long units=0;
        var drugs=new HashSet<java.util.UUID>();
        for(Object item:items) {
            if(!(item instanceof Map<?,?> raw)) throw invalid("item");
            @SuppressWarnings("unchecked") var line=(Map<String,Object>)raw;
            if(!drugs.add(uuid(line,"drugId"))) throw invalid("duplicate drug");
            units=Math.addExact(units,positiveInteger(line.get("quantity"),"quantity"));
        }
        return new ApplyOperationalContributionCommand(event,List.of(
                fact(event,Metric.DISPENSED_PRESCRIPTIONS,BigDecimal.ONE,at),
                fact(event,Metric.DISPENSED_UNITS,BigDecimal.valueOf(units),at)));
    }
    private OperationalContribution fact(DecodedCareFinanceEvent event,Metric metric,BigDecimal value,Instant at) {
        var payload=event.payload();
        return new OperationalContribution(event.metadata().eventId(),"DISPENSE",event.metadata().sourceId(),1,metric,
                uuid(payload,"departmentId"),text(payload,"careEpisodeType"),uuid(payload,"careEpisodeId"),
                at.atZone(reportZone).toLocalDate(),value,null,at);
    }
}
