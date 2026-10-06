package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.port.in.QueryPendingSurgeryClearancesUseCase;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryInboxPort;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public class PendingSurgeryClearanceQueryService implements QueryPendingSurgeryClearancesUseCase {
    private final SurgeryInboxPort inbox;
    private final SurgeryClockPort clock;
    public PendingSurgeryClearanceQueryService(SurgeryInboxPort inbox, SurgeryClockPort clock) {
        this.inbox = inbox; this.clock = clock;
    }
    @Override @Transactional(readOnly = true)
    public List<SurgeryInboxPort.IncomingEvent> due(int limit) {
        return inbox.findDue("financial.clearance.granted",clock.now(),Math.min(100,Math.max(1,limit)));
    }
}
