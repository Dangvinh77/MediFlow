package com.mediflow.report.application.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.mapper.BillingCashRefundMapper;
import com.mediflow.report.application.port.in.ApplyCashRefundUseCase;
import com.mediflow.report.application.port.out.CashRefundStorePort;
import com.mediflow.report.domain.exception.ReportRuleException;

@Service
public class CashRefundApplicationService implements ApplyCashRefundUseCase {
    private final CashRefundStorePort store;
    private final BillingCashRefundMapper mapper;
    public CashRefundApplicationService(CashRefundStorePort store, BillingCashRefundMapper mapper) { this.store = store; this.mapper = mapper; }
    @Override @Transactional(timeout = 5)
    public void apply(DecodedCareFinanceEvent event) {
        var refund = mapper.map(event); store.lockOriginal(refund.originalTransactionId());
        if (!store.record(event, refund)) return;
        var original = store.original(refund.originalTransactionId());
        if (original.isEmpty()) return; // Durable PENDING, no department/date/classification invention.
        refund.verifyOriginal(original.get(), store.appliedTotal(refund.originalTransactionId()));
        store.apply(refund, original.get());
    }
    @Override @Transactional(timeout = 5)
    public void recover(UUID originalId) {
        store.lockOriginal(originalId);
        var original = store.original(originalId); if (original.isEmpty()) return;
        for (var refund : store.pending(originalId, 20)) {
            try { refund.verifyOriginal(original.get(), store.appliedTotal(originalId)); }
            catch (ReportRuleException invalid) {
                // Early invalid evidence was already ACKed. Quarantine it durably, do not poison its valid receipt.
                store.reject(refund.refundTransactionId(), invalid.getCode()); continue;
            }
            store.apply(refund, original.get());
        }
    }
}
