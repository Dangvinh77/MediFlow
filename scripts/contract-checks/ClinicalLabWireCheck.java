import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.clinical.domain.model.*;
import com.mediflow.clinical.application.event.*;
import com.mediflow.lab.domain.model.*;
import com.mediflow.lab.application.event.*;
import java.time.*;
import java.util.*;

/** Read-only verification against actual compiled billing contracts, no production dependency added. */
class ClinicalLabWireCheck {
    public static void main(String[] args) throws Exception {
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        UUID patient = UUID.randomUUID(), doctor = UUID.randomUUID(), department = UUID.randomUUID();
        Appointment appointment = Appointment.create(patient, doctor, department, LocalDate.now().plusDays(1), LocalTime.of(8, 30), null);
        MedicalRecord record = MedicalRecord.create(patient, doctor, department, LocalDate.now(), null,
                appointment.getAppointmentId(), List.of(Diagnosis.create("Flu", null, "J10")));
        appointment.markArrived();
        var arrival = json.readValue(json.writeValueAsBytes(AppointmentStatusChangedEvent.from(appointment, record.getRecordId(), "wire")),
                com.mediflow.billing.application.event.AppointmentStatusChangedEvent.class);
        check(arrival.recordId().equals(record.getRecordId()) && arrival.status().equals("ARRIVED")
                && arrival.departmentId().equals(department) && arrival.patientId().equals(patient), "clinical arrival -> billing");
        var created = json.readValue(json.writeValueAsBytes(MedicalRecordCreatedEvent.from(record, "wire")),
                com.mediflow.billing.application.event.MedicalRecordCreatedEvent.class);
        check(created.recordId().equals(record.getRecordId()) && created.departmentId().equals(department)
                && created.patientId().equals(patient), "clinical record -> billing");
        LabTest lab = LabTest.create(record.getRecordId(), patient, department, "CBC", LocalDate.now());
        lab.recordResults(List.of(LabResult.create("WBC", "<0.01", "10^9/L", "4-11")), "Completed", LocalDate.now());
        var event = LabResultCreatedEvent.from(lab, "wire");
        var result = json.readValue(json.writeValueAsBytes(event),
                com.mediflow.billing.application.event.LabResultCreatedEvent.class);
        check(result.labId().equals(lab.getTestId()) && result.labType().equals("CBC")
                && result.performedDate().equals(lab.getPerformedDate())
                && result.recordId().equals(record.getRecordId()) && result.patientId().equals(patient)
                && result.departmentId().equals(department), "lab result -> billing");
        UUID invoice = UUID.randomUUID(), prescription = UUID.randomUUID();
        var payment = new com.mediflow.billing.application.event.PaymentCompletedEvent(UUID.randomUUID(),
                Instant.now(), invoice.toString(), invoice, patient, department, prescription,
                java.math.BigDecimal.TEN, com.mediflow.billing.domain.model.PaymentMethod.CASH);
        var command = json.readValue(json.writeValueAsBytes(payment),
                com.mediflow.pharmacy.application.dto.command.PaymentCompletedCommand.class);
        check(command.prescriptionId().equals(prescription) && command.correlationId().equals(invoice.toString())
                && command.paymentMethod().equals("CASH"), "billing payment -> pharmacy");
        System.out.println("4 actual cross-service JSON contract checks passed");
    }
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
