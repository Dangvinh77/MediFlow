package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Owned NUMERIC(19,4) storage must preserve the exact quantity published for Billing. */
class SurgeryPerformedItemPrecisionTest {
    @ParameterizedTest
    @ValueSource(strings = {"0.0001", "999999999999999.9999", "1E14", "1.00000", "0.50000", "1", "2.1234"})
    void quantity_exactlyRepresentable_retainsValueWithoutRounding(String value) {
        var original = new BigDecimal(value);
        var item = new SurgeryPerformedItem(UUID.randomUUID(), "TEST_ITEM", "TEST_PRICE", original);
        assertThat(item.quantity()).isEqualByComparingTo(original);
        assertThat(item.quantity().scale()).isLessThanOrEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00001", "1.00001", "999999999999999.99999", "1000000000000000", "1E15", "1E-20", "1E+2147483647", "1E-2147483647"})
    void quantity_roundingOrOverflowWouldBeRequired_rejectsBeforePersistence(String value) {
        assertThatThrownBy(() -> new SurgeryPerformedItem(UUID.randomUUID(), "TEST_ITEM", "TEST_PRICE", new BigDecimal(value)))
                .isInstanceOf(SurgeryRuleException.class)
                .hasMessageContaining("số lượng")
                .satisfies(failure -> assertThat(((SurgeryRuleException) failure).getCode()).isEqualTo("SURGERY_PERFORMED_ITEM_INVALID"));
    }
}
