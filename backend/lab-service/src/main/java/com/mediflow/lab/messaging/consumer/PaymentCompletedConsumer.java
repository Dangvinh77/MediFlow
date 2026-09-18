package com.mediflow.lab.messaging.consumer;

import org.springframework.stereotype.Component;

import com.mediflow.lab.application.dto.command.PaymentCompletedCommand;
import com.mediflow.lab.application.port.in.ReactToPaymentUseCase;
import com.mediflow.lab.messaging.consumer.payload.PaymentCompletedPayload;

/** Maps the Billing wire contract to the Lab application boundary. */
@Component
public class PaymentCompletedConsumer {

    private final ReactToPaymentUseCase useCase;

    public PaymentCompletedConsumer(ReactToPaymentUseCase useCase) {
        this.useCase = useCase;
    }

    public void consume(PaymentCompletedPayload payload) {
        validate(payload);
        useCase.onPaymentCompleted(new PaymentCompletedCommand(
                payload.eventId(), payload.labTestIds()));
    }

    private static void validate(PaymentCompletedPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payment.completed payload is required");
        }
        require(payload.eventId(), "eventId");
        require(payload.labTestIds(), "labTestIds");
        if (payload.labTestIds().stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("payment.completed labTestIds must not contain null");
        }
    }

    private static void require(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("payment.completed " + fieldName + " is required");
        }
    }
}
