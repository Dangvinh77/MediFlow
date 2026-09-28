package com.mediflow.surgery.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class SurgerySchemaIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("surgery_test")
            .withUsername("surgery")
            .withPassword("surgery");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @Test
    void freshMigrationCreatesCaseReliabilityAndResourceTables() {
        assertThat(jdbc.queryForObject("SELECT to_regclass('surgery_case')", String.class))
                .isEqualTo("surgery_case");
        assertThat(jdbc.queryForObject("SELECT to_regclass('surgery_inbox')", String.class))
                .isEqualTo("surgery_inbox");
        assertThat(jdbc.queryForObject("SELECT to_regclass('surgery_outbox')", String.class))
                .isEqualTo("surgery_outbox");
        assertThat(jdbc.queryForObject("SELECT to_regclass('surgery_resource_mutex')", String.class))
                .isEqualTo("surgery_resource_mutex");
    }

    @Test
    void cancelledCaseRequiresRealReasonAndActorWithoutSqlNullEscape() {
        UUID caseId = insertCase();
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE surgery_case SET status = 'CANCELLED',
                cancelled_at = ?, cancellation_actor_type = 'HUMAN',
                cancellation_account_id = ? WHERE surgery_case_id = ?
                """, Timestamp.from(Instant.now()), UUID.randomUUID(), caseId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM surgery_case WHERE surgery_case_id = ?",
                String.class, caseId)).isEqualTo("REQUESTED");
    }

    @Test
    void oneActiveConsentPerTypeButRevokedHistoryCanRemain() {
        UUID caseId = insertCase();
        UUID consentId = UUID.randomUUID();
        insertConsent(consentId, caseId, "SURGERY");
        assertThatThrownBy(() -> insertConsent(UUID.randomUUID(), caseId, "SURGERY"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("""
                UPDATE surgery_consent SET status = 'REVOKED',
                revoked_at = ?, revocation_reason = 'Signer withdrew'
                WHERE consent_id = ?
                """, Timestamp.from(Instant.now()), consentId);
        insertConsent(UUID.randomUUID(), caseId, "SURGERY");
        insertConsent(UUID.randomUUID(), caseId, "ANESTHESIA");
        jdbc.update("""
                UPDATE surgery_consent SET signer_type = 'AUTHORIZED_REPRESENTATIVE'
                WHERE consent_id = ?
                """, consentId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_consent WHERE surgery_case_id = ?",
                Integer.class, caseId)).isEqualTo(3);
    }

    @Test
    void resultAndScheduleGuardsRejectDuplicateOrInvalidRows() {
        UUID caseId = insertCase();
        UUID resultId = UUID.randomUUID();
        Instant start = Instant.parse("2026-09-28T08:00:00Z");
        insertResult(resultId, caseId, start, start.plusSeconds(600));
        assertThatThrownBy(() -> insertResult(UUID.randomUUID(), caseId, start, start.plusSeconds(900)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO surgery_performed_item
                (performed_item_id, result_id, item_code, price_code, quantity)
                VALUES (?, ?, 'ITEM', 'PRICE', 0)
                """, UUID.randomUUID(), resultId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO surgery_schedule
                (schedule_id, surgery_case_id, revision, room_id, starts_at, ends_at, status)
                VALUES (?, ?, 1, ?, ?, ?, 'DRAFT')
                """, UUID.randomUUID(), caseId, UUID.randomUUID(),
                Timestamp.from(start), Timestamp.from(start)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static UUID insertCase() {
        UUID caseId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO surgery_case
                (surgery_case_id, surgery_request_id, episode_type,
                 episode_id, patient_id, department_id, requested_by,
                 procedure_code, indication, priority, status, requested_at)
                VALUES (?, ?, 'OUTPATIENT_VISIT', ?, ?, ?, ?, 'PROC', 'Indication',
                        'ROUTINE', 'REQUESTED', ?)
                """, caseId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), Timestamp.from(Instant.now()));
        return caseId;
    }

    private static void insertConsent(UUID consentId, UUID caseId, String type) {
        jdbc.update("""
                INSERT INTO surgery_consent
                (consent_id, surgery_case_id, consent_type, signer_id,
                 signer_type, status, signed_at)
                VALUES (?, ?, ?, ?, 'PATIENT', 'ACTIVE', ?)
                """, consentId, caseId, type, UUID.randomUUID(), Timestamp.from(Instant.now()));
    }

    private static void insertResult(UUID resultId, UUID caseId, Instant start, Instant end) {
        jdbc.update("""
                INSERT INTO surgery_result
                (result_id, surgery_case_id, procedure_code, method_code,
                 treatment_outcome_code, actual_start_at,
                 actual_end_at, recorded_at,
                 actor_type, account_id, correlation_id)
                VALUES (?, ?, 'PROC', 'METHOD', 'OUTCOME', ?, ?, ?, 'HUMAN', ?, 'schema-test')
                """, resultId, caseId, Timestamp.from(start), Timestamp.from(end),
                Timestamp.from(end), UUID.randomUUID());
    }
}
