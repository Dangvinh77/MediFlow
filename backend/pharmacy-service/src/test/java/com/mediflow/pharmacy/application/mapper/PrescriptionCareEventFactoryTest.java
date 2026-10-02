package com.mediflow.pharmacy.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent.EventType;
import com.mediflow.pharmacy.domain.model.*;
import com.mediflow.pharmacy.domain.model.enums.*;

class PrescriptionCareEventFactoryTest {
    private final UUID prescriptionId = UUID.randomUUID();
    private final Instant created = Instant.parse("2026-10-01T08:00:00Z");
    private final Instant terminal = created.plusSeconds(600);
    private final PrescriptionCareContext care = PrescriptionCareContext.v1(CareContext.OUTPATIENT,
            new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID()), null, "RX-CODE");

    @Test
    void createdEvent_usesPersistedIdentityEpisodeNameAndPriceSnapshot() {
        var rx = prescription(PrescriptionStatus.ACTIVE, "Historical name");
        var event = PrescriptionCareEventFactory.create(UUID.randomUUID(), EventType.CREATED, "trace", rx, null, null);
        assertThat(event.occurredAt()).isEqualTo(created);
        assertThat(event.payload().sourceId()).isEqualTo(prescriptionId);
        assertThat(event.payload().careEpisodeId()).isEqualTo(care.episode().id());
        assertThat(event.payload().items().get(0).drugName()).isEqualTo("Historical name");
        assertThat(event.payload().items().get(0).unitPrice()).isEqualByComparingTo("12.50");
        assertThat(event.payload().totalAmount()).isEqualByComparingTo("25.00");
    }

    @Test
    void allTerminalTypes_haveOnlyTheirBusinessTimestampAndRequiredReasonOrDispense() {
        for (EventType type : List.of(EventType.FILLED, EventType.CANCELLED, EventType.EXPIRED, EventType.DISPENSE_FAILED)) {
            PrescriptionStatus status = switch (type) {
                case FILLED -> PrescriptionStatus.FULFILLED;
                case CANCELLED -> PrescriptionStatus.CANCELLED;
                case EXPIRED -> PrescriptionStatus.EXPIRED;
                default -> PrescriptionStatus.DISPENSE_FAILED;
            };
            UUID dispense = type == EventType.FILLED ? UUID.randomUUID() : null;
            String reason = type == EventType.FILLED ? null : "Saved reason";
            var event = PrescriptionCareEventFactory.create(UUID.randomUUID(), type, "trace", prescription(status, "Historical name"), dispense, reason);
            assertThat(event.occurredAt()).isEqualTo(terminal);
            assertThat(event.payload().createdAt()).isNull();
            assertThat(event.payload().dispenseId()).isEqualTo(dispense);
            assertThat(event.payload().reason()).isEqualTo(reason);
        }
    }

    @Test
    void missingHistoricalName_isNotBackfilledFromCurrentCatalogue() {
        assertThatThrownBy(() -> PrescriptionCareEventFactory.create(UUID.randomUUID(), EventType.CREATED, "trace",
                prescription(PrescriptionStatus.ACTIVE, null), null, null)).hasMessageContaining("priced prescription item");
    }

    @Test
    void stateAndCancellationReasonMustMatchPersistedBusinessSnapshot() {
        assertThatThrownBy(() -> PrescriptionCareEventFactory.create(UUID.randomUUID(), EventType.FILLED, "trace",
                prescription(PrescriptionStatus.ACTIVE, "name"), UUID.randomUUID(), null)).hasMessageContaining("state");
        assertThatThrownBy(() -> PrescriptionCareEventFactory.create(UUID.randomUUID(), EventType.CANCELLED, "trace",
                prescription(PrescriptionStatus.CANCELLED, "name"), null, "Different reason")).hasMessageContaining("Cancellation reason");
    }

    @Test
    void updatedAtCannotSubstituteForMissingTerminalBusinessTime() {
        var rx = Prescription.restore(prescriptionId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 10, 1), new BigDecimal("25.00"), List.of(line("name")), PrescriptionStatus.FULFILLED,
                null, null, null, created, terminal, care);
        assertThatThrownBy(() -> PrescriptionCareEventFactory.create(UUID.randomUUID(), EventType.FILLED, "trace", rx, UUID.randomUUID(), null))
                .hasMessageContaining("lifecycle timestamp");
    }

    @Test
    void reloadLineOrderDoesNotChangeLifecycleEventBytes() {
        var first = line("First snapshot");
        var second = line("Second snapshot");
        var base = prescription(PrescriptionStatus.ACTIVE, "name");
        var rx = copyWithLines(base, List.of(first, second));
        var reordered = copyWithLines(base, List.of(second, first));
        UUID eventId = UUID.randomUUID();
        var firstEvent = PrescriptionCareEventFactory.create(eventId, EventType.CREATED, "trace", rx, null, null);
        var replayed = PrescriptionCareEventFactory.create(eventId, EventType.CREATED, "trace", reordered, null, null);
        assertThat(replayed).isEqualTo(firstEvent);
    }

    private Prescription copyWithLines(Prescription base, List<PrescriptionLine> lines) {
        return Prescription.restore(base.getPrescriptionId(), base.getRecordId(), base.getPatientId(), base.getDoctorId(),
                base.getDepartmentId(), base.getPrescribedDate(), new BigDecimal("50.00"), lines, base.getStatus(),
                null, null, null, base.getCreatedAt(), base.getUpdatedAt(), base.getCareContext());
    }

    @Test
    void lineSnapshotRejectsBlankOrOverlongNameAndLegacyRestoreStillWorks() {
        assertThatThrownBy(() -> PrescriptionLine.create(UUID.randomUUID(), 1, BigDecimal.ONE, null, " ")).hasMessageContaining("snapshot");
        assertThatThrownBy(() -> PrescriptionLine.create(UUID.randomUUID(), 1, BigDecimal.ONE, null, "x".repeat(151))).hasMessageContaining("snapshot");
        assertThat(PrescriptionLine.create(UUID.randomUUID(), 1, BigDecimal.ONE, null).getDrugNameSnapshot()).isNull();
    }

    @Test
    void legacyPrescriptionDoesNotBecomeV1ByEmittingAnEvent() {
        var rx = Prescription.restore(prescriptionId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 10, 1), new BigDecimal("25.00"), List.of(line("name")), PrescriptionStatus.ACTIVE,
                null, null, null, created, created);
        assertThatThrownBy(() -> PrescriptionCareEventFactory.create(UUID.randomUUID(), EventType.CREATED, "trace", rx, null, null))
                .hasMessageContaining("persisted V1");
    }

    private Prescription prescription(PrescriptionStatus status, String name) {
        return Prescription.restore(prescriptionId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 10, 1), new BigDecimal("25.00"), List.of(line(name)), status,
                status == PrescriptionStatus.CANCELLED ? terminal : null, status == PrescriptionStatus.CANCELLED ? UUID.randomUUID() : null,
                status == PrescriptionStatus.CANCELLED ? "Saved reason" : null, created, status == PrescriptionStatus.ACTIVE ? created : terminal, care,
                status == PrescriptionStatus.ACTIVE ? null : terminal);
    }

    private PrescriptionLine line(String name) {
        return PrescriptionLine.restore(UUID.randomUUID(), UUID.randomUUID(), 2, new BigDecimal("12.50"), null, new BigDecimal("25.00"), name);
    }
}
