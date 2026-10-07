package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.event.SurgeryCareEvent;
import com.mediflow.surgery.application.port.out.SurgeryCareEventCapturePort;
import com.mediflow.surgery.infrastructure.messaging.SurgeryCareEventCodec;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
@Profile("!test")
public class SurgeryCareEventCaptureAdapter implements SurgeryCareEventCapturePort {
    private final JdbcTemplate jdbc;
    private final SurgeryCareEventCodec codec;
    public SurgeryCareEventCaptureAdapter(JdbcTemplate jdbc, SurgeryCareEventCodec codec) {
        this.jdbc = jdbc; this.codec = codec;
    }
    @Override public void hold(SurgeryCareEvent event, long caseRevision) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Surgery event requires caller transaction");
        }
        if (event.payload().caseRevision() != caseRevision)
            throw new IllegalArgumentException("Payload and stored case revisions differ");
        UUID caseId = switch (event.payload()) {
            case SurgeryCareEvent.Created created -> created.surgeryCaseId();
            case SurgeryCareEvent.Invalidated invalidated -> invalidated.surgeryCaseId();
            case SurgeryCareEvent.Ready ready -> ready.surgeryCaseId();
            case SurgeryCareEvent.Completed completed -> completed.surgeryCaseId();
            case SurgeryCareEvent.Cancelled cancelled -> cancelled.surgeryCaseId();
        };
        String expectedState = switch (event.payload()) {
            case SurgeryCareEvent.Created ignored -> "REQUESTED";
            case SurgeryCareEvent.Invalidated ignored -> "PREOP_IN_PROGRESS";
            case SurgeryCareEvent.Ready ignored -> "READY";
            case SurgeryCareEvent.Completed ignored -> "COMPLETED";
            case SurgeryCareEvent.Cancelled ignored -> "CANCELLED";
        };
        var care = event.payload();
        Integer stored = jdbc.queryForObject("""
                SELECT count(*) FROM surgery_case WHERE surgery_case_id=? AND revision=? AND status=?
                    AND surgery_request_id=? AND patient_id=? AND department_id=?
                    AND episode_type=? AND episode_id=?
                    AND admission_id IS NOT DISTINCT FROM ? AND medical_record_id IS NOT DISTINCT FROM ?
                """, Integer.class, caseId, caseRevision, expectedState, care.surgeryRequestId(), care.patientId(),
                care.departmentId(), care.careEpisodeType(), care.careEpisodeId(), care.admissionId(), care.recordId());
        if (stored == null || stored != 1) throw new IllegalArgumentException("Event does not match committed case revision/state");
        Integer source = switch (event.payload()) {
            case SurgeryCareEvent.Ready value -> jdbc.queryForObject(
                    "SELECT count(*) FROM surgery_readiness_snapshot WHERE readiness_snapshot_id=? AND surgery_case_id=?",
                    Integer.class, value.readinessSnapshotId(), caseId);
            case SurgeryCareEvent.Invalidated value -> jdbc.queryForObject(
                    "SELECT count(*) FROM surgery_readiness_snapshot WHERE readiness_snapshot_id=? AND surgery_case_id=?",
                    Integer.class, value.readinessSnapshotId(), caseId);
            case SurgeryCareEvent.Completed value -> jdbc.queryForObject(
                    "SELECT count(*) FROM surgery_result WHERE result_id=? AND surgery_case_id=?",
                    Integer.class, value.resultId(), caseId);
            case SurgeryCareEvent.Cancelled value -> jdbc.queryForObject("""
                    SELECT count(*) FROM surgery_case WHERE surgery_case_id=? AND cancellation_account_id=?
                        AND cancellation_staff_id IS NOT DISTINCT FROM ? AND cancellation_reason=?
                    """, Integer.class, caseId, value.cancelledBy(), value.cancelledByStaffId(), value.reason());
            case SurgeryCareEvent.Created ignored -> 1;
        };
        if (source == null || source != 1) throw new com.mediflow.surgery.application.exception.SurgeryRevisionConflictException();
        byte[] payload = codec.encode(event);
        int inserted = jdbc.update("""
                INSERT INTO surgery_care_event_outbox(event_id,surgery_case_id,case_revision,event_type,
                    event_version,correlation_id,payload,occurred_at)
                VALUES (?,?,?,?,1,?,?,?) ON CONFLICT DO NOTHING
                """, event.eventId(),caseId,caseRevision,event.eventType(),event.correlationId(),payload,Timestamp.from(event.occurredAt()));
        if (inserted == 0) {
            var existing = jdbc.query("""
                    SELECT payload FROM surgery_care_event_outbox WHERE event_id=? AND surgery_case_id=?
                        AND case_revision=? AND event_type=? AND delivery_status='HELD'
                    """, (rs, row) -> rs.getBytes(1), event.eventId(),caseId,caseRevision,event.eventType());
            if (existing.size() != 1 || !Arrays.equals(existing.getFirst(),payload)) {
                throw new com.mediflow.surgery.application.exception.SurgeryRevisionConflictException();
            }
        }
    }
}
