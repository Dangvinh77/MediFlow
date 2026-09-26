package com.mediflow.pharmacy.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mediflow.pharmacy.domain.exception.DispenseRuleException;
import com.mediflow.pharmacy.domain.model.enums.DispenseActorType;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Domain rules for auditable manual and automated dispensing identities. */
class DispenseSlipTest {

    private static final Instant DISPENSED_AT = Instant.parse("2026-09-25T08:00:00Z");

    @Test
    void markDispensed_staffAndAccountKeepTheirIdentityKinds() {
        UUID staffId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        DispenseSlip staffSlip = DispenseSlip.createPending(UUID.randomUUID());
        DispenseSlip accountSlip = DispenseSlip.createPending(UUID.randomUUID());

        staffSlip.markDispensed(DispenseActor.staff(staffId), DISPENSED_AT);
        accountSlip.markDispensed(DispenseActor.account(accountId), DISPENSED_AT);

        assertThat(staffSlip.getStatus()).isEqualTo(DispenseStatus.DISPENSED);
        assertThat(staffSlip.getDispensedBy()).isEqualTo(staffId);
        assertThat(staffSlip.getDispensedActorType()).isEqualTo(DispenseActorType.STAFF);
        assertThat(accountSlip.getDispensedBy()).isEqualTo(accountId);
        assertThat(accountSlip.getDispensedActorType()).isEqualTo(DispenseActorType.ACCOUNT);
    }

    @Test
    void markDispensed_systemHasKindWithoutFakeIdentifier() {
        DispenseSlip slip = DispenseSlip.createPending(UUID.randomUUID());

        slip.markDispensed(DispenseActor.system(), DISPENSED_AT);

        assertThat(slip.getDispensedBy()).isNull();
        assertThat(slip.getDispensedActorType()).isEqualTo(DispenseActorType.SYSTEM);
    }

    @Test
    void systemActorRejectsAnIdentifier() {
        assertThatThrownBy(() -> new DispenseActor(DispenseActorType.SYSTEM, UUID.randomUUID()))
                .isInstanceOf(DispenseRuleException.class)
                .hasMessageContaining("không có user id");
        assertThatThrownBy(() -> new DispenseActor(DispenseActorType.LEGACY_UNKNOWN, null))
                .isInstanceOf(DispenseRuleException.class)
                .hasMessageContaining("không xác định không thể dùng");
    }
}
