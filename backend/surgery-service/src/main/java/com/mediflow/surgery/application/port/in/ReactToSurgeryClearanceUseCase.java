package com.mediflow.surgery.application.port.in;

import com.mediflow.surgery.application.port.out.SurgeryInboxPort.IncomingEvent;
import com.mediflow.surgery.domain.model.SurgeryFinancialClearance;

public interface ReactToSurgeryClearanceUseCase {
    Outcome receive(Command command);
    enum Outcome { APPLIED, REPLAYED, DEFERRED, CONFLICT, QUARANTINED }
    record Command(IncomingEvent incoming, SurgeryFinancialClearance clearance, String correlationId) {
        public Command {
            if (incoming == null || clearance == null || correlationId == null || correlationId.isBlank()
                    || correlationId.length() > 128 || !incoming.eventType().equals("financial.clearance.granted")
                    || incoming.version() != 1 || !incoming.producer().equals("billing-service"))
                throw new IllegalArgumentException("Invalid Surgery clearance command");
        }
    }
}
