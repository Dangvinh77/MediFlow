package com.mediflow.report.application.port.out;

import java.math.BigDecimal;
import java.util.*;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.domain.model.*;
public interface CashRefundStorePort {
    void lockOriginal(UUID originalTransactionId);
    boolean record(DecodedCareFinanceEvent event, CashRefund refund);
    Optional<CashReceipt> original(UUID originalTransactionId);
    BigDecimal appliedTotal(UUID originalTransactionId);
    List<CashRefund> pending(UUID originalTransactionId, int limit);
    void apply(CashRefund refund, CashReceipt original);
    void reject(UUID refundTransactionId, String reasonCode);
    List<UUID> readyOriginals(int limit);
    void defer(UUID originalTransactionId);
}
