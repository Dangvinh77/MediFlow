package com.mediflow.billing.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.billing.domain.model.Invoice;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

/**
 * Out-port — "tôi cần ai đó biết cách lưu và tìm hóa đơn".
 * {@code InvoicePersistenceAdapter} (infrastructure, Phần 4/5) hiện thực. Phân trang dùng
 * {@link PageQuery}/{@link PageResult} của common — <b>không</b> dùng Spring Data {@code Pageable}
 * ở tầng application. Chữ ký bám sát backend-spec/06-billing.md §6.
 */
public interface InvoiceRepositoryPort {

    /** Lưu mới hoặc cập nhật. Trả về đối tượng đã có đủ id + timestamps. */
    Invoice save(Invoice invoice);

    /** Tìm một hóa đơn. Không có thì trả {@link Optional#empty()} → application ném {@code InvoiceNotFoundException}. */
    Optional<Invoice> findById(UUID id);

    /**
     * Tìm hóa đơn theo đơn thuốc đã mở saga. Cột {@code prescription_id} có unique partial index
     * {@code uq_invoice_prescription} — mỗi đơn tối đa một hóa đơn (BR-B6), nên kết quả tối đa một.
     */
    Optional<Invoice> findByPrescription(UUID prescriptionId);

    /** Danh sách hóa đơn của một bệnh nhân, có phân trang. */
    PageResult<Invoice> findByPatient(UUID patientId, PageQuery page);

    /**
     * Tổng hợp doanh thu đã thanh toán, gom theo khoa, trong khoảng ngày (BR-B10).
     * {@code departmentId} có thể null để lấy tất cả các khoa.
     */
    List<DepartmentRevenue> sumRevenueByDepartment(UUID departmentId, LocalDate fromDate, LocalDate toDate);

    /**
     * Kết quả tổng hợp doanh thu một khoa — projection thuần của tầng application (không phải
     * domain model, không phải DTO trả ra HTTP). {@code RevenueMapper} đổi nó thành
     * {@code RevenueByDeptDTO}.
     */
    record DepartmentRevenue(UUID departmentId, java.math.BigDecimal totalRevenue, long invoiceCount) {}
}
