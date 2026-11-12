package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.clinical.application.dto.authority.LabOrderLifecycleSnapshot;
import com.mediflow.clinical.application.dto.request.CloseRecordRequest;
import com.mediflow.clinical.application.dto.request.WithdrawLabOrderRequest;
import com.mediflow.clinical.application.dto.response.RecordLabOrderDTO;
import com.mediflow.clinical.application.dto.response.MedicalRecordDTO;
import com.mediflow.clinical.application.dto.response.RecordCapabilitiesDTO;
import com.mediflow.clinical.application.dto.authority.RecordReadDecision;
import com.mediflow.clinical.application.event.DomainEventEnvelope;
import com.mediflow.clinical.application.mapper.ClinicalDtoMapper;
import com.mediflow.clinical.application.port.out.AdmissionReferralRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicQueueRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicalLabOrderRepositoryPort;
import com.mediflow.clinical.application.port.out.EmergencyOverrideRepositoryPort;
import com.mediflow.clinical.application.port.out.ExamClearanceRepositoryPort;
import com.mediflow.clinical.application.port.out.ExamPricePolicyPort;
import com.mediflow.clinical.application.port.out.FinancialExecutionAuthorityPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.LabOrderLifecyclePort;
import com.mediflow.clinical.application.port.out.OutpatientMvpMode;
import com.mediflow.clinical.application.port.out.RecordAccessDeclarationPort;
import com.mediflow.clinical.application.port.out.RecordAccessLogPort;
import com.mediflow.clinical.application.port.out.RecordAccessPolicyMode;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.domain.model.ClinicQueueTicket;
import com.mediflow.clinical.domain.model.ClinicQueueTicketStatus;
import com.mediflow.clinical.domain.model.CareEpisodeType;
import com.mediflow.clinical.domain.model.ClinicalLabOrder;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.clinical.domain.model.OutpatientRegistration;
import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.clinical.domain.model.RecordClosureReason;
import com.mediflow.common.exception.ForbiddenOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class ExamClosureTest {
    private static final CloseRecordRequest REQUEST = new CloseRecordRequest(RecordClosureReason.PATIENT_LEFT, "Bệnh nhân tự ý về");

    private final IntakeHarness h = new IntakeHarness();
    private final ClinicQueueRepositoryPort tickets = mock(ClinicQueueRepositoryPort.class);
    private final StaffLookupPort staff = mock(StaffLookupPort.class);
    private final ClinicalDtoMapper mapper = mock(ClinicalDtoMapper.class);
    private final ClinicalLabOrderRepositoryPort labOrders = mock(ClinicalLabOrderRepositoryPort.class);
    private final LabOrderLifecyclePort lifecycle = mock(LabOrderLifecyclePort.class);
    private ExamClosureApplicationService service;
    private ConsultationSession session;
    private OutpatientRegistration inExam;
    private Appointment appointment;
    private MedicalRecord record;

    @SuppressWarnings("unchecked")
    @BeforeEach void setUp() {
        ObjectProvider<ClinicQueueRepositoryPort> provider = mock(ObjectProvider.class);
        lenient().when(provider.getIfAvailable()).thenReturn(tickets);
        var registrations = new OutpatientRegistrationApplicationService(h.sessions, h.registrations, h.appointments, h.patients,
                h.authority, h.actors, h.outbox, h.correlationIds, h.idempotency, h.clock, h.records, h.recommendations,
                new ClinicIntakeSettings(true), provider);
        var careFinance = new ClinicalCareFinanceApplicationService(h.appointments, h.records, mock(ExamClearanceRepositoryPort.class),
                mock(FinancialExecutionAuthorityPort.class), mock(EmergencyOverrideRepositoryPort.class),
                mock(AdmissionReferralRepositoryPort.class), mock(ExamPricePolicyPort.class), h.outbox, h.actors, staff, mapper,
                h.correlationIds, h.clock, false, labOrders, lifecycle, registrations);
        service = new ExamClosureApplicationService(careFinance, h.actors, h.idempotency);

        h.actAsDoctor();
        lenient().when(staff.departmentOf(h.doctorId)).thenReturn(Optional.of(h.departmentId));
        session = h.withCounts(h.session(), 0, 0, 1, 0);
        var confirmed = h.stub(h.arrivedConfirmed(session));
        inExam = h.stub(confirmed.startExam(IntakeHarness.NOW));
        appointment = h.stubAppointment(inExam, session);
        appointment.confirmScheduledPayment(inExam.reservation().clearanceId(), IntakeHarness.NOW);
        appointment.startScheduledExam(inExam.reservation().clearanceId(), IntakeHarness.NOW);
        lenient().when(h.appointments.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(h.records.save(any())).thenAnswer(call -> call.getArgument(0));
        record = stubRecord(appointment.getAppointmentId());
        lenient().when(tickets.findActiveByRegistration(any())).thenReturn(Optional.empty());
    }

    private MedicalRecord stubRecord(UUID appointmentId) {
        MedicalRecord opened = MedicalRecord.openForAppointment(inExam.patientId(), h.doctorId, h.departmentId, IntakeHarness.TODAY,
                null, appointmentId, IntakeHarness.NOW);
        lenient().when(h.records.findByIdForUpdate(opened.getRecordId())).thenReturn(Optional.of(opened));
        return opened;
    }

    private void close() {
        service.closeWithoutConclusion(record.getRecordId(), REQUEST, "key-1");
    }

    private ClinicalLabOrder labOrder(String status) {
        ClinicalLabOrder order = ClinicalLabOrder.create(record.getRecordId(), record.getPatientId(), h.doctorId, h.departmentId,
                appointment.getAppointmentId(), "CBC", LocalDate.of(2026, 11, 10), "CBC_PRICE", IntakeHarness.NOW);
        lenient().when(labOrders.findByRecordId(record.getRecordId())).thenReturn(List.of(order));
        lenient().when(labOrders.findById(order.sourceOrderId())).thenReturn(Optional.of(order));
        when(lifecycle.lifecycle(eq(order.sourceOrderId()), anyString())).thenReturn(new LabOrderLifecycleSnapshot(order.sourceOrderId(), true,
                UUID.randomUUID(), record.getRecordId(), record.getPatientId(), h.departmentId, "OUTPATIENT_VISIT",
                appointment.getAppointmentId(), status, IntakeHarness.NOW));
        return order;
    }

    private ClinicalLabOrder unreceivedLabOrder() {
        ClinicalLabOrder order = ClinicalLabOrder.create(record.getRecordId(), record.getPatientId(), h.doctorId, h.departmentId,
                appointment.getAppointmentId(), "CBC", LocalDate.of(2026, 11, 10), "CBC_PRICE", IntakeHarness.NOW);
        lenient().when(labOrders.findByRecordId(record.getRecordId())).thenReturn(List.of(order));
        lenient().when(labOrders.findById(order.sourceOrderId())).thenReturn(Optional.of(order));
        lenient().when(lifecycle.lifecycle(eq(order.sourceOrderId()), anyString())).thenReturn(new LabOrderLifecycleSnapshot(
                order.sourceOrderId(), false, null, null, null, null, null, null, null, IntakeHarness.NOW));
        return order;
    }

    private RecordLabOrderDTO withdraw(UUID sourceOrderId, String reason) {
        return service.withdrawLabOrder(record.getRecordId(), sourceOrderId, new WithdrawLabOrderRequest(reason), "key-w").data();
    }

    @Test void unreceivedOrderBlocksClosure() {
        unreceivedLabOrder();
        assertThatThrownBy(this::close).isInstanceOfSatisfying(InvalidClinicalDataException.class,
                e -> assertThat(e.getCode()).isEqualTo("CLINICAL_LAB_ORDER_NOT_FOUND"));
    }

    @Test void doctorWithdrawsOrderLabNeverReceived() {
        ClinicalLabOrder order = unreceivedLabOrder();
        RecordLabOrderDTO withdrawn = withdraw(order.sourceOrderId(), "  Bệnh nhân không đi lấy mẫu ");
        verify(labOrders).withdraw(order.sourceOrderId(), h.doctorId, "Bệnh nhân không đi lấy mẫu", IntakeHarness.NOW);
        assertThat(withdrawn.status()).isEqualTo("WITHDRAWN");
        assertThat(withdrawn.sourceOrderId()).isEqualTo(order.sourceOrderId());
    }

    @Test void orderLabAlreadyHasCannotBeWithdrawn() {
        ClinicalLabOrder order = labOrder("ORDERED");
        assertThatThrownBy(() -> withdraw(order.sourceOrderId(), "Đổi ý"))
                .isInstanceOfSatisfying(InvalidClinicalDataException.class,
                        e -> assertThat(e.getCode()).isEqualTo("CLINICAL_LAB_ORDER_RECEIVED"));
        verify(labOrders, never()).withdraw(any(), any(), any(), any());
    }

    @Test void withdrawalNeedsReason() {
        ClinicalLabOrder order = unreceivedLabOrder();
        assertThatThrownBy(() -> withdraw(order.sourceOrderId(), "   "))
                .isInstanceOfSatisfying(InvalidClinicalDataException.class,
                        e -> assertThat(e.getCode()).isEqualTo("CLINICAL_LAB_ORDER_WITHDRAWAL_INVALID"));
        verify(labOrders, never()).withdraw(any(), any(), any(), any());
    }

    @Test void otherDoctorCannotWithdraw() {
        ClinicalLabOrder order = unreceivedLabOrder();
        h.actAs("DOCTOR", UUID.randomUUID(), h.departmentId);
        assertThatThrownBy(() -> withdraw(order.sourceOrderId(), "Đổi ý"))
                .isInstanceOfSatisfying(ForbiddenOperationException.class,
                        e -> assertThat(e.getCode()).isEqualTo("CLINICAL_DOCTOR_NOT_ASSIGNED"));
        verify(labOrders, never()).withdraw(any(), any(), any(), any());
    }

    @Test void orderOfAnotherRecordCannotBeWithdrawn() {
        UUID unknown = UUID.randomUUID();
        when(labOrders.findById(unknown)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> withdraw(unknown, "Đổi ý"))
                .isInstanceOfSatisfying(InvalidClinicalDataException.class,
                        e -> assertThat(e.getCode()).isEqualTo("CLINICAL_LAB_ORDER_NOT_FOUND"));
        verify(labOrders, never()).withdraw(any(), any(), any(), any());
    }

    @Test void withdrawalRetryReturnsStoredResult() {
        ClinicalLabOrder order = unreceivedLabOrder();
        RecordLabOrderDTO stored = new RecordLabOrderDTO(order.sourceOrderId(), null, "CBC", "WITHDRAWN", IntakeHarness.NOW);
        when(h.idempotency.execute(anyString(), eq("PUT"),
                eq("/api/v2/records/" + record.getRecordId() + "/lab-orders/" + order.sourceOrderId() + "/withdrawal"),
                eq("key-w"), anyString(), eq(RecordLabOrderDTO.class), anyInt(), any(), any()))
                .thenReturn(new IdempotentCommandPort.Result<>(stored, 200, null, true));
        assertThat(withdraw(order.sourceOrderId(), "Đổi ý")).isSameAs(stored);
        verify(labOrders, never()).withdraw(any(), any(), any(), any());
    }

    @Test void assignedDoctorClosesRecordWithoutDiagnosis() {
        close();
        assertThat(record.getStatus()).isEqualTo(MedicalRecordStatus.CLOSED_WITHOUT_CONCLUSION);
        assertThat(record.getClosureReason()).isEqualTo(RecordClosureReason.PATIENT_LEFT);
        assertThat(record.getClosedByStaffId()).isEqualTo(h.doctorId);
        assertThat(record.getClosedAt()).isEqualTo(IntakeHarness.NOW);
        assertThat(record.getDiagnoses()).isEmpty();
        verify(h.records).save(record);
    }

    @Test void otherDoctorIsRefused() {
        h.actAs("DOCTOR", UUID.randomUUID(), h.departmentId);
        assertThatThrownBy(this::close).isInstanceOfSatisfying(ForbiddenOperationException.class,
                e -> assertThat(e.getCode()).isEqualTo("CLINICAL_DOCTOR_NOT_ASSIGNED"));
        assertThat(record.getStatus()).isEqualTo(MedicalRecordStatus.OPEN);
        verify(h.records, never()).save(any());
    }

    @Test void adminIsRefused() {
        h.actAs("ADMIN", h.doctorId, h.departmentId);
        assertThatThrownBy(this::close).isInstanceOf(ForbiddenOperationException.class);
        assertThat(record.getStatus()).isEqualTo(MedicalRecordStatus.OPEN);
    }

    @Test void closingMovesAppointmentAndRegistrationToCompleted() {
        close();
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        var saved = ArgumentCaptor.forClass(OutpatientRegistration.class);
        verify(h.registrations).update(any(), saved.capture(), any(), any(), eq("COMPLETED"), any());
        assertThat(saved.getValue().status()).isEqualTo(OutpatientRegistrationStatus.COMPLETED);
    }

    @Test void closingCompletesRoomTicket() {
        var ticket = ClinicQueueTicket.issue(UUID.randomUUID(), inExam.registrationId(), session, 2, IntakeHarness.ACTOR, null, 7L, IntakeHarness.NOW)
                .startExam(IntakeHarness.NOW);
        when(tickets.findActiveByRegistration(inExam.registrationId())).thenReturn(Optional.of(ticket));
        when(tickets.findByIdForUpdate(ticket.ticketId())).thenReturn(Optional.of(ticket));
        close();
        var saved = ArgumentCaptor.forClass(ClinicQueueTicket.class);
        verify(tickets).update(any(), saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(ClinicQueueTicketStatus.COMPLETED);
    }

    @Test void closingIsBlockedByPendingLabOrder() {
        labOrder("ORDERED");
        assertThatThrownBy(this::close).isInstanceOfSatisfying(InvalidClinicalDataException.class,
                e -> assertThat(e.getCode()).isEqualTo("CLINICAL_LAB_ORDER_PENDING"));
        assertThat(record.getStatus()).isEqualTo(MedicalRecordStatus.OPEN);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.IN_EXAM);
        verify(h.records, never()).save(any());
    }

    @Test void closingIsAllowedOnceOrdersAreCancelled() {
        labOrder("CANCELLED");
        close();
        assertThat(record.getStatus()).isEqualTo(MedicalRecordStatus.CLOSED_WITHOUT_CONCLUSION);
    }

    @Test void closingPublishesStatusChangeButNoRecordCompleted() {
        close();
        var events = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(h.outbox, atLeastOnce()).append(events.capture());
        assertThat(events.getAllValues()).extracting(event -> ((DomainEventEnvelope<?>) event).eventType())
                .contains("appointment.status.changed").doesNotContain("medicalrecord.completed");
    }

    @Test void resultAfterClosureDoesNotReopenRecord() {
        close();
        var results = mock(com.mediflow.clinical.application.port.out.ExternalResultRepositoryPort.class);
        UUID lab = UUID.randomUUID();
        when(results.attach(record.getRecordId(), com.mediflow.clinical.domain.model.ExternalResultType.LAB, lab, "Bình thường"))
                .thenReturn(true);
        when(h.records.findById(record.getRecordId())).thenReturn(Optional.of(record));
        new ClinicalIntegrationService(new ExternalResultApplicationService(h.records, results), h.records)
                .onLabResultCreated(lateResult(lab));
        assertThat(record.getStatus()).isEqualTo(MedicalRecordStatus.CLOSED_WITHOUT_CONCLUSION);
        assertThat(record.getClosureReason()).isEqualTo(RecordClosureReason.PATIENT_LEFT);
        assertThat(record.getClosedAt()).isEqualTo(IntakeHarness.NOW);
        assertThat(record.getCompletedAt()).isNull();
        assertThat(record.getDiagnoses()).isEmpty();
        verify(results).attach(record.getRecordId(), com.mediflow.clinical.domain.model.ExternalResultType.LAB, lab, "Bình thường");
        verify(h.records, times(1)).save(any());
    }

    /** The v1 contract Lab publishes: the binding must match the closed record or the result is refused. */
    private com.mediflow.clinical.application.dto.command.LabResultCreatedCommand lateResult(UUID lab) {
        return new com.mediflow.clinical.application.dto.command.LabResultCreatedCommand(UUID.randomUUID(), record.getRecordId(),
                lab, "Bình thường", 1, record.getPatientId(), record.getDepartmentId(), CareEpisodeType.OUTPATIENT_VISIT,
                appointment.getAppointmentId(), 1);
    }

    @Test void retrySameKeyReturnsStoredResult() {
        MedicalRecordDTO stored = mock(MedicalRecordDTO.class);
        when(h.idempotency.execute(anyString(), eq("PUT"), eq("/api/v2/records/" + record.getRecordId() + "/close-without-conclusion"),
                eq("key-1"), anyString(), eq(MedicalRecordDTO.class), anyInt(), any(), any()))
                .thenReturn(new IdempotentCommandPort.Result<>(stored, 200, null, true));
        var result = service.closeWithoutConclusion(record.getRecordId(), REQUEST, "key-1");
        assertThat(result.data()).isSameAs(stored);
        assertThat(result.replayed()).isTrue();
        assertThat(record.getStatus()).isEqualTo(MedicalRecordStatus.OPEN);
    }

    @Test void closedRecordIsNotWritable() {
        close();
        when(h.appointments.findById(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));
        when(h.records.findById(record.getRecordId())).thenReturn(Optional.of(record));
        RecordReadPolicy policy = mock(RecordReadPolicy.class);
        when(policy.forRecord(any(), any(), any(), any())).thenReturn(RecordReadDecision.allow("CARE_ASSIGNMENT"));
        RecordCapabilitiesDTO capabilities = new RecordAccessApplicationService(h.records, h.appointments, mock(RecordAccessDeclarationPort.class),
                mock(RecordAccessLogPort.class), policy, h.actors, h.correlationIds, new OutpatientMvpMode(true), new RecordAccessPolicyMode(true))
                .capabilities(record.getRecordId());
        assertThat(capabilities.canWriteContent()).isFalse();
        assertThat(capabilities.canComplete()).isFalse();
        assertThat(capabilities.writeBlockedReason()).isEqualTo("RECORD_CLOSED");
    }

    @Test void onlyContractTwoCanClose() {
        Appointment legacy = Appointment.create(inExam.patientId(), h.doctorId, h.departmentId, LocalDate.now().plusDays(1),
                java.time.LocalTime.of(9, 0), "Khám");
        when(h.appointments.findByIdForUpdate(legacy.getAppointmentId())).thenReturn(Optional.of(legacy));
        MedicalRecord legacyRecord = stubRecord(legacy.getAppointmentId());
        assertThatThrownBy(() -> service.closeWithoutConclusion(legacyRecord.getRecordId(), REQUEST, "key-2"))
                .isInstanceOfSatisfying(InvalidClinicalDataException.class,
                        e -> assertThat(e.getCode()).isEqualTo("CLINICAL_INVALID_STATUS_TRANSITION"));
        assertThat(legacyRecord.getStatus()).isEqualTo(MedicalRecordStatus.OPEN);
    }
}
