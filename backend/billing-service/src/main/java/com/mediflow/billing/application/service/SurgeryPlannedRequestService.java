package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.SurgeryChargeCommand;
import com.mediflow.billing.application.port.in.IssueSurgeryChargeUseCase;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.application.port.out.SurgeryPlannedRequestRepositoryPort;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.Charge;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.UUID;
import java.time.Clock;

/** Transaction wrapper is infrastructure-owned. Replays never re-price the original operation. */
public final class SurgeryPlannedRequestService implements IssueSurgeryChargeUseCase {
    private final SurgeryPlannedRequestRepositoryPort repository;
    private final PriceCatalogPort prices;
    private final LedgerEventPort events;
    private final Clock clock;
    private final com.mediflow.billing.application.port.out.SurgeryCancellationRepositoryPort cancellations;
    public SurgeryPlannedRequestService(SurgeryPlannedRequestRepositoryPort repository, PriceCatalogPort prices, LedgerEventPort events, Clock clock) {
        this(repository, prices, events, clock, null);
    }
    public SurgeryPlannedRequestService(SurgeryPlannedRequestRepositoryPort repository, PriceCatalogPort prices, LedgerEventPort events, Clock clock,
            com.mediflow.billing.application.port.out.SurgeryCancellationRepositoryPort cancellations) {
        this.repository = repository; this.prices = prices; this.events = events; this.clock = clock; this.cancellations = cancellations;
    }
    @Override public UUID issue(SurgeryChargeCommand command) {
        repository.claimDelivery(command);
        var recorded = repository.lockRecordedSource(command);
        if (recorded.isPresent()) {
            if (cancellations != null) cancellations.recoverDuringIssuance(command.surgeryCaseId());
            return recorded.get();
        }
        UUID account = repository.openAndLockExactAccount(command);
        var quantities = new TreeMap<String, BigDecimal>();
        command.plannedItems().forEach(item -> quantities.merge(item.priceCode(), item.quantity(), BigDecimal::add));
        var charges = new ArrayList<Charge>(); BigDecimal total = BigDecimal.ZERO;
        for (var line : quantities.entrySet()) {
            var price = prices.requireActive(line.getKey(), command.requestedAt());
            require(price != null && price.description() != null && !price.description().isBlank()
                    && price.description().length() <= 255 && money(price.unitAmount())
                    && price.unitAmount().signum() >= 0, "BILLING_PRICE_SNAPSHOT_INVALID");
            require(line.getValue().precision() - line.getValue().scale() <= 15, "BILLING_CHARGE_INVALID_QUANTITY");
            var charge = Charge.post(account, command.patientId(), command.departmentId(), "SURGERY",
                    command.surgeryCaseId(), line.getKey(), price.description(), line.getValue(), price.unitAmount(), command.requestedAt());
            require(money(charge.getGrossAmount()), "BILLING_CHARGE_INVALID_AMOUNT");
            charges.add(charge); total = total.add(charge.getGrossAmount());
        }
        require(money(total) && total.signum() > 0, "BILLING_PAYMENT_REQUEST_INVALID");
        var issued = repository.saveChargesAndRequest(command, account, charges);
        // Early cancellation and issuance commit together: no transient payable request and no
        // unpaid invoice notice for a case already known cancelled. Historical replays remain exact.
        if (cancellations != null && cancellations.recoverDuringIssuance(command.surgeryCaseId())) return issued.paymentRequestId();
        var now = clock.instant();
        events.appendHeld(account, new LedgerIntegrationEvent(UUID.randomUUID(), "invoice.created", 1, now,
                command.correlationId(), "billing-service", new LedgerIntegrationEvent.SurgeryPaymentRequestPayload(
                    issued.invoiceId(), issued.paymentRequestId(), account, command.patientId(), command.departmentId(),
                    command.careEpisodeType(), command.careEpisodeId(), "SURGERY", command.surgeryCaseId(),
                    command.admissionId(), issued.requestedAmount(), "VND", now, null)));
        return issued.paymentRequestId();
    }
    private static boolean money(BigDecimal amount) {
        return amount != null && amount.stripTrailingZeros().scale() <= 2 && amount.precision() - amount.scale() <= 17;
    }
    private static void require(boolean valid, String code) { if (!valid) throw new BillingRuleException(code, code); }
}
