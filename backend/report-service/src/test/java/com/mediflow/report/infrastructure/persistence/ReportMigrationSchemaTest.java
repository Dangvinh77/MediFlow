package com.mediflow.report.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ReportMigrationSchemaTest {

    @Test
    void v1_containsAllProjectionTablesAndNullableScopeIndexes() throws IOException {
        String sql;
        try (InputStream stream = getClass().getResourceAsStream("/db/migration/V1__init.sql")) {
            assertThat(stream).as("Flyway V1 migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }

        assertThat(sql).contains("create table daily_visit_report")
                .contains("create table monthly_revenue_report")
                .contains("create table drug_statistic")
                .contains("create table processed_event")
                .contains("create table payment_contribution")
                .contains("create unique index uq_daily_report_date_dept")
                .contains("create unique index uq_monthly_revenue_scope")
                .contains("create unique index uq_drug_statistic_scope")
                .contains("nulls not distinct")
                .contains("ck_payment_state_data");
    }

    @Test
    void v2_requiresPendingContributionToBeUnscoped() throws IOException {
        String sql;
        try (InputStream stream = getClass().getResourceAsStream(
                "/db/migration/V2__enforce_pending_payment_scope.sql")) {
            assertThat(stream).as("Flyway V2 migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }

        assertThat(sql).contains("drop constraint ck_payment_state_data")
                .contains("status = 'pending_reversal'")
                .contains("department_id is null");
    }
}
