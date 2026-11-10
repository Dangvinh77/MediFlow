package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.application.port.out.ClinicQueueRepositoryPort;
import com.mediflow.clinical.application.service.ConditionalOnClinicIntake;
import com.mediflow.clinical.domain.model.ClinicQueueTicket;
import com.mediflow.clinical.domain.model.ClinicQueueTicketStatus;
import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.common.exception.DuplicateResourceException;

@Component
@ConditionalOnClinicIntake
public class ClinicQueuePersistenceAdapter implements ClinicQueueRepositoryPort {
    private final JdbcTemplate jdbc;

    public ClinicQueuePersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override @Transactional
    public int allocateRoomQueueNumber(UUID roomId, LocalDate queueDate) {
        Integer number = jdbc.queryForObject("""
                INSERT INTO clinic_room_queue_counter(room_id, queue_date, next_number) VALUES (?, ?, 2)
                ON CONFLICT (room_id, queue_date)
                DO UPDATE SET next_number = clinic_room_queue_counter.next_number + 1
                RETURNING next_number - 1
                """, Integer.class, roomId, Date.valueOf(queueDate));
        if (number == null || number < 1) throw new IllegalStateException("room queue number could not be allocated");
        return number;
    }

    @Override @Transactional(readOnly = true)
    public Optional<ClinicQueueTicket> findActiveByRegistration(UUID registrationId) {
        return jdbc.query("SELECT * FROM clinic_queue_ticket WHERE registration_id = ? AND status <> 'CANCELLED'",
                this::map, registrationId).stream().findFirst();
    }

    @Override @Transactional
    public Optional<ClinicQueueTicket> findByIdForUpdate(UUID ticketId) {
        return jdbc.query("SELECT * FROM clinic_queue_ticket WHERE ticket_id = ? FOR UPDATE", this::map, ticketId).stream().findFirst();
    }

    @Override @Transactional
    public ClinicQueueTicket insert(ClinicQueueTicket t) {
        try {
            jdbc.update("""
                    INSERT INTO clinic_queue_ticket(ticket_id, revision, registration_id, session_id, room_id, department_id,
                        doctor_id, queue_date, room_queue_number, status, priority, issued_by_account_id, issued_by_staff_id,
                        issued_at, authority_scope_revision)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, t.ticketId(), t.revision(), t.registrationId(), t.sessionId(), t.roomId(), t.departmentId(), t.doctorId(),
                    Date.valueOf(t.queueDate()), t.roomQueueNumber(), t.status().name(), t.priority(), t.issuedByAccountId(),
                    t.issuedByStaffId(), Timestamp.from(t.issuedAt()), t.authorityScopeRevision());
        } catch (DuplicateKeyException duplicate) {
            throw new DuplicateResourceException("ROOM_TICKET_EXISTS", "This registration already has a room queue number");
        }
        return t;
    }

    @Override @Transactional
    public void update(ClinicQueueTicket before, ClinicQueueTicket after) {
        int changed = jdbc.update("""
                UPDATE clinic_queue_ticket SET revision = ?, status = ?, priority = ?, called_by_account_id = ?, called_at = ?,
                    cancelled_by_account_id = ?, cancel_reason = ?, cancelled_at = ?
                WHERE ticket_id = ? AND revision = ?
                """, after.revision(), after.status().name(), after.priority(), after.calledByAccountId(), timestamp(after.calledAt()),
                after.cancelledByAccountId(), after.cancelReason(), timestamp(after.cancelledAt()), after.ticketId(), before.revision());
        if (changed != 1) throw new DuplicateResourceException("REVISION_CONFLICT", "Queue ticket revision changed");
    }

    @Override @Transactional(readOnly = true)
    public Map<UUID, Integer> activeNumbers(Collection<UUID> registrationIds) {
        Map<UUID, Integer> numbers = new HashMap<>();
        if (registrationIds.isEmpty()) return numbers;
        jdbc.query("""
                SELECT registration_id, room_queue_number FROM clinic_queue_ticket
                WHERE status <> 'CANCELLED' AND registration_id = ANY(?)
                """, ps -> ps.setArray(1, ps.getConnection().createArrayOf("uuid", registrationIds.toArray())),
                rs -> { numbers.put(rs.getObject("registration_id", UUID.class), rs.getInt("room_queue_number")); });
        return numbers;
    }

    @Override @Transactional(readOnly = true)
    public Optional<ClinicQueueTicket> findById(UUID ticketId) {
        return jdbc.query("SELECT * FROM clinic_queue_ticket WHERE ticket_id = ?", this::map, ticketId).stream().findFirst();
    }

    @Override @Transactional
    public void appendAdjustment(UUID ticketId, String type, String reason, UUID actorAccountId, UUID actorStaffId, Instant at) {
        jdbc.update("""
                INSERT INTO queue_adjustment(adjustment_id, ticket_id, type, reason, actor_account_id, actor_staff_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), ticketId, type, reason, actorAccountId, actorStaffId, Timestamp.from(at));
    }

    @Override @Transactional(readOnly = true)
    public List<BoardRow> boardRows(Collection<UUID> sessionIds) {
        if (sessionIds.isEmpty()) return List.of();
        return jdbc.query("""
                SELECT t.*, r.registration_id AS reg_id, r.patient_id AS reg_patient_id, r.session_id AS reg_session_id,
                       r.status AS reg_status, r.checked_in_at AS reg_checked_in_at, r.queue_number AS reg_queue_number,
                       r.clearance_id AS reg_clearance_id,
                       EXISTS(SELECT 1 FROM vital_sign_observation v WHERE v.registration_id = r.registration_id) AS has_vitals
                FROM outpatient_registration r
                LEFT JOIN clinic_queue_ticket t ON t.registration_id = r.registration_id AND t.status <> 'CANCELLED'
                WHERE r.session_id = ANY(?) AND r.status IN ('AWAITING_PAYMENT','CONFIRMED','IN_EXAM')
                """, ps -> ps.setArray(1, ps.getConnection().createArrayOf("uuid", sessionIds.toArray())), (rs, row) -> {
                    Number queue = (Number) rs.getObject("reg_queue_number");
                    return new BoardRow(rs.getObject("reg_id", UUID.class), rs.getObject("reg_patient_id", UUID.class),
                            rs.getObject("reg_session_id", UUID.class), OutpatientRegistrationStatus.valueOf(rs.getString("reg_status")),
                            instant(rs, "reg_checked_in_at"), queue == null ? null : queue.intValue(),
                            rs.getObject("reg_clearance_id", UUID.class) != null,
                            rs.getObject("ticket_id", UUID.class) == null ? null : map(rs, row), rs.getBoolean("has_vitals"));
                });
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private ClinicQueueTicket map(ResultSet rs, int row) throws SQLException {
        Number scope = (Number) rs.getObject("authority_scope_revision");
        return new ClinicQueueTicket(rs.getObject("ticket_id", UUID.class), rs.getInt("revision"), rs.getObject("registration_id", UUID.class),
                rs.getObject("session_id", UUID.class), rs.getObject("room_id", UUID.class), rs.getObject("department_id", UUID.class),
                rs.getObject("doctor_id", UUID.class), rs.getObject("queue_date", LocalDate.class), rs.getInt("room_queue_number"),
                ClinicQueueTicketStatus.valueOf(rs.getString("status")), rs.getBoolean("priority"), rs.getObject("issued_by_account_id", UUID.class),
                rs.getObject("issued_by_staff_id", UUID.class), instant(rs, "issued_at"), scope == null ? null : scope.longValue(),
                rs.getObject("called_by_account_id", UUID.class), instant(rs, "called_at"), rs.getObject("cancelled_by_account_id", UUID.class),
                rs.getString("cancel_reason"), instant(rs, "cancelled_at"));
    }
}
