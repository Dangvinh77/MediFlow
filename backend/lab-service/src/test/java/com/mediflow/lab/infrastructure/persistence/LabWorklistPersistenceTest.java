package com.mediflow.lab.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

import com.mediflow.lab.application.port.out.LabWorklistPort.WorklistRow;
import com.mediflow.lab.domain.model.LabReport;
import com.mediflow.lab.domain.model.LabSpecimen;
import com.mediflow.lab.domain.model.LabSpecimenDecision;
import com.mediflow.lab.infrastructure.persistence.adapter.JdbcLabReportAdapter;
import com.mediflow.lab.infrastructure.persistence.adapter.JdbcLabSpecimenAdapter;
import com.mediflow.lab.infrastructure.persistence.adapter.JdbcLabWorklistAdapter;

/** LAB-WF-01: what the laboratory still has to do, with service, group, specimen, report and due time. */
@Testcontainers(disabledWithoutDocker = true)
class LabWorklistPersistenceTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static final Instant CREATED = Instant.parse("2026-11-11T01:00:00Z");
    static JdbcTemplate jdbc;
    static JdbcLabWorklistAdapter worklist;

    @BeforeAll static void migrate() {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        worklist = new JdbcLabWorklistAdapter(jdbc);
    }

    UUID test(UUID patient, String status, String serviceCode) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO lab_test(test_id, record_id, patient_id, requesting_department_id, test_type, requested_date,
                                     status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Glucose', DATE '2026-11-11', ?, ?, ?)
                """, id, UUID.randomUUID(), patient, UUID.randomUUID(), status, java.sql.Timestamp.from(CREATED),
                java.sql.Timestamp.from(CREATED));
        if (serviceCode != null) {
            jdbc.update("""
                    INSERT INTO lab_test_template_binding(test_id, service_code, template_code, template_version)
                    SELECT ?, c.service_code, c.template_code, max(t.version)
                      FROM lab_service_catalog c JOIN lab_result_template t ON t.template_code = c.template_code
                     WHERE c.service_code = ? GROUP BY c.service_code, c.template_code
                    """, id, serviceCode);
        }
        return id;
    }

    WorklistRow row(UUID testId) {
        return worklist.open().stream().filter(r -> r.testId().equals(testId)).findFirst().orElse(null);
    }

    @Test void inProgressTestShowsServiceSpecimenReportAndDueTime() {
        UUID patient = UUID.randomUUID();
        UUID testId = test(patient, "IN_PROGRESS", "GLU");
        LabSpecimen specimen = LabSpecimen.collect(patient, "SERUM", List.of(testId), null, UUID.randomUUID(), CREATED);
        specimen.decide(LabSpecimenDecision.ACCEPTED, null, UUID.randomUUID(), CREATED);
        new JdbcLabSpecimenAdapter(jdbc).insert(specimen);
        new JdbcLabReportAdapter(jdbc).insert(LabReport.open(testId, 1, "GLU", 1, null, CREATED));

        WorklistRow row = row(testId);

        assertThat(row.serviceCode()).isEqualTo("GLU");
        assertThat(row.serviceName()).isEqualTo("Glucose máu");
        assertThat(row.displayGroup()).isEqualTo("BIOCHEMISTRY");
        assertThat(row.specimenStatus()).isEqualTo("ACCEPTED");
        assertThat(row.reportStatus()).isEqualTo("DRAFT");
        assertThat(row.dueAt()).isEqualTo(CREATED.plus(4, ChronoUnit.HOURS));
    }

    @Test void untouchedTestHasNoSpecimenNorReportAndNoCatalogDueTime() {
        UUID testId = test(UUID.randomUUID(), "PENDING", null);
        WorklistRow row = row(testId);
        assertThat(row.specimenStatus()).isEqualTo("NOT_COLLECTED");
        assertThat(row.reportStatus()).isNull();
        assertThat(row.serviceCode()).isNull();
        assertThat(row.dueAt()).isNull();
    }

    @Test void finishedTestsLeaveTheWorklist() {
        UUID completed = test(UUID.randomUUID(), "COMPLETED", "GLU");
        UUID cancelled = test(UUID.randomUUID(), "CANCELLED", null);
        assertThat(row(completed)).isNull();
        assertThat(row(cancelled)).isNull();
    }
}
