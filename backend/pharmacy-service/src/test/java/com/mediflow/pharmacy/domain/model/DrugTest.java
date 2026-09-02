package com.mediflow.pharmacy.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.mediflow.pharmacy.domain.exception.DrugRuleException;

class DrugTest {

    @Test
    void create_negativeInitialStock_throws() {
        assertThatThrownBy(() -> create(-1))
                .isInstanceOf(DrugRuleException.class)
                .hasMessageContaining("tồn kho không được âm");
    }

    @Test
    void create_missingPrice_throwsBusinessRuleException() {
        assertThatThrownBy(() -> Drug.create(
                "Paracetamol", null, "viên", null, 10,
                LocalDate.now().plusDays(1), null, 2))
                .isInstanceOf(DrugRuleException.class);
    }

    @Test
    void adjustStock_negativeQuantity_decrementsWithoutGoingBelowZero() {
        Drug drug = create(10);

        drug.adjustStock(-4);

        assertThat(drug.getStockQuantity()).isEqualTo(6);
    }

    @Test
    void adjustStock_belowZero_throws() {
        Drug drug = create(3);

        assertThatThrownBy(() -> drug.adjustStock(-4))
                .isInstanceOf(DrugRuleException.class)
                .hasMessageContaining("tồn kho âm");
    }

    private Drug create(int stockQuantity) {
        return Drug.create(
                "Paracetamol", "Paracetamol", "viên", new BigDecimal("1200.00"), stockQuantity,
                LocalDate.now().plusDays(30), "MediFlow", 2);
    }
}
