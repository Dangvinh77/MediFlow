package com.mediflow.surgery.application.port.out;

import com.mediflow.surgery.domain.model.ReadinessSnapshot;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryResult;
import com.mediflow.surgery.domain.model.SurgerySchedule;

/** Durable LOCAL intent, not approved wire bytes. No broker/dispatch method is exposed. */
public interface SurgeryLifecycleIntentPort {
    void holdReady(SurgeryCase value, SurgerySchedule schedule, ReadinessSnapshot snapshot, String correlationId);
    void holdCompleted(SurgeryCase value, SurgerySchedule schedule, SurgeryResult result, String correlationId);
}
