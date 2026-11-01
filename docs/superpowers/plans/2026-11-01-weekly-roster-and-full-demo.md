# Hoàn thiện lưới tuần và nghiệm thu demo ngoại trú

Base: master `3f9f5147` (đã merge #558). Người dùng yêu cầu lưới tuần, UI admin bổ nhiệm/miễn nhiệm, nghiệm thu hành trình bệnh nhân và cập nhật DEMO_UI_TUNG_BUOC.md.

- [x] Lưới phân ca: bác sĩ theo hàng, 7 ngày thứ Hai–Chủ nhật theo cột, ca/phòng/trạng thái, tổng giờ ACTIVE. Đọc đủ mọi trang trong tuần; không biến tổng trang hiện tại thành tổng tuần. Bác sĩ chưa có ca không được suy diễn thành danh sách nhân sự đầy đủ. Giữ thao tác sửa/tạo buổi/hủy theo guards hiện có. Có giao diện ngày cho cửa sổ hẹp.
- [x] UI admin: dùng dữ liệu demo thử bổ nhiệm/miễn nhiệm, danh tính DOCTOR giữ nguyên; kiểm lịch sử và khả năng chuyển khu vực trước/sau. Sửa lỗi có bằng chứng trong phạm vi tính năng này.
  - Đã nghiệm thu bổ nhiệm/miễn nhiệm, lịch sử, chuyển khu vực và giữ quyền bác sĩ; đã sửa đồng bộ thông tin khoa và tải lại khi xung đột. Thay thế bằng bác sĩ thứ hai chưa nghiệm thu UI.
- [ ] Hành trình bệnh nhân: tạo hồ sơ/cấp tài khoản tùy chọn, đăng ký, thu phí khám, bắt đầu khám, chỉ định Lab, thu Lab, trả kết quả, kê đơn/hoàn tất khám, thu thuốc/xuất thuốc, kết quả chính chủ. Giữ cùng patient/registration/record/source IDs; không dùng API hoặc fixture thay cho các click chưa làm được.
- [x] Ngoài giờ: người dùng chọn giữ giờ thực, nghiệm thu trong 07:00–17:00. Không đổi giờ Windows hoặc nới giờ/guard của chạy thật để nghiệm thu.
- [x] Cập nhật DEMO_UI_TUNG_BUOC.md theo nhãn/nút/source đã xác minh, bổ sung admin/lưới tuần và phiếu bàn giao; tách bước đã nghiệm thu khỏi phần chưa chạy.
- [x] Test, typecheck/lint/build phù hợp; ảnh UI; review; cleanup container của demo; PR có giới hạn nghiệm thu rõ.
  - Đã đạt 267 test/41 file, typecheck/lint/build, review độc lập; đã lưu 14 ảnh và chạy clean-docker.bat thành công. [PR #559](https://github.com/Dangvinh77/MediFlow/pull/559).

Các phần độc lập được giao riêng: lưới tuần thuộc appointment; agent chính điều phối UI/luồng và tài liệu, sửa lỗi đã chứng minh. Không mở rộng nghiệp vụ ca đêm, nội trú hoặc phẫu thuật.
