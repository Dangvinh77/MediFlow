# HANDOFF — tên tham chiếu cho frontend Vinh/Lộc

## Điều phối hiện hành — 26/10/2026

- Registry ID: `H-01`; status: `NEEDS REVALIDATION`.
- Bàn giao hiện tại: Hoàng Anh/Core/Gateway → consumer frontend theo ownership.
- [Registry và phần acceptance còn thiếu](README.md) là nguồn giao việc hiện tại.
- Các base/branch, status, owner và test counts trong checkpoint dưới giữ theo ngày;
  không là bằng chứng bug còn tồn tại hoặc task phải code lại trên HEAD.
- Nghiệp vụ mới dùng [WF-01–11](../plans/07-outpatient-workflow-implementation.md), không đóng từ evidence baseline.

## Cập nhật implementation — 26/10/2026

Contract tối thiểu đã triển khai cho CASHIER/LAB_TECH (patient directory) và mọi human role
(care-labels doctor/department/room); xem [A.13](../plans/03-contracts.md).
Shared frontend đã nối đúng role; tìm tên bác sĩ dùng keyword phía server.
Không dùng mô tả giới hạn ngày 22/10 bên dưới để yêu cầu mở full PatientDTO cho các role này.
Chỉ chuyển NEEDS REVALIDATION: nghiệm thu consumer toàn bộ web/mobile và session reset vẫn
phải đối chiếu theo từng phạm vi; đợt này tập trung ngoại trú web, không đóng thay mobile.

## Hồ sơ phát hiện và checkpoint

Ngày: 2026-10-22. Trạng thái: OPEN — cần contract từ owner Core (Hoàng Anh).
Frontend consumer: Vinh (Clinical/Lab/shared), Lộc (Billing/Notification).

## Hiện trạng đã kiểm tra

Patient search/detail chỉ cho ADMIN/DOCTOR/NURSE; Organization staff/detail cho
ADMIN/MANAGER/DOCTOR/NURSE. `maBenhNhan` trong PatientDTO là UUID, không phải mã
bệnh nhân nghiệp vụ. Không dùng CCCD, số điện thoại hoặc địa chỉ để thay mã hiển thị.

Frontend đã thêm tìm/chọn tên bằng các API Gateway hiện có cho vai trò được phép.
CASHIER/LAB_TECH/PATIENT không gọi Patient search/detail; những vai trò không có
Organization read không gọi staff/department để lấy nhãn. Trường nhập mã tương thích
nằm trong mục thu gọn, kèm lý do chưa thể chọn tên. Đây là giới hạn còn mở,
không phải bằng chứng đã hoàn thành chọn tên cho mọi vai trò.

## Contract cần owner quyết định

- Chốt API tra cứu tham chiếu tối thiểu cho nghiệp vụ thu ngân/thông báo/xét nghiệm:
  chỉ identity, nhãn và thông tin phân biệt tối thiểu được phép; không cấp nguyên
  PatientDTO nhạy cảm cho mọi vai trò để phục vụ dropdown.
- Chốt role matrix và phạm vi dữ liệu theo nghiệp vụ. PATIENT cần identity từ server
  của chính mình; không được tìm toàn bộ bệnh nhân hoặc suy từ JWT subject.
- Chốt tên wire fields, phân trang/tìm tên và lỗi 401/403/404/correlation qua Gateway.
- Organization staff hiện lọc theo khoa/chức danh và phân trang, chưa có tìm tên phía
  server. Frontend lọc tên trong từng trang và thông báo rõ; cần contract keyword
  nếu yêu cầu tìm tên trên toàn danh mục. Không tự thêm query parameter không có thật.

## Nghiệm thu trước khi đóng

1. Core owner công bố controller/DTO/role fixtures và Gateway contract; có kiểm thử
   từ chối truy cập bệnh nhân ngoài phạm vi và không trả dữ liệu riêng tư dư thừa.
2. Frontend nối contract qua app composition, kiểm thử tên trùng, phân trang, mất quyền,
   đổi phiên, chọn rồi sửa tên phải xóa identity cũ, command gửi nguyên ID server.
3. Cashier/Lab tech/Patient được kiểm tra với token đúng vai trò trên Gateway thực.
4. Hai phía đạt tiêu chí thì xóa handoff và chuyển quy tắc lâu dài vào tài liệu canonical.

Các mã financial source/event/transaction và đơn thuốc không có API tìm tên trong
phạm vi consumer này vẫn là đầu vào đối soát kỹ thuật. Contract tìm nguồn thanh toán
cần owner Billing/Clinical/Pharmacy chốt riêng; không tự truy vấn DB hoặc thêm coupling.
