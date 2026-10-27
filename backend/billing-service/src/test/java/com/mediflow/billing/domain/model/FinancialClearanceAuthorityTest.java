package com.mediflow.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FinancialClearanceAuthorityTest {
    private static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");
    private static final UUID SOURCE = UUID.randomUUID();

    @Test void scheduledExamUsesRegistrationAsSource() {
        var authority = exam(null, SOURCE, "OUTPATIENT_VISIT", SOURCE, List.of());
        assertThat(authority.eligible()).isTrue();
        assertThat(authority.matchesSource("EXAM", SOURCE)).isTrue();
        assertThat(authority.sourceId()).isEqualTo(SOURCE);
        assertThat(authority.matchesSource("EXAM", UUID.randomUUID())).isFalse();
        assertThat(authority.matchesSource("LAB_TEST", SOURCE)).isFalse();
    }

    @Test void legacyExamStillUsesAppointmentAsSource() {
        var authority = exam(SOURCE, null, "OUTPATIENT_VISIT", SOURCE, List.of());
        assertThat(authority.matchesSource("EXAM", SOURCE)).isTrue();
        assertThat(authority.sourceId()).isEqualTo(SOURCE);
    }

    @Test void scheduledExamRejectsAmbiguousOrUnboundSources() {
        for (var authority : List.of(
                exam(SOURCE, SOURCE, "OUTPATIENT_VISIT", SOURCE, List.of()),
                exam(null, SOURCE, "OUTPATIENT_VISIT", UUID.randomUUID(), List.of()),
                exam(null, SOURCE, "ADMISSION", SOURCE, List.of()),
                exam(null, SOURCE, "OUTPATIENT_VISIT", SOURCE, List.of(UUID.randomUUID())),
                exam(null, null, "OUTPATIENT_VISIT", SOURCE, List.of()))) {
            assertThat(authority.matchesSource("EXAM", SOURCE)).isFalse();
            assertThat(authority.sourceId()).isNull();
        }
    }

    private static FinancialClearanceAuthority exam(UUID appointment, UUID record, String episodeType,
            UUID episode, List<UUID> labs) {
        return new FinancialClearanceAuthority(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "EXAM", episodeType, episode, null, null,
                NOW.minusSeconds(60), NOW.plusSeconds(60), null, true, NOW,
                appointment, record, labs, null);
    }
}
