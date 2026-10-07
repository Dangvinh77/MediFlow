package com.mediflow.inpatient.application.port.out;

import com.mediflow.inpatient.domain.model.SurgeryEventReceipt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SurgeryEventReceiptRepositoryPort {
    Optional<SurgeryEventReceipt> findByEventId(UUID eventId);
    Optional<SurgeryEventReceipt> findByBusinessOperation(String eventType, UUID operationId);
    List<SurgeryEventReceipt> findByCaseId(UUID surgeryCaseId);
    SurgeryEventReceipt save(SurgeryEventReceipt receipt);
}
