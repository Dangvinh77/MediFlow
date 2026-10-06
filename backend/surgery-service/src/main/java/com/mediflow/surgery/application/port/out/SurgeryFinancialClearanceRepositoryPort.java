package com.mediflow.surgery.application.port.out;

import java.util.Optional;
import java.util.UUID;
import com.mediflow.surgery.domain.model.SurgeryFinancialClearance;

public interface SurgeryFinancialClearanceRepositoryPort {
    SaveDecision saveIfAbsentAndMatching(SurgeryFinancialClearance clearance);
    Optional<SurgeryFinancialClearance> lockById(UUID clearanceId);
    Optional<SurgeryFinancialClearance> findById(UUID clearanceId);
    enum SaveDecision { CREATED, MATCHING, CONFLICT }
}
