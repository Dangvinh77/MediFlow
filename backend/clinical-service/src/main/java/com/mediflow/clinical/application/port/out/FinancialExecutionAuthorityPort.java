package com.mediflow.clinical.application.port.out;

import com.mediflow.clinical.application.dto.command.FinancialExecutionStartRequest;
import java.util.UUID;

/** Billing authority for admitting an exact care target under the same account lock as refunds. */
public interface FinancialExecutionAuthorityPort {

    /** Returns only after Billing has durably recorded this exact target as STARTED. */
    void start(UUID clearanceId, FinancialExecutionStartRequest request, String correlationId);
}
