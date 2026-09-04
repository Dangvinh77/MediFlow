package com.mediflow.pharmacy.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.pharmacy.application.dto.command.CancelPrescriptionCommand;
import com.mediflow.pharmacy.application.dto.request.CancelPrescriptionRequest;
import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;
import com.mediflow.pharmacy.application.dto.response.CancelPrescriptionResult;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import com.mediflow.pharmacy.application.port.in.CancelPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.CreatePrescriptionUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

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
    private final CancelPrescriptionUseCase cancelPrescriptionUseCase;

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

    /**
     * Hủy một đơn đang hoạt động và trả lại toàn bộ lượng tồn đang được giữ.
     *
     * <p>Danh tính người thực hiện được lấy từ JWT subject. Application layer tiếp tục kiểm tra
     * quyền sở hữu: bác sĩ chỉ được hủy đơn do chính mình kê, còn ADMIN được phép override.</p>
     *
     * @param prescriptionId mã đơn thuốc cần hủy
     * @param request lý do hủy đã qua Bean Validation
     * @param authentication danh tính đã được {@code JwtAuthFilter} xác thực
     * @param correlationId mã tương quan do gateway truyền xuống; có thể vắng mặt khi gọi trực tiếp
     * @return HTTP 200 kèm trạng thái đơn và số reservation vừa được giải phóng
     */
    @PutMapping("/{prescriptionId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<ApiResponse<CancelPrescriptionResult>> cancel(
            @PathVariable UUID prescriptionId,
            @Valid @RequestBody CancelPrescriptionRequest request,
            Authentication authentication,
            @RequestHeader(
                    value = JwtClaims.HEADER_CORRELATION_ID,
                    required = false) String correlationId) {

        UUID actorId = parseActorId(authentication);
        boolean administrator = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));

        CancelPrescriptionResult result = cancelPrescriptionUseCase.cancel(
                new CancelPrescriptionCommand(
                        prescriptionId,
                        actorId,
                        administrator,
                        request.reason(),
                        correlationId));

        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }

    /**
     * Chuyển JWT subject theo hợp đồng chung thành mã người dùng UUID.
     *
     * @param authentication authentication hiện tại
     * @return mã người dùng đã xác thực
     * @throws AccessDeniedException nếu principal không tuân thủ hợp đồng UUID
     */
    private UUID parseActorId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException(
                    "Không xác định được người dùng từ JWT subject");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException(
                    "JWT subject không phải mã người dùng hợp lệ",
                    exception);
        }
    }
}
