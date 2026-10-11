package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.dto.command.CloseAdmissionChargesCommand;
import com.mediflow.billing.application.service.AdmissionChargeCloseService;
import com.mediflow.billing.application.service.PrescriptionChargeService;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.CareEpisodeType;
import com.mediflow.billing.infrastructure.messaging.AdmissionChargeCloseDecoder;
import com.mediflow.billing.infrastructure.messaging.PrescriptionChargeDecoder;
import com.mediflow.billing.infrastructure.persistence.adapter.AdmissionChargeCloseStoreAdapter;
import com.mediflow.billing.infrastructure.persistence.adapter.PrescriptionChargeRepositoryAdapter;
import com.mediflow.billing.infrastructure.persistence.adapter.SurgeryChargeRepositoryAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class AdmissionChargeClosePostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    private static DriverManagerDataSource source;
    private static JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AdmissionChargeCloseDecoder decoder = new AdmissionChargeCloseDecoder(mapper);
    private UUID account;
    private CloseAdmissionChargesCommand proof;

    @BeforeAll static void migrate() {
        source = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).load().migrate();
    }

    @BeforeEach void setup() throws Exception {
        jdbc.execute("TRUNCATE admission_charge_close,billing_account,billing_event_outbox CASCADE");
        account = UUID.randomUUID();
        proof = decoder.decode(AdmissionChargeCloseDecoder.ROUTING_KEY, AdmissionChargeCloseDecoderTest.fixture());
    }

    private void account() {
        jdbc.update("""
                INSERT INTO billing_account(account_id,patient_id,department_id,care_episode_type,care_episode_id,opened_at)
                VALUES (?,?,?,'ADMISSION',?,'2026-09-28T02:10:00Z')
                """, account, proof.patientId(), UUID.randomUUID(), proof.admissionId());
    }

    private void tx(Runnable action) {
        var template = new TransactionTemplate(new DataSourceTransactionManager(source));
        template.setTimeout(5);
        template.executeWithoutResult(status -> action.run());
    }

    private void close(CloseAdmissionChargesCommand command) {
        tx(() -> new AdmissionChargeCloseService(new AdmissionChargeCloseStoreAdapter(jdbc), Clock.systemUTC()).close(command));
    }

    private CloseAdmissionChargesCommand changed(String field, String value) throws Exception {
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(AdmissionChargeCloseDecoderTest.fixture());
        if ("eventId".equals(field)) root.put(field, value);
        else ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("payload")).put(field, value);
        return decoder.decode(AdmissionChargeCloseDecoder.ROUTING_KEY, mapper.writeValueAsBytes(root));
    }

    @Test void actualMedicalSourceClosesOnlyExactAccountWithoutMoneyOrFinalEvent() throws Exception {
        account();
        close(proof);
        close(proof);
        close(changed("eventId", UUID.randomUUID().toString()));
        assertThat(jdbc.queryForObject("SELECT status FROM billing_account WHERE account_id=?", String.class, account)).isEqualTo("CHARGE_CLOSED");
        assertThat(jdbc.queryForObject("SELECT version FROM billing_account WHERE account_id=?", Long.class, account)).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM admission_charge_close", String.class)).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admission_charge_close_delivery", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM settlement", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM billing_event_outbox", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_transaction", Integer.class)).isZero();
    }

    @Test void earlyProofPersistsBeforeAckAndBlocksAllNormalAdmissionAccountIssuers() {
        close(proof);
        assertThat(jdbc.queryForObject("SELECT status FROM admission_charge_close", String.class)).isEqualTo("PENDING_ACCOUNT");
        assertThatThrownBy(() -> tx(() -> new SurgeryChargeRepositoryAdapter(jdbc).findOrOpenAccount(
                proof.patientId(), UUID.randomUUID(), CareEpisodeType.ADMISSION, proof.admissionId(), "VND", Instant.now())))
                .isInstanceOf(BillingRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM billing_account", Integer.class)).isZero();
    }

    @Test void laterFinancialIssuerCannotReplaceInpatientPatientAuthority() {
        close(proof);
        UUID actualPatient = UUID.randomUUID();
        assertThatThrownBy(() -> tx(() -> new SurgeryChargeRepositoryAdapter(jdbc).findOrOpenAccount(actualPatient, UUID.randomUUID(),
                CareEpisodeType.ADMISSION, proof.admissionId(), "VND", Instant.now()))).isInstanceOf(BillingRuleException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM admission_charge_close", String.class)).isEqualTo("PENDING_ACCOUNT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM billing_account", Integer.class)).isZero();
    }

    @Test void knownWrongPatientAndChangedSummaryRollBackReceipts() throws Exception {
        account();
        var wrong = changed("patientId", UUID.randomUUID().toString());
        assertThatThrownBy(() -> close(wrong)).isInstanceOf(BillingRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admission_charge_close_delivery", Integer.class)).isZero();
        close(proof);
        var changedSummary = changed("summaryId", UUID.randomUUID().toString());
        assertThatThrownBy(() -> close(changedSummary)).isInstanceOf(BillingRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admission_charge_close_delivery", Integer.class)).isOne();
    }

    @Test void proofWriteFailureRollsBackAccountAndInboxThenExactRetryWorks() {
        account();
        jdbc.execute("CREATE FUNCTION reject_medical_close() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_medical_close BEFORE UPDATE ON admission_charge_close FOR EACH ROW EXECUTE FUNCTION reject_medical_close()");
        try {
            assertThatThrownBy(() -> close(proof)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(jdbc.queryForObject("SELECT status FROM billing_account", String.class)).isEqualTo("OPEN");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM admission_charge_close", Integer.class)).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_medical_close ON admission_charge_close");
            jdbc.execute("DROP FUNCTION reject_medical_close()");
        }
        close(proof);
        assertThat(jdbc.queryForObject("SELECT status FROM billing_account", String.class)).isEqualTo("CHARGE_CLOSED");
    }

    @Test void closedAccountReplayDoesNotReopenOrAdvanceVersion() {
        account(); close(proof);
        jdbc.update("UPDATE billing_account SET status='CLOSED' WHERE account_id=?", account);
        close(proof);
        assertThat(jdbc.queryForObject("SELECT status FROM billing_account", String.class)).isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject("SELECT version FROM billing_account", Long.class)).isOne();
    }

    @Test void samePatientDifferentEpisodeRemainsOpen() {
        account();
        UUID other = UUID.randomUUID();
        tx(() -> new SurgeryChargeRepositoryAdapter(jdbc).findOrOpenAccount(proof.patientId(), UUID.randomUUID(),
                CareEpisodeType.ADMISSION, other, "VND", Instant.now()));
        close(proof);
        assertThat(jdbc.queryForObject("SELECT status FROM billing_account WHERE care_episode_id=?", String.class, other)).isEqualTo("OPEN");
    }

    @Test void futureMedicalTimeCannotCloseAnAccount() {
        account();
        var earlyClock = Clock.fixed(proof.approvedAt().minusNanos(1), ZoneOffset.UTC);
        assertThatThrownBy(() -> tx(() -> new AdmissionChargeCloseService(new AdmissionChargeCloseStoreAdapter(jdbc), earlyClock).close(proof)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM billing_account", String.class)).isEqualTo("OPEN");
    }

    @Test void concurrentReplicasCommitOneClose() throws Exception {
        account();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            java.util.concurrent.Callable<Void> task = () -> {
                ready.countDown();
                if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test start timeout");
                close(proof); return null;
            };
            var first = pool.submit(task); var second = pool.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); go.countDown();
            first.get(15, TimeUnit.SECONDS); second.get(15, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT version FROM billing_account", Long.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admission_charge_close_delivery", Integer.class)).isOne();
    }

    @Test void actualPrescriptionIssuerReplaysAfterCloseButNewSourceCannotCreateCharges() throws Exception {
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(Files.readAllBytes(Path.of(
                "../pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription.created.v1.json")));
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) root.path("payload");
        payload.put("patientId", proof.patientId().toString());
        payload.put("admissionId", proof.admissionId().toString());
        payload.put("careEpisodeId", proof.admissionId().toString());
        payload.put("createdAt", "2026-09-28T02:10:00Z"); root.put("occurredAt", "2026-09-28T02:10:00Z");
        var charge = new PrescriptionChargeDecoder(mapper).decode("prescription.created", mapper.writeValueAsBytes(root));
        var issuer = new PrescriptionChargeService(new PrescriptionChargeRepositoryAdapter(jdbc), (id, event) -> { }, Clock.systemUTC(), code -> { });
        tx(() -> issuer.issue(charge)); close(proof); tx(() -> issuer.issue(charge));
        UUID next = UUID.randomUUID(); root.put("eventId", UUID.randomUUID().toString());
        payload.put("prescriptionId", next.toString()); payload.put("sourceId", next.toString());
        var late = new PrescriptionChargeDecoder(mapper).decode("prescription.created", mapper.writeValueAsBytes(root));
        assertThatThrownBy(() -> tx(() -> issuer.issue(late))).isInstanceOf(BillingRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM charge", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_charge_delivery", Integer.class)).isOne();
    }

    @Test void prescriptionCreationRacesMedicalCloseWithOnlyOneSerializedOutcome() throws Exception {
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(Files.readAllBytes(Path.of(
                "../pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription.created.v1.json")));
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) root.path("payload");
        payload.put("patientId", proof.patientId().toString()).put("admissionId", proof.admissionId().toString())
                .put("careEpisodeId", proof.admissionId().toString());
        var command = new PrescriptionChargeDecoder(mapper).decode("prescription.created", mapper.writeValueAsBytes(root));
        var issuer = new PrescriptionChargeService(new PrescriptionChargeRepositoryAdapter(jdbc), (id, event) -> { }, Clock.systemUTC(), code -> { });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2); var go = new CountDownLatch(1);
            var issue = pool.submit(() -> {
                ready.countDown();
                if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test start timeout");
                try { tx(() -> issuer.issue(command)); return true; }
                catch (BillingRuleException closed) {
                    assertThat(closed.getCode()).isEqualTo("BILLING_ACCOUNT_CHARGE_CLOSED"); return false;
                }
            });
            var discharge = pool.submit(() -> {
                ready.countDown();
                if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test start timeout");
                close(proof); return true;
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); go.countDown();
            boolean issued = issue.get(15, TimeUnit.SECONDS); assertThat(discharge.get(15, TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM charge", Integer.class)).isEqualTo(issued ? 1 : 0);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_charge_delivery", Integer.class)).isEqualTo(issued ? 1 : 0);
            assertThat(jdbc.queryForObject("SELECT status FROM admission_charge_close", String.class)).isEqualTo(issued ? "APPLIED" : "PENDING_ACCOUNT");
            if (issued) assertThat(jdbc.queryForObject("SELECT status FROM billing_account", String.class)).isEqualTo("CHARGE_CLOSED");
        }
    }

    @Test void plannedSurgeryIssuerCannotBypassEarlyMedicalClosure() throws Exception {
        close(proof);
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(Files.readAllBytes(Path.of(
                "../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/surgery.case.created.admission.v1.json")));
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) root.path("payload");
        payload.put("patientId", proof.patientId().toString()).put("admissionId", proof.admissionId().toString())
                .put("careEpisodeId", proof.admissionId().toString());
        var command = new com.mediflow.billing.infrastructure.messaging.SurgeryChargeDecoder(mapper)
                .decode("surgery.case.created", mapper.writeValueAsBytes(root));
        assertThatThrownBy(() -> tx(() -> new com.mediflow.billing.infrastructure.persistence.adapter.SurgeryPlannedRequestRepositoryAdapter(jdbc)
                .openAndLockExactAccount(command))).isInstanceOf(BillingRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM billing_account", Integer.class)).isZero();
    }

    @Test void performedSurgeryReplayIsNoEffectButLateRepricingAfterClosureIsRejected() {
        account();
        var adapter = new SurgeryChargeRepositoryAdapter(jdbc);
        var stored = new java.util.concurrent.atomic.AtomicReference<com.mediflow.billing.domain.model.Charge>();
        tx(() -> {
            var charge = com.mediflow.billing.domain.model.Charge.post(account, proof.patientId(), UUID.randomUUID(),
                    "SURGERY", UUID.randomUUID(), "PRICE", "fixture", java.math.BigDecimal.ONE,
                    new java.math.BigDecimal("100"), proof.approvedAt().minusSeconds(3600));
            charge.reconcilePerformed(UUID.randomUUID(), java.math.BigDecimal.ONE, new java.math.BigDecimal("100"));
            stored.set(adapter.saveCharge(charge));
        });
        close(proof);
        tx(() -> adapter.updateCharge(stored.get()));
        var previous = stored.get();
        var late = com.mediflow.billing.domain.model.Charge.restore(previous.getChargeId(), previous.getAccountId(),
                previous.getPatientId(), previous.getDepartmentId(), previous.getSourceType(), previous.getSourceId(),
                previous.getPriceCode(), previous.getDescription(), new java.math.BigDecimal("2"), previous.getUnitAmount(),
                new java.math.BigDecimal("200"), previous.getStatus(), previous.getVoidReason(), previous.getReconciledResultId(),
                previous.getIncurredAt(), previous.getCreatedAt());
        assertThatThrownBy(() -> tx(() -> adapter.updateCharge(late))).isInstanceOf(BillingRuleException.class);
        assertThat(jdbc.queryForObject("SELECT gross_amount FROM charge", java.math.BigDecimal.class)).isEqualByComparingTo("100");
    }
}
