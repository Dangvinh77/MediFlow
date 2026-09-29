package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SurgeryLocalModelsTest {

    private static final Instant NOW = Instant.parse("2026-09-28T03:00:00Z");
    private static final SurgeryAuditActor HUMAN = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());

    @Test
    void checklistTemplate_snapshotIsPinnedAndMandatoryNotApplicableDoesNotPass() {
        UUID caseId = UUID.randomUUID();
        SurgeryChecklistItemDefinition required = new SurgeryChecklistItemDefinition(
                UUID.randomUUID(), "PREOP_IDENTITY", true, 1);
        SurgeryChecklistItemDefinition optional = new SurgeryChecklistItemDefinition(
                UUID.randomUUID(), "OPTIONAL_NOTE", false, 2);
        SurgeryChecklistTemplate template = new SurgeryChecklistTemplate(
                UUID.randomUUID(), "PROC-001", 3, List.of(required, optional));

        SurgeryChecklistSnapshot pending = template.snapshotForCase(UUID.randomUUID(), caseId);
        SurgeryChecklistItem notApplicable = new SurgeryChecklistItem(
                UUID.randomUUID(), caseId, required.definitionId(), required.itemCode(), true,
                required.displayOrder(), SurgeryChecklistStatus.NOT_APPLICABLE, null, null);
        SurgeryChecklistItem optionalPending = pending.items().get(1);
        SurgeryChecklistSnapshot notApplicableSnapshot = new SurgeryChecklistSnapshot(
                pending.checklistSnapshotId(), caseId, template.templateId(), template.revision(), 0,
                List.of(notApplicable, optionalPending));

        assertThat(pending.items()).allMatch(item -> item.status() == SurgeryChecklistStatus.PENDING);
        assertThat(notApplicableSnapshot.mandatoryChecklistComplete()).isFalse();
        assertThatThrownBy(() -> new SurgeryChecklistTemplate(
                UUID.randomUUID(), "PROC-001", 4, List.of()))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_CHECKLIST_TEMPLATE_INVALID");
        assertThatThrownBy(() -> new SurgeryChecklistSnapshot(
                UUID.randomUUID(), caseId, template.templateId(), 3, 0, List.of()))
                .isInstanceOf(SurgeryRuleException.class);
    }

    @Test
    void checklistSnapshot_requiresEveryMandatoryItemSatisfiedAndCopiesInput() {
        UUID caseId = UUID.randomUUID();
        SurgeryChecklistTemplate template = new SurgeryChecklistTemplate(UUID.randomUUID(), "PROC-002", 1,
                List.of(new SurgeryChecklistItemDefinition(UUID.randomUUID(), "REQUIRED", true, 1)));
        SurgeryChecklistSnapshot initial = template.snapshotForCase(UUID.randomUUID(), caseId);
        SurgeryChecklistItem source = initial.items().getFirst();
        SurgeryChecklistItem satisfied = new SurgeryChecklistItem(source.checklistItemId(), caseId,
                source.templateDefinitionId(), source.itemCode(), true, 1, SurgeryChecklistStatus.SATISFIED,
                UUID.randomUUID(), 2L);
        SurgeryChecklistSnapshot complete = new SurgeryChecklistSnapshot(initial.checklistSnapshotId(), caseId,
                template.templateId(), 1, 1, List.of(satisfied));

        assertThat(initial.mandatoryChecklistComplete()).isFalse();
        assertThat(complete.mandatoryChecklistComplete()).isTrue();
        assertThatThrownBy(() -> new SurgeryChecklistSnapshot(UUID.randomUUID(), caseId,
                template.templateId(), 1, 1, List.of(new SurgeryChecklistItem(
                UUID.randomUUID(), UUID.randomUUID(), source.templateDefinitionId(), "REQUIRED", true, 1,
                SurgeryChecklistStatus.SATISFIED, null, null))))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_CHECKLIST_SNAPSHOT_MISMATCH");
    }

    @Test
    void consent_revocationAppendsAuditAndLeavesOriginalImmutable() {
        SurgeryConsentRecord active = SurgeryConsentRecord.sign(UUID.randomUUID(), UUID.randomUUID(),
                SurgeryConsentType.ANESTHESIA, UUID.randomUUID(), SurgeryConsentSignerType.PATIENT,
                UUID.randomUUID(), HUMAN, NOW, "consent-sign");

        SurgeryConsentRecord revoked = active.revoke(
                SurgeryAuditActor.system("surgery-service"), NOW.plusSeconds(5),
                "consent-revoke", "CONSENT_WITHDRAWN");

        assertThat(active.isActive()).isTrue();
        assertThat(active.auditHistory()).hasSize(1);
        assertThat(revoked.isActive()).isFalse();
        assertThat(revoked.auditHistory()).extracting(SurgeryConsentAuditEntry::action)
                .containsExactly(SurgeryConsentAction.SIGNED, SurgeryConsentAction.REVOKED);
        assertThat(revoked.auditHistory().getLast().recordedBy().actorType())
                .isEqualTo(SurgeryActorType.SYSTEM);
        assertThatThrownBy(() -> revoked.revoke(HUMAN, NOW.plusSeconds(6), "again", "RETRY"))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_CONSENT_NOT_ACTIVE");
    }

    @Test
    void auditActor_distinguishesAccountVerifiedStaffAndProducer() {
        UUID accountId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        SurgeryAuditActor human = SurgeryAuditActor.human(accountId, staffId);
        SurgeryAuditActor system = SurgeryAuditActor.system("clinical-service");

        assertThat(human.accountId()).isEqualTo(accountId);
        assertThat(human.verifiedStaffId()).isEqualTo(staffId);
        assertThat(system.systemProducer()).isEqualTo("clinical-service");
        assertThatThrownBy(() -> new SurgeryAuditActor(
                SurgeryActorType.SYSTEM, accountId, null, "clinical-service"))
                .isInstanceOf(SurgeryRuleException.class);
    }

    @Test
    void schedule_usesHalfOpenIntervalsAndRejectsDuplicateStaff() {
        UUID staffId = UUID.randomUUID();
        SurgeryTeamAssignment surgeon = new SurgeryTeamAssignment(staffId, SurgeryTeamRole.PRIMARY_SURGEON);
        SurgerySchedule first = new SurgerySchedule(UUID.randomUUID(), UUID.randomUUID(), 1,
                UUID.randomUUID(), NOW, NOW.plusSeconds(60), List.of(surgeon));
        SurgerySchedule adjacent = new SurgerySchedule(UUID.randomUUID(), UUID.randomUUID(), 1,
                first.roomId(), NOW.plusSeconds(60), NOW.plusSeconds(120), List.of());
        SurgerySchedule overlapping = new SurgerySchedule(UUID.randomUUID(), UUID.randomUUID(), 1,
                UUID.randomUUID(), NOW.plusSeconds(30), NOW.plusSeconds(90),
                List.of(new SurgeryTeamAssignment(staffId, SurgeryTeamRole.ASSISTANT_SURGEON)));

        assertThat(first.overlaps(adjacent)).isFalse();
        assertThat(first.conflictsWith(adjacent)).isFalse();
        assertThat(first.overlaps(overlapping)).isTrue();
        assertThat(first.conflictsWith(overlapping)).isTrue();
        assertThat(first.sharesStaffWith(overlapping)).isTrue();
        assertThat(first.revise(first.roomId(), NOW, NOW.plusSeconds(90), List.of()).revision())
                .isEqualTo(2);
        assertThatThrownBy(() -> new SurgerySchedule(UUID.randomUUID(), UUID.randomUUID(), 1,
                UUID.randomUUID(), NOW, NOW, List.of()))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_SCHEDULE_INVALID");
        assertThatThrownBy(() -> new SurgerySchedule(UUID.randomUUID(), UUID.randomUUID(), 1,
                UUID.randomUUID(), NOW, NOW.plusSeconds(60), List.of(surgeon, surgeon)))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_SCHEDULE_DUPLICATE_STAFF");
    }

    @Test
    void performedItemAndResult_rejectInvalidTimeAndNonPositiveQuantity() {
        SurgeryPerformedItem line = new SurgeryPerformedItem(
                UUID.randomUUID(), "ITEM-001", "PRICE-001", new BigDecimal("1.500"));
        SurgeryResult result = result(line, NOW, NOW.plusSeconds(20));

        assertThat(line.quantity()).isEqualByComparingTo("1.5");
        assertThat(result.performedItems()).containsExactly(line);
        assertThatThrownBy(() -> new SurgeryPerformedItem(
                UUID.randomUUID(), "ITEM-001", "PRICE-001", BigDecimal.ZERO))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_PERFORMED_ITEM_INVALID");
        assertThatThrownBy(() -> result(line, NOW.plusSeconds(20), NOW))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_RESULT_INVALID");
        assertThatThrownBy(() -> new SurgeryResult(UUID.randomUUID(), result.surgeryCaseId(),
                "PROC-001", "METHOD-1", "OUTCOME-1", null, NOW, NOW.plusSeconds(20),
                List.of(line, line), NOW.plusSeconds(21), HUMAN, "result-correlation"))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_RESULT_DUPLICATE_ITEM");
    }

    @Test
    void readinessSnapshot_tracksExactDependencyRevisionReasonsAndExpiry() {
        List<SurgeryDependencyRevision> dependencies = dependencies();
        ReadinessSnapshot snapshot = ReadinessSnapshot.evaluate(UUID.randomUUID(), UUID.randomUUID(),
                true, true, true, false, true, true, true, NOW, dependencies, NOW.plusSeconds(10));

        assertThat(snapshot.isReady()).isFalse();
        assertThat(snapshot.blockingReasons()).containsExactly("ANESTHESIA_CONSENT_MISSING");
        assertThat(snapshot.isValidAt(NOW.plusSeconds(9))).isFalse();
        ReadinessSnapshot ready = ReadinessSnapshot.evaluate(UUID.randomUUID(), UUID.randomUUID(),
                true, true, true, true, true, true, true, NOW, dependencies, NOW.plusSeconds(10));
        assertThat(ready.isReady()).isTrue();
        assertThat(ready.isValidAt(NOW.plusSeconds(9))).isTrue();
        assertThat(ready.isValidAt(NOW.plusSeconds(10))).isFalse();

        ReadinessSnapshot missingDependency = ReadinessSnapshot.evaluate(UUID.randomUUID(), UUID.randomUUID(),
                true, true, true, true, true, true, true, NOW,
                dependencies.subList(0, dependencies.size() - 1), null);
        assertThat(missingDependency.isReady()).isFalse();
        assertThat(missingDependency.blockingReasons())
                .containsExactly("READINESS_DEPENDENCY_SNAPSHOT_INCOMPLETE");
        assertThatThrownBy(() -> new ReadinessSnapshot(UUID.randomUUID(), UUID.randomUUID(),
                true, true, true, true, true, true, true, NOW, dependencies, null,
                List.of("ANESTHESIA_CONSENT_MISSING")))
                .isInstanceOf(SurgeryRuleException.class)
                .satisfies(error -> assertThat(((SurgeryRuleException) error).getCode())
                        .isEqualTo("SURGERY_READINESS_REASON_MISMATCH"));
    }

    @Test
    void readinessSnapshot_requiresBothTypedConsentsAndFinancialClearance() {
        List<SurgeryDependencyRevision> dependencies = dependencies();

        ReadinessSnapshot missingSurgeryConsent = ReadinessSnapshot.evaluate(UUID.randomUUID(),
                UUID.randomUUID(), true, true, false, true, true, true, true, NOW,
                dependencies, null);
        ReadinessSnapshot missingAnesthesiaConsent = ReadinessSnapshot.evaluate(UUID.randomUUID(),
                UUID.randomUUID(), true, true, true, false, true, true, true, NOW,
                dependencies, null);
        ReadinessSnapshot missingFinancialClearance = ReadinessSnapshot.evaluate(UUID.randomUUID(),
                UUID.randomUUID(), true, true, true, true, true, true, false, NOW,
                dependencies, null);

        assertThat(missingSurgeryConsent.isReady()).isFalse();
        assertThat(missingSurgeryConsent.blockingReasons()).containsExactly("SURGERY_CONSENT_MISSING");
        assertThat(missingAnesthesiaConsent.isReady()).isFalse();
        assertThat(missingAnesthesiaConsent.blockingReasons()).containsExactly("ANESTHESIA_CONSENT_MISSING");
        assertThat(missingFinancialClearance.isReady()).isFalse();
        assertThat(missingFinancialClearance.blockingReasons())
                .containsExactly("FINANCIAL_CLEARANCE_INVALID");
    }

    @Test
    void readinessSnapshot_eachCoreGuardBlocksIndependently() {
        List<SurgeryDependencyRevision> dependencies = dependencies();
        List<ReadinessSnapshot> blocked = List.of(
                ReadinessSnapshot.evaluate(UUID.randomUUID(), UUID.randomUUID(),
                        false, true, true, true, true, true, true, NOW, dependencies, null),
                ReadinessSnapshot.evaluate(UUID.randomUUID(), UUID.randomUUID(),
                        true, false, true, true, true, true, true, NOW, dependencies, null),
                ReadinessSnapshot.evaluate(UUID.randomUUID(), UUID.randomUUID(),
                        true, true, true, true, false, true, true, NOW, dependencies, null),
                ReadinessSnapshot.evaluate(UUID.randomUUID(), UUID.randomUUID(),
                        true, true, true, true, true, false, true, NOW, dependencies, null));
        List<String> expectedReasons = List.of(
                "INDICATION_INVALID", "CHECKLIST_INCOMPLETE", "TEAM_INELIGIBLE", "SCHEDULE_INVALID");

        for (int index = 0; index < blocked.size(); index++) {
            assertThat(blocked.get(index).isReady()).as(expectedReasons.get(index)).isFalse();
            assertThat(blocked.get(index).blockingReasons()).containsExactly(expectedReasons.get(index));
        }
    }

    private static SurgeryResult result(SurgeryPerformedItem line, Instant start, Instant end) {
        return new SurgeryResult(UUID.randomUUID(), UUID.randomUUID(), "PROC-001", "METHOD-1",
                "OUTCOME-1", null, start, end, List.of(line), end, HUMAN, "result-correlation");
    }

    static List<SurgeryDependencyRevision> dependencies() {
        return java.util.Arrays.stream(SurgeryDependencyType.values())
                .map(type -> new SurgeryDependencyRevision(type, UUID.randomUUID(), 4))
                .toList();
    }
}
