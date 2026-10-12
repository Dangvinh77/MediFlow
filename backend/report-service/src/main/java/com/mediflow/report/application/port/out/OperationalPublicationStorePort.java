package com.mediflow.report.application.port.out;

import com.mediflow.report.application.dto.request.PublishOperationalSnapshotRequest;
import com.mediflow.report.application.dto.request.RollbackOperationalSnapshotRequest;
import com.mediflow.report.application.dto.response.OperationalPublicationDTO;
import com.mediflow.report.application.dto.response.OperationalPublicationCandidateDTO;
import java.util.UUID;

public interface OperationalPublicationStorePort {
    OperationalPublicationDTO publish(PublishOperationalSnapshotRequest request, UUID actorAccountId);
    OperationalPublicationDTO rollback(RollbackOperationalSnapshotRequest request, UUID actorAccountId);
    OperationalPublicationCandidateDTO candidate(UUID generationId);
}
