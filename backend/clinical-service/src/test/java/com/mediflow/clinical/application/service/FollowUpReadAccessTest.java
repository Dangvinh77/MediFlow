package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mediflow.clinical.application.dto.authority.CurrentDoctorAuthoritySnapshot;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.response.FollowUpRecommendationDTO;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.ConsultationSessionRepositoryPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.FollowUpRecommendationOutboxPort;
import com.mediflow.clinical.application.port.out.FollowUpRecommendationRepositoryPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.OutpatientRegistrationRepositoryPort;
import com.mediflow.clinical.application.port.out.RecordAccessDeclarationPort;
import com.mediflow.clinical.application.port.out.RecordAccessLogPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.domain.model.Diagnosis;
import com.mediflow.clinical.domain.model.FollowUpRecommendation;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.clinical.domain.model.RecordDisposition;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.common.exception.ResourceNotFoundException;

class FollowUpReadAccessTest {
    private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");
    private static final UUID CORRELATION = UUID.randomUUID();
    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID DEPARTMENT = UUID.randomUUID();
    private static final UUID SOURCE_DOCTOR = UUID.randomUUID();
    private static final UUID RECORD_ID = UUID.randomUUID();
    private static final UUID REGISTRATION = UUID.randomUUID();

    private final MedicalRecordRepositoryPort records = mock(MedicalRecordRepositoryPort.class);
    private final AppointmentRepositoryPort appointments = mock(AppointmentRepositoryPort.class);
    private final FollowUpRecommendationRepositoryPort recommendations = mock(FollowUpRecommendationRepositoryPort.class);
    private final CurrentClinicalActorPort actors = mock(CurrentClinicalActorPort.class);
    private final SchedulingAuthorityPort authority = mock(SchedulingAuthorityPort.class);
    private final CorrelationIdProvider correlations = mock(CorrelationIdProvider.class);
    private final RecordAccessDeclarationPort declarations = mock(RecordAccessDeclarationPort.class);
    private final RecordAccessLogPort log = mock(RecordAccessLogPort.class);

    private FollowUpRecommendationApplicationService service(boolean policyEnabled) {
        RecordReadGuard guard = new RecordReadGuard(new RecordReadPolicy(appointments, declarations, authority), log,
                actors, correlations);
        return new FollowUpRecommendationApplicationService(records, appointments,
                mock(OutpatientRegistrationRepositoryPort.class), mock(ConsultationSessionRepositoryPort.class),
                recommendations, mock(FollowUpRecommendationOutboxPort.class), actors, authority,
                mock(IdempotentCommandPort.class), correlations, Clock.fixed(NOW, ZoneOffset.UTC), guard, policyEnabled);
    }

    @BeforeEach
    void setUp() {
        when(correlations.currentOrCreate()).thenReturn(CORRELATION);
        when(declarations.findActive(any(), any(), any())).thenReturn(Optional.empty());
        when(appointments.findByPatient(PATIENT)).thenReturn(List.of());
        when(records.findById(RECORD_ID)).thenReturn(Optional.of(sourceRecord()));
        when(recommendations.findBySourceRecordId(RECORD_ID)).thenReturn(Optional.of(advice()));
    }

    @Test
    void flagOffKeepsAuthorOnlyRule() {
        doctor(UUID.randomUUID());

        assertThatThrownBy(() -> service(false).get(RECORD_ID)).isInstanceOf(ResourceNotFoundException.class);
        verify(log, never()).append(any());
    }

    @Test
    void receivingDoctorWithAssignmentReadsSourceAdvice() {
        ActorIdentity receiving = doctor(UUID.randomUUID());
        when(appointments.findByPatient(PATIENT)).thenReturn(List.of(Appointment.restore(UUID.randomUUID(), PATIENT,
                receiving.staffId(), UUID.randomUUID(), LocalDate.of(2026, 11, 12), LocalTime.of(9, 0),
                AppointmentStatus.PENDING, "visit", NOW, null)));

        FollowUpRecommendationDTO dto = service(true).get(RECORD_ID);

        assertThat(dto.sourceRecordId()).isEqualTo(RECORD_ID);
        verify(log).append(any());
    }

    @Test
    void doctorWithoutBasisGets403NotNotFound() {
        doctor(UUID.randomUUID());

        assertThatThrownBy(() -> service(true).get(RECORD_ID))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("code", "RECORD_ACCESS_PURPOSE_REQUIRED");
    }

    @Test
    void missingAdviceStaysNotFoundForAllowedReader() {
        doctor(SOURCE_DOCTOR);
        when(recommendations.findBySourceRecordId(RECORD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(true).get(RECORD_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "RESOURCE_NOT_FOUND");
    }

    @Test
    void patientSelfRuleUnchanged() {
        when(actors.current()).thenReturn(new ActorIdentity(UUID.randomUUID(), null, PATIENT, "PATIENT"));

        assertThat(service(true).get(RECORD_ID).sourceRecordId()).isEqualTo(RECORD_ID);
        verify(authority, never()).currentDoctorAuthority(any(), any());

        when(actors.current()).thenReturn(new ActorIdentity(UUID.randomUUID(), null, UUID.randomUUID(), "PATIENT"));
        assertThatThrownBy(() -> service(true).get(RECORD_ID)).isInstanceOf(ResourceNotFoundException.class);
    }

    private ActorIdentity doctor(UUID staffId) {
        ActorIdentity actor = new ActorIdentity(UUID.randomUUID(), staffId, null, "DOCTOR");
        when(actors.current()).thenReturn(actor);
        when(authority.currentDoctorAuthority(actor.accountId(), CORRELATION.toString())).thenReturn(
                new CurrentDoctorAuthoritySnapshot(actor.accountId(), true, true, "DOCTOR", staffId, true,
                        UUID.randomUUID(), true, NOW));
        return actor;
    }

    private static MedicalRecord sourceRecord() {
        return MedicalRecord.restore(RECORD_ID, PATIENT, SOURCE_DOCTOR, DEPARTMENT, LocalDate.of(2026, 10, 20), null,
                REGISTRATION, List.of(Diagnosis.restore(UUID.randomUUID(), "Condition", null, null)), NOW, NOW,
                MedicalRecordStatus.COMPLETED, RecordDisposition.OUTPATIENT_FOLLOW_UP, null, NOW);
    }

    private static FollowUpRecommendation advice() {
        return new FollowUpRecommendation(UUID.randomUUID(), RECORD_ID, REGISTRATION, PATIENT, DEPARTMENT, SOURCE_DOCTOR,
                LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 30), "Review symptoms", null, NOW, UUID.randomUUID(),
                SOURCE_DOCTOR);
    }
}
