# Huy — nút xuất thuốc V1 bị chặn bởi trạng thái thanh toán legacy

Ngày phát hiện: 2026-10-19. Source: `05c7690c3a37b602e336fac4902ced7f3a1f7d89`.
Owner sửa: **Huy**, frontend Pharmacy. Vinh xác minh backend và chạy acceptance sau sửa.

## Phát hiện từ source hiện hành

Frontend mặc định tạo đơn outpatient V1 (`prescriptionMode.ts`, `CreatePrescriptionForm.tsx`).
`PrescriptionDetail.tsx` đặt `canDispenseNow = showDispenseSlot && prescription.paymentConfirmed`,
ẩn nút/dialog xuất thuốc khi boolean này false và hiển thị “Chờ thanh toán”.

Backend giữ `paymentConfirmed` theo receipt V0 trong
`PrescriptionApplicationService.getById`; listener receipt legacy không hoạt động trong profile
outpatient. V1 dùng exact clearance và kiểm tra lại Billing khi thực thi xuất thuốc. Vì vậy một
đơn V1 đã thanh toán, có clearance hợp lệ vẫn có thể trả `paymentConfirmed=false` và bị UI chặn.
[Baseline](../plans/huy-backend-mvp-baseline.md) đã quy định không suy quyền V1 từ boolean này.
Đây là gap tích hợp FE/API; không đổi semantics boolean backend để che lỗi UI.

Phát hiện được xác nhận bằng source/contract; chưa chạy trình duyệt cho toàn bộ chuỗi này.
Backend suite hoặc test DTO riêng không được tính thành UI acceptance.

## Acceptance cho owner

1. Tạo đơn outpatient V1 bằng public API, thanh toán thật, nhận exact grant; giữ nguyên response
   GET với `paymentConfirmed=false`. Với ADMIN/PHARMACIST, UI vẫn cho thực hiện luồng xuất
   thuốc V1 theo contract hiện hành; không hiển thị boolean legacy như kết luận chưa thanh toán V1.
2. Backend tiếp tục quyết định quyền và clearance ở thời điểm command. Unpaid, expired/revoked
   clearance hoặc Billing unavailable phải giữ fail-closed, hiển thị lỗi có thể xử lý, không tự
   đánh dấu đã thanh toán/xuất thuốc ở client.
3. Kiểm tra role, đơn đã hủy/đã xuất, retry và compatibility V0. Sau xuất thành công, reload
   bằng API và giữ cùng dispense ID khi retry; không trừ stock hai lần.
4. Bổ sung regression cho fixture V1 đã có grant nhưng boolean legacy false và chạy build FE.
   Ghi rõ kiểm thử trình duyệt nào đã thực hiện; đóng handoff sau producer/consumer acceptance.

Không tự bổ sung trường authority mới vào DTO hoặc gọi service port trực tiếp. Nếu cần read
contract mới, owner phải khóa fixture/controller/serializer và bàn giao producer/consumer trước.
