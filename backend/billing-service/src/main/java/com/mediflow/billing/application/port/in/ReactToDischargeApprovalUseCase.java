package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.event.DischargeMedicallyApprovedEvent;

public interface ReactToDischargeApprovalUseCase {
    void onDischargeMedicallyApproved(DischargeMedicallyApprovedEvent event);
}
