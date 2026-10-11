package com.mediflow.report.infrastructure.persistence;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.report.application.mapper.BillingSettlementSnapshotMapper;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;
import com.mediflow.report.infrastructure.persistence.adapter.SettlementReceivableStoreAdapter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class SettlementReceivablePostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    private static DriverManagerDataSource source; private static JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final BillingSettlementSnapshotMapper mapping = new BillingSettlementSnapshotMapper(ZoneId.of("Asia/Bangkok"));
    @BeforeAll static void migrate() {
        source=new DriverManagerDataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()); jdbc=new JdbcTemplate(source);
        Flyway.configure().dataSource(source).load().migrate();
    }
    @BeforeEach void clean() { jdbc.execute("TRUNCATE report_settlement_snapshot,report_receivable_stock CASCADE"); }
    private byte[] fixture(int revision) throws Exception {
        return Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/settlement-reconciled-"+revision+".json"));
    }
    private void apply(byte[] bytes) {
        var event=new CareFinanceEnvelopeDecoder(mapper).decode("settlement.reconciled",bytes); var snapshot=mapping.map(event);
        new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(status -> new SettlementReceivableStoreAdapter(jdbc,mapper).apply(event,snapshot));
    }
    private BigDecimal hospital() { return jdbc.queryForObject("SELECT outstanding_receivable FROM report_receivable_stock WHERE department_id IS NULL",BigDecimal.class); }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class); }
    private ObjectNode tree(int revision) throws Exception { return (ObjectNode)mapper.readTree(fixture(revision)); }
    @ParameterizedTest @ValueSource(strings={"123","321","213","132","231","312"})
    void apply_allDeliveryOrders_latestStock15NeverSumsRevisionsOrCash(String order) throws Exception {
        for (char c:order.toCharArray()) apply(fixture(c-'0'));
        assertThat(hospital()).isEqualByComparingTo("15");
        assertThat(jdbc.queryForList("SELECT outstanding_receivable FROM report_receivable_stock",BigDecimal.class)).hasSize(2).containsOnly(new BigDecimal("15.00"));
        assertThat(count("report_settlement_snapshot")).isEqualTo(3); assertThat(count("report_receivable_current")).isOne();
        assertThat(jdbc.queryForObject("SELECT settlement_version FROM report_receivable_current",Integer.class)).isEqualTo(3);
        assertThat(count("report_cash_receipt")).isZero(); assertThat(count("financial_contribution")).isZero();
        assertThat(count("daily_financial_report")).isZero(); assertThat(count("monthly_revenue_report")).isZero();
    }
    @Test void apply_exactDuplicateAndNewDelivery_oneStockEffect() throws Exception {
        apply(fixture(1)); apply(fixture(1)); var duplicate=tree(1); duplicate.put("eventId",UUID.randomUUID().toString());
        apply(mapper.writeValueAsBytes(duplicate));
        assertThat(hospital()).isEqualByComparingTo("40"); assertThat(count("report_settlement_snapshot")).isOne();
        assertThat(count("report_settlement_delivery")).isEqualTo(2);
    }
    @ParameterizedTest @ValueSource(strings={"patientId","departmentId","currency","supersedesSettlementId"})
    void apply_conflictingAccountOrPredecessor_rollsBackClaims(String field) throws Exception {
        apply(fixture(1)); var bad=tree(2); ((ObjectNode)bad.get("payload")).put(field,field.equals("currency")?"USD":UUID.randomUUID().toString());
        assertThatThrownBy(() -> apply(mapper.writeValueAsBytes(bad))).isInstanceOf(com.mediflow.report.domain.exception.ReportRuleException.class);
        assertThat(hospital()).isEqualByComparingTo("40"); assertThat(count("report_settlement_snapshot")).isOne(); assertThat(count("report_settlement_delivery")).isOne();
    }
    @Test void apply_changedDeliveryEnvelope_isConflictNotDuplicate() throws Exception {
        apply(fixture(1)); var bad=tree(1); bad.put("correlationId","changed");
        assertThatThrownBy(() -> apply(mapper.writeValueAsBytes(bad))).isInstanceOf(com.mediflow.report.domain.exception.ReportRuleException.class);
        assertThat(hospital()).isEqualByComparingTo("40"); assertThat(count("report_settlement_delivery")).isOne();
    }
    @Test void apply_secondScopeFailure_rollsBackSourceDeliveryAndHospitalThenRetry() throws Exception {
        jdbc.execute("ALTER TABLE report_receivable_stock ADD CONSTRAINT test_scope_failure CHECK(department_id IS NULL)");
        try {
            assertThatThrownBy(() -> apply(fixture(1))).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(count("report_settlement_snapshot")).isZero(); assertThat(count("report_settlement_delivery")).isZero(); assertThat(count("report_receivable_stock")).isZero();
        } finally { jdbc.execute("ALTER TABLE report_receivable_stock DROP CONSTRAINT test_scope_failure"); }
        apply(fixture(1)); assertThat(hospital()).isEqualByComparingTo("40");
    }
    @Test void apply_concurrentReplicasAndDifferentRevisions_latestWins() throws Exception {
        byte[] first=fixture(1), second=fixture(2), last=fixture(3);
        try(var pool=Executors.newFixedThreadPool(6)) {
            var results=new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for(byte[] bytes:new byte[][]{last,first,second,last,first,second}) results.add(pool.submit(() -> apply(bytes)));
            for(var result:results) result.get(20,TimeUnit.SECONDS);
        }
        assertThat(hospital()).isEqualByComparingTo("15"); assertThat(count("report_settlement_snapshot")).isEqualTo(3);
    }
    @Test void apply_sameRevisionDifferentSourceId_rejectsWithoutOverwrite() throws Exception {
        apply(fixture(1)); var bad=tree(1); bad.put("eventId",UUID.randomUUID().toString()); ((ObjectNode)bad.get("payload")).put("settlementId",UUID.randomUUID().toString());
        assertThatThrownBy(() -> apply(mapper.writeValueAsBytes(bad))).isInstanceOf(com.mediflow.report.domain.exception.ReportRuleException.class);
        assertThat(count("report_settlement_snapshot")).isOne(); assertThat(hospital()).isEqualByComparingTo("40");
    }
    @Test void apply_differentCurrencyStock_neverAddsVndAndUsd() throws Exception {
        apply(fixture(1)); var usd=tree(1); usd.put("eventId",UUID.randomUUID().toString()); var p=(ObjectNode)usd.get("payload");
        for(String field:new String[]{"settlementId","accountId","admissionId","patientId"}) p.put(field,UUID.randomUUID().toString());
        p.put("currency","USD"); apply(mapper.writeValueAsBytes(usd));
        assertThat(jdbc.queryForList("SELECT outstanding_receivable FROM report_receivable_stock WHERE department_id IS NULL",BigDecimal.class))
                .hasSize(2).containsOnly(new BigDecimal("40.00"));
        assertThat(count("report_receivable_current")).isEqualTo(2);
    }
    @Test void apply_nonAdjacentEarlierRevisionWithLaterBusinessTime_rejects() throws Exception {
        apply(fixture(3)); var bad=tree(1); String time="2026-10-10T10:03:00Z";
        bad.put("occurredAt",time); ((ObjectNode)bad.get("payload")).put("reconciledAt",time);
        assertThatThrownBy(() -> apply(mapper.writeValueAsBytes(bad))).isInstanceOf(com.mediflow.report.domain.exception.ReportRuleException.class);
        assertThat(count("report_settlement_snapshot")).isOne(); assertThat(hospital()).isEqualByComparingTo("15");
    }
    @Test void apply_corruptedRetainedSnapshot_rejectsInsteadOfSubtractingFalseBalance() throws Exception {
        apply(fixture(1));
        jdbc.update("UPDATE report_settlement_snapshot SET snapshot_fingerprint=?", "0".repeat(64));
        assertThatThrownBy(() -> apply(fixture(2))).isInstanceOf(com.mediflow.report.domain.exception.ReportRuleException.class);
        assertThat(hospital()).isEqualByComparingTo("40"); assertThat(count("report_settlement_delivery")).isOne();
    }
    @ParameterizedTest @ValueSource(strings={"1234","4321","2413","3142"})
    void apply_refundDueFullSnapshot_setsReceivableZeroNotNegativeOrAnotherCashRefund(String order) throws Exception {
        for(char c:order.toCharArray()) apply(fixture(c-'0'));
        assertThat(hospital()).isZero(); assertThat(count("report_settlement_snapshot")).isEqualTo(4);
        assertThat(count("report_cash_refund")).isZero(); assertThat(count("report_gross_cash_daily")).isZero();
        assertThat(jdbc.queryForObject("SELECT settlement_version FROM report_receivable_current",Integer.class)).isEqualTo(4);
    }
    @Test void apply_cycleWithKnownLaterPredecessor_rejectsImmediately() throws Exception {
        apply(fixture(3)); var bad=tree(2);
        ((ObjectNode)bad.get("payload")).put("supersedesSettlementId",tree(3).path("payload").path("settlementId").textValue());
        assertThatThrownBy(()->apply(mapper.writeValueAsBytes(bad))).isInstanceOf(com.mediflow.report.domain.exception.ReportRuleException.class);
        assertThat(count("report_settlement_snapshot")).isOne(); assertThat(hospital()).isEqualByComparingTo("15");
    }
    @Test void apply_twoSnapshotsClaimSameMissingPredecessor_rejectsWithoutWaitingForItsArrival() throws Exception {
        apply(fixture(3)); var bad=tree(4);
        ((ObjectNode)bad.get("payload")).put("supersedesSettlementId",tree(3).path("payload").path("supersedesSettlementId").textValue());
        assertThatThrownBy(()->apply(mapper.writeValueAsBytes(bad))).isInstanceOf(com.mediflow.report.domain.exception.ReportRuleException.class);
        assertThat(count("report_settlement_snapshot")).isOne(); assertThat(hospital()).isEqualByComparingTo("15");
    }
}
