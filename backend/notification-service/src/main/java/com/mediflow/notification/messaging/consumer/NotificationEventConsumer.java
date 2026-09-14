package com.mediflow.notification.messaging.consumer;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.notification.application.port.in.NotificationTrigger;
import com.mediflow.notification.application.port.in.SendNotificationUseCase;
import com.mediflow.notification.application.service.NotificationTemplates;
import com.mediflow.notification.infrastructure.config.RabbitConfig;
import com.mediflow.notification.messaging.consumer.payload.AppointmentCreatedPayload;
import com.mediflow.notification.messaging.consumer.payload.LabResultCreatedPayload;
import com.mediflow.notification.messaging.consumer.payload.PatientCreatedPayload;
import com.mediflow.notification.messaging.consumer.payload.PaymentCompletedPayload;
import com.mediflow.notification.messaging.consumer.payload.PaymentFailedPayload;
import com.mediflow.notification.messaging.consumer.payload.PrescriptionFilledPayload;

/**
 * Driving adapter duy nhất nhận cả 6 routing key notification subscribe, trên <b>một</b> queue
 * {@value RabbitConfig#QUEUE} (backend-spec/07-notification.md §12 "một class consumer với
 * switch"). Đọc {@link Message} thô + tự định tuyến theo routing key — không dùng 6
 * {@code @RabbitListener} riêng, vì nhiều listener trên cùng một queue vật lý sẽ cạnh tranh nhau
 * kiểu round-robin thay vì mỗi listener nhận đúng loại payload nó hiểu (cùng lý do đã ghi ở
 * {@code BillingEventConsumer}, billing-service).
 *
 * <p>Dựng nội dung từ {@link NotificationTemplates} (§8) rồi gói vào {@link NotificationTrigger}
 * — {@link SendNotificationUseCase} không bao giờ thấy kiểu AMQP. Email/phone chỉ có sẵn trên
 * {@code patient.created}; 5 event còn lại không mang địa chỉ liên hệ. V1 chấp nhận phương án đơn
 * giản ở §10: <b>không</b> dựng bảng chiếu email/phone cục bộ (sẽ cần thêm bảng + tiêu thụ thêm
 * {@code patient.updated}) — 5 event đó gửi {@code email = phone = null}, khiến
 * {@code NotificationApplicationService} (BR-N9) tự rơi về kênh {@code IN_APP}. Đây là lựa chọn
 * hợp lệ mà spec cho phép, không phải thiếu sót; nâng lên bảng chiếu là việc của một task sau.
 */
@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final SendNotificationUseCase sendNotificationUseCase;
    private final NotificationTemplates templates;
    private final ObjectMapper objectMapper;

    public NotificationEventConsumer(SendNotificationUseCase sendNotificationUseCase, NotificationTemplates templates,
                                     ObjectMapper objectMapper) {
        this.sendNotificationUseCase = sendNotificationUseCase;
        this.templates = templates;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onMessage(Message message) throws IOException {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        byte[] body = message.getBody();

        switch (routingKey) {
            case RabbitConfig.RK_PATIENT_CREATED -> handlePatientCreated(read(body, PatientCreatedPayload.class));
            case RabbitConfig.RK_APPOINTMENT_CREATED ->
                    handleAppointmentCreated(read(body, AppointmentCreatedPayload.class));
            case RabbitConfig.RK_LAB_RESULT_CREATED ->
                    handleLabResultCreated(read(body, LabResultCreatedPayload.class));
            case RabbitConfig.RK_PRESCRIPTION_FILLED ->
                    handlePrescriptionFilled(read(body, PrescriptionFilledPayload.class));
            case RabbitConfig.RK_PAYMENT_COMPLETED ->
                    handlePaymentCompleted(read(body, PaymentCompletedPayload.class));
            case RabbitConfig.RK_PAYMENT_FAILED -> handlePaymentFailed(read(body, PaymentFailedPayload.class));
            default -> log.warn("Bỏ qua routing key không xác định trên {}: {}", RabbitConfig.QUEUE, routingKey);
        }
    }

    private void handlePatientCreated(PatientCreatedPayload e) {
        dispatch(RabbitConfig.RK_PATIENT_CREATED, e.eventId(), e.patientId(), e.email(), e.sdt(),
                Map.of("hoTen", nullToEmpty(e.hoTen())));
    }

    private void handleAppointmentCreated(AppointmentCreatedPayload e) {
        dispatch(RabbitConfig.RK_APPOINTMENT_CREATED, e.eventId(), e.patientId(), null, null, Map.of(
                "ngayHen", e.appointmentDate() == null ? "" : e.appointmentDate().toString(),
                "gioHen", e.appointmentTime() == null ? "" : e.appointmentTime().toString()));
    }

    private void handleLabResultCreated(LabResultCreatedPayload e) {
        dispatch(RabbitConfig.RK_LAB_RESULT_CREATED, e.eventId(), e.patientId(), null, null,
                Map.of("loaiXn", nullToEmpty(e.labType())));
    }

    private void handlePrescriptionFilled(PrescriptionFilledPayload e) {
        dispatch(RabbitConfig.RK_PRESCRIPTION_FILLED, e.eventId(), e.patientId(), null, null, Map.of());
    }

    private void handlePaymentCompleted(PaymentCompletedPayload e) {
        dispatch(RabbitConfig.RK_PAYMENT_COMPLETED, e.eventId(), e.patientId(), null, null, Map.of(
                "maHoaDon", String.valueOf(e.invoiceId()),
                "tongTien", e.totalAmount() == null ? "" : e.totalAmount().toPlainString()));
    }

    private void handlePaymentFailed(PaymentFailedPayload e) {
        dispatch(RabbitConfig.RK_PAYMENT_FAILED, e.eventId(), e.patientId(), null, null, Map.of(
                "maHoaDon", String.valueOf(e.invoiceId()),
                "reason", nullToEmpty(e.reason())));
    }

    private void dispatch(String routingKey, UUID eventId, UUID patientId, String email, String phone,
                          Map<String, String> vars) {
        Optional<NotificationTemplates.Rendered> rendered = templates.render(routingKey, vars);
        if (rendered.isEmpty()) {
            log.warn("Không có mẫu nội dung cho routing key {}, bỏ qua event {}", routingKey, eventId);
            return;
        }
        NotificationTrigger trigger = new NotificationTrigger(
                eventId, routingKey, patientId, rendered.get().title(), rendered.get().content(), email, phone);
        sendNotificationUseCase.handleEvent(trigger);
    }

    private <T> T read(byte[] body, Class<T> type) throws IOException {
        return objectMapper.readValue(body, type);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
