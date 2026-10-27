# Plan mobile Pharmacy — Huy

**Ngày lập:** 2026-10-20. **Owner:** Huy (`LQHuy0210`).
**Baseline:** `8883f49d`, nhánh Huy; FE working diff hiện có giữ nguyên.
**Tiến độ code:** **11/12 task (91,7%)** — MP-HUY-01–11 hoàn tất implementation.
Người dùng đã giao thêm shared network/app/config và chọn **hoàn thành code, test thật sau**.
Task12 đã PASS happy path Gateway trên mobile web (26/10), còn matrix lỗi/quyền và native;
không lấy test mock hoặc build thay nghiệm thu thật.
Checkpoint triển khai mới nhất ở mục14; mục13 ghi shared integration, các mục7–12 là lịch sử.

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
Trạng thái lúc lập plan: PharmacyPage còn placeholder. Sau mục 7–8 đã có danh mục/chi tiết,
form thêm thuốc/điều chỉnh tồn và tra cứu đơn cùng domain/data/tests. App binding vẫn ở task 11.

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

Override ngày2026-10-22: người dùng giao Huy các shared paths trong bảng để nối Pharmacy.
Không import feature khác;
composition root truyền context/adapter, presentation không tự dựng concrete repository.
Dependency phối hợp ghi trong [handoff chung Huy](../../docs/handoffs/history/2026-10-22-huy-mvp-checkpoints.md),
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

### MP-HUY-04 — Thêm thuốc — LOCAL DONE

- [x] Form đúng CreateDrugRequest: tên/đơn vị/giá/tồn/hạn dùng/threshold và fields optional.
- [x] Mirror backend limits: tên 150, đơn vị 20, giá không âm với precision contract,
  stock/threshold không âm, expiry không quá khứ. Server vẫn quyết định kết quả cuối.
- [x] ADMIN/PHARMACIST, pending khóa submit đồng bộ, field errors/focus và thành công mở ID server.
- [x] Test invalid/403/409/network/503. Request này chưa có idempotency contract:
  response không rõ phải kiểm kết quả trước lệnh mới, không tự resend hoặc tuyên bố exactly-once.

### MP-HUY-05 — Điều chỉnh tồn kho — LOCAL DONE

- [x] PUT delta quantity không bằng 0; lý do có giới hạn backend, confirmation nêu rõ tăng/giảm.
- [x] Chỉ ADMIN/PHARMACIST; không cho âm stock bằng optimistic update; dùng DrugDTO trả về.
- [x] Pending khóa đôi, conflict/unknown outcome refresh trước lệnh mới; không auto retry delta.
- [x] Test số âm/dương/0, reservation constraint, forbidden, lỗi refresh và không áp delta hai lần.

MP-HUY-04/05 dùng POST/PUT JSON đã có trên master qua shared ApiClient, không còn chờ transport
cho hai lệnh này. Chưa cần Idempotency-Key/bodyless PUT trong hai task này; binding ở task 11.

### MP-HUY-06 — Tra cứu và chi tiết đơn thuốc — LOCAL DONE

- [x] UUID lookup, chi tiết lines/amounts, trạng thái đơn và xuất kho tách biệt, cancellation audit.
- [x] Refresh explicit GET, loading/error/empty-not-found/403 riêng, correlation và stale/session guards.
- [x] Mode là input composition tường minh qua provider, mặc định unavailable; unknown đóng command,
  không đoán từ GET hoặc lỗi API. Root config binding còn ở task 11, read slice chưa có Rx commands.
- [x] Test nullable recordId, paymentConfirmed=false, các trạng thái terminal, 401/403/404/503.

Không có GET danh sách prescriptions: không làm fake history, không suy dữ liệu từ thuốc hoặc DB.

### MP-HUY-07 — Đầu vào kê đơn từ public workflow — LOCAL DONE (tự lấy theo hồ sơ)

- [x] Xác minh recordId/patientId/doctorId/departmentId từ hồ sơ và identity staff thật.
- [x] Xác minh careEpisodeId từ public workflow có explicit OUTPATIENT_VISIT;
  không dùng recordId/appointmentId/accountId thay episodeId.
- [x] priceCode từ catalog/policy Billing được xác nhận; không mặc định DRUG chỉ vì test từng dùng.
- [x] Context thiếu thì hiển thị thiếu đầu vào và chặn submit. Nếu chưa có mobile Clinical handoff,
  cho nhập giá trị đã đối chiếu từ public workflow trong môi trường test, ghi nguồn evidence;
  không import Clinical repository vào feature Pharmacy hoặc gọi DB/internal authority APIs.

Cập nhật 2026-10-26: adapter thật đọc public GET
`/api/v1/pharmacy/prescriptions/prescribing/context?recordId=...` đã có trong backend.
Chọn bệnh nhân tự tải hồ sơ theo patient scope; chỉ tự chọn khi toàn bộ kết quả có đúng một
hồ sơ, không còn trang sau. Nhiều hồ sơ cho chọn trong danh sách của bệnh nhân và tải thêm.
Bác sĩ/khoa/episode/nhóm thu lấy cùng một context server; không tự suy appointmentId từ episodeId,
không cho đổi khoa hoặc bác sĩ riêng. Một nhóm thu tự chọn, nhiều nhóm yêu cầu chọn từ server.
Lỗi/mismatched patient/đổi bệnh nhân xóa context và chặn tạo; response cũ bị bỏ qua.

Đóng khi có quy trình lấy đầy đủ nguồn thật và test không suy authority; entrypoint mobile tích hợp
cần Vinh composition. Không bắt Clinical tạo Lab order chỉ để đoán episode cho kê đơn.

### MP-HUY-08 — Tạo Rx ngoại trú V1 và retry — LOCAL DONE

- [x] Form chọn thuốc qua repository catalog, lines/quantity/dosage/date/context, confirmation trước gửi.
- [x] Một intent giữ frozen payload + UUID key trong memory; explicit retry giữ nguyên cả hai.
- [x] Unknown outcome khóa sửa draft; hướng dẫn kiểm kết quả, không tạo key mới để resend cùng ý định.
  Đổi phiên/mode hoặc rời flow hủy local intent; không lưu PII/intents vào storage mới.
- [x] Widget/domain tests double tap, 400/401/403/409/422/503, timeout, retry/success, stale session, V1 disabled.
  Không fallback V0 khi V1 lỗi; legacy chỉ khi cấu hình compatibility tường minh.

- [x] Adapter dùng shared POST với header Idempotency-Key; tests xác nhận retry cùng body/key,
  không gửi sourceReference hoặc giá tự tính, kiểm context của response.
App root bind writer chỉ khi cấu hình outpatient. Feature default vẫn null khi dùng độc lập.
Native/web process restart chưa có public recovery lookup theo key; không hứa khôi phục intent.
Request tests dùng HTTP adapter double, chưa gọi là signed Gateway acceptance.

### MP-HUY-09 — Hủy đơn — LOCAL DONE

- [x] ADMIN/DOCTOR, confirmation có reason; assigned-doctor authority do backend kiểm.
- [x] Terminal status không mở lại thao tác; pending khóa đôi, success dùng result server rồi refresh.
- [x] Unknown/conflict refresh để xác nhận, không tự resend; failed refresh không phủ nhận command đã thành công.
- [x] Local tests role hợp lệ/backend403 (gồm foreign-doctor denial contract), pharmacist deny,
  cancelled/dispensed/expired/failed và đổi ID giữa request. Actual doctor ownership còn ở12.

### MP-HUY-10 — Xuất thuốc V1 và compatibility V0 — LOCAL DONE

- [x] Mode outpatient: paymentConfirmed=false không là điều kiện chặn xuất duy nhất;
  hiển thị trạng thái quan sát, cho role hợp lệ gửi command với confirmation khi state cho phép.
- [x] Feature giữ quyết định clearance ở backend: unpaid/expired/revoked/Billing unavailable giữ lỗi,
  không tự gán paid hoặc giảm stock. GET refresh không là chứng nhận command sẽ được chấp nhận.
- [x] Mode legacy giữ quy tắc V0 explicit; giữ role, terminal và pending guards.
- [x] Widget/domain tests V1 false→command, unpaid/clearance errors, V0 unpaid deny, duplicate click,
  lost response→refresh, successful command nhưng follow-up read lỗi, không fallback/auto retry.

- [x] Adapter PUT không body qua shared ApiClient và request contract tests.

App root bind dispenser khi mode explicit outpatient/legacy; mode invalid/unavailable khóa command.
Không dùng raw Dio hoặc gửi `{}` thay bodyless. Một phiếu xuất/stock giảm đúng một lần còn ở
MP-HUY-12 thật; local request tests không thay backend acceptance.

### MP-HUY-11 — Nối ứng dụng — LOCAL DONE, actual smoke chuyển task12 theo người dùng

- [x] Bind repository tại app/dependencies.dart; register catalog/detail/new/stock/Rx/new/detail routes
  với thứ tự tránh `new` bị đọc thành UUID. Routes chỉ là navigation, không là context API mới.
- [x] Nested route guard và selected module theo prefix; ADMIN/DOCTOR/PHARMACIST đúng role từng action.
- [x] Mode outpatient/legacy/invalid từ config, không có public toggle tự chuyển backend profile.
  Shared session tiếp tục bỏ callback token cũ; không cho response phiên cũ thay nội dung người mới.
- [x] Root composition tests kiểm routing/DI/role; module implemented vì có feature và adapter thật.
  Theo yêu cầu mới **code trước, test thật sau**, actual smoke nằm task12, chưa nghiệm thu release.

Người dùng đã đổi quyết định và giao sửa shared app/core. Không còn dependency chờ owner nối app.
Test composition dùng production appOverrides/router, với HTTP double; không giả là backend thật.

### MP-HUY-12 — Nghiệm thu thật và bàn giao — PARTIAL (local verification)

- [x] Chạy format/analyze/tests/web build bằng tool/check.ps1; lưu candidate và failure counts,
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

Đã bổ sung optional POST Idempotency-Key và bodyless PUT theo quyền người dùng giao cho Huy.
Giữ Gateway guard/envelope/errors và token của request401; không tự retry mutation. Tests kiểm
header không rò sang request khác, bodyless không gửi JSON giả và request bị từ chối không replay.
Không thay package/framework. Cấu hình PHARMACY_MODE không bật backend gates.

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

## 8. MP-HUY-04/05/06 — 2026-10-22

**6/12 task local,50%**, base `d5373058` trên Huy, working diff chưa commit/push.
[Evidence và verification](evidence/huy-pharmacy/2026-10-22-local/README.md) ghi candidate,
checks và giới hạn. Không sửa FE, backend, shared app/core hoặc dependency versions.

- **04:** domain CreateDrug và adapter POST exact DTO; form validate tên/đơn vị/giá Java
  BigDecimal precision13/2, Java Integer, ngày thật không quá khứ, text optional/threshold
  nullable. ADMIN/PHARMACIST, confirmation, khóa đồng bộ, giữ draft/lỗi/correlation/focus,
  callback mở ID do server cấp. Ambiguous outcome/conflict khóa resend, hướng dẫn tra danh mục.
- **05:** PUT delta signed khác0/reason255, confirmation tăng/giảm, chỉ role quản lý kho.
  Không optimistic stock; success dùng snapshot server. Unknown response giữ khóa; GET lỗi
  không mở thao tác; GET thành công mới cho bắt đầu intent mới với delta/reason rỗng.
  Backend vẫn kiểm reservations/tồn/authority; GET không chứng minh lệnh cũ chưa thực hiện.
- **06:** UUID lookup/keyboard Search, detail lines/server totals/order-vs-dispense/audit,
  explicit refresh, loading/permission/not-found/unavailable/correlation riêng. Mode input
  composition mặc định unavailable; không suy V1 từ nullable record/false legacy flag.
  Chưa expose Rx mutation; binding config thật còn ở11.
- Boundary giữ session/role/repository/mode identity; đổi phiên đóng confirmation và xóa draft,
  late response không navigate hoặc thay facts; đổi ID bỏ kết quả cũ. Widget checks gồm
  320px/font-scale2 cả Sáng/Tối, invalid fields/focus,403/409/422/503/network, double-submit,
  failed-refresh và terminal Rx. Không dùng test fixtures làm dữ liệu runtime.

Standard `tool/check.ps1` **PASS exit0**: dependencies resolved, format **95 files/0 changed**,
analyzer **no issues**, full app+loader **186/186 PASS**, web release build **PASS108,2s**.
Wasm dry run PASS; cảnh báo font CupertinoIcons từ cấu hình hiện có vẫn được ghi ở evidence,
không thêm package/shared asset ngoài scope. Các lỗi test helper/focus/lint ban đầu đã được
sửa và chạy lại; không nới backend validation. Raw log local `.git/mobile-pharmacy-next-check.log`.

Verdict **LOCAL IMPLEMENTATION PASS**, integrated Gateway/browser/native **NOT RUN**. App
composition chưa bind Pharmacy, module chưa mark implemented. Task07 nguồn kê đơn và08–10
Rx commands tiếp theo;11 cần exact shared integration/transport header/bodyless theo mục5,
12 cần actual issuer/Gateway và device. Không cần Report coverage hoặc Gateway route mới
cho Pharmacy mobile; chỉ một handoff chung, không tự tạo nhánh/commit/push.

## 9. MP-HUY-07/08/09 và kiểm khung mobile — 2026-10-22

**8/12 task local,66,7%**:01–07 và09 DONE;08 PARTIAL. Base `d5373058`, nhánh Huy,
working diff chưa commit/push. [Evidence Rx](evidence/huy-pharmacy/2026-10-22-rx/README.md)
thay checkpoint kiểm tra186 tests cho candidate mới, không sửa evidence lịch sử.

- **07 LOCAL DONE ở luồng nhập nguồn đã xác minh:** public MedicalRecordDTO có đúng record,
  patient, doctor staff và department. ClinicalLabOrderDTO của Lab order thực có nhu cầu có
  explicit episode/type; workflow disposable đã được ký ở evidence FE2026-10-19. Billing policy
  xác nhận mã giá theo môi trường. Form để trống IDs/priceCode, yêu cầu nguồn đối chiếu; validate
  thiếu/sai UUID/ngày/lines thì chặn. Không gọi SYSTEM-only prescription-context, không suy từ
  appointment/record hoặc tự tạo Lab order. Chưa có automatic Clinical mobile context producer,
  public episode source tổng quát cho visit chỉ kê thuốc hoặc public catalog selection; đây là
  dependency tích hợp production trong handoff, không giả đã có public API tương ứng.
- **08 PARTIAL:** form dùng repository catalog, lines/quantity/dosage/date, confirmation và
  pure Dart immutable intent+canonical UUID key; wire mapping V1 chính xác, không gửi giá/actor.
  Retry explicit cùng object/key/body, unknown khóa sửa draft, 400/422 cho chủ động sửa bản nháp
  bị từ chối, không fallback V0. Người dùng chọn giữ shared network thành dependency: chưa có
  header POST, chưa có real writer adapter; provider mặc định null và nút gửi khóa. Tests writer
  doubles kiểm behavior, không là bằng chứng header hoặc POST integration. Task này chưa tick DONE.
- **09 LOCAL DONE:** PUT cancel qua ApiClient với reason bắt buộc/max500, role ADMIN/DOCTOR,
  ACTIVE/PENDING, explicit mode, synchronous pending lock và confirmation. Server kiểm doctor
  ownership. Decode strict ID/status, không tự release stock/reservations. Thành công giữ receipt
  qua read503;403/conflict/unknown không resend, explicit GET giữ lỗi/correlation và khóa lệnh.
  Đổi ID/session bỏ late response; mode/role đổi remount và đóng confirmation.
- **Khung hiện có:** inward imports, domain thuần Dart, presentation không data/cross-feature,
  data chỉ shared ApiClient/Gateway. Giữ Riverpod/Material3 theme từ app, Navigator nội bộ,
  shell/sidebar/drawer/router/config/package/platform. Không tạo app con/HTTP client khác.
  Widget320px/font2 light/dark đạt. App DI/session/mode/routes chưa bind; không gọi source/theme
  checks là nghiệm thu browser so ảnh hoặc native thực tế.

`tool/check.ps1` **PASS exit0**: pub get đạt, format **100 files/0 changed**, analyze **no issues**,
full app+loader **234/234 PASS** (thêm48 tests domain/data/widget), web release **PASS77,5s**,
Wasm dry run PASS. Warning CupertinoIcons từ cấu hình có sẵn vẫn ghi trong evidence. Các lỗi
helper cuộn/transition và lint ban đầu đã sửa rồi chạy lại toàn bộ; không bỏ assertion hoặc nới
backend validation. Raw log local `.git/mobile-pharmacy-rx-check.log`.

Verdict LOCAL IMPLEMENTATION PASS; actual Gateway/browser/native **NOT RUN**. Task08 còn transport
và real writer adapter;10 bodyless dispense,11 shared composition,12 actual acceptance. Không sửa
shared/foreign/backend/gates hoặc FE, không thêm package và không tự tạo nhánh/commit/push.

## 10. MP-HUY-08/10 — 2026-10-22

**8/12 task đóng local (66,7%)**, gồm01–07 và09; **08/10 PARTIAL**,11/12 chưa hoàn tất.
Hai task lượt này đã triển khai hết phần feature độc lập, chưa đủ tiêu chí đóng vì shared network
được người dùng giữ thành dependency. [Evidence hiện hành](evidence/huy-pharmacy/2026-10-22-dispense/README.md)
và source hashes ghi đúng working candidate trên Huy/base `d5373058`; chưa commit/push.

| Task | Đã triển khai trong lượt này | Phần còn lại |
|---|---|---|
|08 Tạo Rx V1| Lỗi Java field path đúng quantity/dosage; sửa draft focus field lỗi; catalog rỗng totalPages0; retry network/429 và đóng confirmation giữ nguyên intent/key/body | Shared POST header, real writer adapter/request tests |
|10 Xuất thuốc| Explicit mode V1/V0; ADMIN/PHARMACIST, ACTIVE/PENDING; confirmation/pending/session guards; strict server receipt gồm actor type; phân biệt FAILED/DISPENSED; giữ receipt qua read lỗi; denial/unknown không tự resend/paid/stock | Shared PUT không body, real dispenser adapter/request tests |
|11 Nối app| Feature giữ khung hiện có, hai capability provider mặc định null và command khóa | Root DI/routes/access/session/mode và integration tests |
|12 Nghiệm thu| Full local checks và evidence candidate | Signed Gateway/browser/device/native và stock/one-effect thật |

Standard `tool/check.ps1` **PASS exit0**: pub get đạt, format **103 files/0 changed**, analyzer
**no issues**, full app+loader **284/284 PASS** (thêm50 so checkpoint Rx), web release
**PASS78,3s**, Wasm dry run PASS. Warning CupertinoIcons có sẵn được ghi, không sửa shared asset.
Raw log local `.git/mobile-pharmacy-dispense-check.log`. Đã sửa lint/helper và cập nhật kỳ vọng
cũ của V1 trước khi chạy lại toàn bộ; không bỏ kiểm quyền/trạng thái hoặc nới backend validation.

Các tests mới gồm V1 legacy false, V0 paid/unpaid, role và cả hai terminal state axes,
unpaid/expired/revoked denial,401/403/timeout/conflict/503, pending và late response/session đổi,
receipt sau read503, FAILED receipt, strict actor fields và 320px/font2/light/dark.
Đây là doubles kiểm behavior client; clearance thật/stock không trừ hai lần và actual UI qua
Gateway chưa nghiệm thu. Không gọi test/build là browser acceptance; không bật gates.

Giữ presentation → domain ← data, Material3/theme/Riverpod/Navigator có sẵn; shared app/core,
package/platform/backend/FE không sửa. Dependency cập nhật đúng một handoff chung hiện có.

## 11. MP-HUY-11/12 — giới hạn được xác nhận và regression UX, 2026-10-22

Người dùng chọn **giữ cả phần nối app thành dependency**. Vì vậy11 chưa sửa shared production
hoặc tests; không dựng runtime harness để thay app composition. Task12 PARTIAL: đã kiểm phần
local và môi trường, actual Gateway/browser/device/native vẫn NOT RUN. Tổng **8/12 DONE**,
08/10/12 PARTIAL,11 DEPENDENCY. Không tăng số task đóng khi chưa đạt đủ tiêu chí.

- Phát hiện lỗi catalog RenderFlex overflow khi xoay landscape720×320/font2. Header và
  pagination được giới hạn và cuộn theo chiều cao khả dụng; giữ danh sách, theme và shell có sẵn.
  Thêm4 widget regression cases về rotation/keyboard search/back bỏ draft ở cả Sáng/Tối.
- Full local suite **288/288 PASS**, format **104 files/0 changed**, analyzer **no issues**.
  Web build và source hashes ghi ở [verification hiện hành](evidence/huy-pharmacy/2026-10-22-readiness/verification.json),
  không dùng evidence284 tests cho source mới. Không đổi API/schema/gates/package/shared paths.
- Docker Linux Engine không kết nối được; Gateway8080/mobile4300/4301 không có listener lúc kiểm.
  Flutter nhận Chrome/Edge/Windows; Android SDK36 thiếu cmdline-tools/license status unknown,
  có AVD Pixel_7 nhưng không có Android device kết nối. Windows native thiếu C++ toolchain,
  iOS cần macOS. Không tự cài/accept licenses để che NOT RUN.
- Các điều kiện còn lại: shared owner nối reactive access/session/config/repo/routes và transport,
  Huy thêm real command adapters; môi trường issuer/Gateway/backend với nguồn IDs/price policy
  thật để nghiệm thu clearance/stock/one-effect. Không yêu cầu endpoint Gateway mới.

[Evidence task11/12](evidence/huy-pharmacy/2026-10-22-readiness/README.md) và handoff chung giữ
phân biệt phần code, local tests và integrated/native acceptance. Chưa commit/push/tạo nhánh.

## 12. Runtime sau khi người dùng mở Docker — 2026-10-22

Đã start PostgreSQL/RabbitMQ healthy, package current source Eureka/Gateway/Core/Pharmacy
(-DskipTests, không ghi là backend test PASS) và chạy local Java21. Health cả bốn service UP.
Mobile web4300 dùng build readiness đã có, browser mở login thật; proxy Pharmacy không token401.
[Evidence runtime](evidence/huy-pharmacy/2026-10-22-runtime/README.md) ghi health và screenshot.

Pharmacy boot với database cũ bị chặn do bảng có sẵn nhưng thiếu Flyway history. Không sửa/xóa
hoặc baseline dữ liệu cũ; dùng database test riêng `mediflow_pharmacy_mobile_20261022`, đủ20
migrations đếnv20. Runtime này chưa có dữ liệu nghiệp vụ Pharmacy, không gọi là acceptance cũ.

Docker/Gateway không còn là blocker startup. Shared network/root app binding vẫn dependency
theo người dùng; chưa issuer login/command adapters/Rx→payment→dispense acceptance, chưa bật gates.
Tiến độ đóng task vẫn8/12. Không sửa production app/core/shared hoặc tạo nhánh/commit/push.

## 13. Shared integration sau khi người dùng giao quyền — 2026-10-22

**11/12 task code (91,7%)**. Người dùng thay đổi quyết định giữ dependency, giao triển khai mạng
và app; sau đó chọn **hoàn thành code, test thật sau**. Các giới hạn ownership của checkpoint cũ
không còn chặn những shared paths đã được giao. Không tạo nhánh/commit/push.

- MP-HUY-08: shared POST optional Idempotency-Key, adapter tạo V1 và retry cùng frozen intent;
  kiểm response khớp record/patient/doctor/department, không gửi authority giả hoặc giá tự tính.
- MP-HUY-10: shared PUT optional body; dispense adapter không body, strict audit receipt và
  backend errors giữ nguyên. Không đổi paymentConfirmed, retry tự động hoặc giảm tồn ở client.
- MP-HUY-11: repository/session/mode/writer/dispenser bind tại composition root; catalog/new/
  detail/stock/Rx lookup/new/detail routes, prefix role guard/selected module, back về catalog.
  Giữ Riverpod/GoRouter/Material3 sáng/tối/package/platform; implemented là trạng thái code.
- PHARMACY_MODE nhận outpatient/legacy, mặc định và invalid unavailable; không suy mode từ
  response thiếu metadata, không public toggle hoặc tự bật backend gates.
- MP-HUY-12 còn actual issuer/Gateway workflow, clearance/stock một effect và native acceptance.
  Việc nghiệm thu thật để sau do người dùng chọn; không gọi tests/build là đã đạt nghiệp vụ thật.

Kết quả kiểm tra và source hashes cuối ở [integration evidence](evidence/huy-pharmacy/2026-10-22-integration/README.md).
Handoff shared network/app được đóng trong đúng handoff chung; chỉ giữ phần nghiệm thu/nguồn
workflow còn lại. Không mở Report/Notification mobile, Surgery/Inpatient/full V2.

## 14. Tiếp tục MP-HUY-12 và cập nhật nguồn kê đơn — 2026-10-26

Theo yêu cầu tiếp tục plan, rà lại code và chạy kiểm tra mobile trên nhánh Huy, baseline
`9edf9bcc`. Backend Docker hiện có hoạt động; không đổi gates, dữ liệu khởi tạo,
network/app/config chung hoặc source của feature khác.

- Bổ sung `PrescribingSource`/reader domain, public Gateway adapter và widget nguồn kê đơn
  trong đúng Pharmacy. App composition có sẵn tự dùng capability của adapter, không cần sửa shared.
- Mobile thực tế không còn yêu cầu nhập riêng khoa/bác sĩ/episode/mã giá/nguồn đối chiếu;
  ngày kê đơn mặc định ngày hiện tại. Reader kiểm UUID, matching record, OUTPATIENT_VISIT và
  priceCodes không rỗng trước khi nhận context; widget kiểm patient match trước gửi command.
- Giữ frozen intent, Idempotency-Key, xác nhận trước gửi, retry và session boundary hiện có.
- Format/analyzer/full tests/default web build đã chạy; kết quả và phần runtime được ghi ở
  [checkpoint 26/10](evidence/huy-pharmacy/2026-10-26-mobile/README.md).
- Runtime mobile UI PASS chọn patient → tự liên kết record/doctor/department/episode → tạo đơn;
  chặn dispense chưa thanh toán; Billing API thanh toán demo → mobile UI xuất thành công.
  Tồn 70→69; lặp dispense trả cùng phiếu và giữ 69. Chưa thử concurrency/timeout recovery.
- Đồng bộ priceCode Pharmacy đang chạy DRUG sang RX-DEMO của Billing/overlay demo; giữ
  datasource cũ bằng override local trong `.git/`, không đổi backend source hay bật thêm gates.
- PHARMACIST còn hạn chế lookup tên do quyền hiện có; ghi evidence, không sửa quyền/shared.
- Android thiếu cmdline-tools và trạng thái licenses chưa xác nhận; Windows thiếu C++ workload;
  iOS cần macOS. Chưa có native acceptance, không đánh dấu toàn bộ MP-HUY-12 DONE.

## 15. Ngôn ngữ giao diện Dược — 2026-10-26

Theo phản hồi khi xem bản mobile, bỏ các thuật ngữ giao thức và phiên bản khỏi màn kê đơn,
chi tiết đơn, xuất/hủy thuốc, danh mục và điều chỉnh kho. Lỗi Gateway trên các màn Dược được
diễn đạt thành hướng xử lý bằng tiếng Việt theo mã lỗi/trạng thái; mã hỗ trợ vẫn giữ để tra cứu.
Quyền xem tên hạn chế hiển thị một thông báo gọn, không lặp thông báo tại từng trường.
Ngày giờ hiển thị theo định dạng địa phương. Quyền xuất, thanh toán, dữ liệu nguồn và giao thức
gửi lệnh giữ nguyên; cờ thanh toán cũ chỉ hiện ở chế độ tương ứng, tránh gây hiểu nhầm là đủ
điều kiện xuất thuốc ngoại trú. [Evidence 26/10](evidence/huy-pharmacy/2026-10-26-mobile/README.md)
ghi kết quả kiểm tra cuối.

## 16. Đồng bộ master và tiếp tục giao diện Dược — 2026-10-27

Nhánh Huy trên máy đã nhận đủ 29 commit mới từ `origin/master` tới `e4f98f11`; các thay đổi
Pharmacy mobile đang làm được giữ nguyên, không có xung đột. Master không thay file mobile.
Sau đồng bộ, `tool/check.ps1` PASS: analyzer sạch, 349 tests PASS, web build PASS.
Rà thêm danh sách thuốc: hạn dùng hiển thị ngày/tháng/năm như trang chi tiết. Các mã ở phần
tham chiếu dùng nhãn tiếng Việt thay `ID`; hướng dẫn ngày kê đơn không còn ví dụ ngày cố định.
Các màn liên quan đã chạy lại 97 tests và analyzer sạch. Bản web cấu hình outpatient đã dựng
và phục vụ tại `http://localhost:4300`; Docker/Gateway hiện chưa chạy nên chưa thêm bằng chứng
runtime nghiệp vụ. `flutter doctor` còn thiếu Android cmdline-tools/license và Windows C++ workload;
không có Android device, vì vậy native acceptance chưa thể chạy ở máy này.
MP-HUY-12 vẫn PARTIAL với matrix lỗi/quyền và native chưa nghiệm thu.
