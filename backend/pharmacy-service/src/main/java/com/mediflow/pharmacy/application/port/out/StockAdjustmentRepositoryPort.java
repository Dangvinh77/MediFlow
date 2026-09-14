package com.mediflow.pharmacy.application.port.out;

import com.mediflow.pharmacy.domain.model.StockAdjustment;

/** Out-port for durable stock mutation audit records. */
public interface StockAdjustmentRepositoryPort {

    /** Persists one adjustment in the caller's transaction. */
    StockAdjustment save(StockAdjustment adjustment);
}
