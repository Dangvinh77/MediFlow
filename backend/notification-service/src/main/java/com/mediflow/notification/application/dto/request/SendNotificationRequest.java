package com.mediflow.notification.application.dto.request;

import java.util.UUID;

import com.mediflow.notification.domain.model.NotificationChannel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request gửi thông báo trực tiếp qua API ({@code POST /api/v1/notifications/send}, role
 * ADMIN/SYSTEM) — backend-spec/07-notification.md §6.
 *
 * <p>Bean Validation ở đây chỉ là tuyến phòng thủ tại biên; quy tắc "địa chỉ phải hợp lệ theo
 * đúng kênh" (BR-N1/BR-N2) chạy trong {@code Notification.create}. {@code recipientAddress} có
 * thể để trống khi kênh là {@code IN_APP}.
 *
 * @param patientId        bệnh nhân nhận
 * @param title            tiêu đề (bắt buộc, tối đa 255 ký tự)
 * @param content          nội dung (bắt buộc)
 * @param channel          kênh gửi: {@code EMAIL | SMS | IN_APP}
 * @param recipientAddress email hoặc số điện thoại; bỏ trống với {@code IN_APP}
 */
public record SendNotificationRequest(
        @NotNull UUID patientId,
        @NotBlank @Size(max = 255) String title,
        @NotBlank String content,
        @NotNull NotificationChannel channel,
        @Size(max = 150) String recipientAddress
) {}
