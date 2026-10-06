package com.mediflow.inpatient.infrastructure.persistence.adapter;

import com.mediflow.inpatient.application.port.out.AdmissionAuthorityRepositoryPort;
import com.mediflow.inpatient.domain.model.AdmissionAuthoritySnapshot;
import com.mediflow.inpatient.domain.model.enums.AdmissionStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** One statement gives an internally coherent projection of the authoritative admission row. */
@Repository
@ConditionalOnProperty(name="mediflow.inpatient.persistence.enabled", havingValue="true", matchIfMissing=true)
public class AdmissionAuthorityPersistenceAdapter implements AdmissionAuthorityRepositoryPort {
    private final JdbcTemplate jdbc;
    public AdmissionAuthorityPersistenceAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<AdmissionAuthoritySnapshot> findById(UUID id) {
        return jdbc.query("""
                SELECT admission_id,patient_id,department_id,source_record_id,status,version,
                       medically_discharged_at,closed_at,cancelled_at
                FROM dot_noi_tru WHERE admission_id=?
                """, (row,index) -> new AdmissionAuthoritySnapshot(row.getObject("admission_id",UUID.class),
                row.getObject("patient_id",UUID.class),row.getObject("department_id",UUID.class),
                row.getObject("source_record_id",UUID.class),AdmissionStatus.valueOf(row.getString("status")),
                row.getLong("version"),instant(row.getTimestamp("medically_discharged_at")),
                instant(row.getTimestamp("closed_at")),instant(row.getTimestamp("cancelled_at"))),id)
                .stream().findFirst();
    }

    private static Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }
}
