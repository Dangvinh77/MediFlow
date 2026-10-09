package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.command.SurgeryCancellationCommand;
import java.util.UUID;

public interface ProcessSurgeryCancellationUseCase {
    void receive(SurgeryCancellationCommand command);
    void recover(UUID surgeryCaseId);
    void recoverPending();
}
