package com.mediflow.clinical.application.port.out;

import java.util.UUID;

public interface RecordReviewAuthorityPort {
    boolean canReviewRecords(UUID accountId, UUID correlationId);
}
