package com.mediflow.billing.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.mediflow.billing.application.dto.command.ReconcileAdmissionCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.SettlementReconciledPayload;

public interface AdmissionReconciliationStorePort {
    Optional<SettlementReconciledPayload> lockAndFindReplay(ReconcileAdmissionCommand command);
    Revision lockAndFindRevision(UUID accountId);
    void save(ReconcileAdmissionCommand command, SettlementReconciledPayload payload);
    record Revision(int version, UUID previousId, Instant previousAt) {
        public Revision {
            if (version < 1 || ((version == 1) != (previousId == null))
                    || ((previousId == null) != (previousAt == null))) {
                throw new IllegalArgumentException("Consistent reconciliation revision required");
            }
        }
    }
}
