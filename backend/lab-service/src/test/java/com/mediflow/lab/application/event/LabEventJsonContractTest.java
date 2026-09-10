package com.mediflow.lab.application.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.lab.domain.model.LabResult;
import com.mediflow.lab.domain.model.LabTest;

class LabEventJsonContractTest {

    private final JsonMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void requestCreated_usesStandardEnvelopeAndCanonicalLabId() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID labId = UUID.randomUUID();
        LabRequestCreatedEvent event = new LabRequestCreatedEvent(
                eventId,
                Instant.parse("2026-09-01T09:00:00Z"),
                "corr-001",
                labId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "CBC",
                LocalDate.of(2026, 9, 1));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(event));

        assertThat(json.get("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(json.get("occurredAt").asText()).isEqualTo("2026-09-01T09:00:00Z");
        assertThat(json.get("correlationId").asText()).isEqualTo("corr-001");
        assertThat(json.get("labId").asText()).isEqualTo(labId.toString());
        assertThat(json.has("testId")).isFalse();
        assertThat(json.get("labType").asText()).isEqualTo("CBC");
        assertThat(json.get("requestedDate").asText()).isEqualTo("2026-09-01");
    }

    @Test
    void resultCreated_includesBillingNotificationAndReportFields_andCopiesResults() throws Exception {
        List<LabResultCreatedEvent.Result> results = new ArrayList<>();
        results.add(new LabResultCreatedEvent.Result(
                UUID.randomUUID(), "glucose", "<0.01", "mmol/L", "3.9-5.6"));
        LabResultCreatedEvent event = new LabResultCreatedEvent(
                UUID.randomUUID(),
                Instant.parse("2026-09-01T10:00:00Z"),
                "corr-002",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "CBC",
                LocalDate.of(2026, 9, 1),
                results,
                "Âm tính");
        results.clear();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(event));

        assertThat(json.get("labId")).isNotNull();
        assertThat(json.has("testId")).isFalse();
        assertThat(json.get("labType").asText()).isEqualTo("CBC");
        assertThat(json.get("performedDate").asText()).isEqualTo("2026-09-01");
        assertThat(json.get("results")).hasSize(1);
        assertThat(json.get("results").get(0).get("value").asText()).isEqualTo("<0.01");
        assertThat(json.get("conclusion").asText()).isEqualTo("Âm tính");
    }

    @Test
    void requestCreated_fromAggregate_mapsCanonicalIdentifiersAndEnvelope() {
        UUID testId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        LabTest test = LabTest.restore(testId, recordId, patientId, departmentId, "CBC",
                LocalDate.of(2026, 9, 1), null,
                com.mediflow.lab.domain.model.LabTestStatus.PENDING, null, false, List.of(), null, null);

        LabRequestCreatedEvent event = LabRequestCreatedEvent.from(test, "corr-request");

        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.correlationId()).isEqualTo("corr-request");
        assertThat(event.labId()).isEqualTo(testId);
        assertThat(event.patientId()).isEqualTo(patientId);
        assertThat(event.recordId()).isEqualTo(recordId);
        assertThat(event.departmentId()).isEqualTo(departmentId);
        assertThat(event.labType()).isEqualTo("CBC");
        assertThat(event.requestedDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void resultCreated_fromCompletedAggregate_mapsResultsAndRequiredExtraFields() {
        LabTest test = LabTest.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Urinalysis", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2),
                com.mediflow.lab.domain.model.LabTestStatus.COMPLETED, "Âm tính", false,
                List.of(LabResult.restore(UUID.randomUUID(), "protein", "3+", null, null)), null, null);

        LabResultCreatedEvent event = LabResultCreatedEvent.from(test, "corr-result");

        assertThat(event.eventId()).isNotNull();
        assertThat(event.correlationId()).isEqualTo("corr-result");
        assertThat(event.labId()).isEqualTo(test.getTestId());
        assertThat(event.labType()).isEqualTo("Urinalysis");
        assertThat(event.performedDate()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(event.results()).singleElement()
                .satisfies(result -> {
                    assertThat(result.value()).isEqualTo("3+");
                    assertThat(result.indicator()).isEqualTo("protein");
                });
    }

    @Test
    void resultCreated_fromIncompleteAggregate_rejectsEmptyCompletedContract() {
        LabTest test = LabTest.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CBC", LocalDate.of(2026, 9, 1), null,
                com.mediflow.lab.domain.model.LabTestStatus.IN_PROGRESS, null, false, List.of(), null, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> LabResultCreatedEvent.from(test, "corr"))
                .isInstanceOf(com.mediflow.lab.domain.exception.LabRuleException.class)
                .extracting(com.mediflow.lab.domain.exception.LabRuleException.class::cast)
                .extracting(com.mediflow.lab.domain.exception.LabRuleException::getCode)
                .isEqualTo("LAB_RESULT_NOT_COMPLETED");
    }

    @Test
    void publishedEvent_fromUnsavedAggregate_rejectsMissingLabId() {
        LabTest unsaved = LabTest.restore(null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CBC", LocalDate.of(2026, 9, 1), null,
                com.mediflow.lab.domain.model.LabTestStatus.PENDING, null, false, List.of(), null, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> LabRequestCreatedEvent.from(unsaved, "corr"))
                .isInstanceOf(com.mediflow.lab.domain.exception.LabRuleException.class)
                .extracting(com.mediflow.lab.domain.exception.LabRuleException.class::cast)
                .extracting(com.mediflow.lab.domain.exception.LabRuleException::getCode)
                .isEqualTo("LAB_ID_REQUIRED");
    }

    @Test
    void resultCreated_fromCompletedAggregateWithoutPerformedDate_rejectsInvalidContract() {
        LabTest incomplete = LabTest.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CBC", LocalDate.of(2026, 9, 1), null,
                com.mediflow.lab.domain.model.LabTestStatus.COMPLETED, "Normal", false,
                List.of(LabResult.restore(UUID.randomUUID(), "glucose", "normal", null, null)), null, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> LabResultCreatedEvent.from(incomplete, "corr"))
                .isInstanceOf(com.mediflow.lab.domain.exception.LabRuleException.class)
                .extracting(com.mediflow.lab.domain.exception.LabRuleException.class::cast)
                .extracting(com.mediflow.lab.domain.exception.LabRuleException::getCode)
                .isEqualTo("LAB_PERFORMED_DATE_REQUIRED");
    }
}
