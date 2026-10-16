package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.PrescriptionChargeCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.port.in.IssuePrescriptionChargeUseCase;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.PrescriptionChargeRepositoryPort;
import com.mediflow.billing.application.port.out.PrescriptionPricePolicyPort;
import com.mediflow.billing.application.port.out.PrescriptionChargeRepositoryPort.IssuedCharge;
import com.mediflow.billing.domain.model.Charge;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

/** CARE-BILLING-01: exact bundle snapshot; transaction wrapper belongs to infrastructure. */
public final class PrescriptionChargeService implements IssuePrescriptionChargeUseCase {
    private final PrescriptionChargeRepositoryPort repository;
    private final LedgerEventPort events;
    private final Clock clock;
    private final PrescriptionPricePolicyPort prices;

    public PrescriptionChargeService(PrescriptionChargeRepositoryPort repository, LedgerEventPort events, Clock clock, PrescriptionPricePolicyPort prices) {
        this.repository = java.util.Objects.requireNonNull(repository); this.events = java.util.Objects.requireNonNull(events);
        this.clock = java.util.Objects.requireNonNull(clock); this.prices = java.util.Objects.requireNonNull(prices);
    }
    @Override public IssuedCharge issue(PrescriptionChargeCommand command) {
        repository.claimDelivery(command);
        var previous = repository.lockRecordedSource(command);
        if (previous.isPresent()) return previous.get();
        prices.requireAllowed(command.priceCode());
        UUID account = repository.openAndLockExactAccount(command);
        var charge = Charge.post(account, command.patientId(), command.departmentId(), "PRESCRIPTION",
                command.prescriptionId(), command.priceCode(), "Prescription medication bundle",
                BigDecimal.ONE, command.totalAmount(), command.createdAt());
        var now = clock.instant();
        var issued = repository.saveChargeAndRequest(command, account, charge, now);
        if ("OUTPATIENT".equals(command.careContext())) {
            events.appendForOutpatientRequest(account, issued.paymentRequestId(), new LedgerIntegrationEvent(UUID.randomUUID(), "invoice.created", 1, now,
                    command.correlationId(), "billing-service", new LedgerIntegrationEvent.PrescriptionPaymentRequestPayload(
                        issued.invoiceId(), issued.paymentRequestId(), account, command.patientId(), command.departmentId(),
                        command.careEpisodeType(), command.careEpisodeId(), "PRESCRIPTION", command.prescriptionId(),
                        command.totalAmount(), "VND", now, null)));
        }
        return issued;
    }
}
