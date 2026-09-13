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

    /** Giá thuốc âm phải bị chặn ngay tại domain boundary. */
    @Test
    void create_negativePrice_throws() {
        assertThatThrownBy(() -> Drug.create(
                "Paracetamol", "Paracetamol", "viên", new BigDecimal("-0.01"), 10,
                LocalDate.now().plusDays(1), "MediFlow", 2))
                .isInstanceOf(DrugRuleException.class)
                .hasMessageContaining("không được âm");
    }

    /** Tồn khởi tạo và ngưỡng cảnh báo âm phải bị từ chối độc lập. */
    @Test
    void create_negativeThreshold_throws() {
        assertThatThrownBy(() -> Drug.create(
                "Paracetamol", "Paracetamol", "viên", new BigDecimal("1200.00"), 10,
                LocalDate.now().plusDays(1), "MediFlow", -1))
                .isInstanceOf(DrugRuleException.class)
                .hasMessageContaining("Ngưỡng cảnh báo tồn kho");
    }

    /** Hạn dùng đã qua phải bị từ chối để không đưa thuốc hết hạn vào danh mục. */
    @Test
    void create_pastExpiry_throws() {
        assertThatThrownBy(() -> Drug.create(
                "Paracetamol", "Paracetamol", "viên", new BigDecimal("1200.00"), 10,
                LocalDate.now().minusDays(1), "MediFlow", 2))
                .isInstanceOf(DrugRuleException.class)
                .hasMessageContaining("quá khứ");
    }

    /** Cập nhật ngưỡng âm phải dùng cùng invariant với lúc tạo thuốc. */
    @Test
    void updateInfo_negativeThreshold_throws() {
        Drug drug = create(10);

        assertThatThrownBy(() -> drug.updateInfo(
                "Paracetamol", "Paracetamol", "viên", new BigDecimal("1200.00"),
                LocalDate.now().plusDays(30), "MediFlow", -1))
                .isInstanceOf(DrugRuleException.class)
                .hasMessageContaining("Ngưỡng cảnh báo tồn kho");
    }

    /** Domain không tự làm tròn giá đã nhập; phép tính tiền giữ nguyên BigDecimal chính xác. */
    @Test
    void create_fractionalPrice_preservesExactScale() {
        Drug drug = Drug.create(
                "Paracetamol", "Paracetamol", "viên", new BigDecimal("1200.005"), 10,
                LocalDate.now().plusDays(1), "MediFlow", 2);

        assertThat(drug.getPrice()).isEqualByComparingTo("1200.005");
    }

    /** Hạn bằng ngày nghiệp vụ vẫn còn dùng được; ngày sau đó mới bị xem là hết hạn. */
    @Test
    void isExpiredOn_usesBusinessDateBoundary() {
        Drug drug = Drug.restore(
                java.util.UUID.randomUUID(), "Paracetamol", "Paracetamol", "viên",
                new BigDecimal("1200.00"), 10, LocalDate.of(2026, 9, 13),
                "MediFlow", 2, null, null);

        assertThat(drug.isExpiredOn(LocalDate.of(2026, 9, 13))).isFalse();
        assertThat(drug.isExpiredOn(LocalDate.of(2026, 9, 14))).isTrue();
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

    /** Điều chỉnh vượt giới hạn int phải có mã lỗi số lượng, không bị báo nhầm là hết hàng. */
    @Test
    void adjustStock_overflow_throwsQuantityError() {
        Drug drug = Drug.restore(
                java.util.UUID.randomUUID(), "Paracetamol", "Paracetamol", "viên",
                new BigDecimal("1200.00"), Integer.MAX_VALUE, LocalDate.now().plusDays(30),
                "MediFlow", 2, null, null);

        assertThatThrownBy(() -> drug.adjustStock(1))
                .isInstanceOf(DrugRuleException.class)
                .hasMessageContaining("vượt giới hạn");
    }

    private Drug create(int stockQuantity) {
        return Drug.create(
                "Paracetamol", "Paracetamol", "viên", new BigDecimal("1200.00"), stockQuantity,
                LocalDate.now().plusDays(30), "MediFlow", 2);
    }
}
