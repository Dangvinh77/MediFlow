package com.mediflow.surgery.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.surgery.application.port.in.*;
import com.mediflow.surgery.infrastructure.messaging.SurgeryClearanceDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class SurgeryClearanceConsumerTest {
    private final ReactToSurgeryClearanceUseCase useCase = mock(ReactToSurgeryClearanceUseCase.class);
    private final SurgeryClearanceDecoder decoder = new SurgeryClearanceDecoder(new ObjectMapper());
    private final SurgeryClearanceConsumer consumer = new SurgeryClearanceConsumer(decoder,useCase,
            () -> Instant.parse("2026-10-05T08:01:00Z"));

    @ParameterizedTest @EnumSource(value = ReactToSurgeryClearanceUseCase.Outcome.class, names={"APPLIED","REPLAYED","DEFERRED"})
    void acknowledgementFollowsCommittedOrDurablyDeferredOutcome(ReactToSurgeryClearanceUseCase.Outcome outcome) throws Exception {
        when(useCase.receive(any())).thenReturn(outcome);
        consumer.receive(message(fixture()));
        verify(useCase).receive(any());
    }
    @ParameterizedTest @EnumSource(value = ReactToSurgeryClearanceUseCase.Outcome.class, names={"CONFLICT","QUARANTINED"})
    void permanentOutcomesAreDeadLetteredNotRequeued(ReactToSurgeryClearanceUseCase.Outcome outcome) throws Exception {
        when(useCase.receive(any())).thenReturn(outcome);
        assertThatThrownBy(() -> consumer.receive(message(fixture()))).isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
    @Test void malformedMessageNeverInvokesUseCase() {
        assertThatThrownBy(() -> consumer.receive(message("{}".getBytes())))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(useCase);
    }
    @Test void validOtherPurposeDoesNotCreateSurgeryInboxOrPermission() throws Exception {
        consumer.receive(message(Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/clearance-prescription.json"))));
        verifyNoInteractions(useCase);
    }
    @Test void databaseFailureIsRetryableNotAcknowledged() throws Exception {
        when(useCase.receive(any())).thenThrow(new org.springframework.dao.TransientDataAccessResourceException("test"));
        assertThatThrownBy(() -> consumer.receive(message(fixture())))
                .isInstanceOf(org.springframework.dao.TransientDataAccessResourceException.class);
    }
    @Test void missingApplicationOutcomeCannotAcknowledgeWithoutDurability() throws Exception {
        when(useCase.receive(any())).thenReturn(null);
        assertThatThrownBy(() -> consumer.receive(message(fixture()))).isInstanceOf(IllegalStateException.class);
    }
    @Test void freshWorkerReadsDurableBytesAndContinuesAfterOneFailure() throws Exception {
        var pending = mock(QueryPendingSurgeryClearancesUseCase.class);
        var command = decoder.decode("financial.clearance.granted",fixture(),Instant.parse("2026-10-05T08:01:00Z"));
        when(pending.due(20)).thenReturn(List.of(command.incoming(),command.incoming()));
        when(useCase.receive(any())).thenThrow(new org.springframework.dao.TransientDataAccessResourceException("test"))
                .thenReturn(ReactToSurgeryClearanceUseCase.Outcome.APPLIED);
        var retry = mock(RecordSurgeryClearanceRetryUseCase.class);
        new PendingSurgeryClearanceWorker(pending,useCase,decoder,retry).poll();
        verify(useCase,times(2)).receive(any());
        verify(retry).deferFailure(command.incoming());
    }
    private Message message(byte[] bytes) {
        var properties = new MessageProperties(); properties.setReceivedRoutingKey("financial.clearance.granted");
        return new Message(bytes,properties);
    }
    private byte[] fixture() throws Exception {
        return Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/clearance-surgery.json"));
    }
}
