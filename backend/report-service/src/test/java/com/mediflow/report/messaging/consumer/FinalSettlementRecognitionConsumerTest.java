package com.mediflow.report.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.mapper.BillingFinalSettlementRecognitionMapper;
import com.mediflow.report.application.port.in.ApplyFinalSettlementRecognitionUseCase;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;
import java.nio.file.*;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinalSettlementRecognitionConsumerTest {
    @Test void unchangedBillingBytesReachApplicationBoundary()throws Exception {
        var useCase=mock(ApplyFinalSettlementRecognitionUseCase.class);var wire=new CareFinanceEnvelopeDecoder(new ObjectMapper());
        var bytes=Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/settlement-recognition-completed.json"));
        new FinalSettlementRecognitionConsumer(wire,useCase).receive(message(bytes,"settlement.recognition.completed"));verify(useCase).apply(wire.decode("settlement.recognition.completed",bytes));
    }
    @Test void nonFinalRoutingCannotReachFinancialEffect()throws Exception {
        var wire=new CareFinanceEnvelopeDecoder(new ObjectMapper());var useCase=mock(ApplyFinalSettlementRecognitionUseCase.class);
        var bytes=Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/settlement-recognition-completed.json"));
        assertThatThrownBy(()->new FinalSettlementRecognitionConsumer(wire,useCase).receive(message(bytes,"settlement.reconciled"))).isInstanceOf(AmqpRejectAndDontRequeueException.class);verifyNoInteractions(useCase);
    }
    @Test void businessConflictIsRejectedButStorageFailureRemainsRetryable()throws Exception {
        var wire=new CareFinanceEnvelopeDecoder(new ObjectMapper());var useCase=mock(ApplyFinalSettlementRecognitionUseCase.class);
        var bytes=Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/settlement-recognition-completed.json"));
        doThrow(new com.mediflow.report.domain.exception.ReportRuleException("CONFLICT","conflict")).when(useCase).apply(any());
        var consumer=new FinalSettlementRecognitionConsumer(wire,useCase);
        assertThatThrownBy(()->consumer.receive(message(bytes,"settlement.recognition.completed"))).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("temporary outage")).when(useCase).apply(any());
        assertThatThrownBy(()->consumer.receive(message(bytes,"settlement.recognition.completed"))).isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
    }
    private static Message message(byte[] body,String routing){var properties=new MessageProperties();properties.setReceivedRoutingKey(routing);return new Message(body,properties);}
}
