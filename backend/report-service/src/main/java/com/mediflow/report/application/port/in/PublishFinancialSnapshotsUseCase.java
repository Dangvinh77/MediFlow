package com.mediflow.report.application.port.in;

import com.mediflow.report.application.dto.request.PublishFinancialSnapshotRequest;
import com.mediflow.report.application.dto.request.RollbackFinancialSnapshotRequest;
import com.mediflow.report.application.dto.response.FinancialPublicationDTO;
import java.util.UUID;

public interface PublishFinancialSnapshotsUseCase {
    FinancialPublicationDTO publish(PublishFinancialSnapshotRequest request, UUID actorAccountId);
    FinancialPublicationDTO rollback(RollbackFinancialSnapshotRequest request, UUID actorAccountId);
}
