package com.mediflow.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.notification.messaging.consumer.RegistrationNoticeConsumer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false",
        "mediflow.notification.outpatient-registration-consumer.enabled=true"})
class RegistrationNoticeIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer MQ = new RabbitMQContainer("rabbitmq:3.13-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl); r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword); r.add("spring.rabbitmq.host", MQ::getHost);
        r.add("spring.rabbitmq.port", MQ::getAmqpPort); r.add("spring.rabbitmq.username", MQ::getAdminUsername);
        r.add("spring.rabbitmq.password", MQ::getAdminPassword);
        r.add("mediflow.jwt.secret", () -> "registration-notice-test-secret-at-least-32-bytes");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired RegistrationNoticeConsumer consumer;
    @Autowired ObjectMapper mapper;
    @Autowired org.springframework.amqp.rabbit.core.RabbitAdmin admin;
    @Autowired org.springframework.amqp.rabbit.core.RabbitTemplate rabbit;
    @Autowired org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry listeners;

    @BeforeEach void reset() {
        listeners.getListenerContainers().forEach(c -> c.stop());
        admin.purgeQueue("notification.outpatient-registration.q"); admin.purgeQueue("notification.dlq");
        jdbc.execute("TRUNCATE outpatient_registration_notice_revision, outpatient_registration_notice_cursor, NOTIFICATION, PROCESSED_EVENT CASCADE");
    }

    @org.junit.jupiter.api.AfterEach void stop() { listeners.getListenerContainers().forEach(c -> c.stop()); }

    @Test void actualBrokerDeliversRawJsonOnceAndRoutesPoisonToDeadLetterQueue() throws Exception {
        listeners.getListenerContainers().forEach(c -> c.start());
        var properties = new org.springframework.amqp.core.MessageProperties(); properties.setContentType("application/json");
        byte[] waiting = fact(1, "AWAITING_PAYMENT");
        for (int i = 0; i < 2; i++) rabbit.send("mediflow.events", "outpatient.registration.changed",
                new org.springframework.amqp.core.Message(waiting, properties));
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(count("NOTIFICATION")).isOne());
        rabbit.send("mediflow.events", "outpatient.registration.changed",
                new org.springframework.amqp.core.Message("{}".getBytes(StandardCharsets.UTF_8), properties));
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(admin.getQueueInfo("notification.dlq").getMessageCount()).isOne());
        assertThat(count("NOTIFICATION")).isOne();
    }

    @Test void concurrentDifferentDeliveriesForOneRevisionCommitOneNotice() throws Exception {
        byte[] a = fact(1, "AWAITING_PAYMENT"), b = fact(1, "AWAITING_PAYMENT");
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var x = executor.submit(() -> { start.await(); consumer.receive(a); return true; });
            var y = executor.submit(() -> { start.await(); consumer.receive(b); return true; });
            start.countDown();
            assertThat(x.get(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(y.get(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
        assertThat(count("NOTIFICATION")).isOne(); assertThat(count("PROCESSED_EVENT")).isEqualTo(2);
    }

    @Test void receptionIsNotAConfirmedAppointmentAndReplaysDoNotSendTwice() throws Exception {
        byte[] waiting = fact(1, "AWAITING_PAYMENT");
        consumer.receive(waiting); consumer.receive(waiting);
        assertThat(jdbc.queryForObject("SELECT title FROM NOTIFICATION", String.class)).isEqualTo("Đăng ký đang chờ thanh toán");
        consumer.receive(fact(2, "CONFIRMED"));
        assertThat(jdbc.queryForList("SELECT title FROM NOTIFICATION", String.class))
                .containsExactlyInAnyOrder("Đăng ký đang chờ thanh toán", "Phiếu khám đã xác nhận");
        assertThat(count("NOTIFICATION_EVENT_OUTBOX")).isEqualTo(2);
    }

    @Test void newEventIdForSameRevisionIsNoOpButChangedBytesForSameEventAreRejected() throws Exception {
        byte[] first = fact(1, "AWAITING_PAYMENT"); consumer.receive(first);
        var root = (ObjectNode) mapper.readTree(first); root.put("eventId", UUID.randomUUID().toString());
        consumer.receive(mapper.writeValueAsBytes(root));
        assertThat(count("NOTIFICATION")).isOne();
        assertThatThrownBy(() -> consumer.receive((new String(first, StandardCharsets.UTF_8) + " ").getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class);
        assertThat(count("PROCESSED_EVENT")).isEqualTo(2);
    }

    @Test void cancellationBeforeCreateLeavesTombstoneAndSuppressesStaleReception() throws Exception {
        consumer.receive(fact(3, "CANCELLED")); consumer.receive(fact(1, "AWAITING_PAYMENT"));
        assertThat(jdbc.queryForList("SELECT title FROM NOTIFICATION", String.class)).containsExactly("Phiếu đăng ký đã hủy");
        assertThat(jdbc.queryForObject("SELECT revision FROM outpatient_registration_notice_cursor", Long.class)).isEqualTo(3);
    }

    @Test void missingRevisionRetriesWithoutCommittingInboxOrNotice() throws Exception {
        assertThatThrownBy(() -> consumer.receive(fact(2, "CONFIRMED")))
                .isInstanceOf(org.springframework.dao.TransientDataAccessException.class);
        assertThat(count("PROCESSED_EVENT")).isZero(); assertThat(count("NOTIFICATION")).isZero();
    }

    @Test void changedSnapshotForSameRevisionRollsBackDeliveryClaim() throws Exception {
        consumer.receive(fact(1, "AWAITING_PAYMENT"));
        var root = (ObjectNode) mapper.readTree(fact(1, "AWAITING_PAYMENT"));
        ((ObjectNode) root.path("payload")).put("patientId", UUID.randomUUID().toString());
        assertThatThrownBy(() -> consumer.receive(mapper.writeValueAsBytes(root)))
                .isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class);
        assertThat(count("PROCESSED_EVENT")).isOne(); assertThat(count("NOTIFICATION")).isOne();
    }

    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }

    // Synthetic wire used only for consumer behavior; producer-generated fixtures are checked separately.
    private byte[] fact(long revision, String status) throws Exception {
        var root = mapper.createObjectNode(); root.put("eventId", UUID.randomUUID().toString());
        root.put("eventType", "outpatient.registration.changed"); root.put("version", 1);
        root.put("producer", "clinical-service"); root.put("correlationId", "notice-test");
        root.put("occurredAt", "2026-10-27T01:00:00Z");
        var p = root.putObject("payload");
        for (String key : new String[]{"registrationId", "sourceId", "careEpisodeId"}) p.put(key, "00000000-0000-4000-8000-000000000001");
        for (String key : new String[]{"patientId", "doctorId", "departmentId", "sessionId", "roomId", "reservationId"})
            p.put(key, UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString());
        p.put("registrationRevision", revision); p.put("sessionDate", "2026-10-27");
        p.put("startTime", "07:00"); p.put("endTime", "11:00"); p.put("visitType", "NEW_VISIT");
        p.put("reservationStatus", status.equals("CONFIRMED") ? "CONFIRMED" : status.equals("CANCELLED") ? "CANCELLED" : "HELD");
        p.put("status", status); p.put("checkedInAt", "2026-10-27T01:00:00Z");
        if (status.equals("CONFIRMED")) p.put("queueNumber", 1); else p.putNull("queueNumber");
        p.put("reservationExpiresAt", "2026-10-27T01:15:00Z"); p.put("priceCode", "OUTPATIENT_EXAM");
        p.put("sourceType", "EXAM"); p.put("careEpisodeType", "OUTPATIENT_VISIT");
        p.put("changedAt", "2026-10-27T01:00:00Z");
        if (status.equals("CANCELLED")) {
            var c = p.putObject("cancellation"); c.put("actorAccountId", UUID.randomUUID().toString());
            c.putNull("actorStaffId"); c.put("reason", "Người bệnh đổi kế hoạch"); c.put("cancelledAt", "2026-10-27T01:00:00Z");
        } else p.putNull("cancellation");
        return mapper.writeValueAsBytes(root);
    }
}
