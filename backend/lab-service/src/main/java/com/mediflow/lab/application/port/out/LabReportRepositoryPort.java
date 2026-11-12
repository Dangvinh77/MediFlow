package com.mediflow.lab.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.lab.domain.model.LabReport;

public interface LabReportRepositoryPort {
    /** The newest revision of a test's report, or empty when none was opened. */
    Optional<LabReport> current(UUID testId);

    /** As {@link #current} but locks the row for the calling transaction. */
    Optional<LabReport> forUpdate(UUID testId);

    /** Every revision, oldest first. */
    List<LabReport> revisions(UUID testId);

    void insert(LabReport report);

    /** Writes the report only if its stored draft revision is still {@code expectedDraftRevision}. */
    void update(LabReport report, int expectedDraftRevision);

    /** 0 = results through the old endpoint; 1 = results only through the review workflow. */
    int workflowVersion(UUID testId);

    void enterWorkflow(UUID testId);
}
