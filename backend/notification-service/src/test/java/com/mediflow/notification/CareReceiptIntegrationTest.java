package com.mediflow.notification;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.notification.messaging.consumer.CarePaymentReceiptWireHandler;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CareReceiptIntegrationTest {
    private static final String SECRET = "notification-care-integration-secret-at-least-32-bytes";
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
        registry.add("mediflow.notification.care-v1.enabled", () -> true);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired CarePaymentReceiptWireHandler handler;
    @Autowired ObjectMapper mapper;
    @Autowired TestRestTemplate http;

    @BeforeEach void reset() { jdbc.execute("TRUNCATE NOTIFICATION, PROCESSED_EVENT CASCADE"); }

    @Test void depositDeliveryIsPrivateInAppNotEarnedRevenueOrFullyPaidInvoice() throws Exception {
        handler.receive("payment.completed", fixture("deposit"));
        var row = jdbc.queryForMap("SELECT * FROM NOTIFICATION");
        assertThat(row.get("template_key")).isEqualTo("ADMISSION_DEPOSIT_RECEIPT");
        assertThat(row.get("channel")).isEqualTo("IN_APP");
        assertThat(row.get("status")).isEqualTo("SENT");
        assertThat(row.get("sensitivity")).isEqualTo("PRIVATE_IN_APP_ONLY");
        assertThat(row.get("recipient_address")).isNull();
        assertThat(row.get("content").toString()).contains("chưa phải số viện phí quyết toán");
        assertThat(jdbc.queryForObject("SELECT publication_enabled FROM NOTIFICATION_EVENT_OUTBOX", Boolean.class)).isFalse();
    }

    @Test void concurrentRedeliveryCommitsOneIntentAndOneSentFact() throws Exception {
        byte[] bytes = fixture("service");
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> { start.await(); handler.receive("payment.completed", bytes); return true; });
            var b = workers.submit(() -> { start.await(); handler.receive("payment.completed", bytes); return true; });
            start.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsOnly(true);
        }
        assertThat(count("NOTIFICATION")).isEqualTo(1);
        assertThat(count("PROCESSED_EVENT")).isEqualTo(1);
        assertThat(count("NOTIFICATION_EVENT_OUTBOX")).isEqualTo(1);
    }

    @Test void sameEventWithChangedPayloadConflictsWithoutReplacingHistory() throws Exception {
        byte[] original = fixture("service");
        handler.receive("payment.completed", original);
        var changed = (ObjectNode) mapper.readTree(original);
        ((ObjectNode) changed.path("payload")).put("totalAmount", 50);
        assertThatThrownBy(() -> handler.receive("payment.completed", mapper.writeValueAsBytes(changed)))
                .isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("Conflicting");
        assertThat(count("NOTIFICATION")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT content FROM NOTIFICATION", String.class)).contains("100");
    }

    @Test void deliveryOutboxFailureRollsBackInboxAndHistoryThenRedeliveryRecovers() throws Exception {
        jdbc.execute("CREATE FUNCTION reject_care_delivery() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test rollback'; END $$");
        jdbc.execute("CREATE TRIGGER reject_care_delivery BEFORE INSERT ON NOTIFICATION_EVENT_OUTBOX FOR EACH ROW EXECUTE FUNCTION reject_care_delivery()");
        try {
            assertThatThrownBy(() -> handler.receive("payment.completed", fixture("service"))).isInstanceOf(RuntimeException.class);
            assertThat(count("PROCESSED_EVENT")).isZero();
            assertThat(count("NOTIFICATION")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_care_delivery ON NOTIFICATION_EVENT_OUTBOX");
            jdbc.execute("DROP FUNCTION reject_care_delivery()");
        }
        handler.receive("payment.completed", fixture("service"));
        assertThat(count("NOTIFICATION")).isEqualTo(1);
    }

    @Test void patientReadsOwnDeliveredReceiptOnlyBySignedPatientClaimNotAccountOrHeader() throws Exception {
        handler.receive("payment.completed", fixture("service"));
        UUID patient = UUID.fromString("00000000-0000-0000-0000-000000000002");
        String path = "/api/v1/notifications/" + jdbc.queryForObject("SELECT notification_id FROM NOTIFICATION", UUID.class);
        assertThat(http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(UUID.randomUUID(), patient)), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        var wrong = headers(patient, UUID.randomUUID());
        wrong.set("X-Patient-Id", patient.toString());
        assertThat(http.exchange(path, HttpMethod.GET, new HttpEntity<>(wrong), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(patient, null)), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    private HttpHeaders headers(UUID account, UUID patient) {
        var token = Jwts.builder().subject(account.toString()).claim("role", "PATIENT").claim("type", "access").issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(60)));
        if (patient != null) token.claim("patientId", patient.toString());
        var headers = new HttpHeaders();
        headers.setBearerAuth(token.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact());
        return headers;
    }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    private static byte[] fixture(String name) throws Exception {
        return Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/payment-" + name + ".json"));
    }
}
