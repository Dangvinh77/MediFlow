package com.mediflow.report.application.port.out;

import com.mediflow.report.application.dto.request.PublishFinancialSnapshotRequest;
import com.mediflow.report.application.dto.request.RollbackFinancialSnapshotRequest;
import com.mediflow.report.application.dto.response.FinancialPublicationDTO;
import java.util.UUID;

public interface FinancialPublicationStorePort {
    FinancialPublicationDTO publish(PublishFinancialSnapshotRequest request, UUID actorAccountId);
    FinancialPublicationDTO rollback(RollbackFinancialSnapshotRequest request, UUID actorAccountId);
}
