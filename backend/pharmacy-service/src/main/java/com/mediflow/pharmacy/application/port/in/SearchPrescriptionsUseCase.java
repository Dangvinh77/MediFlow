package com.mediflow.pharmacy.application.port.in;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import java.util.UUID;

public interface SearchPrescriptionsUseCase {
    PageResult<PrescriptionDTO> search(String keyword, UUID patientId, String status, PageQuery page);
}
