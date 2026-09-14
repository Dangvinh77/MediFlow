package com.mediflow.pharmacy.web;

import com.mediflow.pharmacy.application.port.in.ManageDrugUseCase;

import jakarta.validation.Valid;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageQuery;

import lombok.RequiredArgsConstructor;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.pharmacy.application.dto.request.AdjustStockRequest;
import com.mediflow.pharmacy.application.dto.request.CreateDrugRequest;
import com.mediflow.pharmacy.application.dto.response.DrugDTO;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;

/**
 * REST controller cung cấp các API quản lý danh mục thuốc và tồn kho.
 *
 * <p>Controller chỉ tiếp nhận, kiểm tra dữ liệu đầu vào và chuyển yêu cầu tới
 * {@link ManageDrugUseCase}; toàn bộ quy tắc nghiệp vụ được xử lý ở tầng application/domain.
 */
@RestController
@RequestMapping("/api/v1/pharmacy/drugs")
@RequiredArgsConstructor
public class DrugController {
 private final ManageDrugUseCase manageDrugUseCase;

 /**
  * Tìm kiếm thuốc theo từ khóa và trả kết quả phân trang.
  *
  * @param keyword từ khóa tìm theo tên thuốc; có thể để trống để lấy toàn bộ danh sách
  * @param page số trang được yêu cầu; có thể để trống để dùng giá trị mặc định
  * @param size số phần tử trên một trang; có thể để trống để dùng giá trị mặc định
  * @return phản hồi thành công chứa trang kết quả thuốc
  */
 @GetMapping
 @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PHARMACIST')")
 public ResponseEntity<ApiResponse<PageResult<DrugDTO>>> search(
    @RequestParam(required = false) String keyword,
    @RequestParam(required = false) Integer page,
    @RequestParam(required = false) Integer size,
    @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String correlationId
) 
    {
     PageResult<DrugDTO> result = manageDrugUseCase.search(keyword, PageQuery.of(page, size));
     return ResponseEntity.ok(ApiResponse.ok(result, normalizeCorrelationId(correlationId)));
 }

 /**
  * Lấy thông tin chi tiết của một thuốc theo mã định danh.
  *
  * @param id mã định danh của thuốc
  * @return phản hồi thành công chứa thông tin thuốc
  */
 @GetMapping("/{id}")
 @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PHARMACIST')")
 public ResponseEntity<ApiResponse<DrugDTO>> getById(
         @PathVariable UUID id,
         @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String correlationId){
    return ResponseEntity.ok(ApiResponse.ok(manageDrugUseCase.getById(id), normalizeCorrelationId(correlationId)));
 }

 /**
  * Thêm một thuốc mới vào danh mục.
  *
  * @param request thông tin thuốc cần tạo
  * @return phản hồi tạo thành công chứa thuốc mới và URI của tài nguyên
  */
  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN','PHARMACIST')")
 public ResponseEntity<ApiResponse<DrugDTO>> create(
         @Valid @RequestBody CreateDrugRequest request,
         @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String correlationId){
      DrugDTO created = manageDrugUseCase.create(request);
      URI location = URI.create("/api/v1/pharmacy/drugs/" + created.drugId());

    return ResponseEntity.created(location).body(ApiResponse.ok(created, normalizeCorrelationId(correlationId)));
 }

 /**
  * Điều chỉnh số lượng tồn kho của một thuốc.
  *
  * @param id mã định danh của thuốc cần điều chỉnh
  * @param request số lượng thay đổi và lý do điều chỉnh
  * @return phản hồi thành công chứa thông tin thuốc sau khi cập nhật tồn kho
  */
 @PutMapping("/{id}/stock")
 @PreAuthorize("hasAnyRole('ADMIN','PHARMACIST')")
  public ResponseEntity<ApiResponse<DrugDTO>> adjustStock(
     @PathVariable UUID id,
     @Valid @RequestBody AdjustStockRequest request,
     Authentication authentication,
     @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String correlationId){
      ActorIdentity actor = parseActor(authentication);
      String normalizedCorrelationId = correlationId == null || correlationId.isBlank()
              ? UUID.randomUUID().toString() : correlationId.trim();
      DrugDTO updated  = manageDrugUseCase.adjustStock(
             id, request, operationalActorId(actor), normalizedCorrelationId);
      return ResponseEntity.ok(ApiResponse.ok(updated, normalizedCorrelationId));
 }

 /**
  * Parses the shared JWT subject contract without exposing malformed identities as HTTP 500.
  *
  * @param authentication authenticated principal
  * @return actor UUID used in stock audit events
  * @throws AccessDeniedException when the JWT subject is absent or not a UUID
  */
  private ActorIdentity parseActor(Authentication authentication) {
     if (authentication == null || authentication.getName() == null) {
         throw new AccessDeniedException("Không xác định được người dùng từ JWT subject");
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
         throw new AccessDeniedException("JWT subject không phải mã người dùng hợp lệ", exception);
     }
  }

  /** Resolves the signed identity allowed to own a stock-audit operation. */
  private UUID operationalActorId(ActorIdentity actor) {
      try {
          return actor.auditActorId();
      } catch (IllegalStateException exception) {
          throw new AccessDeniedException(
                  "JWT chưa cung cấp staffId đã xác thực cho thao tác nghiệp vụ", exception);
      }
  }

  /** Generates a trace id for direct calls that do not pass through the gateway. */
  private String normalizeCorrelationId(String value) {
      return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
  }

 }
