package com.mediflow.billing.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.dto.command.LabTestChargeCommand;
import java.io.InputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Billing phải đọc đúng byte producer của lab-service — không copy/đổi tên field
 * (HANDOFF-CLINICAL-LAB-FINANCIAL-CLEARANCE). Đọc trực tiếp bản sao fixture thật tại
 * {@code src/test/resources/contracts/lab-request-v1/} (mirror của
 * {@code backend/lab-service/src/test/resources/contracts/lab.request.created.v1.json}).
 */
class LabTestChargeDecoderTest {
    private static final String DIR = "contracts/lab-request-v1/";
    private final LabTestChargeDecoder decoder = new LabTestChargeDecoder(new ObjectMapper());

    @Test
    void requestCreatedFixture_decodesExactLabTestCharge() throws Exception {
        LabTestChargeCommand command = decoder.decode("lab.request.created", bytes("lab.request.created.v1.json"));

        assertThat(command.labId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000002"));
        assertThat(command.sourceOrderId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000008"));
        assertThat(command.patientId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000003"));
        assertThat(command.recordId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000004"));
        assertThat(command.careEpisodeType()).isEqualTo("OUTPATIENT_VISIT");
        assertThat(command.priceCode()).isEqualTo("LAB-CBC");
        assertThat(command.labType()).isEqualTo("CBC");
        assertThat(command.emergencyOverrideId()).isNull();
    }

    @Test
    void wrongRoutingKey_rejected() throws Exception {
        byte[] body = bytes("lab.request.created.v1.json");
        assertThatThrownBy(() -> decoder.decode("lab.result.created", body)).isInstanceOf(IllegalArgumentException.class);
    }

    private byte[] bytes(String fileName) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(DIR + fileName)) {
            assertThat(in).as("fixture " + fileName + " phải tồn tại trong test resources").isNotNull();
            return in.readAllBytes();
        }
    }
}
