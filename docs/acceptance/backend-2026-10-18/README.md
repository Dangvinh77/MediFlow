# Backend verification — 2026-10-18

## Đính chính sau phản hồi của Lộc — 19/10/2026

Các bảng bên dưới là **kết quả lịch sử trước Gateway fix**, không phải trạng thái của
master hiện tại. Đối chiếu remote `4c19dd4a8ede06c07545c9290ff77b687b8dd49c` xác nhận
Gateway đã sửa tại `2805b07c` (PR #431), nhưng hai sửa test V-TEST-BUG-01/02 chưa được
commit lên master. Tag `v0.9.0-backend-feature-complete` trỏ `8d7e6c69` cũng không chứa
hai sửa test này. Vì vậy không dùng tag hoặc kết quả local chưa commit để khẳng định
nguồn chung đã hoàn tất mọi việc trong PR #429.

Ngày 19/10 Vinh chạy lại explicit Gateway refund acceptance **11/11**, toàn Gateway
**1.449/1.449**, đều 0 failure/error/skip. Hai route đã qua kiểm tra policy sau sửa;
11 cases dùng filter/mock exchange, không tự chứng minh toàn luồng HTTP/JWT/Billing.
Phần Report/Pharmacy và phạm vi nguồn được cập nhật trong
[bundle xác minh 19/10](../backend-recheck-2026-10-19/README.md).
Giữ nguyên số liệu đỏ lịch sử bên dưới để truy vết, không tính rerun thành baseline mới.

Baseline master `025ef39e358756a28043d9809fad165096528fbf`, gồm PR #427.
Production source không sửa trong lượt này; Gateway có thêm explicit acceptance test chưa commit.
Đây là kết quả local Windows/Docker Desktop, không phải production activation.

## Regression và build

`mvn -B -ntp test` exit 1 tại Report. Chạy tiếp modules sau điểm dừng bằng
`mvn -B -ntp -fae -pl backend/support-service,backend/inpatient-service,backend/surgery-service test`, exit 0.
Tổng không trùng 6.854 tests: **6.853 pass, 1 failure, 0 errors, 0 skips**.

| Module | Tests | Failures / errors / skips |
|---|---:|---|
| Gateway | 1449 | 0 / 0 / 0 |
| Core | 183 | 0 / 0 / 0 |
| Clinical | 339 | 0 / 0 / 0 |
| Lab | 216 | 0 / 0 / 0 |
| Pharmacy | 838 | 0 / 0 / 0 |
| Billing | 1060 | 0 / 0 / 0 |
| Notification | 279 | 0 / 0 / 0 |
| Report | 1331 | 1 / 0 / 0 |
| Support | 18 | 0 / 0 / 0 |
| Inpatient compatibility (deferred) | 150 | 0 / 0 / 0 |
| Surgery compatibility (deferred) | 991 | 0 / 0 / 0 |

Common/Eureka không có tests; build thành công. `mvn -B -ntp -DskipTests install` exit 0;
đây là build fresh artifacts, không tính thành test pass. Deferred compatibility không mở scope MVP.
Report clock failure và hai lượt rerun 8/8 được giữ riêng; full regression vẫn chưa xanh.

## Selected acceptance, không cộng vào baseline

| Stage | Tests | Failure / error / skip | Exit |
|---|---:|---|---:|
| Gateway refund route explicit red acceptance | 11 | 4 / 0 / 0 | 1 |
| Billing publication process recovery | 1 | 0 / 0 / 0 | 0 |
| Clinical packaged EXAM | 1 | 0 / 0 / 0 | 0 |
| Clinical consumer/outbox process recovery | 3 | 0 / 0 / 0 | 0 |
| Lab packaged, thiếu Billing JAR property | 1 | 1 / 0 / 0 | 1 |
| Lab packaged với Billing JAR property đúng | 1 | 0 / 0 / 0 | 0 |
| Lab consumer/outbox process recovery | 2 | 0 / 0 / 0 | 0 |
| Pharmacy distributed default | 17 | 0 / 0 / 1 | 0 |
| Pharmacy V1 explicit opt-in | 1 | 0 / 1 / 0 | 1 |
| Support packaged | 1 | 0 / 0 / 0 | 0 |
| Historical Support rehearsal trên cold copies | 1 | 0 / 0 / 0 | 0 |
| Actual six-service outpatient acceptance | 1 | 0 / 0 / 0 | 0 |
| Report telemetry targeted rerun 1 | 8 | 0 / 0 / 0 | 0 |
| Report telemetry targeted rerun 2 | 8 | 0 / 0 / 0 | 0 |

Lệnh chính xác, exit và sanitized per-class failures nằm trong [results.json](results.json).
Profiles dùng `-Dtest=__NoUnitTests__ -Dsurefire.failIfNoSpecifiedTests=false` để tránh chạy lại
Surefire; Failsafe classes thực sự được chọn đã được đối soát. Runner ban đầu lấy report theo
mtime có cửa sổ 2 giây, dính report stage trước; bảng/JSON bền vững đã lọc lại đúng module và
class của profile. Không cộng trùng reruns hoặc gọi report cũ là test mới.

Lab lượt đầu thiếu `lab.acceptance.billing.jar`; lỗi cấu hình chạy trước business flow,
không là bằng chứng lỗi production. Lượt đủ property đã pass actual payment/result attachment; exact absolute JAR path nằm trong JSON. Pharmacy default skip có lý do rõ ràng: ca live V1 issuer
chỉ chạy khi opt-in sau producer acceptance; skip không được tính pass. Lượt opt-in chạy riêng đã timeout chờ invoice.created;
Billing container harness chưa được bật publication/purposes/reference. Giao Vinh
[V-TEST-BUG-02](../../handoffs/HANDOFF-VINH-BACKEND-TEST-FAILURES.md) sửa test config và retest;
chưa là bằng chứng lỗi production. Không ký ca issuer-chain từ fixture/default pass.

## Lỗi mở và giới hạn nghiệm thu

- [Gateway HA-CODE-01/02](../../handoffs/HANDOFF-GATEWAY-BILLING-ADJUSTMENT-ROUTES.md):
  bốn ADMIN/CASHIER positive cases bị chặn; bảy deny controls pass. Test filter/mock exchange
  chưa chứng minh JWT/HTTP/actual Billing refund flow. Các bước này cần retest sau code owner.
- [V-TEST-BUG-01](../../handoffs/HANDOFF-VINH-BACKEND-TEST-FAILURES.md): Report DB timestamp
  đi trước host bound 56,3 microsecond; hai clocks khác nhau. Reruns pass không xóa lỗi gốc.
- Chưa ký global M6/T01–T19; T11/T19 refund public path đang blocked. FE smoke và production
  cutover không nằm trong backend local run này. Không dùng số test để suy toàn bộ acceptance.
- Tests container và snapshots dùng fixtures theo harness hiện có. Six-service case không
  tự chứng minh mọi token, mọi financial race hoặc mọi environment.

[Tool/image versions, source SHA, test hash](environment.json) ·
[Bảng giao việc cuối](../../handoffs/BACKEND-FINAL-ASSIGNMENTS-2026-10-18.md).
Full raw logs/XML chỉ giữ tại ignored `target/backend-final-verification` và module targets;
XML có environment nên không publish nguyên bản. Bundle này giữ metadata/failure messages đã lọc,
không chứa credentials, JWT hoặc patient payloads.

## Persisted local volume rehearsal

Operator harness exit 0, `HistoricalSupportCutoverIT` 1/1 pass. Dùng cold copies của
`source_mediflow-pgdata` và `source_mediflow-rabbitdata`, giữ năm persisted broker identities
trong inventory; original volumes chỉ mount read-only. Clone containers đã dừng sau test.
Legacy → Support → broker restart → legacy rollback → Support return giữ bốn accepted
notification writes/probe bytes; Report rows/Flyway history và Pharmacy không đổi.

[Proof](historical/cutover-proof.json), [reconciliation](historical/reconciliation.json),
[source manifest](historical/source-manifest.json) và before/after inventories được giữ nguyên bytes.
PG SHA256 trước/sau `b56745dcd144d5c299926248bd4c8969075c925812244d5f91283352a21bec3d`;
Rabbit SHA256 trước/sau `44fc58ae038c50bc6021980d6a7cce4bd7527b619c35dffd9a23bd7c626d9627`.
**Giới hạn:** source inventory Report/Notification không có business rows; bài này chứng minh
retained local stores và writes mới qua rollback, chưa chứng minh cutover lịch sử nghiệp vụ
không rỗng/production. Activation gates giữ OFF. Không dùng evidence 17/10 thay kết quả mới.
