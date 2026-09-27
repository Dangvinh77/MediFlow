package com.mediflow.lab.application.port.in;

import java.util.List;
import java.util.UUID;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.lab.application.dto.request.AddResultRequest;
import com.mediflow.lab.application.dto.request.CancelLabTestRequest;
import com.mediflow.lab.application.dto.request.CreateLabRequest;
import com.mediflow.lab.application.dto.request.StartLabTestRequest;
import com.mediflow.lab.application.dto.response.LabTestDTO;
import com.mediflow.lab.domain.model.CareEpisodeType;
import com.mediflow.lab.domain.model.LabTestStatus;

/** Use cases exposed to HTTP and other driving adapters. */
public interface ManageLabTestUseCase {

    LabTestDTO create(CreateLabRequest request);

    LabTestDTO getById(UUID id);

    List<LabTestDTO> byPatient(UUID patientId);

    default PageResult<LabTestDTO> search(UUID departmentId, LabTestStatus status, PageQuery page) {
        return search(departmentId, status, null, null, page);
    }

    PageResult<LabTestDTO> search(UUID departmentId, LabTestStatus status,
                                  CareEpisodeType episodeType, UUID episodeId, PageQuery page);

    LabTestDTO start(UUID id, StartLabTestRequest request);

    LabTestDTO cancel(UUID id, CancelLabTestRequest request);

    LabTestDTO addResults(UUID id, AddResultRequest request);

    LabTestDTO changeStatus(UUID id, LabTestStatus status);
}
