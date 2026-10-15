package com.mediflow.lab.application.service;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.lab.application.dto.request.CreateLabRequest;
import com.mediflow.lab.application.dto.response.LabTestDTO;
import com.mediflow.lab.application.event.DomainEventEnvelope;
import com.mediflow.lab.application.event.LabRequestCreatedEvent;
import com.mediflow.lab.application.event.LabRequestV2Payload;
import com.mediflow.lab.application.mapper.LabTestDtoMapper;
import com.mediflow.lab.application.port.in.CreateLabRequestUseCase;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.port.out.LabEventPublisherPort;
import com.mediflow.lab.application.port.out.LabOutboxPort;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.domain.model.LabTest;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

/** Keeps Lab persistence and its outbox fact in one local write transaction. */
@Service
public class LabRequestCreationApplicationService implements CreateLabRequestUseCase {

    private final LabTestRepositoryPort tests;
    private final LabEventPublisherPort publisher;
    private final LabTestDtoMapper mapper;
    private final CorrelationIdProvider correlations;
    private final LabOutboxPort outbox;
    private final Clock clock;

    public LabRequestCreationApplicationService(LabTestRepositoryPort tests,
            LabEventPublisherPort publisher, LabTestDtoMapper mapper, CorrelationIdProvider correlations,
            LabOutboxPort outbox, Clock clock) {
        this.tests = tests;
        this.publisher = publisher;
        this.mapper = mapper;
        this.correlations = correlations;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public LabTestDTO createCompatibility(CreateLabRequest request) {
        LabTest saved = tests.save(LabTest.create(request.recordId(), request.patientId(),
                request.requestingDepartmentId(), request.labType(), request.requestedDate()));
        publisher.publishRequestCreated(
                LabRequestCreatedEvent.from(saved, correlations.currentOrCreate().toString()));
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public LabTestDTO createCareFinance(CreateLabRequest request, String correlationId) {
        if (request.sourceOrderId() != null && tests.existsBySourceOrderId(request.sourceOrderId())) {
            throw new DuplicateResourceException("LAB_DUPLICATE_SOURCE_ORDER",
                    "Lab request already exists for this Clinical source order");
        }
        LabTest saved = tests.save(LabTest.createV2(request.recordId(), request.patientId(),
                request.requestingDepartmentId(), request.sourceOrderId(), request.careEpisodeType(),
                request.careEpisodeId(), request.labType(), request.priceCode(), request.requestedDate()));
        Instant occurredAt = clock.instant();
        LabRequestV2Payload payload = new LabRequestV2Payload(saved.getTestId(), saved.getPatientId(),
                saved.getRecordId(), saved.getRequestingDepartmentId(), saved.getCareEpisodeType(),
                saved.getCareEpisodeId(), saved.getSourceOrderId(), "LAB_TEST", saved.getTestId(),
                saved.getPriceCode(), saved.getLabType(), occurredAt, saved.getEmergencyOverrideId());
        outbox.append(new DomainEventEnvelope<>(UUID.randomUUID(), "lab.request.created", 1,
                occurredAt, correlationId == null ? correlations.currentOrCreate().toString() : correlationId,
                "lab-service", payload));
        return mapper.toDto(saved);
    }
}
