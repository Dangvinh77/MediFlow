package com.mediflow.notification.application.service;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Kho mẫu nội dung thông báo cho sáu sự kiện được tiêu thụ
 * (backend-spec/07-notification.md §8). Tiếng Việt, không chứa PII ngoài tên.
 *
 * <p>Dùng bởi {@code NotificationEventConsumer} (Phần 5/5): consumer dựng tiêu đề + nội dung từ
 * đây rồi gói vào {@code NotificationTrigger}. Trả {@link Optional#empty()} khi routing key không
 * có mẫu — consumer bỏ qua, không tạo thông báo rỗng.
 */
@Component
public class NotificationTemplates {

    /** Kết quả dựng mẫu: tiêu đề + nội dung đã thay biến. */
    public record Rendered(String title, String content) {}

    /**
     * Dựng nội dung cho một sự kiện.
     *
     * @param routingKey routing key của event (vd {@code payment.completed})
     * @param vars       các biến thay vào chỗ {@code {ten_bien}} trong mẫu; thiếu biến nào thì
     *                   giữ nguyên chỗ đó (an toàn hơn là ném lỗi giữa luồng gửi)
     */
    public Optional<Rendered> render(String routingKey, Map<String, String> vars) {
        Map<String, String> v = vars == null ? Map.of() : vars;
        return switch (routingKey) {
            case "patient.created" -> Optional.of(new Rendered(
                    "Chào mừng đến với MediFlow",
                    fill("Xin chào {hoTen}, hồ sơ của bạn đã được tạo.", v)));
            case "appointment.created" -> Optional.of(new Rendered(
                    "Nhắc lịch khám",
                    fill("Bạn có lịch khám ngày {ngayHen} lúc {gioHen}.", v)));
            case "lab.result.created" -> Optional.of(new Rendered(
                    "Kết quả xét nghiệm đã có",
                    fill("Kết quả {loaiXn} của bạn đã sẵn sàng.", v)));
            case "prescription.filled" -> Optional.of(new Rendered(
                    "Thuốc đã sẵn sàng",
                    "Đơn thuốc của bạn đã được chuẩn bị xong."));
            case "payment.completed" -> Optional.of(new Rendered(
                    "Thanh toán thành công",
                    fill("Hóa đơn {maHoaDon} đã được thanh toán: {tongTien} VNĐ.", v)));
            case "payment.failed" -> Optional.of(new Rendered(
                    "Thanh toán không thành công",
                    fill("Hóa đơn {maHoaDon} gặp sự cố: {reason}.", v)));
            default -> Optional.empty();
        };
    }

    private static String fill(String template, Map<String, String> vars) {
        String out = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            out = out.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return out;
    }
}
