package com.mediflow.pharmacy.application.port.in;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import java.util.UUID;

public interface SearchPrescriptionsUseCase {
    default PageResult<PrescriptionDTO> search(String keyword, UUID patientId, String status, PageQuery page) {
        return search(keyword, patientId, null, status, page);
    }

    /** @param doctorId when present, only prescriptions written by this staff member */
    PageResult<PrescriptionDTO> search(String keyword, UUID patientId, UUID doctorId, String status, PageQuery page);
}
