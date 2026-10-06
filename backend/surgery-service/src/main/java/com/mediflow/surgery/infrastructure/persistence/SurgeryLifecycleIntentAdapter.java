package com.mediflow.surgery.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.out.SurgeryLifecycleIntentPort;
import com.mediflow.surgery.domain.model.ReadinessSnapshot;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryResult;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Held protocol is PRIVATE to Surgery; never publish its payload as a versioned domain event. */
@Repository @Profile("!test")
public class SurgeryLifecycleIntentAdapter implements SurgeryLifecycleIntentPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public SurgeryLifecycleIntentAdapter(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    @Override public void holdReady(SurgeryCase value, SurgerySchedule schedule, ReadinessSnapshot snapshot, String correlation) {
        if (value.getStatus() != SurgeryStatus.READY || !snapshot.equals(value.getReadinessSnapshot())) throw new SurgeryRevisionConflictException();
        var fields = context(value,schedule);
        fields.put("readiness",snapshot);
        hold("READY",snapshot.snapshotId(),value,fields,correlation);
    }

    @Override public void holdCompleted(SurgeryCase value, SurgerySchedule schedule, SurgeryResult result, String correlation) {
        if (value.getStatus() != SurgeryStatus.COMPLETED || !value.getSurgeryCaseId().equals(result.surgeryCaseId())) throw new SurgeryRevisionConflictException();
        var fields = context(value,schedule);
        fields.put("result",result); // Controlled codes/quantities only; result model has no clinical narrative.
        hold("COMPLETED",result.resultId(),value,fields,correlation);
    }

    private Map<String,Object> context(SurgeryCase value,SurgerySchedule schedule) {
        if (!value.getSurgeryCaseId().equals(schedule.surgeryCaseId())) throw new SurgeryRevisionConflictException();
        var fields = new LinkedHashMap<String,Object>();
        fields.put("surgeryCaseId",value.getSurgeryCaseId()); fields.put("caseRevision",value.getRevision());
        fields.put("patientId",value.getPatientId()); fields.put("departmentId",value.getDepartmentId());
        fields.put("episode",value.getCareEpisode()); fields.put("schedule",schedule);
        return fields;
    }

    private void hold(String kind, UUID operation, SurgeryCase value, Map<String,Object> fields,String correlation) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Intent requires caller transaction");
        if (correlation == null || correlation.isBlank() || correlation.length() > 128) throw new IllegalArgumentException("Correlation required");
        var persisted = jdbc.query("""
                SELECT surgery_case_id FROM surgery_case WHERE surgery_case_id=? AND revision=? AND status=? FOR UPDATE
                """,(rs,ignored) -> rs.getObject(1,UUID.class),value.getSurgeryCaseId(),value.getRevision(),kind);
        if (persisted.size() != 1) throw new SurgeryRevisionConflictException();
        String sourceTable = kind.equals("READY") ? "surgery_readiness_snapshot" : "surgery_result";
        String sourceColumn = kind.equals("READY") ? "readiness_snapshot_id" : "result_id";
        Integer source = jdbc.queryForObject("SELECT count(*) FROM "+sourceTable+" WHERE "+sourceColumn+"=? AND surgery_case_id=?",
                Integer.class,operation,value.getSurgeryCaseId());
        if (source == null || source != 1) throw new SurgeryRevisionConflictException();
        fields.put("protocolVersion",1); fields.put("intentKind",kind); fields.put("correlationId",correlation);
        byte[] payload;
        try { payload = mapper.writeValueAsBytes(fields); }
        catch (JsonProcessingException failed) { throw new IllegalStateException("Cannot encode held Surgery intent",failed); }
        int stored = jdbc.update("""
                INSERT INTO surgery_lifecycle_intent(operation_id,surgery_case_id,case_revision,intent_kind,protocol_version,correlation_id,payload)
                VALUES (?,?,?,?,1,?,?) ON CONFLICT DO NOTHING
                """,operation,value.getSurgeryCaseId(),value.getRevision(),kind,correlation,payload);
        if (stored == 0) {
            var rows = jdbc.query("""
                    SELECT payload FROM surgery_lifecycle_intent WHERE operation_id=? AND surgery_case_id=? AND case_revision=? AND intent_kind=?
                    """,(rs,ignored) -> rs.getBytes("payload"),operation,value.getSurgeryCaseId(),value.getRevision(),kind);
            if (rows.size() != 1 || !Arrays.equals(rows.getFirst(),payload)) throw new SurgeryRevisionConflictException();
        }
    }
}
