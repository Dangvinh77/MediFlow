package com.mediflow.clinical.application.port.out;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.clinical.domain.model.ClinicQueueTicket;

public interface ClinicQueueRepositoryPort {
    int allocateRoomQueueNumber(UUID roomId, LocalDate queueDate);
    Optional<ClinicQueueTicket> findActiveByRegistration(UUID registrationId);
    Optional<ClinicQueueTicket> findByIdForUpdate(UUID ticketId);
    ClinicQueueTicket insert(ClinicQueueTicket ticket);
    void update(ClinicQueueTicket before, ClinicQueueTicket after);
    Map<UUID, Integer> activeNumbers(Collection<UUID> registrationIds);
}
