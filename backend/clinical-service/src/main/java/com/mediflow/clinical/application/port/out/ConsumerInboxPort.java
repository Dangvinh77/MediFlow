package com.mediflow.clinical.application.port.out;

import com.mediflow.clinical.application.dto.command.ConsumerInboxClaim;

/** Durable source delivery and semantic-operation deduplication. */
public interface ConsumerInboxPort {
    InboxOutcome claim(ConsumerInboxClaim claim);

    enum InboxOutcome {
        APPLIED,
        DUPLICATE_EVENT,
        DUPLICATE_OPERATION
    }
}
