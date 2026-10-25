# Pharmacy mobile — contract và integration manifest

## Review3 — tên hiển thị, 2026-10-22

Chi tiết và form kê đơn có tên bệnh nhân/bác sĩ/khoa từ public Gateway, ID vẫn là thông tin
đối chiếu và đầu vào command. Domain `PharmacyIdentityReader` tách presentation khỏi adapter;
repository đang được app bind đã implement reader nên không sửa app/core/shared.
Riverpod dedupe theo resource, tự bỏ cache khi không còn consumer và reload khi đổi phiên;
không tự retry lookup lỗi. Lookup lỗi không chặn nghiệp vụ hoặc giả lập tên.
PHARMACIST giữ fallback vì Core/Gateway chưa cấp quyền public lookup; ADMIN/DOCTOR tra theo
quyền hiện hành. Gap và acceptance mới ở [handoff chung](../../../../docs/handoffs/history/2026-10-22-huy-mvp-checkpoints.md).
Chưa có nghiệm thu thật/ảnh Review3 mới; mốc 11/12 bên dưới không tự đổi thành 12/12.

## Hiện hành — shared integration đã code, 2026-10-22

**11/12 task implementation DONE; task12 nghiệm thu thật để sau theo yêu cầu người dùng.**
Người dùng đã đổi quyết định và giao shared network/app/config. App root bind repository,
reactive role/session, writer/dispenser và explicit PHARMACY_MODE. Routes catalog, thêm/chi tiết/
kho thuốc, tra cứu/tạo/chi tiết Rx đã đăng ký trong shell hiện có; nested route guard và selected
module dùng prefix đúng context. Module implemented biểu thị code đã nối, không là release gate.

POST V1 dùng shared ApiClient với header Idempotency-Key riêng từng request; explicit retry giữ
nguyên intent/body/key, response phải khớp record/patient/doctor/department. PUT dispense không
body; không gửi `{}`, không retry tự động, không tự đánh dấu paid hoặc giảm tồn.
Outpatient writer chỉ bind khi mode outpatient; dispenser bind outpatient/legacy, các mode khác
khóa command. Không suy mode từ Rx response, không thêm toggle UI, không bật backend gates.
Giữ Clean Architecture, Flutter/Riverpod/GoRouter/Material3 sáng/tối và packages/platform hiện có.

[Evidence hiện hành](../../../docs/evidence/huy-pharmacy/2026-10-22-integration/README.md).
Các checkpoint bên dưới ghi lịch sử, không còn là dependency chờ shared owner.

## Lịch sử checkpoint task11/12 — 2026-10-22

Người dùng giữ cả root app integration thành dependency. **8/12 local DONE**;08/10 PARTIAL,
11 DEPENDENCY,12 PARTIAL local. [Evidence mới](../../../docs/evidence/huy-pharmacy/2026-10-22-readiness/README.md).
Task12 phát hiện và sửa catalog overflow trong landscape/font2: header/pagination có vùng cuộn
theo chiều cao khả dụng. Tests mới kiểm rotation/keyboard search/back bỏ draft ở Sáng/Tối.
Không sửa app/core/router/config hoặc tests shared; không runtime harness thay root integration.
Docker Engine/Gateway chưa chạy lúc kiểm, Android thiếu tooling/license và Windows thiếu C++;
không có actual Gateway/browser/native acceptance mới. Checkpoint08/10 dưới đây là lịch sử.

## Checkpoint MP-HUY-08/10 — 2026-10-22

**8/12 local DONE;08/10 PARTIAL;11/12 chưa hoàn tất.**
[Evidence hiện hành](../../../docs/evidence/huy-pharmacy/2026-10-22-dispense/README.md).

- Tạo V1: lỗi field `lines[0].quantity`/`lines.0.dosage` hiển thị đúng dòng, sửa draft bị
  từ chối đưa focus về field lỗi; catalog rỗng với totalPages0 có thông báo. Retry network/429
  và quay lại confirmation vẫn giữ cùng frozen intent/key/body. Không có real POST adapter.
- Xuất: ADMIN/PHARMACIST, ACTIVE/PENDING; mode outpatient bỏ điều kiện boolean legacy,
  mode legacy vẫn đòi paymentConfirmed. Mode không cấu hình không mở lệnh; không suy mode từ GET.
  Confirmation khóa đồng bộ và chặn hủy song song; đổi phiên/mode/capability bỏ kết quả cũ.
- `PrescriptionDispenser` là domain capability; `DispensePrescription` kiểm phiếu khớp Rx,
  trạng thái terminal, actor/time khi DISPENSED, lý do khi FAILED. DTO giữ actor type
  STAFF/ACCOUNT/SYSTEM/LEGACY_UNKNOWN hoặc null đúng wire, không tự suy danh tính.
  Receipt máy chủ tồn tại qua refresh lỗi; FAILED không hiển thị thành xuất thành công.
- Unpaid/expired/revoked và lỗi Billing giữ nguyên lỗi/correlation; không tự gán paid hoặc giảm
  kho. Rejection đã biết cần GET thành công và confirmation mới; unknown/timeout/conflict/5xx
  giữ khóa đối soát, GET không tự mở resend. Không tự retry mutation hoặc fallback V0.
- `prescriptionDispenserProvider` mặc định null; command khóa và báo dependency bodyless PUT.
  Shared owner cần POST header và PUT không body; sau đó Huy mới nối adapter thật qua ApiClient.
  Không bypass mạng, không gửi `{}` giả bodyless. Widget/domain doubles không chứng minh
  clearance/stock/one-effect thật. Task11 root binding và12 actual acceptance vẫn còn.

## Checkpoint MP-HUY-07/08/09 — 2026-10-22

**8/12 local**: 01–07 và09 đóng local;08 PARTIAL. [Plan](../../../docs/huy-pharmacy-mobile-plan.md)
phân biệt implementation và actual Gateway/native acceptance.

- `CreatePrescriptionPage` dùng Material/Theme/Riverpod hiện có. ADMIN/DOCTOR và mode outpatient
  tường minh; catalog qua repository, không nhập drugId tự sinh. Context trống ban đầu, validation
  UUID/ngày/Integer/dosage, nguồn đối chiếu bắt buộc; không mặc định mã giá hay suy episode.
- `PrescriptionIntent` giữ command/lines immutable và UUID command key trong memory; retry bằng
  writer interface giữ đúng object/key/body, không tự gửi lại hoặc fallback V0. Không persist PII.
  400/422 cho chủ động sửa draft bị từ chối; unknown giữ khóa draft và chỉ explicit retry cùng key.
- **Chưa có POST adapter V1 thật** vì shared ApiClient chưa có header. Writer provider mặc định
  null, nút gửi khóa và báo dependency; writer doubles chỉ nằm ở test. Theo người dùng, không sửa
  `mobile/lib/core/network/api_client.dart` hoặc `mobile/test/api_client_test.dart` trong lượt này.
- Hủy qua Gateway adapter PUT reason đúng CancelPrescriptionRequest (bắt buộc/max500), JWT actor
  không trong body. Chỉ ACTIVE/PENDING và ADMIN/DOCTOR, mode unknown đóng thao tác. Confirmation,
  pending/session/ID guards, strict result ID/CANCELLED; success giữ receipt dù refresh lỗi.
  401/403/conflict/unknown khóa resend, chỉ refresh GET; không tự release reservation/tồn.

### Nguồn đầu vào đã kiểm

| Đầu vào | Authority và giới hạn |
|---|---|
| recordId/patientId/doctorId/departmentId | Public GET `/api/v1/records/{id}` hoặc patient-record response; dùng đúng fields MedicalRecordDTO và đối chiếu signed staff/Core setup. Account UUID không thay doctor staff ID. |
| careEpisodeId/type | ClinicalLabOrderDTO của **chỉ định Lab thực sự có nhu cầu** trả explicit OUTPATIENT_VISIT/episode. Dùng nguồn này cho visit test phù hợp; không tạo Lab order thừa để lấy ID, không suy từ record/appointment. |
| priceCode | Policy Billing theo `mediflow.billing.prescription-charges.allowed-price-codes`, operator xác nhận mã được phép trong môi trường. Không có public catalog selection endpoint hiện hành; không lấy priceCode LAB từ Lab order làm mã thuốc. |
| bằng chứng nguồn | Nhập tham chiếu workflow public và policy đã đối chiếu, chỉ trong memory; không gửi như authority trong JSON, backend vẫn kiểm quan hệ/quyền/giá. |

[Bằng chứng workflow FE đã ký trước đây](../../../../frontend/docs/evidence/huy-six-service/2026-10-19/README.md)
và source-manifest tại đó xác nhận quy trình cho disposable visit, không phải mobile acceptance mới.
Chưa có mobile Clinical context producer hoặc public episode source tổng quát cho visit chỉ kê thuốc;
thiếu nguồn thì chặn, phối hợp Vinh trong handoff chung. `PrescriptionContextController` có path giống
public nhưng yêu cầu SYSTEM service principal; mobile tuyệt đối không gọi endpoint đó hoặc DB.
Mã `DRUG` chỉ hợp lệ khi policy môi trường xác nhận, không có default trong code.

### Đối chiếu khung có sẵn

Giữ presentation → domain ← data; domain không Flutter/Dio. Presentation chỉ import domain,
providers và shared widgets/network failure; không import adapter hoặc feature Clinical/Billing/auth.
Data HTTP chỉ shared ApiClient qua Gateway. Không đổi app shell/sidebar/drawer, Riverpod/GoRouter,
Material3 light/dark (seed hiện có), package/lockfile hoặc platform. Navigator nội bộ cùng cách mở
detail/form ở task04–06; app composition/routes/mode/session thật còn task11. Vì vậy widget pass
không đồng nghĩa frame ứng dụng chính đã bind Pharmacy. Chưa có screenshot/browser/native mới.

## Checkpoint 2026-10-22 — MP-HUY-01–06 LOCAL DONE

**6/12 task local**. Đã thêm domain commands + Gateway POST tạo thuốc/PUT điều chỉnh kho,
form validation/confirmation/role/pending guards và Rx UUID lookup/detail/refresh.
Nguồn contract khóa lại từ CreateDrugRequest/AdjustStockRequest/DrugController và DTO hiện hành.
Giá tối đa 13 chữ số nguyên/2 thập phân, số lượng Java Integer, expiry hợp lệ không quá khứ,
tên/đơn vị 150/20, optional text 150 và reason 255; threshold null để backend dùng default.

Từ danh mục có nút thêm thuốc (ADMIN/PHARMACIST) và tra cứu đơn (ba role đọc); chi tiết thuốc
mở điều chỉnh kho bằng Navigator nội bộ. Create success chuyển sang ID trả về từ máy chủ.
Boundary dùng session identity/role/repository/mode; đổi session đóng cả confirmation dialog
và bỏ callback kết quả cũ. Data adapter tiếp tục dùng duy nhất shared ApiClient.

Hai lệnh này chưa có idempotency contract: network/5xx/409 giữ khóa, không auto resend.
Create yêu cầu về danh mục kiểm kết quả. Stock yêu cầu GET thành công rồi bắt đầu intent mới,
xóa delta cũ; GET không chứng minh lệnh cũ chắc chắn chưa thực hiện. Không tự cộng/trừ stock;
chỉ hiển thị DrugDTO server. Lỗi reservation/field/correlation được giữ và focus field/lỗi.

Rx read có nullable record, audit và status đơn/xuất tách riêng. Cờ thanh toán legacy được
ghi đúng tên, không biến false thành unpaid V1. `pharmacyModeProvider` là composition input,
mặc định unavailable; không suy V1 từ read response và chưa expose Rx commands.
Task 11 vẫn cần bind provider từ cấu hình/session thật, routes và DI theo owner shared.

[Evidence local](../../../docs/evidence/huy-pharmacy/2026-10-22-local/README.md) phân biệt
tests/build với signed Gateway/native acceptance. Những mục read-only bên dưới là baseline
task 01–03; POST/PUT JSON shared hiện đã có, chỉ header/bodyless command còn thiếu cho 08/10.

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

## Nối shared app — đã thực hiện MP-HUY-11 theo quyền người dùng giao

1. `app/dependencies.dart` bind `pharmacyRepositoryProvider` bằng GatewayPharmacyRepository
   với shared apiClientProvider. Presentation chỉ dùng domain interface, không dựng Dio/adapter.
2. Bind `pharmacyAccessProvider` từ actual auth session trong composition root: watch session
   loading/logout/login/role change, trả null khi chưa có phiên, role lấy từ signed session.
   sessionIdentity là object opaque mới theo phiên, ổn định trong cùng phiên; không dùng token
   làm widget key/log. Không chỉ đọc mutable ApiSession bridge một lần vì nó không reactive.
3. Role/deep-link guards cho mọi route trong app/router.dart; feature boundary fail-closed khi
   chưa bind hoặc role ngoài ADMIN/DOCTOR/PHARMACIST. Module đã mark implemented cho code đã nối;
   đây không phải cờ nghiệm thu hoặc bật backend gate.
4. Catalog gọi callback root cho thêm/chi tiết thuốc, tra cứu/tạo đơn; routes stock/Rx detail hỗ trợ
   deep link. Navigation nội bộ của stock/lookup giữ hành vi feature khi dùng độc lập trong tests.
5. AppConfig nhận PHARMACY_MODE; app root map outpatient/legacy/giá trị khác fail closed,
   bind writer/dispenser theo capability. Shared transport không tự retry mutation.

Không có dữ liệu fixture runtime hoặc bypass auth để xem UI. Provider overrides trong widget tests
không là bằng chứng actual issuer/Gateway/native. Feature default không bind tiếp tục fail closed.
MP-HUY-01–11 đóng code; integrated/native acceptance ở MP-HUY-12 được người dùng để sau.
