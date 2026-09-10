package com.mediflow.clinical.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.clinical.application.dto.request.*;
import com.mediflow.clinical.application.dto.response.*;
import com.mediflow.clinical.application.event.*;
import com.mediflow.clinical.application.mapper.ClinicalDtoMapper;
import com.mediflow.clinical.domain.model.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ClinicalContractTest {
    private final ClinicalDtoMapper mapper = Mappers.getMapper(ClinicalDtoMapper.class);
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private Appointment appointment() {
        return Appointment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now().plusDays(1), LocalTime.of(7, 30), "Follow-up");
    }

    @Test void mapAppointment_preservesFieldsAndTimeJson() throws Exception {
        Appointment domain = appointment();
        AppointmentDTO dto = mapper.toDto(domain);
        assertThat(dto).usingRecursiveComparison().isEqualTo(new AppointmentDTO(domain.getAppointmentId(),
                domain.getPatientId(), domain.getDoctorId(), domain.getDepartmentId(), domain.getAppointmentDate(),
                domain.getAppointmentTime(), domain.getStatus(), domain.getReason(), domain.getCreatedAt(), domain.getUpdatedAt()));
        assertThat(json.readTree(json.writeValueAsString(dto)).get("appointmentTime").asText()).isEqualTo("07:30");
    }

    @Test void mapRecord_preservesNestedDiagnosesAndIdentity() {
        Diagnosis diagnosis = mapper.toDomain(new AddDiagnosisRequest("Flu", "Description", "J10.1"));
        MedicalRecord record = MedicalRecord.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), "Fever", UUID.randomUUID(), List.of(diagnosis));
        MedicalRecordDTO dto = mapper.toDto(record);
        assertThat(dto).usingRecursiveComparison().isEqualTo(new MedicalRecordDTO(record.getRecordId(), record.getPatientId(),
                record.getDoctorId(), record.getDepartmentId(), record.getExaminationDate(), record.getSymptoms(),
                record.getAppointmentId(), List.of(new DiagnosisDTO(diagnosis.getDiagnosisId(), "Flu", "Description", "J10.1")),
                record.getCreatedAt(), record.getUpdatedAt()));
        assertThatThrownBy(() -> dto.diagnoses().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void validateRecord_rejectsInvalidNestedDiagnosisAndNullElement() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var bad = new CreateRecordRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(),
                    null, null, Arrays.asList(new AddDiagnosisRequest("", null, "bad"), null));
            assertThat(validator.validate(bad)).extracting(v -> v.getPropertyPath().toString())
                    .contains("diagnoses[0].diagnosisName", "diagnoses[0].icdCode", "diagnoses[1].<list element>");
        }
    }

    @Test void validateRequests_requiredFieldsAndDatesAreEnforced() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new CreateAppointmentRequest(null, null, null, LocalDate.now().minusDays(1), null, "x".repeat(1001))))
                    .hasSize(6);
            assertThat(validator.validate(new UpdateAppointmentRequest(null, null, null))).hasSize(2);
            assertThat(validator.validate(new ChangeStatusRequest(null))).hasSize(1);
            assertThat(validator.validate(new UpdateRecordRequest("x".repeat(4001)))).hasSize(1);
            assertThat(validator.validate(new CreateRecordRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    LocalDate.now().plusDays(1), null, null, List.of()))).hasSize(2);
        }
    }

    @Test void requestDiagnosisList_cannotBeMutatedAfterValidation() {
        List<AddDiagnosisRequest> list = new ArrayList<>(List.of(new AddDiagnosisRequest("Flu", null, null)));
        var request = new CreateRecordRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), null, null, list);
        list.clear();
        assertThat(request.diagnoses()).hasSize(1);
        assertThatThrownBy(() -> request.diagnoses().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void appointmentEvent_keepsNotificationTimeAndEnvelope() throws Exception {
        Appointment domain = appointment();
        var event = AppointmentCreatedEvent.from(domain, "correlation-1");
        var tree = json.readTree(json.writeValueAsString(event));
        assertThat(tree.get("appointmentTime").asText()).isEqualTo("07:30");
        assertThat(tree.get("patientId").asText()).isEqualTo(domain.getPatientId().toString());
        assertThat(tree.get("departmentId").asText()).isEqualTo(domain.getDepartmentId().toString());
        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.correlationId()).isEqualTo("correlation-1");
    }

    // JSON projections mirror billing's application/event records, without a cross-service Java dependency.
    record BillingArrival(UUID eventId, Instant occurredAt, String correlationId, UUID appointmentId,
                          UUID recordId, UUID patientId, UUID departmentId, String status) {}
    record BillingRecord(UUID eventId, Instant occurredAt, String correlationId, UUID recordId, UUID patientId, UUID departmentId) {}

    @Test void arrivalEvent_billingReceivesRecordCorrelation() throws Exception {
        Appointment domain = appointment();
        domain.markArrived();
        UUID recordId = UUID.randomUUID();
        var event = AppointmentStatusChangedEvent.from(domain, recordId, "trace");
        var consumer = json.readValue(json.writeValueAsString(event), BillingArrival.class);
        assertThat(consumer.recordId()).isEqualTo(recordId);
        assertThat(consumer.status()).isEqualTo("ARRIVED");
        assertThat(consumer.appointmentId()).isEqualTo(domain.getAppointmentId());
        assertThat(consumer.patientId()).isEqualTo(domain.getPatientId());
        assertThat(consumer.departmentId()).isEqualTo(domain.getDepartmentId());
    }

    @Test void standaloneArrival_keepsUnknownRecordNull() {
        Appointment domain = appointment();
        domain.markArrived();
        assertThat(AppointmentStatusChangedEvent.from(domain, null, null).recordId()).isNull();
    }

    @Test void medicalRecordEvent_supportsBillingAndReport() throws Exception {
        var record = MedicalRecord.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(),
                null, null, List.of(Diagnosis.create("Flu", null, "J10")));
        var event = MedicalRecordCreatedEvent.from(record, "trace");
        var consumer = json.readValue(json.writeValueAsString(event), BillingRecord.class);
        assertThat(consumer.recordId()).isEqualTo(record.getRecordId());
        assertThat(consumer.patientId()).isEqualTo(record.getPatientId());
        assertThat(consumer.departmentId()).isEqualTo(record.getDepartmentId());
        assertThat(event.doctorId()).isEqualTo(record.getDoctorId());
        assertThat(event.examinationDate()).isEqualTo(record.getExaminationDate());
        assertThat(event.diagnosis()).isEqualTo("Flu");
    }

    @Test void diagnosisEvent_mapsIcdCodeToDiagnosisCode() {
        UUID recordId = UUID.randomUUID();
        var event = DiagnosisAddedEvent.from(recordId, Diagnosis.create("Flu", null, "J10.1"), "trace");
        assertThat(event.recordId()).isEqualTo(recordId);
        assertThat(event.diagnosisCode()).isEqualTo("J10.1");
        assertThat(event.diagnosisName()).isEqualTo("Flu");
    }
}
