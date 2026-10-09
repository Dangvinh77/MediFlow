package com.mediflow.billing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.PaymentRefundedPayload;
import com.mediflow.billing.application.event.SurgeryCancelledEvent;
import com.mediflow.billing.application.event.SurgeryCaseCreatedEvent;
import com.mediflow.billing.application.event.SurgeryCaseCreatedEvent.PlannedItem;
import com.mediflow.billing.application.event.SurgeryCompletedEvent;
import com.mediflow.billing.application.event.SurgeryCompletedEvent.PerformedItem;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.application.port.out.PriceCatalogPort.PriceSnapshot;
import com.mediflow.billing.application.port.out.ProcessedEventPort;
import com.mediflow.billing.application.port.out.SurgeryChargeRepositoryPort;
import com.mediflow.billing.application.port.out.SurgeryChargeRepositoryPort.ChargeAllocation;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.AccountStatus;
import com.mediflow.billing.domain.model.BillingAccount;
import com.mediflow.billing.domain.model.CareEpisodeType;
import com.mediflow.billing.domain.model.Charge;
import com.mediflow.billing.domain.model.ChargeStatus;
import com.mediflow.billing.domain.model.PaymentTransaction;
import com.mediflow.billing.domain.model.PaymentTransactionType;

/**
 * Hợp đồng CONTRACT-SURGERY-BILLING-01: tạo charge dự kiến từ {@code surgery.case.created},
 * đối chiếu thực tế từ {@code surgery.completed} (khớp kế hoạch, số lượng khác kế hoạch, dòng
 * mới, mã giá lạ), hủy/hoàn tiền từ {@code surgery.cancelled}. Dùng đúng field name trong
 * backend/surgery-service/src/test/resources/contracts/surgery-outcomes-v1/*.json.
 */
class SurgeryChargeServiceTest {

    private final Instant now = Instant.parse("2026-10-07T01:00:00Z");
    private final UUID surgeryCaseId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID departmentId = UUID.randomUUID();
    private final UUID careEpisodeId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    private final ProcessedEventPort processedEvent = mock(ProcessedEventPort.class);
    private final SurgeryChargeRepositoryPort repository = mock(SurgeryChargeRepositoryPort.class);
    private final PriceCatalogPort priceCatalog = mock(PriceCatalogPort.class);
    private final LedgerEventPort events = mock(LedgerEventPort.class);

    private final SurgeryChargeService service =
            new SurgeryChargeService(processedEvent, repository, priceCatalog, events);
    private BillingAccount account;

    @BeforeEach
    void setup() {
        account = BillingAccount.restore(accountId, patientId, departmentId, CareEpisodeType.ADMISSION,
                careEpisodeId, AccountStatus.OPEN, "VND", 0, now, null, null, now, now);
        when(processedEvent.alreadyProcessed(any())).thenReturn(false);
    }

    // ---- surgery.case.created ----

    @Test
    void caseCreated_createsOneChargePerPlannedItem() {
        when(repository.findOrOpenAccount(patientId, departmentId, CareEpisodeType.ADMISSION, careEpisodeId,
                "VND", now)).thenReturn(account);
        when(priceCatalog.requireActive("PRICE", now)).thenReturn(new PriceSnapshot("Phẫu thuật", new BigDecimal("500000.00")));
        when(repository.findChargeBySource("SURGERY", surgeryCaseId, "PRICE")).thenReturn(Optional.empty());

        service.onSurgeryCaseCreated(caseCreated(List.of(new PlannedItem("ITEM", "PRICE", BigDecimal.ONE))));

        ArgumentCaptor<Charge> captor = ArgumentCaptor.forClass(Charge.class);
        verify(repository).saveCharge(captor.capture());
        Charge charge = captor.getValue();
        assertThat(charge.getAccountId()).isEqualTo(accountId);
        assertThat(charge.getSourceType()).isEqualTo("SURGERY");
        assertThat(charge.getSourceId()).isEqualTo(surgeryCaseId);
        assertThat(charge.getPriceCode()).isEqualTo("PRICE");
        assertThat(charge.getGrossAmount()).isEqualByComparingTo("500000.00");
        verify(processedEvent).markProcessed(any(), eq("surgery.case.created"));
    }

    @Test
    void caseCreated_alreadyProcessedEventId_isNoOp() {
        when(processedEvent.alreadyProcessed(any())).thenReturn(true);

        service.onSurgeryCaseCreated(caseCreated(List.of(new PlannedItem("ITEM", "PRICE", BigDecimal.ONE))));

        verifyNoInteractions(repository, priceCatalog);
    }

    @Test
    void caseCreated_redeliveredSamePlan_isIdempotentNoOp() {
        when(repository.findOrOpenAccount(any(), any(), any(), any(), any(), any())).thenReturn(account);
        when(priceCatalog.requireActive("PRICE", now)).thenReturn(new PriceSnapshot("Phẫu thuật", new BigDecimal("500000.00")));
        Charge existing = Charge.post(accountId, patientId, departmentId, "SURGERY", surgeryCaseId, "PRICE",
                "Phẫu thuật", BigDecimal.ONE, new BigDecimal("500000.00"), now);
        when(repository.findChargeBySource("SURGERY", surgeryCaseId, "PRICE")).thenReturn(Optional.of(existing));

        service.onSurgeryCaseCreated(caseCreated(List.of(new PlannedItem("ITEM", "PRICE", BigDecimal.ONE))));

        verify(repository, never()).saveCharge(any());
    }

    @Test
    void caseCreated_redeliveredWithChangedQuantity_throwsContractConflict() {
        when(repository.findOrOpenAccount(any(), any(), any(), any(), any(), any())).thenReturn(account);
        when(priceCatalog.requireActive("PRICE", now)).thenReturn(new PriceSnapshot("Phẫu thuật", new BigDecimal("500000.00")));
        Charge existing = Charge.post(accountId, patientId, departmentId, "SURGERY", surgeryCaseId, "PRICE",
                "Phẫu thuật", BigDecimal.ONE, new BigDecimal("500000.00"), now);
        when(repository.findChargeBySource("SURGERY", surgeryCaseId, "PRICE")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.onSurgeryCaseCreated(
                caseCreated(List.of(new PlannedItem("ITEM", "PRICE", new BigDecimal("2"))))))
                .isInstanceOf(BillingRuleException.class)
                .extracting(ex -> ((BillingRuleException) ex).getCode())
                .isEqualTo("BILLING_SURGERY_CHARGE_CONFLICT");
        verify(repository, never()).saveCharge(any());
    }

    // ---- surgery.completed ----

    @Test
    void completed_matchingPlan_reconcilesWithoutChangingAmount() {
        Charge planned = Charge.post(accountId, patientId, departmentId, "SURGERY", surgeryCaseId, "PRICE",
                "Phẫu thuật", BigDecimal.ONE, new BigDecimal("500000.00"), now);
        when(repository.findChargesBySource("SURGERY", surgeryCaseId)).thenReturn(List.of(planned));
        when(priceCatalog.requireActive("PRICE", now)).thenReturn(new PriceSnapshot("Phẫu thuật", new BigDecimal("500000.00")));
        UUID resultId = UUID.randomUUID();

        service.onSurgeryCompleted(completed(resultId, List.of(new PerformedItem(UUID.randomUUID(), "ITEM", "PRICE", BigDecimal.ONE))));

        ArgumentCaptor<Charge> captor = ArgumentCaptor.forClass(Charge.class);
        verify(repository).updateCharge(captor.capture());
        assertThat(captor.getValue().getGrossAmount()).isEqualByComparingTo("500000.00");
        assertThat(captor.getValue().getReconciledResultId()).isEqualTo(resultId);
        verify(repository, never()).saveCharge(any());
    }

    @Test
    void completed_quantityHigherThanPlanned_adjustsExistingChargeInPlace() {
        Charge planned = Charge.post(accountId, patientId, departmentId, "SURGERY", surgeryCaseId, "PRICE",
                "Phẫu thuật", BigDecimal.ONE, new BigDecimal("500000.00"), now);
        when(repository.findChargesBySource("SURGERY", surgeryCaseId)).thenReturn(List.of(planned));
        when(priceCatalog.requireActive("PRICE", now)).thenReturn(new PriceSnapshot("Phẫu thuật", new BigDecimal("500000.00")));
        UUID resultId = UUID.randomUUID();

        service.onSurgeryCompleted(completed(resultId, List.of(new PerformedItem(UUID.randomUUID(), "ITEM", "PRICE", new BigDecimal("2")))));

        ArgumentCaptor<Charge> captor = ArgumentCaptor.forClass(Charge.class);
        verify(repository).updateCharge(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("2");
        assertThat(captor.getValue().getGrossAmount()).isEqualByComparingTo("1000000.00");
    }

    @Test
    void completed_extraItemNotInPlan_createsNewReconciledCharge() {
        Charge planned = Charge.post(accountId, patientId, departmentId, "SURGERY", surgeryCaseId, "PRICE",
                "Phẫu thuật", BigDecimal.ONE, new BigDecimal("500000.00"), now);
        when(repository.findChargesBySource("SURGERY", surgeryCaseId)).thenReturn(List.of(planned));
        when(priceCatalog.requireActive("PRICE", now)).thenReturn(new PriceSnapshot("Phẫu thuật", new BigDecimal("500000.00")));
        when(priceCatalog.requireActive("EXTRA_PRICE", now)).thenReturn(new PriceSnapshot("Vật tư thêm", new BigDecimal("75000.00")));
        UUID resultId = UUID.randomUUID();

        service.onSurgeryCompleted(completed(resultId, List.of(
                new PerformedItem(UUID.randomUUID(), "ITEM", "PRICE", BigDecimal.ONE),
                new PerformedItem(UUID.randomUUID(), "EXTRA_ITEM", "EXTRA_PRICE", new BigDecimal("0.5")))));

        ArgumentCaptor<Charge> captor = ArgumentCaptor.forClass(Charge.class);
        verify(repository).saveCharge(captor.capture());
        Charge extra = captor.getValue();
        assertThat(extra.getPriceCode()).isEqualTo("EXTRA_PRICE");
        assertThat(extra.getAccountId()).isEqualTo(accountId);
        assertThat(extra.getGrossAmount()).isEqualByComparingTo("37500.00");
        assertThat(extra.getReconciledResultId()).isEqualTo(resultId);
    }

    @Test
    void completed_unknownPriceCode_propagatesContractError() {
        Charge planned = Charge.post(accountId, patientId, departmentId, "SURGERY", surgeryCaseId, "PRICE",
                "Phẫu thuật", BigDecimal.ONE, new BigDecimal("500000.00"), now);
        when(repository.findChargesBySource("SURGERY", surgeryCaseId)).thenReturn(List.of(planned));
        when(priceCatalog.requireActive("UNRECOGNIZED_PRICE", now))
                .thenThrow(new BillingRuleException("BILLING_PRICE_CODE_UNKNOWN", "unknown"));

        assertThatThrownBy(() -> service.onSurgeryCompleted(completed(UUID.randomUUID(),
                List.of(new PerformedItem(UUID.randomUUID(), "ITEM", "UNRECOGNIZED_PRICE", BigDecimal.ONE)))))
                .isInstanceOf(BillingRuleException.class)
                .extracting(ex -> ((BillingRuleException) ex).getCode())
                .isEqualTo("BILLING_PRICE_CODE_UNKNOWN");
        verify(repository, never()).saveCharge(any());
        verify(repository, never()).updateCharge(any());
    }

    @Test
    void completed_sameResultRedeliveredIdenticalData_isIdempotent() {
        UUID resultId = UUID.randomUUID();
        Charge reconciled = Charge.post(accountId, patientId, departmentId, "SURGERY", surgeryCaseId, "PRICE",
                "Phẫu thuật", BigDecimal.ONE, new BigDecimal("500000.00"), now);
        reconciled.reconcilePerformed(resultId, BigDecimal.ONE, new BigDecimal("500000.00"));
        when(repository.findChargesBySource("SURGERY", surgeryCaseId)).thenReturn(List.of(reconciled));
        when(priceCatalog.requireActive("PRICE", now)).thenReturn(new PriceSnapshot("Phẫu thuật", new BigDecimal("500000.00")));

        service.onSurgeryCompleted(completed(resultId, List.of(new PerformedItem(UUID.randomUUID(), "ITEM", "PRICE", BigDecimal.ONE))));

        verify(repository).updateCharge(any());
    }

    @Test
    void completed_differentResultChangingAlreadyReconciledCharge_throwsConflict() {
        UUID firstResult = UUID.randomUUID();
        Charge reconciled = Charge.post(accountId, patientId, departmentId, "SURGERY", surgeryCaseId, "PRICE",
                "Phẫu thuật", BigDecimal.ONE, new BigDecimal("500000.00"), now);
        reconciled.reconcilePerformed(firstResult, BigDecimal.ONE, new BigDecimal("500000.00"));
        when(repository.findChargesBySource("SURGERY", surgeryCaseId)).thenReturn(List.of(reconciled));
        when(priceCatalog.requireActive("PRICE", now)).thenReturn(new PriceSnapshot("Phẫu thuật", new BigDecimal("500000.00")));

        assertThatThrownBy(() -> service.onSurgeryCompleted(completed(UUID.randomUUID(),
                List.of(new PerformedItem(UUID.randomUUID(), "ITEM", "PRICE", new BigDecimal("3"))))))
                .isInstanceOf(BillingRuleException.class)
                .extracting(ex -> ((BillingRuleException) ex).getCode())
                .isEqualTo("BILLING_SURGERY_RECONCILIATION_CONFLICT");
    }

    // ---- surgery.cancelled ----
    @Test
    void completed_twoDistinctItemsSharingPrice_reconcilesOneCombinedCharge() {
        Charge planned = Charge.post(accountId, patientId, departmentId, "SURGERY", surgeryCaseId, "PRICE",
                "Synthetic", BigDecimal.ONE, new BigDecimal("100"), now);
        when(repository.findChargesBySource("SURGERY", surgeryCaseId)).thenReturn(List.of(planned));
        when(priceCatalog.requireActive("PRICE", now)).thenReturn(new PriceSnapshot("Synthetic", new BigDecimal("100")));
        service.onSurgeryCompleted(completed(UUID.randomUUID(), List.of(
                new PerformedItem(UUID.randomUUID(),"A","PRICE",new BigDecimal("1.0001")),
                new PerformedItem(UUID.randomUUID(),"B","PRICE",new BigDecimal("0.5")))));
        verify(repository).updateCharge(any());
        assertThat(planned.getQuantity()).isEqualByComparingTo("1.5001");
        assertThat(planned.getGrossAmount()).isEqualByComparingTo("150.01");
    }
    @Test
    void completed_exactResultReplay_doesNotRepriceWithChangedCatalog() {
        UUID result = UUID.randomUUID();
        Charge charge = Charge.post(accountId,patientId,departmentId,"SURGERY",surgeryCaseId,"PRICE","Synthetic",BigDecimal.ONE,new BigDecimal("100"),now);
        charge.reconcilePerformed(result,BigDecimal.ONE,new BigDecimal("100"));
        when(repository.findChargesBySource("SURGERY",surgeryCaseId)).thenReturn(List.of(charge));
        service.onSurgeryCompleted(completed(result,List.of(new PerformedItem(UUID.randomUUID(),"ITEM","PRICE",BigDecimal.ONE))));
        verifyNoInteractions(priceCatalog);
        assertThat(charge.getGrossAmount()).isEqualByComparingTo("100");
    }
    @Test
    void completed_duplicateItemCodes_rejectsBeforeChargeEffects() {
        Charge charge=Charge.post(accountId,patientId,departmentId,"SURGERY",surgeryCaseId,"PRICE","Synthetic",BigDecimal.ONE,new BigDecimal("100"),now);
        when(repository.findChargesBySource("SURGERY",surgeryCaseId)).thenReturn(List.of(charge));
        assertThatThrownBy(() -> service.onSurgeryCompleted(completed(UUID.randomUUID(),List.of(
                new PerformedItem(UUID.randomUUID(),"ITEM","PRICE",BigDecimal.ONE),
                new PerformedItem(UUID.randomUUID(),"ITEM","PRICE",BigDecimal.ONE))))).isInstanceOf(BillingRuleException.class);
        verify(repository,never()).updateCharge(any()); verifyNoInteractions(priceCatalog);
    }
    @Test
    void completed_foreignPatient_rejectsBeforePriceLookupOrMutation() {
        Charge charge=Charge.post(accountId,UUID.randomUUID(),departmentId,"SURGERY",surgeryCaseId,"PRICE","Synthetic",BigDecimal.ONE,new BigDecimal("100"),now);
        when(repository.findChargesBySource("SURGERY",surgeryCaseId)).thenReturn(List.of(charge));
        assertThatThrownBy(() -> service.onSurgeryCompleted(completed(UUID.randomUUID(),List.of(new PerformedItem(UUID.randomUUID(),"ITEM","PRICE",BigDecimal.ONE))))).isInstanceOf(BillingRuleException.class);
        verify(repository,never()).updateCharge(any()); verifyNoInteractions(priceCatalog);
    }
    @Test
    void completed_overPrecisionQuantity_rejectsWithoutStorageRounding() {
        Charge charge=Charge.post(accountId,patientId,departmentId,"SURGERY",surgeryCaseId,"PRICE","Synthetic",BigDecimal.ONE,new BigDecimal("100"),now);
        when(repository.findChargesBySource("SURGERY",surgeryCaseId)).thenReturn(List.of(charge));
        assertThatThrownBy(() -> service.onSurgeryCompleted(completed(UUID.randomUUID(),List.of(new PerformedItem(UUID.randomUUID(),"ITEM","PRICE",new BigDecimal("1.00001")))))).isInstanceOf(BillingRuleException.class);
        verify(repository,never()).updateCharge(any()); verifyNoInteractions(priceCatalog);
    }

    @Test
    void cancelled_unpaidCharge_isVoidedWithoutRefund() {
        Charge charge = Charge.post(accountId, patientId, departmentId, "SURGERY", surgeryCaseId, "PRICE",
                "Phẫu thuật", BigDecimal.ONE, new BigDecimal("500000.00"), now);
        when(repository.findChargesBySource("SURGERY", surgeryCaseId)).thenReturn(List.of(charge));
        when(repository.findCompletedAllocations(any())).thenReturn(List.of());

        service.onSurgeryCancelled(cancelled());

        ArgumentCaptor<Charge> captor = ArgumentCaptor.forClass(Charge.class);
        verify(repository).updateCharge(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ChargeStatus.VOIDED);
        verifyNoInteractions(events);
        verify(repository, never()).saveRefund(any(), any());
    }

    @Test
    void cancelled_paidCharge_rejectsWithoutFabricatingCompletedCashRefund() {
        Charge charge = Charge.post(accountId, patientId, departmentId, "SURGERY", surgeryCaseId, "PRICE",
                "Phẫu thuật", BigDecimal.ONE, new BigDecimal("500000.00"), now);
        when(repository.findChargesBySource("SURGERY", surgeryCaseId)).thenReturn(List.of(charge));
        UUID originalTransactionId = UUID.randomUUID();
        UUID paymentRequestId = UUID.randomUUID();
        when(repository.findCompletedAllocations(any())).thenReturn(List.of(
                new ChargeAllocation(originalTransactionId, new BigDecimal("500000.00"), paymentRequestId, "VND", "CASH")));
        when(repository.refundTransactionExists(any())).thenReturn(false);
        when(repository.findAccountById(accountId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.onSurgeryCancelled(cancelled())).isInstanceOf(BillingRuleException.class)
                .extracting(e -> ((BillingRuleException)e).getCode()).isEqualTo("BILLING_SURGERY_CANCELLATION_REQUIRES_ADJUSTMENT");
        verify(repository,never()).saveRefund(any(),any()); verify(repository,never()).updateCharge(any());
        verifyNoInteractions(events); verify(processedEvent,never()).markProcessed(any(),any());
    }

    @Test
    void cancelled_priorRefund_doesNotGuessACompletedRefundFromCancellation() {
        Charge charge = Charge.post(accountId, patientId, departmentId, "SURGERY", surgeryCaseId, "PRICE",
                "Phẫu thuật", BigDecimal.ONE, new BigDecimal("500000.00"), now);
        when(repository.findChargesBySource("SURGERY", surgeryCaseId)).thenReturn(List.of(charge));
        when(repository.findCompletedAllocations(any())).thenReturn(List.of(
                new ChargeAllocation(UUID.randomUUID(), new BigDecimal("500000.00"), UUID.randomUUID(), "VND", "CASH")));
        when(repository.refundTransactionExists(any())).thenReturn(true);

        assertThatThrownBy(() -> service.onSurgeryCancelled(cancelled())).isInstanceOf(BillingRuleException.class);

        verify(repository, never()).saveRefund(any(), any());
        verifyNoInteractions(events);
    }

    @Test void cancelled_missingCreation_rejectsWithoutTerminalProcessedMarker() {
        when(repository.findChargesBySource("SURGERY",surgeryCaseId)).thenReturn(List.of());
        assertThatThrownBy(() -> service.onSurgeryCancelled(cancelled())).isInstanceOf(BillingRuleException.class);
        verify(processedEvent,never()).markProcessed(any(),any()); verifyNoInteractions(events);
    }
    @Test void cancelled_performedCharge_rejectsWithoutVoidingEarnedCare() {
        var charge=Charge.post(accountId,patientId,departmentId,"SURGERY",surgeryCaseId,"PRICE","Synthetic",BigDecimal.ONE,new BigDecimal("100"),now);
        charge.reconcilePerformed(UUID.randomUUID(),BigDecimal.ONE,new BigDecimal("100"));
        when(repository.findChargesBySource("SURGERY",surgeryCaseId)).thenReturn(List.of(charge));
        assertThatThrownBy(() -> service.onSurgeryCancelled(cancelled())).isInstanceOf(BillingRuleException.class);
        verify(repository,never()).updateCharge(any()); verifyNoInteractions(events);
    }
    @Test void cancelled_unpaidPrefixThenPaidCharge_deniesBeforeAnyVoid() {
        var unpaid=Charge.post(accountId,patientId,departmentId,"SURGERY",surgeryCaseId,"A","Synthetic",BigDecimal.ONE,new BigDecimal("100"),now);
        var paid=Charge.restore(UUID.randomUUID(),accountId,patientId,departmentId,"SURGERY",surgeryCaseId,"B","Synthetic",BigDecimal.ONE,new BigDecimal("100"),new BigDecimal("100"),ChargeStatus.POSTED,null,null,now,now);
        when(repository.findChargesBySource("SURGERY",surgeryCaseId)).thenReturn(List.of(unpaid,paid));
        when(repository.findCompletedAllocations(null)).thenReturn(List.of());
        when(repository.findCompletedAllocations(paid.getChargeId())).thenReturn(List.of(new ChargeAllocation(UUID.randomUUID(),new BigDecimal("100"),UUID.randomUUID(),"VND","CASH")));
        assertThatThrownBy(() -> service.onSurgeryCancelled(cancelled())).isInstanceOf(BillingRuleException.class);
        assertThat(unpaid.isPosted()).isTrue(); verify(repository,never()).updateCharge(any()); verifyNoInteractions(events);
    }

    @Test
    void cancelled_alreadyProcessedEventId_isNoOp() {
        when(processedEvent.alreadyProcessed(any())).thenReturn(true);

        service.onSurgeryCancelled(cancelled());

        verifyNoInteractions(repository, events);
    }

    private Charge argThatVoided() {
        return org.mockito.ArgumentMatchers.argThat(c -> c.getStatus() == ChargeStatus.VOIDED);
    }

    private SurgeryCaseCreatedEvent caseCreated(List<PlannedItem> items) {
        var payload = new SurgeryCaseCreatedEvent.Payload(surgeryCaseId, UUID.randomUUID(), patientId, departmentId,
                "ADMISSION", careEpisodeId, careEpisodeId, UUID.randomUUID(), items, now);
        return new SurgeryCaseCreatedEvent(UUID.randomUUID(), now, "surgery-contract-admission", payload);
    }

    private SurgeryCompletedEvent completed(UUID resultId, List<PerformedItem> items) {
        var payload = new SurgeryCompletedEvent.Payload(surgeryCaseId, patientId, departmentId, careEpisodeId,
                UUID.randomUUID(), resultId, items, now, now, now);
        return new SurgeryCompletedEvent(UUID.randomUUID(), now, "surgery-contract-admission", payload);
    }

    private SurgeryCancelledEvent cancelled() {
        var payload = new SurgeryCancelledEvent.Payload(surgeryCaseId, careEpisodeId, UUID.randomUUID(),
                UUID.randomUUID(), "BEFORE_START", "Patient request", UUID.randomUUID(), now);
        return new SurgeryCancelledEvent(UUID.randomUUID(), now, "surgery-contract-admission", payload);
    }
}
