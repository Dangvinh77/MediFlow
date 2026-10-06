package com.mediflow.billing.application.port.in;

import java.util.UUID;
import com.mediflow.billing.application.dto.response.FinancialClearanceLookupDTO;

public interface LookupFinancialClearanceUseCase {
    FinancialClearanceLookupDTO lookup(UUID clearanceId);
}
