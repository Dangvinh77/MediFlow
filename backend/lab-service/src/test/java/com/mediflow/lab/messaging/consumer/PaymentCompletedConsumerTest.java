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
    void consume_mapsAllExplicitLabTestIdentifiersInCanonicalOrder() {
        UUID eventId = UUID.randomUUID();
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        List<UUID> labTestIds = List.of(second, first);

        consumer.consume(new PaymentCompletedPayload(eventId, labTestIds, UUID.randomUUID()));

        verify(useCase).onPaymentCompleted(new PaymentCompletedCommand(eventId, List.of(first, second)));
    }

    @Test
    void consume_missingLabTestIds_rejectsBeforeUseCase() {
        PaymentCompletedPayload payload = new PaymentCompletedPayload(UUID.randomUUID(), null, UUID.randomUUID());

        assertThatThrownBy(() -> consumer.consume(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("labTestIds");
        verifyNoInteractions(useCase);
    }

    @Test
    void consume_emptyLabTestIds_rejectsReceiptWithoutMaterialEffect() {
        PaymentCompletedPayload payload = new PaymentCompletedPayload(UUID.randomUUID(), List.of(), UUID.randomUUID());

        assertThatThrownBy(() -> consumer.consume(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonempty");
        verifyNoInteractions(useCase);
    }
}
