package com.mediflow.report.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.CareFinanceEventMetadata;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.dto.response.CashReplayProgress.Status;
import com.mediflow.report.application.mapper.BillingCashReceiptMapper;
import com.mediflow.report.application.service.CashReceiptApplicationService;
import com.mediflow.report.application.service.CashReplayApplicationService;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;
import com.mediflow.report.infrastructure.persistence.adapter.CashReceiptPersistenceAdapter;
import com.mediflow.report.infrastructure.persistence.adapter.CashReplayPersistenceAdapter;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CashReceiptApplicationService.class, CashReceiptPersistenceAdapter.class, CashReplayApplicationService.class,
        CashReplayPersistenceAdapter.class, CashReplayPostgresTest.Config.class,
        com.mediflow.report.application.service.CashRefundApplicationService.class,
        com.mediflow.report.infrastructure.persistence.adapter.CashRefundPersistenceAdapter.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CashReplayPostgresTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired CashReceiptApplicationService live;
    @Autowired CashReplayApplicationService replay;
    @Autowired CashReplayPersistenceAdapter store;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE cash_replay_generation,report_cash_delivery,report_cash_receipt,report_gross_cash_daily CASCADE");
    }

    @Test
    void actualBillingReceipts_rebuildFromEmptyIsolatedGenerationAndDedupeDeliveries() throws Exception {
        var service = fixture("service");
        live.apply(service); live.apply(service);
        live.apply(changed(service, service.metadata().sourceId(), "100.00", "VND"));
        live.apply(fixture("deposit"));
        var generation = replay.start();
        assertThat(generation.sourceReceipts()).isEqualTo(2);
        assertThat(count("cash_replay_receipt")).isZero();
        assertThat(replay.advance(generation.generationId(), 1).appliedReceipts()).isOne();
        assertThat(replay.advance(generation.generationId(), 1).status()).isEqualTo(Status.VERIFIED);
        assertThat(count("cash_replay_receipt")).isEqualTo(2);
        assertThat(count("cash_replay_scope")).isEqualTo(4);
        assertThat(jdbc.queryForList("SELECT gross_receipts FROM cash_replay_scope", BigDecimal.class))
                .containsOnly(new BigDecimal("100.00"));
        assertThat(count("report_cash_delivery")).isEqualTo(3);
        assertThat(count("report_cash_receipt")).isEqualTo(2);
        assertThat(count("financial_contribution")).isZero();
        assertThat(count("daily_financial_report")).isZero();
        assertThat(count("operational_report_publication")).isZero();
        assertThat(jdbc.queryForList("SELECT fact_snapshot::text FROM cash_replay_input", String.class))
                .allSatisfy(json -> assertThat(json).doesNotContain("prescriptionId", "labTestIds", "diagnosis", "eventType"));
    }

    @Test
    void twoPartialReceiptsAndAnotherCurrency_preserveExactTotalsNotInvoiceUniqueness() throws Exception {
        // Consumer mutation scenarios; not new producer fixtures or settlement acceptance.
        var original = fixture("service");
        live.apply(changed(original, UUID.randomUUID(), "60.01", "VND"));
        live.apply(changed(original, UUID.randomUUID(), "39.99", "VND"));
        live.apply(changed(original, UUID.randomUUID(), "25.15", "USD"));
        var generation = replay.start();
        assertThat(replay.advance(generation.generationId(), 500).status()).isEqualTo(Status.VERIFIED);
        assertThat(count("cash_replay_receipt")).isEqualTo(3);
        assertThat(total(generation.generationId(), "VND")).isEqualByComparingTo("100.00");
        assertThat(total(generation.generationId(), "USD")).isEqualByComparingTo("25.15");
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT fact_snapshot->>'invoiceId') FROM cash_replay_receipt", Integer.class))
                .isOne();
    }

    @Test
    void frozenManifest_excludesLaterLiveReceiptsAndDoesNotCompareAgainstAdvancingLiveTotals() throws Exception {
        var original = fixture("service"); live.apply(original);
        var generation = replay.start();
        live.apply(changed(original, UUID.randomUUID(), "25.00", "VND"));
        assertThat(replay.advance(generation.generationId(), 1).status()).isEqualTo(Status.VERIFIED);
        assertThat(total(generation.generationId(), "VND")).isEqualByComparingTo("100.00");
        assertThat(jdbc.queryForObject("SELECT sum(gross_receipts) FROM report_gross_cash_daily WHERE department_id IS NULL",
                BigDecimal.class)).isEqualByComparingTo("125.00");
        assertThat(generation.sourceReceipts()).isOne();
    }

    @Test
    void oldUncommittedReceipt_doesNotJoinManifestEvenWhenItCommitsAfterFreeze() throws Exception {
        var event = fixture("service");
        var written = new CountDownLatch(1); var commit = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var pending = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                live.apply(event); written.countDown(); await(commit);
            }));
            assertThat(written.await(15, TimeUnit.SECONDS)).isTrue();
            var generation = replay.start();
            commit.countDown(); pending.get(20, TimeUnit.SECONDS);
            assertThat(generation.sourceReceipts()).isZero();
            assertThat(generation.status()).isEqualTo(Status.VERIFIED);
            assertThat(count("cash_replay_input")).isZero();
            assertThat(count("report_cash_receipt")).isOne();
            assertThat(count("operational_report_publication")).isZero();
        } finally { commit.countDown(); }
    }

    @Test
    void secondScopeFailure_rollsBackWholeBatchAndProgressThenCanResume() throws Exception {
        var event = fixture("service"); live.apply(event);
        live.apply(changed(event, UUID.randomUUID(), "40.00", "VND"));
        UUID id = replay.start().generationId();
        jdbc.execute("ALTER TABLE cash_replay_scope ADD CONSTRAINT test_cash_scope_failure CHECK (department_id IS NULL)");
        try {
            assertThatThrownBy(() -> replay.advance(id, 2)).isInstanceOf(RuntimeException.class);
            assertThat(count("cash_replay_receipt")).isZero();
            assertThat(count("cash_replay_scope")).isZero();
            assertThat(replay.progress(id).appliedReceipts()).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM cash_replay_input WHERE applied", Integer.class)).isZero();
        } finally { jdbc.execute("ALTER TABLE cash_replay_scope DROP CONSTRAINT test_cash_scope_failure"); }
        assertThat(replay.advance(id, 2).status()).isEqualTo(Status.VERIFIED);
        assertThat(total(id, "VND")).isEqualByComparingTo("140.00");
    }

    @Test
    void concurrentWorkers_serializeGenerationAndNeverDoubleItsMoney() throws Exception {
        var event = fixture("service");
        for (int item = 0; item < 4; item++) live.apply(changed(event, UUID.randomUUID(), "10.00", "VND"));
        UUID id = replay.start().generationId();
        var go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(4)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int worker = 0; worker < 4; worker++) tasks.add(pool.submit(() -> { await(go); return replay.advance(id, 1); }));
            go.countDown(); for (var task : tasks) task.get(30, TimeUnit.SECONDS);
        }
        assertThat(replay.progress(id).status()).isEqualTo(Status.VERIFIED);
        assertThat(replay.progress(id).appliedReceipts()).isEqualTo(4);
        assertThat(count("cash_replay_receipt")).isEqualTo(4);
        assertThat(total(id, "VND")).isEqualByComparingTo("40.00");
        assertThat(jdbc.queryForList("SELECT receipt_count FROM cash_replay_scope", Long.class)).containsOnly(4L);
    }

    @Test
    void serviceInstanceReplacement_resumesExactManifestWithoutNewSourceOrLiveEffects() throws Exception {
        var event = fixture("service"); live.apply(event);
        live.apply(changed(event, UUID.randomUUID(), "40.00", "VND"));
        UUID id = replay.start().generationId(); replay.advance(id, 1);
        var replaced = new CashReplayApplicationService(new CashReplayPersistenceAdapter(jdbc, new ObjectMapper()));
        var progress = new TransactionTemplate(transactionManager).execute(status -> replaced.advance(id, 1));
        assertThat(progress.status()).isEqualTo(Status.VERIFIED);
        assertThat(total(id, "VND")).isEqualByComparingTo("140.00");
        assertThat(count("report_cash_receipt")).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(strings = {"fingerprint", "version", "snapshot"})
    void corruptFrozenInput_rejectsBeforeAnyFactScopeOrProgress(String failure) throws Exception {
        live.apply(fixture("service")); UUID id = replay.start().generationId();
        switch (failure) {
            case "fingerprint" -> jdbc.update("UPDATE cash_replay_input SET fact_fingerprint=?", "0".repeat(64));
            case "version" -> jdbc.update("UPDATE cash_replay_input SET snapshot_version=2");
            case "snapshot" -> jdbc.update("UPDATE cash_replay_input SET fact_snapshot=jsonb_set(fact_snapshot,'{departmentId}',to_jsonb(?::text))",
                    UUID.randomUUID().toString());
            default -> throw new IllegalArgumentException("Unknown test failure");
        }
        assertThatThrownBy(() -> replay.advance(id, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(count("cash_replay_receipt")).isZero();
        assertThat(count("cash_replay_scope")).isZero();
        assertThat(replay.progress(id).appliedReceipts()).isZero();
        assertThat(replay.progress(id).status()).isEqualTo(Status.BUILDING);
    }

    @Test
    void missingFirstDeliveryProof_failsFreezeInsteadOfSilentlyDroppingTheReceipt() throws Exception {
        live.apply(fixture("service"));
        jdbc.execute("DELETE FROM report_cash_delivery"); // fault injection into this test's temporary DB only
        assertThatThrownBy(() -> replay.start()).isInstanceOf(RuntimeException.class);
        assertThat(count("cash_replay_generation")).isZero();
        assertThat(count("cash_replay_input")).isZero();
        assertThat(count("report_cash_receipt")).isOne();
    }

    @ParameterizedTest @ValueSource(strings = {"money", "count", "currency", "proof", "extraFact"})
    void reconciliation_checksBothDirectionsFullSourceProofAndScopeDimensions(String mismatch) throws Exception {
        var event = fixture("service"); live.apply(event);
        live.apply(changed(event, UUID.randomUUID(), "40.00", "VND"));
        UUID id = replay.start().generationId(); replay.advance(id, 1);
        switch (mismatch) {
            case "money" -> jdbc.update("UPDATE cash_replay_scope SET gross_receipts=gross_receipts+1 WHERE generation_id=?", id);
            case "count" -> jdbc.update("UPDATE cash_replay_scope SET receipt_count=receipt_count+1 WHERE generation_id=?", id);
            case "currency" -> jdbc.update("UPDATE cash_replay_scope SET currency='USD' WHERE generation_id=?", id);
            case "proof" -> jdbc.update("UPDATE cash_replay_receipt SET payload_fingerprint=? WHERE generation_id=?", "0".repeat(64), id);
            case "extraFact" -> jdbc.update("""
                    INSERT INTO cash_replay_receipt SELECT generation_id,?,first_event_id,fact_snapshot,
                        fact_fingerprint,payload_fingerprint,first_envelope_fingerprint
                    FROM cash_replay_receipt WHERE generation_id=?
                    """, UUID.randomUUID(), id);
            default -> throw new IllegalArgumentException("Unknown test mismatch");
        }
        assertThat(replay.advance(id, 1).status()).isEqualTo(Status.FAILED);
        assertThat(replay.advance(id, 1).status()).isEqualTo(Status.FAILED);
        assertThat(jdbc.queryForObject("SELECT sum(gross_receipts) FROM report_gross_cash_daily WHERE department_id IS NULL",
                BigDecimal.class)).isEqualByComparingTo("140.00");
        assertThat(count("operational_report_publication")).isZero();
    }

    @Test
    void exhaustedManifestWithProgressDrift_failsInsteadOfRemainingBuildingForever() throws Exception {
        live.apply(fixture("service")); UUID id = replay.start().generationId();
        jdbc.update("UPDATE cash_replay_input SET applied=true WHERE generation_id=?", id);
        assertThat(replay.advance(id, 1).status()).isEqualTo(Status.FAILED);
        assertThat(count("cash_replay_receipt")).isZero();
    }

    @Test
    void separateGenerations_haveSeparateDedupeAndTerminalRetryHasNoEffect() throws Exception {
        live.apply(fixture("service"));
        UUID one = replay.start().generationId(); UUID two = replay.start().generationId();
        assertThat(replay.advance(one, 500).status()).isEqualTo(Status.VERIFIED);
        assertThat(replay.advance(two, 500).status()).isEqualTo(Status.VERIFIED);
        assertThat(replay.advance(one, 1).status()).isEqualTo(Status.VERIFIED);
        assertThat(count("cash_replay_receipt")).isEqualTo(2);
        assertThat(total(one, "VND")).isEqualByComparingTo("100.00");
        assertThat(total(two, "VND")).isEqualByComparingTo("100.00");
        assertThat(count("report_cash_receipt")).isOne();
    }

    @Test
    void differentBatchAndInsertionOrders_reconcileIdenticalMultiDimensionFactAndScopeSets() throws Exception {
        var service = fixture("service");
        var anotherAccount = changed(service, UUID.randomUUID(), "3.15", "USD");
        var dimensions = new LinkedHashMap<>(anotherAccount.payload());
        dimensions.put("departmentId", UUID.randomUUID().toString());
        dimensions.put("accountId", UUID.randomUUID().toString());
        anotherAccount = new DecodedCareFinanceEvent(anotherAccount.metadata(), dimensions);
        var earlier = changed(service, UUID.randomUUID(), "7.01", "VND");
        dimensions = new LinkedHashMap<>(earlier.payload()); dimensions.put("completedAt", "2026-09-30T18:30:00.123456789Z");
        earlier = new DecodedCareFinanceEvent(earlier.metadata(), dimensions);
        // Reverse business/date/currency order; semantic IDs, not insertion order, govern the manifest.
        live.apply(anotherAccount); live.apply(fixture("deposit")); live.apply(earlier);
        live.apply(changed(service, UUID.randomUUID(), "60.01", "VND"));
        live.apply(changed(service, UUID.randomUUID(), "39.99", "VND"));
        UUID one = replay.start().generationId(); UUID two = replay.start().generationId();
        for (int batch = 0; batch < 5; batch++) replay.advance(one, 1);
        assertThat(replay.progress(one).status()).isEqualTo(Status.VERIFIED);
        assertThat(replay.advance(two, 500).status()).isEqualTo(Status.VERIFIED);
        assertThat(jdbc.queryForObject("""
                WITH one AS (SELECT business_date,currency,report_zone,department_id,classification,gross_receipts,receipt_count
                    FROM cash_replay_scope WHERE generation_id=?), two AS (
                    SELECT business_date,currency,report_zone,department_id,classification,gross_receipts,receipt_count
                    FROM cash_replay_scope WHERE generation_id=?)
                SELECT NOT EXISTS ((SELECT * FROM one EXCEPT SELECT * FROM two)
                    UNION ALL (SELECT * FROM two EXCEPT SELECT * FROM one))
                """, Boolean.class, one, two)).isTrue();
        assertThat(jdbc.queryForObject("""
                WITH one AS (SELECT transaction_id,first_event_id,fact_snapshot,fact_fingerprint,payload_fingerprint,
                    first_envelope_fingerprint FROM cash_replay_receipt WHERE generation_id=?), two AS (
                    SELECT transaction_id,first_event_id,fact_snapshot,fact_fingerprint,payload_fingerprint,
                    first_envelope_fingerprint FROM cash_replay_receipt WHERE generation_id=?)
                SELECT NOT EXISTS ((SELECT * FROM one EXCEPT SELECT * FROM two)
                    UNION ALL (SELECT * FROM two EXCEPT SELECT * FROM one))
                """, Boolean.class, one, two)).isTrue();
        assertThat(count("report_cash_receipt")).isEqualTo(5);
        assertThat(count("operational_report_publication")).isZero();
    }

    @Test
    void adapters_requireCallerTransactionAndStartRollsBackWithCaller() throws Exception {
        assertThatThrownBy(() -> store.freeze(UUID.randomUUID())).hasMessageContaining("transaction");
        live.apply(fixture("service"));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> { replay.start(); status.setRollbackOnly(); });
        assertThat(count("cash_replay_generation")).isZero();
        assertThat(count("cash_replay_input")).isZero();
        assertThatThrownBy(() -> replay.progress(UUID.randomUUID())).hasMessageContaining("not found");
    }

    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }

    private BigDecimal total(UUID id, String currency) {
        return jdbc.queryForObject("SELECT sum(gross_receipts) FROM cash_replay_scope WHERE generation_id=? AND currency=? AND department_id IS NULL",
                BigDecimal.class, id, currency);
    }

    private static DecodedCareFinanceEvent fixture(String type) throws Exception {
        return new CareFinanceEnvelopeDecoder(new ObjectMapper()).decode("payment.completed", Files.readAllBytes(Path.of(
                "../billing-service/src/test/resources/contracts/ledger-v1/payment-" + type + ".json")));
    }

    private static DecodedCareFinanceEvent changed(DecodedCareFinanceEvent event, UUID transaction, String amount, String currency) {
        var metadata = event.metadata(); var payload = new LinkedHashMap<>(event.payload());
        payload.put("transactionId", transaction.toString()); payload.put("totalAmount", new BigDecimal(amount)); payload.put("currency", currency);
        return new DecodedCareFinanceEvent(new CareFinanceEventMetadata(UUID.randomUUID(), metadata.eventType(), 1,
                metadata.occurredAt(), metadata.correlationId(), metadata.producer(), metadata.sourceField(), transaction), payload);
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Latch timeout"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }

    @TestConfiguration
    static class Config {
        @Bean com.mediflow.report.application.mapper.BillingCashRefundMapper billingCashRefundMapper() {
            return new com.mediflow.report.application.mapper.BillingCashRefundMapper(ZoneId.of("Asia/Bangkok"));
        }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean BillingCashReceiptMapper billingCashReceiptMapper() { return new BillingCashReceiptMapper(ZoneId.of("Asia/Bangkok")); }
    }
}
