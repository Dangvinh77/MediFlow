package com.mediflow.clinical.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.clinical.domain.model.ClinicQueueTicket;
import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;

public interface ClinicQueueRepositoryPort {
    int allocateRoomQueueNumber(UUID roomId, LocalDate queueDate);
    Optional<ClinicQueueTicket> findActiveByRegistration(UUID registrationId);
    Optional<ClinicQueueTicket> findByIdForUpdate(UUID ticketId);
    ClinicQueueTicket insert(ClinicQueueTicket ticket);
    void update(ClinicQueueTicket before, ClinicQueueTicket after);
    Map<UUID, Integer> activeNumbers(Collection<UUID> registrationIds);
    Optional<ClinicQueueTicket> findById(UUID ticketId);
    void appendAdjustment(UUID ticketId, String type, String reason, UUID actorAccountId, UUID actorStaffId, Instant at);
    /** Registrations still in play for these sessions, with their active ticket when there is one. */
    List<BoardRow> boardRows(Collection<UUID> sessionIds);

    record BoardRow(UUID registrationId, UUID patientId, UUID sessionId, OutpatientRegistrationStatus status, Instant checkedInAt,
            Integer queueNumber, boolean cleared, ClinicQueueTicket ticket, boolean vitalSignsRecorded) {}
}
