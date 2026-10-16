package com.mediflow.billing;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
 * L3 remaining resilience proof (HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md §5, L3, the two
 * items originally left honestly unchecked): unroutable/retry-exhaustion/quarantine, admin replay
 * of only an eligible quarantined row, expired-lease/stale-owner reclaim, and same-account
 * same-timestamp ordering where a quarantined predecessor is never skipped. Real Postgres + real
 * RabbitMQ + the actual {@code BillingOutboxDispatcher} throughout; each eligible row is seeded
 * with the full realistic state (charge/payment/cohort) the V23 payload fence requires — a
 * hand-written payload alone would be rejected at insert.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "mediflow.jwt.secret=billing-outpatient-resilience-integration-secret-32-bytes-min",
        "mediflow.billing.ledger.enabled=true",
        "mediflow.billing.outbox.enabled=true",
        "mediflow.billing.outbox.metrics-enabled=false",
        "mediflow.billing.outbox.maintenance-enabled=true",
        "mediflow.billing.outbox.dispatch-delay-ms=200",
        "mediflow.billing.outbox.dispatch-initial-delay-ms=200",
        "mediflow.billing.outbox.confirm-timeout-ms=3000",
        "mediflow.billing.outbox.lease-seconds=3",
        "mediflow.billing.outbox.retry.initial-backoff-ms=150",
        "mediflow.billing.outbox.retry.max-backoff-ms=400",
        "mediflow.billing.outbox.retry.max-attempts=2",
        "spring.rabbitmq.publisher-confirm-type=correlated",
        "spring.rabbitmq.publisher-returns=true"})
class OutpatientPublicationDispatcherResilienceRabbitIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer MQ = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl); r.add("spring.datasource.username", PG::getUsername); r.add("spring.datasource.password", PG::getPassword);
        r.add("spring.rabbitmq.host", MQ::getHost); r.add("spring.rabbitmq.port", MQ::getAmqpPort);
        r.add("spring.rabbitmq.username", MQ::getAdminUsername); r.add("spring.rabbitmq.password", MQ::getAdminPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired TestRestTemplate http;
    private static final String JWT_SECRET = "billing-outpatient-resilience-integration-secret-32-bytes-min";

    // A dedicated connection per test, standing in for another microservice — never the app's own
    // internal RabbitTemplate (see OutpatientPublicationRabbitIntegrationTest for why).
    private CachingConnectionFactory externalConnection;
    private RabbitAdmin externalAdmin;
    private RabbitTemplate externalRabbit;

    private void openExternalConnection() {
        externalConnection = new CachingConnectionFactory(MQ.getHost(), MQ.getAmqpPort());
        externalConnection.setUsername(MQ.getAdminUsername());
        externalConnection.setPassword(MQ.getAdminPassword());
        externalAdmin = new RabbitAdmin(externalConnection);
        externalRabbit = new RabbitTemplate(externalConnection);
        externalAdmin.declareExchange(new TopicExchange("mediflow.events", true, false));
    }

    @Test void unroutableEligibleEvent_isRetriedThenQuarantinedWithoutEverPublishing() throws Exception {
        openExternalConnection();
        try {
            UUID eventId = UUID.randomUUID();
            seedEligiblePaymentCompleted(eventId, null);
            // Deliberately no queue bound to payment.completed on this exchange: mandatory=true
            // means the broker returns it instead of silently dropping it, and the dispatcher's
            // own confirm/return check must treat that exactly like any other publish failure.
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                var row = jdbc.queryForMap("SELECT attempts,last_error,published_at,quarantined_at FROM BILLING_EVENT_OUTBOX WHERE event_id=?", eventId);
                assertThat(row.get("quarantined_at")).as("quarantined after exhausting retries").isNotNull();
                assertThat((Integer) row.get("attempts")).isGreaterThanOrEqualTo(2);
                assertThat(row.get("published_at")).isNull();
                assertThat((String) row.get("last_error")).containsIgnoringCase("return");
            });
        } finally {
            externalConnection.destroy();
        }
    }

    @Test void quarantinedRow_adminReplayPublishesOnceThenRefusesASecondReplay() throws Exception {
        openExternalConnection();
        String queue = null;
        try {
            UUID eventId = UUID.randomUUID();
            seedEligiblePaymentCompleted(eventId, null);
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject("SELECT quarantined_at IS NOT NULL FROM BILLING_EVENT_OUTBOX WHERE event_id=?", Boolean.class, eventId))
                            .isTrue());

            // Operator fixes the routing problem, then replays — same eventId/bytes, exactly once.
            queue = "resilience-replay-" + UUID.randomUUID();
            externalAdmin.declareQueue(new Queue(queue, true, false, false));
            externalAdmin.declareBinding(new Binding(queue, Binding.DestinationType.QUEUE, "mediflow.events", "payment.completed", null));
            byte[] originalBytes = jdbc.queryForObject("SELECT payload FROM BILLING_EVENT_OUTBOX WHERE event_id=?", String.class, eventId)
                    .getBytes(StandardCharsets.UTF_8);

            var replayResponse = replay(eventId);
            assertThat(replayResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            Message delivered = externalRabbit.receive(queue, 20000);
            assertThat(delivered).as("replayed row reaches the broker once routable").isNotNull();
            assertThat(delivered.getBody()).isEqualTo(originalBytes);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject("SELECT published_at IS NOT NULL AND quarantined_at IS NULL FROM BILLING_EVENT_OUTBOX WHERE event_id=?", Boolean.class, eventId))
                            .isTrue());

            // Already published: a second replay must refuse (only an eligible quarantined row may replay).
            var secondReplay = replay(eventId);
            assertThat(secondReplay.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(externalRabbit.receive(queue, 2000)).as("no duplicate financial-event republish").isNull();
        } finally {
            if (queue != null) externalAdmin.deleteQueue(queue);
            externalConnection.destroy();
        }
    }

    @Test void expiredLeaseFromAStaleOwner_isReclaimedAndPublishedExactlyOnce() throws Exception {
        openExternalConnection();
        String queue = null;
        try {
            UUID eventId = UUID.randomUUID();
            seedEligiblePaymentCompleted(eventId, null);
            // Simulate a dispatcher instance that claimed the row and then crashed before publishing:
            // lease-seconds=3, so a lock this old is immediately reclaimable by the real dispatcher.
            jdbc.update("UPDATE BILLING_EVENT_OUTBOX SET locked_at=now()-interval '30 seconds', locked_by='stale-crashed-owner' WHERE event_id=?", eventId);

            queue = "resilience-stale-lease-" + UUID.randomUUID();
            externalAdmin.declareQueue(new Queue(queue, true, false, false));
            externalAdmin.declareBinding(new Binding(queue, Binding.DestinationType.QUEUE, "mediflow.events", "payment.completed", null));

            Message delivered = externalRabbit.receive(queue, 20000);
            assertThat(delivered).as("reclaimed from the stale owner and published").isNotNull();
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject("SELECT published_at IS NOT NULL FROM BILLING_EVENT_OUTBOX WHERE event_id=?", Boolean.class, eventId))
                            .isTrue());
            assertThat(externalRabbit.receive(queue, 2000)).as("exactly one delivery, not reclaimed twice").isNull();
            assertThat(jdbc.queryForObject("SELECT locked_by FROM BILLING_EVENT_OUTBOX WHERE event_id=?", String.class, eventId)).isNull();
        } finally {
            if (queue != null) externalAdmin.deleteQueue(queue);
            externalConnection.destroy();
        }
    }

    @Test void sameAccountSameTimestampOrdering_quarantinedPredecessorBlocksSuccessorUntilResolved() throws Exception {
        openExternalConnection();
        String queue = null;
        try {
            UUID account = UUID.randomUUID();
            // Fixed, deterministic UUIDs so the tie-break on equal created_at is known up front:
            // ...0001 sorts before ...0002, so A must be attempted (and here, quarantined) first.
            UUID eventA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
            UUID eventB = UUID.fromString("a0000000-0000-4000-8000-000000000002");
            Instant sameInstant = Instant.now();
            seedEligiblePaymentCompleted(eventA, account, sameInstant);
            seedEligiblePaymentCompleted(eventB, account, sameInstant);

            // A is unroutable (no binding yet) and must exhaust retries into quarantine. While A sits
            // quarantined, B — same account, later event_id, but NOT yet attempted — must stay
            // untouched: a quarantined predecessor is never skipped just to let a newer row through.
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject("SELECT quarantined_at IS NOT NULL FROM BILLING_EVENT_OUTBOX WHERE event_id=?", Boolean.class, eventA))
                            .isTrue());
            await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
                var b = jdbc.queryForMap("SELECT attempts,published_at,quarantined_at FROM BILLING_EVENT_OUTBOX WHERE event_id=?", eventB);
                assertThat(b.get("published_at")).as("B never published while A blocks it").isNull();
                assertThat(b.get("quarantined_at")).as("B never even attempted while A blocks it").isNull();
                assertThat((Integer) b.get("attempts")).isZero();
            });

            // Operator resolves A (bind + replay); B must then unblock and publish right after, in order.
            queue = "resilience-ordering-" + UUID.randomUUID();
            externalAdmin.declareQueue(new Queue(queue, true, false, false));
            externalAdmin.declareBinding(new Binding(queue, Binding.DestinationType.QUEUE, "mediflow.events", "payment.completed", null));
            assertThat(replay(eventA).getStatusCode()).isEqualTo(HttpStatus.OK);

            Message first = externalRabbit.receive(queue, 20000);
            Message second = externalRabbit.receive(queue, 20000);
            assertThat(first).as("A delivered first").isNotNull();
            assertThat(second).as("B delivered after A unblocks it").isNotNull();
            assertThat(mapper.readTree(first.getBody()).path("eventId").asText()).isEqualTo(eventA.toString());
            assertThat(mapper.readTree(second.getBody()).path("eventId").asText()).isEqualTo(eventB.toString());
            var order = jdbc.queryForList(
                    "SELECT event_id FROM BILLING_EVENT_OUTBOX WHERE aggregate_id=? ORDER BY published_at", UUID.class, account);
            assertThat(order).containsExactly(eventA, eventB);
        } finally {
            if (queue != null) externalAdmin.deleteQueue(queue);
            externalConnection.destroy();
        }
    }

    private org.springframework.http.ResponseEntity<String> replay(UUID eventId) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token("ADMIN"));
        return http.exchange("/api/v1/billing/admin/outbox/" + eventId + "/replay", HttpMethod.POST,
                new HttpEntity<>(headers), String.class);
    }

    private static String token(String role) {
        Instant now = Instant.now();
        return Jwts.builder().subject(UUID.randomUUID().toString()).claim("type", "access").claim("role", role)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    /** Same realistic seed as OutpatientPublicationProcessRecoveryIT's: a brand-new enrolled EXAM
     * request, fully consistent with what the V23 payload fence requires at insert time. */
    private UUID seedEligiblePaymentCompleted(UUID eventId, Instant createdAt) {
        return seedEligiblePaymentCompleted(eventId, UUID.randomUUID(), createdAt);
    }
    private UUID seedEligiblePaymentCompleted(UUID eventId, UUID account, Instant createdAt) {
        UUID request = UUID.randomUUID(), invoice = UUID.randomUUID();
        UUID transaction = UUID.randomUUID(), charge = UUID.randomUUID();
        // EXAM ties care_episode_id to the exact appointment (account-level, fixed once opened).
        // A second seed call reusing the same account must reuse its real patient/department/
        // episode too — otherwise this seed's own payload would conflict with itself, not just
        // with a sibling row.
        var existingAccount = jdbc.queryForList("SELECT patient_id,department_id,care_episode_id FROM BILLING_ACCOUNT WHERE account_id=?", account);
        UUID patient, department, appointment;
        if (existingAccount.isEmpty()) {
            patient = UUID.randomUUID(); department = UUID.randomUUID(); appointment = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,currency,opened_at)
                    VALUES (?,?,?,'OUTPATIENT_VISIT',?,'VND',now())
                    """, account, patient, department, appointment);
        } else {
            var row = existingAccount.getFirst();
            patient = (UUID) row.get("patient_id"); department = (UUID) row.get("department_id"); appointment = (UUID) row.get("care_episode_id");
        }
        jdbc.update("INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,status,requested_amount,currency) "
                + "VALUES (?,?,?,'EXAM','PAID',?,'VND')", request, invoice, account, new BigDecimal("100000.00"));
        jdbc.update("INSERT INTO PAYMENT_REQUEST_TARGET(payment_request_id,appointment_id) VALUES (?,?)", request, appointment);
        // uq_charge_source(source_type,source_id,price_code) is PK-like: a second seed on the same
        // account/appointment must reuse the existing EXAM charge row, not insert a duplicate one.
        var existingCharge = jdbc.queryForList("SELECT charge_id FROM CHARGE WHERE source_type='EXAM' AND source_id=? AND price_code='EXAM'", appointment);
        if (existingCharge.isEmpty()) {
            jdbc.update("""
                    INSERT INTO CHARGE(charge_id,account_id,patient_id,department_id,source_type,source_id,price_code,
                        description,quantity,unit_amount,gross_amount,incurred_at) VALUES (?,?,?,?,'EXAM',?,'EXAM','exam',1,?,?,now())
                    """, charge, account, patient, department, appointment, new BigDecimal("100000.00"), new BigDecimal("100000.00"));
        } else {
            charge = (UUID) existingCharge.getFirst().get("charge_id");
        }
        jdbc.update("INSERT INTO PAYMENT_REQUEST_CHARGE(payment_request_id,charge_id,requested_amount) VALUES (?,?,?)",
                request, charge, new BigDecimal("100000.00"));
        jdbc.update("""
                INSERT INTO PAYMENT_TRANSACTION(transaction_id,account_id,payment_request_id,transaction_type,classification,
                    status,amount,currency,payment_method,idempotency_key,completed_at)
                VALUES (?,?,?,'PAYMENT','SERVICE_PAYMENT','COMPLETED',?,'VND','CASH',?,now())
                """, transaction, account, request, new BigDecimal("100000.00"), UUID.randomUUID().toString());
        jdbc.update("""
                INSERT INTO billing_outpatient_publication_cohort(payment_request_id,account_id,purpose,source_id,acceptance_reference)
                VALUES (?,?,'EXAM',?,'L3-RESILIENCE-RUN')
                """, request, account, appointment);
        String payload = """
                {"eventId":"%s","eventType":"payment.completed","version":1,"occurredAt":"2026-10-16T08:00:00Z",\
                "correlationId":"resilience-test","producer":"billing-service","payload":{"transactionId":"%s",\
                "invoiceId":"%s","paymentRequestId":"%s","accountId":"%s","patientId":"%s","departmentId":"%s",\
                "careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"%s","classification":"SERVICE_PAYMENT",\
                "totalAmount":100000.00,"currency":"VND","paymentMethod":"CASH","completedAt":"2026-10-16T08:00:00Z",\
                "prescriptionId":null,"labTestIds":[]}}
                """.formatted(eventId, transaction, invoice, request, account, patient, department, appointment).strip();
        if (createdAt != null) {
            jdbc.update("""
                    INSERT INTO BILLING_EVENT_OUTBOX(event_id,routing_key,aggregate_id,payload,publication_enabled,contract_version,outpatient_payment_request_id,created_at,available_at)
                    VALUES (?,'payment.completed',?,?,TRUE,1,?,?,?)
                    """, eventId, account, payload, request, java.sql.Timestamp.from(createdAt), java.sql.Timestamp.from(createdAt));
        } else {
            jdbc.update("""
                    INSERT INTO BILLING_EVENT_OUTBOX(event_id,routing_key,aggregate_id,payload,publication_enabled,contract_version,outpatient_payment_request_id)
                    VALUES (?,'payment.completed',?,?,TRUE,1,?)
                    """, eventId, account, payload, request);
        }
        return account;
    }
}
