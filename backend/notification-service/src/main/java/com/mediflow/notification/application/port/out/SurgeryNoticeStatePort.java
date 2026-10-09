package com.mediflow.notification.application.port.out;

import com.mediflow.notification.application.dto.command.SurgeryNoticeCommand;

/** Called only in the inbox/history transaction; row fence serializes all case reminder writers. */
public interface SurgeryNoticeStatePort {
    void lockExactCase(SurgeryNoticeCommand.Context context);
    boolean recordSource(SurgeryNoticeCommand command);
    boolean readyIsSuppressed(SurgeryNoticeCommand command);
    void invalidateSnapshot(SurgeryNoticeCommand command);
    void markTerminal(SurgeryNoticeCommand command);
}
