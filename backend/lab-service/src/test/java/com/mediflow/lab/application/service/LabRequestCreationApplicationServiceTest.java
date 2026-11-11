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
import com.mediflow.lab.application.port.out.LabCatalogPort;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.domain.exception.LabRuleException;
import com.mediflow.lab.domain.model.LabResultTemplate;
import com.mediflow.lab.domain.model.LabServiceCatalogItem;
import java.util.List;
import java.util.Optional;
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

    private final LabCatalogPort catalog = mock(LabCatalogPort.class);
    private final LabRequestCreationApplicationService catalogService = new LabRequestCreationApplicationService(
            tests, publisher, mapper, correlations, outbox, Clock.fixed(NOW, ZoneOffset.UTC), catalog, true);
    private static final LabServiceCatalogItem CBC = new LabServiceCatalogItem("CBC", "Công thức máu", "HEMATOLOGY",
            "WHOLE_BLOOD_EDTA", null, 4, "LAB-CBC", "CBC", true, true);

    private CreateLabRequest catalogRequest(String serviceCode, String labType, String priceCode) {
        return new CreateLabRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), labType,
                LocalDate.of(2026, 9, 20), UUID.randomUUID(), CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(),
                priceCode, serviceCode);
    }

    @Test
    void catalogOn_pinsTheActiveTemplateVersionOfTheOrderedService() {
        when(catalog.service("CBC")).thenReturn(Optional.of(CBC));
        when(catalog.activeTemplate("CBC")).thenReturn(Optional.of(new LabResultTemplate("CBC", 3, "CBC", List.of())));
        when(tests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        catalogService.createCareFinance(catalogRequest("CBC", "Công thức máu", "LAB-CBC"), "c");

        ArgumentCaptor<LabTest> saved = ArgumentCaptor.forClass(LabTest.class);
        verify(tests).save(saved.capture());
        verify(catalog).bind(saved.getValue().getTestId(), "CBC", "CBC", 3);
    }

    @Test
    void catalogOn_requiresAServiceCode() {
        assertThatThrownBy(() -> catalogService.createCareFinance(catalogRequest(null, "CBC", "LAB-CBC"), "c"))
                .isInstanceOf(LabRuleException.class).extracting("code").isEqualTo("LAB_SERVICE_REQUIRED");
        verify(tests, never()).save(any());
    }

    @Test
    void catalogOn_rejectsUnknownOrInactiveServices() {
        when(catalog.service("OLD")).thenReturn(Optional.of(new LabServiceCatalogItem("OLD", "Cũ", "HEMATOLOGY",
                "SERUM", null, 4, "LAB-OLD", "OLD", false, false)));
        assertThatThrownBy(() -> catalogService.createCareFinance(catalogRequest("NOPE", "x", "LAB-CBC"), "c"))
                .extracting("code").isEqualTo("LAB_SERVICE_UNKNOWN");
        assertThatThrownBy(() -> catalogService.createCareFinance(catalogRequest("OLD", "Cũ", "LAB-OLD"), "c"))
                .extracting("code").isEqualTo("LAB_SERVICE_UNKNOWN");
        verify(tests, never()).save(any());
    }

    @Test
    void catalogOn_rejectsANameOrPriceThatDiffersFromTheCatalogue() {
        when(catalog.service("CBC")).thenReturn(Optional.of(CBC));
        assertThatThrownBy(() -> catalogService.createCareFinance(catalogRequest("CBC", "Công thức máu", "LAB-GLU"), "c"))
                .extracting("code").isEqualTo("LAB_SERVICE_MISMATCH");
        assertThatThrownBy(() -> catalogService.createCareFinance(catalogRequest("CBC", "CBC tự đặt", "LAB-CBC"), "c"))
                .extracting("code").isEqualTo("LAB_SERVICE_MISMATCH");
        verify(tests, never()).save(any());
    }

    @Test
    void catalogOff_keepsTheFreeTextBehaviour() {
        when(tests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.createCareFinance(catalogRequest("CBC", "CBC", "LAB-CBC"), "c");
        verify(tests).save(any());
        verify(catalog, never()).bind(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
