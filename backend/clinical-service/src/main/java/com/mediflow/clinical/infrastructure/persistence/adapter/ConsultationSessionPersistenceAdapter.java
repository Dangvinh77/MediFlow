package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.port.out.ConsultationSessionRepositoryPort;
import com.mediflow.clinical.domain.model.ConsultationBreak;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.ConsultationSessionStatus;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.DuplicateResourceException;

@Component
public class ConsultationSessionPersistenceAdapter implements ConsultationSessionRepositoryPort {
    private static final TypeReference<List<ConsultationBreak>> BREAKS_TYPE = new TypeReference<>() {};
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ConsultationSessionPersistenceAdapter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ConsultationSession insert(ConsultationSession session, UUID actorAccountId, String reason, Instant at) {
        lockResources(session.configuration().doctorId(), session.configuration().roomId());
        var config = session.configuration();
        requireNoOverlap(config, null);
        try {
            jdbc.update("""
                    INSERT INTO consultation_session(session_id, revision, doctor_id, department_id, room_id,
                        session_date, start_time, end_time, breaks, capacity, status, held_count,
                        payment_locked_count, confirmed_count, consumed_count, available_count,
                        next_queue_number, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, 0, 0, 0, 0, ?, 1, ?, ?)
                    """, session.sessionId(), session.revision(), config.doctorId(), config.departmentId(),
                    config.roomId(), config.sessionDate(), config.startTime(), config.endTime(),
                    objectMapper.writeValueAsString(config.breaks()), config.capacity(), session.status().name(),
                    config.capacity(), timestamp(at), timestamp(at));
        } catch (DuplicateKeyException collision) {
            throw new DuplicateResourceException("SESSION_OVERLAP", "Doctor or room already has an overlapping session");
        } catch (IOException serializationFailure) {
            throw new IllegalStateException("Consultation session breaks could not be serialized", serializationFailure);
        }
        appendAudit(session, null, actorAccountId, "CREATED", reason, at);
        return session;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConsultationSession> findById(UUID sessionId) {
        return jdbc.query("SELECT * FROM consultation_session WHERE session_id = ?", this::mapOne, sessionId)
                .stream().findFirst();
    }

    @Override
    @Transactional
    public Optional<ConsultationSession> findByIdForUpdate(UUID sessionId) {
        return jdbc.query("SELECT * FROM consultation_session WHERE session_id = ? FOR UPDATE", this::mapOne, sessionId)
                .stream().findFirst();
    }

    @Override
    @Transactional
    public void update(ConsultationSession before, ConsultationSession after, UUID actorAccountId,
            String action, String reason, Instant at) {
        var config = after.configuration();
        lockResources(before.configuration().doctorId(), before.configuration().roomId(),
                config.doctorId(), config.roomId());
        requireNoOverlap(config, after.sessionId());
        int changed;
        try {
            changed = jdbc.update("""
                    UPDATE consultation_session SET revision = ?, doctor_id = ?, department_id = ?, room_id = ?,
                        session_date = ?, start_time = ?, end_time = ?, breaks = ?::jsonb, capacity = ?, status = ?,
                        held_count = ?, payment_locked_count = ?, confirmed_count = ?, consumed_count = ?,
                        available_count = ?, updated_at = ?
                    WHERE session_id = ? AND revision = ?
                    """, after.revision(), config.doctorId(), config.departmentId(), config.roomId(),
                    config.sessionDate(), config.startTime(), config.endTime(),
                    objectMapper.writeValueAsString(config.breaks()), config.capacity(), after.status().name(),
                    after.counts().heldCount(), after.counts().paymentLockedCount(), after.counts().confirmedCount(),
                    after.counts().consumedCount(), after.counts().availableCount(), timestamp(at),
                    after.sessionId(), before.revision());
        } catch (IOException serializationFailure) {
            throw new IllegalStateException("Consultation session breaks could not be serialized", serializationFailure);
        }
        if (changed != 1) throw new DuplicateResourceException("REVISION_CONFLICT", "Session revision changed");
        appendAudit(after, before, actorAccountId, action, reason, at);
    }

    @Override
    @Transactional
    public void updateCounts(ConsultationSession before, ConsultationSession after, Instant at) {
        if (!before.sessionId().equals(after.sessionId()) || before.revision() != after.revision()) {
            throw new IllegalArgumentException("seat count updates cannot change session identity or configuration revision");
        }
        int changed = jdbc.update("""
                UPDATE consultation_session SET held_count = ?, payment_locked_count = ?, confirmed_count = ?,
                    consumed_count = ?, available_count = ?, updated_at = ?
                WHERE session_id = ? AND revision = ? AND held_count = ? AND payment_locked_count = ?
                    AND confirmed_count = ? AND consumed_count = ? AND available_count = ?
                """, after.counts().heldCount(), after.counts().paymentLockedCount(),
                after.counts().confirmedCount(), after.counts().consumedCount(), after.counts().availableCount(),
                timestamp(at), before.sessionId(), before.revision(), before.counts().heldCount(),
                before.counts().paymentLockedCount(), before.counts().confirmedCount(),
                before.counts().consumedCount(), before.counts().availableCount());
        if (changed != 1) throw new DuplicateResourceException("REVISION_CONFLICT", "Session seat counters changed concurrently");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ConsultationSession> search(UUID departmentId, LocalDate fromDate, LocalDate toDate, PageQuery page) {
        Long total = jdbc.queryForObject("""
                SELECT count(*) FROM consultation_session
                WHERE department_id = ? AND session_date BETWEEN ? AND ?
                """, Long.class, departmentId, fromDate, toDate);
        List<ConsultationSession> sessions = jdbc.query("""
                SELECT * FROM consultation_session
                WHERE department_id = ? AND session_date BETWEEN ? AND ?
                ORDER BY session_date, start_time, session_id LIMIT ? OFFSET ?
                """, this::mapOne, departmentId, fromDate, toDate, page.size(), (long) page.page() * page.size());
        return PageResult.of(sessions, total == null ? 0L : total, page.page(), page.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsultationSession> findByDoctor(UUID doctorId, LocalDate fromDate, LocalDate toDate) {
        return jdbc.query("""
                SELECT * FROM consultation_session
                WHERE doctor_id = ? AND session_date BETWEEN ? AND ?
                ORDER BY session_date, start_time, session_id
                """, this::mapOne, doctorId, fromDate, toDate);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveRegistrations(UUID sessionId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM outpatient_registration WHERE session_id = ?
                    AND status IN ('AWAITING_PAYMENT','CONFIRMED','IN_EXAM','RECONCILIATION_REQUIRED'))
                """, Boolean.class, sessionId);
        return Boolean.TRUE.equals(exists);
    }

    private void lockResource(UUID id) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))", resultSet -> {
            resultSet.next();
            return null;
        }, id.toString());
    }

    private void lockResources(UUID... ids) {
        Stream.of(ids).distinct().sorted().forEach(this::lockResource);
    }

    private void requireNoOverlap(ConsultationSession.Configuration config, UUID excludedSessionId) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1 FROM consultation_session
                    WHERE session_date = ? AND status <> 'CANCELLED'
                      AND (doctor_id = ? OR room_id = ?)
                      AND start_time < ? AND end_time > ?
                      AND (?::uuid IS NULL OR session_id <> ?::uuid))
                """;
        boolean overlaps = Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, config.sessionDate(),
                config.doctorId(), config.roomId(), config.endTime(), config.startTime(),
                excludedSessionId, excludedSessionId));
        if (overlaps) throw new DuplicateResourceException("SESSION_OVERLAP", "Doctor or room already has an overlapping session");
    }

    private void appendAudit(ConsultationSession after, ConsultationSession before,
            UUID actorAccountId, String action, String reason, Instant at) {
        try {
            jdbc.update("""
                    INSERT INTO consultation_session_audit(session_id, revision, action, actor_account_id,
                        before_state, after_state, reason, occurred_at)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
                    """, after.sessionId(), after.revision(), action, actorAccountId,
                    before == null ? null : objectMapper.writeValueAsString(before),
                    objectMapper.writeValueAsString(after), reason, timestamp(at));
        } catch (IOException serializationFailure) {
            throw new IllegalStateException("Consultation session audit could not be serialized", serializationFailure);
        }
    }

    private ConsultationSession mapOne(ResultSet row, int index) throws SQLException {
        try {
            var config = new ConsultationSession.Configuration(row.getObject("doctor_id", UUID.class),
                    row.getObject("department_id", UUID.class), row.getObject("room_id", UUID.class),
                    row.getObject("session_date", LocalDate.class), row.getObject("start_time", java.time.LocalTime.class),
                    row.getObject("end_time", java.time.LocalTime.class),
                    objectMapper.readValue(row.getString("breaks"), BREAKS_TYPE), row.getInt("capacity"));
            var counts = new ConsultationSession.Counts(row.getInt("held_count"), row.getInt("payment_locked_count"),
                    row.getInt("confirmed_count"), row.getInt("consumed_count"), row.getInt("available_count"));
            return new ConsultationSession(row.getObject("session_id", UUID.class), row.getInt("revision"),
                    config, ConsultationSessionStatus.valueOf(row.getString("status")), counts);
        } catch (IOException | IllegalArgumentException invalidStoredSession) {
            throw new SQLException("Stored consultation session is malformed", invalidStoredSession);
        }
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
