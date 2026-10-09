package com.mediflow.report.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.report.application.dto.response.CashReplayProgress;
import com.mediflow.report.application.port.in.ReplayCashReceiptsUseCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Actual Billing fixture bytes, real PG/broker, durable gross receipt intake and finite rebuild. */
@Testcontainers
@SpringBootTest(properties = {
        "eureka.client.enabled=false", "mediflow.jwt.secret=report-cash-integration-secret-at-least-32-bytes",
        "mediflow.features.care-finance-v2=true", "mediflow.report.cash-receipt-consumer.enabled=true",
        "mediflow.report.cash-refund-consumer.enabled=true", "mediflow.report.cash-refund-consumer.recovery-initial-delay-ms=3600000",
        "spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.publisher-confirm-type=simple"
})
class CashReceiptRabbitIntegrationTest {
    private static final String QUEUE = "report.cash-receipts-v2.q", DLQ = "report.cash-receipts-v2.dlq";
    @Container @ServiceConnection static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container @ServiceConnection static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitListenerEndpointRegistry registry;
    @Autowired ReplayCashReceiptsUseCase replay;
    @Autowired com.mediflow.report.infrastructure.config.ReportCashRefundConsumerConfiguration.RefundRecoveryWorker refundRecovery;
    @Autowired com.mediflow.report.application.port.out.CashRefundStorePort refundStore;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void cleanAndStartOwnListener() {
        refundListener().stop(); admin.purgeQueue("report.cash-refunds-v2.q"); admin.purgeQueue("report.cash-refunds-v2.dlq");
        listener().stop(); admin.purgeQueue(QUEUE); admin.purgeQueue(DLQ);
        jdbc.execute("TRUNCATE report_cash_receipt, report_cash_delivery, report_gross_cash_daily, cash_replay_generation, report_cash_refund, report_refund_cash_daily CASCADE");
        listener().start();
        ((org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer)refundListener()).setConcurrentConsumers(2);
        refundListener().start();
    }
    @AfterEach void stopOwnListener() { listener().stop(); refundListener().stop(); }

    @ParameterizedTest @ValueSource(strings = {"payment-service.json", "payment-deposit.json"})
    void consume_actualProducerBytes_commitTwoGrossScopesNotEarnedRevenue(String fixture) throws Exception {
        send(fixture(fixture)); drain(1);
        assertThat(jdbc.queryForList("SELECT gross_receipts FROM report_gross_cash_daily", java.math.BigDecimal.class))
                .hasSize(2).allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("100.00"));
        assertThat(jdbc.queryForList("SELECT classification FROM report_gross_cash_daily", String.class))
                .containsOnly(fixture.contains("deposit") ? "ADMISSION_DEPOSIT" : "SERVICE_PAYMENT");
        assertNoLegacyOrFinancialPublication();
    }

    @Test void consume_duplicateAndNewDeliveryThenRebuild_singleTransactionEffect() throws Exception {
        byte[] body = fixture("payment-deposit.json"); send(body); send(body); drain(1);
        var root = tree(body); root.put("eventId", UUID.randomUUID().toString());
        listener().start(); send(mapper.writeValueAsBytes(root)); drain(2);
        assertThat(count("report_cash_receipt")).isOne();
        assertThat(jdbc.queryForList("SELECT receipt_count FROM report_gross_cash_daily", Long.class)).containsOnly(1L);
        var progress = replay.start();
        for (int i = 0; i < 3 && progress.status() == CashReplayProgress.Status.BUILDING; i++)
            progress = replay.advance(progress.generationId(), 1);
        assertThat(progress.status()).isEqualTo(CashReplayProgress.Status.VERIFIED);
        assertNoLegacyOrFinancialPublication();
    }

    @Test void consume_twoTransactionsOnSameInvoice_countBothNotInvoiceDedupe() throws Exception {
        byte[] body = fixture("payment-service.json"); send(body); drain(1);
        var root = tree(body); root.put("eventId", UUID.randomUUID().toString());
        payload(root).put("transactionId", UUID.randomUUID().toString());
        listener().start(); send(mapper.writeValueAsBytes(root)); drain(2);
        assertThat(count("report_cash_receipt")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT gross_receipts FROM report_gross_cash_daily", java.math.BigDecimal.class))
                .hasSize(2).allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("200.00"));
    }

    @Test void consume_depositAndServiceKeepSeparateClassifications() throws Exception {
        send(fixture("payment-service.json")); send(fixture("payment-deposit.json")); drain(2);
        assertThat(count("report_cash_receipt")).isEqualTo(2);
        assertThat(count("report_gross_cash_daily")).isEqualTo(4);
        assertThat(jdbc.queryForList("SELECT classification FROM report_gross_cash_daily", String.class))
                .containsExactlyInAnyOrder("SERVICE_PAYMENT", "SERVICE_PAYMENT", "ADMISSION_DEPOSIT", "ADMISSION_DEPOSIT");
        assertNoLegacyOrFinancialPublication();
    }

    @Test void consume_reusedDeliveryForDifferentTransaction_rollsBackSecondReceipt() throws Exception {
        byte[] body = fixture("payment-service.json"); send(body); drain(1);
        var root = tree(body); payload(root).put("transactionId", UUID.randomUUID().toString());
        byte[] invalid = mapper.writeValueAsBytes(root);
        listener().start(); send(invalid); retainedDlq(invalid);
        assertThat(count("report_cash_receipt")).isOne();
        assertThat(count("report_cash_delivery")).isOne();
        assertThat(jdbc.queryForList("SELECT receipt_count FROM report_gross_cash_daily", Long.class)).containsOnly(1L);
    }

    @Test void consume_exactLargeCents_doNotPassThroughDouble() throws Exception {
        var root = tree(fixture("payment-service.json"));
        payload(root).put("totalAmount", new java.math.BigDecimal("12345678901234567.01"));
        send(mapper.writeValueAsBytes(root)); drain(1);
        assertThat(jdbc.queryForList("SELECT gross_receipts FROM report_gross_cash_daily", java.math.BigDecimal.class))
                .hasSize(2).allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("12345678901234567.01"));
    }

    @Test void consume_legacyAndVersionedListenersCoexist_withoutDuplicateLegacyRevenue() throws Exception {
        var legacy = registry.getListenerContainers().stream().filter(container -> container instanceof org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer simple
                && java.util.Arrays.asList(simple.getQueueNames()).contains("report.q")).findFirst().orElseThrow();
        admin.purgeQueue("report.q"); admin.purgeQueue("report.dlq");
        try {
            legacy.start(); byte[] body = fixture("payment-deposit.json"); send(body); drain(1);
            await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> assertThat(admin.getQueueInfo("report.dlq").getMessageCount()).isOne());
            legacy.stop();
            assertThat(rabbit.receive("report.dlq", 5000).getBody()).isEqualTo(body);
            assertNoLegacyOrFinancialPublication();
        } finally { legacy.stop(); }
    }

    @ParameterizedTest @ValueSource(strings = {"totalAmount", "patientId", "departmentId"})
    void consume_conflictingSource_rollsBackNewDelivery(String field) throws Exception {
        byte[] body = fixture("payment-service.json"); send(body); drain(1);
        var root = tree(body); root.put("eventId", UUID.randomUUID().toString());
        if (field.equals("totalAmount")) payload(root).put(field, 101);
        else payload(root).put(field, UUID.randomUUID().toString());
        byte[] conflict = mapper.writeValueAsBytes(root);
        listener().start(); send(conflict); retainedDlq(conflict);
        assertThat(count("report_cash_delivery")).isOne();
        assertThat(count("report_cash_receipt")).isOne();
        assertThat(jdbc.queryForList("SELECT gross_receipts FROM report_gross_cash_daily", java.math.BigDecimal.class))
                .allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("100.00"));
    }

    @ParameterizedTest @ValueSource(strings = {"version", "producer", "source", "classification", "amount", "time"})
    void consume_unsupportedContract_rejectsBeforeClaim(String defect) throws Exception {
        var root = tree(fixture("payment-deposit.json"));
        switch (defect) {
            case "version" -> root.put("version", 2);
            case "producer" -> root.put("producer", "pharmacy-service");
            case "source" -> payload(root).remove("transactionId");
            case "classification" -> payload(root).put("classification", "SETTLEMENT_PAYMENT");
            case "amount" -> payload(root).put("totalAmount", "100.00");
            case "time" -> payload(root).put("completedAt", "not-an-instant");
            default -> throw new AssertionError(defect);
        }
        byte[] invalid = mapper.writeValueAsBytes(root); send(invalid); retainedDlq(invalid);
        assertThat(count("report_cash_delivery")).isZero();
        assertThat(count("report_cash_receipt")).isZero();
        assertThat(count("report_gross_cash_daily")).isZero();
        assertNoLegacyOrFinancialPublication();
    }

    @Test void consume_legacyFlatPayment_isNotReinterpretedAsLedgerCash() throws Exception {
        var root = tree(fixture("payment-service.json"));
        byte[] invalid = mapper.writeValueAsBytes(root.get("payload")); send(invalid); retainedDlq(invalid);
        assertThat(count("report_cash_delivery")).isZero();
        assertThat(count("report_cash_receipt")).isZero();
    }

    @Test void consume_secondScopeStorageFailure_rollbackAndRetainedByteRecovery() throws Exception {
        // Sequence increments survive rollback: count actual failed DB attempts without mocking the kernel.
        jdbc.execute("CREATE SEQUENCE cash_failed_attempts");
        jdbc.execute("CREATE FUNCTION reject_cash_hospital() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.department_id IS NULL THEN PERFORM nextval('cash_failed_attempts'); RAISE EXCEPTION 'injected cash scope failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER cash_scope_failure BEFORE INSERT OR UPDATE ON report_gross_cash_daily FOR EACH ROW EXECUTE FUNCTION reject_cash_hospital()");
        byte[] body = fixture("payment-deposit.json"), retained;
        try {
            send(body); retained = retainedDlq(body);
            assertThat(count("report_cash_delivery")).isZero();
            assertThat(count("report_cash_receipt")).isZero();
            assertThat(count("report_gross_cash_daily")).isZero();
            assertThat(jdbc.queryForObject("SELECT last_value FROM cash_failed_attempts", Long.class)).isEqualTo(3);
        } finally {
            jdbc.execute("DROP TRIGGER cash_scope_failure ON report_gross_cash_daily");
            jdbc.execute("DROP FUNCTION reject_cash_hospital()");
            jdbc.execute("DROP SEQUENCE cash_failed_attempts");
        }
        listener().start(); send(retained); drain(1);
        assertThat(jdbc.queryForList("SELECT receipt_count FROM report_gross_cash_daily", Long.class)).hasSize(2).containsOnly(1L);
        assertNoLegacyOrFinancialPublication();
    }

    @Test void consume_queuedWhileListenerStopped_restartCapturesOnce() throws Exception {
        listener().stop(); byte[] body = fixture("payment-service.json"); send(body); send(body);
        assertThat(count("report_cash_receipt")).isZero();
        listener().start(); drain(1);
        assertThat(count("report_cash_receipt")).isOne();
        assertThat(jdbc.queryForList("SELECT receipt_count FROM report_gross_cash_daily", Long.class)).containsOnly(1L);
    }

    private void assertNoLegacyOrFinancialPublication() {
        for (String table : new String[]{"processed_event", "daily_visit_report", "monthly_revenue_report", "payment_contribution",
                "operational_report_publication", "financial_contribution", "daily_financial_report", "operational_event_journal"})
            assertThat(count(table)).as(table).isZero();
    }
    @ParameterizedTest @ValueSource(strings = {"service", "deposit"})
    void consume_actualRefundPair_onlyCashOutAndOriginalClassification(String context) throws Exception {
        send(fixture("payment-" + context + ".json")); drain(1);
        sendRefund(fixture("refund-" + context + ".json")); refundDrain(1);
        assertThat(jdbc.queryForList("SELECT completed_refunds FROM report_refund_cash_daily", java.math.BigDecimal.class)).hasSize(2)
                .allSatisfy(value -> assertThat(value).isEqualByComparingTo("20"));
        assertThat(jdbc.queryForList("SELECT gross_receipts FROM report_gross_cash_daily", java.math.BigDecimal.class)).hasSize(2)
                .allSatisfy(value -> assertThat(value).isEqualByComparingTo("100"));
        assertThat(jdbc.queryForObject("SELECT classification FROM report_cash_refund", String.class))
                .isEqualTo(context.equals("deposit") ? "ADMISSION_DEPOSIT" : "SERVICE_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT original_business_date FROM report_cash_refund", java.sql.Date.class).toLocalDate()).isEqualTo("2026-10-05");
        assertThat(jdbc.queryForObject("SELECT business_date FROM report_cash_refund", java.sql.Date.class).toLocalDate()).isEqualTo("2026-10-08");
        assertNoLegacyOrFinancialPublication();
    }
    @Test void consume_refundBeforeReceipt_survivesRestartAndPairsExactOriginal() throws Exception {
        byte[] body = fixture("refund-deposit.json"); sendRefund(body); refundDrain(1);
        assertThat(jdbc.queryForObject("SELECT state FROM report_cash_refund", String.class)).isEqualTo("PENDING");
        assertThat(count("report_refund_cash_daily")).isZero();
        refundListener().start(); sendRefund(body); refundDrain(1);
        send(fixture("payment-deposit.json")); drain(1);
        assertThat(jdbc.queryForObject("SELECT state FROM report_cash_refund", String.class)).isEqualTo("APPLIED");
        assertThat(count("report_cash_refund_delivery")).isOne(); assertThat(count("report_refund_cash_daily")).isEqualTo(2);
    }
    @Test void consume_refundSemanticReplay_noDoubleEffectAndChangedSourceRetained() throws Exception {
        send(fixture("payment-service.json")); drain(1);
        byte[] body = fixture("refund-service.json"); sendRefund(body); sendRefund(body); refundDrain(1);
        var root = tree(body); root.put("eventId", UUID.randomUUID().toString()); refundListener().start(); sendRefund(mapper.writeValueAsBytes(root)); refundDrain(2);
        assertThat(jdbc.queryForList("SELECT refund_count FROM report_refund_cash_daily", Long.class)).containsOnly(1L);
        root.put("eventId", UUID.randomUUID().toString()); payload(root).put("amount", 21);
        byte[] invalid = mapper.writeValueAsBytes(root); refundListener().start(); sendRefund(invalid); refundDlq(invalid);
        assertThat(count("report_cash_refund_delivery")).isEqualTo(2);
    }
    @Test void consume_refundWrongOriginalContext_rejectsWithoutClaim() throws Exception {
        send(fixture("payment-service.json")); drain(1);
        var root = tree(fixture("refund-service.json")); payload(root).put("patientId", UUID.randomUUID().toString());
        byte[] invalid = mapper.writeValueAsBytes(root); sendRefund(invalid); refundDlq(invalid);
        assertThat(count("report_cash_refund")).isZero(); assertThat(count("report_cash_refund_delivery")).isZero(); assertThat(count("report_refund_cash_daily")).isZero();
    }
    @Test void consume_earlyWrongRefund_quarantinesWithoutRejectingValidOriginal() throws Exception {
        var root = tree(fixture("refund-service.json")); payload(root).put("patientId", UUID.randomUUID().toString());
        sendRefund(mapper.writeValueAsBytes(root)); refundDrain(1);
        send(fixture("payment-service.json")); drain(1);
        assertThat(jdbc.queryForObject("SELECT state FROM report_cash_refund", String.class)).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT reason_code FROM report_cash_refund", String.class)).isEqualTo("CASH_REFUND_ORIGINAL_MISMATCH");
        assertThat(count("report_cash_receipt")).isOne(); assertThat(count("report_refund_cash_daily")).isZero();
    }
    @Test void consume_twoConcurrentRefunds_boundToOriginalNotInvoice() throws Exception {
        send(fixture("payment-service.json")); drain(1);
        var root = tree(fixture("refund-service.json")); payload(root).put("amount", 80);
        byte[] first = mapper.writeValueAsBytes(root); sendRefund(first);
        root.put("eventId", UUID.randomUUID().toString()); payload(root).put("refundTransactionId", UUID.randomUUID().toString());
        sendRefund(mapper.writeValueAsBytes(root));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(count("report_cash_refund")).isOne(); assertThat(admin.getQueueInfo("report.cash-refunds-v2.dlq").getMessageCount()).isOne();
        }); refundListener().stop();
        assertThat(jdbc.queryForList("SELECT completed_refunds FROM report_refund_cash_daily", java.math.BigDecimal.class)).hasSize(2)
                .allSatisfy(value -> assertThat(value).isEqualByComparingTo("80"));
    }
    @Test void consume_secondRefundScopeFails_rollsBackBothScopesAndPreservesRetainedBytes() throws Exception {
        send(fixture("payment-service.json")); drain(1);
        jdbc.execute("CREATE SEQUENCE refund_failed_attempts");
        jdbc.execute("CREATE FUNCTION reject_refund_scope() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.department_id IS NOT NULL THEN PERFORM nextval('refund_failed_attempts'); RAISE EXCEPTION 'injected'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER reject_refund_scope BEFORE INSERT OR UPDATE ON report_refund_cash_daily FOR EACH ROW EXECUTE FUNCTION reject_refund_scope()");
        byte[] body = fixture("refund-service.json"), retained;
        try {
            sendRefund(body); retained = refundDlq(body);
            assertThat(count("report_cash_refund")).isZero(); assertThat(count("report_cash_refund_delivery")).isZero(); assertThat(count("report_refund_cash_daily")).isZero();
            assertThat(jdbc.queryForObject("SELECT last_value FROM refund_failed_attempts", Long.class)).isEqualTo(3);
        } finally {
            jdbc.execute("DROP TRIGGER reject_refund_scope ON report_refund_cash_daily"); jdbc.execute("DROP FUNCTION reject_refund_scope()"); jdbc.execute("DROP SEQUENCE refund_failed_attempts");
        }
        refundListener().start(); sendRefund(retained); refundDrain(1); assertThat(count("report_refund_cash_daily")).isEqualTo(2);
    }
    @Test void consume_refundReusesDeliveryForOtherOriginal_rollsBackSecondSource() throws Exception {
        sendRefund(fixture("refund-service.json")); refundDrain(1);
        var root = tree(fixture("refund-deposit.json")); root.put("eventId", tree(fixture("refund-service.json")).path("eventId").asText());
        byte[] invalid = mapper.writeValueAsBytes(root); refundListener().start(); sendRefund(invalid); refundDlq(invalid);
        assertThat(count("report_cash_refund")).isOne(); assertThat(count("report_cash_refund_delivery")).isOne();
    }
    @Test void recovery_moreThanOneBatch_usesDurableEvidenceAndPreservesExactCashBound() throws Exception {
        UUID original = sendEarlyRefunds(21);
        assertThat(count("report_refund_cash_daily")).isZero();
        assertThat(refundStore.readyOriginals(20)).isEmpty();
        send(fixture("payment-service.json")); drain(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_cash_refund WHERE state='APPLIED'", Long.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_cash_refund WHERE state='PENDING'", Long.class)).isOne();
        assertThat(refundStore.readyOriginals(20)).containsExactly(original);
        // The worker reads committed evidence through real transaction proxies after the receipt caller ends.
        refundRecovery.recover(); refundRecovery.recover();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_cash_refund WHERE state='APPLIED'", Long.class)).isEqualTo(21);
        assertThat(refundStore.readyOriginals(20)).isEmpty();
        assertThat(jdbc.queryForList("SELECT completed_refunds FROM report_refund_cash_daily", java.math.BigDecimal.class))
                .hasSize(2).allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("21.00"));
        assertThat(jdbc.queryForList("SELECT gross_receipts FROM report_gross_cash_daily", java.math.BigDecimal.class))
                .hasSize(2).allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("100.00"));
    }

    @Test void recovery_transientStorageFailure_retainsPendingAndPersistsBoundedDeferral() throws Exception {
        UUID original = sendEarlyRefunds(21);
        send(fixture("payment-service.json")); drain(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_cash_refund WHERE state='PENDING'", Long.class)).isOne();
        jdbc.execute("CREATE FUNCTION reject_pending_refund() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected'; END $$");
        jdbc.execute("CREATE TRIGGER reject_pending_refund BEFORE INSERT OR UPDATE ON report_refund_cash_daily FOR EACH ROW EXECUTE FUNCTION reject_pending_refund()");
        try {
            refundRecovery.recover();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM report_cash_refund WHERE state='PENDING'", Long.class)).isOne();
            assertThat(jdbc.queryForList("SELECT completed_refunds FROM report_refund_cash_daily", java.math.BigDecimal.class))
                    .hasSize(2).allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("20.00"));
            assertThat(jdbc.queryForObject("SELECT recovery_attempts FROM report_cash_refund WHERE state='PENDING'", Long.class)).isOne();
            assertThat(jdbc.queryForObject("SELECT next_attempt_at>now()+interval '45 seconds' FROM report_cash_refund WHERE state='PENDING'", Boolean.class)).isTrue();
            assertThat(refundStore.readyOriginals(20)).isEmpty();
            refundRecovery.recover();
            assertThat(jdbc.queryForObject("SELECT recovery_attempts FROM report_cash_refund WHERE state='PENDING'", Long.class)).isOne();
        } finally {
            jdbc.execute("DROP TRIGGER reject_pending_refund ON report_refund_cash_daily");
            jdbc.execute("DROP FUNCTION reject_pending_refund()");
        }
        jdbc.update("UPDATE report_cash_refund SET next_attempt_at=now()-interval '1 second' WHERE state='PENDING'");
        assertThat(refundStore.readyOriginals(20)).containsExactly(original);
        refundRecovery.recover();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_cash_refund WHERE state='APPLIED'", Long.class)).isEqualTo(21);
        assertThat(count("report_refund_cash_daily")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT completed_refunds FROM report_refund_cash_daily", java.math.BigDecimal.class))
                .hasSize(2).allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("21.00"));
    }

    private UUID sendEarlyRefunds(int count) throws Exception {
        var root = tree(fixture("refund-service.json"));
        UUID original = UUID.fromString(payload(root).path("originalTransactionId").asText());
        payload(root).put("amount", 1);
        for (int i = 0; i < count; i++) {
            root.put("eventId", UUID.randomUUID().toString());
            payload(root).put("refundTransactionId", UUID.randomUUID().toString());
            sendRefund(mapper.writeValueAsBytes(root));
        }
        refundDrain(count);
        return original;
    }

    private org.springframework.amqp.rabbit.listener.MessageListenerContainer refundListener() { return registry.getListenerContainer("reportCashRefunds"); }
    private void sendRefund(byte[] body) {
        rabbit.invoke(operations -> { operations.send("mediflow.events", "payment.refunded", new Message(body, new MessageProperties()));
            operations.waitForConfirmsOrDie(10000); return null; });
    }
    private void refundDrain(long deliveries) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("report_cash_refund_delivery")).isEqualTo(deliveries)); refundListener().stop();
    }
    private byte[] refundDlq(byte[] expected) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo("report.cash-refunds-v2.dlq").getMessageCount()).isOne());
        refundListener().stop(); var retained = rabbit.receive("report.cash-refunds-v2.dlq", 5000);
        assertThat(retained).isNotNull(); assertThat(retained.getBody()).isEqualTo(expected); return retained.getBody();
    }
    private org.springframework.amqp.rabbit.listener.MessageListenerContainer listener() { return registry.getListenerContainer("reportCashReceipts"); }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }
    private void drain(long deliveries) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("report_cash_delivery")).isEqualTo(deliveries));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo(QUEUE).getMessageCount()).isZero());
        listener().stop();
    }
    private byte[] retainedDlq(byte[] expected) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo(DLQ).getMessageCount()).isOne());
        listener().stop(); var retained = rabbit.receive(DLQ, 5000); assertThat(retained).isNotNull();
        assertThat(retained.getBody()).isEqualTo(expected); return retained.getBody();
    }
    private void send(byte[] body) {
        rabbit.invoke(operations -> { operations.send("mediflow.events", "payment.completed", new Message(body, new MessageProperties()));
            operations.waitForConfirmsOrDie(10000); return null; });
    }
    private ObjectNode tree(byte[] body) throws Exception { return (ObjectNode) mapper.readTree(body); }
    private ObjectNode payload(ObjectNode root) { return (ObjectNode) root.get("payload"); }
    private byte[] fixture(String file) throws Exception { return Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/" + file)); }
}
