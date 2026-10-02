package com.mediflow.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.billing.domain.exception.BillingRuleException;

class BillingAccountTest {

    @Test
    void open_valid_startsOpenAtVersionZero() {
        BillingAccount account = open();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.OPEN);
        assertThat(account.getVersion()).isZero();
        assertThat(account.isOpenForCharges()).isTrue();
    }

    @Test
    void open_missingEpisodeId_throwsBillingAccountEpisodeIdRequired() {
        assertThatThrownBy(() -> BillingAccount.open(UUID.randomUUID(), UUID.randomUUID(),
                CareEpisodeType.OUTPATIENT_VISIT, null, "VND", Instant.now()))
                .isInstanceOf(BillingRuleException.class)
                .hasMessageContaining("careEpisodeId");
    }

    @Test
    void closeCharges_thenBeginSettlement_thenSettle_thenClose_followsLifecycle() {
        BillingAccount account = open();

        account.closeCharges(Instant.now());
        account.beginSettlement();
        account.settle();
        account.close(Instant.now());

        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(account.getChargeClosedAt()).isNotNull();
        assertThat(account.getClosedAt()).isNotNull();
    }

    @Test
    void settle_skippingChargeClosed_rejects() {
        BillingAccount account = open();

        assertThatThrownBy(account::settle)
                .isInstanceOf(BillingRuleException.class)
                .hasMessageContaining("Không thể chuyển trạng thái tài khoản");
    }

    @Test
    void closeCharges_afterClosed_rejects() {
        BillingAccount account = open();
        account.closeCharges(Instant.now());
        account.beginSettlement();
        account.settle();
        account.close(Instant.now());

        assertThatThrownBy(() -> account.closeCharges(Instant.now()))
                .isInstanceOf(BillingRuleException.class);
    }

    private BillingAccount open() {
        return BillingAccount.open(UUID.randomUUID(), UUID.randomUUID(),
                CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), "VND", Instant.now());
    }
}
