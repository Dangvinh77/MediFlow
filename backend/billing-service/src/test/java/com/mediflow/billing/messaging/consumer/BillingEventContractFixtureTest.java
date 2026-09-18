package com.mediflow.billing.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.event.PaymentCompletedEvent;
import com.mediflow.billing.application.event.PrescriptionCancelledEvent;
import com.mediflow.billing.application.event.PrescriptionCreatedEvent;
import com.mediflow.billing.application.event.PrescriptionDispenseFailedEvent;
import com.mediflow.billing.application.event.PrescriptionExpiredEvent;
import com.mediflow.billing.application.event.PrescriptionFilledEvent;
import com.mediflow.billing.domain.model.PaymentMethod;

/**
 * Verifies the Billing-side copies of Pharmacy's versioned JSON event fixtures.
 *
 * <p>These tests exercise the real Jackson boundary used by {@link BillingEventConsumer}; they
 * intentionally depend only on Billing's application event records, never on Pharmacy classes.
 * The JSON files are Billing-side contract fixtures reviewed against Pharmacy's event records and
 * the handoff contract.
 */
class BillingEventContractFixtureTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void paymentCompleted_fixtureMatchesPharmacyConsumerContract() throws Exception {
        PaymentCompletedEvent event = read("payment.completed.json", PaymentCompletedEvent.class);

        assertEnvelope(event.eventId(), event.occurredAt(), event.correlationId());
        assertThat(event.invoiceId()).isNotNull();
        assertThat(event.prescriptionId()).isNotNull();
        assertThat(event.departmentId()).isNotNull();
        assertThat(event.totalAmount()).isEqualByComparingTo("125000.00");
        assertThat(event.paymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(event.labTestIds()).singleElement()
                .isEqualTo(java.util.UUID.fromString("66666666-6666-6666-6666-666666666666"));
    }

    /** HANDOFF-LAB-PAYMENT-COMPLETED.md — Lab must read labTestIds, never invoice/prescription/record IDs. */
    @Test
    void paymentCompleted_ordinaryInvoiceCarriesEmptyLabTestIds() throws Exception {
        PaymentCompletedEvent event = read("payment.completed-no-lab-fees.json", PaymentCompletedEvent.class);

        assertEnvelope(event.eventId(), event.occurredAt(), event.correlationId());
        assertThat(event.prescriptionId()).isNull();
        assertThat(event.labTestIds()).isEmpty();
    }

    @Test
    void paymentCompleted_invoiceWithMultipleLabFeesCarriesEachLabTestId() throws Exception {
        PaymentCompletedEvent event = read("payment.completed-multiple-lab-fees.json", PaymentCompletedEvent.class);

        assertEnvelope(event.eventId(), event.occurredAt(), event.correlationId());
        assertThat(event.labTestIds()).hasSize(2).containsExactlyInAnyOrder(
                java.util.UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                java.util.UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"));
    }

    @Test
    void prescriptionCreated_fixtureDeserializesWithRequiredContext() throws Exception {
        PrescriptionCreatedEvent event = read("prescription.created.json", PrescriptionCreatedEvent.class);

        assertEnvelope(event.eventId(), event.occurredAt(), event.correlationId());
        assertThat(event.prescriptionId()).isNotNull();
        assertThat(event.departmentId()).isNotNull();
        assertThat(event.totalAmount()).isEqualByComparingTo("125000.00");
        assertThat(event.items()).singleElement().satisfies(item -> {
            assertThat(item.quantity()).isEqualTo(10);
            assertThat(item.price()).isEqualByComparingTo("12500.00");
        });
    }

    @Test
    void prescriptionFilled_fixtureDeserializesWithCurrentV1Contract() throws Exception {
        PrescriptionFilledEvent event = read("prescription.filled.json", PrescriptionFilledEvent.class);

        assertEnvelope(event.eventId(), event.occurredAt(), event.correlationId());
        assertThat(event.prescriptionId()).isNotNull();
        assertThat(event.dispensedItems()).singleElement()
                .extracting(PrescriptionFilledEvent.DispensedItem::quantity)
                .isEqualTo(10);
    }

    @Test
    void prescriptionDispenseFailed_fixtureDeserializesCompensationContext() throws Exception {
        PrescriptionDispenseFailedEvent event = read(
                "prescription.dispense.failed.json", PrescriptionDispenseFailedEvent.class);

        assertEnvelope(event.eventId(), event.occurredAt(), event.correlationId());
        assertThat(event.prescriptionId()).isNotNull();
        assertThat(event.invoiceId()).isNotNull();
        assertThat(event.reason()).isEqualTo("STOCK_UNAVAILABLE");
    }

    @Test
    void prescriptionCancelled_fixtureDeserializesTerminationContext() throws Exception {
        PrescriptionCancelledEvent event = read("prescription.cancelled.json", PrescriptionCancelledEvent.class);

        assertEnvelope(event.eventId(), event.occurredAt(), event.correlationId());
        assertThat(event.prescriptionId()).isNotNull();
        assertThat(event.cancelledBy()).isNotNull();
        assertThat(event.reason()).isEqualTo("PATIENT_REQUEST");
    }

    @Test
    void prescriptionExpired_fixtureDeserializesTerminationContext() throws Exception {
        PrescriptionExpiredEvent event = read("prescription.expired.json", PrescriptionExpiredEvent.class);

        assertEnvelope(event.eventId(), event.occurredAt(), event.correlationId());
        assertThat(event.prescriptionId()).isNotNull();
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
