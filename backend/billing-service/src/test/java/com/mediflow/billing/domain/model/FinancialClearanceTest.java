package com.mediflow.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.billing.domain.exception.BillingRuleException;

class FinancialClearanceTest {

    @Test
    void grant_examWithAppointmentId_succeeds() {
        FinancialClearance clearance = FinancialClearance.grant(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), ClearancePurpose.EXAM,
                CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), UUID.randomUUID(), null, null,
                null, null, null, new BigDecimal("150000.00"), "VND", "CASH", false, null, Instant.now());

        assertThat(clearance.getPurpose()).isEqualTo(ClearancePurpose.EXAM);
        assertThat(clearance.isActive()).isTrue();
        assertThat(clearance.getLabTestIds()).isEmpty();
    }

    @Test
    void grant_examWithoutAppointmentOrRecord_throwsTargetMismatch() {
        assertThatThrownBy(() -> FinancialClearance.grant(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), ClearancePurpose.EXAM,
                CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, null, null, null, null,
                null, new BigDecimal("150000.00"), "VND", "CASH", false, null, Instant.now()))
                .isInstanceOf(BillingRuleException.class)
                .hasMessageContaining("Target không khớp");
    }

    @Test
    void grantClearance_targetMismatch_rejects_labTestPurposeWithEmptyList() {
        assertThatThrownBy(() -> FinancialClearance.grant(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), ClearancePurpose.LAB_TEST,
                CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, null, List.of(), null, null,
                null, new BigDecimal("80000.00"), "VND", "CASH", false, null, Instant.now()))
                .isInstanceOf(BillingRuleException.class);
    }

    @Test
    void grant_labTestWithIds_dedupesDefensiveCopy() {
        UUID labTestId = UUID.randomUUID();
        FinancialClearance clearance = FinancialClearance.grant(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), ClearancePurpose.LAB_TEST,
                CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, null, List.of(labTestId),
                null, null, null, new BigDecimal("80000.00"), "VND", "CASH", false, null, Instant.now());

        assertThat(clearance.getLabTestIds()).containsExactly(labTestId);
    }

    @Test
    void grant_negativeAmount_throwsBillingClearanceInvalidAmount() {
        assertThatThrownBy(() -> FinancialClearance.grant(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), ClearancePurpose.PRESCRIPTION,
                CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, null, null,
                UUID.randomUUID(), null, null, new BigDecimal("-1"), "VND", "CASH", false, null,
                Instant.now()))
                .isInstanceOf(BillingRuleException.class);
    }

    @Test
    void revoke_thenRevokeAgain_throwsBillingClearanceAlreadyRevoked() {
        FinancialClearance clearance = FinancialClearance.grant(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), ClearancePurpose.ADMISSION_DEPOSIT,
                CareEpisodeType.ADMISSION, UUID.randomUUID(), null, null, null, null,
                UUID.randomUUID(), null, new BigDecimal("500000.00"), "VND", "TRANSFER", false, null,
                Instant.now());

        clearance.revoke(Instant.now());

        assertThat(clearance.isActive()).isFalse();
        assertThatThrownBy(() -> clearance.revoke(Instant.now()))
                .isInstanceOf(BillingRuleException.class);
    }
}
