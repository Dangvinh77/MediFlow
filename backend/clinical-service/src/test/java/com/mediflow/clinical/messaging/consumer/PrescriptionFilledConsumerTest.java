package com.mediflow.clinical.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.clinical.application.dto.command.PrescriptionFilledCommand;
import com.mediflow.clinical.application.port.in.ReactToPrescriptionFilledUseCase;
import com.mediflow.clinical.messaging.consumer.payload.PrescriptionFilledPayload;

class PrescriptionFilledConsumerTest {

    private final ReactToPrescriptionFilledUseCase useCase = mock(ReactToPrescriptionFilledUseCase.class);
    private final PrescriptionFilledConsumer consumer = new PrescriptionFilledConsumer(useCase);

    @Test
    void consume_mapsProducerOwnedRecordAndPrescriptionIdentifiers() {
        UUID eventId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();

        consumer.consume(new PrescriptionFilledPayload(eventId, recordId, prescriptionId));

        verify(useCase).onPrescriptionFilled(
                new PrescriptionFilledCommand(eventId, recordId, prescriptionId));
    }

    @Test
    void consume_missingRecordId_rejectsBeforeUseCase() {
        PrescriptionFilledPayload payload = new PrescriptionFilledPayload(
                UUID.randomUUID(), null, UUID.randomUUID());

        assertThatThrownBy(() -> consumer.consume(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recordId");
        verifyNoInteractions(useCase);
    }
}
