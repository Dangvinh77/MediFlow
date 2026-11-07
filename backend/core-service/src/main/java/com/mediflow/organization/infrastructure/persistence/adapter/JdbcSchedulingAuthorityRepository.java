package com.mediflow.organization.infrastructure.persistence.adapter;

import com.mediflow.common.api.PageResult;
import com.mediflow.organization.application.dto.response.RoomSummary;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository;
import com.mediflow.organization.domain.model.Role;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
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
    public Optional<DepartmentAuthority> findDepartment(UUID departmentId) {
        return department(departmentId);
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
                       CASE WHEN a.role='ADMIN' THEN COALESCE(a.is_active AND d.is_active AND d.department_type='CLINICAL',FALSE)
                            WHEN a.role='DOCTOR' THEN COALESCE(a.is_active AND s.status='ACTIVE' AND s.job_title='DOCTOR'
                                 AND s.department_id=d.department_id AND d.is_active AND d.department_type='CLINICAL'
                                 AND h.appointment_id IS NOT NULL,FALSE)
                            ELSE FALSE END AS can_manage_schedules,
                       CASE WHEN a.role='DOCTOR' THEN COALESCE(hs.revision,0) ELSE 0 END AS scope_revision
                FROM (SELECT 1) seed
                LEFT JOIN account a ON a.account_id=?
                LEFT JOIN staff s ON s.staff_id=a.staff_id
                LEFT JOIN department d ON d.department_id=?
                LEFT JOIN department_head_appointment h ON h.account_id=a.account_id AND h.staff_id=a.staff_id AND h.department_id=d.department_id AND h.ended_at IS NULL
                LEFT JOIN department_head_state hs ON hs.department_id=d.department_id
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
                SELECT room_id,department_id,room_name,room_type,room_number,zone,location FROM room
                WHERE department_id=? AND is_active=TRUE AND room_type='CONSULTATION'
                ORDER BY zone,room_number,room_id LIMIT ? OFFSET ?
                """, (row, ignored) -> new RoomSummary(row.getObject("room_id", UUID.class),
                row.getObject("department_id", UUID.class), row.getString("room_name"),
                row.getString("room_type"), row.getString("room_number"), row.getString("zone"),
                row.getString("location")), departmentId, size, offset);
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

    private Optional<DepartmentAuthority> department(UUID departmentId) {
        String sql = "SELECT department_id,is_active FROM department WHERE department_id=?";
        return jdbc.query(sql, (row, ignored) -> new DepartmentAuthority(
                row.getObject("department_id", UUID.class), row.getBoolean("is_active")), departmentId)
                .stream().findFirst();
    }

                }
