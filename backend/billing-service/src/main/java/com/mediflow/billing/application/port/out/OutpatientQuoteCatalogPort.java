package com.mediflow.billing.application.port.out;

import com.mediflow.billing.domain.model.OutpatientQuoteLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface OutpatientQuoteCatalogPort {
    OutpatientQuoteLine requireLine(UUID sourceId, String sourceType, String priceCode, BigDecimal quantity, Instant at);
}
