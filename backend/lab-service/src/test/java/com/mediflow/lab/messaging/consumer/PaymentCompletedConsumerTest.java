package com.mediflow.lab.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.lab.application.dto.command.PaymentCompletedCommand;
import com.mediflow.lab.application.port.in.ReactToPaymentUseCase;
import com.mediflow.lab.messaging.consumer.payload.PaymentCompletedPayload;

class PaymentCompletedConsumerTest {

    private final ReactToPaymentUseCase useCase = mock(ReactToPaymentUseCase.class);
    private final PaymentCompletedConsumer consumer = new PaymentCompletedConsumer(useCase);

    @Test
    void consume_mapsAllExplicitLabTestIdentifiers() {
        UUID eventId = UUID.randomUUID();
        List<UUID> labTestIds = List.of(UUID.randomUUID(), UUID.randomUUID());

        consumer.consume(new PaymentCompletedPayload(eventId, labTestIds));

        verify(useCase).onPaymentCompleted(new PaymentCompletedCommand(eventId, labTestIds));
    }

    @Test
    void consume_missingLabTestIds_rejectsBeforeUseCase() {
        PaymentCompletedPayload payload = new PaymentCompletedPayload(UUID.randomUUID(), null);

        assertThatThrownBy(() -> consumer.consume(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("labTestIds");
        verifyNoInteractions(useCase);
    }
}
