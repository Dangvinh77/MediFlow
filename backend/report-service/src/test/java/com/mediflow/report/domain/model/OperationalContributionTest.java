package com.mediflow.report.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OperationalContributionTest {
    @Test
    void countFact_requiresExactOneNonNullDepartmentAndPositiveRevision() {
        assertThat(fact(OperationalContribution.Metric.COMPLETED_VISITS, BigDecimal.ONE, 1, UUID.randomUUID()).value())
                .isEqualTo(BigDecimal.ONE);
        assertThatThrownBy(() -> fact(OperationalContribution.Metric.COMPLETED_VISITS, BigDecimal.TEN, 1, UUID.randomUUID()))
                .hasMessageContaining("exactly one");
        assertThatThrownBy(() -> fact(OperationalContribution.Metric.COMPLETED_VISITS, BigDecimal.ONE, 0, UUID.randomUUID()))
                .hasMessageContaining("Incomplete");
        assertThatThrownBy(() -> fact(OperationalContribution.Metric.COMPLETED_VISITS, BigDecimal.ONE, 1, null))
                .hasMessageContaining("Incomplete");
    }

    @Test
    void quantitiesAndDuration_rejectFractionsNegativesAndStorageOverflow() {
        assertThat(fact(OperationalContribution.Metric.DISPENSED_UNITS, new BigDecimal("12.000"), 1, UUID.randomUUID()).value())
                .isEqualTo(new BigDecimal("12"));
        for (String invalid : new String[]{"0.5", "-1", "10000000000000000"}) {
            assertThatThrownBy(() -> fact(OperationalContribution.Metric.DISPENSED_UNITS,
                    new BigDecimal(invalid), 1, UUID.randomUUID())).isInstanceOf(RuntimeException.class);
        }
    }

    private OperationalContribution fact(OperationalContribution.Metric metric, BigDecimal value, int revision, UUID department) {
        return new OperationalContribution(UUID.randomUUID(), "MEDICAL_RECORD", UUID.randomUUID(), revision,
                metric, department, null, null, LocalDate.of(2026, 10, 1), value, null, Instant.now());
    }
}
