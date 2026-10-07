package com.mediflow.report.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ReportMigrationSchemaTest {

    @Test
    void v14_indexesOnlyNeverModifiesEvidenceOrPublication() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/db/migration/V14__report_telemetry_indexes.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertThat(sql).contains("ix_report_pending_close_observed", "ix_operational_replay_telemetry",
                    "ix_cash_replay_telemetry", "ix_operational_legacy_unverified");
            assertThat(sql).doesNotContain("create table", "update ", "insert into", "delete from", "truncate ", "drop ");
        }
    }

    @Test
    void v13_isolatesCashReplayAndNeverBackfillsPublishesOrClearsLiveData() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/db/migration/V13__isolated_cash_receipt_replay.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertThat(sql).contains("cash_replay_generation", "cash_replay_input", "cash_replay_receipt", "cash_replay_scope",
                    "first_envelope_fingerprint", "snapshot_version", "projector_version", "nulls not distinct");
            assertThat(sql).doesNotContain("truncate ", "drop table", "delete from", "insert into report_cash",
                    "insert into financial_contribution", "insert into operational_report_publication", "active_generation");
        }
    }

    @Test
    void v9_retainsMinimalPendingEvidenceWithoutFakeRevisionOrMetricEffects() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/db/migration/V9__pending_admission_report_evidence.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertThat(sql).contains("report_admission_target", "report_admission_delivery", "report_admission_fact",
                    "primary key (admission_id, fact_type)", "business_at_iso", "ck_report_admission_fact_shape");
            assertThat(sql).doesNotContain("insert into operational_contribution", "insert into daily_operational_report",
                    "source_revision integer", "jsonb", "diagnosis", "truncate ", "drop table");
        }
    }

    @Test
    void v8_isolatesReplayStateAndKeepsLiveTablesUntouched() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/db/migration/V8__isolated_operational_replay.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertThat(sql).contains("operational_replay_generation", "operational_replay_input",
                    "operational_replay_contribution", "operational_replay_scope", "nulls not distinct");
            assertThat(sql).doesNotContain("truncate ", "drop table", "delete from", "active_generation");
        }
    }

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

    @Test
    void v6_keepsDeliveryProvenanceSeparateFromBusinessOperationKeys() throws IOException {
        String sql;
        try (InputStream stream = getClass().getResourceAsStream(
                "/db/migration/V6__care_finance_projections.sql")) {
            assertThat(stream).as("Flyway V6 migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }

        assertThat(sql).contains("create table financial_contribution")
                .contains("create table daily_financial_report")
                .contains("create table operational_contribution")
                .contains("create table daily_operational_report")
                .contains("source_revision integer not null")
                .contains("uq_financial_contribution_business_operation")
                .contains("uq_operational_contribution_business_operation")
                .contains("nulls not distinct");
        assertThat(sql).doesNotContain("00000000-0000-0000-0000-000000000000")
                .doesNotContain("source_revision integer not null default");
    }
}
