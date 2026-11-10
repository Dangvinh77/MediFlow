package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.clinical.application.dto.request.RegistrationCreateRequest;
import com.mediflow.clinical.application.dto.request.StartExamRequest;
import com.mediflow.clinical.application.dto.response.OpenVisitDTO;
import com.mediflow.clinical.application.mapper.ClinicalDtoMapper;
import com.mediflow.clinical.application.port.out.AdmissionReferralRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicalLabOrderRepositoryPort;
import com.mediflow.clinical.application.port.out.EmergencyOverrideRepositoryPort;
import com.mediflow.clinical.application.port.out.ExamClearanceRepositoryPort;
import com.mediflow.clinical.application.port.out.ExamPricePolicyPort;
import com.mediflow.clinical.application.port.out.FinancialExecutionAuthorityPort;
import com.mediflow.clinical.application.port.out.LabOrderLifecyclePort;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.Diagnosis;
import com.mediflow.clinical.domain.model.FollowUpRecommendation;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.OutpatientReceptionMode;
import com.mediflow.clinical.domain.model.OutpatientRegistration;
import com.mediflow.clinical.domain.model.OutpatientVisitType;
import com.mediflow.clinical.domain.model.RecordClosureReason;
import com.mediflow.clinical.domain.model.RecordDisposition;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RegistrationReturnLinkTest {
    private static final UUID PATIENT = UUID.randomUUID();

    private final IntakeHarness h = new IntakeHarness();
    private OutpatientRegistrationApplicationService service;
    private ConsultationSession target;

    @BeforeEach void setUp() {
        h.actAsNurse();
        service = h.registrationService(false);
        target = h.withCounts(h.session(), 0, 0, 0, 0);
    }

    private RegistrationCreateRequest newVisit(UUID patient, UUID source, String returnReason, String independent) {
        return new RegistrationCreateRequest(patient, target.sessionId(), target.revision(), OutpatientReceptionMode.AT_COUNTER,
                OutpatientVisitType.NEW_VISIT, null, "Khám", null, null, null, source, returnReason, independent);
    }

    private RegistrationCreateRequest followUp(UUID patient, UUID previous, UUID recommendation) {
        return new RegistrationCreateRequest(patient, target.sessionId(), target.revision(), OutpatientReceptionMode.AT_COUNTER,
                OutpatientVisitType.FOLLOW_UP, previous, "Tái khám", recommendation, null, null, null, null, null);
    }

    private MedicalRecord record(UUID patient, UUID department) {
        MedicalRecord record = MedicalRecord.openForAppointment(patient, h.doctorId, department, IntakeHarness.TODAY.minusDays(3), null,
                UUID.randomUUID(), IntakeHarness.NOW);
        lenient().when(h.records.findById(record.getRecordId())).thenReturn(Optional.of(record));
        return record;
    }

    private MedicalRecord completed(UUID patient, UUID department) {
        MedicalRecord record = record(patient, department);
        record.addDiagnosis(Diagnosis.create("Viêm họng", null, null));
        record.complete(RecordDisposition.OTHER, "Ổn định", IntakeHarness.NOW);
        return record;
    }

    private OutpatientRegistration inserted() {
        var saved = ArgumentCaptor.forClass(OutpatientRegistration.class);
        verify(h.registrations).insert(saved.capture(), any(), any(), any(), any());
        return saved.getValue();
    }

    private void assertRefused(RegistrationCreateRequest request, String code) {
        assertThatThrownBy(() -> service.create(request, "key"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo(code));
        verify(h.registrations, never()).insert(any(), any(), any(), any(), any());
    }

    @Test void returnLinkStoredOnNewVisit() {
        MedicalRecord source = completed(PATIENT, h.departmentId);
        service.create(newVisit(PATIENT, source.getRecordId(), "Còn đau họng", null), "key");
        assertThat(inserted().returnLink()).isEqualTo(new OutpatientRegistration.ReturnLink(source.getRecordId(), "Còn đau họng", null));
    }

    @Test void returnSourceOfAnotherPatientIsRefused() {
        MedicalRecord source = completed(UUID.randomUUID(), h.departmentId);
        assertRefused(newVisit(PATIENT, source.getRecordId(), "Còn đau họng", null), "RETURN_SOURCE_INVALID");
    }

    @Test void openRecordIsNotAReturnSource() {
        MedicalRecord source = record(PATIENT, h.departmentId);
        assertRefused(newVisit(PATIENT, source.getRecordId(), "Còn đau họng", null), "RETURN_SOURCE_INVALID");
    }

    @Test void closedRecordIsAReturnSource() {
        MedicalRecord source = record(PATIENT, h.departmentId);
        source.closeWithoutConclusion(RecordClosureReason.PATIENT_LEFT, "Tự về", h.doctorId, IntakeHarness.NOW);
        service.create(newVisit(PATIENT, source.getRecordId(), "Quay lại khám tiếp", null), "key");
        assertThat(inserted().returnLink().sourceRecordId()).isEqualTo(source.getRecordId());
    }

    @Test void returnReasonRequiredWithSource() {
        MedicalRecord source = completed(PATIENT, h.departmentId);
        assertRefused(newVisit(PATIENT, source.getRecordId(), "  ", null), "RETURN_SOURCE_INVALID");
        assertRefused(newVisit(PATIENT, source.getRecordId(), null, null), "RETURN_SOURCE_INVALID");
        assertRefused(newVisit(PATIENT, null, "Còn đau", null), "RETURN_SOURCE_INVALID");
        assertRefused(newVisit(PATIENT, source.getRecordId(), "x".repeat(501), null), "RETURN_SOURCE_INVALID");
    }

    @Test void returnSourceMayBeAnotherDepartment() {
        MedicalRecord source = completed(PATIENT, h.otherDepartmentId);
        service.create(newVisit(PATIENT, source.getRecordId(), "Chuyển khoa khám", null), "key");
        assertThat(inserted().returnLink().sourceRecordId()).isEqualTo(source.getRecordId());
    }

    private void openVisitIn(UUID department) {
        when(h.registrations.findOpenVisitsByPatient(PATIENT)).thenReturn(List.of(new OpenVisitDTO(UUID.randomUUID(), UUID.randomUUID(),
                h.doctorId, department, IntakeHarness.TODAY, IntakeHarness.NOW)));
    }

    @Test void sameDepartmentOpenVisitNeedsReason() {
        openVisitIn(h.departmentId);
        assertThatThrownBy(() -> service.create(newVisit(PATIENT, null, null, null), "key"))
                .isInstanceOfSatisfying(DuplicateResourceException.class, e -> assertThat(e.getCode()).isEqualTo("OPEN_VISIT_EXISTS"));
        assertThatThrownBy(() -> service.create(newVisit(PATIENT, null, null, "   "), "key2"))
                .isInstanceOfSatisfying(DuplicateResourceException.class, e -> assertThat(e.getCode()).isEqualTo("OPEN_VISIT_EXISTS"));
        verify(h.registrations, never()).insert(any(), any(), any(), any(), any());
    }

    @Test void otherDepartmentOpenVisitDoesNotBlock() {
        openVisitIn(h.otherDepartmentId);
        service.create(newVisit(PATIENT, null, null, null), "key");
        assertThat(inserted().returnLink()).isNull();
    }

    @Test void reasonAllowsIndependentVisit() {
        openVisitIn(h.departmentId);
        service.create(newVisit(PATIENT, null, null, "Vấn đề khác, không liên quan lượt trước"), "key");
        assertThat(inserted().returnLink()).isEqualTo(new OutpatientRegistration.ReturnLink(null, null, "Vấn đề khác, không liên quan lượt trước"));
    }

    @Test void independentReasonLongerThanTheColumnIsRefused() {
        openVisitIn(h.departmentId);
        assertRefused(newVisit(PATIENT, null, null, "x".repeat(501)), "INVALID_REQUEST");
    }

    private record Source(OutpatientRegistration registration, MedicalRecord record) {}

    /** A finished visit in the target department, one week earlier. */
    private Source finishedVisit(boolean concluded) {
        ConsultationSession earlier = h.session(h.doctorId, h.roomId, IntakeHarness.TODAY.minusDays(7), 20);
        OutpatientRegistration confirmed = h.arrivedConfirmed(earlier);
        OutpatientRegistration previous = h.stub(confirmed.startExam(IntakeHarness.NOW).complete(IntakeHarness.NOW));
        var config = earlier.configuration();
        Appointment appointment = Appointment.createScheduled(previous.registrationId(), previous.patientId(), h.doctorId, h.departmentId,
                config.sessionDate(), config.startTime(), "Khám", IntakeHarness.NOW, IntakeHarness.NOW);
        appointment.confirmScheduledPayment(previous.reservation().clearanceId(), IntakeHarness.NOW);
        appointment.startScheduledExam(previous.reservation().clearanceId(), IntakeHarness.NOW);
        appointment.completeScheduled(IntakeHarness.NOW);
        when(h.appointments.findById(previous.registrationId())).thenReturn(Optional.of(appointment));
        MedicalRecord record = MedicalRecord.openForAppointment(previous.patientId(), h.doctorId, h.departmentId, config.sessionDate(), null,
                previous.registrationId(), IntakeHarness.NOW);
        record.addDiagnosis(Diagnosis.create("Viêm họng", null, null));
        if (concluded) record.complete(RecordDisposition.OTHER, "Ổn định", IntakeHarness.NOW);
        else record.closeWithoutConclusion(RecordClosureReason.PATIENT_LEFT, "Tự về", h.doctorId, IntakeHarness.NOW);
        when(h.records.findByAppointmentId(previous.registrationId())).thenReturn(Optional.of(record));
        return new Source(previous, record);
    }

    @Test void closedRecordIsNotAFollowUpSource() {
        Source source = finishedVisit(false);
        assertThatThrownBy(() -> service.create(followUp(source.registration().patientId(), source.registration().registrationId(), null), "key"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(h.registrations, never()).insert(any(), any(), any(), any(), any());
    }

    @Test void followUpSourceOfAnotherPatientIsRefused() {
        Source source = finishedVisit(true);
        assertThatThrownBy(() -> service.create(followUp(PATIENT, source.registration().registrationId(), null), "key"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(h.registrations, never()).insert(any(), any(), any(), any(), any());
    }

    @Test void overdueRecommendationStillRegisters() {
        Source source = finishedVisit(true);
        UUID advice = UUID.randomUUID();
        when(h.recommendations.findBySourceRecordId(source.record().getRecordId())).thenReturn(Optional.of(new FollowUpRecommendation(advice,
                source.record().getRecordId(), source.registration().registrationId(), source.registration().patientId(), h.departmentId, h.doctorId,
                IntakeHarness.TODAY.minusDays(30), IntakeHarness.TODAY.minusDays(10), "Tái khám", null, IntakeHarness.NOW, UUID.randomUUID(), h.doctorId)));
        service.create(followUp(source.registration().patientId(), source.registration().registrationId(), advice), "key");
        assertThat(inserted().followUp().recommendationId()).isEqualTo(advice);
    }

    /** Starts the visit and returns the record it opened. */
    private MedicalRecord start(OutpatientRegistration registration, ConsultationSession session) {
        h.actAsDoctor();
        StaffLookupPort staff = mock(StaffLookupPort.class);
        when(staff.departmentOf(h.doctorId)).thenReturn(Optional.of(h.departmentId));
        h.stub(registration);
        Appointment appointment = h.stubAppointment(registration, session);
        appointment.confirmScheduledPayment(registration.reservation().clearanceId(), IntakeHarness.NOW);
        when(h.appointments.save(any())).thenAnswer(call -> call.getArgument(0));
        when(h.records.save(any())).thenAnswer(call -> call.getArgument(0));
        new ClinicalCareFinanceApplicationService(h.appointments, h.records, mock(ExamClearanceRepositoryPort.class),
                mock(FinancialExecutionAuthorityPort.class), mock(EmergencyOverrideRepositoryPort.class),
                mock(AdmissionReferralRepositoryPort.class), mock(ExamPricePolicyPort.class), h.outbox, h.actors, staff, mock(ClinicalDtoMapper.class),
                h.correlationIds, Clock.fixed(IntakeHarness.NOW, ZoneOffset.UTC), false, mock(ClinicalLabOrderRepositoryPort.class),
                mock(LabOrderLifecyclePort.class), service).startExam(appointment.getAppointmentId(), new StartExamRequest(null));
        var saved = ArgumentCaptor.forClass(MedicalRecord.class);
        verify(h.records).save(saved.capture());
        return saved.getValue();
    }

    @Test void startCopiesReturnSourceToRecord() {
        UUID source = UUID.randomUUID();
        ConsultationSession session = h.withCounts(h.session(), 0, 0, 1, 0);
        var registration = h.arrivedConfirmed(session).withReturnLink(new OutpatientRegistration.ReturnLink(source, "Còn đau", null));
        assertThat(start(registration, session).getPreviousRecordId()).isEqualTo(source);
    }

    @Test void startCopiesFollowUpSourceToRecord() {
        Source source = finishedVisit(true);
        ConsultationSession session = h.withCounts(h.session(), 0, 0, 1, 0);
        var registration = h.arrivedConfirmed(session)
                .asFollowUp(new OutpatientRegistration.FollowUpLink(source.registration().registrationId(), null, null, null, "Tái khám"));
        assertThat(start(registration, session).getPreviousRecordId()).isEqualTo(source.record().getRecordId());
    }

    @Test void newRecordCopiesNoDiagnosis() {
        Source source = finishedVisit(true);
        ConsultationSession session = h.withCounts(h.session(), 0, 0, 1, 0);
        var registration = h.arrivedConfirmed(session)
                .asFollowUp(new OutpatientRegistration.FollowUpLink(source.registration().registrationId(), null, null, null, "Tái khám"));
        MedicalRecord opened = start(registration, session);
        assertThat(opened.getDiagnoses()).isEmpty();
        assertThat(opened.getSymptoms()).isNull();
        assertThat(opened.getDisposition()).isNull();
    }
}
