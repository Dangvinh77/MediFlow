package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.billing.application.dto.request.CompleteLedgerPaymentRequest;
import com.mediflow.billing.application.port.in.IssuePrescriptionChargeUseCase;
import com.mediflow.billing.application.port.in.ProcessLedgerPaymentUseCase;
import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.infrastructure.messaging.PrescriptionChargeDecoder;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
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
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(properties = {"eureka.client.enabled=false", "mediflow.billing.ledger.enabled=true",
        "mediflow.billing.prescription-charge-consumer.enabled=true", "mediflow.billing.outbox.enabled=false",
        "mediflow.billing.prescription-charges.allowed-price-codes=RX-DEFAULT",
        "mediflow.billing.refunds.enabled=true",
        "mediflow.billing.outbox.metrics-enabled=false", "mediflow.billing.outbox.maintenance-enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.publisher-confirm-type=simple"})
class PrescriptionChargeRabbitIntegrationTest {
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
    @Autowired IssuePrescriptionChargeUseCase charges;
    @Autowired com.mediflow.billing.application.port.out.PrescriptionChargeRepositoryPort sourceStore;
    @Autowired ProcessLedgerPaymentUseCase payments;
    @Autowired PrescriptionChargeDecoder decoder;
    @Autowired ObjectMapper mapper;
    @Autowired com.mediflow.billing.application.port.in.ApplyPrescriptionTerminalUseCase terminals;
    @Autowired com.mediflow.billing.application.port.in.RefundLedgerPaymentUseCase refunds;
    @Autowired com.mediflow.billing.application.port.in.GetPrescriptionAdjustmentUseCase adjustments;
    private static final String QUEUE = "billing.q", DLQ = "billing.dlq";

    @BeforeEach void reset() {
        listener().stop(); admin.purgeQueue(QUEUE); admin.purgeQueue(DLQ);
        // Fresh private Testcontainers database only, never the configured development DB.
        jdbc.execute("TRUNCATE BILLING_ACCOUNT,BILLING_EVENT_OUTBOX,prescription_terminal_source CASCADE");
    }
    @AfterEach void stop() { listener().stop(); }
    @Test void consume_actualAdmissionBytes_chargeOnlyNoPretendPrepayment() throws Exception {
        listener().start(); send(PrescriptionChargeContractTest.fixture()); drain(1);
        assertThat(count("CHARGE")).isOne(); assertThat(count("prescription_charge_item")).isOne();
        assertThat(count("PAYMENT_REQUEST")).isZero(); assertThat(count("FINANCIAL_CLEARANCE")).isZero();
        assertThat(count("BILLING_EVENT_OUTBOX")).isZero(); assertThat(count("PROCESSED_EVENT")).isZero();
        assertThat(jdbc.queryForObject("SELECT gross_amount FROM CHARGE", BigDecimal.class)).isEqualByComparingTo("2000");
    }
    @Test void issue_outpatientInstallments_exactSelectedChargeOneGrantAndHeldFacts() throws Exception {
        byte[] body = outpatient(); listener().start(); send(body); drain(1);
        var c = decoder.decode("prescription.created", body);
        UUID request = jdbc.queryForObject("SELECT payment_request_id FROM PAYMENT_REQUEST", UUID.class);
        UUID invoice = jdbc.queryForObject("SELECT invoice_id FROM PAYMENT_REQUEST", UUID.class);
        assertThat(invoice).isNotEqualTo(request);
        assertThat(jdbc.queryForObject("SELECT created_by FROM PAYMENT_REQUEST", UUID.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT record_id FROM PAYMENT_REQUEST_TARGET", UUID.class)).isNull();
        var notice = mapper.readTree(fact("invoice.created")).path("payload");
        assertThat(notice.path("purpose").asText()).isEqualTo("PRESCRIPTION");
        assertThat(notice.path("prescriptionId").asText()).isEqualTo(c.prescriptionId().toString());
        var first = payments.complete(request, payment("first", "750"), UUID.fromString("20000000-0000-4000-8000-000000000001"), "rx-payment");
        assertThat(count("FINANCIAL_CLEARANCE")).isZero();
        assertThat(payments.complete(request, payment("first", "750"), UUID.fromString("20000000-0000-4000-8000-000000000001"), "rx-retry").transactionId()).isEqualTo(first.transactionId());
        payments.complete(request, payment("last", "1250"), UUID.randomUUID(), "rx-paid");
        assertThat(count("PAYMENT_TRANSACTION")).isEqualTo(2); assertThat(count("FINANCIAL_CLEARANCE")).isOne();
        var grant = mapper.readTree(fact("financial.clearance.granted")).path("payload");
        assertThat(grant.path("purpose").asText()).isEqualTo("PRESCRIPTION");
        assertThat(grant.path("prescriptionId").asText()).isEqualTo(c.prescriptionId().toString());
        assertThat(grant.path("careEpisodeId").asText()).isEqualTo(c.careEpisodeId().toString());
        assertThat(grant.path("recordId").isNull()).isTrue(); assertThat(grant.path("admissionId").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM PAYMENT_ALLOCATION", BigDecimal.class)).isEqualByComparingTo("2000");
        assertThat(count("BILLING_EVENT_OUTBOX")).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM BILLING_EVENT_OUTBOX WHERE publication_enabled", Long.class)).isZero();
    }
    @Test void consume_duplicateAndNewDelivery_returnOriginalAfterClosure() throws Exception {
        byte[] body = outpatient(); var c = decoder.decode("prescription.created", body); var original = charges.issue(c);
        assertThat(charges.issue(c)).isEqualTo(original);
        jdbc.update("UPDATE BILLING_ACCOUNT SET status='CLOSED'");
        var root = tree(body); root.put("eventId", UUID.randomUUID().toString()); root.put("correlationId", "republish");
        assertThat(charges.issue(decoder.decode("prescription.created", mapper.writeValueAsBytes(root)))).isEqualTo(original);
        assertThat(count("prescription_charge_delivery")).isEqualTo(2); assertThat(count("CHARGE")).isOne();
        assertThat(count("PAYMENT_REQUEST")).isOne(); assertThat(count("BILLING_EVENT_OUTBOX")).isOne();
    }
    @Test void consume_changedSource_newDeliveryDlqNoOverwrite() throws Exception {
        byte[] body = outpatient(); charges.issue(decoder.decode("prescription.created", body));
        var root = tree(body); root.put("eventId", UUID.randomUUID().toString());
        ((ObjectNode)root.path("payload").path("items").get(0)).put("drugName", "Changed snapshot");
        byte[] invalid = mapper.writeValueAsBytes(root); listener().start(); send(invalid); retainedDlq(invalid);
        assertThat(count("prescription_charge_delivery")).isOne(); assertThat(count("CHARGE")).isOne(); assertThat(count("BILLING_EVENT_OUTBOX")).isOne();
    }
    @Test void consume_sameDeliveryDifferentPrescription_conflictRollsBackNewAccount() throws Exception {
        byte[] body = outpatient(); charges.issue(decoder.decode("prescription.created", body));
        var root = tree(body); var p = (ObjectNode) root.get("payload"); UUID rx = UUID.randomUUID();
        p.put("prescriptionId", rx.toString()).put("sourceId", rx.toString());
        assertThatThrownBy(() -> charges.issue(decoder.decode("prescription.created", mapper.writeValueAsBytes(root))))
                .hasMessage("BILLING_PRESCRIPTION_SOURCE_CONFLICT");
        assertThat(count("CHARGE")).isOne(); assertThat(count("prescription_charge_source")).isOne();
    }
    @Test void issue_sameEpisodeWrongPatient_noClaimOrCharge() throws Exception {
        byte[] body = outpatient(); charges.issue(decoder.decode("prescription.created", body));
        var root = nextPrescription(body); ((ObjectNode)root.get("payload")).put("patientId", UUID.randomUUID().toString());
        assertThatThrownBy(() -> charges.issue(decoder.decode("prescription.created", mapper.writeValueAsBytes(root))))
                .hasMessage("BILLING_PRESCRIPTION_SOURCE_CONFLICT");
        assertThat(count("BILLING_ACCOUNT")).isOne(); assertThat(count("prescription_charge_delivery")).isOne(); assertThat(count("CHARGE")).isOne();
    }
    @Test void issue_twoEpisodesSamePatient_neverSelectsAllUnpaidPatientFees() throws Exception {
        byte[] body = outpatient(); var a = charges.issue(decoder.decode("prescription.created", body));
        var root = nextPrescription(body); ((ObjectNode)root.get("payload")).put("careEpisodeId", UUID.randomUUID().toString());
        var b = charges.issue(decoder.decode("prescription.created", mapper.writeValueAsBytes(root)));
        assertThat(a.accountId()).isNotEqualTo(b.accountId()); assertThat(count("PAYMENT_REQUEST_CHARGE")).isEqualTo(2);
        payments.complete(a.paymentRequestId(), payment("first-episode", "2000"), UUID.randomUUID(), "one-episode");
        assertThat(jdbc.queryForObject("SELECT status FROM PAYMENT_REQUEST WHERE payment_request_id=?", String.class, b.paymentRequestId())).isEqualTo("PENDING");
        assertThat(count("FINANCIAL_CLEARANCE")).isOne();
    }
    @Test void issue_twoGeneratingDepartments_keepAccountCashAttribution() throws Exception {
        byte[] body = outpatient(); var a = charges.issue(decoder.decode("prescription.created", body));
        UUID initial = jdbc.queryForObject("SELECT department_id FROM BILLING_ACCOUNT", UUID.class);
        var root = nextPrescription(body); UUID other = UUID.randomUUID(); ((ObjectNode)root.get("payload")).put("departmentId", other.toString());
        var b = charges.issue(decoder.decode("prescription.created", mapper.writeValueAsBytes(root)));
        assertThat(a.accountId()).isEqualTo(b.accountId());
        assertThat(jdbc.queryForObject("SELECT department_id FROM BILLING_ACCOUNT", UUID.class)).isEqualTo(initial);
        assertThat(jdbc.queryForObject("SELECT department_id FROM CHARGE WHERE charge_id=?", UUID.class, b.chargeId())).isEqualTo(other);
        payments.complete(b.paymentRequestId(), payment("second-department", "2000"), UUID.randomUUID(), "cash-department");
        assertThat(mapper.readTree(fact("payment.completed")).path("payload").path("departmentId").asText()).isEqualTo(initial.toString());
    }
    @Test void issue_closedAccount_newSourceFailsButMatchingReplayStillWorks() throws Exception {
        byte[] body = outpatient(); var c = decoder.decode("prescription.created", body); var issued = charges.issue(c);
        jdbc.update("UPDATE BILLING_ACCOUNT SET status='CHARGE_CLOSED'"); var root = nextPrescription(body);
        assertThatThrownBy(() -> charges.issue(decoder.decode("prescription.created", mapper.writeValueAsBytes(root))))
                .hasMessage("BILLING_ACCOUNT_CHARGE_CLOSED");
        assertThat(charges.issue(c)).isEqualTo(issued); assertThat(count("CHARGE")).isOne(); assertThat(count("prescription_charge_delivery")).isOne();
    }
    @Test void issue_outboxFailure_rollsBackEveryLocalEffect_thenRetrySucceeds() throws Exception {
        var c = decoder.decode("prescription.created", outpatient());
        jdbc.execute("CREATE FUNCTION reject_rx_event() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test rollback'; END $$");
        jdbc.execute("CREATE TRIGGER reject_rx_event BEFORE INSERT ON BILLING_EVENT_OUTBOX FOR EACH ROW EXECUTE FUNCTION reject_rx_event()");
        try {
            assertThatThrownBy(() -> charges.issue(c)).isInstanceOf(RuntimeException.class);
            for (String table : List.of("BILLING_ACCOUNT", "CHARGE", "PAYMENT_REQUEST", "PAYMENT_REQUEST_TARGET", "prescription_charge_source", "prescription_charge_item", "prescription_charge_delivery", "BILLING_EVENT_OUTBOX"))
                assertThat(count(table)).as(table).isZero();
        } finally { jdbc.execute("DROP TRIGGER reject_rx_event ON BILLING_EVENT_OUTBOX"); jdbc.execute("DROP FUNCTION reject_rx_event()"); }
        charges.issue(c); assertThat(count("CHARGE")).isOne();
    }
    @Test void issue_racingNewDeliveries_oneSourceOneInvoice() throws Exception {
        byte[] body = outpatient(); var a = decoder.decode("prescription.created", body);
        var root = tree(body); root.put("eventId", UUID.randomUUID().toString()); var b = decoder.decode("prescription.created", mapper.writeValueAsBytes(root));
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var x = workers.submit(() -> { start.await(); return charges.issue(a); });
            var y = workers.submit(() -> { start.await(); return charges.issue(b); }); start.countDown();
            assertThat(x.get(20, TimeUnit.SECONDS)).isEqualTo(y.get(20, TimeUnit.SECONDS));
        }
        assertThat(count("prescription_charge_delivery")).isEqualTo(2); assertThat(count("CHARGE")).isOne(); assertThat(count("BILLING_EVENT_OUTBOX")).isOne();
    }
    @Test void issue_racingDifferentPrescriptions_sameEpisodeOneAccountSeparateRequests() throws Exception {
        byte[] body = outpatient(); var a = decoder.decode("prescription.created", body);
        var b = decoder.decode("prescription.created", mapper.writeValueAsBytes(nextPrescription(body)));
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var x = workers.submit(() -> { start.await(); return charges.issue(a); });
            var y = workers.submit(() -> { start.await(); return charges.issue(b); }); start.countDown();
            var first = x.get(20, TimeUnit.SECONDS); var second = y.get(20, TimeUnit.SECONDS);
            assertThat(first.accountId()).isEqualTo(second.accountId());
            assertThat(first.chargeId()).isNotEqualTo(second.chargeId());
            assertThat(first.paymentRequestId()).isNotEqualTo(second.paymentRequestId());
        }
        assertThat(count("BILLING_ACCOUNT")).isOne(); assertThat(count("CHARGE")).isEqualTo(2);
        assertThat(count("PAYMENT_REQUEST_CHARGE")).isEqualTo(2); assertThat(count("BILLING_EVENT_OUTBOX")).isEqualTo(2);
    }
    @Test void adapter_withoutTransaction_rejectsBeforeDeliveryClaim() throws Exception {
        var c = decoder.decode("prescription.created", outpatient());
        assertThatThrownBy(() -> sourceStore.claimDelivery(c)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThat(count("prescription_charge_delivery")).isZero(); assertThat(count("BILLING_ACCOUNT")).isZero();
    }
    @Test void issue_unapprovedPriceCode_rollsBackClaimWithoutDefaultPrice() throws Exception {
        var root = tree(outpatient()); ((ObjectNode)root.get("payload")).put("priceCode", "UNAPPROVED");
        assertThatThrownBy(() -> charges.issue(decoder.decode("prescription.created", mapper.writeValueAsBytes(root))))
                .hasMessage("BILLING_PRESCRIPTION_PRICE_CODE_UNSUPPORTED");
        assertThat(count("prescription_charge_delivery")).isZero(); assertThat(count("BILLING_ACCOUNT")).isZero(); assertThat(count("CHARGE")).isZero();
    }
    @Test void consume_listenerRestartAndMalformedRetainedBytes_noLostClaim() throws Exception {
        listener().start(); byte[] body = outpatient(); send(body); drain(1);
        listener().start(); send(body); await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(count("CHARGE")).isOne());
        var root = tree(body); root.put("eventId", UUID.randomUUID().toString()); ((ObjectNode)root.get("payload")).put("totalAmount", 2001);
        byte[] invalid = mapper.writeValueAsBytes(root); send(invalid); retainedDlq(invalid);
        assertThat(count("prescription_charge_delivery")).isOne(); assertThat(count("PAYMENT_REQUEST")).isOne();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"prescription.cancelled", "prescription.expired", "prescription.dispense.failed"})
    void terminal_unpaidOutpatient_voidsOnlyExactChargeWithoutInventingRefund(String key) throws Exception {
        var issued = charges.issue(decoder.decode("prescription.created", outpatient()));
        var other = charges.issue(decoder.decode("prescription.created", mapper.writeValueAsBytes(nextPrescription(outpatient()))));
        var bytes = outpatientTerminal(key);
        listener().start(); rabbit.send("mediflow.events", key, new Message(bytes, new MessageProperties()));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(terminalStatus()).isEqualTo("APPLIED")); listener().stop();
        assertThat(jdbc.queryForObject("SELECT status FROM CHARGE WHERE charge_id=?", String.class, issued.chargeId())).isEqualTo("VOIDED");
        assertThat(jdbc.queryForObject("SELECT status FROM PAYMENT_REQUEST WHERE payment_request_id=?", String.class, issued.paymentRequestId())).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM CHARGE WHERE charge_id=?", String.class, other.chargeId())).isEqualTo("POSTED");
        assertThat(count("PAYMENT_TRANSACTION")).isZero(); assertThat(count("prescription_terminal_refund_due")).isZero();
        assertThat(count("PROCESSED_EVENT")).isZero(); assertThat(count("BILLING_EVENT_OUTBOX")).isEqualTo(2);
    }
    @Test void terminal_paidWithPriorPartialRefund_dueIsOriginalNetAllocation_noFakeCashOut() throws Exception {
        var issued = charges.issue(decoder.decode("prescription.created", outpatient()));
        var a = payments.complete(issued.paymentRequestId(), payment("partial", "750"), UUID.randomUUID(), "paid");
        var b = payments.complete(issued.paymentRequestId(), payment("full", "1250"), UUID.randomUUID(), "paid");
        refunds.refund(a.transactionId(), new com.mediflow.billing.application.dto.request.RefundLedgerPaymentRequest(
                "prior-refund", new BigDecimal("250"), "Cash actually returned", PaymentMethod.CASH), UUID.randomUUID(), "prior-refund");
        var bytes = outpatientTerminal("prescription.dispense.failed");
        terminals.apply(decoder.decodeTerminal("prescription.dispense.failed", bytes));
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM prescription_terminal_refund_due", BigDecimal.class)).isEqualByComparingTo("1750");
        assertThat(count("prescription_terminal_refund_due")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL AND revoked_at>=granted_at FROM FINANCIAL_CLEARANCE", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PAYMENT_TRANSACTION WHERE transaction_type='REFUND'", Long.class)).isOne();
        assertThat(adjustments.get(decoder.decode("prescription.created", outpatient()).prescriptionId()).refundsDue())
                .extracting(com.mediflow.billing.application.dto.response.PrescriptionAdjustmentDTO.RefundDue::amount)
                .containsExactlyInAnyOrder(new BigDecimal("500.00"), new BigDecimal("1250.00"));
        refunds.refund(a.transactionId(), new com.mediflow.billing.application.dto.request.RefundLedgerPaymentRequest(
                "remaining-refund", new BigDecimal("500"), "Cash actually returned", PaymentMethod.CASH), UUID.randomUUID(), "returned");
        assertThat(adjustments.get(decoder.decode("prescription.created", outpatient()).prescriptionId()).refundsDue()).hasSize(1);
        refunds.refund(b.transactionId(), new com.mediflow.billing.application.dto.request.RefundLedgerPaymentRequest(
                "last-refund", new BigDecimal("1250"), "Cash actually returned", PaymentMethod.CASH), UUID.randomUUID(), "returned");
        assertThat(adjustments.get(decoder.decode("prescription.created", outpatient()).prescriptionId()).refundsDue()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM PAYMENT_TRANSACTION WHERE transaction_type='REFUND'", BigDecimal.class)).isEqualByComparingTo("2000");
        assertThat(jdbc.queryForObject("SELECT amount FROM PAYMENT_TRANSACTION WHERE transaction_id=?", BigDecimal.class, a.transactionId())).isEqualByComparingTo("750");
        terminals.apply(decoder.decodeTerminal("prescription.dispense.failed", bytes));
        var replay = tree(bytes); replay.put("eventId", UUID.randomUUID().toString());
        jdbc.update("UPDATE BILLING_ACCOUNT SET status='CLOSED'");
        terminals.apply(decoder.decodeTerminal("prescription.dispense.failed", mapper.writeValueAsBytes(replay)));
        assertThat(count("prescription_terminal_delivery")).isEqualTo(2); assertThat(count("prescription_terminal_refund_due")).isEqualTo(2);
    }
    @Test void terminal_earlyAdmission_persistsThenVoidsChargeInCreationTransaction() throws Exception {
        var bytes = PrescriptionTerminalContractTest.fixture("prescription.cancelled");
        terminals.apply(decoder.decodeTerminal("prescription.cancelled", bytes));
        assertThat(terminalStatus()).isEqualTo("PENDING"); assertThat(count("CHARGE")).isZero();
        charges.issue(decoder.decode("prescription.created", PrescriptionChargeContractTest.fixture()));
        assertThat(terminalStatus()).isEqualTo("APPLIED"); assertThat(jdbc.queryForObject("SELECT status FROM CHARGE", String.class)).isEqualTo("VOIDED");
        assertThat(count("PAYMENT_REQUEST")).isZero(); assertThat(count("PAYMENT_TRANSACTION")).isZero();
    }
    @Test void terminal_invalidEarlySnapshot_quarantinedWithoutPoisoningValidCreation() throws Exception {
        var root = tree(PrescriptionTerminalContractTest.fixture("prescription.cancelled"));
        ((ObjectNode)root.path("payload")).put("patientId", UUID.randomUUID().toString());
        terminals.apply(decoder.decodeTerminal("prescription.cancelled", mapper.writeValueAsBytes(root)));
        charges.issue(decoder.decode("prescription.created", PrescriptionChargeContractTest.fixture()));
        assertThat(terminalStatus()).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT status FROM CHARGE", String.class)).isEqualTo("POSTED");
    }
    @Test void terminal_knownChangedSnapshot_rollsBackClaimAndDoesNotTouchCharge() throws Exception {
        charges.issue(decoder.decode("prescription.created", outpatient()));
        var root = tree(outpatientTerminal("prescription.cancelled"));
        ((ObjectNode)root.path("payload").path("items").get(0)).put("drugName", "different");
        assertThatThrownBy(() -> terminals.apply(decoder.decodeTerminal("prescription.cancelled", mapper.writeValueAsBytes(root))))
                .hasMessage("BILLING_PRESCRIPTION_TERMINAL_CONFLICT");
        assertThat(count("prescription_terminal_delivery")).isZero(); assertThat(count("prescription_terminal_source")).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM CHARGE", String.class)).isEqualTo("POSTED");
    }
    @Test void terminal_filledIsAbsorbing_noCancellationOrEarnedRevenue() throws Exception {
        charges.issue(decoder.decode("prescription.created", PrescriptionChargeContractTest.fixture()));
        terminals.apply(decoder.decodeTerminal("prescription.filled", PrescriptionTerminalContractTest.fixture("prescription.filled")));
        assertThat(terminalStatus()).isEqualTo("APPLIED");
        assertThatThrownBy(() -> terminals.apply(decoder.decodeTerminal("prescription.cancelled", PrescriptionTerminalContractTest.fixture("prescription.cancelled"))))
                .hasMessage("BILLING_PRESCRIPTION_TERMINAL_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT status FROM CHARGE", String.class)).isEqualTo("POSTED");
        assertThat(count("prescription_terminal_delivery")).isOne(); assertThat(count("BILLING_EVENT_OUTBOX")).isZero();
    }
    @Test void terminal_futureOrBeforeCreation_deniedWithoutClaim() throws Exception {
        charges.issue(decoder.decode("prescription.created", outpatient()));
        for (String at : List.of("2026-09-01T00:00:00Z", "2099-01-01T00:00:00Z")) {
            var root = tree(outpatientTerminal("prescription.cancelled")); root.put("occurredAt", at);
            ((ObjectNode)root.path("payload")).put("cancelledAt", at);
            assertThatThrownBy(() -> terminals.apply(decoder.decodeTerminal("prescription.cancelled", mapper.writeValueAsBytes(root))))
                    .isInstanceOf(com.mediflow.billing.domain.exception.BillingRuleException.class);
        }
        assertThat(count("prescription_terminal_delivery")).isZero();
    }
    @Test void terminal_closedAccount_doesNotInventLateAdjustment() throws Exception {
        charges.issue(decoder.decode("prescription.created", outpatient())); jdbc.update("UPDATE BILLING_ACCOUNT SET status='CHARGE_CLOSED'");
        assertThatThrownBy(() -> terminals.apply(decoder.decodeTerminal("prescription.expired", outpatientTerminal("prescription.expired"))))
                .hasMessage("BILLING_PRESCRIPTION_ADJUSTMENT_ACCOUNT_CLOSED");
        assertThat(count("prescription_terminal_delivery")).isZero(); assertThat(jdbc.queryForObject("SELECT status FROM CHARGE", String.class)).isEqualTo("POSTED");
    }
    @Test void terminal_storageFailure_atomicRollbackThenRetry() throws Exception {
        var issued = charges.issue(decoder.decode("prescription.created", outpatient()));
        payments.complete(issued.paymentRequestId(), payment("paid", "2000"), UUID.randomUUID(), "paid");
        jdbc.execute("CREATE FUNCTION reject_rx_adjustment() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test rollback'; END $$");
        jdbc.execute("CREATE TRIGGER reject_rx_adjustment BEFORE UPDATE ON prescription_terminal_source FOR EACH ROW EXECUTE FUNCTION reject_rx_adjustment()");
        var command = decoder.decodeTerminal("prescription.cancelled", outpatientTerminal("prescription.cancelled"));
        try {
            assertThatThrownBy(() -> terminals.apply(command)).isInstanceOf(RuntimeException.class);
            assertThat(count("prescription_terminal_source")).isZero(); assertThat(count("prescription_terminal_delivery")).isZero();
            assertThat(count("prescription_terminal_refund_due")).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM CHARGE", String.class)).isEqualTo("POSTED");
            assertThat(jdbc.queryForObject("SELECT revoked_at FROM FINANCIAL_CLEARANCE", java.sql.Timestamp.class)).isNull();
        } finally { jdbc.execute("DROP TRIGGER reject_rx_adjustment ON prescription_terminal_source"); jdbc.execute("DROP FUNCTION reject_rx_adjustment()"); }
        terminals.apply(command); assertThat(terminalStatus()).isEqualTo("APPLIED");
    }
    @Test void terminal_racingCreationAndCancellation_oneAdjustedSource() throws Exception {
        var creation = decoder.decode("prescription.created", outpatient());
        var terminal = decoder.decodeTerminal("prescription.cancelled", outpatientTerminal("prescription.cancelled"));
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> { start.await(); return charges.issue(creation); });
            var b = workers.submit(() -> { start.await(); terminals.apply(terminal); return true; }); start.countDown();
            a.get(20, TimeUnit.SECONDS); b.get(20, TimeUnit.SECONDS);
        }
        assertThat(terminalStatus()).isEqualTo("APPLIED"); assertThat(count("CHARGE")).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM CHARGE", String.class)).isEqualTo("VOIDED");
    }
    @Test void terminal_racingPayment_serializedOutcomeAndNoFakeRefund() throws Exception {
        var issued = charges.issue(decoder.decode("prescription.created", outpatient()));
        var terminal = decoder.decodeTerminal("prescription.cancelled", outpatientTerminal("prescription.cancelled"));
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> { start.await(); terminals.apply(terminal); return true; });
            var b = workers.submit(() -> {
                start.await();
                try { payments.complete(issued.paymentRequestId(), payment("race", "2000"), UUID.randomUUID(), "race"); return true; }
                catch (com.mediflow.billing.domain.exception.BillingRuleException expected) {
                    assertThat(expected.getCode()).isEqualTo("BILLING_REQUEST_NOT_PAYABLE"); return false;
                }
            }); start.countDown(); a.get(20, TimeUnit.SECONDS); boolean paymentWon = b.get(20, TimeUnit.SECONDS);
            assertThat(count("PAYMENT_TRANSACTION")).isEqualTo(paymentWon ? 1 : 0);
            assertThat(count("prescription_terminal_refund_due")).isEqualTo(paymentWon ? 1 : 0);
            if (paymentWon) assertThat(jdbc.queryForObject("SELECT revoked_at>=granted_at FROM FINANCIAL_CLEARANCE", Boolean.class)).isTrue();
        }
        assertThat(terminalStatus()).isEqualTo("APPLIED"); assertThat(jdbc.queryForObject("SELECT status FROM PAYMENT_REQUEST", String.class)).isEqualTo("CANCELLED");
    }
    @Test void terminal_reusedCreationDeliveryId_rejectedAcrossLifecycleTables() throws Exception {
        var creation = decoder.decode("prescription.created", outpatient()); charges.issue(creation);
        var root = tree(outpatientTerminal("prescription.cancelled")); root.put("eventId", creation.eventId().toString());
        assertThatThrownBy(() -> terminals.apply(decoder.decodeTerminal("prescription.cancelled", mapper.writeValueAsBytes(root))))
                .hasMessage("BILLING_PRESCRIPTION_DELIVERY_CONFLICT");
        assertThat(count("prescription_terminal_source")).isZero(); assertThat(count("prescription_terminal_delivery")).isZero();
    }
    @Test void creation_reusesEarlyTerminalDeliveryId_rejectsWithoutMoneyMutation() throws Exception {
        var terminal = decoder.decodeTerminal("prescription.cancelled", outpatientTerminal("prescription.cancelled")); terminals.apply(terminal);
        var root = tree(outpatient()); root.put("eventId", terminal.snapshot().eventId().toString());
        assertThatThrownBy(() -> charges.issue(decoder.decode("prescription.created", mapper.writeValueAsBytes(root))))
                .hasMessage("BILLING_PRESCRIPTION_DELIVERY_CONFLICT");
        assertThat(count("CHARGE")).isZero(); assertThat(terminalStatus()).isEqualTo("PENDING");
    }
    @Test void terminal_oldUnverifiedCreation_requiresActualCreationReplayBeforeAdjustment() throws Exception {
        var creation = decoder.decode("prescription.created", outpatient()); charges.issue(creation);
        jdbc.update("UPDATE prescription_charge_source SET snapshot_fingerprint=NULL");
        var terminal = decoder.decodeTerminal("prescription.cancelled", outpatientTerminal("prescription.cancelled"));
        assertThatThrownBy(() -> terminals.apply(terminal)).hasMessage("BILLING_PRESCRIPTION_CREATION_PROOF_REQUIRED");
        assertThat(count("prescription_terminal_source")).isZero(); charges.issue(creation); terminals.apply(terminal);
        assertThat(terminalStatus()).isEqualTo("APPLIED");
    }
    private String terminalStatus() { return jdbc.queryForObject("SELECT status FROM prescription_terminal_source", String.class); }
    private byte[] outpatientTerminal(String key) throws Exception {
        // Consumer mutation scenario, not a new claimed producer fixture.
        var root = tree(PrescriptionTerminalContractTest.fixture(key)); var p = (ObjectNode) root.path("payload");
        var original = mapper.readTree(outpatient()).path("payload");
        for (String field : List.of("recordId", "admissionId", "careContext", "careEpisodeType", "careEpisodeId")) {
            if (original.has(field)) p.set(field, original.get(field)); else p.remove(field);
        }
        return mapper.writeValueAsBytes(root);
    }
    private ObjectNode nextPrescription(byte[] bytes) throws Exception {
        var root = tree(bytes); root.put("eventId", UUID.randomUUID().toString()); UUID id = UUID.randomUUID();
        ((ObjectNode)root.get("payload")).put("prescriptionId", id.toString()).put("sourceId", id.toString()); return root;
    }
    private byte[] outpatient() throws Exception {
        return PrescriptionChargeContractTest.outpatientFixture();
    }
    private ObjectNode tree(byte[] bytes) throws Exception { return (ObjectNode) mapper.readTree(bytes); }
    private CompleteLedgerPaymentRequest payment(String key, String amount) { return new CompleteLedgerPaymentRequest(key, new BigDecimal(amount), "VND", PaymentMethod.CASH, null); }
    private String fact(String key) { return jdbc.queryForObject("SELECT payload FROM BILLING_EVENT_OUTBOX WHERE routing_key=?", String.class, key); }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }
    private org.springframework.amqp.rabbit.listener.MessageListenerContainer listener() { return registry.getListenerContainer("billingEvents"); }
    private void send(byte[] body) { rabbit.send("mediflow.events", "prescription.created", new Message(body, new MessageProperties())); }
    private void drain(long expected) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(count("prescription_charge_delivery")).isEqualTo(expected)); listener().stop();
    }
    private void retainedDlq(byte[] expected) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(admin.getQueueInfo(DLQ).getMessageCount()).isEqualTo(1));
        listener().stop(); assertThat(rabbit.receive(DLQ, 1000).getBody()).isEqualTo(expected);
    }
}
