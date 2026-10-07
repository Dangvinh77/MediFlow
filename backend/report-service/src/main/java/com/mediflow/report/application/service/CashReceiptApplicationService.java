package com.mediflow.report.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.mapper.BillingCashReceiptMapper;
import com.mediflow.report.application.mapper.CashProjectionPlanner;
import com.mediflow.report.application.port.in.ApplyCashReceiptUseCase;
import com.mediflow.report.application.port.out.CashReceiptStorePort;

/** Gross receipt journal and both currency-separated scopes commit together; not a financial API. */
@Service
public class CashReceiptApplicationService implements ApplyCashReceiptUseCase {
    private final CashReceiptStorePort store;
    private final BillingCashReceiptMapper mapper;

    public CashReceiptApplicationService(CashReceiptStorePort store, BillingCashReceiptMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void apply(DecodedCareFinanceEvent event) {
        var receipt = mapper.map(event);
        if (store.record(event, receipt)) {
            for (var scope : CashProjectionPlanner.scopes(java.util.List.of(receipt))) {
                store.incrementGrossReceipts(scope.receipt(), scope.departmentId());
            }
        }
    }
}
