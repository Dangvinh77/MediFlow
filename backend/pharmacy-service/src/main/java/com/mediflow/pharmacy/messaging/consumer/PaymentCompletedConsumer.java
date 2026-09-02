package com.mediflow.pharmacy.messaging.consumer;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.mediflow.pharmacy.application.dto.command.PaymentCompletedCommand;
import com.mediflow.pharmacy.application.port.in.ReactToPaymentUseCase;
import com.mediflow.pharmacy.messaging.consumer.payload.PaymentCompletedEvent;

import lombok.RequiredArgsConstructor;

/**
 * Driving adapter nhận event {@code payment.completed} từ billing-service.
 *
 * <p>Consumer chỉ chuyển payload AMQP thành {@link PaymentCompletedCommand} rồi gọi
 * {@link ReactToPaymentUseCase}; mọi quy tắc idempotency, xuất thuốc và bù trừ nằm trong
 * application service. Exception không được nuốt tại đây để lỗi hạ tầng có thể đi theo
 * chính sách retry/dead-letter của RabbitMQ.</p>
 */
@Component
@RequiredArgsConstructor
public class PaymentCompletedConsumer {

    private final ReactToPaymentUseCase reactToPaymentUseCase;

    /**
     * Nhận thông báo thanh toán và chuyển dữ liệu vào application layer.
     *
     * @param event payload chuẩn do billing-service publish
     */
    @RabbitListener(queues = "${mediflow.pharmacy.rabbit.queue:pharmacy.q}")
    public void consume(PaymentCompletedEvent event) {
        PaymentCompletedCommand command = new PaymentCompletedCommand(
                event.eventId(),
                event.occurredAt(),
                event.correlationId(),
                event.invoiceId(),
                event.patientId(),
                event.departmentId(),
                event.prescriptionId(),
                event.totalAmount(),
                event.paymentMethod()
        );

        reactToPaymentUseCase.onPaymentCompleted(command);
    }
}
