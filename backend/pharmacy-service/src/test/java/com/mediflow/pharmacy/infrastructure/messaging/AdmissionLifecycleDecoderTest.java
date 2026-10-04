package com.mediflow.pharmacy.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.domain.model.AdmissionMedicationContext;

class AdmissionLifecycleDecoderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AdmissionLifecycleDecoder decoder = new AdmissionLifecycleDecoder(mapper);

    @Test
    void actualInpatientFixtureBytes_supportCloseBeforeStartWithoutReopen() throws Exception {
        var started = decoder.decode("admission.started", fixture("admission.started.v1.json"));
        var closed = decoder.decode("admission.closed", fixture("admission.closed.v1.json"));
        var context = AdmissionMedicationContext.empty(started.fact().admissionId(), started.fact().patientId())
                .apply(closed.fact()).apply(started.fact());
        assertThat(context.closedAt()).isNotNull();
        assertThat(context.departmentId()).isEqualTo(started.fact().departmentId());
        assertThat(started.fact().admissionId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000021"));
    }

    @Test
    void sameFactWithNewEventId_keepsBusinessFingerprintNotEnvelopeFingerprint() throws Exception {
        var root = mapper.readTree(fixture("admission.started.v1.json"));
        var original = decoder.decode("admission.started", mapper.writeValueAsBytes(root));
        ((com.fasterxml.jackson.databind.node.ObjectNode) root).put("eventId", UUID.randomUUID().toString());
        var redelivery = decoder.decode("admission.started", mapper.writeValueAsBytes(root));
        assertThat(redelivery.fact().fingerprint()).isEqualTo(original.fact().fingerprint());
        assertThat(redelivery.eventFingerprint()).isNotEqualTo(original.eventFingerprint());
    }

    @Test
    void actualMedicalDischargeFixture_endsMedicationEligibilityBeforeAdministrativeClose() throws Exception {
        var started = decoder.decode("admission.started", fixture("admission.started.v1.json"));
        var discharged = decoder.decode("discharge.medically.approved",
                fixture("discharge.medically.approved.v1.json"));
        var closed = decoder.decode("admission.closed", fixture("admission.closed.v1.json"));
        var seed = AdmissionMedicationContext.empty(started.fact().admissionId(), started.fact().patientId());
        var context = seed.apply(discharged.fact()).apply(started.fact());
        assertThat(context.medicallyDischargedAt()).isEqualTo(discharged.fact().effectiveAt());
        assertThat(context.closedAt()).isNull();
        assertThatThrownBy(() -> context.requireActive(started.fact().patientId(),
                started.fact().departmentId())).hasMessageContaining("not active");
        assertThat(context.apply(closed.fact())).isEqualTo(
                seed.apply(started.fact()).apply(discharged.fact()).apply(closed.fact()));
    }

    @Test
    void medicalDischargeMissingAuthorityOrWrongRouting_isRejected() throws Exception {
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(
                fixture("discharge.medically.approved.v1.json"));
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) root.get("payload");
        payload.remove("approvedBy");
        assertThatThrownBy(() -> decoder.decode("discharge.medically.approved", mapper.writeValueAsBytes(root)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> decoder.decode("admission.closed",
                fixture("discharge.medically.approved.v1.json")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wrongVersionProducerRoutingAndMissingDepartmentAreRejected() throws Exception {
        String json = new String(fixture("admission.started.v1.json"), java.nio.charset.StandardCharsets.UTF_8);
        assertThatThrownBy(() -> decoder.decode("admission.started", json.replace("\"version\": 1", "\"version\": 2").getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> decoder.decode("admission.started", json.replace("inpatient-service", "billing-service").getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> decoder.decode("admission.closed", json.getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
        var root = mapper.readTree(json);
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.get("payload")).remove("departmentId");
        assertThatThrownBy(() -> decoder.decode("admission.started", mapper.writeValueAsBytes(root)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] fixture(String name) throws Exception {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("backend/inpatient-service"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Repository fixture root not found");
        }
        return Files.readAllBytes(current.resolve("backend/inpatient-service/src/test/resources/contracts/" + name));
    }
}
