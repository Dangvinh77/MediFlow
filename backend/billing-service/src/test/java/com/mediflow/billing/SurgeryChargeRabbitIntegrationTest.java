package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.billing.application.dto.request.CompleteLedgerPaymentRequest;
import com.mediflow.billing.application.port.in.IssueSurgeryChargeUseCase;
import com.mediflow.billing.application.port.in.ProcessLedgerPaymentUseCase;
import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.infrastructure.messaging.SurgeryChargeDecoder;
import com.mediflow.billing.infrastructure.pricing.CareFinancePriceProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"eureka.client.enabled=false", "mediflow.jwt.secret=billing-surgery-integration-secret-at-least-32-bytes",
        "mediflow.billing.ledger.enabled=true", "mediflow.billing.surgery-charge-consumer.enabled=true",
        "mediflow.billing.clearance-lookup.enabled=true",
        "mediflow.billing.outbox.enabled=false", "mediflow.billing.outbox.metrics-enabled=false", "mediflow.billing.outbox.maintenance-enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.publisher-confirm-type=simple"})
class SurgeryChargeRabbitIntegrationTest {
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
    @Autowired IssueSurgeryChargeUseCase charges;
    @Autowired ProcessLedgerPaymentUseCase payments;
    @Autowired SurgeryChargeDecoder decoder;
    @Autowired CareFinancePriceProperties catalog;
    @Autowired ObjectMapper mapper;
    @Autowired TestRestTemplate http;
    private static final String QUEUE = "billing.q", DLQ = "billing.dlq";
    @BeforeEach void reset() {
        listener().stop(); admin.purgeQueue(QUEUE); admin.purgeQueue(DLQ);
        jdbc.execute("TRUNCATE BILLING_ACCOUNT,BILLING_EVENT_OUTBOX,surgery_charge_delivery CASCADE");
        catalog.setPrices(Map.of("PRICE", new CareFinancePriceProperties.Price("Synthetic test catalog", new BigDecimal("100.00"))));
        listener().start();
    }
    @AfterEach void stop() { listener().stop(); }
    @Test void consume_singlePhysicalIntake_noCompetingSurgeryQueueOrCompatibilityEffect() throws Exception {
        assertThat(admin.getQueueInfo("billing.surgery-charge-v1.q")).isNull();
        assertThat(registry.getListenerContainer("billingSurgeryCharges")).isNull();
        send(SurgeryChargeContractTest.fixture("admission")); drain(1);
        assertThat(count("CHARGE")).isOne();
        assertThat(count("PAYMENT_REQUEST")).isOne();
        assertThat(count("PROCESSED_EVENT")).isZero();
        assertThat(count("BILLING_EVENT_OUTBOX")).isOne();
    }
    @ParameterizedTest @ValueSource(strings = {"admission", "outpatient"})
    void consume_realCreationThenPayment_exactClearanceAndHeldReceipts(String context) throws Exception {
        var body = SurgeryChargeContractTest.fixture(context); send(body); drain(1);
        var command = decoder.decode("surgery.case.created", body);
        UUID request = jdbc.queryForObject("SELECT payment_request_id FROM PAYMENT_REQUEST", UUID.class);
        assertThat(jdbc.queryForObject("SELECT gross_amount FROM CHARGE", BigDecimal.class)).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT surgery_case_id FROM PAYMENT_REQUEST_TARGET", UUID.class)).isEqualTo(command.surgeryCaseId());
        payments.complete(request, new CompleteLedgerPaymentRequest("surgery-cash", new BigDecimal("100"), "VND", PaymentMethod.CASH, null), UUID.randomUUID(), "payment-trace");
        var grant = mapper.readTree(jdbc.queryForObject("SELECT payload FROM BILLING_EVENT_OUTBOX WHERE routing_key='financial.clearance.granted'", String.class)).path("payload");
        assertThat(grant.path("surgeryCaseId").asText()).isEqualTo(command.surgeryCaseId().toString());
        assertThat(grant.path("careEpisodeId").asText()).isEqualTo(command.careEpisodeId().toString());
        assertThat(count("BILLING_EVENT_OUTBOX")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM BILLING_EVENT_OUTBOX WHERE publication_enabled", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM PAYMENT_ALLOCATION", BigDecimal.class)).isEqualByComparingTo("100");
        UUID clearanceId = UUID.fromString(grant.path("clearanceId").asText());
        var now = java.time.Instant.now(); var headers = new HttpHeaders(); headers.set("X-Correlation-Id", "issued-surgery-authority");
        headers.setBearerAuth(io.jsonwebtoken.Jwts.builder().subject("surgery-service").claim("type", "service").claim("role", "SYSTEM")
                .issuedAt(java.util.Date.from(now)).expiration(java.util.Date.from(now.plusSeconds(30)))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor("billing-surgery-integration-secret-at-least-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact());
        var response = http.exchange("/api/v1/billing/financial-clearances/" + clearanceId + "/lookup", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var authority = mapper.readTree(response.getBody()).path("data");
        assertThat(authority.path("eligible").asBoolean()).isTrue();
        assertThat(authority.path("surgeryCaseId").asText()).isEqualTo(command.surgeryCaseId().toString());
        assertThat(authority.has("amount")).isFalse();
    }
    @Test void consume_duplicateAndNewDelivery_replaysOriginalRequestWithoutRepricingClosedAccount() throws Exception {
        byte[] body = SurgeryChargeContractTest.fixture("admission"); send(body); send(body); drain(1);
        UUID request = jdbc.queryForObject("SELECT payment_request_id FROM PAYMENT_REQUEST", UUID.class);
        catalog.setPrices(Map.of()); jdbc.update("UPDATE BILLING_ACCOUNT SET status='CLOSED'");
        var root = tree(body); root.put("eventId", UUID.randomUUID().toString());
        listener().start(); send(mapper.writeValueAsBytes(root)); drain(2);
        assertThat(jdbc.queryForObject("SELECT payment_request_id FROM PAYMENT_REQUEST", UUID.class)).isEqualTo(request);
        assertThat(count("CHARGE")).isOne(); assertThat(count("BILLING_EVENT_OUTBOX")).isOne();
    }
    @Test void consume_changedOriginalQuantity_sameCaseIsNotImplicitCorrection() throws Exception {
        byte[] body = SurgeryChargeContractTest.fixture("admission"); send(body); drain(1);
        var root = tree(body); root.put("eventId", UUID.randomUUID().toString()); ((ObjectNode)root.path("payload").path("plannedItems").get(0)).put("quantity", 2);
        byte[] invalid = mapper.writeValueAsBytes(root); listener().start(); send(invalid); retainedDlq(invalid);
        assertThat(count("surgery_charge_delivery")).isOne(); assertThat(count("PAYMENT_REQUEST")).isOne();
        assertThat(jdbc.queryForObject("SELECT quantity FROM CHARGE", BigDecimal.class)).isEqualByComparingTo("1");
    }
    @Test void consume_twoEpisodesSamePatient_neverShareAccount() throws Exception {
        send(SurgeryChargeContractTest.fixture("admission")); drain(1);
        var root = tree(SurgeryChargeContractTest.fixture("outpatient")); root.put("eventId", UUID.randomUUID().toString());
        var payload = (ObjectNode)root.get("payload"); UUID caseId = UUID.randomUUID();
        payload.put("surgeryCaseId", caseId.toString()).put("sourceId", caseId.toString()).put("surgeryRequestId", UUID.randomUUID().toString())
                .put("patientId", tree(SurgeryChargeContractTest.fixture("admission")).path("payload").path("patientId").asText());
        listener().start(); send(mapper.writeValueAsBytes(root)); drain(2);
        assertThat(count("BILLING_ACCOUNT")).isEqualTo(2); assertThat(count("PAYMENT_REQUEST")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT patient_id) FROM BILLING_ACCOUNT", Long.class)).isOne();
    }
    @Test void consume_sameEpisodeWrongPatient_rejectsWithoutNewCharge() throws Exception {
        send(SurgeryChargeContractTest.fixture("admission")); drain(1);
        var root = tree(SurgeryChargeContractTest.fixture("admission")); root.put("eventId", UUID.randomUUID().toString());
        var payload = (ObjectNode)root.get("payload"); UUID caseId = UUID.randomUUID();
        payload.put("surgeryCaseId", caseId.toString()).put("sourceId", caseId.toString()).put("surgeryRequestId", UUID.randomUUID().toString()).put("patientId", UUID.randomUUID().toString());
        byte[] invalid = mapper.writeValueAsBytes(root); listener().start(); send(invalid); retainedDlq(invalid);
        assertThat(count("surgery_charge_delivery")).isOne(); assertThat(count("PAYMENT_REQUEST")).isOne(); assertThat(count("CHARGE")).isOne();
    }
    @Test void consume_sameEpisodeDifferentGeneratingDepartment_reusesAccountWithoutRewritingAttribution() throws Exception {
        byte[] first = SurgeryChargeContractTest.fixture("admission"); send(first); drain(1);
        UUID accountDepartment = jdbc.queryForObject("SELECT department_id FROM BILLING_ACCOUNT", UUID.class);
        var root = tree(first); root.put("eventId", UUID.randomUUID().toString()); var payload = (ObjectNode)root.get("payload");
        UUID caseId = UUID.randomUUID(), generatingDepartment = UUID.randomUUID();
        payload.put("surgeryCaseId", caseId.toString()).put("sourceId", caseId.toString())
                .put("surgeryRequestId", UUID.randomUUID().toString()).put("departmentId", generatingDepartment.toString());
        listener().start(); send(mapper.writeValueAsBytes(root)); drain(2);
        assertThat(count("BILLING_ACCOUNT")).isOne(); assertThat(count("CHARGE")).isEqualTo(2); assertThat(count("PAYMENT_REQUEST")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT department_id FROM BILLING_ACCOUNT", UUID.class)).isEqualTo(accountDepartment);
        assertThat(jdbc.queryForObject("SELECT department_id FROM CHARGE WHERE source_id=?", UUID.class, caseId)).isEqualTo(generatingDepartment);
    }
    @Test void consume_quantityFourDecimals_isPersistedExactly() throws Exception {
        var root = tree(SurgeryChargeContractTest.fixture("admission"));
        ((ObjectNode)root.path("payload").path("plannedItems").get(0)).put("quantity", new BigDecimal("1.0001"));
        send(mapper.writeValueAsBytes(root)); drain(1);
        assertThat(jdbc.queryForObject("SELECT quantity FROM CHARGE", BigDecimal.class)).isEqualByComparingTo("1.0001");
        assertThat(jdbc.queryForObject("SELECT requested_amount FROM PAYMENT_REQUEST", BigDecimal.class)).isEqualByComparingTo("100.01");
    }
    @Test void consume_twoItemsSamePrice_oneChargeButBothSourceLines() throws Exception {
        var root = tree(SurgeryChargeContractTest.fixture("admission"));
        var items = (com.fasterxml.jackson.databind.node.ArrayNode)root.path("payload").path("plannedItems");
        var second = ((ObjectNode)items.get(0)).deepCopy(); second.put("itemCode", "EXTRA_ITEM").put("quantity", new BigDecimal("0.5")); items.add(second);
        send(mapper.writeValueAsBytes(root)); drain(1);
        assertThat(count("CHARGE")).isOne(); assertThat(count("surgery_charge_item")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT requested_amount FROM PAYMENT_REQUEST", BigDecimal.class)).isEqualByComparingTo("150");
    }
    @Test void consume_unknownPrice_rollsBackEverythingThenRetainedBytesRecover() throws Exception {
        catalog.setPrices(Map.of()); byte[] body = SurgeryChargeContractTest.fixture("admission"); send(body); byte[] retained = retainedDlq(body);
        assertThat(count("surgery_charge_delivery")).isZero(); assertThat(count("BILLING_ACCOUNT")).isZero(); assertThat(count("PAYMENT_REQUEST")).isZero();
        catalog.setPrices(Map.of("PRICE", new CareFinancePriceProperties.Price("Reviewed test catalog", new BigDecimal("100"))));
        listener().start(); send(retained); drain(1); assertThat(count("CHARGE")).isOne();
    }
    @Test void consume_outboxFailure_retriesThreeTimesRollsBackAllEffectsAndRecoversExactBytes() throws Exception {
        jdbc.execute("CREATE SEQUENCE surgery_charge_attempts");
        jdbc.execute("CREATE FUNCTION reject_charge_request() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN PERFORM nextval('surgery_charge_attempts'); RAISE EXCEPTION 'injected'; END $$");
        jdbc.execute("CREATE TRIGGER reject_charge_request BEFORE INSERT ON BILLING_EVENT_OUTBOX FOR EACH ROW EXECUTE FUNCTION reject_charge_request()");
        byte[] body = SurgeryChargeContractTest.fixture("admission"), retained;
        try {
            send(body); retained = retainedDlq(body);
            assertThat(jdbc.queryForObject("SELECT last_value FROM surgery_charge_attempts", Long.class)).isEqualTo(3);
            for (String table : List.of("surgery_charge_delivery", "surgery_charge_source", "surgery_charge_item", "BILLING_ACCOUNT", "CHARGE", "PAYMENT_REQUEST", "BILLING_EVENT_OUTBOX")) assertThat(count(table)).as(table).isZero();
        } finally { jdbc.execute("DROP TRIGGER reject_charge_request ON BILLING_EVENT_OUTBOX"); jdbc.execute("DROP FUNCTION reject_charge_request()"); jdbc.execute("DROP SEQUENCE surgery_charge_attempts"); }
        listener().start(); send(retained); drain(1); assertThat(count("PAYMENT_REQUEST")).isOne();
    }
    @Test void issue_twoConcurrentDeliveries_sameSourceOneRequestAndOutbox() throws Exception {
        listener().stop(); byte[] body = SurgeryChargeContractTest.fixture("admission"); var root = tree(body); root.put("eventId", UUID.randomUUID().toString());
        var first = decoder.decode("surgery.case.created", body); var second = decoder.decode("surgery.case.created", mapper.writeValueAsBytes(root));
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> { start.await(); return charges.issue(first); });
            var b = workers.submit(() -> { start.await(); return charges.issue(second); }); start.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS)).isEqualTo(b.get(15, TimeUnit.SECONDS));
        }
        assertThat(count("surgery_charge_delivery")).isEqualTo(2); assertThat(count("PAYMENT_REQUEST")).isOne(); assertThat(count("CHARGE")).isOne(); assertThat(count("BILLING_EVENT_OUTBOX")).isOne();
    }
    private org.springframework.amqp.rabbit.listener.MessageListenerContainer listener() { return registry.getListenerContainer("billingEvents"); }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }
    private void drain(long deliveries) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("surgery_charge_delivery")).isEqualTo(deliveries));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo(QUEUE).getMessageCount()).isZero()); listener().stop();
    }
    private byte[] retainedDlq(byte[] body) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo(DLQ).getMessageCount()).isOne());
        listener().stop(); var message = rabbit.receive(DLQ, 5000); assertThat(message).isNotNull(); assertThat(message.getBody()).isEqualTo(body); return message.getBody();
    }
    private void send(byte[] body) { rabbit.invoke(operations -> { operations.send("mediflow.events", "surgery.case.created", new Message(body, new MessageProperties())); operations.waitForConfirmsOrDie(10000); return null; }); }
    private ObjectNode tree(byte[] body) throws Exception { return (ObjectNode)mapper.readTree(body); }
}
