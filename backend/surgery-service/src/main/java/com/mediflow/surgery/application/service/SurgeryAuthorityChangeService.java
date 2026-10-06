package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.port.in.ReceiveSurgeryAuthorityChangeUseCase;
import com.mediflow.surgery.application.port.out.SurgeryAuthorityInvalidationPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryInboxPort;
import org.springframework.transaction.annotation.Transactional;

/** Inbox, immutable source evidence and fan-out jobs commit before broker ACK. */
public class SurgeryAuthorityChangeService implements ReceiveSurgeryAuthorityChangeUseCase {
    private final SurgeryInboxPort inbox;
    private final SurgeryAuthorityInvalidationPort invalidations;
    private final SurgeryClockPort clock;
    public SurgeryAuthorityChangeService(SurgeryInboxPort inbox, SurgeryAuthorityInvalidationPort invalidations, SurgeryClockPort clock) {
        this.inbox = inbox; this.invalidations = invalidations; this.clock = clock;
    }
    @Override @Transactional(timeout = 5)
    public Outcome receive(Command command) {
        if (command == null) throw new IllegalArgumentException("Authority command required");
        var decision = java.util.Objects.requireNonNull(inbox.begin(command.incoming()), "Inbox decision required");
        if (decision == SurgeryInboxPort.Decision.ALREADY_APPLIED) return Outcome.REPLAYED;
        if (decision == SurgeryInboxPort.Decision.CONFLICT) return Outcome.CONFLICT;
        if (decision == SurgeryInboxPort.Decision.QUARANTINED) return Outcome.QUARANTINED;
        var now = clock.now();
        var capture = java.util.Objects.requireNonNull(invalidations.capture(
                command.incoming().eventId(), command.change(), command.correlationId(), now), "Durable capture required");
        if (capture == SurgeryAuthorityInvalidationPort.Capture.CONFLICT) {
            inbox.quarantine(command.incoming().eventId(), "AUTHORITY_REVISION_CONTENT_MISMATCH");
            return Outcome.CONFLICT;
        }
        inbox.markApplied(command.incoming().eventId(), now);
        return capture == SurgeryAuthorityInvalidationPort.Capture.CREATED ? Outcome.APPLIED : Outcome.REPLAYED;
    }
}
