package com.mediflow.patient.infrastructure.messaging.consumer;

import com.mediflow.patient.infrastructure.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listens to {@code payment.completed} from billing-service.
 *
 * <p>Log only — this service holds no payment state (docs/ai/services/patient.md). It is therefore
 * naturally idempotent: replaying the same event twice writes nothing twice, so no
 * {@code SU_KIEN_DA_XU_LY} ledger is needed here. Services that <em>do</em> change state on an
 * event (lab, pharmacy, billing, report) must dedupe on {@code eventId}.
 *
 * <p>Consumed as a {@code Map} rather than a typed record on purpose: this service does not own
 * the billing event's shape, and copying that record here would couple the two services to each
 * other's payload versions.
 */
@Component
public class PaymentCompletedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentCompletedConsumer.class);

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onPaymentCompleted(Map<String, Object> event) {
        log.info("Nhận payment.completed: invoiceId={}, patientId={}",
                event.get("invoiceId"), event.get("patientId"));
    }
}
