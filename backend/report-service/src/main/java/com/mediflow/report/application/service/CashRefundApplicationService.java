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
    @Override @Transactional(transactionManager = "reportTransactionManager", timeout = 5)
    public void apply(DecodedCareFinanceEvent event) {
        var refund = mapper.map(event);
        // All financial writers acquire account before original, including final recognition.
        store.lockAccount(refund.accountId()); store.lockOriginal(refund.originalTransactionId());
        if (!store.record(event, refund)) return;
        var original = store.original(refund.originalTransactionId());
        if (original.isEmpty()) {
            requireSupportedAccount(refund.accountId());
            return; // Durable PENDING, no department/date/classification invention.
        }
        refund.verifyOriginal(original.get(), store.appliedTotal(refund.originalTransactionId()));
        requireSupportedAccount(refund.accountId());
        store.apply(refund, original.get());
    }
    @Override @Transactional(transactionManager = "reportTransactionManager", timeout = 5)
    public void recover(UUID originalId) {
        // Original identity is immutable. Resolve it before locking, then re-read under both
        // fences; acquiring account after original would deadlock against the final writer.
        var identity = store.original(originalId); if (identity.isEmpty()) return;
        store.lockAccount(identity.get().accountId()); store.lockOriginal(originalId);
        var original = store.original(originalId); if (original.isEmpty()) return;
        for (var refund : store.pending(originalId, 20)) {
            try {
                refund.verifyOriginal(original.get(), store.appliedTotal(originalId));
                requireSupportedAccount(original.get().accountId());
            }
            catch (ReportRuleException invalid) {
                // Early invalid evidence was already ACKed. Quarantine it durably, do not poison its valid receipt.
                store.reject(refund.refundTransactionId(), invalid.getCode()); continue;
            }
            store.apply(refund, original.get());
        }
    }
    private void requireSupportedAccount(UUID accountId) {
        if (store.finalized(accountId)) {
            throw new ReportRuleException("CASH_REFUND_FINAL_CORRECTION_UNSUPPORTED", "A final earned correction requires explicit original allocation evidence");
        }
    }
}
