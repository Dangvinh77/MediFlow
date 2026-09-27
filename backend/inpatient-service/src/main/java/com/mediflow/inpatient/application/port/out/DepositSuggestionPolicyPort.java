package com.mediflow.inpatient.application.port.out;

import com.mediflow.inpatient.application.dto.event.DepositSuggestion;
import com.mediflow.inpatient.domain.model.enums.AdmissionPriority;

import java.util.UUID;

public interface DepositSuggestionPolicyPort {
    DepositSuggestion suggest(AdmissionPriority priority, UUID departmentId);
}
