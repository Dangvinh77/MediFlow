package com.mediflow.billing.web;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.billing.application.dto.request.CreateInvoiceRequest;
import com.mediflow.billing.application.dto.request.PayInvoiceRequest;
import com.mediflow.billing.application.dto.response.InvoiceDTO;
import com.mediflow.billing.application.dto.response.PaymentResultDTO;
import com.mediflow.billing.application.port.in.ManageInvoiceUseCase;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

/**
 * REST controller cho hóa đơn (backend-spec/06-billing.md §8). Chỉ đảm nhiệm HTTP, validation và
 * phân quyền — mọi thuật toán (tính tổng tiền, khử trùng lặp, saga) nằm trong
 * {@link ManageInvoiceUseCase}.
 */
@RestController
@RequestMapping("/api/v1/billing")
public class InvoiceController {

    private final ManageInvoiceUseCase manageInvoiceUseCase;

    public InvoiceController(ManageInvoiceUseCase manageInvoiceUseCase) {
        this.manageInvoiceUseCase = manageInvoiceUseCase;
    }

    /** Xem một hóa đơn theo id, gồm chi tiết các khoản phí. */
    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    public ResponseEntity<ApiResponse<InvoiceDTO>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(manageInvoiceUseCase.getById(id)));
    }

    /** Danh sách hóa đơn của một bệnh nhân, phân trang. */
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    public ResponseEntity<ApiResponse<PageResult<InvoiceDTO>>> byPatient(
            @PathVariable UUID patientId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PageResult<InvoiceDTO> result = manageInvoiceUseCase.byPatient(patientId, PageQuery.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * Lập hóa đơn thường — server tự cộng các khoản phí chưa trả của bệnh nhân (BR-B2). Không còn
     * khoản phí nào chưa trả → {@code BILLING_NO_UNPAID_FEES} (422).
     */
    @PostMapping("/invoices")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    public ResponseEntity<ApiResponse<InvoiceDTO>> create(@Valid @RequestBody CreateInvoiceRequest request) {
        InvoiceDTO created = manageInvoiceUseCase.create(request);
        URI location = URI.create("/api/v1/billing/invoices/" + created.invoiceId());
        return ResponseEntity.created(location).body(ApiResponse.ok(created));
    }

    /**
     * Thanh toán hóa đơn (BR-B1, BR-B3). Nếu hóa đơn mở saga đơn thuốc, đẩy saga sang
     * {@code AWAITING_DISPENSE} và publish {@code payment.completed} sau commit.
     */
    @PutMapping("/invoices/{id}/pay")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    public ResponseEntity<ApiResponse<PaymentResultDTO>> pay(
            @PathVariable UUID id,
            @Valid @RequestBody PayInvoiceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(manageInvoiceUseCase.pay(id, request)));
    }
}
