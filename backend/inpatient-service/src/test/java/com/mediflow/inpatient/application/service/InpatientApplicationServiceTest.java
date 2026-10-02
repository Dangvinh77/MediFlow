package com.mediflow.inpatient.application.service;

import com.mediflow.inpatient.application.dto.command.AdmissionRequestedCommand;
import com.mediflow.inpatient.application.dto.command.FinancialClearanceCommand;
import com.mediflow.inpatient.application.dto.command.LabResultFactCommand;
import com.mediflow.inpatient.application.dto.command.SettlementCompletedCommand;
import com.mediflow.inpatient.application.dto.event.AdmissionClosedEvent;
import com.mediflow.inpatient.application.dto.event.AdmissionStartedEvent;
import com.mediflow.inpatient.application.dto.event.DomainEventEnvelope;
import com.mediflow.inpatient.application.dto.event.MedicalDischargeApprovedEvent;
import com.mediflow.inpatient.application.dto.request.AdmitRequest;
import com.mediflow.inpatient.application.dto.request.AssignBedRequest;
import com.mediflow.inpatient.application.dto.request.CloseAdmissionRequest;
import com.mediflow.inpatient.application.dto.request.MedicalDischargeRequest;
import com.mediflow.inpatient.application.dto.request.ReleaseBedRequest;
import com.mediflow.inpatient.application.dto.request.TransferBedRequest;
import com.mediflow.inpatient.application.mapper.InpatientDtoMapper;
import com.mediflow.inpatient.application.port.out.AdmissionRepositoryPort;
import com.mediflow.inpatient.application.port.out.BedAssignmentRepositoryPort;
import com.mediflow.inpatient.application.port.out.BedRepositoryPort;
import com.mediflow.inpatient.application.port.out.ClinicalOrderReferenceRepositoryPort;
import com.mediflow.inpatient.application.port.out.DepositSuggestionPolicyPort;
import com.mediflow.inpatient.application.port.out.DischargeSummaryRepositoryPort;
import com.mediflow.inpatient.application.port.out.InpatientEventStorePort;
import com.mediflow.inpatient.application.port.out.InpatientOutboxPort;
import com.mediflow.inpatient.application.port.out.ProcessedEventPort;
import com.mediflow.inpatient.application.port.out.TreatmentEntryRepositoryPort;
import com.mediflow.inpatient.domain.exception.AdmissionRuleViolationException;
import com.mediflow.inpatient.domain.model.Admission;
import com.mediflow.inpatient.domain.model.Bed;
import com.mediflow.inpatient.domain.model.BedAssignment;
import com.mediflow.inpatient.domain.model.ClinicalOrderReference;
import com.mediflow.inpatient.domain.model.SettlementSnapshot;
import com.mediflow.inpatient.domain.model.enums.AdmissionPriority;
import com.mediflow.inpatient.domain.model.enums.AdmissionStatus;
import com.mediflow.inpatient.domain.model.enums.BedAssignmentStatus;
import com.mediflow.inpatient.domain.model.enums.BedStatus;
import com.mediflow.inpatient.domain.model.enums.CareEpisodeType;
import com.mediflow.inpatient.domain.model.enums.ClearancePurpose;
import com.mediflow.inpatient.domain.model.enums.ClinicalOrderType;
import com.mediflow.inpatient.domain.model.enums.DischargeOutcome;
import com.mediflow.inpatient.domain.model.enums.ExternalOrderStatus;
import com.mediflow.inpatient.domain.model.enums.SettlementOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InpatientApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-27T04:00:00Z");
    private static final UUID ADMISSION_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID PATIENT_ID = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID DEPARTMENT_ID = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-4000-8000-000000000004");
    private static final UUID BED_ID = UUID.fromString("00000000-0000-4000-8000-000000000005");

    @Mock private AdmissionRepositoryPort admissions;
    @Mock private BedRepositoryPort beds;
    @Mock private BedAssignmentRepositoryPort assignments;
    @Mock private TreatmentEntryRepositoryPort treatments;
    @Mock private ClinicalOrderReferenceRepositoryPort references;
    @Mock private DischargeSummaryRepositoryPort discharges;
    @Mock private ProcessedEventPort processedEvents;
    @Mock private InpatientEventStorePort eventStore;
    @Mock private InpatientOutboxPort outbox;
    @Mock private DepositSuggestionPolicyPort depositSuggestions;
    @Mock private InpatientDtoMapper mapper;

    private InpatientApplicationService service;

    @BeforeEach
    void setUp() {
        service = new InpatientApplicationService(admissions, beds, assignments, treatments,
                references, discharges, processedEvents, eventStore, outbox,
                depositSuggestions, mapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void onAdmissionRequested_duplicateEventCreatesNothing() {
        AdmissionRequestedCommand command = referral();
        when(processedEvents.tryClaim(command.maSuKien(), "admission.requested")).thenReturn(false);

        service.onAdmissionRequested(command);

        verify(admissions, never()).save(any());
        verify(eventStore, never()).appendHistory(any());
    }

    @Test
    void onAdmissionRequested_firstDeliveryCreatesOneAwaitingBedAdmission() {
        AdmissionRequestedCommand command = referral();
        when(processedEvents.tryClaim(command.maSuKien(), "admission.requested")).thenReturn(true);
        when(admissions.findByAdmissionRequestId(command.maYeuCauNoiTru())).thenReturn(Optional.empty());

        service.onAdmissionRequested(command);

        ArgumentCaptor<Admission> saved = ArgumentCaptor.forClass(Admission.class);
        verify(admissions).save(saved.capture());
        assertEquals(AdmissionStatus.AWAITING_BED, saved.getValue().status());
        verify(eventStore, times(2)).appendHistory(any());
        verify(outbox, never()).append(eq(saved.getValue().admissionId()), any());

        InOrder persistenceOrder = inOrder(admissions, eventStore);
        persistenceOrder.verify(admissions).saveAndFlush(any());
        persistenceOrder.verify(eventStore, times(2)).appendHistory(any());
        persistenceOrder.verify(admissions).save(any());
    }

    @Test
    void onFinancialClearance_wrongEpisodeTargetIsRejectedBeforeClaim() {
        FinancialClearanceCommand command = clearance(UUID.randomUUID());

        assertThrows(AdmissionRuleViolationException.class,
                () -> service.onFinancialClearance(command));

        verify(processedEvents, never()).tryClaim(any(), any());
        verify(eventStore, never()).saveClearance(any());
    }

    @Test
    void externalOrderFact_unsupportedEnvelopeVersionIsRejectedBeforeClaim() {
        LabResultFactCommand command = new LabResultFactCommand(UUID.randomUUID(), 2, "corr-lab",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, "normal", NOW);

        assertThrows(AdmissionRuleViolationException.class, () -> service.onExternalOrderFact(command));

        verify(processedEvents, never()).tryClaim(any(), any());
        verify(references, never()).findByTypeAndExternalId(any(), any());
    }

    @Test
    void onDepositClearance_withoutBed_staysAwaitingBed() {
        Admission admission = restoredAdmission(AdmissionStatus.AWAITING_BED, false, null, null, null);
        FinancialClearanceCommand command = validClearance(admission);
        when(processedEvents.tryClaim(command.maSuKien(), "financial.clearance.granted")).thenReturn(true);
        when(admissions.findByIdForUpdate(ADMISSION_ID)).thenReturn(Optional.of(admission));
        when(assignments.findActiveByAdmissionId(ADMISSION_ID)).thenReturn(Optional.empty());

        service.onFinancialClearance(command);

        assertEquals(AdmissionStatus.AWAITING_BED, admission.status());
        assertEquals(command.maXacNhan(), admission.depositClearanceId());
        verify(eventStore).saveClearance(any());
        verify(admissions).save(admission);
    }

    @Test
    void assignBed_withValidClearance_movesReadyWithoutDuplicateDepositRequest() {
        Admission admission = restoredAdmission(
                AdmissionStatus.AWAITING_BED, false, UUID.randomUUID(), null, null);
        Bed bed = Bed.create(BED_ID, DEPARTMENT_ID, "WARD-A", "ROOM-1", "BED-01", "STANDARD");
        when(admissions.findByIdForUpdate(ADMISSION_ID)).thenReturn(Optional.of(admission));
        when(assignments.findActiveByAdmissionId(ADMISSION_ID)).thenReturn(Optional.empty());
        when(beds.findByIdForUpdate(BED_ID)).thenReturn(Optional.of(bed));
        service.assign(ADMISSION_ID, new AssignBedRequest(BED_ID, ACTOR_ID), "corr-ready");

        assertEquals(AdmissionStatus.READY, admission.status());
        assertEquals(BedStatus.OCCUPIED, bed.status());
        verify(assignments).save(any(BedAssignment.class));
        verify(outbox, never()).append(any(UUID.class), any(DomainEventEnvelope.class));
    }

    @Test
    void transferBed_validReleasesOldAndAssignsNewWithoutInventedEvent() {
        UUID targetBedId = UUID.fromString("00000000-0000-4000-8000-000000000006");
        Admission admission = restoredAdmission(AdmissionStatus.ADMITTED, false, null, null, null);
        Bed oldBed = Bed.restore(BED_ID, DEPARTMENT_ID, "WARD-A", "ROOM-1", "BED-01",
                "STANDARD", BedStatus.OCCUPIED, true);
        Bed targetBed = Bed.create(targetBedId, DEPARTMENT_ID, "WARD-A", "ROOM-1", "BED-02", "STANDARD");
        BedAssignment current = BedAssignment.create(
                UUID.randomUUID(), ADMISSION_ID, BED_ID, ACTOR_ID, NOW.minusSeconds(3600));
        when(admissions.findByIdForUpdate(ADMISSION_ID)).thenReturn(Optional.of(admission));
        when(assignments.findActiveByAdmissionId(ADMISSION_ID)).thenReturn(Optional.of(current));
        when(beds.findByIdsForUpdateInOrder(any())).thenReturn(java.util.List.of(oldBed, targetBed));

        service.transfer(ADMISSION_ID,
                new TransferBedRequest(targetBedId, ACTOR_ID, "Clinical transfer"), "corr-transfer");

        assertEquals(BedStatus.AVAILABLE, oldBed.status());
        assertEquals(BedStatus.OCCUPIED, targetBed.status());
        assertEquals(BedAssignmentStatus.RELEASED, current.status());
        ArgumentCaptor<BedAssignment> replacement = ArgumentCaptor.forClass(BedAssignment.class);
        verify(assignments).save(replacement.capture());
        assertEquals(targetBedId, replacement.getValue().bedId());
        verify(outbox, never()).append(any(), any());
    }

    @Test
    void releaseBed_admittedReleasesAssignmentWithoutInventedEvent() {
        Admission admission = restoredAdmission(AdmissionStatus.ADMITTED, false, null, null, null);
        Bed bed = Bed.restore(BED_ID, DEPARTMENT_ID, "WARD-A", "ROOM-1", "BED-01",
                "STANDARD", BedStatus.OCCUPIED, true);
        BedAssignment assignment = BedAssignment.create(
                UUID.randomUUID(), ADMISSION_ID, BED_ID, ACTOR_ID, NOW.minusSeconds(3600));
        when(admissions.findByIdForUpdate(ADMISSION_ID)).thenReturn(Optional.of(admission));
        when(assignments.findActiveByAdmissionId(ADMISSION_ID)).thenReturn(Optional.of(assignment));
        when(beds.findByIdForUpdate(BED_ID)).thenReturn(Optional.of(bed));

        service.release(ADMISSION_ID, new ReleaseBedRequest(ACTOR_ID, "Medical release"), "corr-release");

        assertEquals(BedStatus.AVAILABLE, bed.status());
        assertEquals(BedAssignmentStatus.RELEASED, assignment.status());
        verify(outbox, never()).append(any(), any());
    }

    @Test
    void admit_readyAdmission_persistsTransitionAndPublishesStarted() {
        Admission admission = restoredAdmission(
                AdmissionStatus.READY, false, UUID.randomUUID(), null, null);
        Bed bed = Bed.restore(BED_ID, DEPARTMENT_ID, "WARD-A", "ROOM-1", "BED-01",
                "STANDARD", BedStatus.OCCUPIED, true);
        BedAssignment assignment = BedAssignment.create(
                UUID.randomUUID(), ADMISSION_ID, BED_ID, ACTOR_ID, NOW.minusSeconds(3600));
        when(admissions.findByIdForUpdate(ADMISSION_ID)).thenReturn(Optional.of(admission));
        when(assignments.findActiveByAdmissionId(ADMISSION_ID)).thenReturn(Optional.of(assignment));
        when(beds.findByIdForUpdate(BED_ID)).thenReturn(Optional.of(bed));

        service.admit(ADMISSION_ID, new AdmitRequest(ACTOR_ID, null), "corr-admit");

        assertEquals(AdmissionStatus.ADMITTED, admission.status());
        assertEquals(NOW, admission.admittedAt());
        ArgumentCaptor<DomainEventEnvelope<?>> event = outboxEventCaptor();
        verify(outbox).append(eq(ADMISSION_ID), event.capture());
        assertEquals("admission.started", event.getValue().loaiSuKien());
        assertEquals(1, event.getValue().phienBan());
        AdmissionStartedEvent payload = (AdmissionStartedEvent) event.getValue().duLieu();
        assertEquals(ADMISSION_ID, payload.maDotNoiTru());
        assertEquals(PATIENT_ID, payload.maBenhNhan());
        assertEquals(BED_ID, payload.maGiuong());
        assertEquals(NOW, payload.thoiGianNhapVien());
        verify(eventStore).appendHistory(any());
    }

    @Test
    void medicalDischarge_valid_staysMedicallyDischarged() {
        Admission admission = restoredAdmission(AdmissionStatus.ADMITTED, false, null, null, null);
        when(admissions.findByIdForUpdate(ADMISSION_ID)).thenReturn(Optional.of(admission));
        MedicalDischargeRequest request = new MedicalDischargeRequest(UUID.randomUUID(),
                "Pneumonia", "Antibiotic treatment", DischargeOutcome.IMPROVED,
                "Follow up after seven days", ACTOR_ID, NOW);

        service.approveMedicalDischarge(ADMISSION_ID, request, "corr-discharge");

        assertEquals(AdmissionStatus.MEDICALLY_DISCHARGED, admission.status());
        assertEquals(request.maTomTat(), admission.dischargeSummaryId());
        verify(discharges).save(any());
        ArgumentCaptor<DomainEventEnvelope<?>> event = outboxEventCaptor();
        verify(outbox).append(eq(ADMISSION_ID), event.capture());
        assertEquals("discharge.medically.approved", event.getValue().loaiSuKien());
        assertEquals(1, event.getValue().phienBan());
        MedicalDischargeApprovedEvent payload = (MedicalDischargeApprovedEvent) event.getValue().duLieu();
        assertEquals(ADMISSION_ID, payload.maDotNoiTru());
        assertEquals(PATIENT_ID, payload.maBenhNhan());
        assertEquals(request.maTomTat(), payload.maTomTat());
        assertEquals(NOW, payload.thoiGianDuyet());
    }

    @Test
    void onSettlement_duplicateEvent_appliesOnce() {
        Admission admission = restoredAdmission(
                AdmissionStatus.MEDICALLY_DISCHARGED, false, null, null, UUID.randomUUID());
        SettlementCompletedCommand command = settlement(admission, SettlementOutcome.PAID_IN_FULL);
        when(processedEvents.tryClaim(command.maSuKien(), "settlement.completed"))
                .thenReturn(true, false);
        when(admissions.findByIdForUpdate(ADMISSION_ID)).thenReturn(Optional.of(admission));

        service.onSettlementCompleted(command);
        service.onSettlementCompleted(command);

        assertEquals(command.maQuyetToan(), admission.settlementId());
        verify(eventStore, times(1)).saveSettlement(any());
        verify(admissions, times(1)).save(admission);
    }

    @Test
    void close_activeBed_rejects() {
        UUID settlementId = UUID.randomUUID();
        Admission admission = restoredAdmission(
                AdmissionStatus.MEDICALLY_DISCHARGED, false, null, settlementId, UUID.randomUUID());
        SettlementSnapshot settlement = settlementSnapshot(admission, settlementId, SettlementOutcome.PAID_IN_FULL);
        when(admissions.findByIdForUpdate(ADMISSION_ID)).thenReturn(Optional.of(admission));
        when(assignments.findActiveByAdmissionId(ADMISSION_ID)).thenReturn(Optional.of(
                BedAssignment.create(UUID.randomUUID(), ADMISSION_ID, BED_ID, ACTOR_ID, NOW.minusSeconds(3600))));
        when(eventStore.findLatestSettlement(ADMISSION_ID)).thenReturn(Optional.of(settlement));

        AdmissionRuleViolationException exception = assertThrows(AdmissionRuleViolationException.class,
                () -> service.close(ADMISSION_ID, new CloseAdmissionRequest(ACTOR_ID, null), "corr-close"));

        assertEquals("INPATIENT_ACTIVE_BED_MUST_BE_RELEASED", exception.code());
        verify(admissions, never()).save(any());
        verify(outbox, never()).append(any(), any());
    }

    @Test
    void close_additionalPaymentRequired_rejects() {
        Admission admission = restoredAdmission(
                AdmissionStatus.MEDICALLY_DISCHARGED, false, null, null, UUID.randomUUID());
        SettlementSnapshot settlement = settlementSnapshot(
                admission, UUID.randomUUID(), SettlementOutcome.ADDITIONAL_PAYMENT_REQUIRED);
        when(admissions.findByIdForUpdate(ADMISSION_ID)).thenReturn(Optional.of(admission));
        when(assignments.findActiveByAdmissionId(ADMISSION_ID)).thenReturn(Optional.empty());
        when(eventStore.findLatestSettlement(ADMISSION_ID)).thenReturn(Optional.of(settlement));

        AdmissionRuleViolationException exception = assertThrows(AdmissionRuleViolationException.class,
                () -> service.close(ADMISSION_ID, new CloseAdmissionRequest(ACTOR_ID, null), "corr-close"));

        assertEquals("INPATIENT_SETTLEMENT_REQUIRED", exception.code());
        verify(admissions, never()).save(any());
        verify(outbox, never()).append(any(), any());
    }

    @Test
    void close_validPublishesAdministrativeCloseAfterMedicalDischarge() {
        UUID settlementId = UUID.randomUUID();
        Admission admission = restoredAdmission(
                AdmissionStatus.MEDICALLY_DISCHARGED, false, null, settlementId, UUID.randomUUID());
        SettlementSnapshot settlement = settlementSnapshot(admission, settlementId, SettlementOutcome.PAID_IN_FULL);
        when(admissions.findByIdForUpdate(ADMISSION_ID)).thenReturn(Optional.of(admission));
        when(assignments.findActiveByAdmissionId(ADMISSION_ID)).thenReturn(Optional.empty());
        when(eventStore.findLatestSettlement(ADMISSION_ID)).thenReturn(Optional.of(settlement));

        service.close(ADMISSION_ID, new CloseAdmissionRequest(ACTOR_ID, null), "corr-close");

        assertEquals(AdmissionStatus.CLOSED, admission.status());
        ArgumentCaptor<DomainEventEnvelope<?>> event = outboxEventCaptor();
        verify(outbox).append(eq(ADMISSION_ID), event.capture());
        assertEquals("admission.closed", event.getValue().loaiSuKien());
        assertEquals(1, event.getValue().phienBan());
        AdmissionClosedEvent payload = (AdmissionClosedEvent) event.getValue().duLieu();
        assertEquals(ADMISSION_ID, payload.maDotNoiTru());
        assertEquals(PATIENT_ID, payload.maBenhNhan());
        assertEquals(settlementId, payload.maQuyetToan());
        assertEquals(NOW, payload.thoiGianDong());
    }

    @Test
    void externalOrderFact_referenceForAnotherAdmissionIsRejected() {
        UUID orderId = UUID.randomUUID();
        Admission admission = restoredAdmission(AdmissionStatus.ADMITTED, false, null, null, null);
        LabResultFactCommand command = new LabResultFactCommand(UUID.randomUUID(), 1, "corr-lab",
                orderId, ADMISSION_ID, PATIENT_ID, 2, "normal", NOW);
        ClinicalOrderReference wrongReference = ClinicalOrderReference.create(
                UUID.randomUUID(), ClinicalOrderType.LAB_TEST, orderId,
                ExternalOrderStatus.IN_PROGRESS, null, 1);
        when(processedEvents.tryClaim(command.maSuKien(), "lab.result.created")).thenReturn(true);
        when(admissions.findByIdForUpdate(ADMISSION_ID)).thenReturn(Optional.of(admission));
        when(references.findByTypeAndExternalId(ClinicalOrderType.LAB_TEST, orderId))
                .thenReturn(Optional.of(wrongReference));

        AdmissionRuleViolationException exception = assertThrows(AdmissionRuleViolationException.class,
                () -> service.onExternalOrderFact(command));

        assertEquals("INPATIENT_EXTERNAL_ORDER_MISMATCH", exception.code());
        verify(references, never()).save(any());
    }

    private static AdmissionRequestedCommand referral() {
        return new AdmissionRequestedCommand(UUID.randomUUID(), 1, NOW, UUID.randomUUID().toString(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Pneumonia", AdmissionPriority.ROUTINE, false, NOW);
    }

    private static FinancialClearanceCommand clearance(UUID admissionId) {
        return new FinancialClearanceCommand(UUID.randomUUID(), 1, NOW, UUID.randomUUID().toString(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), ClearancePurpose.ADMISSION_DEPOSIT,
                admissionId, new BigDecimal("100000"), "VND", "CASH", null, false);
    }

    private static FinancialClearanceCommand validClearance(Admission admission) {
        return new FinancialClearanceCommand(UUID.randomUUID(), 1, NOW, "corr-clearance",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), admission.patientId(),
                CareEpisodeType.ADMISSION, admission.admissionId(), ClearancePurpose.ADMISSION_DEPOSIT,
                admission.admissionId(), new BigDecimal("1000000"), "VND", "CASH",
                NOW.plusSeconds(3600), false);
    }

    private static SettlementCompletedCommand settlement(Admission admission, SettlementOutcome outcome) {
        return new SettlementCompletedCommand(UUID.randomUUID(), 1, NOW, "corr-settlement",
                UUID.randomUUID(), admission.admissionId(), UUID.randomUUID(),
                new BigDecimal("1000000"), BigDecimal.ZERO, new BigDecimal("1000000"),
                new BigDecimal("1000000"), BigDecimal.ZERO, BigDecimal.ZERO, outcome, NOW);
    }

    private static SettlementSnapshot settlementSnapshot(Admission admission, UUID settlementId,
                                                         SettlementOutcome outcome) {
        return new SettlementSnapshot(settlementId, UUID.randomUUID(), admission.admissionId(),
                UUID.randomUUID(), new BigDecimal("1000000"), BigDecimal.ZERO,
                new BigDecimal("1000000"), new BigDecimal("1000000"), BigDecimal.ZERO,
                BigDecimal.ZERO, outcome, NOW);
    }

    private static Admission restoredAdmission(AdmissionStatus status, boolean emergency,
                                               UUID clearanceId, UUID settlementId, UUID summaryId) {
        Instant admittedAt = status == AdmissionStatus.ADMITTED
                || status == AdmissionStatus.MEDICALLY_DISCHARGED || status == AdmissionStatus.CLOSED
                ? NOW.minusSeconds(7200) : null;
        Instant medicallyDischargedAt = status == AdmissionStatus.MEDICALLY_DISCHARGED
                || status == AdmissionStatus.CLOSED ? NOW.minusSeconds(3600) : null;
        Instant closedAt = status == AdmissionStatus.CLOSED ? NOW : null;
        return Admission.restore(ADMISSION_ID, UUID.randomUUID(), PATIENT_ID, UUID.randomUUID(),
                ACTOR_ID, "Pneumonia", NOW.minusSeconds(10800), DEPARTMENT_ID,
                AdmissionPriority.ROUTINE, emergency, status,
                status == AdmissionStatus.REQUESTED ? null : NOW.minusSeconds(10000),
                clearanceId, clearanceId == null ? null : NOW.plusSeconds(3600), null,
                settlementId, null, summaryId, admittedAt, medicallyDischargedAt,
                closedAt, null, null);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentCaptor<DomainEventEnvelope<?>> outboxEventCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(DomainEventEnvelope.class);
    }
}
