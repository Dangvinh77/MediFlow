package com.mediflow.surgery.application.port.out;

import com.mediflow.surgery.application.dto.SurgeryActorIdentity;
import com.mediflow.surgery.application.dto.SurgeryReadinessEvidence;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryResult;
import com.mediflow.surgery.domain.model.SurgerySchedule;

/** No production adapter until clinical/legal/effective-finance contracts pass their gates. */
public interface SurgeryReadinessAuthorityPort {
    /** Authorization is required even for receipt replay; no body-supplied roles or admin bypass. */
    void authorize(SurgeryActorIdentity actor, String operation, java.util.UUID caseId);
    /** External I/O occurs before case/resource locks. Include policies, signer and effective grant authority. */
    SurgeryReadinessEvidence observe(SurgeryCase value, SurgerySchedule schedule, String correlationId);
    /** Local source-revision fence only, NO network I/O under locks. Reject newer captured authority hints. */
    void reconcile(SurgeryCase value, SurgerySchedule schedule, SurgeryReadinessEvidence evidence);
    /** Validate controlled procedure/method/outcome/category/item codes before locks, never calculate prices. */
    void verifyResult(SurgeryCase value, SurgeryResult result, String correlationId);
}
