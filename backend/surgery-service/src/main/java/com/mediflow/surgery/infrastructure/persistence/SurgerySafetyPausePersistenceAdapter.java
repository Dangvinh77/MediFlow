package com.mediflow.surgery.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.out.SurgerySafetyPauseStorePort;
import com.mediflow.surgery.application.service.SurgerySafetyPauseFingerprints;
import com.mediflow.surgery.domain.model.SurgerySafetyPause;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
@Profile("!test")
public class SurgerySafetyPausePersistenceAdapter implements SurgerySafetyPauseStorePort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public SurgerySafetyPausePersistenceAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @Override public SurgerySafetyPause append(SurgerySafetyPause pause) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Safety evidence needs an authorized caller transaction");
        }
        var context = jdbc.query("""
                SELECT status,revision,procedure_code FROM surgery_case WHERE surgery_case_id=? FOR UPDATE
                """, (rs, row) -> new Context(SurgeryStatus.valueOf(rs.getString(1)), rs.getLong(2), rs.getString(3)),
                pause.surgeryCaseId());
        if (context.isEmpty()) throw new ResourceNotFoundException("SURGERY_CASE_NOT_FOUND", "Surgery case not found");
        String fingerprint = SurgerySafetyPauseFingerprints.intent(pause);
        var original = jdbc.query("SELECT * FROM surgery_safety_pause WHERE surgery_case_id=? AND command_id=?",
                (rs, row) -> read(rs), pause.surgeryCaseId(), pause.commandId());
        if (!original.isEmpty()) {
            if (!fingerprint.equals(SurgerySafetyPauseFingerprints.intent(original.getFirst()))) {
                throw new SurgeryRevisionConflictException();
            }
            return original.getFirst();
        }
        var history = jdbc.query("""
                SELECT * FROM surgery_safety_pause WHERE surgery_case_id=?
                ORDER BY CASE phase WHEN 'BEFORE_ANAESTHESIA' THEN 1 WHEN 'BEFORE_INCISION' THEN 2 ELSE 3 END
                """, (rs, row) -> read(rs), pause.surgeryCaseId());
        if (history.stream().anyMatch(value -> value.phase() == pause.phase())) throw new SurgeryRevisionConflictException();
        var schedules = jdbc.query("""
                SELECT schedule_id,revision FROM surgery_schedule WHERE surgery_case_id=?
                    AND status IN ('FINALIZED','IN_USE') FOR UPDATE
                """, (rs, row) -> new Schedule(rs.getObject(1, UUID.class), rs.getLong(2)), pause.surgeryCaseId());
        if (schedules.size() != 1) throw new SurgeryRevisionConflictException();
        var current = context.getFirst();
        var schedule = schedules.getFirst();
        if (current.revision() != pause.expectedCaseRevision()) throw new SurgeryRevisionConflictException();
        pause.validateAppend(current.status(), current.revision(), current.procedureCode(), schedule.id(), schedule.revision(),
                history.isEmpty() ? null : history.getLast());
        jdbc.update("""
                INSERT INTO surgery_safety_pause(pause_id,command_id,surgery_case_id,phase,schedule_id,schedule_revision,
                    expected_case_revision,intent_fingerprint,observed_at_iso,recorded_at_iso,snapshot)
                VALUES (?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb))
                """, pause.pauseId(), pause.commandId(), pause.surgeryCaseId(), pause.phase().name(), pause.scheduleId(),
                pause.scheduleRevision(), pause.expectedCaseRevision(), fingerprint, pause.observedAt().toString(),
                pause.recordedAt().toString(), serialize(pause));
        return pause;
    }

    private SurgerySafetyPause read(ResultSet row) throws SQLException {
        try {
            var value = mapper.readValue(row.getString("snapshot"), SurgerySafetyPause.class);
            if (!value.pauseId().equals(row.getObject("pause_id", UUID.class))
                    || !value.commandId().equals(row.getObject("command_id", UUID.class))
                    || !value.surgeryCaseId().equals(row.getObject("surgery_case_id", UUID.class))
                    || !value.phase().name().equals(row.getString("phase"))
                    || !value.scheduleId().equals(row.getObject("schedule_id", UUID.class))
                    || value.scheduleRevision() != row.getLong("schedule_revision")
                    || value.expectedCaseRevision() != row.getLong("expected_case_revision")
                    || !value.observedAt().equals(Instant.parse(row.getString("observed_at_iso")))
                    || !value.recordedAt().equals(Instant.parse(row.getString("recorded_at_iso")))
                    || !SurgerySafetyPauseFingerprints.intent(value).equals(row.getString("intent_fingerprint"))) {
                throw new IllegalStateException("Stored safety evidence identity is inconsistent");
            }
            return value;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("Stored safety evidence is invalid");
        }
    }

    private String serialize(SurgerySafetyPause value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to persist safety evidence"); }
    }

    private record Context(SurgeryStatus status, long revision, String procedureCode) { }
    private record Schedule(UUID id, long revision) { }
}
