package com.mediflow.pharmacy.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.pharmacy.application.dto.command.PrescriptionRevisionBillingPayload;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.pharmacy.domain.model.enums.CareContext;
import com.mediflow.pharmacy.domain.model.enums.CareEpisodeType;
import com.mediflow.pharmacy.infrastructure.security.JwtProperties;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PharmacyBillingPrescriptionRevisionAdapterTest {
    private final PharmacyBillingRevisionFeignClient client = mock(PharmacyBillingRevisionFeignClient.class);
    private final PharmacyBillingPrescriptionRevisionAdapter adapter = new PharmacyBillingPrescriptionRevisionAdapter(
            client, new ObjectMapper(), Clock.fixed(Instant.parse("2026-11-14T01:20:00Z"), ZoneOffset.UTC),
            new JwtProperties("test-secret-key-for-unit-tests-32bytes-minimum!"));
    private final UUID prescriptionId = UUID.randomUUID();
    private final PrescriptionRevisionBillingPayload payload = new PrescriptionRevisionBillingPayload(
            prescriptionId, UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(),
            CareContext.OUTPATIENT, CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(),
            "PRESCRIPTION", prescriptionId, "RX-DEMO",
            List.of(new PrescriptionRevisionBillingPayload.Item(UUID.randomUUID(), "Drug A", 10, new BigDecimal("10000"))),
            new BigDecimal("100000"), 1, 2);

    @Test
    void paymentRefusalWrappedByTheCircuitBreakerStaysABusinessRejection() {
        FeignException refused = answer(422, """
                {"success":false,"data":null,"error":{"code":"BILLING_PRESCRIPTION_REVISION_PAYMENT_EXISTS","message":"BILLING_PRESCRIPTION_REVISION_PAYMENT_EXISTS","details":[]}}
                """);
        when(client.prepare(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("No fallback available.", refused));

        assertThatThrownBy(() -> adapter.prepare(UUID.randomUUID(), payload, "corr-1"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getCode())
                                .isEqualTo("BILLING_PRESCRIPTION_REVISION_PAYMENT_EXISTS"));
    }

    @Test
    void wrappedServerFailureStaysUnavailable() {
        when(client.prepare(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("No fallback available.", answer(503, "{}")));

        assertThatThrownBy(() -> adapter.prepare(UUID.randomUUID(), payload, "corr-2"))
                .isInstanceOf(PharmacyUpstreamUnavailableException.class);
    }

    @Test
    void connectionFailureWithoutAnHttpAnswerStaysUnavailable() {
        when(client.prepare(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> adapter.prepare(UUID.randomUUID(), payload, "corr-3"))
                .isInstanceOf(PharmacyUpstreamUnavailableException.class);
    }

    private static FeignException answer(int status, String body) {
        Request request = Request.create(Request.HttpMethod.POST, "http://billing/prepare", Map.of(),
                new byte[0], StandardCharsets.UTF_8, null);
        Response response = Response.builder().status(status).reason("x").request(request).headers(Map.of())
                .body(body, StandardCharsets.UTF_8).build();
        return FeignException.errorStatus("prepare", response);
    }
}
