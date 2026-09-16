package com.mediflow.billing.infrastructure.messaging;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mediflow.billing.application.port.in.ReplayBillingOutboxUseCase;

/** Retains pending events indefinitely while purging and replaying only safe outbox rows. */
@Component
@ConditionalOnProperty(name = {
        "mediflow.billing.outbox.enabled",
        "mediflow.billing.outbox.maintenance-enabled"
}, havingValue = "true", matchIfMissing = true)
public class BillingOutboxMaintenance implements ReplayBillingOutboxUseCase {

    private final BillingOutboxClaimService claimService;
    private final Clock clock;
    private final Duration retention;

    public BillingOutboxMaintenance(BillingOutboxClaimService claimService,
                                    Clock clock,
                                    @Value("${mediflow.billing.outbox.retention:PT168H}") Duration retention) {
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("Billing outbox retention must be positive");
        }
        this.claimService = claimService;
        this.clock = clock;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${mediflow.billing.outbox.retention-sweep-ms:3600000}")
    public void purgePublished() {
        claimService.deletePublishedBefore(Instant.now(clock).minus(retention));
    }

    @Override
    public boolean replay(UUID eventId) {
        return claimService.replay(eventId);
    }
}
