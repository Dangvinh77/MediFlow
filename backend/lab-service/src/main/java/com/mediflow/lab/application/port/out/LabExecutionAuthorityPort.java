package com.mediflow.lab.application.port.out;

import com.mediflow.lab.application.exception.LabUpstreamUnavailableException;
import com.mediflow.lab.domain.model.LabFinancialClearance;
import java.util.UUID;

/**
 * Billing's current financial authority and durable execution admission for an exact Lab test.
 * Implementations fail closed on an unavailable or malformed Billing response.
 */
public interface LabExecutionAuthorityPort {

    /** Atomically records the test's durable execution start with Billing. */
    void start(LabFinancialClearance clearance, UUID testId, String correlationId);

    /** Rechecks the exact test's current Billing eligibility before results are accepted. */
    void requireCurrent(LabFinancialClearance clearance, UUID testId, String correlationId);

    /** Compatibility construction remains fail-closed until an adapter is explicitly wired. */
    static LabExecutionAuthorityPort unavailable() {
        return new LabExecutionAuthorityPort() {
            @Override
            public void start(LabFinancialClearance clearance, UUID testId, String correlationId) {
                throw new LabUpstreamUnavailableException();
            }

            @Override
            public void requireCurrent(LabFinancialClearance clearance, UUID testId, String correlationId) {
                throw new LabUpstreamUnavailableException();
            }
        };
    }
}
