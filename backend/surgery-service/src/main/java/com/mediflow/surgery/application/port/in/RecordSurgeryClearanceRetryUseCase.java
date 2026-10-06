package com.mediflow.surgery.application.port.in;

import com.mediflow.surgery.application.port.out.SurgeryInboxPort.IncomingEvent;

public interface RecordSurgeryClearanceRetryUseCase {
    void deferFailure(IncomingEvent event);
}
