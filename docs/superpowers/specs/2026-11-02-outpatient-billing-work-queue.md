# MF-03 — Hàng khoản thu Lab và thuốc

User yêu cầu tiếp tục triển khai theo review sau PR #562 đã merge. Đây là lát cắt
tiếp theo của thiết kế được giao, không cần chọn lại nghiệp vụ đã chốt trong review §8.2.
Nền: `c94044b9`; owner Vinh cho Billing FE/BE và Gateway. Không sửa FE Huy/Hoàng Anh.

## Quyết định

Chọn hàng chờ do Billing cung cấp, thay vì chỉ tự điền mã từ URL (vẫn cần bàn giao)
hoặc dựng một orchestration thu gộp (chưa có quy tắc phân bổ/thất bại từng phần).
Phí khám giữ hàng chờ V2 hiện có. Lab/thuốc dùng các command, báo giá, idempotency,
zero-payable và recovery đã có; không tạo một payment API khác.

## Contract mới, chỉ đọc

GET `/api/v1/billing/payment-requests/queue`, ADMIN/CASHIER human access JWT.
Giữ hai cờ ledger/discovery đang có, mặc định tắt. Gateway định tuyến V1 hiện có,
thêm exact GET authorization. Cache-Control no-store và correlation header/envelope.

Query tùy chọn: patientId, careEpisodeId, purpose (LAB_TEST hoặc PRESCRIPTION), page,
size. PageQuery hiện có chuẩn hóa 0+/1..100, mặc định20. Không nhận actor từ query.
Response `ApiResponse<PageResult<PaymentRequestQueueItem>>`:

```typescript
interface PaymentRequestQueueItem {
  request: PaymentRequestDiscoveryDTO; // wire chính xác đã có
  createdAt: string; // thời điểm Billing tạo PAYMENT_REQUEST, không phải ngày khám
}
```

Chỉ nguồn Lab/Rx đã phát sinh trong store Billing, OUTPATIENT_VISIT, account chưa
SETTLED/CLOSED, request PENDING/PARTIALLY_PAID và chưa hết hạn. Còn phải thu dương;
giữ Lab requestedAmount=0 cần xác nhận nghĩa vụ 0, không tạo thu tiền giả. Không trả
Surgery/Admission/EXAM/nguồn chưa phát hành. Tiền/currency tính đúng canonical discovery:
SUM PAYMENT COMPLETED, hoàn tiền không mở lại PAID. Lỗi mapping không được tự chữa
ID hoặc giả thành số dư0. Hai query count/page cùng snapshot, sắp createdAt tăng dần
rồi paymentRequestId. Không truy vấn store ngoài Billing, không gọi từng nguồn từ FE.

## UX

Hàng khoản thu là điểm chính tại `/billing/payment-requests`: lọc bệnh nhân/loại phí,
phân trang, chọn khoản và chọn các khoản cùng lượt từ ID Billing cấp (không gõ UUID).
Thẻ có tên/ngày sinh, khoa, loại, thời điểm phát sinh và số còn phải xử lý.
Ngày phát sinh không được gọi là ngày khám. Chọn khoản đọc lại by-source để lấy
số dư hiện hành; phải khớp request/patient/source/purpose/episode trước hiện nút thu.

Tra cứu mã nằm trong disclosure hỗ trợ; mặc định loại Lab ở workspace mới. Còn
giữ EXAM compatibility tại form hỗ trợ và đường cũ. Chỉ một detail/payment workspace
được hoạt động, khóa chọn/lọc khi pending/uncertain. Giữ receipt khi đọc lại thất bại;
sau payment confirmed tải lại hàng, không thu lần nữa. Session đổi xóa dữ liệu, đọc
muộn không thay bộ lọc/lựa chọn mới. Lỗi queue không đồng nghĩa không có khoản thu.

## Nghiệm thu

DB thật: Lab/Rx đủ điều kiện; lọc patient/episode/purpose; loại paid/cancelled/expired/
closed/inpatient; Lab0; partial/refund; thứ tự/phân trang; mapping hỏng. JWT/Gateway:
chỉ ADMIN/CASHIER, không service/refresh/PATIENT/role khác; gates/correlation/no-store.
FE: click khoản không gõ mã, by-source giữ đủ ID, đọc khớp trước thu, ngăn đổi khoản
khi pending/uncertain, chống đọc muộn, giữ receipt khi refresh lỗi, session reset.
Nghiệm thu UI có ảnh bằng dữ liệu cách ly, giờ thực; dọn Docker khi xong.
