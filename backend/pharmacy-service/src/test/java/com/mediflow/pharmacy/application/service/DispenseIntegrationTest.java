package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.command.CancelPrescriptionCommand;
import com.mediflow.pharmacy.application.dto.command.CreatePrescriptionCommand;
import com.mediflow.pharmacy.application.dto.command.PaymentCompletedCommand;
import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;
import com.mediflow.pharmacy.application.dto.request.PrescriptionLineRequest;
import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import com.mediflow.pharmacy.application.event.PrescriptionFilledEvent;
import com.mediflow.pharmacy.application.port.in.CancelPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.CreatePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.DispensePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.ReactToPaymentUseCase;
import com.mediflow.pharmacy.application.port.out.PaymentReceiptClaimResult;
import com.mediflow.pharmacy.application.port.out.PaymentReceiptRepositoryPort;
import com.mediflow.pharmacy.domain.exception.PaymentReceiptRuleException;
import com.mediflow.pharmacy.domain.model.PaymentReceipt;
import com.mediflow.pharmacy.domain.model.enums.DispenseActorType;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;
import com.mediflow.pharmacy.domain.model.enums.PaymentReceiptStatus;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;
import com.mediflow.pharmacy.domain.model.enums.ReservationStatus;
import com.mediflow.pharmacy.infrastructure.messaging.PharmacyEventPublisherAdapter;
import com.mediflow.pharmacy.infrastructure.persistence.adapter.PaymentReceiptPersistenceAdapter;
import com.mediflow.pharmacy.infrastructure.persistence.jpaentity.DrugJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.DispenseSlipJpaRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.DrugJpaEntityRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PaymentReceiptJpaRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PharmacyEventOutboxJpaRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.ProcessedEventJpaRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PrescriptionJpaRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.StockReservationJpaRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL integration coverage for concurrent dispense and post-rollback failure recording. */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "mediflow.pharmacy.outbox.enabled=false",
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
        "mediflow.pharmacy.reservation.release-cron=-"
})
@Import(DispenseIntegrationTest.PaymentReceiptTestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class DispenseIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CreatePrescriptionUseCase createPrescriptionUseCase;

    @Autowired
    private CancelPrescriptionUseCase cancelPrescriptionUseCase;

    @Autowired
    private DispensePrescriptionUseCase dispensePrescriptionUseCase;

    @Autowired
    private ReactToPaymentUseCase reactToPaymentUseCase;

    @Autowired
    private DrugJpaEntityRepository drugRepository;

    @Autowired
    private PrescriptionJpaRepository prescriptionRepository;

    @Autowired
    private DispenseSlipJpaRepository dispenseSlipRepository;

    @Autowired
    private StockReservationJpaRepository reservationRepository;

    @Autowired
    private PaymentReceiptRepositoryPort paymentReceiptRepository;

    @Autowired
    private PaymentReceiptFaultInjector paymentReceiptFaultInjector;

    @Autowired
    private PaymentReceiptJpaRepository paymentReceiptJpaRepository;

    @Autowired
    private PharmacyEventOutboxJpaRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @SpyBean
    private PharmacyEventPublisherAdapter eventPublisher;

    @Autowired
    private ProcessedEventJpaRepository processedEventRepository;

    /** Removes aggregate rows in foreign-key order between integration cases. */
    @AfterEach
    void cleanDatabase() {
        paymentReceiptFaultInjector.reset();
        processedEventRepository.deleteAllInBatch();
        paymentReceiptJpaRepository.deleteAllInBatch();
        outboxRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        dispenseSlipRepository.deleteAllInBatch();
        prescriptionRepository.deleteAllInBatch();
        drugRepository.deleteAllInBatch();
    }

    /** Two concurrent callers serialize on row locks and decrement stock exactly once (BR-D9/D10). */
    @Test
    void dispense_twoConcurrentCalls_decrementsStockOnce() throws Exception {
        DrugJpaEntity drug = drugRepository.saveAndFlush(drug("Concurrent drug", 10));
        PrescriptionDTO prescription = createPrescription(List.of(
                new PrescriptionLineRequest(drug.getDrugId(), 2, "Ngày 2 lần")));
        paymentReceiptRepository.claim(paymentReceipt(prescription));
        UUID actorId = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<DispenseDTO> first = executor.submit(() -> dispenseWhenReleased(
                ready, start, prescription.prescriptionId(), actorId));
        Future<DispenseDTO> second = executor.submit(() -> dispenseWhenReleased(
                ready, start, prescription.prescriptionId(), actorId));

        try {
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).status()).isEqualTo(DispenseStatus.DISPENSED);
            assertThat(second.get(10, TimeUnit.SECONDS).status()).isEqualTo(DispenseStatus.DISPENSED);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(drugRepository.findById(drug.getDrugId()).orElseThrow().getStockQuantity())
                .isEqualTo(8);
        assertThat(reservationRepository.findByPrescriptionId(prescription.prescriptionId()))
                .singleElement()
                .extracting(reservation -> reservation.getStatus())
                .isEqualTo(ReservationStatus.FULFILLED);
        assertThat(dispenseSlipRepository.findByPrescriptionId(prescription.prescriptionId())
                .orElseThrow().getDispensedBy()).isEqualTo(actorId);
        assertThat(dispenseSlipRepository.findByPrescriptionId(prescription.prescriptionId())
                .orElseThrow().getDispensedActorType()).isEqualTo(DispenseActorType.STAFF);
        assertThat(outboxCorrelationId("prescription.filled", prescription.prescriptionId()))
                .isEqualTo("concurrent-dispense-correlation");
    }

    /** A late line failure rolls back earlier stock changes, then persists FAILED separately (BR-D12). */
    @Test
    void dispense_secondDrugExpired_rollsBackStockAndRecordsFailure() {
        DrugJpaEntity first = drugRepository.saveAndFlush(drug("First drug", 10));
        DrugJpaEntity second = drugRepository.saveAndFlush(drug("Second drug", 10));
        List<DrugJpaEntity> sorted = java.util.stream.Stream.of(first, second)
                .sorted(java.util.Comparator.comparing(DrugJpaEntity::getDrugId))
                .toList();
        PrescriptionDTO prescription = createPrescription(sorted.stream()
                .map(drug -> new PrescriptionLineRequest(drug.getDrugId(), 2, "Ngày 2 lần"))
                .toList());
        DrugJpaEntity laterLockedDrug = sorted.get(1);
        laterLockedDrug.setExpiryDate(LocalDate.now().minusDays(1));
        drugRepository.saveAndFlush(laterLockedDrug);
        paymentReceiptRepository.claim(paymentReceipt(prescription));

        assertThatThrownBy(() -> dispensePrescriptionUseCase.dispense(
                prescription.prescriptionId(), UUID.randomUUID(), "failure-correlation"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("DRUG_EXPIRED"));

        assertThat(drugRepository.findAll())
                .extracting(DrugJpaEntity::getStockQuantity)
                .containsOnly(10);
        assertThat(reservationRepository.findByPrescriptionId(prescription.prescriptionId()))
                .extracting(reservation -> reservation.getStatus())
                .containsOnly(ReservationStatus.RELEASED);
        assertThat(prescriptionRepository.findById(prescription.prescriptionId()).orElseThrow().getStatus())
                .isEqualTo(PrescriptionStatus.DISPENSE_FAILED);
        assertThat(dispenseSlipRepository.findByPrescriptionId(prescription.prescriptionId())
                .orElseThrow().getStatus()).isEqualTo(DispenseStatus.FAILED);
        assertThat(outboxRepository.findAll())
                .anySatisfy(event -> {
                    assertThat(event.getRoutingKey()).isEqualTo("prescription.dispense.failed");
                    assertThat(event.getAggregateId()).isEqualTo(prescription.prescriptionId());
                });
    }

    /** An outbox persistence failure rolls back the entire dispense mutation (P-03d). */
    @Test
    void dispense_filledOutboxFails_rollsBackStockPrescriptionSlipAndReservations() {
        DrugJpaEntity drug = drugRepository.saveAndFlush(drug("Outbox failure drug", 10));
        PrescriptionDTO prescription = createPrescription(List.of(
                new PrescriptionLineRequest(drug.getDrugId(), 2, "Ngày 2 lần")));
        PaymentReceipt receipt = paymentReceipt(prescription);
        paymentReceiptRepository.claim(receipt);

        AtomicBoolean failAfterOutboxInsert = new AtomicBoolean(true);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            entityManager.flush();
            if (failAfterOutboxInsert.compareAndSet(true, false)) {
                throw new IllegalStateException("simulated dispense outbox persistence outage");
            }
            return null;
        }).when(eventPublisher).publishPrescriptionFilled(any(PrescriptionFilledEvent.class));

        assertThatThrownBy(() -> dispensePrescriptionUseCase.dispense(
                prescription.prescriptionId(), UUID.randomUUID(), "outbox-failure-correlation"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated dispense outbox persistence outage");

        assertThat(drugRepository.findById(drug.getDrugId()).orElseThrow().getStockQuantity())
                .isEqualTo(10);
        assertThat(prescriptionRepository.findById(prescription.prescriptionId()).orElseThrow().getStatus())
                .isEqualTo(PrescriptionStatus.ACTIVE);
        assertThat(dispenseSlipRepository.findByPrescriptionId(prescription.prescriptionId())
                .orElseThrow().getStatus()).isEqualTo(DispenseStatus.PENDING);
        assertThat(reservationRepository.findByPrescriptionId(prescription.prescriptionId()))
                .singleElement()
                .satisfies(reservation -> {
                    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
                    assertThat(reservation.getQuantity()).isEqualTo(2);
                });
        assertThat(paymentReceiptRepository.findByEventId(receipt.getEventId()))
                .get().extracting(PaymentReceipt::getStatus)
                .isEqualTo(PaymentReceiptStatus.RECEIVED);
        assertOutboxCount("prescription.filled", prescription.prescriptionId(), 0);
    }

    /** Concurrent payment redelivery has one stock, dispense and outbox effect across real DB transactions. */
    @RepeatedTest(5)
    void paymentCompleted_twoConcurrentDeliveries_commitsOneDispenseAndFilledEvent() throws Exception {
        DrugJpaEntity drug = drugRepository.saveAndFlush(drug("Payment concurrency drug", 10));
        PrescriptionDTO prescription = createPrescription(List.of(
                new PrescriptionLineRequest(drug.getDrugId(), 2, "Ngày 2 lần")));
        PaymentCompletedCommand payment = payment(prescription);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        paymentReceiptFaultInjector.pauseAfterBothClaims(payment.eventId());
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> first = executor.submit(() -> {
            applyPaymentWhenReleased(ready, start, payment);
            return null;
        });
        Future<?> second = executor.submit(() -> {
            applyPaymentWhenReleased(ready, start, payment);
            return null;
        });
        try {
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(paymentReceiptFaultInjector.awaitBothClaims(payment.eventId(), 10, TimeUnit.SECONDS))
                    .as("both receipt claims must commit before either handler can dispense")
                    .isTrue();
            assertThat(paymentReceiptRepository.findByEventId(payment.eventId()))
                    .get().extracting(PaymentReceipt::getStatus)
                    .isEqualTo(PaymentReceiptStatus.RECEIVED);
            paymentReceiptFaultInjector.releaseClaims(payment.eventId());
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            paymentReceiptFaultInjector.releaseClaims(payment.eventId());
            executor.shutdownNow();
        }

        assertThat(drugRepository.findById(drug.getDrugId()).orElseThrow().getStockQuantity()).isEqualTo(8);
        assertThat(dispenseSlipRepository.findByPrescriptionId(prescription.prescriptionId()).orElseThrow()
                .getStatus()).isEqualTo(DispenseStatus.DISPENSED);
        assertThat(prescriptionRepository.findById(prescription.prescriptionId()).orElseThrow().getStatus())
                .isEqualTo(PrescriptionStatus.FULFILLED);
        assertThat(paymentReceiptRepository.findByEventId(payment.eventId()))
                .get().extracting(PaymentReceipt::getStatus).isEqualTo(PaymentReceiptStatus.DISPENSED);
        assertThat(processedEventRepository.findById(payment.eventId())).isPresent();
        assertThat(outboxRepository.findAll().stream()
                .filter(event -> "prescription.filled".equals(event.getRoutingKey()))
                .filter(event -> prescription.prescriptionId().equals(event.getAggregateId())))
                .hasSize(1);
        assertThat(dispenseSlipRepository.findByPrescriptionId(prescription.prescriptionId())
                .orElseThrow().getDispensedBy()).isNull();
        assertThat(dispenseSlipRepository.findByPrescriptionId(prescription.prescriptionId())
                .orElseThrow().getDispensedActorType()).isEqualTo(DispenseActorType.SYSTEM);
        assertThat(paymentReceiptRepository.findByEventId(payment.eventId()))
                .get().extracting(PaymentReceipt::getCorrelationId)
                .isEqualTo(payment.correlationId());
        assertThat(outboxCorrelationId("prescription.filled", prescription.prescriptionId()))
                .isEqualTo(payment.correlationId());
    }

    /** A retry after stock commits but receipt finalization fails must resume without dispensing twice. */
    @Test
    void paymentCompleted_receiptSaveFailsAfterDispense_redeliveryFinalizesSameEffect() {
        DrugJpaEntity drug = drugRepository.saveAndFlush(drug("Receipt recovery drug", 10));
        PrescriptionDTO prescription = createPrescription(List.of(
                new PrescriptionLineRequest(drug.getDrugId(), 2, "Ngày 2 lần")));
        PaymentCompletedCommand payment = payment(prescription);
        paymentReceiptFaultInjector.failNextSave(payment.eventId());

        assertThatThrownBy(() -> reactToPaymentUseCase.onPaymentCompleted(payment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated receipt persistence outage after dispense");

        assertThat(drugRepository.findById(drug.getDrugId()).orElseThrow().getStockQuantity())
                .isEqualTo(8);
        assertThat(dispenseSlipRepository.findByPrescriptionId(prescription.prescriptionId())
                .orElseThrow().getStatus()).isEqualTo(DispenseStatus.DISPENSED);
        assertThat(paymentReceiptRepository.findByEventId(payment.eventId()))
                .get().extracting(PaymentReceipt::getStatus)
                .isEqualTo(PaymentReceiptStatus.RECEIVED);
        assertThat(processedEventRepository.findById(payment.eventId())).isEmpty();
        assertOutboxCount("prescription.filled", prescription.prescriptionId(), 1);

        reactToPaymentUseCase.onPaymentCompleted(payment);

        assertThat(drugRepository.findById(drug.getDrugId()).orElseThrow().getStockQuantity())
                .isEqualTo(8);
        assertThat(paymentReceiptRepository.findByEventId(payment.eventId()))
                .get().extracting(PaymentReceipt::getStatus)
                .isEqualTo(PaymentReceiptStatus.DISPENSED);
        assertThat(processedEventRepository.findById(payment.eventId())).isPresent();
        assertOutboxCount("prescription.filled", prescription.prescriptionId(), 1);

        PaymentCompletedCommand conflictingPayment = new PaymentCompletedCommand(
                payment.eventId(), payment.occurredAt(), payment.correlationId(),
                UUID.randomUUID(), payment.patientId(), payment.departmentId(),
                payment.prescriptionId(), payment.totalAmount(), payment.paymentMethod());
        assertThatThrownBy(() -> reactToPaymentUseCase.onPaymentCompleted(conflictingPayment))
                .isInstanceOf(PaymentReceiptRuleException.class)
                .hasMessageContaining("payload xung đột");
        assertThat(drugRepository.findById(drug.getDrugId()).orElseThrow().getStockQuantity())
                .isEqualTo(8);
        assertOutboxCount("prescription.filled", prescription.prescriptionId(), 1);
    }

    /** A stale terminal receipt cannot overwrite the first committed terminal outcome. */
    @Test
    void paymentReceipt_saveStaleConflictingTerminalOutcome_rejectsWithoutOverwritingWinner() {
        PaymentCompletedCommand payment = new PaymentCompletedCommand(
                UUID.randomUUID(), Instant.now(), "stale-receipt-correlation",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("125.00"), "CASH");
        paymentReceiptRepository.claim(paymentReceipt(payment));

        PaymentReceipt compensated = paymentReceiptRepository.findByEventId(payment.eventId()).orElseThrow();
        PaymentReceipt sameCompensation = paymentReceiptRepository.findByEventId(payment.eventId()).orElseThrow();
        PaymentReceipt staleDispense = paymentReceiptRepository.findByEventId(payment.eventId()).orElseThrow();
        compensated.markCompensated("DISPENSE_FAILED", Instant.now());
        sameCompensation.markCompensated("DISPENSE_FAILED", Instant.now());
        staleDispense.markDispensed(Instant.now());

        assertThat(paymentReceiptRepository.save(compensated).getStatus())
                .isEqualTo(PaymentReceiptStatus.COMPENSATED);
        assertThat(paymentReceiptRepository.save(sameCompensation).getStatus())
                .isEqualTo(PaymentReceiptStatus.COMPENSATED);
        assertThatThrownBy(() -> paymentReceiptRepository.save(staleDispense))
                .isInstanceOf(PaymentReceiptRuleException.class)
                .hasMessageContaining("outcome khác");
        assertThat(paymentReceiptRepository.findByEventId(payment.eventId()))
                .get().extracting(PaymentReceipt::getStatus)
                .isEqualTo(PaymentReceiptStatus.COMPENSATED);
    }

    /** Concurrent late-payment deliveries record one compensation and never dispense a cancelled order. */
    @Test
    void paymentCompleted_twoConcurrentDeliveries_afterCancellation_compensatesOnce() throws Exception {
        DrugJpaEntity drug = drugRepository.saveAndFlush(drug("Late payment cancelled drug", 10));
        PrescriptionDTO prescription = createPrescription(List.of(
                new PrescriptionLineRequest(drug.getDrugId(), 2, "Ngày 2 lần")));
        cancelPrescriptionUseCase.cancel(new CancelPrescriptionCommand(
                prescription.prescriptionId(),
                new ActorIdentity(UUID.randomUUID(), prescription.doctorId(), "DOCTOR"),
                "Cancelled before payment", "late-payment-cancel-correlation"));
        PaymentCompletedCommand payment = payment(prescription);
        paymentReceiptFaultInjector.pauseAfterBothClaims(payment.eventId());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> first = executor.submit(() -> {
            applyPaymentWhenReleased(ready, start, payment);
            return null;
        });
        Future<?> second = executor.submit(() -> {
            applyPaymentWhenReleased(ready, start, payment);
            return null;
        });

        try {
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(paymentReceiptFaultInjector.awaitBothClaims(payment.eventId(), 10, TimeUnit.SECONDS))
                    .as("both receipt claims must commit before compensation starts")
                    .isTrue();
            paymentReceiptFaultInjector.releaseClaims(payment.eventId());
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            paymentReceiptFaultInjector.releaseClaims(payment.eventId());
            executor.shutdownNow();
        }

        assertThat(drugRepository.findById(drug.getDrugId()).orElseThrow().getStockQuantity())
                .isEqualTo(10);
        assertThat(prescriptionRepository.findById(prescription.prescriptionId()).orElseThrow().getStatus())
                .isEqualTo(PrescriptionStatus.CANCELLED);
        assertThat(dispenseSlipRepository.findByPrescriptionId(prescription.prescriptionId())
                .orElseThrow().getStatus()).isEqualTo(DispenseStatus.CANCELLED);
        assertThat(paymentReceiptRepository.findByEventId(payment.eventId()))
                .get().extracting(PaymentReceipt::getStatus)
                .isEqualTo(PaymentReceiptStatus.COMPENSATED);
        assertThat(processedEventRepository.findById(payment.eventId())).isPresent();
        assertOutboxCount("prescription.dispense.failed", prescription.prescriptionId(), 1);
        assertOutboxCount("prescription.filled", prescription.prescriptionId(), 0);
        assertThat(paymentReceiptRepository.findByEventId(payment.eventId()))
                .get().extracting(PaymentReceipt::getCorrelationId)
                .isEqualTo(payment.correlationId());
        assertThat(outboxCorrelationId("prescription.dispense.failed", prescription.prescriptionId()))
                .isEqualTo(payment.correlationId());
    }

    private DispenseDTO dispenseWhenReleased(
            CountDownLatch ready,
            CountDownLatch start,
            UUID prescriptionId,
            UUID actorId) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent dispense start timed out");
        }
        return dispensePrescriptionUseCase.dispense(
                prescriptionId, actorId, "concurrent-dispense-correlation");
    }

    private void applyPaymentWhenReleased(
            CountDownLatch ready,
            CountDownLatch start,
            PaymentCompletedCommand payment) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent payment start timed out");
        }
        reactToPaymentUseCase.onPaymentCompleted(payment);
    }

    private PaymentCompletedCommand payment(PrescriptionDTO prescription) {
        return new PaymentCompletedCommand(
                UUID.randomUUID(), Instant.now(), "payment-integration-correlation",
                UUID.randomUUID(), prescription.patientId(), prescription.departmentId(),
                prescription.prescriptionId(), prescription.totalAmount(), "CASH");
    }

    private void assertOutboxCount(String routingKey, UUID aggregateId, int expectedCount) {
        assertThat(outboxRepository.findAll().stream()
                .filter(event -> routingKey.equals(event.getRoutingKey()))
                .filter(event -> aggregateId.equals(event.getAggregateId())))
                .hasSize(expectedCount);
    }

    private String outboxCorrelationId(String routingKey, UUID aggregateId) {
        var events = outboxRepository.findAll().stream()
                .filter(event -> routingKey.equals(event.getRoutingKey()))
                .filter(event -> aggregateId.equals(event.getAggregateId()))
                .toList();
        assertThat(events).hasSize(1);
        try {
            return objectMapper.readTree(events.get(0).getPayload()).path("correlationId").asText();
        } catch (JsonProcessingException exception) {
            throw new AssertionError("Pharmacy outbox payload must be valid JSON", exception);
        }
    }

    private PrescriptionDTO createPrescription(List<PrescriptionLineRequest> lines) {
        UUID doctorId = UUID.randomUUID();
        CreatePrescriptionRequest request = new CreatePrescriptionRequest(
                UUID.randomUUID(), UUID.randomUUID(), doctorId, UUID.randomUUID(),
                LocalDate.now(), lines);
        return createPrescriptionUseCase.create(new CreatePrescriptionCommand(
                request,
                new com.mediflow.pharmacy.application.dto.command.ActorIdentity(
                        UUID.randomUUID(), doctorId, "DOCTOR"),
                "create-prescription-correlation"));
    }

    private DrugJpaEntity drug(String name, int stock) {
        return DrugJpaEntity.builder()
                .drugName(name)
                .activeIngredient(name)
                .unit("viên")
                .price(new BigDecimal("500.00"))
                .stockQuantity(stock)
                .expiryDate(LocalDate.now().plusYears(1))
                .manufacturer("MediFlow")
                .lowStockThreshold(1)
                .build();
    }

    private PaymentReceipt paymentReceipt(PrescriptionDTO prescription) {
        return PaymentReceipt.receive(
                UUID.randomUUID(), UUID.randomUUID(), prescription.prescriptionId(),
                prescription.patientId(), prescription.departmentId(), BigDecimal.TEN, "CASH",
                java.time.Instant.now(), "integration-payment-correlation", null);
    }

    private PaymentReceipt paymentReceipt(PaymentCompletedCommand payment) {
        return PaymentReceipt.receive(
                payment.eventId(), payment.invoiceId(), payment.prescriptionId(),
                payment.patientId(), payment.departmentId(), payment.totalAmount(),
                payment.paymentMethod(), payment.occurredAt(), payment.correlationId(), null);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PaymentReceiptTestConfiguration {
        @Bean
        @Primary
        PaymentReceiptFaultInjector paymentReceiptFaultInjector(
                PaymentReceiptPersistenceAdapter paymentReceiptPersistenceAdapter) {
            return new PaymentReceiptFaultInjector(paymentReceiptPersistenceAdapter);
        }
    }

    /** Test-only wrapper for deterministic claim barriers and a one-shot persistence fault. */
    static final class PaymentReceiptFaultInjector implements PaymentReceiptRepositoryPort {
        private final PaymentReceiptPersistenceAdapter delegate;
        private final java.util.concurrent.ConcurrentMap<UUID, ClaimBarrier> claimBarriers =
                new ConcurrentHashMap<>();
        private final AtomicReference<UUID> eventIdForNextSaveFailure = new AtomicReference<>();

        PaymentReceiptFaultInjector(PaymentReceiptPersistenceAdapter delegate) {
            this.delegate = delegate;
        }

        void pauseAfterBothClaims(UUID eventId) {
            claimBarriers.put(eventId, new ClaimBarrier());
        }

        boolean awaitBothClaims(UUID eventId, long timeout, TimeUnit unit) throws InterruptedException {
            ClaimBarrier barrier = claimBarriers.get(eventId);
            return barrier != null && barrier.bothClaims.await(timeout, unit);
        }

        void releaseClaims(UUID eventId) {
            ClaimBarrier barrier = claimBarriers.get(eventId);
            if (barrier != null) {
                barrier.release.countDown();
            }
        }

        void failNextSave(UUID eventId) {
            if (!eventIdForNextSaveFailure.compareAndSet(null, eventId)) {
                throw new IllegalStateException("A payment receipt save failure is already armed");
            }
        }

        void reset() {
            claimBarriers.values().forEach(barrier -> barrier.release.countDown());
            claimBarriers.clear();
            eventIdForNextSaveFailure.set(null);
        }

        @Override
        public PaymentReceiptClaimResult claim(PaymentReceipt receipt) {
            // The delegate's transactional claim commits before the test barrier blocks this caller.
            PaymentReceiptClaimResult result = delegate.claim(receipt);
            ClaimBarrier barrier = claimBarriers.get(receipt.getEventId());
            if (barrier != null) {
                barrier.bothClaims.countDown();
                try {
                    if (!barrier.release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release concurrent claims");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while synchronizing receipt claims", exception);
                }
            }
            return result;
        }

        @Override
        public java.util.Optional<PaymentReceipt> findByEventId(UUID eventId) {
            return delegate.findByEventId(eventId);
        }

        @Override
        public List<PaymentReceipt> findByPrescriptionId(UUID prescriptionId) {
            return delegate.findByPrescriptionId(prescriptionId);
        }

        @Override
        public PaymentReceipt save(PaymentReceipt receipt) {
            UUID armedEventId = eventIdForNextSaveFailure.get();
            if (armedEventId != null && armedEventId.equals(receipt.getEventId())
                    && eventIdForNextSaveFailure.compareAndSet(armedEventId, null)) {
                throw new IllegalStateException("simulated receipt persistence outage after dispense");
            }
            return delegate.save(receipt);
        }

        private static final class ClaimBarrier {
            private final CountDownLatch bothClaims = new CountDownLatch(2);
            private final CountDownLatch release = new CountDownLatch(1);
        }
    }
}
