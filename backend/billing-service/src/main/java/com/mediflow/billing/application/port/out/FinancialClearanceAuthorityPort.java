package com.mediflow.billing.application.port.out;

import java.util.Optional;
import java.util.UUID;
import com.mediflow.billing.domain.model.FinancialClearanceAuthority;

public interface FinancialClearanceAuthorityPort {
    Optional<FinancialClearanceAuthority> find(UUID clearanceId);
}
