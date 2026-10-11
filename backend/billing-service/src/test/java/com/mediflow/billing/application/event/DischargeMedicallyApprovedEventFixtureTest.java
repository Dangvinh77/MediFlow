package com.mediflow.billing.application.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Billing phải đọc đúng byte producer của inpatient-service — không copy/đổi tên field
 * (HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT). Đọc trực tiếp bản sao fixture thật tại
 * {@code src/test/resources/contracts/discharge-approval-v1/}.
 */
class DischargeMedicallyApprovedEventFixtureTest {
    private static final String DIR = "contracts/discharge-approval-v1/";
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void dischargeApprovedFixture_decodesExactAdmission() throws Exception {
        DischargeMedicallyApprovedEvent event = mapper.treeToValue(
                fixture("discharge.medically.approved.v1.json"), DischargeMedicallyApprovedEvent.class);

        assertThat(event.admissionId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000021"));
        assertThat(event.patientId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000022"));
        assertThat(event.summaryId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000025"));
        assertThat(event.approvedBy()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000026"));
    }

    private JsonNode fixture(String fileName) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(DIR + fileName)) {
            assertThat(in).as("fixture " + fileName + " phải tồn tại trong test resources").isNotNull();
            return mapper.readTree(in);
        }
    }
}
