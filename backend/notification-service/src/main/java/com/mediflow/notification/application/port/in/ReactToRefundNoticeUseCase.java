package com.mediflow.notification.application.port.in;

import com.mediflow.notification.application.dto.command.RefundNoticeCommand;
public interface ReactToRefundNoticeUseCase { void receive(RefundNoticeCommand command); }
