package com.mediflow.pharmacy.infrastructure.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
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

        assertThat(flyway().info().current().getVersion().getVersion()).isEqualTo("11");
        assertThat(tableExists("PAYMENT_RECEIPT")).isTrue();
        assertThat(tableExists("PHARMACY_SCHEDULER_LEASE")).isTrue();
        assertThat(columnExists("PHARMACY_SCHEDULER_LEASE", "LEASE_TOKEN")).isTrue();
        assertThat(indexExists("uk_reservation_prescription_drug")).isTrue();
    }

    /** Rows created on a V4 schema survive V5-V11 without being fabricated into paid receipts. */
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
                var result = connection.getMetaData().getTables(null, null, table, null)) {
            return result.next();
        }
    }

    private boolean columnExists(String table, String column) throws Exception {
        try (Connection connection = connection();
                var result = connection.getMetaData().getColumns(null, null, table, column)) {
            return result.next();
        }
    }

    private boolean indexExists(String indexName) throws Exception {
        return queryInt("SELECT count(*) FROM pg_indexes WHERE indexname = '" + indexName + "'") == 1;
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
