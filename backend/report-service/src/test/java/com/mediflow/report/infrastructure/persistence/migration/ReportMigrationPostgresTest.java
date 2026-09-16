package com.mediflow.report.infrastructure.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL-specific migration checks; skipped automatically when Docker is unavailable. */
@Testcontainers(disabledWithoutDocker = true)
class ReportMigrationPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeEach
    void cleanDatabase() {
        flyway().clean();
    }

    @Test
    void freshDatabase_hasFiveTablesAndNullableScopeUniqueness() throws Exception {
        flyway().migrate();

        assertThat(queryInt("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema='public' AND table_name IN "
                + "('daily_visit_report','monthly_revenue_report','drug_statistic',"
                + "'processed_event','payment_contribution')")).isEqualTo(5);
        execute("INSERT INTO daily_visit_report(report_id, report_date) "
                + "VALUES ('" + UUID.randomUUID() + "', DATE '2026-09-01')");
        assertConstraintViolation(() -> execute("INSERT INTO daily_visit_report(report_id, report_date) "
                + "VALUES ('" + UUID.randomUUID() + "', DATE '2026-09-01')"), "23505");

        execute("INSERT INTO monthly_revenue_report(report_id, month, year) "
                + "VALUES ('" + UUID.randomUUID() + "', 9, 2026)");
        assertConstraintViolation(() -> execute("INSERT INTO monthly_revenue_report(report_id, month, year) "
                + "VALUES ('" + UUID.randomUUID() + "', 9, 2026)"), "23505");

        execute("INSERT INTO drug_statistic(statistic_id, drug_id, drug_name, report_date) "
                + "VALUES ('" + UUID.randomUUID() + "', '" + UUID.randomUUID()
                + "', 'Paracetamol', DATE '2026-09-01')");
        UUID duplicateDrugId = UUID.randomUUID();
        execute("INSERT INTO drug_statistic(statistic_id, drug_id, drug_name, report_date) "
                + "VALUES ('" + UUID.randomUUID() + "', '" + duplicateDrugId
                + "', 'Paracetamol', DATE '2026-09-01')");
        assertConstraintViolation(() -> execute("INSERT INTO drug_statistic(statistic_id, drug_id, drug_name, report_date) "
                + "VALUES ('" + UUID.randomUUID() + "', '" + duplicateDrugId
                + "', 'Paracetamol', DATE '2026-09-01')"), "23505");
    }

    @Test
    void constraints_rejectInvalidMonthNegativeRevenueAndInvalidPaymentState() throws Exception {
        flyway().migrate();

        assertConstraintViolation(() -> execute("INSERT INTO monthly_revenue_report "
                + "(report_id, month, year) VALUES ('" + UUID.randomUUID() + "', 13, 2026)"), "23514");
        assertConstraintViolation(() -> execute("INSERT INTO daily_visit_report "
                + "(report_id, report_date, revenue) VALUES ('" + UUID.randomUUID()
                + "', DATE '2026-09-01', -1)"), "23514");
        assertConstraintViolation(() -> execute("INSERT INTO daily_visit_report "
                + "(report_id, report_date, visit_count) VALUES ('" + UUID.randomUUID()
                + "', DATE '2026-09-02', -1)"), "23514");
        assertConstraintViolation(() -> execute("INSERT INTO daily_visit_report "
                + "(report_id, report_date, lab_count) VALUES ('" + UUID.randomUUID()
                + "', DATE '2026-09-03', -1)"), "23514");
        assertConstraintViolation(() -> execute("INSERT INTO daily_visit_report "
                + "(report_id, report_date, prescription_count) VALUES ('" + UUID.randomUUID()
                + "', DATE '2026-09-04', -1)"), "23514");
        assertConstraintViolation(() -> execute("INSERT INTO monthly_revenue_report "
                + "(report_id, month, year, total_revenue) VALUES ('" + UUID.randomUUID()
                + "', 9, 2026, -1)"), "23514");
        assertConstraintViolation(() -> execute("INSERT INTO monthly_revenue_report "
                + "(report_id, month, year, invoice_count) VALUES ('" + UUID.randomUUID()
                + "', 9, 2026, -1)"), "23514");
        assertConstraintViolation(() -> execute("INSERT INTO drug_statistic "
                + "(statistic_id, drug_id, drug_name, report_date, dispensed_quantity) VALUES ('"
                + UUID.randomUUID() + "', '" + UUID.randomUUID() + "', 'Paracetamol', DATE '2026-09-01', -1)"), "23514");
        assertConstraintViolation(() -> execute("INSERT INTO payment_contribution "
                + "(invoice_id, status, completed_event_id, payment_date, amount) VALUES ('"
                + UUID.randomUUID() + "', 'APPLIED', '" + UUID.randomUUID()
                + "', DATE '2026-09-01', -1)"), "23514");
        assertConstraintViolation(() -> execute("INSERT INTO payment_contribution(invoice_id, status) "
                + "VALUES ('" + UUID.randomUUID() + "', 'BOGUS')"), "23514");
        assertConstraintViolation(() -> execute("INSERT INTO payment_contribution "
                + "(invoice_id, status, failed_event_id, department_id) VALUES ('"
                + UUID.randomUUID() + "', 'PENDING_REVERSAL', '" + UUID.randomUUID() + "', '"
                + UUID.randomUUID() + "')"), "23514");
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void assertConstraintViolation(SqlCall call, String expectedSqlState) throws Exception {
        try {
            call.run();
            fail("Expected SQL constraint violation");
        } catch (SQLException expected) {
            assertThat(expected.getSQLState()).isEqualTo(expectedSqlState);
        }
    }

    private int queryInt(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
    }

    @FunctionalInterface
    private interface SqlCall {
        void run() throws SQLException;
    }
}
