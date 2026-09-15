package com.mediflow.pharmacy.application.service;

import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;
import com.mediflow.pharmacy.application.dto.request.PrescriptionLineRequest;
import com.mediflow.pharmacy.application.dto.command.CreatePrescriptionCommand;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import com.mediflow.pharmacy.application.port.in.CreatePrescriptionUseCase;
import com.mediflow.pharmacy.infrastructure.messaging.PharmacyEventPublisherAdapter;
import com.mediflow.pharmacy.infrastructure.persistence.jpaentity.DrugJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.DispenseSlipJpaRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.DrugJpaEntityRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PharmacyEventOutboxJpaRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PrescriptionJpaRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.StockReservationJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "mediflow.pharmacy.outbox.enabled=false",
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes"
})
@Testcontainers(disabledWithoutDocker = true)
class PrescriptionCreationTransactionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CreatePrescriptionUseCase useCase;

    @Autowired
    private DrugJpaEntityRepository drugRepository;

    @Autowired
    private PrescriptionJpaRepository prescriptionRepository;

    @Autowired
    private StockReservationJpaRepository reservationRepository;

    @Autowired
    private DispenseSlipJpaRepository dispenseSlipRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PharmacyEventOutboxJpaRepository outboxRepository;

    @SpyBean
    private PharmacyEventPublisherAdapter eventPublisher;

    @AfterEach
    void cleanDatabase() {
        outboxRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        dispenseSlipRepository.deleteAllInBatch();
        prescriptionRepository.deleteAllInBatch();
        drugRepository.deleteAllInBatch();
    }

    @Test
    void create_outboxStepFails_rollsBackBusinessRowsAndOutbox() {
        UUID firstDrugId = drugRepository.saveAndFlush(drugEntity("Paracetamol", "1000.00"))
                .getDrugId();
        UUID secondDrugId = drugRepository.saveAndFlush(drugEntity("Amoxicillin", "2500.00"))
                .getDrugId();

        doAnswer(invocation -> {
            invocation.callRealMethod();
            entityManager.flush();
            throw new IllegalStateException("Mô phỏng lỗi sau khi ghi outbox");
        }).when(eventPublisher).publishPrescriptionCreated(any());

        CreatePrescriptionRequest request = new CreatePrescriptionRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                List.of(
                        new PrescriptionLineRequest(firstDrugId, 2, "Ngày 2 lần"),
                        new PrescriptionLineRequest(secondDrugId, 3, "Ngày 3 lần")));

        assertThatThrownBy(() -> useCase.create(new CreatePrescriptionCommand(
                request,
                new com.mediflow.pharmacy.application.dto.command.ActorIdentity(
                        UUID.randomUUID(), request.doctorId(), "DOCTOR"),
                "test-correlation")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Mô phỏng lỗi sau khi ghi outbox");

        assertThat(prescriptionRepository.count()).isZero();
        assertThat(reservationRepository.count()).isZero();
        assertThat(dispenseSlipRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    /** A successful create commits the aggregate and its event in the same database transaction. */
    @Test
    void create_success_commitsBusinessRowsAndOutboxTogether() {
        UUID drugId = drugRepository.saveAndFlush(drugEntity("Paracetamol", "1000.00"))
                .getDrugId();
        UUID doctorId = UUID.randomUUID();
        CreatePrescriptionRequest request = new CreatePrescriptionRequest(
                UUID.randomUUID(), UUID.randomUUID(), doctorId, UUID.randomUUID(), LocalDate.now(),
                List.of(new PrescriptionLineRequest(drugId, 2, "Ngày 2 lần")));

        PrescriptionDTO result = useCase.create(new CreatePrescriptionCommand(
                request,
                new com.mediflow.pharmacy.application.dto.command.ActorIdentity(
                        UUID.randomUUID(), doctorId, "DOCTOR"),
                "test-correlation"));

        assertThat(prescriptionRepository.count()).isOne();
        assertThat(reservationRepository.count()).isOne();
        assertThat(dispenseSlipRepository.count()).isOne();
        assertThat(outboxRepository.findAll())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getRoutingKey()).isEqualTo("prescription.created");
                    assertThat(event.getAggregateId()).isEqualTo(result.prescriptionId());
                    assertThat(event.getPublishedAt()).isNull();
                });
    }

    private DrugJpaEntity drugEntity(String name, String price) {
        return DrugJpaEntity.builder()
                .drugName(name)
                .activeIngredient(name)
                .unit("viên")
                .price(new BigDecimal(price))
                .stockQuantity(100)
                .expiryDate(LocalDate.now().plusYears(1))
                .manufacturer("Dược phẩm VN")
                .lowStockThreshold(10)
                .build();
    }
}
