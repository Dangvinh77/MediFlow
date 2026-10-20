# Pharmacy mobile — contract và integration manifest

Khóa source ngày 2026-10-20, base `8883f49d`. Owner Huy; scope và thứ tự tại
[plan MP-HUY](../../../docs/huy-pharmacy-mobile-plan.md).
Task 01–03 triển khai read slice; không mở commands, backend flags hoặc shared composition.

## Nguồn contract

- `backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/web/DrugController.java`:
  GET drugs keyword/page/size và GET drugs/{id}, ADMIN/DOCTOR/PHARMACIST.
- Cùng web package, `PrescriptionController.java`: GET prescriptions/{id}, cùng ba role;
  không có collection GET prescriptions. Các method command giữ ở plan để khóa ranh giới,
  chưa được expose trong read-only repository.
- `backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/application/dto/response/`:
  DrugDTO, PrescriptionDTO, PrescriptionLineDTO, DispenseDTO, CancelPrescriptionResult.
- `backend/common/src/main/java/com/mediflow/common/api/PageResult.java` và PageQuery:
  content/number/size/totalPages/totalElements, default size 20, maximum 100.
- `backend/gateway/src/main/java/com/mediflow/gateway/filter/RouteAuthorizationFilter.java`:
  exact GET paths/roles trên khớp controller. Backend là authority, role UI không thay security.
- Pharmacy GlobalExceptionHandler: 400 validation/malformed, 403 ownership/role,
  404 resource not found (`DRUG_NOT_FOUND`), 409 duplicate/conflict, 422 business rule,
  503 `PHARMACY_UPSTREAM_UNAVAILABLE`; JWT 401 do security. Giữ code/details/correlation gốc.

DTO decoder map English wire fields sang pure Dart models, kiểm required presence và kiểu;
nullable field có mặt với null được giữ nguyên. Unknown enums/invalid dates/IDs/numbers không
fallback hoặc tạo facts. Drug price/Rx amount lấy nguyên giá trị số server, không tính lại giá.
Prescription read không mang careContractVersion/context: không suy V0/V1 từ field thiếu.
Public V1 command/profile/gates và compatibility nằm ở task 07–10; read slice không tự thay chúng.

Domain gồm Drug, Prescription/Line, enum states, terminal result models và read repository/use cases.
Data chỉ gọi shared ApiClient; GET paths bắt đầu `/v1/`, base config đã chứa `/api/`.
Repository đối chiếu identity detail trả về và giữ nguyên ApiFailure. Test doubles chỉ ở mobile/test.

## Thiết kế read slice

- Danh mục dùng card theo chiều dọc; tên, server price/unit, tồn, hạn và nhãn low/out-of-stock.
  Input tên + tìm kiếm/keyboard Search; kết quả chỉ theo query đã submit, không theo draft đang gõ.
- Đổi search reset page 0; paging/retry/refresh giữ query/page. Refresh có nút và pull gesture,
  kể cả empty result. Đang tải không hiển thị snapshot cũ; số 0 chỉ khi server thật trả 0.
- Chi tiết có các fields thuốc, nullable rõ, ID selectable, refresh và back. Local Navigator mở
  detail mặc định; `PharmacyPage(onOpenDrug: ...)` cho composition root chọn deep-link routing.
- Loading semantics, error/correlation, retry GET chỉ network/5xx/429; 401/403/404 và malformed
  response không hiện nút retry. Không auto retry. Theme/Material primitives hiện có, không thêm package.
- Thiết kế các màn sau đã khóa ở mức plan, chưa code: Rx lookup là input UUID + detail/refresh;
  form V1 có source context, ngày và danh sách thuốc; hủy cần reason và confirmation;
  xuất có confirmation riêng với trạng thái quan sát và lỗi clearance server. Tạo thuốc/stock
  dùng form dọc với field errors, không thêm controls quản trị vào read-only màn danh mục lúc này.
  Giá/tổng vẫn là server facts; V1 có frozen intent/key, bodyless PUT dispense, legacy mode explicit.
- Generation guards bảo vệ search/detail và dispose; keyed boundary bỏ state khi repository hoặc
  session identity đổi. UI chỉ đọc, không hiện nút tạo/stock/kê/hủy/xuất trong ba task đầu.

## Nối shared app — chưa thực hiện, MP-HUY-11 do Vinh phối hợp

1. `app/dependencies.dart` bind `pharmacyRepositoryProvider` bằng GatewayPharmacyRepository
   với shared apiClientProvider. Presentation chỉ dùng domain interface, không dựng Dio/adapter.
2. Bind `pharmacyAccessProvider` từ actual auth session trong composition root: watch session
   loading/logout/login/role change, trả null khi chưa có phiên, role lấy từ signed session.
   sessionIdentity là object opaque mới theo phiên, ổn định trong cùng phiên; không dùng token
   làm widget key/log. Không chỉ đọc mutable ApiSession bridge một lần vì nó không reactive.
3. Role/deep-link guards cho mọi route trong app/router.dart; feature boundary fail-closed khi
   chưa bind hoặc role ngoài ADMIN/DOCTOR/PHARMACIST. Module chưa mark implemented.
4. Có thể dùng detail navigation nội bộ đã có cho read slice, hoặc nối DrugDetailPage qua callback;
   không tạo route business cho Rx chưa triển khai. Root/shared paths vẫn cần đúng owner thực hiện.

Không có dữ liệu fixture runtime hoặc bypass auth để xem UI. Provider overrides trong widget tests
không là bằng chứng actual issuer/Gateway/native. Khi dependencies chưa nối, app hiển thị chưa sẵn sàng.
Các task này đóng ở local implementation; integrated/native acceptance nằm ở MP-HUY-11/12.
