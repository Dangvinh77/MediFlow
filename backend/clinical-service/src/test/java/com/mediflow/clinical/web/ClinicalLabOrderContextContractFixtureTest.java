package com.mediflow.clinical.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.dto.response.ClinicalLabOrderContextDTO;
import com.mediflow.common.api.ApiResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ClinicalLabOrderContextContractFixtureTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void producerFixtureLocksTheLabContextWireNamesAndOutpatientValues() throws IOException {
        String fixture;
        try (var stream = getClass().getResourceAsStream(
                "/contracts/clinical-lab-order-context-v1/valid.json")) {
            if (stream == null) throw new IOException("Missing Clinical Lab context fixture");
            fixture = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        ApiResponse<ClinicalLabOrderContextDTO> response = mapper.readValue(fixture,
                new TypeReference<>() {});

        assertThat(response.success()).isTrue();
        assertThat(response.error()).isNull();
        assertThat(response.correlationId()).isEqualTo("00000000-0000-0000-0000-000000001099");
        assertThat(response.data().exists()).isTrue();
        assertThat(response.data().sourceOrderId().toString())
                .isEqualTo("00000000-0000-0000-0000-000000001001");
        assertThat(response.data().recordId().toString()).isEqualTo("00000000-0000-0000-0000-000000001002");
        assertThat(response.data().requestingDoctorId().toString())
                .isEqualTo("00000000-0000-0000-0000-000000001004");
        assertThat(response.data().careEpisodeType().name()).isEqualTo("OUTPATIENT_VISIT");
        assertThat(response.data().labType()).isEqualTo("CBC");
        assertThat(response.data().priceCode()).isEqualTo("LAB-CBC");
    }
}
