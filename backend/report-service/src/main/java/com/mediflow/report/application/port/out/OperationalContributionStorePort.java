package com.mediflow.report.application.port.out;

import java.util.UUID;

import com.mediflow.report.application.dto.command.carefinance.ApplyOperationalContributionCommand;
import com.mediflow.report.domain.model.OperationalContribution;

public interface OperationalContributionStorePort {
    /** Persist decoded envelope; eventId conflict with a different canonical payload must throw. */
    void recordEvent(ApplyOperationalContributionCommand command);

    /** Delivery+metric dedupe; compare the full fact before allowing redelivery. */
    boolean claimDelivery(OperationalContribution contribution);

    /** Business-operation dedupe, including deliveries with new event IDs. Conflicting facts must throw. */
    boolean insertContribution(OperationalContribution contribution);

    /** Null department represents hospital, not an invented sentinel department. */
    void incrementScope(OperationalContribution contribution, UUID departmentId);
}
