package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

import com.mediflow.clinical.application.dto.authority.ConsultationEligibilitySnapshot;
import com.mediflow.clinical.application.dto.authority.SchedulingAuthoritySnapshot;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.RegistrationCreateRequest;
import com.mediflow.clinical.application.dto.response.FollowUpSourceSummary;
import com.mediflow.clinical.application.dto.response.OutpatientRegistrationDTO;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicalOutboxPort;
import com.mediflow.clinical.application.port.out.ConsultationSessionRepositoryPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.FollowUpRecommendationRepositoryPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.OutpatientRegistrationRepositoryPort;
import com.mediflow.clinical.application.port.out.PatientLookupPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.Diagnosis;
import com.mediflow.clinical.domain.model.FollowUpRecommendation;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.clinical.domain.model.OutpatientReceptionMode;
import com.mediflow.clinical.domain.model.OutpatientRegistration;
import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.clinical.domain.model.OutpatientReservationStatus;
import com.mediflow.clinical.domain.model.OutpatientVisitType;
import com.mediflow.clinical.domain.model.RecordDisposition;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.common.exception.ResourceNotFoundException;

class FollowUpRegistrationAcceptanceTest {
    private static final Instant NOW = Instant.parse("2026-10-26T02:00:00Z");
    private static final Instant SOURCE_AT = Instant.parse("2026-10-20T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID ACTOR_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID ACTOR_STAFF_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID SOURCE_DOCTOR_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final UUID SOURCE_REGISTRATION_ID = UUID.randomUUID();
    private static final UUID SOURCE_RECORD_ID = UUID.randomUUID();
    private static final UUID SOURCE_SESSION_ID = UUID.randomUUID();
    private static final UUID TARGET_SESSION_ID = UUID.randomUUID();
    private static final UUID RECOMMENDATION_ID = UUID.randomUUID();
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    private final ConsultationSessionRepositoryPort sessions = mock(ConsultationSessionRepositoryPort.class);
    private final OutpatientRegistrationRepositoryPort registrations = mock(OutpatientRegistrationRepositoryPort.class);
    private final AppointmentRepositoryPort appointments = mock(AppointmentRepositoryPort.class);
    private final PatientLookupPort patients = mock(PatientLookupPort.class);
    private final SchedulingAuthorityPort authority = mock(SchedulingAuthorityPort.class);
    private final CurrentClinicalActorPort actors = mock(CurrentClinicalActorPort.class);
    private final ClinicalOutboxPort outbox = mock(ClinicalOutboxPort.class);
    private final CorrelationIdProvider correlations = mock(CorrelationIdProvider.class);
    private final IdempotentCommandPort idempotency = mock(IdempotentCommandPort.class);
    private final MedicalRecordRepositoryPort records = mock(MedicalRecordRepositoryPort.class);
    private final FollowUpRecommendationRepositoryPort recommendations = mock(FollowUpRecommendationRepositoryPort.class);

    private OutpatientRegistrationApplicationService service;
    private ConsultationSession targetSession;
    private ConsultationSession sourceSession;
    private OutpatientRegistration sourceRegistration;
    private Appointment sourceAppointment;
    private MedicalRecord sourceRecord;
    private FollowUpRecommendation recommendation;

    @BeforeEach
    void setUp() {
        service = new OutpatientRegistrationApplicationService(sessions, registrations, appointments, patients,
                authority, actors, outbox, correlations, idempotency, CLOCK, records, recommendations);
        targetSession = session(TARGET_SESSION_ID, SOURCE_DOCTOR_ID, DEPARTMENT_ID);
        sourceSession = session(SOURCE_SESSION_ID, SOURCE_DOCTOR_ID, DEPARTMENT_ID, LocalDate.parse("2026-10-20"));
        sourceRegistration = sourceRegistration(OutpatientRegistrationStatus.COMPLETED);
        sourceAppointment = completedAppointment(SOURCE_REGISTRATION_ID, PATIENT_ID, SOURCE_DOCTOR_ID, DEPARTMENT_ID);
        sourceRecord = completedRecord(SOURCE_RECORD_ID, SOURCE_REGISTRATION_ID, PATIENT_ID,
                SOURCE_DOCTOR_ID, DEPARTMENT_ID);
        recommendation = recommendation(RECOMMENDATION_ID, SOURCE_RECORD_ID, SOURCE_REGISTRATION_ID,
                PATIENT_ID, SOURCE_DOCTOR_ID, DEPARTMENT_ID);

        when(actors.current()).thenReturn(new ActorIdentity(ACTOR_ACCOUNT_ID, ACTOR_STAFF_ID, null, "NURSE"));
        when(correlations.currentOrCreate()).thenReturn(CORRELATION_ID);
        when(patients.exists(any())).thenReturn(true);
        when(sessions.findById(TARGET_SESSION_ID)).thenAnswer(ignored -> Optional.of(targetSession));
        when(sessions.findByIdForUpdate(TARGET_SESSION_ID)).thenAnswer(ignored -> Optional.of(targetSession));
        when(sessions.findById(SOURCE_SESSION_ID)).thenReturn(Optional.of(sourceSession));
        when(registrations.findById(SOURCE_REGISTRATION_ID)).thenAnswer(ignored -> Optional.of(sourceRegistration));
        when(registrations.findByIdForUpdate(SOURCE_REGISTRATION_ID)).thenAnswer(ignored -> Optional.of(sourceRegistration));
        when(registrations.existsActivePatientSession(any(), any())).thenReturn(false);
        when(registrations.insert(any(), any(), any(), any(), any())).thenAnswer(call -> call.getArgument(0));
        when(appointments.findById(SOURCE_REGISTRATION_ID)).thenAnswer(ignored -> Optional.of(sourceAppointment));
        when(appointments.save(any())).thenAnswer(call -> call.getArgument(0));
        when(records.findByAppointmentId(SOURCE_REGISTRATION_ID)).thenAnswer(ignored -> Optional.of(sourceRecord));
        when(recommendations.findBySourceRecordId(SOURCE_RECORD_ID)).thenAnswer(ignored -> Optional.of(recommendation));
        when(authority.authority(eq(ACTOR_ACCOUNT_ID), any(UUID.class), anyString())).thenAnswer(call ->
                currentAuthority((UUID) call.getArgument(1)));
        when(authority.consultationEligibility(any(), any(), any(), anyString())).thenAnswer(call -> {
            UUID doctorId = call.getArgument(0);
            UUID departmentId = call.getArgument(1);
            UUID roomId = call.getArgument(2);
            return new ConsultationEligibilitySnapshot(doctorId, true, true, true, departmentId,
                    departmentId, true, true, roomId, true, true, departmentId, "CONSULTATION", true, NOW);
        });
        when(idempotency.execute(anyString(), anyString(), anyString(), anyString(), anyString(),
                eq(OutpatientRegistrationDTO.class), anyInt(), any(), any()))
                .thenAnswer(call -> {
                    Supplier<OutpatientRegistrationDTO> command = call.getArgument(8);
                    OutpatientRegistrationDTO result = command.get();
                    return new IdempotentCommandPort.Result<>(result, call.getArgument(6),
                            "/api/v2/outpatient-registrations/" + result.registrationId(), false);
                });
    }

    @Test
    void acceptsSamePatientCompletedV2SourceInTheSameDepartment() {
        var result = service.create(request(PATIENT_ID, SOURCE_REGISTRATION_ID, RECOMMENDATION_ID,
                SOURCE_DOCTOR_ID, null, null), "registration-1");

        assertThat(result.data().visitType()).isEqualTo(OutpatientVisitType.FOLLOW_UP);
        assertThat(result.data().previousRegistrationId()).isEqualTo(SOURCE_REGISTRATION_ID);
        ArgumentCaptor<OutpatientRegistration> registration = ArgumentCaptor.forClass(OutpatientRegistration.class);
        verify(registrations).insert(registration.capture(), eq(ACTOR_ACCOUNT_ID), eq(ACTOR_STAFF_ID),
                eq("CREATED"), any());
        assertThat(registration.getValue().followUp().previousRegistrationId()).isEqualTo(SOURCE_REGISTRATION_ID);
        assertThat(registration.getValue().followUp().recommendationId()).isEqualTo(RECOMMENDATION_ID);
    }

    @Test
    void rejectsAnotherPatientsCompletedSourceBeforeAnyRegistrationWrite() {
        UUID otherPatientId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(request(otherPatientId, SOURCE_REGISTRATION_ID,
                RECOMMENDATION_ID, SOURCE_DOCTOR_ID, null, null), "registration-1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "RESOURCE_NOT_FOUND");

        verify(registrations, never()).insert(any(), any(), any(), any(), any());
        verify(appointments, never()).save(any());
    }

    @Test
    void rejectsSourceFromAnotherDepartment() {
        targetSession = session(TARGET_SESSION_ID, SOURCE_DOCTOR_ID, UUID.randomUUID());

        assertThatThrownBy(() -> service.create(request(PATIENT_ID, SOURCE_REGISTRATION_ID,
                RECOMMENDATION_ID, SOURCE_DOCTOR_ID, null, null), "registration-1"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(registrations, never()).insert(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsNonCompletedSourceRegistration() {
        sourceRegistration = sourceRegistration(OutpatientRegistrationStatus.CONFIRMED);

        assertThatThrownBy(() -> service.create(request(PATIENT_ID, SOURCE_REGISTRATION_ID,
                RECOMMENDATION_ID, SOURCE_DOCTOR_ID, null, null), "registration-1"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(registrations, never()).insert(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsCompletedLegacyAppointmentAndOpenSourceRecord() {
        sourceAppointment = Appointment.restore(SOURCE_REGISTRATION_ID, PATIENT_ID, SOURCE_DOCTOR_ID,
                DEPARTMENT_ID, LocalDate.parse("2026-10-20"), LocalTime.of(10, 0),
                com.mediflow.clinical.domain.model.AppointmentStatus.COMPLETED, "legacy", SOURCE_AT, SOURCE_AT,
                (short) 1, UUID.randomUUID(), SOURCE_AT, null, "OUTPATIENT_EXAM", SOURCE_AT, SOURCE_AT, SOURCE_AT);

        assertThatThrownBy(() -> service.create(request(PATIENT_ID, SOURCE_REGISTRATION_ID,
                RECOMMENDATION_ID, SOURCE_DOCTOR_ID, null, null), "registration-1"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(registrations, never()).insert(any(), any(), any(), any(), any());

        sourceAppointment = completedAppointment(SOURCE_REGISTRATION_ID, PATIENT_ID,
                SOURCE_DOCTOR_ID, DEPARTMENT_ID);
        sourceRecord = MedicalRecord.restore(SOURCE_RECORD_ID, PATIENT_ID, SOURCE_DOCTOR_ID, DEPARTMENT_ID,
                LocalDate.parse("2026-10-20"), null, SOURCE_REGISTRATION_ID, List.of(), SOURCE_AT, SOURCE_AT,
                MedicalRecordStatus.OPEN, null, null, null);
        assertThatThrownBy(() -> service.create(request(PATIENT_ID, SOURCE_REGISTRATION_ID,
                RECOMMENDATION_ID, SOURCE_DOCTOR_ID, null, null), "registration-2"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(registrations, never()).insert(any(), any(), any(), any(), any());
    }

    @Test
    void requiresPatientConsentAndReasonForSubstituteDoctorAndPersistsBoth() {
        UUID substituteDoctor = UUID.randomUUID();
        targetSession = session(TARGET_SESSION_ID, substituteDoctor, DEPARTMENT_ID);

        assertThatThrownBy(() -> service.create(request(PATIENT_ID, SOURCE_REGISTRATION_ID,
                RECOMMENDATION_ID, substituteDoctor, null, null), "registration-1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "FOLLOW_UP_DOCTOR_REPLACEMENT_INVALID");

        var result = service.create(request(PATIENT_ID, SOURCE_REGISTRATION_ID, RECOMMENDATION_ID,
                substituteDoctor, "Patient agreed to continue with the available doctor", true), "registration-2");

        assertThat(result.data().visitType()).isEqualTo(OutpatientVisitType.FOLLOW_UP);
        ArgumentCaptor<OutpatientRegistration> registration = ArgumentCaptor.forClass(OutpatientRegistration.class);
        verify(registrations).insert(registration.capture(), eq(ACTOR_ACCOUNT_ID), eq(ACTOR_STAFF_ID),
                eq("CREATED"), any());
        assertThat(registration.getValue().followUp().replacementReason())
                .isEqualTo("Patient agreed to continue with the available doctor");
        assertThat(registration.getValue().followUp().patientAgreedToDoctorReplacement()).isTrue();
    }

    @Test
    void rejectsRecommendationNotOwnedByTheExactCompletedSource() {
        UUID unrelatedRecommendation = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(request(PATIENT_ID, SOURCE_REGISTRATION_ID,
                unrelatedRecommendation, SOURCE_DOCTOR_ID, null, null), "registration-1"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(registrations, never()).insert(any(), any(), any(), any(), any());
    }

    @Test
    void permitsSelfReturnWithoutRecommendationOnlyWhenReasonIsRecorded() {
        assertThatThrownBy(() -> service.create(new RegistrationCreateRequest(PATIENT_ID, TARGET_SESSION_ID, 1,
                OutpatientReceptionMode.AT_COUNTER, OutpatientVisitType.FOLLOW_UP, SOURCE_REGISTRATION_ID,
                null, null, null, null), "registration-1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_REQUEST");
        verify(registrations, never()).insert(any(), any(), any(), any(), any());

        var request = new RegistrationCreateRequest(PATIENT_ID, TARGET_SESSION_ID, 1,
                OutpatientReceptionMode.AT_COUNTER, OutpatientVisitType.FOLLOW_UP, SOURCE_REGISTRATION_ID,
                "Patient requests follow-up", null, null, null);
        service.create(request, "registration-2");

        ArgumentCaptor<Appointment> appointment = ArgumentCaptor.forClass(Appointment.class);
        verify(appointments).save(appointment.capture());
        assertThat(appointment.getValue().getReason()).isEqualTo("Patient requests follow-up");
        ArgumentCaptor<OutpatientRegistration> registration = ArgumentCaptor.forClass(OutpatientRegistration.class);
        verify(registrations).insert(registration.capture(), eq(ACTOR_ACCOUNT_ID), eq(ACTOR_STAFF_ID),
                eq("CREATED"), any());
        assertThat(registration.getValue().followUp().recommendationId()).isNull();
    }

    @Test
    void rejectsReplacementClaimsWhenThePreferredDoctorIsAlreadyScheduled() {
        assertThatThrownBy(() -> service.create(request(PATIENT_ID, SOURCE_REGISTRATION_ID,
                RECOMMENDATION_ID, SOURCE_DOCTOR_ID, "Patient agreed", true), "registration-1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "FOLLOW_UP_DOCTOR_REPLACEMENT_INVALID");

        verify(registrations, never()).insert(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsNewVisitRequestsThatCarryFollowUpFields() {
        var request = new RegistrationCreateRequest(PATIENT_ID, TARGET_SESSION_ID, 1,
                OutpatientReceptionMode.AT_COUNTER, OutpatientVisitType.NEW_VISIT,
                SOURCE_REGISTRATION_ID, null, RECOMMENDATION_ID, null, null);

        assertThatThrownBy(() -> service.create(request, "registration-1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_REQUEST");

        verify(registrations, never()).insert(any(), any(), any(), any(), any());
    }

    @Test
    void sourcePickerRequiresCurrentDepartmentAuthorityForEveryReturnedSource() {
        UUID otherDepartment = UUID.randomUUID();
        var source = new FollowUpSourceSummary(SOURCE_REGISTRATION_ID, SOURCE_RECORD_ID, PATIENT_ID,
                otherDepartment, SOURCE_DOCTOR_ID, SOURCE_AT, null);
        PageQuery page = PageQuery.of(0, 20);
        when(registrations.findFollowUpSources(PATIENT_ID, page)).thenReturn(PageResult.of(List.of(source), 1, 0, 20));
        when(authority.authority(eq(ACTOR_ACCOUNT_ID), eq(otherDepartment), anyString()))
                .thenReturn(currentAuthority(DEPARTMENT_ID));

        assertThatThrownBy(() -> service.followUpSources(PATIENT_ID, page))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("code", "SCHEDULING_SCOPE_DENIED");
    }

    @Test
    void sourcePickerReturnsNullableRecommendationOnlyAfterCurrentScopeCheck() {
        var source = new FollowUpSourceSummary(SOURCE_REGISTRATION_ID, SOURCE_RECORD_ID, PATIENT_ID,
                DEPARTMENT_ID, SOURCE_DOCTOR_ID, SOURCE_AT, null);
        PageQuery page = PageQuery.of(0, 20);
        when(registrations.findFollowUpSources(PATIENT_ID, page)).thenReturn(PageResult.of(List.of(source), 1, 0, 20));
        when(recommendations.findBySourceRecordId(SOURCE_RECORD_ID)).thenReturn(Optional.empty());

        var result = service.followUpSources(PATIENT_ID, page);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).recommendation()).isNull();
        verify(authority).authority(eq(ACTOR_ACCOUNT_ID), eq(DEPARTMENT_ID), anyString());
    }

    @Test
    void managerWithoutCurrentScheduleAssignmentCannotReadSessionRegistrationSummaries() {
        PageQuery page = PageQuery.of(0, 20);
        when(actors.current()).thenReturn(new ActorIdentity(ACTOR_ACCOUNT_ID, ACTOR_STAFF_ID, null, "MANAGER"));
        when(authority.authority(eq(ACTOR_ACCOUNT_ID), eq(DEPARTMENT_ID), anyString()))
                .thenReturn(managerAuthority(false));
        when(registrations.findBySession(TARGET_SESSION_ID, page)).thenReturn(PageResult.of(List.of(), 0, 0, 20));

        assertThatThrownBy(() -> service.sessionRegistrations(TARGET_SESSION_ID, page))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("code", "SCHEDULING_SCOPE_DENIED");

        verify(registrations, never()).findBySession(TARGET_SESSION_ID, page);
    }

    @Test
    void managerWithCurrentScheduleAssignmentCanReadSessionRegistrationSummaries() {
        PageQuery page = PageQuery.of(0, 20);
        when(actors.current()).thenReturn(new ActorIdentity(ACTOR_ACCOUNT_ID, ACTOR_STAFF_ID, null, "MANAGER"));
        when(authority.authority(eq(ACTOR_ACCOUNT_ID), eq(DEPARTMENT_ID), anyString()))
                .thenReturn(managerAuthority(true));
        when(registrations.findBySession(TARGET_SESSION_ID, page)).thenReturn(PageResult.of(List.of(), 0, 0, 20));

        var result = service.sessionRegistrations(TARGET_SESSION_ID, page);

        assertThat(result.content()).isEmpty();
        verify(registrations).findBySession(TARGET_SESSION_ID, page);
    }

    private RegistrationCreateRequest request(UUID patientId, UUID previousRegistrationId,
            UUID recommendationId, UUID targetDoctorId, String replacementReason, Boolean consent) {
        return new RegistrationCreateRequest(patientId, TARGET_SESSION_ID, 1, OutpatientReceptionMode.AT_COUNTER,
                OutpatientVisitType.FOLLOW_UP, previousRegistrationId, null, recommendationId,
                replacementReason, consent);
    }

    private SchedulingAuthoritySnapshot currentAuthority(UUID departmentId) {
        return new SchedulingAuthoritySnapshot(ACTOR_ACCOUNT_ID, true, true, "NURSE", ACTOR_STAFF_ID,
                true, departmentId, true, false, 3L, NOW);
    }

    private SchedulingAuthoritySnapshot managerAuthority(boolean canManageSchedules) {
        return new SchedulingAuthoritySnapshot(ACTOR_ACCOUNT_ID, true, true, "MANAGER", ACTOR_STAFF_ID,
                true, DEPARTMENT_ID, true, canManageSchedules, 3L, NOW);
    }

    private static ConsultationSession session(UUID id, UUID doctorId, UUID departmentId) {
        return session(id, doctorId, departmentId, LocalDate.parse("2026-10-27"));
    }

    private static ConsultationSession session(UUID id, UUID doctorId, UUID departmentId, LocalDate date) {
        return ConsultationSession.open(id, doctorId, departmentId, UUID.randomUUID(), date,
                LocalTime.of(9, 0), LocalTime.of(11, 0), List.of(), 1);
    }

    private static OutpatientRegistration sourceRegistration(OutpatientRegistrationStatus status) {
        OutpatientReservationStatus reservationStatus = status == OutpatientRegistrationStatus.COMPLETED
                ? OutpatientReservationStatus.CONSUMED : OutpatientReservationStatus.CONFIRMED;
        var reservation = new OutpatientRegistration.Reservation(UUID.randomUUID(), 3, reservationStatus,
                SOURCE_AT.plusSeconds(900), UUID.randomUUID(), 1L, SOURCE_AT, SOURCE_AT.plusSeconds(120),
                UUID.randomUUID(), UUID.randomUUID(), SOURCE_AT);
        return new OutpatientRegistration(SOURCE_REGISTRATION_ID, 3, PATIENT_ID, SOURCE_SESSION_ID,
                OutpatientReceptionMode.AT_COUNTER, OutpatientVisitType.NEW_VISIT, status, SOURCE_AT,
                1, SOURCE_AT, SOURCE_AT, reservation, null);
    }

    private static Appointment completedAppointment(UUID registrationId, UUID patientId,
            UUID doctorId, UUID departmentId) {
        Appointment appointment = Appointment.createScheduled(registrationId, patientId, doctorId, departmentId,
                LocalDate.parse("2026-10-20"), LocalTime.of(10, 0), "source", SOURCE_AT, null);
        UUID clearance = UUID.randomUUID();
        appointment.confirmScheduledPayment(clearance, SOURCE_AT);
        appointment.arriveScheduled(SOURCE_AT);
        appointment.startScheduledExam(clearance, SOURCE_AT);
        appointment.completeScheduled(SOURCE_AT);
        return appointment;
    }

    private static MedicalRecord completedRecord(UUID recordId, UUID registrationId, UUID patientId,
            UUID doctorId, UUID departmentId) {
        return MedicalRecord.restore(recordId, patientId, doctorId, departmentId, LocalDate.parse("2026-10-20"),
                null, registrationId, List.of(Diagnosis.restore(UUID.randomUUID(), "Condition", null, null)),
                SOURCE_AT, SOURCE_AT, MedicalRecordStatus.COMPLETED, RecordDisposition.OUTPATIENT_FOLLOW_UP,
                null, SOURCE_AT);
    }

    private static FollowUpRecommendation recommendation(UUID recommendationId, UUID recordId,
            UUID registrationId, UUID patientId, UUID doctorId, UUID departmentId) {
        return new FollowUpRecommendation(recommendationId, recordId, registrationId, patientId, departmentId,
                doctorId, LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-30"), "Review symptoms",
                null, NOW, ACTOR_ACCOUNT_ID, doctorId);
    }
}
