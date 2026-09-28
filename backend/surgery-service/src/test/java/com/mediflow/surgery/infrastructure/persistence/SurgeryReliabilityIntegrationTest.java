package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.port.out.SurgeryCommandReceiptPort;
import com.mediflow.surgery.application.port.out.SurgeryInboxPort;
import com.mediflow.surgery.application.port.out.SurgeryOutboxPort;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class SurgeryReliabilityIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("surgery_reliability")
            .withUsername("surgery")
            .withPassword("surgery");
    private static final Instant NOW = Instant.parse("2026-09-28T08:00:00Z");
    private static final String FP_A = "a".repeat(64);
    private static final String FP_B = "b".repeat(64);
    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static SurgeryCommandReceiptAdapter receipts;
    private static SurgeryInboxAdapter inbox;
    private static SurgeryOutboxAdapter outbox;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        receipts = new SurgeryCommandReceiptAdapter(jdbc);
        inbox = new SurgeryInboxAdapter(jdbc);
        outbox = new SurgeryOutboxAdapter(jdbc);
    }

    @Test
    void receiptReplaysCommittedResponseAndRejectsChangedPayload() {
        UUID caseId = insertCase();
        SurgeryCommandReceiptPort.Key key = new SurgeryCommandReceiptPort.Key(
                UUID.randomUUID().toString(), "BEGIN_PREOP", UUID.randomUUID().toString());
        UUID receiptId = tx.execute(ignored -> {
            SurgeryCommandReceiptPort.Claim first = receipts.claim(key, FP_A);
            assertThat(first.state()).isEqualTo(SurgeryCommandReceiptPort.State.NEW);
            receipts.complete(first.receiptId(), caseId, "OK", bytes("saved"), NOW);
            return first.receiptId();
        });
        SurgeryCommandReceiptPort.Claim replay = tx.execute(ignored -> receipts.claim(key, FP_A));
        assertThat(replay.state()).isEqualTo(SurgeryCommandReceiptPort.State.REPLAY);
        assertThat(replay.receiptId()).isEqualTo(receiptId);
        assertThat(replay.response()).isEqualTo(bytes("saved"));
        SurgeryCommandReceiptPort.State conflict = tx.execute(ignored -> receipts.claim(key, FP_B).state());
        assertThat(conflict)
                .isEqualTo(SurgeryCommandReceiptPort.State.CONFLICT);
    }

    @Test
    void transactionRollbackRemovesReceiptAndOutboxTogether() {
        UUID caseId = insertCase();
        SurgeryCommandReceiptPort.Key key = new SurgeryCommandReceiptPort.Key(
                UUID.randomUUID().toString(), "START", UUID.randomUUID().toString());
        UUID eventId = UUID.randomUUID();
        assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> {
            SurgeryCommandReceiptPort.Claim claim = receipts.claim(key, FP_A);
            receipts.complete(claim.receiptId(), caseId, "OK", bytes("started"), NOW);
            outbox.append(event(eventId, caseId, 1, 0));
            throw new IllegalStateException("simulate failure before commit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_command_receipt WHERE command_code = 'START'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_outbox WHERE event_id = ?",
                Integer.class, eventId)).isZero();
    }

    @Test
    void inboxRetainsPendingAndQuarantinesConflictingDuplicates() {
        String semanticKey = "referral:" + UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        SurgeryInboxPort.IncomingEvent original = incoming(eventId, semanticKey, FP_A, "one");
        tx.executeWithoutResult(ignored -> {
            assertThat(inbox.begin(original)).isEqualTo(SurgeryInboxPort.Decision.NEW);
            inbox.defer(eventId, "WAIT_FOR_CASE", NOW.plusSeconds(60));
        });
        SurgeryInboxPort.Decision pending = tx.execute(ignored -> inbox.begin(original));
        assertThat(pending)
                .isEqualTo(SurgeryInboxPort.Decision.RETRY_PENDING);
        tx.executeWithoutResult(ignored -> inbox.markApplied(eventId, NOW.plusSeconds(61)));
        SurgeryInboxPort.Decision applied = tx.execute(ignored -> inbox.begin(original));
        assertThat(applied)
                .isEqualTo(SurgeryInboxPort.Decision.ALREADY_APPLIED);
        SurgeryInboxPort.Decision semanticReplay = tx.execute(ignored ->
                inbox.begin(incoming(UUID.randomUUID(), semanticKey, FP_A, "one")));
        assertThat(semanticReplay)
                .isEqualTo(SurgeryInboxPort.Decision.ALREADY_APPLIED);
        SurgeryInboxPort.Decision semanticConflict = tx.execute(ignored ->
                inbox.begin(incoming(UUID.randomUUID(), semanticKey, FP_A, "changed")));
        assertThat(semanticConflict)
                .isEqualTo(SurgeryInboxPort.Decision.CONFLICT);
        SurgeryInboxPort.Decision idConflict = tx.execute(ignored ->
                inbox.begin(incoming(eventId, semanticKey, FP_B, "changed")));
        assertThat(idConflict)
                .isEqualTo(SurgeryInboxPort.Decision.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_inbox_conflict WHERE event_id = ?",
                Integer.class, eventId)).isEqualTo(1);
    }

    @Test
    void outboxFencesExpiredAttemptAndPreservesCaseOrder() {
        UUID caseId = insertCase();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        tx.executeWithoutResult(ignored -> {
            outbox.append(event(firstId, caseId, 1, 0));
            outbox.append(event(secondId, caseId, 2, 0));
        });
        SurgeryOutboxPort.Delivery first = tx.execute(ignored ->
                outbox.claimNext(NOW, Duration.ofSeconds(10)).orElseThrow());
        assertThat(first.eventId()).isEqualTo(firstId);
        java.util.Optional<SurgeryOutboxPort.Delivery> blocked = tx.execute(ignored ->
                outbox.claimNext(NOW.plusSeconds(1), Duration.ofSeconds(10)));
        assertThat(blocked)
                .isEmpty();
        SurgeryOutboxPort.Delivery recovered = tx.execute(ignored ->
                outbox.claimNext(NOW.plusSeconds(11), Duration.ofSeconds(10)).orElseThrow());
        assertThat(recovered.eventId()).isEqualTo(firstId);
        assertThat(recovered.attemptToken()).isNotEqualTo(first.attemptToken());
        Boolean staleConfirm = tx.execute(ignored -> outbox.markPublished(firstId,
                first.attemptToken(), NOW.plusSeconds(12)));
        assertThat(staleConfirm).isFalse();
        Boolean currentConfirm = tx.execute(ignored -> outbox.markPublished(firstId,
                recovered.attemptToken(), NOW.plusSeconds(12)));
        assertThat(currentConfirm).isTrue();
        SurgeryOutboxPort.Delivery second = tx.execute(ignored ->
                outbox.claimNext(NOW.plusSeconds(13), Duration.ofSeconds(10)).orElseThrow());
        assertThat(second.eventId()).isEqualTo(secondId);
        Boolean returned = tx.execute(ignored -> outbox.markReturned(secondId,
                second.attemptToken(), "NO_ROUTE", NOW.plusSeconds(14)));
        assertThat(returned).isTrue();
        java.util.Optional<SurgeryOutboxPort.Delivery> backedOff = tx.execute(ignored ->
                outbox.claimNext(NOW.plusSeconds(15), Duration.ofSeconds(10)));
        assertThat(backedOff)
                .isEmpty();
        java.util.Optional<SurgeryOutboxPort.Delivery> retried = tx.execute(ignored ->
                outbox.claimNext(NOW.plusSeconds(16), Duration.ofSeconds(10)));
        assertThat(retried)
                .isPresent();
    }

    private static SurgeryOutboxPort.OutgoingEvent event(UUID eventId, UUID caseId,
                                                          long revision, int order) {
        return new SurgeryOutboxPort.OutgoingEvent(eventId, caseId, revision, order,
                "test.surgery.event", 1, "reliability-test", bytes("payload"), NOW);
    }

    private static SurgeryInboxPort.IncomingEvent incoming(UUID eventId, String semanticKey,
                                                            String fingerprint, String payload) {
        return new SurgeryInboxPort.IncomingEvent(eventId, "test.referral.created", 1,
                "clinical-service", fingerprint, semanticKey, bytes(payload), NOW);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static UUID insertCase() {
        UUID caseId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO surgery_case
                (surgery_case_id, surgery_request_id, episode_type,
                 episode_id, patient_id, department_id, requested_by,
                 procedure_code, indication, priority, status, requested_at)
                VALUES (?, ?, 'OUTPATIENT_VISIT', ?, ?, ?, ?, 'PROC', 'Indication',
                        'ROUTINE', 'REQUESTED', ?)
                """, caseId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), Timestamp.from(NOW));
        return caseId;
    }
}
