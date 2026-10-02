package com.mediflow.patient.contract;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.patient.application.dto.response.PatientLookupDTO;
import com.mediflow.patient.application.event.PatientCreatedEvent;
import com.mediflow.patient.application.event.PatientUpdatedEvent;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PatientContractDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void lookupFixtureDeserializesTheServiceOnlyEnvelope() throws Exception {
        try (InputStream fixture = getClass().getResourceAsStream("/contracts/patient.lookup.exists.json")) {
            assertThat(fixture).isNotNull();
            ApiResponse<PatientLookupDTO> response = objectMapper.readValue(fixture,
                    new TypeReference<ApiResponse<PatientLookupDTO>>() { });

            assertThat(response.success()).isTrue();
            assertThat(response.data().exists()).isTrue();
            assertThat(response.data().patientId())
                    .isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000001"));
            assertThat(response.correlationId()).isEqualTo("10000000-0000-4000-8000-000000000001");
        }
    }

    @Test
    void patientCreatedFixtureKeepsNotificationCompatibilityPayload() throws Exception {
        try (InputStream fixture = getClass().getResourceAsStream("/contracts/patient.created.json")) {
            assertThat(fixture).isNotNull();
            PatientCreatedEvent event = objectMapper.readValue(fixture, PatientCreatedEvent.class);

            assertThat(event.eventId()).isEqualTo(UUID.fromString("20000000-0000-4000-8000-000000000001"));
            assertThat(event.patientId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000001"));
            assertThat(event.hoTen()).isEqualTo("Nguyen Van A");
            assertThat(event.email()).isEqualTo("a@example.com");
            assertThat(event.sdt()).isEqualTo("0900000000");
        }
    }

    @Test
    void patientUpdatedFixtureDeserializesTheLockedPayload() throws Exception {
        try (InputStream fixture = getClass().getResourceAsStream("/contracts/patient.updated.json")) {
            assertThat(fixture).isNotNull();
            PatientUpdatedEvent event = objectMapper.readValue(fixture, PatientUpdatedEvent.class);

            assertThat(event.patientId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000001"));
            assertThat(event.hoTen()).isEqualTo("Nguyen Van A Updated");
            assertThat(event.sdt()).isEqualTo("0900000001");
            assertThat(event.diaChi()).isEqualTo("Ho Chi Minh City");
        }
    }
}
