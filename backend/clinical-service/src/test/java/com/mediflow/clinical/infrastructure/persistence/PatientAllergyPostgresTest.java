package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.clinical.domain.model.AllergySeverity;
import com.mediflow.clinical.domain.model.AllergyStatus;
import com.mediflow.clinical.domain.model.PatientAllergy;
import com.mediflow.clinical.infrastructure.persistence.adapter.JdbcPatientAllergyAdapter;

@Testcontainers(disabledWithoutDocker = true)
class PatientAllergyPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static final Instant AT = Instant.parse("2026-11-10T03:00:00Z");
    static JdbcTemplate jdbc;
    static JdbcPatientAllergyAdapter adapter;

    @BeforeAll static void migrate() {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        adapter = new JdbcPatientAllergyAdapter(jdbc);
    }

    @Test void roundTripAndOneActivePerSubstance() {
        UUID patient = UUID.randomUUID();
        PatientAllergy allergy = PatientAllergy.record(patient, "Penicillin", "Nổi mẩn", AllergySeverity.SEVERE, null,
                UUID.randomUUID(), AT);
        adapter.insert(allergy);
        assertThat(adapter.findActiveByKey(patient, PatientAllergy.key("PENICILLIN"))).isPresent();
        assertThatThrownBy(() -> adapter.insert(PatientAllergy.record(patient, " penicillin ", null, null, null,
                UUID.randomUUID(), AT))).isInstanceOf(DataIntegrityViolationException.class);

        PatientAllergy locked = adapter.findByIdForUpdate(allergy.getAllergyId()).orElseThrow();
        locked.changeStatus(AllergyStatus.RESOLVED, "Hết dị ứng", UUID.randomUUID(), AT);
        adapter.changeStatus(locked);
        assertThat(adapter.findActiveByKey(patient, PatientAllergy.key("Penicillin"))).isEmpty();
        adapter.insert(PatientAllergy.record(patient, "Penicillin", null, null, null, UUID.randomUUID(), AT));
        assertThat(adapter.findByPatient(patient)).hasSize(2);
        assertThat(adapter.findByPatient(patient)).filteredOn(a -> a.getAllergyId().equals(allergy.getAllergyId()))
                .singleElement().satisfies(a -> assertThat(a.getStatusReason()).isEqualTo("Hết dị ứng"));
    }

    @Test void allergyStatusColumnsAllOrNothing() {
        PatientAllergy allergy = PatientAllergy.record(UUID.randomUUID(), "Aspirin", null, null, null, UUID.randomUUID(), AT);
        adapter.insert(allergy);
        assertThatThrownBy(() -> jdbc.update("UPDATE patient_allergy SET status='RESOLVED' WHERE allergy_id=?",
                allergy.getAllergyId())).isInstanceOf(DataIntegrityViolationException.class);
    }
}
