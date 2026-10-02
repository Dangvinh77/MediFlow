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
    void v10Upgrade_preservesLegacyAndVerifiedGenerationButDoesNotInventCoverage() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target("9").load().migrate();
        UUID id = UUID.randomUUID();
        execute("INSERT INTO operational_replay_generation(generation_id, status, completed_at) VALUES ('" + id + "', 'VERIFIED', now())");
        execute("INSERT INTO daily_visit_report(report_id, report_date, visit_count) VALUES ('" + UUID.randomUUID() + "', DATE '2026-10-01', 7)");
        flyway().migrate();
        assertThat(queryInt("SELECT count(*) FROM operational_replay_generation")).isOne();
        assertThat(queryInt("SELECT sum(visit_count) FROM daily_visit_report")).isEqualTo(7);
        assertThat(queryInt("SELECT count(*) FROM operational_report_publication")).isZero();
    }

    @Test
    void v9Upgrade_preservesLegacyDataAndAddsEmptyPendingEvidenceTables() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target("8").load().migrate();
        execute("INSERT INTO daily_visit_report(report_id, report_date, visit_count) VALUES ('" + UUID.randomUUID()
                + "', DATE '2026-10-01', 7)");
        flyway().migrate();
        assertThat(queryInt("SELECT sum(visit_count) FROM daily_visit_report")).isEqualTo(7);
        assertThat(queryInt("SELECT count(*) FROM report_admission_fact")).isZero();
        assertThat(queryInt("SELECT count(*) FROM report_admission_delivery")).isZero();
        assertThat(queryInt("SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' "
                + "AND table_name = 'report_admission_fact' AND column_name = 'source_revision'")).isZero();
    }

    @Test
    void v9Constraints_rejectMissingStartDimensionsAndAmbiguousCloseProof() throws Exception {
        flyway().migrate();
        UUID admissionId = UUID.randomUUID();
        execute("INSERT INTO report_admission_target(admission_id) VALUES ('" + admissionId + "')");
        String prefix = "INSERT INTO report_admission_fact(admission_id, fact_type, business_at, patient_id, business_at_iso, emergency, "
                + "settlement_id, approved_override_id) VALUES ('" + admissionId + "', ";
        assertConstraintViolation(() -> execute(prefix + "'STARTED', TIMESTAMPTZ '2026-10-01T01:00:00Z', '" + UUID.randomUUID()
                + "', '2026-10-01T01:00:00Z', false, NULL, NULL)"), "23514");
        assertConstraintViolation(() -> execute(prefix + "'CLOSED', TIMESTAMPTZ '2026-10-01T01:00:00Z', '" + UUID.randomUUID()
                + "', '2026-10-01T01:00:00Z', false, '" + UUID.randomUUID() + "', '" + UUID.randomUUID() + "')"), "23514");
        assertConstraintViolation(() -> execute(prefix + "'CLOSED', TIMESTAMPTZ '2026-10-01T01:00:00Z', '" + UUID.randomUUID()
                + "', '2026-10-01T01:00:00Z', false, NULL, NULL)"), "23514");
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

    @Test
    void v6_businessOperationKeysDedupeNewEventIdsAndAllowExplicitRevisions() throws Exception {
        flyway().migrate();

        UUID financialSourceId = UUID.randomUUID();
        insertFinancialContribution(UUID.randomUUID(), UUID.randomUUID(), financialSourceId, 1, null);
        assertConstraintViolation(() -> insertFinancialContribution(
                UUID.randomUUID(), UUID.randomUUID(), financialSourceId, 1, null), "23505");
        insertFinancialContribution(UUID.randomUUID(), UUID.randomUUID(), financialSourceId, 2, null);
        insertFinancialContribution(UUID.randomUUID(), UUID.randomUUID(), financialSourceId, 1,
                UUID.fromString("00000000-0000-0000-0000-000000000000"));

        UUID operationalSourceId = UUID.randomUUID();
        insertOperationalContribution(UUID.randomUUID(), UUID.randomUUID(), operationalSourceId, 1, null);
        assertConstraintViolation(() -> insertOperationalContribution(
                UUID.randomUUID(), UUID.randomUUID(), operationalSourceId, 1, null), "23505");
        insertOperationalContribution(UUID.randomUUID(), UUID.randomUUID(), operationalSourceId, 2, null);
    }

    @Test
    void v6NullHospitalScopeDoesNotCollideWithZeroUuidDepartment() throws Exception {
        flyway().migrate();
        String reportDate = "DATE '2026-10-01'";
        UUID zeroDepartmentId = UUID.fromString("00000000-0000-0000-0000-000000000000");

        insertDailyFinancialReport(UUID.randomUUID(), reportDate, null);
        insertDailyFinancialReport(UUID.randomUUID(), reportDate, zeroDepartmentId);
        assertConstraintViolation(() -> insertDailyFinancialReport(UUID.randomUUID(), reportDate, null), "23505");
        assertConstraintViolation(() -> insertDailyFinancialReport(
                UUID.randomUUID(), reportDate, zeroDepartmentId), "23505");

        insertDailyOperationalReport(UUID.randomUUID(), reportDate, null);
        insertDailyOperationalReport(UUID.randomUUID(), reportDate, zeroDepartmentId);
        assertConstraintViolation(() -> insertDailyOperationalReport(UUID.randomUUID(), reportDate, null), "23505");
        assertConstraintViolation(() -> insertDailyOperationalReport(
                UUID.randomUUID(), reportDate, zeroDepartmentId), "23505");
    }

    @Test
    void v6_rejectsIncompleteEpisodeTupleAndNonPositiveSourceRevision() throws Exception {
        flyway().migrate();

        assertConstraintViolation(() -> execute("INSERT INTO operational_contribution "
                + "(contribution_id, event_id, source_type, source_id, source_revision, metric_type, "
                + "care_episode_id, metric_date, occurred_at) VALUES ('" + UUID.randomUUID() + "', '"
                + UUID.randomUUID() + "', 'LAB_RESULT', '" + UUID.randomUUID() + "', 1, 'LAB_TEST', '"
                + UUID.randomUUID() + "', DATE '2026-10-01', TIMESTAMPTZ '2026-10-01T08:00:00Z')"), "23514");

        assertConstraintViolation(() -> execute("INSERT INTO operational_contribution "
                + "(contribution_id, event_id, source_type, source_id, source_revision, metric_type, "
                + "care_episode_type, metric_date, occurred_at) VALUES ('" + UUID.randomUUID() + "', '"
                + UUID.randomUUID() + "', 'LAB_RESULT', '" + UUID.randomUUID() + "', 1, 'LAB_TEST', "
                + "'ADMISSION', DATE '2026-10-01', TIMESTAMPTZ '2026-10-01T08:00:00Z')"), "23514");
        assertConstraintViolation(() -> execute("INSERT INTO operational_contribution "
                + "(contribution_id, event_id, source_type, source_id, source_revision, metric_type, "
                + "metric_date, occurred_at) VALUES ('" + UUID.randomUUID() + "', '" + UUID.randomUUID()
                + "', 'LAB_RESULT', '" + UUID.randomUUID() + "', 0, 'LAB_TEST', DATE '2026-10-01', "
                + "TIMESTAMPTZ '2026-10-01T08:00:00Z')"), "23514");
        assertConstraintViolation(() -> execute("INSERT INTO operational_contribution "
                + "(contribution_id, event_id, source_type, source_id, metric_type, metric_date, occurred_at) "
                + "VALUES ('" + UUID.randomUUID() + "', '" + UUID.randomUUID() + "', 'LAB_RESULT', '"
                + UUID.randomUUID() + "', 'LAB_TEST', DATE '2026-10-01', "
                + "TIMESTAMPTZ '2026-10-01T08:00:00Z')"), "23502");
    }

    private void insertFinancialContribution(UUID contributionId, UUID eventId, UUID sourceId,
            int sourceRevision, UUID departmentId) throws SQLException {
        String departmentValue = departmentId == null ? "NULL" : "'" + departmentId + "'";
        execute("INSERT INTO financial_contribution "
                + "(contribution_id, event_id, source_type, source_id, source_revision, account_id, patient_id, "
                + "department_id, care_episode_type, care_episode_id, contribution_type, business_date, occurred_at) "
                + "VALUES ('" + contributionId + "', '" + eventId + "', 'PAYMENT_TRANSACTION', '" + sourceId
                + "', " + sourceRevision + ", '" + UUID.randomUUID() + "', '" + UUID.randomUUID() + "', "
                + departmentValue + ", 'OUTPATIENT_VISIT', '" + UUID.randomUUID()
                + "', 'PAYMENT', DATE '2026-10-01', TIMESTAMPTZ '2026-10-01T08:00:00Z')");
    }

    private void insertOperationalContribution(UUID contributionId, UUID eventId, UUID sourceId,
            int sourceRevision, UUID departmentId) throws SQLException {
        String departmentValue = departmentId == null ? "NULL" : "'" + departmentId + "'";
        execute("INSERT INTO operational_contribution "
                + "(contribution_id, event_id, source_type, source_id, source_revision, metric_type, department_id, "
                + "care_episode_type, care_episode_id, metric_date, occurred_at) VALUES ('" + contributionId
                + "', '" + eventId + "', 'LAB_RESULT', '" + sourceId + "', " + sourceRevision
                + ", 'LAB_TEST', " + departmentValue + ", 'OUTPATIENT_VISIT', '" + UUID.randomUUID()
                + "', DATE '2026-10-01', TIMESTAMPTZ '2026-10-01T08:00:00Z')");
    }

    private void insertDailyFinancialReport(UUID reportId, String reportDate, UUID departmentId) throws SQLException {
        String departmentValue = departmentId == null ? "NULL" : "'" + departmentId + "'";
        execute("INSERT INTO daily_financial_report(report_id, report_date, department_id) VALUES ('"
                + reportId + "', " + reportDate + ", " + departmentValue + ")");
    }

    private void insertDailyOperationalReport(UUID reportId, String reportDate, UUID departmentId) throws SQLException {
        String departmentValue = departmentId == null ? "NULL" : "'" + departmentId + "'";
        execute("INSERT INTO daily_operational_report(report_id, report_date, department_id) VALUES ('"
                + reportId + "', " + reportDate + ", " + departmentValue + ")");
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
