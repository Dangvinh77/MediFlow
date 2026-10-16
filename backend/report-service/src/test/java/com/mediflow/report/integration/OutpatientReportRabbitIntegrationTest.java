package com.mediflow.report.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Own disposable stores/broker; producer fixtures stay byte-identical. Not live owner sign-off. */
@Testcontainers
@ActiveProfiles("outpatient-mvp")
@SpringBootTest(properties = {
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "mediflow.jwt.secret=outpatient-report-integration-secret-at-least-32-bytes",
        "mediflow.features.care-finance-v2=true", "mediflow.report.outpatient-consumer.enabled=true",
        "mediflow.report.cash-refund-consumer.recovery-initial-delay-ms=3600000",
        "spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.publisher-confirm-type=simple"})
class OutpatientReportRabbitIntegrationTest {
    private static final String QUEUE = "report.outpatient-v1.q", DLQ = "report.outpatient-v1.dlq";
    @Container @ServiceConnection static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container @ServiceConnection static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitListenerEndpointRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void startIsolatedScenario() {
        listener().stop(); admin.purgeQueue(QUEUE); admin.purgeQueue(DLQ);
        jdbc.execute("TRUNCATE operational_contribution, operational_delivery, operational_event_journal, operational_source_snapshot, daily_operational_report, report_cash_receipt, report_cash_delivery, report_gross_cash_daily, report_cash_refund, report_refund_cash_daily CASCADE");
        listener().start();
    }
    @AfterEach void stopListener() { listener().stop(); }

    @Test void prescriptionProducerBytesAndNewEnvelopeIdHaveOneOperationEffect() throws Exception {
        byte[] body = prescription(); send("prescription.filled", body); send("prescription.filled", body);
        settle(() -> assertThat(count("operational_event_journal")).isOne());
        var root = (ObjectNode) mapper.readTree(body); root.put("eventId", UUID.randomUUID().toString());
        listener().start(); send("prescription.filled", mapper.writeValueAsBytes(root));
        settle(() -> assertThat(count("operational_event_journal")).isEqualTo(2));
        assertThat(jdbc.queryForObject("SELECT sum(numeric_value) FROM operational_contribution WHERE metric_type='DISPENSED_PRESCRIPTIONS'", Long.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT sum(numeric_value) FROM operational_contribution WHERE metric_type='DISPENSED_UNITS'", Long.class)).isEqualTo(2);
        assertThat(count("payment_contribution")).isZero();
    }

    @Test void earlyRefundSurvivesConsumerRestartAndPaymentRecoversExactlyOnce() throws Exception {
        byte[] refund = billing("refund-service.json"), payment = billing("payment-service.json");
        send("payment.refunded", refund);
        settle(() -> assertThat(jdbc.queryForObject("SELECT state FROM report_cash_refund", String.class)).isEqualTo("PENDING"));
        assertThat(count("report_refund_cash_daily")).isZero();
        listener().start(); send("payment.completed", payment); send("payment.refunded", refund); send("payment.completed", payment);
        settle(() -> assertThat(jdbc.queryForObject("SELECT state FROM report_cash_refund", String.class)).isEqualTo("APPLIED"));
        assertThat(count("report_cash_receipt")).isOne(); assertThat(count("report_cash_refund")).isOne();
        assertThat(jdbc.queryForList("SELECT gross_receipts FROM report_gross_cash_daily", java.math.BigDecimal.class))
                .hasSize(2).allSatisfy(v -> assertThat(v).isEqualByComparingTo("100.00"));
        assertThat(jdbc.queryForList("SELECT completed_refunds FROM report_refund_cash_daily", java.math.BigDecimal.class))
                .hasSize(2).allSatisfy(v -> assertThat(v).isEqualByComparingTo("20.00"));
        assertThat(count("financial_contribution")).isZero();
    }

    @Test void admissionDepositIsRetainedInDlqWithoutFinancialOrOperationalEffects() throws Exception {
        byte[] deferred = billing("payment-deposit.json"); send("payment.completed", deferred);
        assertDlq(deferred);
        assertThat(count("report_cash_receipt")).isZero(); assertThat(count("report_cash_delivery")).isZero();
        assertThat(count("operational_contribution")).isZero(); assertThat(count("payment_contribution")).isZero();
    }

    @Test void conflictingSameOperationIsRetainedWithoutChangingAcceptedContribution() throws Exception {
        byte[] body = prescription(); send("prescription.filled", body);
        settle(() -> assertThat(count("operational_event_journal")).isOne());
        var root = (ObjectNode) mapper.readTree(body); root.put("eventId", UUID.randomUUID().toString());
        root.put("occurredAt", "2026-10-01T11:00:00Z");
        ((ObjectNode) root.get("payload")).put("filledAt", "2026-10-01T11:00:00Z");
        byte[] conflict = mapper.writeValueAsBytes(root); listener().start(); send("prescription.filled", conflict);
        assertDlq(conflict);
        assertThat(count("operational_event_journal")).isOne();
        assertThat(jdbc.queryForObject("SELECT sum(numeric_value) FROM operational_contribution WHERE metric_type='DISPENSED_UNITS'", Long.class)).isEqualTo(2);
    }

    @Test void databaseFailureRetriesThreeTimesRollsBackAllEffectsAndRetainsOriginalBytes() throws Exception {
        jdbc.execute("CREATE SEQUENCE outpatient_failed_attempts");
        jdbc.execute("CREATE FUNCTION reject_outpatient_cash() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN PERFORM nextval('outpatient_failed_attempts'); RAISE EXCEPTION 'isolated injected failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_outpatient_cash BEFORE INSERT ON report_gross_cash_daily FOR EACH ROW EXECUTE FUNCTION reject_outpatient_cash()");
        byte[] body = billing("payment-service.json");
        try {
            send("payment.completed", body); assertDlq(body);
            assertThat(count("report_cash_receipt")).isZero(); assertThat(count("report_cash_delivery")).isZero();
            assertThat(count("report_gross_cash_daily")).isZero();
            assertThat(jdbc.queryForObject("SELECT last_value FROM outpatient_failed_attempts", Long.class)).isEqualTo(3);
        } finally {
            jdbc.execute("DROP TRIGGER reject_outpatient_cash ON report_gross_cash_daily");
            jdbc.execute("DROP FUNCTION reject_outpatient_cash()"); jdbc.execute("DROP SEQUENCE outpatient_failed_attempts");
        }
        listener().start(); send("payment.completed", body);
        settle(() -> assertThat(count("report_cash_receipt")).isOne());
        assertThat(count("report_gross_cash_daily")).isEqualTo(2);
    }

    private org.springframework.amqp.rabbit.listener.MessageListenerContainer listener() {
        return registry.getListenerContainer("reportOutpatientFacts");
    }
    private void settle(Runnable assertion) {
        await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
            assertion.run(); assertThat(admin.getQueueInfo(QUEUE).getMessageCount()).isZero();
        });
        listener().stop(); // graceful stop waits for in-flight deliveries before final state assertions
    }
    private void assertDlq(byte[] expected) {
        await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> assertThat(admin.getQueueInfo(DLQ).getMessageCount()).isOne());
        listener().stop(); var retained = rabbit.receive(DLQ, 5000);
        assertThat(retained).isNotNull(); assertThat(retained.getBody()).isEqualTo(expected);
    }
    private void send(String key, byte[] body) {
        rabbit.invoke(ops -> { ops.send("mediflow.events", key, new Message(body, new MessageProperties()));
            ops.waitForConfirmsOrDie(10000); return null; });
    }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }
    private byte[] billing(String file) throws Exception {
        return Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/" + file));
    }
    private byte[] prescription() throws Exception {
        return Files.readAllBytes(Path.of("../pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription.filled.outpatient.v1.json"));
    }
}
