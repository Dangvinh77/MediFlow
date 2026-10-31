# Đánh giá tương tác và bố cục UI theo sáu vai trò

**Cập nhật triển khai:** xem [kết quả sửa và kiểm chứng](AFTER.md). Phần dưới giữ nguyên hiện trạng trước sửa.

Ngày 31/10/2026, khởi động demo lúc 09:55, chốt báo cáo lúc 10:12 giờ Việt Nam. Source `5be32324` (đã có PR #555).
Kiểm tra trực tiếp bằng Microsoft Edge trên desktop 1536×816, giao diện tối theo hệ thống, thao tác qua chuột/điều khiển accessibility và quan sát screenshot sau các bước.

## Kết luận

Giao diện đã có các chức năng chính và phân menu theo vai trò, nhưng cách tổ chức còn thiên về màn hình quản trị dữ liệu hơn là bàn làm việc theo ca khám. Cần sửa **một lỗi hợp đồng đầu vào đã tái hiện**, rồi ưu tiên trang đầu theo vai trò, giữ ngữ cảnh người bệnh và thông báo dành cho bệnh nhân. Chưa đủ cơ sở gọi là sẵn sàng nghiệm thu UX toàn bộ.

Đây là đợt **đánh giá**, chưa thay đổi source chức năng hay tạo PR sửa mới. 35 ảnh được lưu cục bộ trong `screenshots/`; một số là trạng thái trung gian, các ảnh dẫn trong bảng là minh chứng chính. Ảnh chứa thanh trình duyệt cá nhân nên không tự động đẩy lên remote.

## Môi trường và phạm vi thật sự đã thử

- Khởi động bằng hai BAT của repository; chờ đủ tám ứng dụng và `READY` trước khi kết luận về lỗi dữ liệu.
- Backend: snapshot `snapshot-20261031-095509`; seed báo `FOLLOW-UP SOURCE READY`, `historyReady=true`, khoa Nội tổng quát, lịch 31/10 và 01/11.
- Đăng nhập trực tiếp lần lượt: `demo.doctor`, `demo.cashier`, `demo.patient2`, `demo.manager`, `demo.admin`, `demo.nurse`. Đầu phiên nurse còn phiên đăng nhập cũ; cuối phiên đã đăng nhập nurse lại sau READY để kiểm chứng.
- Chỉ đọc, tìm, chọn bộ lọc và mở các màn hình. Không ghi nhận thanh toán, không tạo lịch/hồ sơ mới, không thay mật khẩu, không đổi scope hoặc khóa tài khoản trong đợt này.
- Kết quả/đề xuất của Trần Thị Bình Demo là dữ liệu nền do seed tạo, không phải bằng chứng đã click trọn một ca khám mới trong đợt này.
- Những lỗi tải dữ liệu xuất hiện trước READY đã loại khỏi danh sách lỗi sản phẩm.
- Không đo HTTP timing, Core Web Vitals hoặc kiểm thử tải trong phiên native UI này. Các chuyển trang lần đầu có chờ biên dịch dev; không quy thời gian đó thành số đo production.

## Những chỗ cần sửa, theo mức ưu tiên

P1: cần xử lý trước vòng demo nghiệm thu tiếp theo. P2: cải thiện thao tác/bố cục sau các điểm P1. Không có kết luận về lỗi bảo mật hay sai tiền từ phiên đọc UI này.

| Mã | Mức / loại | Thấy trực tiếp | Cần sửa và điều kiện nghiệm thu | Phần phụ trách |
|---|---|---|---|---|
| UX01 | P1 / lỗi chức năng | Manager chọn khoa, giữ Từ ngày mặc định nhưng Đến ngày trống → tải buổi → lỗi hệ thống. Nurse chọn khoa, để cả hai ngày trống cũng lỗi. Điền đủ ngày thì tải được. | Ngày mặc định phù hợp, đánh dấu bắt buộc và kiểm tra thiếu/sai/đảo khoảng ngày trước khi gửi. Backend trả 400 có lỗi trường khi thiếu tham số, không trả 500. Kiểm lại cả hai form và client gọi trực tiếp. | FE Appointment + BE Clinical |
| UX02 | P1 / định tuyến công việc | Doctor vào danh sách lịch hẹn lịch sử; cashier vào hóa đơn cũ; admin vào danh sách bệnh nhân. Trang đầu không đưa tới công việc chính. | Doctor → lịch hôm nay; nurse → tiếp nhận có lối tìm/tạo hồ sơ; cashier → hàng chờ thu; admin → tổ chức/tài khoản; manager → phạm vi và tổng quan vận hành; patient → lịch/kết quả mới nhất. Giữ lối truy cập hợp lệ tới chức năng khác. | FE shell/auth và từng service |
| UX03 | P1 / mất ngữ cảnh bệnh nhân | Bác sĩ phải mở lịch, tải, mở phiếu, cuộn tới cuối để mở hồ sơ. Khi cuộn, thanh cố định chỉ còn vai trò, không có tên người bệnh. Nurse cũng mất phần bệnh nhân khi cuộn tới lượt nguồn tái khám. | Thanh tóm tắt gọn tên + ngày sinh + mã hiển thị + ngày/lượt/khoa giữ nhìn thấy khi thao tác; nút chính ở vị trí dễ thấy. Từ hàng khám có lối mở đúng bệnh án, không tạo bệnh án mới tùy tiện. Thử chuyển liên tiếp hai bệnh nhân và dữ liệu trả chậm. | FE Appointment/Clinical/shared |
| UX04 | P1 / trải nghiệm bệnh nhân | Thông báo là bảng tám cột kiểu nhật ký gửi. Chi tiết thông báo của chính patient2 hiển thị “Bệnh nhân · Chưa có quyền xem tên”, phần kỹ thuật và không có nút mở kết quả. Nội dung còn dùng từ “nurse”. | View riêng cho PATIENT: nội dung, thời gian, việc cần làm và nút xem kết quả/phiếu. Dùng hồ sơ chính chủ hoặc bỏ khối tên nếu không cần; không mở rộng quyền danh bạ để chữa nhãn. Có metadata nguồn mới liên kết đúng ca; khi chưa có thì dẫn trang kết quả của tôi và nói rõ. Dùng “điều dưỡng”. | FE Notification/Patient; BE Support nếu cần metadata |
| UX05 | P1 / khoảng trống nghiệm thu dữ liệu | Báo cáo ngoại trú hôm nay trả REPORT_NOT_FOUND dù seed có ca hoàn tất. Báo cáo cũ có cảnh báo chưa bao phủ đầy đủ luồng ngoại trú. | Xác minh job/projection/seed báo cáo; hiển thị phạm vi, thời điểm dữ liệu và trạng thái đối soát. Chưa có báo cáo phải phân biệt với số 0, không bỏ cảnh báo hay bịa KPI. Manager cần lối xem buổi khám/phiếu để đối soát trong lúc chờ. | BE Support/Report + FE Report |
| UX06 | P2 / tải dữ liệu thủ công | “Phiếu khám của tôi” vào trang chỉ có nút Tải; lịch bác sĩ cũng phải bấm tải dù đã mặc định hôm nay; quầy thu phải bấm tải hàng chờ. | Tự tải lần đầu trong phạm vi hợp lệ, giữ nút làm mới, phân biệt chưa tải/loading/rỗng/lỗi. Không tự tải toàn bệnh viện khi chưa biết scope. Tránh fetch lặp sau đổi tài khoản. | FE Appointment/Billing |
| UX07 | P2 / điều hướng | Cả “Lịch hẹn” và mục con “Tiếp nhận ca khám”/“Lịch khám của tôi”/“Quản lý buổi khám” cùng được tô active; Viện phí cũng active cùng Thu phí lịch khám. | Chỉ một mục cụ thể có aria-current=page và kiểu active; nếu muốn mở nhóm cha thì dùng kiểu nhóm riêng. Kiểm các URL chi tiết và query string. | FE shared DashboardSidebar |
| UX08 | P2 / bố cục picker | Khoa/bệnh nhân/manager chỉ chiếm khoảng 180–230 px, tên đã chọn bị cắt; cạnh đó select loại lượt và khoảng trống chiếm hàng rất rộng. Nút tìm bệnh nhân và tải hàng chờ cách nhau xa. | Picker chiếm đủ cột, nhãn/ô nhập nhất quán và cho thấy tên + ngày sinh sau chọn; gom nút cùng bộ lọc. Không làm mất việc xác nhận chọn ID thật. | FE references/Appointment/Billing |
| UX09 | P2 / thứ tự thông tin lâm sàng | Trang hồ sơ dùng gần hết màn hình đầu cho tiêu đề, mã và thời điểm tạo/cập nhật; chẩn đoán xuống dưới. Xem Lab ở cuối, kê thuốc phải rời sang module khác. | Đưa danh tính, lý do/triệu chứng, chẩn đoán và hành động chính lên trước; thời điểm kỹ thuật thu gọn. Đề xuất liên kết kê đơn gắn đúng hồ sơ, vẫn kiểm quyền và trạng thái phía server. Không tự cho hoàn tất khi Lab còn đang chờ. | FE Clinical/Pharmacy |
| UX10 | P2 / cấu trúc điều phối | Manager muốn xem lịch đã lập nhưng phải đi qua form tạo lịch dài; phạm vi khoa quản lý không thể hiện trên trang đầu. Admin lại có khối cấp scope ở đầu cùng trang lập lịch. | Tách phần xem/điều phối khỏi form tạo/sửa; hiển thị phạm vi được cấp, bộ lọc ngày và mức tải buổi trước. Scope là quản trị truy cập, nên có khu vực riêng rõ ràng. Không suy scope chỉ từ khoa công tác. | FE Appointment/Organization |
| UX11 | P2 / nhận diện tài khoản | Danh sách admin có username, role, trạng thái nhưng không có người/hồ sơ liên kết; phải mở chi tiết mới thấy Trần Quản Lý Demo. Từ chi tiết manager không có liên kết mang ngữ cảnh sang quản lý scope. | Thêm nhãn người/hồ sơ liên kết theo dữ liệu được cấp; lối quản lý scope mang đúng account, có đọc quyền hiện tại và đối chiếu thay đổi trước khi lưu. Không tự cấp/quét lại quyền khi chỉ mở trang. | FE Organization/Appointment |
| UX12 | P2 / thuật ngữ và ngày giờ | Sidebar còn “Hospital workspace”; patient có “Không gian vận hành”, “Nghiệp vụ”, “Lịch sử gửi”. Input ngày mm/dd/yyyy, giờ AM/PM trong khi thẻ hiển thị dd/MM và 24h. | Viết nội dung theo vai trò, đặt nhãn rõ “Khám đợt mới” so với tái khám; giải thích định dạng ngày/giờ. mm/dd và AM/PM là ảnh hưởng locale trình duyệt đã quan sát, không kết luận date parser sai. Kiểm cả locale vi-VN/en-US trước khi đổi control. | FE shared/copy và từng service |
| UX13 | P2 / ngữ cảnh tái khám | Lượt nguồn có bác sĩ, khoa, khoảng đề xuất; sau chọn lại ghi “Đã liên kết lượt Lê Bác Sĩ Demo”, giống tên bác sĩ hơn định danh lượt. Không thấy tên người bệnh khi cuộn tới khối này. | Tóm tắt rõ lượt ngày nào, bệnh nhân nào, khoa/bác sĩ nào; nhắc lại đề xuất và chuẩn bị. Giữ chọn nguồn rõ ràng; không coi có đề xuất là đã đăng ký thành công. | FE Appointment/references |
| OPS01 | P2 / cleanup | Clean lần đầu gặp No such container khi runner tự gỡ container giữa ls và inspect; chạy lại thành công nhưng hai image nền còn do mất danh sách ứng viên. | Read-Containers chịu được container biến mất giữa hai thao tác; giữ tập image ứng viên trước khi dừng runner, không xóa tài nguyên dùng chung. Có kiểm tra cleanup chạy lặp. | scripts/demo_25_10 |

## Bằng chứng của lỗi UX01

### Tái hiện manager

1. Đăng nhập demo.manager → Quản lý buổi khám.
2. Khoa buổi khám: gõ Nội → Tìm → chọn Khoa Nội tổng quát Demo.
3. Giữ Từ ngày = 31/10/2026, Đến ngày trống → Tải buổi đã lập.
4. Nhận “Đã xảy ra lỗi hệ thống”, kèm mã theo dõi. [Ảnh lỗi manager](screenshots/24-manager-loaded-sessions.png).

### Tái hiện nurse và đối chứng

1. Đăng nhập demo.nurse → Tiếp nhận ca khám.
2. Tìm/chọn cùng khoa; để hai ngày trống → Tải buổi khám.
3. Nhận lỗi tương tự. [Ảnh thiếu ngày](screenshots/31-nurse-missing-dates-error.png).
4. Mở từng calendar, chọn Today cho cả hai ngày → Tải buổi khám: thấy buổi 07:00–17:00, còn 39/40 chỗ. [Ảnh đối chứng hợp lệ](screenshots/32-nurse-valid-date-sessions.png).

Đối chiếu source và log, không dựa riêng vào thông báo UI:

- `ReceptionWorkspace.tsx:70` và `ScheduleManagementWorkspace.tsx:169`: truyền ngày rỗng thành undefined; chỉ kiểm khoa trước khi gửi.
- `ConsultationSessionController.java:92`: search yêu cầu cả fromDate và toDate qua RequestParam bắt buộc.
- `GlobalExceptionHandler.java`: MissingServletRequestParameterException chưa có trong nhóm malformedRequest nên rơi vào unexpected → INTERNAL_SERVER_ERROR.
- Log phiên tại `scripts/demo_25_10/evidence/snapshot-20261031-095509/clinical-service.log:96` thiếu toDate; dòng 229 thiếu fromDate. Không đưa toàn bộ log vào báo cáo vì không cần thiết.

## Ảnh cần xem theo từng vai trò

| Vai trò | Ảnh | Điểm đánh giá |
|---|---|---|
| Bác sĩ | [Trang đầu](screenshots/04-doctor-landing.png), [lịch ban đầu](screenshots/05-doctor-calendar.png), [lịch sau tải](screenshots/06-doctor-today-loaded.png), [hàng bệnh nhân](screenshots/07-doctor-session-queue.png) | Trang đầu lịch sử; nhiều bước mở hàng khám; hai mục menu active |
| Bác sĩ | [Đầu phiếu](screenshots/08-doctor-appointment-detail.png), [hành động cuối phiếu](screenshots/09-doctor-detail-actions-below-fold.png), [đầu hồ sơ](screenshots/10-doctor-record-top.png) | Nút mở hồ sơ dưới màn hình; danh tính mất khi cuộn; metadata lấn nội dung khám |
| Thu ngân | [Trang đầu](screenshots/11-cashier-landing.png), [trước tải hàng chờ](screenshots/12-cashier-exam-queue.png), [đã tải rỗng](screenshots/13-cashier-empty-queue.png) | Màn hình hóa đơn cũ; trường nhập UUID; hàng chờ không tự tải |
| Bệnh nhân | [Thông báo](screenshots/14-patient-landing.png), [chi tiết](screenshots/15-patient-notification-detail.png) | Bảng nhật ký gửi; chưa có quyền xem tên chính mình; thiếu nút mở kết quả |
| Bệnh nhân | [Kết quả khám](screenshots/16-patient-records.png), [phiếu ban đầu](screenshots/17-patient-registration-initial.png), [phiếu sau tải](screenshots/18-patient-registration-loaded.png) | Đọc được chẩn đoán và hướng dẫn tái khám; phiếu phải tải thủ công |
| Manager | [Trang đầu](screenshots/19-manager-landing.png), [bộ lọc ngoại trú](screenshots/20-manager-outpatient-report.png), [chưa có báo cáo](screenshots/21-manager-report-result.png) | Phải phân biệt báo cáo chưa sẵn sàng với số 0 |
| Manager | [Lập lịch](screenshots/22-manager-schedule.png), [tìm khoa](screenshots/23-manager-department-search.png), [lỗi tải](screenshots/24-manager-loaded-sessions.png) | Xem lịch nằm sau form tạo, thiếu ngày bắt buộc |
| Admin | [Trang đầu](screenshots/25-admin-landing.png), [tổ chức](screenshots/26-admin-organization.png), [tài khoản](screenshots/27-admin-accounts.png), [tìm manager](screenshots/28-admin-manager-search.png), [chi tiết](screenshots/29-admin-manager-detail.png), [scope](screenshots/30-admin-scheduling-scope.png) | Quản trị tổ chức khá rõ; danh sách tài khoản cần thêm người liên kết, scope cần mang ngữ cảnh |
| Điều dưỡng | [Tiếp nhận](screenshots/01-nurse-reception.png), [form hồ sơ](screenshots/02-nurse-patient-form.png), [liên hệ/BHYT](screenshots/03-nurse-form-bottom.png) | Form chia nhóm, có ví dụ BHYT; picker khoa hẹp |
| Điều dưỡng | [Lỗi ngày](screenshots/31-nurse-missing-dates-error.png), [tải hợp lệ](screenshots/32-nurse-valid-date-sessions.png), [form đăng ký](screenshots/33-nurse-registration-form.png), [nguồn tái khám](screenshots/34-nurse-followup-source.png), [đã chọn nguồn](screenshots/35-nurse-followup-selected.png) | Tái khám tìm được nguồn, có đề xuất và hướng dẫn; cần giữ danh tính khi cuộn |

## Các điểm đang làm tốt cần giữ

- Kết quả tìm bệnh nhân có họ tên và ngày sinh; gõ từ khóa chưa tự coi là đã chọn hồ sơ.
- Điều dưỡng chọn được lượt nguồn đã hoàn tất, thấy đề xuất, khoảng ngày và chuẩn bị; chưa gửi đăng ký không có nghĩa đã có lịch tái khám.
- Phiếu cá nhân sau tải có khoa, bác sĩ, phòng, số thứ tự và trạng thái; không lộ danh sách bệnh nhân khác trong các màn hình chính chủ đã thử.
- Kết quả khám chính chủ hiển thị chẩn đoán và hướng dẫn tái khám. Nội dung tiếng Anh trên ca này đến từ seed, không phải lỗi dịch nhãn UI.
- Quản trị tổ chức có tab Khoa/Phòng/Nhân sự, bộ lọc và liên kết người dùng thật trong chi tiết tài khoản.
- Lịch hiển thị sức chứa và trạng thái; lỗi có mã theo dõi. Không bỏ những kiểm tra quyền/trạng thái hiện có chỉ để rút ngắn số click.

## Thứ tự triển khai đề nghị

1. Sửa UX01 ở cả FE và BE; kiểm thử thiếu một/hai ngày, ngày không hợp lệ, khoảng đảo, dữ liệu hợp lệ và không gửi request lỗi từ form.
2. UX02–04: trang đầu theo vai trò, tóm tắt bệnh nhân cố định, thông báo bệnh nhân có hành động rõ. Trước mắt có thể dùng danh sách kết quả chính chủ; không suy đoán record ID từ notification ID.
3. Xác minh nguồn báo cáo UX05; hoàn thiện autoload và bộ lọc theo scope. Không ghi nhận số 0 khi chưa có dữ liệu đối soát.
4. UX07–13: điều hướng, picker, thứ tự nội dung khám, điều phối lịch và quản trị tài khoản/scope.
5. Sửa cleanup OPS01 và chạy vòng kiểm thử thực tế tiếp theo trên cùng một bệnh nhân từ tiếp nhận tới thuốc; thêm mobile, bàn phím và tên/dữ liệu dài.

## Giới hạn đánh giá

- Không thực hiện lại toàn bộ nghiệp vụ thu tiền/xét nghiệm/cấp thuốc trong phiên này; xem biên bản 29/10 để biết phần nào đã click ở phiên trước.
- Chưa thử hoàn tất đổi mật khẩu mới, lưu quyền, khóa tài khoản, hủy buổi, xử lý hoàn tiền, lỗi mạng khi ghi, hay hai nhân viên đồng thời.
- Chưa đo tương phản màu/font theo tiêu chuẩn, chưa kiểm screen reader và mobile. Bố cục nhận xét dựa trên viewport thực tế của phiên, không suy rộng mọi thiết bị.
- Không có thay đổi source chức năng nên không chạy lại 205 unit test cũ để coi là bằng chứng cho những đề xuất chưa triển khai.

## Kết thúc

Đã đăng xuất, đóng tab demo do phiên này mở, dừng đúng frontend của repository. Chạy clean-docker.bat: lần đầu có race No such container; lần hai exit 0. Gỡ thêm đúng hai image nền demo postgres:16-alpine và rabbitmq:3.13-alpine sau khi không còn container; giữ volume và image MCP. Xác minh không còn cổng 3000 và tám cổng backend demo đang lắng nghe. Không chạy global prune.
