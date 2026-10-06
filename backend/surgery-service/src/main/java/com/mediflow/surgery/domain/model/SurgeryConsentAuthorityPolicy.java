package com.mediflow.surgery.domain.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Clinical/legal policy is explicit per consent type. Coarse API roles never prove signing rights. */
public record SurgeryConsentAuthorityPolicy(UUID policyId,long revision,SurgeryConsentType consentType,
        Set<SurgeryConsentSignerType> signers,boolean witnessRequired,boolean documentRequired) {
    public SurgeryConsentAuthorityPolicy {
        if (policyId == null || revision < 1 || consentType == null || signers == null || signers.isEmpty()
                || signers.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("Explicit consent policy required");
        signers = Set.copyOf(signers);
    }

    public boolean permitsSigning(SurgeryCase value,SurgeryConsentRecord consent,Authority authority,Instant at) {
        if (!matches(value,consent,authority,at) || !consent.isActive() || !authority.recordingApproved()
                || !signers.contains(consent.signerType()) || consent.signedAt().isAfter(at)
                || !authority.recorderAccountId().equals(consent.auditHistory().getFirst().recordedBy().accountId())
                || documentRequired && consent.evidenceDocumentId() == null
                || witnessRequired && (authority.witnessStaffId() == null || !authority.witnessVerified())) return false;
        return consent.signerType() == SurgeryConsentSignerType.PATIENT
                ? consent.signerId().equals(value.getPatientId()) : authority.relationshipVerified();
    }

    public boolean permitsRevocation(SurgeryCase value,SurgeryConsentRecord consent,SurgeryAuditActor actor,Authority authority,Instant at) {
        return matches(value,consent,authority,at) && consent.isActive() && actor != null
                && actor.actorType() == SurgeryActorType.HUMAN && actor.accountId().equals(authority.recorderAccountId())
                && authority.revocationApproved() && value.getStatus() != SurgeryStatus.IN_PROGRESS
                && value.getStatus() != SurgeryStatus.COMPLETED && value.getStatus() != SurgeryStatus.CANCELLED;
    }

    private boolean matches(SurgeryCase value,SurgeryConsentRecord consent,Authority authority,Instant at) {
        return value != null && consent != null && authority != null && at != null
                && value.getSurgeryCaseId().equals(consent.surgeryCaseId()) && value.getSurgeryCaseId().equals(authority.surgeryCaseId())
                && consent.consentType() == consentType && consentType == authority.consentType()
                && consent.signerId().equals(authority.signerId()) && consent.signerType() == authority.signerType()
                && !at.isBefore(authority.validFrom()) && (authority.validUntil() == null || at.isBefore(authority.validUntil()));
    }

    public record Authority(UUID surgeryCaseId,SurgeryConsentType consentType,UUID signerId,SurgeryConsentSignerType signerType,
            UUID recorderAccountId,UUID witnessStaffId,boolean witnessVerified,boolean relationshipVerified,
            boolean recordingApproved,boolean revocationApproved,Instant validFrom,Instant validUntil) {
        public Authority {
            if (surgeryCaseId == null || consentType == null || signerId == null || signerType == null || recorderAccountId == null
                    || validFrom == null || validUntil != null && !validUntil.isAfter(validFrom)
                    || witnessVerified && witnessStaffId == null) throw new IllegalArgumentException("Invalid exact consent authority");
        }
    }
}
