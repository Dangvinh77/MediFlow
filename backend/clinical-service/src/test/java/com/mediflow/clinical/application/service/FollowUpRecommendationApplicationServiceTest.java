package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mediflow.clinical.application.dto.authority.CurrentDoctorAuthoritySnapshot;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.FollowUpRecommendationRequest;
import com.mediflow.clinical.application.dto.response.FollowUpRecommendationDTO;
import com.mediflow.clinical.application.event.DomainEventEnvelope;
import com.mediflow.clinical.application.event.FollowUpRecommendationCreatedFact;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicalOutboxPort;
import com.mediflow.clinical.application.port.out.ConsultationSessionRepositoryPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.FollowUpRecommendationRepositoryPort;
import com.mediflow.clinical.application.port.out.FollowUpRecommendationOutboxPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.OutpatientRegistrationRepositoryPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.Diagnosis;
import com.mediflow.clinical.domain.model.FollowUpRecommendation;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.OutpatientReceptionMode;
import com.mediflow.clinical.domain.model.OutpatientRegistration;
import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.clinical.domain.model.OutpatientReservationStatus;
import com.mediflow.clinical.domain.model.OutpatientVisitType;
import com.mediflow.clinical.domain.model.RecordDisposition;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.common.exception.ResourceNotFoundException;

class FollowUpRecommendationApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-26T02:00:00Z");
    private static final Instant SOURCE_AT = Instant.parse("2026-10-20T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final UUID REGISTRATION_ID = UUID.randomUUID();
    private static final UUID RECORD_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    private final MedicalRecordRepositoryPort records = mock(MedicalRecordRepositoryPort.class);
    private final AppointmentRepositoryPort appointments = mock(AppointmentRepositoryPort.class);
    private final OutpatientRegistrationRepositoryPort registrations = mock(OutpatientRegistrationRepositoryPort.class);
    private final ConsultationSessionRepositoryPort sessions = mock(ConsultationSessionRepositoryPort.class);
    private final FollowUpRecommendationRepositoryPort recommendations = mock(FollowUpRecommendationRepositoryPort.class);
    private final FollowUpRecommendationOutboxPort followUpOutbox = mock(FollowUpRecommendationOutboxPort.class);
    private final CurrentClinicalActorPort actors = mock(CurrentClinicalActorPort.class);
    private final SchedulingAuthorityPort authority = mock(SchedulingAuthorityPort.class);
    private final IdempotentCommandPort idempotency = mock(IdempotentCommandPort.class);
    private final CorrelationIdProvider correlations = mock(CorrelationIdProvider.class);
    private final FollowUpRecommendationApplicationService service = new FollowUpRecommendationApplicationService(
            records, appointments, registrations, sessions, recommendations, followUpOutbox,
            actors, authority, idempotency, correlations, CLOCK);

    @BeforeEach
    void setUp() {
        when(correlations.currentOrCreate()).thenReturn(CORRELATION_ID);
        when(actors.current()).thenReturn(new ActorIdentity(ACCOUNT_ID, DOCTOR_ID, null, "DOCTOR"));
        when(authority.currentDoctorAuthority(eq(ACCOUNT_ID), anyString())).thenReturn(
                new CurrentDoctorAuthoritySnapshot(ACCOUNT_ID, true, true, "DOCTOR", DOCTOR_ID,
                        true, DEPARTMENT_ID, true, NOW));
        when(idempotency.execute(eq(ACCOUNT_ID.toString()), eq("POST"), anyString(), eq("advice-1"),
                anyString(), eq(FollowUpRecommendationDTO.class), eq(201), any(), any()))
                .thenAnswer(call -> {
                    Supplier<FollowUpRecommendationDTO> command = call.getArgument(8);
                    FollowUpRecommendationDTO result = command.get();
                    return new IdempotentCommandPort.Result<>(result, 201,
                            "/api/v2/records/" + RECORD_ID + "/follow-up-recommendation", false);
                });
        when(recommendations.save(any(), any(), any())).thenAnswer(call -> call.getArgument(0));

        MedicalRecord record = MedicalRecord.restore(RECORD_ID, PATIENT_ID, DOCTOR_ID, DEPARTMENT_ID,
                LocalDate.parse("2026-10-20"), "symptoms", REGISTRATION_ID,
                List.of(Diagnosis.restore(UUID.randomUUID(), "Condition", null, null)),
                SOURCE_AT, NOW, com.mediflow.clinical.domain.model.MedicalRecordStatus.COMPLETED,
                RecordDisposition.OUTPATIENT_FOLLOW_UP, null, SOURCE_AT);
        when(records.findByIdForUpdate(RECORD_ID)).thenReturn(Optional.of(record));
        when(records.findById(RECORD_ID)).thenReturn(Optional.of(record));

        Appointment appointment = Appointment.createScheduled(REGISTRATION_ID, PATIENT_ID, DOCTOR_ID,
                DEPARTMENT_ID, LocalDate.parse("2026-10-20"), LocalTime.of(10, 0), "symptoms",
                SOURCE_AT, SOURCE_AT);
        UUID clearanceId = UUID.randomUUID();
        appointment.confirmScheduledPayment(clearanceId, SOURCE_AT);
        appointment.startScheduledExam(clearanceId, SOURCE_AT);
        appointment.completeScheduled(SOURCE_AT);
        when(appointments.findByIdForUpdate(REGISTRATION_ID)).thenReturn(Optional.of(appointment));
        when(appointments.findById(REGISTRATION_ID)).thenReturn(Optional.of(appointment));

        var reservation = new OutpatientRegistration.Reservation(UUID.randomUUID(), 3,
                OutpatientReservationStatus.CONSUMED, NOW, UUID.randomUUID(), 1L,
                SOURCE_AT, SOURCE_AT.plusSeconds(120), UUID.randomUUID(), clearanceId, SOURCE_AT);
        var registration = new OutpatientRegistration(REGISTRATION_ID, 3, PATIENT_ID, SESSION_ID,
                OutpatientReceptionMode.AT_COUNTER, OutpatientVisitType.NEW_VISIT,
                OutpatientRegistrationStatus.COMPLETED, SOURCE_AT, 1, SOURCE_AT,
                SOURCE_AT, reservation, null);
        when(registrations.findByIdForUpdate(REGISTRATION_ID)).thenReturn(Optional.of(registration));
        when(registrations.findById(REGISTRATION_ID)).thenReturn(Optional.of(registration));
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(ConsultationSession.open(SESSION_ID,
                DOCTOR_ID, DEPARTMENT_ID, UUID.randomUUID(), LocalDate.parse("2026-10-20"),
                LocalTime.of(9, 0), LocalTime.of(11, 0), List.of(), 1)));
    }

    @Test
    void createPersistsOneRecommendationAndPublishesOnlyIdentifierAndDateFacts() {
        var request = new FollowUpRecommendationRequest(LocalDate.parse("2026-10-25"),
                LocalDate.parse("2026-10-27"), "Review symptoms", "Bring medication list");

        var result = service.create(RECORD_ID, request, "advice-1");

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.data().preferredDoctorId()).isEqualTo(DOCTOR_ID);
        ArgumentCaptor<FollowUpRecommendation> recommendation = ArgumentCaptor.forClass(FollowUpRecommendation.class);
        verify(recommendations).save(recommendation.capture(), any(), eq(CORRELATION_ID.toString()));
        assertThat(recommendation.getValue().sourceRegistrationId()).isEqualTo(REGISTRATION_ID);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<DomainEventEnvelope<FollowUpRecommendationCreatedFact>> event =
                ArgumentCaptor.forClass((Class) DomainEventEnvelope.class);
        verify(followUpOutbox).append(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo("followup.recommendation.created");
        assertThat(event.getValue().payload()).isInstanceOf(FollowUpRecommendationCreatedFact.class);
        assertThat(event.getValue().payload().toString()).doesNotContain("Review symptoms", "Bring medication list");
    }

    @Test
    void createRejectsSourceRecordOwnedByAnotherDoctorBeforeWriting() {
        var record = MedicalRecord.openForAppointment(PATIENT_ID, UUID.randomUUID(), DEPARTMENT_ID,
                LocalDate.parse("2026-10-25"), null, REGISTRATION_ID, SOURCE_AT);
        when(records.findByIdForUpdate(RECORD_ID)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.create(RECORD_ID, new FollowUpRecommendationRequest(
                LocalDate.parse("2026-10-25"), LocalDate.parse("2026-10-27"), "Review", null), "advice-1"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(recommendations, never()).save(any(), any(), any());
        verify(followUpOutbox, never()).append(any());
    }

    @Test
    void createRejectsRecordWithoutCompletedV2RegistrationBinding() {
        Appointment legacy = Appointment.create(PATIENT_ID, DOCTOR_ID, DEPARTMENT_ID,
                LocalDate.now(CLOCK), LocalTime.of(10, 0), "legacy", CLOCK);
        when(appointments.findByIdForUpdate(REGISTRATION_ID)).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> service.create(RECORD_ID, new FollowUpRecommendationRequest(
                LocalDate.parse("2026-10-25"), LocalDate.parse("2026-10-27"), "Review", null), "advice-1"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(recommendations, never()).save(any(), any(), any());
    }

    @Test
    void patientCanOnlyReadOwnRecommendationAndMissingRecommendationIsNotFound() {
        when(actors.current()).thenReturn(new ActorIdentity(ACCOUNT_ID, null, UUID.randomUUID(), "PATIENT"));

        assertThatThrownBy(() -> service.get(RECORD_ID)).isInstanceOf(ResourceNotFoundException.class);

        when(actors.current()).thenReturn(new ActorIdentity(ACCOUNT_ID, null, PATIENT_ID, "PATIENT"));
        when(recommendations.findBySourceRecordId(RECORD_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(RECORD_ID)).isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "RESOURCE_NOT_FOUND");
    }

    @Test
    void rejectsInactiveOrChangedCoreDoctorAuthority() {
        when(authority.currentDoctorAuthority(eq(ACCOUNT_ID), anyString())).thenReturn(
                new CurrentDoctorAuthoritySnapshot(ACCOUNT_ID, true, false, "DOCTOR", DOCTOR_ID,
                        true, DEPARTMENT_ID, true, NOW));

        assertThatThrownBy(() -> service.create(RECORD_ID, new FollowUpRecommendationRequest(
                LocalDate.parse("2026-10-25"), LocalDate.parse("2026-10-27"), "Review", null), "advice-1"))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(idempotency, never()).execute(anyString(), anyString(), anyString(), anyString(), anyString(),
                any(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }
}
