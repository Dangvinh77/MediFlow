package com.mediflow.report.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.report.domain.exception.ReportRuleException;

class TopMedicineSummaryTest {

    @Test
    void create_validSummary_trimsDrugName() {
        TopMedicineSummary summary = new TopMedicineSummary(UUID.randomUUID(), " Aspirin ", 10);

        assertThat(summary.drugName()).isEqualTo("Aspirin");
        assertThat(summary.totalQuantity()).isEqualTo(10);
    }

    @Test
    void create_negativeQuantity_throwsReportRule() {
        assertThatThrownBy(() -> new TopMedicineSummary(UUID.randomUUID(), "Aspirin", -1))
                .isInstanceOfSatisfying(ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_DRUG_QUANTITY_INVALID"));
    }
}
