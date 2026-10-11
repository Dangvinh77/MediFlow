package com.mediflow.pharmacy.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Same producer-owned bytes as Billing's real issuer/two-installment service tests. */
class BillingPrescriptionIssuanceFixtureTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    @Test void decode_issuedPrescriptionFullPayment_grantMatchesOriginalRxOnly() throws Exception {
        try (var source = getClass().getResourceAsStream("/contracts/care-finance-v1/prescription.created.outpatient.v1.json")) {
            assertThat(source).isNotNull(); var creation = new PrescriptionCareEventCodec(mapper).decode("prescription.created", source.readAllBytes());
            byte[] body = Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/clearance-prescription-issued.json"));
            var grant = new PrescriptionClearanceDecoder(mapper).decode("financial.clearance.granted", body).clearance();
            assertThat(grant.prescriptionId()).isEqualTo(creation.payload().prescriptionId());
            assertThat(grant.patientId()).isEqualTo(creation.payload().patientId());
            assertThat(grant.episode().id()).isEqualTo(creation.payload().careEpisodeId());
            assertThat(grant.amount()).isEqualByComparingTo(creation.payload().totalAmount());
            assertThat(mapper.readTree(body).path("payload").path("recordId").isNull()).isTrue();
        }
    }
    @Test void decode_installmentReceipt_isNotDispenseAuthority() throws Exception {
        var decoder = new PrescriptionClearanceDecoder(mapper);
        for (String name : java.util.List.of("payment-prescription-first", "payment-prescription-last", "invoice-prescription")) {
            byte[] body = Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/" + name + ".json"));
            assertThatThrownBy(() -> decoder.decode("financial.clearance.granted", body)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
