package com.mediflow.organization.infrastructure.persistence.adapter;

import com.mediflow.common.api.PageResult;
import com.mediflow.organization.application.dto.response.RoomSummary;
import com.mediflow.organization.application.dto.response.ScheduleManagementScopeSnapshot;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Role;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC adapter explicitly bound to the Organization store. */
@Repository
public class JdbcSchedulingAuthorityRepository implements SchedulingAuthorityRepository {
    private final JdbcTemplate jdbc;

    public JdbcSchedulingAuthorityRepository(@Qualifier("organizationDataSource") DataSource source) {
        this.jdbc = new JdbcTemplate(source);
    }

    @Override
    public Optional<AccountAuthority> findAccount(UUID accountId) {
        return account(accountId, false);
    }

    @Override
    public Optional<AccountAuthority> lockAccount(UUID accountId) {
        return account(accountId, true);
    }

    @Override
    public Optional<StaffAuthority> findStaff(UUID staffId) {
        return staff(staffId, false);
    }

    @Override
    public Optional<StaffAuthority> lockStaff(UUID staffId) {
        return staff(staffId, true);
    }

    @Override
    public Optional<DepartmentAuthority> findDepartment(UUID departmentId) {
        return department(departmentId, false);
    }

    @Override
    public Optional<DepartmentAuthority> lockDepartment(UUID departmentId) {
        return department(departmentId, true);
    }

    @Override
    public ScopeState findScope(UUID accountId) {
        List<ScopeState> state = jdbc.query("""
                SELECT revision, updated_at, updated_by_account_id
                FROM schedule_management_scope WHERE account_id=?
                """, (row, ignored) -> new ScopeState(row.getLong("revision"),
                departmentIds(accountId), instant(row, "updated_at"),
                row.getObject("updated_by_account_id", UUID.class)), accountId);
        if (state.isEmpty()) return new ScopeState(0, List.of(), null, null);
        return state.getFirst();
    }

    @Override
    public Optional<ScopeCommandReceipt> findReceipt(UUID actorAccountId, String method,
            String canonicalPath, String idempotencyKey) {
        return jdbc.query("""
                SELECT actor_account_id,account_id,method,canonical_path,idempotency_key,expected_revision,
                       department_ids,reason,response_revision,response_department_ids,
                       response_updated_at,response_updated_by_account_id,response_status,response_location
                FROM schedule_management_scope_command_receipt
                WHERE actor_account_id=? AND method=? AND canonical_path=? AND idempotency_key=?
                """, (row, ignored) -> {
            ScheduleManagementScopeSnapshot response = new ScheduleManagementScopeSnapshot(
                    row.getObject("account_id", UUID.class), row.getLong("response_revision"),
                    parseIds(row.getString("response_department_ids")),
                    instant(row, "response_updated_at"),
                    row.getObject("response_updated_by_account_id", UUID.class));
            return new ScopeCommandReceipt(row.getObject("actor_account_id", UUID.class),
                    row.getObject("account_id", UUID.class),
                    row.getString("method"), row.getString("canonical_path"),
                    row.getString("idempotency_key"), row.getLong("expected_revision"),
                    parseIds(row.getString("department_ids")), row.getString("reason"), response,
                    row.getInt("response_status"), row.getString("response_location"));
        }, actorAccountId, method, canonicalPath, idempotencyKey).stream().findFirst();
    }

    @Override
    public void saveScope(UUID accountId, long revision, List<UUID> departmentIds,
            Instant updatedAt, UUID updatedByAccountId) {
        jdbc.update("""
                INSERT INTO schedule_management_scope(account_id,revision,updated_at,updated_by_account_id)
                VALUES (?,?,?,?)
                ON CONFLICT (account_id) DO UPDATE SET revision=EXCLUDED.revision,
                    updated_at=EXCLUDED.updated_at, updated_by_account_id=EXCLUDED.updated_by_account_id
                """, accountId, revision, Timestamp.from(updatedAt), updatedByAccountId);
        jdbc.update("DELETE FROM schedule_management_scope_department WHERE account_id=?", accountId);
        if (!departmentIds.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO schedule_management_scope_department(account_id,department_id)
                    VALUES (?,?)
                    """, departmentIds, departmentIds.size(), (statement, departmentId) -> {
                statement.setObject(1, accountId);
                statement.setObject(2, departmentId);
            });
        }
    }

    @Override
    public void appendAudit(ScopeAudit audit) {
        jdbc.update("""
                INSERT INTO schedule_management_scope_audit
                    (audit_id,account_id,revision,previous_revision,actor_account_id,reason,
                     previous_department_ids,department_ids,occurred_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, audit.auditId(), audit.accountId(), audit.revision(), audit.previousRevision(),
                audit.actorAccountId(), audit.reason(), joinIds(audit.previousDepartmentIds()),
                joinIds(audit.departmentIds()), Timestamp.from(audit.occurredAt()));
    }

    @Override
    public void saveReceipt(ScopeCommandReceipt receipt) {
        jdbc.update("""
                INSERT INTO schedule_management_scope_command_receipt
                    (actor_account_id,account_id,method,canonical_path,idempotency_key,expected_revision,
                     department_ids,reason,response_revision,response_department_ids,
                     response_updated_at,response_updated_by_account_id,response_status,response_location)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, receipt.actorAccountId(), receipt.accountId(), receipt.method(), receipt.canonicalPath(),
                receipt.idempotencyKey(), receipt.expectedRevision(), joinIds(receipt.departmentIds()),
                receipt.reason(), receipt.response().revision(),
                joinIds(receipt.response().departmentIds()), Timestamp.from(receipt.response().updatedAt()),
                receipt.response().updatedByAccountId(), receipt.responseStatus(), receipt.responseLocation());
    }

    @Override
    public SchedulingAuthorityData findSchedulingAuthority(UUID actorAccountId, UUID departmentId) {
        return jdbc.queryForObject("""
                SELECT (a.account_id IS NOT NULL) AS account_exists,
                       COALESCE(a.is_active,FALSE) AS account_active,
                       a.role AS current_role,
                       a.staff_id AS staff_id,
                       COALESCE(s.status='ACTIVE',FALSE) AS staff_active,
                       COALESCE(d.is_active,FALSE) AS department_active,
                       CASE WHEN a.role='ADMIN' THEN COALESCE(a.is_active AND d.is_active,FALSE)
                            WHEN a.role='MANAGER' THEN COALESCE(a.is_active AND s.status='ACTIVE'
                                 AND d.is_active AND sd.department_id IS NOT NULL,FALSE)
                            ELSE FALSE END AS can_manage_schedules,
                       COALESCE(scope.revision,0) AS scope_revision
                FROM (SELECT 1) seed
                LEFT JOIN account a ON a.account_id=?
                LEFT JOIN staff s ON s.staff_id=a.staff_id
                LEFT JOIN department d ON d.department_id=?
                LEFT JOIN schedule_management_scope scope ON scope.account_id=a.account_id
                LEFT JOIN schedule_management_scope_department sd
                       ON sd.account_id=a.account_id AND sd.department_id=d.department_id
                """, (row, ignored) -> new SchedulingAuthorityData(
                row.getBoolean("account_exists"), row.getBoolean("account_active"),
                row.getString("current_role"), row.getObject("staff_id", UUID.class),
                row.getBoolean("staff_active"), row.getBoolean("department_active"),
                row.getBoolean("can_manage_schedules"), row.getLong("scope_revision")),
                actorAccountId, departmentId);
    }

    @Override
    public CurrentDoctorAuthorityData findCurrentDoctorAuthority(UUID actorAccountId) {
        return jdbc.queryForObject("""
                SELECT (a.account_id IS NOT NULL) AS account_exists,
                       COALESCE(a.is_active,FALSE) AS account_active,
                       a.role AS current_role,
                       a.staff_id AS staff_id,
                       COALESCE(s.status='ACTIVE',FALSE) AS staff_active,
                       s.department_id AS department_id,
                       COALESCE(d.is_active,FALSE) AS department_active
                FROM (SELECT 1) seed
                LEFT JOIN account a ON a.account_id=?
                LEFT JOIN staff s ON s.staff_id=a.staff_id
                LEFT JOIN department d ON d.department_id=s.department_id
                """, (row, ignored) -> new CurrentDoctorAuthorityData(
                row.getBoolean("account_exists"), row.getBoolean("account_active"),
                row.getString("current_role"), row.getObject("staff_id", UUID.class),
                row.getBoolean("staff_active"), row.getObject("department_id", UUID.class),
                row.getBoolean("department_active")), actorAccountId);
    }

    @Override
    public ConsultationEligibilityData findConsultationEligibility(UUID doctorId,
            UUID departmentId, UUID roomId) {
        return jdbc.queryForObject("""
                SELECT (s.staff_id IS NOT NULL) AS doctor_exists,
                       COALESCE(s.status='ACTIVE',FALSE) AS doctor_active,
                       COALESCE(s.status='ACTIVE' AND s.job_title='DOCTOR',FALSE) AS eligible_doctor,
                       s.department_id AS doctor_department_id,
                       (d.department_id IS NOT NULL) AS department_exists,
                       COALESCE(d.is_active,FALSE) AS department_active,
                       (r.room_id IS NOT NULL) AS room_exists,
                       COALESCE(r.is_active,FALSE) AS room_active,
                       r.department_id AS room_department_id,
                       r.room_type AS room_type,
                       COALESCE(s.status='ACTIVE' AND s.job_title='DOCTOR'
                           AND s.department_id=? AND d.is_active
                           AND r.is_active AND r.department_id=? AND r.room_type='CONSULTATION',FALSE)
                           AS eligible
                FROM (SELECT 1) seed
                LEFT JOIN staff s ON s.staff_id=?
                LEFT JOIN department d ON d.department_id=?
                LEFT JOIN room r ON r.room_id=?
                """, (row, ignored) -> new ConsultationEligibilityData(
                row.getBoolean("doctor_exists"), row.getBoolean("doctor_active"),
                row.getBoolean("eligible_doctor"), row.getObject("doctor_department_id", UUID.class),
                row.getBoolean("department_exists"), row.getBoolean("department_active"),
                row.getBoolean("room_exists"), row.getBoolean("room_active"),
                row.getObject("room_department_id", UUID.class), row.getString("room_type"),
                row.getBoolean("eligible")), departmentId, departmentId, doctorId, departmentId, roomId);
    }

    @Override
    public PageResult<RoomSummary> findConsultationRooms(UUID departmentId, int page, int size) {
        long offset = Math.multiplyExact((long) page, size);
        Long total = jdbc.queryForObject("""
                SELECT count(*) FROM room
                WHERE department_id=? AND is_active=TRUE AND room_type='CONSULTATION'
                """, Long.class, departmentId);
        List<RoomSummary> rooms = jdbc.query("""
                SELECT room_id,department_id,room_name,room_type FROM room
                WHERE department_id=? AND is_active=TRUE AND room_type='CONSULTATION'
                ORDER BY room_name,room_id LIMIT ? OFFSET ?
                """, (row, ignored) -> new RoomSummary(row.getObject("room_id", UUID.class),
                row.getObject("department_id", UUID.class), row.getString("room_name"),
                row.getString("room_type")), departmentId, size, offset);
        return PageResult.of(rooms, total == null ? 0 : total, page, size);
    }

    private Optional<AccountAuthority> account(UUID accountId, boolean lock) {
        String sql = "SELECT account_id,is_active,role,staff_id FROM account WHERE account_id=?"
                + (lock ? " FOR UPDATE" : "");
        return jdbc.query(sql, (row, ignored) -> new AccountAuthority(
                row.getObject("account_id", UUID.class), row.getBoolean("is_active"),
                Role.valueOf(row.getString("role")), row.getObject("staff_id", UUID.class)), accountId)
                .stream().findFirst();
    }

    private Optional<StaffAuthority> staff(UUID staffId, boolean lock) {
        String sql = "SELECT staff_id,status,job_title,department_id FROM staff WHERE staff_id=?"
                + (lock ? " FOR UPDATE" : "");
        return jdbc.query(sql, (row, ignored) -> new StaffAuthority(
                row.getObject("staff_id", UUID.class), "ACTIVE".equals(row.getString("status")),
                JobTitle.valueOf(row.getString("job_title")),
                row.getObject("department_id", UUID.class)), staffId).stream().findFirst();
    }

    private Optional<DepartmentAuthority> department(UUID departmentId, boolean lock) {
        String sql = "SELECT department_id,is_active FROM department WHERE department_id=?"
                + (lock ? " FOR UPDATE" : "");
        return jdbc.query(sql, (row, ignored) -> new DepartmentAuthority(
                row.getObject("department_id", UUID.class), row.getBoolean("is_active")), departmentId)
                .stream().findFirst();
    }

    private List<UUID> departmentIds(UUID accountId) {
        return jdbc.query("""
                SELECT department_id FROM schedule_management_scope_department
                WHERE account_id=? ORDER BY department_id
                """, (row, ignored) -> row.getObject("department_id", UUID.class), accountId);
    }

    private static String joinIds(List<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(","));
    }

    private static List<UUID> parseIds(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<UUID> ids = new ArrayList<>();
        for (String id : value.split(",")) ids.add(UUID.fromString(id));
        return List.copyOf(ids);
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
