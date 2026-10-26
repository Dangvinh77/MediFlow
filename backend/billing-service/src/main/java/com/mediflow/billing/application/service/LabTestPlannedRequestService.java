package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.LabTestChargeCommand;
import com.mediflow.billing.application.port.in.IssueLabTestChargeUseCase;
import com.mediflow.billing.application.port.out.LabTestChargeRepositoryPort;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.application.port.out.OutpatientCancellationRepositoryPort;
import com.mediflow.billing.application.port.out.OutpatientQuoteCatalogPort;
import com.mediflow.billing.application.service.IssueOutpatientQuoteService;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.Charge;
import com.mediflow.billing.domain.model.OutpatientQuoteLine;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** CONTRACT-CARE-BILLING-01 — one {@code lab.request.created} fact issues one LAB_TEST request.
 * Transaction wrapper is infrastructure-owned. Replays never re-price the original operation. */
public final class LabTestPlannedRequestService implements IssueLabTestChargeUseCase {
    private final LabTestChargeRepositoryPort repository;
    private final PriceCatalogPort prices;
    private final OutpatientCancellationRepositoryPort cancellations;
    private final OutpatientQuoteCatalogPort quoteCatalog;
    private final IssueOutpatientQuoteService quoteIssuer;
    private final boolean quotesEnabled;
    private final Clock clock;

    public LabTestPlannedRequestService(LabTestChargeRepositoryPort repository, PriceCatalogPort prices) {
        this(repository, prices, null, null, null, false, Clock.systemUTC());
    }

    public LabTestPlannedRequestService(LabTestChargeRepositoryPort repository, PriceCatalogPort prices,
            OutpatientCancellationRepositoryPort cancellations) {
        this(repository, prices, cancellations, null, null, false, Clock.systemUTC());
    }

    public LabTestPlannedRequestService(LabTestChargeRepositoryPort repository, PriceCatalogPort prices,
            OutpatientCancellationRepositoryPort cancellations, OutpatientQuoteCatalogPort quoteCatalog,
            IssueOutpatientQuoteService quoteIssuer, boolean quotesEnabled, Clock clock) {
        this.repository = repository;
        this.prices = prices;
        this.cancellations = cancellations;
        this.quoteCatalog = quoteCatalog;
        this.quoteIssuer = quoteIssuer;
        this.quotesEnabled = quotesEnabled;
        this.clock = clock;
    }

    @Override
    public UUID issue(LabTestChargeCommand command) {
        repository.claimDelivery(command);
        var recorded = repository.lockRecordedSource(command);
        if (recorded.isPresent()) {
            recoverCancellation(command.labId());
            return recorded.get();
        }
        UUID account = repository.openAndLockExactAccount(command);
        if (quotesEnabled && "OUTPATIENT_VISIT".equals(command.careEpisodeType())) {
            return issueQuotedOutpatient(command, account);
        }
        var price = prices.requireActive(command.priceCode(), command.requestedAt());
        require(price != null && price.description() != null && !price.description().isBlank()
                && price.description().length() <= 255 && money(price.unitAmount())
                && price.unitAmount().signum() >= 0, "BILLING_PRICE_SNAPSHOT_INVALID");
        Charge charge = Charge.post(account, command.patientId(), command.departmentId(), "LAB_TEST",
                command.labId(), command.priceCode(), price.description(), BigDecimal.ONE,
                price.unitAmount(), command.requestedAt());
        require(money(charge.getGrossAmount()) && charge.getGrossAmount().signum() > 0,
                "BILLING_PAYMENT_REQUEST_INVALID");
        var issued = repository.saveChargeAndRequest(command, account, charge, charge.getGrossAmount(), null);
        recoverCancellation(command.labId());
        return issued.paymentRequestId();
    }

    private UUID issueQuotedOutpatient(LabTestChargeCommand command, UUID account) {
        require(quoteCatalog != null && quoteIssuer != null, "BILLING_QUOTE_CONFIGURATION_REQUIRED");
        Instant issuedAt = clock.instant();
        OutpatientQuoteLine line = quoteCatalog.requireLine(command.labId(), "LAB_TEST", command.priceCode(),
                BigDecimal.ONE, issuedAt);
        require(line != null && line.sourceId().equals(command.labId()) && "LAB_TEST".equals(line.sourceType())
                && command.priceCode().equals(line.priceCode()) && line.quantity().compareTo(BigDecimal.ONE) == 0
                && line.description().length() <= 255 && money(line.unitPrice()) && money(line.grossAmount())
                && money(line.patientPayable()), "BILLING_QUOTE_BINDING_CONFLICT");
        Instant expiresAt = issuedAt.plus(Duration.ofMinutes(15));
        Charge charge = Charge.post(account, command.patientId(), command.departmentId(), "LAB_TEST",
                command.labId(), command.priceCode(), line.description(), line.quantity(), line.unitPrice(), command.requestedAt());
        require(charge.getGrossAmount().compareTo(line.grossAmount()) == 0, "BILLING_QUOTE_BINDING_CONFLICT");
        var issued = repository.saveChargeAndRequest(command, account, charge, line.patientPayable(), expiresAt);
        quoteIssuer.issue(issued.paymentRequestId(), command.patientId(), command.careEpisodeId(), List.of(line), issuedAt, expiresAt);
        recoverCancellation(command.labId());
        return issued.paymentRequestId();
    }

    private void recoverCancellation(UUID labId) {
        if (cancellations != null) cancellations.recoverDuringIssuance("LAB_TEST", labId);
    }

    private static boolean money(BigDecimal amount) {
        return amount != null && amount.stripTrailingZeros().scale() <= 2 && amount.precision() - amount.scale() <= 17;
    }
    private static void require(boolean valid, String code) { if (!valid) throw new BillingRuleException(code, code); }
}
