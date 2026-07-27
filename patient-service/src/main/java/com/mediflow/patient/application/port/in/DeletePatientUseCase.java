package com.mediflow.patient.application.port.in;

import java.util.UUID;

/** Xóa bệnh nhân. Chỉ ADMIN — xem docs/ai/services/patient.md. */
public interface DeletePatientUseCase {

    void delete(UUID maBenhNhan);
}
