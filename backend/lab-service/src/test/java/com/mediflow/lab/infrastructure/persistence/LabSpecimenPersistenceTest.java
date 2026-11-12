package com.mediflow.lab.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.lab.domain.model.LabSpecimen;
import com.mediflow.lab.domain.model.LabSpecimenDecision;
import com.mediflow.lab.infrastructure.persistence.adapter.JdbcLabSpecimenAdapter;

@Testcontainers(disabledWithoutDocker = true)
class LabSpecimenPersistenceTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static final Instant AT = Instant.parse("2026-11-11T04:00:00Z");
    static JdbcTemplate jdbc;
    static JdbcLabSpecimenAdapter adapter;

    @BeforeAll static void migrate() {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        adapter = new JdbcLabSpecimenAdapter(jdbc);
    }

    UUID test(UUID patient) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO lab_test(test_id, record_id, patient_id, requesting_department_id, test_type, requested_date,
                                     status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Glucose', DATE '2026-11-11', 'IN_PROGRESS', now(), now())
                """, id, UUID.randomUUID(), patient, UUID.randomUUID());
        return id;
    }

    @Test void acceptedSpecimenUnlocksSubmissionForEveryLinkedTest() {
        UUID patient = UUID.randomUUID();
        UUID a = test(patient);
        UUID b = test(patient);
        LabSpecimen specimen = LabSpecimen.collect(patient, "BLOOD", List.of(a, b), null, UUID.randomUUID(), AT);
        adapter.insert(specimen);
        assertThat(adapter.hasAcceptedSpecimen(a)).isFalse();

        LabSpecimen stored = adapter.findByIdForUpdate(specimen.getSpecimenId()).orElseThrow();
        stored.decide(LabSpecimenDecision.ACCEPTED, null, UUID.randomUUID(), AT);
        adapter.update(stored);

        assertThat(adapter.hasAcceptedSpecimen(a)).isTrue();
        assertThat(adapter.hasAcceptedSpecimen(b)).isTrue();
        assertThat(adapter.findById(specimen.getSpecimenId()).orElseThrow().getTestIds()).containsExactly(a, b);
        assertThat(adapter.forTest(a)).extracting(LabSpecimen::getAccession).containsExactly(specimen.getAccession());
    }

    @Test void recollectionKeepsTheRejectedSpecimen() {
        UUID patient = UUID.randomUUID();
        UUID a = test(patient);
        LabSpecimen rejected = LabSpecimen.collect(patient, "URINE", List.of(a), null, UUID.randomUUID(), AT);
        rejected.decide(LabSpecimenDecision.REJECTED, "Không đủ thể tích", UUID.randomUUID(), AT);
        adapter.insert(rejected);
        LabSpecimen again = LabSpecimen.collect(patient, "URINE", List.of(a), rejected.getSpecimenId(),
                UUID.randomUUID(), AT.plusSeconds(60));
        adapter.insert(again);

        assertThat(adapter.forTest(a)).extracting(LabSpecimen::getSpecimenId)
                .containsExactly(rejected.getSpecimenId(), again.getSpecimenId());
        assertThat(adapter.findById(again.getSpecimenId()).orElseThrow().getReplacesSpecimenId())
                .isEqualTo(rejected.getSpecimenId());
        assertThat(adapter.hasAcceptedSpecimen(a)).isFalse();
    }
}
