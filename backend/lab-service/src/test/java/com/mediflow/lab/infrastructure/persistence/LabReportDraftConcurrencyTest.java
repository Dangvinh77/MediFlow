package com.mediflow.lab.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
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

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.lab.domain.model.LabReport;
import com.mediflow.lab.domain.model.LabValueInput;
import com.mediflow.lab.infrastructure.persistence.adapter.JdbcLabReportAdapter;

/** Two technicians saving the same draft revision: the second write loses with LAB_REPORT_STALE. */
@Testcontainers(disabledWithoutDocker = true)
class LabReportDraftConcurrencyTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static final Instant AT = Instant.parse("2026-11-11T02:00:00Z");
    static JdbcTemplate jdbc;
    static JdbcLabReportAdapter adapter;

    @BeforeAll static void migrate() {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        adapter = new JdbcLabReportAdapter(jdbc);
    }

    UUID test() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO lab_test(test_id, record_id, patient_id, requesting_department_id, test_type, requested_date,
                                     status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Glucose', DATE '2026-11-11', 'IN_PROGRESS', now(), now())
                """, id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        return id;
    }

    @Test void secondWriterOfTheSameRevisionIsStale() {
        UUID testId = test();
        adapter.insert(LabReport.open(testId, 1, "GLU", 1, null, AT));
        LabReport first = adapter.current(testId).orElseThrow();
        LabReport second = adapter.current(testId).orElseThrow();
        first.saveDraft(0, List.of(new LabValueInput("GLU", null, "5.2", null)), null, LocalDate.of(2026, 11, 11),
                UUID.randomUUID(), AT);
        adapter.update(first, 0);
        second.saveDraft(0, List.of(new LabValueInput("GLU", null, "9.9", null)), null, null, UUID.randomUUID(), AT);
        assertThatThrownBy(() -> adapter.update(second, 0)).isInstanceOf(DuplicateResourceException.class)
                .extracting("code").isEqualTo("LAB_REPORT_STALE");
        LabReport stored = adapter.current(testId).orElseThrow();
        assertThat(stored.getDraftRevision()).isEqualTo(1);
        assertThat(stored.getValues()).extracting(LabValueInput::value).containsExactly("5.2");
        assertThat(stored.getPerformedDate()).isEqualTo(LocalDate.of(2026, 11, 11));
    }

    @Test void onlyOneOpenRevisionPerTest() {
        UUID testId = test();
        adapter.insert(LabReport.open(testId, 1, "GLU", 1, null, AT));
        assertThatThrownBy(() -> adapter.insert(LabReport.open(testId, 2, "GLU", 1, "x", AT)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void newTestsStayOnTheOldWorkflowUntilEntered() {
        UUID testId = test();
        assertThat(adapter.workflowVersion(testId)).isZero();
        adapter.enterWorkflow(testId);
        assertThat(adapter.workflowVersion(testId)).isOne();
    }
}
