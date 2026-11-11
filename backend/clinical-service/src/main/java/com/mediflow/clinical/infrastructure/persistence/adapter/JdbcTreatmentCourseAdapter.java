package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.port.out.TreatmentCourseRepositoryPort;
import com.mediflow.clinical.domain.model.TreatmentCourse;
import com.mediflow.clinical.domain.model.TreatmentCourseDecision;
import com.mediflow.clinical.domain.model.TreatmentCourseStatus;

@Component
public class JdbcTreatmentCourseAdapter implements TreatmentCourseRepositoryPort {
    private static final String COLUMNS = """
            c.course_id, c.patient_id, c.department_id, c.problem, c.opening_reason, c.responsible_doctor_id,
            c.status, c.opened_at, c.closure_decision, c.closure_note, c.closed_by_staff_id, c.closed_at
            """;
    private static final String LAST_VISIT = """
            (SELECT max(r.examination_date) FROM treatment_course_visit v
             JOIN medical_record r ON r.record_id = v.record_id WHERE v.course_id = c.course_id) AS last_visit_date
            """;

    private final JdbcTemplate jdbc;

    public JdbcTreatmentCourseAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(TreatmentCourse course) {
        jdbc.update("""
                INSERT INTO treatment_course(course_id, patient_id, department_id, problem, opening_reason,
                    responsible_doctor_id, status, opened_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, course.getCourseId(), course.getPatientId(), course.getDepartmentId(), course.getProblem(),
                course.getOpeningReason(), course.getResponsibleDoctorId(), course.getStatus().name(),
                Timestamp.from(course.getOpenedAt()));
        for (UUID recordId : course.getRecordIds()) {
            addVisit(course.getCourseId(), recordId, course.getResponsibleDoctorId(), course.getOpenedAt());
        }
    }

    @Override
    public Optional<TreatmentCourse> findByIdForUpdate(UUID courseId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM treatment_course c WHERE c.course_id = ? FOR UPDATE",
                (rs, n) -> course(rs), courseId).stream().findFirst();
    }

    @Override
    public Optional<CourseView> view(UUID courseId) {
        return jdbc.query("SELECT " + COLUMNS + ", " + LAST_VISIT + " FROM treatment_course c WHERE c.course_id = ?",
                (rs, n) -> new CourseView(course(rs), date(rs)), courseId).stream().findFirst();
    }

    @Override
    public List<CourseView> findByPatient(UUID patientId) {
        return jdbc.query("SELECT " + COLUMNS + ", " + LAST_VISIT
                        + " FROM treatment_course c WHERE c.patient_id = ? ORDER BY c.opened_at DESC, c.course_id",
                (rs, n) -> new CourseView(course(rs), date(rs)), patientId);
    }

    @Override
    public boolean addVisit(UUID courseId, UUID recordId, UUID staffId, Instant at) {
        return jdbc.update("""
                INSERT INTO treatment_course_visit(course_id, record_id, added_by_staff_id, added_at) VALUES (?,?,?,?)
                ON CONFLICT (course_id, record_id) DO NOTHING
                """, courseId, recordId, staffId, Timestamp.from(at)) == 1;
    }

    @Override
    public void close(TreatmentCourse course) {
        jdbc.update("""
                UPDATE treatment_course SET status = ?, closure_decision = ?, closure_note = ?, closed_by_staff_id = ?,
                    closed_at = ? WHERE course_id = ?
                """, course.getStatus().name(), course.getClosureDecision().name(), course.getClosureNote(),
                course.getClosedByStaffId(), Timestamp.from(course.getClosedAt()), course.getCourseId());
    }

    @Override
    public Map<UUID, List<UUID>> courseIdsByRecord(Collection<UUID> recordIds) {
        Map<UUID, List<UUID>> result = new LinkedHashMap<>();
        if (recordIds.isEmpty()) {
            return result;
        }
        jdbc.query("SELECT record_id, course_id FROM treatment_course_visit WHERE record_id = ANY(?) ORDER BY added_at, course_id",
                rs -> {
                    result.computeIfAbsent(rs.getObject("record_id", UUID.class), ignored -> new ArrayList<>())
                            .add(rs.getObject("course_id", UUID.class));
                }, (Object) recordIds.toArray(UUID[]::new));
        return result;
    }

    private TreatmentCourse course(ResultSet rs) throws SQLException {
        UUID courseId = rs.getObject("course_id", UUID.class);
        List<UUID> visits = jdbc.queryForList(
                "SELECT record_id FROM treatment_course_visit WHERE course_id = ? ORDER BY added_at, record_id",
                UUID.class, courseId);
        String decision = rs.getString("closure_decision");
        return TreatmentCourse.restore(courseId, rs.getObject("patient_id", UUID.class),
                rs.getObject("department_id", UUID.class), rs.getString("problem"), rs.getString("opening_reason"),
                rs.getObject("responsible_doctor_id", UUID.class), TreatmentCourseStatus.valueOf(rs.getString("status")),
                visits, instant(rs, "opened_at"), decision == null ? null : TreatmentCourseDecision.valueOf(decision),
                rs.getString("closure_note"), rs.getObject("closed_by_staff_id", UUID.class), instant(rs, "closed_at"));
    }

    private static LocalDate date(ResultSet rs) throws SQLException {
        java.sql.Date value = rs.getDate("last_visit_date");
        return value == null ? null : value.toLocalDate();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
