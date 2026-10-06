package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.port.in.ReactToSurgeryClearanceUseCase.Command;
import com.mediflow.surgery.application.port.in.ReactToSurgeryClearanceUseCase.Outcome;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryFinancialClearanceRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryInboxPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.ReadinessSnapshot;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryDependencyRevision;
import com.mediflow.surgery.domain.model.SurgeryDependencyType;
import com.mediflow.surgery.domain.model.SurgeryFinancialClearance;
import com.mediflow.surgery.domain.model.SurgeryPriority;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import com.mediflow.surgery.domain.model.SurgeryTeamAssignment;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SurgeryFinancialClearanceServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-05T08:01:00Z");
    private static final String CORRELATION = "financial-authority-test";
    @Mock SurgeryCaseRepositoryPort cases;
    @Mock SurgeryFinancialClearanceRepositoryPort clearances;
    @Mock SurgeryInboxPort inbox;
    @Mock SurgeryScheduleRepositoryPort schedules;
    @Mock SurgeryResourceReservationPort reservations;
    @Mock SurgeryClockPort clock;
    @InjectMocks SurgeryFinancialClearanceService service;

    @ParameterizedTest
    @EnumSource(value = SurgeryStatus.class, names = {"READY", "SCHEDULED"})
    void newlyObservedGrantInvalidatesPreStartDecisionAndReleasesOnlyExactSchedule(SurgeryStatus status) {
        SurgeryCase surgeryCase = caseAt(status);
        Command command = commandFor(surgeryCase);
        prepare(surgeryCase, command, SurgeryFinancialClearanceRepositoryPort.SaveDecision.CREATED);
        var scheduleDependency = surgeryCase.getReadinessSnapshot().dependencyRevisions().stream()
                .filter(value -> value.dependencyType() == SurgeryDependencyType.SCHEDULE).findFirst().orElseThrow();
        var schedule = new SurgerySchedule(scheduleDependency.sourceId(), surgeryCase.getSurgeryCaseId(), 1,
                UUID.randomUUID(), NOW.plusSeconds(3600), NOW.plusSeconds(5400),
                List.of(new SurgeryTeamAssignment(UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON)));
        when(schedules.findByCaseId(surgeryCase.getSurgeryCaseId())).thenReturn(Optional.of(schedule));
        long previous = surgeryCase.getRevision();

        assertThat(service.receive(command)).isEqualTo(Outcome.APPLIED);

        assertThat(surgeryCase.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(surgeryCase.getReadinessSnapshot()).isNull();
        assertThat(surgeryCase.getRevision()).isEqualTo(previous + 1);
        assertThat(surgeryCase.getRevisionHistory().getLast().changeCode()).isEqualTo("FINANCIAL_CLEARANCE_CHANGED");
        assertThat(surgeryCase.getRevisionHistory().getLast().actor()).isEqualTo(SurgeryAuditActor.system("billing-service"));
        verify(cases).save(surgeryCase, previous);
        verify(inbox).markApplied(command.incoming().eventId(), NOW);
        if (status == SurgeryStatus.SCHEDULED) {
            verify(reservations).release(surgeryCase.getSurgeryCaseId(), schedule.scheduleId(), 1, NOW);
        } else verifyNoInteractions(reservations);
    }

    @Test void anotherEventForUnchangedGrantDoesNotInvalidateOrReleaseAgain() {
        SurgeryCase surgeryCase = caseAt(SurgeryStatus.SCHEDULED);
        Command command = commandFor(surgeryCase);
        prepare(surgeryCase, command, SurgeryFinancialClearanceRepositoryPort.SaveDecision.MATCHING);
        long previous = surgeryCase.getRevision();

        assertThat(service.receive(command)).isEqualTo(Outcome.APPLIED);

        assertThat(surgeryCase.getStatus()).isEqualTo(SurgeryStatus.SCHEDULED);
        assertThat(surgeryCase.getRevision()).isEqualTo(previous);
        verify(cases, never()).save(any(), anyLong());
        verifyNoInteractions(schedules, reservations);
    }

    @ParameterizedTest
    @EnumSource(value = SurgeryStatus.class, names = {"IN_PROGRESS", "COMPLETED", "CANCELLED"})
    void lateGrantIsFinancialEvidenceNotAClinicalRollback(SurgeryStatus status) {
        SurgeryCase surgeryCase = caseAt(status);
        Command command = commandFor(surgeryCase);
        prepare(surgeryCase, command, SurgeryFinancialClearanceRepositoryPort.SaveDecision.CREATED);
        long previous = surgeryCase.getRevision();

        assertThat(service.receive(command)).isEqualTo(Outcome.APPLIED);

        assertThat(surgeryCase.getStatus()).isEqualTo(status);
        assertThat(surgeryCase.getRevision()).isEqualTo(previous);
        verify(cases, never()).save(any(), anyLong());
        verifyNoInteractions(schedules, reservations);
    }

    private void prepare(SurgeryCase surgeryCase, Command command, SurgeryFinancialClearanceRepositoryPort.SaveDecision decision) {
        when(inbox.begin(command.incoming())).thenReturn(SurgeryInboxPort.Decision.NEW);
        when(cases.lockById(surgeryCase.getSurgeryCaseId())).thenReturn(Optional.of(surgeryCase));
        when(clearances.saveIfAbsentAndMatching(command.clearance())).thenReturn(decision);
        when(clock.now()).thenReturn(NOW);
    }

    private static Command commandFor(SurgeryCase surgeryCase) {
        UUID eventId = UUID.randomUUID();
        String hash = "a".repeat(64);
        var proof = new SurgeryFinancialClearance(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                surgeryCase.getPatientId(), surgeryCase.getSurgeryCaseId(), CareEpisodeType.ADMISSION,
                surgeryCase.getCareEpisode().episodeId(), surgeryCase.getCareEpisode().admissionId(),
                new BigDecimal("100.00"), "VND", "CASH", NOW.minusSeconds(120), NOW.plusSeconds(600), hash);
        var incoming = new SurgeryInboxPort.IncomingEvent(eventId, "financial.clearance.granted", 1,
                "billing-service", hash, "financial.clearance.granted:" + eventId,
                "{}".getBytes(StandardCharsets.UTF_8), NOW);
        return new Command(incoming, proof, CORRELATION);
    }

    private static SurgeryCase caseAt(SurgeryStatus status) {
        var actor = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        UUID admissionId = UUID.randomUUID();
        var surgeryCase = SurgeryCase.create(UUID.randomUUID(), UUID.randomUUID(),
                new CareEpisode(CareEpisodeType.ADMISSION, admissionId, admissionId, null),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "TEST_PROC", "Test indication",
                SurgeryPriority.ROUTINE, NOW.minusSeconds(180), actor, CORRELATION);
        if (status == SurgeryStatus.CANCELLED) {
            surgeryCase.cancel(actor, CORRELATION, NOW.minusSeconds(150), "Test-only cancellation");
            return surgeryCase;
        }
        surgeryCase.beginPreop(actor, CORRELATION, NOW.minusSeconds(150));
        var readiness = ReadinessSnapshot.evaluate(UUID.randomUUID(), surgeryCase.getSurgeryCaseId(),
                true, true, true, true, true, true, true, NOW.minusSeconds(30),
                Arrays.stream(SurgeryDependencyType.values()).map(type ->
                        new SurgeryDependencyRevision(type, UUID.randomUUID(), 1)).toList(), null);
        surgeryCase.markReady(readiness, actor, CORRELATION);
        if (status == SurgeryStatus.READY) return surgeryCase;
        surgeryCase.finalizeSchedule(actor, CORRELATION, NOW.minusSeconds(20));
        if (status == SurgeryStatus.SCHEDULED) return surgeryCase;
        surgeryCase.start(readiness, actor, CORRELATION, NOW.minusSeconds(10));
        if (status == SurgeryStatus.COMPLETED) surgeryCase.complete(actor, CORRELATION, NOW.minusSeconds(5));
        return surgeryCase;
    }
}
