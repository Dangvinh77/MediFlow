package com.mediflow.clinical.application.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mediflow.clinical.application.dto.authority.RecordReadDecision;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.RecordAccessLogPort;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.RecordAccessLogEntry;
import com.mediflow.common.exception.ForbiddenOperationException;

/** Applies the read policy and records every decision before any data leaves the service. */
@Service
public class RecordReadGuard {
    private final RecordReadPolicy policy;
    private final RecordAccessLogPort log;
    private final CurrentClinicalActorPort actors;
    private final CorrelationIdProvider correlationIds;

    public RecordReadGuard(RecordReadPolicy policy, RecordAccessLogPort log, CurrentClinicalActorPort actors,
            CorrelationIdProvider correlationIds) {
        this.policy = policy;
        this.log = log;
        this.actors = actors;
        this.correlationIds = correlationIds;
    }

    /** Authorizes the current actor for one record, or throws after logging the denial. */
    public RecordReadDecision requireRecord(MedicalRecord record) {
        ActorIdentity actor = currentActor();
        Instant at = Instant.now();
        String correlationId = correlationIds.currentOrCreate().toString();
        RecordReadDecision decision = policy.forRecord(actor, record, at, correlationId);
        append(actor, record.getPatientId(), record.getRecordId(), "READ", decision, correlationId, at);
        return requireAllowed(decision);
    }

    /** Authorizes the current actor for a patient's record list, or throws after logging the denial. */
    public RecordReadDecision requirePatient(UUID patientId) {
        ActorIdentity actor = currentActor();
        Instant at = Instant.now();
        String correlationId = correlationIds.currentOrCreate().toString();
        RecordReadDecision decision = policy.forPatient(actor, patientId, at, correlationId);
        append(actor, patientId, null, "LIST", decision, correlationId, at);
        return requireAllowed(decision);
    }

    /** Logs that an amendment reviewer was shown clinical text of this record; the caller already authorized it. */
    public void logReviewRead(UUID patientId, UUID recordId) {
        ActorIdentity actor = currentActor();
        Instant at = Instant.now();
        append(actor, patientId, recordId, "READ", RecordReadDecision.allow("AMENDMENT_REVIEW"),
                correlationIds.currentOrCreate().toString(), at);
    }

    /** Logs and returns a decision for an actor named by a trusted internal caller. */
    public RecordReadDecision decideForActor(ActorIdentity actor, UUID patientId) {
        Instant at = Instant.now();
        String correlationId = correlationIds.currentOrCreate().toString();
        RecordReadDecision decision = policy.forPatient(actor, patientId, at, correlationId);
        append(actor, patientId, null, "READ", decision, correlationId, at);
        return decision;
    }

    private ActorIdentity currentActor() {
        ActorIdentity actor = actors.current();
        if (actor == null || actor.accountId() == null || actor.role() == null) {
            throw new ForbiddenOperationException(RecordReadPolicy.DENIED, "An authenticated staff identity is required");
        }
        return actor;
    }

    private void append(ActorIdentity actor, UUID patientId, UUID recordId, String action,
            RecordReadDecision decision, String correlationId, Instant at) {
        log.append(new RecordAccessLogEntry(actor.accountId(), actor.staffId(), actor.role(), patientId, recordId,
                action, decision.allowed() ? "ALLOWED" : "DENIED", decision.basis(), null, correlationId, at));
    }

    private static RecordReadDecision requireAllowed(RecordReadDecision decision) {
        if (!decision.allowed()) {
            throw new ForbiddenOperationException(decision.denialCode(), "Reading this medical record is not permitted");
        }
        return decision;
    }
}
