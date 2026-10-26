package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.event.OutpatientFinancialEligibilityGrantedEvent;

public interface ScheduledEligibilityEventPort {
    void appendHeld(OutpatientFinancialEligibilityGrantedEvent event);
}
