package com.mediflow.pharmacy.application.port.out;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;
import java.util.UUID;

/** Bounded prescription discovery within the Pharmacy store. */
public interface PrescriptionSearchPort {
    default PageResult<UUID> search(String keyword, UUID patientId, PrescriptionStatus status, PageQuery page) {
        return search(keyword, patientId, null, status, page);
    }

    PageResult<UUID> search(String keyword, UUID patientId, UUID doctorId, PrescriptionStatus status, PageQuery page);
}
