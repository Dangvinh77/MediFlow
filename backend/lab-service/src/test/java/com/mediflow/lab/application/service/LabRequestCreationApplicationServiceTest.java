package com.mediflow.lab.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.lab.application.dto.request.CreateLabRequest;
import com.mediflow.lab.application.event.DomainEventEnvelope;
import com.mediflow.lab.application.event.LabRequestV2Payload;
import com.mediflow.lab.application.mapper.LabTestDtoMapper;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.port.out.LabEventPublisherPort;
import com.mediflow.lab.application.port.out.LabOutboxPort;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.domain.model.CareEpisodeType;
import com.mediflow.lab.domain.model.LabTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LabRequestCreationApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");
    private final LabTestRepositoryPort tests = mock(LabTestRepositoryPort.class);
    private final LabEventPublisherPort publisher = mock(LabEventPublisherPort.class);
    private final LabTestDtoMapper mapper = mock(LabTestDtoMapper.class);
    private final CorrelationIdProvider correlations = mock(CorrelationIdProvider.class);
    private final LabOutboxPort outbox = mock(LabOutboxPort.class);
    private final LabRequestCreationApplicationService service = new LabRequestCreationApplicationService(
            tests, publisher, mapper, correlations, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createCareFinance_persistsMatchedRequestAndOutboxEventInWriterBoundary() {
        UUID sourceOrderId = UUID.randomUUID();
        UUID episodeId = UUID.randomUUID();
        String correlation = UUID.randomUUID().toString();
        CreateLabRequest request = new CreateLabRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CBC", LocalDate.of(2026, 9, 20), sourceOrderId,
                CareEpisodeType.OUTPATIENT_VISIT, episodeId, "LAB-CBC");
        when(tests.existsBySourceOrderId(sourceOrderId)).thenReturn(false);
        when(tests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createCareFinance(request, correlation);

        ArgumentCaptor<LabTest> saved = ArgumentCaptor.forClass(LabTest.class);
        verify(tests).save(saved.capture());
        assertThat(saved.getValue().getSourceOrderId()).isEqualTo(sourceOrderId);
        assertThat(saved.getValue().getCareEpisodeType()).isEqualTo(CareEpisodeType.OUTPATIENT_VISIT);
        assertThat(saved.getValue().getPriceCode()).isEqualTo("LAB-CBC");
        ArgumentCaptor<DomainEventEnvelope<?>> envelope = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(outbox).append(envelope.capture());
        assertThat(envelope.getValue().correlationId()).isEqualTo(correlation);
        assertThat(envelope.getValue().occurredAt()).isEqualTo(NOW);
        assertThat(((LabRequestV2Payload) envelope.getValue().payload()).sourceOrderId()).isEqualTo(sourceOrderId);
        verify(publisher, never()).publishRequestCreated(any());
    }

    @Test
    void createCareFinance_duplicateSourceOrderWritesNothing() {
        UUID sourceOrderId = UUID.randomUUID();
        when(tests.existsBySourceOrderId(sourceOrderId)).thenReturn(true);
        CreateLabRequest request = new CreateLabRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CBC", LocalDate.of(2026, 9, 20), sourceOrderId,
                CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), "LAB-CBC");

        assertThatThrownBy(() -> service.createCareFinance(request, UUID.randomUUID().toString()))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting(DuplicateResourceException.class::cast)
                .extracting(DuplicateResourceException::getCode).isEqualTo("LAB_DUPLICATE_SOURCE_ORDER");
        verify(tests, never()).save(any());
        verify(outbox, never()).append(any());
    }
}
