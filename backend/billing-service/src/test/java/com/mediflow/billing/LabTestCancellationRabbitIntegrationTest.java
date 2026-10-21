package com.mediflow.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.billing.application.dto.request.CompleteLedgerPaymentRequest;
import com.mediflow.billing.application.dto.request.RefundLedgerPaymentRequest;
import com.mediflow.billing.application.dto.command.StartOutpatientExecutionCommand;
import com.mediflow.billing.application.port.in.ProcessLedgerPaymentUseCase;
import com.mediflow.billing.application.port.in.RefundLedgerPaymentUseCase;
import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.infrastructure.pricing.CareFinancePriceProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.awaitility.Awaitility.await;

/** Billing's side of lab.request.cancelled V1: actual PostgreSQL, RabbitMQ and API transaction paths. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "mediflow.jwt.secret=billing-lab-cancellation-integration-secret-at-least-32-bytes",
        "mediflow.billing.ledger.enabled=true", "mediflow.billing.refunds.enabled=true",
        "mediflow.billing.lab-test-charge-consumer.enabled=true",
        "mediflow.billing.clearance-lookup.enabled=true",
        "mediflow.billing.outbox.enabled=false", "mediflow.billing.outbox.metrics-enabled=false", "mediflow.billing.outbox.maintenance-enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.publisher-confirm-type=simple"})
class LabTestCancellationRabbitIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer MQ = new RabbitMQContainer("rabbitmq:3.13-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl); r.add("spring.datasource.username", PG::getUsername); r.add("spring.datasource.password", PG::getPassword);
        r.add("spring.rabbitmq.host", MQ::getHost); r.add("spring.rabbitmq.port", MQ::getAmqpPort);
        r.add("spring.rabbitmq.username", MQ::getAdminUsername); r.add("spring.rabbitmq.password", MQ::getAdminPassword);
    }
    private static final String QUEUE = "billing.q", DLQ = "billing.dlq";
    private static final String SECRET = "billing-lab-cancellation-integration-secret-at-least-32-bytes";
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitListenerEndpointRegistry registry;
    @Autowired ProcessLedgerPaymentUseCase payments;
    @Autowired RefundLedgerPaymentUseCase refunds;
    @Autowired CareFinancePriceProperties catalog;
    @Autowired ObjectMapper mapper;
    @Autowired org.springframework.boot.test.web.client.TestRestTemplate http;

    @BeforeEach void reset() {
        listener().stop(); admin.purgeQueue(QUEUE); admin.purgeQueue(DLQ);
        jdbc.execute("TRUNCATE BILLING_ACCOUNT,BILLING_EVENT_OUTBOX,lab_test_charge_delivery,outpatient_cancellation_source CASCADE");
        catalog.setPrices(Map.of("LAB-CBC", new CareFinancePriceProperties.Price("Xet nghiem CBC", new BigDecimal("150000.00"))));
        listener().start();
    }
    @AfterEach void stop() { listener().stop(); }

    @Test void cancellationBeforeCreationIsDurableAndAbsorbsTheLaterCharge() throws Exception {
        byte[] cancelled = cancellationFixture();
        send(cancelled); drainCancellation();
        assertThat(count("outpatient_cancellation_source")).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM outpatient_cancellation_source", String.class)).isEqualTo("PENDING");

        send(createdFixture()); drainCreation();
        assertThat(count("CHARGE")).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM CHARGE", String.class)).isEqualTo("VOIDED");
        assertThat(jdbc.queryForObject("SELECT status FROM PAYMENT_REQUEST", String.class)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM outpatient_cancellation_source", String.class)).isEqualTo("APPLIED");
        assertThat(count("outpatient_cancellation_refund_due")).isZero();
        assertThat(count("FINANCIAL_CLEARANCE")).isZero();
    }

    @Test void paidCancellationCreatesCappedRefundDueAndKeepsCashRefundAnAuditedSeparateAction() throws Exception {
        send(createdFixture()); drainCreation();
        UUID labId = labId();
        UUID request = requestId();
        var paid = payments.complete(request,
                new CompleteLedgerPaymentRequest("lab-cash", new BigDecimal("150000.00"), "VND", PaymentMethod.CASH, null),
                UUID.randomUUID(), "payment-trace");
        UUID clearanceId = jdbc.queryForObject("SELECT clearance_id FROM FINANCIAL_CLEARANCE", UUID.class);
        var startCommand = new StartOutpatientExecutionCommand(labId(), "LAB_TEST", labId(),
                UUID.fromString("00000000-0000-4000-8000-000000000003"), "OUTPATIENT_VISIT",
                UUID.fromString("00000000-0000-4000-8000-000000000006"));
        var startResponse = http.postForEntity("/api/v1/billing/financial-clearances/" + clearanceId + "/execution-starts",
                new HttpEntity<>(startCommand, serviceHeaders("lab-service")), String.class);
        assertThat(startResponse.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(count("outpatient_execution_start")).isOne();
        String original = jdbc.queryForObject("SELECT row_to_json(t)::text FROM PAYMENT_TRANSACTION t WHERE transaction_id=?", String.class, paid.transactionId());

        send(cancellationFixture()); drainCancellation();
        assertThat(jdbc.queryForObject("SELECT status FROM CHARGE", String.class)).isEqualTo("VOIDED");
        assertThat(jdbc.queryForObject("SELECT status FROM PAYMENT_REQUEST", String.class)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT amount FROM outpatient_cancellation_refund_due", BigDecimal.class)).isEqualByComparingTo("150000.00");
        assertThat(count("PAYMENT_TRANSACTION")).isOne(); // cancellation records due, never fake cash-out

        var staleRetry = http.postForEntity("/api/v1/billing/financial-clearances/" + clearanceId + "/execution-starts",
                new HttpEntity<>(startCommand, serviceHeaders("lab-service")), String.class);
        assertThat(staleRetry.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        assertThat(count("outpatient_execution_start")).isOne();

        var first = refunds.refund(paid.transactionId(), refund("lab-refund-partial", "40000.00"), UUID.randomUUID(), "first-refund");
        assertThat(first.originalTransactionId()).isEqualTo(paid.transactionId());
        assertThat(mapper.readTree(refundDue(labId).getBody()).path("data").path("refundsDue").get(0).path("amount").decimalValue())
                .isEqualByComparingTo("110000.00");
        refunds.refund(paid.transactionId(), refund("lab-refund-remainder", "110000.00"), UUID.randomUUID(), "second-refund");
        assertThat(jdbc.queryForObject("SELECT row_to_json(t)::text FROM PAYMENT_TRANSACTION t WHERE transaction_id=?", String.class, paid.transactionId()))
                .isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM PAYMENT_TRANSACTION WHERE transaction_type='REFUND'", BigDecimal.class))
                .isEqualByComparingTo("150000.00");
        assertThat(refundDue(labId).getBody()).doesNotContain("originalTransactionId");
    }

    @Test void emergencyAndLegacyNoReceiptRefundsRequireAcceptedCancellationEvidence() throws Exception {
        send(createdFixture()); drainCreation();
        var paid = payments.complete(requestId(),
                new CompleteLedgerPaymentRequest("lab-no-receipt", new BigDecimal("150000.00"), "VND", PaymentMethod.CASH, null),
                UUID.randomUUID(), "payment-no-receipt");
        jdbc.update("UPDATE FINANCIAL_CLEARANCE SET emergency_override=true");

        // Emergency bypass and pre-upgrade grants can both lack a Billing execution-start receipt.
        assertThat(count("outpatient_execution_start")).isZero();
        assertThatThrownBy(() -> refunds.refund(paid.transactionId(), refund("emergency-no-cancel", "1000.00"),
                UUID.randomUUID(), "emergency-refund"))
                .hasMessage("BILLING_REFUND_REQUIRES_CANCELLATION_EVIDENCE");
        jdbc.update("UPDATE FINANCIAL_CLEARANCE SET emergency_override=false");
        assertThatThrownBy(() -> refunds.refund(paid.transactionId(), refund("legacy-no-cancel", "1000.00"),
                UUID.randomUUID(), "legacy-refund"))
                .hasMessage("BILLING_REFUND_REQUIRES_CANCELLATION_EVIDENCE");
        assertThat(count("PAYMENT_TRANSACTION")).isOne();

        send(cancellationFixture()); drainCancellation();
        var admittedRefund = refunds.refund(paid.transactionId(), refund("legacy-after-cancel", "1000.00"),
                UUID.randomUUID(), "legacy-refund-after-cancel");
        assertThat(admittedRefund.originalTransactionId()).isEqualTo(paid.transactionId());
        assertThat(count("outpatient_execution_start")).isZero();
    }

    @Test void duplicateCancellationReplaysOnceAndSameEventIdWithChangedBytesIsDeadLettered() throws Exception {
        byte[] exact = cancellationFixture();
        send(exact); send(exact); drainCancellation();
        assertThat(count("outpatient_cancellation_delivery")).isOne();
        assertThat(count("outpatient_cancellation_source")).isOne();

        ObjectNode conflict = tree(exact);
        ((ObjectNode) conflict.path("payload")).put("reason", "A changed fact is not a retry");
        byte[] changed = mapper.writeValueAsBytes(conflict);
        send(changed); retainedDlq(changed);
        assertThat(count("outpatient_cancellation_delivery")).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM outpatient_cancellation_source", String.class)).isEqualTo("PENDING");
    }

    @Test void exactCreationReplayRepairsLegacySnapshotBeforeApplyingCancellation() throws Exception {
        send(createdFixture()); drainCreation();
        jdbc.update("UPDATE lab_test_charge_source SET lab_type=NULL,requested_at_iso=NULL WHERE lab_id=?", labId());

        send(cancellationFixture()); drainCancellation();
        assertThat(jdbc.queryForObject("SELECT status FROM outpatient_cancellation_source", String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM CHARGE", String.class)).isEqualTo("POSTED");

        ObjectNode replay = tree(createdFixture());
        replay.put("eventId", "00000000-0000-4000-8000-000000000099"); // new delivery, identical source fact
        send(mapper.writeValueAsBytes(replay));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("lab_test_charge_delivery")).isEqualTo(2));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo(QUEUE).getMessageCount()).isZero());
        listener().stop();
        assertThat(jdbc.queryForObject("SELECT lab_type FROM lab_test_charge_source WHERE lab_id=?", String.class, labId())).isEqualTo("CBC");
        assertThat(jdbc.queryForObject("SELECT status FROM outpatient_cancellation_source", String.class)).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("SELECT status FROM CHARGE", String.class)).isEqualTo("VOIDED");
        assertThat(count("PAYMENT_REQUEST")).isOne(); // replay repairs evidence; it never issues a second bill

        ObjectNode conflict = tree(createdFixture());
        conflict.put("eventId", "00000000-0000-4000-8000-000000000098");
        ((ObjectNode) conflict.path("payload")).put("labType", "CHEMISTRY");
        byte[] changed = mapper.writeValueAsBytes(conflict);
        send(changed); retainedDlq(changed);
        assertThat(jdbc.queryForObject("SELECT lab_type FROM lab_test_charge_source WHERE lab_id=?", String.class, labId())).isEqualTo("CBC");
        assertThat(count("CHARGE")).isOne();
    }

    @Test void cancellationAndPaymentSerializeOnTheOwnedAccount() throws Exception {
        send(createdFixture()); drainCreation();
        UUID request = requestId();
        var paymentTask = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return payments.complete(request,
                        new CompleteLedgerPaymentRequest("lab-race", new BigDecimal("150000.00"), "VND", PaymentMethod.CASH, null),
                        UUID.randomUUID(), "race-payment").transactionId();
            } catch (RuntimeException cancelled) { return null; }
        });
        send(cancellationFixture());
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT status FROM outpatient_cancellation_source", String.class)).isEqualTo("APPLIED"));
        UUID payment = paymentTask.get();
        assertThat(jdbc.queryForObject("SELECT status FROM CHARGE", String.class)).isEqualTo("VOIDED");
        assertThat(jdbc.queryForObject("SELECT status FROM PAYMENT_REQUEST", String.class)).isEqualTo("CANCELLED");
        BigDecimal due = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM outpatient_cancellation_refund_due", BigDecimal.class);
        if (payment == null) {
            assertThat(due).isZero();
            assertThat(count("PAYMENT_TRANSACTION")).isZero();
        } else {
            assertThat(due).isEqualByComparingTo("150000.00");
            assertThat(jdbc.queryForObject("SELECT status FROM PAYMENT_TRANSACTION WHERE transaction_id=?", String.class, payment))
                    .isEqualTo("COMPLETED");
        }
    }

    private org.springframework.amqp.rabbit.listener.MessageListenerContainer listener() { return registry.getListenerContainer("billingEvents"); }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }
    private UUID labId() { return UUID.fromString("00000000-0000-4000-8000-000000000002"); }
    private UUID requestId() { return jdbc.queryForObject("SELECT payment_request_id FROM lab_test_charge_source WHERE lab_id=?", UUID.class, labId()); }
    private RefundLedgerPaymentRequest refund(String key, String amount) {
        return new RefundLedgerPaymentRequest(key, new BigDecimal(amount), "Patient cancelled before collection", PaymentMethod.CASH);
    }
    private ResponseEntity<String> refundDue(UUID id) {
        var headers = new HttpHeaders(); headers.setBearerAuth(token("CASHIER")); headers.set("X-Correlation-Id", "lab-adjustment-read");
        return new org.springframework.boot.test.web.client.TestRestTemplate().exchange(
                "http://localhost:" + port() + "/api/v1/billing/lab-adjustments/" + id + "/refunds-due",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
    @org.springframework.beans.factory.annotation.Value("${local.server.port}") int localPort;
    private int port() { return localPort; }
    private HttpHeaders serviceHeaders(String subject) {
        var headers = new HttpHeaders(); headers.set("X-Correlation-Id", "lab-execution-start");
        Instant issued = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        headers.setBearerAuth(Jwts.builder().subject(subject).claim("role", "SYSTEM").claim("type", "service")
                .issuedAt(Date.from(issued)).expiration(Date.from(issued.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact());
        return headers;
    }
    private String token(String role) {
        var issued = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        return Jwts.builder().subject(UUID.randomUUID().toString()).claim("role", role).claim("type", "access")
                .issuedAt(Date.from(issued)).expiration(Date.from(issued.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
    private void drainCancellation() {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("outpatient_cancellation_source")).isEqualTo(1));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo(QUEUE).getMessageCount()).isZero());
        listener().stop();
    }
    private void drainCreation() {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("lab_test_charge_delivery")).isEqualTo(1));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo(QUEUE).getMessageCount()).isZero());
        listener().stop();
    }
    private byte[] retainedDlq(byte[] body) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo(DLQ).getMessageCount()).isOne());
        listener().stop(); var message = rabbit.receive(DLQ, 5000);
        assertThat(message).isNotNull(); assertThat(message.getBody()).isEqualTo(body); return message.getBody();
    }
    private void send(byte[] body) {
        listener().start();
        final String routingKey;
        try { routingKey = mapper.readTree(body).path("eventType").asText(); }
        catch (Exception malformed) { throw new AssertionError("Test message must carry an eventType", malformed); }
        rabbit.invoke(operations -> { operations.send("mediflow.events", routingKey, new Message(body, new MessageProperties())); operations.waitForConfirmsOrDie(10000); return null; });
    }
    private ObjectNode tree(byte[] body) throws Exception { return (ObjectNode) mapper.readTree(body); }
    private static byte[] createdFixture() throws Exception { return fixture("contracts/lab-request-v1/lab.request.created.v1.json"); }
    private static byte[] cancellationFixture() throws Exception { return fixture("contracts/lab-request-cancelled-v1/lab.request.cancelled.v1.json"); }
    private static byte[] fixture(String path) throws Exception {
        try (InputStream in = LabTestCancellationRabbitIntegrationTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("contract fixture must exist: " + path).isNotNull(); return in.readAllBytes();
        }
    }
}
