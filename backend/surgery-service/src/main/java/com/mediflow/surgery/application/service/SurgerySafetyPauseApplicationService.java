package com.mediflow.surgery.application.service;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.application.port.in.RecordSurgerySafetyPauseUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryPreopAuthorityPort;
import com.mediflow.surgery.application.port.out.SurgerySafetyPauseAuthorityPort;
import com.mediflow.surgery.application.port.out.SurgerySafetyPauseStorePort;
import com.mediflow.surgery.application.port.out.SurgeryUnitOfWorkPort;
import com.mediflow.surgery.domain.model.SurgerySafetyPause;
import java.time.Duration;
import java.util.Objects;

/** No automatic bean/public route: a real authority provider is mandatory before exposing recording. */
public final class SurgerySafetyPauseApplicationService implements RecordSurgerySafetyPauseUseCase {
    private static final Duration MAX_PROOF_AGE = Duration.ofSeconds(30);
    private final SurgeryCaseRepositoryPort cases;
    private final SurgerySafetyPauseStorePort store;
    private final SurgerySafetyPauseAuthorityPort authority;
    private final SurgeryClockPort clock;
    private final SurgeryUnitOfWorkPort transactions;

    public SurgerySafetyPauseApplicationService(SurgeryCaseRepositoryPort cases, SurgerySafetyPauseStorePort store,
            SurgerySafetyPauseAuthorityPort authority, SurgeryClockPort clock, SurgeryUnitOfWorkPort transactions) {
        this.cases = Objects.requireNonNull(cases);
        this.store = Objects.requireNonNull(store);
        this.authority = Objects.requireNonNull(authority);
        this.clock = Objects.requireNonNull(clock);
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override public SurgerySafetyPause record(SurgerySafetyPause proposal) {
        Objects.requireNonNull(proposal);
        var context = transactions.read(() -> SurgeryPreopAuthorityPort.Context.from(cases.findById(proposal.surgeryCaseId())
                .orElseThrow(() -> new ResourceNotFoundException("SURGERY_CASE_NOT_FOUND", "Surgery case not found"))));
        String fingerprint = SurgeryCommandReceipts.fingerprint(SurgerySafetyPauseFingerprints.intent(proposal),
                context.surgeryRequestId().toString(), context.patientId().toString(), context.departmentId().toString(),
                context.careEpisode().toString(), context.procedureCode(), Long.toString(context.caseRevision()));
        var approval = transactions.outside(() -> authority.approve(context, proposal, fingerprint));
        return transactions.outside(() -> transactions.write(() -> {
            var locked = cases.lockById(context.surgeryCaseId()).orElseThrow(SurgeryRevisionConflictException::new);
            if (!context.equals(SurgeryPreopAuthorityPort.Context.from(locked))) throw new SurgeryRevisionConflictException();
            requireFresh(approval, fingerprint);
            var value = new SurgerySafetyPause(proposal.pauseId(), proposal.commandId(), proposal.surgeryCaseId(),
                    proposal.expectedCaseRevision(), proposal.scheduleId(), proposal.scheduleRevision(), proposal.policy(),
                    proposal.phase(), proposal.observedAt(), clock.now(), proposal.recorder(), proposal.correlationId(),
                    proposal.participants(), proposal.decisions());
            var original = store.append(value);
            requireFresh(approval, fingerprint); // A ledger/schedule wait must not outlive source approval.
            return original;
        }));
    }

    private void requireFresh(SurgeryPreopAuthorityPort.Approval proof, String fingerprint) {
        var now = clock.now();
        if (proof == null || !proof.fingerprint().equals(fingerprint) || proof.observedAt().isAfter(now)
                || Duration.between(proof.observedAt(), now).compareTo(MAX_PROOF_AGE) > 0 || !now.isBefore(proof.validUntil())) {
            throw new UpstreamUnavailableException("Safety pause authority is unavailable or stale");
        }
    }
}
