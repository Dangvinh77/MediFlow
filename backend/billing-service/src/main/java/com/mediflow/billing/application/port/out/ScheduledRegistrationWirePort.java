package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.event.OutpatientRegistrationChangedEvent;

public interface ScheduledRegistrationWirePort {
    OutpatientRegistrationChangedEvent decode(String routingKey, byte[] raw);
}
