package com.mediflow.report.application.port.out;

import com.mediflow.report.application.dto.request.AcceptOutpatientCoverageRequest;
import com.mediflow.report.domain.model.OutpatientCoverage;
import java.util.Optional;
import java.util.UUID;

public interface OutpatientCoverageAcceptancePort {
    /** Bounded local lock: serialize approvals and freeze Report inputs until commit. */
    void lock();
    Optional<Saved> find(UUID requestId);
    void save(AcceptOutpatientCoverageRequest request, UUID actor, OutpatientCoverage coverage);
    record Saved(AcceptOutpatientCoverageRequest request, UUID actor, OutpatientCoverage coverage) { }
}
