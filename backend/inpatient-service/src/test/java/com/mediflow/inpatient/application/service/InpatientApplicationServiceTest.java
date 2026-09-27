package com.mediflow.inpatient.application.service;

import com.mediflow.inpatient.application.dto.command.AdmissionRequestedCommand;
import com.mediflow.inpatient.application.dto.command.FinancialClearanceCommand;
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
import com.mediflow.inpatient.domain.model.enums.AdmissionPriority;
import com.mediflow.inpatient.domain.model.enums.AdmissionStatus;
import com.mediflow.inpatient.domain.model.enums.CareEpisodeType;
import com.mediflow.inpatient.domain.model.enums.ClearancePurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InpatientApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-27T04:00:00Z");

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
    }

    @Test
    void onFinancialClearance_wrongEpisodeTargetIsRejectedBeforeClaim() {
        FinancialClearanceCommand command = clearance(UUID.randomUUID());

        assertThrows(AdmissionRuleViolationException.class,
                () -> service.onFinancialClearance(command));

        verify(processedEvents, never()).tryClaim(any(), any());
        verify(eventStore, never()).saveClearance(any());
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
}
