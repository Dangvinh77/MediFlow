package com.mediflow.billing.application.event;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.billing.application.dto.request.*;
import com.mediflow.billing.application.mapper.LedgerPaymentMapper;
import com.mediflow.billing.application.port.out.*;
import com.mediflow.billing.application.service.*;
import com.mediflow.billing.domain.model.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real monetary commands pin matching fixture IDs; only generated delivery/transaction IDs normalize. */
class FinalRecognitionCashProducerFixtureTest {
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    @Test void actualPaymentAndRefundCommandsMatchFinalRecognitionOriginalBudget()throws Exception {
        var paid=fixture("payment-final-recognition-deposit");var returned=fixture("refund-final-recognition-deposit");
        Instant at=Instant.parse(paid.get("occurredAt").asText());
        var account=BillingAccount.restore(id(1),id(3),id(4),CareEpisodeType.ADMISSION,id(2),AccountStatus.OPEN,"VND",0,at,null,null,at,at);
        var request=PaymentRequest.restore(id(31),null,id(1),PaymentRequestPurpose.ADMISSION_DEPOSIT,PaymentRequestStatus.PENDING,new BigDecimal("120.00"),"VND",null,id(21),at,null);
        var payments=mock(LedgerPaymentRepositoryPort.class);var refunds=mock(LedgerRefundRepositoryPort.class);var events=mock(LedgerEventPort.class);
        when(payments.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(payments.lockRequest(id(31))).thenReturn(new LedgerPaymentRepositoryPort.PaymentContext(account,request,new ClearanceTarget(null,null,List.of(),null,id(2),null),BigDecimal.ZERO));
        when(payments.saveClearance(any())).thenAnswer(call->call.getArgument(0));
        var paymentMapper=Mappers.getMapper(LedgerPaymentMapper.class);
        new LedgerPaymentService(payments,events,paymentMapper,Clock.fixed(at,ZoneOffset.UTC)).complete(id(31),
                new CompleteLedgerPaymentRequest("final-deposit",new BigDecimal("120.00"),"VND",PaymentMethod.CASH,null),id(21),"final-recognition-cash-fixture");
        var captured=ArgumentCaptor.forClass(LedgerIntegrationEvent.class);verify(events,times(2)).appendForOutpatientRequest(eq(id(1)),eq(id(31)),captured.capture());
        compare(captured.getAllValues().getFirst(),paid,"transactionId");verify(payments,never()).allocate(any());clearInvocations(events);
        var original=PaymentTransaction.restore(id(11),id(1),id(31),PaymentTransactionType.PAYMENT,PaymentClassification.ADMISSION_DEPOSIT,
                PaymentTransactionStatus.COMPLETED,new BigDecimal("120.00"),"VND","CASH",null,"final-deposit",null,at,at);
        when(refunds.lockOriginal(id(11))).thenReturn(new LedgerRefundRepositoryPort.RefundContext(account,original,BigDecimal.ZERO));
        when(refunds.outpatientRefundEligibility(id(11))).thenReturn(
                new LedgerRefundRepositoryPort.OutpatientRefundEligibility(false, false, false, BigDecimal.ZERO));
        new LedgerRefundService(payments,refunds,events,paymentMapper,Clock.fixed(Instant.parse(returned.get("occurredAt").asText()),ZoneOffset.UTC))
                .refund(id(11),new RefundLedgerPaymentRequest("unused-20",new BigDecimal("20.00"),"Private audit",PaymentMethod.CASH),id(21),"final-recognition-cash-fixture");
        verify(events).appendHeld(eq(id(1)),captured.capture());compare(captured.getValue(),returned,"refundTransactionId");
    }
    private void compare(LedgerIntegrationEvent event,JsonNode expected,String generatedField) {
        var actual=(ObjectNode)mapper.valueToTree(event);actual.set("eventId",expected.get("eventId"));
        ((ObjectNode)actual.get("payload")).set(generatedField,expected.get("payload").get(generatedField));assertThat(actual).isEqualTo(expected);
    }
    private JsonNode fixture(String name)throws Exception{return mapper.readTree(Files.readAllBytes(Path.of("src/test/resources/contracts/ledger-v1/"+name+".json")));}
    private static UUID id(int value){return UUID.fromString("00000000-0000-4000-8000-"+String.format("%012d",value));}
}
