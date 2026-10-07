package com.mediflow.surgery.application.port.in;

import com.mediflow.surgery.domain.model.SurgeryPlannedItem;
import java.util.List;
import java.util.UUID;

/** Called in the case-creation transaction after authoritative referral validation; not a public issuer. */
public interface CaptureSurgeryCaseCreatedUseCase {
    void capture(UUID surgeryCaseId,List<SurgeryPlannedItem> plannedItems);
}
