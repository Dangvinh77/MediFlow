package com.mediflow.notification;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.notification.messaging.consumer.PrescriptionNoticeWireHandler;
import com.mediflow.notification.messaging.consumer.PrescriptionPaymentNoticeWireHandler;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
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

/** Actual queue/PG acceptance; never substitutes a mocked port for persistence or transport. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false",
        "mediflow.notification.care-v1.enabled=true",
        "mediflow.notification.outpatient-prescription-consumer.enabled=true"})
class PrescriptionNoticeRabbitIntegrationTest {
    private static final String SECRET = "notification-outpatient-rx-integration-secret-at-least-32-bytes";
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer MQ = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl); r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword); r.add("spring.rabbitmq.host", MQ::getHost);
        r.add("spring.rabbitmq.port", MQ::getAmqpPort); r.add("spring.rabbitmq.username", MQ::getAdminUsername);
        r.add("spring.rabbitmq.password", MQ::getAdminPassword); r.add("mediflow.jwt.secret", () -> SECRET);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PrescriptionNoticeWireHandler handler;
    @Autowired PrescriptionPaymentNoticeWireHandler paymentRequests;
    @Autowired ObjectMapper mapper;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitListenerEndpointRegistry registry;
    @Autowired TestRestTemplate http;

    @BeforeEach void reset() {
        registry.getListenerContainers().forEach(container -> container.stop());
        admin.purgeQueue("notification.q"); admin.purgeQueue("notification.dlq");
        jdbc.execute("TRUNCATE NOTIFICATION, PROCESSED_EVENT, care_notification_source CASCADE");
    }

    @ParameterizedTest @ValueSource(strings = {"created", "filled", "cancelled", "expired", "dispense.failed"})
    void originalProducerFactsCommitOnePrivateNoticeAndHeldSentFact(String kind) throws Exception {
        registry.getListenerContainers().forEach(container -> container.start());
        try {
            rabbit.send("mediflow.events", "prescription." + kind,
                    new Message(PrescriptionNoticeContractTest.fixture(kind), new MessageProperties()));
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(count("NOTIFICATION")).isOne());
        } finally {
            registry.getListenerContainers().forEach(container -> container.stop());
        }
        var row = jdbc.queryForMap("SELECT * FROM NOTIFICATION");
        assertThat(row.get("channel")).isEqualTo("IN_APP");
        assertThat(row.get("status")).isEqualTo("SENT");
        assertThat(row.get("sensitivity")).isEqualTo("PRIVATE_IN_APP_ONLY");
        assertThat(row.get("recipient_address")).isNull();
        assertThat(count("PROCESSED_EVENT")).isOne();
        assertThat(count("care_notification_source")).isOne();
        assertThat(count("NOTIFICATION_EVENT_OUTBOX")).isOne();
        assertThat(jdbc.queryForObject("SELECT publication_enabled FROM NOTIFICATION_EVENT_OUTBOX", Boolean.class)).isFalse();
    }

    @Test void concurrentNewDeliveryIdsForSameOperationCommitOneNotice() throws Exception {
        byte[] original = PrescriptionNoticeContractTest.fixture("filled");
        var changed = (ObjectNode) mapper.readTree(original);
        changed.put("eventId", UUID.randomUUID().toString());
        byte[] redelivery = mapper.writeValueAsBytes(changed);
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> { start.await(); handler.receive("prescription.filled", original); return true; });
            var b = workers.submit(() -> { start.await(); handler.receive("prescription.filled", redelivery); return true; });
            start.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsOnly(true);
        }
        assertThat(count("PROCESSED_EVENT")).isEqualTo(2);
        assertThat(count("care_notification_source")).isOne();
        assertThat(count("NOTIFICATION")).isOne();
        assertThat(count("NOTIFICATION_EVENT_OUTBOX")).isOne();
    }

    @Test void sourceConflictRollsBackNewInboxAndPreservesOriginalPatient() throws Exception {
        byte[] original = PrescriptionNoticeContractTest.fixture("filled");
        handler.receive("prescription.filled", original);
        var changed = (ObjectNode) mapper.readTree(original);
        changed.put("eventId", UUID.randomUUID().toString());
        ((ObjectNode) changed.path("payload")).put("patientId", UUID.randomUUID().toString());
        assertThatThrownBy(() -> handler.receive("prescription.filled", mapper.writeValueAsBytes(changed)))
                .isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class);
        assertThat(count("PROCESSED_EVENT")).isOne();
        assertThat(count("NOTIFICATION")).isOne();
        assertThat(jdbc.queryForObject("SELECT patient_id FROM NOTIFICATION", UUID.class))
                .isEqualTo(UUID.fromString("10000000-0000-4000-8000-000000000002"));
    }

    @Test void outboxFaultRollsBackDeliverySourceAndHistoryThenExactBytesRecover() throws Exception {
        jdbc.execute("CREATE FUNCTION reject_rx_notice() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test rollback'; END $$");
        jdbc.execute("CREATE TRIGGER reject_rx_notice BEFORE INSERT ON NOTIFICATION_EVENT_OUTBOX FOR EACH ROW EXECUTE FUNCTION reject_rx_notice()");
        try {
            assertThatThrownBy(() -> handler.receive("prescription.filled", PrescriptionNoticeContractTest.fixture("filled")))
                    .isInstanceOf(RuntimeException.class);
            assertThat(count("PROCESSED_EVENT")).isZero();
            assertThat(count("care_notification_source")).isZero();
            assertThat(count("NOTIFICATION")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_rx_notice ON NOTIFICATION_EVENT_OUTBOX");
            jdbc.execute("DROP FUNCTION reject_rx_notice()");
        }
        handler.receive("prescription.filled", PrescriptionNoticeContractTest.fixture("filled"));
        assertThat(count("NOTIFICATION")).isOne();
    }

    @Test void realQueueReceivesOriginalBytesAndDeadLettersWrongProducerWithoutEffect() throws Exception {
        registry.getListenerContainers().forEach(container -> container.start());
        try {
            rabbit.send("mediflow.events", "prescription.filled", new Message(PrescriptionNoticeContractTest.fixture("filled"), new MessageProperties()));
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(count("NOTIFICATION")).isOne());
            var invalid = (ObjectNode) mapper.readTree(PrescriptionNoticeContractTest.fixture("filled"));
            invalid.put("producer", "report-service");
            rabbit.send("mediflow.events", "prescription.filled", new Message(mapper.writeValueAsBytes(invalid), new MessageProperties()));
            Message rejected = rabbit.receive("notification.dlq", 15000);
            assertThat(rejected).isNotNull();
            assertThat(count("NOTIFICATION")).isOne();
            assertThat(count("PROCESSED_EVENT")).isOne();
            rabbit.send("mediflow.events", "invoice.created", new Message(PrescriptionNoticeContractTest.paymentRequestFixture(), new MessageProperties()));
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(count("NOTIFICATION")).isEqualTo(2));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM NOTIFICATION WHERE template_key='PRESCRIPTION_PAYMENT_REQUEST'", Long.class)).isOne();
        } finally {
            registry.getListenerContainers().forEach(container -> container.stop());
        }
    }

    @Test void billingRequestNewDeliveryIdIsDeduplicatedAndChangedPatientConflicts() throws Exception {
        byte[] original = PrescriptionNoticeContractTest.paymentRequestFixture();
        paymentRequests.receive("invoice.created", original);
        var retry = (ObjectNode) mapper.readTree(original);
        retry.put("eventId", UUID.randomUUID().toString());
        paymentRequests.receive("invoice.created", mapper.writeValueAsBytes(retry));
        assertThat(count("PROCESSED_EVENT")).isEqualTo(2);
        assertThat(count("NOTIFICATION")).isOne();
        retry.put("eventId", UUID.randomUUID().toString());
        ((ObjectNode) retry.path("payload")).put("patientId", UUID.randomUUID().toString());
        assertThatThrownBy(() -> paymentRequests.receive("invoice.created", mapper.writeValueAsBytes(retry)))
                .isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class);
        assertThat(count("PROCESSED_EVENT")).isEqualTo(2);
        assertThat(count("care_notification_source")).isOne();
        assertThat(count("NOTIFICATION_EVENT_OUTBOX")).isOne();
    }

    @Test void paymentReceiptByteConflictDeadLettersOriginalVariantWithoutDuplicateEffects() throws Exception {
        byte[] original = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                "../billing-service/src/test/resources/contracts/ledger-v1/payment-service.json"));
        byte[] variant = (" \n" + new String(original, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
        registry.getListenerContainers().forEach(container -> container.start());
        try {
            rabbit.send("mediflow.events", "payment.completed", new Message(original, new MessageProperties()));
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(count("NOTIFICATION")).isOne());
            rabbit.send("mediflow.events", "payment.completed", new Message(original, new MessageProperties()));
            rabbit.send("mediflow.events", "payment.completed", new Message(variant, new MessageProperties()));
            Message rejected = rabbit.receive("notification.dlq", 15000);
            assertThat(rejected).isNotNull();
            assertThat(rejected.getBody()).isEqualTo(variant);
            assertThat(rejected.getMessageProperties().getXDeathHeader()).hasSize(1);
            assertThat(rejected.getMessageProperties().getXDeathHeader().getFirst().get("count")).isEqualTo(1L);
        } finally {
            registry.getListenerContainers().forEach(container -> container.stop());
        }
        assertThat(count("NOTIFICATION")).isOne();
        assertThat(count("PROCESSED_EVENT")).isOne();
        assertThat(count("NOTIFICATION_EVENT_OUTBOX")).isOne();
        assertThat(jdbc.queryForObject("SELECT receipt_raw_hash FROM PROCESSED_EVENT", String.class)).isEqualTo(
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(original)));
    }

    @Test void historyUsesSignedPatientMappingAndDoesNotTrustAccountOrHeader() throws Exception {
        handler.receive("prescription.filled", PrescriptionNoticeContractTest.fixture("filled"));
        UUID patient = UUID.fromString("10000000-0000-4000-8000-000000000002");
        String path = "/api/v1/notifications/" + jdbc.queryForObject("SELECT notification_id FROM NOTIFICATION", UUID.class);
        assertThat(http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(UUID.randomUUID(), patient)), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        var wrong = headers(patient, UUID.randomUUID()); wrong.set("X-Patient-Id", patient.toString());
        assertThat(http.exchange(path, HttpMethod.GET, new HttpEntity<>(wrong), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private HttpHeaders headers(UUID account, UUID patient) {
        String token = Jwts.builder().subject(account.toString()).claim("role", "PATIENT").claim("type", "access")
                .claim("patientId", patient.toString()).issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        var headers = new HttpHeaders(); headers.setBearerAuth(token); return headers;
    }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }
}
