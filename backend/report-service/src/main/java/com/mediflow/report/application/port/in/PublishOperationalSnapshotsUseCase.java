package com.mediflow.report.application.port.in;

import com.mediflow.report.application.dto.request.PublishOperationalSnapshotRequest;
import com.mediflow.report.application.dto.request.RollbackOperationalSnapshotRequest;
import com.mediflow.report.application.dto.response.OperationalPublicationDTO;
import java.util.UUID;

public interface PublishOperationalSnapshotsUseCase {
    OperationalPublicationDTO publish(PublishOperationalSnapshotRequest request, UUID actorAccountId);
    OperationalPublicationDTO rollback(RollbackOperationalSnapshotRequest request, UUID actorAccountId);
}
