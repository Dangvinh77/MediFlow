package com.mediflow.notification.application.port.out;

import com.mediflow.notification.application.dto.command.SurgeryPaymentNoticeCommand;

public interface SurgeryPaymentNoticeWirePort {
    SurgeryPaymentNoticeCommand decode(String routingKey, byte[] body);
}
