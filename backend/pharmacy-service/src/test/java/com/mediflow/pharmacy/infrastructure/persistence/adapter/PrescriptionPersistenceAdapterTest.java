package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import com.mediflow.pharmacy.domain.model.Drug;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.PrescriptionLine;
import com.mediflow.pharmacy.domain.model.enums.ReservationStatus;
import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.StockReservationJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.StockReservationJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({DrugPersistenceAdapter.class, PrescriptionPersistenceAdapter.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PrescriptionPersistenceAdapterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private DrugPersistenceAdapter drugAdapter;

    @Autowired
    private PrescriptionPersistenceAdapter prescriptionAdapter;

    @Autowired
    private StockReservationJpaRepository reservationRepository;

    @Test
    void save_drugPriceChangesLater_preservesCapturedPrescriptionPrice() {
        Drug drug = drugAdapter.save(newDrug("Paracetamol", "1000.00"));
        PrescriptionLine line = PrescriptionLine.create(
                drug.getDrugId(), 2, drug.getPrice(), "Ngày 2 lần");
        Prescription prescription = prescriptionAdapter.save(Prescription.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                List.of(line)));

        drug.updateInfo(
                drug.getDrugName(),
                drug.getActiveIngredient(),
                drug.getUnit(),
                new BigDecimal("2500.00"),
                drug.getExpiryDate(),
                drug.getManufacturer(),
                drug.getLowStockThreshold());
        drugAdapter.save(drug);

        Prescription reloaded = prescriptionAdapter
                .findById(prescription.getPrescriptionId())
                .orElseThrow();

        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getLines()).hasSize(1);
        assertThat(reloaded.getLines().get(0).getUnitPrice())
                .isEqualByComparingTo("1000.00");
        assertThat(reloaded.getLines().get(0).getLineTotal())
                .isEqualByComparingTo("2000.00");
    }

    @Test
    void save_duplicatePrescriptionAndDrugReservation_violatesUniqueConstraint() {
        Drug drug = drugAdapter.save(newDrug("Amoxicillin", "2500.00"));
        Prescription prescription = prescriptionAdapter.save(Prescription.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                List.of(PrescriptionLine.create(
                        drug.getDrugId(), 1, drug.getPrice(), "Ngày 2 lần"))));

        reservationRepository.saveAndFlush(reservationEntity(
                prescription.getPrescriptionId(), drug.getDrugId(), 1));

        assertThatThrownBy(() -> reservationRepository.saveAndFlush(reservationEntity(
                prescription.getPrescriptionId(), drug.getDrugId(), 2)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Drug newDrug(String name, String price) {
        return Drug.create(
                name,
                name,
                "viên",
                new BigDecimal(price),
                100,
                LocalDate.now().plusYears(1),
                "Dược phẩm VN",
                10);
    }

    private StockReservationJpaEntity reservationEntity(
            UUID prescriptionId,
            UUID drugId,
            int quantity) {
        return StockReservationJpaEntity.builder()
                .prescriptionId(prescriptionId)
                .drugId(drugId)
                .quantity(quantity)
                .status(ReservationStatus.RESERVED)
                .expiresAt(Instant.now().plusSeconds(24 * 3600))
                .build();
    }
}
