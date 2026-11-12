package com.mediflow.clinical.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.application.dto.authority.ConsultationEligibilitySnapshot;
import com.mediflow.clinical.application.dto.authority.SchedulingAuthoritySnapshot;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.RegistrationCancellationCommand;
import com.mediflow.clinical.application.dto.request.RegistrationCreateRequest;
import com.mediflow.clinical.application.dto.request.PaymentLease;
import com.mediflow.clinical.application.dto.request.PaymentLockCommand;
import com.mediflow.clinical.application.dto.request.PaymentResolutionCommand;
import com.mediflow.clinical.application.dto.request.RevisionCommand;
import com.mediflow.clinical.application.dto.response.OutpatientRegistrationDTO;
import com.mediflow.clinical.application.dto.response.ReservationPaymentContext;
import com.mediflow.clinical.application.dto.response.RegistrationSummary;
import com.mediflow.clinical.application.event.DomainEventEnvelope;
import com.mediflow.clinical.application.event.OutpatientRegistrationFact;
import com.mediflow.clinical.application.event.OutpatientRegistrationFact.CancellationFact;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicalOutboxPort;
import com.mediflow.clinical.application.port.out.ConsultationSessionRepositoryPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.OutpatientRegistrationRepositoryPort;
import com.mediflow.clinical.application.port.out.PatientLookupPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.ConsultationSessionStatus;
import com.mediflow.clinical.domain.model.OutpatientReceptionMode;
import com.mediflow.clinical.domain.model.OutpatientPaymentOutcome;
import com.mediflow.clinical.domain.model.OutpatientPaymentOperationState;
import com.mediflow.clinical.domain.model.OutpatientRegistration;
import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.clinical.domain.model.OutpatientReservationStatus;
import com.mediflow.clinical.domain.model.OutpatientVisitType;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.common.exception.ResourceNotFoundException;

@Service
@ConditionalOnProperty(prefix = "mediflow.clinical.scheduling", name = "enabled", havingValue = "true")
public class OutpatientRegistrationApplicationService {
    private static final String COLLECTION_PATH = "/api/v2/outpatient-registrations";
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String PRICE_CODE = "OUTPATIENT_EXAM";

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public java.util.Optional<Appointment> lockScheduledAppointment(UUID id) {
        var visible = registrations.findById(id);
        if (visible.isEmpty()) return java.util.Optional.empty();
        lockSession(visible.get().sessionId());
        lockRegistration(id);
        return java.util.Optional.of(lockAppointment(id));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void startExamination(Appointment appointment, Instant at) {
        var before = lockRegistration(appointment.getAppointmentId());
        var session = lockSession(before.sessionId());
        var actor = requireHumanActor("DOCTOR");
        requireAssignedDoctor(actor, session);
        if (!before.patientId().equals(appointment.getPatientId())
                || !session.configuration().departmentId().equals(appointment.getDepartmentId())
                || !java.util.Objects.equals(before.reservation().clearanceId(), appointment.getExamClearanceId())) notFoundRegistration();
        if (before.status() == OutpatientRegistrationStatus.IN_EXAM
                && appointment.getStatus() == com.mediflow.clinical.domain.model.AppointmentStatus.IN_EXAM
                && before.reservation().status() == OutpatientReservationStatus.CONSUMED) return;
        var after = domain(() -> before.startExam(at));
        if (clinicIntake.enabled()) startRoomTicket(before.registrationId(), at);
        registrations.update(before, after, actor.accountId(), actor.staffId(), "EXAM_STARTED", at);
        sessions.updateCounts(session, adjustCounts(session, 0, 0, -1, 1, 0), at);
        appendFact(after, session, at);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void completeExamination(Appointment appointment, Instant at) {
        completeExamination(appointment, at, null);
    }

    /**
     * Ends the registration for the record's responsible doctor. After a handover that is the receiver, not the
     * doctor of the consultation session; {@code null} means the session doctor.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void completeExamination(Appointment appointment, Instant at, java.util.UUID responsibleDoctorId) {
        var before = lockRegistration(appointment.getAppointmentId());
        var session = lockSession(before.sessionId());
        var actor = requireHumanActor("DOCTOR");
        requireAssignedDoctor(actor, session,
                responsibleDoctorId == null ? session.configuration().doctorId() : responsibleDoctorId);
        if (!before.patientId().equals(appointment.getPatientId())) notFoundRegistration();
        var after = domain(() -> before.complete(at));
        if (clinicIntake.enabled()) completeRoomTicket(before.registrationId(), at);
        registrations.update(before, after, actor.accountId(), actor.staffId(), "COMPLETED", at);
        appendFact(after, session, at);
    }

    private static final int TEXT_REASON_MAX = 500;

    private final ConsultationSessionRepositoryPort sessions;
    private final OutpatientRegistrationRepositoryPort registrations;
    private final AppointmentRepositoryPort appointments;
    private final PatientLookupPort patients;
    private final SchedulingAuthorityPort authority;
    private final CurrentClinicalActorPort actors;
    private final ClinicalOutboxPort outbox;
    private final CorrelationIdProvider correlationIds;
    private final IdempotentCommandPort idempotency;
    private final Clock clock;
    private final com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort records;
    private final com.mediflow.clinical.application.port.out.FollowUpRecommendationRepositoryPort recommendations;
    private final ClinicIntakeSettings clinicIntake;
    private final org.springframework.beans.factory.ObjectProvider<com.mediflow.clinical.application.port.out.ClinicQueueRepositoryPort> clinicQueue;

    public OutpatientRegistrationApplicationService(ConsultationSessionRepositoryPort sessions,
            OutpatientRegistrationRepositoryPort registrations, AppointmentRepositoryPort appointments,
            PatientLookupPort patients, SchedulingAuthorityPort authority, CurrentClinicalActorPort actors,
            ClinicalOutboxPort outbox, CorrelationIdProvider correlationIds, IdempotentCommandPort idempotency,
            @Qualifier("clinicalSchedulingClock") Clock clock) {
        this(sessions, registrations, appointments, patients, authority, actors, outbox, correlationIds, idempotency,
                clock, null, null);
    }

    public OutpatientRegistrationApplicationService(ConsultationSessionRepositoryPort sessions,
            OutpatientRegistrationRepositoryPort registrations, AppointmentRepositoryPort appointments,
            PatientLookupPort patients, SchedulingAuthorityPort authority, CurrentClinicalActorPort actors,
            ClinicalOutboxPort outbox, CorrelationIdProvider correlationIds, IdempotentCommandPort idempotency,
            Clock clock, com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort records,
            com.mediflow.clinical.application.port.out.FollowUpRecommendationRepositoryPort recommendations) {
        this(sessions, registrations, appointments, patients, authority, actors, outbox, correlationIds, idempotency,
                clock, records, recommendations, new ClinicIntakeSettings(false));
    }

    public OutpatientRegistrationApplicationService(ConsultationSessionRepositoryPort sessions,
            OutpatientRegistrationRepositoryPort registrations, AppointmentRepositoryPort appointments,
            PatientLookupPort patients, SchedulingAuthorityPort authority, CurrentClinicalActorPort actors,
            ClinicalOutboxPort outbox, CorrelationIdProvider correlationIds, IdempotentCommandPort idempotency,
            Clock clock, com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort records,
            com.mediflow.clinical.application.port.out.FollowUpRecommendationRepositoryPort recommendations,
            ClinicIntakeSettings clinicIntake) {
        this(sessions, registrations, appointments, patients, authority, actors, outbox, correlationIds, idempotency,
                clock, records, recommendations, clinicIntake, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OutpatientRegistrationApplicationService(ConsultationSessionRepositoryPort sessions,
            OutpatientRegistrationRepositoryPort registrations, AppointmentRepositoryPort appointments,
            PatientLookupPort patients, SchedulingAuthorityPort authority, CurrentClinicalActorPort actors,
            ClinicalOutboxPort outbox, CorrelationIdProvider correlationIds, IdempotentCommandPort idempotency,
            @Qualifier("clinicalSchedulingClock") Clock clock,
            com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort records,
            com.mediflow.clinical.application.port.out.FollowUpRecommendationRepositoryPort recommendations,
            ClinicIntakeSettings clinicIntake,
            org.springframework.beans.factory.ObjectProvider<com.mediflow.clinical.application.port.out.ClinicQueueRepositoryPort> clinicQueue) {
        this.clinicIntake = clinicIntake;
        this.clinicQueue = clinicQueue;
        this.records = records;
        this.recommendations = recommendations;
        this.sessions = sessions;
        this.registrations = registrations;
        this.appointments = appointments;
        this.patients = patients;
        this.authority = authority;
        this.actors = actors;
        this.outbox = outbox;
        this.correlationIds = correlationIds;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    public IdempotentCommandPort.Result<OutpatientRegistrationDTO> create(
            RegistrationCreateRequest request, String idempotencyKey) {
        ActorIdentity actor = requireHumanActor("ADMIN", "NURSE");
        ConsultationSession visible = readSession(request.sessionId());
        requireActiveActor(actor, visible.configuration().departmentId());
        if (!patients.exists(request.patientId())) {
            throw new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Patient was not found");
        }
        requireEligible(visible.configuration());
        validateVisitType(request);
        requireIdempotencyKey(idempotencyKey);
        return idempotency.execute(actor.accountId().toString(), "POST", COLLECTION_PATH, idempotencyKey,
                fingerprint(request), OutpatientRegistrationDTO.class, 201,
                data -> COLLECTION_PATH + "/" + data.registrationId(), () -> {
                    ConsultationSession session = lockSession(request.sessionId());
                    validateCreateRequest(request, session, clock.instant());
                    if (registrations.existsActivePatientSession(request.patientId(), session.sessionId())) {
                        throw new DuplicateResourceException("ACTIVE_REGISTRATION_EXISTS",
                                "Patient already has an active registration for this session");
                    }
                    requireReasonForOpenVisit(request, session);
                    OutpatientRegistration.ReturnLink returnLink = validatedReturnLink(request);
                    Instant now = clock.instant();
                    UUID registrationId = UUID.randomUUID();
                    OutpatientRegistration registration = OutpatientRegistration.create(registrationId,
                            UUID.randomUUID(), request.patientId(), session, request.receptionMode(), now);
                    var config = session.configuration();
                    Appointment appointment = Appointment.createScheduled(registrationId, request.patientId(),
                            config.doctorId(), config.departmentId(), config.sessionDate(), config.startTime(),
                            request.reason(), now,
                            request.receptionMode() == OutpatientReceptionMode.AT_COUNTER ? now : null);
                    if (request.visitType() == OutpatientVisitType.FOLLOW_UP) {
                        registration = registration.asFollowUp(validateFollowUp(request, session));
                    }
                    if (returnLink != null) registration = registration.withReturnLink(returnLink);
                    appointments.save(appointment);
                    registrations.insert(registration, actor.accountId(), actor.staffId(), "CREATED", now);
                    ConsultationSession after = adjustCounts(session, 1, 0, 0, 0, -1);
                    sessions.updateCounts(session, after, now);
                    appendFact(registration, session, now);
                    return toDto(registration, session);
                });
    }

    public IdempotentCommandPort.Result<OutpatientRegistrationDTO> arrive(
            UUID registrationId, RevisionCommand request, String idempotencyKey) {
        OutpatientRegistration visible = readRegistration(registrationId);
        ConsultationSession session = readSession(visible.sessionId());
        ActorIdentity actor = requireHumanActor("ADMIN", "NURSE");
        requireActiveActor(actor, session.configuration().departmentId());
        requireIdempotencyKey(idempotencyKey);
        String path = COLLECTION_PATH + "/" + registrationId + "/arrival";
        return idempotency.execute(actor.accountId().toString(), "PUT", path, idempotencyKey,
                fingerprint(request), OutpatientRegistrationDTO.class, 200, ignored -> null, () -> {
                    ConsultationSession lockedSession = lockSession(session.sessionId());
                    OutpatientRegistration before = lockRegistration(registrationId);
                    Instant now = clock.instant();
                    OutpatientRegistration after = domain(() -> before.arrive(request.expectedRevision(), now));
                    Appointment appointment = lockAppointment(registrationId);
                    appointment.arriveScheduled(now);
                    appointments.save(appointment);
                    registrations.update(before, after, actor.accountId(), actor.staffId(), "ARRIVED", now);
                    appendFact(after, lockedSession, now);
                    return toDto(after, lockedSession);
                });
    }

    public IdempotentCommandPort.Result<OutpatientRegistrationDTO> cancel(
            UUID registrationId, RegistrationCancellationCommand request, String idempotencyKey) {
        OutpatientRegistration visible = readRegistration(registrationId);
        ConsultationSession session = readSession(visible.sessionId());
        ActorIdentity actor = requireHumanActor("ADMIN", "NURSE", "DOCTOR");
        if ("DOCTOR".equals(actor.role())) requireAssignedDoctor(actor, session);
        else requireActiveActor(actor, session.configuration().departmentId());
        requireIdempotencyKey(idempotencyKey);
        String path = COLLECTION_PATH + "/" + registrationId + "/cancellation";
        return idempotency.execute(actor.accountId().toString(), "PUT", path, idempotencyKey,
                fingerprint(request), OutpatientRegistrationDTO.class, 200, ignored -> null, () -> {
                    ConsultationSession lockedSession = lockSession(session.sessionId());
                    OutpatientRegistration before = lockRegistration(registrationId);
                    Instant now = clock.instant();
                    OutpatientRegistration after = domain(() -> before.cancel(request.expectedRevision(),
                            actor.accountId(), actor.staffId(), request.reason(), now));
                    if (clinicIntake.enabled()) cancelRoomTicket(registrationId, actor.accountId(), request.reason(), now);
                    Appointment appointment = lockAppointment(registrationId);
                    appointment.cancelScheduled(request.reason(), actor.accountId(), now);
                    appointments.save(appointment);
                    registrations.update(before, after, actor.accountId(), actor.staffId(), "CANCELLED", now);
                    ConsultationSession afterCounts;
                    if (before.reservation().status() == OutpatientReservationStatus.HELD) {
                        afterCounts = adjustCounts(lockedSession, -1, 0, 0, 0, 1);
                    } else {
                        afterCounts = adjustCounts(lockedSession, 0, 0, -1, 0, 1);
                    }
                    sessions.updateCounts(lockedSession, afterCounts, now);
                    appendFact(after, lockedSession, now);
                    return toDto(after, lockedSession);
                });
    }

    /** Gives an unpaid hold's seat back once its time is up; safe to run twice or against a paid registration. */
    @Transactional
    public void expireHeldReservation(UUID registrationId) {
        OutpatientRegistration visible = readRegistration(registrationId);
        ConsultationSession session = lockSession(visible.sessionId());
        OutpatientRegistration before = lockRegistration(registrationId);
        requireStillIn(session, before);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        OutpatientRegistration after = before.expire(now);
        if (after == before) return;
        registrations.update(before, after, null, null, "EXPIRED", now);
        sessions.updateCounts(session, adjustCounts(session, -1, 0, 0, 0, 1), now);
        appendFact(after, session, now);
    }

    public OutpatientRegistrationDTO get(UUID registrationId) {
        OutpatientRegistration registration = readRegistration(registrationId);
        ConsultationSession session = readSession(registration.sessionId());
        ActorIdentity actor = requireHumanActor("ADMIN", "NURSE", "DOCTOR", "PATIENT");
        if ("PATIENT".equals(actor.role())) {
            if (actor.patientId() == null || !actor.patientId().equals(registration.patientId())) notFoundRegistration();
        } else if ("DOCTOR".equals(actor.role())) {
            requireAssignedDoctor(actor, session);
        } else {
            requireActiveActor(actor, session.configuration().departmentId());
        }
        return toDto(registration, session);
    }

    public PageResult<OutpatientRegistrationDTO> mine(PageQuery page) {
        ActorIdentity actor = requireHumanActor("PATIENT");
        if (actor.patientId() == null) notFoundRegistration();
        return registrations.findByPatient(actor.patientId(), page).map(registration ->
                toDto(registration, readSession(registration.sessionId())));
    }

    public PageResult<RegistrationSummary> sessionRegistrations(UUID sessionId, PageQuery page) {
        ConsultationSession session = readSession(sessionId);
        ActorIdentity actor = actors.current();
        if (actor == null || actor.accountId() == null || actor.role() == null) {
            throw new ForbiddenOperationException("SCHEDULING_SCOPE_DENIED", "A verified clinical actor is required");
        }
        switch (actor.role()) {
            case "ADMIN", "NURSE" -> requireActiveActor(actor, session.configuration().departmentId());
            case "DOCTOR" -> requireSessionDoctorOrDepartmentHead(actor, session);
            default -> throw new ForbiddenOperationException("SCHEDULING_SCOPE_DENIED",
                    "Actor role cannot view session registrations");
        }
        var found = registrations.findBySession(sessionId, page);
        var rooms = roomNumbers(found.content().stream().map(OutpatientRegistration::registrationId).toList());
        return found.map(registration -> new RegistrationSummary(
                registration.registrationId(), registration.patientId(), registration.visitType(),
                registration.status(), registration.checkedInAt(), registration.queueNumber(),
                registration.status() == OutpatientRegistrationStatus.CONFIRMED
                        && registration.checkedInAt() != null && registration.reservation().clearanceId() != null,
                rooms.get(registration.registrationId())));
    }

    private java.util.Optional<com.mediflow.clinical.domain.model.ClinicQueueTicket> lockActiveTicket(UUID registrationId) {
        var queue = clinicQueue();
        if (queue == null) return java.util.Optional.empty();
        return queue.findActiveByRegistration(registrationId)
                .map(found -> queue.findByIdForUpdate(found.ticketId()).orElse(found));
    }

    private void startRoomTicket(UUID registrationId, Instant at) {
        var ticket = lockActiveTicket(registrationId).orElseThrow(() -> new BusinessRuleException("CLINICAL_ROOM_TICKET_REQUIRED",
                "The department desk must issue a room number before the exam starts"));
        clinicQueue().update(ticket, ticketStep(() -> ticket.startExam(at)));
    }

    /** An exam begun before the desk went live has no ticket and still finishes. */
    private void completeRoomTicket(UUID registrationId, Instant at) {
        lockActiveTicket(registrationId).filter(ticket -> ticket.status() == com.mediflow.clinical.domain.model.ClinicQueueTicketStatus.IN_EXAM)
                .ifPresent(ticket -> clinicQueue().update(ticket, ticket.complete(at)));
    }

    private void cancelRoomTicket(UUID registrationId, UUID actor, String reason, Instant at) {
        lockActiveTicket(registrationId).ifPresent(ticket -> clinicQueue().update(ticket, ticketStep(() -> ticket.cancel(actor, reason, at))));
    }

    private static com.mediflow.clinical.domain.model.ClinicQueueTicket ticketStep(
            java.util.function.Supplier<com.mediflow.clinical.domain.model.ClinicQueueTicket> step) {
        try {
            return step.get();
        } catch (IllegalStateException conflict) {
            throw new DuplicateResourceException(conflict.getMessage(), conflict.getMessage());
        }
    }

    private com.mediflow.clinical.application.port.out.ClinicQueueRepositoryPort clinicQueue() {
        return clinicQueue == null ? null : clinicQueue.getIfAvailable();
    }

    /** Empty while the clinic desk is off, which keeps the field off the wire. */
    private java.util.Map<UUID, Integer> roomNumbers(java.util.Collection<UUID> registrationIds) {
        var queue = clinicQueue();
        return queue == null ? java.util.Map.of() : queue.activeNumbers(registrationIds);
    }

    public ReservationPaymentContext paymentContext(UUID reservationId) {
        return registrations.findPaymentContext(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Reservation was not found"));
    }

    @Transactional
    public PaymentLease acquirePayment(UUID reservationId, PaymentLockCommand command) {
        if (!validBasis(command.basis(), command.quoteId())) throw new BusinessRuleException("INVALID_REQUEST", "Invalid eligibility basis");
        validatePaymentBinding(command);
        registrations.lockPaymentOperation(command.paymentOperationId());
        var existing = registrations.findPaymentOperationForUpdate(command.paymentOperationId());
        if (existing.isPresent()) {
            var receipt = existing.get();
            requireSameAcquireBinding(receipt, command, reservationId);
            if (receipt.state() == OutpatientPaymentOperationState.CLOSED_NOT_PAID) {
                throw new DuplicateResourceException("PAYMENT_OPERATION_CLOSED", "Payment operation was already closed");
            }
            return toLease(receipt);
        }

        OutpatientRegistration visible = readRegistration(command.registrationId());
        ConsultationSession session = lockSession(visible.sessionId());
        OutpatientRegistration before = lockRegistration(command.registrationId());
        requireStillIn(session, before);
        var reservation = before.reservation();
        if (!before.registrationId().equals(command.sourceId())
                || !before.registrationId().equals(command.careEpisodeId())
                || !before.patientId().equals(command.patientId())
                || !reservation.reservationId().equals(reservationId)) {
            throw new DuplicateResourceException("IDEMPOTENCY_CONFLICT", "Payment operation source binding does not match the reservation");
        }
        if (reservation.revision() != command.expectedReservationRevision()) {
            throw new DuplicateResourceException("REVISION_CONFLICT", "Reservation revision changed");
        }
        // Match persisted timestamp precision so a replay returns the identical lease.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (before.status() != OutpatientRegistrationStatus.AWAITING_PAYMENT
                || reservation.status() != OutpatientReservationStatus.HELD
                || !now.isBefore(reservation.expiresAt()) || sessionHasEnded(session, now)) {
            throw new DuplicateResourceException("RESERVATION_NOT_PAYABLE", "Reservation is no longer payable");
        }
        long fenceToken = registrations.allocateFenceToken(before.registrationId());
        OutpatientRegistration after = domain(() -> before.acquirePayment(
                command.expectedReservationRevision(), command.paymentOperationId(), fenceToken, now));
        int leaseRevision = after.reservation().revision();
        var operation = new OutpatientRegistrationRepositoryPort.PaymentOperationReceipt(
                command.paymentOperationId(), command.paymentRequestId(), before.registrationId(), reservation.reservationId(),
                before.patientId(), command.sourceType(), command.sourceId(), command.careEpisodeId(),
                command.expectedReservationRevision(), leaseRevision, fenceToken,
                OutpatientPaymentOperationState.PAYMENT_LOCKED, now, now.plusSeconds(120), null, null, null, null, command.basis(), command.quoteId());
        registrations.update(before, after, null, null, "PAYMENT_LOCKED", now);
        sessions.updateCounts(session, adjustCounts(session, -1, 1, 0, 0, 0), now);
        registrations.insertPaymentOperation(operation, now);
        appendFact(after, session, now);
        return toLease(operation);
    }

    @Transactional
    public OutpatientRegistrationDTO resolvePayment(PaymentResolutionCommand command) {
        validateResolution(command);
        registrations.lockPaymentOperation(command.paymentOperationId());
        var operation = registrations.findPaymentOperationForUpdate(command.paymentOperationId())
                .orElseThrow(() -> new ResourceNotFoundException("PAYMENT_OPERATION_NOT_FOUND", "Payment operation was not found"));
        requireSameResolutionBinding(operation, command);
        OutpatientRegistration visible = readRegistration(operation.registrationId());
        ConsultationSession session = lockSession(visible.sessionId());
        OutpatientRegistration before = lockRegistration(operation.registrationId());

        if (operation.state() != OutpatientPaymentOperationState.PAYMENT_LOCKED) {
            if (isExactTerminalReplay(operation, command)) return toDto(before, session);
            throw new DuplicateResourceException("PAYMENT_OPERATION_CLOSED", "Payment operation already has another terminal result");
        }
        if (before.reservation().status() != OutpatientReservationStatus.PAYMENT_LOCKED
                || !command.paymentOperationId().equals(before.reservation().paymentOperationId())
                || !command.fenceToken().equals(before.reservation().fenceToken())) {
            throw new DuplicateResourceException("PAYMENT_OPERATION_CLOSED", "Payment lease is no longer current");
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        OutpatientRegistration after;
        OutpatientRegistrationRepositoryPort.PaymentOperationReceipt terminal;
        Appointment appointment = lockAppointment(before.registrationId());
        ConsultationSession afterCounts;
        if (command.outcome() == OutpatientPaymentOutcome.COMPLETED) {
            boolean ended = sessionHasEnded(session, now);
            int queue = ended ? 0 : clinicIntake.enabled()
                    ? registrations.allocateDepartmentQueueNumber(session.configuration().departmentId(), session.configuration().sessionDate())
                    : registrations.allocateQueueNumber(session.sessionId());
            after = domain(() -> before.resolveCompleted(command.paymentOperationId(), command.fenceToken(), queue,
                    command.transactionId(), command.clearanceId(), command.completedAt(), ended, command.basis()));
            if (!ended) appointment.confirmScheduledPayment(command.clearanceId(), command.completedAt());
            terminal = new OutpatientRegistrationRepositoryPort.PaymentOperationReceipt(operation.paymentOperationId(),
                    operation.paymentRequestId(), operation.registrationId(), operation.reservationId(), operation.patientId(),
                    operation.sourceType(), operation.sourceId(), operation.careEpisodeId(), operation.expectedReservationRevision(),
                    operation.leaseReservationRevision(), operation.fenceToken(), OutpatientPaymentOperationState.COMPLETED,
                    operation.lockedAt(), operation.reconcileAfter(), command.transactionId(), command.clearanceId(),
                    command.completedAt(), null, operation.basis(), operation.quoteId());
            afterCounts = ended ? session : adjustCounts(session, 0, -1, 1, 0, 0);
        } else {
            after = domain(() -> before.resolveClosedNotPaid(command.paymentOperationId(), command.fenceToken(),
                    now, sessionHasEnded(session, now)));
            boolean releasedToHeld = after.reservation().status() == OutpatientReservationStatus.HELD;
            terminal = new OutpatientRegistrationRepositoryPort.PaymentOperationReceipt(operation.paymentOperationId(),
                    operation.paymentRequestId(), operation.registrationId(), operation.reservationId(), operation.patientId(),
                    operation.sourceType(), operation.sourceId(), operation.careEpisodeId(), operation.expectedReservationRevision(),
                    operation.leaseReservationRevision(), operation.fenceToken(), OutpatientPaymentOperationState.CLOSED_NOT_PAID,
                    operation.lockedAt(), operation.reconcileAfter(), null, null, null, "RESERVATION_RECONCILIATION", operation.basis(), operation.quoteId());
            afterCounts = releasedToHeld ? adjustCounts(session, 1, -1, 0, 0, 0)
                    : adjustCounts(session, 0, -1, 0, 0, 1);
        }
        registrations.update(before, after, null, null, command.outcome().name(), now);
        appointments.save(appointment);
        sessions.updateCounts(session, afterCounts, now);
        registrations.updatePaymentOperation(operation, terminal, now);
        appendFact(after, session, now);
        return toDto(after, session);
    }

    @Transactional
    public OutpatientRegistrationDTO consumeEligibility(com.mediflow.clinical.application.event.OutpatientEligibilityFact fact) {
        if (fact == null || fact.paymentOperationId() == null || fact.patientId() == null || fact.registrationId() == null
                || !"EXAM".equals(fact.sourceType()) || !"OUTPATIENT_VISIT".equals(fact.careEpisodeType())
                || !fact.registrationId().equals(fact.sourceId()) || !fact.registrationId().equals(fact.careEpisodeId())
                || !"VND".equals(fact.currency()) || !validBasis(fact.basis(), fact.quoteId()))
            throw new BusinessRuleException("INVALID_REQUEST", "Eligibility source binding is invalid");
        java.math.BigDecimal amount;
        try { amount = new java.math.BigDecimal(fact.amount()); }
        catch (RuntimeException invalid) { throw new BusinessRuleException("INVALID_REQUEST", "Eligibility amount is invalid"); }
        if ("ZERO_LIABILITY".equals(fact.basis()) ? amount.signum() != 0 : amount.signum() <= 0)
            throw new BusinessRuleException("INVALID_REQUEST", "Eligibility amount does not match its basis");
        registrations.lockPaymentOperation(fact.paymentOperationId());
        var receipt = registrations.findPaymentOperationForUpdate(fact.paymentOperationId()).orElseThrow(() ->
                new ResourceNotFoundException("PAYMENT_OPERATION_NOT_FOUND", "Eligibility lease has not been recorded"));
        if (!fact.patientId().equals(receipt.patientId()) || !fact.sourceId().equals(receipt.sourceId())
                || !fact.careEpisodeId().equals(receipt.careEpisodeId()) || !fact.sourceType().equals(receipt.sourceType()))
            throw new DuplicateResourceException("IDEMPOTENCY_CONFLICT", "Eligibility source differs from its lease");
        return resolvePayment(new PaymentResolutionCommand(fact.paymentOperationId(), fact.paymentRequestId(),
                fact.registrationId(), fact.reservationId(), fact.fenceToken(), OutpatientPaymentOutcome.COMPLETED,
                fact.transactionId(), fact.clearanceId(), fact.completedAt(), fact.basis(), fact.quoteId()));
    }

    private void validateCreateRequest(RegistrationCreateRequest request, ConsultationSession session, Instant now) {
        validateVisitType(request);
        if (session.revision() != request.expectedSessionRevision()) {
            throw new DuplicateResourceException("REVISION_CONFLICT", "Session revision changed");
        }
        if (session.status() != ConsultationSessionStatus.OPEN) {
            throw new DuplicateResourceException("SESSION_NOT_OPEN", "Session is not accepting new reservations");
        }
        if (session.counts().availableCount() < 1) {
            throw new DuplicateResourceException("SESSION_CAPACITY_EXHAUSTED", "Session has no available seats");
        }
        var config = session.configuration();
        LocalDate clinicDate = now.atZone(CLINIC_ZONE).toLocalDate();
        LocalTime clinicTime = now.atZone(CLINIC_ZONE).toLocalTime();
        if (config.sessionDate().isBefore(clinicDate) || config.sessionDate().equals(clinicDate)
                && !clinicTime.isBefore(config.endTime())) {
            throw new BusinessRuleException("RESERVATION_NOT_PAYABLE", "Past or ended sessions cannot accept reservations");
        }
    }

    private static void validateVisitType(RegistrationCreateRequest request) {
        if (request.visitType() == OutpatientVisitType.NEW_VISIT) {
            if (request.previousRegistrationId() != null || request.followUpRecommendationId() != null
                    || request.replacementReason() != null || request.patientAgreedToDoctorReplacement() != null)
                throw new BusinessRuleException("INVALID_REQUEST", "NEW_VISIT cannot carry follow-up fields");
        } else if (request.visitType() != OutpatientVisitType.FOLLOW_UP || request.previousRegistrationId() == null
                || request.followUpRecommendationId() == null && (request.reason() == null || request.reason().isBlank())) {
            throw new BusinessRuleException("INVALID_REQUEST", "Follow-up requires a completed source and a recommendation or reason");
        }
        if (request.visitType() == OutpatientVisitType.FOLLOW_UP && (request.returnSourceRecordId() != null || request.returnReason() != null)) {
            throw new BusinessRuleException("INVALID_REQUEST", "FOLLOW_UP names its source through the previous registration");
        }
        if (request.independentVisitReason() != null && request.independentVisitReason().length() > TEXT_REASON_MAX) {
            throw new BusinessRuleException("INVALID_REQUEST", "Independent visit reason is too long");
        }
        boolean hasReason = request.returnReason() != null && !request.returnReason().isBlank()
                && request.returnReason().length() <= TEXT_REASON_MAX;
        if (request.returnSourceRecordId() == null ? request.returnReason() != null : !hasReason) {
            throw new BusinessRuleException("RETURN_SOURCE_INVALID", "A return visit needs both its source record and a reason");
        }
    }

    /** A second visit in a department where one is still open is usually a mistake, so it must be explained. */
    private void requireReasonForOpenVisit(RegistrationCreateRequest request, ConsultationSession target) {
        boolean openInDepartment = registrations.findOpenVisitsByPatient(request.patientId()).stream()
                .anyMatch(visit -> visit.departmentId().equals(target.configuration().departmentId()));
        if (openInDepartment && (request.independentVisitReason() == null || request.independentVisitReason().isBlank())) {
            throw new DuplicateResourceException("OPEN_VISIT_EXISTS", "Patient has an open visit in this department; give a reason to register another");
        }
    }

    private OutpatientRegistration.ReturnLink validatedReturnLink(RegistrationCreateRequest request) {
        String independent = request.independentVisitReason() == null || request.independentVisitReason().isBlank()
                ? null : request.independentVisitReason();
        if (request.returnSourceRecordId() == null) {
            return independent == null ? null : new OutpatientRegistration.ReturnLink(null, null, independent);
        }
        var source = records.findById(request.returnSourceRecordId()).orElse(null);
        if (source == null || !request.patientId().equals(source.getPatientId())
                || source.getStatus() == com.mediflow.clinical.domain.model.MedicalRecordStatus.OPEN) {
            throw new BusinessRuleException("RETURN_SOURCE_INVALID", "The return source must be a finished record of this patient");
        }
        return new OutpatientRegistration.ReturnLink(source.getRecordId(), request.returnReason(), independent);
    }

    /** The earlier record a new examination refers to; only the link is carried, never its content. */
    public UUID previousRecordIdFor(UUID registrationId) {
        var registration = registrations.findById(registrationId).orElse(null);
        if (registration == null) return null;
        if (registration.returnLink() != null && registration.returnLink().sourceRecordId() != null) {
            return registration.returnLink().sourceRecordId();
        }
        if (registration.followUp() == null) return null;
        return records.findByAppointmentId(registration.followUp().previousRegistrationId())
                .map(com.mediflow.clinical.domain.model.MedicalRecord::getRecordId).orElse(null);
    }

    private OutpatientRegistration.FollowUpLink validateFollowUp(RegistrationCreateRequest request, ConsultationSession target) {
        var previous = readRegistration(request.previousRegistrationId());
        var sourceSession = readSession(previous.sessionId());
        var sourceAppointment = appointments.findById(previous.registrationId()).orElseThrow(() ->
                new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Follow-up source was not found"));
        var record = records.findByAppointmentId(previous.registrationId()).orElseThrow(() ->
                new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Completed source record was not found"));
        if (previous.status() != OutpatientRegistrationStatus.COMPLETED
                || sourceAppointment.getCareContractVersion() != 2
                || sourceAppointment.getStatus() != com.mediflow.clinical.domain.model.AppointmentStatus.COMPLETED
                || record.getStatus() != com.mediflow.clinical.domain.model.MedicalRecordStatus.COMPLETED
                || !request.patientId().equals(previous.patientId())
                || !request.patientId().equals(record.getPatientId())
                || !request.patientId().equals(sourceAppointment.getPatientId())
                || !sourceSession.configuration().departmentId().equals(target.configuration().departmentId())
                || !record.getDepartmentId().equals(target.configuration().departmentId())
                || !sourceAppointment.getDepartmentId().equals(record.getDepartmentId())
                || !sourceAppointment.getDoctorId().equals(record.getDoctorId())
                || !sourceSession.configuration().doctorId().equals(record.getDoctorId())) notFoundRegistration();
        if (request.followUpRecommendationId() != null) {
            var advice = recommendations.findBySourceRecordId(record.getRecordId()).orElseThrow(() ->
                    new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Follow-up recommendation was not found"));
            if (!request.followUpRecommendationId().equals(advice.recommendationId())
                    || !previous.registrationId().equals(advice.sourceRegistrationId())
                    || !request.patientId().equals(advice.patientId())
                    || !target.configuration().departmentId().equals(advice.departmentId())) notFoundRegistration();
        }
        boolean replaced = !record.getDoctorId().equals(target.configuration().doctorId());
        if (replaced && (request.replacementReason() == null || request.replacementReason().isBlank()
                || request.replacementReason().length() > 1000 || !Boolean.TRUE.equals(request.patientAgreedToDoctorReplacement()))
                || !replaced && (request.replacementReason() != null || request.patientAgreedToDoctorReplacement() != null)) {
            throw new BusinessRuleException("FOLLOW_UP_DOCTOR_REPLACEMENT_INVALID", "Doctor replacement requires reason and patient consent");
        }
        return new OutpatientRegistration.FollowUpLink(previous.registrationId(), request.followUpRecommendationId(),
                request.replacementReason(), request.patientAgreedToDoctorReplacement(), request.reason());
    }

    public PageResult<com.mediflow.clinical.application.dto.response.FollowUpSourceSummary> followUpSources(UUID patientId, PageQuery page) {
        var actor = requireHumanActor("ADMIN", "NURSE");
        var sources = registrations.findFollowUpSources(patientId, page);
        return sources.map(source -> {
            requireActiveActor(actor, source.departmentId());
            var advice = recommendations.findBySourceRecordId(source.recordId())
                    .map(FollowUpRecommendationApplicationService::toDto).orElse(null);
            return new com.mediflow.clinical.application.dto.response.FollowUpSourceSummary(source.registrationId(),
                    source.recordId(), source.patientId(), source.departmentId(), source.doctorId(), source.completedAt(), advice);
        });
    }

    SchedulingAuthoritySnapshot requireActiveActor(ActorIdentity actor, UUID departmentId) {
        SchedulingAuthoritySnapshot snapshot = authority.authority(actor.accountId(), departmentId,
                correlationIds.currentOrCreate().toString());
        if (!actor.role().equals(snapshot.currentRole()) || !Boolean.TRUE.equals(snapshot.accountExists())
                || !Boolean.TRUE.equals(snapshot.accountActive()) || !Boolean.TRUE.equals(snapshot.departmentActive())) {
            throw new ForbiddenOperationException("SCHEDULING_SCOPE_DENIED", "Actor has no current scheduling authority");
        }
        if (!"ADMIN".equals(actor.role()) && (actor.staffId() == null
                || !actor.staffId().equals(snapshot.staffId()) || !Boolean.TRUE.equals(snapshot.staffActive())
                || !departmentId.equals(snapshot.departmentId()))) {
            throw new ForbiddenOperationException("SCHEDULING_SCOPE_DENIED", "Actor staff association is not active");
        }
        return snapshot;
    }

    private void requireSessionDoctorOrDepartmentHead(ActorIdentity actor, ConsultationSession session) {
        if (actor.staffId() != null && actor.staffId().equals(session.configuration().doctorId())) {
            requireAssignedDoctor(actor, session);
            return;
        }
        SchedulingAuthoritySnapshot snapshot = requireActiveActor(actor, session.configuration().departmentId());
        if (!Boolean.TRUE.equals(snapshot.canManageSchedules())) notFoundRegistration();
    }

    void requireAssignedDoctor(ActorIdentity actor, ConsultationSession session) {
        requireAssignedDoctor(actor, session, session.configuration().doctorId());
    }

    private void requireAssignedDoctor(ActorIdentity actor, ConsultationSession session, java.util.UUID doctorId) {
        if (actor.staffId() == null || !actor.staffId().equals(doctorId)) {
            notFoundRegistration();
        }
        var snapshot = authority.currentDoctorAuthority(actor.accountId(), correlationIds.currentOrCreate().toString());
        if (!Boolean.TRUE.equals(snapshot.accountExists()) || !Boolean.TRUE.equals(snapshot.accountActive())
                || !"DOCTOR".equals(snapshot.currentRole()) || !actor.staffId().equals(snapshot.staffId())
                || !Boolean.TRUE.equals(snapshot.staffActive())
                || !session.configuration().departmentId().equals(snapshot.departmentId())
                || !Boolean.TRUE.equals(snapshot.departmentActive())) {
            notFoundRegistration();
        }
    }

    void requireEligible(ConsultationSession.Configuration config) {
        ConsultationEligibilitySnapshot snapshot = authority.consultationEligibility(config.doctorId(),
                config.departmentId(), config.roomId(), correlationIds.currentOrCreate().toString());
        if (!Boolean.TRUE.equals(snapshot.eligible()) || !Boolean.TRUE.equals(snapshot.doctorActive())
                || !Boolean.TRUE.equals(snapshot.eligibleDoctor()) || !Boolean.TRUE.equals(snapshot.departmentActive())
                || !Boolean.TRUE.equals(snapshot.roomActive()) || !"CONSULTATION".equals(snapshot.roomType())) {
            throw new BusinessRuleException("SCHEDULE_INELIGIBLE", "Session doctor, department, or room is not currently eligible");
        }
    }

    private static void validatePaymentBinding(PaymentLockCommand command) {
        if (command == null || command.paymentOperationId() == null || command.paymentRequestId() == null
                || command.registrationId() == null || command.patientId() == null
                || !"EXAM".equals(command.sourceType()) || command.sourceId() == null
                || command.careEpisodeId() == null || command.expectedReservationRevision() < 1) {
            throw new BusinessRuleException("INVALID_REQUEST", "Payment lock binding is incomplete");
        }
    }

    private static void validateResolution(PaymentResolutionCommand command) {
        if (command == null || command.paymentOperationId() == null || command.paymentRequestId() == null
                || command.registrationId() == null || command.reservationId() == null || command.fenceToken() == null
                || command.fenceToken() < 1 || command.outcome() == null) {
            throw new BusinessRuleException("INVALID_REQUEST", "Payment resolution binding is incomplete");
        }
        boolean completed = command.outcome() == OutpatientPaymentOutcome.COMPLETED;
        if (completed != (validBasis(command.basis(), command.quoteId()) && ("ZERO_LIABILITY".equals(command.basis()) ? command.transactionId() == null : command.transactionId() != null) && command.clearanceId() != null && command.completedAt() != null)
                || !completed && (command.transactionId() != null || command.clearanceId() != null || command.completedAt() != null)) {
            throw new BusinessRuleException("INVALID_REQUEST", "Payment resolution receipt does not match its outcome");
        }
    }

    private static boolean validBasis(String basis, UUID quoteId) {
        return "PATIENT_PAYMENT".equals(basis) || "ZERO_LIABILITY".equals(basis) && quoteId != null;
    }

    private static void requireSameAcquireBinding(
            OutpatientRegistrationRepositoryPort.PaymentOperationReceipt receipt,
            PaymentLockCommand command, UUID reservationId) {
        if (!receipt.paymentRequestId().equals(command.paymentRequestId())
                || !receipt.registrationId().equals(command.registrationId())
                || !receipt.reservationId().equals(reservationId)
                || !receipt.patientId().equals(command.patientId())
                || !receipt.sourceType().equals(command.sourceType())
                || !receipt.sourceId().equals(command.sourceId())
                || !receipt.careEpisodeId().equals(command.careEpisodeId())
                || receipt.expectedReservationRevision() != command.expectedReservationRevision()
                || !java.util.Objects.equals(receipt.basis(), command.basis()) || !java.util.Objects.equals(receipt.quoteId(), command.quoteId())) {
            throw new DuplicateResourceException("IDEMPOTENCY_CONFLICT", "Payment operation id is already bound to another command");
        }
    }

    private static void requireSameResolutionBinding(
            OutpatientRegistrationRepositoryPort.PaymentOperationReceipt receipt,
            PaymentResolutionCommand command) {
        if (!receipt.paymentRequestId().equals(command.paymentRequestId())
                || !receipt.registrationId().equals(command.registrationId())
                || !receipt.reservationId().equals(command.reservationId())
                || !command.fenceToken().equals(receipt.fenceToken())
                || !java.util.Objects.equals(receipt.basis(), command.basis()) || !java.util.Objects.equals(receipt.quoteId(), command.quoteId())) {
            throw new DuplicateResourceException("IDEMPOTENCY_CONFLICT", "Payment operation receipt binding does not match");
        }
    }

    private static boolean isExactTerminalReplay(
            OutpatientRegistrationRepositoryPort.PaymentOperationReceipt receipt,
            PaymentResolutionCommand command) {
        if (command.outcome() == OutpatientPaymentOutcome.COMPLETED) {
            return receipt.state() == OutpatientPaymentOperationState.COMPLETED
                    && java.util.Objects.equals(command.transactionId(), receipt.transactionId())
                    && command.clearanceId().equals(receipt.clearanceId())
                    && command.completedAt().equals(receipt.completedAt());
        }
        return receipt.state() == OutpatientPaymentOperationState.CLOSED_NOT_PAID
                && command.transactionId() == null && command.clearanceId() == null && command.completedAt() == null;
    }

    private static PaymentLease toLease(OutpatientRegistrationRepositoryPort.PaymentOperationReceipt receipt) {
        if (receipt.leaseReservationRevision() == null || receipt.fenceToken() == null
                || receipt.lockedAt() == null || receipt.reconcileAfter() == null) {
            throw new IllegalStateException("Payment operation lease receipt is incomplete");
        }
        return new PaymentLease(receipt.paymentOperationId(), receipt.reservationId(),
                receipt.leaseReservationRevision(), receipt.fenceToken(), receipt.lockedAt(),
                receipt.reconcileAfter(), receipt.basis(), receipt.quoteId());
    }

    private boolean sessionHasEnded(ConsultationSession session, Instant now) {
        var config = session.configuration();
        LocalDate clinicDate = now.atZone(CLINIC_ZONE).toLocalDate();
        LocalTime clinicTime = now.atZone(CLINIC_ZONE).toLocalTime();
        return clinicDate.isAfter(config.sessionDate())
                || clinicDate.equals(config.sessionDate()) && !clinicTime.isBefore(config.endTime());
    }

    private ConsultationSession readSession(UUID id) {
        return sessions.findById(id).orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Session was not found"));
    }

    private ConsultationSession lockSession(UUID id) {
        return sessions.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Session was not found"));
    }

    private OutpatientRegistration readRegistration(UUID id) {
        return registrations.findById(id).orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Registration was not found"));
    }

    /** A reschedule can commit between reading the registration and locking its session. */
    private static void requireStillIn(ConsultationSession session, OutpatientRegistration registration) {
        if (!registration.sessionId().equals(session.sessionId())) {
            throw new DuplicateResourceException("REVISION_CONFLICT", "Registration moved to another session");
        }
    }

    private OutpatientRegistration lockRegistration(UUID id) {
        return registrations.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Registration was not found"));
    }

    private Appointment lockAppointment(UUID id) {
        return appointments.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Registration was not found"));
    }

    ActorIdentity requireHumanActor(String... roles) {
        ActorIdentity actor = actors.current();
        if (actor == null || actor.accountId() == null || actor.role() == null) {
            throw new ForbiddenOperationException("SCHEDULING_SCOPE_DENIED", "A verified clinical actor is required");
        }
        for (String role : roles) if (role.equals(actor.role())) return actor;
        throw new ForbiddenOperationException("SCHEDULING_SCOPE_DENIED", "Actor role cannot use this registration command");
    }

    static ConsultationSession adjustCounts(ConsultationSession session, int held, int locked,
            int confirmed, int consumed, int available) {
        var counts = session.counts();
        return session.withCounts(new ConsultationSession.Counts(counts.heldCount() + held,
                counts.paymentLockedCount() + locked, counts.confirmedCount() + confirmed,
                counts.consumedCount() + consumed, counts.availableCount() + available));
    }

    OutpatientRegistrationDTO toDto(OutpatientRegistration registration, ConsultationSession session) {
        var config = session.configuration();
        boolean ready = registration.status() == OutpatientRegistrationStatus.CONFIRMED
                && registration.checkedInAt() != null && registration.reservation().clearanceId() != null;
        var reservation = registration.reservation();
        UUID recordId = null;
        com.mediflow.clinical.application.dto.response.FollowUpRecommendationDTO advice = null;
        if (registration.status() == OutpatientRegistrationStatus.COMPLETED && records != null) {
            var record = records.findByAppointmentId(registration.registrationId()).orElse(null);
            if (record != null && record.getStatus() == com.mediflow.clinical.domain.model.MedicalRecordStatus.COMPLETED
                    && registration.patientId().equals(record.getPatientId())
                    && config.doctorId().equals(record.getDoctorId()) && config.departmentId().equals(record.getDepartmentId())) {
                recordId = record.getRecordId();
                if (recommendations != null) advice = recommendations.findBySourceRecordId(recordId)
                        .filter(r -> r.patientId().equals(registration.patientId())
                                && r.sourceRegistrationId().equals(registration.registrationId())
                                && r.departmentId().equals(config.departmentId()) && r.preferredDoctorId().equals(config.doctorId()))
                        .map(FollowUpRecommendationApplicationService::toDto).orElse(null);
            }
        }
        return new OutpatientRegistrationDTO(registration.registrationId(), registration.revision(),
                registration.patientId(), config.doctorId(), config.departmentId(), config.roomId(), session.sessionId(),
                config.sessionDate(), config.startTime(), config.endTime(), registration.receptionMode(),
                registration.visitType(), registration.followUp() == null ? null : registration.followUp().previousRegistrationId(),
                registration.status(), reservation.reservationId(),
                reservation.revision(), reservation.status(), reservation.expiresAt(), registration.checkedInAt(),
                registration.queueNumber(), ready, registration.createdAt(), registration.updatedAt(), recordId, advice,
                roomNumbers(java.util.List.of(registration.registrationId())).get(registration.registrationId()));
    }

    private void appendFact(OutpatientRegistration registration, ConsultationSession session, Instant changedAt) {
        appendFact(registration, session, changedAt, null);
    }

    void appendFact(OutpatientRegistration registration, ConsultationSession session, Instant changedAt,
            OutpatientRegistrationFact.RescheduleFact reschedule) {
        var config = session.configuration();
        var reservation = registration.reservation();
        CancellationFact cancellation = registration.cancellation() == null ? null : new CancellationFact(
                registration.cancellation().actorAccountId(), registration.cancellation().actorStaffId(),
                registration.cancellation().reason(), registration.cancellation().cancelledAt());
        OutpatientRegistrationFact fact = new OutpatientRegistrationFact(registration.registrationId(),
                registration.revision(), registration.patientId(), config.doctorId(), config.departmentId(),
                session.sessionId(), config.roomId(), config.sessionDate(), config.startTime(), config.endTime(),
                registration.visitType(), reservation.reservationId(), reservation.status(), registration.status(),
                registration.checkedInAt(), registration.queueNumber(), reservation.expiresAt(), registration.visitType() == OutpatientVisitType.FOLLOW_UP ? "OUTPATIENT_FOLLOW_UP" : PRICE_CODE,
                "EXAM", registration.registrationId(), "OUTPATIENT_VISIT", registration.registrationId(),
                cancellation, changedAt, reschedule);
        String correlationId = correlationIds.currentOrCreate().toString();
        DomainEventEnvelope<OutpatientRegistrationFact> event = new DomainEventEnvelope<>(UUID.randomUUID(),
                "outpatient.registration.changed", 1, changedAt, correlationId, "clinical-service", fact);
        outbox.appendRawOutpatientEvent(event);
    }

    private static OutpatientRegistration domain(java.util.function.Supplier<OutpatientRegistration> operation) {
        try {
            return operation.get();
        } catch (IllegalStateException conflict) {
            String code = conflict.getMessage() == null ? "CLINICAL_INVALID_STATUS_TRANSITION" : conflict.getMessage();
            if ("REVISION_CONFLICT".equals(code) || "RESERVATION_NOT_PAYABLE".equals(code)
                    || "PAYMENT_OPERATION_IN_PROGRESS".equals(code) || "PAYMENT_OPERATION_CLOSED".equals(code)) {
                throw new DuplicateResourceException(code, code);
            }
            throw new BusinessRuleException(code, code);
        } catch (IllegalArgumentException invalid) {
            throw new BusinessRuleException("INVALID_REQUEST", invalid.getMessage());
        }
    }

    private static void requireIdempotencyKey(String key) {
        if (key == null || key.isEmpty() || key.length() > 120
                || !key.chars().allMatch(value -> value >= 33 && value <= 126)) {
            throw new BusinessRuleException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be 1-120 printable ASCII characters without spaces");
        }
    }

    private static String fingerprint(Object command) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(command.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void notFoundRegistration() {
        throw new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Registration was not found");
    }
}
