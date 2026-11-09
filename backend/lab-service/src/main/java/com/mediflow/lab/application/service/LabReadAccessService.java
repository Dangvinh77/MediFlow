package com.mediflow.lab.application.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.lab.application.dto.response.LabTestDTO;
import com.mediflow.lab.application.port.in.ManageLabTestUseCase;
import com.mediflow.lab.application.port.out.ClinicalReadDecisionPort;
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
    private static final String LAB_TECH = "LAB_TECH";
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
            requireAllowed(test.patientId(), reader);
        }
        return test;
    }

    public List<LabTestDTO> byPatient(UUID patientId) {
        if (enabled) {
            requireAllowed(patientId, requireReader());
        }
        return labs.byPatient(patientId);
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
            result.content().stream().map(LabTestDTO::patientId).collect(Collectors.toSet())
                    .forEach(patientId -> requireAllowed(patientId, reader));
        }
        return result;
    }

    private ReaderIdentity requireReader() {
        return readers.current().orElseThrow(() -> new ForbiddenOperationException(DENIED,
                "An authenticated staff identity is required"));
    }

    private void requireAllowed(UUID patientId, ReaderIdentity reader) {
        var decision = decisions.decide(patientId, reader, correlationIds.currentOrCreate().toString());
        if (!decision.allowed()) {
            throw new ForbiddenOperationException(decision.denialCode() == null ? DENIED : decision.denialCode(),
                    "Reading these Lab results is not permitted");
        }
    }
}
