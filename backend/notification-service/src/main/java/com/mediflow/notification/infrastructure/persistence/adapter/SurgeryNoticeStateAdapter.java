package com.mediflow.notification.infrastructure.persistence.adapter;

import com.mediflow.notification.application.dto.command.SurgeryNoticeCommand;
import com.mediflow.notification.application.port.out.SurgeryNoticeStatePort;
import com.mediflow.notification.domain.exception.NotificationEventConflictException;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class SurgeryNoticeStateAdapter implements SurgeryNoticeStatePort {
    private final JdbcTemplate jdbc;
    public SurgeryNoticeStateAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void lockExactCase(SurgeryNoticeCommand.Context context) {
        jdbc.update("""
                INSERT INTO surgery_notice_case(surgery_case_id,surgery_request_id,patient_id,department_id,
                    care_episode_type,care_episode_id,admission_id,record_id) VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT(surgery_case_id) DO NOTHING
                """, context.surgeryCaseId(), context.surgeryRequestId(), context.patientId(), context.departmentId(),
                context.careEpisodeType(), context.careEpisodeId(), context.admissionId(), context.recordId());
        var row = jdbc.queryForMap("SELECT * FROM surgery_notice_case WHERE surgery_case_id=? FOR UPDATE", context.surgeryCaseId());
        same(context.surgeryRequestId(), row.get("surgery_request_id")); same(context.patientId(), row.get("patient_id"));
        same(context.departmentId(), row.get("department_id")); same(context.careEpisodeType(), row.get("care_episode_type"));
        same(context.careEpisodeId(), row.get("care_episode_id")); same(context.admissionId(), row.get("admission_id"));
        same(context.recordId(), row.get("record_id"));
    }

    @Override public boolean recordSource(SurgeryNoticeCommand command) {
        int inserted = jdbc.update("""
                INSERT INTO surgery_notice_source(event_type,source_id,surgery_case_id,payload_fingerprint)
                VALUES (?,?,?,?) ON CONFLICT(event_type,source_id) DO NOTHING
                """, command.eventType(), command.sourceId(), command.context().surgeryCaseId(), command.sourceFingerprint());
        var row = jdbc.queryForMap("SELECT * FROM surgery_notice_source WHERE event_type=? AND source_id=?", command.eventType(), command.sourceId());
        same(command.context().surgeryCaseId(), row.get("surgery_case_id"));
        same(command.sourceFingerprint(), row.get("payload_fingerprint"));
        return inserted == 1;
    }

    @Override public boolean readyIsSuppressed(SurgeryNoticeCommand command) {
        pinSnapshot(command);
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT invalidated FROM surgery_notice_snapshot WHERE snapshot_id=?", Boolean.class, command.sourceId()))
                || jdbc.queryForObject("SELECT terminal_event_type IS NOT NULL FROM surgery_notice_case WHERE surgery_case_id=?", Boolean.class, command.context().surgeryCaseId());
    }

    @Override public void invalidateSnapshot(SurgeryNoticeCommand command) {
        pinSnapshot(command);
        jdbc.update("UPDATE surgery_notice_snapshot SET invalidated=TRUE WHERE snapshot_id=?", command.sourceId());
    }

    @Override public void markTerminal(SurgeryNoticeCommand command) {
        var previous = jdbc.queryForMap("SELECT terminal_event_type,terminal_source_id FROM surgery_notice_case WHERE surgery_case_id=?", command.context().surgeryCaseId());
        if (previous.get("terminal_event_type") != null) {
            same(command.eventType(), previous.get("terminal_event_type")); same(command.sourceId(), previous.get("terminal_source_id"));
        }
        jdbc.update("UPDATE surgery_notice_case SET terminal_event_type=?,terminal_source_id=? WHERE surgery_case_id=?",
                command.eventType(), command.sourceId(), command.context().surgeryCaseId());
        jdbc.update("UPDATE surgery_notice_snapshot SET invalidated=TRUE WHERE surgery_case_id=?", command.context().surgeryCaseId());
    }

    private void pinSnapshot(SurgeryNoticeCommand command) {
        jdbc.update("""
                INSERT INTO surgery_notice_snapshot(snapshot_id,surgery_case_id,schedule_id,schedule_revision)
                VALUES (?,?,?,?) ON CONFLICT(snapshot_id) DO NOTHING
                """, command.sourceId(), command.context().surgeryCaseId(), command.scheduleId(), command.scheduleRevision());
        var row = jdbc.queryForMap("SELECT * FROM surgery_notice_snapshot WHERE snapshot_id=?", command.sourceId());
        same(command.context().surgeryCaseId(), row.get("surgery_case_id")); same(command.scheduleId(), row.get("schedule_id"));
        same(command.scheduleRevision(), row.get("schedule_revision"));
    }
    private void same(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new NotificationEventConflictException();
    }
}
