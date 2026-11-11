package com.mediflow.lab.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.lab.domain.model.LabCriticalNotice;

public interface LabCriticalNoticeRepositoryPort {
    /** False when the same (test, revision, field) already has a notice. */
    boolean insertIfAbsent(LabCriticalNotice notice);

    Optional<LabCriticalNotice> findForUpdate(UUID noticeId);

    void update(LabCriticalNotice notice);

    /** Newest first; {@code departmentId} null means every department. */
    List<LabCriticalNotice> find(String status, UUID departmentId);
}
