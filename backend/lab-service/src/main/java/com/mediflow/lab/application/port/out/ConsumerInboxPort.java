package com.mediflow.lab.application.port.out;

import com.mediflow.lab.application.dto.command.ConsumerInboxClaim;

/** Durable source delivery and semantic-operation deduplication. */
public interface ConsumerInboxPort {
    InboxOutcome claim(ConsumerInboxClaim claim);

    enum InboxOutcome {
        APPLIED,
        DUPLICATE_EVENT,
        DUPLICATE_OPERATION
    }
}
