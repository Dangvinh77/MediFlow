package com.mediflow.billing.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.port.out.ClinicalPaymentContextPort;
import com.mediflow.billing.application.port.out.ClinicalPaymentLockPort;
import com.mediflow.billing.domain.model.ScheduledPaymentLease;
import com.mediflow.billing.domain.model.ScheduledPaymentOperation;
import com.mediflow.billing.infrastructure.security.BillingServiceTokenIssuer;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public final class ClinicalPaymentLockHttpAdapter implements ClinicalPaymentLockPort, ClinicalPaymentContextPort {
    private static final ParameterizedTypeReference<ApiResponse<ScheduledPaymentLease>> LEASE_RESPONSE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<PaymentContext>> CONTEXT_RESPONSE =
            new ParameterizedTypeReference<>() { };
    private final RestClient rest;
    private final BillingServiceTokenIssuer tokens;
    private final ObjectMapper mapper;

    public ClinicalPaymentLockHttpAdapter(RestClient.Builder builder, BillingServiceTokenIssuer tokens,
            ObjectMapper mapper, @Value("${mediflow.clients.clinical-service:http://clinical-service:8082}") String baseUrl) {
        var requestFactory = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.rest = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.tokens = tokens;
        this.mapper = mapper;
    }

    @Override
    public ScheduledPaymentLease acquire(ScheduledPaymentOperation operation) {
        var command = new PaymentLockCommand(operation.paymentOperationId(), operation.paymentRequestId(),
                operation.registrationId(), operation.patientId(), operation.sourceType(), operation.sourceId(),
                operation.careEpisodeId(), operation.expectedReservationRevision(), operation.basis(), operation.quoteId());
        try {
            var response = rest.post().uri("/api/v2/clinical/reservations/{id}/payment-locks", operation.reservationId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue())
                    .header(JwtClaims.HEADER_CORRELATION_ID, operation.correlationId())
                    .body(command).retrieve().body(LEASE_RESPONSE);
            if (response == null || !response.success() || response.data() == null) {
                throw new PaymentAuthorityUnavailableException("Clinical payment lock returned no lease receipt");
            }
            return response.data();
        } catch (RestClientResponseException failure) {
            String code = errorCode(failure);
            if (failure.getStatusCode().value() == 409 || failure.getStatusCode().value() == 404) {
                throw new PaymentAcquireRejectedException(code, "Clinical refused the payment lock");
            }
            throw new PaymentAuthorityUnavailableException("Clinical payment lock is unavailable", failure);
        } catch (ResourceAccessException failure) {
            throw new PaymentAuthorityUnavailableException("Clinical payment lock is unavailable", failure);
        }
    }

    @Override
    public void resolve(PaymentResolution resolution) {
        var command = new PaymentResolutionCommand(resolution.paymentOperationId(), resolution.paymentRequestId(),
                resolution.registrationId(), resolution.reservationId(), resolution.fenceToken(),
                    resolution.outcome(), resolution.transactionId(), resolution.clearanceId(), resolution.completedAt(), resolution.basis(), resolution.quoteId());
        try {
            var response = rest.put().uri("/api/v2/clinical/reservations/{id}/payment-resolution", resolution.reservationId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue())
                    .header(JwtClaims.HEADER_CORRELATION_ID, resolution.correlationId())
                    .body(command).retrieve().body(new ParameterizedTypeReference<ApiResponse<JsonNode>>() { });
            if (response == null || !response.success()) {
                throw new PaymentAuthorityUnavailableException("Clinical payment resolution was not acknowledged");
            }
        } catch (RestClientResponseException | ResourceAccessException failure) {
            throw new PaymentAuthorityUnavailableException("Clinical payment resolution is unavailable", failure);
        }
    }

    @Override
    public PaymentContext get(UUID reservationId) {
        try {
            var response = rest.get().uri("/api/v2/clinical/reservations/{id}/payment-context", reservationId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue())
                    .header(JwtClaims.HEADER_CORRELATION_ID, UUID.randomUUID().toString())
                    .retrieve().body(CONTEXT_RESPONSE);
            if (response == null || !response.success() || response.data() == null) {
                throw new PaymentContextUnavailableException("Clinical payment context returned no data");
            }
            return response.data();
        } catch (RestClientResponseException | ResourceAccessException failure) {
            throw new PaymentContextUnavailableException("Clinical payment context is unavailable", failure);
        }
    }

    private String errorCode(RestClientResponseException failure) {
        try {
            JsonNode root = mapper.readTree(failure.getResponseBodyAsByteArray());
            JsonNode code = root.path("error").path("code");
            if (code.isTextual()) return code.asText();
        } catch (Exception ignored) { }
        return failure.getStatusCode().value() == 404 ? "RESOURCE_NOT_FOUND" : "CLINICAL_PAYMENT_LOCK_REJECTED";
    }

    public record PaymentLockCommand(UUID paymentOperationId, UUID paymentRequestId, UUID registrationId,
            UUID patientId, String sourceType, UUID sourceId, UUID careEpisodeId, long expectedReservationRevision, String basis, UUID quoteId) {
    public PaymentLockCommand(UUID paymentOperationId, UUID paymentRequestId, UUID registrationId,
            UUID patientId, String sourceType, UUID sourceId, UUID careEpisodeId, long expectedReservationRevision) { this(paymentOperationId, paymentRequestId, registrationId, patientId, sourceType, sourceId, careEpisodeId, expectedReservationRevision, "PATIENT_PAYMENT", null); }
 }

    public record PaymentResolutionCommand(UUID paymentOperationId, UUID paymentRequestId, UUID registrationId,
            UUID reservationId, long fenceToken, String outcome, UUID transactionId, UUID clearanceId,
            java.time.Instant completedAt, String basis, UUID quoteId) {
    public PaymentResolutionCommand(UUID paymentOperationId, UUID paymentRequestId, UUID registrationId,
            UUID reservationId, long fenceToken, String outcome, UUID transactionId, UUID clearanceId,
            java.time.Instant completedAt) { this(paymentOperationId, paymentRequestId, registrationId, reservationId, fenceToken, outcome, transactionId, clearanceId, completedAt, "PATIENT_PAYMENT", null); }
 }
}
