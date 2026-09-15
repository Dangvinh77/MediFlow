package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.command.CancelPrescriptionCommand;
import com.mediflow.pharmacy.application.dto.command.CreatePrescriptionCommand;
import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;
import com.mediflow.pharmacy.application.dto.request.PrescriptionLineRequest;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import com.mediflow.pharmacy.application.port.in.CancelPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.CreatePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.DispensePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.ReleaseExpiredReservationsUseCase;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.out.PaymentReceiptRepositoryPort;
import com.mediflow.pharmacy.domain.model.PaymentReceipt;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;
import com.mediflow.pharmacy.domain.model.enums.ReservationStatus;
import com.mediflow.pharmacy.domain.exception.StockReservationRuleException;
import com.mediflow.pharmacy.infrastructure.persistence.jpaentity.DrugJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.DispenseSlipJpaRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.DrugJpaEntityRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PrescriptionJpaRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.StockReservationJpaRepository;

/** PostgreSQL concurrency matrix for lifecycle, reservation and stock invariants. */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "mediflow.pharmacy.outbox.enabled=false",
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
        "mediflow.pharmacy.reservation.release-cron=-",
        "mediflow.pharmacy.reservation.reconciliation-cron=-"
})
@Testcontainers(disabledWithoutDocker = true)
class PrescriptionLifecycleConcurrencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private CreatePrescriptionUseCase create;
    @Autowired private CancelPrescriptionUseCase cancel;
    @Autowired private DispensePrescriptionUseCase dispense;
    @Autowired private ReleaseExpiredReservationsUseCase expire;
    @Autowired private DrugJpaEntityRepository drugs;
    @Autowired private PrescriptionJpaRepository prescriptions;
    @Autowired private DispenseSlipJpaRepository slips;
    @Autowired private StockReservationJpaRepository reservations;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private PharmacyEventPublisherPort publisher;
    @MockBean private PaymentReceiptRepositoryPort payments;

    private UUID drugId;
    private UUID prescriptionId;
    private final List<UUID> createdPrescriptionIds = new CopyOnWriteArrayList<>();

    /** Cleans all rows created by the current test without relying on test order. */
    @AfterEach
    void clean() {
        reservations.deleteAllInBatch();
        slips.deleteAllInBatch();
        prescriptions.deleteAllInBatch();
        if (drugId != null) {
            drugs.deleteById(drugId);
        }
        createdPrescriptionIds.clear();
    }

    /** Cancel and dispense have one prescription lock and therefore one terminal winner. */
    @Test
    void cancelVsDispense_singleWinner() throws Exception {
        DrugJpaEntity drug = saveDrug(3);
        PrescriptionDTO prescription = createPrescription(drug, 2);
        clearInvocations(publisher);
        when(payments.findByPrescriptionId(prescription.prescriptionId()))
                .thenReturn(List.of(payment(prescription.prescriptionId())));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            List<Future<Object>> results = executor.invokeAll(List.of(
                    race(() -> cancel.cancel(new CancelPrescriptionCommand(
                            prescription.prescriptionId(), admin(), "race", "cancel-race")), barrier),
                    race(() -> dispense.dispense(prescription.prescriptionId(), UUID.randomUUID(),
                            "dispense-race"), barrier)), 10, TimeUnit.SECONDS);
            assertThat(results.stream().map(this::await).filter(this::successfulOutcome).count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
        assertThat(prescriptions.findById(prescription.prescriptionId()).orElseThrow().getStatus())
                .isIn(PrescriptionStatus.CANCELLED, PrescriptionStatus.FULFILLED, PrescriptionStatus.DISPENSE_FAILED);
        assertThat(drugs.findById(drug.getDrugId()).orElseThrow().getStockQuantity()).isGreaterThanOrEqualTo(1);
        assertThat(reservations.findByPrescriptionId(prescription.prescriptionId()))
                .allSatisfy(row -> assertThat(row.getStatus()).isNotEqualTo(ReservationStatus.RESERVED));
        assertSingleTerminalEvent();
    }

    /** Expiry and dispense serialize; expiry event/transition cannot be applied twice. */
    @Test
    void expireVsDispense_singleWinner() throws Exception {
        DrugJpaEntity drug = saveDrug(3);
        PrescriptionDTO prescription = createPrescription(drug, 2);
        clearInvocations(publisher);
        jdbc.update("UPDATE STOCK_RESERVATION SET expires_at = ? WHERE prescription_id = ?",
                Timestamp.from(Instant.now().minusSeconds(30)), prescription.prescriptionId());
        when(payments.findByPrescriptionId(prescription.prescriptionId()))
                .thenReturn(List.of(payment(prescription.prescriptionId())));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            List<Future<Object>> results = executor.invokeAll(List.of(
                    race(() -> expire.releaseExpiredReservations(), barrier),
                    race(() -> dispense.dispense(prescription.prescriptionId(), UUID.randomUUID(), "expiry-race"), barrier)),
                    10, TimeUnit.SECONDS);
            // Completion plus one durable terminal event is the logical-winner contract. The
            // losing caller may legitimately return zero or a business exception after locking.
            assertThat(results.stream().map(this::await).toList()).hasSize(2);
        } finally {
            executor.shutdownNow();
        }
        assertThat(prescriptions.findById(prescription.prescriptionId()).orElseThrow().getStatus())
                .isIn(PrescriptionStatus.EXPIRED, PrescriptionStatus.FULFILLED, PrescriptionStatus.DISPENSE_FAILED);
        assertThat(reservations.findByPrescriptionId(prescription.prescriptionId()))
                .allSatisfy(row -> assertThat(row.getStatus()).isNotEqualTo(ReservationStatus.RESERVED));
        assertSingleTerminalEvent();
    }

    /** Cancel and expiry share the same prescription lock and emit at most one terminal result. */
    @Test
    void cancelVsExpire_singleWinner() throws Exception {
        DrugJpaEntity drug = saveDrug(3);
        PrescriptionDTO prescription = createPrescription(drug, 2);
        clearInvocations(publisher);
        jdbc.update("UPDATE STOCK_RESERVATION SET expires_at = ? WHERE prescription_id = ?",
                Timestamp.from(Instant.now().minusSeconds(30)), prescription.prescriptionId());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            List<Future<Object>> results = executor.invokeAll(List.of(
                    race(() -> expire.releaseExpiredReservations(), barrier),
                    race(() -> cancel.cancel(new CancelPrescriptionCommand(
                            prescription.prescriptionId(), admin(), "race", "cancel-expire-race")), barrier)),
                    10, TimeUnit.SECONDS);
            assertThat(results.stream().map(this::await).filter(this::successfulOutcome).count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
        assertThat(prescriptions.findById(prescription.prescriptionId()).orElseThrow().getStatus())
                .isIn(PrescriptionStatus.CANCELLED, PrescriptionStatus.EXPIRED);
        assertThat(reservations.findByPrescriptionId(prescription.prescriptionId()))
                .allSatisfy(row -> assertThat(row.getStatus()).isNotEqualTo(ReservationStatus.RESERVED));
        assertSingleTerminalEvent();
    }

    /** Two prescriptions cannot reserve more than the same drug's available stock. */
    @Test
    void twoPrescriptionsSameDrug_onlyOneReservationFits() throws Exception {
        DrugJpaEntity drug = saveDrug(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Future<Object>> results;
        try {
            results = executor.invokeAll(List.of(
                    race(() -> createPrescription(drug, 1), barrier),
                    race(() -> createPrescription(drug, 1), barrier)), 10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        long successes = results.stream().map(future -> {
            try {
                return future.get();
            } catch (Exception exception) {
                return exception;
            }
        }).filter(PrescriptionDTO.class::isInstance).count();
        assertThat(successes).isEqualTo(1);
        assertThat(results.stream().map(future -> {
            try { return future.get(); } catch (Exception exception) { return exception; }
        }).filter(StockReservationRuleException.class::isInstance).count()).isEqualTo(1);
        assertThat(reservations.findByDrugIdAndStatus(drug.getDrugId(), ReservationStatus.RESERVED))
                .singleElement();
    }

    private <T> Callable<Object> race(Callable<T> action, CyclicBarrier barrier) {
        return () -> {
            barrier.await(5, TimeUnit.SECONDS);
            try {
                return action.call();
            } catch (RuntimeException exception) {
                return exception;
            }
        };
    }

    private Object await(Future<Object> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Concurrency worker was interrupted", exception);
        } catch (java.util.concurrent.TimeoutException | CancellationException exception) {
            throw new AssertionError("Concurrency worker did not complete", exception);
        } catch (ExecutionException exception) {
            throw new AssertionError("Concurrency worker failed", exception.getCause());
        }
    }

    private boolean successfulOutcome(Object outcome) {
        if (outcome instanceof RuntimeException || outcome instanceof Exception) {
            return false;
        }
        if (outcome instanceof Integer count) {
            return count > 0;
        }
        return outcome != null;
    }

    private void assertSingleTerminalEvent() {
        java.util.Set<String> terminalMethods = java.util.Set.of(
                "publishPrescriptionCancelled",
                "publishPrescriptionExpired",
                "publishPrescriptionFilled",
                "publishPrescriptionDispenseFailed");
        long terminalEventCount = mockingDetails(publisher).getInvocations().stream()
                .map(invocation -> invocation.getMethod().getName())
                .filter(terminalMethods::contains)
                .count();
        assertThat(terminalEventCount).isOne();
    }

    private DrugJpaEntity saveDrug(int stock) {
        DrugJpaEntity drug = drugs.saveAndFlush(DrugJpaEntity.builder()
                .drugName("Lifecycle drug").activeIngredient("ingredient").unit("tablet")
                .price(new BigDecimal("10.00")).stockQuantity(stock)
                .expiryDate(LocalDate.now().plusYears(1)).manufacturer("MediFlow")
                .lowStockThreshold(0).build());
        drugId = drug.getDrugId();
        return drug;
    }

    private PrescriptionDTO createPrescription(DrugJpaEntity drug, int quantity) {
        UUID doctor = UUID.randomUUID();
        CreatePrescriptionRequest request = new CreatePrescriptionRequest(
                UUID.randomUUID(), UUID.randomUUID(), doctor, UUID.randomUUID(), LocalDate.now(),
                List.of(new PrescriptionLineRequest(drug.getDrugId(), quantity, "once")));
        PrescriptionDTO result = create.create(new CreatePrescriptionCommand(
                request, new ActorIdentity(UUID.randomUUID(), doctor, "DOCTOR"), "lifecycle-create"));
        prescriptionId = result.prescriptionId();
        createdPrescriptionIds.add(result.prescriptionId());
        return result;
    }

    private ActorIdentity admin() {
        return new ActorIdentity(UUID.randomUUID(), null, "ADMIN");
    }

    private PaymentReceipt payment(UUID id) {
        return PaymentReceipt.receive(UUID.randomUUID(), UUID.randomUUID(), id, UUID.randomUUID(),
                UUID.randomUUID(), BigDecimal.TEN, "CASH", Instant.now(), "payment", null);
    }
}
