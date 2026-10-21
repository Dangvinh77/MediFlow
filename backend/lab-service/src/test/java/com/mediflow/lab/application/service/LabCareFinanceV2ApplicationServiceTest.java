package com.mediflow.lab.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.lab.application.dto.context.LabClinicalOrderContext;
import com.mediflow.lab.application.exception.LabUpstreamUnavailableException;
import com.mediflow.lab.application.dto.command.FinancialClearanceCommand;
import com.mediflow.lab.application.dto.request.AddResultRequest;
import com.mediflow.lab.application.dto.request.CancelLabTestRequest;
import com.mediflow.lab.application.dto.request.CreateLabRequest;
import com.mediflow.lab.application.dto.request.EmergencyOverrideRequest;
import com.mediflow.lab.application.dto.request.LabResultItem;
import com.mediflow.lab.application.dto.request.StartLabTestRequest;
import com.mediflow.lab.application.event.DomainEventEnvelope;
import com.mediflow.lab.application.event.LabRequestV2Payload;
import com.mediflow.lab.application.event.LabResultV2Payload;
import com.mediflow.lab.application.mapper.LabTestDtoMapper;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContext;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContextPort;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.port.out.LabClearanceRepositoryPort;
import com.mediflow.lab.application.port.out.LabClinicalOrderContextPort;
import com.mediflow.lab.application.port.out.LabEmergencyOverrideRepositoryPort;
import com.mediflow.lab.application.port.out.LabEventPublisherPort;
import com.mediflow.lab.application.port.out.LabExecutionAuthorityPort;
import com.mediflow.lab.application.port.out.LabOutboxPort;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.domain.exception.LabRuleException;
import com.mediflow.lab.domain.model.CareEpisodeType;
import com.mediflow.lab.domain.model.ClearancePurpose;
import com.mediflow.lab.domain.model.LabFinancialClearance;
import com.mediflow.lab.domain.model.LabEmergencyOverride;
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
    private final LabExecutionAuthorityPort executionAuthority = mock(LabExecutionAuthorityPort.class);
    private final AuthenticatedStaffContextPort authenticatedStaff = mock(AuthenticatedStaffContextPort.class);
    private final LabApplicationService service = new LabApplicationService(tests, compatibilityEvents,
            mapper, correlations, outbox, clearances, overrides, authenticatedStaff, true,
            false, Clock.fixed(NOW, ZoneOffset.UTC), null, null, executionAuthority);

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
        assertThat(envelope.getValue().occurredAt()).isEqualTo(NOW);
        assertThat(envelope.getValue().correlationId()).isEqualTo(correlationId.toString());
        LabRequestV2Payload payload = (LabRequestV2Payload) envelope.getValue().payload();
        assertThat(payload.sourceType()).isEqualTo("LAB_TEST");
        assertThat(payload.sourceId()).isEqualTo(payload.labId());
        assertThat(payload.sourceOrderId()).isEqualTo(sourceOrderId);
        assertThat(payload.careEpisodeId()).isEqualTo(episodeId);
        assertThat(payload.requestedAt()).isEqualTo(NOW);
        verify(compatibilityEvents, never()).publishRequestCreated(any());
    }

    @Test
    void outpatientMvp_v1CreateFailsClosedUntilClinicalOrderAuthorityExists() {
        when(authenticatedStaff.currentStaff()).thenReturn(Optional.of(
                new AuthenticatedStaffContext(UUID.randomUUID(), "DOCTOR")));
        LabApplicationService outpatient = new LabApplicationService(tests, compatibilityEvents, mapper,
                correlations, outbox, clearances, overrides, authenticatedStaff, true, true,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> outpatient.create(v2Request(UUID.randomUUID(), UUID.randomUUID())))
                .isInstanceOf(LabUpstreamUnavailableException.class);
        verify(tests, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void outpatientMvp_v1AcceptsOnlyExactPersistedClinicalOrderAndVerifiedDoctor() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001001");
        UUID recordId = UUID.fromString("00000000-0000-0000-0000-000000001002");
        UUID patientId = UUID.fromString("00000000-0000-0000-0000-000000001003");
        UUID doctorId = UUID.fromString("00000000-0000-0000-0000-000000001004");
        UUID departmentId = UUID.fromString("00000000-0000-0000-0000-000000001005");
        UUID episodeId = UUID.fromString("00000000-0000-0000-0000-000000001006");
        UUID correlationId = UUID.fromString("00000000-0000-0000-0000-000000001099");
        AuthenticatedStaffContext actor = new AuthenticatedStaffContext(doctorId, "DOCTOR");
        LabClinicalOrderContextPort contexts = mock(LabClinicalOrderContextPort.class);
        com.mediflow.lab.application.port.in.CreateLabRequestUseCase creation = mock(
                com.mediflow.lab.application.port.in.CreateLabRequestUseCase.class);
        LabClinicalOrderContext authority = new LabClinicalOrderContext(orderId, recordId, patientId, doctorId,
                departmentId, CareEpisodeType.OUTPATIENT_VISIT, episodeId, "CBC", REQUESTED_DATE,
                "LAB-CBC", NOW.minusSeconds(1), NOW);
        CreateLabRequest request = new CreateLabRequest(recordId, patientId, departmentId, "CBC",
                REQUESTED_DATE, orderId, CareEpisodeType.OUTPATIENT_VISIT, episodeId, "LAB-CBC");
        when(authenticatedStaff.currentStaff()).thenReturn(Optional.of(actor));
        when(correlations.currentOrCreate()).thenReturn(correlationId);
        when(contexts.findById(orderId, correlationId.toString(), actor)).thenReturn(Optional.of(authority));
        when(creation.createCareFinance(request, correlationId.toString())).thenReturn(null);

        LabApplicationService outpatient = outpatient(contexts, creation);

        outpatient.create(request);

        verify(contexts).findById(orderId, correlationId.toString(), actor);
        verify(creation).createCareFinance(request, correlationId.toString());
        verifyNoInteractions(outbox);
    }

    @Test
    void outpatientMvp_v1RejectsEveryCallerFieldThatDiffersFromClinicalAuthority() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001001");
        UUID recordId = UUID.fromString("00000000-0000-0000-0000-000000001002");
        UUID patientId = UUID.fromString("00000000-0000-0000-0000-000000001003");
        UUID doctorId = UUID.fromString("00000000-0000-0000-0000-000000001004");
        UUID departmentId = UUID.fromString("00000000-0000-0000-0000-000000001005");
        UUID episodeId = UUID.fromString("00000000-0000-0000-0000-000000001006");
        AuthenticatedStaffContext actor = new AuthenticatedStaffContext(doctorId, "DOCTOR");
        LabClinicalOrderContextPort contexts = mock(LabClinicalOrderContextPort.class);
        com.mediflow.lab.application.port.in.CreateLabRequestUseCase creation = mock(
                com.mediflow.lab.application.port.in.CreateLabRequestUseCase.class);
        LabClinicalOrderContext authority = new LabClinicalOrderContext(orderId, recordId, patientId, doctorId,
                departmentId, CareEpisodeType.OUTPATIENT_VISIT, episodeId, "CBC", REQUESTED_DATE,
                "LAB-CBC", NOW.minusSeconds(1), NOW);
        when(authenticatedStaff.currentStaff()).thenReturn(Optional.of(actor));
        when(correlations.currentOrCreate()).thenReturn(UUID.randomUUID());
        when(contexts.findById(any(UUID.class), anyString(), any())).thenReturn(Optional.of(authority));
        LabApplicationService outpatient = outpatient(contexts, creation);
        List<CreateLabRequest> changedRequests = List.of(
                new CreateLabRequest(UUID.randomUUID(), patientId, departmentId, "CBC", REQUESTED_DATE,
                        orderId, CareEpisodeType.OUTPATIENT_VISIT, episodeId, "LAB-CBC"),
                new CreateLabRequest(recordId, UUID.randomUUID(), departmentId, "CBC", REQUESTED_DATE,
                        orderId, CareEpisodeType.OUTPATIENT_VISIT, episodeId, "LAB-CBC"),
                new CreateLabRequest(recordId, patientId, UUID.randomUUID(), "CBC", REQUESTED_DATE,
                        orderId, CareEpisodeType.OUTPATIENT_VISIT, episodeId, "LAB-CBC"),
                new CreateLabRequest(recordId, patientId, departmentId, "BMP", REQUESTED_DATE,
                        orderId, CareEpisodeType.OUTPATIENT_VISIT, episodeId, "LAB-CBC"),
                new CreateLabRequest(recordId, patientId, departmentId, "CBC", REQUESTED_DATE.plusDays(1),
                        orderId, CareEpisodeType.OUTPATIENT_VISIT, episodeId, "LAB-CBC"),
                new CreateLabRequest(recordId, patientId, departmentId, "CBC", REQUESTED_DATE,
                        UUID.randomUUID(), CareEpisodeType.OUTPATIENT_VISIT, episodeId, "LAB-CBC"),
                new CreateLabRequest(recordId, patientId, departmentId, "CBC", REQUESTED_DATE,
                        orderId, CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), "LAB-CBC"),
                new CreateLabRequest(recordId, patientId, departmentId, "CBC", REQUESTED_DATE,
                        orderId, CareEpisodeType.OUTPATIENT_VISIT, episodeId, "LAB-OTHER"));

        for (CreateLabRequest changed : changedRequests) {
            assertThatThrownBy(() -> outpatient.create(changed)).isInstanceOf(LabRuleException.class)
                    .extracting(LabRuleException.class::cast).extracting(LabRuleException::getCode)
                    .isEqualTo("LAB_SOURCE_ORDER_MISMATCH");
        }
        when(authenticatedStaff.currentStaff()).thenReturn(Optional.of(
                new AuthenticatedStaffContext(UUID.randomUUID(), "DOCTOR")));
        assertThatThrownBy(() -> outpatient.create(new CreateLabRequest(recordId, patientId, departmentId,
                "CBC", REQUESTED_DATE, orderId, CareEpisodeType.OUTPATIENT_VISIT, episodeId, "LAB-CBC")))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast).extracting(LabRuleException::getCode)
                .isEqualTo("LAB_SOURCE_ORDER_MISMATCH");
        verify(creation, never()).createCareFinance(any(), anyString());
        verifyNoInteractions(tests, outbox);
    }

    @Test
    void outpatientMvp_v1RejectsAdmissionOrdersBeforeClinicalLookup() {
        LabClinicalOrderContextPort contexts = mock(LabClinicalOrderContextPort.class);
        com.mediflow.lab.application.port.in.CreateLabRequestUseCase creation = mock(
                com.mediflow.lab.application.port.in.CreateLabRequestUseCase.class);
        LabApplicationService outpatient = outpatient(contexts, creation);
        CreateLabRequest admission = new CreateLabRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CBC", REQUESTED_DATE, UUID.randomUUID(), CareEpisodeType.ADMISSION, UUID.randomUUID(), "LAB-CBC");

        assertThatThrownBy(() -> outpatient.create(admission)).isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast).extracting(LabRuleException::getCode)
                .isEqualTo("LAB_ADMISSION_DEFERRED");
        verifyNoInteractions(contexts, creation, tests, outbox);
    }

    @Test
    void outpatientMvp_v1RequiresVerifiedDoctorOrAdministrator() {
        LabClinicalOrderContextPort contexts = mock(LabClinicalOrderContextPort.class);
        com.mediflow.lab.application.port.in.CreateLabRequestUseCase creation = mock(
                com.mediflow.lab.application.port.in.CreateLabRequestUseCase.class);
        when(authenticatedStaff.currentStaff()).thenReturn(Optional.empty());
        LabApplicationService outpatient = outpatient(contexts, creation);

        assertThatThrownBy(() -> outpatient.create(v2Request(UUID.randomUUID(), UUID.randomUUID())))
                .isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(contexts, creation, tests, outbox);
    }

    @Test
    void outpatientMvp_v1TreatsConfirmedAbsenceAndUpstreamFailureSeparately() {
        UUID orderId = UUID.randomUUID();
        UUID episodeId = UUID.randomUUID();
        LabClinicalOrderContextPort contexts = mock(LabClinicalOrderContextPort.class);
        com.mediflow.lab.application.port.in.CreateLabRequestUseCase creation = mock(
                com.mediflow.lab.application.port.in.CreateLabRequestUseCase.class);
        when(authenticatedStaff.currentStaff()).thenReturn(Optional.of(
                new AuthenticatedStaffContext(UUID.randomUUID(), "DOCTOR")));
        when(correlations.currentOrCreate()).thenReturn(UUID.randomUUID());
        when(contexts.findById(eq(orderId), anyString(), any())).thenReturn(Optional.empty());
        LabApplicationService outpatient = outpatient(contexts, creation);
        CreateLabRequest request = v2Request(orderId, episodeId);

        assertThatThrownBy(() -> outpatient.create(request)).isInstanceOf(ResourceNotFoundException.class)
                .extracting(ResourceNotFoundException.class::cast)
                .extracting(ResourceNotFoundException::getCode).isEqualTo("LAB_SOURCE_ORDER_NOT_FOUND");
        when(contexts.findById(eq(orderId), anyString(), any())).thenThrow(new LabUpstreamUnavailableException());
        assertThatThrownBy(() -> outpatient.create(request)).isInstanceOf(LabUpstreamUnavailableException.class);
        verify(creation, never()).createCareFinance(any(), anyString());
        verifyNoInteractions(tests, outbox);
    }

    @Test
    void outpatientMvp_refusesHistoricalAdmissionTestMutations() {
        UUID episodeId = UUID.randomUUID();
        LabTest admissionTest = LabTest.createV2(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), CareEpisodeType.ADMISSION, episodeId, "CBC", "LAB-CBC", REQUESTED_DATE);
        LabApplicationService outpatient = new LabApplicationService(tests, compatibilityEvents, mapper,
                correlations, outbox, clearances, overrides, authenticatedStaff, true, true,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(tests.findByIdForUpdate(admissionTest.getTestId())).thenReturn(Optional.of(admissionTest));

        assertThatThrownBy(() -> outpatient.start(admissionTest.getTestId(), null))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_ADMISSION_DEFERRED");
        verify(tests, never()).save(any());
        verifyNoInteractions(clearances);
    }

    @Test
    void outpatientMvp_quarantinesAdmissionClearanceBeforeClaimingIt() {
        UUID episodeId = UUID.randomUUID();
        LabTest admissionTest = LabTest.createV2(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), CareEpisodeType.ADMISSION, episodeId, "CBC", "LAB-CBC", REQUESTED_DATE);
        LabApplicationService outpatient = new LabApplicationService(tests, compatibilityEvents, mapper,
                correlations, outbox, clearances, overrides, authenticatedStaff, true, true,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> outpatient.onFinancialClearance(clearanceCommand(
                UUID.randomUUID(), admissionTest, CareEpisodeType.ADMISSION, episodeId)))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_ADMISSION_DEFERRED");
        verifyNoInteractions(clearances);
    }

    @Test
    void outpatientMvp_v1MutationStillRequiresV2GateWhenHistoricalVisitExists() {
        LabTest test = v2Test();
        LabApplicationService outpatient = new LabApplicationService(tests, compatibilityEvents, mapper,
                correlations, outbox, clearances, overrides, authenticatedStaff, false, true,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(tests.findByIdForUpdate(test.getTestId())).thenReturn(Optional.of(test));

        assertThatThrownBy(() -> outpatient.changeStatus(test.getTestId(), LabTestStatus.CANCELLED))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_CARE_FINANCE_V2_DISABLED");
        assertThat(test.getStatus()).isEqualTo(LabTestStatus.AWAITING_PAYMENT);
        verify(tests, never()).save(any());
    }

    @Test
    void addResults_v2UsesOneClockInstantForEnvelopeAndPayload() {
        LabTest test = v2Test();
        UUID verifiedBy = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        LabEmergencyOverride approval = LabEmergencyOverride.approve(UUID.randomUUID(), test.getTestId(),
                test.getPatientId(), test.getCareEpisodeType(), test.getCareEpisodeId(), verifiedBy,
                "ADMIN", "Urgent diagnostic care", NOW.minusSeconds(1));
        test.start(null, approval, NOW);
        when(tests.findByIdForUpdate(test.getTestId())).thenReturn(Optional.of(test));
        when(tests.save(test)).thenReturn(test);
        when(authenticatedStaff.currentStaff())
                .thenReturn(Optional.of(new AuthenticatedStaffContext(verifiedBy, "LAB_TECH")));
        when(correlations.currentOrCreate()).thenReturn(correlationId);

        service.addResults(test.getTestId(), new AddResultRequest(
                List.of(new LabResultItem("Hemoglobin", "13.5", "g/dL", "12-16")),
                "Normal", REQUESTED_DATE));

        ArgumentCaptor<DomainEventEnvelope<?>> envelope = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(outbox).append(envelope.capture());
        assertThat(envelope.getValue().occurredAt()).isEqualTo(NOW);
        assertThat(envelope.getValue().correlationId()).isEqualTo(correlationId.toString());
        LabResultV2Payload payload = (LabResultV2Payload) envelope.getValue().payload();
        assertThat(payload.completedAt()).isEqualTo(NOW);
        assertThat(payload.verifiedBy()).isEqualTo(verifiedBy);
        assertThat(payload.resultVersion()).isEqualTo(1);
        verifyNoInteractions(clearances);
    }

    @Test
    void addResults_v2CompletesWithTheExactPaidClearanceBoundToTest() {
        LabTest test = v2Test();
        UUID verifiedBy = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        LabFinancialClearance clearance = LabFinancialClearance.grant(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), test.getTestId(), test.getPatientId(),
                test.getCareEpisodeType(), test.getCareEpisodeId(), ClearancePurpose.LAB_TEST,
                new BigDecimal("250000.00"), "VND", NOW.plusSeconds(60), false, NOW.minusSeconds(60));
        test.grantClearance(clearance);
        test.markPaid();
        test.start(clearance, null, NOW.minusSeconds(1));
        when(tests.findByIdForUpdate(test.getTestId())).thenReturn(Optional.of(test));
        when(tests.save(test)).thenReturn(test);
        when(clearances.findByTestIdAndClearanceId(test.getTestId(), clearance.clearanceId()))
                .thenReturn(Optional.of(clearance));
        when(authenticatedStaff.currentStaff())
                .thenReturn(Optional.of(new AuthenticatedStaffContext(verifiedBy, "LAB_TECH")));
        when(correlations.currentOrCreate()).thenReturn(correlationId);

        service.addResults(test.getTestId(), new AddResultRequest(
                List.of(new LabResultItem("Hemoglobin", "13.5", "g/dL", "12-16")),
                "Normal", REQUESTED_DATE));

        assertThat(test.isPaid()).isTrue();
        assertThat(test.getStatus()).isEqualTo(LabTestStatus.COMPLETED);
        assertThat(test.getUpdatedAt()).isEqualTo(NOW);
        verify(clearances).findByTestIdAndClearanceId(test.getTestId(), clearance.clearanceId());
        verify(executionAuthority).requireCurrent(clearance, test.getTestId(), correlationId.toString());
        ArgumentCaptor<DomainEventEnvelope<?>> envelope = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(outbox).append(envelope.capture());
        assertThat(envelope.getValue().occurredAt()).isEqualTo(NOW);
        LabResultV2Payload payload = (LabResultV2Payload) envelope.getValue().payload();
        assertThat(payload.completedAt()).isEqualTo(NOW);
        assertThat(payload.verifiedBy()).isEqualTo(verifiedBy);
        assertThat(payload.resultVersion()).isEqualTo(1);
    }

    @Test
    void addResults_v2RejectsMissingBoundClearanceProjection() {
        LabTest test = v2Test();
        LabFinancialClearance clearance = LabFinancialClearance.grant(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), test.getTestId(), test.getPatientId(),
                test.getCareEpisodeType(), test.getCareEpisodeId(), ClearancePurpose.LAB_TEST,
                new BigDecimal("250000.00"), "VND", null, false, NOW.minusSeconds(60));
        test.grantClearance(clearance);
        test.start(clearance, null, NOW.minusSeconds(1));
        when(tests.findByIdForUpdate(test.getTestId())).thenReturn(Optional.of(test));
        when(clearances.findByTestIdAndClearanceId(test.getTestId(), clearance.clearanceId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addResults(test.getTestId(), new AddResultRequest(
                List.of(new LabResultItem("Hemoglobin", "13.5", "g/dL", "12-16")),
                "Normal", REQUESTED_DATE)))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_CLEARANCE_REQUIRED");

        assertThat(test.getStatus()).isEqualTo(LabTestStatus.IN_PROGRESS);
        verify(clearances).findByTestIdAndClearanceId(test.getTestId(), clearance.clearanceId());
        verify(tests, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void addResults_v2RejectsClearanceExpiredAtResultAction() {
        LabTest test = v2Test();
        LabFinancialClearance clearance = LabFinancialClearance.grant(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), test.getTestId(), test.getPatientId(),
                test.getCareEpisodeType(), test.getCareEpisodeId(), ClearancePurpose.LAB_TEST,
                new BigDecimal("250000.00"), "VND", NOW, false, NOW.minusSeconds(60));
        test.grantClearance(clearance);
        test.start(clearance, null, NOW.minusSeconds(1));
        when(tests.findByIdForUpdate(test.getTestId())).thenReturn(Optional.of(test));
        when(tests.save(test)).thenReturn(test);
        when(clearances.findByTestIdAndClearanceId(test.getTestId(), clearance.clearanceId()))
                .thenReturn(Optional.of(clearance));
        when(authenticatedStaff.currentStaff()).thenReturn(Optional.of(
                new AuthenticatedStaffContext(UUID.randomUUID(), "LAB_TECH")));
        when(correlations.currentOrCreate()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.addResults(test.getTestId(), new AddResultRequest(
                List.of(new LabResultItem("Hemoglobin", "13.5", "g/dL", "12-16")),
                "Normal", REQUESTED_DATE)))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_CLEARANCE_EXPIRED");

        assertThat(test.getStatus()).isEqualTo(LabTestStatus.IN_PROGRESS);
        verify(clearances).findByTestIdAndClearanceId(test.getTestId(), clearance.clearanceId());
        verify(tests, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void addResults_v2FailsClosedWhenBillingHasRevokedTheCurrentGrant() {
        LabTest test = v2Test();
        UUID correlationId = UUID.randomUUID();
        LabFinancialClearance clearance = clearanceFor(test);
        test.grantClearance(clearance);
        test.start(clearance, null, NOW.minusSeconds(1));
        when(tests.findByIdForUpdate(test.getTestId())).thenReturn(Optional.of(test));
        when(clearances.findByTestIdAndClearanceId(test.getTestId(), clearance.clearanceId()))
                .thenReturn(Optional.of(clearance));
        when(correlations.currentOrCreate()).thenReturn(correlationId);
        org.mockito.Mockito.doThrow(new LabRuleException("LAB_CLEARANCE_REQUIRED", "Billing revoked the grant"))
                .when(executionAuthority).requireCurrent(clearance, test.getTestId(), correlationId.toString());

        assertThatThrownBy(() -> service.addResults(test.getTestId(), new AddResultRequest(
                List.of(new LabResultItem("Hemoglobin", "13.5", "g/dL", "12-16")),
                "Normal", REQUESTED_DATE)))
                .isInstanceOf(LabRuleException.class)
                .hasFieldOrPropertyWithValue("code", "LAB_CLEARANCE_REQUIRED");

        verify(tests, never()).save(any());
        verifyNoInteractions(outbox);
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
    void start_v2FailsClosedWhenBillingCannotDurablyAdmitTheTest() {
        LabTest test = v2Test();
        LabFinancialClearance clearance = clearanceFor(test);
        UUID correlationId = UUID.randomUUID();
        test.grantClearance(clearance);
        when(tests.findByIdForUpdate(test.getTestId())).thenReturn(Optional.of(test));
        when(clearances.findValidByTestId(eq(test.getTestId()), any(Instant.class)))
                .thenReturn(Optional.of(clearance));
        when(correlations.currentOrCreate()).thenReturn(correlationId);
        org.mockito.Mockito.doThrow(new LabUpstreamUnavailableException())
                .when(executionAuthority).start(clearance, test.getTestId(), correlationId.toString());

        assertThatThrownBy(() -> service.start(test.getTestId(), new StartLabTestRequest(null)))
                .isInstanceOf(LabUpstreamUnavailableException.class);

        verify(executionAuthority).start(clearance, test.getTestId(), correlationId.toString());
        verify(tests, never()).save(any());
        verifyNoInteractions(outbox);
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
        when(authenticatedStaff.currentStaff())
                .thenReturn(Optional.of(new AuthenticatedStaffContext(actor, "ADMIN")));
        when(correlations.currentOrCreate()).thenReturn(UUID.randomUUID());
        EmergencyOverrideRequest approval = new EmergencyOverrideRequest(UUID.randomUUID(), actor, "ADMIN",
                "Urgent diagnostic care", NOW.minusSeconds(1));

        service.start(test.getTestId(), new StartLabTestRequest(approval));

        assertThat(test.getStatus()).isEqualTo(LabTestStatus.IN_PROGRESS);
        assertThat(test.isPaid()).isFalse();
        ArgumentCaptor<LabEmergencyOverride> audit = ArgumentCaptor.forClass(LabEmergencyOverride.class);
        verify(overrides).save(audit.capture());
        assertThat(audit.getValue().approvedBy()).isEqualTo(actor);
        assertThat(audit.getValue().approverRole()).isEqualTo("ADMIN");
        verify(tests).save(test);
    }

    @Test
    void start_explicitValidEmergencyOverrideTakesPrecedenceOverLocalGrantAndSkipsBilling() {
        LabTest test = v2Test();
        LabFinancialClearance clearance = clearanceFor(test);
        test.grantClearance(clearance);
        UUID actor = UUID.randomUUID();
        when(tests.findByIdForUpdate(test.getTestId())).thenReturn(Optional.of(test));
        when(clearances.findValidByTestId(eq(test.getTestId()), any(Instant.class)))
                .thenReturn(Optional.of(clearance));
        when(authenticatedStaff.currentStaff())
                .thenReturn(Optional.of(new AuthenticatedStaffContext(actor, "ADMIN")));
        when(correlations.currentOrCreate()).thenReturn(UUID.randomUUID());
        EmergencyOverrideRequest approval = new EmergencyOverrideRequest(UUID.randomUUID(), actor, "ADMIN",
                "Urgent diagnostic care", NOW.minusSeconds(1));

        service.start(test.getTestId(), new StartLabTestRequest(approval));

        assertThat(test.getStatus()).isEqualTo(LabTestStatus.IN_PROGRESS);
        assertThat(test.isPaid()).isFalse();
        assertThat(test.getEmergencyOverrideId()).isEqualTo(approval.overrideId());
        verify(overrides).save(any());
        verifyNoInteractions(executionAuthority);
        verify(tests).save(test);
    }

    @Test
    void start_emergencyOverrideRejectsSpoofedApproverIdentity() {
        LabTest test = v2Test();
        UUID authenticatedAdmin = UUID.randomUUID();
        when(tests.findByIdForUpdate(test.getTestId())).thenReturn(Optional.of(test));
        when(authenticatedStaff.currentStaff())
                .thenReturn(Optional.of(new AuthenticatedStaffContext(authenticatedAdmin, "ADMIN")));
        EmergencyOverrideRequest spoofedApproval = new EmergencyOverrideRequest(
                UUID.randomUUID(), UUID.randomUUID(), "ADMIN", "Urgent diagnostic care", NOW.minusSeconds(1));

        assertThatThrownBy(() -> service.start(test.getTestId(), new StartLabTestRequest(spoofedApproval)))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_OVERRIDE_INVALID");

        verify(overrides, never()).save(any());
        verify(tests, never()).save(any());
    }

    @Test
    void start_emergencyOverrideRejectsLabTechClaimingAdminRole() {
        LabTest test = v2Test();
        UUID authenticatedLabTech = UUID.randomUUID();
        when(tests.findByIdForUpdate(test.getTestId())).thenReturn(Optional.of(test));
        when(authenticatedStaff.currentStaff())
                .thenReturn(Optional.of(new AuthenticatedStaffContext(authenticatedLabTech, "LAB_TECH")));
        EmergencyOverrideRequest spoofedApproval = new EmergencyOverrideRequest(
                UUID.randomUUID(), authenticatedLabTech, "ADMIN", "Urgent diagnostic care", NOW.minusSeconds(1));

        assertThatThrownBy(() -> service.start(test.getTestId(), new StartLabTestRequest(spoofedApproval)))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_OVERRIDE_INVALID");

        verify(overrides, never()).save(any());
        verify(tests, never()).save(any());
    }

    @Test
    void cancel_rejectsBodyActorThatDoesNotMatchVerifiedStaff() {
        LabTest test = v2Test();
        UUID authenticatedStaffId = UUID.randomUUID();
        when(tests.findByIdForUpdate(test.getTestId())).thenReturn(Optional.of(test));
        when(authenticatedStaff.currentStaff())
                .thenReturn(Optional.of(new AuthenticatedStaffContext(authenticatedStaffId, "LAB_TECH")));

        assertThatThrownBy(() -> service.cancel(test.getTestId(),
                new CancelLabTestRequest("duplicate", UUID.randomUUID())))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_ACTOR_MISMATCH");
        verify(tests, never()).save(any());
    }

    private static CreateLabRequest v2Request(UUID sourceOrderId, UUID episodeId) {
        return new CreateLabRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "CBC",
                REQUESTED_DATE, sourceOrderId, CareEpisodeType.OUTPATIENT_VISIT, episodeId, "LAB-CBC");
    }

    private LabApplicationService outpatient(LabClinicalOrderContextPort contexts,
            com.mediflow.lab.application.port.in.CreateLabRequestUseCase creation) {
        return new LabApplicationService(tests, compatibilityEvents, mapper, correlations, outbox,
                clearances, overrides, authenticatedStaff, true, true, Clock.fixed(NOW, ZoneOffset.UTC),
                contexts, creation, executionAuthority);
    }

    private static LabFinancialClearance clearanceFor(LabTest test) {
        return LabFinancialClearance.grant(test.getTestId(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), test.getTestId(), test.getPatientId(),
                test.getCareEpisodeType(), test.getCareEpisodeId(), ClearancePurpose.LAB_TEST,
                new BigDecimal("250000.00"), "VND", NOW.plusSeconds(60), false, NOW.minusSeconds(60));
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
