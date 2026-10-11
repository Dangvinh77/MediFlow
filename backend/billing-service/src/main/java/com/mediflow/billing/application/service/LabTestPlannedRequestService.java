package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.LabTestChargeCommand;
import com.mediflow.billing.application.port.in.IssueLabTestChargeUseCase;
import com.mediflow.billing.application.port.out.LabTestChargeRepositoryPort;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.Charge;
import java.math.BigDecimal;
import java.util.UUID;

/** CONTRACT-CARE-BILLING-01 — one {@code lab.request.created} fact issues one LAB_TEST request.
 * Transaction wrapper is infrastructure-owned. Replays never re-price the original operation. */
public final class LabTestPlannedRequestService implements IssueLabTestChargeUseCase {
    private final LabTestChargeRepositoryPort repository;
    private final PriceCatalogPort prices;

    public LabTestPlannedRequestService(LabTestChargeRepositoryPort repository, PriceCatalogPort prices) {
        this.repository = repository;
        this.prices = prices;
    }

    @Override
    public UUID issue(LabTestChargeCommand command) {
        repository.claimDelivery(command);
        var recorded = repository.lockRecordedSource(command);
        if (recorded.isPresent()) {
            return recorded.get();
        }
        UUID account = repository.openAndLockExactAccount(command);
        var price = prices.requireActive(command.priceCode(), command.requestedAt());
        require(price != null && price.description() != null && !price.description().isBlank()
                && price.description().length() <= 255 && money(price.unitAmount())
                && price.unitAmount().signum() >= 0, "BILLING_PRICE_SNAPSHOT_INVALID");
        Charge charge = Charge.post(account, command.patientId(), command.departmentId(), "LAB_TEST",
                command.labId(), command.priceCode(), price.description(), BigDecimal.ONE,
                price.unitAmount(), command.requestedAt());
        require(money(charge.getGrossAmount()) && charge.getGrossAmount().signum() > 0,
                "BILLING_PAYMENT_REQUEST_INVALID");
        var issued = repository.saveChargeAndRequest(command, account, charge);
        return issued.paymentRequestId();
    }

    private static boolean money(BigDecimal amount) {
        return amount != null && amount.stripTrailingZeros().scale() <= 2 && amount.precision() - amount.scale() <= 17;
    }
    private static void require(boolean valid, String code) { if (!valid) throw new BillingRuleException(code, code); }
}
