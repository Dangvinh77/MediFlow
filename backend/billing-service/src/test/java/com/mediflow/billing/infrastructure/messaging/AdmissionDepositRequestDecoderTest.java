package com.mediflow.billing.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.dto.command.AdmissionDepositRequestCommand;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Billing phải đọc đúng byte producer của inpatient-service — không copy/đổi tên field
 * (HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT). Đọc trực tiếp bản sao fixture thật tại
 * {@code src/test/resources/contracts/admission-deposit-v1/}.
 */
class AdmissionDepositRequestDecoderTest {
    private static final String DIR = "contracts/admission-deposit-v1/";
    private final AdmissionDepositRequestDecoder decoder = new AdmissionDepositRequestDecoder(new ObjectMapper());

    @Test
    void depositRequestedFixture_decodesExactSuggestedAmount() throws Exception {
        AdmissionDepositRequestCommand command = decoder.decode("admission.deposit.requested", bytes("admission.deposit.requested.v1.json"));

        assertThat(command.admissionId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000021"));
        assertThat(command.patientId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000022"));
        assertThat(command.careEpisodeType()).isEqualTo("ADMISSION");
        assertThat(command.careEpisodeId()).isEqualTo(command.admissionId());
        assertThat(command.priceCode()).isEqualTo("INPATIENT_DEPOSIT");
        assertThat(command.suggestedAmount()).isEqualByComparingTo(new BigDecimal("150000.00"));
        assertThat(command.reason()).isEqualTo("Initial admission deposit");
    }

    @Test
    void wrongRoutingKey_rejected() throws Exception {
        byte[] body = bytes("admission.deposit.requested.v1.json");
        assertThatThrownBy(() -> decoder.decode("admission.started", body)).isInstanceOf(IllegalArgumentException.class);
    }

    private byte[] bytes(String fileName) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(DIR + fileName)) {
            assertThat(in).as("fixture " + fileName + " phải tồn tại trong test resources").isNotNull();
            return in.readAllBytes();
        }
    }
}
