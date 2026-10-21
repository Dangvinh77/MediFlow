package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import org.mockito.ArgumentCaptor;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.command.FinancialClearanceCommand;
import com.mediflow.clinical.application.dto.command.FinancialExecutionStartRequest;
import com.mediflow.clinical.application.dto.request.CompleteRecordRequest;
import com.mediflow.clinical.application.dto.request.CreateAdmissionReferralRequest;
import com.mediflow.clinical.application.dto.request.EmergencyOverrideRequest;
import com.mediflow.clinical.application.dto.request.StartExamRequest;
import com.mediflow.clinical.application.event.AppointmentExamCancelledPayload;
import com.mediflow.clinical.application.event.DomainEventEnvelope;
import com.mediflow.clinical.application.event.MedicalRecordCreatedV2Payload;
import com.mediflow.clinical.application.mapper.ClinicalDtoMapper;
import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.clinical.application.port.out.AdmissionReferralRepositoryPort;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicalOutboxPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.EmergencyOverrideRepositoryPort;
import com.mediflow.clinical.application.port.out.ExamClearanceRepositoryPort;
import com.mediflow.clinical.application.port.out.FinancialExecutionAuthorityPort;
import com.mediflow.clinical.application.port.out.ExamPricePolicyPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.domain.model.AdmissionPriority;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.domain.model.CareEpisodeType;
import com.mediflow.clinical.domain.model.ClearancePurpose;
import com.mediflow.clinical.domain.model.Diagnosis;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.RecordDisposition;
import com.mediflow.common.exception.DuplicateResourceException;

class ClinicalCareFinanceApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-27T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final AppointmentRepositoryPort appointments = mock(AppointmentRepositoryPort.class);
    private final MedicalRecordRepositoryPort records = mock(MedicalRecordRepositoryPort.class);
    private final ExamClearanceRepositoryPort clearances = mock(ExamClearanceRepositoryPort.class);
    private final FinancialExecutionAuthorityPort executionAuthority = mock(FinancialExecutionAuthorityPort.class);
    private final EmergencyOverrideRepositoryPort overrides = mock(EmergencyOverrideRepositoryPort.class);
    private final AdmissionReferralRepositoryPort referrals = mock(AdmissionReferralRepositoryPort.class);
    private final ExamPricePolicyPort prices = mock(ExamPricePolicyPort.class);
    private final ClinicalOutboxPort outbox = mock(ClinicalOutboxPort.class);
    private final CurrentClinicalActorPort actors = mock(CurrentClinicalActorPort.class);
    private final StaffLookupPort staff = mock(StaffLookupPort.class);
    private final ClinicalDtoMapper mapper = mock(ClinicalDtoMapper.class);
    private final CorrelationIdProvider correlationIds = mock(CorrelationIdProvider.class);
    private final ClinicalCareFinanceApplicationService service = new ClinicalCareFinanceApplicationService(
            appointments, records, clearances, executionAuthority, overrides, referrals, prices, outbox, actors, staff, mapper,
            correlationIds, CLOCK, true);
    private final ClinicalCareFinanceApplicationService outpatientMvpService =
            new ClinicalCareFinanceApplicationService(appointments, records, clearances, executionAuthority, overrides, referrals,
                    prices, outbox, actors, staff, mapper, correlationIds, CLOCK, false);

    @BeforeEach
    void setUp() {
        when(correlationIds.currentOrCreate()).thenReturn(UUID.randomUUID());
        when(prices.resolvePriceCode(any(UUID.class))).thenReturn("OUTPATIENT_EXAM");
        when(appointments.save(any(Appointment.class))).thenAnswer(call -> call.getArgument(0));
        when(records.save(any(MedicalRecord.class))).thenAnswer(call -> call.getArgument(0));
        when(overrides.save(any())).thenAnswer(call -> call.getArgument(0));
        when(referrals.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void checkIn_pendingAppointment_opensGateAndAppendsChargeFact() {
        Appointment appointment = appointment();
        when(appointments.findByIdForUpdate(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));

        service.checkIn(appointment.getAppointmentId());

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.AWAITING_PAYMENT);
        assertThat(appointment.getExamPriceCode()).isEqualTo("OUTPATIENT_EXAM");
        ArgumentCaptor<DomainEventEnvelope<?>> event = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo("appointment.status.changed");
    }

    @Test
    void startExam_arrivedWithoutClearanceOrOverride_rejects() {
        Appointment appointment = appointment();
        authorize(appointment);
        appointment.checkIn("OUTPATIENT_EXAM", NOW);
        when(appointments.findByIdForUpdate(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));
        when(clearances.findByAppointment(appointment.getAppointmentId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startExam(appointment.getAppointmentId(), new StartExamRequest(null)))
                .isInstanceOf(InvalidClinicalDataException.class)
                .hasFieldOrPropertyWithValue("code", "CLINICAL_EXAM_CLEARANCE_REQUIRED");

        verify(records, never()).save(any());
        verifyNoOutboxEvent();
    }

    @Test
    void cancelCareFinanceExam_persistsAuditAndPublishesExactBillingEvidence() {
        Appointment appointment = appointment();
        appointment.checkIn("OUTPATIENT_EXAM", NOW);
        UUID actor = UUID.randomUUID();
        UUID correlation = UUID.randomUUID();
        when(actors.current()).thenReturn(new ActorIdentity(actor, "ADMIN"));
        when(correlationIds.currentOrCreate()).thenReturn(correlation);
        when(appointments.findByIdForUpdate(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));
        when(mapper.toDto(any(Appointment.class))).thenReturn(null);

        service.cancelCareFinanceExam(appointment.getAppointmentId(), "Patient declined before examination");

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointment.getCancelledBy()).isEqualTo(actor);
        assertThat(appointment.getCancellationReason()).isEqualTo("Patient declined before examination");
        assertThat(appointment.getCancelledAt()).isEqualTo(NOW);
        ArgumentCaptor<DomainEventEnvelope<?>> events = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(outbox, org.mockito.Mockito.times(2)).append(events.capture());
        DomainEventEnvelope<?> cancellation = events.getAllValues().stream()
                .filter(event -> event.eventType().equals("appointment.exam.cancelled"))
                .findFirst().orElseThrow();
        assertThat(cancellation.version()).isEqualTo(1);
        assertThat(cancellation.producer()).isEqualTo("clinical-service");
        assertThat(cancellation.occurredAt()).isEqualTo(NOW);
        assertThat(cancellation.correlationId()).isEqualTo(correlation.toString());
        assertThat(cancellation.payload()).isEqualTo(new AppointmentExamCancelledPayload(
                appointment.getAppointmentId(), appointment.getPatientId(), appointment.getDepartmentId(),
                CareEpisodeType.OUTPATIENT_VISIT, appointment.getAppointmentId(), "EXAM",
                appointment.getAppointmentId(), actor, "Patient declined before examination", NOW));
        assertThat(events.getAllValues()).extracting(DomainEventEnvelope::correlationId)
                .containsOnly(correlation.toString());
    }

    @Test
    void cancelCareFinanceExam_requiresSignedClinicalActorBeforeMutation() {
        Appointment appointment = appointment();
        appointment.checkIn("OUTPATIENT_EXAM", NOW);
        when(appointments.findByIdForUpdate(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));
        when(actors.current()).thenReturn(null);

        assertThatThrownBy(() -> service.cancelCareFinanceExam(appointment.getAppointmentId(), "Patient requested it"))
                .isInstanceOf(com.mediflow.common.exception.ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("code", "CLINICAL_STAFF_IDENTITY_REQUIRED");

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.AWAITING_PAYMENT);
        verify(appointments, never()).save(any());
        verifyNoOutboxEvent();
    }

    @Test
    void startExam_withOnlyPersistedClearance_failsClosedWhenCurrentBillingAuthorityIsUnavailable() {
        Appointment appointment = appointment();
        authorize(appointment);
        appointment.checkIn("OUTPATIENT_EXAM", NOW);
        FinancialClearanceCommand grant = clearance(appointment.getPatientId(), appointment.getAppointmentId(),
                appointment.getAppointmentId());
        appointment.recordExamClearance(grant.clearanceId(), grant.occurredAt());
        when(appointments.findByIdForUpdate(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));
        when(clearances.findByAppointment(appointment.getAppointmentId())).thenReturn(Optional.of(
                com.mediflow.clinical.domain.model.ExamClearance.grant(grant.eventId(), grant.clearanceId(),
                        grant.invoiceId(), grant.accountId(), grant.appointmentId(), grant.recordId(), grant.patientId(),
                        grant.careEpisodeType(), grant.careEpisodeId(), grant.purpose(), grant.amount(), grant.currency(),
                        grant.expiresAt(), grant.emergencyOverride(), grant.occurredAt())));
        org.mockito.Mockito.doThrow(new UpstreamUnavailableException("Billing is unavailable"))
                .when(executionAuthority).start(org.mockito.ArgumentMatchers.eq(grant.clearanceId()), any(), any());

        assertThatThrownBy(() -> service.startExam(appointment.getAppointmentId(), new StartExamRequest(null)))
                .isInstanceOf(UpstreamUnavailableException.class);

        verify(records, never()).save(any());
        verify(appointments, never()).save(any());
        verifyNoOutboxEvent();
    }

    @Test
    void startExam_withCurrentGrantRequestsDurableBillingAdmissionForExactAppointment() {
        Appointment appointment = appointment();
        authorize(appointment);
        appointment.checkIn("OUTPATIENT_EXAM", NOW);
        FinancialClearanceCommand grant = clearance(appointment.getPatientId(), appointment.getAppointmentId(),
                appointment.getAppointmentId());
        UUID correlation = UUID.randomUUID();
        when(appointments.findByIdForUpdate(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));
        when(clearances.findByAppointment(appointment.getAppointmentId())).thenReturn(Optional.of(
                com.mediflow.clinical.domain.model.ExamClearance.grant(grant.eventId(), grant.clearanceId(),
                        grant.invoiceId(), grant.accountId(), grant.appointmentId(), grant.recordId(), grant.patientId(),
                        grant.careEpisodeType(), grant.careEpisodeId(), grant.purpose(), grant.amount(), grant.currency(),
                        grant.expiresAt(), grant.emergencyOverride(), grant.occurredAt())));
        when(records.findByAppointmentId(appointment.getAppointmentId())).thenReturn(Optional.empty());
        when(correlationIds.currentOrCreate()).thenReturn(correlation);
        when(mapper.toDto(any(Appointment.class))).thenReturn(null);

        service.startExam(appointment.getAppointmentId(), new StartExamRequest(null));

        ArgumentCaptor<FinancialExecutionStartRequest> admission =
                ArgumentCaptor.forClass(FinancialExecutionStartRequest.class);
        verify(executionAuthority).start(org.mockito.ArgumentMatchers.eq(grant.clearanceId()), admission.capture(),
                org.mockito.ArgumentMatchers.eq(correlation.toString()));
        assertThat(admission.getValue()).isEqualTo(new FinancialExecutionStartRequest(
                appointment.getAppointmentId(), "EXAM", appointment.getAppointmentId(), appointment.getPatientId(),
                "OUTPATIENT_VISIT", appointment.getAppointmentId()));
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.IN_EXAM);
        assertThat(appointment.getExamClearanceId()).isEqualTo(grant.clearanceId());
        ArgumentCaptor<DomainEventEnvelope<?>> events = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(outbox, org.mockito.Mockito.times(2)).append(events.capture());
        assertThat(events.getAllValues()).extracting(DomainEventEnvelope::correlationId)
                .containsOnly(correlation.toString());
    }

    @Test
    void startExam_explicitValidEmergencyOverrideTakesPrecedenceOverLocalGrantAndSkipsBilling() {
        Appointment appointment = appointment();
        authorize(appointment);
        appointment.checkIn("OUTPATIENT_EXAM", NOW);
        FinancialClearanceCommand grant = clearance(appointment.getPatientId(), appointment.getAppointmentId(),
                appointment.getAppointmentId());
        when(appointments.findByIdForUpdate(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));
        when(clearances.findByAppointment(appointment.getAppointmentId())).thenReturn(Optional.of(
                com.mediflow.clinical.domain.model.ExamClearance.grant(grant.eventId(), grant.clearanceId(),
                        grant.invoiceId(), grant.accountId(), grant.appointmentId(), grant.recordId(), grant.patientId(),
                        grant.careEpisodeType(), grant.careEpisodeId(), grant.purpose(), grant.amount(), grant.currency(),
                        grant.expiresAt(), grant.emergencyOverride(), grant.occurredAt())));
        when(records.findByAppointmentId(appointment.getAppointmentId())).thenReturn(Optional.empty());
        when(mapper.toDto(any(Appointment.class))).thenReturn(null);
        EmergencyOverrideRequest approval = new EmergencyOverrideRequest(UUID.randomUUID(), appointment.getDoctorId(),
                "DOCTOR", "Emergency care cannot wait for financial confirmation", NOW);

        service.startExam(appointment.getAppointmentId(), new StartExamRequest(approval));

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.IN_EXAM);
        assertThat(appointment.getEmergencyOverrideId()).isEqualTo(approval.overrideId());
        org.mockito.Mockito.verifyNoInteractions(executionAuthority);
        verify(overrides).save(any());
        verify(records).save(any(MedicalRecord.class));
    }

    @Test
    void onClearance_matchingExam_movesAppointmentToReady() {
        Appointment appointment = appointment();
        appointment.checkIn("OUTPATIENT_EXAM", NOW);
        when(appointments.findByIdForUpdate(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));
        when(clearances.claimAndSave(any())).thenReturn(true);

        service.onFinancialClearance(clearance(appointment.getPatientId(), appointment.getAppointmentId(),
                appointment.getAppointmentId()));

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.READY_FOR_EXAM);
        verify(outbox).append(any());
    }

    @Test
    void onClearance_duplicateEvent_appliesOnce() {
        Appointment appointment = appointment();
        appointment.checkIn("OUTPATIENT_EXAM", NOW);
        when(appointments.findByIdForUpdate(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));
        when(clearances.claimAndSave(any())).thenReturn(false);

        service.onFinancialClearance(clearance(appointment.getPatientId(), appointment.getAppointmentId(),
                appointment.getAppointmentId()));

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.AWAITING_PAYMENT);
        verify(appointments, never()).save(any());
        verifyNoOutboxEvent();
    }

    @Test
    void onClearance_wrongPatient_rejectsExactTargetMismatch() {
        Appointment appointment = appointment();
        appointment.checkIn("OUTPATIENT_EXAM", NOW);
        when(appointments.findByIdForUpdate(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));
        when(clearances.claimAndSave(any())).thenReturn(true);

        assertThatThrownBy(() -> service.onFinancialClearance(clearance(
                UUID.randomUUID(), appointment.getAppointmentId(), appointment.getAppointmentId())))
                .hasFieldOrPropertyWithValue("code", "CLINICAL_CLEARANCE_TARGET_MISMATCH");
    }

    @Test
    void onClearance_appointmentAndRecordTogetherRejectsBeforeClaim() {
        UUID appointmentId = UUID.randomUUID();
        FinancialClearanceCommand invalid = new FinancialClearanceCommand(
                UUID.randomUUID(), "financial.clearance.granted", 1, NOW,
                UUID.randomUUID().toString(), "billing-service", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), CareEpisodeType.OUTPATIENT_VISIT, appointmentId,
                ClearancePurpose.EXAM, appointmentId, UUID.randomUUID(), List.of(), null, null, null,
                new BigDecimal("150000.00"), "VND", "CASH", null, false);

        assertThatThrownBy(() -> service.onFinancialClearance(invalid))
                .hasFieldOrPropertyWithValue("code", "CLINICAL_CLEARANCE_TARGET_MISMATCH");
        verify(clearances, never()).claimAndSave(any());
        verifyNoOutboxEvent();
    }

    @Test
    void startExam_emergencyOverride_persistsAuditAndStartsRecord() {
        Appointment appointment = appointment();
        authorize(appointment);
        appointment.checkIn("OUTPATIENT_EXAM", NOW);
        when(appointments.findByIdForUpdate(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));
        when(clearances.findByAppointment(appointment.getAppointmentId())).thenReturn(Optional.empty());
        when(records.findByAppointmentId(appointment.getAppointmentId())).thenReturn(Optional.empty());
        when(mapper.toDto(any(Appointment.class))).thenReturn(null);

        service.startExam(appointment.getAppointmentId(), new StartExamRequest(
                new EmergencyOverrideRequest(UUID.randomUUID(), appointment.getDoctorId(), "DOCTOR",
                        "Critical condition", NOW)));

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.IN_EXAM);
        assertThat(appointment.getEmergencyOverrideId()).isNotNull();
        verify(overrides).save(any());
        verify(records).save(any(MedicalRecord.class));
        ArgumentCaptor<DomainEventEnvelope<?>> events = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(outbox, org.mockito.Mockito.times(2)).append(events.capture());
        assertThat(events.getAllValues()).extracting(DomainEventEnvelope::eventType)
                .contains("medicalrecord.created", "appointment.status.changed");
    }

    @Test
    void startExam_legacyArrivedEmergency_promotesAppointmentAndPublishesPriceCode() {
        Appointment appointment = appointment();
        authorize(appointment);
        appointment.changeStatus(AppointmentStatus.ARRIVED);
        when(appointments.findByIdForUpdate(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));
        when(clearances.findByAppointment(appointment.getAppointmentId())).thenReturn(Optional.empty());
        when(records.findByAppointmentId(appointment.getAppointmentId())).thenReturn(Optional.empty());
        when(mapper.toDto(any(Appointment.class))).thenReturn(null);

        service.startExam(appointment.getAppointmentId(), new StartExamRequest(
                new EmergencyOverrideRequest(UUID.randomUUID(), appointment.getDoctorId(), "DOCTOR",
                        "Critical condition", NOW)));

        assertThat(appointment.getCareContractVersion()).isEqualTo((short) 1);
        assertThat(appointment.getExamPriceCode()).isEqualTo("OUTPATIENT_EXAM");
        assertThat(appointment.getCheckedInAt()).isEqualTo(NOW);
        ArgumentCaptor<DomainEventEnvelope<?>> events = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(outbox, org.mockito.Mockito.times(2)).append(events.capture());
        MedicalRecordCreatedV2Payload created = events.getAllValues().stream()
                .filter(event -> event.eventType().equals("medicalrecord.created"))
                .map(event -> (MedicalRecordCreatedV2Payload) event.payload())
                .findFirst().orElseThrow();
        assertThat(created.priceCode()).isEqualTo("OUTPATIENT_EXAM");
    }

    @Test
    void complete_missingDiagnosis_rejectsWithoutPublishing() {
        Appointment appointment = appointment();
        authorize(appointment);
        appointment.checkIn("OUTPATIENT_EXAM", NOW);
        appointment.startExam(UUID.randomUUID(), null, NOW);
        MedicalRecord record = MedicalRecord.openForAppointment(appointment.getPatientId(), appointment.getDoctorId(),
                appointment.getDepartmentId(), LocalDate.now(CLOCK), "Fever", appointment.getAppointmentId(), NOW);
        when(records.findByIdForUpdate(record.getRecordId())).thenReturn(Optional.of(record));
        when(appointments.findByIdForUpdate(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.complete(record.getRecordId(),
                new CompleteRecordRequest(RecordDisposition.OUTPATIENT_FOLLOW_UP, null)))
                .hasFieldOrPropertyWithValue("code", "CLINICAL_DIAGNOSIS_REQUIRED");

        verifyNoOutboxEvent();
    }

    @Test
    void complete_repeatedCommand_publishesCompletedFactOnce() {
        Appointment appointment = appointment();
        authorize(appointment);
        appointment.checkIn("OUTPATIENT_EXAM", NOW);
        appointment.startExam(UUID.randomUUID(), null, NOW);
        MedicalRecord record = MedicalRecord.openForAppointment(appointment.getPatientId(), appointment.getDoctorId(),
                appointment.getDepartmentId(), LocalDate.now(CLOCK), "Fever", appointment.getAppointmentId(), NOW);
        record.addDiagnosis(Diagnosis.create("Influenza", null, "J10"));
        when(records.findByIdForUpdate(record.getRecordId())).thenReturn(Optional.of(record));
        when(appointments.findByIdForUpdate(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));

        service.complete(record.getRecordId(),
                new CompleteRecordRequest(RecordDisposition.OUTPATIENT_FOLLOW_UP, "Return in one week"));
        assertThatThrownBy(() -> service.complete(record.getRecordId(),
                new CompleteRecordRequest(RecordDisposition.OUTPATIENT_FOLLOW_UP, null)))
                .hasFieldOrPropertyWithValue("code", "CLINICAL_INVALID_STATUS_TRANSITION");

        ArgumentCaptor<DomainEventEnvelope<?>> events = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(outbox, org.mockito.Mockito.times(2)).append(events.capture());
        assertThat(events.getAllValues().stream().filter(event ->
                event.eventType().equals("medicalrecord.completed"))).hasSize(1);
    }

    @Test
    void requestAdmission_duplicateRecord_conflicts() {
        MedicalRecord record = MedicalRecord.openForAppointment(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), LocalDate.now(CLOCK), "Fever", UUID.randomUUID(), NOW);
        record.addDiagnosis(Diagnosis.create("Influenza", null, "J10"));
        record.complete(RecordDisposition.ADMISSION, null, NOW);
        when(records.findByIdForUpdate(record.getRecordId())).thenReturn(Optional.of(record));
        when(actors.current()).thenReturn(new ActorIdentity(UUID.randomUUID(), "DOCTOR"));
        when(referrals.existsByRecordId(record.getRecordId())).thenReturn(true);

        assertThatThrownBy(() -> service.requestAdmission(record.getRecordId(),
                new CreateAdmissionReferralRequest("Influenza requiring admission", AdmissionPriority.URGENT, false)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasFieldOrPropertyWithValue("code", "CLINICAL_ADMISSION_REFERRAL_CONFLICT");

        verifyNoOutboxEvent();
    }

    @Test
    void startExam_withoutSignedStaffIdentity_failsClosedBeforeMutation() {
        Appointment appointment = appointment();
        appointment.checkIn("OUTPATIENT_EXAM", NOW);
        when(appointments.findByIdForUpdate(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));
        when(clearances.findByAppointment(appointment.getAppointmentId())).thenReturn(Optional.empty());
        when(actors.current()).thenReturn(null);

        assertThatThrownBy(() -> service.startExam(appointment.getAppointmentId(), new StartExamRequest(null)))
                .hasFieldOrPropertyWithValue("code", "CLINICAL_STAFF_IDENTITY_REQUIRED");

        verify(records, never()).save(any());
        verifyNoOutboxEvent();
    }

    @Test
    void startExam_ineligibleAssignedDoctor_failsClosedBeforeMutation() {
        Appointment appointment = appointment();
        authorize(appointment);
        when(staff.departmentOf(appointment.getDoctorId())).thenReturn(Optional.empty());
        appointment.checkIn("OUTPATIENT_EXAM", NOW);
        when(appointments.findByIdForUpdate(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.startExam(appointment.getAppointmentId(), new StartExamRequest(null)))
                .hasFieldOrPropertyWithValue("code", "CLINICAL_DOCTOR_NOT_ELIGIBLE");

        verify(records, never()).save(any());
        verifyNoOutboxEvent();
    }

    @Test
    void complete_admissionDispositionIsRejectedWhenDeferredGateIsOff() {
        Appointment appointment = appointment();
        authorize(appointment);
        appointment.checkIn("OUTPATIENT_EXAM", NOW);
        appointment.startExam(UUID.randomUUID(), null, NOW);
        MedicalRecord record = MedicalRecord.openForAppointment(appointment.getPatientId(), appointment.getDoctorId(),
                appointment.getDepartmentId(), LocalDate.now(CLOCK), "Fever", appointment.getAppointmentId(), NOW);
        record.addDiagnosis(Diagnosis.create("Influenza", null, "J10"));
        when(records.findByIdForUpdate(record.getRecordId())).thenReturn(Optional.of(record));
        when(appointments.findByIdForUpdate(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> outpatientMvpService.complete(record.getRecordId(),
                new CompleteRecordRequest(RecordDisposition.ADMISSION, null)))
                .hasFieldOrPropertyWithValue("code", "CLINICAL_ADMISSION_DEFERRED");

        assertThat(record.getDisposition()).isNull();
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.IN_EXAM);
        verifyNoOutboxEvent();
    }

    private Appointment appointment() {
        return Appointment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(CLOCK), LocalTime.NOON, "Checkup", CLOCK);
    }

    private void authorize(Appointment appointment) {
        when(actors.current()).thenReturn(new ActorIdentity(appointment.getDoctorId(), "DOCTOR"));
        when(staff.departmentOf(appointment.getDoctorId())).thenReturn(Optional.of(appointment.getDepartmentId()));
    }

    private FinancialClearanceCommand clearance(UUID patientId, UUID appointmentId, UUID episodeId) {
        return new FinancialClearanceCommand(UUID.randomUUID(), "financial.clearance.granted", 1, NOW,
                UUID.randomUUID().toString(), "billing-service", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), patientId, CareEpisodeType.OUTPATIENT_VISIT, episodeId,
                ClearancePurpose.EXAM, appointmentId, null, List.of(), null, null, null,
                new BigDecimal("150000.00"), "VND", "CASH", null, false);
    }

    private void verifyNoOutboxEvent() {
        verify(outbox, never()).append(any());
    }
}
