package com.mediflow.surgery.application.exception;

import com.mediflow.common.exception.BusinessRuleException;

public final class SurgeryCommandBusyException extends BusinessRuleException {
    public SurgeryCommandBusyException() {
        super("SURGERY_COMMAND_BUSY", "Resource is busy; retry the same command later");
    }
}
