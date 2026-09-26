package com.mediflow.pharmacy.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.event.PrescriptionCancelledEvent;
import com.mediflow.pharmacy.application.event.PrescriptionCreatedEvent;
import com.mediflow.pharmacy.application.event.PrescriptionDispenseFailedEvent;
import com.mediflow.pharmacy.application.event.PrescriptionExpiredEvent;
import com.mediflow.pharmacy.application.event.PrescriptionFilledEvent;
import com.mediflow.pharmacy.messaging.consumer.payload.PaymentCompletedEvent;

/**
 * Verifies pharmacy's versioned JSON fixtures at the Rabbit boundary.
 *
 * <p>These fixtures are the contract handoff for Billing, Notification and Report owners. They
 * intentionally assert only the fields owned by pharmacy and do not couple to another service's
 * Java classes.</p>
 */
class PharmacyEventContractFixtureTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /** Payment proof fixture keeps UUID, amount and saga envelope types stable. */
    @Test
    void paymentCompleted_fixtureMatchesPharmacyPayload() throws Exception {
        PaymentCompletedEvent event = read("payment.completed.json", PaymentCompletedEvent.class);

        assertEnvelope(event.eventId(), event.occurredAt(), event.correlationId());
        assertThat(event.prescriptionId()).isNotNull();
        assertThat(event.totalAmount()).isEqualByComparingTo("125000.00");
        assertThat(event.paymentMethod()).isEqualTo("CASH");
    }

    /** Prescription-created fixture exposes a price snapshot and line quantity. */
    @Test
    void prescriptionCreated_fixtureMatchesPharmacyEvent() throws Exception {
        PrescriptionCreatedEvent event = read("prescription.created.json", PrescriptionCreatedEvent.class);

        assertEnvelope(event.eventId(), event.occurredAt(), event.correlationId());
        assertThat(event.items()).singleElement().satisfies(item -> {
            assertThat(item.quantity()).isEqualTo(10);
            assertThat(item.price()).isEqualByComparingTo("12500.00");
        });
    }

    /** Filled fixture carries the exact dispensed quantity for downstream reporting. */
    @Test
    void prescriptionFilled_fixtureMatchesPharmacyEvent() throws Exception {
        PrescriptionFilledEvent event = read("prescription.filled.json", PrescriptionFilledEvent.class);

        assertEnvelope(event.eventId(), event.occurredAt(), event.correlationId());
        assertThat(event.prescriptionId()).isEqualTo(
                java.util.UUID.fromString("55555555-5555-5555-5555-555555555555"));
        assertThat(event.recordId()).isEqualTo(
                java.util.UUID.fromString("66666666-6666-6666-6666-666666666666"));
        assertThat(event.dispensedItems()).singleElement()
                .extracting(PrescriptionFilledEvent.DispensedItem::quantity)
                .isEqualTo(10);
    }

    /** Failure fixture carries compensation invoice context and failed item quantities. */
    @Test
    void prescriptionDispenseFailed_fixtureMatchesPharmacyEvent() throws Exception {
        PrescriptionDispenseFailedEvent event = read(
                "prescription.dispense.failed.json", PrescriptionDispenseFailedEvent.class);

        assertEnvelope(event.eventId(), event.occurredAt(), event.correlationId());
        assertThat(event.invoiceId()).isNotNull();
        assertThat(event.failedItems()).singleElement()
                .extracting(PrescriptionDispenseFailedEvent.FailedItem::availableQty)
                .isEqualTo(0);
    }

    /** The existing Billing consumer fixture must keep the cancellation actor and reason. */
    @Test
    void prescriptionCancelled_legacyBillingFixtureMatchesPharmacyEvent() throws Exception {
        PrescriptionCancelledEvent event = read(
                "prescription.cancelled.json", PrescriptionCancelledEvent.class);

        assertEnvelope(event.eventId(), event.occurredAt(), event.correlationId());
        assertThat(event.prescriptionId()).isNotNull();
        assertThat(event.patientId()).isNotNull();
        assertThat(event.cancelledBy()).isNotNull();
        assertThat(event.reason()).isEqualTo("PATIENT_REQUEST");
    }

    /** The existing Billing consumer fixture keeps reservation count and prescription identity. */
    @Test
    void prescriptionExpired_legacyBillingFixtureMatchesPharmacyEvent() throws Exception {
        PrescriptionExpiredEvent event = read(
                "prescription.expired.json", PrescriptionExpiredEvent.class);

        assertEnvelope(event.eventId(), event.occurredAt(), event.correlationId());
        assertThat(event.prescriptionId()).isNotNull();
        assertThat(event.patientId()).isNotNull();
        assertThat(event.expiredReservations()).isEqualTo(1);
    }

    private <T> T read(String name, Class<T> type) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/contracts/" + name)) {
            assertThat(stream).as("fixture %s", name).isNotNull();
            return objectMapper.readValue(stream, type);
        }
    }

    private void assertEnvelope(java.util.UUID eventId, java.time.Instant occurredAt, String correlationId) {
        assertThat(eventId).isNotNull();
        assertThat(occurredAt).isNotNull();
        assertThat(correlationId).isNotBlank();
    }
}
