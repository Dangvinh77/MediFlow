package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.FinalizeAdmissionCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.FinalSettlementRecognitionPayload;
import com.mediflow.billing.domain.model.AdmissionFinalizationPlan;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinalAdmissionSettlementStorePort {
    Optional<FinalSettlementRecognitionPayload> lockAndFindReplay(FinalizeAdmissionCommand command);
    /** Locks/fences the exact account; loads only owned accepted medical/performed/allocation receipts. */
    Evidence lockAndReadEvidence(UUID accountId);
    void save(FinalizeAdmissionCommand command,FinalSettlementRecognitionPayload payload);

    record Evidence(Instant medicalApprovedAt,List<AdmissionFinalizationPlan.PerformedCharge> performedCharges,
            List<FinalSettlementRecognitionPayload.AllocationEvidence> allocations) {
        public Evidence {performedCharges=List.copyOf(performedCharges);allocations=List.copyOf(allocations);}
    }
}
