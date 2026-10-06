package com.mediflow.surgery.application.port.in;

import com.mediflow.surgery.application.dto.SurgeryLifecycleCommand;
import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.domain.model.SurgeryPerformedItem;
import java.time.Instant;
import java.util.List;

public interface CompleteSurgeryUseCase {
    SurgeryCommandOutcome complete(Command command);
    record Command(SurgeryLifecycleCommand identity, String procedureCode, String methodCode,
            String outcomeCode, String complicationGroupCode, Instant actualStartAt,
            Instant actualEndAt, List<SurgeryPerformedItem> performedItems) {
        public Command {
            if (identity == null || performedItems == null) throw new IllegalArgumentException("Completion identity/items required");
            performedItems = List.copyOf(performedItems);
        }
    }
}
