package com.mediflow.inpatient.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.inpatient.application.dto.event.BedCapacityChangedEvent;
import com.mediflow.inpatient.application.dto.event.DomainEventEnvelope;
import com.mediflow.inpatient.domain.model.BedCapacitySnapshot;
import com.mediflow.inpatient.domain.model.enums.BedStatus;
import com.mediflow.inpatient.infrastructure.messaging.InpatientEventWireMapper;
import com.mediflow.inpatient.infrastructure.persistence.adapter.BedCapacityHistoryPersistenceAdapter;
import com.mediflow.inpatient.infrastructure.persistence.adapter.InpatientOutboxPersistenceAdapter;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class BedCapacityOutboxIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void historyAndOutboxPersistTheSameBedCapacityEventIdentity() {
        Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()).load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var histories = new BedCapacityHistoryPersistenceAdapter(jdbc);
        var outbox = new InpatientOutboxPersistenceAdapter(
                jdbc, objectMapper, new InpatientEventWireMapper());
        UUID bedId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant effectiveAt = Instant.parse("2026-10-12T04:00:00Z");
        String correlationId = "capacity-" + eventId;
        jdbc.update("""
                INSERT INTO giuong_benh(bed_id,department_id,ward_code,room_code,bed_code,bed_type,
                status,active,version,capacity_revision,created_at,updated_at)
                VALUES(?,?,'WARD-A','101','01','STANDARD','OUT_OF_SERVICE',true,0,2,?,?)
                """, bedId, departmentId, effectiveAt, effectiveAt);
        var snapshot = new BedCapacitySnapshot(UUID.randomUUID(), eventId, bedId, 2,
                departmentId, "WARD-A", "STANDARD", true, BedStatus.OUT_OF_SERVICE,
                false, effectiveAt, actorId, correlationId);

        histories.append(snapshot);
        outbox.append("BED", bedId, new DomainEventEnvelope<>(eventId, "bed.capacity.changed", 1,
                effectiveAt, correlationId, "inpatient-service", BedCapacityChangedEvent.from(snapshot)));

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM lich_su_cong_suat_giuong h
                JOIN su_kien_outbox_noi_tru o ON o.event_id = h.event_id
                WHERE h.bed_id = ? AND h.capacity_revision = 2
                  AND o.aggregate_type = 'BED' AND o.aggregate_id = ?
                  AND o.payload ->> 'eventType' = 'bed.capacity.changed'
                  AND o.payload #>> '{payload,capacityEligible}' = 'false'
                """, Integer.class, bedId, bedId)).isEqualTo(1);
    }
}
