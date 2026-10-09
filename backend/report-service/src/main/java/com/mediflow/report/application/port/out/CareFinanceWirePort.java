package com.mediflow.report.application.port.out;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;

/** Strict source-owned envelope decoding, without transport types crossing the boundary. */
public interface CareFinanceWirePort {
    DecodedCareFinanceEvent decode(String routingKey, byte[] body);
}
