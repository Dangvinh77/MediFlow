package com.mediflow.report.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.dto.response.CashReplayProgress.Status;
import com.mediflow.report.application.mapper.BillingCashReceiptMapper;
import com.mediflow.report.application.mapper.BillingCashRefundMapper;
import com.mediflow.report.application.service.*;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;
import com.mediflow.report.infrastructure.persistence.adapter.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CashReceiptApplicationService.class,CashReceiptPersistenceAdapter.class,CashRefundApplicationService.class,
        CashRefundPersistenceAdapter.class,CashReplayApplicationService.class,CashReplayPersistenceAdapter.class,
        RefundReplayApplicationService.class,RefundReplayPersistenceAdapter.class,RefundReplayPostgresTest.Config.class})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RefundReplayPostgresTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired CashReceiptApplicationService receipts;
    @Autowired CashRefundApplicationService refunds;
    @Autowired RefundReplayApplicationService replay;
    @Autowired CashReplayApplicationService grossReplay;
    @Autowired RefundReplayPersistenceAdapter store;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @BeforeEach void clean() {
        jdbc.execute("TRUNCATE cash_replay_generation,report_cash_receipt,report_cash_delivery,report_gross_cash_daily,report_cash_refund,report_refund_cash_daily CASCADE");
    }
    @Test void realServiceAndDepositBytes_rebuildBothCashScopesWithoutEarnedRevenueOrPublication() throws Exception {
        receipts.apply(fixture("payment-service")); refunds.apply(fixture("refund-service"));
        receipts.apply(fixture("payment-deposit")); refunds.apply(fixture("refund-deposit"));
        UUID id=replay.start().generationId();
        assertThat(replay.advance(id,1).status()).isEqualTo(Status.BUILDING);
        assertThat(replay.advance(id,1).processedRefunds()).isOne();
        assertThat(replay.advance(id,1).status()).isEqualTo(Status.VERIFIED);
        assertThat(count("refund_replay_fact")).isEqualTo(2);
        assertThat(count("refund_replay_scope")).isEqualTo(4);
        assertThat(total(id)).isEqualByComparingTo("40.00");
        assertThat(jdbc.queryForObject("SELECT SUM(gross_receipts) FROM cash_replay_scope WHERE department_id IS NULL",BigDecimal.class)).isEqualByComparingTo("200");
        assertThat(count("daily_financial_report")).isZero(); assertThat(count("operational_report_publication")).isZero();
        assertThat(replay.advance(id,500)).isEqualTo(replay.progress(id));
        assertThat(count("report_cash_refund")).isEqualTo(2);
    }
    @Test void earlyRefund_snapshotStaysPendingAfterLiveOriginalRecovery_newGenerationReflectsNewState() throws Exception {
        refunds.apply(fixture("refund-service"));
        UUID old=replay.start().generationId();
        receipts.apply(fixture("payment-service"));
        assertThat(replay.advance(old,500).status()).isEqualTo(Status.VERIFIED);
        assertThat(replay.progress(old).pendingRefunds()).isOne(); assertThat(total(old)).isNull();
        UUID fresh=replay.start().generationId();
        assertThat(replay.advance(fresh,500).status()).isEqualTo(Status.VERIFIED);
        assertThat(replay.progress(fresh).pendingRefunds()).isZero(); assertThat(total(fresh)).isEqualByComparingTo("20");
        assertThat(jdbc.queryForObject("SELECT state FROM refund_replay_fact WHERE generation_id=?",String.class,old)).isEqualTo("PENDING");
    }
    @Test void rejectedEarlyRefund_preservesReasonAndHasNoCashEffect() throws Exception {
        var root=new ObjectMapper().readTree(bytes("refund-service"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)root.get("payload")).put("amount",new BigDecimal("101"));
        refunds.apply(new CareFinanceEnvelopeDecoder(new ObjectMapper()).decode("payment.refunded",new ObjectMapper().writeValueAsBytes(root)));
        receipts.apply(fixture("payment-service"));
        UUID id=replay.start().generationId();
        assertThat(replay.advance(id,500).status()).isEqualTo(Status.VERIFIED);
        assertThat(replay.progress(id).rejectedRefunds()).isOne(); assertThat(total(id)).isNull();
        assertThat(jdbc.queryForObject("SELECT fact_snapshot->>'reasonCode' FROM refund_replay_fact",String.class)).isEqualTo("CASH_REFUND_EXCEEDS_ORIGINAL");
    }
    @Test void frozenPair_excludesLateRefundAndLiveTotalsAreNotItsReconciliationSource() throws Exception {
        receipts.apply(fixture("payment-service")); UUID id=replay.start().generationId();
        refunds.apply(fixture("refund-service"));
        assertThat(replay.advance(id,500).status()).isEqualTo(Status.VERIFIED); assertThat(total(id)).isNull();
        assertThat(jdbc.queryForObject("SELECT SUM(completed_refunds) FROM report_refund_cash_daily WHERE department_id IS NULL",BigDecimal.class)).isEqualByComparingTo("20");
    }
    @Test void partialRefunds_sameOriginalAndNewDeliveries_rebuildOnceWithExactNanoseconds() throws Exception {
        receipts.apply(fixture("payment-service")); refunds.apply(fixture("refund-service"));
        var mapper=new ObjectMapper(); var root=mapper.readTree(bytes("refund-service"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)root).put("eventId",UUID.randomUUID().toString());
        refunds.apply(new CareFinanceEnvelopeDecoder(mapper).decode("payment.refunded",mapper.writeValueAsBytes(root)));
        ((com.fasterxml.jackson.databind.node.ObjectNode)root).put("eventId",UUID.randomUUID().toString()).put("occurredAt","2026-10-08T04:00:00.123456789Z");
        ((com.fasterxml.jackson.databind.node.ObjectNode)root.get("payload")).put("refundTransactionId",UUID.randomUUID().toString())
                .put("amount",new BigDecimal("80.00")).put("completedAt","2026-10-08T04:00:00.123456789Z");
        refunds.apply(new CareFinanceEnvelopeDecoder(mapper).decode("payment.refunded",mapper.writeValueAsBytes(root)));
        UUID id=replay.start().generationId(); replay.advance(id,1);
        assertThat(replay.advance(id,1).status()).isEqualTo(Status.VERIFIED);
        assertThat(total(id)).isEqualByComparingTo("100");
        assertThat(count("refund_replay_fact")).isEqualTo(2); assertThat(count("report_cash_refund_delivery")).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT fact_snapshot->>'completedAt' FROM refund_replay_fact",String.class))
                .contains("2026-10-08T04:00:00.123456789Z");
    }
    @Test void secondRefundScopeFailure_rollsBackReceiptReplayRefundFactsAndProgressThenResumes() throws Exception {
        receipts.apply(fixture("payment-service")); refunds.apply(fixture("refund-service")); UUID id=replay.start().generationId();
        jdbc.execute("ALTER TABLE refund_replay_scope ADD CONSTRAINT test_refund_scope CHECK (department_id IS NULL)");
        try {
            assertThatThrownBy(()->replay.advance(id,500)).isInstanceOf(RuntimeException.class);
            assertThat(count("cash_replay_receipt")).isZero(); assertThat(count("refund_replay_fact")).isZero();
            assertThat(count("refund_replay_scope")).isZero(); assertThat(replay.progress(id).processedRefunds()).isZero();
        } finally { jdbc.execute("ALTER TABLE refund_replay_scope DROP CONSTRAINT test_refund_scope"); }
        assertThat(replay.advance(id,500).status()).isEqualTo(Status.VERIFIED); assertThat(total(id)).isEqualByComparingTo("20");
    }
    @Test void missingFirstDeliveryProof_abortsBothManifestsWithoutExcludingBadSource() throws Exception {
        receipts.apply(fixture("payment-service")); refunds.apply(fixture("refund-service")); jdbc.execute("DELETE FROM report_cash_refund_delivery");
        assertThatThrownBy(()->replay.start()).isInstanceOf(RuntimeException.class);
        assertThat(count("cash_replay_generation")).isZero(); assertThat(count("refund_replay_generation")).isZero();
    }
    @Test void corruptFrozenRefundHash_rejectsBeforeBatchEffectsAndRemainsResumable() throws Exception {
        receipts.apply(fixture("payment-service")); refunds.apply(fixture("refund-service")); UUID id=replay.start().generationId();
        jdbc.update("UPDATE refund_replay_input SET fact_snapshot=jsonb_set(fact_snapshot,'{amount}','19') WHERE generation_id=?",id);
        assertThatThrownBy(()->replay.advance(id,500)).hasMessageContaining("Corrupt frozen refund hash");
        assertThat(count("cash_replay_receipt")).isZero(); assertThat(count("refund_replay_fact")).isZero();
    }
    @Test void reconcile_extraIsolatedScope_failsWithoutChangingLiveOrPublishing() throws Exception {
        refunds.apply(fixture("refund-service")); UUID id=replay.start().generationId();
        jdbc.update("INSERT INTO refund_replay_scope VALUES (?,DATE '2026-10-08','VND','Asia/Bangkok',NULL,'SERVICE_PAYMENT',1,1)",id);
        assertThat(replay.advance(id,500).status()).isEqualTo(Status.FAILED);
        assertThat(count("report_cash_refund")).isOne(); assertThat(count("operational_report_publication")).isZero();
    }
    @Test void twoWorkers_generationRowFence_preventsDoubleRefundCash() throws Exception {
        receipts.apply(fixture("payment-service")); refunds.apply(fixture("refund-service")); UUID id=replay.start().generationId();
        var go=new CountDownLatch(1);
        try (var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{ go.await(); return replay.advance(id,500); });
            var b=pool.submit(()->{ go.await(); return replay.advance(id,500); }); go.countDown();
            assertThat(a.get(20,TimeUnit.SECONDS).status()).isEqualTo(Status.VERIFIED);
            assertThat(b.get(20,TimeUnit.SECONDS).status()).isEqualTo(Status.VERIFIED);
        }
        assertThat(count("refund_replay_fact")).isOne(); assertThat(total(id)).isEqualByComparingTo("20");
    }
    @Test void oldGrossOnlyGeneration_remainsReceiptOnlyAndCanResume() throws Exception {
        receipts.apply(fixture("payment-service")); refunds.apply(fixture("refund-service")); var old=grossReplay.start();
        assertThat(grossReplay.advance(old.generationId(),500).status()).isEqualTo(Status.VERIFIED);
        assertThat(count("refund_replay_generation")).isZero(); assertThat(count("refund_replay_fact")).isZero();
    }
    @Test void readCommittedOuterCaller_isRejectedInsteadOfMixingReceiptRefundSnapshots() {
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->replay.start()))
                .hasMessageContaining("Paired replay needs repeatable read");
        assertThat(count("cash_replay_generation")).isZero();
    }
    @Test void pairedFreeze_refundCommittedBetweenStatements_doesNotEnterEarlierReceiptSnapshot() throws Exception {
        receipts.apply(fixture("payment-service"));
        jdbc.execute("CREATE FUNCTION test_pause_refund_freeze() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN PERFORM pg_advisory_xact_lock(9217530); RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER test_pause_refund_freeze BEFORE INSERT ON refund_replay_generation FOR EACH ROW EXECUTE FUNCTION test_pause_refund_freeze()");
        try (var controller=jdbc.getDataSource().getConnection(); var pool=Executors.newSingleThreadExecutor()) {
            try {
                controller.createStatement().execute("SELECT pg_advisory_lock(9217530)");
                var frozen=pool.submit(replay::start);
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15)).until(()->
                        Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE locktype='advisory' AND objid=9217530 AND NOT granted)",Boolean.class)));
                refunds.apply(fixture("refund-service"));
                controller.createStatement().execute("SELECT pg_advisory_unlock(9217530)");
                var run=frozen.get(20,TimeUnit.SECONDS);
                assertThat(run.sourceRefunds()).isZero();
                assertThat(replay.advance(run.generationId(),500).status()).isEqualTo(Status.VERIFIED);
                assertThat(total(run.generationId())).isNull();
                assertThat(count("report_cash_refund")).isOne();
            } finally { controller.createStatement().execute("SELECT pg_advisory_unlock(9217530)"); }
        } finally {
            jdbc.execute("DROP TRIGGER test_pause_refund_freeze ON refund_replay_generation");
            jdbc.execute("DROP FUNCTION test_pause_refund_freeze()");
        }
    }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class); }
    private BigDecimal total(UUID id) { return jdbc.queryForObject("SELECT SUM(completed_refunds) FROM refund_replay_scope WHERE generation_id=? AND department_id IS NULL",BigDecimal.class,id); }
    private static byte[] bytes(String name) throws Exception { return Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/"+name+".json")); }
    private static DecodedCareFinanceEvent fixture(String name) throws Exception {
        return new CareFinanceEnvelopeDecoder(new ObjectMapper()).decode(name.startsWith("refund")?"payment.refunded":"payment.completed",bytes(name));
    }
    @TestConfiguration static class Config {
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
        @Bean BillingCashReceiptMapper receiptMapper() { return new BillingCashReceiptMapper(ZoneId.of("Asia/Bangkok")); }
        @Bean BillingCashRefundMapper refundMapper() { return new BillingCashRefundMapper(ZoneId.of("Asia/Bangkok")); }
    }
}
