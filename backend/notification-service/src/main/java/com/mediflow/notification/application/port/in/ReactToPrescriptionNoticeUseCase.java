package com.mediflow.notification.application.port.in;

import com.mediflow.notification.application.dto.command.PrescriptionNoticeCommand;

public interface ReactToPrescriptionNoticeUseCase {
    void receive(PrescriptionNoticeCommand command);
}
