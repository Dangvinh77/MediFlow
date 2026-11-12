package com.mediflow.lab.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LabCriticalNoticeTest {
    static final Instant AT = Instant.parse("2026-11-11T06:00:00Z");

    LabCriticalNotice notice() {
        return LabCriticalNotice.raise(UUID.randomUUID(), 1, "K", "Kali", "7.2", "mmol/L", LabFlag.CRITICAL_HIGH,
                UUID.randomUUID(), UUID.randomUUID(), AT);
    }

    @Test void onlyCriticalFlagsRaiseANotice() {
        assertThatThrownBy(() -> LabCriticalNotice.raise(UUID.randomUUID(), 1, "GLU", "Glucose", "7.1", "mmol/L",
                LabFlag.HIGH, UUID.randomUUID(), UUID.randomUUID(), AT)).isInstanceOf(IllegalArgumentException.class);
        assertThat(notice().getStatus()).isEqualTo(LabCriticalNoticeStatus.OPEN);
    }

    @Test void secondAcknowledgementKeepsTheFirst() {
        LabCriticalNotice notice = notice();
        UUID first = UUID.randomUUID();
        assertThat(notice.acknowledge(first, AT)).isTrue();
        assertThat(notice.acknowledge(UUID.randomUUID(), AT.plusSeconds(30))).isFalse();
        assertThat(notice.getStatus()).isEqualTo(LabCriticalNoticeStatus.ACKNOWLEDGED);
        assertThat(notice.getAcknowledgedBy()).isEqualTo(first);
        assertThat(notice.getAcknowledgedAt()).isEqualTo(AT);
    }
}
