package com.mediflow.pharmacy.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mediflow.pharmacy.domain.exception.DrugRuleException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies immutable stock-adjustment audit invariants. */
class StockAdjustmentTest {

    /** Negative adjustments require a trimmed, bounded reason. */
    @Test
    void create_negativeDelta_requiresReasonAndNormalizesLength() {
        UUID drugId = UUID.randomUUID();
        assertThatThrownBy(() -> StockAdjustment.create(drugId, 10, -1, 9, " ", null, null, Instant.now()))
                .isInstanceOf(DrugRuleException.class)
                .hasMessageContaining("Lý do");

        StockAdjustment adjustment = StockAdjustment.create(drugId, 10, -1, 9,
                "  Kiểm kê  ", UUID.randomUUID(), "corr", Instant.now());
        assertThat(adjustment.getReason()).isEqualTo("Kiểm kê");
        assertThat(adjustment.getAfterStock()).isEqualTo(9);
    }

    /** Snapshot arithmetic must agree with the requested delta. */
    @Test
    void create_invalidSnapshot_isRejected() {
        assertThatThrownBy(() -> StockAdjustment.create(UUID.randomUUID(), 10, -2, 9,
                "Điều chỉnh", null, null, Instant.now()))
                .isInstanceOf(DrugRuleException.class)
                .hasMessageContaining("Snapshot");
    }
}
