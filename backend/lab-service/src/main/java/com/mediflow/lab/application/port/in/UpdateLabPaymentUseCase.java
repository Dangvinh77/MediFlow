package com.mediflow.lab.application.port.in;

import java.util.UUID;

/** Updates the payment flag of one explicitly identified Lab aggregate. */
public interface UpdateLabPaymentUseCase {

    void markPaid(UUID testId);
}
