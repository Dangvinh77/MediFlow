package com.mediflow.report.application.service;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.mapper.*;
import com.mediflow.report.application.port.in.*;
import com.mediflow.report.domain.model.CashReceipt;
import java.time.ZoneId;
import org.springframework.transaction.annotation.Transactional;

/** Validates producer evidence first; unsupported/deferred facts remain on the durable DLQ path. */
public class OutpatientReportFactReceiver implements ReceiveOutpatientReportFactUseCase {
    private final ApplyOperationalContributionUseCase operations;
    private final ApplyCashReceiptUseCase cash;
    private final ApplyCashRefundUseCase refunds;
    private final ClinicalOperationalContributionMapper clinical;
    private final LabOperationalContributionMapper lab;
    private final PrescriptionOperationalContributionMapper pharmacy;
    private final BillingCashReceiptMapper receipts;
    private final BillingCashRefundMapper refundMapper;

    public OutpatientReportFactReceiver(ApplyOperationalContributionUseCase operations,
            ApplyCashReceiptUseCase cash, ApplyCashRefundUseCase refunds, ZoneId zone) {
        this.operations = operations; this.cash = cash; this.refunds = refunds;
        clinical = new ClinicalOperationalContributionMapper(zone);
        lab = new LabOperationalContributionMapper(zone);
        pharmacy = new PrescriptionOperationalContributionMapper(zone);
        receipts = new BillingCashReceiptMapper(zone);
        refundMapper = new BillingCashRefundMapper(zone);
    }

    @Override @Transactional("reportTransactionManager")
    public void receive(DecodedCareFinanceEvent event) {
        switch (event.metadata().eventType()) {
            case "medicalrecord.completed" -> {
                var command = clinical.map(event);
                if (java.util.Set.of("ADMISSION", "TRANSFER").contains(command.contributions().getFirst().category()))
                    throw deferred();
                operations.apply(command);
            }
            case "lab.result.created", "prescription.filled" -> {
                var command = event.metadata().eventType().equals("lab.result.created") ? lab.map(event) : pharmacy.map(event);
                if (command.contributions().stream().anyMatch(f -> !"OUTPATIENT_VISIT".equals(f.careEpisodeType())))
                    throw deferred();
                operations.apply(command);
            }
            case "payment.completed" -> {
                var receipt = receipts.map(event);
                if (!"OUTPATIENT_VISIT".equals(receipt.careEpisodeType())
                        || receipt.classification() != CashReceipt.Classification.SERVICE_PAYMENT) throw deferred();
                cash.apply(event); // Existing receipt/operation dedupe and early refund recovery are reused.
            }
            case "payment.refunded" -> {
                if (!"OUTPATIENT_VISIT".equals(refundMapper.map(event).careEpisodeType())) throw deferred();
                refunds.apply(event); // Original linkage/partial-refund guards remain in the atomic kernel.
            }
            default -> throw deferred();
        }
    }

    private IllegalArgumentException deferred() {
        return new IllegalArgumentException("Unsupported or deferred outpatient report fact");
    }
}
