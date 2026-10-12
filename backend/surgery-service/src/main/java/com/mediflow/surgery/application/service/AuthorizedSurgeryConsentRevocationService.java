package com.mediflow.surgery.application.service;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.application.port.in.ManageSurgeryConsentUseCase;
import com.mediflow.surgery.application.port.in.RevokeSurgeryConsentUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryConsentRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryConsentRevocationAuthorityPort;
import com.mediflow.surgery.application.port.out.SurgeryPreopAuthorityPort;
import com.mediflow.surgery.application.port.out.SurgeryUnitOfWorkPort;
import com.mediflow.surgery.domain.exception.SurgeryRuleException;
import com.mediflow.surgery.domain.model.SurgeryActorType;
import com.mediflow.surgery.domain.model.SurgeryConsentRecord;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Internal only: explicit default-off wiring requires authority; no HTTP mapping is installed.
 * The same case-first lock as START serializes owned consent effects, not external source changes.
 */
public final class AuthorizedSurgeryConsentRevocationService implements RevokeSurgeryConsentUseCase {
    private static final Duration MAX_PROOF_AGE = Duration.ofSeconds(30);
    private final SurgeryCaseRepositoryPort cases;
    private final SurgeryConsentRepositoryPort consents;
    private final ManageSurgeryConsentUseCase kernel;
    private final SurgeryConsentRevocationAuthorityPort authority;
    private final SurgeryClockPort clock;
    private final SurgeryUnitOfWorkPort transactions;

    public AuthorizedSurgeryConsentRevocationService(SurgeryCaseRepositoryPort cases,
            SurgeryConsentRepositoryPort consents, ManageSurgeryConsentUseCase kernel,
            SurgeryConsentRevocationAuthorityPort authority, SurgeryClockPort clock, SurgeryUnitOfWorkPort transactions) {
        this.cases = Objects.requireNonNull(cases);
        this.consents = Objects.requireNonNull(consents);
        this.kernel = Objects.requireNonNull(kernel);
        this.authority = Objects.requireNonNull(authority);
        this.clock = Objects.requireNonNull(clock);
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override public SurgeryCommandOutcome revoke(ManageSurgeryConsentUseCase.RevokeCommand command) {
        Objects.requireNonNull(command);
        var actor = command.recordedBy();
        if (actor.actorType() != SurgeryActorType.HUMAN || actor.verifiedStaffId() == null) {
            throw new SurgeryRuleException("SURGERY_VERIFIED_RECORDER_REQUIRED", "Verified human staff recorder required");
        }
        var observed = transactions.read(() -> {
            var context = SurgeryPreopAuthorityPort.Context.from(cases.findById(command.surgeryCaseId())
                    .orElseThrow(() -> new ResourceNotFoundException("SURGERY_CASE_NOT_FOUND", "Surgery case not found")));
            return new Observed(context, exactConsent(command));
        });
        String fingerprint = fingerprint(observed, command);
        var proof = transactions.outside(() -> authority.approve(observed.context(), observed.consent(), command, fingerprint));
        return transactions.outside(() -> transactions.write(() -> {
            var locked = cases.lockById(command.surgeryCaseId()).orElseThrow(SurgeryRevisionConflictException::new);
            if (!observed.context().equals(SurgeryPreopAuthorityPort.Context.from(locked))
                    || !observed.consent().equals(exactConsent(command))) throw new SurgeryRevisionConflictException();
            requireFresh(proof, fingerprint);
            // Kernel replays the original receipt before expected revision/state checks. Replay
            // still requires authorization above, even when the consent is now revoked/terminal.
            var outcome = kernel.revoke(command);
            // Receipt/resource waits must not commit beyond the authorization observation bound.
            // A failure rolls back audit, readiness/release, receipt and HELD event together.
            requireFresh(proof, fingerprint);
            return outcome;
        }));
    }

    private SurgeryConsentRecord exactConsent(ManageSurgeryConsentUseCase.RevokeCommand command) {
        return consents.findById(command.consentId())
                .filter(value -> value.surgeryCaseId().equals(command.surgeryCaseId()))
                .orElseThrow(SurgeryRevisionConflictException::new);
    }

    private void requireFresh(SurgeryPreopAuthorityPort.Approval proof, String fingerprint) {
        var now = clock.now();
        if (proof == null || !fingerprint.equals(proof.fingerprint()) || proof.observedAt().isAfter(now)
                || Duration.between(proof.observedAt(), now).compareTo(MAX_PROOF_AGE) > 0
                || !now.isBefore(proof.validUntil())) {
            throw new UpstreamUnavailableException("Consent revocation authority is unavailable or stale");
        }
    }

    private static String fingerprint(Observed observed, ManageSurgeryConsentUseCase.RevokeCommand command) {
        var context = observed.context();
        var consent = observed.consent();
        var fields = new ArrayList<String>();
        fields.add("AUTHORIZED_REVOKE_CONSENT_V1");
        fields.add(context.surgeryCaseId().toString());
        fields.add(context.surgeryRequestId().toString());
        fields.add(context.patientId().toString());
        fields.add(context.departmentId().toString());
        fields.add(context.careEpisode().type().name());
        fields.add(context.careEpisode().episodeId().toString());
        fields.add(text(context.careEpisode().admissionId()));
        fields.add(text(context.careEpisode().recordId()));
        fields.add(context.procedureCode());
        fields.add(Long.toString(context.caseRevision()));
        fields.add(consent.consentId().toString());
        fields.add(consent.consentType().name());
        fields.add(consent.signerId().toString());
        fields.add(consent.signerType().name());
        fields.add(text(consent.evidenceDocumentId()));
        fields.add(Integer.toString(consent.auditHistory().size()));
        for (var audit : consent.auditHistory()) {
            fields.add(audit.action().name());
            fields.add(audit.recordedBy().actorType().name());
            fields.add(text(audit.recordedBy().accountId()));
            fields.add(text(audit.recordedBy().verifiedStaffId()));
            fields.add(audit.recordedBy().systemProducer());
            fields.add(audit.occurredAt().toString());
            fields.add(audit.correlationId());
            fields.add(audit.reason());
        }
        fields.add(Long.toString(command.expectedCaseRevision()));
        fields.add(command.recordedBy().accountId().toString());
        fields.add(command.recordedBy().verifiedStaffId().toString());
        fields.add(command.reason().trim());
        fields.add(command.idempotencyKey());
        return SurgeryCommandReceipts.fingerprint(fields.toArray(String[]::new));
    }

    private static String text(Object value) { return value == null ? null : value.toString(); }
    private record Observed(SurgeryPreopAuthorityPort.Context context, SurgeryConsentRecord consent) { }
}
