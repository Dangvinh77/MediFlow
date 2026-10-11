package com.mediflow.billing.application.port.in;

public interface ConsumeAdmissionMedicalDischargeUseCase {
    void receive(String routingKey, byte[] bytes);
}
