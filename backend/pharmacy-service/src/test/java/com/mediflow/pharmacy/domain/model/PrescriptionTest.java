package com.mediflow.pharmacy.domain.model;

import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Kiểm tra các invariant của aggregate đơn thuốc. */
class PrescriptionTest {

    @Test
    void create_multipleDrugs_calculatesRoundedTotal() {
        PrescriptionLine first = PrescriptionLine.create(
                UUID.randomUUID(), 3, new BigDecimal("1000.00"), "Ngày 3 lần");
        PrescriptionLine second = PrescriptionLine.create(
                UUID.randomUUID(), 2, new BigDecimal("2500.00"), "Ngày 2 lần");

        Prescription prescription = Prescription.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                List.of(first, second));

        assertThat(prescription.getLines()).hasSize(2);
        assertThat(prescription.getTotalAmount()).isEqualByComparingTo("8000.00");
    }

    @Test
    void create_duplicateDrug_throwsBusinessRule() {
        UUID drugId = UUID.randomUUID();
        PrescriptionLine first = PrescriptionLine.create(
                drugId, 2, new BigDecimal("1000.00"), "Buổi sáng");
        PrescriptionLine duplicate = PrescriptionLine.create(
                drugId, 3, new BigDecimal("1000.00"), "Buổi tối");

        assertThatThrownBy(() -> Prescription.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                List.of(first, duplicate)))
                .isInstanceOfSatisfying(
                        PrescriptionRuleException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("PRESCRIPTION_DUPLICATE_DRUG"));
    }
}
