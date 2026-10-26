package com.mediflow.clinical.infrastructure.client;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.dto.authority.ScheduledPaymentSnapshot;
import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.clinical.application.port.out.ScheduledPaymentReconciliationPort;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;

/** Validates Billing's envelope and correlation before exposing a payment receipt to recovery. */
@Component
public class ClinicalScheduledPaymentAdapter implements ScheduledPaymentReconciliationPort {
    private final ClinicalScheduledPaymentFeignClient client;

    public ClinicalScheduledPaymentAdapter(ClinicalScheduledPaymentFeignClient client) {
        this.client = client;
    }

    @Override
    public ScheduledPaymentSnapshot lookup(UUID paymentOperationId, UUID registrationId, UUID reservationId,
            String correlationId) {
        requireRequest(paymentOperationId, registrationId, reservationId, correlationId);
        try {
            return requireData(client.lookup(paymentOperationId, registrationId, reservationId, correlationId));
        } catch (UpstreamUnavailableException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw unavailable(failure);
        }
    }

    @Override
    public ScheduledPaymentSnapshot closeIfUnpaid(UUID paymentOperationId, UUID registrationId, UUID reservationId,
            long fenceToken, String reason, String correlationId) {
        requireRequest(paymentOperationId, registrationId, reservationId, correlationId);
        if (fenceToken < 1 || !"RESERVATION_RECONCILIATION".equals(reason)) {
            throw new IllegalArgumentException("The exact Clinical recovery fence and reason are required");
        }
        try {
            return requireData(client.closeIfUnpaid(paymentOperationId,
                    new ClinicalScheduledPaymentFeignClient.CloseIfUnpaidRequest(registrationId, reservationId,
                            fenceToken, reason), correlationId));
        } catch (UpstreamUnavailableException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw unavailable(failure);
        }
    }

    private static ScheduledPaymentSnapshot requireData(
            ResponseEntity<ApiResponse<ScheduledPaymentSnapshot>> response) {
        if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw unavailable("Billing returned no successful reconciliation response");
        }
        ApiResponse<ScheduledPaymentSnapshot> envelope = response.getBody();
        String headerCorrelation = response.getHeaders().getFirst(JwtClaims.HEADER_CORRELATION_ID);
        if (!envelope.success() || envelope.error() != null || envelope.data() == null
                || envelope.correlationId() == null || envelope.correlationId().isBlank()
                || envelope.correlationId().length() > 120 || !envelope.correlationId().equals(headerCorrelation)) {
            throw unavailable("Billing returned a malformed reconciliation response envelope");
        }
        return envelope.data();
    }

    private static void requireRequest(UUID operationId, UUID registrationId, UUID reservationId,
            String correlationId) {
        if (operationId == null || registrationId == null || reservationId == null
                || correlationId == null || correlationId.isBlank() || correlationId.length() > 120) {
            throw new IllegalArgumentException("An exact payment operation binding and correlation are required");
        }
    }

    private static UpstreamUnavailableException unavailable(String message) {
        return new UpstreamUnavailableException("BILLING_PAYMENT_RECONCILIATION_UNAVAILABLE", message);
    }

    private static UpstreamUnavailableException unavailable(Throwable cause) {
        return new UpstreamUnavailableException("BILLING_PAYMENT_RECONCILIATION_UNAVAILABLE",
                "Billing payment reconciliation is unavailable", cause);
    }
}
