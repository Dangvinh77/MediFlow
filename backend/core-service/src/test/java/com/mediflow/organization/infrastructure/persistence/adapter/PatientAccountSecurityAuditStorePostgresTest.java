package com.mediflow.organization.infrastructure.persistence.adapter;

import com.mediflow.organization.application.port.out.PatientAccountSecurityAuditStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class PatientAccountSecurityAuditStorePostgresTest {
    private static final String SCHEMA = "patient_account_security_audit_test";
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static PatientAccountSecurityAuditStoreAdapter audit;
    private static DataSource dataSource;

    @BeforeAll
    static void migrateFreshOrganizationSchema() {
        String url = schemaUrl();
        Flyway flyway = Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .locations("classpath:db/organization-migration")
                .load();
        flyway.migrate();

        dataSource = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        audit = new PatientAccountSecurityAuditStoreAdapter(dataSource);
    }

    @Test
    void v17AuditIsDurableIdempotentAndParticipatesInOrganizationTransaction() {
        UUID actorId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        String operationKey = "patient-reset:" + patientId + ":" + UUID.randomUUID().toString().replace("-", "");
        insertAccount(actorId, "admin-" + actorId + "@example.test", "ADMIN", null);
        insertAccount(targetId, "patient-" + targetId + "@example.test", "PATIENT", patientId);

        audit.record(PatientAccountSecurityAuditStore.Action.PATIENT_PASSWORD_RESET,
                actorId, patientId, targetId, correlationId, operationKey);

        assertThat(jdbc.queryForObject("SELECT version FROM " + SCHEMA + ".flyway_schema_history "
                + "WHERE success=true ORDER BY installed_rank DESC LIMIT 1", String.class)).isEqualTo("18");
        assertThat(jdbc.queryForMap("""
                SELECT actor_account_id,patient_id,target_account_id,operation,correlation_id
                FROM patient_account_security_audit WHERE operation_key=?
                """, operationKey))
                .containsEntry("actor_account_id", actorId)
                .containsEntry("patient_id", patientId)
                .containsEntry("target_account_id", targetId)
                .containsEntry("operation", "PATIENT_PASSWORD_RESET")
                .containsEntry("correlation_id", correlationId);
        assertThatThrownBy(() -> audit.record(PatientAccountSecurityAuditStore.Action.PATIENT_PASSWORD_RESET,
                actorId, patientId, targetId, correlationId, operationKey))
                .isInstanceOf(DataIntegrityViolationException.class);

        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        UUID retryCorrelation = UUID.randomUUID();
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            audit.record(PatientAccountSecurityAuditStore.Action.PATIENT_MAIL_RETRY,
                    actorId, patientId, targetId, retryCorrelation, null);
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class).hasMessage("force rollback");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patient_account_security_audit", Integer.class))
                .isEqualTo(1);
    }

    private static void insertAccount(UUID id, String username, String role, UUID patientId) {
        jdbc.update("""
                INSERT INTO account(account_id,username,password_hash,role,is_active,patient_id)
                VALUES (?,?,?,?,true,?)
                """, id, username, "test-hash", role, patientId);
    }

    private static String schemaUrl() {
        String url = POSTGRES.getJdbcUrl();
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA;
    }
}
