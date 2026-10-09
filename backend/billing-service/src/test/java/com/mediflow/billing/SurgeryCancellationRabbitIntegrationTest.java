package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.billing.application.dto.request.CompleteLedgerPaymentRequest;
import com.mediflow.billing.application.dto.request.RefundLedgerPaymentRequest;
import com.mediflow.billing.application.port.in.*;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.infrastructure.messaging.SurgeryCancellationDecoder;
import com.mediflow.billing.infrastructure.messaging.SurgeryChargeDecoder;
import com.mediflow.billing.infrastructure.pricing.CareFinancePriceProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
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
import org.springframework.http.*;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "mediflow.jwt.secret=cancellation-test-credential-at-least-32-bytes",
        "mediflow.billing.ledger.enabled=true", "mediflow.billing.surgery-charge-consumer.enabled=true",
        "mediflow.billing.refunds.enabled=true", "mediflow.billing.clearance-lookup.enabled=true",
        "mediflow.billing.outbox.enabled=false", "mediflow.billing.outbox.metrics-enabled=false", "mediflow.billing.outbox.maintenance-enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false", "mediflow.billing.surgery-cancellation-recovery.delay-ms=3600000",
        "mediflow.billing.rabbit.retry.initial-interval-ms=10", "mediflow.billing.rabbit.retry.max-interval-ms=20"})
class SurgeryCancellationRabbitIntegrationTest {
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
    @Autowired RefundLedgerPaymentUseCase refunds;
    @Autowired ProcessSurgeryCancellationUseCase cancellations;
    @Autowired GetSurgeryCancellationUseCase reads;
    @Autowired SurgeryChargeDecoder creationDecoder;
    @Autowired SurgeryCancellationDecoder cancellationDecoder;
    @Autowired CareFinancePriceProperties catalog;
    @Autowired ObjectMapper mapper;
    @Autowired TestRestTemplate http;
    @Autowired SurgeryChargeUseCase outcomes;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.mediflow.billing.application.port.out.SurgeryPlannedRequestRepositoryPort sourceRepository;
    @Autowired com.mediflow.billing.application.port.out.PriceCatalogPort prices;
    @Autowired com.mediflow.billing.application.port.out.LedgerEventPort events;
    private final UUID cashier = UUID.randomUUID();

    @BeforeEach void reset() {
        listener().stop(); admin.purgeQueue("billing.q"); admin.purgeQueue("billing.dlq");
        jdbc.execute("TRUNCATE surgery_cancellation_source,BILLING_ACCOUNT,BILLING_EVENT_OUTBOX CASCADE");
        catalog.setPrices(Map.of("PRICE", new CareFinancePriceProperties.Price("Synthetic procedure", new BigDecimal("100")),
                "EXTRA_PRICE", new CareFinancePriceProperties.Price("Synthetic item", new BigDecimal("50"))));
    }
    @AfterEach void stop() { listener().stop(); jdbc.execute("ALTER TABLE surgery_cancellation_source DROP CONSTRAINT IF EXISTS test_cancel_rollback"); }

    @ParameterizedTest @ValueSource(strings = {"admission", "outpatient"})
    void cancel_paidCase_voidsAndRevokesButOnlyActualCashierRefundCreatesCashOut(String context) throws Exception {
        UUID request = issue(context, true);
        UUID actualCase = creationDecoder.decode("surgery.case.created", SurgeryChargeContractTest.fixture(context)).surgeryCaseId();
        UUID original = pay(request, "150", "initial");
        UUID clearance = jdbc.queryForObject("SELECT clearance_id FROM FINANCIAL_CLEARANCE", UUID.class);
        sendCancellation(SurgeryCancellationContractTest.fixture(context)); applied();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM CHARGE WHERE status='VOIDED'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM PAYMENT_REQUEST", String.class)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM FINANCIAL_CLEARANCE", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PAYMENT_TRANSACTION WHERE transaction_type='REFUND'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM BILLING_EVENT_OUTBOX WHERE routing_key='payment.refunded'", Integer.class)).isZero();
        var due = reads.get(actualCase);
        assertThat(due.status()).isEqualTo("APPLIED"); assertThat(due.refundsDue()).hasSize(1);
        assertThat(due.refundsDue().getFirst().amount()).isEqualByComparingTo("150");
        assertThat(due.refundsDue().getFirst().originalTransactionId()).isEqualTo(original);
        var observation = http.exchange("/api/v1/billing/financial-clearances/" + clearance + "/lookup", HttpMethod.GET,
                new HttpEntity<>(serviceHeaders()), String.class);
        assertThat(observation.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(observation.getBody()).path("data").path("eligible").asBoolean()).isFalse();
        assertThatThrownBy(() -> pay(request, "1", "after-cancel")).isInstanceOf(BillingRuleException.class);
        var refund = refunds.refund(original, new RefundLedgerPaymentRequest("actual-refund", new BigDecimal("150"), "Cash returned after cancellation", PaymentMethod.CASH), cashier, "refund-trace");
        assertThat(reads.get(actualCase).refundsDue()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PAYMENT_ALLOCATION WHERE transaction_id=?", Integer.class, refund.refundTransactionId())).isEqualTo(2);
        var fact = mapper.readTree(jdbc.queryForObject("SELECT payload FROM BILLING_EVENT_OUTBOX WHERE routing_key='payment.refunded'", String.class));
        assertThat(fact.path("payload").path("reason").asText()).isEqualTo("CASHIER_RECORDED_REFUND");
        assertThat(fact.toString()).doesNotContain("Patient request", "Cash returned");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM BILLING_EVENT_OUTBOX WHERE publication_enabled", Integer.class)).isZero();
    }
    @Test void cancel_priorPartialRefund_onlyRemainingOwnAllocationsAreDue() throws Exception {
        UUID request = issue("admission", true), original = pay(request, "150", "initial");
        refunds.refund(original, new RefundLedgerPaymentRequest("prior", new BigDecimal("40"), "Prior actual refund", PaymentMethod.CASH), cashier, "prior-trace");
        sendCancellation(SurgeryCancellationContractTest.fixture("admission")); applied();
        assertThat(reads.get(caseId()).refundsDue().getFirst().amount()).isEqualByComparingTo("110");
        refunds.refund(original, new RefundLedgerPaymentRequest("remaining", new BigDecimal("110"), "Remaining actual refund", PaymentMethod.CASH), cashier, "remaining-trace");
        assertThat(reads.get(caseId()).refundsDue()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM PAYMENT_TRANSACTION WHERE transaction_type='REFUND'", BigDecimal.class)).isEqualByComparingTo("150");
    }
    @Test void cancel_beforeCreation_commitsPendingThenRealCreationRecoversWithoutCashFabrication() throws Exception {
        sendCancellation(SurgeryCancellationContractTest.fixture("admission"));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("surgery_cancellation_delivery")).isOne());
        assertThat(reads.get(caseId()).status()).isEqualTo("PENDING");
        assertThat(count("CHARGE")).isZero();
        send("surgery.case.created", SurgeryChargeContractTest.fixture("admission")); applied();
        assertThat(jdbc.queryForObject("SELECT status FROM CHARGE", String.class)).isEqualTo("VOIDED");
        assertThat(reads.get(caseId()).refundsDue()).isEmpty(); assertThat(count("PAYMENT_TRANSACTION")).isZero();
        assertThat(count("BILLING_EVENT_OUTBOX")).isZero();
        UUID request=jdbc.queryForObject("SELECT payment_request_id FROM PAYMENT_REQUEST",UUID.class);
        assertThatThrownBy(() -> pay(request,"100","late-creation-payment")).isInstanceOf(BillingRuleException.class);
    }
    @Test void cancel_badEarlyPatient_quarantinesWithoutPoisoningValidLaterCreation() throws Exception {
        var root = tree(SurgeryCancellationContractTest.fixture("admission"));
        ((ObjectNode) root.get("payload")).put("patientId", UUID.randomUUID().toString());
        sendCancellation(mapper.writeValueAsBytes(root));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("surgery_cancellation_delivery")).isOne());
        send("surgery.case.created", SurgeryChargeContractTest.fixture("admission"));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(reads.get(caseId()).status()).isEqualTo("REJECTED"));
        assertThat(jdbc.queryForObject("SELECT status FROM CHARGE", String.class)).isEqualTo("POSTED");
        assertThat(jdbc.queryForObject("SELECT status FROM PAYMENT_REQUEST", String.class)).isEqualTo("PENDING");
        assertThat(count("surgery_charge_delivery")).isOne(); assertThat(count("surgery_cancellation_refund_due")).isZero();
        assertThat(admin.getQueueInfo("billing.dlq").getMessageCount()).isZero();
    }
    @Test void cancel_sameSourceNewDelivery_dedupesEvenAfterAccountClosed() throws Exception {
        issue("admission", false);
        byte[] body = SurgeryCancellationContractTest.fixture("admission"); sendCancellation(body); applied();
        jdbc.update("UPDATE BILLING_ACCOUNT SET status='CLOSED'");
        var root = tree(body); root.put("eventId", UUID.randomUUID().toString()); sendCancellation(mapper.writeValueAsBytes(root));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("surgery_cancellation_delivery")).isEqualTo(2));
        assertThat(count("surgery_cancellation_source")).isOne(); assertThat(count("PAYMENT_TRANSACTION")).isZero();
    }
    @Test void cancel_changedNarrativeSameSource_conflictsWithoutMarkerOrNewAdjustment() throws Exception {
        issue("admission", false); byte[] original = SurgeryCancellationContractTest.fixture("admission"); sendCancellation(original); applied();
        var root = tree(original); root.put("eventId", UUID.randomUUID().toString()); ((ObjectNode)root.get("payload")).put("reason", "Changed private text");
        byte[] invalid = mapper.writeValueAsBytes(root); sendCancellation(invalid); dlq(invalid);
        assertThat(count("surgery_cancellation_delivery")).isOne(); assertThat(count("surgery_cancellation_source")).isOne();
    }
    @Test void cancel_knownWrongPatient_rejectsAllEffects() throws Exception {
        issue("admission", false); var root = tree(SurgeryCancellationContractTest.fixture("admission"));
        ((ObjectNode)root.get("payload")).put("patientId", UUID.randomUUID().toString()); byte[] invalid = mapper.writeValueAsBytes(root);
        sendCancellation(invalid); dlq(invalid);
        assertThat(count("surgery_cancellation_source")).isZero(); assertThat(count("surgery_cancellation_delivery")).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM CHARGE", String.class)).isEqualTo("POSTED");
    }
    @Test void cancel_signedAccountActorWithoutStaff_retainsNullProducerIdentity() throws Exception {
        issue("admission",false); var root=tree(SurgeryCancellationContractTest.fixture("admission"));
        ((ObjectNode)root.get("payload")).putNull("cancelledByStaffId"); sendCancellation(mapper.writeValueAsBytes(root)); applied();
        assertThat(jdbc.queryForObject("SELECT cancelled_by_staff_id FROM surgery_cancellation_source",UUID.class)).isNull();
        assertThat(reads.get(caseId()).status()).isEqualTo("APPLIED");
    }
    @Test void cancel_futureBusinessFact_rejectsBeforeVoidOrDeliveryReceipt() throws Exception {
        issue("admission",false); var root=tree(SurgeryCancellationContractTest.fixture("admission"));
        String future=Instant.now().plusSeconds(60).toString(); root.put("occurredAt",future); ((ObjectNode)root.get("payload")).put("cancelledAt",future);
        byte[] body=mapper.writeValueAsBytes(root); sendCancellation(body); dlq(body);
        assertThat(count("surgery_cancellation_source")).isZero(); assertThat(count("surgery_cancellation_delivery")).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM CHARGE",String.class)).isEqualTo("POSTED");
    }
    @Test void cancel_oneNanosecondAfterRequest_usesExactProducerInstant_notRoundedSqlTime() throws Exception {
        var creation=tree(SurgeryChargeContractTest.fixture("admission"));
        String requested="2026-10-07T01:00:00.123456789Z", cancelled="2026-10-07T01:00:00.123456790Z";
        creation.put("occurredAt",requested); ((ObjectNode)creation.get("payload")).put("requestedAt",requested);
        charges.issue(creationDecoder.decode("surgery.case.created",mapper.writeValueAsBytes(creation)));
        var cancellation=tree(SurgeryCancellationContractTest.fixture("admission")); cancellation.put("occurredAt",cancelled);
        ((ObjectNode)cancellation.get("payload")).put("cancelledAt",cancelled);
        cancellations.receive(cancellationDecoder.decode("surgery.cancelled",mapper.writeValueAsBytes(cancellation)));
        assertThat(reads.get(caseId()).status()).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("SELECT requested_at_iso FROM surgery_charge_source",String.class)).isEqualTo(requested);
    }
    @Test void cancel_oldRoundedHistory_withinUncertainty_doesNotInventExactChronology() throws Exception {
        var creation=tree(SurgeryChargeContractTest.fixture("admission"));
        creation.put("occurredAt","2026-10-07T01:00:00.123456789Z"); ((ObjectNode)creation.get("payload")).put("requestedAt","2026-10-07T01:00:00.123456789Z");
        charges.issue(creationDecoder.decode("surgery.case.created",mapper.writeValueAsBytes(creation)));
        jdbc.update("UPDATE surgery_charge_source SET requested_at_iso=NULL"); // historical V9/V10 state
        var cancellation=tree(SurgeryCancellationContractTest.fixture("admission")); cancellation.put("occurredAt","2026-10-07T01:00:00.123456790Z");
        ((ObjectNode)cancellation.get("payload")).put("cancelledAt","2026-10-07T01:00:00.123456790Z");
        var command=cancellationDecoder.decode("surgery.cancelled",mapper.writeValueAsBytes(cancellation));
        assertThatThrownBy(() -> cancellations.receive(command)).isInstanceOf(BillingRuleException.class)
                .extracting(e -> ((BillingRuleException)e).getCode()).isEqualTo("BILLING_SURGERY_CANCELLATION_TIME_UNVERIFIABLE");
        assertThat(count("surgery_cancellation_source")).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM CHARGE",String.class)).isEqualTo("POSTED");
    }
    @Test void cancel_alreadyPerformed_rejectsWithoutRefund() throws Exception {
        issue("admission", false); jdbc.update("UPDATE CHARGE SET reconciled_result_id=?", UUID.randomUUID());
        byte[] invalid = SurgeryCancellationContractTest.fixture("admission"); sendCancellation(invalid); dlq(invalid);
        assertThat(count("surgery_cancellation_source")).isZero(); assertThat(count("PAYMENT_TRANSACTION")).isZero();
    }
    @Test void recovery_dueBadAndGoodEarlyFacts_quarantinesOnlyBad_andRestoresExactNanoseconds() throws Exception {
        var first=tree(SurgeryCancellationContractTest.fixture("admission"));
        ((ObjectNode)first.get("payload")).put("patientId",UUID.randomUUID().toString());
        cancellations.receive(cancellationDecoder.decode("surgery.cancelled",mapper.writeValueAsBytes(first)));
        var second=tree(SurgeryCancellationContractTest.fixture("admission"));
        UUID secondCase=UUID.randomUUID(), secondRequest=UUID.randomUUID();
        second.put("eventId",UUID.randomUUID().toString()).put("occurredAt","2026-10-07T01:05:00.123456789Z");
        ((ObjectNode)second.get("payload")).put("surgeryCaseId",secondCase.toString()).put("surgeryRequestId",secondRequest.toString())
                .put("cancellationId",UUID.randomUUID().toString()).put("cancelledAt","2026-10-07T01:05:00.123456789Z");
        cancellations.receive(cancellationDecoder.decode("surgery.cancelled",mapper.writeValueAsBytes(second)));
        issueWithPreviousWriter(SurgeryChargeContractTest.fixture("admission"));
        var creation=tree(SurgeryChargeContractTest.fixture("admission")); creation.put("eventId",UUID.randomUUID().toString());
        ((ObjectNode)creation.get("payload")).put("surgeryCaseId",secondCase.toString()).put("sourceId",secondCase.toString()).put("surgeryRequestId",secondRequest.toString());
        issueWithPreviousWriter(mapper.writeValueAsBytes(creation));
        jdbc.update("UPDATE surgery_cancellation_source SET retry_at=now()");
        cancellations.recoverPending();
        assertThat(reads.get(caseId()).status()).isEqualTo("REJECTED");
        assertThat(reads.get(secondCase).status()).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("SELECT cancelled_at_iso FROM surgery_cancellation_source WHERE surgery_case_id=?",String.class,secondCase)).isEqualTo("2026-10-07T01:05:00.123456789Z");
        assertThat(jdbc.queryForObject("SELECT status FROM CHARGE WHERE source_id=?",String.class,caseId())).isEqualTo("POSTED");
        assertThat(jdbc.queryForObject("SELECT status FROM CHARGE WHERE source_id=?",String.class,secondCase)).isEqualTo("VOIDED");
    }
    @Test void cancel_mixedSelectedRequest_rejectsWithoutCancellingOtherCare() throws Exception {
        UUID request=issue("admission",false);
        var creation=tree(SurgeryChargeContractTest.fixture("admission")); creation.put("eventId",UUID.randomUUID().toString());
        UUID otherCase=UUID.randomUUID();
        ((ObjectNode)creation.get("payload")).put("surgeryCaseId",otherCase.toString()).put("sourceId",otherCase.toString()).put("surgeryRequestId",UUID.randomUUID().toString());
        charges.issue(creationDecoder.decode("surgery.case.created",mapper.writeValueAsBytes(creation)));
        UUID otherCharge=jdbc.queryForObject("SELECT charge_id FROM CHARGE WHERE source_id=?",UUID.class,otherCase);
        jdbc.update("INSERT INTO PAYMENT_REQUEST_CHARGE(payment_request_id,charge_id,requested_amount) VALUES (?,?,100)",request,otherCharge);
        var c=cancellationDecoder.decode("surgery.cancelled",SurgeryCancellationContractTest.fixture("admission"));
        assertThatThrownBy(() -> cancellations.receive(c)).isInstanceOf(BillingRuleException.class)
                .extracting(e -> ((BillingRuleException)e).getCode()).isEqualTo("BILLING_SURGERY_REQUEST_MISMATCH");
        assertThat(count("surgery_cancellation_source")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM CHARGE WHERE status='POSTED'",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PAYMENT_REQUEST WHERE status='PENDING'",Integer.class)).isEqualTo(2);
    }
    @Test void cancel_applyFailure_rollsBackVoidRequestRevocationAndDue_thenRetainedBytesRecover() throws Exception {
        UUID request = issue("admission", true); pay(request, "150", "initial");
        jdbc.execute("ALTER TABLE surgery_cancellation_source ADD CONSTRAINT test_cancel_rollback CHECK(status<>'APPLIED') NOT VALID");
        byte[] body = SurgeryCancellationContractTest.fixture("admission"); sendCancellation(body); dlq(body);
        assertThat(count("surgery_cancellation_source")).isZero(); assertThat(count("surgery_cancellation_refund_due")).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM PAYMENT_REQUEST", String.class)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM CHARGE WHERE status='POSTED'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NULL FROM FINANCIAL_CLEARANCE", Boolean.class)).isTrue();
        jdbc.execute("ALTER TABLE surgery_cancellation_source DROP CONSTRAINT test_cancel_rollback"); sendCancellation(body); applied();
        assertThat(reads.get(caseId()).refundsDue().getFirst().amount()).isEqualByComparingTo("150");
    }
    @Test void earlyCancellation_joinedAdjustmentFailure_rollsBackWholeIssuance_butPreservesPriorPendingForRetry() throws Exception {
        cancellations.receive(cancellationDecoder.decode("surgery.cancelled",SurgeryCancellationContractTest.fixture("admission")));
        jdbc.execute("ALTER TABLE surgery_cancellation_source ADD CONSTRAINT test_cancel_rollback CHECK(status<>'APPLIED') NOT VALID");
        byte[] creation=SurgeryChargeContractTest.fixture("admission"); send("surgery.case.created",creation); dlq(creation);
        assertThat(count("surgery_cancellation_delivery")).isOne();
        assertThat(reads.get(caseId()).status()).isEqualTo("PENDING");
        assertThat(count("surgery_charge_delivery")).isZero(); assertThat(count("CHARGE")).isZero();
        assertThat(count("PAYMENT_REQUEST")).isZero(); assertThat(count("BILLING_ACCOUNT")).isZero(); assertThat(count("BILLING_EVENT_OUTBOX")).isZero();
        jdbc.execute("ALTER TABLE surgery_cancellation_source DROP CONSTRAINT test_cancel_rollback");
        send("surgery.case.created",creation); applied();
        assertThat(count("surgery_charge_delivery")).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM PAYMENT_REQUEST",String.class)).isEqualTo("CANCELLED");
        assertThat(count("BILLING_EVENT_OUTBOX")).isZero();
    }
    @Test void cancel_twoWorkers_andPaymentRace_noLatePaymentOrDuplicateObligation() throws Exception {
        UUID request = issue("admission", false);
        var command = cancellationDecoder.decode("surgery.cancelled", SurgeryCancellationContractTest.fixture("admission"));
        var latch = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(3)) {
            var first = pool.submit(() -> { latch.await(); cancellations.receive(command); return true; });
            var second = pool.submit(() -> { latch.await(); cancellations.receive(command); return true; });
            var payment = pool.submit(() -> { latch.await(); try { pay(request,"100","race"); return true; }
                catch (BillingRuleException denied) { assertThat(denied.getCode()).isEqualTo("BILLING_REQUEST_NOT_PAYABLE"); return false; } });
            latch.countDown(); assertThat(first.get(15,TimeUnit.SECONDS)).isTrue(); assertThat(second.get(15,TimeUnit.SECONDS)).isTrue();
            boolean paid = payment.get(15,TimeUnit.SECONDS);
            assertThat(count("PAYMENT_TRANSACTION")).isEqualTo(paid ? 1 : 0);
            assertThat(reads.get(caseId()).refundsDue()).hasSize(paid ? 1 : 0);
        }
        assertThat(count("surgery_cancellation_source")).isOne(); assertThat(count("surgery_cancellation_delivery")).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM FINANCIAL_CLEARANCE WHERE revoked_at<granted_at",Integer.class)).isZero();
    }
    @RepeatedTest(3) void cancel_completionRace_hasOneTerminalLedgerOutcome_neverRepostsVoidedCharge() throws Exception {
        issue("admission",false);
        var cancel=cancellationDecoder.decode("surgery.cancelled",SurgeryCancellationContractTest.fixture("admission"));
        var source=creationDecoder.decode("surgery.case.created",SurgeryChargeContractTest.fixture("admission"));
        var result=UUID.randomUUID();
        var completed=new com.mediflow.billing.application.event.SurgeryCompletedEvent(UUID.randomUUID(),Instant.now(),"terminal-race",
                new com.mediflow.billing.application.event.SurgeryCompletedEvent.Payload(source.surgeryCaseId(),source.patientId(),source.departmentId(),
                        source.admissionId(),source.recordId(),result,java.util.List.of(new com.mediflow.billing.application.event.SurgeryCompletedEvent.PerformedItem(UUID.randomUUID(),"ITEM","PRICE",BigDecimal.ONE)),
                        Instant.now(),Instant.now(),Instant.now()));
        var latch=new CountDownLatch(1);
        try (var pool=Executors.newFixedThreadPool(2)) {
            var cancelled=pool.submit(() -> { latch.await(); try { cancellations.receive(cancel); return true; }
                catch (BillingRuleException denial) { assertThat(denial.getCode()).isEqualTo("BILLING_SURGERY_ALREADY_PERFORMED_OR_VOIDED"); return false; } });
            var performed=pool.submit(() -> { latch.await(); try { outcomes.onSurgeryCompleted(completed); return true; }
                catch (BillingRuleException denial) { assertThat(denial.getCode()).isEqualTo("BILLING_SURGERY_RECONCILIATION_CONTEXT_MISMATCH"); return false; } });
            latch.countDown(); boolean c=cancelled.get(15,TimeUnit.SECONDS), p=performed.get(15,TimeUnit.SECONDS);
            assertThat(c).isNotEqualTo(p);
            assertThat(jdbc.queryForObject("SELECT status FROM CHARGE",String.class)).isEqualTo(c ? "VOIDED" : "POSTED");
            assertThat(jdbc.queryForObject("SELECT reconciled_result_id FROM CHARGE",UUID.class)).isEqualTo(p ? result : null);
        }
        assertThat(count("PAYMENT_TRANSACTION")).isZero();
    }
    @Test void cancel_waitsBehindPayment_revocationAuditTimeIsAfterTheWinningGrant() throws Exception {
        UUID request=issue("admission",false);
        var command=cancellationDecoder.decode("surgery.cancelled",SurgeryCancellationContractTest.fixture("admission"));
        var waiting=new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>>();
        try (var pool=Executors.newSingleThreadExecutor()) {
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.queryForMap("SELECT * FROM BILLING_ACCOUNT FOR UPDATE");
                waiting.set(pool.submit(() -> cancellations.receive(command)));
                await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM pg_locks l JOIN pg_stat_activity a ON a.pid=l.pid WHERE NOT l.granted AND a.datname=current_database() AND a.query LIKE '%BILLING_ACCOUNT%'",Integer.class)).isPositive());
                pay(request,"100","winning-payment");
            });
            waiting.get().get(15,TimeUnit.SECONDS);
        }
        assertThat(reads.get(caseId()).status()).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("SELECT revoked_at>=granted_at FROM FINANCIAL_CLEARANCE",Boolean.class)).isTrue();
        assertThat(reads.get(caseId()).refundsDue().getFirst().amount()).isEqualByComparingTo("100");
    }
    @ParameterizedTest @ValueSource(strings = {"DOCTOR","MANAGER","PATIENT","SYSTEM"})
    void get_refundDue_wrongRoleOrServiceCredential_denied(String role) throws Exception {
        issue("admission", false); cancellations.receive(cancellationDecoder.decode("surgery.cancelled", SurgeryCancellationContractTest.fixture("admission")));
        var response = http.exchange("/api/v1/billing/surgery-cancellations/" + caseId() + "/refunds-due", HttpMethod.GET,
                new HttpEntity<>(humanHeaders(role,role.equals("SYSTEM") ? "service" : "access")), String.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.FORBIDDEN,HttpStatus.UNAUTHORIZED);
    }
    @Test void get_cashierAccess_seesExactDue_refreshesAfterActualRefund() throws Exception {
        UUID request=issue("admission", false); pay(request,"100","initial");
        cancellations.receive(cancellationDecoder.decode("surgery.cancelled", SurgeryCancellationContractTest.fixture("admission")));
        var response=http.exchange("/api/v1/billing/surgery-cancellations/" + caseId() + "/refunds-due",HttpMethod.GET,new HttpEntity<>(humanHeaders("CASHIER","access")),String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(response.getBody()).path("data").path("refundsDue").get(0).path("amount").decimalValue()).isEqualByComparingTo("100");
    }
    private UUID issue(String context, boolean extra) throws Exception {
        var root=tree(SurgeryChargeContractTest.fixture(context));
        if (extra) {
            var items=(com.fasterxml.jackson.databind.node.ArrayNode)root.path("payload").path("plannedItems");
            var item=((ObjectNode)items.get(0)).deepCopy(); item.put("itemCode","EXTRA_ITEM").put("priceCode","EXTRA_PRICE"); items.add(item);
        }
        return charges.issue(creationDecoder.decode("surgery.case.created",mapper.writeValueAsBytes(root)));
    }
    private void issueWithPreviousWriter(byte[] body) {
        // Model committed V9 issuance from the previous binary, which did not join early cancellation.
        // Durable recovery must still handle that history after upgrade, without a live callback.
        var previous=new com.mediflow.billing.application.service.SurgeryPlannedRequestService(sourceRepository,prices,events,java.time.Clock.systemUTC());
        var command=creationDecoder.decode("surgery.case.created",body);
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> previous.issue(command));
    }
    private UUID pay(UUID request,String amount,String key) { return payments.complete(request,new CompleteLedgerPaymentRequest(key,new BigDecimal(amount),"VND",PaymentMethod.CASH,null),cashier,"payment-trace").transactionId(); }
    private UUID caseId() { return UUID.fromString("00000000-0000-4000-8000-000000000001"); }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table,Long.class); }
    private ObjectNode tree(byte[] body) throws Exception { return (ObjectNode)mapper.readTree(body); }
    private org.springframework.amqp.rabbit.listener.MessageListenerContainer listener() { return registry.getListenerContainer("billingEvents"); }
    private void sendCancellation(byte[] body) { send("surgery.cancelled",body); }
    private void send(String key,byte[] body) { listener().start(); var p=new MessageProperties(); p.setContentType("application/json"); rabbit.send("mediflow.events",key,new Message(body,p)); }
    private void applied() { await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_cancellation_source WHERE status='APPLIED'",Integer.class)).isOne()); listener().stop(); }
    private void dlq(byte[] expected) { await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo("billing.dlq").getMessageCount()).isPositive()); listener().stop(); assertThat(rabbit.receive("billing.dlq",2000).getBody()).isEqualTo(expected); }
    private HttpHeaders humanHeaders(String role,String type) { return tokenHeaders(cashier.toString(),role,type); }
    private HttpHeaders serviceHeaders() { return tokenHeaders("surgery-service","SYSTEM","service"); }
    private HttpHeaders tokenHeaders(String subject,String role,String type) {
        var now=Instant.now(); var h=new HttpHeaders(); h.set("X-Correlation-Id","cancellation-observation");
        h.setBearerAuth(io.jsonwebtoken.Jwts.builder().subject(subject).claim("type",type).claim("role",role)
                .issuedAt(java.util.Date.from(now)).expiration(java.util.Date.from(now.plusSeconds(30)))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor("cancellation-test-credential-at-least-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact());
        return h;
    }
}
