package com.mediflow.pharmacy.infrastructure.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that pharmacy migrations are forward-compatible and preserve legacy rows.
 *
 * <p>The test intentionally uses Flyway directly instead of the application context so it can
 * exercise both a partial V4 database and a fresh database in isolation.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class PharmacyMigrationCompatibilityTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Cleans the isolated database before each migration scenario. */
    @BeforeEach
    void cleanDatabase() {
        flyway().clean();
    }

    /** A brand-new database migrates through every version and exposes the expected constraints. */
    @Test
    void freshDatabase_migratesToLatestVersion() throws Exception {
        flyway().migrate();

        assertThat(flyway().info().current().getVersion().getVersion()).isEqualTo("12");
        assertThat(tableExists("PAYMENT_RECEIPT")).isTrue();
        assertThat(tableExists("PHARMACY_SCHEDULER_LEASE")).isTrue();
        assertThat(columnExists("PHARMACY_SCHEDULER_LEASE", "LEASE_TOKEN")).isTrue();
        assertThat(indexExists("uk_reservation_prescription_drug")).isTrue();
        assertThat(indexDefinitionContains("uk_reservation_prescription_drug", "UNIQUE")).isTrue();
        assertThat(constraintExists("DRUG", "ck_drug_stock_non_negative")).isTrue();
        assertThat(constraintExists("PRESCRIPTION", "ck_prescription_status")).isTrue();
        assertThat(constraintExists("STOCK_RESERVATION", "ck_reservation_release_audit")).isTrue();
        assertThat(constraintExists("DISPENSE_SLIP", "ck_dispense_status")).isTrue();
        assertThat(constraintExists("STOCK_ADJUSTMENT", "ck_stock_adjustment_snapshot")).isTrue();
        assertThat(constraintExists("PHARMACY_EVENT_OUTBOX", "pharmacy_event_outbox_pkey")).isTrue();
        assertThat(columnExists("PHARMACY_EVENT_OUTBOX", "AGGREGATE_ID")).isTrue();
        assertThat(indexExists("idx_pharmacy_outbox_aggregate_order")).isTrue();
        assertThat(constraintExists("PAYMENT_RECEIPT", "payment_receipt_pkey")).isTrue();
        assertThat(foreignKeyExists("PRESCRIPTION_LINE", "prescription_id", "PRESCRIPTION", "prescription_id"))
                .isTrue();
        assertThat(foreignKeyExists("PRESCRIPTION_LINE", "drug_id", "DRUG", "drug_id")).isTrue();
        assertThat(foreignKeyExists("DISPENSE_SLIP", "prescription_id", "PRESCRIPTION", "prescription_id"))
                .isTrue();
        assertThat(foreignKeyExists("STOCK_RESERVATION", "drug_id", "DRUG", "drug_id")).isTrue();
        assertThat(foreignKeyExists("STOCK_RESERVATION", "prescription_id", "PRESCRIPTION", "prescription_id"))
                .isTrue();
        assertThat(constraintDefinitionContains("DRUG", "ck_drug_stock_non_negative", "stock_quantity >= 0"))
                .isTrue();
        assertThat(constraintDefinitionContains("PRESCRIPTION", "ck_prescription_status", "status"))
                .isTrue();
        assertThat(constraintDefinitionContains("STOCK_ADJUSTMENT", "ck_stock_adjustment_snapshot",
                "after_stock-before_stock=delta")).isTrue();
        assertThat(externalForeignKeyCount()).isZero();
    }

    /** Rows created on a V4 schema survive later migrations without fabricated payment proof. */
    @Test
    void v4DatabaseWithLegacyRows_upgradesWithoutBackfillingPaymentProof() throws Exception {
        flyway(MigrationVersion.fromVersion("4")).migrate();
        UUID drugId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        insertLegacyRows(drugId, prescriptionId);

        flyway().migrate();

        assertThat(queryInt("SELECT count(*) FROM PRESCRIPTION WHERE prescription_id = '"
                + prescriptionId + "'")).isEqualTo(1);
        assertThat(queryString("SELECT status FROM PRESCRIPTION WHERE prescription_id = '"
                + prescriptionId + "'")).isEqualTo("ACTIVE");
        assertThat(queryInt("SELECT count(*) FROM PAYMENT_RECEIPT WHERE prescription_id = '"
                + prescriptionId + "'")).isZero();
        assertThat(queryInt("SELECT count(*) FROM PRESCRIPTION_LINE WHERE prescription_id = '"
                + prescriptionId + "'")).isEqualTo(1);
    }

    /** A V5 outbox row keeps its event identity and payload while lease/quarantine columns are added. */
    @Test
    void v5DatabaseWithPendingOutboxRow_upgradesWithoutChangingDeliveryIntent() throws Exception {
        flyway(MigrationVersion.fromVersion("5")).migrate();
        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\",\"kind\":\"legacy\"}";
        insertV5OutboxRow(eventId, payload);

        flyway().migrate();

        assertThat(queryString("SELECT payload FROM PHARMACY_EVENT_OUTBOX WHERE event_id = '"
                + eventId + "'"))
                .isEqualTo(payload);
        assertThat(queryInt("SELECT attempts FROM PHARMACY_EVENT_OUTBOX WHERE event_id = '"
                + eventId + "'"))
                .isZero();
        assertThat(queryInt("SELECT count(*) FROM PHARMACY_EVENT_OUTBOX WHERE event_id = '"
                + eventId
                + "' AND published_at IS NULL AND available_at IS NOT NULL"
                + " AND quarantined_at IS NULL AND aggregate_id IS NULL"))
                .isEqualTo(1);
    }

    private void insertV5OutboxRow(UUID eventId, String payload) throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO PHARMACY_EVENT_OUTBOX
                        (event_id, routing_key, payload, created_at, attempts)
                    VALUES ('%s', 'prescription.created', '%s', now(), 0)
                    """.formatted(eventId, payload.replace("'", "''")));
        }
    }

    private void insertLegacyRows(UUID drugId, UUID prescriptionId) throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO DRUG (drug_id, drug_name, active_ingredient, unit, price,
                        stock_quantity, expiry_date, low_stock_threshold, created_at)
                    VALUES ('%s', 'Legacy drug', 'ingredient', 'tablet', 2.00, 10, '%s', 1, now())
                    """.formatted(drugId, LocalDate.now().plusYears(1)));
            statement.executeUpdate("""
                    INSERT INTO PRESCRIPTION (prescription_id, record_id, patient_id, doctor_id,
                        department_id, prescribed_date, total_amount, status, created_at)
                    VALUES ('%s', '%s', '%s', '%s', '%s', '%s', 2.00, 'ACTIVE', now())
                    """.formatted(prescriptionId, UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), LocalDate.now()));
            statement.executeUpdate("""
                    INSERT INTO PRESCRIPTION_LINE (line_id, prescription_id, drug_id, quantity,
                        unit_price, line_total)
                    VALUES ('%s', '%s', '%s', 1, 2.00, 2.00)
                    """.formatted(UUID.randomUUID(), prescriptionId, drugId));
            statement.executeUpdate("""
                    INSERT INTO DISPENSE_SLIP (dispense_id, prescription_id, status, created_at)
                    VALUES ('%s', '%s', 'PENDING', now())
                    """.formatted(UUID.randomUUID(), prescriptionId));
            statement.executeUpdate("""
                    INSERT INTO STOCK_RESERVATION (reservation_id, drug_id, prescription_id,
                        quantity, status, expires_at, created_at)
                    VALUES ('%s', '%s', '%s', 1, 'RESERVED', now() + interval '1 day', now())
                    """.formatted(UUID.randomUUID(), drugId, prescriptionId));
        }
    }

    private Flyway flyway() {
        return flyway(null);
    }

    private Flyway flyway(MigrationVersion target) {
        FluentConfiguration config = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false);
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private boolean tableExists(String table) throws Exception {
        try (Connection connection = connection();
                var result = connection.getMetaData().getTables(null, "public",
                        table.toLowerCase(Locale.ROOT), null)) {
            return result.next();
        }
    }

    private boolean columnExists(String table, String column) throws Exception {
        try (Connection connection = connection();
                var result = connection.getMetaData().getColumns(null, "public",
                        table.toLowerCase(Locale.ROOT), column.toLowerCase(Locale.ROOT))) {
            return result.next();
        }
    }

    private boolean indexExists(String indexName) throws Exception {
        return queryInt("SELECT count(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = '"
                + indexName + "'") == 1;
    }

    /** Confirms a named public index has the expected definition fragment. */
    private boolean indexDefinitionContains(String indexName, String fragment) throws Exception {
        String sql = "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public' "
                + "AND indexname = '" + indexName + "' AND upper(indexdef) LIKE upper('%" + fragment + "%')";
        return queryInt(sql) == 1;
    }

    private boolean constraintExists(String tableName, String constraintName) throws Exception {
        String sql = "SELECT count(*) FROM pg_constraint c "
                + "JOIN pg_class t ON t.oid = c.conrelid "
                + "JOIN pg_namespace n ON n.oid = t.relnamespace "
                + "WHERE n.nspname = 'public' AND lower(t.relname) = lower('" + tableName + "') "
                + "AND lower(c.conname) = lower('" + constraintName + "')";
        return queryInt(sql) == 1;
    }

    /** Confirms the database expression behind a named check constraint. */
    private boolean constraintDefinitionContains(String tableName, String constraintName,
            String fragment) throws Exception {
        String normalizedFragment = fragment.replace(" ", "").replace("(", "").replace(")", "");
        String sql = "SELECT count(*) FROM pg_constraint c "
                + "JOIN pg_class t ON t.oid = c.conrelid "
                + "JOIN pg_namespace n ON n.oid = t.relnamespace "
                + "WHERE n.nspname = 'public' AND lower(t.relname) = lower('" + tableName + "') "
                + "AND lower(c.conname) = lower('" + constraintName + "') "
                + "AND translate(lower(pg_get_constraintdef(c.oid)), ' ()', '') LIKE lower('%"
                + normalizedFragment + "%')";
        return queryInt(sql) == 1;
    }

    /** Verifies an internal FK without allowing any cross-service database relationship. */
    private boolean foreignKeyExists(String tableName, String columnName,
            String referencedTable, String referencedColumn) throws Exception {
        String sql = "SELECT count(*) FROM information_schema.table_constraints tc "
                + "JOIN information_schema.key_column_usage kcu "
                + "ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema "
                + "JOIN information_schema.constraint_column_usage ccu "
                + "ON tc.constraint_name = ccu.constraint_name AND tc.table_schema = ccu.table_schema "
                + "WHERE tc.table_schema = 'public' AND ccu.table_schema = 'public' "
                + "AND lower(tc.constraint_type) = 'foreign key' "
                + "AND lower(tc.table_name) = lower('" + tableName + "') "
                + "AND lower(kcu.column_name) = lower('" + columnName + "') "
                + "AND lower(ccu.table_name) = lower('" + referencedTable + "') "
                + "AND lower(ccu.column_name) = lower('" + referencedColumn + "')";
        return queryInt(sql) == 1;
    }

    /** Returns FK count that points outside Pharmacy's public schema tables. */
    private int externalForeignKeyCount() throws Exception {
        String sql = "SELECT count(*) FROM information_schema.table_constraints tc "
                + "JOIN information_schema.constraint_column_usage ccu "
                + "ON tc.constraint_name = ccu.constraint_name AND tc.table_schema = ccu.table_schema "
                + "WHERE tc.table_schema = 'public' "
                + "AND tc.constraint_type = 'FOREIGN KEY' "
                + "AND (ccu.table_schema <> 'public' OR ccu.table_name NOT IN "
                + "('drug', 'prescription', 'prescription_line', 'dispense_slip',"
                + " 'stock_reservation', 'stock_adjustment'))";
        return queryInt(sql);
    }

    private int queryInt(String sql) throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private String queryString(String sql) throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }
}
