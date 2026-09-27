package com.mediflow.inpatient.application.port.out;

import com.mediflow.inpatient.domain.model.AdmissionStatusHistory;
import com.mediflow.inpatient.domain.model.DepositTopupRequest;
import com.mediflow.inpatient.domain.model.FinancialClearance;
import com.mediflow.inpatient.domain.model.SettlementSnapshot;
import com.mediflow.inpatient.domain.model.enums.OverrideType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InpatientEventStorePort {
    void appendHistory(AdmissionStatusHistory history);
    void saveClearance(FinancialClearance clearance);
    void saveSettlement(SettlementSnapshot settlement);
    Optional<SettlementSnapshot> findLatestSettlement(UUID admissionId);
    void saveTopupRequest(DepositTopupRequest request);
    void saveOverride(UUID overrideId, UUID admissionId, OverrideType type, UUID approvedBy,
                      String approverRole, String reason, Instant approvedAt);
}
