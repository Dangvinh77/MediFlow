package com.mediflow.surgery.application.port.in;

import com.mediflow.surgery.application.port.out.SurgeryInboxPort.IncomingEvent;
import com.mediflow.surgery.domain.model.SurgeryAuthorityChange;

public interface ReceiveSurgeryAuthorityChangeUseCase {
    String EVENT_TYPE = "organization.surgery.authority.changed";
    Outcome receive(Command command);
    enum Outcome { APPLIED, REPLAYED, CONFLICT, QUARANTINED }
    record Command(IncomingEvent incoming, SurgeryAuthorityChange change, String correlationId) {
        public Command {
            if (incoming == null || change == null || !EVENT_TYPE.equals(incoming.eventType())
                    || incoming.version() != 1 || !"organization-service".equals(incoming.producer())
                    || !incoming.semanticKey().equals(EVENT_TYPE + ":" + incoming.eventId())
                    || correlationId == null || !correlationId.matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
                throw new IllegalArgumentException("Invalid Organization authority command");
            }
        }
    }
}
