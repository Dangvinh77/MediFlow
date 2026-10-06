package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.port.in.RecordSurgeryClearanceRetryUseCase;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryInboxPort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/** Backoff is durable and independent of the failed transaction. Applied winners are never reopened. */
public class SurgeryClearanceRetryService implements RecordSurgeryClearanceRetryUseCase {
    private final SurgeryInboxPort inbox;
    private final SurgeryClockPort clock;
    public SurgeryClearanceRetryService(SurgeryInboxPort inbox, SurgeryClockPort clock) {
        this.inbox = inbox; this.clock = clock;
    }
    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deferFailure(SurgeryInboxPort.IncomingEvent event) {
        if (event == null || !"financial.clearance.granted".equals(event.eventType())
                || event.version() != 1 || !"billing-service".equals(event.producer()))
            throw new IllegalArgumentException("Exact clearance event required");
        var decision = inbox.begin(event);
        if (decision == SurgeryInboxPort.Decision.NEW || decision == SurgeryInboxPort.Decision.RETRY_PENDING) {
            inbox.defer(event.eventId(),"SURGERY_CLEARANCE_RETRY_UNAVAILABLE",clock.now().plusSeconds(60));
        }
    }
}
