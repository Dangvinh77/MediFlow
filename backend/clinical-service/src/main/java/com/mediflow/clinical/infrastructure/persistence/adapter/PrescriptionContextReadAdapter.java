package com.mediflow.clinical.infrastructure.persistence.adapter;

import com.mediflow.clinical.application.dto.response.PrescriptionContextDTO;
import com.mediflow.clinical.application.port.out.PrescriptionContextReadPort;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(name = "mediflow.clinical.prescription-context-lookup.enabled", havingValue = "true")
public class PrescriptionContextReadAdapter implements PrescriptionContextReadPort {
    private final JdbcTemplate jdbc;
    public PrescriptionContextReadAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional(readOnly = true, timeout = 5)
    public PrescriptionContextDTO find(UUID recordId) {
        Objects.requireNonNull(recordId);
        // One MVCC statement. Only Clinical selects its own appointment-backed/walk-in episode.
        return jdbc.queryForObject("""
                SELECT r.record_id,r.patient_id,r.doctor_id,r.department_id,r.status,r.disposition,
                       r.appointment_id,a.patient_id AS appointment_patient,a.department_id AS appointment_department,
                       statement_timestamp() AS observed_at
                FROM (SELECT ?::uuid AS id) requested
                LEFT JOIN medical_record r ON r.record_id=requested.id
                LEFT JOIN appointment a ON a.appointment_id=r.appointment_id
                """, (rs, row) -> {
            var observed = rs.getTimestamp("observed_at").toInstant();
            if (rs.getObject("record_id") == null)
                return new PrescriptionContextDTO(false, recordId, null, null, null, null, null, null, null, observed);
            UUID patient = rs.getObject("patient_id", UUID.class), department = rs.getObject("department_id", UUID.class);
            UUID appointment = rs.getObject("appointment_id", UUID.class);
            if (appointment != null && (!patient.equals(rs.getObject("appointment_patient", UUID.class))
                    || !department.equals(rs.getObject("appointment_department", UUID.class))))
                throw new DataIntegrityViolationException("Clinical relationship is unverifiable");
            return new PrescriptionContextDTO(true, recordId, patient, rs.getObject("doctor_id", UUID.class),
                    department, "OUTPATIENT_VISIT", appointment == null ? recordId : appointment,
                    rs.getString("status"), rs.getString("disposition"), observed);
        }, recordId);
    }
}
