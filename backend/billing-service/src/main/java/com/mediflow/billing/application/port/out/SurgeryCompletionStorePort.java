package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.CaptureSurgeryCompletionCommand;

public interface SurgeryCompletionStorePort {
    /** Locks exact source/account/delivery, validates immutable context; returns true on replay. */
    boolean lockAndFindReplay(CaptureSurgeryCompletionCommand command);
    /** Saves only after every performed line has successfully reconciled in the same transaction. */
    void save(CaptureSurgeryCompletionCommand command);
}
