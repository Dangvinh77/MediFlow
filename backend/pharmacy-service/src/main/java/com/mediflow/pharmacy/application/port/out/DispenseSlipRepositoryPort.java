package com.mediflow.pharmacy.application.port.out;

import java.util.Optional;

import java.util.UUID;

import com.mediflow.pharmacy.domain.model.DispenseSlip;

/**
 * 
 * DispenseSlipRepositoryPort
 * Vì sao cần: trạng thái phiếu (PENDING / DISPENSED / FAILED) phải được ghi nhận, 
 * vì nó là bằng chứng của saga: đã xuất chưa, xuất thất bại vì lý do gì.
 */
public interface DispenseSlipRepositoryPort {
   //lưu phiếu
   DispenseSlip save(DispenseSlip dispenseSlip);

   //tìm 1 phiếu
   Optional<DispenseSlip> findById(UUID id);

   //Tìm phiếu theo đơn
   Optional<DispenseSlip> findByPrescription(UUID prescriptionId);
}
