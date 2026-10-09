package com.mediflow.notification.application.port.out;

import com.mediflow.notification.application.dto.command.RefundNoticeCommand;
public interface RefundNoticeWirePort { RefundNoticeCommand decode(String key, byte[] body); }
