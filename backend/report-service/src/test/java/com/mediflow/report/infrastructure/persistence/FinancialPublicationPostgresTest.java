package com.mediflow.report.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.dto.request.PublishFinancialSnapshotRequest;
import com.mediflow.report.application.dto.request.RollbackFinancialSnapshotRequest;
import com.mediflow.report.application.dto.response.FinancialPublicationDTO;
import com.mediflow.report.application.dto.response.FinancialRebuildProgress;
import com.mediflow.report.application.dto.response.FinancialRebuildProgress.Status;
import com.mediflow.report.application.mapper.BillingCashReceiptMapper;
import com.mediflow.report.application.mapper.BillingCashRefundMapper;
import com.mediflow.report.application.mapper.BillingFinalSettlementRecognitionMapper;
import com.mediflow.report.application.service.CashReceiptApplicationService;
import com.mediflow.report.application.service.CashRefundApplicationService;
import com.mediflow.report.application.service.FinancialPublicationApplicationService;
import com.mediflow.report.application.service.FinancialRebuildApplicationService;
import com.mediflow.report.application.service.FinalSettlementRecognitionApplicationService;
import com.mediflow.report.application.service.FinancialSnapshotReadService;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.FinancialPublicationPolicy.HistoryPolicy;
import com.mediflow.report.infrastructure.persistence.adapter.CashReceiptPersistenceAdapter;
import com.mediflow.report.infrastructure.persistence.adapter.CashRefundPersistenceAdapter;
import com.mediflow.report.infrastructure.persistence.adapter.FinancialPublicationPersistenceAdapter;
import com.mediflow.report.infrastructure.persistence.adapter.FinancialRebuildPersistenceAdapter;
import com.mediflow.report.infrastructure.persistence.adapter.FinancialSnapshotReadPersistenceAdapter;
import com.mediflow.report.infrastructure.persistence.adapter.FinalSettlementRecognitionStoreAdapter;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * PostgreSQL acceptance tests for the controlled V23 -&gt; V20 financial switch.
 *
 * <p>The source side deliberately uses the checked-in Billing event bytes through the same
 * mappers as {@link FinancialRebuildPostgresTest}; no foreign database or synthetic money is
 * used.  These tests exercise the operator command, the exclusive publication fence, the
 * immutable acceptance/audit records and the existing read model together.</p>
 */
@DataJpaTest
@com.mediflow.report.support.ReportPersistenceTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        FinancialRebuildApplicationService.class,
        FinancialRebuildPersistenceAdapter.class,
        CashReceiptApplicationService.class,
        CashReceiptPersistenceAdapter.class,
        CashRefundApplicationService.class,
        CashRefundPersistenceAdapter.class,
        FinalSettlementRecognitionApplicationService.class,
        FinalSettlementRecognitionStoreAdapter.class,
        FinancialPublicationApplicationService.class,
        FinancialPublicationPersistenceAdapter.class,
        FinancialPublicationPostgresTest.Config.class
})
class FinancialPublicationPostgresTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    /** The retained Billing fixture starts on 2026-10-10 in the report zone. */
    private static final LocalDate FROM = LocalDate.of(2026, 10, 10);
    private static final LocalDate TO = LocalDate.of(2026, 10, 10);
    private static final String ZONE = "Asia/Bangkok";
    private static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-000000000099");
    private static final Instant READER_CLOCK = Instant.parse("2100-01-01T00:00:00Z");

    @Autowired FinancialPublicationApplicationService publication;
    @Autowired FinancialRebuildApplicationService rebuild;
    @Autowired CashReceiptApplicationService receipts;
    @Autowired CashRefundApplicationService refunds;
    @Autowired FinalSettlementRecognitionApplicationService finals;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE financial_publication_audit,financial_generation_acceptance," +
                "financial_report_publication,financial_report_day,financial_report_generation," +
                "financial_source_rebuild_day,financial_source_rebuild_input,financial_source_rebuild," +
                "report_final_recognition,report_final_recognition_daily,report_cash_receipt," +
                "report_gross_cash_daily,report_cash_refund,report_refund_cash_daily," +
                "report_settlement_snapshot,report_receivable_current,report_receivable_stock CASCADE");
    }

    @Test
    void publish_verifiedGeneration_copiesAllFiveMetricsAndReaderReturnsActualBillingTotals() throws Exception {
        FinancialRebuildProgress generation = verifiedActualBillingGeneration();

        FinancialPublicationDTO result = publication.publish(publishRequest(generation, UUID.randomUUID(), null), ACTOR);
        var report = new FinancialSnapshotReadService(new FinancialSnapshotReadPersistenceAdapter(jdbc),
                ZoneId.of(ZONE), "VND", Clock.fixed(READER_CLOCK, ZoneId.of("UTC")))
                .daily(FROM, TO, null);
        var finalDay = report.periods().stream().filter(period -> period.from().equals(TO)).findFirst().orElseThrow();

        assertThat(result.action()).isEqualTo("PUBLISH");
        assertThat(result.generationId()).isEqualTo(generation.generationId());
        assertThat(result.previousGenerationId()).isNull();
        assertThat(report.generationId()).isEqualTo(generation.generationId());
        assertThat(finalDay.cashReceived()).isEqualByComparingTo("120.00");
        assertThat(finalDay.refunds()).isEqualByComparingTo("20.00");
        assertThat(finalDay.earnedRevenue()).isEqualByComparingTo("100.00");
        assertThat(finalDay.depositLiability()).isZero();
        assertThat(finalDay.outstandingReceivable()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_report_day WHERE generation_id=?",
                Integer.class, result.generationId())).isEqualTo(4);
    }

    @Test
    void publish_whenLiveManifestChanged_requiresNewFrozenGeneration() throws Exception {
        FinancialRebuildProgress stale = verifiedActualBillingGeneration();
        receipts.apply(event("payment-deposit"));

        assertThatThrownBy(() -> publication.publish(publishRequest(stale, UUID.randomUUID(), null), ACTOR))
                .isInstanceOf(ReportRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_report_publication", Integer.class)).isZero();

        FinancialRebuildProgress fresh = rebuild.start(FROM, TO, ZONE);
        fresh = rebuild.advance(fresh.generationId(), 31);
        assertThat(fresh.status()).isEqualTo(Status.VERIFIED);
        assertThat(fresh.manifestFingerprint()).isNotEqualTo(stale.manifestFingerprint());
        publication.publish(publishRequest(fresh, UUID.randomUUID(), null), ACTOR);
        assertThat(jdbc.queryForObject("SELECT generation_id FROM financial_report_publication",
                UUID.class)).isEqualTo(fresh.generationId());
    }

    @Test
    void publish_buildingGeneration_isRejectedBeforeAnyAcceptanceOrReadRows() throws Exception {
        cash();
        finals.apply(event("settlement-recognition-completed"));
        FinancialRebuildProgress building = rebuild.start(FROM, TO, ZONE);

        assertThat(building.status()).isEqualTo(Status.BUILDING);
        assertThatThrownBy(() -> publication.publish(publishRequest(building, UUID.randomUUID(), null), ACTOR))
                .isInstanceOf(ReportRuleException.class);
        assertPublicationTablesEmpty();
    }

    @Test
    void publish_pendingSourceGeneration_isRejectedInsteadOfTreatingMissingEvidenceAsZero() throws Exception {
        finals.apply(event("settlement-recognition-completed"));
        FinancialRebuildProgress pending = rebuild.start(FROM, TO, ZONE);
        FinancialRebuildProgress failed = rebuild.advance(pending.generationId(), 31);

        assertThat(failed.status()).isEqualTo(Status.FAILED);
        assertThat(failed.unresolvedInputs()).isPositive();
        assertThatThrownBy(() -> publication.publish(publishRequest(pending, UUID.randomUUID(), null), ACTOR))
                .isInstanceOf(ReportRuleException.class);
        assertPublicationTablesEmpty();
    }

    @Test
    void publish_emptyVerifiedGeneration_rejectsInventedNoEvents() {
        FinancialRebuildProgress started = rebuild.start(FROM, TO, ZONE);
        FinancialRebuildProgress empty = rebuild.advance(started.generationId(), 31);

        assertThat(empty.status()).isEqualTo(Status.VERIFIED);
        assertThat(empty.sourceInputs()).isZero();
        assertThat(empty.unresolvedInputs()).isZero();
        assertPublicationCode(publishRequest(empty, UUID.randomUUID(), null), ACTOR,
                "REPORT_PUBLICATION_UNVERIFIED");
        assertPublicationTablesEmpty();
    }

    @Test
    void publish_coverageBeforeRetainedHistory_isRejectedInsteadOfInventingZeroHistory() throws Exception {
        cash();
        finals.apply(event("settlement-recognition-completed"));
        FinancialRebuildProgress candidate = rebuild.start(FROM.minusDays(1), TO, ZONE);
        candidate = rebuild.advance(candidate.generationId(), 31);

        assertThat(candidate.status()).isEqualTo(Status.VERIFIED);
        assertPublicationCode(publishRequest(candidate, UUID.randomUUID(), null), ACTOR,
                "REPORT_PUBLICATION_HISTORY_UNAVAILABLE");
        assertPublicationTablesEmpty();
    }

    @Test
    void publish_sameCommandWithChangedRequestOrActor_isRejectedWithoutOverwrite() throws Exception {
        FinancialRebuildProgress generation = verifiedActualBillingGeneration();
        UUID commandId = UUID.randomUUID();
        PublishFinancialSnapshotRequest original = publishRequest(generation, commandId, null);
        FinancialPublicationDTO accepted = publication.publish(original, ACTOR);

        PublishFinancialSnapshotRequest changedReference = new PublishFinancialSnapshotRequest(
                commandId, generation.generationId(), null, generation.manifestFingerprint(),
                "CHANGED-" + commandId, HistoryPolicy.AVAILABLE_EVIDENCE_ONLY);
        assertPublicationCode(changedReference, ACTOR, "REPORT_PUBLICATION_COMMAND_CONFLICT");
        assertPublicationCode(original, UUID.fromString("00000000-0000-4000-8000-000000000098"),
                "REPORT_PUBLICATION_COMMAND_CONFLICT");

        assertThat(jdbc.queryForObject("SELECT generation_id FROM financial_report_publication", UUID.class))
                .isEqualTo(accepted.generationId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_publication_audit", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_report_day WHERE generation_id=?",
                Integer.class, accepted.generationId())).isEqualTo(4);
    }

    @Test
    void rollback_unknownOrNeverPublishedGeneration_isRejectedWithoutCreatingPointer() throws Exception {
        RollbackFinancialSnapshotRequest unknown = new RollbackFinancialSnapshotRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ROLLBACK-UNKNOWN");
        assertRollbackCode(unknown, "REPORT_ROLLBACK_TARGET_UNAVAILABLE");

        FinancialRebuildProgress verifiedButNeverPublished = verifiedActualBillingGeneration();
        RollbackFinancialSnapshotRequest neverPublished = new RollbackFinancialSnapshotRequest(
                UUID.randomUUID(), verifiedButNeverPublished.generationId(), UUID.randomUUID(), "ROLLBACK-UNPUBLISHED");
        assertRollbackCode(neverPublished, "REPORT_ROLLBACK_TARGET_UNAVAILABLE");
        assertPublicationTablesEmpty();
    }

    @Test
    void publish_expectedCurrentGenerationMismatch_isRejectedWithoutPointerChange() throws Exception {
        FinancialRebuildProgress generation = verifiedActualBillingGeneration();
        UUID wrongExpected = UUID.randomUUID();
        PublishFinancialSnapshotRequest request = publishRequest(generation, UUID.randomUUID(), wrongExpected);

        assertThatThrownBy(() -> publication.publish(request, ACTOR)).isInstanceOf(ReportRuleException.class);
        assertPublicationTablesEmpty();
    }

    @Test
    void publish_retryOfOldCommand_afterLaterSwitch_returnsOriginalResultWithoutOverwrite() throws Exception {
        FinancialRebuildProgress first = verifiedActualBillingGeneration();
        UUID firstCommand = UUID.randomUUID();
        PublishFinancialSnapshotRequest firstRequest = publishRequest(first, firstCommand, null);
        FinancialPublicationDTO firstResult = publication.publish(firstRequest, ACTOR);

        FinancialRebuildProgress second = rebuild.start(FROM, TO, ZONE);
        second = rebuild.advance(second.generationId(), 31);
        FinancialPublicationDTO secondResult = publication.publish(
                publishRequest(second, UUID.randomUUID(), first.generationId()), ACTOR);

        FinancialPublicationDTO retry = publication.publish(firstRequest, ACTOR);

        assertThat(retry).isEqualTo(firstResult);
        assertThat(secondResult.generationId()).isEqualTo(second.generationId());
        assertThat(jdbc.queryForObject("SELECT generation_id FROM financial_report_publication",
                UUID.class)).isEqualTo(second.generationId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_publication_audit", Integer.class)).isEqualTo(2);
    }

    @Test
    void publish_sameCommand_concurrentlyCommitsExactlyOneAcceptanceAndReturnsSameResult() throws Exception {
        FinancialRebuildProgress generation = verifiedActualBillingGeneration();
        PublishFinancialSnapshotRequest request = publishRequest(generation, UUID.randomUUID(), null);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        Callable<FinancialPublicationDTO> call = () -> {
            ready.countDown();
            if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("publication start timed out");
            return publication.publish(request, ACTOR);
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(call);
            var two = pool.submit(call);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            FinancialPublicationDTO first = one.get(20, TimeUnit.SECONDS);
            FinancialPublicationDTO second = two.get(20, TimeUnit.SECONDS);
            assertThat(second).isEqualTo(first);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_generation_acceptance", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_publication_audit", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_report_publication", Integer.class)).isOne();
    }

    @Test
    void publish_auditStorageFailure_rollsBackAcceptancePointerAndCopiedRows() throws Exception {
        FinancialRebuildProgress generation = verifiedActualBillingGeneration();
        jdbc.execute("CREATE FUNCTION fail_financial_publication_audit_test() RETURNS trigger LANGUAGE plpgsql AS $$ " +
                "BEGIN RAISE EXCEPTION 'test publication audit failure'; END; $$");
        jdbc.execute("CREATE TRIGGER fail_financial_publication_audit_test BEFORE INSERT ON " +
                "financial_publication_audit FOR EACH ROW EXECUTE FUNCTION fail_financial_publication_audit_test()");
        try {
            assertThatThrownBy(() -> publication.publish(publishRequest(generation, UUID.randomUUID(), null), ACTOR))
                    .isInstanceOf(DataAccessException.class);
            assertPublicationTablesEmpty();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_source_rebuild", Integer.class)).isOne();
        } finally {
            jdbc.execute("DROP TRIGGER fail_financial_publication_audit_test ON financial_publication_audit");
            jdbc.execute("DROP FUNCTION fail_financial_publication_audit_test()");
        }
        publication.publish(publishRequest(generation, UUID.randomUUID(), null), ACTOR);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_report_publication", Integer.class)).isOne();
    }

    @Test
    void publicationFence_exclusiveSwitchBlocksSourceWriterUntilRelease_thenRejectsLateManifest() throws Exception {
        FinancialRebuildProgress stale = verifiedActualBillingGeneration();
        var lockHeld = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var writerStarted = new CountDownLatch(1);
        var writerPid = new AtomicInteger();
        PublishFinancialSnapshotRequest request = publishRequest(stale, UUID.randomUUID(), null);

        try (var pool = Executors.newFixedThreadPool(3)) {
            var blocker = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                        "report-financial-publication");
                lockHeld.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("lock release timed out");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
            }));
            assertThat(lockHeld.await(5, TimeUnit.SECONDS)).isTrue();

            var writer = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                writerPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                writerStarted.countDown();
                try {
                    receipts.apply(event("payment-deposit"));
                } catch (Exception error) {
                    throw new IllegalStateException(error);
                }
            }));
            assertThat(writerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event='advisory')",
                    Boolean.class, writerPid.get())));
            assertThat(writer.isDone()).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM report_cash_receipt", Integer.class)).isOne();

            var publisher = pool.submit(() -> assertPublicationCode(request, ACTOR,
                    "REPORT_PUBLICATION_CATCHUP_REQUIRED"));
            release.countDown();
            blocker.get(10, TimeUnit.SECONDS);
            writer.get(10, TimeUnit.SECONDS);
            publisher.get(10, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM report_cash_receipt", Integer.class)).isEqualTo(2);
            assertPublicationTablesEmpty();
        } finally {
            release.countDown();
        }
    }

    @Test
    void publicationFence_publisherWaitsForSharedWriter_thenFreshReadRejectsCommittedLateSource() throws Exception {
        FinancialRebuildProgress stale = verifiedActualBillingGeneration();
        PublishFinancialSnapshotRequest request = publishRequest(stale, UUID.randomUUID(), null);
        var sourceApplied = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var writerPid = new AtomicInteger();
        var publisherStarted = new CountDownLatch(1);
        var publisherPid = new AtomicInteger();
        var publisherCode = new AtomicReference<String>();

        try (var pool = Executors.newFixedThreadPool(2)) {
            var writer = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                writerPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                try {
                    receipts.apply(event("payment-deposit"));
                    sourceApplied.countDown();
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("writer release timed out");
                } catch (Exception error) {
                    throw new IllegalStateException(error);
                }
            }));
            assertThat(sourceApplied.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM report_cash_receipt", Integer.class)).isOne();

            var publisher = pool.submit(() -> {
                try {
                    new TransactionTemplate(transactions).executeWithoutResult(status -> {
                        publisherPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        publisherStarted.countDown();
                        try {
                            publication.publish(request, ACTOR);
                            publisherCode.set("UNEXPECTED_SUCCESS");
                        } catch (ReportRuleException expected) {
                            publisherCode.set(expected.getCode());
                        }
                    });
                } catch (org.springframework.transaction.UnexpectedRollbackException expected) {
                    // The service's rule exception marks the joined transaction rollback-only.
                }
            });
            assertThat(publisherStarted.await(5, TimeUnit.SECONDS)).isTrue();
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event='advisory')",
                    Boolean.class, publisherPid.get())));
            assertThat(publisher.isDone()).isFalse();
            assertThat(publisherCode.get()).isNull();
            release.countDown();
            writer.get(10, TimeUnit.SECONDS);
            publisher.get(10, TimeUnit.SECONDS);
            assertThat(publisherCode.get()).isEqualTo("REPORT_PUBLICATION_CATCHUP_REQUIRED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM report_cash_receipt", Integer.class)).isEqualTo(2);
            assertPublicationTablesEmpty();
        } finally {
            release.countDown();
        }
    }

    @Test
    void postFinalServicePaymentRefund_isRejectedWithoutExtraSourceOrTotals() throws Exception {
        ObjectNode payment = root("payment-final-recognition-deposit");
        ((ObjectNode) payment.get("payload")).put("classification", "SERVICE_PAYMENT");
        receipts.apply(decode(payment));
        refunds.apply(event("refund-final-recognition-deposit"));

        ObjectNode finalRecognition = root("settlement-recognition-completed");
        ObjectNode finalPayload = (ObjectNode) finalRecognition.get("payload");
        finalPayload.withArray("allocations").forEach(line -> ((ObjectNode) line).put("classification", "SERVICE_PAYMENT"));
        finalPayload.withArray("depositReleases").removeAll();
        finals.apply(decode(finalRecognition));

        assertThat(jdbc.queryForObject("SELECT state FROM report_final_recognition", String.class)).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_cash_refund WHERE state='APPLIED'", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_final_deposit_release", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(completed_refunds),0) FROM report_refund_cash_daily WHERE department_id IS NULL",
                BigDecimal.class)).isEqualByComparingTo("20.00");
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM report_cash_refund WHERE state='APPLIED'",
                BigDecimal.class)).isEqualByComparingTo("20.00");

        ObjectNode lateRefund = root("refund-final-recognition-deposit").deepCopy();
        lateRefund.put("eventId", id(97).toString());
        lateRefund.put("occurredAt", "2026-10-10T00:02:00Z");
        lateRefund.put("correlationId", "final-correction-after-final");
        ObjectNode latePayload = (ObjectNode) lateRefund.get("payload");
        latePayload.put("refundTransactionId", id(98).toString());
        latePayload.put("amount", 1.00);
        latePayload.put("completedAt", "2026-10-10T00:02:00Z");

        assertRefundCode(decode(lateRefund), "CASH_REFUND_FINAL_CORRECTION_UNSUPPORTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_cash_refund", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(completed_refunds),0) FROM report_refund_cash_daily WHERE department_id IS NULL",
                BigDecimal.class)).isEqualByComparingTo("20.00");
        assertThat(jdbc.queryForObject("SELECT state FROM report_final_recognition", String.class)).isEqualTo("APPLIED");
    }

    @Test
    void reader_requestOutsideAcceptedCoverage_returnsNotFound() throws Exception {
        FinancialRebuildProgress generation = verifiedActualBillingGeneration();
        publication.publish(publishRequest(generation, UUID.randomUUID(), null), ACTOR);
        var reader = new FinancialSnapshotReadService(new FinancialSnapshotReadPersistenceAdapter(jdbc),
                ZoneId.of(ZONE), "VND", Clock.fixed(READER_CLOCK, ZoneId.of("UTC")));

        assertThatThrownBy(() -> reader.daily(FROM.minusDays(1), TO, null))
                .isInstanceOf(com.mediflow.report.application.exception.ReportProjectionUnavailableException.class);
        assertThatThrownBy(() -> reader.daily(FROM, TO.plusDays(1), null))
                .isInstanceOf(com.mediflow.report.application.exception.ReportProjectionUnavailableException.class);
    }

    @Test
    void rollback_toPreviouslyAcceptedGeneration_switchesReaderPointer() throws Exception {
        FinancialRebuildProgress first = verifiedActualBillingGeneration();
        FinancialPublicationDTO firstResult = publication.publish(
                publishRequest(first, UUID.randomUUID(), null), ACTOR);
        FinancialRebuildProgress second = rebuild.start(FROM, TO, ZONE);
        second = rebuild.advance(second.generationId(), 31);
        publication.publish(publishRequest(second, UUID.randomUUID(), first.generationId()), ACTOR);

        RollbackFinancialSnapshotRequest rollback = new RollbackFinancialSnapshotRequest(
                UUID.randomUUID(), first.generationId(), second.generationId(), "ROLLBACK-ACCEPTED-FIRST");
        FinancialPublicationDTO result = publication.rollback(rollback, ACTOR);

        assertThat(result.action()).isEqualTo("ROLLBACK");
        assertThat(result.generationId()).isEqualTo(first.generationId());
        assertThat(result.previousGenerationId()).isEqualTo(second.generationId());
        assertThat(result.commandId()).isEqualTo(rollback.commandId());
        assertThat(result.generationId()).isEqualTo(firstResult.generationId());
        assertThat(jdbc.queryForObject("SELECT generation_id FROM financial_report_publication",
                UUID.class)).isEqualTo(first.generationId());
    }

    @Test
    void auditedAcceptanceAndPublicationRows_areImmutableAfterSuccessfulSwitch() throws Exception {
        FinancialRebuildProgress generation = verifiedActualBillingGeneration();
        FinancialPublicationDTO result = publication.publish(
                publishRequest(generation, UUID.randomUUID(), null), ACTOR);

        assertThatThrownBy(() -> jdbc.update("UPDATE financial_generation_acceptance SET actor_account_id=? WHERE generation_id=?",
                UUID.randomUUID(), generation.generationId())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM financial_generation_acceptance WHERE generation_id=?",
                generation.generationId())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE financial_publication_audit SET actor_account_id=? WHERE command_id=?",
                UUID.randomUUID(), result.commandId())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM financial_publication_audit WHERE command_id=?",
                result.commandId())).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_generation_acceptance", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_publication_audit", Integer.class)).isOne();
    }

    @Test
    void publish_v20DayMutationBeforeVerification_isRejectedAndRolledBack() throws Exception {
        FinancialRebuildProgress generation = verifiedActualBillingGeneration();
        jdbc.execute("CREATE FUNCTION tamper_financial_publication_day_test() RETURNS trigger LANGUAGE plpgsql AS $$ " +
                "BEGIN UPDATE financial_report_day SET earned_revenue=999.00 WHERE generation_id=NEW.generation_id " +
                "AND business_date=NEW.business_date AND department_id IS NOT DISTINCT FROM NEW.department_id; RETURN NEW; END; $$");
        jdbc.execute("CREATE TRIGGER tamper_financial_publication_day_test AFTER INSERT ON financial_report_day " +
                "FOR EACH ROW EXECUTE FUNCTION tamper_financial_publication_day_test()");
        try {
            assertThatThrownBy(() -> publication.publish(publishRequest(generation, UUID.randomUUID(), null), ACTOR))
                    .isInstanceOf(ReportRuleException.class);
        } finally {
            jdbc.execute("DROP TRIGGER tamper_financial_publication_day_test ON financial_report_day");
            jdbc.execute("DROP FUNCTION tamper_financial_publication_day_test()");
        }
        assertPublicationTablesEmpty();
    }

    private void assertPublicationCode(PublishFinancialSnapshotRequest request, UUID actor, String code) {
        Throwable failure = catchThrowable(() -> publication.publish(request, actor));
        assertThat(failure).isInstanceOf(ReportRuleException.class);
        assertThat(((ReportRuleException) failure).getCode()).isEqualTo(code);
    }

    private void assertRollbackCode(RollbackFinancialSnapshotRequest request, String code) {
        Throwable failure = catchThrowable(() -> publication.rollback(request, ACTOR));
        assertThat(failure).isInstanceOf(ReportRuleException.class);
        assertThat(((ReportRuleException) failure).getCode()).isEqualTo(code);
    }

    private void assertRefundCode(DecodedCareFinanceEvent event, String code) {
        Throwable failure = catchThrowable(() -> refunds.apply(event));
        assertThat(failure).isInstanceOf(ReportRuleException.class);
        assertThat(((ReportRuleException) failure).getCode()).isEqualTo(code);
    }

    private FinancialRebuildProgress verifiedActualBillingGeneration() throws Exception {
        cash();
        finals.apply(event("settlement-recognition-completed"));
        FinancialRebuildProgress generation = rebuild.start(FROM, TO, ZONE);
        FinancialRebuildProgress finished = rebuild.advance(generation.generationId(), 31);
        assertThat(finished.status()).isEqualTo(Status.VERIFIED);
        return finished;
    }

    private PublishFinancialSnapshotRequest publishRequest(FinancialRebuildProgress generation,
            UUID commandId, UUID expectedCurrentGenerationId) {
        return new PublishFinancialSnapshotRequest(commandId, generation.generationId(), expectedCurrentGenerationId,
                generation.manifestFingerprint(), "ACCEPT-" + commandId, HistoryPolicy.AVAILABLE_EVIDENCE_ONLY);
    }

    private void assertPublicationTablesEmpty() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_generation_acceptance", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_publication_audit", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_report_publication", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_report_generation", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_report_day", Integer.class)).isZero();
    }

    private void cash() throws Exception {
        receipts.apply(event("payment-final-recognition-deposit"));
        refunds.apply(event("refund-final-recognition-deposit"));
    }

    private static UUID id(int value) {
        return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", value));
    }

    private static ObjectNode root(String name) throws Exception {
        return (ObjectNode) new ObjectMapper().readTree(Files.readAllBytes(
                Path.of("../billing-service/src/test/resources/contracts/ledger-v1/" + name + ".json")));
    }

    private static DecodedCareFinanceEvent event(String name) throws Exception {
        ObjectNode value = root(name);
        return decode(value);
    }

    private static DecodedCareFinanceEvent decode(ObjectNode value) throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        return new CareFinanceEnvelopeDecoder(mapper).decode(value.get("eventType").asText(), mapper.writeValueAsBytes(value));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean BillingCashReceiptMapper receiptMapper() { return new BillingCashReceiptMapper(ZoneId.of(ZONE)); }
        @Bean BillingCashRefundMapper refundMapper() { return new BillingCashRefundMapper(ZoneId.of(ZONE)); }
        @Bean BillingFinalSettlementRecognitionMapper finalMapper() { return new BillingFinalSettlementRecognitionMapper(ZoneId.of(ZONE)); }
    }
}
