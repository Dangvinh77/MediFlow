package com.mediflow.report.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.*;
import com.mediflow.report.application.dto.request.*;
import com.mediflow.report.application.mapper.SurgeryOperationalContributionMapper;
import com.mediflow.report.application.service.*;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;
import com.mediflow.report.infrastructure.persistence.adapter.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Actual Surgery producer fixtures; only isolated PostgreSQL, never foreign/service DB access. */
@DataJpaTest
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Import({OperationalContributionPersistenceAdapter.class, OperationalContributionApplicationService.class,
        OperationalReplayPersistenceAdapter.class, OperationalReplayApplicationService.class,
        OperationalPublicationPersistenceAdapter.class, OperationalSnapshotReadPersistenceAdapter.class,
        OperationalPublicationPostgresTest.Config.class})
class OperationalPublicationPostgresTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");
    private static final LocalDate DATE = LocalDate.of(2026, 10, 7);
    private static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-000000000099");
    @Autowired OperationalContributionApplicationService live;
    @Autowired OperationalReplayApplicationService replay;
    @Autowired OperationalPublicationApplicationService publication;
    @Autowired OperationalSnapshotReadPersistenceAdapter reads;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;

    @BeforeEach void cleanIsolatedDatabase() {
        jdbc.execute("TRUNCATE operational_publication_audit,operational_generation_acceptance,operational_report_publication,"
                + "operational_replay_generation,operational_event_journal,operational_source_snapshot,operational_delivery,"
                + "operational_contribution,daily_operational_report CASCADE");
    }

    @Test void actualProducerFiniteRebuild_publishMakesOnlyVerifiedSurgeryCountsReadable() throws Exception {
        live.apply(fixture(false));
        UUID id = verified();
        var result = publication.publish(request(id, null), ACTOR);
        assertThat(result.generationId()).isEqualTo(id);
        var report = new OperationalSnapshotReadService(reads, ZONE).surgery(DATE, DATE, null);
        assertThat(report.snapshotOnly()).isTrue();
        assertThat(report.days().getFirst().metrics()).containsEntry("surgeriesCompleted", 1L)
                .containsEntry("surgeryDurationMinutes", 6L).containsEntry("surgeriesCancelled", 0L);
        assertThat(reads.publication("DAILY")).isEmpty();
        assertThat(count("operational_publication_audit")).isOne();
    }

    @Test void sameCommandRetryAfterLaterPublish_doesNotMovePointerBack() throws Exception {
        live.apply(fixture(false)); UUID first = verified(); var command = request(first, null);
        var original = publication.publish(command, ACTOR);
        live.apply(redelivery()); UUID second = verified(); publication.publish(request(second, first), ACTOR);
        assertThat(publication.publish(command, ACTOR)).isEqualTo(original);
        assertThat(pointer()).isEqualTo(second);
        assertThat(count("operational_publication_audit")).isEqualTo(2);
    }

    @Test void sameCommandChangedActor_isRejectedWithoutPointerOrAuditEffect() throws Exception {
        live.apply(fixture(false)); UUID id = verified(); var command = request(id, null);
        publication.publish(command, ACTOR);
        assertThatThrownBy(() -> publication.publish(command, UUID.randomUUID())).isInstanceOf(ReportRuleException.class);
        assertThat(pointer()).isEqualTo(id); assertThat(count("operational_publication_audit")).isOne();
    }

    @Test void lateAcceptedDelivery_requiresNewFreezeEvenWithSameSemanticTotals() throws Exception {
        live.apply(fixture(false)); UUID id = verified(); live.apply(redelivery());
        assertThatThrownBy(() -> publication.publish(request(id, null), ACTOR)).isInstanceOf(ReportRuleException.class)
                .hasMessageContaining("Publication evidence");
        assertThat(count("operational_report_publication")).isZero();
        assertThat(count("operational_generation_acceptance")).isZero();
    }

    @Test void emptyManifest_cannotEstablishHistoricalZeroCoverage() {
        UUID id = replay.start().generationId();
        assertThatThrownBy(() -> publication.publish(request(id, null), ACTOR)).isInstanceOf(ReportRuleException.class);
        assertThat(count("operational_report_publication")).isZero();
    }

    @Test void beforeEarliestRetainedMetric_isUnavailableNotZero() throws Exception {
        live.apply(fixture(false)); UUID id = verified(); var input = request(id, null);
        var old = new PublishOperationalSnapshotRequest(input.commandId(), id, input.manifestFingerprint(), DATE.minusDays(1), DATE,
                input.acceptanceReference(), null);
        assertThatThrownBy(() -> publication.publish(old, ACTOR)).isInstanceOf(ReportRuleException.class);
        assertThat(count("operational_report_publication")).isZero();
    }

    @Test void buildingGeneration_isNotPublished() throws Exception {
        live.apply(fixture(false)); UUID id = replay.start().generationId();
        assertThatThrownBy(() -> publication.publish(request(id, null), ACTOR)).isInstanceOf(ReportRuleException.class);
        assertThat(count("operational_generation_acceptance")).isZero();
    }

    @Test void corruptedVerifiedScope_isRejectedDespiteHistoricVerifiedLabel() throws Exception {
        live.apply(fixture(false)); UUID id = verified();
        jdbc.update("UPDATE operational_replay_scope SET numeric_value=numeric_value+1 WHERE generation_id=?", id);
        assertThatThrownBy(() -> publication.publish(request(id, null), ACTOR)).isInstanceOf(ReportRuleException.class);
        assertThat(count("operational_publication_audit")).isZero();
    }

    @Test void corruptedFactKey_isRejectedDespiteUnchangedSnapshotAndTotals() throws Exception {
        live.apply(fixture(false)); UUID id = verified();
        jdbc.update("UPDATE operational_replay_contribution SET source_id=? WHERE generation_id=?", UUID.randomUUID(), id);
        assertThatThrownBy(() -> publication.publish(request(id, null), ACTOR)).isInstanceOf(ReportRuleException.class);
        assertThat(count("operational_publication_audit")).isZero();
    }

    @Test void modifiedPointerCoverage_isUnreadableWithoutMatchingOriginalAcceptance() throws Exception {
        live.apply(fixture(false)); UUID id = verified(); publication.publish(request(id, null), ACTOR);
        jdbc.update("UPDATE operational_report_publication SET covered_from=covered_from-1 WHERE report_kind='SURGERY'");
        assertThat(reads.publication("SURGERY")).isEmpty();
    }

    @Test void wrongManifestProof_doesNotPublish() throws Exception {
        live.apply(fixture(false)); UUID id = verified();
        var wrong = new PublishOperationalSnapshotRequest(UUID.randomUUID(), id, "f".repeat(64), DATE, DATE, "test/proof", null);
        assertThatThrownBy(() -> publication.publish(wrong, ACTOR)).isInstanceOf(ReportRuleException.class);
        assertThat(count("operational_generation_acceptance")).isZero();
    }

    @Test void failedAuditInsert_rollsBackAcceptanceAndPointer() throws Exception {
        live.apply(fixture(false)); UUID id = verified();
        jdbc.execute("CREATE FUNCTION test_reject_operational_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test failure'; END; $$");
        jdbc.execute("CREATE TRIGGER test_reject_operational_audit BEFORE INSERT ON operational_publication_audit FOR EACH ROW EXECUTE FUNCTION test_reject_operational_audit()");
        try {
            assertThatThrownBy(() -> publication.publish(request(id, null), ACTOR)).isInstanceOf(DataAccessException.class);
            assertThat(count("operational_report_publication")).isZero(); assertThat(count("operational_generation_acceptance")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER test_reject_operational_audit ON operational_publication_audit");
            jdbc.execute("DROP FUNCTION test_reject_operational_audit()");
        }
    }

    @Test void concurrentPublishWithSameExpectedPointer_exactlyOneWins() throws Exception {
        live.apply(fixture(false)); UUID one = verified(), two = verified();
        var first = request(one, null); var second = request(two, null);
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { ready.countDown(); await(start); return catchThrowable(() -> publication.publish(first, ACTOR)); });
            var b = pool.submit(() -> { ready.countDown(); await(start); return catchThrowable(() -> publication.publish(second, ACTOR)); });
            await(ready); start.countDown();
            var failureA = a.get(10, TimeUnit.SECONDS); var failureB = b.get(10, TimeUnit.SECONDS);
            assertThat((failureA == null ? 1 : 0) + (failureB == null ? 1 : 0)).isOne();
            assertThat(failureA == null ? failureB : failureA).isInstanceOf(ReportRuleException.class);
        }
        assertThat(count("operational_publication_audit")).isOne(); assertThat(count("operational_generation_acceptance")).isOne();
    }

    @Test void publicationWaitsForSourceWriter_thenFreshReadRejectsStaleManifest() throws Exception {
        live.apply(fixture(false)); UUID id = verified(); var input = request(id, null);
        var written = new CountDownLatch(1); var commit = new CountDownLatch(1);
        var later = redelivery();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var writer = pool.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                live.apply(later); written.countDown(); await(commit);
            }));
            await(written);
            var publisher = pool.submit(() -> catchThrowable(() -> publication.publish(input, ACTOR)));
            // This is synchronization with a live PostgreSQL lock, not an assumed timing-only race.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            boolean waiting = false;
            while (System.nanoTime() < deadline) {
                waiting = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE locktype='advisory' AND NOT granted)", Boolean.class));
                if (waiting || publisher.isDone()) break;
                Thread.onSpinWait();
            }
            commit.countDown(); writer.get(10, TimeUnit.SECONDS);
            assertThat(waiting).isTrue(); assertThat(publisher.get(10, TimeUnit.SECONDS)).isInstanceOf(ReportRuleException.class);
        } finally { commit.countDown(); }
        assertThat(count("operational_report_publication")).isZero();
    }

    @Test void acceptedGenerationRowsAndProvenance_areImmutable() throws Exception {
        live.apply(fixture(false)); UUID id = verified(); publication.publish(request(id, null), ACTOR);
        assertThatThrownBy(() -> jdbc.update("UPDATE operational_replay_scope SET numeric_value=0 WHERE generation_id=?", id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM operational_replay_input WHERE generation_id=?", id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE operational_generation_acceptance SET acceptance_reference='fake' WHERE generation_id=?", id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("DELETE FROM operational_publication_audit")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO operational_replay_scope(generation_id,metric_date,metric_type,numeric_value) VALUES (?,?,'SURGERIES_COMPLETED',0)", id, java.sql.Date.valueOf(DATE.plusDays(1))))
                .isInstanceOf(DataAccessException.class);
    }

    @Test void rollbackRetainsOriginalAsOf_andRetriesDoNotSwitchAgain() throws Exception {
        live.apply(fixture(false)); UUID first = verified(); publication.publish(request(first, null), ACTOR);
        Instant asOf = reads.publication("SURGERY").orElseThrow().reconciledAt();
        live.apply(redelivery()); UUID second = verified(); publication.publish(request(second, first), ACTOR);
        var input = new RollbackOperationalSnapshotRequest(UUID.randomUUID(), first, "test/rollback", second);
        var original = publication.rollback(input, ACTOR);
        assertThat(pointer()).isEqualTo(first); assertThat(reads.publication("SURGERY").orElseThrow().reconciledAt()).isEqualTo(asOf);
        publication.publish(request(second, first), ACTOR);
        assertThat(publication.rollback(input, ACTOR)).isEqualTo(original); assertThat(pointer()).isEqualTo(second);
    }

    @Test void unacceptedRollbackAndWrongExpectedPointer_haveNoEffect() throws Exception {
        live.apply(fixture(false)); UUID first = verified(); publication.publish(request(first, null), ACTOR); UUID second = verified();
        assertThatThrownBy(() -> publication.rollback(new RollbackOperationalSnapshotRequest(UUID.randomUUID(), second, "test/rollback", first), ACTOR)).isInstanceOf(ReportRuleException.class);
        assertThatThrownBy(() -> publication.publish(request(second, UUID.randomUUID()), ACTOR)).isInstanceOf(ReportRuleException.class);
        assertThat(pointer()).isEqualTo(first); assertThat(count("operational_publication_audit")).isOne();
    }

    @Test void repeatableReadCaller_isRejectedRatherThanUsingStaleSourceSnapshot() throws Exception {
        live.apply(fixture(false)); UUID id = verified(); var input = request(id, null);
        var tx = new TransactionTemplate(manager); tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> publication.publish(input, ACTOR))).isInstanceOf(ReportRuleException.class);
        assertThat(count("operational_report_publication")).isZero();
    }

    private UUID verified() {
        UUID id = replay.start().generationId();
        assertThat(replay.advance(id, 500).status()).isEqualTo(com.mediflow.report.application.dto.response.OperationalReplayProgress.Status.VERIFIED);
        return id;
    }
    private PublishOperationalSnapshotRequest request(UUID id, UUID previous) {
        return new PublishOperationalSnapshotRequest(UUID.randomUUID(), id, publication.candidate(id).manifestFingerprint(), DATE, DATE, "test/local-surgery", previous);
    }
    private ApplyOperationalContributionCommand fixture(boolean cancelled) throws Exception {
        String name = cancelled ? "surgery.cancelled.admission.v1.json" : "surgery.completed.admission.v1.json";
        var event = new CareFinanceEnvelopeDecoder(new ObjectMapper()).decode(cancelled ? "surgery.cancelled" : "surgery.completed",
                Files.readAllBytes(Path.of("../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/" + name)));
        return new SurgeryOperationalContributionMapper(ZONE).map(event);
    }
    private ApplyOperationalContributionCommand redelivery() throws Exception {
        var original = fixture(false).event(); var m = original.metadata();
        return new SurgeryOperationalContributionMapper(ZONE).map(new DecodedCareFinanceEvent(new CareFinanceEventMetadata(UUID.randomUUID(),
                m.eventType(), m.version(), m.occurredAt(), m.correlationId(), m.producer(), m.sourceField(), m.sourceId()), original.payload()));
    }
    private UUID pointer() { return jdbc.queryForObject("SELECT generation_id FROM operational_report_publication WHERE report_kind='SURGERY'", UUID.class); }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Latch timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
    @TestConfiguration static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean ZoneId reportZoneId() { return ZONE; }
        @Bean OperationalPublicationApplicationService operationalPublication(OperationalPublicationPersistenceAdapter store) {
            return new OperationalPublicationApplicationService(store, Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"), ZONE), ZONE);
        }
    }
}
