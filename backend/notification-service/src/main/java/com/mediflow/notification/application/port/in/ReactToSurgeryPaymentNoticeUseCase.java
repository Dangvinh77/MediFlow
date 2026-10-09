package com.mediflow.notification.application.port.in;

import com.mediflow.notification.application.dto.command.SurgeryPaymentNoticeCommand;

public interface ReactToSurgeryPaymentNoticeUseCase {
    void receive(SurgeryPaymentNoticeCommand command);
}
