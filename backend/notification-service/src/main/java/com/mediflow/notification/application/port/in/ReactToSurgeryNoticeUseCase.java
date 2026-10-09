package com.mediflow.notification.application.port.in;

import com.mediflow.notification.application.dto.command.SurgeryNoticeCommand;

public interface ReactToSurgeryNoticeUseCase {
    void receive(SurgeryNoticeCommand command);
}
