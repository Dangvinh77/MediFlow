package com.mediflow.clinical.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.command.FinancialClearanceCommand;
import com.mediflow.clinical.application.dto.command.FinancialExecutionStartRequest;
import com.mediflow.clinical.application.dto.request.CompleteRecordRequest;
import com.mediflow.clinical.application.dto.request.CreateAdmissionReferralRequest;
import com.mediflow.clinical.application.dto.request.EmergencyOverrideRequest;
import com.mediflow.clinical.application.dto.request.StartExamRequest;
import com.mediflow.clinical.application.dto.response.AdmissionReferralDTO;
import com.mediflow.clinical.application.dto.response.AppointmentDTO;
import com.mediflow.clinical.application.dto.response.MedicalRecordDTO;
import com.mediflow.clinical.application.event.AdmissionRequestedPayload;
import com.mediflow.clinical.application.event.AppointmentExamCancelledPayload;
import com.mediflow.clinical.application.event.AppointmentStatusChangedV2Payload;
import com.mediflow.clinical.application.event.DomainEventEnvelope;
import com.mediflow.clinical.application.event.MedicalRecordCompletedPayload;
import com.mediflow.clinical.application.event.MedicalRecordCreatedV2Payload;
import com.mediflow.clinical.application.mapper.ClinicalDtoMapper;
import com.mediflow.clinical.application.port.in.CompleteMedicalRecordUseCase;
import com.mediflow.clinical.application.port.in.CancelCareFinanceExamUseCase;
import com.mediflow.clinical.application.port.in.ManageExamGateUseCase;
import com.mediflow.clinical.application.port.in.ReactToFinancialClearanceUseCase;
import com.mediflow.clinical.application.port.out.AdmissionReferralRepositoryPort;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicalOutboxPort;
import com.mediflow.clinical.application.port.out.ClinicalLabOrderRepositoryPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.EmergencyOverrideRepositoryPort;
import com.mediflow.clinical.application.port.out.ExamClearanceRepositoryPort;
import com.mediflow.clinical.application.port.out.FinancialExecutionAuthorityPort;
import com.mediflow.clinical.application.port.out.ExamPricePolicyPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.LabOrderLifecyclePort;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.domain.exception.ClinicalAppointmentNotFoundException;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.domain.exception.ClinicalRecordNotFoundException;
import com.mediflow.clinical.domain.model.AdmissionPriority;
import com.mediflow.clinical.domain.model.AdmissionReferral;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.domain.model.CareEpisodeType;
import com.mediflow.clinical.domain.model.EmergencyOverride;
import com.mediflow.clinical.domain.model.ExamClearance;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.clinical.domain.model.RecordDisposition;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ForbiddenOperationException;

public class ClinicalCareFinanceApplicationService
        implements ManageExamGateUseCase, CompleteMedicalRecordUseCase, ReactToFinancialClearanceUseCase,
        CancelCareFinanceExamUseCase {

    private static final String APPOINTMENT_STATUS_CHANGED = "appointment.status.changed";
    private static final String APPOINTMENT_EXAM_CANCELLED = "appointment.exam.cancelled";
    private static final String MEDICAL_RECORD_CREATED = "medicalrecord.created";
    private static final String MEDICAL_RECORD_COMPLETED = "medicalrecord.completed";
    private static final String ADMISSION_REQUESTED = "admission.requested";

    private final AppointmentRepositoryPort appointments;
    private final MedicalRecordRepositoryPort records;
    private final ExamClearanceRepositoryPort clearances;
    private final FinancialExecutionAuthorityPort executionAuthority;
    private final EmergencyOverrideRepositoryPort overrides;
    private final AdmissionReferralRepositoryPort referrals;
    private final ExamPricePolicyPort prices;
    private final ClinicalOutboxPort outbox;
    private final CurrentClinicalActorPort actors;
    private final ClinicalStaffAuthority staffAuthority;
    private final ClinicalDtoMapper mapper;
    private final CorrelationIdProvider correlationIds;
    private final Clock clock;
    private final boolean admissionReferralsEnabled;
    private final ClinicalLabCompletionGuard labCompletionGuard;
    private final OutpatientRegistrationApplicationService scheduledRegistrations;

    public ClinicalCareFinanceApplicationService(AppointmentRepositoryPort appointments,
                                                  MedicalRecordRepositoryPort records,
                                                  ExamClearanceRepositoryPort clearances,
                                                  FinancialExecutionAuthorityPort executionAuthority,
                                                  EmergencyOverrideRepositoryPort overrides,
                                                  AdmissionReferralRepositoryPort referrals,
                                                  ExamPricePolicyPort prices,
                                                  ClinicalOutboxPort outbox,
                                                  CurrentClinicalActorPort actors,
                                                  StaffLookupPort staff,
                                                  ClinicalDtoMapper mapper,
                                                  CorrelationIdProvider correlationIds,
                                                  Clock clock,
                                                  boolean admissionReferralsEnabled,
                                                  ClinicalLabOrderRepositoryPort clinicalLabOrders,
                                                  LabOrderLifecyclePort labOrderLifecycle) {
        this(appointments, records, clearances, executionAuthority, overrides, referrals, prices, outbox,
                actors, staff, mapper, correlationIds, clock, admissionReferralsEnabled, clinicalLabOrders,
                labOrderLifecycle, null);
    }

    public ClinicalCareFinanceApplicationService(AppointmentRepositoryPort appointments,
            MedicalRecordRepositoryPort records, ExamClearanceRepositoryPort clearances,
            FinancialExecutionAuthorityPort executionAuthority, EmergencyOverrideRepositoryPort overrides,
            AdmissionReferralRepositoryPort referrals, ExamPricePolicyPort prices, ClinicalOutboxPort outbox,
            CurrentClinicalActorPort actors, StaffLookupPort staff, ClinicalDtoMapper mapper,
            CorrelationIdProvider correlationIds, Clock clock, boolean admissionReferralsEnabled,
            ClinicalLabOrderRepositoryPort clinicalLabOrders, LabOrderLifecyclePort labOrderLifecycle,
            OutpatientRegistrationApplicationService scheduledRegistrations) {
        this.appointments = appointments;
        this.records = records;
        this.clearances = clearances;
        this.executionAuthority = java.util.Objects.requireNonNull(executionAuthority);
        this.overrides = overrides;
        this.referrals = referrals;
        this.prices = prices;
        this.outbox = outbox;
        this.actors = actors;
        this.staffAuthority = new ClinicalStaffAuthority(actors, staff);
        this.mapper = mapper;
        this.correlationIds = correlationIds;
        this.clock = clock;
        this.admissionReferralsEnabled = admissionReferralsEnabled;
        this.labCompletionGuard = new ClinicalLabCompletionGuard(clinicalLabOrders, labOrderLifecycle);
        this.scheduledRegistrations = scheduledRegistrations;
    }

    @Override
    @Transactional
    public AppointmentDTO checkIn(UUID appointmentId) {
        Appointment appointment = lockedAppointment(appointmentId);
        AppointmentStatus oldStatus = appointment.getStatus();
        Instant now = clock.instant();
        appointment.checkIn(prices.resolvePriceCode(appointment.getDepartmentId()), now);
        Appointment saved = appointments.save(appointment);
        appendStatusChanged(saved, null, oldStatus, saved.getStatus(), now, null);
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public AppointmentDTO cancelCareFinanceExam(UUID appointmentId, String reason) {
        Appointment appointment = lockedAppointment(appointmentId);
        if (appointment.getCareContractVersion() != 1) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "Only a V1 outpatient examination uses the audited cancellation flow");
        }
        ActorIdentity actor = actors.current();
        if (actor == null || actor.staffId() == null
                || (!"ADMIN".equals(actor.role()) && !"DOCTOR".equals(actor.role())
                && !"NURSE".equals(actor.role()))) {
            throw new ForbiddenOperationException("CLINICAL_STAFF_IDENTITY_REQUIRED",
                    "An authenticated clinical staff identity is required for cancellation");
        }
        if ("DOCTOR".equals(actor.role())) {
            staffAuthority.requireAssignedDoctor(appointment.getDoctorId(), appointment.getDepartmentId());
        }

        AppointmentStatus oldStatus = appointment.getStatus();
        Instant cancelledAt = clock.instant();
        appointment.cancelCareFinance(reason, actor.staffId(), cancelledAt);
        Appointment saved = appointments.save(appointment);
        String correlationId = correlationIds.currentOrCreate().toString();
        appendStatusChanged(saved, null, oldStatus, AppointmentStatus.CANCELLED, cancelledAt, null, correlationId);
        append(APPOINTMENT_EXAM_CANCELLED, correlationId, cancelledAt, new AppointmentExamCancelledPayload(
                saved.getAppointmentId(), saved.getPatientId(), saved.getDepartmentId(),
                CareEpisodeType.OUTPATIENT_VISIT, saved.getAppointmentId(), "EXAM", saved.getAppointmentId(),
                actor.staffId(), reason, cancelledAt));
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public AppointmentDTO startExam(UUID appointmentId, StartExamRequest request) {
        if (request == null) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "Start exam command is required");
        }
        Appointment appointment = lockedAppointment(appointmentId);
        staffAuthority.requireAssignedDoctor(appointment.getDoctorId(), appointment.getDepartmentId());
        AppointmentStatus oldStatus = appointment.getStatus();
        Instant now = clock.instant();
        EmergencyOverrideRequest overrideRequest = request.emergencyOverride();
        EmergencyOverride emergencyOverride = overrideRequest == null
                ? null : validatedOverride(appointment, overrideRequest, now);
        // An explicitly authenticated emergency approval is the audited authorization for this start.
        // It takes precedence over any persisted grant, so an old local clearance can neither reject
        // emergency care nor make the service depend on Billing availability.
        boolean scheduled = appointment.getCareContractVersion() == 2;
        if (scheduled && oldStatus == AppointmentStatus.IN_EXAM && overrideRequest == null) {
            requireScheduledLifecycle();
            scheduledRegistrations.startExamination(appointment, now);
            var existing = records.findByAppointmentId(appointmentId).orElseThrow(() ->
                    new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION", "Started exam record is missing"));
            if (existing.getStatus() != MedicalRecordStatus.OPEN
                    || !existing.getPatientId().equals(appointment.getPatientId())
                    || !existing.getDoctorId().equals(appointment.getDoctorId())
                    || !existing.getDepartmentId().equals(appointment.getDepartmentId())) {
                throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION", "Started exam record binding differs");
            }
            return mapper.toDto(appointment);
        }
        if (scheduled && emergencyOverride != null) {
            throw new InvalidClinicalDataException("CLINICAL_REGISTRATION_FLOW_REQUIRED", "Scheduled registrations require their confirmed reservation");
        }
        ExamClearance clearance = emergencyOverride == null && !scheduled
                ? clearances.findByAppointment(appointmentId).orElse(null)
                : null;
        boolean validClearance = false;
        if (clearance != null) {
            if (!clearance.matchesAppointment(appointment)) {
                throw clearanceMismatch();
            }
            validClearance = clearance.isValidAt(now);
            if (!validClearance) {
                throw new InvalidClinicalDataException("CLINICAL_CLEARANCE_EXPIRED",
                        "Exam clearance expired before examination started");
            }
            if (validClearance) {
                appointment.recordExamClearance(clearance.getClearanceId(), clearance.getGrantedAt());
            }
        }

        UUID clearanceId = scheduled ? appointment.getExamClearanceId() : validClearance ? clearance.getClearanceId() : null;
        UUID overrideId = emergencyOverride == null ? null : emergencyOverride.getOverrideId();
        if (appointment.getCareContractVersion() == 0 && (clearanceId != null || overrideId != null)) {
            appointment.promoteToCareFinanceV2(
                    prices.resolvePriceCode(appointment.getDepartmentId()), now);
        }
        if (scheduled) {
            requireScheduledLifecycle();
            scheduledRegistrations.startExamination(appointment, now);
            appointment.startScheduledExam(clearanceId, now);
        } else appointment.startExam(clearanceId, overrideId, now);

        MedicalRecord record = records.findByAppointmentId(appointmentId).orElse(null);
        if (record == null) {
            record = MedicalRecord.openForAppointment(appointment.getPatientId(), appointment.getDoctorId(),
                    appointment.getDepartmentId(), LocalDate.now(clock), null, appointmentId, now);
        } else if (record.getStatus() != MedicalRecordStatus.OPEN
                || !record.getPatientId().equals(appointment.getPatientId())
                || !record.getDoctorId().equals(appointment.getDoctorId())
                || !record.getDepartmentId().equals(appointment.getDepartmentId())) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "Appointment record does not match the examination target");
        }

        String correlationId = correlationIds.currentOrCreate().toString();
        if (clearanceId != null) {
            executionAuthority.start(clearanceId, new FinancialExecutionStartRequest(
                    appointmentId, "EXAM", appointmentId, appointment.getPatientId(),
                    "OUTPATIENT_VISIT", appointmentId), correlationId);
        }
        if (emergencyOverride != null) {
            overrides.save(emergencyOverride);
        }
        MedicalRecord savedRecord = records.save(record);
        Appointment savedAppointment = appointments.save(appointment);
        append(MEDICAL_RECORD_CREATED, correlationId, now, new MedicalRecordCreatedV2Payload(
                savedRecord.getRecordId(), appointmentId, appointment.getPatientId(), appointment.getDoctorId(),
                appointment.getDepartmentId(), CareEpisodeType.OUTPATIENT_VISIT, appointmentId,
                "EXAM", appointmentId, appointment.getExamPriceCode(), savedRecord.getExaminationDate()));
        appendStatusChanged(savedAppointment, savedRecord.getRecordId(), oldStatus,
                savedAppointment.getStatus(), now, savedAppointment.getEmergencyOverrideId(), correlationId);
        return mapper.toDto(savedAppointment);
    }

    @Override
    @Transactional
    public void onFinancialClearance(FinancialClearanceCommand command) {
        ExamClearance clearance = toExamClearance(command);
        if (!clearances.claimAndSave(clearance)) {
            return;
        }
        Instant now = clock.instant();
        if (clearance.getAppointmentId() != null) {
            Appointment appointment = lockedAppointment(clearance.getAppointmentId());
            if (!clearance.matchesAppointment(appointment)) {
                throw clearanceMismatch();
            }
            AppointmentStatus oldStatus = appointment.getStatus();
            appointment.recordExamClearance(clearance.getClearanceId(), clearance.getGrantedAt());
            Appointment saved = appointments.save(appointment);
            if (oldStatus != saved.getStatus()) {
                UUID recordId = records.findByAppointmentId(saved.getAppointmentId())
                        .map(MedicalRecord::getRecordId).orElse(null);
                appendStatusChanged(saved, recordId, oldStatus, saved.getStatus(), now, null);
            }
            return;
        }
        MedicalRecord record = records.findByIdForUpdate(clearance.getRecordId())
                .orElseThrow(() -> new ClinicalRecordNotFoundException(
                        "Medical record not found: " + clearance.getRecordId()));
        if (!clearance.matchesRecord(record)) {
            throw clearanceMismatch();
        }
    }

    @Override
    @Transactional
    public MedicalRecordDTO complete(UUID recordId, CompleteRecordRequest request) {
        if (request == null) {
            throw new InvalidClinicalDataException("CLINICAL_DISPOSITION_REQUIRED",
                    "Completion disposition is required");
        }
        MedicalRecord record = lockedRecord(recordId);
        if (record.getAppointmentId() == null) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "V2 record completion requires an appointment examination");
        }
        Appointment appointment = lockedAppointment(record.getAppointmentId());
        if (appointment.getStatus() != AppointmentStatus.IN_EXAM) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "Appointment must be in examination before record completion");
        }
        staffAuthority.requireAssignedDoctor(appointment.getDoctorId(), appointment.getDepartmentId());
        if (!admissionReferralsEnabled && (request.disposition() == RecordDisposition.ADMISSION
                || request.disposition() == RecordDisposition.TRANSFER)) {
            throw deferredAdmission();
        }
        String correlationId = correlationIds.currentOrCreate().toString();
        if (appointment.getCareContractVersion() == 2) {
            labCompletionGuard.requireAllClosed(record, appointment, correlationId);
        }
        Instant now = clock.instant();
        AppointmentStatus oldStatus = appointment.getStatus();
        record.complete(request.disposition(), request.dispositionNote(), now);
        if (appointment.getCareContractVersion() == 2) {
            requireScheduledLifecycle();
            scheduledRegistrations.completeExamination(appointment, now);
            appointment.completeScheduled(now);
        }
        else appointment.complete(now);
        MedicalRecord savedRecord = records.save(record);
        Appointment savedAppointment = appointments.save(appointment);
        append(MEDICAL_RECORD_COMPLETED, correlationId, now, new MedicalRecordCompletedPayload(
                savedRecord.getRecordId(), savedRecord.getAppointmentId(), savedRecord.getPatientId(),
                savedRecord.getDepartmentId(), savedRecord.getDisposition(),
                savedRecord.getDisposition() == RecordDisposition.ADMISSION, savedRecord.getCompletedAt()));
        appendStatusChanged(savedAppointment, savedRecord.getRecordId(), oldStatus,
                savedAppointment.getStatus(), now, savedAppointment.getEmergencyOverrideId());
        return mapper.toDto(savedRecord);
    }

    @Override
    @Transactional
    public AdmissionReferralDTO requestAdmission(UUID recordId, CreateAdmissionReferralRequest request) {
        if (!admissionReferralsEnabled) {
            throw deferredAdmission();
        }
        if (request == null) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "Admission referral command is required");
        }
        MedicalRecord record = lockedRecord(recordId);
        if (record.getStatus() != MedicalRecordStatus.COMPLETED
                || record.getDisposition() != RecordDisposition.ADMISSION) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "Admission referral requires a completed record with ADMISSION disposition");
        }
        if (referrals.existsByRecordId(recordId)) {
            throw admissionReferralConflict();
        }
        ActorIdentity actor = actors.current();
        if (actor == null || actor.staffId() == null
                || (!"ADMIN".equals(actor.role()) && !"DOCTOR".equals(actor.role()))) {
            throw new ForbiddenOperationException("CLINICAL_STAFF_IDENTITY_REQUIRED",
                    "An authenticated staff identity is required for an admission referral");
        }
        Instant now = clock.instant();
        AdmissionReferral referral = AdmissionReferral.request(record.getRecordId(), record.getPatientId(),
                record.getDepartmentId(), actor.staffId(), request.diagnosisSummary(), request.priority(),
                request.emergency(), now);
        AdmissionReferral saved;
        try {
            saved = referrals.save(referral);
        } catch (DuplicateResourceException duplicate) {
            throw admissionReferralConflict();
        }
        append(ADMISSION_REQUESTED, correlationIds.currentOrCreate().toString(), now,
                new AdmissionRequestedPayload(saved.getAdmissionRequestId(), saved.getRecordId(),
                        saved.getPatientId(), saved.getDepartmentId(), saved.getRequestedBy(),
                        saved.getDiagnosisSummary(), saved.getPriority(), saved.isEmergency(), saved.getRequestedAt()));
        return toDto(saved);
    }

    private EmergencyOverride validatedOverride(Appointment appointment, EmergencyOverrideRequest request,
                                                Instant now) {
        ActorIdentity actor = actors.current();
        if (actor == null || actor.staffId() == null || !actor.staffId().equals(request.approvedBy())
                || !actor.role().equals(request.approverRole())
                || (!"ADMIN".equals(actor.role()) && !"DOCTOR".equals(actor.role()))
                || request.approvedAt().isAfter(now)) {
            throw new InvalidClinicalDataException("CLINICAL_OVERRIDE_INVALID",
                    "Emergency approval must match the authenticated staff identity and role");
        }
        return EmergencyOverride.create(request.overrideId(), appointment.getAppointmentId(), null,
                appointment.getPatientId(), appointment.getAppointmentId(), actor.staffId(), actor.role(),
                request.reason(), request.approvedAt());
    }

    private void appendStatusChanged(Appointment appointment, UUID recordId, AppointmentStatus oldStatus,
                                     AppointmentStatus newStatus, Instant at, UUID overrideId) {
        appendStatusChanged(appointment, recordId, oldStatus, newStatus, at, overrideId,
                correlationIds.currentOrCreate().toString());
    }

    private void appendStatusChanged(Appointment appointment, UUID recordId, AppointmentStatus oldStatus,
                                     AppointmentStatus newStatus, Instant at, UUID overrideId,
                                     String correlationId) {
        append(APPOINTMENT_STATUS_CHANGED, correlationId, at,
                new AppointmentStatusChangedV2Payload(appointment.getAppointmentId(), recordId,
                        appointment.getPatientId(), appointment.getDepartmentId(), oldStatus, newStatus,
                        CareEpisodeType.OUTPATIENT_VISIT, appointment.getAppointmentId(), "EXAM",
                        appointment.getAppointmentId(), appointment.getExamPriceCode(), at, overrideId));
    }

    private void append(String eventType, String correlationId, Instant at, Object payload) {
        outbox.append(DomainEventEnvelope.versionOne(eventType, at, correlationId, payload));
    }

    private static ExamClearance toExamClearance(FinancialClearanceCommand command) {
        if (command == null || command.version() != 1
                || !"financial.clearance.granted".equals(command.eventType())
                || !"billing-service".equals(command.producer()) || command.occurredAt() == null
                || command.correlationId() == null || command.correlationId().isBlank()) {
            throw clearanceMismatch();
        }
        boolean appointmentTarget = command.appointmentId() != null && command.recordId() == null;
        boolean walkInTarget = command.appointmentId() == null && command.recordId() != null;
        if ((!appointmentTarget && !walkInTarget) || command.prescriptionId() != null
                || command.admissionId() != null || command.surgeryCaseId() != null
                || !command.labTestIds().isEmpty()) {
            throw clearanceMismatch();
        }
        return ExamClearance.grant(command.eventId(), command.clearanceId(), command.invoiceId(),
                command.accountId(), command.appointmentId(), command.recordId(), command.patientId(),
                command.careEpisodeType(), command.careEpisodeId(), command.purpose(), command.amount(),
                command.currency(), command.expiresAt(), command.emergencyOverride(), command.occurredAt());
    }

    private Appointment lockedAppointment(UUID id) {
        if (scheduledRegistrations != null) {
            var scheduled = scheduledRegistrations.lockScheduledAppointment(id);
            if (scheduled.isPresent()) return scheduled.get();
        }
        return appointments.findByIdForUpdate(id)
                .orElseThrow(() -> new ClinicalAppointmentNotFoundException("Appointment not found: " + id));
    }

    private void requireScheduledLifecycle() {
        if (scheduledRegistrations == null) throw new InvalidClinicalDataException(
                "CLINICAL_REGISTRATION_FLOW_REQUIRED", "Scheduled lifecycle is unavailable");
    }

    private MedicalRecord lockedRecord(UUID id) {
        return records.findByIdForUpdate(id)
                .orElseThrow(() -> new ClinicalRecordNotFoundException("Medical record not found: " + id));
    }

    private static InvalidClinicalDataException clearanceMismatch() {
        return new InvalidClinicalDataException("CLINICAL_CLEARANCE_TARGET_MISMATCH",
                "Exam clearance does not match the exact Clinical target");
    }

    private static InvalidClinicalDataException deferredAdmission() {
        return new InvalidClinicalDataException("CLINICAL_ADMISSION_DEFERRED",
                "Admission and transfer dispositions are not enabled in the outpatient MVP profile");
    }

    private static DuplicateResourceException admissionReferralConflict() {
        return new DuplicateResourceException("CLINICAL_ADMISSION_REFERRAL_CONFLICT",
                "An admission referral already exists for this record");
    }

    private static AdmissionReferralDTO toDto(AdmissionReferral referral) {
        return new AdmissionReferralDTO(referral.getAdmissionRequestId(), referral.getRecordId(),
                referral.getPatientId(), referral.getDepartmentId(), referral.getRequestedBy(),
                referral.getDiagnosisSummary(), referral.getPriority(), referral.isEmergency(),
                referral.getRequestedAt());
    }
}
