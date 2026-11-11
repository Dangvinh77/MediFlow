package com.mediflow.clinical.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.clinical.domain.model.AmendmentAssignment;
import com.mediflow.clinical.domain.model.RecordAmendment;

public interface RecordAmendmentRepositoryPort {
    record PendingAmendment(RecordAmendment amendment, UUID departmentId) {}

    void insert(RecordAmendment amendment);

    Optional<RecordAmendment> findByIdForUpdate(UUID amendmentId);

    void decide(RecordAmendment amendment);

    /** Oldest first. */
    List<RecordAmendment> findByRecord(UUID recordId);

    /** Every pending amendment with its visit department, oldest first. */
    List<PendingAmendment> findPending();

    void insertAssignment(AmendmentAssignment assignment);

    Optional<AmendmentAssignment> findAssignment(UUID recordId, UUID assigneeStaffId);
}
