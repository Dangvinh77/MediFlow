package com.mediflow.lab.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.lab.application.dto.command.FinancialClearanceCommand;
import com.mediflow.lab.application.dto.request.CreateLabRequest;
import com.mediflow.lab.application.dto.request.EmergencyOverrideRequest;
import com.mediflow.lab.application.dto.request.StartLabTestRequest;
import com.mediflow.lab.application.event.DomainEventEnvelope;
import com.mediflow.lab.application.event.LabRequestV2Payload;
import com.mediflow.lab.application.mapper.LabTestDtoMapper;
import com.mediflow.lab.application.port.out.AuthenticatedStaffIdPort;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.port.out.LabClearanceRepositoryPort;
import com.mediflow.lab.application.port.out.LabEmergencyOverrideRepositoryPort;
import com.mediflow.lab.application.port.out.LabEventPublisherPort;
import com.mediflow.lab.application.port.out.LabOutboxPort;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.domain.exception.LabRuleException;
import com.mediflow.lab.domain.model.CareEpisodeType;
import com.mediflow.lab.domain.model.ClearancePurpose;
import com.mediflow.lab.domain.model.LabFinancialClearance;
import com.mediflow.lab.domain.model.LabTest;
import com.mediflow.lab.domain.model.LabTestStatus;

class LabCareFinanceV2ApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");
    private static final LocalDate REQUESTED_DATE = LocalDate.of(2026, 9, 20);

    private final LabTestRepositoryPort tests = mock(LabTestRepositoryPort.class);
    private final LabEventPublisherPort compatibilityEvents = mock(LabEventPublisherPort.class);
    private final LabTestDtoMapper mapper = mock(LabTestDtoMapper.class);
    private final CorrelationIdProvider correlations = mock(CorrelationIdProvider.class);
    private final LabOutboxPort outbox = mock(LabOutboxPort.class);
    private final LabClearanceRepositoryPort clearances = mock(LabClearanceRepositoryPort.class);
    private final LabEmergencyOverrideRepositoryPort overrides = mock(LabEmergencyOverrideRepositoryPort.class);
    private final AuthenticatedStaffIdPort staffIds = mock(AuthenticatedStaffIdPort.class);
    private final LabApplicationService service = new LabApplicationService(tests, compatibilityEvents,
            mapper, correlations, outbox, clearances, overrides, staffIds, true);

    @Test
    void create_v2AppendsRequestTimeChargeFactToOutbox() {
        UUID sourceOrderId = UUID.randomUUID();
        UUID episodeId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        when(correlations.currentOrCreate()).thenReturn(correlationId);
        when(tests.existsBySourceOrderId(sourceOrderId)).thenReturn(false);
        when(tests.save(any())).thenAnswer(call -> call.getArgument(0));
        CreateLabRequest request = v2Request(sourceOrderId, episodeId);

        service.create(request);

        ArgumentCaptor<DomainEventEnvelope<?>> envelope = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(outbox).append(envelope.capture());
        assertThat(envelope.getValue().eventType()).isEqualTo("lab.request.created");
        assertThat(envelope.getValue().version()).isEqualTo(1);
        assertThat(envelope.getValue().correlationId()).isEqualTo(correlationId.toString());
        LabRequestV2Payload payload = (LabRequestV2Payload) envelope.getValue().payload();
        assertThat(payload.sourceType()).isEqualTo("LAB_TEST");
        assertThat(payload.sourceId()).isEqualTo(payload.labId());
        assertThat(payload.sourceOrderId()).isEqualTo(sourceOrderId);
        assertThat(payload.careEpisodeId()).isEqualTo(episodeId);
        verify(compatibilityEvents, never()).publishRequestCreated(any());
    }

    @Test
    void create_v2DuplicateSourceOrder_rejectsBeforePersisting() {
        UUID sourceOrderId = UUID.randomUUID();
        when(tests.existsBySourceOrderId(sourceOrderId)).thenReturn(true);

        assertThatThrownBy(() -> service.create(v2Request(sourceOrderId, UUID.randomUUID())))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting(DuplicateResourceException.class::cast)
                .extracting(DuplicateResourceException::getCode)
                .isEqualTo("LAB_DUPLICATE_SOURCE_ORDER");
        verify(tests, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void start_awaitingPaymentWithoutClearance_rejects() {
        LabTest test = v2Test();
        when(tests.findByIdForUpdate(test.getTestId())).thenReturn(Optional.of(test));
        when(clearances.findValidByTestId(eq(test.getTestId()), any(Instant.class))).thenReturn(Optional.empty());
        when(clearances.findLatestByTestId(test.getTestId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(test.getTestId(), new StartLabTestRequest(null)))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_CLEARANCE_REQUIRED");
        verify(tests, never()).save(any());
        verifyNoInteractions(overrides);
    }

    @Test
    void onClearance_explicitIdsUnlockOnlyTargets() {
        UUID patientId = UUID.randomUUID();
        UUID episodeId = UUID.randomUUID();
        LabTest first = v2Test(patientId, CareEpisodeType.OUTPATIENT_VISIT, episodeId);
        LabTest second = v2Test(patientId, CareEpisodeType.OUTPATIENT_VISIT, episodeId);
        UUID eventId = UUID.randomUUID();
        when(tests.findByIdForUpdate(first.getTestId())).thenReturn(Optional.of(first));
        when(tests.findByIdForUpdate(second.getTestId())).thenReturn(Optional.of(second));
        when(clearances.claimAndSave(eq(eventId), anyList())).thenReturn(true);
        when(tests.save(any())).thenAnswer(call -> call.getArgument(0));

        service.onFinancialClearance(clearanceCommand(eventId, first, second));

        assertThat(first.getStatus()).isEqualTo(LabTestStatus.READY);
        assertThat(second.getStatus()).isEqualTo(LabTestStatus.READY);
        ArgumentCaptor<List<LabFinancialClearance>> targets = ArgumentCaptor.forClass(List.class);
        verify(clearances).claimAndSave(eq(eventId), targets.capture());
        assertThat(targets.getValue()).extracting(LabFinancialClearance::testId)
                .containsExactly(first.getTestId(), second.getTestId());
        verify(tests).save(first);
        verify(tests).save(second);
    }

    @Test
    void onClearance_wrongEpisode_rejectsAndDoesNotSaveTest() {
        LabTest test = v2Test();
        UUID eventId = UUID.randomUUID();
        when(tests.findByIdForUpdate(test.getTestId())).thenReturn(Optional.of(test));
        when(clearances.claimAndSave(eq(eventId), anyList())).thenReturn(true);

        assertThatThrownBy(() -> service.onFinancialClearance(clearanceCommand(
                eventId, test, CareEpisodeType.ADMISSION, UUID.randomUUID())))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_CLEARANCE_TARGET_MISMATCH");
        verify(tests, never()).save(any());
    }

    @Test
    void onClearance_duplicateEventDoesNotUnlockAgain() {
        LabTest test = v2Test();
        UUID eventId = UUID.randomUUID();
        when(tests.findByIdForUpdate(test.getTestId())).thenReturn(Optional.of(test));
        when(clearances.claimAndSave(eq(eventId), anyList())).thenReturn(false);

        service.onFinancialClearance(clearanceCommand(eventId, test));

        assertThat(test.getStatus()).isEqualTo(LabTestStatus.AWAITING_PAYMENT);
        verify(tests, never()).save(any());
    }

    @Test
    void start_emergencyOverridePersistsAuditWithoutMarkingPaid() {
        LabTest test = v2Test();
        UUID actor = UUID.randomUUID();
        when(tests.findByIdForUpdate(test.getTestId())).thenReturn(Optional.of(test));
        when(clearances.findValidByTestId(eq(test.getTestId()), any(Instant.class))).thenReturn(Optional.empty());
        when(clearances.findLatestByTestId(test.getTestId())).thenReturn(Optional.empty());
        EmergencyOverrideRequest approval = new EmergencyOverrideRequest(UUID.randomUUID(), actor, "DOCTOR",
                "Urgent diagnostic care", NOW.minusSeconds(1));

        service.start(test.getTestId(), new StartLabTestRequest(approval));

        assertThat(test.getStatus()).isEqualTo(LabTestStatus.IN_PROGRESS);
        assertThat(test.isPaid()).isFalse();
        verify(overrides).save(any());
        verify(tests).save(test);
    }

    private static CreateLabRequest v2Request(UUID sourceOrderId, UUID episodeId) {
        return new CreateLabRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "CBC",
                REQUESTED_DATE, sourceOrderId, CareEpisodeType.OUTPATIENT_VISIT, episodeId, "LAB-CBC");
    }

    private static LabTest v2Test() {
        return LabTest.createV2(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), "CBC", "LAB-CBC", REQUESTED_DATE);
    }

    private static LabTest v2Test(UUID patientId, CareEpisodeType episodeType, UUID episodeId) {
        return LabTest.createV2(UUID.randomUUID(), patientId, UUID.randomUUID(), UUID.randomUUID(),
                episodeType, episodeId, "CBC", "LAB-CBC", REQUESTED_DATE);
    }

    private static FinancialClearanceCommand clearanceCommand(UUID eventId, LabTest... tests) {
        return clearanceCommand(eventId, java.util.Arrays.stream(tests).map(LabTest::getTestId).toList(),
                tests[0].getPatientId(), tests[0].getCareEpisodeType(), tests[0].getCareEpisodeId());
    }

    private static FinancialClearanceCommand clearanceCommand(
            UUID eventId, LabTest test, CareEpisodeType episodeType, UUID episodeId) {
        return clearanceCommand(eventId, List.of(test.getTestId()), test.getPatientId(), episodeType, episodeId);
    }

    private static FinancialClearanceCommand clearanceCommand(
            UUID eventId, List<UUID> testIds, UUID patientId, CareEpisodeType episodeType, UUID episodeId) {
        return new FinancialClearanceCommand(eventId, "financial.clearance.granted", 1, NOW,
                UUID.randomUUID().toString(), "billing-service", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), patientId, episodeType, episodeId, ClearancePurpose.LAB_TEST,
                testIds, new BigDecimal("250000.00"), "VND", null, false);
    }
}
