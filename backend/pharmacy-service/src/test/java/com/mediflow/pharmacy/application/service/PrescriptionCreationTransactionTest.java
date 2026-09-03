package com.mediflow.pharmacy.application.service;

import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;
import com.mediflow.pharmacy.application.dto.request.PrescriptionLineRequest;
import com.mediflow.pharmacy.application.port.in.CreatePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.out.DispenseSlipRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.domain.model.DispenseSlip;
import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.DrugJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.DispenseSlipJpaRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.DrugJpaEntityRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PrescriptionJpaRepository;
import com.mediflow.pharmacy.infrastructure.persistence.repository.StockReservationJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
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

    @MockBean
    private DispenseSlipRepositoryPort dispenseSlipPort;

    @MockBean
    private PharmacyEventPublisherPort eventPublisher;

    @AfterEach
    void cleanDatabase() {
        reservationRepository.deleteAllInBatch();
        dispenseSlipRepository.deleteAllInBatch();
        prescriptionRepository.deleteAllInBatch();
        drugRepository.deleteAllInBatch();
    }

    @Test
    void create_lateStepFails_rollsBackPrescriptionLinesAndReservations() {
        UUID firstDrugId = drugRepository.saveAndFlush(drugEntity("Paracetamol", "1000.00"))
                .getDrugId();
        UUID secondDrugId = drugRepository.saveAndFlush(drugEntity("Amoxicillin", "2500.00"))
                .getDrugId();

        when(dispenseSlipPort.save(any(DispenseSlip.class)))
                .thenAnswer(invocation -> {
                    entityManager.flush();
                    throw new IllegalStateException("Mô phỏng lỗi lưu phiếu xuất");
                });

        CreatePrescriptionRequest request = new CreatePrescriptionRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                List.of(
                        new PrescriptionLineRequest(firstDrugId, 2, "Ngày 2 lần"),
                        new PrescriptionLineRequest(secondDrugId, 3, "Ngày 3 lần")));

        assertThatThrownBy(() -> useCase.create(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Mô phỏng lỗi lưu phiếu xuất");

        assertThat(prescriptionRepository.count()).isZero();
        assertThat(reservationRepository.count()).isZero();
        assertThat(dispenseSlipRepository.count()).isZero();
        verify(eventPublisher, never()).publishPrescriptionCreated(any());
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
