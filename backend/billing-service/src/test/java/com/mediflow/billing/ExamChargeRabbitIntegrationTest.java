package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.billing.application.dto.request.CompleteLedgerPaymentRequest;
import com.mediflow.billing.application.port.in.ProcessLedgerPaymentUseCase;
import com.mediflow.billing.application.port.in.RefundLedgerPaymentUseCase;
import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.infrastructure.pricing.CareFinancePriceProperties;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * HANDOFF-CLINICAL-LAB-FINANCIAL-CLEARANCE acceptance: "Docker proves check-in -> charge -> payment
 * -> EXAM clearance -> examination start". Billing can only prove its own side of that chain (up to
 * publishing the clearance); Clinical's own "examination start" consumption is out of this module's
 * ownership. Uses clinical-service's real appointment.status.changed.v1.json fixture, not a guess.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "mediflow.jwt.secret=billing-exam-integration-secret-at-least-32-bytes",
        "mediflow.billing.ledger.enabled=true", "mediflow.billing.exam-charge-consumer.enabled=true",
        "mediflow.billing.refunds.enabled=true",
        "mediflow.billing.outbox.enabled=false", "mediflow.billing.outbox.metrics-enabled=false", "mediflow.billing.outbox.maintenance-enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.publisher-confirm-type=simple"})
class ExamChargeRabbitIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer MQ = new RabbitMQContainer("rabbitmq:3.13-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl); r.add("spring.datasource.username", PG::getUsername); r.add("spring.datasource.password", PG::getPassword);
        r.add("spring.rabbitmq.host", MQ::getHost); r.add("spring.rabbitmq.port", MQ::getAmqpPort);
        r.add("spring.rabbitmq.username", MQ::getAdminUsername); r.add("spring.rabbitmq.password", MQ::getAdminPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitListenerEndpointRegistry registry;
    @Autowired ProcessLedgerPaymentUseCase payments;
    @Autowired RefundLedgerPaymentUseCase refunds;
    @Autowired CareFinancePriceProperties catalog;
    @Autowired ObjectMapper mapper;
    private static final String QUEUE = "billing.q", DLQ = "billing.dlq";
    private static final String ROUTING_KEY = "appointment.status.changed";

    @BeforeEach void reset() {
        listener().stop(); admin.purgeQueue(QUEUE); admin.purgeQueue(DLQ);
        jdbc.execute("TRUNCATE BILLING_ACCOUNT,BILLING_EVENT_OUTBOX,exam_charge_delivery CASCADE");
        catalog.setPrices(Map.of("OUTPATIENT_EXAM", new CareFinancePriceProperties.Price("Kham ngoai tru", new BigDecimal("100000.00"))));
        listener().start();
    }
    @AfterEach void stop() { listener().stop(); }

    @Test void consume_checkInThenPayment_exactExamClearanceGranted() throws Exception {
        byte[] body = fixture(); send(body); drain(1);
        assertThat(count("CHARGE")).isOne();
        assertThat(count("PAYMENT_REQUEST")).isOne();
        assertThat(jdbc.queryForObject("SELECT gross_amount FROM CHARGE", BigDecimal.class)).isEqualByComparingTo("100000.00");
        UUID appointmentId = UUID.fromString(tree(body).path("payload").path("appointmentId").asText());
        assertThat(jdbc.queryForObject("SELECT appointment_id FROM PAYMENT_REQUEST_TARGET", UUID.class)).isEqualTo(appointmentId);

        UUID request = jdbc.queryForObject("SELECT payment_request_id FROM PAYMENT_REQUEST", UUID.class);
        payments.complete(request, new CompleteLedgerPaymentRequest("exam-cash", new BigDecimal("100000.00"), "VND", PaymentMethod.CASH, null),
                UUID.randomUUID(), "payment-trace");

        var grant = mapper.readTree(jdbc.queryForObject(
                "SELECT payload FROM BILLING_EVENT_OUTBOX WHERE routing_key='financial.clearance.granted'", String.class)).path("payload");
        assertThat(grant.path("purpose").asText()).isEqualTo("EXAM");
        assertThat(grant.path("appointmentId").asText()).isEqualTo(appointmentId.toString());
        assertThat(grant.path("careEpisodeId").asText()).isEqualTo(appointmentId.toString());
        assertThat(grant.path("recordId").isNull()).isTrue();
        assertThat(grant.path("amount").decimalValue()).isEqualByComparingTo("100000.00");
    }

    @Test void consume_duplicateDelivery_oneChargeAndOneRequest() throws Exception {
        byte[] body = fixture(); send(body); send(body); drain(1);
        assertThat(count("CHARGE")).isOne();
        assertThat(count("PAYMENT_REQUEST")).isOne();
        assertThat(count("exam_charge_delivery")).isOne();
    }

    @Test void consume_malformedTriggerShape_retainedInDlqWithoutEffect() throws Exception {
        var root = tree(fixture()); root.put("eventId", UUID.randomUUID().toString());
        ((ObjectNode) root.path("payload")).put("oldStatus", "READY_FOR_EXAM"); // violates the check-in invariant
        byte[] invalid = mapper.writeValueAsBytes(root);
        send(invalid); retainedDlq(invalid);
        assertThat(count("exam_charge_delivery")).isZero();
        assertThat(count("CHARGE")).isZero();
        assertThat(count("PAYMENT_REQUEST")).isZero();
    }

    @Test void consume_otherValidTransition_isAckedWithoutCharge() throws Exception {
        var root = tree(fixture()); root.put("eventId", UUID.randomUUID().toString());
        var payload = (ObjectNode) root.path("payload");
        payload.put("oldStatus", "AWAITING_PAYMENT").put("newStatus", "IN_EXAM").put("recordId", UUID.randomUUID().toString());
        byte[] notApplicable = mapper.writeValueAsBytes(root);
        send(notApplicable);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo(QUEUE).getMessageCount()).isZero());
        listener().stop();
        assertThat(admin.getQueueInfo(DLQ).getMessageCount()).isZero();
        assertThat(count("exam_charge_delivery")).isZero();
        assertThat(count("CHARGE")).isZero();
    }

    @Test void consume_exactClinicalCancellationFixtureUnlocksOnlyAuditedRefundDue() throws Exception {
        byte[] creation = fixture(); send(creation); drain(1);
        UUID appointmentId = UUID.fromString(tree(creation).path("payload").path("appointmentId").asText());
        UUID request = jdbc.queryForObject("SELECT payment_request_id FROM PAYMENT_REQUEST", UUID.class);
        var payment = payments.complete(request,
                new CompleteLedgerPaymentRequest("exam-cash", new BigDecimal("100000.00"), "VND", PaymentMethod.CASH, null),
                UUID.randomUUID(), "exam-payment");
        assertThat(count("outpatient_execution_start")).isZero();
        assertThatThrownBy(() -> refunds.refund(payment.transactionId(), refund("exam-refund-before-cancel", "1000.00"),
                UUID.randomUUID(), "exam-refund-before-cancel"))
                .hasMessage("BILLING_REFUND_REQUIRES_CANCELLATION_EVIDENCE");

        byte[] cancellation = examCancellationFixture();
        listener().start();
        send(cancellation);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM outpatient_cancellation_source WHERE source_type='EXAM' AND source_id=?",
                    Integer.class, appointmentId)).isOne();
            assertThat(jdbc.queryForObject("SELECT status FROM outpatient_cancellation_source WHERE source_type='EXAM' AND source_id=?",
                    String.class, appointmentId)).isEqualTo("APPLIED");
        });
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo(QUEUE).getMessageCount()).isZero());
        listener().stop();

        assertThat(jdbc.queryForObject("SELECT status FROM CHARGE", String.class)).isEqualTo("VOIDED");
        assertThat(jdbc.queryForObject("SELECT amount FROM outpatient_cancellation_refund_due", BigDecimal.class))
                .isEqualByComparingTo("100000.00");
        var refund = refunds.refund(payment.transactionId(), refund("exam-refund-after-cancel", "1000.00"),
                UUID.randomUUID(), "exam-refund-after-cancel");
        assertThat(refund.originalTransactionId()).isEqualTo(payment.transactionId());
        assertThat(count("outpatient_execution_start")).isZero();
    }

    private org.springframework.amqp.rabbit.listener.MessageListenerContainer listener() { return registry.getListenerContainer("billingEvents"); }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }
    private void drain(long deliveries) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("exam_charge_delivery")).isEqualTo(deliveries));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo(QUEUE).getMessageCount()).isZero());
        listener().stop();
    }
    private byte[] retainedDlq(byte[] body) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo(DLQ).getMessageCount()).isOne());
        listener().stop(); var message = rabbit.receive(DLQ, 5000);
        assertThat(message).isNotNull(); assertThat(message.getBody()).isEqualTo(body); return message.getBody();
    }
    private void send(byte[] body) {
        final String routingKey;
        try { routingKey = mapper.readTree(body).path("eventType").asText(); }
        catch (Exception malformed) { throw new AssertionError("Test message must carry an eventType", malformed); }
        rabbit.invoke(operations -> { operations.send("mediflow.events", routingKey, new Message(body, new MessageProperties())); operations.waitForConfirmsOrDie(10000); return null; });
    }
    private com.mediflow.billing.application.dto.request.RefundLedgerPaymentRequest refund(String key, String amount) {
        return new com.mediflow.billing.application.dto.request.RefundLedgerPaymentRequest(key, new BigDecimal(amount),
                "Patient declined before examination", PaymentMethod.CASH);
    }
    private ObjectNode tree(byte[] body) throws Exception { return (ObjectNode) mapper.readTree(body); }
    private static byte[] fixture() throws Exception {
        try (InputStream in = ExamChargeRabbitIntegrationTest.class.getClassLoader()
                .getResourceAsStream("contracts/appointment-status-changed-v1/appointment.status.changed.v1.json")) {
            assertThat(in).as("EXAM check-in fixture must exist").isNotNull();
            return in.readAllBytes();
        }
    }
    private static byte[] examCancellationFixture() throws Exception {
        try (InputStream in = ExamChargeRabbitIntegrationTest.class.getClassLoader()
                .getResourceAsStream("contracts/appointment-exam-cancelled-v1/appointment.exam.cancelled.v1.json")) {
            assertThat(in).as("Clinical EXAM cancellation fixture must exist").isNotNull();
            return in.readAllBytes();
        }
    }
}
