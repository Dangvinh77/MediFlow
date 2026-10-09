package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.SurgeryCancellationCommand;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SurgeryCancellationRepositoryPort {
    void claimAndLock(SurgeryCancellationCommand command);
    Optional<SurgeryCancellationCommand> lockPending(UUID surgeryCaseId);
    /** Atomic local adjustment, not a completed cash refund. Returns false until exact planned source exists. */
    boolean apply(SurgeryCancellationCommand command);
    /** Called while the issuer holds the case/account locks, before it can publish a payable request. */
    boolean recoverDuringIssuance(UUID surgeryCaseId);
    List<UUID> dueCases(int limit);
    void defer(UUID surgeryCaseId, Instant retryAt);
    void reject(UUID surgeryCaseId, String code);
    com.mediflow.billing.application.dto.response.SurgeryCancellationDTO get(UUID surgeryCaseId);
}
