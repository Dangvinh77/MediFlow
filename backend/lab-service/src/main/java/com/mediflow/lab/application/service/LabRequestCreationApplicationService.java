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
import com.mediflow.lab.application.port.out.LabCatalogPort;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.domain.exception.LabRuleException;
import com.mediflow.lab.domain.model.LabResultTemplate;
import com.mediflow.lab.domain.model.LabServiceCatalogItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private final LabCatalogPort catalog;
    private final boolean catalogEnabled;

    public LabRequestCreationApplicationService(LabTestRepositoryPort tests,
            LabEventPublisherPort publisher, LabTestDtoMapper mapper, CorrelationIdProvider correlations,
            LabOutboxPort outbox, Clock clock) {
        this(tests, publisher, mapper, correlations, outbox, clock, null, false);
    }

    @Autowired
    public LabRequestCreationApplicationService(LabTestRepositoryPort tests,
            LabEventPublisherPort publisher, LabTestDtoMapper mapper, CorrelationIdProvider correlations,
            LabOutboxPort outbox, Clock clock, LabCatalogPort catalog,
            @Value("${mediflow.v2.lab-catalog:false}") boolean catalogEnabled) {
        this.tests = tests;
        this.publisher = publisher;
        this.mapper = mapper;
        this.correlations = correlations;
        this.outbox = outbox;
        this.clock = clock;
        this.catalog = catalog;
        this.catalogEnabled = catalogEnabled && catalog != null;
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
        LabResultTemplate template = catalogEnabled ? catalogTemplate(request) : null;
        LabTest saved = tests.save(LabTest.createV2(request.recordId(), request.patientId(),
                request.requestingDepartmentId(), request.sourceOrderId(), request.careEpisodeType(),
                request.careEpisodeId(), request.labType(), request.priceCode(), request.requestedDate()));
        if (template != null) {
            catalog.bind(saved.getTestId(), request.serviceCode(), template.templateCode(), template.version());
        }
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

    /** The ordered service must be active and carry the same name and price code as the order. */
    private LabResultTemplate catalogTemplate(CreateLabRequest request) {
        if (request.serviceCode() == null || request.serviceCode().isBlank()) {
            throw new LabRuleException("LAB_SERVICE_REQUIRED", "A Lab order must name a catalogue service");
        }
        LabServiceCatalogItem service = catalog.service(request.serviceCode()).filter(LabServiceCatalogItem::active)
                .orElseThrow(() -> new LabRuleException("LAB_SERVICE_UNKNOWN", "Unknown or inactive Lab service"));
        if (!service.name().equals(request.labType()) || !service.priceCode().equals(request.priceCode())) {
            throw new LabRuleException("LAB_SERVICE_MISMATCH",
                    "The ordered test name and price code must match the catalogue service");
        }
        return catalog.activeTemplate(service.templateCode())
                .orElseThrow(() -> new LabRuleException("LAB_SERVICE_UNKNOWN", "The Lab service has no active template"));
    }
}
