package com.mediflow.pharmacy.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import com.mediflow.pharmacy.application.port.in.CreatePrescriptionUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * REST controller cho nghiệp vụ kê đơn thuốc.
 *
 * <p>Controller chỉ đảm nhiệm HTTP, validation và phân quyền. Việc khóa thuốc,
 * kiểm tra tồn, chụp giá, lưu dữ liệu và phát event thuộc về
 * {@link CreatePrescriptionUseCase}.</p>
 */
@RestController
@RequestMapping("/api/v1/pharmacy/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final CreatePrescriptionUseCase createPrescriptionUseCase;

    /**
     * Tạo một đơn thuốc mới.
     *
     * @param request thông tin đơn và các dòng thuốc; không chứa giá
     * @return HTTP 201 kèm đơn đã tính giá, tổng tiền và trạng thái PENDING
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<ApiResponse<PrescriptionDTO>> create(
            @Valid @RequestBody
            CreatePrescriptionRequest request) {

        PrescriptionDTO created =
                createPrescriptionUseCase.create(request);

        URI location = URI.create(
                "/api/v1/pharmacy/prescriptions/"
                        + created.prescriptionId());

        return ResponseEntity
                .created(location)
                .body(ApiResponse.ok(created));
    }
}