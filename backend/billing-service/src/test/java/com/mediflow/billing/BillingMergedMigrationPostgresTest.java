package com.mediflow.billing;

import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/** Uses only an isolated PostgreSQL container, never repairs an existing developer database. */
@Testcontainers
class BillingMergedMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    private JdbcTemplate jdbc;

    @BeforeEach void resetIsolatedContainer() {
        flyway("latest").clean();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
    }

    @Test void freshDatabase_mergedVersionsCreateBothProducerNamespaces() {
        var merged = flyway("latest");
        merged.migrate();
        merged.validate();
        assertAllNamespaces();
    }

    @Test void masterV13ToMergedV20_preservesSourceReceiptsRequestsAndAccount() {
        flyway("13").migrate();
        UUID account = UUID.randomUUID(), admission = UUID.randomUUID();
        UUID lab = UUID.randomUUID(), labRequest = UUID.randomUUID(), depositRequest = UUID.randomUUID();
        jdbc.update("INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,opened_at) "
                + "VALUES (?,?,?,'ADMISSION',?,now())", account, UUID.randomUUID(), UUID.randomUUID(), admission);
        jdbc.update("INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,requested_amount) "
                + "VALUES (?,?,?,'LAB_TEST',100)", labRequest, UUID.randomUUID(), account);
        jdbc.update("INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,requested_amount) "
                + "VALUES (?,?,?,'ADMISSION_DEPOSIT',100)", depositRequest, UUID.randomUUID(), account);
        jdbc.update("INSERT INTO lab_test_charge_source(lab_id,source_order_id,source_fingerprint,payment_request_id,requested_at) "
                + "VALUES (?,?,?,?,now())", lab, UUID.randomUUID(), "a".repeat(64), labRequest);
        jdbc.update("INSERT INTO lab_test_charge_delivery(event_id,lab_id,payload_fingerprint) VALUES (?,?,?)",
                UUID.randomUUID(), lab, "b".repeat(64));
        jdbc.update("INSERT INTO admission_deposit_source(admission_id,source_fingerprint,payment_request_id,requested_at) "
                + "VALUES (?,?,?,now())", admission, "c".repeat(64), depositRequest);
        jdbc.update("INSERT INTO admission_deposit_delivery(event_id,admission_id,payload_fingerprint) VALUES (?,?,?)",
                UUID.randomUUID(), admission, "d".repeat(64));
        List<String> tables = List.of("billing_account", "payment_request", "lab_test_charge_source",
                "lab_test_charge_delivery", "admission_deposit_source", "admission_deposit_delivery");
        var before = tables.stream().map(this::rows).toList();

        var merged = flyway("latest");
        merged.migrate();
        merged.validate();

        assertThat(tables.stream().map(this::rows).toList()).isEqualTo(before);
        assertAllNamespaces();
    }

    private void assertAllNamespaces() {
        for (String table : List.of("lab_test_charge_source", "admission_deposit_source",
                "prescription_charge_source", "prescription_terminal_source", "final_admission_settlement_operation")) {
            assertThat(jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, table))
                    .as("merged table %s", table).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT version) FROM flyway_schema_history "
                + "WHERE type='SQL' AND success", Integer.class)).isEqualTo(21);
    }
    private List<String> rows(String table) {
        // These table identifiers are a private fixed test list, never user input.
        return jdbc.queryForList("SELECT to_jsonb(t)::text FROM " + table + " t ORDER BY to_jsonb(t)::text", String.class);
    }
    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration").target(target).cleanDisabled(false).load();
    }
}
