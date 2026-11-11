package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.port.out.PatientTimelineReadPort;

@Component
public class JdbcPatientTimelineAdapter implements PatientTimelineReadPort {
    static final String RESPONSIBLE_DOCTOR = "NULL::uuid";

    private final JdbcTemplate jdbc;

    public JdbcPatientTimelineAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TimelinePage page(UUID patientId, UUID departmentId, UUID nurseDepartmentId, String status, int page,
            int size) {
        StringBuilder where = new StringBuilder(" WHERE r.patient_id = ?");
        List<Object> args = new ArrayList<>(List.of(patientId));
        if (departmentId != null) {
            where.append(" AND r.department_id = ?");
            args.add(departmentId);
        }
        if (nurseDepartmentId != null) {
            where.append(" AND r.department_id = ?");
            args.add(nurseDepartmentId);
        }
        if (status != null) {
            where.append(" AND r.status = ?");
            args.add(status);
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM medical_record r" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<TimelineRow> rows = jdbc.query("""
                SELECT r.record_id, r.examination_date, r.department_id, r.doctor_id, %s AS responsible_doctor_id,
                       o.visit_type, r.status, r.disposition, r.previous_record_id
                FROM medical_record r
                LEFT JOIN outpatient_registration o ON o.registration_id = r.appointment_id
                """.formatted(RESPONSIBLE_DOCTOR) + where
                + " ORDER BY r.examination_date DESC, r.created_at DESC, r.record_id LIMIT ? OFFSET ?",
                (rs, n) -> new TimelineRow(rs.getObject("record_id", UUID.class),
                        rs.getDate("examination_date").toLocalDate(), rs.getObject("department_id", UUID.class),
                        rs.getObject("doctor_id", UUID.class), rs.getObject("responsible_doctor_id", UUID.class),
                        rs.getString("visit_type"), rs.getString("status"), rs.getString("disposition"),
                        rs.getObject("previous_record_id", UUID.class)),
                pageArgs.toArray());
        return new TimelinePage(rows, total == null ? 0 : total);
    }
}
