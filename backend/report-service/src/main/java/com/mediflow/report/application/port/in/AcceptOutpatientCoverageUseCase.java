package com.mediflow.report.application.port.in;

import com.mediflow.report.application.dto.request.AcceptOutpatientCoverageRequest;
import com.mediflow.report.application.port.out.OutpatientCoverageEvidencePort.Snapshot;
import com.mediflow.report.application.dto.response.OutpatientCoverageDTO;
import java.time.LocalDate;
import java.util.UUID;

public interface AcceptOutpatientCoverageUseCase {
    Snapshot preview(LocalDate from, LocalDate to, UUID department);
    OutpatientCoverageDTO accept(AcceptOutpatientCoverageRequest request, UUID actor);
}
