package com.mediflow.billing;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.dto.request.CompleteLedgerPaymentRequest;
import com.mediflow.billing.application.dto.command.ScheduledPaymentCommand;
import com.mediflow.billing.application.port.out.ScheduledPaymentTransactionsPort;
import com.mediflow.billing.application.port.out.OutpatientRegistrationSourcePort;
import com.mediflow.billing.application.port.in.ProcessLedgerPaymentUseCase;
import com.mediflow.billing.application.event.OutpatientRegistrationChangedEvent;
import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.domain.model.ScheduledPaymentLease;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.application.dto.request.ScheduledPaymentRequest;
import com.mediflow.billing.infrastructure.persistence.repository.BillingEventOutboxJpaRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class LedgerPaymentIntegrationTest {
    private static final String SECRET = "billing-ledger-integration-secret-at-least-32-bytes";
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> false);
        registry.add("mediflow.jwt.secret", () -> SECRET);
        registry.add("eureka.client.enabled", () -> false);
        registry.add("mediflow.billing.ledger.enabled", () -> true);
        registry.add("mediflow.billing.scheduled-payments.enabled", () -> true);
        registry.add("mediflow.billing.scheduled-payments.recovery-initial-delay-ms", () -> 3600000);
        registry.add("mediflow.billing.scheduled-payments.recovery-delay-ms", () -> 3600000);
        registry.add("mediflow.billing.refunds.enabled", () -> true);
        registry.add("mediflow.billing.clearance-lookup.enabled", () -> true);
        registry.add("mediflow.billing.outbox.enabled", () -> false);
        registry.add("mediflow.billing.outbox.metrics-enabled", () -> false);
        registry.add("mediflow.billing.outbox.maintenance-enabled", () -> false);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ProcessLedgerPaymentUseCase payments;
    @Autowired com.mediflow.billing.application.port.in.RefundLedgerPaymentUseCase refunds;
    @Autowired PlatformTransactionManager manager;
    @Autowired BillingEventOutboxJpaRepository outbox;
    @Autowired ScheduledPaymentTransactionsPort scheduledPayments;
    @Autowired OutpatientRegistrationSourcePort registrationSources;
    @Autowired ObjectMapper mapper;
    @Autowired TestRestTemplate http;
    @org.springframework.boot.test.mock.mockito.MockBean(name = "billingClock")
    java.time.Clock clock;
    @org.springframework.boot.test.mock.mockito.MockBean
    com.mediflow.billing.infrastructure.client.ClinicalPaymentLockHttpAdapter clinicalPayment;
    private UUID account, patient, department, request, invoice, episode, source, actor, reservation;
    @TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS) java.nio.file.Path processDirectory;

    @BeforeEach void setup() {
        org.mockito.Mockito.when(clock.instant()).thenAnswer(ignored -> Instant.now());
        org.mockito.Mockito.when(clock.getZone()).thenReturn(java.time.ZoneOffset.UTC);
        // These tables belong to the fresh Testcontainers database, never a configured developer DB.
        jdbc.execute("TRUNCATE BILLING_ACCOUNT, BILLING_EVENT_OUTBOX CASCADE");
        account = UUID.randomUUID(); patient = UUID.randomUUID(); department = UUID.randomUUID();
        request = UUID.randomUUID(); invoice = UUID.randomUUID(); episode = UUID.randomUUID();
        source = UUID.randomUUID(); actor = UUID.randomUUID();
    }

    @Test void outpatientRegistrationRevisionCannotReplaceLockedSourceBindings() throws Exception {
        UUID registrationId = UUID.randomUUID();
        UUID originalReservation = UUID.randomUUID();
        var first = registrationEvent(registrationId, 1, originalReservation, department,
                UUID.randomUUID(), UUID.randomUUID(), registrationId);
        registrationSources.apply(first, mapper.writeValueAsBytes(first), "0".repeat(64), null, null);

        var changedBindings = List.of(
                registrationEvent(registrationId, 2, UUID.randomUUID(), department,
                        first.payload().sessionId(), first.payload().roomId(), registrationId),
                registrationEvent(registrationId, 2, originalReservation, UUID.randomUUID(),
                        first.payload().sessionId(), first.payload().roomId(), registrationId),
                registrationEvent(registrationId, 2, originalReservation, department,
                        UUID.randomUUID(), first.payload().roomId(), registrationId),
                registrationEvent(registrationId, 2, originalReservation, department,
                        first.payload().sessionId(), UUID.randomUUID(), registrationId));
        changedBindings = new java.util.ArrayList<>(changedBindings);
        changedBindings.add(registrationEvent(registrationId, 2, originalReservation, department,
                first.payload().sessionId(), first.payload().roomId(), UUID.randomUUID()));

        for (var changed : changedBindings) {
            byte[] raw = mapper.writeValueAsBytes(changed);
            assertThatThrownBy(() -> registrationSources.apply(changed, raw,
                    sha256(raw), null, null))
                    .isInstanceOf(BillingRuleException.class)
                    .hasMessageContaining("BILLING_REGISTRATION_SOURCE_CONFLICT");
        }

        var stored = jdbc.queryForMap("SELECT registration_revision,reservation_id,department_id,session_id,room_id "
                + "FROM outpatient_registration_source WHERE registration_id=?", registrationId);
        assertThat(stored.get("registration_revision")).isEqualTo(1L);
        assertThat(stored.get("reservation_id")).isEqualTo(originalReservation);
        assertThat(stored.get("department_id")).isEqualTo(department);
        assertThat(stored.get("session_id")).isEqualTo(first.payload().sessionId());
        assertThat(stored.get("room_id")).isEqualTo(first.payload().roomId());
    }

    private OutpatientRegistrationChangedEvent registrationEvent(UUID registrationId, long revision,
            UUID reservationId, UUID departmentId, UUID sessionId, UUID roomId, UUID sourceId) {
        var fact = new OutpatientRegistrationChangedEvent.RegistrationFact(registrationId, revision, patient,
                UUID.nameUUIDFromBytes((registrationId + ":doctor").getBytes(StandardCharsets.UTF_8)), departmentId,
                sessionId, roomId, java.time.LocalDate.of(2026, 10, 26),
                java.time.LocalTime.of(9, 0), java.time.LocalTime.of(9, 30), "NEW_VISIT", reservationId,
                revision == 1 ? "HELD" : "PAYMENT_LOCKED", "AWAITING_PAYMENT", null, null,
                Instant.parse("2026-10-26T02:00:00Z"), "OUTPATIENT_EXAM", "EXAM", sourceId,
                "OUTPATIENT_VISIT", registrationId, null, Instant.parse("2026-10-26T01:00:00Z"));
        return new OutpatientRegistrationChangedEvent(UUID.randomUUID(), "outpatient.registration.changed", 1,
                Instant.parse("2026-10-26T01:00:00Z"), "trace", "clinical-service", fact);
    }

    private static String sha256(byte[] raw) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "2026-10-18T08:00:00.123456100Z", "2026-10-18T08:00:00.999999900Z"})
    void httpPaymentReplayPreservesExactCompletedAtAtDatabasePrecision(String timestamp) throws Exception {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        org.mockito.Mockito.when(clock.instant()).thenReturn(Instant.parse(timestamp));
        String path = "/api/v1/billing/payment-requests/" + request + "/payments";
        var first = http.postForEntity(path, new HttpEntity<>(command("precision", "100"), headers("CASHIER")), String.class);
        var retry = http.postForEntity(path, new HttpEntity<>(command("precision", "100"), headers("CASHIER")), String.class);
        assertThat(first.getStatusCode()).as(first.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        var original = mapper.readTree(first.getBody()).path("data");
        var replayed = mapper.readTree(retry.getBody()).path("data");
        assertThat(Instant.parse(original.path("completedAt").asText()))
                .isEqualTo(Instant.parse(timestamp).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        for (String field : List.of("transactionId", "accountId", "paymentRequestId", "classification", "status",
                "currency", "paymentMethod", "completedAt")) {
            assertThat(replayed.path(field)).as(field).isEqualTo(original.path(field));
        }
        assertThat(replayed.path("amount").decimalValue()).isEqualByComparingTo(original.path("amount").decimalValue());
        assertThat(Instant.parse(original.path("completedAt").asText()))
                .isEqualTo(jdbc.queryForObject("SELECT completed_at FROM PAYMENT_TRANSACTION", java.sql.Timestamp.class).toInstant());
        var receipt = mapper.readTree(jdbc.queryForObject(
                "SELECT payload FROM BILLING_EVENT_OUTBOX WHERE routing_key='payment.completed'", String.class));
        assertThat(receipt.path("payload").path("completedAt")).isEqualTo(original.path("completedAt"));
        assertThat(count("PAYMENT_TRANSACTION")).isOne();
        assertThat(count("FINANCIAL_CLEARANCE")).isOne();
        assertThat(count("BILLING_EVENT_OUTBOX")).isEqualTo(2);
    }

    @Test void threeInstallmentsHaveThreeReceiptsOneClearanceAndNoDoubleEffect() throws Exception {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        var first = payments.complete(request, command("first", "20"), actor, "trace-1");
        payments.complete(request, command("second", "30"), actor, "trace-2");
        payments.complete(request, command("third", "50"), actor, "trace-3");
        assertThat(payments.complete(request, command("first", "20.00"), actor, "retry").transactionId()).isEqualTo(first.transactionId());
        assertThat(count("PAYMENT_TRANSACTION")).isEqualTo(3);
        assertThat(count("FINANCIAL_CLEARANCE")).isEqualTo(1);
        assertThat(count("BILLING_EVENT_OUTBOX")).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM PAYMENT_ALLOCATION", BigDecimal.class)).isEqualByComparingTo("100");
        String body = jdbc.queryForObject("SELECT payload FROM BILLING_EVENT_OUTBOX WHERE routing_key='financial.clearance.granted'", String.class);
        var event = mapper.readTree(body);
        assertThat(event.path("producer").asText()).isEqualTo("billing-service");
        assertThat(event.path("payload").path("prescriptionId").asText()).isEqualTo(source.toString());
        assertThat(event.path("payload").path("careEpisodeId").asText()).isEqualTo(episode.toString());
        assertThat(event.path("payload").path("amount").decimalValue()).isEqualByComparingTo("100");
        assertThat(event.path("payload").path("clearanceId").asText()).isNotEqualTo(request.toString());
    }

    @Test void depositProducesLiabilityReceiptAndNoEarnedAllocation() throws Exception {
        seed("ADMISSION_DEPOSIT", "ADMISSION", "100");
        payments.complete(request, command("deposit", "100"), actor, "deposit-trace");
        assertThat(count("PAYMENT_ALLOCATION")).isZero();
        var receipt = mapper.readTree(jdbc.queryForObject("SELECT payload FROM BILLING_EVENT_OUTBOX WHERE routing_key='payment.completed'", String.class));
        assertThat(receipt.path("payload").path("classification").asText()).isEqualTo("ADMISSION_DEPOSIT");
        var clearance = mapper.readTree(jdbc.queryForObject("SELECT payload FROM BILLING_EVENT_OUTBOX WHERE routing_key='financial.clearance.granted'", String.class));
        assertThat(clearance.path("payload").path("admissionId").asText()).isEqualTo(episode.toString());
    }

    @Test void surgeryClearanceBindsExactCaseAndAdmission() throws Exception {
        seed("SURGERY", "ADMISSION", "100");
        payments.complete(request, command("surgery", "100"), actor, "surgery-trace");
        var event = mapper.readTree(jdbc.queryForObject("SELECT payload FROM BILLING_EVENT_OUTBOX WHERE routing_key='financial.clearance.granted'", String.class));
        assertThat(event.path("payload").path("surgeryCaseId").asText()).isEqualTo(source.toString());
        assertThat(event.path("payload").path("admissionId").asText()).isEqualTo(episode.toString());
    }

    @Test void wrongTargetOrCrossAccountChargeFailsClosedWithoutReceipt() {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        jdbc.update("UPDATE PAYMENT_REQUEST_TARGET SET prescription_id=?", UUID.randomUUID());
        assertThatThrownBy(() -> payments.complete(request, command("wrong", "100"), actor, "trace"))
                .isInstanceOf(BillingRuleException.class).hasMessage("BILLING_REQUEST_SOURCE_TARGET_MISMATCH");
        assertThat(count("PAYMENT_TRANSACTION")).isZero();
        assertThat(count("BILLING_EVENT_OUTBOX")).isZero();
    }

    @Test void scheduledPaymentCommitsPreparingBeforeCallingClinicalAndReplaysReceipt() throws Exception {
        seedScheduledRegistration();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        org.mockito.Mockito.when(clinicalPayment.acquire(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    var operation = (com.mediflow.billing.domain.model.ScheduledPaymentOperation) invocation.getArgument(0);
                    entered.countDown();
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test release timed out");
                    Instant lockedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
                    return new ScheduledPaymentLease(operation.paymentOperationId(), operation.reservationId(),
                            operation.expectedReservationRevision() + 1, 11L, lockedAt, lockedAt.plusSeconds(120));
                });
        var body = new ScheduledPaymentRequest(reservation, 5, "100", "VND", "CASH", null);
        var headers = headers("CASHIER");
        headers.set("Idempotency-Key", "scheduled-preparing-first");
        headers.set("X-Correlation-Id", "scheduled-preparing-trace");
        var executor = Executors.newSingleThreadExecutor();
        try {
            var responseFuture = executor.submit(() -> http.postForEntity(
                    "/api/v2/billing/payment-requests/" + request + "/payments",
                    new HttpEntity<>(body, headers), String.class));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("SELECT status FROM scheduled_payment_operation", String.class))
                    .isEqualTo("PREPARING");
            assertThat(count("PAYMENT_TRANSACTION")).isZero();
            release.countDown();
            var response = responseFuture.get(15, TimeUnit.SECONDS);
            assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
            var operationId = UUID.fromString(mapper.readTree(response.getBody()).path("data").path("operation")
                    .path("paymentOperationId").asText());
            assertThat(jdbc.queryForObject("SELECT status FROM scheduled_payment_operation", String.class))
                    .isEqualTo("COMPLETED");
            assertThat(count("PAYMENT_TRANSACTION")).isOne();
            assertThat(count("PAYMENT_ALLOCATION")).isOne();
            assertThat(count("FINANCIAL_CLEARANCE")).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM BILLING_EVENT_OUTBOX WHERE routing_key='financial.clearance.granted'", Long.class))
                    .isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM BILLING_EVENT_OUTBOX WHERE routing_key='outpatient.financial.eligibility.granted' AND publication_enabled=FALSE", Long.class))
                    .isOne();

            var replayHeaders = headers("CASHIER");
            replayHeaders.set("Idempotency-Key", "scheduled-preparing-first");
            replayHeaders.set("X-Correlation-Id", "scheduled-preparing-retry");
            var replay = http.postForEntity("/api/v2/billing/payment-requests/" + request + "/payments",
                    new HttpEntity<>(body, replayHeaders), String.class);
            assertThat(replay.getStatusCode()).as(replay.getBody()).isEqualTo(HttpStatus.OK);
            assertThat(mapper.readTree(replay.getBody()).path("data").path("operation").path("paymentOperationId").asText())
                    .isEqualTo(operationId.toString());
            assertThat(count("PAYMENT_TRANSACTION")).isOne();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"BEFORE_ACQUIRE", "AFTER_COMMIT", "CLOSED_BEFORE_RESTART"})
    void killedPaymentProcess_recoversDurableOutcomeWithoutDuplicateCash(String phase) throws Exception {
        seedScheduledRegistration();
        var barrier = processDirectory.resolve("crash-barrier.txt");
        Process child = paymentProcess("CLOSED_BEFORE_RESTART".equals(phase) ? "BEFORE_ACQUIRE" : phase,
                barrier, processDirectory.resolve("before-crash.log"));
        UUID operationId;
        try {
            try {
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(120)).until(() ->
                        java.nio.file.Files.exists(barrier) || !child.isAlive());
            } catch (org.awaitility.core.ConditionTimeoutException timeout) {
                throw new AssertionError(processLog(processDirectory.resolve("before-crash.log")), timeout);
            }
            assertThat(java.nio.file.Files.exists(barrier))
                    .withFailMessage("Child exited before crash barrier:\n%s",
                            processLog(processDirectory.resolve("before-crash.log"))).isTrue();
            String receipt = java.nio.file.Files.readString(barrier);
            operationId = UUID.fromString(receipt.split(":")[0]);
            assertThat(scheduledPayments.findOperation(operationId).status().name())
                    .isEqualTo("AFTER_COMMIT".equals(phase) ? "COMPLETED" : "PREPARING");
            assertThat(count("PAYMENT_TRANSACTION")).isEqualTo("AFTER_COMMIT".equals(phase) ? 1 : 0);
        } finally {
            child.destroyForcibly();
            assertThat(child.waitFor(20, TimeUnit.SECONDS)).isTrue();
        }
        if ("CLOSED_BEFORE_RESTART".equals(phase)) {
            scheduledPayments.closeIfUnpaid(operationId, new ScheduledPaymentTransactionsPort.ClosePaymentCommand(
                    source, reservation, 41, "RESERVATION_RECONCILIATION"), Instant.now());
        }
        var delivered = processDirectory.resolve("recovered-receipt.txt");
        Process restarted = paymentProcess("RECOVER", delivered, processDirectory.resolve("after-restart.log"));
        try {
            assertThat(restarted.waitFor(120, TimeUnit.SECONDS))
                    .withFailMessage("Recovery JVM timed out:\n%s", processLog(processDirectory.resolve("after-restart.log"))).isTrue();
            assertThat(restarted.exitValue()).withFailMessage("Recovery JVM failed:\n%s",
                    processLog(processDirectory.resolve("after-restart.log"))).isZero();
        } finally {
            if (restarted.isAlive()) {
                restarted.destroyForcibly();
                assertThat(restarted.waitFor(20, TimeUnit.SECONDS)).isTrue();
            }
        }
        var recovered = scheduledPayments.findOperation(operationId);
        if ("CLOSED_BEFORE_RESTART".equals(phase)) {
            assertThat(recovered.status().name()).isEqualTo("CLOSED_NOT_PAID");
            assertThat(count("PAYMENT_TRANSACTION")).isZero();
            assertThat(count("FINANCIAL_CLEARANCE")).isZero();
            assertThat(java.nio.file.Files.exists(delivered)).isFalse();
        } else {
            assertThat(recovered.status().name()).isEqualTo("COMPLETED");
            assertThat(recovered.resolutionAppliedAt()).isNotNull();
            assertThat(count("PAYMENT_TRANSACTION")).isOne();
            assertThat(count("FINANCIAL_CLEARANCE")).isOne();
            assertThat(java.nio.file.Files.readString(delivered)).isEqualTo(operationId + ":"
                    + recovered.transactionId() + ":" + recovered.clearanceId());
            if ("AFTER_COMMIT".equals(phase)) {
                assertThat(java.nio.file.Files.readString(barrier)).isEqualTo(java.nio.file.Files.readString(delivered));
            }
            scheduledPayments.complete(operationId, "process-crash-replay", Instant.now());
            assertThat(count("PAYMENT_TRANSACTION")).isOne();
        }
        int expectedEffects = "CLOSED_BEFORE_RESTART".equals(phase) ? 0 : 1;
        assertThat(jdbc.queryForObject("SELECT count(*) FROM BILLING_EVENT_OUTBOX "
                + "WHERE routing_key='outpatient.financial.eligibility.granted'", Integer.class))
                .isEqualTo(expectedEffects);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM BILLING_EVENT_OUTBOX "
                + "WHERE routing_key='payment.completed'", Integer.class)).isEqualTo(expectedEffects);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM scheduled_payment_operation", Integer.class)).isOne();
    }

    private String processLog(java.nio.file.Path log) throws java.io.IOException {
        var lines = java.nio.file.Files.readAllLines(log);
        return String.join("\n", lines.subList(Math.max(0, lines.size() - 40), lines.size()));
    }

    private Process paymentProcess(String phase, java.nio.file.Path receipt, java.nio.file.Path log) throws Exception {
        var builder = new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx256m", "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                "com.mediflow.acceptance.fixture.ScheduledPaymentRecoveryProcess", phase, receipt.toString(),
                request.toString(), reservation.toString(), actor.toString());
        builder.environment().putAll(java.util.Map.of(
                "TEST_DB_URL", PG.getJdbcUrl(), "TEST_DB_USER", PG.getUsername(), "TEST_DB_PASSWORD", PG.getPassword(),
                "TEST_RABBIT_HOST", RABBIT.getHost(), "TEST_RABBIT_PORT", RABBIT.getAmqpPort().toString(),
                "TEST_RABBIT_USER", RABBIT.getAdminUsername(), "TEST_RABBIT_PASSWORD", RABBIT.getAdminPassword()));
        return builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
    }

    @Test void persistedLeaseAllowsOneCashCommitAfterOriginalRequestExpiry() {
        seedScheduledRegistration();
        Instant acquiredAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var command = new ScheduledPaymentCommand(reservation, 5, new BigDecimal("100"), "VND", PaymentMethod.CASH, null);
        var operation = scheduledPayments.prepare(request, command, actor, "scheduled-after-expiry",
                "d".repeat(64), "scheduled-after-expiry-trace", acquiredAt);
        scheduledPayments.recordLease(operation.paymentOperationId(), new ScheduledPaymentLease(operation.paymentOperationId(),
                reservation, 6, 51, acquiredAt, acquiredAt.plusSeconds(120)), acquiredAt);
        Instant expiresAt = jdbc.queryForObject("SELECT expires_at FROM PAYMENT_REQUEST WHERE payment_request_id=?",
                java.sql.Timestamp.class, request).toInstant();
        Instant committedAt = expiresAt.plusSeconds(1);
        var completed = scheduledPayments.complete(operation.paymentOperationId(), "scheduled-after-expiry-trace", committedAt);
        assertThat(completed.status().name()).isEqualTo("COMPLETED");
        assertThat(completed.completedAt()).isEqualTo(committedAt);
        var replay = scheduledPayments.complete(operation.paymentOperationId(), "scheduled-after-expiry-replay", committedAt.plusSeconds(10));
        assertThat(replay.transactionId()).isEqualTo(completed.transactionId());
        assertThat(replay.clearanceId()).isEqualTo(completed.clearanceId());
        assertThat(replay.completedAt()).isEqualTo(committedAt);
        assertThat(count("PAYMENT_TRANSACTION")).isOne();
        assertThat(count("FINANCIAL_CLEARANCE")).isOne();
    }

    @Test void closeRacingCashCommitProducesOneDurableTerminalOutcome() throws Exception {
        seedScheduledRegistration();
        var command = new ScheduledPaymentCommand(reservation, 5, new BigDecimal("100"), "VND", PaymentMethod.CASH, null);
        var operation = scheduledPayments.prepare(request, command, actor, "scheduled-close-race",
                "a".repeat(64), "scheduled-close-race-trace", Instant.now());
        Instant lockedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        scheduledPayments.recordLease(operation.paymentOperationId(), new ScheduledPaymentLease(operation.paymentOperationId(),
                reservation, 6, 12, lockedAt, lockedAt.plusSeconds(120)), Instant.now());
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var commit = workers.submit(() -> {
                start.await();
                return scheduledPayments.complete(operation.paymentOperationId(), "scheduled-close-race-trace", Instant.now());
            });
            var close = workers.submit(() -> {
                start.await();
                return scheduledPayments.closeIfUnpaid(operation.paymentOperationId(),
                        new ScheduledPaymentTransactionsPort.ClosePaymentCommand(source, reservation, 12,
                                "RESERVATION_RECONCILIATION"), Instant.now());
            });
            start.countDown();
            commit.get(15, TimeUnit.SECONDS);
            close.get(15, TimeUnit.SECONDS);
        }
        String status = jdbc.queryForObject("SELECT status FROM scheduled_payment_operation", String.class);
        assertThat(status).isIn("COMPLETED", "CLOSED_NOT_PAID");
        assertThat(count("PAYMENT_TRANSACTION")).isEqualTo("COMPLETED".equals(status) ? 1 : 0);
        assertThat(count("FINANCIAL_CLEARANCE")).isEqualTo("COMPLETED".equals(status) ? 1 : 0);
        scheduledPayments.complete(operation.paymentOperationId(), "scheduled-close-race-trace", Instant.now());
        assertThat(jdbc.queryForObject("SELECT status FROM scheduled_payment_operation", String.class)).isEqualTo(status);
        assertThat(count("PAYMENT_TRANSACTION")).isEqualTo("COMPLETED".equals(status) ? 1 : 0);
    }

    @Test void closeAfterLostAcquireReceiptCanFenceLateBillingRetry() {
        seedScheduledRegistration();
        var command = new ScheduledPaymentCommand(reservation, 5, new BigDecimal("100"), "VND", PaymentMethod.CASH, null);
        var operation = scheduledPayments.prepare(request, command, actor, "scheduled-lost-lease",
                "c".repeat(64), "scheduled-lost-lease-trace", Instant.now());
        var close = scheduledPayments.closeIfUnpaid(operation.paymentOperationId(),
                new ScheduledPaymentTransactionsPort.ClosePaymentCommand(source, reservation, 21,
                        "RESERVATION_RECONCILIATION"), Instant.now());
        assertThat(close.operation().status().name()).isEqualTo("CLOSED_NOT_PAID");
        Instant lockedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var lateReceipt = scheduledPayments.recordLease(operation.paymentOperationId(),
                new ScheduledPaymentLease(operation.paymentOperationId(), reservation, 6, 21,
                        lockedAt, lockedAt.plusSeconds(120)), Instant.now());
        assertThat(lateReceipt.status().name()).isEqualTo("CLOSED_NOT_PAID");
        assertThat(lateReceipt.hasLease()).isFalse();
        assertThat(scheduledPayments.complete(operation.paymentOperationId(), "scheduled-lost-lease-trace", Instant.now())
                .status().name()).isEqualTo("CLOSED_NOT_PAID");
        assertThat(count("PAYMENT_TRANSACTION")).isZero();
    }

    @Test void racingInstallmentsCannotOverpayOneRequest() throws Exception {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> attempt(start, "race-a", "80"));
            var b = workers.submit(() -> attempt(start, "race-b", "80"));
            start.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("COMPLETED", "BILLING_PAYMENT_EXCEEDS_REQUEST");
        }
        assertThat(count("PAYMENT_TRANSACTION")).isEqualTo(1);
        assertThat(count("FINANCIAL_CLEARANCE")).isZero();
    }

    @Test void racingSameKeyReturnsOneImmutableTransaction() throws Exception {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> { start.await(); return payments.complete(request, command("same", "100"), actor, "a"); });
            var b = workers.submit(() -> { start.await(); return payments.complete(request, command("same", "100"), actor, "b"); });
            start.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS).transactionId()).isEqualTo(b.get(15, TimeUnit.SECONDS).transactionId());
        }
        assertThat(count("PAYMENT_TRANSACTION")).isEqualTo(1);
        assertThat(count("FINANCIAL_CLEARANCE")).isEqualTo(1);
        assertThat(count("BILLING_EVENT_OUTBOX")).isEqualTo(2);
    }

    @Test void outboxInsertFailureRollsBackPaymentAllocationAndClearance() {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        jdbc.execute("CREATE FUNCTION reject_ledger_event() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test rollback'; END $$");
        jdbc.execute("CREATE TRIGGER reject_ledger_event BEFORE INSERT ON BILLING_EVENT_OUTBOX FOR EACH ROW EXECUTE FUNCTION reject_ledger_event()");
        try {
            assertThatThrownBy(() -> payments.complete(request, command("rollback", "100"), actor, "trace")).isInstanceOf(RuntimeException.class);
            assertThat(count("PAYMENT_TRANSACTION")).isZero();
            assertThat(count("PAYMENT_ALLOCATION")).isZero();
            assertThat(count("FINANCIAL_CLEARANCE")).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM PAYMENT_REQUEST", String.class)).isEqualTo("PENDING");
        } finally {
            jdbc.execute("DROP TRIGGER reject_ledger_event ON BILLING_EVENT_OUTBOX");
            jdbc.execute("DROP FUNCTION reject_ledger_event()");
        }
    }

    @Test void legacyDispatcherDoesNotClaimHeldV1EventsEvenAfterReplay() {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        payments.complete(request, command("held", "100"), actor, "trace");
        assertThatThrownBy(() -> jdbc.update("UPDATE BILLING_EVENT_OUTBOX SET publication_enabled=TRUE WHERE contract_version=1"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var transactions = new TransactionTemplate(manager);
        var held = transactions.execute(status -> outbox.findClaimable(Instant.now().plusSeconds(10), Instant.now().minusSeconds(60), 10));
        assertThat(held).isEmpty();
        UUID eventId = jdbc.queryForObject("SELECT event_id FROM BILLING_EVENT_OUTBOX LIMIT 1", UUID.class);
        jdbc.update("UPDATE BILLING_EVENT_OUTBOX SET quarantined_at=now() WHERE event_id=?", eventId);
        transactions.executeWithoutResult(status -> outbox.replay(eventId, Instant.now()));
        var replayed = transactions.execute(status -> outbox.findClaimable(Instant.now().plusSeconds(10), Instant.now().minusSeconds(60), 10));
        assertThat(replayed).isEmpty();
        UUID legacyId = UUID.randomUUID();
        jdbc.update("INSERT INTO BILLING_EVENT_OUTBOX(event_id,routing_key,aggregate_id,payload) VALUES (?,?,?,?)",
                legacyId, "invoice.created", UUID.randomUUID(), "{}");
        var legacy = transactions.execute(status -> outbox.findClaimable(Instant.now().plusSeconds(10), Instant.now().minusSeconds(60), 10));
        assertThat(legacy)
                .extracting(row -> row.getEventId()).containsExactly(legacyId);
    }

    @Test void httpRequiresCashierAndStoresVerifiedActorNotSpoofedHeader() {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        String path = "/api/v1/billing/payment-requests/" + request + "/payments";
        assertThat(http.postForEntity(path, command("http", "100"), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var headers = headers("DOCTOR");
        assertThat(http.postForEntity(path, new HttpEntity<>(command("http", "100"), headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        headers = headers("CASHIER");
        headers.set("X-Actor-Account-Id", UUID.randomUUID().toString());
        headers.set("X-Correlation-Id", "http-trace");
        var response = http.postForEntity(path, new HttpEntity<>(command("http", "100"), headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT actor_account_id FROM PAYMENT_TRANSACTION", UUID.class)).isEqualTo(actor);
        assertThat(response.getBody()).contains("http-trace");
    }

    @Test void surgeryLookup_exactLedgerGrantIsActiveAndMissingGrantIsConfirmedAbsence() throws Exception {
        UUID clearance = paidSurgery();
        var response = lookup(clearance, serviceHeaders("service","SYSTEM","surgery-service",60));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("lookup-trace");
        var root = mapper.readTree(response.getBody());
        assertThat(root.path("correlationId").asText()).isEqualTo("lookup-trace");
        var data = root.path("data");
        assertThat(data.path("eligible").asBoolean()).isTrue();
        assertThat(data.path("patientId").asText()).isEqualTo(patient.toString());
        assertThat(data.path("surgeryCaseId").asText()).isEqualTo(source.toString());
        assertThat(data.path("admissionId").asText()).isEqualTo(episode.toString());
        assertThat(data.has("amount")).isFalse();
        var missing = mapper.readTree(lookup(UUID.randomUUID(),serviceHeaders("service","SYSTEM","surgery-service",60)).getBody()).path("data");
        assertThat(missing.path("exists").asBoolean()).isFalse();
        assertThat(missing.path("eligible").asBoolean()).isFalse();
        assertThat(missing.path("patientId").isNull()).isTrue();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"revoked","expired","refund","voided-charge","wrong-patient",
            "wrong-invoice","wrong-target","cancelled-request","closed-account","emergency-override","missing-target","wrong-currency"})
    void surgeryLookup_invalidCurrentLedgerNeverGrantsReadiness(String changed) throws Exception {
        UUID clearance = paidSurgery();
        switch(changed) {
            case "revoked" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET revoked_at=now()");
            case "expired" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET expires_at=granted_at");
            case "voided-charge" -> jdbc.update("UPDATE CHARGE SET status='VOIDED'");
            case "wrong-patient" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET patient_id=?",UUID.randomUUID());
            case "wrong-invoice" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET invoice_id=?",UUID.randomUUID());
            case "wrong-target" -> jdbc.update("UPDATE PAYMENT_REQUEST_TARGET SET surgery_case_id=?",UUID.randomUUID());
            case "cancelled-request" -> jdbc.update("UPDATE PAYMENT_REQUEST SET status='CANCELLED'");
            case "closed-account" -> jdbc.update("UPDATE BILLING_ACCOUNT SET status='CLOSED'");
            case "emergency-override" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET emergency_override=TRUE");
            case "missing-target" -> jdbc.update("DELETE FROM PAYMENT_REQUEST_TARGET");
            case "wrong-currency" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET currency='USD'");
            case "refund" -> jdbc.update("""
                    INSERT INTO PAYMENT_TRANSACTION(transaction_id,account_id,payment_request_id,transaction_type,
                        classification,status,amount,currency,payment_method,idempotency_key,original_transaction_id,completed_at)
                    SELECT ?,account_id,payment_request_id,'REFUND',classification,'COMPLETED',1,currency,payment_method,
                        'test-refund',transaction_id,now() FROM PAYMENT_TRANSACTION WHERE transaction_type='PAYMENT'
                    """, UUID.randomUUID());
            default -> throw new AssertionError(changed);
        }
        var result = lookup(clearance,serviceHeaders("service","SYSTEM","surgery-service",60));
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(result.getBody()).path("data").path("exists").asBoolean()).isTrue();
        assertThat(mapper.readTree(result.getBody()).path("data").path("eligible").asBoolean()).isFalse();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"access,SYSTEM,surgery-service,60", "refresh,SYSTEM,surgery-service,60",
            "service,ADMIN,surgery-service,60", "service,SYSTEM,unknown-service,60", "service,SYSTEM,surgery-service,120"})
    void surgeryLookup_rejectsHumanRefreshWrongRoleWrongSubjectAndLongLivedCredentials(String type,String role,String subject,int ttl) {
        assertThat(lookup(UUID.randomUUID(),serviceHeaders(type,role,subject,ttl)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void surgeryServiceCredentialCannotCallHumanPaymentApi() {
        seed("SURGERY","ADMISSION","100");
        var result = http.postForEntity("/api/v1/billing/payment-requests/"+request+"/payments",
                new HttpEntity<>(command("must-not-pay","100"),serviceHeaders("service","SYSTEM","surgery-service",60)),String.class);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(count("PAYMENT_TRANSACTION")).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"valid","revoked","expired","refund","voided-charge",
            "wrong-patient","wrong-invoice","wrong-target","cancelled-request","closed-account","emergency-override",
            "missing-target","wrong-currency","wrong-source","mixed-target","wrong-record","missing-charge",
            "missing-allocation","partial-allocation","wrong-charge-patient"})
    void prescriptionLookupUsesCurrentExactOutpatientLedger(String changed) throws Exception {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        var payment = payments.complete(request, command("lookup-paid", "100"), actor, "lookup-trace");
        UUID clearance = jdbc.queryForObject("SELECT clearance_id FROM FINANCIAL_CLEARANCE", UUID.class);
        switch (changed) {
            case "valid" -> { }
            case "revoked" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET revoked_at=now()");
            case "expired" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET expires_at=granted_at");
            case "refund" -> refunds.refund(payment.transactionId(), refundCommand("lookup-refund", "1"), actor, "refund");
            case "voided-charge" -> jdbc.update("UPDATE CHARGE SET status='VOIDED'");
            case "wrong-patient" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET patient_id=?", UUID.randomUUID());
            case "wrong-invoice" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET invoice_id=?", UUID.randomUUID());
            case "wrong-target" -> jdbc.update("UPDATE PAYMENT_REQUEST_TARGET SET prescription_id=?", UUID.randomUUID());
            case "cancelled-request" -> jdbc.update("UPDATE PAYMENT_REQUEST SET status='CANCELLED'");
            case "closed-account" -> jdbc.update("UPDATE BILLING_ACCOUNT SET status='CLOSED'");
            case "emergency-override" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET emergency_override=TRUE");
            case "missing-target" -> jdbc.update("DELETE FROM PAYMENT_REQUEST_TARGET");
            case "wrong-currency" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET currency='USD'");
            case "wrong-source" -> jdbc.update("UPDATE CHARGE SET source_id=?", UUID.randomUUID());
            case "mixed-target" -> jdbc.update("UPDATE PAYMENT_REQUEST_TARGET SET surgery_case_id=?", UUID.randomUUID());
            case "wrong-record" -> jdbc.update("UPDATE PAYMENT_REQUEST_TARGET SET record_id=?", UUID.randomUUID());
            case "missing-charge" -> jdbc.update("DELETE FROM PAYMENT_REQUEST_CHARGE");
            case "missing-allocation" -> jdbc.update("DELETE FROM PAYMENT_ALLOCATION");
            case "partial-allocation" -> jdbc.update("UPDATE PAYMENT_ALLOCATION SET amount=1");
            case "wrong-charge-patient" -> jdbc.update("UPDATE CHARGE SET patient_id=?", UUID.randomUUID());
            default -> throw new AssertionError(changed);
        }
        var response = lookup(clearance, serviceHeaders("service", "SYSTEM", "pharmacy-service", 60));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = mapper.readTree(response.getBody()).path("data");
        assertThat(data.path("exists").asBoolean()).isTrue();
        assertThat(data.path("purpose").asText()).isEqualTo("PRESCRIPTION");
        assertThat(data.path("eligible").asBoolean()).isEqualTo(changed.equals("valid"));
        var originalGrant = mapper.readTree(jdbc.queryForObject(
                "SELECT payload FROM BILLING_EVENT_OUTBOX WHERE routing_key='financial.clearance.granted'", String.class));
        assertThat(Instant.parse(data.path("grantedAt").asText()))
                .as("Lookup retains the original grant instant rather than PostgreSQL's rounded timestamp")
                .isEqualTo(Instant.parse(originalGrant.path("occurredAt").asText()));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"EXAM", "LAB_TEST"})
    void outpatientClearanceLookup_exactPaidLedgerIsEligibleAndUsesOriginalGrantInstant(String purpose) throws Exception {
        seedOutpatient(purpose, "100");
        Instant exactGrant = Instant.now().minusSeconds(2).plusNanos(123_456_789);
        jdbc.update("UPDATE PAYMENT_REQUEST SET created_at=?", java.sql.Timestamp.from(exactGrant.minusSeconds(1)));
        org.mockito.Mockito.when(clock.instant()).thenReturn(exactGrant);
        var payment = payments.complete(request, command("outpatient-lookup", "100"), actor, "lookup-trace");
        UUID clearance = jdbc.queryForObject("SELECT clearance_id FROM FINANCIAL_CLEARANCE", UUID.class);

        var response = lookup(clearance, serviceHeaders("service", "SYSTEM", purpose.equals("EXAM") ? "clinical-service" : "lab-service", 60));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = mapper.readTree(response.getBody()).path("data");
        assertThat(data.path("purpose").asText()).isEqualTo(purpose);
        assertThat(data.path("sourceType").asText()).isEqualTo(purpose);
        assertThat(data.path("sourceId").asText()).isEqualTo(source.toString());
        assertThat(data.path("eligible").asBoolean()).isTrue();
        assertThat(data.path("patientId").asText()).isEqualTo(patient.toString());
        assertThat(data.path("careEpisodeId").asText()).isEqualTo(episode.toString());
        var grant = mapper.readTree(jdbc.queryForObject(
                "SELECT payload FROM BILLING_EVENT_OUTBOX WHERE routing_key='financial.clearance.granted'", String.class));
        assertThat(Instant.parse(data.path("grantedAt").asText()))
                .as("The lookup returns the original V1 grant instant without PostgreSQL microsecond rounding")
                .isEqualTo(Instant.parse(grant.path("occurredAt").asText()));
        assertThat(payment.transactionId()).isNotNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "EXAM,wrong-target", "EXAM,wrong-source", "EXAM,wrong-patient", "EXAM,wrong-invoice",
            "EXAM,wrong-currency", "EXAM,missing-charge", "EXAM,missing-allocation", "EXAM,partial-allocation",
            "EXAM,voided-charge", "EXAM,cancelled-request", "EXAM,revoked",
            "LAB_TEST,wrong-target", "LAB_TEST,wrong-record", "LAB_TEST,wrong-source", "LAB_TEST,wrong-patient",
            "LAB_TEST,wrong-invoice", "LAB_TEST,wrong-currency", "LAB_TEST,missing-charge", "LAB_TEST,missing-allocation",
            "LAB_TEST,partial-allocation", "LAB_TEST,multi-target", "LAB_TEST,voided-charge", "LAB_TEST,cancelled-request", "LAB_TEST,revoked"
    })
    void outpatientClearanceLookup_invalidCurrentLedgerNeverGrantsReadiness(String purpose, String changed) throws Exception {
        seedOutpatient(purpose, "100");
        var payment = payments.complete(request, command("outpatient-invalid", "100"), actor, "lookup-trace");
        UUID clearance = jdbc.queryForObject("SELECT clearance_id FROM FINANCIAL_CLEARANCE", UUID.class);
        switch (changed) {
            case "wrong-target" -> {
                if (purpose.equals("EXAM")) jdbc.update("UPDATE PAYMENT_REQUEST_TARGET SET appointment_id=?", UUID.randomUUID());
                else jdbc.update("UPDATE PAYMENT_REQUEST_TARGET SET lab_test_ids=ARRAY[?::uuid]", UUID.randomUUID());
            }
            case "multi-target" -> jdbc.update("UPDATE PAYMENT_REQUEST_TARGET SET lab_test_ids=ARRAY[?::uuid,?::uuid]", source, UUID.randomUUID());
            case "wrong-record" -> jdbc.update("UPDATE PAYMENT_REQUEST_TARGET SET record_id=?", UUID.randomUUID());
            case "wrong-source" -> jdbc.update("UPDATE CHARGE SET source_id=?", UUID.randomUUID());
            case "wrong-patient" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET patient_id=?", UUID.randomUUID());
            case "wrong-invoice" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET invoice_id=?", UUID.randomUUID());
            case "wrong-currency" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET currency='USD'");
            case "missing-charge" -> jdbc.update("DELETE FROM PAYMENT_REQUEST_CHARGE");
            case "missing-allocation" -> jdbc.update("DELETE FROM PAYMENT_ALLOCATION");
            case "partial-allocation" -> jdbc.update("UPDATE PAYMENT_ALLOCATION SET amount=1");
            case "voided-charge" -> jdbc.update("UPDATE CHARGE SET status='VOIDED'");
            case "cancelled-request" -> jdbc.update("UPDATE PAYMENT_REQUEST SET status='CANCELLED'");
            case "revoked" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET revoked_at=now()");
            default -> throw new AssertionError(changed);
        }

        var data = mapper.readTree(lookup(clearance, serviceHeaders("service", "SYSTEM",
                purpose.equals("EXAM") ? "clinical-service" : "lab-service", 60)).getBody()).path("data");
        assertThat(data.path("exists").asBoolean()).isTrue();
        assertThat(data.path("eligible").asBoolean()).as(purpose + " / " + changed).isFalse();
        if (changed.equals("multi-target")) assertThat(data.path("sourceId").isNull()).isTrue();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"EXAM", "LAB_TEST"})
    void executionStartPersistsOneTargetReceiptAndRevalidatesBeforeReplay(String purpose) throws Exception {
        seedOutpatient(purpose, "100");
        var payment = payments.complete(request, command("execution-start-paid", "100"), actor, "execution-start-paid");
        UUID clearance = jdbc.queryForObject("SELECT clearance_id FROM FINANCIAL_CLEARANCE", UUID.class);
        String service = purpose.equals("EXAM") ? "clinical-service" : "lab-service";
        var start = new com.mediflow.billing.application.dto.command.StartOutpatientExecutionCommand(source, purpose, source,
                patient, "OUTPATIENT_VISIT", episode);
        String path = "/api/v1/billing/financial-clearances/" + clearance + "/execution-starts";

        var first = http.postForEntity(path, new HttpEntity<>(start, serviceHeaders("service", "SYSTEM", service, 60)), String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        var originalReceipt = mapper.readTree(first.getBody()).path("data");
        assertThat(originalReceipt.path("operationId").asText()).isEqualTo(source.toString());
        assertThat(originalReceipt.path("state").asText()).isEqualTo("STARTED");
        var replay = http.postForEntity(path, new HttpEntity<>(start, serviceHeaders("service", "SYSTEM", service, 60)), String.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(replay.getBody()).path("data")).isEqualTo(originalReceipt);
        assertThat(count("outpatient_execution_start")).isOne();

        assertThatThrownBy(() -> refunds.refund(payment.transactionId(), refundCommand("started-without-cancel", "1"), actor, "refund"))
                .hasMessage("BILLING_REFUND_REQUIRES_CANCELLATION_EVIDENCE");
        var wrongOperation = new com.mediflow.billing.application.dto.command.StartOutpatientExecutionCommand(
                UUID.randomUUID(), purpose, source, patient, "OUTPATIENT_VISIT", episode);
        var conflict = http.postForEntity(path, new HttpEntity<>(wrongOperation, serviceHeaders("service", "SYSTEM", service, 60)), String.class);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(count("outpatient_execution_start")).isOne();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"EXAM", "LAB_TEST"})
    void refundBeforeAcceptedCancellationIsRejectedAndDoesNotBlockExecutionStart(String purpose) throws Exception {
        seedOutpatient(purpose, "100");
        var payment = payments.complete(request, command("unrefundable-before-start", "100"), actor, "paid");
        assertThatThrownBy(() -> refunds.refund(payment.transactionId(), refundCommand("before-start-refund", "1"), actor, "refund"))
                .hasMessage("BILLING_REFUND_REQUIRES_CANCELLATION_EVIDENCE");
        UUID clearance = jdbc.queryForObject("SELECT clearance_id FROM FINANCIAL_CLEARANCE", UUID.class);
        String service = purpose.equals("EXAM") ? "clinical-service" : "lab-service";
        var start = new com.mediflow.billing.application.dto.command.StartOutpatientExecutionCommand(source, purpose, source,
                patient, "OUTPATIENT_VISIT", episode);
        var response = http.postForEntity("/api/v1/billing/financial-clearances/" + clearance + "/execution-starts",
                new HttpEntity<>(start, serviceHeaders("service", "SYSTEM", service, 60)), String.class);
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(count("outpatient_execution_start")).isOne();
        assertThat(count("ledger_refund_evidence")).isZero();
    }

    @Test void executionStartAndRefundRaceKeepsUncancelledOutpatientPaymentSafe() throws Exception {
        seedOutpatient("LAB_TEST", "100");
        var payment = payments.complete(request, command("start-refund-race", "100"), actor, "paid");
        UUID clearance = jdbc.queryForObject("SELECT clearance_id FROM FINANCIAL_CLEARANCE", UUID.class);
        var start = new com.mediflow.billing.application.dto.command.StartOutpatientExecutionCommand(source, "LAB_TEST", source,
                patient, "OUTPATIENT_VISIT", episode);
        String path = "/api/v1/billing/financial-clearances/" + clearance + "/execution-starts";
        var gate = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var startTask = workers.submit(() -> {
                gate.await();
                var response = http.postForEntity(path,
                        new HttpEntity<>(start, serviceHeaders("service", "SYSTEM", "lab-service", 60)), String.class);
                return response.getStatusCode().value() == 200 ? "STARTED" : "START_REJECTED";
            });
            var refundTask = workers.submit(() -> {
                gate.await();
                try {
                    refunds.refund(payment.transactionId(), refundCommand("start-refund-race-key", "1"), actor, "refund-race");
                    return "REFUNDED";
                } catch (BillingRuleException rejected) { return rejected.getCode(); }
            });
            gate.countDown();
            var startResult = startTask.get(20, TimeUnit.SECONDS);
            var refundResult = refundTask.get(20, TimeUnit.SECONDS);
            assertThat(startResult).isEqualTo("STARTED");
            assertThat(refundResult).isEqualTo("BILLING_REFUND_REQUIRES_CANCELLATION_EVIDENCE");
        }
        assertThat(count("outpatient_execution_start")).isOne();
        assertThat(count("ledger_refund_evidence")).isZero();
    }

    private UUID paidSurgery() {
        seed("SURGERY","ADMISSION","100");
        payments.complete(request,command("lookup-paid","100"),actor,"lookup-trace");
        return jdbc.queryForObject("SELECT clearance_id FROM FINANCIAL_CLEARANCE",UUID.class);
    }
    @Test void refund_completedSurgeryPayment_appendsReversalAndDeniesCurrentClearance() throws Exception {
        seed("SURGERY", "ADMISSION", "100");
        var payment = payments.complete(request, command("paid", "100"), actor, "paid");
        var original = jdbc.queryForObject("SELECT row_to_json(t)::text FROM PAYMENT_TRANSACTION t WHERE transaction_id=?", String.class, payment.transactionId());
        UUID clearance = jdbc.queryForObject("SELECT clearance_id FROM FINANCIAL_CLEARANCE", UUID.class);
        var refund = refunds.refund(payment.transactionId(), refundCommand("refund", "20"), actor, "refund-trace");
        assertThat(refund.originalTransactionId()).isEqualTo(payment.transactionId());
        assertThat(jdbc.queryForObject("SELECT row_to_json(t)::text FROM PAYMENT_TRANSACTION t WHERE transaction_id=?", String.class, payment.transactionId())).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM PAYMENT_ALLOCATION WHERE transaction_id=?", BigDecimal.class, refund.refundTransactionId())).isEqualByComparingTo("20");
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM FINANCIAL_CLEARANCE", Boolean.class)).isTrue();
        assertThat(mapper.readTree(lookup(clearance, serviceHeaders("service", "SYSTEM", "surgery-service", 60)).getBody()).path("data").path("eligible").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM PAYMENT_REQUEST", String.class)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT payload FROM BILLING_EVENT_OUTBOX WHERE routing_key='payment.refunded'", String.class))
                .contains("CASHIER_RECORDED_REFUND", "refund-trace").doesNotContain("Private audit reason");
        assertThat(jdbc.queryForObject("SELECT publication_enabled FROM BILLING_EVENT_OUTBOX WHERE routing_key='payment.refunded'", Boolean.class)).isFalse();
    }

    @Test void refund_deposit_recordsCashOutWithoutEarnedAllocation() {
        seed("ADMISSION_DEPOSIT", "ADMISSION", "100");
        var original = payments.complete(request, command("deposit", "100"), actor, "paid");
        var refund = refunds.refund(original.transactionId(), refundCommand("refund", "100"), actor, "refund");
        assertThat(count("PAYMENT_ALLOCATION")).isZero();
        assertThat(refund.classification().name()).isEqualTo("ADMISSION_DEPOSIT");
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM FINANCIAL_CLEARANCE", Boolean.class)).isTrue();
    }

    @Test void refund_twoPartialsCannotExceedOriginalAndReplayPreservesAudit() {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        var original = payments.complete(request, command("paid", "100"), actor, "paid");
        var first = refunds.refund(original.transactionId(), refundCommand("refund-1", "60"), actor, "first");
        refunds.refund(original.transactionId(), refundCommand("refund-2", "40"), actor, "second");
        assertThatThrownBy(() -> refunds.refund(original.transactionId(), refundCommand("refund-3", "0.01"), actor, "third"))
                .hasMessage("BILLING_REFUND_EXCEEDS_PAYMENT");
        jdbc.update("UPDATE BILLING_ACCOUNT SET status='CLOSED'");
        assertThat(refunds.refund(original.transactionId(), refundCommand("refund-1", "60.00"), actor, "retry").refundTransactionId())
                .isEqualTo(first.refundTransactionId());
        assertThat(count("ledger_refund_evidence")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM PAYMENT_TRANSACTION WHERE transaction_type='REFUND'", BigDecimal.class)).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM BILLING_EVENT_OUTBOX WHERE routing_key='payment.refunded'", Long.class)).isEqualTo(2);
    }

    @Test void refund_racingDistinctKeys_serializesOriginalBudget() throws Exception {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        var original = payments.complete(request, command("paid", "100"), actor, "paid");
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> refundAttempt(start, original.transactionId(), "a"));
            var b = workers.submit(() -> refundAttempt(start, original.transactionId(), "b"));
            start.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("COMPLETED", "BILLING_REFUND_EXCEEDS_PAYMENT");
        }
        assertThat(count("ledger_refund_evidence")).isEqualTo(1);
    }

    @Test void refund_racingSameKey_returnsOneImmutableRefund() throws Exception {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        var original = payments.complete(request, command("paid", "100"), actor, "paid");
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> { start.await(); return refunds.refund(original.transactionId(), refundCommand("same", "20"), actor, "a"); });
            var b = workers.submit(() -> { start.await(); return refunds.refund(original.transactionId(), refundCommand("same", "20"), actor, "b"); });
            start.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS).refundTransactionId()).isEqualTo(b.get(15, TimeUnit.SECONDS).refundTransactionId());
        }
        assertThat(count("ledger_refund_evidence")).isEqualTo(1);
    }

    @Test void refund_outboxFailure_rollsBackMoneyReversalAuditAndRevocation() {
        seed("SURGERY", "ADMISSION", "100");
        var original = payments.complete(request, command("paid", "100"), actor, "paid");
        jdbc.execute("CREATE FUNCTION reject_refund_event() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test rollback'; END $$");
        jdbc.execute("CREATE TRIGGER reject_refund_event BEFORE INSERT ON BILLING_EVENT_OUTBOX FOR EACH ROW EXECUTE FUNCTION reject_refund_event()");
        try {
            assertThatThrownBy(() -> refunds.refund(original.transactionId(), refundCommand("refund", "20"), actor, "refund"))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(count("PAYMENT_TRANSACTION")).isEqualTo(1);
            assertThat(count("PAYMENT_ALLOCATION")).isEqualTo(1);
            assertThat(count("ledger_refund_evidence")).isZero();
            assertThat(jdbc.queryForObject("SELECT revoked_at FROM FINANCIAL_CLEARANCE", java.sql.Timestamp.class)).isNull();
        } finally {
            jdbc.execute("DROP TRIGGER reject_refund_event ON BILLING_EVENT_OUTBOX");
            jdbc.execute("DROP FUNCTION reject_refund_event()");
        }
        refunds.refund(original.transactionId(), refundCommand("refund", "20"), actor, "retry-after-fix");
        assertThat(count("ledger_refund_evidence")).isEqualTo(1);
    }

    @Test void refund_httpRoleValidationAndSignedActorCannotBeSpoofed() throws Exception {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        var original = payments.complete(request, command("paid", "100"), actor, "paid");
        String path = "/api/v1/billing/transactions/" + original.transactionId() + "/refunds";
        assertThat(http.postForEntity(path, refundCommand("http", "20"), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        for (String role : List.of("DOCTOR", "NURSE", "MANAGER", "PATIENT", "PHARMACIST", "LAB_TECH")) {
            assertThat(http.postForEntity(path, new HttpEntity<>(refundCommand("http", "20"), headers(role)), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(http.postForEntity(path, new HttpEntity<>(refundCommand("http", "20"), serviceHeaders("refresh", "CASHIER", actor.toString(), 60)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.postForEntity(path, new HttpEntity<>(refundCommand("http", "20"), serviceHeaders("service", "SYSTEM", "surgery-service", 60)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.postForEntity(path, new HttpEntity<>(refundCommand("http", "0.001"), headers("CASHIER")), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(count("ledger_refund_evidence")).isZero();
        var headers = headers("CASHIER"); headers.set("X-Actor-Account-Id", UUID.randomUUID().toString()); headers.set("X-Correlation-Id", "refund-http");
        var response = http.postForEntity(path, new HttpEntity<>(refundCommand("http", "20"), headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var result = mapper.readTree(response.getBody());
        assertThat(result.path("correlationId").asText()).isEqualTo("refund-http");
        assertThat(result.path("data").path("originalTransactionId").asText()).isEqualTo(original.transactionId().toString());
        assertThat(jdbc.queryForObject("SELECT actor_account_id FROM PAYMENT_TRANSACTION WHERE transaction_type='REFUND'", UUID.class)).isEqualTo(actor);
    }

    @Test void refund_wrongOriginalOrReusedPaymentKey_neverCreatesEffect() {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        var original = payments.complete(request, command("paid", "100"), actor, "paid");
        assertThatThrownBy(() -> refunds.refund(UUID.randomUUID(), refundCommand("absent", "20"), actor, "refund")).hasMessage("Payment transaction not found");
        assertThatThrownBy(() -> refunds.refund(original.transactionId(), refundCommand("paid", "20"), actor, "refund")).hasMessage("BILLING_IDEMPOTENCY_CONFLICT");
        var refunded = refunds.refund(original.transactionId(), refundCommand("refund", "20"), actor, "refund");
        assertThatThrownBy(() -> refunds.refund(refunded.refundTransactionId(), refundCommand("refund-of-refund", "1"), actor, "refund"))
                .hasMessage("BILLING_REFUND_REQUIRES_COMPLETED_PAYMENT");
        assertThat(count("ledger_refund_evidence")).isEqualTo(1);
    }
    @Test void refund_httpStorageFailure_returnsSafe503AndSameKeyRecovers() {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        var payment = payments.complete(request, command("paid", "100"), actor, "paid");
        String path = "/api/v1/billing/transactions/" + payment.transactionId() + "/refunds";
        jdbc.execute("CREATE FUNCTION fail_refund_http() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'private SQL detail'; END $$");
        jdbc.execute("CREATE TRIGGER fail_refund_http BEFORE INSERT ON BILLING_EVENT_OUTBOX FOR EACH ROW EXECUTE FUNCTION fail_refund_http()");
        try {
            var headers = headers("CASHIER"); headers.set("X-Correlation-Id", "refund-unavailable");
            var response = http.postForEntity(path, new HttpEntity<>(refundCommand("http-retry", "20"), headers), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).contains("BILLING_LEDGER_UNAVAILABLE", "refund-unavailable").doesNotContain("private SQL", "INSERT INTO", "Private audit");
            assertThat(count("ledger_refund_evidence")).isZero();
        } finally { jdbc.execute("DROP TRIGGER fail_refund_http ON BILLING_EVENT_OUTBOX"); jdbc.execute("DROP FUNCTION fail_refund_http()"); }
        assertThat(http.postForEntity(path, new HttpEntity<>(refundCommand("http-retry", "20"), headers("CASHIER")), String.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(count("ledger_refund_evidence")).isOne();
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings = {"CLOSED", "SETTLED"})
    void refund_terminalAccount_requiresExplicitReopenPolicy(String status) {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        var payment = payments.complete(request, command("paid", "100"), actor, "paid");
        jdbc.update("UPDATE BILLING_ACCOUNT SET status=?", status);
        assertThatThrownBy(() -> refunds.refund(payment.transactionId(), refundCommand("refund", "20"), actor, "refund"))
                .hasMessage("BILLING_REFUND_REQUIRES_ACCOUNT_REOPEN");
        assertThat(count("ledger_refund_evidence")).isZero();
    }
    @Test void refund_oneInstallment_cannotReverseAnotherPaymentsAllocations() {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        var first = payments.complete(request, command("first", "40"), actor, "paid");
        var second = payments.complete(request, command("second", "60"), actor, "paid");
        refunds.refund(first.transactionId(), refundCommand("refund", "40"), actor, "refund");
        assertThatThrownBy(() -> refunds.refund(first.transactionId(), refundCommand("too-much", "0.01"), actor, "refund"))
                .hasMessage("BILLING_REFUND_EXCEEDS_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM PAYMENT_ALLOCATION WHERE transaction_id=?", BigDecimal.class, second.transactionId()))
                .isEqualByComparingTo("60");
        assertThat(jdbc.queryForObject("SELECT SUM(CASE WHEN t.transaction_type='PAYMENT' THEN a.amount ELSE -a.amount END) FROM PAYMENT_ALLOCATION a JOIN PAYMENT_TRANSACTION t ON t.transaction_id=a.transaction_id", BigDecimal.class))
                .isEqualByComparingTo("60");
    }

    private String refundAttempt(CountDownLatch start, UUID original, String key) throws InterruptedException {
        start.await();
        try { refunds.refund(original, refundCommand(key, "80"), actor, key); return "COMPLETED"; }
        catch (BillingRuleException rejected) { return rejected.getCode(); }
    }
    private com.mediflow.billing.application.dto.request.RefundLedgerPaymentRequest refundCommand(String key, String amount) {
        return new com.mediflow.billing.application.dto.request.RefundLedgerPaymentRequest(key, new BigDecimal(amount), "Private audit reason", PaymentMethod.CASH);
    }
    private ResponseEntity<String> lookup(UUID clearance,HttpHeaders headers) {
        headers.set("X-Correlation-Id","lookup-trace");
        return http.exchange("/api/v1/billing/financial-clearances/"+clearance+"/lookup",HttpMethod.GET,new HttpEntity<>(headers),String.class);
    }
    private HttpHeaders serviceHeaders(String type,String role,String subject,int ttl) {
        var headers = new HttpHeaders();
        headers.set("X-Correlation-Id", "billing-ledger-test");
        Instant issued = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        headers.setBearerAuth(Jwts.builder().subject(subject).claim("role",role).claim("type",type)
                .issuedAt(Date.from(issued)).expiration(Date.from(issued.plusSeconds(ttl)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact());
        return headers;
    }

    private String attempt(CountDownLatch start, String key, String amount) throws InterruptedException {
        start.await();
        try { payments.complete(request, command(key, amount), actor, "race"); return "COMPLETED"; }
        catch (BillingRuleException rejected) { return rejected.getCode(); }
    }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    private CompleteLedgerPaymentRequest command(String key, String amount) {
        return new CompleteLedgerPaymentRequest(key, new BigDecimal(amount), "VND", PaymentMethod.CASH, null);
    }
    private HttpHeaders headers(String role) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(Jwts.builder().subject(actor.toString()).claim("role", role).claim("type", "access")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact());
        return headers;
    }
    private void seed(String purpose, String episodeType, String total) {
        jdbc.update("""
                INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,opened_at)
                VALUES (?,?,?,?,?,now())
                """, account, patient, department, episodeType, episode);
        jdbc.update("""
                INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,requested_amount,created_by)
                VALUES (?,?,?,?,?,?)
                """, request, invoice, account, purpose, new BigDecimal(total), actor);
        boolean deposit = "ADMISSION_DEPOSIT".equals(purpose), surgery = "SURGERY".equals(purpose);
        jdbc.update("""
                INSERT INTO PAYMENT_REQUEST_TARGET(payment_request_id,prescription_id,admission_id,surgery_case_id)
                VALUES (?,?,?,?)
                """, request, "PRESCRIPTION".equals(purpose) ? source : null,
                deposit || surgery && "ADMISSION".equals(episodeType) ? episode : null, surgery ? source : null);
        if (!deposit) {
            UUID charge = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO CHARGE(charge_id,account_id,patient_id,department_id,source_type,source_id,price_code,
                        description,unit_amount,gross_amount,incurred_at) VALUES (?,?,?,?,?,?,?,?,?,?,now())
                    """, charge, account, patient, department, purpose, source, "TEST_PRICE", "Test-only price",
                    new BigDecimal(total), new BigDecimal(total));
            jdbc.update("INSERT INTO PAYMENT_REQUEST_CHARGE(payment_request_id,charge_id,requested_amount) VALUES (?,?,?)",
                    request, charge, new BigDecimal(total));
        }
    }

    private void seedScheduledRegistration() {
        source = UUID.randomUUID();
        episode = source;
        reservation = UUID.randomUUID();
        var changedAt = Instant.now().minusSeconds(30).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var expiresAt = Instant.now().plusSeconds(600).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        jdbc.update("""
                INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,opened_at)
                VALUES (?,?,?,'OUTPATIENT_VISIT',?,?)
                """, account, patient, department, source, java.sql.Timestamp.from(changedAt));
        jdbc.update("""
                INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,requested_amount,currency,
                    expires_at,created_by,created_at)
                VALUES (?,?,?,'EXAM',?,'VND',?,?,?)
                """, request, invoice, account, new BigDecimal("100"), java.sql.Timestamp.from(expiresAt), actor,
                java.sql.Timestamp.from(changedAt));
        jdbc.update("INSERT INTO PAYMENT_REQUEST_TARGET(payment_request_id,record_id) VALUES (?,?)", request, source);
        UUID charge = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO CHARGE(charge_id,account_id,patient_id,department_id,source_type,source_id,price_code,
                    description,quantity,unit_amount,gross_amount,incurred_at)
                VALUES (?,?,?,?,?,?,'OUTPATIENT_EXAM','Outpatient exam',1,100,100,?)
                """, charge, account, patient, department, "EXAM", source, java.sql.Timestamp.from(changedAt));
        jdbc.update("INSERT INTO PAYMENT_REQUEST_CHARGE(payment_request_id,charge_id,requested_amount) VALUES (?,?,?)",
                request, charge, new BigDecimal("100"));
        jdbc.update("""
                INSERT INTO outpatient_registration_source(registration_id,registration_revision,patient_id,doctor_id,
                    department_id,session_id,room_id,session_date,start_time,end_time,visit_type,reservation_id,
                    reservation_status,registration_status,reservation_expires_at,price_code,source_type,source_id,
                    care_episode_type,care_episode_id,changed_at,payload_fingerprint,payment_request_id,invoice_id,account_id,
                    request_issue_status)
                VALUES (?,1,?,?,?,?,?,CURRENT_DATE,'09:00','09:30','NEW_VISIT',?,'HELD','AWAITING_PAYMENT',?,
                    'OUTPATIENT_EXAM','EXAM',?,'OUTPATIENT_VISIT',?,?,?,?,?,?,'READY')
                """, source, patient, UUID.randomUUID(), department, UUID.randomUUID(), UUID.randomUUID(), reservation,
                java.sql.Timestamp.from(expiresAt), source, source, java.sql.Timestamp.from(changedAt), "b".repeat(64),
                request, invoice, account);
    }

    private void seedOutpatient(String purpose, String total) {
        if (purpose.equals("EXAM")) episode = source;
        UUID record = UUID.randomUUID();
        UUID sourceOrder = UUID.randomUUID();
        Instant requestedAt = Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        jdbc.update("""
                INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,opened_at)
                VALUES (?,?,?,'OUTPATIENT_VISIT',?,now())
                """, account, patient, department, episode);
        jdbc.update("""
                INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,requested_amount,created_by)
                VALUES (?,?,?,?,?,?)
                """, request, invoice, account, purpose, new BigDecimal(total), actor);
        if (purpose.equals("EXAM")) {
            jdbc.update("INSERT INTO PAYMENT_REQUEST_TARGET(payment_request_id,appointment_id) VALUES (?,?)", request, source);
            jdbc.update("""
                    INSERT INTO exam_charge_source(appointment_id,source_fingerprint,payment_request_id,requested_at,
                        patient_id,department_id,care_episode_type,care_episode_id,price_code,requested_at_iso)
                    VALUES (?,repeat('a',64),?,?,?,?,'OUTPATIENT_VISIT',?,'TEST_PRICE',?)
                    """, source, request, java.sql.Timestamp.from(requestedAt), patient, department, episode, requestedAt.toString());
        } else {
            jdbc.update(connection -> {
                var statement = connection.prepareStatement(
                        "INSERT INTO PAYMENT_REQUEST_TARGET(payment_request_id,record_id,lab_test_ids) VALUES (?,?,?)");
                statement.setObject(1, request);
                statement.setObject(2, record);
                statement.setArray(3, connection.createArrayOf("uuid", new UUID[]{source}));
                return statement;
            });
            jdbc.update("""
                    INSERT INTO lab_test_charge_source(lab_id,source_order_id,source_fingerprint,payment_request_id,requested_at,
                        patient_id,department_id,record_id,care_episode_type,care_episode_id,price_code,lab_type,requested_at_iso)
                    VALUES (?,?,repeat('b',64),?,?,?,? ,?,'OUTPATIENT_VISIT',?,'TEST_PRICE','CBC',?)
                    """, source, sourceOrder, request, java.sql.Timestamp.from(requestedAt), patient, department, record,
                    episode, requestedAt.toString());
        }
        UUID charge = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO CHARGE(charge_id,account_id,patient_id,department_id,source_type,source_id,price_code,
                    description,unit_amount,gross_amount,incurred_at) VALUES (?,?,?,?,?,?,?,?,?,?,now())
                """, charge, account, patient, department, purpose, source, "TEST_PRICE", "Test-only price",
                new BigDecimal(total), new BigDecimal(total));
        jdbc.update("INSERT INTO PAYMENT_REQUEST_CHARGE(payment_request_id,charge_id,requested_amount) VALUES (?,?,?)",
                request, charge, new BigDecimal(total));
    }
}
