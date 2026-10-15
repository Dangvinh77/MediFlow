package com.mediflow.lab.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.lab.application.dto.command.ConsumerInboxClaim;
import com.mediflow.lab.application.exception.ConsumerInboxConflictException;
import com.mediflow.lab.application.port.out.ConsumerInboxPort.InboxOutcome;
import com.mediflow.lab.application.service.ConsumerInboxBoundary;
import com.mediflow.lab.infrastructure.persistence.adapter.ConsumerInboxPersistenceAdapter;
import com.mediflow.lab.messaging.consumer.inbox.InboxEvidenceFactory;
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
    private static final UUID RECORD_ID = UUID.fromString("00000000-0000-0000-0000-000000002001");
    private static final UUID TEST_ID = UUID.fromString("00000000-0000-0000-0000-000000002002");
    private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000002003");
    private static final UUID CLEARANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000002004");
    private static final UUID OLD_EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000002005");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @org.junit.jupiter.api.Test
    void freshSchema_preservesExactDeliveryBytesAndDedupeSemantics() {
        TestDatabase db = migrateFresh();
        assertThat(latestVersion(db)).isEqualTo("3");
        ConsumerInboxBoundary boundary = boundary(db);
        String operationKey = "lab:medical-record:v0:" + RECORD_ID;
        UUID firstEvent = UUID.fromString("00000000-0000-0000-0000-000000002101");
        UUID secondEvent = UUID.fromString("00000000-0000-0000-0000-000000002102");
        UUID thirdEvent = UUID.fromString("00000000-0000-0000-0000-000000002103");
        byte[] firstRaw = medicalRecordBody(firstEvent, "corr-a", "OPEN");
        ConsumerInboxClaim first = medicalRecordClaim(firstEvent, operationKey, "corr-a", "OPEN");

        assertThat(deliver(db, boundary, first, () -> insertEffect(db, "medical-record", operationKey)))
                .isEqualTo(InboxOutcome.APPLIED);
        assertThat(deliver(db, boundary, first, () -> insertEffect(db, "medical-record", operationKey)))
                .isEqualTo(InboxOutcome.DUPLICATE_EVENT);

        ConsumerInboxClaim sameOperation = medicalRecordClaim(secondEvent, operationKey, "corr-b", "OPEN");
        assertThat(deliver(db, boundary, sameOperation,
                () -> insertEffect(db, "medical-record", operationKey)))
                .isEqualTo(InboxOutcome.DUPLICATE_OPERATION);
        assertThat(db.jdbc.queryForObject("SELECT raw_body FROM consumer_inbox_delivery WHERE event_id = ?",
                byte[].class, firstEvent)).containsExactly(firstRaw);
        byte[] firstCanonical = canonicalFor(db, firstEvent);
        assertThat(canonicalFor(db, secondEvent)).containsExactly(firstCanonical);

        ConsumerInboxClaim changedDelivery = medicalRecordClaim(firstEvent, operationKey, "corr-changed", "OPEN");
        assertThatThrownBy(() -> deliver(db, boundary, changedDelivery,
                () -> insertEffect(db, "medical-record", operationKey)))
                .isInstanceOf(ConsumerInboxConflictException.class);
        ConsumerInboxClaim changedBusiness = medicalRecordClaim(thirdEvent, operationKey, "corr-c", "COMPLETED");
        assertThatThrownBy(() -> deliver(db, boundary, changedBusiness,
                () -> insertEffect(db, "medical-record", operationKey)))
                .isInstanceOf(ConsumerInboxConflictException.class);

        assertThat(count(db, "consumer_inbox_delivery")).isEqualTo(2);
        assertThat(count(db, "consumer_inbox_operation")).isEqualTo(1);
        assertThat(count(db, "consumer_inbox_test_effect")).isEqualTo(1);
    }

    @org.junit.jupiter.api.Test
    void oneGlobalEventIdCannotBeClaimedByDifferentLabConsumers() {
        TestDatabase db = migrateFresh();
        ConsumerInboxBoundary boundary = boundary(db);
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000002201");
        ConsumerInboxClaim record = medicalRecordClaim(eventId,
                "lab:medical-record:v0:" + RECORD_ID, "corr-global", "OPEN");
        ConsumerInboxClaim payment = paymentClaim(eventId, "lab:payment:v0:" + INVOICE_ID,
                "corr-global", List.of());

        assertThat(deliver(db, boundary, record, () -> insertEffect(db, "medical-record", RECORD_ID.toString())))
                .isEqualTo(InboxOutcome.APPLIED);
        assertThatThrownBy(() -> deliver(db, boundary, payment,
                () -> insertEffect(db, "payment", INVOICE_ID.toString())))
                .isInstanceOf(ConsumerInboxConflictException.class);

        assertThat(count(db, "consumer_inbox_delivery")).isEqualTo(1);
        assertThat(count(db, "consumer_inbox_operation")).isEqualTo(1);
        assertThat(count(db, "consumer_inbox_test_effect")).isEqualTo(1);
    }

    @org.junit.jupiter.api.Test
    void throwingEffectRollsBackBothInboxClaimsAndBusinessEffect() {
        TestDatabase db = migrateFresh();
        ConsumerInboxBoundary boundary = boundary(db);
        ConsumerInboxClaim claim = medicalRecordClaim(
                UUID.fromString("00000000-0000-0000-0000-000000002301"),
                "lab:medical-record:v0:" + RECORD_ID, "corr-rollback", "OPEN");

        assertThatThrownBy(() -> deliver(db, boundary, claim, () -> {
            insertEffect(db, "medical-record", claim.operationKey());
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
        String operationKey = "lab:medical-record:v0:" + RECORD_ID;
        ConsumerInboxClaim first = medicalRecordClaim(
                UUID.fromString("00000000-0000-0000-0000-000000002401"), operationKey, "corr-race-a", "OPEN");
        ConsumerInboxClaim second = medicalRecordClaim(
                UUID.fromString("00000000-0000-0000-0000-000000002402"), operationKey, "corr-race-b", "OPEN");
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
    void upgradeFromV2PreservesLegacyEvidenceAndFailsClosedForUnverifiableEffects() {
        TestDatabase db = newDatabase();
        migrate(db, "2");
        assertThat(latestVersion(db)).isEqualTo("2");
        seedLegacyLabEvidence(db);

        migrate(db, null);

        assertThat(latestVersion(db)).isEqualTo("3");
        createEffectTable(db);
        assertThat(count(db, "processed_event")).isEqualTo(1);
        assertThat(count(db, "lab_test")).isEqualTo(1);
        assertThat(count(db, "lab_financial_clearance")).isEqualTo(1);

        ConsumerInboxBoundary boundary = boundary(db);
        ConsumerInboxClaim oldReceipt = paymentClaim(OLD_EVENT_ID,
                "lab:payment:v0:" + UUID.randomUUID(), "corr-old-receipt", List.of());
        assertThatThrownBy(() -> deliver(db, boundary, oldReceipt, () -> failIfCalled()))
                .isInstanceOf(ConsumerInboxConflictException.class);

        ConsumerInboxClaim oldPaidEffect = paymentClaim(
                UUID.fromString("00000000-0000-0000-0000-000000002501"),
                "lab:payment:v0:" + INVOICE_ID, "corr-old-payment", List.of(TEST_ID));
        assertLegacyClaimRejected(db, boundary, oldPaidEffect);
        ConsumerInboxClaim oldClearanceEffect = clearanceClaim(
                UUID.fromString("00000000-0000-0000-0000-000000002502"),
                "lab:financial-clearance:v1:" + CLEARANCE_ID, "corr-old-clearance");
        assertLegacyClaimRejected(db, boundary, oldClearanceEffect);

        assertThat(count(db, "consumer_inbox_delivery")).isZero();
        assertThat(count(db, "consumer_inbox_operation")).isZero();
        assertThat(count(db, "processed_event")).isEqualTo(1);
        assertThat(count(db, "lab_test")).isEqualTo(1);
        assertThat(count(db, "lab_financial_clearance")).isEqualTo(1);
    }

    private static TestDatabase migrateFresh() {
        TestDatabase db = newDatabase();
        migrate(db, null);
        createEffectTable(db);
        return db;
    }

    private static TestDatabase newDatabase() {
        String schema = "lab_inbox_" + UUID.randomUUID().toString().replace("-", "");
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
        return deliver(db, boundary, claim, () -> insertEffect(db, "medical-record", operationKey));
    }

    private static ConsumerInboxClaim medicalRecordClaim(UUID eventId, String operationKey,
            String correlationId, String status) {
        byte[] body = medicalRecordBody(eventId, correlationId, status);
        return evidence().create("lab-medical-record", "medicalrecord.created", eventId, operationKey, body);
    }

    private static ConsumerInboxClaim paymentClaim(UUID eventId, String operationKey, String correlationId,
            List<UUID> legacyEffectIds) {
        byte[] body = envelope(eventId, "payment.completed", correlationId,
                "{\"invoiceId\":\"" + INVOICE_ID + "\",\"paid\":true}");
        return evidence().create("lab-payment", "payment.completed", eventId, operationKey, body,
                legacyEffectIds);
    }

    private static ConsumerInboxClaim clearanceClaim(UUID eventId, String operationKey, String correlationId) {
        byte[] body = envelope(eventId, "financial.clearance.granted", correlationId,
                "{\"clearanceId\":\"" + CLEARANCE_ID + "\",\"purpose\":\"LAB_TEST\"}");
        return evidence().create("lab-test-clearance", "financial.clearance.granted", eventId,
                operationKey, body);
    }

    private static InboxEvidenceFactory evidence() {
        return new InboxEvidenceFactory(new ObjectMapper());
    }

    private static byte[] medicalRecordBody(UUID eventId, String correlationId, String status) {
        return envelope(eventId, "medicalrecord.created", correlationId,
                "{\"recordId\":\"" + RECORD_ID + "\",\"status\":\"" + status + "\"}");
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

    private static void seedLegacyLabEvidence(TestDatabase db) {
        db.jdbc.update("INSERT INTO lab_test (test_id, record_id, patient_id, requesting_department_id, "
                        + "test_type, requested_date, is_paid) VALUES (?, ?, ?, ?, ?, ?, TRUE)",
                TEST_ID, RECORD_ID, UUID.randomUUID(), UUID.randomUUID(), "CBC", LocalDate.of(2026, 10, 14));
        db.jdbc.update("INSERT INTO processed_event (event_id, routing_key) VALUES (?, ?)",
                OLD_EVENT_ID, "payment.completed");
        db.jdbc.update("""
                INSERT INTO lab_financial_clearance (
                    clearance_target_id, clearance_id, event_id, invoice_id, account_id, test_id,
                    patient_id, care_episode_type, care_episode_id, amount, currency, granted_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'OUTPATIENT_VISIT', ?, 100.00, 'VND', ?)
                """, UUID.randomUUID(), CLEARANCE_ID, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), TEST_ID, UUID.randomUUID(), UUID.randomUUID(),
                Timestamp.from(Instant.parse("2026-10-14T10:00:00Z")));
    }

    private static int count(TestDatabase db, String table) {
        return db.jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private static void failIfCalled() {
        throw new AssertionError("An unapplied inbox claim must not execute its business effect");
    }

    private record TestDatabase(String schema, DriverManagerDataSource dataSource, JdbcTemplate jdbc,
                                PlatformTransactionManager transactionManager) { }
}
