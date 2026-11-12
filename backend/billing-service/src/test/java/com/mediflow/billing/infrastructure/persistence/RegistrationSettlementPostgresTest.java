package com.mediflow.billing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.dto.request.RefundLedgerPaymentRequest;
import com.mediflow.billing.application.event.OutpatientRegistrationChangedEvent;
import com.mediflow.billing.application.event.OutpatientRegistrationChangedEvent.Cancellation;
import com.mediflow.billing.application.event.OutpatientRegistrationChangedEvent.Reschedule;
import com.mediflow.billing.application.port.in.ConsumeOutpatientRegistrationFactUseCase;
import com.mediflow.billing.application.port.in.GetOutpatientAdjustmentUseCase;
import com.mediflow.billing.application.port.in.ListRegistrationSettlementsUseCase;
import com.mediflow.billing.application.port.in.RefundLedgerPaymentUseCase;
import com.mediflow.billing.application.port.out.LedgerRefundRepositoryPort;
import com.mediflow.billing.application.port.out.OutpatientCancellationWirePort;
import com.mediflow.billing.application.port.out.OutpatientQuoteRepositoryPort;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.application.port.out.PriceCatalogPort.PriceSnapshot;
import com.mediflow.billing.application.port.out.ScheduledRegistrationWirePort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.common.api.PageQuery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class RegistrationSettlementPostgresTest {
    private static final Instant CHANGED_AT = Instant.now().minusSeconds(3600).truncatedTo(ChronoUnit.MICROS);
    private static final BigDecimal PAID = new BigDecimal("50000");
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID STAFF = UUID.randomUUID();

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
        registry.add("mediflow.jwt.secret", () -> "billing-settlement-secret-at-least-32-bytes-long");
        registry.add("eureka.client.enabled", () -> false);
        registry.add("mediflow.billing.ledger.enabled", () -> true);
        registry.add("mediflow.billing.refunds.enabled", () -> true);
        registry.add("mediflow.billing.clearance-lookup.enabled", () -> true);
        registry.add("mediflow.billing.scheduled-payments.enabled", () -> true);
        registry.add("mediflow.billing.outpatient-quotes.enabled", () -> true);
        registry.add("mediflow.billing.zero-payable-eligibility.enabled", () -> true);
        registry.add("billing.outpatient-quotes.catalogue-version", () -> "CATALOGUE-TEST-1");
        registry.add("billing.outpatient-quotes.policy-version", () -> "POLICY-TEST-1");
        registry.add("billing.outpatient-quotes.items[0].source-type", () -> "EXAM");
        registry.add("billing.outpatient-quotes.items[0].price-code", () -> "OUTPATIENT_EXAM");
        registry.add("billing.outpatient-quotes.items[0].support-rate", () -> "0.5");
        registry.add("mediflow.billing.scheduled-payments.recovery-initial-delay-ms", () -> 3600000);
        registry.add("mediflow.billing.scheduled-payments.recovery-delay-ms", () -> 3600000);
        registry.add("mediflow.billing.outbox.enabled", () -> false);
        registry.add("mediflow.billing.outbox.metrics-enabled", () -> false);
        registry.add("mediflow.billing.outbox.maintenance-enabled", () -> false);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired TransactionTemplate tx;
    @Autowired ConsumeOutpatientRegistrationFactUseCase registrations;
    @Autowired ScheduledRegistrationWirePort wire;
    @Autowired OutpatientQuoteRepositoryPort quotes;
    @Autowired com.mediflow.billing.application.service.ScheduledPaymentCoordinator payments;
    @Autowired RefundLedgerPaymentUseCase refunds;
    @Autowired LedgerRefundRepositoryPort refundStore;
    @Autowired GetOutpatientAdjustmentUseCase adjustments;
    @Autowired ListRegistrationSettlementsUseCase settlements;
    @Autowired OutpatientCancellationWirePort cancellationWire;
    @MockBean PriceCatalogPort prices;
    @MockBean(name = "billingClock") Clock billingClock;
    @MockBean com.mediflow.billing.application.port.out.ClinicalPaymentLockPort clinical;
    @MockBean com.mediflow.billing.application.port.out.ClinicalPaymentContextPort paymentContexts;

    private UUID transactionId;

    private record Reg(UUID id, UUID patient, UUID doctor, UUID department, UUID session, UUID room, UUID reservation, LocalDate date) {
        Reg moved(UUID newSession, UUID newDoctor, UUID newRoom) { return new Reg(id, patient, newDoctor, department, newSession, newRoom, reservation, date); }
        Reg inDepartment(UUID other) { return new Reg(id, patient, doctor, other, session, room, reservation, date); }
        Reg forPatient(UUID other) { return new Reg(id, other, doctor, department, session, room, reservation, date); }
    }

    @BeforeEach void resetState() {
        jdbc.execute("TRUNCATE BILLING_ACCOUNT, BILLING_EVENT_OUTBOX CASCADE");
        when(billingClock.instant()).thenReturn(CHANGED_AT.plusSeconds(60));
        when(billingClock.getZone()).thenReturn(ZoneOffset.UTC);
        when(prices.requireActive("OUTPATIENT_EXAM", CHANGED_AT.plusSeconds(60)))
                .thenReturn(new PriceSnapshot("Outpatient visit", new BigDecimal("100000")));
        when(clinical.acquire(any())).thenAnswer(invocation -> {
            com.mediflow.billing.domain.model.ScheduledPaymentOperation op = invocation.getArgument(0);
            return new com.mediflow.billing.domain.model.ScheduledPaymentLease(op.paymentOperationId(), op.reservationId(),
                    2L, 1L, CHANGED_AT.plusSeconds(60), CHANGED_AT.plusSeconds(180), op.basis(), op.quoteId());
        });
    }

    private static Reg reg() {
        return new Reg(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), LocalDate.ofInstant(CHANGED_AT, ZoneOffset.UTC));
    }

    private OutpatientRegistrationChangedEvent event(Reg r, long revision, String reservationStatus, String status, Integer queue,
            Cancellation cancellation, Reschedule reschedule) {
        Instant at = CHANGED_AT.plusSeconds(revision * 60);
        var fact = new OutpatientRegistrationChangedEvent.RegistrationFact(r.id(), revision, r.patient(), r.doctor(), r.department(), r.session(),
                r.room(), r.date(), LocalTime.of(9, 0), LocalTime.of(9, 30), "NEW_VISIT", r.reservation(), reservationStatus, status, null, queue,
                CHANGED_AT.plusSeconds(1200), "OUTPATIENT_EXAM", "EXAM", r.id(), "OUTPATIENT_VISIT", r.id(), cancellation, at, reschedule);
        return new OutpatientRegistrationChangedEvent(UUID.randomUUID(), "outpatient.registration.changed", 1, at, "trace", "clinical-service", fact);
    }

    private void send(OutpatientRegistrationChangedEvent event) throws Exception {
        registrations.consume(event, mapper.writeValueAsBytes(event));
    }

    private Reg unpaid() throws Exception {
        Reg r = reg();
        send(event(r, 1, "HELD", "AWAITING_PAYMENT", null, null, null));
        return r;
    }

    private Reg paid() throws Exception {
        Reg r = unpaid();
        UUID request = request(r);
        var quote = quotes.findCurrent(request).orElseThrow();
        var command = new com.mediflow.billing.application.dto.command.ScheduledPaymentCommand(r.reservation(), 1, quote.patientPayable(), "VND",
                PaymentMethod.CASH, null, quote.quoteId(), "PATIENT_PAYMENT");
        transactionId = payments.pay(request, command, ACTOR, "settlement-" + r.id(), "settlement", CHANGED_AT.plusSeconds(60)).payment().transactionId();
        send(event(r, 2, "CONFIRMED", "CONFIRMED", 1, null, null));
        return r;
    }

    private UUID request(Reg r) {
        return jdbc.queryForObject("SELECT payment_request_id FROM outpatient_registration_source WHERE registration_id=?", UUID.class, r.id());
    }

    private String requestStatus(Reg r) {
        return jdbc.queryForObject("SELECT status FROM PAYMENT_REQUEST WHERE payment_request_id=?", String.class, request(r));
    }

    private static Cancellation cancellation(long revision) {
        return new Cancellation(ACTOR, STAFF, "Bệnh nhân không đến được", CHANGED_AT.plusSeconds(revision * 60));
    }

    private void cancel(Reg r, long revision) throws Exception {
        send(event(r, revision, "CANCELLED", "CANCELLED", null, cancellation(revision), null));
    }

    private java.util.Map<String, Object> settlement(Reg r) {
        return jdbc.queryForMap("SELECT * FROM outpatient_registration_settlement WHERE registration_id=?", r.id());
    }

    private static Reschedule move(Reg from, long revision) {
        return new Reschedule(ACTOR, STAFF, "Bác sĩ bận đột xuất", from.session(), from.doctor(), from.room(), from.date(), LocalTime.of(9, 0),
                CHANGED_AT.plusSeconds(revision * 60));
    }

    private void assertConflict(OutpatientRegistrationChangedEvent event) {
        assertThatThrownBy(() -> send(event)).isInstanceOfSatisfying(BillingRuleException.class,
                e -> assertThat(e.getCode()).isEqualTo("BILLING_REGISTRATION_SOURCE_CONFLICT"));
    }

    private void refund(BigDecimal amount, String key) {
        refunds.refund(transactionId, new RefundLedgerPaymentRequest(key, amount, "Hoàn tiền khám", PaymentMethod.CASH), ACTOR, "trace");
    }

    @Test void cancelledUnpaidIsVoided() throws Exception {
        Reg r = unpaid();
        cancel(r, 2);
        assertThat(settlement(r).get("kind")).isEqualTo("VOID_UNPAID");
        assertThat(requestStatus(r)).isEqualTo("CANCELLED");
    }

    @Test void cancelledPaidBecomesRefundDue() throws Exception {
        Reg r = paid();
        String before = requestStatus(r);
        cancel(r, 3);
        var row = settlement(r);
        assertThat(row.get("kind")).isEqualTo("REFUND_DUE");
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo(PAID);
        assertThat(row.get("payment_request_id")).isEqualTo(request(r));
        assertThat(row.get("actor_account_id")).isEqualTo(ACTOR);
        assertThat(row.get("actor_staff_id")).isEqualTo(STAFF);
        assertThat(row.get("reason")).isEqualTo("Bệnh nhân không đến được");
        assertThat(row.get("source_type")).isEqualTo("EXAM");
        assertThat(row.get("source_id")).isEqualTo(r.id());
        assertThat(requestStatus(r)).isEqualTo(before);
    }

    @Test void cashierCanRefundCancelledPaidRegistration() throws Exception {
        Reg r = paid();
        cancel(r, 3);
        var eligibility = tx.execute(status -> refundStore.outpatientRefundEligibility(transactionId));
        assertThat(eligibility.cancellationApplied()).isTrue();
        assertThat(eligibility.refundDue()).isEqualByComparingTo(PAID);
        refund(PAID, "refund-full");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PAYMENT_TRANSACTION WHERE original_transaction_id=? AND transaction_type='REFUND'",
                Integer.class, transactionId)).isEqualTo(1);
    }

    @Test void paidCancellationStopsTheChargeAndClearance() throws Exception {
        Reg r = paid();
        cancel(r, 3);
        assertThat(jdbc.queryForObject("SELECT status FROM CHARGE WHERE source_type='EXAM' AND source_id=?", String.class, r.id())).isEqualTo("VOIDED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM FINANCIAL_CLEARANCE WHERE payment_request_id=? AND revoked_at IS NULL",
                Integer.class, request(r))).isZero();
    }

    @Test void refundAboveDueIsRefused() throws Exception {
        Reg r = paid();
        cancel(r, 3);
        // The cash ceiling is checked first, so the outpatient ceiling is only reachable when it is lower than the payment.
        jdbc.update("UPDATE outpatient_cancellation_refund_due SET amount=30000 WHERE source_id=?", r.id());
        assertThatThrownBy(() -> refund(new BigDecimal("40000"), "refund-over")).isInstanceOfSatisfying(BillingRuleException.class,
                e -> assertThat(e.getCode()).isEqualTo("BILLING_REFUND_EXCEEDS_OUTPATIENT_DUE"));
    }

    @Test void unpaidCancellationHasNoRefundEvidence() throws Exception {
        Reg r = unpaid();
        cancel(r, 2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outpatient_cancellation_source WHERE source_id=?", Integer.class, r.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outpatient_cancellation_refund_due WHERE source_id=?", Integer.class, r.id())).isZero();
    }

    @Test void examAdjustmentLookupShowsTheDueAmount() throws Exception {
        Reg r = paid();
        cancel(r, 3);
        var adjustment = tx.execute(status -> adjustments.get("EXAM", r.id()));
        assertThat(adjustment.refundsDue()).hasSize(1);
        assertThat(adjustment.refundsDue().getFirst().amount()).isEqualByComparingTo(PAID);
        assertThat(adjustment.refundsDue().getFirst().originalTransactionId()).isEqualTo(transactionId);
    }

    private byte[] labCancelled(String extra) {
        UUID id = UUID.randomUUID();
        String body = """
                {"eventId":"%s","eventType":"lab.request.cancelled","version":1,"occurredAt":"2026-11-10T01:00:00Z","correlationId":"trace","producer":"lab-service",
                "payload":{"testId":"%s","sourceId":"%s","sourceType":"LAB_TEST","sourceOrderId":"%s","requestingDepartmentId":"%s","priceCode":"LAB","labType":"BLOOD",
                "patientId":"%s","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"%s","cancelledAt":"2026-11-10T01:00:00Z"%s}}
                """.formatted(UUID.randomUUID(), id, id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), extra);
        return body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test void labCancellationStillRequiresActorAndReason() {
        String actor = ",\"cancelledBy\":\"" + UUID.randomUUID() + "\"";
        String reason = ",\"reason\":\"Sai chỉ định\"";
        assertThat(cancellationWire.decode("lab.request.cancelled", labCancelled(actor + reason)).reason()).isEqualTo("Sai chỉ định");
        assertThatThrownBy(() -> cancellationWire.decode("lab.request.cancelled", labCancelled(reason))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cancellationWire.decode("lab.request.cancelled", labCancelled(actor))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void partialRefundLeavesOutstanding() throws Exception {
        Reg r = paid();
        cancel(r, 3);
        refund(new BigDecimal("20000"), "refund-part");
        var page = settlements.list("REFUND_DUE", PageQuery.of(0, 20));
        assertThat(page.content()).hasSize(1);
        var item = page.content().getFirst();
        assertThat(item.amount()).isEqualByComparingTo(PAID);
        assertThat(item.refundedAmount()).isEqualByComparingTo("20000");
        assertThat(item.outstandingAmount()).isEqualByComparingTo("30000");
    }

    @Test void rescheduleUpdatesSourceAndKeepsRequest() throws Exception {
        Reg r = paid();
        UUID request = request(r);
        Reg moved = r.moved(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        // Real bytes through the strict wire decoder: the optional field must be accepted.
        var event = event(moved, 3, "CONFIRMED", "CONFIRMED", 1, null, move(r, 3));
        byte[] raw = mapper.writeValueAsBytes(event);
        registrations.consume(wire.decode("outpatient.registration.changed", raw), raw);
        var source = jdbc.queryForMap("SELECT * FROM outpatient_registration_source WHERE registration_id=?", r.id());
        assertThat(source.get("session_id")).isEqualTo(moved.session());
        assertThat(source.get("doctor_id")).isEqualTo(moved.doctor());
        assertThat(source.get("room_id")).isEqualTo(moved.room());
        assertThat(source.get("payment_request_id")).isEqualTo(request);
        var row = settlement(r);
        assertThat(row.get("kind")).isEqualTo("RESCHEDULED");
        assertThat(row.get("previous_session_id")).isEqualTo(r.session());
        assertThat(row.get("new_session_id")).isEqualTo(moved.session());
        assertThat(row.get("reason")).isEqualTo("Bác sĩ bận đột xuất");
    }

    @Test void redeliveredRescheduleCreatesOneSettlement() throws Exception {
        Reg r = paid();
        var event = event(r.moved(UUID.randomUUID(), r.doctor(), r.room()), 3, "CONFIRMED", "CONFIRMED", 1, null, move(r, 3));
        send(event);
        send(event);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outpatient_registration_settlement WHERE registration_id=?", Integer.class, r.id())).isEqualTo(1);
    }

    @Test void rescheduleCannotChangeDepartmentOrPatient() throws Exception {
        Reg r = paid();
        Reg moved = r.moved(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertConflict(event(moved.inDepartment(UUID.randomUUID()), 3, "CONFIRMED", "CONFIRMED", 1, null, move(r, 3)));
        assertConflict(event(moved.forPatient(UUID.randomUUID()), 3, "CONFIRMED", "CONFIRMED", 1, null, move(r, 3)));
    }

    @Test void changedDoctorWithoutRescheduleFactStillConflicts() throws Exception {
        Reg r = paid();
        assertConflict(event(r.moved(r.session(), UUID.randomUUID(), r.room()), 3, "CONFIRMED", "CONFIRMED", 1, null, null));
    }

    @Test void rescheduleMustMatchCurrentSession() throws Exception {
        Reg r = paid();
        Reg elsewhere = r.moved(UUID.randomUUID(), r.doctor(), r.room());
        assertConflict(event(r.moved(UUID.randomUUID(), r.doctor(), r.room()), 3, "CONFIRMED", "CONFIRMED", 1, null, move(elsewhere, 3)));
    }
}
