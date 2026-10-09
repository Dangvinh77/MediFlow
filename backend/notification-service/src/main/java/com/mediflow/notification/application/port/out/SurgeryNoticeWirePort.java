package com.mediflow.notification.application.port.out;

import com.mediflow.notification.application.dto.command.SurgeryNoticeCommand;

public interface SurgeryNoticeWirePort {
    SurgeryNoticeCommand decode(String routingKey, byte[] body);
}
