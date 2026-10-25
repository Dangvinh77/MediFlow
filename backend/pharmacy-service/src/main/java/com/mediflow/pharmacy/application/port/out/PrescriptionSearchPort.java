package com.mediflow.pharmacy.application.port.out;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;
import java.util.UUID;

/** Bounded prescription discovery within the Pharmacy store. */
public interface PrescriptionSearchPort {
    PageResult<UUID> search(String keyword, UUID patientId, PrescriptionStatus status, PageQuery page);
}
