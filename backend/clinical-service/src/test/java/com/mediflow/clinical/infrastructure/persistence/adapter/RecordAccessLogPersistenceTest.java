package com.mediflow.clinical.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.clinical.domain.model.RecordAccessDeclaration;
import com.mediflow.clinical.domain.model.RecordAccessLogEntry;

/** Verifies access-log durability and declaration expiry against the Clinical PostgreSQL migrations. */
@Testcontainers(disabledWithoutDocker = true)
class RecordAccessLogPersistenceTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager transactions;
    private static RecordAccessPersistenceAdapter adapter;

    @BeforeAll
    static void migrate() {
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        transactions = new DataSourceTransactionManager(dataSource);
        adapter = new RecordAccessPersistenceAdapter(jdbc, transactions);
    }

    @Test
    void deniedEntrySurvivesCallerRollback() {
        UUID patientId = UUID.randomUUID();

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            adapter.append(new RecordAccessLogEntry(UUID.randomUUID(), UUID.randomUUID(), "DOCTOR", patientId, null,
                    "READ", "DENIED", null, null, "corr-1", Instant.now()));
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM record_access_log WHERE patient_id = ?",
                Long.class, patientId)).isEqualTo(1L);
    }

    @Test
    void findActiveExcludesExpiryInstant() {
        UUID accountId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        Instant expiresAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        adapter.save(new RecordAccessDeclaration(UUID.randomUUID(), accountId, UUID.randomUUID(), "DOCTOR", patientId,
                "CONSULTATION", null, expiresAt.minusSeconds(3600), expiresAt));

        assertThat(adapter.findActive(accountId, patientId, expiresAt)).isEmpty();
        assertThat(adapter.findActive(accountId, patientId, expiresAt.minusSeconds(1)))
                .get().extracting(RecordAccessDeclaration::purpose).isEqualTo("CONSULTATION");
    }
}
