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
    private final boolean purgePublishedEnabled;

    /** Creates maintenance with a positive retention period. */
    public PharmacyOutboxMaintenance(
            PharmacyOutboxClaimService claimService,
            Clock clock,
            @Value("${mediflow.pharmacy.outbox.retention:PT168H}") Duration retention,
            @Value("${mediflow.pharmacy.outbox.purge-published-enabled:false}") boolean purgePublishedEnabled) {
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("Outbox retention must be positive");
        }
        this.claimService = claimService;
        this.clock = clock;
        this.retention = retention;
        this.purgePublishedEnabled = purgePublishedEnabled;
    }

    /** Disabled until archived source coverage and retention are explicitly reviewed. */
    @Scheduled(fixedDelayString = "${mediflow.pharmacy.outbox.retention-sweep-ms:3600000}")
    public void purgePublished() {
        if (!purgePublishedEnabled) return;
        claimService.deletePublishedBefore(Instant.now(clock).minus(retention));
    }

    /** Replays a failed event using its original event id and payload. */
    @Override
    public boolean replay(UUID eventId) {
        return claimService.replay(eventId);
    }
}
