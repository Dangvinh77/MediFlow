# Demo trưởng khoa: một tài khoản, hai dashboard

Phạm vi: DOCTOR được bổ nhiệm DEPARTMENT_HEAD khoa CLINICAL, phân ca bác sĩ và tạo buổi nhận bệnh nhân từ ca. Đây là hướng dẫn thao tác, không thay bằng chứng nghiệm thu. Bác sĩ vẫn phải có phân công và điều kiện hiện có để khám/kê đơn.

## Khởi động

1. Chạy `start-backend.bat`, chờ READY. Chạy `start-frontend.bat`, mở http://127.0.0.1:3000. Scheduling UI và Gateway gate được hai BAT cấu hình cho demo dùng một lần.
2. `start-backend.bat` đã bổ nhiệm sẵn demo.staff03@example.test (Lê Bác Sĩ Demo) làm trưởng Khoa Nội tổng quát Demo. Muốn tự thử bổ nhiệm bằng UI, trước hết dùng demo.staff01@example.test miễn nhiệm theo mục C, chưa chạy script dưới, rồi làm theo các bước tiếp theo.
3. Nếu muốn có sẵn trưởng khoa, một ca và một buổi khám mẫu, chạy từ root source:

       python scripts/demo_25_10/prepare_department_head_demo.py

   Script chỉ chấp nhận active disposable rehearsal ở gateway 28080, gọi API công khai, giữ command journal; không viết SQL nghiệp vụ và không thay một trưởng khoa khác đang có. Ca ở ngày hôm nay +2 (giữ ngày gốc khi rerun), 08:00–12:00, buổi khám 09:00–11:00, 12 chỗ. Ngày và IDs thật được in/lưu trong evidence cục bộ của phiên. Không đăng ký bệnh nhân vào buổi mẫu.

Tài khoản mẫu: demo.staff01@example.test (ADMIN) và demo.staff03@example.test (DOCTOR), mật khẩu demo công khai `MediFlowDemo@2026`. Đăng nhập bằng email; username cũ demo.admin/demo.doctor không còn dùng được. Không dùng thông tin demo cho tài khoản thật.

## A. Bổ nhiệm và chuyển khu vực

1. Đăng nhập demo.staff03@example.test: mặc định vào Khám bệnh. Khi chưa có bổ nhiệm sẽ chưa có nút Sang Quản lý khoa.
2. Đăng xuất, đăng nhập demo.staff01@example.test → Tổ chức → Khoa → mở khoa đang có bác sĩ này (đối chiếu hồ sơ nhân sự, không đoán từ tên khoa).
3. Trong Bổ nhiệm trưởng khoa, chọn đúng bác sĩ, nhập lý do, chọn Xem trước rồi Xác nhận bổ nhiệm. Thay thế người đương nhiệm sẽ kết thúc nhiệm kỳ cũ và được ghi lịch sử; không tạo tài khoản mới.
4. Đăng nhập lại demo.staff03@example.test hoặc quay về cửa sổ bác sĩ và focus/làm mới để tải quyền. Header hiện Sang Quản lý khoa. Bấm để sang dashboard quản lý; kiểm tên khoa và nút Về Khám bệnh.

## B. Phân ca bác sĩ và tạo buổi khám

1. Trong khu quản lý, mở lịch làm việc bác sĩ. Chọn ngày/tuần; khi có fixture sẵn, chọn đúng ngày script đã in.
2. Chọn bác sĩ trong khoa, phòng CONSULTATION hợp lệ, ngày và giờ 07:00–17:00 rồi lưu ca. Thử một khoảng trùng để thấy lỗi, không coi thông báo lỗi là đã ghi ca mới.
3. Kiểm ca vừa lưu có ngày/giờ, bác sĩ, phòng và trạng thái. Chưa tạo buổi khám thì ca chưa có số chỗ nhận bệnh nhân.
4. Chọn Tạo buổi khám từ ca, đặt khoảng giờ bên trong ca, giờ nghỉ nếu có và số chỗ; lưu. Mở lịch buổi khám của khoa để thấy buổi mới.
5. Thử sửa/hủy ca đã liên kết buổi khám còn hoạt động: backend chặn, cần xử lý buổi khám theo quy tắc hiện có. Không có thao tác tự hủy người bệnh hoặc hoàn tiền.
6. Bấm Về Khám bệnh; bác sĩ xem lịch làm việc của mình và lịch buổi khám. Chỉ nhận khám bệnh nhân được phân công/đủ điều kiện theo luồng cũ.
7. Nhập một thay đổi chưa lưu rồi bấm chuyển dashboard: phải có cảnh báo rời trang. Chọn ở lại để tiếp tục nhập; chuyển UI không tự gửi form.

## C. Miễn nhiệm và phân quyền

1. Dùng admin mở lại khoa, nhập lý do và xác nhận Miễn nhiệm. Kiểm lịch sử giữ nhiệm kỳ đã kết thúc.
2. Quay về phiên bác sĩ cũ: request quản lý mới bị từ chối, quyền/menu được cập nhật; bác sĩ vẫn xem lịch chính chủ và làm nghiệp vụ chuyên môn theo phân công. Không cần đổi role hoặc chờ token hết hạn.
3. DOCTOR chưa được bổ nhiệm, NURSE và LAB_TECH không được vào quản lý bằng cách dán URL; trưởng khoa không được quản lý khoa khác. Tài khoản MANAGER cũ đã bị khóa từ migration V18; quản lý khoa chỉ đến từ bổ nhiệm trưởng khoa.

Kiểm chứng bằng API với token bác sĩ cũ có thể chạy trên phiên disposable mới:

    python scripts/demo_25_10/prepare_department_head_demo.py --verify-revocation

Lệnh có thay đổi bổ nhiệm: miễn nhiệm rồi khôi phục demo.staff03@example.test để tiếp tục demo; giữ receipt/journal. Nếu chạy bị gián đoạn hoặc fixture đã bị sửa, xem journal/evidence và đối soát trước khi thử lại, không xóa journal để gửi một yêu cầu mới tùy tiện.

## Kết thúc

Đăng xuất, dừng frontend đã mở cho demo, chạy `clean-docker.bat`. Cleanup giữ volume và tài nguyên không thuộc dự án; không dùng prune toàn máy.
