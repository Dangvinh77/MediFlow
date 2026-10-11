package com.mediflow.pharmacy.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Unit coverage for operator replay and retention boundaries of the outbox. */
class PharmacyOutboxMaintenanceTest {

    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");

    private final PharmacyOutboxClaimService claimService =
            org.mockito.Mockito.mock(PharmacyOutboxClaimService.class);
    private final PharmacyOutboxMaintenance maintenance = new PharmacyOutboxMaintenance(
            claimService, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(7), true);

    /** Retention cleanup delegates a cutoff that is relative to the configured clock. */
    @Test
    void purgePublished_usesRetentionCutoff() {
        maintenance.purgePublished();

        verify(claimService).deletePublishedBefore(NOW.minus(Duration.ofDays(7)));
    }

    /** Replay keeps the original event identity and returns the repository outcome. */
    @Test
    void replay_delegatesEventIdAndOutcome() {
        UUID eventId = UUID.randomUUID();
        when(claimService.replay(eventId)).thenReturn(true);

        assertThat(maintenance.replay(eventId)).isTrue();

        verify(claimService).replay(eventId);
    }
    @Test void missingPurgeProperty_preservesPublishedHistoryWhileReplayRemainsAvailable() {
        new ApplicationContextRunner().withInitializer(context -> context.getBeanFactory().setConversionService(
                org.springframework.boot.convert.ApplicationConversionService.getSharedInstance()))
                .withBean(PharmacyOutboxClaimService.class, () -> claimService)
                .withBean(Clock.class, () -> Clock.fixed(NOW,ZoneOffset.UTC))
                .withUserConfiguration(PharmacyOutboxMaintenance.class).run(context -> {
                    assertThat(context).hasSingleBean(PharmacyOutboxMaintenance.class);
                    var worker=context.getBean(PharmacyOutboxMaintenance.class); worker.purgePublished();
                    verifyNoInteractions(claimService);
                    UUID eventId=UUID.randomUUID(); when(claimService.replay(eventId)).thenReturn(true);
                    assertThat(worker.replay(eventId)).isTrue(); verify(claimService).replay(eventId);
                });
    }
    @Test void explicitPurgeOptIn_keepsOriginalRetentionCutoff() {
        new ApplicationContextRunner().withInitializer(context -> context.getBeanFactory().setConversionService(
                org.springframework.boot.convert.ApplicationConversionService.getSharedInstance()))
                .withBean(PharmacyOutboxClaimService.class, () -> claimService)
                .withBean(Clock.class, () -> Clock.fixed(NOW,ZoneOffset.UTC))
                .withUserConfiguration(PharmacyOutboxMaintenance.class)
                .withPropertyValues("mediflow.pharmacy.outbox.purge-published-enabled=true").run(context -> {
                    context.getBean(PharmacyOutboxMaintenance.class).purgePublished();
                    verify(claimService).deletePublishedBefore(NOW.minus(Duration.ofDays(7)));
                });
    }
}
