package com.mediflow.billing.messaging.consumer;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.event.AppointmentStatusChangedEvent;
import com.mediflow.billing.application.event.LabResultCreatedEvent;
import com.mediflow.billing.application.event.MedicalRecordCreatedEvent;
import com.mediflow.billing.application.event.PrescriptionCreatedEvent;
import com.mediflow.billing.application.event.PrescriptionCancelledEvent;
import com.mediflow.billing.application.event.PrescriptionDispenseFailedEvent;
import com.mediflow.billing.application.event.PrescriptionExpiredEvent;
import com.mediflow.billing.application.event.PrescriptionFilledEvent;
import com.mediflow.billing.application.port.in.AccrueFeeUseCase;
import com.mediflow.billing.application.port.in.SagaCompensationUseCase;
import com.mediflow.billing.infrastructure.config.RabbitConfig;

/**
 * Driving adapter nhận toàn bộ 8 routing key mà billing subscribe, trên <b>một</b> queue
 * {@value RabbitConfig#QUEUE} (backend-spec/06-billing.md §12.4).
 *
 * <p><b>Vì sao một class, không phải 6 {@code @RabbitListener} riêng như §12.1 liệt kê:</b> nhiều
 * listener cùng khai báo trên một queue vật lý sẽ cạnh tranh nhau kiểu round-robin — mỗi message
 * chỉ tới đúng MỘT trong số các listener đó theo RabbitMQ, không phải "listener nào hiểu payload
 * thì nhận". Với 8 loại payload khác nhau trên cùng một queue, cách an toàn duy nhất là một
 * consumer đọc {@link Message} thô, tự đọc routing key rồi định tuyến — cùng tinh thần với
 * {@code NotificationEventConsumer} mà backend-spec/07-notification.md §12 yêu cầu ("một class
 * consumer với switch"). Đây là sai khác có chủ ý so với §12.1, ghi lại tại đây thay vì
 * {@code THELOC-INTEGRATION-FOLLOWUP.md} vì không đổi hợp đồng JSON, chỉ đổi cách đọc.
 *
 * <p>Consumer chỉ deserialize payload rồi gọi in-port; mọi quy tắc nghiệp vụ (khử trùng lặp,
 * BR-B7, BR-B8, saga) nằm ở {@link AccrueFeeUseCase}/{@link SagaCompensationUseCase}. Lỗi
 * deserialize hoặc lỗi từ application đều được ném tiếp — không nuốt — để message vào DLQ
 * (docs/ai/06-events-rabbitmq.md).
 */
@Component
public class BillingEventConsumer {

    private final AccrueFeeUseCase accrueFeeUseCase;
    private final SagaCompensationUseCase sagaCompensationUseCase;
    private final ObjectMapper objectMapper;

    public BillingEventConsumer(AccrueFeeUseCase accrueFeeUseCase, SagaCompensationUseCase sagaCompensationUseCase,
                                ObjectMapper objectMapper) {
        this.accrueFeeUseCase = accrueFeeUseCase;
        this.sagaCompensationUseCase = sagaCompensationUseCase;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onMessage(Message message) throws IOException {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        byte[] body = message.getBody();
        switch (routingKey) {
            case RabbitConfig.RK_MEDICAL_RECORD_CREATED ->
                    accrueFeeUseCase.onMedicalRecordCreated(read(body, MedicalRecordCreatedEvent.class));
            case RabbitConfig.RK_LAB_RESULT_CREATED ->
                    accrueFeeUseCase.onLabResultCreated(read(body, LabResultCreatedEvent.class));
            case RabbitConfig.RK_APPOINTMENT_STATUS_CHANGED ->
                    accrueFeeUseCase.onAppointmentStatusChanged(read(body, AppointmentStatusChangedEvent.class));
            case RabbitConfig.RK_PRESCRIPTION_CREATED ->
                    accrueFeeUseCase.onPrescriptionCreated(read(body, PrescriptionCreatedEvent.class));
            case RabbitConfig.RK_PRESCRIPTION_FILLED ->
                    sagaCompensationUseCase.onPrescriptionFilled(read(body, PrescriptionFilledEvent.class));
            case RabbitConfig.RK_PRESCRIPTION_DISPENSE_FAILED ->
                    sagaCompensationUseCase.onDispenseFailed(read(body, PrescriptionDispenseFailedEvent.class));
            case RabbitConfig.RK_PRESCRIPTION_CANCELLED ->
                    sagaCompensationUseCase.onPrescriptionCancelled(read(body, PrescriptionCancelledEvent.class));
            case RabbitConfig.RK_PRESCRIPTION_EXPIRED ->
                    sagaCompensationUseCase.onPrescriptionExpired(read(body, PrescriptionExpiredEvent.class));
            default -> throw new IllegalArgumentException(
                    "Routing key không được hỗ trợ trên " + RabbitConfig.QUEUE + ": " + routingKey);
        }
    }

    private <T> T read(byte[] body, Class<T> type) throws IOException {
        return objectMapper.readValue(body, type);
    }
}
