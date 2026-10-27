package com.mediflow.lab.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mediflow.lab.application.dto.request.AddResultRequest;
import com.mediflow.lab.application.dto.request.CancelLabTestRequest;
import com.mediflow.lab.application.dto.request.CreateLabRequest;
import com.mediflow.lab.application.dto.request.LabResultItem;
import com.mediflow.lab.application.event.LabRequestCreatedEvent;
import com.mediflow.lab.application.event.LabResultCreatedEvent;
import com.mediflow.lab.application.mapper.LabTestDtoMapper;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.port.out.LabEventPublisherPort;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.domain.exception.LabTestNotFoundException;
import com.mediflow.lab.domain.exception.LabRuleException;
import com.mediflow.lab.domain.model.LabTest;
import com.mediflow.lab.domain.model.LabTestStatus;

class LabApplicationServiceTest {

    @Test
    void cancellationPublishesDurableFactWithVerifiedActorAndStoredAudit() {
        UUID actor = UUID.randomUUID();
        var test = LabTest.createV2(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), com.mediflow.lab.domain.model.CareEpisodeType.OUTPATIENT_VISIT,
                UUID.randomUUID(), "CBC", "LAB-CBC", LocalDate.now());
        var events = new java.util.ArrayList<com.mediflow.lab.application.event.DomainEventEnvelope<?>>();
        var now = java.time.Instant.parse("2026-10-21T00:00:00Z");
        var app = new LabApplicationService(tests, publisher, mapper, correlationIds,
                events::add, null, null, () -> Optional.of(new com.mediflow.lab.application.port.out.AuthenticatedStaffContext(
                        actor, "DOCTOR")), true, true, java.time.Clock.fixed(now, java.time.ZoneOffset.UTC));
        when(tests.findByIdForUpdate(test.getTestId())).thenReturn(Optional.of(test));
        when(tests.save(test)).thenReturn(test);
        when(correlationIds.currentOrCreate()).thenReturn(UUID.randomUUID());
        app.cancel(test.getTestId(), new CancelLabTestRequest("Declined", actor));
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("lab.request.cancelled");
            assertThat(event.occurredAt()).isEqualTo(now);
            assertThat(event.payload()).hasFieldOrPropertyWithValue("cancelledBy", actor)
                    .hasFieldOrPropertyWithValue("reason", "Declined");
        });
        app.cancel(test.getTestId(), new CancelLabTestRequest("Declined", actor));
        assertThat(events).hasSize(1);
    }

    private final LabTestRepositoryPort tests = mock(LabTestRepositoryPort.class);
    private final LabEventPublisherPort publisher = mock(LabEventPublisherPort.class);
    private final LabTestDtoMapper mapper = mock(LabTestDtoMapper.class);
    private final CorrelationIdProvider correlationIds = mock(CorrelationIdProvider.class);
    private final LabApplicationService service =
            new LabApplicationService(tests, publisher, mapper, correlationIds);

    @Test
    void create_persistsAndPublishesRequestEvent() {
        UUID correlationId = UUID.randomUUID();
        when(correlationIds.currentOrCreate()).thenReturn(correlationId);
        var request = new CreateLabRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CBC", LocalDate.now());
        when(tests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request);

        ArgumentCaptor<LabTest> saved = ArgumentCaptor.forClass(LabTest.class);
        verify(tests).save(saved.capture());
        ArgumentCaptor<LabRequestCreatedEvent> event = ArgumentCaptor.forClass(LabRequestCreatedEvent.class);
        verify(publisher).publishRequestCreated(event.capture());
        assertThat(event.getValue().labId()).isEqualTo(saved.getValue().getTestId());
        assertThat(event.getValue().labType()).isEqualTo("CBC");
        assertThat(event.getValue().correlationId()).isEqualTo(correlationId.toString());
    }

    @Test
    void addResults_locksAggregateCompletesAndPublishesFullResult() {
        UUID correlationId = UUID.randomUUID();
        when(correlationIds.currentOrCreate()).thenReturn(correlationId);
        LabTest test = newTest();
        when(tests.findByIdForUpdate(test.getTestId())).thenReturn(Optional.of(test));
        when(tests.save(test)).thenReturn(test);
        var request = new AddResultRequest(
                List.of(new LabResultItem("WBC", "7.5", "10^9/L", "4.0-10.0")),
                "Normal", LocalDate.now());

        service.addResults(test.getTestId(), request);

        assertThat(test.getStatus()).isEqualTo(LabTestStatus.COMPLETED);
        verify(tests).save(test);
        ArgumentCaptor<LabResultCreatedEvent> event = ArgumentCaptor.forClass(LabResultCreatedEvent.class);
        verify(publisher).publishResultCreated(event.capture());
        assertThat(event.getValue().labType()).isEqualTo(test.getLabType());
        assertThat(event.getValue().performedDate()).isEqualTo(request.performedDate());
        assertThat(event.getValue().correlationId()).isEqualTo(correlationId.toString());
        assertThat(event.getValue().results()).singleElement()
                .satisfies(result -> assertThat(result.value()).isEqualTo("7.5"));
    }

    @Test
    void changeStatus_missingTest_throwsTypedNotFound() {
        UUID testId = UUID.randomUUID();
        when(tests.findByIdForUpdate(testId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(testId, LabTestStatus.IN_PROGRESS))
                .isInstanceOf(LabTestNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "LAB_TEST_NOT_FOUND");
        verify(tests, never()).save(any());
    }

    @Test
    void markPaid_usesExplicitTestIdAndPersists() {
        LabTest test = newTest();
        when(tests.findByIdForUpdate(test.getTestId())).thenReturn(Optional.of(test));
        when(tests.save(test)).thenReturn(test);

        service.markPaid(test.getTestId());

        assertThat(test.isPaid()).isTrue();
        verify(tests).save(test);
    }

    @Test
    void outpatientMvp_rejectsV0CreateBeforePersistingOrPublishing() {
        LabApplicationService outpatient = outpatientService(false, null);
        var request = new CreateLabRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CBC", LocalDate.now());

        assertThatThrownBy(() -> outpatient.create(request))
                .isInstanceOf(LabRuleException.class)
                .hasFieldOrPropertyWithValue("code", "LAB_V0_MUTATION_DISABLED");
        verify(tests, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void outpatientMvp_rejectsV0LifecycleMutationsBeforeChangingState() {
        LabApplicationService outpatient = outpatientService(false, null);
        LabTest test = newTest();
        when(tests.findByIdForUpdate(test.getTestId())).thenReturn(Optional.of(test));

        assertThatThrownBy(() -> outpatient.start(test.getTestId(), null))
                .isInstanceOf(LabRuleException.class)
                .hasFieldOrPropertyWithValue("code", "LAB_V0_MUTATION_DISABLED");
        assertThatThrownBy(() -> outpatient.changeStatus(test.getTestId(), LabTestStatus.IN_PROGRESS))
                .isInstanceOf(LabRuleException.class)
                .hasFieldOrPropertyWithValue("code", "LAB_V0_MUTATION_DISABLED");
        assertThatThrownBy(() -> outpatient.cancel(test.getTestId(),
                new CancelLabTestRequest("reason", UUID.randomUUID())))
                .isInstanceOf(LabRuleException.class)
                .hasFieldOrPropertyWithValue("code", "LAB_V0_MUTATION_DISABLED");

        assertThat(test.getStatus()).isEqualTo(LabTestStatus.PENDING);
        verify(tests, never()).save(any());
    }

    private LabApplicationService outpatientService(boolean careFinanceEnabled,
                                                     com.mediflow.lab.application.port.out.AuthenticatedStaffContextPort actor) {
        return new LabApplicationService(tests, publisher, mapper, correlationIds,
                event -> { }, null, null, actor == null ? () -> Optional.empty() : actor,
                careFinanceEnabled, true, java.time.Clock.systemUTC());
    }

    private static LabTest newTest() {
        return LabTest.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "CBC", LocalDate.now());
    }
}
