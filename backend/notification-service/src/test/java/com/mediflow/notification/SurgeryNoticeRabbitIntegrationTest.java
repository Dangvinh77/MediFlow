package com.mediflow.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.UUID;
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(properties = {"eureka.client.enabled=false", "mediflow.jwt.secret=notification-surgery-integration-secret-at-least-32-bytes",
        "mediflow.notification.care-v1.enabled=true", "mediflow.notification.surgery-consumer.enabled=true",
        "mediflow.notification.surgery-payment-request-consumer.enabled=true",
        "mediflow.notification.refund-consumer.enabled=true",
        "spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.publisher-confirm-type=simple"})
class SurgeryNoticeRabbitIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container @ServiceConnection static final RabbitMQContainer MQ = new RabbitMQContainer("rabbitmq:3.13-alpine");
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitListenerEndpointRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String QUEUE = "notification.surgery-v1.q", DLQ = "notification.surgery-v1.dlq";
    private static final String PAYMENT_QUEUE = "notification.surgery-payment-requests-v1.q", PAYMENT_DLQ = "notification.surgery-payment-requests-v1.dlq";
    @BeforeEach void reset() {
        refundListener().stop(); admin.purgeQueue("notification.refunds-v1.q"); admin.purgeQueue("notification.refunds-v1.dlq");
        listener().stop(); paymentListener().stop(); admin.purgeQueue(QUEUE); admin.purgeQueue(DLQ); admin.purgeQueue(PAYMENT_QUEUE); admin.purgeQueue(PAYMENT_DLQ);
        jdbc.execute("TRUNCATE NOTIFICATION,PROCESSED_EVENT,surgery_notice_case,care_notification_source CASCADE");
        ((org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer)listener()).setConcurrentConsumers(2);
        listener().start(); paymentListener().start();
        refundListener().start();
    }
    @AfterEach void stop() { listener().stop(); paymentListener().stop(); refundListener().stop(); }

    @Test void consume_actualReady_isPrivateProvisionalAndDuplicateSafe() throws Exception {
        byte[] body = fixture("surgery.ready"); send("surgery.ready", body); send("surgery.ready", body); drain(1);
        var root = tree(body); root.put("eventId", UUID.randomUUID().toString());
        listener().start(); send("surgery.ready", mapper.writeValueAsBytes(root)); drain(2);
        var notice = jdbc.queryForMap("SELECT * FROM NOTIFICATION");
        assertThat(notice.get("template_key")).isEqualTo("SURGERY_READY");
        assertThat(notice.get("channel")).isEqualTo("IN_APP"); assertThat(notice.get("recipient_address")).isNull();
        assertThat(notice.get("sensitivity")).isEqualTo("PRIVATE_IN_APP_ONLY");
        assertThat(notice.get("content").toString()).contains("chưa phải lịch đặt phòng đã xác nhận");
        assertThat(count("NOTIFICATION_EVENT_OUTBOX")).isOne();
        assertThat(jdbc.queryForObject("SELECT publication_enabled FROM NOTIFICATION_EVENT_OUTBOX", Boolean.class)).isFalse();
    }
    @Test void consume_invalidationBeforeReady_persistsTombstoneAndSuppressedHistoryAfterRestart() throws Exception {
        send("surgery.readiness.invalidated", fixture("surgery.readiness.invalidated")); drain(1);
        listener().start(); send("surgery.ready", fixture("surgery.ready")); drain(2);
        assertThat(jdbc.queryForObject("SELECT status FROM NOTIFICATION", String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT failure_reason FROM NOTIFICATION", String.class)).isEqualTo("SURGERY_REMINDER_SUPPRESSED");
        assertThat(count("NOTIFICATION_EVENT_OUTBOX")).isZero();
    }
    @Test void consume_invalidationAfterReady_keepsOriginalSentHistoryButSuppressesExactSnapshot() throws Exception {
        send("surgery.ready", fixture("surgery.ready")); drain(1);
        listener().start(); send("surgery.readiness.invalidated", fixture("surgery.readiness.invalidated")); drain(2);
        assertThat(jdbc.queryForObject("SELECT invalidated FROM surgery_notice_snapshot", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM NOTIFICATION", String.class)).isEqualTo("SENT");
        assertThat(count("NOTIFICATION_EVENT_OUTBOX")).isOne();
    }
    @ParameterizedTest @ValueSource(strings = {"surgery.cancelled", "surgery.completed"})
    void consume_terminalBeforeReady_noLateProvisionalDelivery(String key) throws Exception {
        send(key, fixture(key)); drain(1); listener().start(); send("surgery.ready", fixture("surgery.ready")); drain(2);
        assertThat(jdbc.queryForObject("SELECT status FROM NOTIFICATION WHERE template_key='SURGERY_READY'", String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT terminal_event_type FROM surgery_notice_case", String.class)).isEqualTo(key);
        assertThat(count("NOTIFICATION_EVENT_OUTBOX")).isEqualTo(key.equals("surgery.cancelled") ? 1 : 0);
    }
    @Test void consume_newSnapshotAfterInvalidation_createsDistinctProvisionalNotReopenedOldReminder() throws Exception {
        send("surgery.readiness.invalidated", fixture("surgery.readiness.invalidated")); drain(1);
        var root = tree(fixture("surgery.ready")); root.put("eventId", UUID.randomUUID().toString());
        ((ObjectNode)root.get("payload")).put("readinessSnapshotId", UUID.randomUUID().toString()).put("caseRevision", 5);
        listener().start(); send("surgery.ready", mapper.writeValueAsBytes(root)); drain(2);
        assertThat(jdbc.queryForObject("SELECT status FROM NOTIFICATION", String.class)).isEqualTo("SENT");
        assertThat(count("surgery_notice_snapshot")).isEqualTo(2);
    }
    @Test void consume_wrongPatient_rollsBackClaimAndPreservesOriginalReminder() throws Exception {
        send("surgery.ready", fixture("surgery.ready")); drain(1);
        var root = tree(fixture("surgery.readiness.invalidated")); ((ObjectNode)root.get("payload")).put("patientId", UUID.randomUUID().toString());
        byte[] invalid = mapper.writeValueAsBytes(root); listener().start(); send("surgery.readiness.invalidated", invalid); retainedDlq(invalid);
        assertThat(count("PROCESSED_EVENT")).isOne();
        assertThat(jdbc.queryForObject("SELECT invalidated FROM surgery_notice_snapshot", Boolean.class)).isFalse();
    }
    @Test void consume_changedSameSnapshot_rejectsSemanticConflict() throws Exception {
        byte[] body = fixture("surgery.ready"); send("surgery.ready", body); drain(1);
        var root = tree(body); root.put("eventId", UUID.randomUUID().toString());
        ((ObjectNode)root.get("payload")).put("roomId", UUID.randomUUID().toString());
        byte[] invalid = mapper.writeValueAsBytes(root); listener().start(); send("surgery.ready", invalid); retainedDlq(invalid);
        assertThat(count("PROCESSED_EVENT")).isOne(); assertThat(count("NOTIFICATION")).isOne();
    }
    @Test void consume_invalidationDifferentSchedule_sameSnapshotCannotBeRebound() throws Exception {
        send("surgery.ready", fixture("surgery.ready")); drain(1);
        var root = tree(fixture("surgery.readiness.invalidated"));
        ((ObjectNode) root.get("payload")).put("scheduleId", UUID.randomUUID().toString());
        byte[] invalid = mapper.writeValueAsBytes(root);
        listener().start(); send("surgery.readiness.invalidated", invalid); retainedDlq(invalid);
        assertThat(count("PROCESSED_EVENT")).isOne();
        assertThat(jdbc.queryForObject("SELECT invalidated FROM surgery_notice_snapshot", Boolean.class)).isFalse();
    }
    @Test void consume_secondResultForTerminalCase_rejectsConflictingSource() throws Exception {
        send("surgery.completed", fixture("surgery.completed")); drain(1);
        var root = tree(fixture("surgery.completed")); root.put("eventId", UUID.randomUUID().toString());
        ((ObjectNode) root.get("payload")).put("resultId", UUID.randomUUID().toString());
        byte[] invalid = mapper.writeValueAsBytes(root);
        listener().start(); send("surgery.completed", invalid); retainedDlq(invalid);
        assertThat(count("PROCESSED_EVENT")).isOne(); assertThat(count("surgery_notice_source")).isOne();
        assertThat(count("NOTIFICATION_EVENT_OUTBOX")).isZero();
    }
    @Test void consume_cancelAfterCompleted_cannotReplaceTerminalEvidence() throws Exception {
        send("surgery.completed", fixture("surgery.completed")); drain(1);
        byte[] invalid = fixture("surgery.cancelled");
        listener().start(); send("surgery.cancelled", invalid); retainedDlq(invalid);
        assertThat(jdbc.queryForObject("SELECT terminal_event_type FROM surgery_notice_case", String.class)).isEqualTo("surgery.completed");
        assertThat(count("PROCESSED_EVENT")).isOne(); assertThat(count("NOTIFICATION")).isZero();
    }
    @Test void consume_concurrentReadyAndInvalidation_caseFenceKeepsTombstoneWithoutDuplicateHistory() throws Exception {
        // Two actual broker consumers may acquire the case fence in either order.
        send("surgery.ready", fixture("surgery.ready"));
        send("surgery.readiness.invalidated", fixture("surgery.readiness.invalidated")); drain(2);
        assertThat(jdbc.queryForObject("SELECT invalidated FROM surgery_notice_snapshot", Boolean.class)).isTrue();
        assertThat(count("NOTIFICATION")).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM NOTIFICATION", String.class)).isIn("SENT", "FAILED");
        assertThat(count("NOTIFICATION_EVENT_OUTBOX")).isLessThanOrEqualTo(1);
        listener().start(); send("surgery.ready", fixture("surgery.ready")); drain(2);
        assertThat(count("NOTIFICATION")).isOne();
    }
    @Test void consume_notificationOutboxWriteFailure_rollsBackAndRetainedBytesRecover() throws Exception {
        jdbc.execute("CREATE FUNCTION reject_surgery_notice() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected notice failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_surgery_notice BEFORE INSERT ON NOTIFICATION_EVENT_OUTBOX FOR EACH ROW EXECUTE FUNCTION reject_surgery_notice()");
        byte[] body = fixture("surgery.ready"), retained;
        try {
            send("surgery.ready", body); retained = retainedDlq(body);
            assertThat(count("PROCESSED_EVENT")).isZero(); assertThat(count("NOTIFICATION")).isZero();
            assertThat(count("surgery_notice_case")).isZero(); assertThat(count("surgery_notice_source")).isZero();
        } finally { jdbc.execute("DROP TRIGGER reject_surgery_notice ON NOTIFICATION_EVENT_OUTBOX"); jdbc.execute("DROP FUNCTION reject_surgery_notice()"); }
        listener().start(); send("surgery.ready", retained); drain(1); assertThat(count("NOTIFICATION")).isOne();
    }
    @ParameterizedTest @ValueSource(strings = {"admission", "outpatient"})
    void consume_actualBillingRequest_privateOneNoticeNotReceipt(String context) throws Exception {
        byte[] body = SurgeryPaymentNoticeContractTest.fixture(context); send("invoice.created", body);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("NOTIFICATION")).isOne());
        paymentListener().stop();
        assertThat(jdbc.queryForObject("SELECT template_key FROM NOTIFICATION", String.class)).isEqualTo("SURGERY_PAYMENT_REQUEST");
        assertThat(jdbc.queryForObject("SELECT content FROM NOTIFICATION", String.class)).contains("không phải biên nhận");
        assertThat(jdbc.queryForObject("SELECT recipient_address FROM NOTIFICATION", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT publication_enabled FROM NOTIFICATION_EVENT_OUTBOX", Boolean.class)).isFalse();
    }
    @Test void consume_requestSameSourceNewDelivery_oneNoticeAndConflictingAmountRejects() throws Exception {
        byte[] body = SurgeryPaymentNoticeContractTest.fixture("admission"); send("invoice.created", body);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("PROCESSED_EVENT")).isOne());
        var root = tree(body); root.put("eventId", UUID.randomUUID().toString()); send("invoice.created", mapper.writeValueAsBytes(root));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("PROCESSED_EVENT")).isEqualTo(2)); paymentListener().stop();
        assertThat(count("NOTIFICATION")).isOne();
        root.put("eventId", UUID.randomUUID().toString()); ((ObjectNode)root.get("payload")).put("totalAmount", 200);
        byte[] invalid = mapper.writeValueAsBytes(root); paymentListener().start(); send("invoice.created", invalid); paymentRetainedDlq(invalid);
        assertThat(count("PROCESSED_EVENT")).isEqualTo(2); assertThat(count("NOTIFICATION")).isOne();
    }
    @Test void consume_requestMalformedAmount_isRetainedWithoutAnyDelivery() throws Exception {
        var root = tree(SurgeryPaymentNoticeContractTest.fixture("admission")); ((ObjectNode)root.get("payload")).put("totalAmount", "100.00");
        byte[] invalid = mapper.writeValueAsBytes(root); send("invoice.created", invalid); paymentRetainedDlq(invalid);
        assertThat(count("PROCESSED_EVENT")).isZero(); assertThat(count("NOTIFICATION")).isZero(); assertThat(count("care_notification_source")).isZero();
    }
    @Test void consume_requestOutboxFailure_rollsBackSourceAndDeliveryThenReplaysRetainedBytes() throws Exception {
        jdbc.execute("CREATE FUNCTION reject_payment_notice() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected'; END $$");
        jdbc.execute("CREATE TRIGGER reject_payment_notice BEFORE INSERT ON NOTIFICATION_EVENT_OUTBOX FOR EACH ROW EXECUTE FUNCTION reject_payment_notice()");
        byte[] body = SurgeryPaymentNoticeContractTest.fixture("admission"), retained;
        try {
            send("invoice.created", body); retained = paymentRetainedDlq(body);
            assertThat(count("PROCESSED_EVENT")).isZero(); assertThat(count("care_notification_source")).isZero(); assertThat(count("NOTIFICATION")).isZero();
        } finally { jdbc.execute("DROP TRIGGER reject_payment_notice ON NOTIFICATION_EVENT_OUTBOX"); jdbc.execute("DROP FUNCTION reject_payment_notice()"); }
        paymentListener().start(); send("invoice.created", retained);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("NOTIFICATION")).isOne()); paymentListener().stop();
    }
    private org.springframework.amqp.rabbit.listener.MessageListenerContainer paymentListener() { return registry.getListenerContainer("notificationSurgeryPaymentRequests"); }
    @ParameterizedTest @ValueSource(strings = {"service", "deposit"})
    void consume_actualRefund_privateLinkedNoticeAndHeldDelivery(String context) throws Exception {
        send("payment.refunded", RefundNoticeContractTest.fixture(context)); refundDrain(1);
        assertThat(jdbc.queryForObject("SELECT template_key FROM NOTIFICATION", String.class)).isEqualTo("PAYMENT_REFUNDED");
        assertThat(jdbc.queryForObject("SELECT content FROM NOTIFICATION", String.class)).contains("không phải quyết toán");
        assertThat(jdbc.queryForObject("SELECT recipient_address FROM NOTIFICATION", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT sensitivity FROM NOTIFICATION", String.class)).isEqualTo("PRIVATE_IN_APP_ONLY");
        assertThat(jdbc.queryForObject("SELECT publication_enabled FROM NOTIFICATION_EVENT_OUTBOX", Boolean.class)).isFalse();
    }
    @Test void consume_refundNewDeliverySameSource_noDoubleNoticeButChangedMoneyRejects() throws Exception {
        byte[] body = RefundNoticeContractTest.fixture("service"); send("payment.refunded", body); send("payment.refunded", body); refundDrain(1);
        var root = tree(body); root.put("eventId", UUID.randomUUID().toString()); refundListener().start(); send("payment.refunded", mapper.writeValueAsBytes(root)); refundDrain(2);
        assertThat(count("NOTIFICATION")).isOne();
        root.put("eventId", UUID.randomUUID().toString()); ((ObjectNode)root.get("payload")).put("amount", 21);
        byte[] invalid = mapper.writeValueAsBytes(root); refundListener().start(); send("payment.refunded", invalid); refundRetainedDlq(invalid);
        assertThat(count("PROCESSED_EVENT")).isEqualTo(2); assertThat(count("NOTIFICATION")).isOne();
    }
    @Test void consume_refundOutboxFailure_rollsBackAndRecoversExactBytes() throws Exception {
        jdbc.execute("CREATE FUNCTION reject_refund_notice() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected'; END $$");
        jdbc.execute("CREATE TRIGGER reject_refund_notice BEFORE INSERT ON NOTIFICATION_EVENT_OUTBOX FOR EACH ROW EXECUTE FUNCTION reject_refund_notice()");
        byte[] body = RefundNoticeContractTest.fixture("service"), retained;
        try {
            send("payment.refunded", body); retained = refundRetainedDlq(body);
            assertThat(count("PROCESSED_EVENT")).isZero(); assertThat(count("care_notification_source")).isZero(); assertThat(count("NOTIFICATION")).isZero();
        } finally { jdbc.execute("DROP TRIGGER reject_refund_notice ON NOTIFICATION_EVENT_OUTBOX"); jdbc.execute("DROP FUNCTION reject_refund_notice()"); }
        refundListener().start(); send("payment.refunded", retained); refundDrain(1); assertThat(count("NOTIFICATION")).isOne();
    }
    @Test void consume_refundPoison_retainedWithoutAnyHistory() throws Exception {
        var root = tree(RefundNoticeContractTest.fixture("deposit")); ((ObjectNode)root.get("payload")).remove("originalTransactionId");
        byte[] invalid = mapper.writeValueAsBytes(root); send("payment.refunded", invalid); refundRetainedDlq(invalid);
        assertThat(count("PROCESSED_EVENT")).isZero(); assertThat(count("care_notification_source")).isZero(); assertThat(count("NOTIFICATION")).isZero();
    }
    private org.springframework.amqp.rabbit.listener.MessageListenerContainer refundListener() { return registry.getListenerContainer("notificationRefundNotices"); }
    private void refundDrain(long deliveries) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("PROCESSED_EVENT")).isEqualTo(deliveries)); refundListener().stop();
    }
    private byte[] refundRetainedDlq(byte[] body) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo("notification.refunds-v1.dlq").getMessageCount()).isOne());
        refundListener().stop(); var message = rabbit.receive("notification.refunds-v1.dlq", 5000);
        assertThat(message).isNotNull(); assertThat(message.getBody()).isEqualTo(body); return message.getBody();
    }
    private byte[] paymentRetainedDlq(byte[] body) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo(PAYMENT_DLQ).getMessageCount()).isOne());
        paymentListener().stop(); var message = rabbit.receive(PAYMENT_DLQ, 5000); assertThat(message).isNotNull(); assertThat(message.getBody()).isEqualTo(body); return message.getBody();
    }
    private org.springframework.amqp.rabbit.listener.MessageListenerContainer listener() { return registry.getListenerContainer("notificationSurgeryNotices"); }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }
    private void drain(long deliveries) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("PROCESSED_EVENT")).isEqualTo(deliveries));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo(QUEUE).getMessageCount()).isZero()); listener().stop();
    }
    private byte[] retainedDlq(byte[] body) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo(DLQ).getMessageCount()).isOne());
        listener().stop(); var message = rabbit.receive(DLQ, 5000); assertThat(message).isNotNull(); assertThat(message.getBody()).isEqualTo(body); return message.getBody();
    }
    private void send(String key, byte[] body) {
        rabbit.invoke(operations -> { operations.send("mediflow.events", key, new Message(body, new MessageProperties())); operations.waitForConfirmsOrDie(10000); return null; });
    }
    private byte[] fixture(String key) throws Exception { return SurgeryNoticeContractTest.fixture(key, "admission"); }
    private ObjectNode tree(byte[] body) throws Exception { return (ObjectNode)mapper.readTree(body); }
}
