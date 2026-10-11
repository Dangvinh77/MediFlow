package com.mediflow.report.application.port.in;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;

public interface ApplySettlementSnapshotUseCase { void apply(DecodedCareFinanceEvent event); }
