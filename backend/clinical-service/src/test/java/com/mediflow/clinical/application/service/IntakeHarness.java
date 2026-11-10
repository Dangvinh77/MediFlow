package com.mediflow.clinical.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.mediflow.clinical.application.dto.authority.ConsultationEligibilitySnapshot;
import com.mediflow.clinical.application.dto.authority.CurrentDoctorAuthoritySnapshot;
import com.mediflow.clinical.application.dto.authority.SchedulingAuthoritySnapshot;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
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
import com.mediflow.clinical.domain.model.OutpatientPaymentOperationState;
import com.mediflow.clinical.domain.model.OutpatientReceptionMode;
import com.mediflow.clinical.domain.model.OutpatientRegistration;

/** Mock ports wired around the real intake services, with registrations in each lifecycle state. */
final class IntakeHarness {
    /** 08:00 on 10/11/2026 in Asia/Ho_Chi_Minh. */
    static final Instant NOW = Instant.parse("2026-11-10T01:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    static final long FENCE = 5L;
    static final UUID ACTOR = UUID.randomUUID();

    final ConsultationSessionRepositoryPort sessions = mock(ConsultationSessionRepositoryPort.class);
    final OutpatientRegistrationRepositoryPort registrations = mock(OutpatientRegistrationRepositoryPort.class);
    final AppointmentRepositoryPort appointments = mock(AppointmentRepositoryPort.class);
    final PatientLookupPort patients = mock(PatientLookupPort.class);
    final SchedulingAuthorityPort authority = mock(SchedulingAuthorityPort.class);
    final CurrentClinicalActorPort actors = mock(CurrentClinicalActorPort.class);
    final ClinicalOutboxPort outbox = mock(ClinicalOutboxPort.class);
    final CorrelationIdProvider correlationIds = mock(CorrelationIdProvider.class);
    final IdempotentCommandPort idempotency = mock(IdempotentCommandPort.class);
    final MedicalRecordRepositoryPort records = mock(MedicalRecordRepositoryPort.class);
    final FollowUpRecommendationRepositoryPort recommendations = mock(FollowUpRecommendationRepositoryPort.class);
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    final UUID departmentId = UUID.randomUUID();
    final UUID otherDepartmentId = UUID.randomUUID();
    final UUID doctorId = UUID.randomUUID();
    final UUID roomId = UUID.randomUUID();

    IntakeHarness() {
        lenient().when(correlationIds.currentOrCreate()).thenReturn(UUID.randomUUID());
        lenient().when(patients.exists(any())).thenReturn(true);
        lenient().when(idempotency.execute(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyInt(), any(), any()))
                .thenAnswer(call -> new IdempotentCommandPort.Result<>(((Supplier<?>) call.getArgument(8)).get(),
                        call.getArgument(6), null, false));
        lenient().when(authority.consultationEligibility(any(), any(), any(), any())).thenAnswer(call ->
                eligibility(call.getArgument(0), call.getArgument(1), call.getArgument(2), true));
    }

    OutpatientRegistrationApplicationService registrationService(boolean clinicIntake) {
        return new OutpatientRegistrationApplicationService(sessions, registrations, appointments, patients, authority,
                actors, outbox, correlationIds, idempotency, clock, records, recommendations,
                new ClinicIntakeSettings(clinicIntake));
    }

    ConsultationSession session() {
        return session(doctorId, roomId, TODAY, 20);
    }

    ConsultationSession session(UUID doctor, UUID room, LocalDate date, int capacity) {
        ConsultationSession session = ConsultationSession.open(UUID.randomUUID(), doctor, departmentId, room, date,
                LocalTime.of(8, 0), LocalTime.of(11, 0), List.of(), capacity);
        return stub(session);
    }

    ConsultationSession stub(ConsultationSession session) {
        lenient().when(sessions.findById(session.sessionId())).thenReturn(Optional.of(session));
        lenient().when(sessions.findByIdForUpdate(session.sessionId())).thenReturn(Optional.of(session));
        return session;
    }

    ConsultationSession withCounts(ConsultationSession session, int held, int locked, int confirmed, int consumed) {
        int capacity = session.configuration().capacity();
        return stub(session.withCounts(new ConsultationSession.Counts(held, locked, confirmed, consumed,
                capacity - held - locked - confirmed - consumed)));
    }

    OutpatientRegistration held(ConsultationSession session, OutpatientReceptionMode mode) {
        return OutpatientRegistration.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), session, mode, NOW);
    }

    OutpatientRegistration paymentLocked(ConsultationSession session, UUID operationId) {
        return held(session, OutpatientReceptionMode.AT_COUNTER)
                .acquirePayment(1, operationId, FENCE, NOW);
    }

    OutpatientRegistration confirmed(ConsultationSession session, OutpatientReceptionMode mode) {
        UUID operation = UUID.randomUUID();
        return held(session, mode).acquirePayment(1, operation, FENCE, NOW).resolveCompleted(operation, FENCE, 3,
                UUID.randomUUID(), UUID.randomUUID(), NOW, false);
    }

    OutpatientRegistration arrivedConfirmed(ConsultationSession session) {
        return confirmed(session, OutpatientReceptionMode.AT_COUNTER);
    }

    OutpatientRegistration stub(OutpatientRegistration registration) {
        lenient().when(registrations.findById(registration.registrationId())).thenReturn(Optional.of(registration));
        lenient().when(registrations.findByIdForUpdate(registration.registrationId())).thenReturn(Optional.of(registration));
        return registration;
    }

    Appointment stubAppointment(OutpatientRegistration registration, ConsultationSession session) {
        var config = session.configuration();
        Appointment appointment = Appointment.createScheduled(registration.registrationId(), registration.patientId(),
                config.doctorId(), config.departmentId(), config.sessionDate(), config.startTime(), "Khám", NOW,
                registration.checkedInAt());
        lenient().when(appointments.findByIdForUpdate(registration.registrationId())).thenReturn(Optional.of(appointment));
        return appointment;
    }

    OutpatientRegistrationRepositoryPort.PaymentOperationReceipt lockedReceipt(OutpatientRegistration registration, UUID operationId, UUID requestId) {
        return new OutpatientRegistrationRepositoryPort.PaymentOperationReceipt(operationId, requestId,
                registration.registrationId(), registration.reservation().reservationId(), registration.patientId(), "EXAM",
                registration.registrationId(), registration.registrationId(), 1, 2, FENCE,
                OutpatientPaymentOperationState.PAYMENT_LOCKED, NOW, NOW.plusSeconds(120), null, null, null, null,
                "PATIENT_PAYMENT", null);
    }

    /** Signs the actor in and makes Core report this role and department for it. */
    ActorIdentity actAs(String role, UUID staffId, UUID staffDepartment) {
        ActorIdentity actor = new ActorIdentity(UUID.randomUUID(), staffId, null, role);
        lenient().when(actors.current()).thenReturn(actor);
        lenient().when(authority.currentDoctorAuthority(any(), any())).thenReturn(new CurrentDoctorAuthoritySnapshot(
                actor.accountId(), true, true, role, staffId, true, staffDepartment, true, NOW));
        lenient().when(authority.authority(any(), any(), any())).thenAnswer(call -> new SchedulingAuthoritySnapshot(
                actor.accountId(), true, true, role, staffId, true, call.getArgument(1), true, false, 7L, NOW));
        return actor;
    }

    /** An administrator whose current Core scope allows schedule management. */
    ActorIdentity actAsManager() {
        ActorIdentity actor = new ActorIdentity(UUID.randomUUID(), null, null, "ADMIN");
        lenient().when(actors.current()).thenReturn(actor);
        lenient().when(authority.authority(any(), any(), any())).thenAnswer(call -> new SchedulingAuthoritySnapshot(
                actor.accountId(), true, true, "ADMIN", null, null, call.getArgument(1), true, true, 7L, NOW));
        return actor;
    }

    ActorIdentity actAsNurse() {
        return actAs("NURSE", UUID.randomUUID(), departmentId);
    }

    ActorIdentity actAsDoctor() {
        return actAs("DOCTOR", doctorId, departmentId);
    }

    ConsultationEligibilitySnapshot eligibility(UUID doctor, UUID department, UUID room, boolean eligible) {
        return new ConsultationEligibilitySnapshot(doctor, true, eligible, true, department, department, true, true,
                room, true, true, department, "CONSULTATION", eligible, NOW);
    }
}
