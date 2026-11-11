package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.port.out.RecordHandoverRepositoryPort;
import com.mediflow.clinical.domain.model.RecordHandover;
import com.mediflow.clinical.domain.model.RecordHandoverStatus;

@Component
public class JdbcRecordHandoverAdapter implements RecordHandoverRepositoryPort {
    private static final String COLUMNS = "handover_id, record_id, from_doctor_id, to_doctor_id, reason, pending_items, "
            + "status, requested_at, accepted_at";
    private final JdbcTemplate jdbc;

    public JdbcRecordHandoverAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(RecordHandover h) {
        jdbc.update("INSERT INTO record_handover(" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?)", h.getHandoverId(),
                h.getRecordId(), h.getFromDoctorId(), h.getToDoctorId(), h.getReason(), h.getPendingItems(),
                h.getStatus().name(), Timestamp.from(h.getRequestedAt()),
                h.getAcceptedAt() == null ? null : Timestamp.from(h.getAcceptedAt()));
    }

    @Override
    public Optional<RecordHandover> findPending(UUID recordId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM record_handover WHERE record_id = ? AND status = 'PENDING'",
                JdbcRecordHandoverAdapter::row, recordId).stream().findFirst();
    }

    @Override
    public Optional<RecordHandover> findByIdForUpdate(UUID handoverId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM record_handover WHERE handover_id = ? FOR UPDATE",
                JdbcRecordHandoverAdapter::row, handoverId).stream().findFirst();
    }

    @Override
    public void accept(RecordHandover h) {
        jdbc.update("UPDATE record_handover SET status = ?, accepted_at = ? WHERE handover_id = ? AND status = 'PENDING'",
                h.getStatus().name(), Timestamp.from(h.getAcceptedAt()), h.getHandoverId());
    }

    @Override
    public List<RecordHandover> findByRecord(UUID recordId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM record_handover WHERE record_id = ? ORDER BY requested_at, handover_id",
                JdbcRecordHandoverAdapter::row, recordId);
    }

    private static RecordHandover row(ResultSet rs, int n) throws SQLException {
        Timestamp accepted = rs.getTimestamp("accepted_at");
        return RecordHandover.restore(rs.getObject("handover_id", UUID.class), rs.getObject("record_id", UUID.class),
                rs.getObject("from_doctor_id", UUID.class), rs.getObject("to_doctor_id", UUID.class),
                rs.getString("reason"), rs.getString("pending_items"), RecordHandoverStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("requested_at").toInstant(), accepted == null ? null : accepted.toInstant());
    }
}
