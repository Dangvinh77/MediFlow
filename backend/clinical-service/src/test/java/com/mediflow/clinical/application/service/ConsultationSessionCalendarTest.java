package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.clinical.application.dto.authority.CurrentDoctorAuthoritySnapshot;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.port.out.ConsultationSessionRepositoryPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.ForbiddenOperationException;

class ConsultationSessionCalendarTest {

    private static final Instant NOW = Instant.parse("2026-10-26T03:00:00Z");
    private static final UUID ACCOUNT_ID = id(1);
    private static final UUID DOCTOR_ID = id(2);
    private static final UUID DEPARTMENT_ID = id(3);

    private final ConsultationSessionRepositoryPort sessions = mock(ConsultationSessionRepositoryPort.class);
    private final SchedulingAuthorityPort authority = mock(SchedulingAuthorityPort.class);
    private final CurrentClinicalActorPort actors = mock(CurrentClinicalActorPort.class);
    private final CorrelationIdProvider correlations = mock(CorrelationIdProvider.class);
    private final IdempotentCommandPort idempotency = mock(IdempotentCommandPort.class);
    private final ConsultationSessionApplicationService service = new ConsultationSessionApplicationService(
            sessions, authority, actors, correlations, idempotency, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void weekCalendarUsesVerifiedDoctorAndVietnamMondayThroughSundayEvenWhenEmpty() {
        when(actors.current()).thenReturn(new ActorIdentity(ACCOUNT_ID, DOCTOR_ID, null, "DOCTOR"));
        when(correlations.currentOrCreate()).thenReturn(id(9));
        when(authority.currentDoctorAuthority(ACCOUNT_ID, id(9).toString())).thenReturn(authoritySnapshot());

        var calendar = service.mine(LocalDate.of(2026, 10, 26), null);

        assertThat(calendar.doctorId()).isEqualTo(DOCTOR_ID);
        assertThat(calendar.fromDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(calendar.toDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(calendar.timezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(calendar.sessions()).isEmpty();
        verify(sessions).findByDoctor(DOCTOR_ID, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1));
    }

    @Test
    void dailyCalendarIncludesOnlyOwnDoctorSessions() {
        when(actors.current()).thenReturn(new ActorIdentity(ACCOUNT_ID, DOCTOR_ID, null, "DOCTOR"));
        when(correlations.currentOrCreate()).thenReturn(id(9));
        when(authority.currentDoctorAuthority(ACCOUNT_ID, id(9).toString())).thenReturn(authoritySnapshot());
        ConsultationSession own = ConsultationSession.open(id(4), DOCTOR_ID, DEPARTMENT_ID, id(5),
                LocalDate.of(2026, 10, 27), LocalTime.of(9, 0), LocalTime.of(10, 0), List.of(), 4);
        when(sessions.findByDoctor(DOCTOR_ID, LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 27)))
                .thenReturn(List.of(own));

        var calendar = service.mine(null, LocalDate.of(2026, 10, 27));

        assertThat(calendar.sessions()).singleElement()
                .satisfies(session -> assertThat(session.sessionId()).isEqualTo(own.sessionId()));
    }

    @Test
    void missingOrInactiveDoctorAuthorityDeniesAnEmptyCalendar() {
        when(actors.current()).thenReturn(new ActorIdentity(ACCOUNT_ID, DOCTOR_ID, null, "DOCTOR"));
        when(correlations.currentOrCreate()).thenReturn(id(9));
        when(authority.currentDoctorAuthority(ACCOUNT_ID, id(9).toString())).thenReturn(
                new CurrentDoctorAuthoritySnapshot(ACCOUNT_ID, true, false, "DOCTOR", DOCTOR_ID,
                        true, DEPARTMENT_ID, true, NOW));

        assertThatThrownBy(() -> service.mine(null, LocalDate.of(2026, 10, 27)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void requiresExactlyOneDateSelectorAndMondayWeekStart() {
        assertThatThrownBy(() -> service.mine(null, null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.mine(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 27)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.mine(LocalDate.of(2026, 10, 27), null))
                .isInstanceOf(BusinessRuleException.class);
    }

    private CurrentDoctorAuthoritySnapshot authoritySnapshot() {
        return new CurrentDoctorAuthoritySnapshot(ACCOUNT_ID, true, true, "DOCTOR", DOCTOR_ID,
                true, DEPARTMENT_ID, true, NOW);
    }

    private static UUID id(int suffix) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(suffix));
    }
}
