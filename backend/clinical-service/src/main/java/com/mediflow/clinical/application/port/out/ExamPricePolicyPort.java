package com.mediflow.clinical.application.port.out;

import java.util.UUID;

public interface ExamPricePolicyPort {
    String resolvePriceCode(UUID departmentId);
}
