package com.mediflow.report.application.service;

import java.util.ArrayList;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.report.application.dto.command.carefinance.ApplyOperationalContributionCommand;
import com.mediflow.report.application.port.in.ApplyOperationalContributionUseCase;
import com.mediflow.report.application.port.out.OperationalContributionStorePort;
import com.mediflow.report.application.mapper.OperationalProjectionPlanner;
import com.mediflow.report.domain.model.OperationalContribution;

/** Journal, dedupe, contribution and both scopes commit atomically; failures roll back every effect. */
@Service
public class OperationalContributionApplicationService implements ApplyOperationalContributionUseCase {
    private final OperationalContributionStorePort store;

    public OperationalContributionApplicationService(OperationalContributionStorePort store) {
        this.store = store;
    }

    @Override
    @Transactional
    public void apply(ApplyOperationalContributionCommand command) {
        store.recordEvent(command);
        var accepted = new ArrayList<OperationalContribution>();
        // Stable order also prevents contribution-key deadlocks for two redelivered multi-metric events.
        for (var fact : OperationalProjectionPlanner.ordered(command.contributions())) {
            if (store.claimDelivery(fact) && store.insertContribution(fact)) {
                accepted.add(fact);
            }
        }
        for (var delta : OperationalProjectionPlanner.scopes(accepted)) {
            store.incrementScope(delta.fact(), delta.departmentId());
        }
    }

}
