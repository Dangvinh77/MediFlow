package com.mediflow.report.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.report.domain.model.CashReceipt;

class CashProjectionPlannerTest {
    @Test
    void scopes_receipt_updatesHospitalThenExactAccountDepartmentWithoutMoneyConversion() {
        var receipt = receipt("VND", UUID.randomUUID());
        var scopes = CashProjectionPlanner.scopes(List.of(receipt));
        assertThat(scopes).hasSize(2);
        assertThat(scopes.getFirst().departmentId()).isNull();
        assertThat(scopes.getLast().departmentId()).isEqualTo(receipt.departmentId());
        assertThat(scopes).allSatisfy(scope -> assertThat(scope.receipt()).isEqualTo(receipt));
    }

    @Test
    void scopes_reverseInputOrder_hasSameStableOrderAndPreservesSeparateCurrencies() {
        var one = receipt("USD", UUID.randomUUID());
        var two = receipt("VND", UUID.randomUUID());
        assertThat(CashProjectionPlanner.scopes(List.of(one, two)))
                .containsExactlyElementsOf(CashProjectionPlanner.scopes(List.of(two, one)));
        assertThat(CashProjectionPlanner.scopes(List.of(one, two))).hasSize(4);
    }

    private static CashReceipt receipt(String currency, UUID department) {
        return new CashReceipt(UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                department, "OUTPATIENT_VISIT", UUID.randomUUID(), CashReceipt.Classification.SERVICE_PAYMENT,
                new BigDecimal("100.00"), currency, "CASH", Instant.parse("2026-10-05T08:00:00Z"),
                LocalDate.of(2026, 10, 5), "Asia/Bangkok");
    }
}
