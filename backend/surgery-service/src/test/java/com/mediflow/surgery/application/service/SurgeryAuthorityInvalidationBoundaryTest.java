package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.port.in.QuerySurgeryAuthorityInvalidationsUseCase.Candidate;
import com.mediflow.surgery.application.port.in.ReceiveSurgeryAuthorityChangeUseCase;
import com.mediflow.surgery.application.port.out.SurgeryAuthorityInvalidationPort;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryInboxPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.domain.model.SurgeryAuthorityChange;
import com.mediflow.surgery.domain.model.SurgeryAuthorityChange.ReferenceKind;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SurgeryAuthorityInvalidationBoundaryTest {
    private static final Instant NOW = Instant.parse("2026-10-05T02:01:00Z");
    private final SurgeryInboxPort inbox = mock(SurgeryInboxPort.class);
    private final SurgeryAuthorityInvalidationPort jobs = mock(SurgeryAuthorityInvalidationPort.class);
    private final SurgeryClockPort clock = () -> NOW;

    @ParameterizedTest @EnumSource(value = SurgeryInboxPort.Decision.class, names = {"ALREADY_APPLIED", "CONFLICT", "QUARANTINED"})
    void terminalInboxDecisionDoesNotCaptureNewWork(SurgeryInboxPort.Decision decision) {
        var command = command(); when(inbox.begin(command.incoming())).thenReturn(decision);
        assertThat(new SurgeryAuthorityChangeService(inbox, jobs, clock).receive(command)).isEqualTo(switch (decision) {
            case ALREADY_APPLIED -> ReceiveSurgeryAuthorityChangeUseCase.Outcome.REPLAYED;
            case CONFLICT -> ReceiveSurgeryAuthorityChangeUseCase.Outcome.CONFLICT;
            default -> ReceiveSurgeryAuthorityChangeUseCase.Outcome.QUARANTINED;
        });
        verifyNoInteractions(jobs);
    }
    @ParameterizedTest @EnumSource(SurgeryAuthorityInvalidationPort.Capture.class)
    void intakeMarksOnlyDurableAcceptedSourceOrQuarantinesConflict(SurgeryAuthorityInvalidationPort.Capture capture) {
        var command = command(); when(inbox.begin(command.incoming())).thenReturn(SurgeryInboxPort.Decision.NEW);
        when(jobs.capture(command.incoming().eventId(), command.change(), command.correlationId(), NOW)).thenReturn(capture);
        var outcome = new SurgeryAuthorityChangeService(inbox, jobs, clock).receive(command);
        if (capture == SurgeryAuthorityInvalidationPort.Capture.CONFLICT) {
            assertThat(outcome).isEqualTo(ReceiveSurgeryAuthorityChangeUseCase.Outcome.CONFLICT);
            verify(inbox).quarantine(command.incoming().eventId(), "AUTHORITY_REVISION_CONTENT_MISMATCH");
        } else {
            assertThat(outcome).isEqualTo(capture == SurgeryAuthorityInvalidationPort.Capture.CREATED
                    ? ReceiveSurgeryAuthorityChangeUseCase.Outcome.APPLIED : ReceiveSurgeryAuthorityChangeUseCase.Outcome.REPLAYED);
            verify(inbox).markApplied(command.incoming().eventId(), NOW);
        }
    }
    @ParameterizedTest @ValueSource(ints = {0, -1, 101})
    void queryRejectsUnboundedWork(int limit) {
        assertThatThrownBy(() -> new SurgeryAuthorityInvalidationQueryService(jobs, clock).findDue(limit)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jobs);
    }
    @Test void missingCaseCannotReleaseOrFinishWork() {
        var cases = mock(SurgeryCaseRepositoryPort.class); var schedules = mock(SurgeryScheduleRepositoryPort.class);
        var resources = mock(SurgeryResourceReservationPort.class); var candidate = candidate();
        when(cases.lockById(candidate.surgeryCaseId())).thenReturn(Optional.empty());
        assertThat(new SurgeryAuthorityInvalidationService(cases, schedules, resources, jobs, clock,
                mock(com.mediflow.surgery.application.port.out.SurgeryCareEventCapturePort.class)).apply(candidate)).isFalse();
        verifyNoInteractions(schedules, resources, jobs);
    }
    @Test void retryRejectsFreeTextAndMissingCaseDoesNotReopenWork() {
        var cases = mock(SurgeryCaseRepositoryPort.class); var candidate = candidate();
        var retry = new SurgeryAuthorityInvalidationRetryService(cases, jobs, clock);
        assertThatThrownBy(() -> retry.defer(candidate, "exception contains sensitive data")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cases, jobs);
        when(cases.lockById(candidate.surgeryCaseId())).thenReturn(Optional.empty());
        retry.defer(candidate, "DatabaseUnavailable"); verifyNoInteractions(jobs);
    }
    @Test void queryCopiesResultsAndUsesClock() {
        var candidate = candidate(); when(jobs.findDue(NOW, 20)).thenReturn(List.of(candidate));
        var result = new SurgeryAuthorityInvalidationQueryService(jobs, clock).findDue(20);
        assertThat(result).containsExactly(candidate); assertThatThrownBy(result::clear).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void missingDurableCaptureCannotBeMarkedApplied() {
        var command = command(); when(inbox.begin(command.incoming())).thenReturn(SurgeryInboxPort.Decision.NEW);
        assertThatThrownBy(() -> new SurgeryAuthorityChangeService(inbox, jobs, clock).receive(command)).isInstanceOf(NullPointerException.class);
        org.mockito.Mockito.verify(inbox, org.mockito.Mockito.never()).markApplied(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
    private Candidate candidate() { return new Candidate(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1); }
    private ReceiveSurgeryAuthorityChangeUseCase.Command command() {
        UUID eventId = UUID.randomUUID(); String type = ReceiveSurgeryAuthorityChangeUseCase.EVENT_TYPE;
        var incoming = new SurgeryInboxPort.IncomingEvent(eventId, type, 1, "organization-service", "a".repeat(64),
                type + ":" + eventId, new byte[]{1}, NOW);
        return new ReceiveSurgeryAuthorityChangeUseCase.Command(incoming,
                new SurgeryAuthorityChange(ReferenceKind.ROOM, UUID.randomUUID(), null, 1, NOW, UUID.randomUUID(), "Test decision", "b".repeat(64)),
                UUID.randomUUID().toString());
    }
}
