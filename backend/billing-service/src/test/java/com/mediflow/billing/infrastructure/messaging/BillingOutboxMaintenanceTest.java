package com.mediflow.billing.infrastructure.messaging;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BillingOutboxMaintenanceTest {
    private static final Instant NOW=Instant.parse("2026-10-11T00:00:00Z");
    private final BillingOutboxClaimService claims=mock(BillingOutboxClaimService.class);
    private ApplicationContextRunner context() {
        return new ApplicationContextRunner().withInitializer(context -> context.getBeanFactory().setConversionService(
                org.springframework.boot.convert.ApplicationConversionService.getSharedInstance()))
                .withBean(BillingOutboxClaimService.class, () -> claims)
                .withBean(Clock.class, () -> Clock.fixed(NOW,ZoneOffset.UTC))
                .withUserConfiguration(BillingOutboxMaintenance.class);
    }
    @Test void missingPurgeProperty_keepsHistoricalPublishedFactsAndOperatorReplay() {
        context().run(context -> {
            assertThat(context).hasSingleBean(BillingOutboxMaintenance.class);
            var worker=context.getBean(BillingOutboxMaintenance.class); worker.purgePublished();
            verifyNoInteractions(claims);
            UUID eventId=UUID.randomUUID(); when(claims.replay(eventId)).thenReturn(true);
            assertThat(worker.replay(eventId)).isTrue(); verify(claims).replay(eventId);
        });
    }
    @Test void explicitDisabledPurge_doesNotDeleteEvenWithMaintenanceEnabled() {
        context().withPropertyValues("mediflow.billing.outbox.purge-published-enabled=false",
                "mediflow.billing.outbox.maintenance-enabled=true").run(context -> {
                    context.getBean(BillingOutboxMaintenance.class).purgePublished(); verifyNoInteractions(claims);
                });
    }
    @Test void explicitOptIn_usesConfiguredClockAndRetentionOnly() {
        context().withPropertyValues("mediflow.billing.outbox.purge-published-enabled=true",
                "mediflow.billing.outbox.retention=PT48H").run(context -> {
                    context.getBean(BillingOutboxMaintenance.class).purgePublished();
                    verify(claims).deletePublishedBefore(NOW.minus(Duration.ofDays(2)));
                });
    }
    @Test void maintenanceDisabled_stillRemovesMaintenanceBean() {
        context().withPropertyValues("mediflow.billing.outbox.maintenance-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(BillingOutboxMaintenance.class));
    }
    @Test void invalidRetention_stillRejectsUnsafeConfigurationEvenWhenPurgeDisabled() {
        for (Duration duration : new Duration[]{Duration.ZERO,Duration.ofDays(-1)}) {
            assertThatThrownBy(() -> new BillingOutboxMaintenance(claims,Clock.fixed(NOW,ZoneOffset.UTC),duration,false))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
