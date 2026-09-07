package com.mediflow.notification.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.notification.application.dto.response.NotificationDTO;
import com.mediflow.notification.domain.model.Notification;
import com.mediflow.notification.domain.model.NotificationChannel;
import com.mediflow.notification.domain.model.NotificationStatus;

/**
 * Điểm quan trọng cần khóa: {@code NotificationDTO} KHÔNG được lộ {@code recipientAddress}
 * (PII) và {@code retryCount} (nội bộ) — backend-spec/07-notification.md §6. Vì DTO không có
 * hai field đó, MapStruct tự bỏ; test này đảm bảo nếu ai thêm chúng vào DTO sau này thì có chỗ
 * nhắc lại chủ ý.
 */
class NotificationDtoMapperTest {

    private final NotificationDtoMapper mapper = new NotificationDtoMapperImpl();

    @Test
    void toDto_copiesClientFacingFields() {
        UUID id = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        Notification n = Notification.restore(id, patientId, "Thanh toán thành công",
                "Hóa đơn HD1 đã được thanh toán: 300000 VNĐ.",
                NotificationChannel.EMAIL, "benhnhan@example.com",
                NotificationStatus.SENT, null, 1,
                Instant.parse("2026-09-02T09:00:00Z"),
                Instant.parse("2026-09-02T09:00:05Z"));

        NotificationDTO dto = mapper.toDto(n);

        assertThat(dto.notificationId()).isEqualTo(id);
        assertThat(dto.patientId()).isEqualTo(patientId);
        assertThat(dto.title()).isEqualTo("Thanh toán thành công");
        assertThat(dto.channel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(dto.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(dto.sentAt()).isEqualTo(Instant.parse("2026-09-02T09:00:05Z"));
    }

    @Test
    void notificationDto_hasNoPiiOrInternalFields() {
        var componentNames = java.util.Arrays.stream(NotificationDTO.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();

        assertThat(componentNames)
                .doesNotContain("recipientAddress")
                .doesNotContain("retryCount");
    }
}
