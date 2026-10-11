package com.mediflow.billing.application.service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.billing.application.dto.command.ReconcileAdmissionCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.port.out.AdmissionLedgerReadPort;
import com.mediflow.billing.application.port.out.AdmissionReconciliationStorePort;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.domain.model.AccountStatus;
import com.mediflow.billing.domain.model.AdmissionLedgerSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdmissionReconciliationServiceTest {
    static UUID id(int prefix, int suffix) { return UUID.fromString(prefix + "000000-0000-0000-0000-" + String.format("%012d", suffix)); }
    private final UUID account = id(91,1), actor = id(99,1);
    private final AdmissionLedgerReadPort ledger = mock(AdmissionLedgerReadPort.class);
    private final AdmissionReconciliationStorePort store = mock(AdmissionReconciliationStorePort.class);
    private final LedgerEventPort events = mock(LedgerEventPort.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private ReconcileAdmissionCommand command() { return new ReconcileAdmissionCommand(account, actor, "reconcile-key"); }
    private AdmissionLedgerSnapshot snapshot(int version) {
        var tx = new ArrayList<AdmissionLedgerSnapshot.TransactionLine>();
        var allocations = new ArrayList<AdmissionLedgerSnapshot.AllocationLine>();
        var at = Instant.parse("2026-10-10T10:0" + (version-1) + ":00.123456789Z"); var charge = id(95,1);
        tx.add(new AdmissionLedgerSnapshot.TransactionLine(id(96,1), account, null,"PAYMENT","SERVICE_PAYMENT","COMPLETED",new BigDecimal("60"),"VND", at.minusSeconds(1200)));
        allocations.add(new AdmissionLedgerSnapshot.AllocationLine(id(96,1),charge,new BigDecimal("60")));
        if (version >= 2) {
            tx.add(new AdmissionLedgerSnapshot.TransactionLine(id(96,2), account,null,"PAYMENT","SERVICE_PAYMENT","COMPLETED",new BigDecimal("40"),"VND",at.minusSeconds(600)));
            allocations.add(new AdmissionLedgerSnapshot.AllocationLine(id(96,2),charge,new BigDecimal("40")));
        }
        if (version >= 3) {
            tx.add(new AdmissionLedgerSnapshot.TransactionLine(id(96,3),account,id(96,1),"REFUND","SERVICE_PAYMENT","COMPLETED",new BigDecimal("15"),"VND",at.minusSeconds(300)));
            allocations.add(new AdmissionLedgerSnapshot.AllocationLine(id(96,3),charge,new BigDecimal("15")));
        }
        return new AdmissionLedgerSnapshot(account,id(92,1),id(93,1),id(94,1),"VND",AccountStatus.CHARGE_CLOSED,
                at.minusSeconds(3600),at,List.of(new AdmissionLedgerSnapshot.ChargeLine(charge,account,id(93,1),"POSTED",new BigDecimal("100"))),tx,allocations,
                version==4 ? List.of(new AdmissionLedgerSnapshot.InsuranceLine(id(97,1),account,id(92,1),null,"APPROVAL",new BigDecimal("20"))) : List.of());
    }
    @ParameterizedTest @ValueSource(ints = {1,2,3,4})
    void reconcile_actualProducerService_matchesSharedFixtureWithoutFinalDischarge(int revision) throws Exception {
        when(store.lockAndFindReplay(command())).thenReturn(Optional.empty());
        when(store.lockAndFindRevision(account)).thenReturn(new AdmissionReconciliationStorePort.Revision(revision,
                revision == 1 ? null : id(90,revision-1), revision == 1 ? null : snapshot(revision-1).observedAt()));
        when(ledger.read(account)).thenReturn(Optional.of(snapshot(revision)));
        var ids = new ArrayDeque<>(List.of(id(90,revision),id(98,revision)));
        var payload = new AdmissionReconciliationService(ledger,store,events,ids::removeFirst).reconcile(command());
        var event = org.mockito.ArgumentCaptor.forClass(LedgerIntegrationEvent.class);
        verify(events).appendHeld(eq(account),event.capture()); verify(store).save(command(),payload);
        assertThat(mapper.readTree(mapper.writeValueAsBytes(event.getValue())))
                .isEqualTo(mapper.readTree(Files.readAllBytes(Path.of("src/test/resources/contracts/ledger-v1/settlement-reconciled-" + revision + ".json"))));
        assertThat(payload.finalSettlement()).isFalse();
    }
    @Test void reconcile_sameKey_replaysImmutableResultWithoutReadingOrPublishingAgain() {
        var payload = new LedgerIntegrationEvent.SettlementReconciledPayload(id(90,1),1,null,account,id(92,1),id(93,1),id(94,1),"VND",
                new BigDecimal("100"),BigDecimal.ZERO,new BigDecimal("100"),new BigDecimal("60"),BigDecimal.ZERO,BigDecimal.ZERO,
                new BigDecimal("60"),new BigDecimal("40"),"ADDITIONAL_PAYMENT_REQUIRED",snapshot(1).observedAt(),false);
        when(store.lockAndFindReplay(command())).thenReturn(Optional.of(payload));
        assertThat(new AdmissionReconciliationService(ledger,store,events).reconcile(command())).isSameAs(payload);
        verifyNoInteractions(ledger,events); verify(store,never()).lockAndFindRevision(any());
    }
    @ParameterizedTest @ValueSource(strings = {"OPEN","SETTLED","CLOSED"})
    void reconcile_invalidAccountState_noSnapshotOrOutbox(String state) {
        var s = snapshot(1);
        when(store.lockAndFindReplay(command())).thenReturn(Optional.empty());
        when(store.lockAndFindRevision(account)).thenReturn(new AdmissionReconciliationStorePort.Revision(1,null,null));
        when(ledger.read(account)).thenReturn(Optional.of(new AdmissionLedgerSnapshot(s.accountId(),s.admissionId(),s.patientId(),s.departmentId(),
                s.currency(),AccountStatus.valueOf(state),s.chargeClosedAt(),s.observedAt(),s.charges(),s.transactions(),s.allocations(),s.insurance())));
        assertThatThrownBy(() -> new AdmissionReconciliationService(ledger,store,events).reconcile(command())).isInstanceOf(com.mediflow.billing.domain.exception.BillingRuleException.class);
        verify(store,never()).save(any(),any()); verifyNoInteractions(events);
    }
    @ParameterizedTest @ValueSource(strings = {"", "space key", "slash/key", "\n"})
    void command_invalidIdempotencyKey_rejects(String key) {
        assertThatThrownBy(() -> new ReconcileAdmissionCommand(account,actor,key)).isInstanceOf(com.mediflow.billing.domain.exception.BillingRuleException.class);
    }
}
