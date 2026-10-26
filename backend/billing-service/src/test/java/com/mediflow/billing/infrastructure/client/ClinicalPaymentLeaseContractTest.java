package com.mediflow.billing.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.billing.domain.model.ScheduledPaymentLease;
import com.mediflow.common.api.ApiResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClinicalPaymentLeaseContractTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void clinicalLeaseHttpPayloadCarriesEligibilityBasisAndQuoteBinding() throws Exception {
        UUID operationId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        String payload = """
                {"success":true,"data":{"paymentOperationId":"%s","reservationId":"%s",
                "reservationRevision":5,"fenceToken":12,"lockedAt":"2026-10-26T08:00:00Z",
                "reconcileAfter":"2026-10-26T08:02:00Z","basis":"ZERO_LIABILITY","quoteId":"%s"},
                "error":null,"timestamp":"2026-10-26T08:00:00Z","correlationId":"payment-trace"}
                """.formatted(operationId, reservationId, quoteId);

        ApiResponse<ScheduledPaymentLease> response = mapper.readValue(payload,
                new TypeReference<ApiResponse<ScheduledPaymentLease>>() { });

        assertThat(response.success()).isTrue();
        assertThat(response.data().basis()).isEqualTo("ZERO_LIABILITY");
        assertThat(response.data().quoteId()).isEqualTo(quoteId);
        assertThat(response.data().lockedAt()).isEqualTo(Instant.parse("2026-10-26T08:00:00Z"));
    }
}
