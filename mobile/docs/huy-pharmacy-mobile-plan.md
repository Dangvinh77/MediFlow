# Plan mobile Pharmacy — Huy

**Ngày lập:** 2026-10-20. **Owner:** Huy (`LQHuy0210`).
**Baseline:** `8883f49d`, nhánh Huy; FE working diff hiện có giữ nguyên.
**Tiến độ implementation:** **3/12 task (25%)** — MP-HUY-01/02/03 hoàn tất local;
shared app integration và nghiệm thu Gateway/native nằm ở MP-HUY-11/12, chưa thực hiện.

## 1. Kết quả cần đạt

Mobile có danh mục/chi tiết thuốc, tạo thuốc, điều chỉnh kho, tra cứu đơn, kê đơn ngoại trú V1,
hủy và xuất thuốc qua Gateway thật. Luồng nghiệm thu chính:
**nguồn hồ sơ/episode hợp lệ → tạo Rx V1 → thanh toán qua Billing hiện có → refresh → xuất thuốc**.
Backend giữ authority về giá, quyền, payment clearance, reservations và stock.

Pharmacy triển khai trước, không phụ thuộc Report coverage để nghiệm thu nghiệp vụ Pharmacy.
Không mở thêm Report/Notification mobile, Billing UI, Inpatient, Surgery, ADMISSION hoặc full V2.
Không thêm Rx-list, sửa/xóa thuốc, quét barcode, offline queue hoặc export khi backend chưa có contract.
Outbox replay là công cụ quản trị riêng, ngoài đợt mobile Pharmacy này.

## 2. Hiện trạng và phạm vi

Đã có Flutter/Riverpod/GoRouter/Dio/SecureStorage, auth và Gateway client, shared paged widgets.
PharmacyPage mới là FeatureWorkspace; domain/data chỉ có README, chưa có nghiệp vụ hay test riêng.

| Phạm vi | Trách nhiệm |
|---|---|
| `mobile/lib/features/pharmacy/**` | Huy IMPLEMENT: presentation → domain ← data |
| `mobile/test/pharmacy_*_test.dart` | Huy IMPLEMENT: contract, use case, controller/widget |
| `mobile/docs/huy-pharmacy-mobile-plan.md` | Plan và checkpoint của Huy |
| `mobile/lib/core/network/api_client.dart`, `mobile/test/api_client_test.dart` | Vinh/shared: PUT bodyless/JSON, POST command headers và regression |
| `mobile/lib/app/dependencies.dart` | Vinh/shared: bind Pharmacy repository adapter |
| `mobile/lib/app/router.dart`, `mobile/lib/app/modules.dart` | Vinh/shared: routes, nested route role checks, trạng thái implemented |
| `mobile/lib/core/config/app_config.dart`, `mobile/test/foundation_test.dart` | Vinh/shared: cấu hình mode tường minh và kiểm fail-closed |
| `mobile/lib/app/app_shell.dart` | Vinh/shared: chỉ kiểm và sửa nếu nested route cần chọn đúng module |
| Clinical/Core/Billing mobile hoặc backend | VERIFY-CONTRACT, không sửa trong plan này |

Đây là phân công dự kiến, không tự cấp quyền shared paths cho Huy. Không import feature khác;
composition root truyền context/adapter, presentation không tự dựng concrete repository.
Dependency phối hợp ghi trong [handoff chung Huy](../../docs/handoffs/HANDOFF-HUY-MVP.md),
không tạo thêm file handoff. Đọc AGENTS gần nhất trước mỗi đợt sửa.

## 3. Contract đã đối chiếu ở baseline

Các path dưới là public Gateway `/api/v1/pharmacy`; adapter truyền `/v1/pharmacy/...`
cho ApiClient vì base URL đã có `/api/`. Không gọi service port hoặc tạo HTTP client riêng.

| Method/path dưới `/api/v1/pharmacy` | Role backend/Gateway | Request/result |
|---|---|---|
| GET `/drugs` | ADMIN, DOCTOR, PHARMACIST | keyword/page/size → DrugDTO page |
| GET `/drugs/{id}` | ADMIN, DOCTOR, PHARMACIST | DrugDTO |
| POST `/drugs` | ADMIN, PHARMACIST | CreateDrugRequest → 201 DrugDTO |
| PUT `/drugs/{id}/stock` | ADMIN, PHARMACIST | quantity/reason → DrugDTO |
| GET `/prescriptions/{id}` | ADMIN, DOCTOR, PHARMACIST | PrescriptionDTO |
| POST `/prescriptions` | ADMIN, DOCTOR | V1 request + Idempotency-Key → 201 PrescriptionDTO |
| PUT `/prescriptions/{id}/cancel` | ADMIN, DOCTOR | reason → CancelPrescriptionResult |
| PUT `/prescriptions/{id}/dispense` | ADMIN, PHARMACIST | không body → DispenseDTO |

Role ở đây là quyền vào endpoint; assigned doctor/actor/clearance và trạng thái vẫn do use case
backend kiểm tra. Không coi ADMIN/DOCTOR role là bằng chứng sở hữu đơn.

- Page dùng `content,totalElements,totalPages,number,size`; page zero-based, size mặc định 20,
  backend giới hạn 100. Shared decodePage đọc được shape này; không sửa decoder nếu chưa có lỗi.
- DrugDTO: drugId, drugName, activeIngredient, unit, price, stockQuantity, expiryDate,
  manufacturer, lowStockThreshold, createdAt, updatedAt. Giữ nullable theo DTO/serializer.
- Rx gồm prescriptionId, nullable recordId, patientId/doctorId/departmentId, prescribedDate,
  totalAmount, lines, status, dispenseStatus, paymentConfirmed và cancellation/timestamps.
  GET hiện **không có metadata phiên bản/context**: không suy V1 từ field vắng mặt.
- V1 POST thêm careContractVersion=1, careContext=OUTPATIENT,
  careEpisodeType=OUTPATIENT_VISIT, careEpisodeId, admissionId=null, priceCode.
  Public outpatient flow cần recordId hợp lệ dù DTO tổng quát cho phép null.
  Lines chỉ gửi drugId/quantity/dosage, không gửi unitPrice/total để làm authority.
- Idempotency-Key của V1 là UUID canonical. Chỉ command ID do client tạo;
  patient/doctor/department/record/episode IDs tuyệt đối không tự tạo.
- UUID/string/date/time và nullable/error fields phải khóa lại từ Java DTO, serializer và fixtures
  ngay trước implementation. Monetary JSON numbers phải được đọc/serialize theo contract,
  không tự làm tròn giá hoặc tính lại tổng thay backend; chưa thêm dependency tiền tệ.

Nguồn kiểm tra: [DrugController](../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/web/DrugController.java),
[PrescriptionController](../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/web/PrescriptionController.java),
`backend/pharmacy-service/.../application/dto/`, Gateway RouteAuthorizationFilter,
[hướng dẫn Pharmacy web](../../frontend/docs/services/pharmacy.md). Web là bằng chứng tham khảo;
contract backend hiện hành là authority, không sao chép cấu trúc Next.js sang Flutter.

## 4. Thứ tự thực hiện và điều kiện đóng

### MP-HUY-01 — Khóa contract, role và thiết kế màn hình — LOCAL DONE

- [x] Ghi manifest API/DTO/errors/profile/mode hiện hành, bao gồm bodyless dispense và V1 idempotency.
- [x] Chốt layout dùng Theme hiện có: danh mục, thuốc, tra cứu đơn, kê đơn, xác nhận hủy/xuất;
  navigation mobile, không bê nguyên bảng desktop. Không đổi theme/shell toàn app.
- [x] Xác nhận nullability, enum terminal và error codes bằng source/fixtures thật.
- [x] Liệt kê shared deliverables mục 5; không bật backend gates để làm UI chạy.

Đóng khi contract đủ để triển khai và giới hạn được ghi rõ; chưa gọi đây là runtime acceptance.

### MP-HUY-02 — Model, repository và adapter đọc dữ liệu — LOCAL DONE

- [x] Pure Dart Drug/Prescription/Line/Dispense/CancelResult và repository interfaces/use cases.
- [x] DTO decoder và Gateway adapter GET; dependency được inject, không import feature khác.
- [x] Test page, nullable, enum lạ/field thiếu, invalid UUID/date/number và malformed envelope;
  dữ liệu sai thành lỗi, không biến thành empty/zero/paid=false.
- [x] Test exact path/query và propagation status/code/details/correlation; không log token/PII.

Đóng khi domain/data read tests đạt; binding thật do MP-HUY-11, không dựng adapter trong widget.

### MP-HUY-03 — Danh mục và chi tiết thuốc — LOCAL DONE

- [x] Keyword search, phân trang, detail; giữ query đã submit, reset page khi đổi tìm kiếm.
- [x] Loading/empty/error/retry/refresh; server stock, expiry và low-stock trình bày riêng.
- [x] Bỏ response cũ khi đổi query/id, logout/đổi phiên hoặc dispose; lỗi quyền không có retry vòng lặp.
- [x] Widget tests navigation callback, kết quả rỗng, pagination, lỗi và stale response.

Đóng local khi tests đạt; mở route thật sau composition, signed proof ghi MP-HUY-12.

### MP-HUY-04 — Thêm thuốc

- [ ] Form đúng CreateDrugRequest: tên/đơn vị/giá/tồn/hạn dùng/threshold và fields optional.
- [ ] Mirror backend limits: tên 150, đơn vị 20, giá không âm với precision contract,
  stock/threshold không âm, expiry không quá khứ. Server vẫn quyết định kết quả cuối.
- [ ] ADMIN/PHARMACIST, pending khóa submit đồng bộ, field errors/focus và thành công mở ID server.
- [ ] Test invalid/403/409/network/503. Request này chưa có idempotency contract:
  response không rõ phải kiểm kết quả trước lệnh mới, không tự resend hoặc tuyên bố exactly-once.

### MP-HUY-05 — Điều chỉnh tồn kho

- [ ] PUT delta quantity không bằng 0; lý do có giới hạn backend, confirmation nêu rõ tăng/giảm.
- [ ] Chỉ ADMIN/PHARMACIST; không cho âm stock bằng optimistic update; dùng DrugDTO trả về.
- [ ] Pending khóa đôi, conflict/unknown outcome refresh trước lệnh mới; không auto retry delta.
- [ ] Test số âm/dương/0, reservation constraint, forbidden, lỗi refresh và không áp delta hai lần.

MP-HUY-04/05 chờ shared PUT/transport thích hợp; không bypass ApiClient bằng Dio riêng.

### MP-HUY-06 — Tra cứu và chi tiết đơn thuốc

- [ ] UUID lookup, chi tiết lines/amounts, trạng thái đơn và xuất kho tách biệt, cancellation audit.
- [ ] Refresh explicit GET, loading/error/empty-not-found/403 riêng, correlation và stale/session guards.
- [ ] Mode lấy từ cấu hình app đã khóa; unknown mode đóng command, không đoán từ GET hoặc lỗi API.
- [ ] Test nullable recordId, paymentConfirmed=false, các trạng thái terminal, 401/403/404/503.

Không có GET danh sách prescriptions: không làm fake history, không suy dữ liệu từ thuốc hoặc DB.

### MP-HUY-07 — Đầu vào kê đơn từ public workflow

- [ ] Xác minh recordId/patientId/doctorId/departmentId từ hồ sơ và identity staff thật.
- [ ] Xác minh careEpisodeId từ public workflow có explicit OUTPATIENT_VISIT;
  không dùng recordId/appointmentId/accountId thay episodeId.
- [ ] priceCode từ catalog/policy Billing được xác nhận; không mặc định DRUG chỉ vì test từng dùng.
- [ ] Context thiếu thì hiển thị thiếu đầu vào và chặn submit. Nếu chưa có mobile Clinical handoff,
  cho nhập giá trị đã đối chiếu từ public workflow trong môi trường test, ghi nguồn evidence;
  không import Clinical repository vào feature Pharmacy hoặc gọi DB/internal authority APIs.

Đóng khi có quy trình lấy đầy đủ nguồn thật và test không suy authority; entrypoint mobile tích hợp
cần Vinh composition. Không bắt Clinical tạo Lab order chỉ để đoán episode cho kê đơn.

### MP-HUY-08 — Tạo Rx ngoại trú V1 và retry

- [ ] Form chọn thuốc từ catalog thật, lines/quantity/dosage/date/context, confirmation trước gửi.
- [ ] Một intent giữ frozen payload + UUID key trong memory; explicit retry giữ nguyên cả hai.
- [ ] Unknown outcome khóa sửa draft; hướng dẫn kiểm kết quả, không tạo key mới để resend cùng ý định.
  Đổi phiên/mode hoặc rời flow hủy local intent; không lưu PII/intents vào storage mới.
- [ ] Test double tap, 400/401/403/409/422/503, timeout, retry/success, stale session, V1 disabled.
  Không fallback V0 khi V1 lỗi; legacy chỉ khi cấu hình compatibility tường minh.

Đóng local sau transport header thật được bind/test. Native/web process restart chưa có recovery
command nếu backend không cung cấp public lookup theo key; ghi giới hạn thay vì hứa khôi phục.

### MP-HUY-09 — Hủy đơn

- [ ] ADMIN/DOCTOR, confirmation có reason; assigned-doctor authority do backend kiểm.
- [ ] Terminal status không mở lại thao tác; pending khóa đôi, success dùng result server rồi refresh.
- [ ] Unknown/conflict refresh để xác nhận, không tự resend; failed refresh không phủ nhận command đã thành công.
- [ ] Test owner/foreign doctor, pharmacist deny, cancelled/dispensed/expired/failed và đổi ID giữa request.

### MP-HUY-10 — Xuất thuốc V1 và compatibility V0

- [ ] Mode outpatient: paymentConfirmed=false không là điều kiện chặn xuất duy nhất;
  hiển thị trạng thái quan sát, cho role hợp lệ gửi command với confirmation khi state cho phép.
- [ ] Backend quyết định clearance tại PUT: unpaid/expired/revoked/Billing unavailable phải giữ lỗi,
  không tự gán paid hoặc giảm stock. GET refresh không là chứng nhận command sẽ được chấp nhận.
- [ ] Mode legacy giữ quy tắc V0 explicit; giữ role, terminal và pending guards; PUT không body.
- [ ] Test V1 false→command, unpaid/clearance errors, V0 unpaid deny, duplicate click,
  lost response→refresh, successful command nhưng follow-up read lỗi, không fallback/auto retry.

Đóng local bằng contract/widget tests; một phiếu xuất/stock giảm đúng một lần cần MP-HUY-12 thật.

### MP-HUY-11 — Nối ứng dụng cùng Vinh

- [ ] Bind repository tại app/dependencies.dart; register catalog/detail/new/stock/Rx/new/detail routes
  với thứ tự tránh `new` bị đọc thành UUID. Routes chỉ là navigation, không là context API mới.
- [ ] Nested route guard và selected module theo prefix; ADMIN/DOCTOR/PHARMACIST đúng role từng action.
- [ ] Mode outpatient/legacy/invalid từ config, không có public toggle tự chuyển backend profile.
  Shared session tiếp tục bỏ callback token cũ; không cho response phiên cũ thay nội dung người mới.
- [ ] Integration widget tests root routing/DI/role/deep link/back; update implemented chỉ sau
  feature đã bind và có smoke thực tế, không đánh dấu xong bằng placeholder.

Đây là task phối hợp, chưa giao quyền shared cho Huy; feature tests dùng repository overrides hợp lệ.

### MP-HUY-12 — Nghiệm thu thật và bàn giao

- [ ] Chạy format/analyze/tests/web build bằng tool/check.ps1; lưu candidate và failure counts,
  phân biệt focused và full. Tests không thay device/browser acceptance.
- [ ] Actual issuer DOCTOR tạo V1, CASHIER thanh toán qua Billing public flow hiện có,
  PHARMACIST refresh/xuất; API IDs/correlation, stock và một effect được backend verification chứng minh.
- [ ] ADMIN/PHARMACIST catalog/create/stock, foreign doctor deny, forbidden role, unpaid,
  terminal, service unavailable, timeout/retry và session expiry/new-login matrix thật.
- [ ] Test touch/double tap, keyboard/focus/semantics, font scale, small viewport, light/dark,
  rotate/back và loading; không overflow hoặc lộ nội dung phiên trước.
- [ ] Web proxy local trước; Android emulator/device khi SDK/license sẵn.
  Build Android và kiểm HTTP-debug/HTTPS-release policy; iOS cần macOS/Xcode, Windows cần C++ toolchain.
  Chưa có thiết bị/toolchain ghi NOT RUN, không gọi web pass là native pass.
- [ ] Evidence không secrets/PII thật, verdict local/integrated/native riêng và giới hạn rollback;
  cập nhật plan/README và handoff chung, không tạo nhiều handoff.

## 5. Shared deliverables và thứ tự chạy

Hiện ApiClient chỉ GET/POST, POST body bắt buộc và chưa có command headers. Cần Vinh bổ sung
PUT JSON/bodyless và optional Idempotency-Key theo contract; giữ Gateway guard/envelope/errors,
không tự retry mutations. Test request headers không rò sang request khác, bodyless không gửi
JSON giả và 401 của token cũ không xóa phiên mới. Không cần thay package/framework để làm bước này.

Thứ tự: **01 → 02 → 03 → 06** để có lát cắt đọc; **11** nối read slice ngay khi sẵn sàng.
Tiếp **04/05**, **07 → 08**, **09/10**, rồi hoàn tất **11 → 12**.
Shared transport có thể được Vinh làm trong lúc Huy làm read slice; Billing UI mới và Report
coverage không nằm trên đường chặn triển khai Pharmacy. Backend/provider unavailable được báo
đúng lỗi; muốn nghiệm thu mutation phải có môi trường test được phép với gates đã được owner duyệt.

Mỗi checkpoint ghi: task/subcriteria đạt, files, API/roles, kết quả test, bằng chứng runtime,
dependency và bước tiếp theo. Chỉ tick toàn task khi đủ điều kiện; phần code xong và nghiệm thu
thật ghi riêng. Không tạo nhánh/commit/push tự động; không sửa FE working diff khi làm mobile.

## 6. Checkpoint ban đầu — lịch sử trước implementation

- Plan đã được tạo theo yêu cầu; **0/12 implementation tasks**, Pharmacy còn placeholder.
- Flutter/Dart có trong PATH; chưa chạy doctor/analyze/test/build hoặc backend/device smoke trong lượt lập plan.
- Chưa thay mobile production, pubspec, platform hosts, shared routing/transport hoặc backend gates.
- Task bắt đầu: **MP-HUY-01**, sau đó **02/03/06**; shared dependency đã liệt kê để phối hợp,
  không coi là lý do dừng phần feature làm được độc lập.

## 7. MP-HUY-01/02/03 — 2026-10-20

Working diff trên Huy, không tạo nhánh/commit/push. **3/12 task local, 25%**.
[Contract và integration manifest](../lib/features/pharmacy/README.md) khóa live paths/roles,
DTOs/page/errors, mode không suy từ read response và composition cần phối hợp.

- Pure Dart models/repository/use cases, strict DTO decoder, Gateway adapter GET thuốc/page/Rx;
  chưa có Rx UI hoặc mutations. Thiếu/sai facts không biến thành empty/zero/paid=false.
- Catalog search/paging/refresh/detail, submitted query riêng draft, local navigation và callback
  cho router. Scope boundary cần injected repository + reactive session access; thay phiên/repo
  remount và bỏ late response. Không import auth feature hoặc tự dựng data adapter trong widget.
- Không retry tự động; lỗi 401/403/404, malformed/invalid ID không hiện retry. 5xx/network có
  explicit GET retry. Loading clear snapshot; detail giữ đúng ID và chỉ hiện stock server trả.
- Widget test 320px/font scale 2 light/dark ban đầu bắt overflow; đổi detail affordance sang Wrap,
  hai trường hợp đạt lại, không giảm cỡ chữ hoặc bỏ assertion.
- Full app + loader: **87/87 tests PASS, exit 0**; gồm 59 test Pharmacy mới ở ba file test.
  Analyzer `flutter analyze --no-pub`: **no issues**. Format check toàn lib/test/tool:
  **55 files, 0 changed**. Raw log local `.git/mobile-pharmacy-full-final.log`.
- Web release build `flutter build web --no-pub --dart-define=GATEWAY_URL=http://localhost:4300/api`
  **PASS, exit 0, 127.3s**; raw log `.git/mobile-pharmacy-web-final.log`. Flutter cảnh báo thiếu
  font CupertinoIcons trong cấu hình hiện có; không thêm dependency/shared asset ngoài scope.
  Build không chứng minh feature đã được bind, API thật hoặc native acceptance.
- `flutter pub get` tải/resolved dependencies nhưng exit lỗi Windows symlink support;
  không ghi command này PASS. Dùng package config đã resolve để analyze/test/build với `--no-pub`,
  không sửa Developer Mode, dependency versions hoặc platform settings.
- Không có actual issuer/Gateway/browser/device acceptance mới. App shared binding chưa làm,
  module vẫn chưa mark implemented; màn hình chưa bind hiển thị chưa sẵn sàng. Tests dùng doubles
  trong test scope, không có mock/demo dữ liệu runtime. Native Android/iOS/Windows NOT RUN.

MP-HUY-04/05 và 06 là các bước tiếp theo theo plan. Shared transport/DI/routes/session composition
vẫn thuộc MP-HUY-11, ghi một đầu mối trong handoff chung hiện có.
