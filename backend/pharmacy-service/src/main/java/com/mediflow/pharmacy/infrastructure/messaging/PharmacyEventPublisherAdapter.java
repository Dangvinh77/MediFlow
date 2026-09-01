package com.mediflow.pharmacy.infrastructure.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.mediflow.pharmacy.application.event.PrescriptionCreatedEvent;
import com.mediflow.pharmacy.application.event.PrescriptionDispenseFailedEvent;
import com.mediflow.pharmacy.application.event.PrescriptionFilledEvent;
import com.mediflow.pharmacy.application.event.StockLowEvent;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;

import lombok.RequiredArgsConstructor;

/**
 * Adapter cho {@link PharmacyEventPublisherPort} — publish event ra RabbitMQ.
 * {@code application} chỉ gọi port (không biết RabbitTemplate); mọi thứ AMQP nằm ở đây.
 *
 * <p>Exchange: {@code mediflow.events} (topic) — convention chung docs/ai/06-events-rabbitmq.md.
 * Routing key thống nhất theo tên event: {@code pharmacy.<tên>}. Consumer (billing) sẽ khai
 * binding của riêng nó, service này chỉ publish, không đụng queue.
 */
@Component
@RequiredArgsConstructor
public class PharmacyEventPublisherAdapter implements PharmacyEventPublisherPort {

    private static final String EXCHANGE = "mediflow.events";

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishPrescriptionCreated(PrescriptionCreatedEvent event) {
        publish("pharmacy.prescription.created", event);
    }

    @Override
    public void publishPrescriptionFilled(PrescriptionFilledEvent event) {
        publish("pharmacy.prescription.filled", event);
    }

    @Override
    public void publishPrescriptionDispenseFailed(PrescriptionDispenseFailedEvent event) {
        publish("pharmacy.prescription.dispense.failed", event);
    }

    @Override
    public void publishStockLow(StockLowEvent event) {
        publish("pharmacy.stock.low", event);
    }

    private void publish(String routingKey, Object payload) {
        rabbitTemplate.convertAndSend(EXCHANGE, routingKey, payload);
    }
}
