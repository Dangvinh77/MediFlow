package com.mediflow.surgery.support;

import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.ReadinessSnapshot;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryDependencyRevision;
import com.mediflow.surgery.domain.model.SurgeryDependencyType;
import com.mediflow.surgery.domain.model.SurgeryPriority;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryTeamAssignment;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Isolated Surgery-owned test prerequisite, NOT clinical evaluation or a READY/finalize API. */
public final class SurgeryScheduledFixture {
    private SurgeryScheduledFixture() {}

    public record Fixture(UUID caseId, UUID scheduleId, UUID snapshotId) {}

    public static Fixture seed(JdbcTemplate db, UUID department, UUID room, UUID staff,
            Instant startsAt, Instant endsAt) {
        return new TransactionTemplate(new DataSourceTransactionManager(db.getDataSource())).execute(ignored -> {
            Instant now = Instant.now();
            var actor = SurgeryAuditActor.human(UUID.randomUUID(), staff);
            var value = SurgeryCase.create(UUID.randomUUID(), UUID.randomUUID(),
                    new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, UUID.randomUUID()),
                    UUID.randomUUID(), department, staff, "TEST-PROC", "Isolated test prerequisite",
                    SurgeryPriority.ROUTINE, now.minusSeconds(60), actor, "runtime-prerequisite");
            value.beginPreop(actor, "runtime-prerequisite", now.minusSeconds(50));
            var schedule = new SurgerySchedule(UUID.randomUUID(), value.getSurgeryCaseId(), 1, room,
                    startsAt, endsAt, List.of(new SurgeryTeamAssignment(staff, SurgeryTeamRole.PRIMARY_SURGEON)));
            var snapshot = ReadinessSnapshot.evaluate(UUID.randomUUID(), value.getSurgeryCaseId(),
                    true, true, true, true, true, true, true, now.minusSeconds(30),
                    Arrays.stream(SurgeryDependencyType.values()).map(type -> new SurgeryDependencyRevision(type,
                            type == SurgeryDependencyType.SCHEDULE ? schedule.scheduleId() : UUID.randomUUID(), 1)).toList(),
                    now.plusSeconds(3600));
            value.markReady(snapshot, actor, "runtime-prerequisite");
            value.finalizeSchedule(actor, "runtime-prerequisite", now.minusSeconds(20));
            db.update("""
                    INSERT INTO surgery_case(surgery_case_id,surgery_request_id,episode_type,episode_id,medical_record_id,
                        patient_id,department_id,requested_by,procedure_code,indication,priority,status,requested_at,ready_at,revision)
                    VALUES (?,?,'OUTPATIENT_VISIT',?,?,?,?,?,'TEST-PROC','Isolated test prerequisite','ROUTINE','SCHEDULED',?,?,3)
                    """, value.getSurgeryCaseId(), value.getSurgeryRequestId(), value.getCareEpisode().episodeId(),
                    value.getCareEpisode().medicalRecordId(), value.getPatientId(), department, staff,
                    Timestamp.from(value.getRequestedAt()), Timestamp.from(value.getReadyAt()));
            db.update("""
                    INSERT INTO surgery_schedule(schedule_id,surgery_case_id,revision,room_id,starts_at,ends_at,status)
                    VALUES (?,?,1,?,?,?,'FINALIZED')
                    """, schedule.scheduleId(), value.getSurgeryCaseId(), room, Timestamp.from(startsAt), Timestamp.from(endsAt));
            db.update("INSERT INTO surgery_team_assignment(schedule_id,staff_id,role) VALUES (?,?,'PRIMARY_SURGEON')", schedule.scheduleId(), staff);
            db.update("""
                    INSERT INTO surgery_schedule_history(schedule_id,revision,room_id,starts_at,ends_at,occurred_at)
                    VALUES (?,1,?,?,?,?)
                    """, schedule.scheduleId(), room,
                    Timestamp.from(startsAt), Timestamp.from(endsAt), Timestamp.from(now.minusSeconds(40)));
            db.update("""
                    INSERT INTO surgery_team_assignment_history(schedule_id,revision,staff_id,role)
                    VALUES (?,1,?,'PRIMARY_SURGEON')
                    """, schedule.scheduleId(), staff);
            db.update("""
                    INSERT INTO surgery_readiness_snapshot(readiness_snapshot_id,surgery_case_id,indication_valid,
                        mandatory_checklist_complete,surgery_consent_active,anesthesia_consent_active,team_eligible,
                        schedule_confirmed,financial_clearance_valid,evaluated_at,valid_until)
                    VALUES (?,?,true,true,true,true,true,true,true,?,?)
                    """, snapshot.snapshotId(), value.getSurgeryCaseId(), Timestamp.from(snapshot.evaluatedAt()),
                    Timestamp.from(snapshot.validUntil()));
            for (int index = 0; index < snapshot.dependencyRevisions().size(); index++) {
                var dependency = snapshot.dependencyRevisions().get(index);
                db.update("""
                        INSERT INTO surgery_readiness_dependency(readiness_snapshot_id,sequence_no,dependency_type,source_id,revision)
                        VALUES (?,?,?,?,?)
                        """, snapshot.snapshotId(), index,
                        dependency.dependencyType().name(), dependency.sourceId(), dependency.revision());
            }
            db.update("UPDATE surgery_case SET readiness_snapshot_id=? WHERE surgery_case_id=?", snapshot.snapshotId(), value.getSurgeryCaseId());
            for (int index = 0; index < value.getStatusHistory().size(); index++) {
                var change = value.getStatusHistory().get(index);
                db.update("""
                        INSERT INTO surgery_status_history(surgery_case_id,sequence_no,previous_status,new_status,actor_type,
                            account_id,staff_id,reason,occurred_at,correlation_id) VALUES (?,?,?,?,'HUMAN',?,?,?,?,?)
                        """, value.getSurgeryCaseId(), index, change.previousStatus() == null ? null : change.previousStatus().name(),
                        change.newStatus().name(), actor.accountId(), staff, change.reason(), Timestamp.from(change.occurredAt()), change.correlationId());
            }
            for (var audit : value.getRevisionHistory()) {
                db.update("""
                        INSERT INTO surgery_revision_history(surgery_case_id,revision,change_code,previous_status,new_status,
                            actor_type,account_id,staff_id,occurred_at,correlation_id) VALUES (?,?,?,?,?,'HUMAN',?,?,?,?)
                        """, value.getSurgeryCaseId(), audit.revision(), audit.changeCode(),
                        audit.previousStatus() == null ? null : audit.previousStatus().name(), audit.newStatus().name(), actor.accountId(), staff,
                        Timestamp.from(audit.occurredAt()), audit.correlationId());
            }
            for (String kind : List.of("ROOM", "STAFF")) {
                UUID resource = kind.equals("ROOM") ? room : staff;
                db.update("INSERT INTO surgery_resource_mutex(resource_type,resource_id) VALUES (?,?) ON CONFLICT DO NOTHING", kind, resource);
                db.update("""
                        INSERT INTO surgery_resource_reservation(reservation_id,surgery_case_id,schedule_id,schedule_revision,
                            resource_type,resource_id,starts_at,ends_at,status) VALUES (?,?,?,1,?,?,?,?,'RESERVED')
                        """, UUID.randomUUID(), value.getSurgeryCaseId(), schedule.scheduleId(), kind, resource,
                        Timestamp.from(startsAt), Timestamp.from(endsAt));
            }
            return new Fixture(value.getSurgeryCaseId(), schedule.scheduleId(), snapshot.snapshotId());
        });
    }
}
