package com.mediflow.report.application.port.out;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.domain.model.MedicalDischargeFact;
import java.util.Optional;
import java.util.UUID;

public interface MedicalDischargeStorePort {
    void accept(DecodedCareFinanceEvent event, MedicalDischargeFact fact);
    Optional<DecodedCareFinanceEvent> find(UUID admissionId);
    void markApplied(UUID admissionId);
    boolean pending(UUID admissionId);
    void reject(UUID admissionId);
}
