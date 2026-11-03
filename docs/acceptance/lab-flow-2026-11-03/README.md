# Lab → cashier và doctor cancellation — 2026-11-03

Baseline `2e3fa734c162d29340c12c2b48b557ae43ef992b` cộng working-tree fix
được mô tả trong [audit](../../reviews/2026-11-03-lab-flow-audit.md).
Tất cả UUID và dữ liệu trong bằng chứng được sinh bởi test, không phải bệnh nhân thật.

`PackagedOutpatientLabAcceptanceIT`: 1 test, 0 failures, 0 errors, 0 skipped,
164.7 giây. Các JAR Core/Clinical/Lab/Billing/Gateway/Eureka được build từ mã
hiện tại; PostgreSQL 16 và RabbitMQ 3.13 chạy trong container cô lập.

- [Thanh toán Lab, clearance và kết quả](paid-lab-evidence.json): CASHIER trả
  150.000 VND, Lab hoàn tất, Clinical nhận kết quả và hoàn tất hồ sơ.
- [Hủy trước thu tiền](lab-unpaid-cancellation-evidence.json): Billing APPLIED,
  không có khoản hoàn.
- [Hủy sau thu tiền](lab-paid-cancellation-evidence.json): Billing APPLIED,
  nghĩa vụ hoàn 150.000 VND gắn đúng originalTransactionId. Chưa thực hiện hoàn tiền.

Test còn assert hàng thu trống trước khi gửi Clinical order sang Lab, có đúng
source trước thanh toán, không còn source sau hủy, doctor audit, retry không
phát event trùng, chặn role không đủ quyền và actor giả. Xem source test để biết
toàn bộ assertions; ba JSON là phản hồi nghiệp vụ chọn lọc, không thay cho báo cáo test.

Chạy lại sau khi package các JAR: Maven profile `packaged-slice` trong Lab,
`-Dit.test=PackagedOutpatientLabAcceptanceIT`, truyền sáu thuộc tính
`lab.acceptance.{core,gateway,eureka,clinical,lab,billing}.jar`, rồi
`test-compile failsafe:integration-test failsafe:verify`.
Không có mật khẩu hoặc access token được lưu trong bằng chứng này.
