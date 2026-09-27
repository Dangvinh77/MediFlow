package com.mediflow.inpatient.application.port.in;

import com.mediflow.common.api.PageResult;
import com.mediflow.inpatient.application.dto.query.BedSearchQuery;
import com.mediflow.inpatient.application.dto.request.AssignBedRequest;
import com.mediflow.inpatient.application.dto.request.CreateBedRequest;
import com.mediflow.inpatient.application.dto.request.ReleaseBedRequest;
import com.mediflow.inpatient.application.dto.request.TransferBedRequest;
import com.mediflow.inpatient.application.dto.request.UpdateBedRequest;
import com.mediflow.inpatient.application.dto.response.AdmissionDTO;
import com.mediflow.inpatient.application.dto.response.BedDTO;

import java.util.UUID;

public interface ManageBedUseCase {
    BedDTO create(CreateBedRequest request);
    BedDTO update(UUID bedId, UpdateBedRequest request);
    PageResult<BedDTO> search(BedSearchQuery query);
    AdmissionDTO assign(UUID admissionId, AssignBedRequest request, String correlationId);
    AdmissionDTO transfer(UUID admissionId, TransferBedRequest request, String correlationId);
    AdmissionDTO release(UUID admissionId, ReleaseBedRequest request, String correlationId);
}
