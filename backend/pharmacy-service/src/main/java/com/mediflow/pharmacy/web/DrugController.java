package com.mediflow.pharmacy.web;

import com.mediflow.pharmacy.application.port.in.ManageDrugUseCase;

import jakarta.validation.Valid;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageQuery;

import lombok.RequiredArgsConstructor;
import com.mediflow.common.api.PageResult;
import com.mediflow.pharmacy.application.dto.request.AdjustStockRequest;
import com.mediflow.pharmacy.application.dto.request.CreateDrugRequest;
import com.mediflow.pharmacy.application.dto.response.DrugDTO;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    @RequestParam(required = false) Integer size
) 
    {
     PageResult<DrugDTO> result = manageDrugUseCase.search(keyword, PageQuery.of(page, size));
     return ResponseEntity.ok(ApiResponse.ok(result));
 }

 /**
  * Lấy thông tin chi tiết của một thuốc theo mã định danh.
  *
  * @param id mã định danh của thuốc
  * @return phản hồi thành công chứa thông tin thuốc
  */
 @GetMapping("/{id}")
 @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PHARMACIST')")
 public ResponseEntity<ApiResponse<DrugDTO>> getById(@PathVariable UUID id){
    return ResponseEntity.ok(ApiResponse.ok(manageDrugUseCase.getById(id)));
 }

 /**
  * Thêm một thuốc mới vào danh mục.
  *
  * @param request thông tin thuốc cần tạo
  * @return phản hồi tạo thành công chứa thuốc mới và URI của tài nguyên
  */
  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN','PHARMACIST')")
 public ResponseEntity<ApiResponse<DrugDTO>> create(@Valid @RequestBody CreateDrugRequest request){
      DrugDTO created = manageDrugUseCase.create(request);
      URI location = URI.create("/api/v1/pharmacy/drugs/" + created.drugId());

    return ResponseEntity.created(location).body(ApiResponse.ok(created));
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
    @Valid @RequestBody AdjustStockRequest request){
    DrugDTO updated  = manageDrugUseCase.adjustStock(id, request);
    return ResponseEntity.ok(ApiResponse.ok(updated));
 }
 
}
