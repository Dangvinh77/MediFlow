package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.billing.application.dto.request.CompleteLedgerPaymentRequest;
import com.mediflow.billing.application.port.in.ProcessLedgerPaymentUseCase;
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
import static org.awaitility.Awaitility.await;

/**
 * HANDOFF-CLINICAL-LAB-FINANCIAL-CLEARANCE acceptance: "Docker proves Lab request -> charge ->
 * payment -> LAB_TEST clearance -> execution start". Billing can only prove its own side of that
 * chain (up to publishing the clearance); Lab's own "execution start" consumption is out of this
 * module's ownership. Uses lab-service's real lab.request.created.v1.json fixture, not a guess.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "mediflow.jwt.secret=billing-lab-test-integration-secret-at-least-32-bytes",
        "mediflow.billing.ledger.enabled=true", "mediflow.billing.lab-test-charge-consumer.enabled=true",
        "mediflow.billing.outbox.enabled=false", "mediflow.billing.outbox.metrics-enabled=false", "mediflow.billing.outbox.maintenance-enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.publisher-confirm-type=simple"})
class LabTestChargeRabbitIntegrationTest {
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
    @Autowired CareFinancePriceProperties catalog;
    @Autowired ObjectMapper mapper;
    private static final String QUEUE = "billing.q", DLQ = "billing.dlq";
    private static final String ROUTING_KEY = "lab.request.created";

    @BeforeEach void reset() {
        listener().stop(); admin.purgeQueue(QUEUE); admin.purgeQueue(DLQ);
        jdbc.execute("TRUNCATE BILLING_ACCOUNT,BILLING_EVENT_OUTBOX,lab_test_charge_delivery CASCADE");
        catalog.setPrices(Map.of("LAB-CBC", new CareFinancePriceProperties.Price("Xet nghiem CBC", new BigDecimal("150000.00"))));
        listener().start();
    }
    @AfterEach void stop() { listener().stop(); }

    @Test void consume_labRequestThenPayment_exactLabTestClearanceGranted() throws Exception {
        byte[] body = fixture(); send(body); drain(1);
        assertThat(count("CHARGE")).isOne();
        assertThat(count("PAYMENT_REQUEST")).isOne();
        assertThat(jdbc.queryForObject("SELECT gross_amount FROM CHARGE", BigDecimal.class)).isEqualByComparingTo("150000.00");
        UUID labId = UUID.fromString(tree(body).path("payload").path("labId").asText());

        UUID request = jdbc.queryForObject("SELECT payment_request_id FROM PAYMENT_REQUEST", UUID.class);
        payments.complete(request, new CompleteLedgerPaymentRequest("lab-cash", new BigDecimal("150000.00"), "VND", PaymentMethod.CASH, null),
                UUID.randomUUID(), "payment-trace");

        var grant = mapper.readTree(jdbc.queryForObject(
                "SELECT payload FROM BILLING_EVENT_OUTBOX WHERE routing_key='financial.clearance.granted'", String.class)).path("payload");
        assertThat(grant.path("purpose").asText()).isEqualTo("LAB_TEST");
        assertThat(grant.path("labTestIds").get(0).asText()).isEqualTo(labId.toString());
        assertThat(grant.path("amount").decimalValue()).isEqualByComparingTo("150000.00");
    }

    @Test void consume_duplicateDelivery_oneChargeAndOneRequest() throws Exception {
        byte[] body = fixture(); send(body); send(body); drain(1);
        assertThat(count("CHARGE")).isOne();
        assertThat(count("PAYMENT_REQUEST")).isOne();
        assertThat(count("lab_test_charge_delivery")).isOne();
    }

    @Test void consume_mismatchedSourceId_retainedInDlqWithoutEffect() throws Exception {
        var root = tree(fixture()); root.put("eventId", UUID.randomUUID().toString());
        ((ObjectNode) root.path("payload")).put("sourceId", UUID.randomUUID().toString()); // must equal labId
        byte[] invalid = mapper.writeValueAsBytes(root);
        send(invalid); retainedDlq(invalid);
        assertThat(count("lab_test_charge_delivery")).isZero();
        assertThat(count("CHARGE")).isZero();
        assertThat(count("PAYMENT_REQUEST")).isZero();
    }

    private org.springframework.amqp.rabbit.listener.MessageListenerContainer listener() { return registry.getListenerContainer("billingEvents"); }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }
    private void drain(long deliveries) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("lab_test_charge_delivery")).isEqualTo(deliveries));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo(QUEUE).getMessageCount()).isZero());
        listener().stop();
    }
    private byte[] retainedDlq(byte[] body) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo(DLQ).getMessageCount()).isOne());
        listener().stop(); var message = rabbit.receive(DLQ, 5000);
        assertThat(message).isNotNull(); assertThat(message.getBody()).isEqualTo(body); return message.getBody();
    }
    private void send(byte[] body) {
        rabbit.invoke(operations -> { operations.send("mediflow.events", ROUTING_KEY, new Message(body, new MessageProperties())); operations.waitForConfirmsOrDie(10000); return null; });
    }
    private ObjectNode tree(byte[] body) throws Exception { return (ObjectNode) mapper.readTree(body); }
    private static byte[] fixture() throws Exception {
        try (InputStream in = LabTestChargeRabbitIntegrationTest.class.getClassLoader()
                .getResourceAsStream("contracts/lab-request-v1/lab.request.created.v1.json")) {
            assertThat(in).as("LAB_TEST request fixture must exist").isNotNull();
            return in.readAllBytes();
        }
    }
}
