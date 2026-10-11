package com.mediflow.billing.application.port.in;

/** One strict branch of the existing Billing queue, not a competing listener. */
public interface ConsumeSurgeryCompletionUseCase {
    void receive(String routingKey, byte[] originalBytes);
}
