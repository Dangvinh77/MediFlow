package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.event.OutpatientRegistrationChangedEvent;

public interface ConsumeOutpatientRegistrationFactUseCase {
    void consume(OutpatientRegistrationChangedEvent event, byte[] rawEnvelope);
}
