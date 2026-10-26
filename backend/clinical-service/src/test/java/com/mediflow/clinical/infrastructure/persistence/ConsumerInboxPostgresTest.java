package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.dto.authority.PaymentRecoveryOperation;
import com.mediflow.clinical.application.dto.command.ConsumerInboxClaim;
import com.mediflow.clinical.application.exception.ConsumerInboxConflictException;
import com.mediflow.clinical.application.port.out.ConsumerInboxPort.InboxOutcome;
import com.mediflow.clinical.application.port.out.OutpatientPaymentRecoveryPort;
import com.mediflow.clinical.application.service.ConsumerInboxBoundary;
import com.mediflow.clinical.infrastructure.persistence.adapter.ConsumerInboxPersistenceAdapter;
import com.mediflow.clinical.infrastructure.persistence.adapter.OutpatientPaymentRecoveryPersistenceAdapter;
import com.mediflow.clinical.messaging.consumer.inbox.InboxEvidenceFactory;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Exercises the inbox adapter, transaction boundary and schema against PostgreSQL itself. */
@Testcontainers(disabledWithoutDocker = true)
class ConsumerInboxPostgresTest {
    private static final UUID RECORD_ID = UUID.fromString("00000000-0000-0000-0000-000000001002");
    private static final UUID LAB_ID = UUID.fromString("00000000-0000-0000-0000-000000001010");
    private static final UUID PRESCRIPTION_ID = UUID.fromString("00000000-0000-0000-0000-000000001011");
    private static final UUID CLEARANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000001012");
    private static final UUID OLD_EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000001013");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @org.junit.jupiter.api.Test
    void freshSchema_preservesExactDeliveryBytesAndDedupeSemantics() {
        TestDatabase db = migrateFresh();
        assertThat(latestVersion(db)).isEqualTo("11");
        ConsumerInboxBoundary boundary = boundary(db);
        String operationKey = "clinical:lab-result:v0:" + LAB_ID;
        UUID firstEvent = UUID.fromString("00000000-0000-0000-0000-000000001101");
        UUID secondEvent = UUID.fromString("00000000-0000-0000-0000-000000001102");
        UUID thirdEvent = UUID.fromString("00000000-0000-0000-0000-000000001103");
        byte[] firstRaw = labResultBody(firstEvent, "corr-a", "7.5");
        ConsumerInboxClaim first = labResultClaim(db, firstEvent, operationKey, "corr-a", "7.5");

        assertThat(deliver(db, boundary, first, () -> insertEffect(db, "lab-result", operationKey)))
                .isEqualTo(InboxOutcome.APPLIED);
        assertThat(deliver(db, boundary, first, () -> insertEffect(db, "lab-result", operationKey)))
                .isEqualTo(InboxOutcome.DUPLICATE_EVENT);

        ConsumerInboxClaim sameOperation = labResultClaim(db, secondEvent, operationKey, "corr-b", "7.5");
        assertThat(deliver(db, boundary, sameOperation, () -> insertEffect(db, "lab-result", operationKey)))
                .isEqualTo(InboxOutcome.DUPLICATE_OPERATION);
        assertThat(db.jdbc.queryForObject("SELECT raw_body FROM consumer_inbox_delivery WHERE event_id = ?",
                byte[].class, firstEvent)).containsExactly(firstRaw);
        byte[] firstCanonical = canonicalFor(db, firstEvent);
        assertThat(canonicalFor(db, secondEvent)).containsExactly(firstCanonical);

        ConsumerInboxClaim changedDelivery = labResultClaim(db, firstEvent, operationKey, "corr-changed", "7.5");
        assertThatThrownBy(() -> deliver(db, boundary, changedDelivery,
                () -> insertEffect(db, "lab-result", operationKey)))
                .isInstanceOf(ConsumerInboxConflictException.class);
        ConsumerInboxClaim changedBusiness = labResultClaim(db, thirdEvent, operationKey, "corr-c", "8.0");
        assertThatThrownBy(() -> deliver(db, boundary, changedBusiness,
                () -> insertEffect(db, "lab-result", operationKey)))
                .isInstanceOf(ConsumerInboxConflictException.class);

        assertThat(count(db, "consumer_inbox_delivery")).isEqualTo(2);
        assertThat(count(db, "consumer_inbox_operation")).isEqualTo(1);
        assertThat(count(db, "consumer_inbox_test_effect")).isEqualTo(1);
    }

    @org.junit.jupiter.api.Test
    void outpatientEligibility_contractAcceptsFenceScopedDedupeAndRejectsChangedBusinessBytes() {
        TestDatabase db = migrateFresh();
        ConsumerInboxBoundary boundary = boundary(db);
        UUID eventOne = UUID.fromString("00000000-0000-0000-0000-000000001601");
        UUID eventTwo = UUID.fromString("00000000-0000-0000-0000-000000001602");
        UUID operationId = UUID.fromString("00000000-0000-0000-0000-000000001603");
        String operationKey = "clinical:outpatient-eligibility:v1:" + operationId + ":19:COMPLETED";
        ConsumerInboxClaim first = eligibilityClaim(eventOne, operationKey, "corr-eligibility-one", "120.00");

        assertThat(deliver(db, boundary, first, () -> insertEffect(db, "eligibility", operationKey)))
                .isEqualTo(InboxOutcome.APPLIED);
        ConsumerInboxClaim sameOperation = eligibilityClaim(eventTwo, operationKey, "corr-eligibility-two", "120.00");
        assertThat(deliver(db, boundary, sameOperation, () -> insertEffect(db, "eligibility", operationKey)))
                .isEqualTo(InboxOutcome.DUPLICATE_OPERATION);
        assertThat(canonicalFor(db, eventTwo)).containsExactly(canonicalFor(db, eventOne));

        ConsumerInboxClaim changedBusiness = eligibilityClaim(UUID.randomUUID(), operationKey,
                "corr-eligibility-changed", "121.00");
        assertThatThrownBy(() -> deliver(db, boundary, changedBusiness,
                () -> insertEffect(db, "eligibility", operationKey)))
                .isInstanceOf(ConsumerInboxConflictException.class);
        assertThat(count(db, "consumer_inbox_test_effect")).isEqualTo(1);
    }

    @org.junit.jupiter.api.Test
    void recoveryClaims_areDurableExclusiveBackedOffAndAlertOnce() {
        TestDatabase db = migrateFresh();
        PaymentIds ids = seedLockedPayment(db);
        OutpatientPaymentRecoveryPort recovery = new OutpatientPaymentRecoveryPersistenceAdapter(
                new NamedParameterJdbcTemplate(db.dataSource));
        Instant started = Instant.parse("2026-10-26T04:00:00Z");
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();

        PaymentRecoveryOperation firstClaim = transaction(db, () -> recovery.claimDue(firstOwner, started,
                started.plusSeconds(45), 10).getFirst());
        assertThat(firstClaim.paymentOperationId()).isEqualTo(ids.operationId());
        assertThat(firstClaim.attemptCount()).isEqualTo(1);
        assertThat(firstClaim.recoveryOwner()).isEqualTo(firstOwner);
        assertThat(transaction(db, () -> recovery.claimDue(secondOwner, started.plusSeconds(1),
                started.plusSeconds(46), 10))).isEmpty();

        Instant alertAt = started.plusSeconds(300);
        assertThat(transaction(db, () -> recovery.defer(firstClaim, alertAt,
                alertAt.plusSeconds(30), "BILLING_HTTP_503"))).isTrue();
        assertThat(transaction(db, () -> recovery.claimDue(secondOwner, alertAt.plusSeconds(10),
                alertAt.plusSeconds(55), 10))).isEmpty();

        PaymentRecoveryOperation retry = transaction(db, () -> recovery.claimDue(secondOwner,
                alertAt.plusSeconds(30), alertAt.plusSeconds(75), 10).getFirst());
        assertThat(retry.firstAttemptAt()).isEqualTo(started);
        assertThat(retry.attemptCount()).isEqualTo(2);
        assertThat(transaction(db, () -> recovery.defer(retry, alertAt.plusSeconds(31),
                alertAt.plusSeconds(91), "BILLING_HTTP_503"))).isFalse();
        assertThat(db.jdbc.queryForObject("SELECT recovery_attempt_count FROM outpatient_payment_operation_receipt "
                + "WHERE payment_operation_id = ?", Integer.class, ids.operationId())).isEqualTo(2);
        assertThat(db.jdbc.queryForObject("SELECT recovery_alerted_at IS NOT NULL FROM outpatient_payment_operation_receipt "
                + "WHERE payment_operation_id = ?", Boolean.class, ids.operationId())).isTrue();
    }

    @org.junit.jupiter.api.Test
    void oneGlobalEventIdCannotBeClaimedByDifferentClinicalConsumers() {
        TestDatabase db = migrateFresh();
        ConsumerInboxBoundary boundary = boundary(db);
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000001201");
        ConsumerInboxClaim result = labResultClaim(db, eventId, "clinical:lab-result:v0:" + LAB_ID,
                "corr-global", "7.5");
        ConsumerInboxClaim prescription = prescriptionClaim(db, eventId, "clinical:prescription:v0:" + PRESCRIPTION_ID);

        assertThat(deliver(db, boundary, result, () -> insertEffect(db, "lab-result", LAB_ID.toString())))
                .isEqualTo(InboxOutcome.APPLIED);
        assertThatThrownBy(() -> deliver(db, boundary, prescription,
                () -> insertEffect(db, "prescription", PRESCRIPTION_ID.toString())))
                .isInstanceOf(ConsumerInboxConflictException.class);

        assertThat(count(db, "consumer_inbox_delivery")).isEqualTo(1);
        assertThat(count(db, "consumer_inbox_operation")).isEqualTo(1);
        assertThat(count(db, "consumer_inbox_test_effect")).isEqualTo(1);
    }

    @org.junit.jupiter.api.Test
    void throwingEffectRollsBackBothInboxClaimsAndBusinessEffect() {
        TestDatabase db = migrateFresh();
        ConsumerInboxBoundary boundary = boundary(db);
        ConsumerInboxClaim claim = labResultClaim(db,
                UUID.fromString("00000000-0000-0000-0000-000000001301"),
                "clinical:lab-result:v0:" + LAB_ID, "corr-rollback", "7.5");

        assertThatThrownBy(() -> deliver(db, boundary, claim, () -> {
            insertEffect(db, "lab-result", claim.operationKey());
            throw new IllegalStateException("simulate failed local effect");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(count(db, "consumer_inbox_delivery")).isZero();
        assertThat(count(db, "consumer_inbox_operation")).isZero();
        assertThat(count(db, "consumer_inbox_test_effect")).isZero();
    }

    @org.junit.jupiter.api.Test
    void concurrentSameOperationWithEquivalentBytesAppliesOneEffect() throws Exception {
        TestDatabase db = migrateFresh();
        ConsumerInboxBoundary boundary = boundary(db);
        String operationKey = "clinical:lab-result:v0:" + LAB_ID;
        ConsumerInboxClaim first = labResultClaim(db,
                UUID.fromString("00000000-0000-0000-0000-000000001401"), operationKey, "corr-race-a", "7.5");
        ConsumerInboxClaim second = labResultClaim(db,
                UUID.fromString("00000000-0000-0000-0000-000000001402"), operationKey, "corr-race-b", "7.5");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<InboxOutcome> firstResult = executor.submit(() -> concurrentDelivery(
                    db, boundary, first, ready, start, operationKey));
            Future<InboxOutcome> secondResult = executor.submit(() -> concurrentDelivery(
                    db, boundary, second, ready, start, operationKey));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(firstResult.get(15, TimeUnit.SECONDS), secondResult.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(InboxOutcome.APPLIED, InboxOutcome.DUPLICATE_OPERATION);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(count(db, "consumer_inbox_delivery")).isEqualTo(2);
        assertThat(count(db, "consumer_inbox_operation")).isEqualTo(1);
        assertThat(count(db, "consumer_inbox_test_effect")).isEqualTo(1);
    }

    @org.junit.jupiter.api.Test
    void upgradeFromV5PreservesLegacyEvidenceAndFailsClosedForUnverifiableEffects() {
        TestDatabase db = newDatabase();
        migrate(db, "5");
        assertThat(latestVersion(db)).isEqualTo("5");
        seedLegacyClinicalEvidence(db);

        migrate(db, null);

        assertThat(latestVersion(db)).isEqualTo("11");
        createEffectTable(db);
        assertThat(count(db, "processed_event")).isEqualTo(1);
        assertThat(count(db, "attached_result")).isEqualTo(2);
        assertThat(count(db, "exam_clearance")).isEqualTo(1);

        ConsumerInboxBoundary boundary = boundary(db);
        ConsumerInboxClaim oldReceipt = labResultClaim(db, OLD_EVENT_ID,
                "clinical:lab-result:v0:" + UUID.randomUUID(), "corr-old-receipt", "7.5");
        assertThatThrownBy(() -> deliver(db, boundary, oldReceipt, () -> failIfCalled()))
                .isInstanceOf(ConsumerInboxConflictException.class);

        UUID labResultEvent = UUID.fromString("00000000-0000-0000-0000-000000001501");
        assertLegacyClaimRejected(db, boundary, labResultClaim(db, labResultEvent,
                "clinical:lab-result:v0:" + LAB_ID, "corr-old-lab", "7.5"));
        UUID labResultV1Event = UUID.fromString("00000000-0000-0000-0000-000000001502");
        assertLegacyClaimRejected(db, boundary, labResultClaim(db, labResultV1Event,
                "clinical:lab-result:v1:" + LAB_ID + ":1", "corr-old-lab-v1", "7.5"));
        UUID prescriptionEvent = UUID.fromString("00000000-0000-0000-0000-000000001503");
        assertLegacyClaimRejected(db, boundary, prescriptionClaim(db, prescriptionEvent,
                "clinical:prescription:v0:" + PRESCRIPTION_ID));
        UUID clearanceEvent = UUID.fromString("00000000-0000-0000-0000-000000001504");
        assertLegacyClaimRejected(db, boundary, clearanceClaim(db, clearanceEvent,
                "clinical:clearance:v1:" + CLEARANCE_ID));

        assertThat(count(db, "consumer_inbox_delivery")).isZero();
        assertThat(count(db, "consumer_inbox_operation")).isZero();
        assertThat(count(db, "attached_result")).isEqualTo(2);
        assertThat(count(db, "exam_clearance")).isEqualTo(1);
    }

    private static TestDatabase migrateFresh() {
        TestDatabase db = newDatabase();
        migrate(db, null);
        createEffectTable(db);
        return db;
    }

    private static TestDatabase newDatabase() {
        String schema = "clinical_inbox_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource admin = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        admin.setDriverClassName("org.postgresql.Driver");
        new JdbcTemplate(admin).execute("CREATE SCHEMA " + schema);

        DriverManagerDataSource scoped = new DriverManagerDataSource();
        scoped.setDriverClassName("org.postgresql.Driver");
        String jdbcUrl = POSTGRES.getJdbcUrl();
        scoped.setUrl(jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema);
        scoped.setUsername(POSTGRES.getUsername());
        scoped.setPassword(POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(scoped);
        return new TestDatabase(schema, scoped, jdbc, new DataSourceTransactionManager(scoped));
    }

    private static void createEffectTable(TestDatabase db) {
        db.jdbc.execute("CREATE TABLE consumer_inbox_test_effect (effect_id BIGSERIAL PRIMARY KEY, "
                + "consumer_name VARCHAR(64) NOT NULL, operation_key VARCHAR(512) NOT NULL)");
    }

    private static void migrate(TestDatabase db, String target) {
        FluentConfiguration config = Flyway.configure().dataSource(db.dataSource)
                .locations("classpath:db/migration").schemas(db.schema).defaultSchema(db.schema);
        if (target != null) {
            config.target(target);
        }
        Flyway flyway = config.load();
        flyway.migrate();
    }

    private static String latestVersion(TestDatabase db) {
        return db.jdbc.queryForObject("SELECT version FROM flyway_schema_history "
                + "WHERE success ORDER BY installed_rank DESC LIMIT 1", String.class);
    }

    private static ConsumerInboxBoundary boundary(TestDatabase db) {
        return new ConsumerInboxBoundary(new ConsumerInboxPersistenceAdapter(db.jdbc));
    }

    private static InboxOutcome deliver(TestDatabase db, ConsumerInboxBoundary boundary,
                                        ConsumerInboxClaim claim, Runnable effect) {
        TransactionTemplate transaction = new TransactionTemplate(db.transactionManager);
        return transaction.execute(status -> boundary.execute(claim, effect));
    }

    private static InboxOutcome concurrentDelivery(TestDatabase db, ConsumerInboxBoundary boundary,
            ConsumerInboxClaim claim, CountDownLatch ready, CountDownLatch start, String operationKey)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for concurrent inbox delivery");
        }
        return deliver(db, boundary, claim, () -> insertEffect(db, "lab-result", operationKey));
    }

    private static ConsumerInboxClaim labResultClaim(TestDatabase db, UUID eventId, String operationKey,
            String correlationId, String value) {
        return evidence().create("clinical-lab-result", "lab.result.created", eventId, operationKey,
                labResultBody(eventId, correlationId, value));
    }

    private static ConsumerInboxClaim prescriptionClaim(TestDatabase db, UUID eventId, String operationKey) {
        byte[] body = envelope(eventId, "prescription.filled", "corr-prescription",
                "{\"prescriptionId\":\"" + PRESCRIPTION_ID + "\",\"status\":\"FILLED\"}");
        return evidence().create("clinical-prescription", "prescription.filled", eventId, operationKey, body);
    }

    private static ConsumerInboxClaim clearanceClaim(TestDatabase db, UUID eventId, String operationKey) {
        byte[] body = envelope(eventId, "financial.clearance.granted", "corr-clearance",
                "{\"clearanceId\":\"" + CLEARANCE_ID + "\",\"purpose\":\"EXAM\"}");
        return evidence().create("clinical-exam-clearance", "financial.clearance.granted", eventId,
                operationKey, body);
    }

    private static ConsumerInboxClaim eligibilityClaim(UUID eventId, String operationKey,
            String correlationId, String amount) {
        String payload = "{\"paymentOperationId\":\"00000000-0000-0000-0000-000000001603\","
                + "\"paymentRequestId\":\"00000000-0000-0000-0000-000000001604\","
                + "\"registrationId\":\"00000000-0000-0000-0000-000000001605\","
                + "\"reservationId\":\"00000000-0000-0000-0000-000000001606\","
                + "\"fenceToken\":19,\"patientId\":\"00000000-0000-0000-0000-000000001607\","
                + "\"sourceType\":\"EXAM\",\"sourceId\":\"00000000-0000-0000-0000-000000001605\","
                + "\"careEpisodeType\":\"OUTPATIENT_VISIT\","
                + "\"careEpisodeId\":\"00000000-0000-0000-0000-000000001605\","
                + "\"transactionId\":\"00000000-0000-0000-0000-000000001608\","
                + "\"clearanceId\":\"00000000-0000-0000-0000-000000001609\","
                + "\"amount\":\"" + amount + "\",\"currency\":\"VND\","
                + "\"completedAt\":\"2026-10-26T04:00:00Z\",\"basis\":\"PATIENT_PAYMENT\",\"quoteId\":null}";
        String json = "{\"eventId\":\"" + eventId + "\","
                + "\"eventType\":\"outpatient.financial.eligibility.granted\",\"version\":1,"
                + "\"occurredAt\":\"2026-10-26T04:00:00Z\",\"correlationId\":\"" + correlationId + "\","
                + "\"producer\":\"billing-service\",\"payload\":" + payload + "}";
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        return evidence().create("clinical-outpatient-eligibility", "outpatient.financial.eligibility.granted",
                eventId, operationKey, body);
    }

    private static InboxEvidenceFactory evidence() {
        return new InboxEvidenceFactory(new ObjectMapper());
    }

    private static PaymentIds seedLockedPayment(TestDatabase db) {
        UUID registrationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-26T04:00:00Z");
        db.jdbc.update("""
                INSERT INTO appointment (appointment_id, patient_id, doctor_id, department_id, appointment_date,
                    appointment_time, status, reason, created_at, care_contract_version, exam_price_code)
                VALUES (?, ?, ?, ?, DATE '2026-10-27', TIME '09:00', 'AWAITING_PAYMENT', 'Visit', ?, 2, 'OUTPATIENT_EXAM')
                """, registrationId, patientId, doctorId, departmentId, Timestamp.from(now));
        db.jdbc.update("""
                INSERT INTO consultation_session (session_id, revision, doctor_id, department_id, room_id,
                    session_date, start_time, end_time, breaks, capacity, status, held_count,
                    payment_locked_count, confirmed_count, consumed_count, available_count, created_at, updated_at)
                VALUES (?, 1, ?, ?, ?, DATE '2026-10-27', TIME '09:00', TIME '10:00', '[]'::jsonb,
                    1, 'OPEN', 0, 1, 0, 0, 0, ?, ?)
                """, sessionId, doctorId, departmentId, roomId, Timestamp.from(now), Timestamp.from(now));
        db.jdbc.update("""
                INSERT INTO outpatient_registration (registration_id, session_id, patient_id, revision,
                    reception_mode, visit_type, status, reservation_id, reservation_revision,
                    reservation_status, reservation_expires_at, active_payment_operation_id, active_fence_token,
                    locked_at, reconcile_after, created_at, updated_at)
                VALUES (?, ?, ?, 1, 'PREBOOKED', 'NEW_VISIT', 'AWAITING_PAYMENT', ?, 2,
                    'PAYMENT_LOCKED', ?, ?, 19, ?, ?, ?, ?)
                """, registrationId, sessionId, patientId, reservationId,
                Timestamp.from(now.plusSeconds(3600)), operationId, Timestamp.from(now), Timestamp.from(now.minusSeconds(120)),
                Timestamp.from(now), Timestamp.from(now));
        db.jdbc.update("""
                INSERT INTO outpatient_payment_operation_receipt (payment_operation_id, payment_request_id,
                    registration_id, reservation_id, patient_id, source_type, source_id, care_episode_id,
                    expected_reservation_revision, lease_reservation_revision, fence_token, state,
                    locked_at, reconcile_after, basis, quote_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'EXAM', ?, ?, 2, 2, 19, 'PAYMENT_LOCKED', ?, ?, 'PATIENT_PAYMENT', NULL, ?, ?)
                """, operationId, requestId, registrationId, reservationId, patientId, registrationId,
                registrationId, Timestamp.from(now), Timestamp.from(now.minusSeconds(120)),
                Timestamp.from(now), Timestamp.from(now));
        return new PaymentIds(operationId);
    }

    private static <T> T transaction(TestDatabase db, java.util.function.Supplier<T> action) {
        return new TransactionTemplate(db.transactionManager).execute(status -> action.get());
    }

    private static byte[] labResultBody(UUID eventId, String correlationId, String value) {
        return envelope(eventId, "lab.result.created", correlationId,
                "{\"labTestId\":\"" + LAB_ID + "\",\"result\":\"" + value + "\"}");
    }

    private static byte[] envelope(UUID eventId, String eventType, String correlationId, String payload) {
        String json = "{\"eventId\":\"" + eventId + "\",\"eventType\":\"" + eventType
                + "\",\"occurredAt\":\"2026-10-15T10:00:00Z\",\"correlationId\":\""
                + correlationId + "\",\"payload\":" + payload + "}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] canonicalFor(TestDatabase db, UUID eventId) {
        return db.jdbc.queryForObject("SELECT canonical_business_bytes FROM consumer_inbox_delivery "
                + "WHERE event_id = ?", byte[].class, eventId);
    }

    private static void insertEffect(TestDatabase db, String consumerName, String operationKey) {
        db.jdbc.update("INSERT INTO consumer_inbox_test_effect (consumer_name, operation_key) VALUES (?, ?)",
                consumerName, operationKey);
    }

    private static void assertLegacyClaimRejected(TestDatabase db, ConsumerInboxBoundary boundary,
                                                  ConsumerInboxClaim claim) {
        assertThatThrownBy(() -> deliver(db, boundary, claim, () -> failIfCalled()))
                .isInstanceOf(ConsumerInboxConflictException.class);
        assertThat(count(db, "consumer_inbox_delivery")).isZero();
        assertThat(count(db, "consumer_inbox_operation")).isZero();
    }

    private static void seedLegacyClinicalEvidence(TestDatabase db) {
        db.jdbc.update("INSERT INTO medical_record (record_id, patient_id, doctor_id, department_id, "
                        + "examination_date, symptoms) VALUES (?, ?, ?, ?, ?, ?)",
                RECORD_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 10, 14), "legacy record");
        db.jdbc.update("INSERT INTO attached_result (record_id, type, reference_id, summary) VALUES (?, 'LAB', ?, ?)",
                RECORD_ID, LAB_ID, "legacy result");
        db.jdbc.update("INSERT INTO attached_result (record_id, type, reference_id, summary) "
                        + "VALUES (?, 'PRESCRIPTION', ?, ?)",
                RECORD_ID, PRESCRIPTION_ID, "legacy prescription");
        db.jdbc.update("INSERT INTO processed_event (event_id, routing_key) VALUES (?, ?)",
                OLD_EVENT_ID, "lab.result.created");
        db.jdbc.update("""
                INSERT INTO exam_clearance (
                    clearance_id, event_id, invoice_id, account_id, record_id, patient_id,
                    care_episode_type, care_episode_id, amount, currency, granted_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'OUTPATIENT_VISIT', ?, 100.00, 'VND', ?)
                """, CLEARANCE_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), RECORD_ID,
                UUID.randomUUID(), UUID.randomUUID(), Timestamp.from(Instant.parse("2026-10-14T10:00:00Z")));
    }

    private static int count(TestDatabase db, String table) {
        return db.jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private static void failIfCalled() {
        throw new AssertionError("An unapplied inbox claim must not execute its business effect");
    }

    private record PaymentIds(UUID operationId) { }

    private record TestDatabase(String schema, DriverManagerDataSource dataSource, JdbcTemplate jdbc,
                                PlatformTransactionManager transactionManager) { }
}
