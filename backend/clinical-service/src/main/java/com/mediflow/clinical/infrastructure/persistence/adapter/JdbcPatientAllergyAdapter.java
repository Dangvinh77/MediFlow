package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.port.out.PatientAllergyRepositoryPort;
import com.mediflow.clinical.domain.model.AllergySeverity;
import com.mediflow.clinical.domain.model.AllergyStatus;
import com.mediflow.clinical.domain.model.PatientAllergy;

@Component
public class JdbcPatientAllergyAdapter implements PatientAllergyRepositoryPort {
    private static final String COLUMNS = "allergy_id, patient_id, substance, reaction, severity, status, recorded_by_staff_id, "
            + "recorded_at, source_record_id, status_reason, status_changed_by_staff_id, status_changed_at";
    private final JdbcTemplate jdbc;

    public JdbcPatientAllergyAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(PatientAllergy a) {
        jdbc.update("INSERT INTO patient_allergy(" + COLUMNS + ", substance_key) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                a.getAllergyId(), a.getPatientId(), a.getSubstance(), a.getReaction(), a.getSeverity().name(),
                a.getStatus().name(), a.getRecordedByStaffId(), Timestamp.from(a.getRecordedAt()), a.getSourceRecordId(),
                a.getStatusReason(), a.getStatusChangedByStaffId(),
                a.getStatusChangedAt() == null ? null : Timestamp.from(a.getStatusChangedAt()), a.substanceKey());
    }

    @Override
    public Optional<PatientAllergy> findByIdForUpdate(UUID allergyId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM patient_allergy WHERE allergy_id = ? FOR UPDATE",
                JdbcPatientAllergyAdapter::row, allergyId).stream().findFirst();
    }

    @Override
    public void changeStatus(PatientAllergy a) {
        jdbc.update("""
                UPDATE patient_allergy SET status = ?, status_reason = ?, status_changed_by_staff_id = ?, status_changed_at = ?
                WHERE allergy_id = ? AND status = 'ACTIVE'
                """, a.getStatus().name(), a.getStatusReason(), a.getStatusChangedByStaffId(),
                Timestamp.from(a.getStatusChangedAt()), a.getAllergyId());
    }

    @Override
    public List<PatientAllergy> findByPatient(UUID patientId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM patient_allergy WHERE patient_id = ? ORDER BY recorded_at DESC, allergy_id",
                JdbcPatientAllergyAdapter::row, patientId);
    }

    @Override
    public Optional<PatientAllergy> findActiveByKey(UUID patientId, String substanceKey) {
        return jdbc.query("SELECT " + COLUMNS + " FROM patient_allergy WHERE patient_id = ? AND substance_key = ? AND status = 'ACTIVE'",
                JdbcPatientAllergyAdapter::row, patientId, substanceKey).stream().findFirst();
    }

    private static PatientAllergy row(ResultSet rs, int n) throws SQLException {
        Timestamp changed = rs.getTimestamp("status_changed_at");
        return PatientAllergy.restore(rs.getObject("allergy_id", UUID.class), rs.getObject("patient_id", UUID.class),
                rs.getString("substance"), rs.getString("reaction"), AllergySeverity.valueOf(rs.getString("severity")),
                AllergyStatus.valueOf(rs.getString("status")), rs.getObject("recorded_by_staff_id", UUID.class),
                rs.getTimestamp("recorded_at").toInstant(), rs.getObject("source_record_id", UUID.class),
                rs.getString("status_reason"), rs.getObject("status_changed_by_staff_id", UUID.class),
                changed == null ? null : changed.toInstant());
    }
}
