package com.mediflow.billing.infrastructure.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Unit test thuần Java cho {@link PriceListAdapter} — không cần Spring. Có mục riêng thì dùng giá
 * đó, không có thì rơi về giá mặc định (không bao giờ trả null → billing luôn sinh được phí).
 */
class PriceListAdapterTest {

    private PriceListProperties props(BigDecimal defaultExam, BigDecimal defaultLab,
                                     Map<UUID, BigDecimal> byDept, Map<String, BigDecimal> byType) {
        PriceListProperties p = new PriceListProperties();
        p.setDefaultExamFee(defaultExam);
        p.setDefaultLabFee(defaultLab);
        p.setExamFeeByDepartment(byDept);
        p.setLabFeeByType(byType);
        return p;
    }

    @Test
    void examFee_usesPerDepartmentOverrideWhenPresent_elseDefault() {
        UUID dept = UUID.randomUUID();
        PriceListAdapter adapter = new PriceListAdapter(props(
                new BigDecimal("150000"), new BigDecimal("80000"),
                Map.of(dept, new BigDecimal("250000")), Map.of()));

        assertThat(adapter.examFee(dept)).isEqualByComparingTo("250000");
        assertThat(adapter.examFee(UUID.randomUUID())).isEqualByComparingTo("150000");
    }

    @Test
    void labFee_usesPerTypeOverrideWhenPresent_elseDefault() {
        PriceListAdapter adapter = new PriceListAdapter(props(
                new BigDecimal("150000"), new BigDecimal("80000"),
                Map.of(), Map.of("CBC", new BigDecimal("120000"))));

        assertThat(adapter.labFee("CBC")).isEqualByComparingTo("120000");
        assertThat(adapter.labFee("UNKNOWN")).isEqualByComparingTo("80000");
    }

    @Test
    void defaults_areSaneOutOfTheBox() {
        PriceListProperties p = new PriceListProperties();

        assertThat(p.getDefaultExamFee()).isEqualByComparingTo("150000");
        assertThat(p.getDefaultLabFee()).isEqualByComparingTo("80000");
        assertThat(p.getExamFeeByDepartment()).isEmpty();
        assertThat(p.getLabFeeByType()).isEmpty();
    }
}
