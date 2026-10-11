package com.mediflow.billing.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.billing.application.dto.command.AllocateAdmissionDepositCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.DepositAllocatedPayload;
import com.mediflow.billing.application.port.out.AdmissionDepositAllocationStorePort;
import com.mediflow.billing.application.port.out.AdmissionLedgerReadPort;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.domain.model.AccountStatus;
import com.mediflow.billing.domain.model.AdmissionLedgerSnapshot;
import com.mediflow.billing.domain.model.ChargeStatus;
import com.mediflow.billing.domain.model.PaymentClassification;
import com.mediflow.billing.domain.model.PaymentTransactionStatus;
import com.mediflow.billing.domain.model.PaymentTransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdmissionDepositAllocationServiceTest {
    private final AdmissionDepositAllocationStorePort store=mock(AdmissionDepositAllocationStorePort.class);
    private final AdmissionLedgerReadPort ledger=mock(AdmissionLedgerReadPort.class);
    private final LedgerEventPort events=mock(LedgerEventPort.class);
    private final Instant time=Instant.parse("2026-10-10T03:00:00Z");
    private static UUID id(int tail){return UUID.fromString("40000000-0000-4000-8000-"+String.format("%012d",tail));}
    private AllocateAdmissionDepositCommand command(){return new AllocateAdmissionDepositCommand(id(1),id(10),id(11),"fixture",
            Map.of(id(5),new BigDecimal("48.00"),id(6),new BigDecimal("32.00")));}
    private AdmissionLedgerSnapshot snapshot(){return new AdmissionLedgerSnapshot(id(1),id(2),id(3),id(4),"VND",AccountStatus.CHARGE_CLOSED,
            time.minusSeconds(1),time,List.of(
                new AdmissionLedgerSnapshot.ChargeLine(id(5),id(1),id(3),ChargeStatus.POSTED,new BigDecimal("48.00")),
                new AdmissionLedgerSnapshot.ChargeLine(id(6),id(1),id(3),ChargeStatus.POSTED,new BigDecimal("32.00"))),
            List.of(new AdmissionLedgerSnapshot.TransactionLine(id(10),id(1),null,PaymentTransactionType.PAYMENT,
                    PaymentClassification.ADMISSION_DEPOSIT,PaymentTransactionStatus.COMPLETED,new BigDecimal("100.00"),"VND",time.minusSeconds(60))),
            List.of(),List.of());}
    private AdmissionDepositAllocationService service(){var sequence=new java.util.ArrayDeque<>(List.of(id(21),id(22),id(23),id(24)));
        return new AdmissionDepositAllocationService(store,ledger,events,sequence::removeFirst);}

    @Test void actualServiceSerializesProducerOwnedAllocationFixtureWithoutRecognizingRevenue() throws Exception {
        when(store.lockAndFindReplay(any())).thenReturn(Optional.empty()); when(ledger.read(id(1))).thenReturn(Optional.of(snapshot()));
        var payload=service().allocate(command());
        var event=ArgumentCaptor.forClass(LedgerIntegrationEvent.class); verify(events).appendHeld(eq(id(1)),event.capture());
        var mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        try(var bytes=getClass().getResourceAsStream("/contracts/ledger-v1/deposit-allocated.json")){
            assertThat(bytes).isNotNull(); assertThat(mapper.readTree(mapper.writeValueAsBytes(event.getValue()))).isEqualTo(mapper.readTree(bytes));
        }
        assertThat(payload.finalSettlement()).isFalse(); verify(store).save(command(),payload);
    }
    @Test void storedReplayNeverReallocatesOrPublishesAnotherEvent() {
        var payload=new DepositAllocatedPayload(id(23),id(1),id(2),id(3),id(10),"VND",new BigDecimal("80.00"),List.of(),time,false);
        when(store.lockAndFindReplay(command())).thenReturn(Optional.of(payload));
        assertThat(service().allocate(command())).isSameAs(payload); verifyNoInteractions(ledger,events);
    }
    @Test void missingExactAccountDoesNotSaveOrPublish() {
        when(store.lockAndFindReplay(any())).thenReturn(Optional.empty()); when(ledger.read(id(1))).thenReturn(Optional.empty());
        assertThatThrownBy(()->service().allocate(command())).isInstanceOf(com.mediflow.common.exception.ResourceNotFoundException.class);
        verifyNoInteractions(events);
    }
    @Test void unavailableMedicalProofNeverReadsMoneyOrAllocates() {
        when(store.lockAndFindReplay(any())).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("unavailable proof")).when(store).lockMedicallyClosedAccount(id(1));
        assertThatThrownBy(()->service().allocate(command())).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(ledger,events);
    }
    @Test void saveFailureIsPropagatedBeforeOutboxAppend() {
        when(store.lockAndFindReplay(any())).thenReturn(Optional.empty()); when(ledger.read(id(1))).thenReturn(Optional.of(snapshot()));
        doThrow(new IllegalStateException("database failure")).when(store).save(any(),any());
        assertThatThrownBy(()->service().allocate(command())).isInstanceOf(IllegalStateException.class); verifyNoInteractions(events);
    }
}
