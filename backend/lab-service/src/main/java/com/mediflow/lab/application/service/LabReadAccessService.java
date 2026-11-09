package com.mediflow.lab.application.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.lab.application.dto.response.LabTestDTO;
import com.mediflow.lab.application.port.in.ManageLabTestUseCase;
import com.mediflow.lab.application.port.out.ClinicalReadDecisionPort;
import com.mediflow.lab.application.port.out.ClinicalReadDecisionPort.ClinicalReadDecision;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.port.out.CurrentReaderPort;
import com.mediflow.lab.application.port.out.ReaderIdentity;
import com.mediflow.lab.domain.model.CareEpisodeType;
import com.mediflow.lab.domain.model.LabTestStatus;

/**
 * Applies Clinical's read decision to result reads. Deliberately not transactional: the Clinical
 * call must not run while a Lab database transaction is held open.
 */
public class LabReadAccessService {
    private static final String DENIED = "RECORD_ACCESS_DENIED";
    private static final String PURPOSE_REQUIRED = "RECORD_ACCESS_PURPOSE_REQUIRED";
    private static final String DECLARE_PURPOSE =
            "Bạn chưa có căn cứ xem kết quả xét nghiệm này. Hãy mở hồ sơ khám của người bệnh và khai báo mục đích đọc.";
    private static final String NOT_PERMITTED = "Bạn không có quyền xem kết quả xét nghiệm này.";
    private static final String LAB_TECH = "LAB_TECH";
    private static final String ADMIN = "ADMIN";
    private static final Set<String> DEPARTMENT_BOUND_ROLES = Set.of("DOCTOR", "NURSE");

    private final ManageLabTestUseCase labs;
    private final ClinicalReadDecisionPort decisions;
    private final CurrentReaderPort readers;
    private final CorrelationIdProvider correlationIds;
    private final boolean enabled;

    public LabReadAccessService(ManageLabTestUseCase labs, ClinicalReadDecisionPort decisions,
            CurrentReaderPort readers, CorrelationIdProvider correlationIds, boolean enabled) {
        this.labs = labs;
        this.decisions = decisions;
        this.readers = readers;
        this.correlationIds = correlationIds;
        this.enabled = enabled;
    }

    public LabTestDTO getById(UUID id) {
        LabTestDTO test = labs.getById(id);
        if (!enabled) {
            return test;
        }
        ReaderIdentity reader = requireReader();
        if (!LAB_TECH.equals(reader.role())) {
            ClinicalReadDecision decision = requireAllowed(test.patientId(), reader);
            if (!inScope(decision, test)) {
                throw new ForbiddenOperationException(DENIED, NOT_PERMITTED);
            }
        }
        return test;
    }

    public List<LabTestDTO> byPatient(UUID patientId) {
        if (!enabled) {
            return labs.byPatient(patientId);
        }
        ClinicalReadDecision decision = requireAllowed(patientId, requireReader());
        return labs.byPatient(patientId).stream().filter(test -> inScope(decision, test)).toList();
    }

    public PageResult<LabTestDTO> search(UUID departmentId, LabTestStatus status, CareEpisodeType episodeType,
            UUID episodeId, PageQuery page) {
        if (!enabled) {
            return labs.search(departmentId, status, episodeType, episodeId, page);
        }
        ReaderIdentity reader = requireReader();
        if (LAB_TECH.equals(reader.role())) {
            return labs.search(departmentId, status, episodeType, episodeId, page);
        }
        UUID effectiveDepartment = departmentId;
        if (episodeId == null && DEPARTMENT_BOUND_ROLES.contains(reader.role())) {
            if (reader.departmentId() == null) {
                throw new ForbiddenOperationException(DENIED, "A current department is required to list Lab results");
            }
            effectiveDepartment = reader.departmentId();
        }
        PageResult<LabTestDTO> result = labs.search(effectiveDepartment, status, episodeType, episodeId, page);
        if (episodeId != null) {
            return keepReadable(result, reader);
        }
        return ADMIN.equals(reader.role()) ? result.map(LabReadAccessService::withoutResultContent) : result;
    }

    private PageResult<LabTestDTO> keepReadable(PageResult<LabTestDTO> page, ReaderIdentity reader) {
        Map<UUID, ClinicalReadDecision> decisionsByPatient = page.content().stream().map(LabTestDTO::patientId).distinct()
                .collect(Collectors.toMap(Function.identity(), patientId -> requireAllowed(patientId, reader)));
        List<LabTestDTO> readable = page.content().stream()
                .filter(test -> inScope(decisionsByPatient.get(test.patientId()), test))
                .toList();
        long removed = page.content().size() - readable.size();
        return removed == 0 ? page : PageResult.of(readable, page.totalElements() - removed, page.number(), page.size());
    }

    private static boolean inScope(ClinicalReadDecision decision, LabTestDTO test) {
        return decision.departmentScope() == null || decision.departmentScope().equals(test.requestingDepartmentId());
    }

    /** An unfiltered list has no patient to declare against, so it carries work-queue fields only. */
    private static LabTestDTO withoutResultContent(LabTestDTO test) {
        return new LabTestDTO(test.testId(), test.recordId(), test.patientId(), test.requestingDepartmentId(),
                test.labType(), test.requestedDate(), test.performedDate(), test.status(), null, test.paid(), List.of(),
                test.createdAt(), test.updatedAt(), test.careContractVersion(), test.sourceOrderId(),
                test.careEpisodeType(), test.careEpisodeId(), test.priceCode(), test.clearanceId(),
                test.emergencyOverrideId(), test.resultVersion(), test.cancelledBy(), test.cancellationReason(),
                test.cancelledAt());
    }

    private ReaderIdentity requireReader() {
        return readers.current().orElseThrow(() -> new ForbiddenOperationException(DENIED,
                "An authenticated staff identity is required"));
    }

    private ClinicalReadDecision requireAllowed(UUID patientId, ReaderIdentity reader) {
        var decision = decisions.decide(patientId, reader, correlationIds.currentOrCreate().toString());
        if (!decision.allowed()) {
            throw PURPOSE_REQUIRED.equals(decision.denialCode())
                    ? new ForbiddenOperationException(PURPOSE_REQUIRED, DECLARE_PURPOSE)
                    : new ForbiddenOperationException(decision.denialCode() == null ? DENIED : decision.denialCode(), NOT_PERMITTED);
        }
        return decision;
    }
}
