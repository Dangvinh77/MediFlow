package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.time.Instant;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.clinical.application.port.out.ExternalResultRepositoryPort.LabResultRevision;
import com.mediflow.clinical.domain.model.ExternalResultType;
import com.mediflow.clinical.infrastructure.persistence.adapter.ExternalResultPersistenceAdapter;

/** Clinical shows the newest released Lab revision whatever order the broker delivers them in. */
@Testcontainers(disabledWithoutDocker = true)
class LabResultRevisionOrderingTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static final Instant AT = Instant.parse("2026-09-28T06:00:00Z");
    static JdbcTemplate jdbc;
    static ExternalResultPersistenceAdapter adapter;

    @BeforeAll static void migrate() {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        adapter = new ExternalResultPersistenceAdapter(null, jdbc);
    }

    private UUID record() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO medical_record(record_id,patient_id,doctor_id,department_id,examination_date) VALUES (?,?,?,?,?)",
                id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Date.valueOf("2026-09-28"));
        return id;
    }

    private String current(UUID recordId) {
        return adapter.findByRecord(recordId).get(0).summary();
    }

    @Test void secondThenThirdShowsTheThird() {
        UUID recordId = record(), labId = UUID.randomUUID();
        assertThat(adapter.attach(recordId, ExternalResultType.LAB, labId, "v1", AT)).isTrue();
        assertThat(adapter.attachLabRevision(recordId, labId, 2, "v2", "sửa 1", AT.plusSeconds(60))).isTrue();
        assertThat(adapter.attachLabRevision(recordId, labId, 3, "v3", "sửa 2", AT.plusSeconds(120))).isTrue();
        assertThat(current(recordId)).isEqualTo("v3");
        assertThat(adapter.findByRecord(recordId)).hasSize(1);
        assertThat(adapter.findByRecord(recordId).get(0).attachedAt()).isEqualTo(AT);
        assertThat(adapter.labResultRevisions(recordId, labId)).extracting(LabResultRevision::resultVersion)
                .containsExactly(1, 2, 3);
    }

    @Test void thirdThenSecondKeepsTheThird() {
        UUID recordId = record(), labId = UUID.randomUUID();
        adapter.attach(recordId, ExternalResultType.LAB, labId, "v1", AT);
        adapter.attachLabRevision(recordId, labId, 3, "v3", "sửa 2", AT.plusSeconds(120));
        assertThat(adapter.attachLabRevision(recordId, labId, 2, "v2", "sửa 1", AT.plusSeconds(60))).isTrue();
        assertThat(current(recordId)).isEqualTo("v3");
        assertThat(adapter.labResultRevisions(recordId, labId)).extracting(LabResultRevision::summary)
                .containsExactly("v1", "v2", "v3");
    }

    @Test void replayingTheSecondAddsNoRow() {
        UUID recordId = record(), labId = UUID.randomUUID();
        adapter.attach(recordId, ExternalResultType.LAB, labId, "v1", AT);
        adapter.attachLabRevision(recordId, labId, 2, "v2", "sửa 1", AT.plusSeconds(60));
        assertThat(adapter.attachLabRevision(recordId, labId, 2, "v2", "sửa 1", AT.plusSeconds(60))).isFalse();
        assertThat(adapter.labResultRevisions(recordId, labId)).hasSize(2);
        assertThat(adapter.findByRecord(recordId)).hasSize(1);
    }

    @Test void originalArrivingAfterTheAmendmentDoesNotWinOrFail() {
        UUID recordId = record(), labId = UUID.randomUUID();
        adapter.attachLabRevision(recordId, labId, 2, "v2", "sửa 1", AT.plusSeconds(60));
        assertThat(adapter.attach(recordId, ExternalResultType.LAB, labId, "v1", AT)).isTrue();
        assertThat(current(recordId)).isEqualTo("v2");
        assertThat(adapter.labResultRevisions(recordId, labId)).extracting(LabResultRevision::amendmentReason)
                .containsExactly(null, "sửa 1");
    }

    @Test void lateOriginalRestoresTheEarliestSourceTime() {
        UUID recordId = record(), labId = UUID.randomUUID();
        adapter.attachLabRevision(recordId, labId, 2, "v2", "sửa 1", AT.plusSeconds(60));
        adapter.attach(recordId, ExternalResultType.LAB, labId, "v1", AT);
        assertThat(adapter.findByRecord(recordId).get(0).attachedAt()).isEqualTo(AT);
        // A replayed amendment never moves the time forward again.
        adapter.attachLabRevision(recordId, labId, 2, "v2", "sửa 1", AT.plusSeconds(60));
        assertThat(adapter.findByRecord(recordId).get(0).attachedAt()).isEqualTo(AT);
        assertThat(current(recordId)).isEqualTo("v2");
    }
}
