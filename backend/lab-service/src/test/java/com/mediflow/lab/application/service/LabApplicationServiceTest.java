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
import com.mediflow.lab.application.dto.request.CreateLabRequest;
import com.mediflow.lab.application.dto.request.LabResultItem;
import com.mediflow.lab.application.event.LabRequestCreatedEvent;
import com.mediflow.lab.application.event.LabResultCreatedEvent;
import com.mediflow.lab.application.mapper.LabTestDtoMapper;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.port.out.LabEventPublisherPort;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.domain.exception.LabTestNotFoundException;
import com.mediflow.lab.domain.model.LabResult;
import com.mediflow.lab.domain.model.LabTest;
import com.mediflow.lab.domain.model.LabTestStatus;

class LabApplicationServiceTest {

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
        UUID persistedTestId = UUID.randomUUID();
        LabTest persisted = LabTest.restore(persistedTestId, request.recordId(), request.patientId(),
                request.requestingDepartmentId(), request.labType(), request.requestedDate(), null,
                LabTestStatus.PENDING, null, false, List.of(), null, null);
        when(tests.save(any())).thenReturn(persisted);

        service.create(request);

        ArgumentCaptor<LabTest> saved = ArgumentCaptor.forClass(LabTest.class);
        verify(tests).save(saved.capture());
        ArgumentCaptor<LabRequestCreatedEvent> event = ArgumentCaptor.forClass(LabRequestCreatedEvent.class);
        verify(publisher).publishRequestCreated(event.capture());
        assertThat(saved.getValue().getTestId()).isNull();
        assertThat(event.getValue().labId()).isEqualTo(persistedTestId);
        assertThat(event.getValue().labType()).isEqualTo("CBC");
        assertThat(event.getValue().correlationId()).isEqualTo(correlationId.toString());
        verify(mapper).toDto(persisted);
    }

    @Test
    void addResults_locksAggregateCompletesAndPublishesFullResult() {
        UUID correlationId = UUID.randomUUID();
        when(correlationIds.currentOrCreate()).thenReturn(correlationId);
        LabTest test = newTest();
        when(tests.findByIdForUpdate(test.getTestId())).thenReturn(Optional.of(test));
        UUID persistedResultId = UUID.randomUUID();
        LabTest persisted = LabTest.restore(test.getTestId(), test.getRecordId(), test.getPatientId(),
                test.getRequestingDepartmentId(), test.getLabType(), test.getRequestedDate(),
                LocalDate.now(), LabTestStatus.COMPLETED, "Normal", false,
                List.of(LabResult.restore(persistedResultId, "WBC", "7.5", "10^9/L", "4.0-10.0")),
                test.getCreatedAt(), test.getUpdatedAt());
        when(tests.save(test)).thenReturn(persisted);
        var request = new AddResultRequest(
                List.of(new LabResultItem("WBC", "7.5", "10^9/L", "4.0-10.0")),
                "Normal", LocalDate.now());

        var dto = mock(com.mediflow.lab.application.dto.response.LabTestDTO.class);
        when(mapper.toDto(persisted)).thenReturn(dto);

        assertThat(service.addResults(test.getTestId(), request)).isSameAs(dto);

        assertThat(test.getStatus()).isEqualTo(LabTestStatus.COMPLETED);
        verify(tests).save(test);
        ArgumentCaptor<LabResultCreatedEvent> event = ArgumentCaptor.forClass(LabResultCreatedEvent.class);
        verify(publisher).publishResultCreated(event.capture());
        assertThat(event.getValue().labType()).isEqualTo(test.getLabType());
        assertThat(event.getValue().performedDate()).isEqualTo(request.performedDate());
        assertThat(event.getValue().correlationId()).isEqualTo(correlationId.toString());
        assertThat(event.getValue().results()).singleElement()
                .satisfies(result -> {
                    assertThat(result.resultId()).isEqualTo(persistedResultId);
                    assertThat(result.value()).isEqualTo("7.5");
                });
        verify(mapper).toDto(persisted);
    }

    @Test
    void changeStatus_missingTest_throwsTypedNotFound() {
        UUID testId = UUID.randomUUID();
        when(tests.findByIdForUpdate(testId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(testId, LabTestStatus.IN_PROGRESS))
                .isInstanceOf(LabTestNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "LAB_NOT_FOUND");
        verify(tests, never()).save(any());
    }

    @Test
    void autoCreateFromRecord_blankLabType_doesNothing() {
        service.autoCreateFromRecord(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "  ");

        verifyNoInteractions(tests, publisher, mapper, correlationIds);
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

    private static LabTest newTest() {
        return LabTest.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CBC", LocalDate.now(), null, LabTestStatus.PENDING, null, false, List.of(), null, null);
    }
}
