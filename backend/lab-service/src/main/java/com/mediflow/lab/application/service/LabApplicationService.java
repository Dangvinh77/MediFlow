package com.mediflow.lab.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.lab.application.dto.request.AddResultRequest;
import com.mediflow.lab.application.dto.request.CancelLabTestRequest;
import com.mediflow.lab.application.dto.request.CreateLabRequest;
import com.mediflow.lab.application.dto.request.EmergencyOverrideRequest;
import com.mediflow.lab.application.dto.request.StartLabTestRequest;
import com.mediflow.lab.application.dto.command.FinancialClearanceCommand;
import com.mediflow.lab.application.dto.response.LabTestDTO;
import com.mediflow.lab.application.event.DomainEventEnvelope;
import com.mediflow.lab.application.event.LabRequestCreatedEvent;
import com.mediflow.lab.application.event.LabResultCreatedEvent;
import com.mediflow.lab.application.event.LabRequestV2Payload;
import com.mediflow.lab.application.event.LabResultV2Payload;
import com.mediflow.lab.application.mapper.LabTestDtoMapper;
import com.mediflow.lab.application.port.in.ManageLabTestUseCase;
import com.mediflow.lab.application.port.in.ReactToClinicalUseCase;
import com.mediflow.lab.application.port.in.ReactToFinancialClearanceUseCase;
import com.mediflow.lab.application.port.in.UpdateLabPaymentUseCase;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContext;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContextPort;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.port.out.LabClearanceRepositoryPort;
import com.mediflow.lab.application.port.out.LabEmergencyOverrideRepositoryPort;
import com.mediflow.lab.application.port.out.LabEventPublisherPort;
import com.mediflow.lab.application.port.out.LabOutboxPort;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.domain.exception.LabRuleException;
import com.mediflow.lab.domain.exception.LabTestNotFoundException;
import com.mediflow.lab.domain.model.CareEpisodeType;
import com.mediflow.lab.domain.model.ClearancePurpose;
import com.mediflow.lab.domain.model.LabEmergencyOverride;
import com.mediflow.lab.domain.model.LabFinancialClearance;
import com.mediflow.lab.domain.model.LabResult;
import com.mediflow.lab.domain.model.LabTest;
import com.mediflow.lab.domain.model.LabTestStatus;

public class LabApplicationService implements ManageLabTestUseCase, ReactToClinicalUseCase,
        UpdateLabPaymentUseCase, ReactToFinancialClearanceUseCase {

    private final LabTestRepositoryPort tests;
    private final LabEventPublisherPort publisher;
    private final LabTestDtoMapper mapper;
    private final CorrelationIdProvider correlationIds;
    private final LabOutboxPort outbox;
    private final LabClearanceRepositoryPort clearances;
    private final LabEmergencyOverrideRepositoryPort overrides;
    private final AuthenticatedStaffContextPort authenticatedStaff;
    private final boolean careFinanceV2Enabled;
    private final Clock clock;

    public LabApplicationService(LabTestRepositoryPort tests, LabEventPublisherPort publisher,
                                 LabTestDtoMapper mapper, CorrelationIdProvider correlationIds,
                                 LabOutboxPort outbox, LabClearanceRepositoryPort clearances,
                                 LabEmergencyOverrideRepositoryPort overrides,
                                 AuthenticatedStaffContextPort authenticatedStaff,
                                  boolean careFinanceV2Enabled,
                                  Clock clock) {
        this.tests = tests;
        this.publisher = publisher;
        this.mapper = mapper;
        this.correlationIds = correlationIds;
        this.outbox = outbox;
        this.clearances = clearances;
        this.overrides = overrides;
        this.authenticatedStaff = authenticatedStaff;
        this.careFinanceV2Enabled = careFinanceV2Enabled;
        this.clock = clock;
    }

    /** Compatibility constructor retained for version-0 unit fixtures. */
    public LabApplicationService(LabTestRepositoryPort tests, LabEventPublisherPort publisher,
                                 LabTestDtoMapper mapper, CorrelationIdProvider correlationIds) {
        this(tests, publisher, mapper, correlationIds, event -> { }, null, null,
                OptionalStaffContextPort.INSTANCE, false, Clock.systemUTC());
    }

    @Override
    @Transactional
    public LabTestDTO create(CreateLabRequest request) {
        boolean hasV2Identity = request.sourceOrderId() != null || request.careEpisodeType() != null
                || request.careEpisodeId() != null || request.priceCode() != null;
        if (careFinanceV2Enabled) {
            return createV2(request);
        }
        if (hasV2Identity) {
            throw featureDisabled();
        }
        LabTest saved = saveNew(request.recordId(), request.patientId(), request.requestingDepartmentId(),
                request.labType(), request.requestedDate());
        return mapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public LabTestDTO getById(UUID id) {
        return mapper.toDto(tests.findById(id).orElseThrow(() -> new LabTestNotFoundException(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabTestDTO> byPatient(UUID patientId) {
        return tests.findByPatient(patientId).stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<LabTestDTO> search(UUID departmentId, LabTestStatus status, PageQuery page) {
        return search(departmentId, status, null, null, page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<LabTestDTO> search(UUID departmentId, LabTestStatus status,
                                         CareEpisodeType episodeType, UUID episodeId, PageQuery page) {
        return tests.search(departmentId, status, episodeType, episodeId, page).map(mapper::toDto);
    }

    @Override
    @Transactional
    public LabTestDTO start(UUID id, StartLabTestRequest request) {
        LabTest test = locked(id);
        if (test.getCareContractVersion() == 0) {
            test.start(null, null, clock.instant());
            return mapper.toDto(tests.save(test));
        }
        requireV2Enabled();

        Instant now = clock.instant();
        LabEmergencyOverride override = toOverride(request == null ? null : request.emergencyOverride(), test, now);
        LabFinancialClearance clearance = clearances.findValidByTestId(id, now)
                .orElseGet(() -> clearances.findLatestByTestId(id).orElse(null));
        if (override != null && clearance != null && clearance.isExpiredAt(now)) {
            clearance = null;
        }

        UUID previousOverrideId = test.getEmergencyOverrideId();
        test.start(clearance, override, now);
        if (override != null && !override.overrideId().equals(previousOverrideId)
                && override.overrideId().equals(test.getEmergencyOverrideId())) {
            overrides.save(override);
        }
        return mapper.toDto(tests.save(test));
    }

    @Override
    @Transactional
    public LabTestDTO cancel(UUID id, CancelLabTestRequest request) {
        LabTest test = locked(id);
        if (test.getCareContractVersion() == 1) {
            requireV2Enabled();
        }
        test.cancel(request.reason(), request.cancelledBy());
        return mapper.toDto(tests.save(test));
    }

    @Override
    @Transactional
    public LabTestDTO addResults(UUID id, AddResultRequest request) {
        LabTest test = locked(id);
        List<LabResult> results = request.results().stream()
                .map(item -> LabResult.create(item.indicator(), item.value(), item.unit(), item.referenceRange()))
                .toList();
        test.recordResults(results, request.conclusion(), request.performedDate());
        UUID verifiedBy = null;
        if (test.getCareContractVersion() == 1) {
            requireV2Enabled();
            verifiedBy = authenticatedStaff.currentStaff().map(AuthenticatedStaffContext::staffId)
                    .orElseThrow(() ->
                    new LabRuleException("LAB_STAFF_REQUIRED", "Người xác nhận kết quả phải là nhân viên đã xác thực"));
        }
        LabTest saved = tests.save(test);
        if (saved.getCareContractVersion() == 1) {
            appendResultEvent(saved, verifiedBy);
        } else {
            publisher.publishResultCreated(
                    LabResultCreatedEvent.from(saved, correlationIds.currentOrCreate().toString()));
        }
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public LabTestDTO changeStatus(UUID id, LabTestStatus status) {
        LabTest test = locked(id);
        test.changeStatus(status);
        return mapper.toDto(tests.save(test));
    }

    @Override
    @Transactional
    public void autoCreateFromRecord(UUID recordId, UUID patientId, UUID departmentId, String labType) {
        if (labType == null || labType.isBlank()) {
            return;
        }
        saveNew(recordId, patientId, departmentId, labType, LocalDate.now());
    }

    @Override
    @Transactional
    public void markPaid(UUID testId) {
        LabTest test = locked(testId);
        test.markPaid();
        tests.save(test);
    }

    @Override
    @Transactional
    public void onFinancialClearance(FinancialClearanceCommand command) {
        requireV2Enabled();
        validateClearanceCommand(command);

        List<LabFinancialClearance> projections = command.labTestIds().stream()
                .map(testId -> LabFinancialClearance.grant(UUID.randomUUID(), command.clearanceId(),
                        command.eventId(), command.invoiceId(), command.accountId(), testId,
                        command.patientId(), command.careEpisodeType(), command.careEpisodeId(),
                        command.purpose(), command.amount(), command.currency(), command.expiresAt(),
                        command.emergencyOverride(), command.occurredAt()))
                .toList();
        List<LabTest> lockedTests = new ArrayList<>(projections.size());
        for (LabFinancialClearance projection : projections) {
            LabTest test = tests.findByIdForUpdate(projection.testId())
                    .orElseThrow(() -> clearanceTargetMismatch("Xét nghiệm trong quyền thanh toán không tồn tại"));
            lockedTests.add(test);
        }

        // The marker and all target rows share this command's transaction with aggregate updates.
        if (!clearances.claimAndSave(command.eventId(), projections)) {
            return;
        }
        for (int i = 0; i < projections.size(); i++) {
            LabTest test = lockedTests.get(i);
            test.grantClearance(projections.get(i));
            tests.save(test);
        }
    }

    private LabTest saveNew(UUID recordId, UUID patientId, UUID departmentId,
                            String labType, LocalDate requestedDate) {
        LabTest saved = tests.save(LabTest.create(recordId, patientId, departmentId, labType, requestedDate));
        publisher.publishRequestCreated(
                LabRequestCreatedEvent.from(saved, correlationIds.currentOrCreate().toString()));
        return saved;
    }

    private LabTestDTO createV2(CreateLabRequest request) {
        requireV2Enabled();
        if (request.sourceOrderId() != null && tests.existsBySourceOrderId(request.sourceOrderId())) {
            throw new DuplicateResourceException("LAB_DUPLICATE_SOURCE_ORDER",
                    "Đơn xét nghiệm từ hệ thống yêu cầu đã được tiếp nhận");
        }
        LabTest created = LabTest.createV2(request.recordId(), request.patientId(),
                request.requestingDepartmentId(), request.sourceOrderId(), request.careEpisodeType(),
                request.careEpisodeId(), request.labType(), request.priceCode(), request.requestedDate());
        LabTest saved = tests.save(created);
        String correlationId = correlationIds.currentOrCreate().toString();
        Instant occurredAt = clock.instant();
        LabRequestV2Payload payload = new LabRequestV2Payload(saved.getTestId(), saved.getPatientId(),
                saved.getRecordId(), saved.getRequestingDepartmentId(), saved.getCareEpisodeType(),
                saved.getCareEpisodeId(), saved.getSourceOrderId(), "LAB_TEST", saved.getTestId(),
                saved.getPriceCode(), saved.getLabType(), occurredAt, saved.getEmergencyOverrideId());
        outbox.append(new DomainEventEnvelope<>(UUID.randomUUID(), "lab.request.created", 1,
                occurredAt, correlationId, "lab-service", payload));
        return mapper.toDto(saved);
    }

    private void appendResultEvent(LabTest test, UUID verifiedBy) {
        String correlationId = correlationIds.currentOrCreate().toString();
        Instant occurredAt = clock.instant();
        LabResultV2Payload payload = new LabResultV2Payload(test.getTestId(), test.getPatientId(),
                test.getRecordId(), test.getRequestingDepartmentId(), test.getCareEpisodeType(),
                test.getCareEpisodeId(), test.getLabType(), test.getResultVersion(),
                test.getResults().stream().map(LabResultCreatedEvent.Result::from).toList(),
                test.getConclusion(), verifiedBy, test.getPerformedDate(), occurredAt);
        outbox.append(new DomainEventEnvelope<>(UUID.randomUUID(), "lab.result.created", 1,
                occurredAt, correlationId, "lab-service", payload));
    }

    private LabEmergencyOverride toOverride(EmergencyOverrideRequest request, LabTest test, Instant now) {
        if (request == null) {
            return null;
        }
        AuthenticatedStaffContext approver = authenticatedStaff.currentStaff()
                .filter(staff -> "ADMIN".equals(staff.role()))
                .filter(staff -> staff.staffId().equals(request.approvedBy()))
                .filter(staff -> staff.role().equals(request.approverRole()))
                .orElseThrow(() -> new LabRuleException("LAB_OVERRIDE_INVALID",
                        "Người duyệt cấp cứu phải là ADMIN đã xác thực và khớp thông tin kiểm toán"));
        return LabEmergencyOverride.approve(request.overrideId(), test.getTestId(), test.getPatientId(),
                test.getCareEpisodeType(), test.getCareEpisodeId(), approver.staffId(),
                approver.role(), request.reason(), request.approvedAt());
    }

    private static void validateClearanceCommand(FinancialClearanceCommand command) {
        if (command == null || command.eventId() == null || command.version() != 1
                || !"financial.clearance.granted".equals(command.eventType())
                || !"billing-service".equals(command.producer()) || command.occurredAt() == null
                || command.correlationId() == null || command.clearanceId() == null
                || command.invoiceId() == null || command.accountId() == null || command.patientId() == null
                || command.careEpisodeType() == null || command.careEpisodeId() == null
                || command.purpose() != ClearancePurpose.LAB_TEST || command.labTestIds() == null
                || command.labTestIds().isEmpty() || command.labTestIds().stream().anyMatch(java.util.Objects::isNull)
                || command.labTestIds().stream().distinct().count() != command.labTestIds().size()
                || command.amount() == null || command.amount().compareTo(BigDecimal.ZERO) < 0
                || command.currency() == null || !command.currency().matches("[A-Z]{3}")) {
            throw clearanceTargetMismatch("Sự kiện quyền thanh toán thiếu dữ liệu hoặc sai mục đích");
        }
    }

    private static LabRuleException clearanceTargetMismatch(String message) {
        return new LabRuleException("LAB_CLEARANCE_TARGET_MISMATCH", message);
    }

    private void requireV2Enabled() {
        if (!careFinanceV2Enabled) {
            throw featureDisabled();
        }
    }

    private static LabRuleException featureDisabled() {
        return new LabRuleException("LAB_CARE_FINANCE_V2_DISABLED",
                "Luồng Care & Finance V2 của Lab chưa được bật");
    }

    private enum OptionalStaffContextPort implements AuthenticatedStaffContextPort {
        INSTANCE;

        @Override public Optional<AuthenticatedStaffContext> currentStaff() { return Optional.empty(); }
    }

    private LabTest locked(UUID id) {
        return tests.findByIdForUpdate(id).orElseThrow(() -> new LabTestNotFoundException(id));
    }
}
