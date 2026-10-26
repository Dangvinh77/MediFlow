package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.response.RegistrationPaymentRequestDTO;
import com.mediflow.billing.application.port.in.DiscoverRegistrationPaymentRequestUseCase;
import com.mediflow.billing.application.port.out.ClinicalPaymentContextPort;
import com.mediflow.billing.application.port.out.OutpatientRegistrationSourcePort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.exception.ScheduledPaymentRequestNotReadyException;
import java.util.UUID;

public final class DiscoverRegistrationPaymentRequestService implements DiscoverRegistrationPaymentRequestUseCase {
    private final OutpatientRegistrationSourcePort sources;
    private final ClinicalPaymentContextPort clinical;

    public DiscoverRegistrationPaymentRequestService(OutpatientRegistrationSourcePort sources,
            ClinicalPaymentContextPort clinical) {
        this.sources = sources;
        this.clinical = clinical;
    }

    @Override
    public RegistrationPaymentRequestDTO discover(UUID registrationId) {
        if (registrationId == null) throw new BillingRuleException("INVALID_REQUEST", "Registration id is required");
        var snapshot = sources.findPaymentRequestByRegistration(registrationId)
                .orElseThrow(() -> new ScheduledPaymentRequestNotReadyException(registrationId));
        var context = clinical.get(snapshot.reservationId());
        if (context == null) throw new ClinicalPaymentContextPort.PaymentContextUnavailableException(
                "Clinical returned an empty payment context");
        if (!snapshot.reservationId().equals(context.reservationId())
                || !snapshot.registrationId().equals(context.registrationId())
                || !snapshot.patientId().equals(context.patientId()) || context.reservationRevision() <= 0
                || context.reservationStatus() == null || context.expiresAt() == null) {
            throw new BillingRuleException("BILLING_PAYMENT_CONTEXT_CONFLICT",
                    "Clinical payment context does not match the Billing registration binding");
        }
        return new RegistrationPaymentRequestDTO(snapshot.paymentRequestId(), snapshot.invoiceId(), snapshot.accountId(),
                snapshot.registrationId(), snapshot.reservationId(), context.reservationRevision(), snapshot.patientId(),
                snapshot.departmentId(), snapshot.sourceType(), snapshot.sourceId(), snapshot.careEpisodeType(),
                snapshot.careEpisodeId(), snapshot.requestedAmount(), snapshot.paidAmount(), snapshot.remainingAmount(),
                snapshot.currency(), snapshot.status(), snapshot.expiresAt(), snapshot.observedAt());
    }
}
