package com.mediflow.clinical.application.port.in;

import java.util.UUID;
import java.util.List;
import java.time.LocalDate;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.application.dto.request.CreateAppointmentRequest;
import com.mediflow.clinical.application.dto.request.UpdateAppointmentRequest;
import com.mediflow.clinical.application.dto.response.AppointmentDTO;

public interface ManageAppointmentUseCase {
    AppointmentDTO create(CreateAppointmentRequest request);
    AppointmentDTO update(UUID id, UpdateAppointmentRequest request);
    AppointmentDTO changeStatus(UUID id, AppointmentStatus status);
    AppointmentDTO getById(UUID id);
    List<AppointmentDTO> byPatient(UUID patientId);
    PageResult<AppointmentDTO> search(UUID departmentId, LocalDate appointmentDate, PageQuery page);
}
