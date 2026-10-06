package com.mediflow.billing.application.port.out;

import java.util.UUID;
import com.mediflow.billing.application.event.LedgerIntegrationEvent;

public interface LedgerEventPort {
    void appendHeld(UUID accountId, LedgerIntegrationEvent event);
}
