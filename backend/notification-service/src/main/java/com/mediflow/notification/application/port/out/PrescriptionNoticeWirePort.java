package com.mediflow.notification.application.port.out;

import com.mediflow.notification.application.dto.command.PrescriptionNoticeCommand;

public interface PrescriptionNoticeWirePort {
    PrescriptionNoticeCommand decode(String routingKey, byte[] body);
}
