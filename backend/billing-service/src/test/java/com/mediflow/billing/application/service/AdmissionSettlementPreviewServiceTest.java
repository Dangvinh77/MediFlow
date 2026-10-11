package com.mediflow.billing.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.mediflow.billing.application.port.out.AdmissionLedgerReadPort;
import com.mediflow.billing.domain.model.AccountStatus;
import com.mediflow.billing.domain.model.AdmissionLedgerSnapshot;
import com.mediflow.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdmissionSettlementPreviewServiceTest {
    private final AdmissionLedgerReadPort ledger = mock(AdmissionLedgerReadPort.class);
    private final AdmissionSettlementPreviewService service = new AdmissionSettlementPreviewService(ledger);
    @Test void get_missingAccount_isNotZeroOrCompletedSettlement() {
        var id = UUID.randomUUID(); when(ledger.read(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ResourceNotFoundException.class);
        verify(ledger).read(id); verifyNoMoreInteractions(ledger);
    }
    @Test void get_nullAccount_rejectsBeforeRead() {
        assertThatThrownBy(() -> service.get(null)).isInstanceOf(IllegalArgumentException.class); verifyNoInteractions(ledger);
    }
    @Test void get_closedEmptyLedger_isExplicitDiagnosticNeverFinal() {
        var id = UUID.randomUUID(); var now = Instant.now();
        when(ledger.read(id)).thenReturn(Optional.of(new AdmissionLedgerSnapshot(id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "VND",
                AccountStatus.CHARGE_CLOSED, now.minusSeconds(1), now, List.of(), List.of(), List.of(), List.of())));
        var result = service.get(id);
        assertThat(result.snapshotOnly()).isTrue(); assertThat(result.finalSettlement()).isFalse(); assertThat(result.grossAmount()).isZero();
        verify(ledger).read(id); verifyNoMoreInteractions(ledger);
    }
    @Test void get_wrongAccountSnapshot_rejectsBeforeReturningMoney() {
        var id = UUID.randomUUID(); var now = Instant.now();
        when(ledger.read(id)).thenReturn(Optional.of(new AdmissionLedgerSnapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "VND",
                AccountStatus.CHARGE_CLOSED, now.minusSeconds(1), now, List.of(), List.of(), List.of(), List.of())));
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(com.mediflow.billing.domain.exception.BillingRuleException.class);
    }
}
