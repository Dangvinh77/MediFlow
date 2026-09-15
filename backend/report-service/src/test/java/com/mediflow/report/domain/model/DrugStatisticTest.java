package com.mediflow.report.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.report.domain.exception.ReportRuleException;

class DrugStatisticTest {

    private static final UUID DRUG_ID = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 8, 14);

    @Test
    void initialize_validDrug_startsWithZeroQuantity() {
        DrugStatistic statistic = DrugStatistic.initialize(DRUG_ID, " Paracetamol ", DATE, null);

        assertThat(statistic.getDrugId()).isEqualTo(DRUG_ID);
        assertThat(statistic.getDrugName()).isEqualTo("Paracetamol");
        assertThat(statistic.getDispensedQuantity()).isZero();
    }

    @Test
    void initialize_blankDrugName_throwsReportRule() {
        assertThatThrownBy(() -> DrugStatistic.initialize(DRUG_ID, "  ", DATE, null))
                .isInstanceOfSatisfying(ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_DRUG_NAME_REQUIRED"));
    }

    @Test
    void refreshDrugName_updatesLatestSnapshot() {
        DrugStatistic statistic = DrugStatistic.initialize(DRUG_ID, "Paracetamol", DATE, null);

        statistic.refreshDrugName("  Acetaminophen  ");

        assertThat(statistic.getDrugName()).isEqualTo("Acetaminophen");
    }

    @Test
    void refreshDrugName_tooLongName_throwsReportRule() {
        DrugStatistic statistic = DrugStatistic.initialize(DRUG_ID, "Paracetamol", DATE, null);

        assertThatThrownBy(() -> statistic.refreshDrugName("x".repeat(151)))
                .isInstanceOfSatisfying(ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_DRUG_NAME_TOO_LONG"));
    }

    @Test
    void incrementQuantity_positiveDelta_updatesQuantity() {
        DrugStatistic statistic = DrugStatistic.initialize(DRUG_ID, "Paracetamol", DATE, null);

        statistic.incrementQuantity(5);
        statistic.incrementQuantity(2);

        assertThat(statistic.getDispensedQuantity()).isEqualTo(7);
    }

    @Test
    void incrementQuantity_negativeDelta_throwsReportRule() {
        DrugStatistic statistic = DrugStatistic.initialize(DRUG_ID, "Paracetamol", DATE, null);

        assertThatThrownBy(() -> statistic.incrementQuantity(-1))
                .isInstanceOfSatisfying(ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_DRUG_DELTA_INVALID"));
    }
}
