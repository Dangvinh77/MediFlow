# Task Sheet — Kế hoạch hoàn thiện MediFlow

- **Ngày lập kế hoạch:** 16/10/2026.
- **Thời gian thực hiện:** 17/10/2026–24/10/2026.
- **Ngày kết thúc dự kiến:** 24/10/2026.
- **Phạm vi:** hoàn thiện backend, frontend web và ứng dụng mobile phục vụ tiếp nhận, khám ngoại trú, xét nghiệm, cấp thuốc, thanh toán, báo cáo và thông báo.

Đây là kế hoạch công việc sắp tới, dựa trên chức năng đã có để hoàn thiện và tích hợp. Ngày bắt đầu và ngày kết thúc trong bảng là ngày dự kiến; trạng thái được khởi tạo tại ngày lập kế hoạch, chưa xác nhận công việc đã triển khai hoặc hoàn thành. Chỉ chuyển sang **Đang thực hiện**, **Chờ xử lý phụ thuộc** hoặc **Hoàn thành** khi có cập nhật thực tế. Công việc chỉ hoàn thành khi đạt kết quả và kiểm tra tương ứng trong bảng.

## Phân công

| Thành viên | Backend | Frontend và mobile theo nghiệp vụ |
|---|---|---|
| Vinh | Khám bệnh, xét nghiệm; điều phối tích hợp | Lịch khám, hồ sơ khám, xét nghiệm; điều phối giao diện chung |
| Huy | Dược, báo cáo | Thuốc, đơn thuốc, báo cáo |
| Hoàng Anh | Tổ chức, người bệnh, Gateway | Nhân sự, khoa phòng, người bệnh; phối hợp đăng nhập và phân quyền |
| Lộc | Thanh toán, thông báo | Hóa đơn, thanh toán, thông báo |

Phân công mobile là kế hoạch phối hợp theo nghiệp vụ. Mỗi thành viên sử dụng giao diện và hợp đồng backend đã được thống nhất; phần tích hợp chung được Vinh điều phối, giữ trách nhiệm từng thành viên đối với nghiệp vụ của mình.

## Kế hoạch backend

| STT | Công việc | Phụ trách | Bắt đầu | Kết thúc | Trạng thái | Kết quả cần đạt |
|---:|---|---|---|---|---|---|
| 1 | Rà soát chức năng còn thiếu, thống nhất dữ liệu trao đổi và thứ tự tích hợp | Cả nhóm; Vinh tổng hợp | 17/10/2026 | 17/10/2026 | Chưa bắt đầu | Danh sách việc cần sửa, API, quyền và dữ liệu kiểm thử được thống nhất cho web và mobile |
| 2 | Hoàn thiện Core, quản lý tài khoản, nhân sự, khoa phòng và người bệnh | Hoàng Anh | 17/10/2026 | 18/10/2026 | Chưa bắt đầu | Đăng nhập và quản lý hồ sơ đúng quyền; dữ liệu Organization và Patient được quản lý độc lập |
| 3 | Hoàn thiện định tuyến Gateway và kiểm tra danh tính người gọi | Hoàng Anh | 18/10/2026 | 19/10/2026 | Chưa bắt đầu | Các API được phép sử dụng truy cập được qua Gateway; từ chối yêu cầu sai quyền hoặc hết phiên |
| 4 | Hoàn thiện lịch khám, tiếp nhận, bắt đầu khám và kết thúc khám | Vinh | 17/10/2026 | 19/10/2026 | Chưa bắt đầu | Luồng khám chuyển trạng thái đúng; kiểm tra điều kiện thanh toán trước thao tác cần thu phí |
| 5 | Hoàn thiện chỉ định, tiếp nhận mẫu và trả kết quả xét nghiệm | Vinh | 18/10/2026 | 20/10/2026 | Chưa bắt đầu | Chỉ định liên kết đúng hồ sơ; kết quả và trạng thái được đồng bộ; không tạo trùng khi gửi lại |
| 6 | Hoàn thiện quản lý thuốc, tồn kho, đơn thuốc và cấp thuốc | Huy | 17/10/2026 | 20/10/2026 | Chưa bắt đầu | Kiểm tra tồn kho và thanh toán; cấp hoặc hủy đúng quyền, không cấp trùng |
| 7 | Hoàn thiện khoản phí, hóa đơn và ghi nhận thanh toán | Lộc | 17/10/2026 | 20/10/2026 | Chưa bắt đầu | Thu đúng khoản phí của lượt khám, xét nghiệm và đơn thuốc; tính tiền chính xác, không ghi nhận trùng |
| 8 | Hoàn thiện báo cáo và kiểm tra nguồn dữ liệu | Huy | 19/10/2026 | 21/10/2026 | Chưa bắt đầu | Báo cáo có bộ lọc phù hợp, số liệu đối chiếu được; thể hiện rõ khi dữ liệu chưa có |
| 9 | Hoàn thiện thông báo trong hệ thống và quyền xem của người nhận | Lộc | 19/10/2026 | 21/10/2026 | Chưa bắt đầu | Thông báo đến đúng người; không lộ thông tin người bệnh khác; xử lý gửi lại không tạo trùng |
| 10 | Hoàn thiện đóng gói Support và cấu hình kết nối các dịch vụ | Huy, Lộc; Vinh điều phối; Hoàng Anh phối hợp Gateway | 20/10/2026 | 21/10/2026 | Chưa bắt đầu | Core, Clinical, Lab, Pharmacy, Billing và Support khởi chạy, định tuyến và kết nối dữ liệu đúng cấu hình |
| 11 | Kiểm thử backend, phân quyền và phục hồi khi có lỗi | Từng thành viên theo nghiệp vụ | 21/10/2026 | 22/10/2026 | Chưa bắt đầu | Kiểm tra thành công, sai quyền, sai trạng thái, gửi trùng, mất kết nối và xử lý lại; sửa lỗi phát hiện |

## Kế hoạch frontend web

| STT | Công việc | Phụ trách | Bắt đầu | Kết thúc | Trạng thái | Kết quả cần đạt |
|---:|---|---|---|---|---|---|
| 12 | Rà soát đăng nhập, phân quyền menu, bố cục và hiển thị trên điện thoại | Vinh điều phối; Hoàng Anh phối hợp tài khoản | 17/10/2026 | 18/10/2026 | Chưa bắt đầu | Menu đúng vai trò; xử lý hết phiên; bố cục sử dụng được trên màn hình nhỏ |
| 13 | Hoàn thiện giao diện nhân sự, khoa phòng và người bệnh | Hoàng Anh | 18/10/2026 | 20/10/2026 | Chưa bắt đầu | Tìm kiếm, phân trang, xem và cập nhật đúng quyền; kiểm tra dữ liệu nhập và lỗi API |
| 14 | Hoàn thiện giao diện lịch khám, tiếp nhận và hồ sơ khám | Vinh | 18/10/2026 | 20/10/2026 | Chưa bắt đầu | Thực hiện được luồng khám qua Gateway; hiển thị đúng trạng thái và điều kiện thanh toán |
| 15 | Hoàn thiện giao diện chỉ định và kết quả xét nghiệm | Vinh | 19/10/2026 | 21/10/2026 | Chưa bắt đầu | Hiển thị chỉ định, cập nhật trạng thái và kết quả đúng hồ sơ, đúng quyền |
| 16 | Hoàn thiện giao diện thuốc, tồn kho và cấp thuốc | Huy | 18/10/2026 | 21/10/2026 | Chưa bắt đầu | Tìm thuốc, xem tồn, xử lý đơn và cấp thuốc theo trạng thái backend; dùng giá do máy chủ cung cấp |
| 17 | Hoàn thiện giao diện hóa đơn và thanh toán | Lộc | 18/10/2026 | 21/10/2026 | Chưa bắt đầu | Chọn đúng khoản phí, xem hóa đơn và ghi nhận thanh toán; ngăn thao tác lặp và hiển thị lỗi rõ ràng |
| 18 | Hoàn thiện giao diện báo cáo | Huy | 20/10/2026 | 21/10/2026 | Chưa bắt đầu | Bộ lọc và số liệu khớp backend; có trạng thái đang tải, không có dữ liệu và lỗi |
| 19 | Hoàn thiện giao diện thông báo | Lộc | 20/10/2026 | 21/10/2026 | Chưa bắt đầu | Danh sách và chi tiết đúng người nhận; thao tác chỉ xuất hiện khi backend hỗ trợ |
| 20 | Kiểm thử giao diện theo vai trò và luồng nghiệp vụ | Từng thành viên theo nghiệp vụ | 21/10/2026 | 22/10/2026 | Chưa bắt đầu | Các trang chính sử dụng API thật, xử lý lỗi và phân quyền đúng; ghi lại lỗi cần sửa |

## Kế hoạch mobile

Ứng dụng mobile ưu tiên hoàn thiện luồng đã được backend hỗ trợ. Ngày 17/10, nhóm chốt màn hình và vai trò trên ứng dụng; chỉ đưa chức năng vào bàn giao khi API và quyền truy cập đã sẵn sàng. Những chức năng chưa có hợp đồng backend được ghi nhận để xử lý phụ thuộc, không giả lập thành chức năng đã hoàn thành.

| STT | Công việc | Phụ trách | Bắt đầu | Kết thúc | Trạng thái | Kết quả cần đạt |
|---:|---|---|---|---|---|---|
| 21 | Rà soát ứng dụng hiện có, chốt màn hình và chuẩn bị cấu hình kết nối | Vinh điều phối; cả nhóm rà soát nghiệp vụ | 17/10/2026 | 18/10/2026 | Chưa bắt đầu | Danh sách màn hình, vai trò và môi trường chạy được thống nhất; ứng dụng kết nối qua Gateway |
| 22 | Hoàn thiện đăng nhập, lưu phiên an toàn và điều hướng theo quyền | Vinh điều phối phần chung; Hoàng Anh phối hợp xác thực | 18/10/2026 | 19/10/2026 | Chưa bắt đầu | Đăng nhập, đăng xuất và hết phiên được xử lý; không hiển thị màn hình ngoài quyền được cấp |
| 23 | Hoàn thiện tra cứu và hiển thị thông tin người bệnh | Hoàng Anh | 19/10/2026 | 20/10/2026 | Chưa bắt đầu | Xem, tìm kiếm hồ sơ theo vai trò và API được cấp; bảo vệ dữ liệu cá nhân |
| 24 | Hoàn thiện màn hình lịch khám và thông tin khám | Vinh | 19/10/2026 | 21/10/2026 | Chưa bắt đầu | Xem lịch, trạng thái và thông tin khám theo quyền; thao tác cập nhật chỉ dùng API được hỗ trợ |
| 25 | Hoàn thiện màn hình kết quả xét nghiệm | Vinh | 20/10/2026 | 21/10/2026 | Chưa bắt đầu | Kết quả gắn đúng người bệnh và hồ sơ; xử lý tải lại, dữ liệu trống và lỗi |
| 26 | Hoàn thiện màn hình thông tin thuốc và đơn thuốc | Huy | 19/10/2026 | 21/10/2026 | Chưa bắt đầu | Hiển thị thuốc, hướng dẫn và trạng thái đơn theo dữ liệu backend và quyền người dùng |
| 27 | Hoàn thiện màn hình hóa đơn và trạng thái thanh toán | Lộc | 19/10/2026 | 21/10/2026 | Chưa bắt đầu | Xem đúng khoản phí, số tiền và trạng thái; chỉ cung cấp thao tác thanh toán đã có backend hỗ trợ |
| 28 | Hoàn thiện màn hình thông báo của người dùng | Lộc | 20/10/2026 | 21/10/2026 | Chưa bắt đầu | Hiển thị đúng người nhận; không truy cập thông báo của tài khoản khác |
| 29 | Kiểm thử mobile trên thiết bị và chuẩn bị bản cài Android | Từng thành viên kiểm tra nghiệp vụ; Vinh tổng hợp bản cài | 21/10/2026 | 22/10/2026 | Chưa bắt đầu | Kiểm tra kích thước màn hình, bàn phím, mạng chậm, mất mạng và hết phiên; cài và chạy được bản thử nghiệm |

## Tích hợp, nghiệm thu và bàn giao

| STT | Công việc | Phụ trách | Bắt đầu | Kết thúc | Trạng thái | Kết quả cần đạt |
|---:|---|---|---|---|---|---|
| 30 | Chuẩn bị tài khoản theo vai trò, dữ liệu mẫu và kịch bản kiểm thử chung | Cả nhóm; Hoàng Anh chuẩn bị tài khoản | 20/10/2026 | 21/10/2026 | Chưa bắt đầu | Có dữ liệu thống nhất để kiểm tra backend, web và mobile; không dùng dữ liệu cá nhân thật |
| 31 | Kiểm thử xuyên suốt tiếp nhận, khám, xét nghiệm, thanh toán và cấp thuốc | Cả nhóm; Vinh điều phối | 22/10/2026 | 22/10/2026 | Chưa bắt đầu | Chạy luồng thực tế qua Gateway trên web và các màn hình mobile đã thống nhất; đối chiếu trạng thái, tiền và dữ liệu |
| 32 | Kiểm tra báo cáo, thông báo, quyền truy cập và tính nhất quán giữa các ứng dụng | Cả nhóm theo nghiệp vụ | 22/10/2026 | 23/10/2026 | Chưa bắt đầu | Số liệu và trạng thái khớp nhau; không lộ dữ liệu; sự kiện gửi lại không tạo nghiệp vụ trùng |
| 33 | Sửa lỗi tích hợp và kiểm thử lại các luồng bị ảnh hưởng | Từng thành viên theo nghiệp vụ | 23/10/2026 | 23/10/2026 | Chưa bắt đầu | Đóng lỗi chặn đăng nhập, luồng khám, thanh toán, cấp thuốc và lỗi bảo mật; ghi rõ hạn chế còn lại |
| 34 | Hoàn thiện hướng dẫn cài đặt, sử dụng, báo cáo và nội dung trình bày | Cả nhóm; Vinh tổng hợp | 23/10/2026 | 24/10/2026 | Chưa bắt đầu | Hướng dẫn phản ánh đúng chức năng chạy được; có cấu hình, tài khoản demo và kịch bản trình bày |
| 35 | Đóng gói, nghiệm thu cuối và bàn giao dự án | Cả nhóm | 24/10/2026 | 24/10/2026 | Chưa bắt đầu | Chạy lại kịch bản chính, kiểm tra bản cài và môi trường; bàn giao mã nguồn, cấu hình, tài liệu và danh sách hạn chế |

## Mốc hoàn thành

| Ngày | Mục tiêu |
|---|---|
| 17/10/2026 | Chốt công việc, API, quyền và màn hình mobile; xác định phụ thuộc cần giải quyết |
| 19/10/2026 | Sẵn sàng đăng nhập và định tuyến; hoàn thiện nền tảng tích hợp |
| 21/10/2026 | Hoàn thiện chức năng theo nghiệp vụ, chuẩn bị dữ liệu và ứng dụng để kiểm thử chung |
| 22/10/2026 | Kiểm thử xuyên suốt backend, frontend và mobile |
| 23/10/2026 | Sửa lỗi, kiểm thử lại và ổn định bản bàn giao |
| 24/10/2026 | Nghiệm thu, trình bày và bàn giao; kết thúc dự án theo kế hoạch |

Công việc giao diện phụ thuộc API và quyền tương ứng; báo cáo và thông báo phụ thuộc dữ liệu nghiệp vụ được phát sinh đúng. Nếu phụ thuộc chưa sẵn sàng vào ngày đã đặt, người phụ trách cập nhật trạng thái và báo nhóm trong ngày để điều chỉnh phần việc trước hạn bàn giao. Không mở rộng sang nội trú và phẫu thuật trong lịch này. Tại buổi nghiệm thu, ghi rõ phần hoàn thành, lỗi còn tồn tại và phạm vi chưa bàn giao; ngày dự kiến không thay thế kết quả kiểm thử thực tế.
