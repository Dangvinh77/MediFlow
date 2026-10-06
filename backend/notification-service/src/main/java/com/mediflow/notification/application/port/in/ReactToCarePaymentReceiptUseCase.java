package com.mediflow.notification.application.port.in;

import com.mediflow.notification.application.dto.command.CarePaymentReceiptCommand;

public interface ReactToCarePaymentReceiptUseCase {
    void receive(CarePaymentReceiptCommand receipt);
}
