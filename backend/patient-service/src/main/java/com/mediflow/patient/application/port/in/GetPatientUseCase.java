package com.mediflow.patient.application.port.in;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.patient.application.dto.response.PatientDTO;

import java.util.UUID;

public interface GetPatientUseCase {

    PatientDTO getById(UUID patientId);

    PageResult<PatientDTO> search(String keyword, PageQuery page);
}
