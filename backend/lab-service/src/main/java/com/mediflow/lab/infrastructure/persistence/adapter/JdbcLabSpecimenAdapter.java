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

import com.mediflow.lab.application.port.out.LabSpecimenRepositoryPort;
import com.mediflow.lab.domain.model.LabSpecimen;
import com.mediflow.lab.domain.model.LabSpecimenDecision;

@Repository
public class JdbcLabSpecimenAdapter implements LabSpecimenRepositoryPort {
    private static final String COLUMNS = """
            s.specimen_id, s.accession, s.patient_id, s.specimen_type, s.collected_by, s.collected_at, s.received_by,
            s.received_at, s.decision, s.rejection_reason, s.replaces_specimen_id""";
    private final JdbcTemplate jdbc;

    public JdbcLabSpecimenAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LabSpecimen> findById(UUID specimenId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM lab_specimen s WHERE s.specimen_id = ?", this::row, specimenId)
                .stream().findFirst();
    }

    @Override
    public Optional<LabSpecimen> findByIdForUpdate(UUID specimenId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM lab_specimen s WHERE s.specimen_id = ? FOR UPDATE", this::row,
                specimenId).stream().findFirst();
    }

    @Override
    public List<LabSpecimen> forTest(UUID testId) {
        return jdbc.query("SELECT " + COLUMNS + """
                 FROM lab_specimen s JOIN lab_specimen_test t ON t.specimen_id = s.specimen_id
                WHERE t.test_id = ? ORDER BY s.collected_at, s.accession
                """, this::row, testId);
    }

    @Override
    public boolean hasAcceptedSpecimen(UUID testId) {
        Boolean found = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM lab_specimen s JOIN lab_specimen_test t ON t.specimen_id = s.specimen_id
                               WHERE t.test_id = ? AND s.decision = 'ACCEPTED')
                """, Boolean.class, testId);
        return Boolean.TRUE.equals(found);
    }

    @Override
    public void insert(LabSpecimen s) {
        jdbc.update("""
                INSERT INTO lab_specimen(specimen_id, accession, patient_id, specimen_type, collected_by, collected_at,
                                         received_by, received_at, decision, rejection_reason, replaces_specimen_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, s.getSpecimenId(), s.getAccession(), s.getPatientId(), s.getSpecimenType(), s.getCollectedBy(),
                ts(s.getCollectedAt()), s.getReceivedBy(), ts(s.getReceivedAt()),
                s.getDecision() == null ? null : s.getDecision().name(), s.getRejectionReason(),
                s.getReplacesSpecimenId());
        int position = 0;
        for (UUID testId : s.getTestIds()) {
            jdbc.update("INSERT INTO lab_specimen_test(specimen_id, test_id, position) VALUES (?,?,?)",
                    s.getSpecimenId(), testId, ++position);
        }
    }

    @Override
    public void update(LabSpecimen s) {
        jdbc.update("""
                UPDATE lab_specimen SET received_by = ?, received_at = ?, decision = ?, rejection_reason = ?
                WHERE specimen_id = ?
                """, s.getReceivedBy(), ts(s.getReceivedAt()), s.getDecision() == null ? null : s.getDecision().name(),
                s.getRejectionReason(), s.getSpecimenId());
    }

    private LabSpecimen row(ResultSet rs, int n) throws SQLException {
        UUID id = rs.getObject("specimen_id", UUID.class);
        List<UUID> tests = jdbc.query("SELECT test_id FROM lab_specimen_test WHERE specimen_id = ? ORDER BY position",
                (r, i) -> r.getObject(1, UUID.class), id);
        String decision = rs.getString("decision");
        return LabSpecimen.restore(id, rs.getString("accession"), rs.getObject("patient_id", UUID.class),
                rs.getString("specimen_type"), tests, rs.getObject("replaces_specimen_id", UUID.class),
                rs.getObject("collected_by", UUID.class), instant(rs, "collected_at"),
                rs.getObject("received_by", UUID.class), instant(rs, "received_at"),
                decision == null ? null : LabSpecimenDecision.valueOf(decision), rs.getString("rejection_reason"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
