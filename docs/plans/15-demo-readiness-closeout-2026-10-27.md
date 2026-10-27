# Hoàn thiện trước nghiệm thu trực tiếp — 27/10/2026

Tiếp tục từ `43c20412` và PR #547 theo yêu cầu hoàn thiện trước khi Vinh, Huy,
Hoàng Anh thử UX và luồng demo. Giữ [06](06-acceptance-runbook.md) là tiêu chí
nghiệp vụ; không xóa hoặc đánh dấu đạt tiêu chí chỉ để hết checkbox.

## Công việc kỹ thuật trong lượt này

- [x] Kiểm kê checklist hiện hành, tách baseline lịch sử/deferred khỏi khoảng trống thật.
- [x] Kiểm thử giao diện kết quả khám/Lab: chờ, rỗng, lỗi, thử lại và phản hồi cũ.
- [x] Hoàn thiện cách trình bày lời hẹn cho bệnh nhân, giữ định danh đối chiếu ngoài luồng đọc chính.
- [x] Bổ sung replay ba fact kết quả trên broker riêng, giữ nguyên bytes/event IDs;
  kiểm số thông báo và IDs trước/sau qua Gateway trên packaged Support.
- [x] Thao tác trình duyệt cấp account bị mất phản hồi, tải lại và đối soát cùng hồ sơ/key;
  xác nhận điều hướng tiếp nhận không cần patient login. Trạng thái kết quả có component tests;
  đổi mật khẩu trực tiếp và thử đa vai trò chuyển đúng lượt nghiệm thu do developer thực hiện.
- [x] Chạy kiểm tra phù hợp, lưu evidence và review; cập nhật PR.
- [x] Bàn giao một runbook cho ba developer có phân vai, điều kiện bắt đầu, kết quả mong đợi
  và mẫu ghi lỗi. Checklist UX trực tiếp chưa chạy không được ký thay.

## Ranh giới

Production chỉ trong Clinical/Lab/Billing/Notification và frontend owned/shared theo AGENTS.
Core/Gateway/Patient/Organization/Pharmacy/Report kiểm hợp đồng và runtime; không tự đổi
production của owner khác. Runners/docs là phần tiếp tục đã được giao. Giữ nguyên
Surgery/Inpatient, default gates, dữ liệu và credential của môi trường dự án.

Phát lại sự kiện dùng broker disposable có manifest, capture queue riêng và nguyên bytes
được producer phát thật; không lấy payload từ DB hay tự tạo fact để giả hoàn tất.
Đặt lịch cho ngày mai không chứng minh người bệnh thực sự quay lại vào ngày khác.
Xác minh danh tính người quên mật khẩu cần người bệnh/nhân viên và quy trình bệnh viện;
test synthetic chỉ chứng minh account/session effects.

## Tiêu chí bàn giao

Các việc kỹ thuật trên có kết quả cụ thể. Runbook phân biệt evidence API, component,
broker và browser với phần ba developer cần thực hiện. Nếu còn lỗi kỹ thuật, ghi đúng lỗi
và tiếp tục sửa; không chuyển lỗi thành checkbox UX để coi đã xong. H-11/WF-11 chỉ đóng
khi acceptance tương ứng thực sự đủ; readiness cho vòng thử trực tiếp không là duyệt demo.

## Kết quả

Code `d496b49e`; [biên bản và bằng chứng](../acceptance/demo-readiness-2026-10-27/README.md).
Các việc kỹ thuật của lượt này hoàn tất; [runbook ba developer](16-three-developer-demo-acceptance-2026-10-27.md)
là nơi thực hiện lượt thử tiếp theo. Checkbox ở trên chỉ đóng phạm vi lượt này, không đóng
ONB/WF/SC hoặc các handoff chưa đủ acceptance. Không có independent reviewer sign-off trong lượt bổ sung.
