package com.mediflow.report.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.dto.response.FinancialRebuildProgress.Status;
import com.mediflow.report.application.mapper.BillingCashReceiptMapper;
import com.mediflow.report.application.mapper.BillingCashRefundMapper;
import com.mediflow.report.application.mapper.BillingFinalSettlementRecognitionMapper;
import com.mediflow.report.application.mapper.BillingSettlementSnapshotMapper;
import com.mediflow.report.application.service.CashReceiptApplicationService;
import com.mediflow.report.application.service.CashRefundApplicationService;
import com.mediflow.report.application.service.FinalSettlementRecognitionApplicationService;
import com.mediflow.report.application.service.FinancialRebuildApplicationService;
import com.mediflow.report.application.service.SettlementSnapshotApplicationService;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;
import com.mediflow.report.infrastructure.persistence.adapter.CashReceiptPersistenceAdapter;
import com.mediflow.report.infrastructure.persistence.adapter.CashRefundPersistenceAdapter;
import com.mediflow.report.infrastructure.persistence.adapter.FinalSettlementRecognitionStoreAdapter;
import com.mediflow.report.infrastructure.persistence.adapter.FinancialRebuildPersistenceAdapter;
import com.mediflow.report.infrastructure.persistence.adapter.FinancialArchiveJsonCodec;
import com.mediflow.report.application.service.FinancialSourceArchiveApplicationService;
import com.mediflow.report.domain.model.FinancialEvidenceArchive;
import com.mediflow.report.infrastructure.persistence.adapter.SettlementReceivableStoreAdapter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({FinancialRebuildApplicationService.class,FinancialRebuildPersistenceAdapter.class,
    FinancialSourceArchiveApplicationService.class, FinancialArchiveJsonCodec.class,
    CashReceiptApplicationService.class,CashReceiptPersistenceAdapter.class,CashRefundApplicationService.class,CashRefundPersistenceAdapter.class,
    FinalSettlementRecognitionApplicationService.class,FinalSettlementRecognitionStoreAdapter.class,
    SettlementSnapshotApplicationService.class,SettlementReceivableStoreAdapter.class,FinancialRebuildPostgresTest.Config.class})
@Testcontainers
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class FinancialRebuildPostgresTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final LocalDate FROM = LocalDate.of(2026,10,9), TO = LocalDate.of(2026,10,10);
    @Autowired FinancialRebuildApplicationService rebuild;
    @Autowired FinancialRebuildPersistenceAdapter store;
    @Autowired FinancialSourceArchiveApplicationService archives;
    @Autowired CashReceiptApplicationService receipts;
    @Autowired CashRefundApplicationService refunds;
    @Autowired FinalSettlementRecognitionApplicationService finals;
    @Autowired SettlementSnapshotApplicationService balances;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach void clean() {
        jdbc.execute("TRUNCATE financial_source_rebuild,report_final_recognition,report_final_recognition_daily,report_cash_receipt,report_gross_cash_daily,report_cash_refund,report_refund_cash_daily,report_settlement_snapshot,report_receivable_current,report_receivable_stock CASCADE");
    }
    @Test void archive_actualBillingEvidence_restoresOnEmptyReportDatabaseAndRebuildsAllFiveMetrics() throws Exception {
        cash(); finals.apply(event("settlement-recognition-completed"));
        var origin = rebuild.start(FROM,TO,"Asia/Bangkok");
        rebuild.advance(origin.generationId(),31);
        byte[] bytes = archives.exportArchive(origin.generationId());
        var backup = new FinancialArchiveJsonCodec().decode(bytes);
        // Independent Report database with migrations only: no Billing lookup or live originals to lean on.
        String restoredDb = "restore_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + restoredDb);
        var restoredSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                PG.getJdbcUrl().replace("/" + PG.getDatabaseName(), "/" + restoredDb), PG.getUsername(), PG.getPassword());
        org.flywaydb.core.Flyway.configure().dataSource(restoredSource).load().migrate();
        var recoveredJdbc = new JdbcTemplate(restoredSource);
        var recoveredStore = new FinancialRebuildPersistenceAdapter(recoveredJdbc,new ObjectMapper());
        var recoveredTransactions = new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(restoredSource));
        var recoveredArchive = new FinancialSourceArchiveApplicationService(recoveredStore,new FinancialArchiveJsonCodec());
        UUID actor = UUID.randomUUID();
        var restored = recoveredTransactions.execute(status -> recoveredArchive.restoreArchive(bytes,actor));
        assertThat(restored.originGenerationId()).isEqualTo(origin.generationId());
        assertThat(restored.generationId()).isNotEqualTo(origin.generationId());
        assertThat(restored.archiveFingerprint()).isEqualTo(backup.archiveFingerprint());
        assertThat(recoveredJdbc.queryForObject("SELECT source_inputs FROM financial_source_rebuild",Integer.class)).isEqualTo(3);
        assertThat(recoveredJdbc.queryForObject("SELECT actor_account_id FROM financial_archive_restore",UUID.class)).isEqualTo(actor);
        var recoveredRebuild = new FinancialRebuildApplicationService(recoveredStore);
        assertThat(recoveredTransactions.execute(status -> recoveredRebuild.advance(restored.generationId(),31)).status()).isEqualTo(Status.VERIFIED);
        assertThat(recoveredJdbc.queryForList("SELECT cash_received,refunds,earned_revenue,deposit_liability,outstanding_receivable FROM financial_source_rebuild_day WHERE business_date=? AND department_id IS NULL",java.sql.Date.valueOf(TO)))
                .isEqualTo(jdbc.queryForList("SELECT cash_received,refunds,earned_revenue,deposit_liability,outstanding_receivable FROM financial_source_rebuild_day WHERE generation_id=? AND business_date=? AND department_id IS NULL",origin.generationId(),java.sql.Date.valueOf(TO)));
        assertThat(recoveredJdbc.queryForList("SELECT source_kind,source_id,first_event_id,source_state,source_fingerprint,envelope_fingerprint,fact_snapshot,snapshot_fingerprint FROM financial_source_rebuild_input ORDER BY source_kind,source_id"))
                .isEqualTo(jdbc.queryForList("SELECT source_kind,source_id,first_event_id,source_state,source_fingerprint,envelope_fingerprint,fact_snapshot,snapshot_fingerprint FROM financial_source_rebuild_input WHERE generation_id=? ORDER BY source_kind,source_id",origin.generationId()));
        var repeated = recoveredTransactions.execute(status -> recoveredArchive.restoreArchive(bytes,UUID.randomUUID()));
        assertThat(repeated).isEqualTo(restored);
        assertThat(recoveredJdbc.queryForObject("SELECT count(*) FROM financial_archive_restore",Integer.class)).isOne();
        for (String table : java.util.List.of("report_cash_receipt","report_cash_refund","report_final_recognition","financial_report_generation","financial_report_publication")) {
            assertThat(recoveredJdbc.queryForObject("SELECT count(*) FROM " + table,Integer.class)).as(table).isZero();
        }
    }
    @Test void archive_pendingInventory_survivesRestoreWithoutApprovalOrLiveRetry() throws Exception {
        finals.apply(event("settlement-recognition-completed"));
        var origin = rebuild.start(FROM,TO,"Asia/Bangkok");
        byte[] bytes = archives.exportArchive(origin.generationId());
        cash(); // Live recovery after the snapshot cannot rewrite the backup's original pending decision.
        var restored = archives.restoreArchive(bytes, UUID.randomUUID());
        assertThat(rebuild.progress(restored.generationId()).unresolvedInputs()).isOne();
        var failed = rebuild.advance(restored.generationId(),31);
        assertThat(failed.status()).isEqualTo(Status.FAILED);
        assertThat(failed.reasonCode()).isEqualTo("REPORT_FINANCIAL_SOURCE_UNRESOLVED");
        assertThat(jdbc.queryForObject("SELECT source_state FROM financial_source_rebuild_input WHERE generation_id=?",String.class,restored.generationId())).isEqualTo("PENDING");
        assertThat(count("financial_report_publication")).isZero();
    }
    @Test void archive_concurrentOperators_restoreOneIsolatedGeneration() throws Exception {
        cash(); var origin = rebuild.start(FROM,TO,"Asia/Bangkok"); byte[] bytes = archives.exportArchive(origin.generationId());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var go = new CountDownLatch(1);
            Callable<com.mediflow.report.application.dto.response.FinancialArchiveRestoreDTO> restore = () -> {
                if (!go.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("restore start timed out");
                return archives.restoreArchive(bytes,UUID.randomUUID());
            };
            var first = pool.submit(restore); var second = pool.submit(restore); go.countDown();
            assertThat(first.get(20,TimeUnit.SECONDS)).isEqualTo(second.get(20,TimeUnit.SECONDS));
        }
        assertThat(count("financial_archive_restore")).isOne(); assertThat(count("financial_source_rebuild")).isEqualTo(2);
    }
    @Test void archive_auditStorageFailure_rollsBackGenerationAndAllInputs() throws Exception {
        cash(); var origin = rebuild.start(FROM,TO,"Asia/Bangkok"); byte[] bytes = archives.exportArchive(origin.generationId());
        jdbc.execute("CREATE FUNCTION fail_archive_audit_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test audit storage failure'; END; $$");
        jdbc.execute("CREATE TRIGGER fail_archive_audit_test BEFORE INSERT ON financial_archive_restore FOR EACH ROW EXECUTE FUNCTION fail_archive_audit_test()");
        try {
            assertThatThrownBy(() -> archives.restoreArchive(bytes,UUID.randomUUID())).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(count("financial_source_rebuild")).isOne(); assertThat(count("financial_archive_restore")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER fail_archive_audit_test ON financial_archive_restore"); jdbc.execute("DROP FUNCTION fail_archive_audit_test()");
        }
        assertThat(archives.restoreArchive(bytes,UUID.randomUUID()).generationId()).isNotNull();
    }
    @Test void archive_selfConsistentDigestButInvalidAppliedFact_rollsBackWithoutPublication() throws Exception {
        cash(); var origin = rebuild.start(FROM,TO,"Asia/Bangkok");
        var backup = new FinancialArchiveJsonCodec().decode(archives.exportArchive(origin.generationId()));
        var entries = new java.util.ArrayList<>(backup.entries());
        var first = entries.getFirst();
        entries.set(0, new FinancialEvidenceArchive.Entry(first.sourceKind(),first.sourceId(),first.firstEventId(),first.sourceState(),
                first.sourceFingerprint(),first.envelopeFingerprint(),"{}",FinancialEvidenceArchive.sha256("{}")));
        var invalid = FinancialEvidenceArchive.create(backup.originGenerationId(),backup.coveredFrom(),backup.coveredTo(),backup.reportZone(),
                backup.sourceObservedAt(),backup.unresolvedInputs(),FinancialEvidenceArchive.manifestHash(entries),entries);
        assertThatThrownBy(() -> archives.restoreArchive(new FinancialArchiveJsonCodec().encode(invalid),UUID.randomUUID()))
                .isInstanceOf(com.mediflow.report.domain.exception.ReportRuleException.class);
        assertThat(count("financial_source_rebuild")).isOne(); assertThat(count("financial_archive_restore")).isZero();
        assertThat(count("financial_report_publication")).isZero();
    }
    @Test void archive_databaseProvenance_remainsImmutable() throws Exception {
        cash(); var origin = rebuild.start(FROM,TO,"Asia/Bangkok");
        var restored = archives.restoreArchive(archives.exportArchive(origin.generationId()),UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update("UPDATE financial_archive_restore SET actor_account_id=? WHERE generation_id=?",UUID.randomUUID(),restored.generationId()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM financial_archive_restore WHERE generation_id=?",restored.generationId()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(count("financial_archive_restore")).isOne();
    }
    @Test void archive_unknownGeneration_reportsUnavailableWithoutAnyMutation() {
        assertThatThrownBy(() -> archives.exportArchive(UUID.randomUUID()))
                .isInstanceOf(com.mediflow.report.application.exception.ReportProjectionUnavailableException.class);
        assertThat(count("financial_source_rebuild")).isZero(); assertThat(count("financial_archive_restore")).isZero();
    }
    @Test void unchangedActualBillingFixturesRebuildAllFiveMetricsAndDoNotPublish() throws Exception {
        cash(); finals.apply(event("settlement-recognition-completed"));
        var generation = rebuild.start(FROM,TO,"Asia/Bangkok");
        assertThat(generation.sourceInputs()).isEqualTo(3); assertThat(generation.processedDays()).isZero();
        assertThat(rebuild.advance(generation.generationId(),1).status()).isEqualTo(Status.BUILDING);
        var finished = rebuild.advance(generation.generationId(),1);
        assertThat(finished.status()).isEqualTo(Status.VERIFIED); assertThat(finished.processedDays()).isEqualTo(2);
        assertThat(amount(generation.generationId(),TO,null,"cash_received")).isEqualByComparingTo("120");
        assertThat(amount(generation.generationId(),TO,null,"refunds")).isEqualByComparingTo("20");
        assertThat(amount(generation.generationId(),TO,null,"earned_revenue")).isEqualByComparingTo("100");
        assertThat(amount(generation.generationId(),TO,null,"deposit_liability")).isZero();
        assertThat(amount(generation.generationId(),TO,null,"outstanding_receivable")).isZero();
        assertThat(amount(generation.generationId(),TO,id(9),"earned_revenue")).isEqualByComparingTo("60");
        assertThat(amount(generation.generationId(),TO,id(10),"earned_revenue")).isEqualByComparingTo("40");
        assertThat(count("financial_report_generation")).isZero(); assertThat(count("financial_report_publication")).isZero();
        assertThat(rebuild.advance(generation.generationId(),31)).isEqualTo(finished);
    }
    @ParameterizedTest
    @ValueSource(strings={"PRF","PFR","RPF","RFP","FPR","FRP"})
    void initialFinancialContributions_allActualDeliveryOrders_recoverSameEquationsOnce(String deliveryOrder) throws Exception {
        for (char kind : deliveryOrder.toCharArray()) {
            switch (kind) {
                case 'P' -> receipts.apply(event("payment-final-recognition-deposit"));
                case 'R' -> refunds.apply(event("refund-final-recognition-deposit"));
                case 'F' -> finals.apply(event("settlement-recognition-completed"));
                default -> throw new IllegalArgumentException("Unknown delivery kind");
            }
        }
        // A recreated worker has no remembered arrival order or volatile pending state.
        var recreatedRefunds = new CashRefundApplicationService(
                new CashRefundPersistenceAdapter(jdbc,new ObjectMapper()), new BillingCashRefundMapper(ZoneId.of("Asia/Bangkok")));
        var recreatedFinals = new FinalSettlementRecognitionApplicationService(
                new BillingFinalSettlementRecognitionMapper(ZoneId.of("Asia/Bangkok")),new FinalSettlementRecognitionStoreAdapter(jdbc,new ObjectMapper()));
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            recreatedRefunds.recover(id(11)); recreatedFinals.recover(id(15));
        });
        receipts.apply(event("payment-final-recognition-deposit")); refunds.apply(event("refund-final-recognition-deposit"));
        finals.apply(event("settlement-recognition-completed"));
        assertThat(jdbc.queryForObject("SELECT state FROM report_cash_refund",String.class)).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("SELECT state FROM report_final_recognition",String.class)).isEqualTo("APPLIED");
        assertThat(count("report_cash_receipt")).isOne(); assertThat(count("report_cash_refund")).isOne();
        assertThat(count("report_final_deposit_release")).isOne(); assertThat(count("report_final_recognition_contribution")).isEqualTo(4);
        var run = rebuild.start(FROM,TO,"Asia/Bangkok");
        assertThat(rebuild.advance(run.generationId(),31).status()).isEqualTo(Status.VERIFIED);
        assertThat(amount(run.generationId(),TO,null,"cash_received")).isEqualByComparingTo("120");
        assertThat(amount(run.generationId(),TO,null,"refunds")).isEqualByComparingTo("20");
        assertThat(amount(run.generationId(),TO,null,"earned_revenue")).isEqualByComparingTo("100");
        assertThat(amount(run.generationId(),TO,null,"deposit_liability")).isZero();
        assertThat(amount(run.generationId(),TO,null,"outstanding_receivable")).isZero();
        assertThat(amount(run.generationId(),TO,id(9),"earned_revenue")).isEqualByComparingTo("60");
        assertThat(amount(run.generationId(),TO,id(10),"earned_revenue")).isEqualByComparingTo("40");
        assertThat(count("financial_report_publication")).isZero();
    }
    @ParameterizedTest
    @ValueSource(strings={"RECEIPT","REFUND","REFUND_RECOVERY","FINAL","FINAL_RECOVERY","NON_FINAL"})
    void financialWriters_sameAccount_waitForSharedDatabaseFence(String writerKind) throws Exception {
        if (!"RECEIPT".equals(writerKind) && !"NON_FINAL".equals(writerKind)) receipts.apply(event("payment-final-recognition-deposit"));
        if ("FINAL".equals(writerKind) || "FINAL_RECOVERY".equals(writerKind)) refunds.apply(event("refund-final-recognition-deposit"));
        if ("FINAL_RECOVERY".equals(writerKind)) {
            // Keep a real early final operation pending before supplying the last cash proof.
            clean(); finals.apply(event("settlement-recognition-completed")); cash();
        }
        UUID account = "NON_FINAL".equals(writerKind) ? UUID.fromString("91000000-0000-0000-0000-000000000001") : id(1);
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var writerPid = new java.util.concurrent.atomic.AtomicInteger();
        var payment = event("payment-final-recognition-deposit"); var refund = event("refund-final-recognition-deposit");
        var recognition = event("settlement-recognition-completed"); var balance = event("settlement-reconciled-1");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var blocker = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","report-final-account:"+account);
                held.countDown();
                try { if (!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Fence timeout"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
            }));
            try {
                assertThat(held.await(5,TimeUnit.SECONDS)).isTrue();
                var writer = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    writerPid.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));
                    switch (writerKind) {
                        case "RECEIPT" -> receipts.apply(payment);
                        case "REFUND" -> refunds.apply(refund);
                        case "REFUND_RECOVERY" -> refunds.recover(id(11));
                        case "FINAL" -> finals.apply(recognition);
                        case "FINAL_RECOVERY" -> finals.recover(id(15));
                        case "NON_FINAL" -> balances.apply(balance);
                        default -> throw new IllegalArgumentException("Unknown writer");
                    }
                }));
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(2)).until(() ->
                        writerPid.get()!=0 && Boolean.TRUE.equals(jdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event='advisory')",Boolean.class,writerPid.get())));
                assertThat(writer.isDone()).isFalse();
                release.countDown(); blocker.get(10,TimeUnit.SECONDS); writer.get(10,TimeUnit.SECONDS);
            } finally { release.countDown(); }
        }
    }
    @ParameterizedTest @ValueSource(strings={"PAYMENTS_FIRST","REFUNDS_FIRST","INTERLEAVED"})
    void outpatientInstallmentsAndOriginalPartialRefunds_allActualBytes_reconcileWithoutEarnedInvention(String order) throws Exception {
        String[] sources = switch (order) {
            case "PAYMENTS_FIRST" -> new String[]{"payment-prescription-first","payment-prescription-last","refund-prescription-first","refund-prescription-remainder","refund-prescription-last"};
            case "REFUNDS_FIRST" -> new String[]{"refund-prescription-last","refund-prescription-remainder","refund-prescription-first","payment-prescription-last","payment-prescription-first"};
            case "INTERLEAVED" -> new String[]{"refund-prescription-first","payment-prescription-last","refund-prescription-last","payment-prescription-first","refund-prescription-remainder"};
            default -> throw new IllegalArgumentException("Unknown delivery order");
        };
        for (String source : sources) {
            var input = event(source);
            if (source.startsWith("payment-")) receipts.apply(input); else refunds.apply(input);
        }
        // Redelivery is not another transaction or another refund allocation.
        for (String source : sources) {
            var input = event(source);
            if (source.startsWith("payment-")) receipts.apply(input); else refunds.apply(input);
        }
        assertThat(count("report_cash_receipt")).isEqualTo(2); assertThat(count("report_cash_refund")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_cash_refund WHERE state='APPLIED'",Long.class)).isEqualTo(3);
        var run = rebuild.start(FROM,TO,"Asia/Bangkok");
        assertThat(rebuild.advance(run.generationId(),31).status()).isEqualTo(Status.VERIFIED);
        assertThat(amount(run.generationId(),FROM,null,"cash_received")).isEqualByComparingTo("2000");
        assertThat(amount(run.generationId(),FROM,null,"refunds")).isEqualByComparingTo("2000");
        for (String metric : new String[]{"earned_revenue","deposit_liability","outstanding_receivable"}) {
            assertThat(amount(run.generationId(),FROM,null,metric)).isZero();
        }
        assertThat(count("report_final_recognition")).isZero(); assertThat(count("financial_report_publication")).isZero();
    }
    @Test void lateLocalSourcesCannotChangeFrozenManifestOrAlreadyBuiltRows() throws Exception {
        receipts.apply(event("payment-final-recognition-deposit")); var first = rebuild.start(FROM,TO,"Asia/Bangkok");
        refunds.apply(event("refund-final-recognition-deposit")); finals.apply(event("settlement-recognition-completed"));
        assertThat(rebuild.advance(first.generationId(),31).status()).isEqualTo(Status.VERIFIED);
        assertThat(amount(first.generationId(),TO,null,"deposit_liability")).isEqualByComparingTo("120");
        assertThat(amount(first.generationId(),TO,null,"earned_revenue")).isZero();
        var second = rebuild.start(FROM,TO,"Asia/Bangkok"); rebuild.advance(second.generationId(),31);
        assertThat(second.sourceInputs()).isEqualTo(3); assertThat(first.sourceInputs()).isOne();
        assertThat(amount(second.generationId(),TO,null,"earned_revenue")).isEqualByComparingTo("100");
    }
    @Test void pendingFrozenSourceCannotBeChangedToCompleteByLaterRecovery() throws Exception {
        finals.apply(event("settlement-recognition-completed")); var generation = rebuild.start(FROM,TO,"Asia/Bangkok");
        assertThat(generation.unresolvedInputs()).isOne(); cash(); finals.recover(id(15));
        var failed = rebuild.advance(generation.generationId(),31);
        assertThat(failed.status()).isEqualTo(Status.FAILED); assertThat(failed.reasonCode()).isEqualTo("REPORT_FINANCIAL_SOURCE_UNRESOLVED");
        assertThat(count("financial_source_rebuild_day")).isZero();
        var later = rebuild.start(FROM,TO,"Asia/Bangkok"); assertThat(rebuild.advance(later.generationId(),31).status()).isEqualTo(Status.VERIFIED);
    }
    @Test void missingFirstDeliveryProofAbortsEntireFreezeRatherThanSilentlyDroppingSource() throws Exception {
        receipts.apply(event("payment-final-recognition-deposit")); jdbc.update("DELETE FROM report_cash_delivery");
        assertThatThrownBy(() -> rebuild.start(FROM,TO,"Asia/Bangkok")).hasMessage("Invalid or incomplete frozen financial evidence");
        assertThat(count("financial_source_rebuild")).isZero(); assertThat(count("financial_source_rebuild_input")).isZero();
    }
    @Test void secondScopeFailureRollsBackEntireBatchAndCheckpointThenResumes() throws Exception {
        cash(); finals.apply(event("settlement-recognition-completed")); var generation = rebuild.start(FROM,TO,"Asia/Bangkok");
        jdbc.execute("CREATE FUNCTION fail_financial_rebuild_scope() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.department_id IS NOT NULL THEN RAISE EXCEPTION 'forced scope fault'; END IF; RETURN NEW; END; $$");
        jdbc.execute("CREATE TRIGGER fail_financial_rebuild_scope BEFORE INSERT ON financial_source_rebuild_day FOR EACH ROW EXECUTE FUNCTION fail_financial_rebuild_scope()");
        try { assertThatThrownBy(() -> rebuild.advance(generation.generationId(),1)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(count("financial_source_rebuild_day")).isZero(); assertThat(rebuild.progress(generation.generationId()).processedDays()).isZero();
        } finally { jdbc.execute("DROP TRIGGER fail_financial_rebuild_scope ON financial_source_rebuild_day"); jdbc.execute("DROP FUNCTION fail_financial_rebuild_scope()"); }
        assertThat(rebuild.advance(generation.generationId(),31).status()).isEqualTo(Status.VERIFIED);
    }
    @Test void concurrentWorkersSerializeCheckpointAndDoNotDuplicateDays() throws Exception {
        cash(); finals.apply(event("settlement-recognition-completed")); var generation = rebuild.start(FROM,TO,"Asia/Bangkok");
        var ready = new CountDownLatch(2); var go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Void> work = () -> { ready.countDown(); if (!go.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); rebuild.advance(generation.generationId(),1); return null; };
            var one = pool.submit(work); var two = pool.submit(work); assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); go.countDown(); one.get(20,TimeUnit.SECONDS); two.get(20,TimeUnit.SECONDS);
        }
        assertThat(rebuild.progress(generation.generationId()).status()).isEqualTo(Status.VERIFIED); assertThat(count("financial_source_rebuild_day")).isEqualTo(8);
    }
    @Test void verifiedRowsManifestAndMetadataAreDatabaseSealed() throws Exception {
        cash(); finals.apply(event("settlement-recognition-completed")); var generation = rebuild.start(FROM,TO,"Asia/Bangkok"); rebuild.advance(generation.generationId(),31);
        assertThatThrownBy(() -> jdbc.update("UPDATE financial_source_rebuild_day SET earned_revenue=0 WHERE generation_id=?",generation.generationId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE financial_source_rebuild_input SET snapshot_fingerprint=? WHERE generation_id=?","0".repeat(64),generation.generationId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE financial_source_rebuild SET status='BUILDING' WHERE generation_id=?",generation.generationId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void differentSnapshotOrderingAndNewDeliveriesHaveSameAmountsNotAdditionalCash() throws Exception {
        refunds.apply(event("refund-final-recognition-deposit")); finals.apply(event("settlement-recognition-completed")); receipts.apply(event("payment-final-recognition-deposit")); finals.recover(id(15));
        var first = rebuild.start(FROM,TO,"Asia/Bangkok"); rebuild.advance(first.generationId(),31);
        var root = root("payment-final-recognition-deposit"); root.put("eventId",UUID.randomUUID().toString()); receipts.apply(decode(root));
        var second = rebuild.start(FROM,TO,"Asia/Bangkok"); rebuild.advance(second.generationId(),31);
        assertThat(second.manifestFingerprint()).isEqualTo(first.manifestFingerprint()); assertThat(second.sourceInputs()).isEqualTo(3);
        assertThat(amount(second.generationId(),TO,null,"earned_revenue")).isEqualByComparingTo("100");
    }
    @Test void driftInOneBuiltMetricFailsBidirectionalReconciliation() throws Exception {
        cash(); finals.apply(event("settlement-recognition-completed")); var generation = rebuild.start(FROM,TO,"Asia/Bangkok"); rebuild.advance(generation.generationId(),1);
        jdbc.update("UPDATE financial_source_rebuild_day SET cash_received=1 WHERE generation_id=? AND department_id IS NULL",generation.generationId());
        assertThat(rebuild.advance(generation.generationId(),1).status()).isEqualTo(Status.FAILED);
        assertThat(rebuild.progress(generation.generationId()).reasonCode()).isEqualTo("REPORT_FINANCIAL_RECONCILIATION_FAILED");
        assertThat(count("financial_report_publication")).isZero();
    }
    @Test void lowerIsolationOuterTransactionAndNoTransactionCannotFreezeMixedSources() {
        assertThatThrownBy(() -> store.freeze(UUID.randomUUID(),FROM,TO,"Asia/Bangkok"))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        var transaction = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> transaction.execute(status -> rebuild.start(FROM,TO,"Asia/Bangkok"))).hasMessage("Invalid or incomplete frozen financial evidence");
        assertThat(count("financial_source_rebuild")).isZero();
    }
    @Test void frozenBuildingManifestCannotAppendAnotherSourceOrBeReopened() throws Exception {
        receipts.apply(event("payment-final-recognition-deposit")); var generation = rebuild.start(FROM,TO,"Asia/Bangkok");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO financial_source_rebuild_input(generation_id,source_kind,source_id,first_event_id,source_state,
                    source_fingerprint,envelope_fingerprint,fact_snapshot,snapshot_fingerprint)
                SELECT generation_id,source_kind,?,first_event_id,source_state,source_fingerprint,envelope_fingerprint,fact_snapshot,snapshot_fingerprint
                    FROM financial_source_rebuild_input WHERE generation_id=?
                """,UUID.randomUUID(),generation.generationId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE financial_source_rebuild SET manifest_sealed=false WHERE generation_id=?",generation.generationId()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(rebuild.advance(generation.generationId(),31).status()).isEqualTo(Status.VERIFIED);
    }
    @Test void recreatedApplicationWorkerUsesOnlyDurableCheckpointAndFrozenSource() throws Exception {
        cash(); finals.apply(event("settlement-recognition-completed")); var generation = rebuild.start(FROM,TO,"Asia/Bangkok");
        assertThat(rebuild.advance(generation.generationId(),1).processedDays()).isOne();
        var recreated = new FinancialRebuildApplicationService(store);
        var finished = new TransactionTemplate(transactions).execute(status -> recreated.advance(generation.generationId(),31));
        assertThat(finished.status()).isEqualTo(Status.VERIFIED); assertThat(finished.processedDays()).isEqualTo(2);
        assertThat(count("financial_source_rebuild_day")).isEqualTo(8);
        assertThat(amount(generation.generationId(),TO,null,"earned_revenue")).isEqualByComparingTo("100");
    }
    @Test void actualNonFinalRevisionsRebuildReceivableHistoryWithoutInventingCashOrEarned() throws Exception {
        balances.apply(event("settlement-reconciled-3")); balances.apply(event("settlement-reconciled-1")); balances.apply(event("settlement-reconciled-2"));
        var generation = rebuild.start(FROM,TO,"Asia/Bangkok");
        assertThat(rebuild.advance(generation.generationId(),31).status()).isEqualTo(Status.VERIFIED);
        assertThat(amount(generation.generationId(),TO,null,"outstanding_receivable")).isEqualByComparingTo("15");
        for (String metric : new String[] {"cash_received","deposit_liability","earned_revenue","refunds"}) {
            assertThat(amount(generation.generationId(),TO,null,metric)).isZero();
        }
        assertThat(generation.sourceInputs()).isEqualTo(3);
    }
    @Test void revisionHoleFailsRatherThanUsingLatestSnapshotAsCompleteHistory() throws Exception {
        balances.apply(event("settlement-reconciled-3")); var generation = rebuild.start(FROM,TO,"Asia/Bangkok");
        var failed = rebuild.advance(generation.generationId(),31);
        assertThat(failed.status()).isEqualTo(Status.FAILED); assertThat(failed.reasonCode()).isEqualTo("REPORT_FINANCIAL_SOURCE_UNRESOLVED");
        assertThat(count("financial_source_rebuild_day")).isZero();
    }
    @Test void earlierReceiptSuppliesOpeningLiabilityButNotCashFlowInLaterPeriod() throws Exception {
        receipts.apply(event("payment-deposit")); var generation = rebuild.start(TO,TO,"Asia/Bangkok");
        assertThat(rebuild.advance(generation.generationId(),31).status()).isEqualTo(Status.VERIFIED);
        assertThat(amount(generation.generationId(),TO,null,"cash_received")).isZero();
        assertThat(amount(generation.generationId(),TO,null,"deposit_liability")).isEqualByComparingTo("100");
    }
    @Test void futureOrUnfinishedCoveredDayCannotBeCertified() {
        var today = LocalDate.now(ZoneId.of("Asia/Bangkok"));
        assertThatThrownBy(() -> rebuild.start(today,today,"Asia/Bangkok")).hasMessage("Invalid or incomplete frozen financial evidence");
        assertThat(count("financial_source_rebuild")).isZero();
    }
    @Test void corruptedCopiedSnapshotFailsBeforeAnyDayEffects() throws Exception {
        receipts.apply(event("payment-final-recognition-deposit")); var generation = rebuild.start(FROM,TO,"Asia/Bangkok");
        // Simulate storage corruption, not a supported repair path: ordinary mutations are denied.
        jdbc.execute("ALTER TABLE financial_source_rebuild_input DISABLE TRIGGER financial_source_rebuild_input_fence");
        try { jdbc.update("UPDATE financial_source_rebuild_input SET fact_snapshot='{}' WHERE generation_id=?",generation.generationId()); }
        finally { jdbc.execute("ALTER TABLE financial_source_rebuild_input ENABLE TRIGGER financial_source_rebuild_input_fence"); }
        assertThat(rebuild.advance(generation.generationId(),31).status()).isEqualTo(Status.FAILED);
        assertThat(count("financial_source_rebuild_day")).isZero();
    }
    @Test void uncommittedSourceIsNotIncludedByWallClockOrGuessedSequence() throws Exception {
        var staged = new CountDownLatch(1); var commit = new CountDownLatch(1); var cashEvent = event("payment-final-recognition-deposit");
        try (var pool = Executors.newSingleThreadExecutor()) {
            var writer = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                receipts.apply(cashEvent); staged.countDown();
                try { if (!commit.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
            }));
            try {
                assertThat(staged.await(5,TimeUnit.SECONDS)).isTrue(); var first = rebuild.start(FROM,TO,"Asia/Bangkok");
                assertThat(first.sourceInputs()).isZero(); commit.countDown(); writer.get(15,TimeUnit.SECONDS);
                assertThat(rebuild.advance(first.generationId(),31).status()).isEqualTo(Status.VERIFIED);
                assertThat(amount(first.generationId(),TO,null,"cash_received")).isZero();
                assertThat(rebuild.start(FROM,TO,"Asia/Bangkok").sourceInputs()).isOne();
            } finally { commit.countDown(); }
        }
    }

    private void cash() throws Exception { receipts.apply(event("payment-final-recognition-deposit")); refunds.apply(event("refund-final-recognition-deposit")); }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table,Long.class); }
    private BigDecimal amount(UUID generation, LocalDate date, UUID department, String metric) {
        return jdbc.queryForObject("SELECT " + metric + " FROM financial_source_rebuild_day WHERE generation_id=? AND business_date=? AND department_id IS NOT DISTINCT FROM ?",
                BigDecimal.class,generation,java.sql.Date.valueOf(date),department);
    }
    private static UUID id(int value) { return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d",value)); }
    private static ObjectNode root(String name) throws Exception { return (ObjectNode) new ObjectMapper().readTree(Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/" + name + ".json"))); }
    private static DecodedCareFinanceEvent event(String name) throws Exception { return decode(root(name)); }
    private static DecodedCareFinanceEvent decode(ObjectNode root) throws Exception { return new CareFinanceEnvelopeDecoder(new ObjectMapper()).decode(root.get("eventType").asText(),new ObjectMapper().writeValueAsBytes(root)); }
    @TestConfiguration static class Config {
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean BillingCashReceiptMapper receiptMapper() { return new BillingCashReceiptMapper(ZoneId.of("Asia/Bangkok")); }
        @Bean BillingCashRefundMapper refundMapper() { return new BillingCashRefundMapper(ZoneId.of("Asia/Bangkok")); }
        @Bean BillingFinalSettlementRecognitionMapper finalMapper() { return new BillingFinalSettlementRecognitionMapper(ZoneId.of("Asia/Bangkok")); }
        @Bean BillingSettlementSnapshotMapper balanceMapper() { return new BillingSettlementSnapshotMapper(ZoneId.of("Asia/Bangkok")); }
    }
}
