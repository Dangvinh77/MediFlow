package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.pharmacy.application.dto.command.CreatePrescriptionCommand;
import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;
import com.mediflow.pharmacy.application.dto.request.PrescriptionLineRequest;
import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import com.mediflow.pharmacy.application.port.in.CreatePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.DispensePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.out.PaymentReceiptRepositoryPort;
import com.mediflow.pharmacy.domain.model.PaymentReceipt;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;
import com.mediflow.pharmacy.domain.model.enums.ReservationStatus;
import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.DrugJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.DispenseSlipJpaRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.DrugJpaEntityRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PrescriptionJpaRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.StockReservationJpaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
@Testcontainers(disabledWithoutDocker = true)
class DispenseIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CreatePrescriptionUseCase createPrescriptionUseCase;

    @Autowired
    private DispensePrescriptionUseCase dispensePrescriptionUseCase;

    @Autowired
    private DrugJpaEntityRepository drugRepository;

    @Autowired
    private PrescriptionJpaRepository prescriptionRepository;

    @Autowired
    private DispenseSlipJpaRepository dispenseSlipRepository;

    @Autowired
    private StockReservationJpaRepository reservationRepository;

    @MockBean
    private PharmacyEventPublisherPort eventPublisher;

    @MockBean
    private PaymentReceiptRepositoryPort paymentReceiptRepository;

    /** Removes aggregate rows in foreign-key order between integration cases. */
    @AfterEach
    void cleanDatabase() {
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
        when(paymentReceiptRepository.findByPrescriptionId(prescription.prescriptionId()))
                .thenReturn(List.of(paymentReceipt(prescription.prescriptionId())));
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
        when(paymentReceiptRepository.findByPrescriptionId(prescription.prescriptionId()))
                .thenReturn(List.of(paymentReceipt(prescription.prescriptionId())));

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
        verify(eventPublisher).publishPrescriptionDispenseFailed(any());
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

    private PaymentReceipt paymentReceipt(UUID prescriptionId) {
        return PaymentReceipt.receive(
                UUID.randomUUID(), UUID.randomUUID(), prescriptionId,
                UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, "CASH",
                java.time.Instant.now(), "integration-payment-correlation", null);
    }
}
