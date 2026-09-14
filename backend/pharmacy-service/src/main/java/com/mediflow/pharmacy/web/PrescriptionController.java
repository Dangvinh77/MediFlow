package com.mediflow.pharmacy.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.pharmacy.application.dto.command.CancelPrescriptionCommand;
import com.mediflow.pharmacy.application.dto.command.CreatePrescriptionCommand;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.request.CancelPrescriptionRequest;
import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;
import com.mediflow.pharmacy.application.dto.response.CancelPrescriptionResult;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import com.mediflow.pharmacy.application.port.in.CancelPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.CreatePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.DispensePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.GetPrescriptionUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final GetPrescriptionUseCase getPrescriptionUseCase;
    private final CancelPrescriptionUseCase cancelPrescriptionUseCase;
    private final DispensePrescriptionUseCase dispensePrescriptionUseCase;

    /**
     * Tạo một đơn thuốc mới.
     *
     * @param request thông tin đơn và các dòng thuốc; không chứa giá
     * @return HTTP 201 kèm đơn đã tính giá, tổng tiền và trạng thái PENDING
     */
   @PostMapping
@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
public ResponseEntity<ApiResponse<PrescriptionDTO>> create(
        @Valid @RequestBody CreatePrescriptionRequest request,
        Authentication authentication,
        @RequestHeader(
                value = JwtClaims.HEADER_CORRELATION_ID,
                required = false) String correlationId) {

    ActorIdentity actor = parseActor(authentication);

    CreatePrescriptionCommand command =
            new CreatePrescriptionCommand(
                    request,
                    actor,
                    correlationId);

    PrescriptionDTO created =
            createPrescriptionUseCase.create(command);

    URI location = URI.create(
            "/api/v1/pharmacy/prescriptions/"
                    + created.prescriptionId());

    return ResponseEntity
            .created(location)
            .body(ApiResponse.ok(created, command.correlationId()));
}

    /**
     * Hủy một đơn đang hoạt động và trả lại toàn bộ lượng tồn đang được giữ.
     *
     * <p>JWT subject là accountId; application layer dùng staffId đã ký để kiểm tra quyền sở hữu:
     * bác sĩ chỉ được hủy đơn do chính mình kê, còn ADMIN được phép override.</p>
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

        ActorIdentity actor = parseActor(authentication);

        CancelPrescriptionCommand command = new CancelPrescriptionCommand(
                        prescriptionId,
                        actor,
                        request.reason(),
                        correlationId);
        CancelPrescriptionResult result = cancelPrescriptionUseCase.cancel(command);

        return ResponseEntity.ok(ApiResponse.ok(result, command.correlationId()));
    }

    /**
     * Lấy chi tiết đơn thuốc, bao gồm lifecycle và trạng thái phiếu xuất.
     *
     * @param prescriptionId mã đơn thuốc cần đọc
     * @return HTTP 200 với DTO đơn thuốc
     */
    @GetMapping("/{prescriptionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PHARMACIST')")
    public ResponseEntity<ApiResponse<PrescriptionDTO>> getById(
            @PathVariable UUID prescriptionId,
            @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String correlationId) {
        return ResponseEntity.ok(ApiResponse.ok(
                getPrescriptionUseCase.getPrescriptionById(prescriptionId),
                normalizeCorrelationId(correlationId)));
    }

    /**
     * Xuất thuốc thủ công sau khi dược sĩ đã xác nhận thanh toán tại quầy.
     *
     * @param prescriptionId mã đơn cần xuất
     * @param authentication danh tính dược sĩ hoặc quản trị viên
     * @param correlationId mã tương quan của request
     * @return HTTP 200 với phiếu xuất đã chuyển DISPENSED
     */
    @PutMapping("/{prescriptionId}/dispense")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
    public ResponseEntity<ApiResponse<com.mediflow.pharmacy.application.dto.response.DispenseDTO>> dispense(
            @PathVariable UUID prescriptionId,
            Authentication authentication,
            @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String correlationId) {
        ActorIdentity actor = parseActor(authentication);
        String normalizedCorrelationId = normalizeCorrelationId(correlationId);
        var result = dispensePrescriptionUseCase.dispense(
                prescriptionId, operationalActorId(actor), normalizedCorrelationId);
        return ResponseEntity.ok(ApiResponse.ok(result, normalizedCorrelationId));
    }

    /**
     * Chuyển JWT subject theo hợp đồng chung thành mã người dùng UUID.
     *
     * @param authentication authentication hiện tại
     * @return mã người dùng đã xác thực
     * @throws AccessDeniedException nếu principal không tuân thủ hợp đồng UUID
     */
    private ActorIdentity parseActor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException(
                    "Không xác định được người dùng từ JWT subject");
        }
        if (authentication.getPrincipal() instanceof ActorIdentity actor) {
            return actor;
        }
        try {
            UUID accountId = UUID.fromString(authentication.getName());
            String role = authentication.getAuthorities().stream()
                    .findFirst()
                    .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                    .orElseThrow(() -> new AccessDeniedException("JWT không có role hợp lệ"));
            return new ActorIdentity(accountId, null, role);
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException(
                    "JWT subject không phải mã người dùng hợp lệ",
                    exception);
        }
    }

    /** Resolves the signed identity allowed to own a dispensing operation. */
    private UUID operationalActorId(ActorIdentity actor) {
        try {
            return actor.auditActorId();
        } catch (IllegalStateException exception) {
            throw new AccessDeniedException(
                    "JWT chưa cung cấp staffId đã xác thực cho thao tác nghiệp vụ", exception);
        }
    }

    /** Generates a correlation id when a direct caller omitted the tracing header. */
    private String normalizeCorrelationId(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }
}
