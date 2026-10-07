package com.mediflow.surgery.application.port.in;

import com.mediflow.surgery.application.dto.SurgeryCreationOutcome;
import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryPlannedItem;
import com.mediflow.surgery.domain.model.SurgeryPriority;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Internal channel-neutral command. A driving adapter must supply a trusted actor, never body roles. */
public interface CreateSurgeryCaseUseCase {
    SurgeryCreationOutcome create(Command command);

    record Command(UUID requestId, CareEpisode careEpisode, UUID patientId, UUID departmentId,
                   UUID requestedBy, String procedureCode, String indication, SurgeryPriority priority,
                   Instant requestedAt, long templateRevision, List<SurgeryPlannedItem> plannedItems,
                   SurgeryAuditActor actor, String correlationId) {
        public Command {
            Objects.requireNonNull(requestId);
            Objects.requireNonNull(careEpisode);
            Objects.requireNonNull(patientId);
            Objects.requireNonNull(departmentId);
            Objects.requireNonNull(requestedBy);
            Objects.requireNonNull(priority);
            Objects.requireNonNull(requestedAt);
            Objects.requireNonNull(actor);
            if (procedureCode == null || !procedureCode.matches("[A-Za-z0-9._-]{1,64}")
                    || indication == null || indication.isBlank() || indication.length() > 4_000
                    || templateRevision < 1 || correlationId == null || correlationId.isBlank()
                    || correlationId.length() > 128 || plannedItems == null || plannedItems.isEmpty()
                    || plannedItems.size() > 100) {
                throw new IllegalArgumentException("Invalid Surgery creation command");
            }
            indication = indication.trim();
            plannedItems = List.copyOf(plannedItems);
            var itemCodes = new HashSet<String>();
            for (var item : plannedItems) {
                if (!itemCodes.add(item.itemCode())) throw new IllegalArgumentException("Duplicate planned item code");
            }
        }
    }
}
