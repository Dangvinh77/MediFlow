package com.mediflow.pharmacy.application.port.out;
/**
 * 
 * PrescriptionRepositoryPort
 * Đơn thuốc (kèm các dòng phải được lưu lại để phục vụ xem lịch sử và xuất thuốc)
 */

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.pharmacy.domain.model.Prescription;

public interface PrescriptionRepositoryPort {

    Prescription save(Prescription prescription);

    //Tìm đơn để xem chi tiết
    Optional<Prescription> findById(UUID id);

    //danh sách đơn của 1 bệnh nhân
    List<Prescription> findByPatient(UUID patientId); 

  
}
