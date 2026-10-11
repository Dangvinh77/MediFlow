package com.mediflow.billing.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.billing.application.dto.command.FinalizeAdmissionCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.FinalSettlementRecognitionPayload;
import com.mediflow.billing.application.port.out.AdmissionLedgerReadPort;
import com.mediflow.billing.application.port.out.FinalAdmissionSettlementStorePort;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.domain.model.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinalAdmissionSettlementServiceTest {
    private final FinalAdmissionSettlementStorePort store=mock(FinalAdmissionSettlementStorePort.class);
    private final AdmissionLedgerReadPort ledger=mock(AdmissionLedgerReadPort.class);
    private final LedgerEventPort events=mock(LedgerEventPort.class);
    private final FinalizeAdmissionCommand command=new FinalizeAdmissionCommand(id(1),id(21),"final-fixture");
    private final Instant medical=Instant.parse("2026-10-09T23:59:00Z"),completed=Instant.parse("2026-10-10T00:01:00Z");
    private AdmissionLedgerSnapshot snapshot;
    private FinalAdmissionSettlementStorePort.Evidence evidence;
    @BeforeEach void setup() {
        snapshot=new AdmissionLedgerSnapshot(id(1),id(2),id(3),id(4),"VND",AccountStatus.CHARGE_CLOSED,medical.plusSeconds(1),completed,
                List.of(new AdmissionLedgerSnapshot.ChargeLine(id(5),id(1),id(3),ChargeStatus.POSTED,money("60")),
                        new AdmissionLedgerSnapshot.ChargeLine(id(6),id(1),id(3),ChargeStatus.POSTED,money("40"))),
                List.of(new AdmissionLedgerSnapshot.TransactionLine(id(11),id(1),null,PaymentTransactionType.PAYMENT,PaymentClassification.ADMISSION_DEPOSIT,
                                PaymentTransactionStatus.COMPLETED,money("120"),"VND",medical.minusSeconds(20)),
                        new AdmissionLedgerSnapshot.TransactionLine(id(12),id(1),id(11),PaymentTransactionType.REFUND,PaymentClassification.ADMISSION_DEPOSIT,
                                PaymentTransactionStatus.COMPLETED,money("20"),"VND",medical.minusSeconds(10))),
                List.of(new AdmissionLedgerSnapshot.AllocationLine(id(11),id(5),money("60")),
                        new AdmissionLedgerSnapshot.AllocationLine(id(11),id(6),money("40"))),List.of());
        evidence=new FinalAdmissionSettlementStorePort.Evidence(medical,List.of(
                new AdmissionFinalizationPlan.PerformedCharge(id(5),id(9),"SURGERY",id(7),id(19),"a".repeat(64),medical.minusSeconds(5)),
                new AdmissionFinalizationPlan.PerformedCharge(id(6),id(10),"SURGERY",id(8),id(20),"b".repeat(64),medical.minusSeconds(5))),
                List.of(new FinalSettlementRecognitionPayload.AllocationEvidence(id(13),id(11),null,id(5),"PAYMENT","ADMISSION_DEPOSIT",money("60")),
                        new FinalSettlementRecognitionPayload.AllocationEvidence(id(14),id(11),null,id(6),"PAYMENT","ADMISSION_DEPOSIT",money("40"))));
        when(store.lockAndFindReplay(command)).thenReturn(Optional.empty());when(store.lockAndReadEvidence(id(1))).thenReturn(evidence);
        when(ledger.read(id(1))).thenReturn(Optional.of(snapshot));
    }
    private FinalAdmissionSettlementService service() {
        var ids=new ArrayDeque<>(List.of(id(15),id(16),id(17),id(18)));
        return new FinalAdmissionSettlementService(store,ledger,events,ids::removeFirst);
    }
    @Test void exactActualServiceSerializerPinsBothHeldFinalFactsAnd60_40GeneratingSplit()throws Exception {
        var result=service().finalizeAdmission(command);var captured=ArgumentCaptor.forClass(LedgerIntegrationEvent.class);
        verify(events,times(2)).appendHeld(eq(id(1)),captured.capture());
        var mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var expected=mapper.readTree(Files.readAllBytes(Path.of("src/test/resources/contracts/ledger-v1/settlement-recognition-completed.json")));
        assertThat(mapper.readTree(mapper.writeValueAsBytes(captured.getAllValues().get(1)))).isEqualTo(expected);
        assertThat(captured.getAllValues().getFirst().eventType()).isEqualTo("settlement.completed");
        assertThat(result.charges()).extracting(FinalSettlementRecognitionPayload.ChargeRecognition::departmentId).containsExactly(id(9),id(10));
        assertThat(result.depositReleases().getFirst().receiptDepartmentId()).isEqualTo(id(4));
        assertThat(result.finalSettlement()).isTrue();
        var order=inOrder(store,ledger,events);order.verify(store).lockAndFindReplay(command);order.verify(store).lockAndReadEvidence(id(1));
        order.verify(ledger).read(id(1));order.verify(store).save(command,result);order.verify(events,times(2)).appendHeld(eq(id(1)),any());
    }
    @Test void replayReturnsSameFinalOperationWithoutReadingMoneyOrAppendingAgain() {
        var original=service().finalizeAdmission(command);reset(ledger,events);
        when(store.lockAndFindReplay(command)).thenReturn(Optional.of(original));
        assertThat(service().finalizeAdmission(command)).isSameAs(original);verifyNoInteractions(ledger,events);
    }
    @Test void missingAccountCannotSaveOrEmitFinalAuthority() {
        when(ledger.read(id(1))).thenReturn(Optional.empty());assertThatThrownBy(()->service().finalizeAdmission(command)).isInstanceOf(com.mediflow.common.exception.ResourceNotFoundException.class);
        verify(store,never()).save(any(),any());verifyNoInteractions(events);
    }
    @Test void missingPerformedProofCannotEmitFinalAuthority() {
        when(store.lockAndReadEvidence(id(1))).thenReturn(new FinalAdmissionSettlementStorePort.Evidence(medical,List.of(),evidence.allocations()));
        assertThatThrownBy(()->service().finalizeAdmission(command)).hasMessage("BILLING_FINAL_PERFORMED_PROOF_INCOMPLETE");verifyNoInteractions(events);
    }
    @Test void inconsistentOriginalAllocationReceiptCannotBeEncodedAsRecognition() {
        var changed=new FinalSettlementRecognitionPayload.AllocationEvidence(id(13),id(11),id(12),id(5),"PAYMENT","ADMISSION_DEPOSIT",money("60"));
        when(store.lockAndReadEvidence(id(1))).thenReturn(new FinalAdmissionSettlementStorePort.Evidence(medical,evidence.performedCharges(),List.of(changed,evidence.allocations().get(1))));
        assertThatThrownBy(()->service().finalizeAdmission(command)).hasMessage("Exact original allocation evidence required");verifyNoInteractions(events);
    }
    @Test void failedFinalStoreDoesNotAppendOneSidedSettlementFact() {
        doThrow(new IllegalStateException("storage fault")).when(store).save(any(),any());
        assertThatThrownBy(()->service().finalizeAdmission(command)).isInstanceOf(IllegalStateException.class);verifyNoInteractions(events);
    }
    @Test void fullyReturnedVoidedChargeKeepsAccountCashButNoUnexplainedRecognizedAllocation() {
        var charges=new java.util.ArrayList<>(snapshot.charges());charges.add(new AdmissionLedgerSnapshot.ChargeLine(id(23),id(1),id(3),ChargeStatus.VOIDED,money("50")));
        var transactions=new java.util.ArrayList<>(snapshot.transactions());
        transactions.add(new AdmissionLedgerSnapshot.TransactionLine(id(24),id(1),null,PaymentTransactionType.PAYMENT,PaymentClassification.SERVICE_PAYMENT,
                PaymentTransactionStatus.COMPLETED,money("50"),"VND",medical.minusSeconds(20)));
        transactions.add(new AdmissionLedgerSnapshot.TransactionLine(id(25),id(1),id(24),PaymentTransactionType.REFUND,PaymentClassification.SERVICE_PAYMENT,
                PaymentTransactionStatus.COMPLETED,money("50"),"VND",medical.minusSeconds(10)));
        var allocations=new java.util.ArrayList<>(snapshot.allocations());allocations.add(new AdmissionLedgerSnapshot.AllocationLine(id(24),id(23),money("50")));
        allocations.add(new AdmissionLedgerSnapshot.AllocationLine(id(25),id(23),money("50")));
        snapshot=new AdmissionLedgerSnapshot(snapshot.accountId(),snapshot.admissionId(),snapshot.patientId(),snapshot.departmentId(),snapshot.currency(),
                snapshot.accountStatus(),snapshot.chargeClosedAt(),snapshot.observedAt(),charges,transactions,allocations,List.of());
        var originalEvidence=new java.util.ArrayList<>(evidence.allocations());
        originalEvidence.add(new FinalSettlementRecognitionPayload.AllocationEvidence(id(27),id(24),null,id(23),"PAYMENT","SERVICE_PAYMENT",money("50")));
        originalEvidence.add(new FinalSettlementRecognitionPayload.AllocationEvidence(id(28),id(25),id(24),id(23),"REFUND","SERVICE_PAYMENT",money("50")));
        when(store.lockAndReadEvidence(id(1))).thenReturn(new FinalAdmissionSettlementStorePort.Evidence(medical,evidence.performedCharges(),originalEvidence));
        when(ledger.read(id(1))).thenReturn(Optional.of(snapshot));var result=service().finalizeAdmission(command);
        assertThat(result.completedPayments()).isEqualByComparingTo("170");assertThat(result.completedRefunds()).isEqualByComparingTo("70");
        assertThat(result.grossAmount()).isEqualByComparingTo("100");assertThat(result.allocations()).hasSize(2);
        assertThat(result.charges()).hasSize(2);
    }
    private static UUID id(int value){return UUID.fromString("00000000-0000-4000-8000-"+String.format("%012d",value));}
    private static BigDecimal money(String value){return new BigDecimal(value).setScale(2);}
}
