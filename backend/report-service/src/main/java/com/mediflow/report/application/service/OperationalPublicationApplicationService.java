package com.mediflow.report.application.service;

import com.mediflow.report.application.dto.request.PublishOperationalSnapshotRequest;
import com.mediflow.report.application.dto.request.RollbackOperationalSnapshotRequest;
import com.mediflow.report.application.dto.response.OperationalPublicationDTO;
import com.mediflow.report.application.port.in.PublishOperationalSnapshotsUseCase;
import com.mediflow.report.application.port.out.OperationalPublicationStorePort;
import com.mediflow.report.domain.exception.ReportRuleException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

public class OperationalPublicationApplicationService implements PublishOperationalSnapshotsUseCase,
        com.mediflow.report.application.port.in.GetOperationalPublicationCandidateUseCase {
    private final OperationalPublicationStorePort store;
    private final Clock clock;
    private final ZoneId zone;
    public OperationalPublicationApplicationService(OperationalPublicationStorePort store, Clock clock, ZoneId zone) {
        this.store = store; this.clock = clock; this.zone = zone;
    }

    @Override
    @Transactional(transactionManager = "reportTransactionManager", isolation=Isolation.READ_COMMITTED, timeout=15)
    public OperationalPublicationDTO publish(PublishOperationalSnapshotRequest request, UUID actorAccountId) {
        require(request != null);
        command(request.commandId(), request.generationId(), actorAccountId, request.acceptanceReference());
        require(request.manifestFingerprint() != null && request.manifestFingerprint().matches("[a-f0-9]{64}"));
        var from = request.coveredFrom(); var to = request.coveredTo();
        require(from != null && to != null && !to.isBefore(from) && from.getYear() >= 2000 && to.getYear() <= 2100
                && ChronoUnit.DAYS.between(from, to) < 366 && to.isBefore(LocalDate.now(clock.withZone(zone))));
        return store.publish(request, actorAccountId);
    }

    @Override
    @Transactional(transactionManager = "reportTransactionManager", isolation=Isolation.READ_COMMITTED, timeout=15)
    public OperationalPublicationDTO rollback(RollbackOperationalSnapshotRequest request, UUID actorAccountId) {
        require(request != null && request.expectedCurrentGenerationId() != null);
        command(request.commandId(), request.generationId(), actorAccountId, request.acceptanceReference());
        return store.rollback(request, actorAccountId);
    }

    @Override
    @Transactional(transactionManager = "reportTransactionManager", readOnly=true)
    public com.mediflow.report.application.dto.response.OperationalPublicationCandidateDTO candidate(UUID generationId) {
        require(generationId != null);
        return store.candidate(generationId);
    }

    private static void command(UUID command, UUID generation, UUID actor, String reference) {
        require(command != null && generation != null && actor != null && reference != null
                && reference.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,119}"));
    }
    private static void require(boolean condition) {
        if (!condition) throw new ReportRuleException("OPERATIONAL_PUBLICATION_INVALID", "Explicit bounded publication command required");
    }
}
