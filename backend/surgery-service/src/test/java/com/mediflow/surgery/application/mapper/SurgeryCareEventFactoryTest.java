package com.mediflow.surgery.application.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.surgery.application.event.SurgeryCareEvent;
import com.mediflow.surgery.domain.model.*;
import com.mediflow.surgery.infrastructure.messaging.SurgeryCareEventCodec;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class SurgeryCareEventFactoryTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final SurgeryCareEventCodec codec = new SurgeryCareEventCodec(json);

    @ParameterizedTest @ValueSource(strings={"admission","outpatient"})
    void serialize_allOutcomes_matchesProducerFixturesExactly(String mode) throws Exception {
        assertFixture("case.created", mode, SurgeryCareEventFactory.created(newCase(mode),
                List.of(new SurgeryPlannedItem("ITEM", "PRICE", BigDecimal.ONE))));
        var ready = ready(mode);
        assertFixture("ready",mode,SurgeryCareEventFactory.ready(ready.value,ready.schedule,ready.snapshot));
        ready.value.invalidateReadiness(actor(mode), correlation(mode), at("01:03:00"), "CHECKLIST_CHANGED");
        ready.value.recordBusinessMutation(actor(mode), correlation(mode), at("01:03:00"), "CHECKLIST_ITEM_CHANGED");
        assertFixture("readiness.invalidated", mode, SurgeryCareEventFactory.invalidated(ready.value,
                ready.snapshot, ready.schedule, at("01:03:00"), "CHECKLIST_CHANGED", correlation(mode)));
        var completed = ready(mode);
        var actor=actor(mode);
        completed.value.finalizeSchedule(actor,correlation(mode),at("01:03:00"));
        completed.value.start(completed.snapshot,actor,correlation(mode),at("01:04:00"));
        var result=new SurgeryResult(id(mode,14),id(mode,1),"PROC","METHOD","COMPLETE",null,
                at("01:04:00"),at("01:10:30"),List.of(new SurgeryPerformedItem(id(mode,15),"ITEM","PRICE",BigDecimal.ONE)),
                at("01:11:00"),actor,correlation(mode));
        completed.value.complete(actor,correlation(mode),result.recordedAt());
        var event=SurgeryCareEventFactory.completed(completed.value,result);
        assertFixture("completed",mode,event);
        assertThat(codec.encode(SurgeryCareEventFactory.completed(completed.value,result))).isEqualTo(codec.encode(event));
        var cancelled=ready(mode);
        cancelled.value.finalizeSchedule(actor,correlation(mode),at("01:03:00"));
        cancelled.value.cancel(actor,correlation(mode),at("01:05:00"),"Patient request");
        assertFixture("cancelled",mode,SurgeryCareEventFactory.cancelled(cancelled.value));
    }

    @Test void ready_foreignScheduleOrSnapshot_cannotEmitAuthority() {
        var fixture=ready("admission");
        var foreign=new SurgerySchedule(UUID.randomUUID(),UUID.randomUUID(),1,UUID.randomUUID(),
                at("01:04:00"),at("02:00:00"),fixture.schedule.teamAssignments());
        assertThatThrownBy(()->SurgeryCareEventFactory.ready(fixture.value,foreign,fixture.snapshot))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void invalidation_withoutCommittedTransition_cannotSuppressReminder() {
        var fixture = ready("admission");
        assertThatThrownBy(() -> SurgeryCareEventFactory.invalidated(fixture.value, fixture.snapshot,
                fixture.schedule, at("01:03:00"), "CHECKLIST_CHANGED", correlation("admission")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void created_emptyOrDuplicatedPlan_cannotInventCharges() {
        var value = newCase("admission");
        assertThatThrownBy(() -> SurgeryCareEventFactory.created(value, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        var item = new SurgeryPlannedItem("ITEM", "PRICE", BigDecimal.ONE);
        assertThatThrownBy(() -> SurgeryCareEventFactory.created(value, List.of(item, item)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"admission", "outpatient"})
    void completed_differentPerformedPlan_matchesBillingReconciliationFixture(String mode) throws Exception {
        var event = completed(mode, List.of(
                new SurgeryPerformedItem(id(mode, 15), "ITEM", "PRICE", new BigDecimal("2")),
                new SurgeryPerformedItem(id(mode, 16), "EXTRA_ITEM", "EXTRA_PRICE", new BigDecimal("0.5"))));
        assertFixture("completed", mode + ".planned-difference", event);
        var payload = (SurgeryCareEvent.Completed) event.payload();
        assertThat(payload.performedItems()).extracting(SurgeryCareEvent.PerformedItem::quantity)
                .containsExactly(new BigDecimal("2"), new BigDecimal("0.5"));
        assertThat(json.readTree(codec.encode(event)).toString()).doesNotContain("amount", "unitPrice", "totalAmount");
    }

    @ParameterizedTest
    @ValueSource(strings = {"admission", "outpatient"})
    void completed_unknownBillingPriceCode_isPreservedWithoutZeroPricing(String mode) throws Exception {
        var event = completed(mode, List.of(
                new SurgeryPerformedItem(id(mode, 15), "ITEM", "UNRECOGNIZED_PRICE", BigDecimal.ONE)));
        assertFixture("completed", mode + ".unknown-price", event);
        assertThat(((SurgeryCareEvent.Completed) event.payload()).performedItems().getFirst().priceCode())
                .isEqualTo("UNRECOGNIZED_PRICE");
    }

    @Test
    void created_contractMetadata_rejectsReferralAliasSuffixVersionAndForeignProducer() {
        var event = SurgeryCareEventFactory.created(newCase("admission"),
                List.of(new SurgeryPlannedItem("ITEM", "PRICE", BigDecimal.ONE)));
        assertThat(event.eventType()).isEqualTo("surgery.case.created");
        assertThat(event.version()).isEqualTo(1);
        assertThat(event.producer()).isEqualTo("surgery-service");
        for (var type : List.of("surgery.requested", "surgery.case.created.v1", "surgery.completed")) {
            assertThatThrownBy(() -> new SurgeryCareEvent(event.eventId(), type, 1, event.occurredAt(),
                    event.correlationId(), event.producer(), event.payload())).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new SurgeryCareEvent(event.eventId(), event.eventType(), 2, event.occurredAt(),
                event.correlationId(), event.producer(), event.payload())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SurgeryCareEvent(event.eventId(), event.eventType(), 1, event.occurredAt(),
                event.correlationId(), "billing-service", event.payload())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void completion_verifiedActualProcedureMayDifferFromPlannedProcedure() {
        var fixture = ready("admission"); var actor = actor("admission");
        fixture.value.finalizeSchedule(actor, correlation("admission"), at("01:03:00"));
        fixture.value.start(fixture.snapshot, actor, correlation("admission"), at("01:04:00"));
        var result = new SurgeryResult(id("admission",14), id("admission",1), "ACTUAL", "METHOD", "COMPLETE", null,
                at("01:04:00"), at("01:10:30"), List.of(), at("01:11:00"), actor, correlation("admission"));
        fixture.value.complete(actor, correlation("admission"), result.recordedAt());
        var payload = (com.mediflow.surgery.application.event.SurgeryCareEvent.Completed)
                SurgeryCareEventFactory.completed(fixture.value, result).payload();
        assertThat(payload.procedureCode()).isEqualTo("ACTUAL");
    }

    @ParameterizedTest @ValueSource(strings={"REQUESTED","PREOP_IN_PROGRESS","READY","SCHEDULED"})
    void cancellation_stageComesFromPersistedPreviousState(String state) {
        String mode="admission";
        var value=newCase(mode);
        if(!state.equals("REQUESTED")) value.beginPreop(actor(mode),correlation(mode),at("01:01:00"));
        if(state.equals("READY")||state.equals("SCHEDULED")) value=ready(mode).value;
        if(state.equals("SCHEDULED")) value.finalizeSchedule(actor(mode),correlation(mode),at("01:03:00"));
        value.cancel(actor(mode),correlation(mode),at("01:05:00"),"Patient request");
        var payload=(com.mediflow.surgery.application.event.SurgeryCareEvent.Cancelled) SurgeryCareEventFactory.cancelled(value).payload();
        assertThat(payload.cancellationStage()).isEqualTo(state.equals("REQUESTED")?"BEFORE_PREOP":state.equals("SCHEDULED")?"BEFORE_START":"AFTER_PREOP");
        assertThat(payload.cancelledBy()).isEqualTo(id(mode,6));
    }

    private void assertFixture(String type,String mode,com.mediflow.surgery.application.event.SurgeryCareEvent event) throws Exception {
        var expected=Files.readAllBytes(Path.of("src/test/resources/contracts/surgery-outcomes-v1/surgery."+type+"."+mode+".v1.json"));
        assertThat(json.readTree(codec.encode(event))).isEqualTo(json.readTree(expected));
    }
    private static SurgeryCareEvent completed(String mode, List<SurgeryPerformedItem> items) {
        var fixture = ready(mode);
        var actor = actor(mode);
        fixture.value.finalizeSchedule(actor, correlation(mode), at("01:03:00"));
        fixture.value.start(fixture.snapshot, actor, correlation(mode), at("01:04:00"));
        var result = new SurgeryResult(id(mode, 14), id(mode, 1), "PROC", "METHOD", "COMPLETE", null,
                at("01:04:00"), at("01:10:30"), items, at("01:11:00"), actor, correlation(mode));
        fixture.value.complete(actor, correlation(mode), result.recordedAt());
        return SurgeryCareEventFactory.completed(fixture.value, result);
    }
    private static Fixture ready(String mode) {
        var value=newCase(mode);
        value.beginPreop(actor(mode),correlation(mode),at("01:01:00"));
        var schedule=new SurgerySchedule(id(mode,11),id(mode,1),1,id(mode,12),at("01:04:00"),at("02:00:00"),
                List.of(new SurgeryTeamAssignment(id(mode,7),SurgeryTeamRole.PRIMARY_SURGEON)));
        var dependencies=Arrays.stream(SurgeryDependencyType.values()).map(type->new SurgeryDependencyRevision(type,
                type==SurgeryDependencyType.SCHEDULE?id(mode,11):id(mode,20+type.ordinal()),type==SurgeryDependencyType.SCHEDULE?1:0)).toList();
        var snapshot=ReadinessSnapshot.evaluate(id(mode,13),id(mode,1),true,true,true,true,true,true,true,
                at("01:02:00"),dependencies,null);
        value.markReady(snapshot,actor(mode),correlation(mode));
        return new Fixture(value,schedule,snapshot);
    }
    private static SurgeryCase newCase(String mode) {
        var episode=new CareEpisode(mode.equals("admission")?CareEpisodeType.ADMISSION:CareEpisodeType.OUTPATIENT_VISIT,
                id(mode,mode.equals("admission")?8:10),mode.equals("admission")?id(mode,8):null,id(mode,9));
        return SurgeryCase.create(id(mode,1),id(mode,2),episode,id(mode,3),id(mode,4),id(mode,5),"PROC","Indication stays private",
                SurgeryPriority.ROUTINE,at("01:00:00"),actor(mode),correlation(mode));
    }
    private static UUID id(String mode,int n) { return UUID.fromString("00000000-0000-4000-8000-"+String.format("%012d",n+(mode.equals("admission")?0:100))); }
    private static SurgeryAuditActor actor(String mode) { return SurgeryAuditActor.human(id(mode,6),id(mode,7)); }
    private static Instant at(String time) { return Instant.parse("2026-10-07T"+time+"Z"); }
    private static String correlation(String mode) { return "surgery-contract-"+mode; }
    private record Fixture(SurgeryCase value,SurgerySchedule schedule,ReadinessSnapshot snapshot) { }
}
