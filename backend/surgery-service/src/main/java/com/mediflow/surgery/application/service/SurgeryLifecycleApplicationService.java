package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.port.out.SurgeryCareEventCapturePort;

import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.dto.SurgeryLifecycleCommand;
import com.mediflow.surgery.application.dto.SurgeryReadinessEvidence;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.application.port.in.CompleteSurgeryUseCase;
import com.mediflow.surgery.application.port.in.EvaluateSurgeryReadinessUseCase;
import com.mediflow.surgery.application.port.in.FinalizeSurgeryScheduleUseCase;
import com.mediflow.surgery.application.port.in.StartSurgeryUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryChecklistRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryCommandReceiptPort;
import com.mediflow.surgery.application.port.out.SurgeryConsentRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryFinancialClearanceRepositoryPort;
import com.mediflow.surgery.application.port.out.FinancialClearanceLookupPort;
import com.mediflow.surgery.application.port.out.SurgeryLifecycleIntentPort;
import com.mediflow.surgery.application.port.out.SurgeryReadinessAuthorityPort;
import com.mediflow.surgery.application.port.out.SurgeryReadinessSnapshotPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryResultRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryUnitOfWorkPort;
import com.mediflow.surgery.domain.exception.SurgeryCaseNotFoundException;
import com.mediflow.surgery.domain.exception.SurgeryRuleException;
import com.mediflow.surgery.domain.model.ReadinessSnapshot;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryConsentType;
import com.mediflow.surgery.domain.model.SurgeryDependencyType;
import com.mediflow.surgery.domain.model.SurgeryResult;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Authority-backed lifecycle; production wiring remains gated independently from its HTTP boundary. */
public class SurgeryLifecycleApplicationService implements EvaluateSurgeryReadinessUseCase,
        FinalizeSurgeryScheduleUseCase, StartSurgeryUseCase, CompleteSurgeryUseCase {
    private final SurgeryCaseRepositoryPort cases;
    private final SurgeryScheduleRepositoryPort schedules;
    private final SurgeryCommandReceiptPort receipts;
    private final SurgeryReadinessSnapshotPort snapshots;
    private final SurgeryChecklistRepositoryPort checklists;
    private final SurgeryConsentRepositoryPort consents;
    private final SurgeryFinancialClearanceRepositoryPort clearances;
    private final FinancialClearanceLookupPort financialAuthority;
    private final SurgeryResourceReservationPort resources;
    private final SurgeryResultRepositoryPort results;
    private final SurgeryReadinessAuthorityPort authority;
    private final SurgeryLifecycleIntentPort intents;
    private final SurgeryClockPort clock;
    private final SurgeryCareEventCapturePort events;
    private final SurgeryReadinessEngine engine;
    private final SurgeryUnitOfWorkPort unitOfWork;

    public SurgeryLifecycleApplicationService(SurgeryCaseRepositoryPort cases, SurgeryScheduleRepositoryPort schedules,
            SurgeryCommandReceiptPort receipts, SurgeryReadinessSnapshotPort snapshots,
            SurgeryChecklistRepositoryPort checklists, SurgeryConsentRepositoryPort consents,
            SurgeryFinancialClearanceRepositoryPort clearances, SurgeryResourceReservationPort resources,
            SurgeryResultRepositoryPort results, SurgeryReadinessAuthorityPort authority,
            SurgeryLifecycleIntentPort intents, SurgeryClockPort clock, SurgeryReadinessEngine engine,
            FinancialClearanceLookupPort financialAuthority, SurgeryCareEventCapturePort events,
            SurgeryUnitOfWorkPort unitOfWork) {
        this.unitOfWork = java.util.Objects.requireNonNull(unitOfWork);
        this.events = java.util.Objects.requireNonNull(events);
        this.cases = cases; this.schedules = schedules; this.receipts = receipts; this.snapshots = snapshots;
        this.checklists = checklists; this.consents = consents; this.clearances = clearances; this.resources = resources;
        this.results = results; this.authority = authority; this.intents = intents; this.clock = clock; this.engine = engine;
        this.financialAuthority = java.util.Objects.requireNonNull(financialAuthority);
    }

    @Override
    public SurgeryCommandOutcome evaluate(SurgeryLifecycleCommand command) {
        return decide("EVALUATE_READINESS",command,SurgeryStatus.PREOP_IN_PROGRESS);
    }

    @Override
    public SurgeryCommandOutcome finalizeSchedule(SurgeryLifecycleCommand command) {
        return decide("FINALIZE_SCHEDULE",command,SurgeryStatus.READY);
    }

    @Override
    public SurgeryCommandOutcome start(SurgeryLifecycleCommand command) {
        return decide("START_SURGERY",command,SurgeryStatus.SCHEDULED);
    }

    private SurgeryCommandOutcome decide(String operation, SurgeryLifecycleCommand command, SurgeryStatus required) {
        String fingerprint = fingerprint(operation,command);
        return unitOfWork.outside(() -> {
            authority.authorize(command.actor(),operation,command.surgeryCaseId());
            var replay = probe(operation,command,fingerprint);
            if (replay != null) return replay;
            Preflight observed;
            try {
                observed = unitOfWork.read(() -> {
                    var value = find(command);
                    requireStatus(value,required);
                    return new Preflight(value,schedule(command));
                });
            } catch (SurgeryRevisionConflictException | SurgeryRuleException failure) {
                // Another identical caller may commit between the first probe and preflight read.
                var committed = probe(operation,command,fingerprint);
                if (committed != null) return committed;
                throw failure;
            }
            var evidence = authority.observe(observed.value(),observed.schedule(),command.correlationId());
            if (evidence == null) throw new UpstreamUnavailableException("Required readiness authority is unavailable");
            var verified = observeFinancialAuthority(observed.value(), evidence, command.correlationId());
            return unitOfWork.write(() -> decideAtomically(operation,command,required,fingerprint,observed.schedule(),verified));
        });
    }

    private SurgeryCommandOutcome decideAtomically(String operation, SurgeryLifecycleCommand command,
            SurgeryStatus required, String fingerprint, SurgerySchedule planned, SurgeryReadinessEvidence evidence) {
        var claim = claim(operation,command,fingerprint);
        if (claim.isReplay()) return claim.replay();
        var value = lock(command);
        requireStatus(value,required);
        var current = schedule(command);
        if (!planned.equals(current)) throw new SurgeryRevisionConflictException();
        if (required != SurgeryStatus.PREOP_IN_PROGRESS) requirePinnedSchedule(value,current);
        resources.lockForMutation(current);
        authority.reconcile(value,current,evidence); // Local revision fence, never REST under locks.
        Instant at = clock.now(); // Freshness/expiry must also cover resource-lock wait.
        var snapshot = engine.evaluate(value,current,reconcileOwnedProofs(value,evidence,at),at);
        snapshots.store(snapshot);
        boolean priorExpired = required != SurgeryStatus.PREOP_IN_PROGRESS && !value.getReadinessSnapshot().isValidAt(at);
        boolean changed = required != SurgeryStatus.PREOP_IN_PROGRESS && !sameDependencies(value.getReadinessSnapshot(),snapshot);
        if (!snapshot.isReady() || priorExpired || changed) {
            if (required != SurgeryStatus.PREOP_IN_PROGRESS) {
                var pendingEvent = SurgeryReadinessInvalidation.invalidateIfRequired(value,actor(command),command.correlationId(),at,
                        "READINESS_RECHECK_FAILED",schedules,resources);
                cases.save(value,command.expectedCaseRevision());
                pendingEvent.capture(value, events);
            }
            // Return a committed denial, not an exception that rolls back the invalidation/audit.
            String denial = priorExpired ? "READINESS_EXPIRED" : !snapshot.isReady() ? "NOT_READY" : "READINESS_CHANGED";
            var reasons = new java.util.ArrayList<>(snapshot.blockingReasons());
            if (priorExpired) reasons.add("READINESS_EXPIRED");
            if (changed) reasons.add("READINESS_CHANGED");
            var outcome = new SurgeryCommandOutcome(operation,value.getSurgeryCaseId(),value.getRevision(),
                    snapshot.snapshotId(),0,denial,at,false,reasons);
            SurgeryCommandReceipts.complete(receipts,claim.receiptId(),outcome);
            return outcome;
        }
        switch (required) {
            case PREOP_IN_PROGRESS -> {
                value.markReady(snapshot,actor(command),command.correlationId());
                cases.save(value,command.expectedCaseRevision());
                intents.holdReady(value,current,snapshot,command.correlationId());
            }
            case READY -> {
                resources.reserve(current,at);
                value.finalizeSchedule(actor(command),command.correlationId(),at);
                cases.save(value,command.expectedCaseRevision());
            }
            case SCHEDULED -> {
                resources.markInUse(value.getSurgeryCaseId(),current.scheduleId(),current.revision(),at);
                value.start(snapshot,actor(command),command.correlationId(),at);
                cases.save(value,command.expectedCaseRevision());
            }
            default -> throw new IllegalStateException("Unsupported lifecycle decision");
        }
        return required == SurgeryStatus.PREOP_IN_PROGRESS
                ? finish(operation,command,claim,value,snapshot.snapshotId(),0,value.getStatus().name(),at)
                : finish(operation,command,claim,value,current.scheduleId(),current.revision(),value.getStatus().name(),at);
    }

    @Override
    public SurgeryCommandOutcome complete(CompleteSurgeryUseCase.Command command) {
        if (command == null) throw new IllegalArgumentException("Completion required");
        var identity = command.identity();
        // Sort and normalize decimal scale; delimiter framing preserves distinct performed lines.
        String itemFingerprint = SurgeryCommandReceipts.fingerprint(command.performedItems().stream()
                .sorted(Comparator.comparing(item -> item.performedItemId().toString()))
                .map(item -> SurgeryCommandReceipts.fingerprint(item.performedItemId().toString(),item.itemCode(),item.priceCode(),
                        item.quantity().stripTrailingZeros().toPlainString())).toArray(String[]::new));
        String fingerprint = SurgeryCommandReceipts.fingerprint(fingerprint("COMPLETE_SURGERY",identity),
                command.procedureCode(),command.methodCode(),command.outcomeCode(),command.complicationGroupCode(),
                String.valueOf(command.actualStartAt()),String.valueOf(command.actualEndAt()),itemFingerprint);
        return unitOfWork.outside(() -> {
            authority.authorize(identity.actor(),"COMPLETE_SURGERY",identity.surgeryCaseId());
            var replay = probe("COMPLETE_SURGERY",identity,fingerprint);
            if (replay != null) return replay;
            SurgeryCase observed;
            try {
                observed = unitOfWork.read(() -> {
                    var value = find(identity);
                    requireStatus(value,SurgeryStatus.IN_PROGRESS);
                    return value;
                });
            } catch (SurgeryRevisionConflictException | SurgeryRuleException failure) {
                var committed = probe("COMPLETE_SURGERY",identity,fingerprint);
                if (committed != null) return committed;
                throw failure;
            }
            var result = new SurgeryResult(UUID.randomUUID(),identity.surgeryCaseId(),command.procedureCode(),command.methodCode(),
                    command.outcomeCode(),command.complicationGroupCode(),command.actualStartAt(),command.actualEndAt(),
                    command.performedItems(),clock.now(),actor(identity),identity.correlationId());
            authority.verifyResult(observed,result,identity.correlationId());
            return unitOfWork.write(() -> completeAtomically(command,fingerprint,result));
        });
    }

    private SurgeryCommandOutcome completeAtomically(CompleteSurgeryUseCase.Command command, String fingerprint, SurgeryResult verified) {
        var identity = command.identity();
        var claim = claim("COMPLETE_SURGERY",identity,fingerprint);
        if (claim.isReplay()) return claim.replay();
        var value = lock(identity);
        requireStatus(value,SurgeryStatus.IN_PROGRESS);
        var current = schedule(identity);
        requirePinnedSchedule(value,current);
        resources.lockForMutation(current);
        Instant at = clock.now();
        var result = new SurgeryResult(verified.resultId(),verified.surgeryCaseId(),verified.procedureCode(),verified.methodCode(),
                verified.treatmentOutcomeCode(),verified.complicationGroupCode(),verified.actualStartAt(),verified.actualEndAt(),
                verified.performedItems(),at,verified.recordedBy(),verified.correlationId());
        if (results.findByCaseId(value.getSurgeryCaseId()).isPresent()) throw new SurgeryRevisionConflictException();
        results.create(result); // Adapter verifies persisted IN_PROGRESS before transition.
        resources.release(value.getSurgeryCaseId(),current.scheduleId(),current.revision(),at);
        value.complete(actor(identity),identity.correlationId(),at);
        cases.save(value,identity.expectedCaseRevision());
        intents.holdCompleted(value,current,result,identity.correlationId());
        return finish("COMPLETE_SURGERY",identity,claim,value,result.resultId(),0,"COMPLETED",at);
    }

    private SurgeryReadinessEvidence observeFinancialAuthority(SurgeryCase value, SurgeryReadinessEvidence evidence, String correlation) {
        var proofs = evidence.proofs().stream().map(proof -> {
            if (proof.type() != SurgeryDependencyType.FINANCIAL_CLEARANCE) return proof;
            var grant = unitOfWork.read(() -> clearances.findById(proof.sourceId()).orElse(null));
            if (grant == null || !grant.matches(value)) return new SurgeryReadinessEvidence.Proof(proof.type(),proof.sourceId(),
                    proof.revision(),SurgeryReadinessEvidence.Decision.UNSATISFIED,proof.observedAt(),proof.validFrom(),proof.validUntil());
            var current = financialAuthority.observe(grant,correlation); // Mandatory live Billing read, before mutation locks.
            if (current == null) throw new UpstreamUnavailableException("Billing financial authority unavailable");
            Instant until = current.validUntil();
            if (proof.validUntil() != null && (until == null || proof.validUntil().isBefore(until))) until = proof.validUntil();
            return new SurgeryReadinessEvidence.Proof(proof.type(),proof.sourceId(),proof.revision(),
                    current.eligible() ? proof.decision() : SurgeryReadinessEvidence.Decision.UNSATISFIED,
                    current.observedAt(),proof.validFrom(),until);
        }).toList();
        return new SurgeryReadinessEvidence(evidence.surgeryCaseId(),evidence.patientId(),evidence.departmentId(),evidence.episode(),
                evidence.caseRevision(),evidence.scheduleId(),evidence.scheduleRevision(),proofs);
    }

    private SurgeryReadinessEvidence reconcileOwnedProofs(SurgeryCase value, SurgeryReadinessEvidence evidence, Instant at) {
        var checklist = checklists.findSnapshotByCaseId(value.getSurgeryCaseId());
        var recordedConsents = consents.findByCaseId(value.getSurgeryCaseId());
        var proofs = evidence.proofs().stream().map(proof -> {
            if (proof.type() == SurgeryDependencyType.FINANCIAL_CLEARANCE) {
                var grant = clearances.lockById(proof.sourceId()).orElse(null);
                if (grant != null && grant.isValidFor(value,at)) {
                    Instant until = proof.validUntil();
                    if (grant.expiresAt() != null && (until == null || grant.expiresAt().isBefore(until))) until = grant.expiresAt();
                    Instant from = grant.grantedAt().isAfter(proof.validFrom()) ? grant.grantedAt() : proof.validFrom();
                    return new SurgeryReadinessEvidence.Proof(proof.type(),proof.sourceId(),proof.revision(),proof.decision(),proof.observedAt(),from,until);
                }
                return new SurgeryReadinessEvidence.Proof(proof.type(),proof.sourceId(),proof.revision(),SurgeryReadinessEvidence.Decision.UNSATISFIED,
                        proof.observedAt(),proof.validFrom(),proof.validUntil());
            }
            boolean matches = switch (proof.type()) {
                case CHECKLIST -> checklist.filter(item -> item.surgeryCaseId().equals(value.getSurgeryCaseId())
                        && item.checklistSnapshotId().equals(proof.sourceId()) && item.revision() == proof.revision()
                        && item.mandatoryChecklistComplete()).isPresent();
                case SURGERY_CONSENT, ANESTHESIA_CONSENT -> {
                    var type = proof.type() == SurgeryDependencyType.SURGERY_CONSENT ? SurgeryConsentType.SURGERY : SurgeryConsentType.ANESTHESIA;
                    var active = recordedConsents.stream().filter(item -> item.consentType() == type && item.isActive()).toList();
                    yield active.size() == 1 && active.getFirst().surgeryCaseId().equals(value.getSurgeryCaseId())
                            && active.getFirst().consentId().equals(proof.sourceId())
                            && active.getFirst().auditHistory().size() - 1 == proof.revision()
                            && !active.getFirst().signedAt().isAfter(at);
                }
                default -> true; // These require the independently verified authority policy/fence.
            };
            return matches ? proof : new SurgeryReadinessEvidence.Proof(proof.type(),proof.sourceId(),proof.revision(),
                    SurgeryReadinessEvidence.Decision.UNSATISFIED,proof.observedAt(),proof.validFrom(),proof.validUntil());
        }).toList();
        return new SurgeryReadinessEvidence(evidence.surgeryCaseId(),evidence.patientId(),evidence.departmentId(),
                evidence.episode(),evidence.caseRevision(),evidence.scheduleId(),evidence.scheduleRevision(),proofs);
    }

    private SurgeryCommandReceipts.ClaimResult claim(String operation, SurgeryLifecycleCommand command, String fingerprint) {
        if (command == null) throw new IllegalArgumentException("Command required");
        return SurgeryCommandReceipts.claim(receipts,new SurgeryCommandReceiptPort.Key(command.actor().accountId().toString(),
                operation+":"+command.surgeryCaseId(),command.idempotencyKey()),fingerprint,operation,command.surgeryCaseId());
    }

    private SurgeryCommandOutcome probe(String operation, SurgeryLifecycleCommand command, String fingerprint) {
        return unitOfWork.read(() -> receipts.find(new SurgeryCommandReceiptPort.Key(command.actor().accountId().toString(),
                operation+":"+command.surgeryCaseId(),command.idempotencyKey()),fingerprint)
                .map(found -> SurgeryCommandReceipts.resolve(found,operation,command.surgeryCaseId()).replay()).orElse(null));
    }

    private record Preflight(SurgeryCase value, SurgerySchedule schedule) { }
    private SurgeryCase find(SurgeryLifecycleCommand command) {
        var value = cases.findById(command.surgeryCaseId()).orElseThrow(() -> new SurgeryCaseNotFoundException(command.surgeryCaseId()));
        requireRevision(value,command); return value;
    }
    private SurgeryCase lock(SurgeryLifecycleCommand command) {
        var value = cases.lockById(command.surgeryCaseId()).orElseThrow(() -> new SurgeryCaseNotFoundException(command.surgeryCaseId()));
        requireRevision(value,command); return value;
    }
    private SurgerySchedule schedule(SurgeryLifecycleCommand command) {
        var schedule = schedules.findByCaseId(command.surgeryCaseId()).orElseThrow(SurgeryRevisionConflictException::new);
        if (!schedule.surgeryCaseId().equals(command.surgeryCaseId()) || schedule.revision() != command.expectedScheduleRevision()) {
            throw new SurgeryRevisionConflictException();
        }
        return schedule;
    }
    private static void requireRevision(SurgeryCase value, SurgeryLifecycleCommand command) {
        if (value.getRevision() != command.expectedCaseRevision()) throw new SurgeryRevisionConflictException();
    }
    private static void requireStatus(SurgeryCase value, SurgeryStatus required) {
        if (value.getStatus() != required) throw new SurgeryRuleException("SURGERY_INVALID_TRANSITION","Invalid lifecycle transition");
    }
    private static void requirePinnedSchedule(SurgeryCase value, SurgerySchedule schedule) {
        if (value.getReadinessSnapshot() == null || value.getReadinessSnapshot().dependencyRevisions().stream().noneMatch(proof ->
                proof.dependencyType() == SurgeryDependencyType.SCHEDULE && proof.sourceId().equals(schedule.scheduleId())
                        && proof.revision() == schedule.revision())) throw new SurgeryRevisionConflictException();
    }
    private static boolean sameDependencies(ReadinessSnapshot prior, ReadinessSnapshot current) {
        return prior != null && new java.util.HashSet<>(prior.dependencyRevisions()).equals(new java.util.HashSet<>(current.dependencyRevisions()));
    }
    private SurgeryCommandOutcome finish(String operation, SurgeryLifecycleCommand command, SurgeryCommandReceipts.ClaimResult claim,
            SurgeryCase value, UUID subject, long subjectRevision, String state, Instant at) {
        var outcome = new SurgeryCommandOutcome(operation,value.getSurgeryCaseId(),value.getRevision(),subject,subjectRevision,state,at,false);
        SurgeryCommandReceipts.complete(receipts,claim.receiptId(),outcome); return outcome;
    }
    private static SurgeryAuditActor actor(SurgeryLifecycleCommand command) {
        return SurgeryAuditActor.human(command.actor().accountId(),command.actor().verifiedStaffId());
    }
    private static String fingerprint(String operation, SurgeryLifecycleCommand command) {
        if (command == null) throw new IllegalArgumentException("Command required");
        return SurgeryCommandReceipts.fingerprint(operation,command.surgeryCaseId().toString(),Long.toString(command.expectedCaseRevision()),
                Long.toString(command.expectedScheduleRevision()));
    }
}
