package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.pharmacy.application.port.out.AdmissionMedicationContextPort;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import com.mediflow.pharmacy.domain.model.AdmissionMedicationContext;

/** All methods join one projection/dispense transaction; a claim cannot commit on its own. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class AdmissionMedicationContextPersistenceAdapter implements AdmissionMedicationContextPort {
    private final JdbcTemplate jdbc;

    public AdmissionMedicationContextPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean claim(UUID eventId, String fingerprint) {
        int inserted = jdbc.update("""
                INSERT INTO admission_medication_event(event_id, event_fingerprint) VALUES (?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, eventId, fingerprint);
        String current = jdbc.queryForObject(
                "SELECT event_fingerprint FROM admission_medication_event WHERE event_id = ?",
                String.class, eventId);
        if (!fingerprint.equals(current)) {
            throw new PrescriptionRuleException("ADMISSION_EVENT_CONFLICT", "Admission event payload changed");
        }
        return inserted == 1;
    }

    @Override
    public AdmissionMedicationContext lockOrCreate(UUID admissionId, UUID patientId) {
        jdbc.update("""
                INSERT INTO admission_medication_context(admission_id, patient_id) VALUES (?, ?)
                ON CONFLICT (admission_id) DO NOTHING
                """, admissionId, patientId);
        return jdbc.queryForObject("SELECT * FROM admission_medication_context WHERE admission_id = ? FOR UPDATE",
                (row, index) -> new AdmissionMedicationContext(
                        row.getObject("admission_id", UUID.class), row.getObject("patient_id", UUID.class),
                        row.getObject("department_id", UUID.class), instant(row.getString("source_started_at")),
                        row.getString("started_fingerprint"), instant(row.getString("source_closed_at")),
                        row.getString("closed_fingerprint"), row.getLong("version")), admissionId);
    }

    @Override
    public void save(AdmissionMedicationContext context) {
        int changed = jdbc.update("""
                UPDATE admission_medication_context SET department_id = ?, started_at = ?, source_started_at = ?,
                    started_fingerprint = ?, closed_at = ?, source_closed_at = ?, closed_fingerprint = ?, version = ?
                WHERE admission_id = ? AND patient_id = ? AND version = ?
                """, context.departmentId(), timestamp(context.startedAt()), sourceInstant(context.startedAt()), context.startedFingerprint(),
                timestamp(context.closedAt()), sourceInstant(context.closedAt()), context.closedFingerprint(), context.version(),
                context.admissionId(), context.patientId(), context.version() - 1);
        if (changed != 1) {
            throw new PrescriptionRuleException("ADMISSION_CONTEXT_STALE", "Admission context version changed");
        }
    }

    private static Instant instant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    private static String sourceInstant(Instant value) {
        return value == null ? null : value.toString();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
