package com.mediflow.inpatient.infrastructure.persistence.adapter;

import com.mediflow.inpatient.application.port.out.PlacementAuthorityRepositoryPort;
import com.mediflow.inpatient.domain.model.PlacementAuthoritySnapshot;
import com.mediflow.inpatient.domain.model.enums.AdmissionStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "mediflow.inpatient.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class PlacementAuthorityPersistenceAdapter implements PlacementAuthorityRepositoryPort {
    private final JdbcTemplate jdbc;

    public PlacementAuthorityPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PlacementAuthoritySnapshot> findByAdmissionId(UUID admissionId) {
        try {
            return jdbc.query("""
                SELECT a.admission_id, a.patient_id, a.status, a.medically_discharged_at,
                       a.closed_at, a.cancelled_at, a.placement_revision,
                       x.assignment_id, x.bed_id, b.department_id AS placement_department_id,
                       b.ward_code, x.assigned_at
                FROM dot_noi_tru a
                LEFT JOIN phan_giuong x
                  ON x.admission_id = a.admission_id AND x.status = 'ACTIVE'
                LEFT JOIN giuong_benh b ON b.bed_id = x.bed_id
                WHERE a.admission_id = ?
                """, (row, index) -> new PlacementAuthoritySnapshot(
                row.getObject("admission_id", UUID.class),
                row.getObject("patient_id", UUID.class),
                AdmissionStatus.valueOf(row.getString("status")),
                instant(row.getTimestamp("medically_discharged_at")),
                instant(row.getTimestamp("closed_at")),
                instant(row.getTimestamp("cancelled_at")),
                row.getLong("placement_revision"),
                row.getObject("assignment_id", UUID.class),
                row.getObject("bed_id", UUID.class),
                row.getObject("placement_department_id", UUID.class),
                row.getString("ward_code"),
                instant(row.getTimestamp("assigned_at"))), admissionId).stream().findFirst();
        } catch (DataAccessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DataRetrievalFailureException(
                    "Inpatient placement authority row is invalid", exception);
        }
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
