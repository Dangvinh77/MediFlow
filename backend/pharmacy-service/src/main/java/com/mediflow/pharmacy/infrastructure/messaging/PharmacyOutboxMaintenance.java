package com.mediflow.pharmacy.infrastructure.messaging;

import com.mediflow.pharmacy.application.port.in.ReplayPharmacyOutboxUseCase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Operational maintenance for replay and retention of durable pharmacy outbox rows. */
@Component
@ConditionalOnProperty(name = {
        "mediflow.pharmacy.outbox.enabled",
        "mediflow.pharmacy.outbox.maintenance-enabled" }, havingValue = "true", matchIfMissing = true)
public class PharmacyOutboxMaintenance implements ReplayPharmacyOutboxUseCase {

    private final PharmacyOutboxClaimService claimService;
    private final Clock clock;
    private final Duration retention;

    /** Creates maintenance with a positive retention period. */
    public PharmacyOutboxMaintenance(
            PharmacyOutboxClaimService claimService,
            Clock clock,
            @Value("${mediflow.pharmacy.outbox.retention:PT168H}") Duration retention) {
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("Outbox retention must be positive");
        }
        this.claimService = claimService;
        this.clock = clock;
        this.retention = retention;
    }

    /** Deletes only published rows older than the configured retention period. */
    @Scheduled(fixedDelayString = "${mediflow.pharmacy.outbox.retention-sweep-ms:3600000}")
    public void purgePublished() {
        claimService.deletePublishedBefore(Instant.now(clock).minus(retention));
    }

    /** Replays a failed event using its original event id and payload. */
    @Override
    public boolean replay(UUID eventId) {
        return claimService.replay(eventId);
    }
}
