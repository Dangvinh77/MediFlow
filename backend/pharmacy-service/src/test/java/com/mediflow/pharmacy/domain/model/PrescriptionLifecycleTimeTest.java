package com.mediflow.pharmacy.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;

class PrescriptionLifecycleTimeTest {
    @Test
    void allTerminalTransitions_keepExplicitExactBusinessTime() {
        Instant at = Instant.parse("2026-10-02T08:00:00.123456789Z");
        var filled = rx(); filled.markFulfilled(at);
        var failed = rx(); failed.markDispenseFailed(at);
        var expired = rx(); expired.markExpired(at);
        var cancelled = rx(); cancelled.cancel(UUID.randomUUID(), "Cancelled", at);
        for (var rx : List.of(filled, failed, expired, cancelled)) assertThat(rx.getLifecycleAt()).isEqualTo(at);
        var slip = DispenseSlip.createPending(UUID.randomUUID());
        slip.markExpired(at);
        assertThat(slip.getLifecycleAt()).isEqualTo(at);
    }

    @Test
    void invalidTimeDoesNotMutatePrescriptionOrSlipState() {
        var rx = rx();
        assertThatThrownBy(() -> rx.markFulfilled(null)).hasMessageContaining("bắt buộc");
        assertThat(rx.getStatus()).isEqualTo(PrescriptionStatus.ACTIVE);
        assertThat(rx.getLifecycleAt()).isNull();
        var slip = DispenseSlip.createPending(UUID.randomUUID());
        assertThatThrownBy(() -> slip.markExpired(null)).hasMessageContaining("bắt buộc");
        assertThat(slip.isPending()).isTrue();
        assertThat(slip.getLifecycleAt()).isNull();
    }

    private Prescription rx() {
        return Prescription.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 10, 2), List.of(PrescriptionLine.create(UUID.randomUUID(), 1, BigDecimal.ONE, null)));
    }
}
