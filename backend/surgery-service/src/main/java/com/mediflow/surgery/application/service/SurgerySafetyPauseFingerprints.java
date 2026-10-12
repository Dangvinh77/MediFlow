package com.mediflow.surgery.application.service;

import com.mediflow.surgery.domain.model.SurgerySafetyPause;
import java.util.ArrayList;

/** Delivery correlation, generated pause ID and recording time do not change an observed intent. */
public final class SurgerySafetyPauseFingerprints {
    private SurgerySafetyPauseFingerprints() { }

    public static String intent(SurgerySafetyPause value) {
        var fields = new ArrayList<String>();
        fields.add("SAFETY_PAUSE_V1");
        fields.add(value.commandId().toString());
        fields.add(value.surgeryCaseId().toString());
        fields.add(Long.toString(value.expectedCaseRevision()));
        fields.add(value.scheduleId().toString());
        fields.add(Long.toString(value.scheduleRevision()));
        fields.add(value.phase().name());
        fields.add(value.observedAt().toString());
        fields.add(value.recorder().accountId().toString());
        fields.add(value.recorder().verifiedStaffId().toString());
        var policy = value.policy();
        fields.add(policy.policyId().toString());
        fields.add(Long.toString(policy.revision()));
        fields.add(policy.procedureCode());
        fields.add(policy.approvalReferenceId().toString());
        for (var phase : policy.phases()) {
            fields.add(phase.phase().name());
            fields.add(Integer.toString(phase.requiredParticipantRoles().size()));
            phase.requiredParticipantRoles().stream().sorted().forEach(role -> fields.add(role.name()));
            fields.add(Integer.toString(phase.items().size()));
            for (var item : phase.items()) {
                fields.add(item.itemCode());
                fields.add(Integer.toString(item.permittedNotApplicableReasons().size()));
                fields.addAll(item.permittedNotApplicableReasons());
            }
        }
        fields.add(Integer.toString(value.participants().size()));
        for (var person : value.participants()) {
            fields.add(person.staffId().toString());
            fields.add(person.role().name());
            fields.add(person.authorityReferenceId().toString());
            fields.add(Long.toString(person.authorityRevision()));
        }
        fields.add(Integer.toString(value.decisions().size()));
        for (var item : value.decisions()) {
            fields.add(item.itemCode());
            fields.add(item.status().name());
            fields.add(item.evidenceReferenceId().toString());
            fields.add(Long.toString(item.evidenceRevision()));
            fields.add(item.reasonCode());
            fields.add(item.applicabilityEvidenceId() == null ? null : item.applicabilityEvidenceId().toString());
        }
        return SurgeryCommandReceipts.fingerprint(fields.toArray(String[]::new));
    }
}
