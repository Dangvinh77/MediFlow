package com.mediflow.lab.infrastructure.persistence.adapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mediflow.lab.application.port.out.LabCriticalNoticeRepositoryPort;
import com.mediflow.lab.domain.model.LabCriticalNotice;
import com.mediflow.lab.domain.model.LabCriticalNoticeStatus;
import com.mediflow.lab.domain.model.LabFlag;

@Repository
public class JdbcLabCriticalNoticeAdapter implements LabCriticalNoticeRepositoryPort {
    private static final String COLUMNS = """
            notice_id, test_id, result_version, field_code, label, value, unit, flag, patient_id, department_id, status,
            acknowledged_by, acknowledged_at, created_at""";
    private final JdbcTemplate jdbc;

    public JdbcLabCriticalNoticeAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(LabCriticalNotice n) {
        return jdbc.update("INSERT INTO lab_critical_notice(" + COLUMNS + """
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (test_id, result_version, field_code) DO NOTHING
                """, n.getNoticeId(), n.getTestId(), n.getResultVersion(), n.getFieldCode(), n.getLabel(), n.getValue(),
                n.getUnit(), n.getFlag().name(), n.getPatientId(), n.getDepartmentId(), n.getStatus().name(),
                n.getAcknowledgedBy(), ts(n.getAcknowledgedAt()), ts(n.getCreatedAt())) == 1;
    }

    @Override
    public Optional<LabCriticalNotice> findForUpdate(UUID noticeId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM lab_critical_notice WHERE notice_id = ? FOR UPDATE", this::row,
                noticeId).stream().findFirst();
    }

    @Override
    public void update(LabCriticalNotice n) {
        jdbc.update("UPDATE lab_critical_notice SET status = ?, acknowledged_by = ?, acknowledged_at = ? WHERE notice_id = ?",
                n.getStatus().name(), n.getAcknowledgedBy(), ts(n.getAcknowledgedAt()), n.getNoticeId());
    }

    @Override
    public List<LabCriticalNotice> find(String status, UUID departmentId) {
        return jdbc.query("SELECT " + COLUMNS + """
                 FROM lab_critical_notice
                WHERE status = ? AND (CAST(? AS uuid) IS NULL OR department_id = ?)
                ORDER BY created_at DESC LIMIT 200
                """, this::row, status, departmentId, departmentId);
    }

    private LabCriticalNotice row(ResultSet rs, int n) throws SQLException {
        Timestamp ack = rs.getTimestamp("acknowledged_at");
        return LabCriticalNotice.restore(rs.getObject("notice_id", UUID.class), rs.getObject("test_id", UUID.class),
                rs.getInt("result_version"), rs.getString("field_code"), rs.getString("label"), rs.getString("value"),
                rs.getString("unit"), LabFlag.valueOf(rs.getString("flag")), rs.getObject("patient_id", UUID.class),
                rs.getObject("department_id", UUID.class), LabCriticalNoticeStatus.valueOf(rs.getString("status")),
                rs.getObject("acknowledged_by", UUID.class), ack == null ? null : ack.toInstant(),
                rs.getTimestamp("created_at").toInstant());
    }

    private static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
