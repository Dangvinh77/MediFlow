package com.mediflow.inpatient.infrastructure.persistence.adapter;

import com.mediflow.inpatient.application.port.out.BedCapacityHistoryPort;
import com.mediflow.inpatient.domain.model.BedCapacitySnapshot;
import java.sql.Timestamp;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
@ConditionalOnProperty(name = "mediflow.inpatient.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class BedCapacityHistoryPersistenceAdapter implements BedCapacityHistoryPort {
    private final JdbcTemplate jdbcTemplate;

    public BedCapacityHistoryPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void append(BedCapacitySnapshot snapshot) {
        jdbcTemplate.update("""
                INSERT INTO lich_su_cong_suat_giuong (
                  capacity_history_id, bed_id, capacity_revision, department_id, ward_code,
                  bed_type, active, operational_status, capacity_eligible, effective_at,
                  actor_id, correlation_id, event_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, snapshot.capacityHistoryId(), snapshot.bedId(), snapshot.capacityRevision(),
                snapshot.departmentId(), snapshot.wardCode(), snapshot.bedType(), snapshot.active(),
                snapshot.operationalStatus().name(), snapshot.capacityEligible(),
                Timestamp.from(snapshot.effectiveAt()), snapshot.actorId(), snapshot.correlationId(),
                snapshot.eventId());
    }
}
