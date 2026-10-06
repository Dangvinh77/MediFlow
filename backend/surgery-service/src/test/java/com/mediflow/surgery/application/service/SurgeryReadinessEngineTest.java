package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.dto.SurgeryReadinessEvidence;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryDependencyType;
import com.mediflow.surgery.domain.model.SurgeryPriority;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryTeamAssignment;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SurgeryReadinessEngineTest {
    private final Instant now = Instant.parse("2026-10-06T08:00:00Z");
    private final SurgeryReadinessEngine engine = new SurgeryReadinessEngine(Duration.ofSeconds(30),Duration.ofSeconds(5));
    private final SurgeryCase value = SurgeryCase.create(UUID.randomUUID(),UUID.randomUUID(),
            new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT,UUID.randomUUID(),null,UUID.randomUUID()),UUID.randomUUID(),
            UUID.randomUUID(),UUID.randomUUID(),"TEST","Test-only",SurgeryPriority.ROUTINE,now.minusSeconds(60),
            SurgeryAuditActor.human(UUID.randomUUID(),null),"engine-test");
    private final SurgerySchedule schedule = new SurgerySchedule(UUID.randomUUID(),value.getSurgeryCaseId(),1,UUID.randomUUID(),
            now.plusSeconds(60),now.plusSeconds(600),List.of(new SurgeryTeamAssignment(UUID.randomUUID(),SurgeryTeamRole.PRIMARY_SURGEON)));

    @Test void evaluate_allVerifiedProofs_usesMinimumExplicitExpiryAndDeterministicDependencies() {
        var proof = evidence();
        var snapshot = engine.evaluate(value,schedule,proof,now);
        assertThat(snapshot.isReady()).isTrue();
        assertThat(snapshot.validUntil()).isEqualTo(now.plusSeconds(90));
        assertThat(snapshot.dependencyRevisions()).extracting(item -> item.dependencyType()).containsExactly(SurgeryDependencyType.values());
        assertThat(snapshot.blockingReasons()).isEmpty();
    }
    @ParameterizedTest @EnumSource(SurgeryDependencyType.class)
    void evaluate_eachUnsatisfiedGuard_blocksReadinessIncludingFinancialWithoutAnyOverride(SurgeryDependencyType type) {
        var evidence = evidence();
        var changed = evidence.proofs().stream().map(proof -> proof.type() != type ? proof : new SurgeryReadinessEvidence.Proof(
                type,proof.sourceId(),proof.revision(),SurgeryReadinessEvidence.Decision.UNSATISFIED,now,now.minusSeconds(1),now.plusSeconds(90))).toList();
        assertThat(engine.evaluate(value,schedule,withProofs(changed),now).isReady()).isFalse();
    }
    @ParameterizedTest @EnumSource(SurgeryDependencyType.class)
    void evaluate_eachMissingProof_neverSynthesizesPermissionOrDependency(SurgeryDependencyType type) {
        var snapshot = engine.evaluate(value,schedule,withProofs(evidence().proofs().stream().filter(proof -> proof.type() != type).toList()),now);
        assertThat(snapshot.isReady()).isFalse();
        assertThat(snapshot.blockingReasons()).contains("READINESS_DEPENDENCY_SNAPSHOT_INCOMPLETE");
        assertThat(snapshot.dependencyRevisions()).hasSize(6);
    }
    @ParameterizedTest @ValueSource(strings = {"stale","future","expired","not-effective","unknown"})
    void evaluate_untrustedOrInvalidTemporalProof_failsClosed(String kind) {
        var changed = evidence().proofs().stream().map(proof -> proof.type() != SurgeryDependencyType.FINANCIAL_CLEARANCE ? proof
                : new SurgeryReadinessEvidence.Proof(proof.type(),proof.sourceId(),0,
                kind.equals("unknown") ? SurgeryReadinessEvidence.Decision.UNVERIFIABLE : SurgeryReadinessEvidence.Decision.SATISFIED,
                kind.equals("stale") ? now.minusSeconds(31) : kind.equals("future") ? now.plusSeconds(6) : now,
                kind.equals("not-effective") ? now.plusSeconds(1) : now.minusSeconds(60),
                kind.equals("expired") ? now : now.plusSeconds(90))).toList();
        assertThat(engine.evaluate(value,schedule,withProofs(changed),now).blockingReasons()).contains("FINANCIAL_CLEARANCE_INVALID");
    }
    @Test void evaluate_nullExpiry_doesNotInventClinicalTtl() {
        var proofs = evidence().proofs().stream().map(proof -> new SurgeryReadinessEvidence.Proof(proof.type(),proof.sourceId(),proof.revision(),
                proof.decision(),proof.observedAt(),proof.validFrom(),null)).toList();
        assertThat(engine.evaluate(value,schedule,withProofs(proofs),now).validUntil()).isNull();
    }
    @Test void evaluate_expiryAtNanosecondBoundary_isExclusive() {
        var proofs = evidence().proofs().stream().map(proof -> new SurgeryReadinessEvidence.Proof(proof.type(),proof.sourceId(),proof.revision(),
                proof.decision(),proof.observedAt(),proof.validFrom(),now.plusNanos(1))).toList();
        assertThat(engine.evaluate(value,schedule,withProofs(proofs),now).isReady()).isTrue();
        assertThat(engine.evaluate(value,schedule,withProofs(proofs),now.plusNanos(1)).isReady()).isFalse();
    }
    @ParameterizedTest @ValueSource(strings = {"case","patient","department","episode","revision","schedule"})
    void evaluate_foreignOrStaleContext_conflictsRatherThanCrossAssociates(String field) {
        var original = evidence();
        var forged = new SurgeryReadinessEvidence(field.equals("case") ? UUID.randomUUID() : original.surgeryCaseId(),
                field.equals("patient") ? UUID.randomUUID() : original.patientId(),field.equals("department") ? UUID.randomUUID() : original.departmentId(),
                field.equals("episode") ? new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT,UUID.randomUUID(),null,UUID.randomUUID()) : original.episode(),
                field.equals("revision") ? 1 : original.caseRevision(),field.equals("schedule") ? UUID.randomUUID() : original.scheduleId(),1,original.proofs());
        assertThatThrownBy(() -> engine.evaluate(value,schedule,forged,now)).isInstanceOf(SurgeryRevisionConflictException.class);
    }
    @Test void evidence_duplicateGuard_rejectsInsteadOfLastWriteWins() {
        var proof = evidence().proofs().getFirst();
        assertThatThrownBy(() -> withProofs(List.of(proof,proof))).isInstanceOf(IllegalArgumentException.class);
    }
    private SurgeryReadinessEvidence evidence() {
        return withProofs(Arrays.stream(SurgeryDependencyType.values()).map(type -> new SurgeryReadinessEvidence.Proof(type,
                type == SurgeryDependencyType.SCHEDULE ? schedule.scheduleId() : UUID.randomUUID(),type == SurgeryDependencyType.SCHEDULE ? 1 : 0,
                SurgeryReadinessEvidence.Decision.SATISFIED,now,now.minusSeconds(60),now.plusSeconds(type == SurgeryDependencyType.FINANCIAL_CLEARANCE ? 90 : 120))).toList());
    }
    private SurgeryReadinessEvidence withProofs(List<SurgeryReadinessEvidence.Proof> proofs) {
        return new SurgeryReadinessEvidence(value.getSurgeryCaseId(),value.getPatientId(),value.getDepartmentId(),value.getCareEpisode(),
                value.getRevision(),schedule.scheduleId(),schedule.revision(),proofs);
    }
}
