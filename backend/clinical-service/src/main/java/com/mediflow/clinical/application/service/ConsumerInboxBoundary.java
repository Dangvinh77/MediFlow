package com.mediflow.clinical.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.application.dto.command.ConsumerInboxClaim;
import com.mediflow.clinical.application.port.out.ConsumerInboxPort;
import com.mediflow.clinical.application.port.out.ConsumerInboxPort.InboxOutcome;

/** Keeps the durable receipt and the existing integration effect in one database transaction. */
@Service
public class ConsumerInboxBoundary {
    private final ConsumerInboxPort inbox;

    public ConsumerInboxBoundary(ConsumerInboxPort inbox) {
        this.inbox = inbox;
    }

    @Transactional
    public InboxOutcome execute(ConsumerInboxClaim claim, Runnable effect) {
        InboxOutcome outcome = inbox.claim(claim);
        if (outcome == InboxOutcome.APPLIED) {
            effect.run();
        }
        return outcome;
    }
}
