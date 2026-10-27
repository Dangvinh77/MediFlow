# Mobile Pharmacy — tiếp tục task 12, 2026-10-26

Baseline nhánh Huy: `9edf9bcc`. Không tạo commit/push trong đợt triển khai mobile.

## Code và kiểm tra local

Đã thay việc nhập riêng các nguồn kê đơn trong adapter production bằng public Pharmacy
prescribing context. Chọn bệnh nhân → hồ sơ theo patient scope → doctor/department/episode/
priceCodes từ server. Không import feature Clinical/Core/Billing, không sửa backend hay shared
app/network/config. Tiếp tục dùng Clean Architecture, Riverpod, Material3 và session boundary.

- Baseline: 128 files đúng format, analyzer không lỗi, 342 tests PASS, web build PASS.
- Sau sửa: `tool/check.ps1` PASS; analyzer không lỗi, full suite 348 tests PASS, default web build PASS.
- Thêm regression pagination: focused source widget suite 5/5 PASS. Lượt chốt sau đó chạy lại
  toàn bộ: format 130 files không thay đổi, analyzer không lỗi, 348 tests PASS (58 giây).
  Dùng số đếm full suite từ runner, không cộng lượt focused thành số tests riêng biệt.
  Không tự chọn khi một kết quả còn trang tiếp; nhiều hồ sơ chọn
  đúng patient scope, bỏ context cũ khi đổi patient, mismatch/error chặn gửi và cho retry.
- Repository tests khóa path/query, IDs, nullable/malformed source, mode và price policy;
  widget test gửi đúng doctor/department/episode/priceCode từ nguồn đã xác minh.
- Web build vẫn có cảnh báo CupertinoIcons đã có trước đợt này. Không sửa package/theme chung.

## Runtime và giới hạn

Mobile host/proxy tại `http://localhost:4300`, Gateway `http://127.0.0.1:8080`.
Build runtime dùng `PHARMACY_MODE=outpatient`, `MEDIFLOW_OUTPATIENT_UI_ENABLED=true`;
đây là client configuration, không bật hay thay backend gates.

Đã đăng nhập DOCTOR và PHARMACIST qua proxy thật trên Flutter web trong Codex. Có thử viewport
390×844, nhưng ảnh detail bị cắt theo vùng canvas; ảnh phiếu xuất cuối dùng kích thước cửa sổ mặc
định. Không dùng lần này làm bằng chứng đạt responsive/native acceptance:

| Tình huống | Kết quả quan sát |
|---|---|
| Chọn Nguyễn Văn An Demo | Tự hiện hồ sơ 25/10, bác sĩ Lê Bác Sĩ Demo, khoa Nội tổng quát Demo; không chọn riêng các nguồn |
| Tạo đơn bằng UI | Đơn `36bda652-2f0c-47e9-8080-5bb2328d3c36`, 1 Ambroxol 30mg, tổng server 2500 VND |
| Đơn chưa thanh toán | UI gửi dispense, backend từ chối vì thiếu matching V1 clearance; không tự xác nhận thanh toán |
| Billing discovery | Public Gateway tìm thấy payment request `76d6226c-5ac1-4496-8937-22dff9683be1`, đúng source/patient/episode, PENDING 2500 VND |
| Thanh toán demo | CASH qua public Billing API bằng demo.cashier, COMPLETED; đây là API acceptance, chưa phải Billing mobile UI acceptance |
| Xuất bằng mobile UI | PHARMACIST nhận phiếu `777fa0bc-0e54-4963-bd09-07f647855ac1`, trạng thái đơn hoàn tất/đã xuất, không còn nút xuất |
| Tồn và lặp lệnh | Public drug API: 70 trước xuất, 69 sau xuất; lặp PUT dispense trả cùng phiếu, tồn vẫn 69 |

Lượt kiểm lặp lệnh chứng minh một effect trong tình huống tuần tự vừa chạy; không thay cho thử
concurrency, timeout/unknown outcome hoặc restart recovery.

Runtime ban đầu lệch priceCode: Pharmacy DRUG, Billing RX-DEMO, nên đơn đầu không có payment
request. Đơn đầu `70aad795-4c81-4890-996a-bb7f13c8e56a` dùng để kiểm chặn unpaid đã CANCELLED
qua public API bằng DOCTOR, trả 1 reservation. Sau đó recreate riêng Pharmacy theo overlay demo
hiện có, dùng override local `.git/mobile-runtime-override.yml` để **giữ nguyên** datasource đang
chạy `mediflow_pharmacy_docker` (overlay demo hiện trỏ database khác), chỉ đồng bộ priceCode
RX-DEMO và giữ các gates đã bật. Không sửa backend source, không đổi dữ liệu khởi tạo hoặc
recreate PostgreSQL/RabbitMQ. Gateway restart để cập nhật discovery. Public prescribing context
xác nhận RX-DEMO trước khi tạo đơn mới. Khi chạy lại môi trường này phải giữ override datasource;
không dùng overlay demo đơn lẻ rồi coi database trống là mất dữ liệu.

Hạn chế quan sát: PHARMACIST chưa có quyền lookup tên hồ sơ/patient/doctor/department/staff;
UI hiện thông báo thiếu quyền thay vì tên. DOCTOR xem được các tên khi kê đơn. Không mở rộng
quyền hay sửa shared reference reader trong đợt này.

Ảnh: [nguồn kê đơn tự liên kết](prescribing-source.png), [đơn đã tạo](prescription-created.png),
[chặn unpaid](unpaid-dispense-blocked.png), [phiếu xuất thành công](prescription-dispensed.png).

Task12 vẫn PARTIAL: cần matrix quyền/foreign doctor/timeout/expiry/service unavailable thật,
concurrency/recovery sâu hơn và native acceptance. Happy path clearance/stock đã PASS ở trên.
Android chưa có cmdline-tools/license
status; Windows thiếu C++ workload, iOS không có macOS/Xcode. Flutter web không thay native.

Không lưu tokens/mật khẩu/log thô hoặc dữ liệu người bệnh thật trong evidence.

## Tiếp tục sau khi gộp master — 2026-10-27

Huy local fast-forward từ `9edf9bcc` lên `e4f98f11` (29 commit của master) và khôi phục
đầy đủ phần Pharmacy mobile chưa commit. `origin/master` và Huy local cùng commit; không có
xung đột hoặc thay đổi file mobile từ master. `origin/Huy` chưa được push trong lượt này.

Sau đồng bộ, `mobile/tool/check.ps1` PASS: analyzer không lỗi, 349 tests PASS, web build PASS.
Sau chỉnh thêm ngày ở danh mục, nhãn mã tham chiếu và hướng dẫn ngày kê đơn, analyzer không lỗi,
97 widget tests liên quan PASS, `git diff --check` sạch. Docker daemon và Gateway đang không
hoạt động khi kiểm tra, vì vậy chưa chạy thêm tình huống thật của task 12 trong lượt này.
Bản web outpatient build PASS, host/proxy trả HTTP 200 tại `http://localhost:4300`.
`flutter doctor -v` xác nhận thiếu Android cmdline-tools/license và Windows C++ workload;
`flutter devices` chỉ thấy Windows, Chrome, Edge, không có Android device.
