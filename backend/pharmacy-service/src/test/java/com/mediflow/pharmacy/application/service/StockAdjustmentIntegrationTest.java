package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.command.CreatePrescriptionCommand;
import com.mediflow.pharmacy.application.dto.request.AdjustStockRequest;
import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;
import com.mediflow.pharmacy.application.dto.request.PrescriptionLineRequest;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.in.ManageDrugUseCase;
import com.mediflow.pharmacy.application.port.in.CreatePrescriptionUseCase;
import com.mediflow.pharmacy.infrastructure.persistence.jpaentity.DrugJpaEntity;
import com.mediflow.pharmacy.domain.model.enums.ReservationStatus;
import com.mediflow.pharmacy.infrastructure.persistence.repository.DrugJpaEntityRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.DispenseSlipJpaRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PrescriptionJpaRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.StockAdjustmentJpaRepository;
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

/** PostgreSQL integration coverage for durable stock audit and transaction rollback. */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "mediflow.pharmacy.outbox.enabled=false",
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
        "mediflow.pharmacy.reservation.release-cron=-"
})
@Testcontainers(disabledWithoutDocker = true)
class StockAdjustmentIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ManageDrugUseCase manageDrugUseCase;

    @Autowired
    private CreatePrescriptionUseCase createPrescriptionUseCase;

    @Autowired
    private DrugJpaEntityRepository drugRepository;

    @Autowired
    private StockAdjustmentJpaRepository adjustmentRepository;

    @Autowired
    private StockReservationJpaRepository reservationRepository;

    @Autowired
    private PrescriptionJpaRepository prescriptionRepository;

    @Autowired
    private DispenseSlipJpaRepository dispenseSlipRepository;

    @MockBean
    private PharmacyEventPublisherPort eventPublisher;

    /** Removes audit rows before drugs so every test starts from an empty aggregate. */
    @AfterEach
    void cleanDatabase() {
        adjustmentRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        dispenseSlipRepository.deleteAllInBatch();
        prescriptionRepository.deleteAllInBatch();
        drugRepository.deleteAllInBatch();
    }

    /** A successful adjustment persists the immutable before/delta/after audit snapshot. */
    @Test
    void adjustStock_persistsDurableAudit() {
        DrugJpaEntity drug = drugRepository.saveAndFlush(drug(10));
        UUID actorId = UUID.randomUUID();

        manageDrugUseCase.adjustStock(
                drug.getDrugId(), new AdjustStockRequest(-2, "  Kiểm kê cuối ca  "),
                actorId, "corr-stock-001");

        assertThat(drugRepository.findById(drug.getDrugId()).orElseThrow().getStockQuantity())
                .isEqualTo(8);
        assertThat(adjustmentRepository.findAll())
                .singleElement()
                .satisfies(a -> {
                    assertThat(a.getDrugId()).isEqualTo(drug.getDrugId());
                    assertThat(a.getBeforeStock()).isEqualTo(10);
                    assertThat(a.getDelta()).isEqualTo(-2);
                    assertThat(a.getAfterStock()).isEqualTo(8);
                    assertThat(a.getReason()).isEqualTo("Kiểm kê cuối ca");
                    assertThat(a.getActorId()).isEqualTo(actorId);
                    assertThat(a.getCorrelationId()).isEqualTo("corr-stock-001");
                });
    }

    /** A publisher failure rolls back both the stock mutation and its audit row atomically. */
    @Test
    void adjustStock_eventFailure_rollsBackDrugAndAudit() {
        DrugJpaEntity drug = drugRepository.saveAndFlush(drug(10));
        doThrow(new IllegalStateException("outbox unavailable"))
                .when(eventPublisher).publishStockAdjusted(any());

        assertThatThrownBy(() -> manageDrugUseCase.adjustStock(
                drug.getDrugId(), new AdjustStockRequest(-2, "Kiểm kê"),
                UUID.randomUUID(), "corr-stock-rollback"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox unavailable");

        assertThat(drugRepository.findById(drug.getDrugId()).orElseThrow().getStockQuantity())
                .isEqualTo(10);
        assertThat(adjustmentRepository.count()).isZero();
    }

    /** Concurrent adjustment and prescription creation serialize on the drug lock. */
    @Test
    void adjustStock_racesWithPrescriptionCreation_preservesReservedInvariant() throws Exception {
        DrugJpaEntity drug = drugRepository.saveAndFlush(drug(10));
        UUID doctorId = UUID.randomUUID();
        CreatePrescriptionRequest request = new CreatePrescriptionRequest(
                UUID.randomUUID(), UUID.randomUUID(), doctorId, UUID.randomUUID(),
                LocalDate.now(), List.of(new PrescriptionLineRequest(
                        drug.getDrugId(), 8, "Ngày 2 lần")));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> adjustment = executor.submit(() -> {
                await(start, ready);
                try {
                    manageDrugUseCase.adjustStock(
                            drug.getDrugId(), new AdjustStockRequest(-5, "Kiểm kê"),
                            UUID.randomUUID(), "corr-race-adjust");
                    return true;
                } catch (BusinessRuleException exception) {
                    return false;
                }
            });
            Future<Boolean> creation = executor.submit(() -> {
                await(start, ready);
                try {
                    createPrescriptionUseCase.create(new CreatePrescriptionCommand(
                            request,
                            new ActorIdentity(UUID.randomUUID(), doctorId, "DOCTOR"),
                            "corr-race-create"));
                    return true;
                } catch (BusinessRuleException exception) {
                    return false;
                }
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            boolean adjustmentWon = adjustment.get(10, TimeUnit.SECONDS);
            boolean creationWon = creation.get(10, TimeUnit.SECONDS);
            assertThat(adjustmentWon).isNotEqualTo(creationWon);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        int reserved = reservationRepository.findByDrugIdAndStatus(
                        drug.getDrugId(), ReservationStatus.RESERVED).stream()
                .mapToInt(reservation -> reservation.getQuantity())
                .sum();
        assertThat(drugRepository.findById(drug.getDrugId()).orElseThrow().getStockQuantity())
                .isGreaterThanOrEqualTo(reserved);
    }

    private void await(CountDownLatch start, CountDownLatch ready) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out waiting for race test start");
        }
    }

    private DrugJpaEntity drug(int stock) {
        return DrugJpaEntity.builder()
                .drugName("Audit drug " + UUID.randomUUID())
                .activeIngredient("Hoạt chất")
                .unit("viên")
                .price(new BigDecimal("1000.00"))
                .stockQuantity(stock)
                .expiryDate(LocalDate.now().plusYears(1))
                .manufacturer("MediFlow")
                .lowStockThreshold(1)
                .build();
    }
}
