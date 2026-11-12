package com.mediflow.organization.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.organization.application.dto.response.DepartmentHeadSnapshot;
import com.mediflow.organization.application.dto.response.OwnCapabilities;
import com.mediflow.organization.application.event.DepartmentHeadChangedEvent;
import com.mediflow.organization.application.port.out.DepartmentHeadRepository;
import com.mediflow.organization.infrastructure.messaging.OrganizationEventOutboxStore;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

@Repository
public class JdbcDepartmentHeadRepository implements DepartmentHeadRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final OrganizationEventOutboxStore outbox;

    public JdbcDepartmentHeadRepository(
            @Qualifier("organizationDataSource") DataSource source,
            ObjectMapper json,
            OrganizationEventOutboxStore outbox) {
        this.jdbc = new JdbcTemplate(source);
        this.json = json;
        this.outbox = outbox;
    }

    public void lockCommands() {
        // Appointment volume is low. A transaction-scoped lock also serializes commands
        // for different departments competing for the same staff; unique indexes remain
        // the final database guard. This lock never protects authority reads.
        jdbc.execute("SELECT pg_advisory_xact_lock(20261031)");
    }

    public boolean lockClinicalDepartment(UUID id) {
        return jdbc
                .query(
                        "SELECT is_active AND department_type='CLINICAL' FROM department WHERE"
                                + " department_id=? FOR UPDATE",
                        (r, n) -> r.getBoolean(1),
                        id)
                .stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "RESOURCE_NOT_FOUND", "Department not found"));
    }

    public Optional<UUID> lockEligibleDoctorAccount(UUID staffId, UUID departmentId) {
        var staff =
                jdbc.query(
                        "SELECT staff_id FROM staff WHERE staff_id=? AND department_id=? AND"
                                + " status='ACTIVE' AND job_title='DOCTOR' FOR UPDATE",
                        (r, n) -> r.getObject(1, UUID.class),
                        staffId,
                        departmentId);
        if (staff.isEmpty()) return Optional.empty();
        var accounts =
                jdbc.query(
                        "SELECT account_id FROM account WHERE staff_id=? AND is_active AND"
                                + " role='DOCTOR' ORDER BY account_id FOR UPDATE",
                        (r, n) -> r.getObject(1, UUID.class),
                        staffId);
        return accounts.size() == 1 ? Optional.of(accounts.getFirst()) : Optional.empty();
    }

    public boolean hasActiveStaffAppointment(UUID id) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM department_head_appointment WHERE staff_id=?"
                                + " AND ended_at IS NULL)",
                        Boolean.class,
                        id));
    }

    public DepartmentHeadSnapshot snapshot(UUID id) {
        var terms =
                jdbc.query(
                        "SELECT * FROM department_head_appointment WHERE department_id=? ORDER BY"
                                + " started_at,appointment_id",
                        (r, n) ->
                                new DepartmentHeadSnapshot.Term(
                                        r.getObject("appointment_id", UUID.class),
                                        r.getObject("staff_id", UUID.class),
                                        r.getObject("account_id", UUID.class),
                                        instant(r, "started_at"),
                                        instant(r, "ended_at")),
                        id);
        var current = terms.stream().filter(t -> t.endedAt() == null).findFirst().orElse(null);
        var state =
                jdbc.query(
                        "SELECT * FROM department_head_state WHERE department_id=?",
                        (r, n) ->
                                new DepartmentHeadSnapshot(
                                        id,
                                        r.getLong("revision"),
                                        current == null ? null : current.appointmentId(),
                                        current == null ? null : current.staffId(),
                                        current == null ? null : current.accountId(),
                                        instant(r, "updated_at"),
                                        r.getObject("updated_by_account_id", UUID.class),
                                        List.copyOf(terms)),
                        id);
        return state.isEmpty()
                ? new DepartmentHeadSnapshot(
                        id, 0, null, null, null, null, null, List.copyOf(terms))
                : state.getFirst();
    }

    public Optional<Receipt> receipt(UUID actor, UUID department, String key) {
        return jdbc
                .query(
                        "SELECT command,response FROM department_head_receipt WHERE"
                                + " actor_account_id=? AND department_id=? AND idempotency_key=?",
                        (r, n) -> new Receipt(r.getString(1), decode(r.getString(2))),
                        actor,
                        department,
                        key)
                .stream()
                .findFirst();
    }

    public void receipt(
            UUID actor,
            UUID department,
            String key,
            String command,
            DepartmentHeadSnapshot response) {
        jdbc.update(
                "INSERT INTO"
                    + " department_head_receipt(actor_account_id,department_id,idempotency_key,command,response)"
                    + " VALUES(?,?,?,?,?)",
                actor,
                department,
                key,
                command,
                encode(response));
    }

    public void replace(
            UUID actor,
            UUID department,
            UUID staff,
            UUID account,
            UUID appointment,
            long revision,
            String reason,
            Instant now,
            String correlationId) {
        var previous = snapshot(department);
        jdbc.update(
                "UPDATE department_head_appointment SET ended_at=? WHERE department_id=? AND"
                        + " ended_at IS NULL",
                Timestamp.from(now),
                department);
        if (appointment != null)
            jdbc.update(
                    "INSERT INTO"
                        + " department_head_appointment(appointment_id,department_id,staff_id,account_id,started_at)"
                        + " VALUES(?,?,?,?,?)",
                    appointment,
                    department,
                    staff,
                    account,
                    Timestamp.from(now));
        jdbc.update(
                "INSERT INTO"
                    + " department_head_state(department_id,revision,updated_at,updated_by_account_id)"
                    + " VALUES(?,?,?,?) ON CONFLICT(department_id) DO UPDATE SET"
                    + " revision=EXCLUDED.revision,updated_at=EXCLUDED.updated_at,updated_by_account_id=EXCLUDED.updated_by_account_id",
                department,
                revision,
                Timestamp.from(now),
                actor);
        jdbc.update(
                "UPDATE department SET department_head_id=?,updated_at=? WHERE department_id=?",
                staff,
                Timestamp.from(now),
                department);
        jdbc.update(
                "INSERT INTO"
                    + " department_head_audit(audit_id,department_id,revision,previous_revision,previous_appointment_id,appointment_id,actor_account_id,reason,occurred_at)"
                    + " VALUES(?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                department,
                revision,
                previous.revision(),
                previous.appointmentId(),
                appointment,
                actor,
                reason,
                Timestamp.from(now));
        UUID eventId = UUID.randomUUID();
        var event =
                new DepartmentHeadChangedEvent(
                        eventId,
                        now,
                        correlationId,
                        department,
                        revision,
                        previous.appointmentId(),
                        appointment,
                        staff,
                        account,
                        actor);
        outbox.append(
                eventId,
                now,
                correlationId,
                "organization.department-head.changed",
                event.getClass().getName(),
                encode(event));
    }

    public OwnCapabilities capabilities(UUID id, Instant now) {
        return jdbc.queryForObject(
                """
SELECT a.account_id,a.staff_id,a.role,
  COALESCE(a.is_active AND a.role='DOCTOR' AND s.status='ACTIVE' AND s.job_title='DOCTOR' AND d.is_active AND d.department_type='CLINICAL',FALSE) AS practice,
  a.is_active AS active,COALESCE(s.status='ACTIVE',FALSE) AS staff_active,
  COALESCE(a.is_active AND a.role='LAB_TECH' AND s.status='ACTIVE' AND EXISTS(SELECT 1 FROM staff_capability_grant g
    WHERE g.staff_id=a.staff_id AND g.capability='LAB_RESULT_RELEASE' AND g.revoked_at IS NULL),FALSE) AS lab_release
FROM account a LEFT JOIN staff s ON s.staff_id=a.staff_id LEFT JOIN department d ON d.department_id=s.department_id WHERE a.account_id=?
""",
                (r, n) ->
                        new OwnCapabilities(
                                id,
                                r.getObject("staff_id", UUID.class),
                                r.getString("role"),
                                r.getBoolean("practice"),
                                management(
                                        id,
                                        r.getString("role"),
                                        r.getBoolean("active") && r.getBoolean("staff_active")),
                                now,
                                r.getBoolean("lab_release")),
                id);
    }

    private List<OwnCapabilities.ManagementDepartment> management(
            UUID id, String role, boolean active) {
        if (!active) return List.of();
        if (!"DOCTOR".equals(role)) return List.of();
        return jdbc.query(
                """
SELECT d.department_id,d.department_name,h.appointment_id,st.revision
FROM department_head_appointment h JOIN account a ON a.account_id=h.account_id AND a.is_active AND a.role='DOCTOR' AND a.staff_id=h.staff_id
JOIN staff s ON s.staff_id=h.staff_id AND s.status='ACTIVE' AND s.job_title='DOCTOR' AND s.department_id=h.department_id
JOIN department d ON d.department_id=h.department_id AND d.is_active AND d.department_type='CLINICAL'
JOIN department_head_state st ON st.department_id=d.department_id WHERE h.account_id=? AND h.ended_at IS NULL ORDER BY d.department_name,d.department_id
""",
                (r, n) ->
                        new OwnCapabilities.ManagementDepartment(
                                r.getObject(1, UUID.class),
                                r.getString(2),
                                r.getObject(3, UUID.class),
                                r.getLong(4)),
                id);
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize appointment record", e);
        }
    }

    private DepartmentHeadSnapshot decode(String value) {
        try {
            return json.readValue(value, DepartmentHeadSnapshot.class);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read appointment receipt", e);
        }
    }

    private static Instant instant(ResultSet r, String column) throws SQLException {
        var t = r.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }
}
