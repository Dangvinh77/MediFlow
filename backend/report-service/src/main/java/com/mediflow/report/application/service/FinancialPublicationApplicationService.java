package com.mediflow.report.application.service;

import com.mediflow.report.application.dto.request.PublishFinancialSnapshotRequest;
import com.mediflow.report.application.dto.request.RollbackFinancialSnapshotRequest;
import com.mediflow.report.application.dto.response.FinancialPublicationDTO;
import com.mediflow.report.application.port.in.PublishFinancialSnapshotsUseCase;
import com.mediflow.report.application.port.out.FinancialPublicationStorePort;
import com.mediflow.report.domain.model.FinancialPublicationPolicy;
import com.mediflow.report.domain.exception.ReportRuleException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialPublicationApplicationService implements PublishFinancialSnapshotsUseCase {
    private final FinancialPublicationStorePort store;
    public FinancialPublicationApplicationService(FinancialPublicationStorePort store) { this.store = store; }

    @Override
    @Transactional(isolation=Isolation.READ_COMMITTED, timeout=15)
    public FinancialPublicationDTO publish(PublishFinancialSnapshotRequest request, UUID actorAccountId) {
        if (request == null) throw invalid();
        FinancialPublicationPolicy.requireCommand(request.commandId(), request.generationId(), actorAccountId, request.acceptanceReference());
        FinancialPublicationPolicy.requireEvidence(request.manifestFingerprint(), request.historyPolicy());
        return store.publish(request, actorAccountId);
    }

    @Override
    @Transactional(isolation=Isolation.READ_COMMITTED, timeout=15)
    public FinancialPublicationDTO rollback(RollbackFinancialSnapshotRequest request, UUID actorAccountId) {
        if (request == null || request.expectedCurrentGenerationId() == null) throw invalid();
        FinancialPublicationPolicy.requireCommand(request.commandId(), request.generationId(), actorAccountId, request.acceptanceReference());
        return store.rollback(request, actorAccountId);
    }

    private static ReportRuleException invalid() {
        return new ReportRuleException("REPORT_PUBLICATION_INVALID", "Explicit bounded publication command required");
    }
}
