# Rà soát tính liền mạch giao diện theo vai trò — 04/11/2026

## Phạm vi và trạng thái

Rà soát mã nguồn web, mobile và các hợp đồng tài khoản liên quan tại nền Git
`d9a1635b`. Đây là kết quả kiểm tra và đề xuất thiết kế, chưa triển khai các thay
đổi giao diện/API bên dưới. Không thực hiện thao tác cấp/reset trên tài khoản thật
và chưa nghiệm thu bằng trình duyệt hoặc thiết bị.

Yêu cầu: thao tác từ hồ sơ đang xem; không tìm lại bệnh nhân khi đã có ngữ cảnh;
quản lý tài khoản ngay trong hồ sơ nhân sự/bệnh nhân; người hỗ trợ không xem được
mật khẩu; đổi email đồng bộ tên đăng nhập; bác sĩ kê toa trong hồ sơ khám; web và
mobile cùng một luồng nghiệp vụ. Phạm vi ngoại trú hiện hành và phân quyền backend
vẫn áp dụng.

## Các phát hiện đã đối chiếu nguồn

Mức ưu tiên P1: trực tiếp cản trở luồng người dùng yêu cầu. P2: giảm thao tác thừa
và mất ngữ cảnh. Đây là ưu tiên cải thiện UX, không phải kết luận lỗ hổng bảo mật.

| Mã | Mức | Hiện trạng | Hệ quả | Hướng sửa |
|---|---|---|---|---|
| A1 | P1 | `StaffDetail.tsx` chỉ hiển thị hồ sơ, sửa và quay lại; phần bảo mật ở `AccountDetail.tsx`/`AdminAccountSecurity.tsx`. `StaffForm.tsx` chuyển sang trang tạo tài khoản riêng. | ADMIN phải qua hai khu vực để xử lý một nhân viên. | Đưa trạng thái và thao tác tài khoản vào hồ sơ nhân sự; tạo xong giữ nguyên hồ sơ. |
| A2 | P1 | `PatientAccountIssuance.tsx` khởi tạo `issued = null`, không đọc trạng thái tài khoản khi mở hồ sơ. | Hồ sơ mở lại vẫn hiện nút cấp ban đầu, không có phân biệt rõ chưa có/đã có tài khoản và không có reset tại đây. Backend có xử lý tài khoản cũ nên không kết luận việc này tạo trùng. | Tra liên kết bằng `patientId`; chỉ hiện một hành động chính phù hợp trạng thái. |
| A3 | P1 | `PatientAccountController` cho ADMIN/NURSE cấp tài khoản; `AccountManagementController` và Gateway chỉ cho ADMIN reset/xem trạng thái thư/gửi lại. | Không thể chỉ thêm nút reset vào UI của điều dưỡng. | Bổ sung thao tác theo hồ sơ bệnh nhân với kiểm tra quyền, chủ sở hữu và vai trò đích ở server. |
| A4 | P1 | Form nhân sự và bệnh nhân hướng người dùng sang quản lý tài khoản khi đổi email đã liên kết. `UpdateStaffService` và `PatientController` có chặn tương ứng. | Cùng một thay đổi thông tin phải thực hiện ở UI khác. | Đổi email ngay trong hồ sơ bằng lệnh bảo mật hiện có; giữ lý do và nhật ký. Không gửi thêm một lệnh cập nhật email hồ sơ độc lập. |
| D1 | P1 | `MedicalRecordDetail.tsx` đã có link kê đơn mang `patientId` và `recordId`, nhưng nằm trong “Tác vụ khác”, bên dưới phần hoàn tất khám. | Chức năng quan trọng khó thấy; người khám rời hồ sơ để kê đơn. | Đưa đơn thuốc thành phần chính trong workspace hồ sơ, trước hoàn tất. |
| D2 | P2 | `CreatePrescriptionForm.tsx` nhận sẵn ID nhưng vẫn cho chọn bệnh nhân/hồ sơ; tạo thành công chuyển sang chi tiết đơn. `PrescriptionDetail.tsx` quay về tra cứu, không có đường quay lại hồ sơ nguồn. | Dễ mất ngữ cảnh đang khám; thao tác quay lại dài. | Chế độ nhúng khóa ngữ cảnh nguồn, tạo xong hiển thị đơn ngay trong hồ sơ; trang đơn độc lập có link quay về hồ sơ đúng quyền. |
| N1 | P2 | `OrganizationDirectory.tsx` giữ tab trong local state, mặc định “Khoa”; `StaffDetail.tsx` quay lại `/organization`. | Nhấn quay lại có thể mất tab Nhân sự và bộ lọc. | Lưu tab/bộ lọc/trang vào URL, dùng đường quay lại được kiểm tra. |
| L1 | P2 | `LabDetail.tsx` có `returnTo` nhưng hồ sơ nguồn hiển thị ở thông tin kỹ thuật; hướng dẫn bàn giao vẫn yêu cầu thu ngân tìm bệnh nhân. | Bác sĩ/điều dưỡng xem kết quả từ hàng đợi thiếu đường trở về hồ sơ; hỗ trợ bàn giao còn thủ công. | Thêm mở hồ sơ nguồn cho vai trò được phép và hỗ trợ mở hàng chờ thu đã lọc đúng bệnh nhân/lượt khám cho ADMIN/CASHIER. Không cấp thêm quyền đọc bệnh án cho LAB_TECH/CASHIER. |
| P1 | P2 | `PatientExamResults.tsx` và `PatientLabResults.tsx` hiển thị hai danh sách riêng; phần khám không tập hợp xét nghiệm/đơn thuốc theo lượt. | Bệnh nhân tự nối các kết quả của cùng một lần khám. | Thiết kế “Lần khám của tôi” với kết quả theo lượt; trước khi triển khai phải xác minh/bổ sung API tự tra cứu đúng tài khoản, nhất là đơn thuốc. |
| N2 | P2 | `NotificationDTO` chỉ có bệnh nhân/nội dung/kênh/trạng thái, không có định danh tài nguyên đích. | Không thể tạo link chính xác đến phiếu khám/xét nghiệm từ thông báo chỉ bằng sửa UI. | Bổ sung hợp đồng đích có loại và ID đã được producer xác nhận; không bóc UUID hoặc URL từ nội dung thư. |
| M1 | P1 | Mobile `patient_page.dart` chưa có thao tác tài khoản trong hồ sơ; `organization_page.dart` có khu tài khoản ADMIN riêng. | Mobile chưa đáp ứng luồng hồ sơ tập trung. | Thêm cùng trạng thái và thao tác theo hồ sơ như web, qua domain/use case/repository. |
| M2 | P1 | Mobile `MedicalRecordDetailPage` có panel chỉ định xét nghiệm và hoàn tất, chưa có tạo đơn. “Kê đơn thuốc” hiện là lựa chọn hướng xử trí. `CreatePrescriptionPage` chưa nhận ngữ cảnh bệnh nhân/hồ sơ ban đầu. | Chọn hướng xử trí không tạo đơn; bác sĩ phải mở chức năng dược riêng. | Nối kê đơn thực tế với hồ sơ, truyền ngữ cảnh đáng tin cậy, cập nhật danh sách đơn khi lưu. |

Các đường dẫn trong bảng nằm dưới `frontend/src/features/`,
`backend/core-service/src/main/java/com/mediflow/`, hoặc `mobile/lib/features/`
theo tên thành phần; danh sách điểm tích hợp cụ thể ở cuối tài liệu.

## Những hành vi hiện đã đúng cần giữ

- Cấp tài khoản và reset gửi mật khẩu tạm qua email, không trả mật khẩu về UI.
  `IssuedAccount` và `MailDeliveryDTO` không chứa mật khẩu; `AccountResponse`
  không chứa password/hash. Reset ADMIN từ chối mật khẩu do người thao tác nhập.
- `AdminAccountSecurity` không có ô mật khẩu hoặc nút xem/copy mật khẩu. Không
  thay hành vi này bằng việc tải mật khẩu xuống rồi che bằng dấu chấm.
- Gửi lại thư là gửi lại cùng thông điệp, không reset thêm lần nữa. Yêu cầu
  reset/cấp tài khoản có idempotency và xử lý mất phản hồi; phải giữ khi nhúng UI.
- Đổi email bằng `SecurityAccountOperationsService.changeEmail` đã đổi username,
  ghi audit, vô hiệu hóa phiên/link khôi phục cũ, cập nhật nhân sự và xếp hàng đồng
  bộ hồ sơ bệnh nhân. Email bệnh nhân qua hai context là đồng bộ bất đồng bộ.
- Phiếu khám đã có đường “Mở hồ sơ khám” qua `ExaminationRecordLink`; hồ sơ bệnh
  nhân đã có “Tiếp tục đăng ký khám” mang sẵn patientId. Không cần xây lại các nối này.
- Thu ngân đã tìm bằng thông tin bệnh nhân và chọn khoản thu kèm bệnh nhân/lượt
  khám trong `PaymentRequestWorkspace`; không còn cơ sở nói mọi thao tác đòi nhập
  UUID. Báo cáo đã có biểu đồ và chọn kỳ có dữ liệu sau PR #571.
- Hộp thư demo có nội dung thông tin đăng nhập cho danh tính seed được backend
  xác minh. Đây là ngoại lệ mô phỏng đã được tài liệu hóa, không phải quyền xem
  mật khẩu tài khoản thật của ADMIN; phải duy trì cách ly khỏi tài khoản thật.

## Thiết kế đề xuất

### 1. Một mục “Tài khoản đăng nhập” trong từng hồ sơ

Thông tin: email đăng nhập, trạng thái chưa cấp/hoạt động/khóa, cần đổi mật khẩu,
trạng thái gửi thư. Không hiện mật khẩu cũ hoặc mới ở bất kỳ trạng thái nào.

| Trạng thái đã xác minh | Hành động chính | Hành động hỗ trợ |
|---|---|---|
| Chưa có tài khoản, email hợp lệ, hồ sơ hoạt động | Cấp tài khoản | Sửa email theo quyền |
| Chưa có tài khoản, thiếu email | Bổ sung email trước | Không cho gửi lệnh cấp thiếu dữ liệu |
| Đã có tài khoản | Cấp lại mật khẩu | Kiểm tra thư; gửi lại đúng thư khi cần |
| Tài khoản khóa | Hiện rõ trạng thái khóa | ADMIN xử lý khóa/mở; reset không tự mở khóa |
| Không tải được trạng thái liên kết | Thử tải lại | Không suy diễn là chưa có tài khoản |
| Lệnh cấp/reset chưa rõ kết quả | Đối soát cùng yêu cầu | Giữ idempotency key; tránh phát sinh mật khẩu mới |
| Hồ sơ đã lưu trữ/ngừng hoạt động | Hiện trạng thái | Không cấp mới trái điều kiện server |

Sau khi lưu bệnh nhân: về hồ sơ vừa lưu, có đúng một hành động tài khoản phù hợp
trạng thái. “Tiếp nhận khám” là hành động nghiệp vụ khác, không trộn vào nút cấp
tài khoản. Cấp tài khoản không phải điều kiện bắt buộc để khám ngoại trú.

Khu “Quản lý tài khoản” chung vẫn phục vụ ADMIN tra cứu khóa, audit và xử lý sự cố;
đặt trong phần quản trị nâng cao. Tác vụ thông thường bắt đầu và kết thúc ở hồ sơ.

### 2. API và quyền cần bổ sung

Đây là yêu cầu hợp đồng mới, không phải endpoint đã có:

- Tra tài khoản theo định danh hồ sơ `staffId`/`patientId`, trả metadata tối thiểu.
  Không ghép tài khoản bằng so khớp email; email có thể đổi. ADMIN được tra nhân sự
  và bệnh nhân; NURSE chỉ tra bệnh nhân trong phạm vi cho phép.
- Cấp lại mật khẩu, tra trạng thái/gửi lại thư theo hồ sơ bệnh nhân cho NURSE.
  Server tự xác minh account liên kết, đích phải là PATIENT, kiểm tra hồ sơ và quyền
  người thao tác; không nhận role hoặc accountId tùy ý từ UI làm căn cứ cấp quyền.
  Có audit actor/target/reason/correlation, idempotency và khóa chống yêu cầu lặp.
- Gateway và controller cùng kiểm tra quyền. Không mở generic endpoint reset
  ADMIN cho NURSE vì endpoint đó còn có thể nhắm tới nhân viên/quản trị viên.
- Chỉ trả trạng thái gửi; không trả nội dung thư chứa thông tin đăng nhập. Khôi
  phục tự phục vụ của người dùng vẫn dùng luồng quên mật khẩu hiện có.

### 3. Đổi email và tên đăng nhập từ một nơi

Trong hồ sơ, ADMIN chọn “Đổi email đăng nhập”, nhập email mới và lý do. Với hồ sơ
đã liên kết, dùng lệnh bảo mật hiện có rồi làm mới account/profile. Với hồ sơ chưa
liên kết, dùng cập nhật liên hệ theo quyền hiện hành. Không có hai ô email độc lập
cùng cho phép lưu những giá trị khác nhau.

Nhân sự: xác nhận email hồ sơ và username mới sau giao dịch Organization.
Bệnh nhân: hiển thị riêng “Đã đổi email đăng nhập” và “Đang đồng bộ hồ sơ” khi
chưa đọc lại được email mới từ Patient. Nếu hàng đợi lỗi, có trạng thái/retry cho
quản trị; không tự báo thành công toàn bộ hoặc gửi lại lệnh đổi email liên tục.
Cần xác minh/bổ sung API trạng thái đồng bộ trước khi UI cam kết khả năng này.

Giữ giới hạn đổi email hiện hành: yêu cầu này cho phép điều dưỡng hỗ trợ reset,
chưa có căn cứ tự mở quyền đổi email đăng nhập cho NURSE hay PATIENT. Nếu cần cho
người dùng tự đổi email về sau, cần luồng xác minh riêng; không dùng form liên hệ
để vượt quyền bảo mật.

### 4. Hồ sơ khám là nơi làm việc chính của bác sĩ

Trong cùng workspace, theo thứ tự: thông tin bệnh nhân/lượt khám → triệu chứng,
chẩn đoán → chỉ định và kết quả xét nghiệm → đơn thuốc → kết luận, hoàn tất.
Kê đơn phải dễ thấy, có danh sách đơn đã kê và trạng thái. Chọn “Kê đơn” mở phần
form ngay trong workspace, giữ nguyên bệnh nhân và record nguồn.

Tái sử dụng form hiện có, đọc lại prescribing context từ server để lấy bác sĩ,
khoa, lượt khám, giá; không coi query string là nguồn thẩm quyền. Chế độ nhúng
khóa bộ chọn bệnh nhân/record. Lưu xong cập nhật phần đơn thuốc, giữ vị trí và dữ
liệu khám. Không tự hoàn tất hồ sơ khi tạo đơn; không bắt mọi lượt khám phải có
đơn. Hồ sơ đã hoàn tất chỉ xem và thực hiện hành động backend thực sự cho phép.

Web ghép Medical Record và Pharmacy tại app route layer thông qua slot/callback;
không import chéo feature. Mobile ghép tại app layer qua callbacks/giá trị context
và use cases, giữ `presentation → domain ← data`.

### 5. Quy tắc điều hướng áp dụng mọi vai trò

Màn hình chi tiết mang theo đường quay lại nội bộ được kiểm tra, bộ lọc, trang
và bệnh nhân/lượt khám đang chọn. Không dùng URL đích tùy ý; không nhét hồ sơ bệnh
nhân hoặc mật khẩu vào URL. Sau thao tác có thay đổi dữ liệu phải đọc lại trạng
thái có thẩm quyền. Bản nháp chưa lưu cần được bảo vệ khi chuyển màn hình/đổi bệnh
nhân. Khóa thao tác lặp trong lúc xử lý; đổi phiên phải bỏ kết quả phiên cũ.

| Vai trò | Luồng chính cần liền mạch | Trọng tâm sau rà soát |
|---|---|---|
| ADMIN | Nhân sự/bệnh nhân → tài khoản tại hồ sơ → quay lại đúng danh sách | A1–A4, N1; giữ quản trị tài khoản nâng cao |
| NURSE | Lưu hồ sơ → cấp tài khoản khi cần → tiếp nhận; mở lại hồ sơ → reset | A2–A3, M1; không phải xin ADMIN làm thay reset bệnh nhân |
| DOCTOR | Lịch → phiếu khám → hồ sơ → xét nghiệm/đơn → hoàn tất | D1–D2, M2; đã có nối phiếu với hồ sơ, cần kê đơn trong workspace |
| LAB_TECH | Hàng đợi → xét nghiệm → nhập/kết luận → về hàng đợi | Giữ returnTo hiện có; không mở quyền bệnh án chỉ để thêm shortcut |
| PHARMACIST | Hàng đợi/tra đơn → kiểm tra → xuất thuốc → về đúng hàng đợi | Giữ kiểm tra thanh toán/tồn kho; đường quay lại có bộ lọc, không chỉ generic tra cứu |
| CASHIER | Hàng chờ thu → khoản thu theo lượt → hóa đơn/hoàn tiền | Giữ ngữ cảnh đã có; nối bàn giao theo bệnh nhân/lượt khi có quyền, không thao tác bệnh án |
| MANAGER | Báo cáo/khoa → xem chỉ số đúng kỳ/phạm vi | Giữ kỳ/phạm vi khi chuyển view; không cấp quyền tài khoản hoặc chi tiết lâm sàng qua biểu đồ |
| PATIENT | Lần khám của tôi → kết luận/xét nghiệm/đơn/tái khám | P1, N2; tập hợp qua API self-scope, không mở route bệnh án dành cho nhân viên |

## Thứ tự triển khai và điều kiện nghiệm thu

1. Hợp đồng lookup theo hồ sơ và thao tác bảo mật bệnh nhân có giới hạn; test
   Gateway/controller/use case trước khi nối UI NURSE.
2. Khối tài khoản trong hồ sơ nhân sự/bệnh nhân trên web và mobile, đổi email từ
   một nơi, trạng thái thư và đồng bộ. Sau đó giảm độ nổi bật trang tài khoản chung.
3. Nhúng kê đơn vào hồ sơ khám web và mobile, xử lý đơn đã tạo/bản nháp/đổi phiên.
4. Sửa đường quay lại, các link theo nguồn và lưu bộ lọc. Nhóm kết quả bệnh nhân và
   đích thông báo thành các lát cắt riêng sau khi chốt hợp đồng dữ liệu.

Các kiểm tra cần có khi triển khai:

- Mở hồ sơ chưa cấp/đã cấp/khóa/lưu trữ, tải lỗi và mở lại sau reload: đúng hành
  động, không tạo tài khoản chỉ vì lookup lỗi; không thay mật khẩu khi cấp lại
  tài khoản đã có nếu người dùng chưa chọn reset.
- ADMIN/NURSE cấp và reset bệnh nhân: không có password/hash trong JSON, DOM,
  state lưu bền, log hoặc thông báo. NURSE gọi trực tiếp API nhắm nhân viên/ADMIN
  phải bị chặn. Gửi lại thư không sinh thêm mật khẩu. Timeout không gửi lệnh mới.
- Đổi email: username và hồ sơ cuối cùng khớp; trùng email/không quyền bị chặn;
  phiên cũ bị vô hiệu hóa; Patient đồng bộ chậm/lỗi có thông báo đúng thực tế.
- DOCTOR từ một hồ sơ kê đơn, thêm thuốc, lưu, xem lại: giữ đúng bệnh nhân/record;
  không tìm mã lại; không mất chẩn đoán; phản hồi cũ không lọt sang lượt khác.
  Giữ kiểm tra server cho hồ sơ đóng, quyền bác sĩ, giá/tồn và thanh toán.
- Quay lại danh sách nhân sự, xét nghiệm, đơn thuốc, thu phí: đúng tab, bộ lọc,
  trang; URL quay lại ngoài ứng dụng bị từ chối; role không quyền không có link.
- Chạy cùng kịch bản trên web và mobile; kiểm tra màn hình hẹp, bàn phím, focus,
  trạng thái tải/lỗi, thiết bị thật. Chưa có bằng chứng thực thi cho thiết kế mới.

## Bằng chứng kiểm tra hiện tại

Đã chạy 4 file Vitest, **49/49 test pass** ngày 04/11/2026:

- `frontend/src/features/organization/components/AdminAccountSecurity.test.tsx`
- `frontend/src/features/organization/components/PatientAccountIssuance.test.tsx`
- `frontend/src/features/medical-record/components/MedicalRecordDetail.test.tsx`
- `frontend/src/features/pharmacy/components/prescription/CreatePrescriptionForm.test.tsx`

Đây là xác nhận regression của hành vi hiện có, không chứng minh thiết kế đề xuất
đã triển khai. Test backend về không trả password/hash đã được đọc trong
`AccountManagementControllerTest` và `PatientAccountControllerTest`, chưa chạy lại
trong lượt rà soát này. Chưa chạy mobile hoặc kiểm thử giao diện trực tiếp.

## Điểm tích hợp nguồn

- Account UI: `frontend/src/features/organization/components/{StaffDetail,StaffForm,AccountDetail,AdminAccountSecurity,PatientAccountIssuance,OrganizationDirectory}.tsx`.
- Patient UI: `frontend/src/features/patient/components/{PatientDetail,PatientForm}.tsx`, `frontend/src/app/(dashboard)/patients/[patientId]/page.tsx`.
- Doctor UI: `frontend/src/features/medical-record/components/{MedicalRecordDetail,ExaminationRecordLink,PatientExamResults}.tsx`, `frontend/src/app/(dashboard)/records/[recordId]/page.tsx`.
- Rx UI: `frontend/src/features/pharmacy/components/prescription/{CreatePrescriptionForm,PrescriptionDetail}.tsx`.
- Other roles: `frontend/src/features/lab/components/{LabDetail,PatientLabResults}.tsx`, `frontend/src/features/billing/components/PaymentRequestWorkspace.tsx`, `frontend/src/features/notification/{types.ts,components/NotificationDetail.tsx}`, `frontend/src/components/layout/DashboardSidebar.tsx`.
- Backend: `backend/core-service/src/main/java/com/mediflow/organization/web/controller/{AccountManagementController,PatientAccountController}.java`, `organization/application/service/{SecurityAccountOperationsService,UpdateStaffService}.java`, `organization/application/dto/response/MailDeliveryDTO.java`, `organization/web/dto/response/AccountResponse.java`, `patient/web/PatientController.java`, `backend/gateway/src/main/java/com/mediflow/gateway/filter/RouteAuthorizationFilter.java`.
- Mobile: `mobile/lib/features/patient/presentation/patient_page.dart`, `mobile/lib/features/organization/presentation/organization_page.dart`, `mobile/lib/features/medical_record/presentation/medical_record_page.dart`, `mobile/lib/features/pharmacy/presentation/{create_prescription_page,prescription_page}.dart`, `mobile/lib/app/router.dart`.
- Quy tắc đối chiếu: `docs/ai/12-frontend.md`, `docs/ai/14-flutter.md`, `docs/ai/15-frontend-ownership.md`, `docs/plans/20-email-account-demo-runbook-2026-11-04.md`.
