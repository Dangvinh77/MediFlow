package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.port.in.QueryExpiredSurgeryReadinessUseCase.Candidate;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryReadinessExpiryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SurgeryReadinessExpiryBoundaryTest {
    private final SurgeryReadinessExpiryPort store = mock(SurgeryReadinessExpiryPort.class);
    private final SurgeryClockPort clock = mock(SurgeryClockPort.class);
    private final Instant now = Instant.parse("2026-10-05T08:00:00Z");
    private final Candidate candidate = new Candidate(UUID.randomUUID(), UUID.randomUUID());

    @Test void query_boundedLimitUsesClockAndReturnsImmutableCandidates() {
        when(clock.now()).thenReturn(now);
        when(store.findDue(now, 20)).thenReturn(new ArrayList<>(List.of(candidate)));
        var candidates = new ExpiredSurgeryReadinessQueryService(store, clock).findDue(20);
        assertThat(candidates).containsExactly(candidate);
        assertThatThrownBy(() -> candidates.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void query_invalidLimitDoesNotReadClockOrStorage() {
        var query = new ExpiredSurgeryReadinessQueryService(store, clock);
        assertThatThrownBy(() -> query.findDue(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> query.findDue(101)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(store, clock);
    }

    @Test void retry_usesCurrentClockAndSafeCode() {
        when(clock.now()).thenReturn(now);
        new SurgeryReadinessExpiryRetryService(store, clock).defer(candidate, "LockTimeout");
        verify(store).deferIfStillDue(candidate, now, "LockTimeout");
    }

    @Test void retry_rejectsExceptionNarrativeBeforePorts() {
        var retry = new SurgeryReadinessExpiryRetryService(store, clock);
        assertThatThrownBy(() -> retry.defer(candidate, "Patient name or token: secret")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> retry.defer(null, "Failure")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> retry.defer(candidate, "A".repeat(65))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(store, clock);
    }

    @Test void candidate_missingIdentityIsRejected() {
        assertThatThrownBy(() -> new Candidate(null, UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Candidate(UUID.randomUUID(), null)).isInstanceOf(IllegalArgumentException.class);
    }
}
