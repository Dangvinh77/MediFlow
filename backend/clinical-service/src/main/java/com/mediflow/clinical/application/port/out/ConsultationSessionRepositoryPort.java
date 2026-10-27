package com.mediflow.clinical.application.port.out;

import java.time.LocalDate;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

public interface ConsultationSessionRepositoryPort {
    ConsultationSession insert(ConsultationSession session, UUID actorAccountId, String reason, Instant at);
    Optional<ConsultationSession> findById(UUID sessionId);
    Optional<ConsultationSession> findByIdForUpdate(UUID sessionId);
    void update(ConsultationSession before, ConsultationSession after, UUID actorAccountId, String action,
            String reason, Instant at);
    void updateCounts(ConsultationSession before, ConsultationSession after, Instant at);
    PageResult<ConsultationSession> search(UUID departmentId, LocalDate fromDate, LocalDate toDate, PageQuery page);
    List<ConsultationSession> findByDoctor(UUID doctorId, LocalDate fromDate, LocalDate toDate);
}
