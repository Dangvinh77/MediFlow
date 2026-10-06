package com.mediflow.surgery.application.port.in;

import com.mediflow.surgery.application.port.out.SurgeryInboxPort.IncomingEvent;
import java.util.List;

public interface QueryPendingSurgeryClearancesUseCase {
    List<IncomingEvent> due(int limit);
}
