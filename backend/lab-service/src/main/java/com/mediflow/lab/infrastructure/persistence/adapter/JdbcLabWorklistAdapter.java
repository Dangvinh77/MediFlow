package com.mediflow.lab.infrastructure.persistence.adapter;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mediflow.lab.application.port.out.LabWorklistPort;

@Repository
public class JdbcLabWorklistAdapter implements LabWorklistPort {
    private final JdbcTemplate jdbc;

    public JdbcLabWorklistAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<WorklistRow> open() {
        return jdbc.query("""
                SELECT t.test_id, t.patient_id, t.test_type, t.status, b.service_code, c.name, c.display_group,
                       CASE WHEN s.specimen_id IS NULL THEN 'NOT_COLLECTED'
                            WHEN s.decision IS NULL THEN 'COLLECTED' ELSE s.decision END AS specimen_status,
                       r.status AS report_status, r.result_version,
                       CASE WHEN c.turnaround_hours IS NULL THEN NULL
                            ELSE t.created_at + make_interval(hours => c.turnaround_hours) END AS due_at
                  FROM lab_test t
                  LEFT JOIN lab_test_template_binding b ON b.test_id = t.test_id
                  LEFT JOIN lab_service_catalog c ON c.service_code = b.service_code
                  LEFT JOIN LATERAL (
                        SELECT ls.specimen_id, ls.decision FROM lab_specimen ls
                          JOIN lab_specimen_test lst ON lst.specimen_id = ls.specimen_id
                         WHERE lst.test_id = t.test_id ORDER BY ls.collected_at DESC, ls.accession DESC LIMIT 1) s ON true
                  LEFT JOIN LATERAL (
                        SELECT lr.status, lr.result_version FROM lab_report lr
                         WHERE lr.test_id = t.test_id ORDER BY lr.result_version DESC LIMIT 1) r ON true
                 WHERE t.status IN ('PENDING', 'AWAITING_PAYMENT', 'READY', 'IN_PROGRESS')
                    OR (t.status = 'COMPLETED' AND r.status IN ('DRAFT', 'PENDING_REVIEW'))
                 ORDER BY due_at NULLS LAST, t.created_at
                 LIMIT 500
                """, (rs, n) -> {
            Timestamp due = rs.getTimestamp("due_at");
            Integer version = (Integer) rs.getObject("result_version");
            return new WorklistRow(rs.getObject("test_id", UUID.class), rs.getObject("patient_id", UUID.class),
                    rs.getString("test_type"), rs.getString("status"), rs.getString("service_code"),
                    rs.getString("name"), rs.getString("display_group"), rs.getString("specimen_status"),
                    rs.getString("report_status"), version, due == null ? null : due.toInstant());
        });
    }
}
