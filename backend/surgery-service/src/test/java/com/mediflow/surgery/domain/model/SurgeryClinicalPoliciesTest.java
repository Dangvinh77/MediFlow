package com.mediflow.surgery.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SurgeryClinicalPoliciesTest {
    private final Instant now = Instant.parse("2026-10-06T08:00:00Z");
    private final SurgeryAuditActor actor = SurgeryAuditActor.human(UUID.randomUUID(),UUID.randomUUID());
    private final SurgeryCase value = SurgeryCase.create(UUID.randomUUID(),UUID.randomUUID(),
            new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT,UUID.randomUUID(),null,UUID.randomUUID()),UUID.randomUUID(),
            UUID.randomUUID(),actor.verifiedStaffId(),"TEST","Test-only",SurgeryPriority.ROUTINE,now.minusSeconds(60),actor,"policy-test");

    @Test void teamPolicy_explicitCardinalityAndAllowedRoles_hasNoDefaultsOrJobTitleInference() {
        var policy = new SurgeryTeamCompositionPolicy(UUID.randomUUID(),"TEST",1,Map.of(
                SurgeryTeamRole.PRIMARY_SURGEON,new SurgeryTeamCompositionPolicy.Cardinality(1,1),
                SurgeryTeamRole.OR_NURSE,new SurgeryTeamCompositionPolicy.Cardinality(1,2)));
        var surgeon = new SurgeryTeamAssignment(UUID.randomUUID(),SurgeryTeamRole.PRIMARY_SURGEON);
        var nurse = new SurgeryTeamAssignment(UUID.randomUUID(),SurgeryTeamRole.OR_NURSE);
        assertThat(policy.accepts("TEST",schedule(List.of(surgeon,nurse)))).isTrue();
        assertThat(policy.accepts("TEST",schedule(List.of(surgeon)))).isFalse();
        assertThat(policy.accepts("OTHER",schedule(List.of(surgeon,nurse)))).isFalse();
        assertThat(policy.accepts("TEST",schedule(List.of(surgeon,nurse,new SurgeryTeamAssignment(UUID.randomUUID(),SurgeryTeamRole.ASSISTANT_SURGEON))))).isFalse();
        assertThat(policy.accepts("TEST",schedule(List.of(surgeon,nurse,new SurgeryTeamAssignment(UUID.randomUUID(),SurgeryTeamRole.PRIMARY_SURGEON))))).isFalse();
    }
    @Test void teamPolicy_missingClinicalConfiguration_rejectsRatherThanAssumingSingleSurgeon() {
        assertThatThrownBy(() -> new SurgeryTeamCompositionPolicy(UUID.randomUUID(),"TEST",1,Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SurgeryTeamCompositionPolicy.Cardinality(2,1)).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"valid","foreign-case","foreign-patient","foreign-episode","corrected","expired","manual","unauthorized","wrong-id","stale","na"})
    void checklistPolicy_exactCaseCurrentEvidenceAndAllowedSource_isRequired(String kind) {
        UUID definition = UUID.randomUUID(), itemId = UUID.randomUUID(), source = UUID.randomUUID(), template = UUID.randomUUID();
        var item = new SurgeryChecklistItem(itemId,value.getSurgeryCaseId(),definition,"TEST-LAB",true,1,
                kind.equals("na") ? SurgeryChecklistStatus.NOT_APPLICABLE : SurgeryChecklistStatus.SATISFIED,source,1L,1);
        var checklist = new SurgeryChecklistSnapshot(UUID.randomUUID(),value.getSurgeryCaseId(),template,1,1,List.of(item));
        var policy = new SurgeryChecklistEvidencePolicy(UUID.randomUUID(),1,"TEST",template,1,Duration.ofSeconds(60),Map.of(
                "TEST-LAB",new SurgeryChecklistEvidencePolicy.Rule(true,Set.of(SurgeryChecklistEvidencePolicy.Source.LAB_RESULT),false)));
        var evidence = new SurgeryChecklistEvidencePolicy.Evidence(kind.equals("foreign-case") ? UUID.randomUUID() : value.getSurgeryCaseId(),
                kind.equals("foreign-patient") ? UUID.randomUUID() : value.getPatientId(),
                kind.equals("foreign-episode") ? new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT,UUID.randomUUID(),null,UUID.randomUUID()) : value.getCareEpisode(),
                kind.equals("manual") ? SurgeryChecklistEvidencePolicy.Source.MANUAL_ATTESTATION : SurgeryChecklistEvidencePolicy.Source.LAB_RESULT,
                kind.equals("wrong-id") ? UUID.randomUUID() : source,1,kind.equals("corrected") ? 2 : 1,
                now.minusSeconds(kind.equals("stale") ? 61 : 10),kind.equals("expired") ? now : now.plusSeconds(10),!kind.equals("unauthorized"));
        assertThat(policy.accepts(value,checklist,Map.of(itemId,evidence),now)).isEqualTo(kind.equals("valid"));
        assertThat(policy.accepts(value,checklist,Map.of(),now)).isFalse();
    }
    @ParameterizedTest @ValueSource(strings = {"valid","foreign-case","wrong-recorder","wrong-type","missing-document","missing-witness","expired","unapproved","foreign-signer"})
    void consentPolicy_exactSignerRecorderTypeAndClinicalAuthority_isRequired(String kind) {
        UUID signer = kind.equals("foreign-signer") ? UUID.randomUUID() : value.getPatientId();
        var consent = SurgeryConsentRecord.sign(UUID.randomUUID(),kind.equals("foreign-case") ? UUID.randomUUID() : value.getSurgeryCaseId(),
                kind.equals("wrong-type") ? SurgeryConsentType.ANESTHESIA : SurgeryConsentType.SURGERY,signer,SurgeryConsentSignerType.PATIENT,
                kind.equals("missing-document") ? null : UUID.randomUUID(),actor,now.minusSeconds(10),"policy-test");
        var policy = new SurgeryConsentAuthorityPolicy(UUID.randomUUID(),1,SurgeryConsentType.SURGERY,Set.of(SurgeryConsentSignerType.PATIENT),true,true);
        var authority = new SurgeryConsentAuthorityPolicy.Authority(value.getSurgeryCaseId(),SurgeryConsentType.SURGERY,signer,SurgeryConsentSignerType.PATIENT,
                kind.equals("wrong-recorder") ? UUID.randomUUID() : actor.accountId(),kind.equals("missing-witness") ? null : UUID.randomUUID(),
                !kind.equals("missing-witness"),false,!kind.equals("unapproved"),true,now.minusSeconds(20),kind.equals("expired") ? now : now.plusSeconds(10));
        assertThat(policy.permitsSigning(value,consent,authority,now)).isEqualTo(kind.equals("valid"));
    }
    @ParameterizedTest @ValueSource(strings = {"valid","wrong-reference","wrong-revision","unapproved","disallowed"})
    void checklistPolicy_optionalNotApplicable_requiresExactStoredAttestation(String kind) {
        UUID source = UUID.randomUUID(), template = UUID.randomUUID(), itemId = UUID.randomUUID();
        var required = new SurgeryChecklistItem(UUID.randomUUID(),value.getSurgeryCaseId(),UUID.randomUUID(),"REQUIRED",true,1,
                SurgeryChecklistStatus.SATISFIED,source,1L,1);
        var optional = new SurgeryChecklistItem(itemId,value.getSurgeryCaseId(),UUID.randomUUID(),"OPTIONAL",false,2,
                SurgeryChecklistStatus.NOT_APPLICABLE,source,1L,1);
        var checklist = new SurgeryChecklistSnapshot(UUID.randomUUID(),value.getSurgeryCaseId(),template,1,1,List.of(required,optional));
        var manual = SurgeryChecklistEvidencePolicy.Source.MANUAL_ATTESTATION;
        var policy = new SurgeryChecklistEvidencePolicy(UUID.randomUUID(),1,"TEST",template,1,Duration.ofSeconds(60),Map.of(
                "REQUIRED",new SurgeryChecklistEvidencePolicy.Rule(true,Set.of(manual),false),
                "OPTIONAL",new SurgeryChecklistEvidencePolicy.Rule(false,Set.of(manual),!kind.equals("disallowed"))));
        var requiredProof = new SurgeryChecklistEvidencePolicy.Evidence(value.getSurgeryCaseId(),value.getPatientId(),value.getCareEpisode(),
                manual,source,1,1,now.minusSeconds(10),null,true);
        long revision = kind.equals("wrong-revision") ? 2 : 1;
        var optionalProof = new SurgeryChecklistEvidencePolicy.Evidence(value.getSurgeryCaseId(),value.getPatientId(),value.getCareEpisode(),
                manual,kind.equals("wrong-reference") ? UUID.randomUUID() : source,revision,revision,now.minusSeconds(10),null,!kind.equals("unapproved"));
        assertThat(policy.accepts(value,checklist,Map.of(required.checklistItemId(),requiredProof,itemId,optionalProof),now)).isEqualTo(kind.equals("valid"));
    }
    @Test void consentPolicy_guardianRelationshipAndRevocation_areSeparatelyAuthorized() {
        UUID guardian = UUID.randomUUID();
        var consent = SurgeryConsentRecord.sign(UUID.randomUUID(),value.getSurgeryCaseId(),SurgeryConsentType.SURGERY,guardian,
                SurgeryConsentSignerType.GUARDIAN,UUID.randomUUID(),actor,now.minusSeconds(10),"policy-test");
        var policy = new SurgeryConsentAuthorityPolicy(UUID.randomUUID(),1,SurgeryConsentType.SURGERY,Set.of(SurgeryConsentSignerType.GUARDIAN),false,true);
        var authority = new SurgeryConsentAuthorityPolicy.Authority(value.getSurgeryCaseId(),SurgeryConsentType.SURGERY,guardian,SurgeryConsentSignerType.GUARDIAN,
                actor.accountId(),null,false,false,true,false,now.minusSeconds(20),null);
        assertThat(policy.permitsSigning(value,consent,authority,now)).isFalse();
        assertThat(policy.permitsRevocation(value,consent,actor,authority,now)).isFalse();
        assertThat(policy.permitsSigning(value,consent,null,now)).isFalse();
    }
    private SurgerySchedule schedule(List<SurgeryTeamAssignment> team) {
        return new SurgerySchedule(UUID.randomUUID(),value.getSurgeryCaseId(),1,UUID.randomUUID(),now,now.plusSeconds(600),team);
    }
}
