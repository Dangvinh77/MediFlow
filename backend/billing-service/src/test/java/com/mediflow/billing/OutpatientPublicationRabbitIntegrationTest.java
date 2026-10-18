package com.mediflow.billing;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.dto.request.CompleteLedgerPaymentRequest;
import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.infrastructure.pricing.CareFinancePriceProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
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

/**
 * L3 — actual Rabbit publication proof (HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md §5, L3).
 * Real Postgres + real RabbitMQ + the actual {@code BillingOutboxDispatcher} (never mocked,
 * never a direct {@code rabbit.send} standing in for it). Each flow uses the owning service's real
 * producer fixture, finds its request through the real L1 HTTP endpoint, pays through the real
 * signed POST, and receives the grant/receipt as actual broker bytes — compared by SHA-256 against
 * the exact bytes {@link com.mediflow.billing.infrastructure.messaging.LedgerEventOutboxAdapter}
 * stored, never just the JSON in the table.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "mediflow.jwt.secret=billing-outpatient-publication-integration-secret-32-bytes-min",
        "mediflow.billing.ledger.enabled=true",
        "mediflow.billing.exam-charge-consumer.enabled=true",
        "mediflow.billing.lab-test-charge-consumer.enabled=true",
        "mediflow.billing.prescription-charge-consumer.enabled=true",
        "mediflow.billing.prescription-charges.allowed-price-codes=RX-DEFAULT",
        "mediflow.billing.payment-request-discovery.enabled=true",
        "mediflow.billing.outpatient-publication.enabled=true",
        "mediflow.billing.outpatient-publication.purposes=EXAM,LAB_TEST,PRESCRIPTION",
        "mediflow.billing.outpatient-publication.acceptance-reference=L3-ACCEPTANCE-RUN",
        "mediflow.features.outpatient-mvp.enabled=true",
        "mediflow.billing.outbox.enabled=true",
        "mediflow.billing.outbox.dispatch-delay-ms=300",
        "mediflow.billing.outbox.dispatch-initial-delay-ms=300",
        "mediflow.billing.outbox.retry.initial-backoff-ms=200",
        "mediflow.billing.outbox.retry.max-backoff-ms=2000",
        "mediflow.billing.outbox.metrics-enabled=false",
        "mediflow.billing.outbox.maintenance-enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.publisher-confirm-type=correlated",
        "spring.rabbitmq.publisher-returns=true"})
class OutpatientPublicationRabbitIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer MQ = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl); r.add("spring.datasource.username", PG::getUsername); r.add("spring.datasource.password", PG::getPassword);
        r.add("spring.rabbitmq.host", MQ::getHost); r.add("spring.rabbitmq.port", MQ::getAmqpPort);
        r.add("spring.rabbitmq.username", MQ::getAdminUsername); r.add("spring.rabbitmq.password", MQ::getAdminPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitListenerEndpointRegistry registry;
    @Autowired CareFinancePriceProperties catalog;
    @Autowired ObjectMapper mapper;
    @Autowired TestRestTemplate http;

    private static final String INBOUND_QUEUE = "billing.q", INBOUND_DLQ = "billing.dlq";
    private static final String OUTBOUND_QUEUE = "outpatient-publication-test";
    private static final String JWT_SECRET = "billing-outpatient-publication-integration-secret-32-bytes-min";

    // A dedicated connection, standing in for another microservice listening to billing's own
    // outbound events — never the app's own internal RabbitTemplate, so observing deliveries here
    // cannot interact with the dispatcher's own confirm/channel state.
    private static CachingConnectionFactory externalConnection;
    private static RabbitAdmin externalAdmin;
    private static RabbitTemplate externalRabbit;

    @org.junit.jupiter.api.BeforeAll static void openExternalConnection() {
        externalConnection = new CachingConnectionFactory(MQ.getHost(), MQ.getAmqpPort());
        externalConnection.setUsername(MQ.getAdminUsername());
        externalConnection.setPassword(MQ.getAdminPassword());
        externalAdmin = new RabbitAdmin(externalConnection);
        externalRabbit = new RabbitTemplate(externalConnection);
        externalAdmin.declareExchange(new TopicExchange("mediflow.events", true, false));
        externalAdmin.declareQueue(new Queue(OUTBOUND_QUEUE, true, false, false));
        for (String key : new String[]{"payment.completed", "financial.clearance.granted", "invoice.created"}) {
            externalAdmin.declareBinding(new Binding(OUTBOUND_QUEUE, Binding.DestinationType.QUEUE, "mediflow.events", key, null));
        }
    }
    @org.junit.jupiter.api.AfterAll static void closeExternalConnection() { externalConnection.destroy(); }

    private java.nio.file.Path evidence;

    @BeforeEach void reset(org.junit.jupiter.api.TestInfo test) throws Exception {
        evidence = java.nio.file.Path.of("target", "outpatient-handoff", "raw", test.getTestMethod().orElseThrow().getName());
        java.nio.file.Files.createDirectories(evidence);
        try (var files = java.nio.file.Files.list(evidence)) {
            for (var file : files.toList()) java.nio.file.Files.delete(file);
        }
        listener().stop();
        admin.purgeQueue(INBOUND_QUEUE); admin.purgeQueue(INBOUND_DLQ);
        jdbc.execute("TRUNCATE BILLING_ACCOUNT, BILLING_EVENT_OUTBOX, exam_charge_delivery, "
                + "lab_test_charge_delivery, prescription_charge_delivery CASCADE");
        catalog.setPrices(Map.of(
                "OUTPATIENT_EXAM", new CareFinancePriceProperties.Price("Kham ngoai tru", new BigDecimal("100000.00")),
                "LAB-CBC", new CareFinancePriceProperties.Price("Xet nghiem CBC", new BigDecimal("150000.00"))));
        externalAdmin.purgeQueue(OUTBOUND_QUEUE);
        buffered.clear();
        listener().start();
    }
    @AfterEach void stop() throws Exception {
        listener().stop();
        if (evidence != null) {
            saveEvidence("migrations", mapper.writeValueAsBytes(jdbc.queryForList(
                    "SELECT version,description,checksum,success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank")));
            saveEvidence("outbox-progress", mapper.writeValueAsBytes(jdbc.queryForList(
                    "SELECT event_id,routing_key,publication_enabled,published_at,attempts,quarantined_at FROM BILLING_EVENT_OUTBOX ORDER BY created_at,event_id")));
        }
    }

    private void saveEvidence(String name, byte[] body) {
        try {
            java.nio.file.Files.write(evidence.resolve(name + "-" + UUID.randomUUID() + ".json"), body);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException("Cannot export publication evidence", failure);
        }
    }

    @Test void examCheckIn_thenRealHttpDiscoveryAndPayment_actualGrantBytesMatchStoredOutbox() throws Exception {
        byte[] body = examFixture(); send("appointment.status.changed", body); drain(1);
        var fixture = mapper.readTree(body).path("payload");
        UUID appointmentId = uuid(fixture, "appointmentId"), patientId = uuid(fixture, "patientId"), episode = appointmentId;

        var discovered = discover("EXAM", appointmentId, patientId, episode);
        UUID request = UUID.fromString(discovered.path("paymentRequestId").asText());

        pay(request, "exam-cash", "100000.00");
        assertMatchesBroker("payment.completed", request);
        assertMatchesBroker("financial.clearance.granted", request);
    }

    @Test void labRequest_thenRealHttpDiscoveryAndPayment_actualGrantBytesMatchStoredOutbox() throws Exception {
        byte[] body = labFixture(); send("lab.request.created", body); drain(1);
        var fixture = mapper.readTree(body).path("payload");
        UUID labId = uuid(fixture, "labId"), patientId = uuid(fixture, "patientId"), episode = uuid(fixture, "careEpisodeId");

        var discovered = discover("LAB_TEST", labId, patientId, episode);
        UUID request = UUID.fromString(discovered.path("paymentRequestId").asText());
        assertThat(discovered.path("labTestIds").get(0).asText()).isEqualTo(labId.toString());

        pay(request, "lab-cash", "150000.00");
        assertMatchesBroker("payment.completed", request);
        assertMatchesBroker("financial.clearance.granted", request);
    }

    @Test void prescriptionOutpatient_invoiceAtIssuance_thenRealHttpDiscoveryAndTwoInstallments() throws Exception {
        byte[] body = PrescriptionChargeContractTest.outpatientFixture();
        send("prescription.created", body); drainPrescription(1);
        var fixture = mapper.readTree(body).path("payload");
        UUID prescriptionId = uuid(fixture, "prescriptionId"), patientId = uuid(fixture, "patientId"), episode = uuid(fixture, "careEpisodeId");

        // invoice.created is eligible the instant the request is enrolled, before any payment.
        assertMatchesBrokerByEventType("invoice.created", prescriptionId);

        var discovered = discover("PRESCRIPTION", prescriptionId, patientId, episode);
        UUID request = UUID.fromString(discovered.path("paymentRequestId").asText());
        assertThat(discovered.path("requestedAmount").decimalValue()).isEqualByComparingTo("2000.00");

        pay(request, "rx-first", "750.00");
        assertThat(received("financial.clearance.granted", Duration.ofSeconds(2))).isNull();
        // Each installment produces its own payment.completed; both must be drained and verified —
        // not just the one a single assertMatchesBroker call happens to pick up first (PR #401 review).
        assertMatchesBrokerPaymentCompletedAmount(request, "750.00");
        pay(request, "rx-last", "1250.00");
        assertMatchesBrokerPaymentCompletedAmount(request, "1250.00");
        assertMatchesBroker("financial.clearance.granted", request);
    }

    private void assertMatchesBrokerPaymentCompletedAmount(UUID paymentRequestId, String expectedAmount) throws Exception {
        Message message = received("payment.completed", Duration.ofSeconds(20));
        assertThat(message).as("payment.completed delivered on the broker").isNotNull();
        var payload = mapper.readTree(message.getBody());
        assertThat(payload.path("eventType").asText()).isEqualTo("payment.completed");
        assertThat(payload.path("payload").path("totalAmount").decimalValue()).isEqualByComparingTo(expectedAmount);
        UUID eventId = UUID.fromString(payload.path("eventId").asText());
        byte[] stored = jdbc.queryForObject("SELECT payload FROM BILLING_EVENT_OUTBOX WHERE event_id=?", String.class, eventId)
                .getBytes(StandardCharsets.UTF_8);
        saveEvidence("stored-" + eventId, stored);
        assertThat(sha256(message.getBody())).as("broker bytes match stored outbox bytes exactly").isEqualTo(sha256(stored));
        assertThat(jdbc.queryForObject("SELECT outpatient_payment_request_id FROM BILLING_EVENT_OUTBOX WHERE event_id=?", UUID.class, eventId))
                .isEqualTo(paymentRequestId);
    }

    @Test void partialPayment_receiptPublishedButNoGrantYet() throws Exception {
        byte[] body = labFixture(); send("lab.request.created", body); drain(1);
        var fixture = mapper.readTree(body).path("payload");
        UUID labId = uuid(fixture, "labId"), patientId = uuid(fixture, "patientId"), episode = uuid(fixture, "careEpisodeId");
        UUID request = UUID.fromString(discover("LAB_TEST", labId, patientId, episode).path("paymentRequestId").asText());

        pay(request, "lab-partial", "60000.00");
        assertMatchesBroker("payment.completed", request);
        assertThat(received("financial.clearance.granted", Duration.ofSeconds(2))).isNull();
    }

    @Test void duplicatePaymentRetry_sameIdempotencyKey_noSecondBrokerDelivery() throws Exception {
        byte[] body = examFixture(); send("appointment.status.changed", body); drain(1);
        var fixture = mapper.readTree(body).path("payload");
        UUID appointmentId = uuid(fixture, "appointmentId"), patientId = uuid(fixture, "patientId");
        UUID request = UUID.fromString(discover("EXAM", appointmentId, patientId, appointmentId).path("paymentRequestId").asText());

        pay(request, "exam-retry", "100000.00");
        assertMatchesBroker("payment.completed", request);
        assertMatchesBroker("financial.clearance.granted", request);
        // Same idempotency key again: the use case must short-circuit before any new outbox row exists.
        pay(request, "exam-retry", "100000.00");
        assertThat(received("payment.completed", Duration.ofSeconds(2))).isNull();
        assertThat(received("financial.clearance.granted", Duration.ofSeconds(2))).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM BILLING_EVENT_OUTBOX", Integer.class)).isEqualTo(2);
    }

    @Test void deferredAdmissionDepositPurpose_isNeverPublishedToTheBroker() throws Exception {
        // Deferred purpose: seeded directly (its own consumer/fixture is a different owner's concern
        // here; only the outbox/dispatcher behavior for a non-enrolled purpose is under test).
        UUID account = UUID.randomUUID(), admission = UUID.randomUUID(), request = UUID.randomUUID(), invoice = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,currency,opened_at)
                VALUES (?,?,?,'ADMISSION',?,'VND',now())
                """, account, UUID.randomUUID(), UUID.randomUUID(), admission);
        jdbc.update("INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,requested_amount,currency) VALUES (?,?,?,'ADMISSION_DEPOSIT',?,'VND')",
                request, invoice, account, new BigDecimal("50000.00"));
        jdbc.update("INSERT INTO PAYMENT_REQUEST_TARGET(payment_request_id,admission_id) VALUES (?,?)", request, admission);

        pay(request, "deposit-cash", "50000.00");
        // ADMISSION_DEPOSIT paid in full also grants a clearance (not just a receipt); neither is
        // enrolled, so neither may ever reach the broker.
        assertThat(received("payment.completed", Duration.ofSeconds(3))).isNull();
        assertThat(received("financial.clearance.granted", Duration.ofSeconds(3))).isNull();
        assertThat(jdbc.queryForList(
                "SELECT publication_enabled FROM BILLING_EVENT_OUTBOX WHERE aggregate_id=?", Boolean.class, account))
                .hasSize(2).allMatch(enabled -> !enabled);
    }

    @Test void newEventIdForSamePrescriptionOperationDoesNotIssueOrPublishAgain() throws Exception {
        byte[] original = PrescriptionChargeContractTest.outpatientFixture();
        send("prescription.created", original); drainPrescription(1);
        var source = mapper.readTree(original).path("payload");
        UUID prescription = uuid(source, "prescriptionId");
        assertMatchesBrokerByEventType("invoice.created", prescription);
        var first = discover("PRESCRIPTION", prescription, uuid(source, "patientId"), uuid(source, "careEpisodeId"));

        var redelivery = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(original);
        redelivery.put("eventId", UUID.randomUUID().toString());
        listener().start(); send("prescription.created", mapper.writeValueAsBytes(redelivery)); drainPrescription(2);
        var second = discover("PRESCRIPTION", prescription, uuid(source, "patientId"), uuid(source, "careEpisodeId"));
        assertThat(second.path("paymentRequestId")).isEqualTo(first.path("paymentRequestId"));
        assertThat(received("invoice.created", Duration.ofSeconds(2))).isNull();

        UUID request = UUID.fromString(first.path("paymentRequestId").asText());
        pay(request, "rx-new-delivery", "2000.00");
        assertMatchesBroker("payment.completed", request);
        assertMatchesBroker("financial.clearance.granted", request);
        redelivery.put("eventId", UUID.randomUUID().toString());
        listener().start(); send("prescription.created", mapper.writeValueAsBytes(redelivery)); drainPrescription(3);
        pay(request, "rx-new-delivery", "2000.00");
        assertThat(received("invoice.created", Duration.ofSeconds(2))).isNull();
        assertThat(received("payment.completed", Duration.ofSeconds(2))).isNull();
        assertThat(received("financial.clearance.granted", Duration.ofSeconds(2))).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM CHARGE", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PAYMENT_REQUEST", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM BILLING_EVENT_OUTBOX", Integer.class)).isEqualTo(3);
    }

    @Test void outpatientIssuedWithPublicationOffStaysHeldAfterEnablingAndRetryingSourceAndPayment() throws Exception {
        listener().stop();
        byte[] original = PrescriptionChargeContractTest.outpatientFixture();
        // A second actual application context uses the same disposable stores and real consumer,
        // with publication OFF. No SQL seed or mocked enrollment stands in for historical issuance.
        try (var beforeActivation = new org.springframework.boot.builder.SpringApplicationBuilder(BillingServiceApplication.class).run(
                "--server.port=0", "--eureka.client.enabled=false",
                "--spring.datasource.url=" + PG.getJdbcUrl(), "--spring.datasource.username=" + PG.getUsername(),
                "--spring.datasource.password=" + PG.getPassword(), "--spring.rabbitmq.host=" + MQ.getHost(),
                "--spring.rabbitmq.port=" + MQ.getAmqpPort(), "--spring.rabbitmq.username=" + MQ.getAdminUsername(),
                "--spring.rabbitmq.password=" + MQ.getAdminPassword(), "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--mediflow.billing.ledger.enabled=true", "--mediflow.billing.prescription-charge-consumer.enabled=true",
                "--mediflow.billing.prescription-charges.allowed-price-codes=RX-DEFAULT",
                "--mediflow.features.outpatient-mvp.enabled=true", "--mediflow.billing.outpatient-publication.enabled=false",
                "--mediflow.billing.outbox.enabled=false", "--mediflow.billing.outbox.maintenance-enabled=false")) {
            beforeActivation.getBean(RabbitListenerEndpointRegistry.class).getListenerContainer("billingEvents").start();
            send("prescription.created", original);
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_charge_delivery", Integer.class)).isOne());
        }
        var storedInvoice = jdbc.queryForMap("SELECT event_id,payload FROM BILLING_EVENT_OUTBOX");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM billing_outpatient_publication_cohort", Integer.class)).isZero();
        assertThat(received("invoice.created", Duration.ofSeconds(2))).isNull();

        // The main context is already publication-enabled. Retrying the existing operation cannot
        // enroll it retroactively, and paying it must not silently release historical events.
        var redelivery = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(original);
        redelivery.put("eventId", UUID.randomUUID().toString());
        listener().start(); send("prescription.created", mapper.writeValueAsBytes(redelivery)); drainPrescription(2);
        var source = mapper.readTree(original).path("payload");
        var discovered = discover("PRESCRIPTION", uuid(source, "prescriptionId"), uuid(source, "patientId"), uuid(source, "careEpisodeId"));
        UUID request = UUID.fromString(discovered.path("paymentRequestId").asText());
        pay(request, "historical-rx", "2000.00");
        pay(request, "historical-rx", "2000.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM billing_outpatient_publication_cohort", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT publication_enabled FROM BILLING_EVENT_OUTBOX", Boolean.class)).hasSize(3).containsOnly(false);
        assertThat(jdbc.queryForObject("SELECT payload FROM BILLING_EVENT_OUTBOX WHERE event_id=?", String.class, storedInvoice.get("event_id")))
                .isEqualTo(storedInvoice.get("payload"));
        for (String key : new String[]{"invoice.created", "payment.completed", "financial.clearance.granted"}) {
            assertThat(received(key, Duration.ofSeconds(2))).isNull();
        }
    }

    private void assertMatchesBroker(String eventType, UUID paymentRequestId) throws Exception {
        Message message = received(eventType, Duration.ofSeconds(20));
        assertThat(message).as("%s delivered on the broker", eventType).isNotNull();
        var payload = mapper.readTree(message.getBody());
        assertThat(payload.path("eventType").asText()).isEqualTo(eventType);
        UUID eventId = UUID.fromString(payload.path("eventId").asText());
        byte[] stored = jdbc.queryForObject("SELECT payload FROM BILLING_EVENT_OUTBOX WHERE event_id=?", String.class, eventId)
                .getBytes(StandardCharsets.UTF_8);
        saveEvidence("stored-" + eventId, stored);
        assertThat(sha256(message.getBody())).as("broker bytes match stored outbox bytes exactly").isEqualTo(sha256(stored));
        assertThat(jdbc.queryForObject("SELECT outpatient_payment_request_id FROM BILLING_EVENT_OUTBOX WHERE event_id=?", UUID.class, eventId))
                .isEqualTo(paymentRequestId);
    }

    private void assertMatchesBrokerByEventType(String eventType, UUID expectedSourceIdInPayload) throws Exception {
        Message message = received(eventType, Duration.ofSeconds(20));
        assertThat(message).as("%s delivered on the broker", eventType).isNotNull();
        var payload = mapper.readTree(message.getBody());
        assertThat(payload.path("eventType").asText()).isEqualTo(eventType);
        assertThat(payload.path("payload").path("prescriptionId").asText()).isEqualTo(expectedSourceIdInPayload.toString());
        UUID eventId = UUID.fromString(payload.path("eventId").asText());
        byte[] stored = jdbc.queryForObject("SELECT payload FROM BILLING_EVENT_OUTBOX WHERE event_id=?", String.class, eventId)
                .getBytes(StandardCharsets.UTF_8);
        saveEvidence("stored-" + eventId, stored);
        assertThat(sha256(message.getBody())).isEqualTo(sha256(stored));
    }

    // Events can legitimately arrive out of the order a test happens to ask about (e.g. a random
    // eventId tie-break may let financial.clearance.granted reach the queue before payment.completed
    // for the same account; or two installments on one request each produce their own
    // payment.completed). Each eventType gets its own FIFO queue — never a single slot — so a
    // second same-type message is never silently overwritten/lost; callers that expect N events of
    // one type must call received() N times to drain them in arrival order.
    private final Map<String, java.util.Deque<Message>> buffered = new java.util.HashMap<>();

    private Message received(String eventType, Duration timeout) throws InterruptedException {
        var already = buffered.get(eventType);
        if (already != null && !already.isEmpty()) return already.poll();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Message candidate = externalRabbit.receive(OUTBOUND_QUEUE, 300);
            if (candidate == null) continue;
            saveEvidence("broker", candidate.getBody());
            try {
                String type = mapper.readTree(candidate.getBody()).path("eventType").asText();
                if (type.equals(eventType)) return candidate;
                buffered.computeIfAbsent(type, key -> new java.util.LinkedList<>()).add(candidate);
            } catch (Exception ignored) { /* not JSON we care about */ }
        }
        return null;
    }

    private JsonNode discover(String purpose, UUID sourceId, UUID patientId, UUID careEpisodeId) {
        var headers = authHeaders("ADMIN");
        String url = "/api/v1/billing/payment-requests/by-source?purpose=" + purpose
                + "&sourceId=" + sourceId + "&patientId=" + patientId + "&careEpisodeId=" + careEpisodeId;
        var response = http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).as("discovery response: %s", response.getBody()).isEqualTo(HttpStatus.OK);
        try {
            saveEvidence("discovery", response.getBody().getBytes(StandardCharsets.UTF_8));
            return mapper.readTree(response.getBody()).path("data");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void pay(UUID requestId, String idempotencyKey, String amount) {
        var headers = authHeaders("CASHIER");
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var body = new CompleteLedgerPaymentRequest(idempotencyKey, new BigDecimal(amount), "VND", PaymentMethod.CASH, null);
        var response = http.exchange("/api/v1/billing/payment-requests/" + requestId + "/payments", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode()).as("payment response: %s", response.getBody()).isEqualTo(HttpStatus.OK);
        saveEvidence("payment", response.getBody().getBytes(StandardCharsets.UTF_8));
    }

    // Memoized per test instance (JUnit5 creates a fresh instance per @Test method): a retried
    // payment must come from the SAME signed actor to be treated as a safe replay, exactly like a
    // real cashier re-submitting after a timeout, not a different operator's new transaction.
    private final Map<String, String> tokensByRole = new java.util.HashMap<>();

    private HttpHeaders authHeaders(String role) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(tokensByRole.computeIfAbsent(role, OutpatientPublicationRabbitIntegrationTest::token));
        return headers;
    }

    private static String token(String role) {
        Instant now = Instant.now();
        return Jwts.builder().subject(UUID.randomUUID().toString()).claim("type", "access").claim("role", role)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private static UUID uuid(JsonNode node, String field) { return UUID.fromString(node.path(field).asText()); }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private byte[] examFixture() throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream(
                "contracts/appointment-status-changed-v1/appointment.status.changed.v1.json")) {
            assertThat(in).isNotNull(); return in.readAllBytes();
        }
    }
    private byte[] labFixture() throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream("contracts/lab-request-v1/lab.request.created.v1.json")) {
            assertThat(in).isNotNull(); return in.readAllBytes();
        }
    }
    private void send(String routingKey, byte[] body) {
        externalRabbit.send("mediflow.events", routingKey, new Message(body, new MessageProperties()));
    }
    private void drain(long deliveries) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT count(*) FROM exam_charge_delivery", Long.class)
                        + jdbc.queryForObject("SELECT count(*) FROM lab_test_charge_delivery", Long.class)).isEqualTo(deliveries));
        listener().stop();
    }
    private void drainPrescription(long deliveries) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_charge_delivery", Long.class)).isEqualTo(deliveries));
        listener().stop();
    }
    private org.springframework.amqp.rabbit.listener.MessageListenerContainer listener() { return registry.getListenerContainer("billingEvents"); }
}
