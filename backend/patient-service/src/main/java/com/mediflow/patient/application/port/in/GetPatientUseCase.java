package com.mediflow.patient.application.port.in;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.patient.application.dto.response.PatientDTO;

import java.util.UUID;

/** Tra cứu bệnh nhân. Đây là đường nóng của service — hầu hết lưu lượng rơi vào đây. */
public interface GetPatientUseCase {

    PatientDTO getById(UUID maBenhNhan);

    PageResult<PatientDTO> search(String keyword, PageQuery pageQuery);
}
