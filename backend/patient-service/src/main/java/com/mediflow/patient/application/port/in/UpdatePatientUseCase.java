package com.mediflow.patient.application.port.in;

import com.mediflow.patient.application.dto.request.UpdatePatientRequest;
import com.mediflow.patient.application.dto.response.PatientDTO;

import java.util.UUID;

/** Cập nhật thông tin bệnh nhân. Số CMND/CCCD không nằm trong phạm vi sửa được. */
public interface UpdatePatientUseCase {

    PatientDTO update(UUID maBenhNhan, UpdatePatientRequest request);
}
