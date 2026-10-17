# MediFlow — Task Sheet

## Tiếng Việt

**Ngày lập:** 16/10/2026. **Thời gian dự kiến:** 01/10/2026–24/10/2026. **Ngày kết thúc dự án dự kiến:** 24/10/2026.

Các giá trị trong Ngày bắt đầu thực tế và Số ngày thực tế được điền theo lịch dự kiến để lập kế hoạch, chưa phải số liệu thực hiện đã xác nhận. Số ngày tính theo ngày lịch, gồm ngày bắt đầu và ngày kết thúc; các công việc có thể thực hiện song song. Ngày bắt đầu dự kiến nằm trong khoảng 01–10/10, ngày bắt đầu thực tế theo lịch này sau đó 3–4 ngày, thời lượng từ 5–12 ngày. Khi triển khai, cập nhật hai cột này và trạng thái theo thực tế. Mobile sử dụng API và quyền truy cập đã thống nhất.

**Trạng thái rà soát ngày 16/10/2026:** đối chiếu source backend, frontend, mobile và kết quả kiểm thử đã ghi nhận trong repository. Đang thực hiện: đã có một phần công việc; Chờ tích hợp: đã có mã nhưng phụ thuộc hoặc nghiệm thu liên thông còn mở; Chưa bắt đầu: chưa có phần triển khai tương ứng; Chưa nghiệm thu: chưa có kết quả bàn giao cuối. Mỗi trạng thái áp dụng cho toàn bộ nội dung của dòng. Lượt cập nhật này không chạy lại kiểm thử ứng dụng.

<table>
<thead>
<tr>
<th rowspan="2">STT</th>
<th rowspan="2">Thành viên</th>
<th rowspan="2">Nội dung công việc</th>
<th rowspan="2">Tên trang / Phạm vi</th>
<th>Kế hoạch hoạt động</th>
<th colspan="3">Thông tin thực hiện hoạt động</th>
</tr>
<tr>
<th>Ngày bắt đầu dự kiến</th>
<th>Ngày bắt đầu thực tế</th>
<th>Số ngày thực tế</th>
<th>Trạng thái</th>
</tr>
</thead>
<tbody>
<tr>
<td>1</td>
<td>Cả nhóm; Vinh tổng hợp</td>
<td>Rà soát chức năng còn thiếu, thống nhất dữ liệu trao đổi và thứ tự tích hợp.<br>Danh sách việc cần sửa, API, quyền và dữ liệu kiểm thử được thống nhất cho web và mobile.</td>
<td>Backend / Frontend / Mobile — thống nhất chức năng và API</td>
<td>2026-10-01</td>
<td>2026-10-04</td>
<td>5</td>
<td><strong>Đang thực hiện</strong><br>Đã có thiết kế và API; còn thống nhất tích hợp web/mobile.</td>
</tr>
<tr>
<td>2</td>
<td>Hoàng Anh</td>
<td>Hoàn thiện Core, quản lý tài khoản, nhân sự, khoa phòng và người bệnh.<br>Đăng nhập và quản lý hồ sơ đúng quyền; dữ liệu Organization và Patient được quản lý độc lập.</td>
<td>Backend — Core: Organization, Patient</td>
<td>2026-10-01</td>
<td>2026-10-04</td>
<td>10</td>
<td><strong>Chờ tích hợp</strong><br>Đã có Core và các API; chưa xác nhận đầy đủ luồng đăng nhập và dữ liệu liên thông.</td>
</tr>
<tr>
<td>3</td>
<td>Hoàng Anh</td>
<td>Hoàn thiện định tuyến Gateway và kiểm tra danh tính người gọi.<br>Các API được phép sử dụng truy cập được qua Gateway; từ chối yêu cầu sai quyền hoặc hết phiên.</td>
<td>Backend — Gateway: đăng nhập, định tuyến, phân quyền</td>
<td>2026-10-02</td>
<td>2026-10-06</td>
<td>9</td>
<td><strong>Đang thực hiện</strong><br>Đã có định tuyến và kiểm tra quyền; còn thiếu đường tra cứu khoản phí và đối chiếu quyền hủy đơn.</td>
</tr>
<tr>
<td>4</td>
<td>Vinh</td>
<td>Hoàn thiện lịch khám, tiếp nhận, bắt đầu khám và kết thúc khám.<br>Luồng khám chuyển trạng thái đúng; kiểm tra điều kiện thanh toán trước thao tác cần thu phí.</td>
<td>Backend — Clinical: lịch khám, hồ sơ khám</td>
<td>2026-10-01</td>
<td>2026-10-05</td>
<td>12</td>
<td><strong>Chờ tích hợp</strong><br>Đã có xử lý nghiệp vụ và kiểm thử; còn nghiệm thu luồng khám với thanh toán thật.</td>
</tr>
<tr>
<td>5</td>
<td>Vinh</td>
<td>Hoàn thiện chỉ định, tiếp nhận mẫu và trả kết quả xét nghiệm.<br>Chỉ định liên kết đúng hồ sơ; kết quả và trạng thái được đồng bộ; không tạo trùng khi gửi lại.</td>
<td>Backend — Lab: chỉ định, mẫu, kết quả</td>
<td>2026-10-02</td>
<td>2026-10-06</td>
<td>12</td>
<td><strong>Chờ tích hợp</strong><br>Đã có chỉ định, kết quả và kiểm thử phục hồi; còn nghiệm thu thanh toán xét nghiệm liên thông.</td>
</tr>
<tr>
<td>6</td>
<td>Huy</td>
<td>Hoàn thiện quản lý thuốc, tồn kho, đơn thuốc và cấp thuốc.<br>Kiểm tra tồn kho và thanh toán; cấp hoặc hủy đúng quyền, không cấp trùng.</td>
<td>Backend — Pharmacy: thuốc, tồn kho, đơn thuốc</td>
<td>2026-10-01</td>
<td>2026-10-04</td>
<td>12</td>
<td><strong>Chờ tích hợp</strong><br>Đã có quản lý thuốc và cấp thuốc; còn kiểm tra quyền qua Gateway và luồng đơn đã thanh toán.</td>
</tr>
<tr>
<td>7</td>
<td>Lộc</td>
<td>Hoàn thiện khoản phí, hóa đơn và ghi nhận thanh toán.<br>Thu đúng khoản phí của lượt khám, xét nghiệm và đơn thuốc; tính tiền chính xác, không ghi nhận trùng.</td>
<td>Backend — Billing: khoản phí, hóa đơn, thanh toán</td>
<td>2026-10-01</td>
<td>2026-10-05</td>
<td>12</td>
<td><strong>Đang thực hiện</strong><br>Đã có thu phí, thanh toán và kiểm thử; còn bổ sung bằng chứng phát sự kiện và nghiệm thu với bên nhận.</td>
</tr>
<tr>
<td>8</td>
<td>Huy</td>
<td>Hoàn thiện báo cáo và kiểm tra nguồn dữ liệu.<br>Báo cáo có bộ lọc phù hợp, số liệu đối chiếu được; thể hiện rõ khi dữ liệu chưa có.</td>
<td>Backend — Report: báo cáo</td>
<td>2026-10-04</td>
<td>2026-10-07</td>
<td>10</td>
<td><strong>Chờ tích hợp</strong><br>Đã có API báo cáo; nguồn dữ liệu và độ đầy đủ số liệu còn chờ xác nhận.</td>
</tr>
<tr>
<td>9</td>
<td>Lộc</td>
<td>Hoàn thiện thông báo trong hệ thống và quyền xem của người nhận.<br>Thông báo đến đúng người; không lộ thông tin người bệnh khác; xử lý gửi lại không tạo trùng.</td>
<td>Backend — Notification: thông báo</td>
<td>2026-10-04</td>
<td>2026-10-08</td>
<td>10</td>
<td><strong>Chờ tích hợp</strong><br>Đã có API thông báo; còn nghiệm thu sự kiện thực và quyền người nhận trong luồng chung.</td>
</tr>
<tr>
<td>10</td>
<td>Huy, Lộc; Vinh điều phối; Hoàng Anh phối hợp Gateway</td>
<td>Hoàn thiện đóng gói Support và cấu hình kết nối các dịch vụ.<br>Core, Clinical, Lab, Pharmacy, Billing và Support khởi chạy, định tuyến và kết nối dữ liệu đúng cấu hình.</td>
<td>Backend — Support và cấu hình tích hợp</td>
<td>2026-10-05</td>
<td>2026-10-09</td>
<td>10</td>
<td><strong>Chưa bắt đầu</strong><br>Chưa có module Support; các dịch vụ báo cáo và thông báo còn tách riêng.</td>
</tr>
<tr>
<td>11</td>
<td>Từng thành viên theo nghiệp vụ</td>
<td>Kiểm thử backend, phân quyền và phục hồi khi có lỗi.<br>Kiểm tra thành công, sai quyền, sai trạng thái, gửi trùng, mất kết nối và xử lý lại; sửa lỗi phát hiện.</td>
<td>Backend — kiểm thử các nghiệp vụ</td>
<td>2026-10-07</td>
<td>2026-10-11</td>
<td>11</td>
<td><strong>Đang thực hiện</strong><br>Đã có nhiều kiểm thử nghiệp vụ và phục hồi; kiểm thử liên thông toàn hệ thống còn mở.</td>
</tr>
<tr>
<td>12</td>
<td>Vinh điều phối; Hoàng Anh phối hợp tài khoản</td>
<td>Rà soát đăng nhập, phân quyền menu, bố cục và hiển thị trên điện thoại.<br>Menu đúng vai trò; xử lý hết phiên; bố cục sử dụng được trên màn hình nhỏ.</td>
<td>Frontend — đăng nhập, menu, bố cục chung</td>
<td>2026-10-01</td>
<td>2026-10-05</td>
<td>7</td>
<td><strong>Đang thực hiện</strong><br>Đã có khung giao diện, menu và đăng nhập; còn kiểm tra phiên thật và điều hướng theo vai trò.</td>
</tr>
<tr>
<td>13</td>
<td>Hoàng Anh</td>
<td>Hoàn thiện giao diện nhân sự, khoa phòng và người bệnh.<br>Tìm kiếm, phân trang, xem và cập nhật đúng quyền; kiểm tra dữ liệu nhập và lỗi API.</td>
<td>Frontend — /organization; /patients</td>
<td>2026-10-03</td>
<td>2026-10-06</td>
<td>11</td>
<td><strong>Chờ tích hợp</strong><br>Đã có các trang và thao tác quản lý; còn xác nhận hoạt động với Core qua Gateway.</td>
</tr>
<tr>
<td>14</td>
<td>Vinh</td>
<td>Hoàn thiện giao diện lịch khám, tiếp nhận và hồ sơ khám.<br>Thực hiện được luồng khám qua Gateway; hiển thị đúng trạng thái và điều kiện thanh toán.</td>
<td>Frontend — /appointments; /records</td>
<td>2026-10-03</td>
<td>2026-10-07</td>
<td>11</td>
<td><strong>Chờ tích hợp</strong><br>Đã có lịch và hồ sơ khám; các thao tác khám liên quan thanh toán còn chờ nghiệm thu.</td>
</tr>
<tr>
<td>15</td>
<td>Vinh</td>
<td>Hoàn thiện giao diện chỉ định và kết quả xét nghiệm.<br>Hiển thị chỉ định, cập nhật trạng thái và kết quả đúng hồ sơ, đúng quyền.</td>
<td>Frontend — /lab; /records</td>
<td>2026-10-04</td>
<td>2026-10-07</td>
<td>11</td>
<td><strong>Chờ tích hợp</strong><br>Đã có giao diện và kiểm thử cục bộ; chưa xác nhận luồng xét nghiệm đã thanh toán qua Gateway.</td>
</tr>
<tr>
<td>16</td>
<td>Huy</td>
<td>Hoàn thiện giao diện thuốc, tồn kho và cấp thuốc.<br>Tìm thuốc, xem tồn, xử lý đơn và cấp thuốc theo trạng thái backend; dùng giá do máy chủ cung cấp.</td>
<td>Frontend — /pharmacy</td>
<td>2026-10-03</td>
<td>2026-10-07</td>
<td>11</td>
<td><strong>Chờ tích hợp</strong><br>Đã có màn hình thuốc, tồn kho và xử lý đơn; còn kiểm tra vai trò thật và cấp thuốc sau thanh toán.</td>
</tr>
<tr>
<td>17</td>
<td>Lộc</td>
<td>Hoàn thiện giao diện hóa đơn và thanh toán.<br>Chọn đúng khoản phí, xem hóa đơn và ghi nhận thanh toán; ngăn thao tác lặp và hiển thị lỗi rõ ràng.</td>
<td>Frontend — /billing</td>
<td>2026-10-03</td>
<td>2026-10-06</td>
<td>12</td>
<td><strong>Chờ tích hợp</strong><br>Đã có hóa đơn và giao diện thanh toán; tra cứu khoản phí theo nghiệp vụ qua Gateway còn thiếu.</td>
</tr>
<tr>
<td>18</td>
<td>Huy</td>
<td>Hoàn thiện giao diện báo cáo.<br>Bộ lọc và số liệu khớp backend; có trạng thái đang tải, không có dữ liệu và lỗi.</td>
<td>Frontend — /reports</td>
<td>2026-10-05</td>
<td>2026-10-08</td>
<td>10</td>
<td><strong>Chờ tích hợp</strong><br>Đã có các trang và kiểm thử báo cáo cục bộ; còn đối chiếu số liệu thực và nguồn dữ liệu.</td>
</tr>
<tr>
<td>19</td>
<td>Lộc</td>
<td>Hoàn thiện giao diện thông báo.<br>Danh sách và chi tiết đúng người nhận; thao tác chỉ xuất hiện khi backend hỗ trợ.</td>
<td>Frontend — /notifications</td>
<td>2026-10-05</td>
<td>2026-10-09</td>
<td>10</td>
<td><strong>Chờ tích hợp</strong><br>Đã có danh sách, chi tiết và gửi thông báo; còn kiểm tra người nhận với phiên đăng nhập thật.</td>
</tr>
<tr>
<td>20</td>
<td>Từng thành viên theo nghiệp vụ</td>
<td>Kiểm thử giao diện theo vai trò và luồng nghiệp vụ.<br>Các trang chính sử dụng API thật, xử lý lỗi và phân quyền đúng; ghi lại lỗi cần sửa.</td>
<td>Frontend — kiểm thử các trang theo vai trò</td>
<td>2026-10-07</td>
<td>2026-10-11</td>
<td>11</td>
<td><strong>Đang thực hiện</strong><br>Đã có kiểm thử giao diện cục bộ; chưa kiểm tra đủ vai trò và luồng API thật.</td>
</tr>
<tr>
<td>21</td>
<td>Vinh điều phối; cả nhóm rà soát nghiệp vụ</td>
<td>Rà soát ứng dụng hiện có, chốt màn hình và chuẩn bị cấu hình kết nối.<br>Danh sách màn hình, vai trò và môi trường chạy được thống nhất; ứng dụng kết nối qua Gateway.</td>
<td>Mobile — cấu hình ứng dụng, danh sách màn hình</td>
<td>2026-10-01</td>
<td>2026-10-04</td>
<td>6</td>
<td><strong>Đang thực hiện</strong><br>Đã có khung thư mục và thành phần loading; chưa có cấu hình ứng dụng và kết nối Gateway.</td>
</tr>
<tr>
<td>22</td>
<td>Vinh điều phối phần chung; Hoàng Anh phối hợp xác thực</td>
<td>Hoàn thiện đăng nhập, lưu phiên an toàn và điều hướng theo quyền.<br>Đăng nhập, đăng xuất và hết phiên được xử lý; không hiển thị màn hình ngoài quyền được cấp.</td>
<td>Mobile — đăng nhập, đăng xuất, điều hướng</td>
<td>2026-10-02</td>
<td>2026-10-06</td>
<td>8</td>
<td><strong>Chưa bắt đầu</strong><br>Chưa có mã đăng nhập, lưu phiên và điều hướng mobile.</td>
</tr>
<tr>
<td>23</td>
<td>Hoàng Anh</td>
<td>Hoàn thiện tra cứu và hiển thị thông tin người bệnh.<br>Xem, tìm kiếm hồ sơ theo vai trò và API được cấp; bảo vệ dữ liệu cá nhân.</td>
<td>Mobile — thông tin người bệnh</td>
<td>2026-10-04</td>
<td>2026-10-07</td>
<td>10</td>
<td><strong>Chưa bắt đầu</strong><br>Chưa có màn hình và xử lý dữ liệu người bệnh trên mobile.</td>
</tr>
<tr>
<td>24</td>
<td>Vinh</td>
<td>Hoàn thiện màn hình lịch khám và thông tin khám.<br>Xem lịch, trạng thái và thông tin khám theo quyền; thao tác cập nhật chỉ dùng API được hỗ trợ.</td>
<td>Mobile — lịch khám, thông tin khám</td>
<td>2026-10-04</td>
<td>2026-10-08</td>
<td>10</td>
<td><strong>Chưa bắt đầu</strong><br>Chưa có màn hình lịch khám và thông tin khám trên mobile.</td>
</tr>
<tr>
<td>25</td>
<td>Vinh</td>
<td>Hoàn thiện màn hình kết quả xét nghiệm.<br>Kết quả gắn đúng người bệnh và hồ sơ; xử lý tải lại, dữ liệu trống và lỗi.</td>
<td>Mobile — kết quả xét nghiệm</td>
<td>2026-10-05</td>
<td>2026-10-08</td>
<td>10</td>
<td><strong>Chưa bắt đầu</strong><br>Chưa có màn hình kết quả xét nghiệm trên mobile.</td>
</tr>
<tr>
<td>26</td>
<td>Huy</td>
<td>Hoàn thiện màn hình thông tin thuốc và đơn thuốc.<br>Hiển thị thuốc, hướng dẫn và trạng thái đơn theo dữ liệu backend và quyền người dùng.</td>
<td>Mobile — thuốc, đơn thuốc</td>
<td>2026-10-04</td>
<td>2026-10-08</td>
<td>10</td>
<td><strong>Chưa bắt đầu</strong><br>Chưa có màn hình thuốc và đơn thuốc trên mobile.</td>
</tr>
<tr>
<td>27</td>
<td>Lộc</td>
<td>Hoàn thiện màn hình hóa đơn và trạng thái thanh toán.<br>Xem đúng khoản phí, số tiền và trạng thái; chỉ cung cấp thao tác thanh toán đã có backend hỗ trợ.</td>
<td>Mobile — hóa đơn, trạng thái thanh toán</td>
<td>2026-10-04</td>
<td>2026-10-07</td>
<td>11</td>
<td><strong>Chưa bắt đầu</strong><br>Chưa có màn hình hóa đơn và thanh toán trên mobile.</td>
</tr>
<tr>
<td>28</td>
<td>Lộc</td>
<td>Hoàn thiện màn hình thông báo của người dùng.<br>Hiển thị đúng người nhận; không truy cập thông báo của tài khoản khác.</td>
<td>Mobile — thông báo</td>
<td>2026-10-05</td>
<td>2026-10-09</td>
<td>10</td>
<td><strong>Chưa bắt đầu</strong><br>Chưa có màn hình thông báo trên mobile.</td>
</tr>
<tr>
<td>29</td>
<td>Từng thành viên kiểm tra nghiệp vụ; Vinh tổng hợp bản cài</td>
<td>Kiểm thử mobile trên thiết bị và chuẩn bị bản cài Android.<br>Kiểm tra kích thước màn hình, bàn phím, mạng chậm, mất mạng và hết phiên; cài và chạy được bản thử nghiệm.</td>
<td>Mobile — kiểm thử thiết bị, bản cài Android</td>
<td>2026-10-07</td>
<td>2026-10-11</td>
<td>11</td>
<td><strong>Chưa bắt đầu</strong><br>Chưa có ứng dụng hoàn chỉnh, kiểm thử thiết bị hoặc bản cài Android để nghiệm thu.</td>
</tr>
<tr>
<td>30</td>
<td>Cả nhóm; Hoàng Anh chuẩn bị tài khoản</td>
<td>Chuẩn bị tài khoản theo vai trò, dữ liệu mẫu và kịch bản kiểm thử chung.<br>Có dữ liệu thống nhất để kiểm tra backend, web và mobile; không dùng dữ liệu cá nhân thật.</td>
<td>Backend / Frontend / Mobile — tài khoản, dữ liệu kiểm thử</td>
<td>2026-10-06</td>
<td>2026-10-09</td>
<td>10</td>
<td><strong>Đang thực hiện</strong><br>Đã có dữ liệu mẫu và fixture kiểm thử backend/web; chưa có bộ kiểm thử chung cho cả mobile.</td>
</tr>
<tr>
<td>31</td>
<td>Cả nhóm; Vinh điều phối</td>
<td>Kiểm thử xuyên suốt tiếp nhận, khám, xét nghiệm, thanh toán và cấp thuốc.<br>Chạy luồng thực tế qua Gateway trên web và các màn hình mobile đã thống nhất; đối chiếu trạng thái, tiền và dữ liệu.</td>
<td>Backend / Frontend / Mobile — luồng khám đến cấp thuốc</td>
<td>2026-10-08</td>
<td>2026-10-12</td>
<td>11</td>
<td><strong>Chờ tích hợp</strong><br>Luồng thanh toán thực qua Gateway còn phụ thuộc; mobile chưa có chức năng.</td>
</tr>
<tr>
<td>32</td>
<td>Cả nhóm theo nghiệp vụ</td>
<td>Kiểm tra báo cáo, thông báo, quyền truy cập và tính nhất quán giữa các ứng dụng.<br>Số liệu và trạng thái khớp nhau; không lộ dữ liệu; sự kiện gửi lại không tạo nghiệp vụ trùng.</td>
<td>Backend / Frontend / Mobile — báo cáo, thông báo, phân quyền</td>
<td>2026-10-08</td>
<td>2026-10-12</td>
<td>12</td>
<td><strong>Chờ tích hợp</strong><br>Chưa nghiệm thu dữ liệu, phân quyền và sự kiện xuyên suốt cả ba phần.</td>
</tr>
<tr>
<td>33</td>
<td>Từng thành viên theo nghiệp vụ</td>
<td>Sửa lỗi tích hợp và kiểm thử lại các luồng bị ảnh hưởng.<br>Đóng lỗi chặn đăng nhập, luồng khám, thanh toán, cấp thuốc và lỗi bảo mật; ghi rõ hạn chế còn lại.</td>
<td>Backend / Frontend / Mobile — sửa lỗi, kiểm thử lại</td>
<td>2026-10-09</td>
<td>2026-10-13</td>
<td>11</td>
<td><strong>Đang thực hiện</strong><br>Đã có các sửa lỗi và kiểm thử hồi quy cục bộ; còn lỗi và phụ thuộc tích hợp cần kiểm tra.</td>
</tr>
<tr>
<td>34</td>
<td>Cả nhóm; Vinh tổng hợp</td>
<td>Hoàn thiện hướng dẫn cài đặt, sử dụng, báo cáo và nội dung trình bày.<br>Hướng dẫn phản ánh đúng chức năng chạy được; có cấu hình, tài khoản demo và kịch bản trình bày.</td>
<td>Tài liệu — cài đặt, sử dụng, báo cáo, trình bày</td>
<td>2026-10-09</td>
<td>2026-10-12</td>
<td>12</td>
<td><strong>Đang thực hiện</strong><br>Đã có tài liệu thiết kế và hướng dẫn; hồ sơ bàn giao cần cập nhật theo kết quả cuối.</td>
</tr>
<tr>
<td>35</td>
<td>Cả nhóm</td>
<td>Đóng gói, nghiệm thu cuối và bàn giao dự án.<br>Chạy lại kịch bản chính, kiểm tra bản cài và môi trường; bàn giao mã nguồn, cấu hình, tài liệu và danh sách hạn chế.</td>
<td>Backend / Frontend / Mobile — nghiệm thu, bàn giao</td>
<td>2026-10-10</td>
<td>2026-10-14</td>
<td>11</td>
<td><strong>Chưa nghiệm thu</strong><br>Chưa có kết quả nghiệm thu toàn hệ thống hoặc bộ bàn giao hoàn chỉnh.</td>
</tr>
</tbody>
</table>

## English

**Prepared on:** 2026-10-16. **Planned period:** 2026-10-01–2026-10-24. **Planned project completion:** 2026-10-24.

Actual Start Date and Actual Days are populated with scheduling estimates, not confirmed execution records. Durations use calendar days, including the start and end dates; tasks may run in parallel. Planned starts fall on October 1–10, the scheduled actual starts follow 3–4 days later, and durations range from 5 to 12 days. Update these columns and status with actual records during execution. Mobile uses agreed APIs and access permissions.

**Status reviewed on 2026-10-16:** checked backend, frontend and mobile source and recorded repository test evidence. In progress: partially implemented; Awaiting integration: code exists but dependencies or integrated acceptance remain open; Not started: the corresponding implementation is absent; Not accepted: final acceptance and handover are outstanding. Each status covers the entire row. This update did not rerun application tests.

<table>
<thead>
<tr>
<th rowspan="2">No.</th>
<th rowspan="2">Member</th>
<th rowspan="2">Contents</th>
<th rowspan="2">Page Name</th>
<th>Activity Plan</th>
<th colspan="3">Date of Preparation of Activity Plan</th>
</tr>
<tr>
<th>Planned Start Date</th>
<th>Actual Start Date</th>
<th>Actual Days</th>
<th>Status</th>
</tr>
</thead>
<tbody>
<tr>
<td>1</td>
<td>All members; Vinh consolidates</td>
<td>Review outstanding functions, agree API contracts and integration order. Produce a shared list of changes, permissions and test data for web and mobile.</td>
<td>Backend / Frontend / Mobile — scope and API alignment</td>
<td>2026-10-01</td>
<td>2026-10-04</td>
<td>5</td>
<td><strong>In progress</strong><br>Design and APIs exist; web/mobile integration alignment remains.</td>
</tr>
<tr>
<td>2</td>
<td>Hoàng Anh</td>
<td>Complete Core account, staff, department and patient management. Enforce login and record permissions; keep Organization and Patient data independent.</td>
<td>Backend — Core: Organization, Patient</td>
<td>2026-10-01</td>
<td>2026-10-04</td>
<td>10</td>
<td><strong>Awaiting integration</strong><br>Core and APIs exist; complete login and cross-service data flows remain unverified.</td>
</tr>
<tr>
<td>3</td>
<td>Hoàng Anh</td>
<td>Complete Gateway routing and caller identity checks. Expose authorized APIs and reject forbidden requests or expired sessions.</td>
<td>Backend — Gateway: authentication, routing, authorization</td>
<td>2026-10-02</td>
<td>2026-10-06</td>
<td>9</td>
<td><strong>In progress</strong><br>Routing and role checks exist; payment-request discovery routing and prescription cancellation permissions remain open.</td>
</tr>
<tr>
<td>4</td>
<td>Vinh</td>
<td>Complete appointment, check-in, examination start and completion workflows. Enforce valid state transitions and required payment clearance.</td>
<td>Backend — Clinical: appointments and medical records</td>
<td>2026-10-01</td>
<td>2026-10-05</td>
<td>12</td>
<td><strong>Awaiting integration</strong><br>Business logic and tests exist; acceptance with actual examination payments remains open.</td>
</tr>
<tr>
<td>5</td>
<td>Vinh</td>
<td>Complete lab orders, sample reception and result delivery. Link orders to the correct record, synchronize results and prevent duplicates on retry.</td>
<td>Backend — Lab: orders, samples and results</td>
<td>2026-10-02</td>
<td>2026-10-06</td>
<td>12</td>
<td><strong>Awaiting integration</strong><br>Orders, results and recovery tests exist; integrated lab payment acceptance remains open.</td>
</tr>
<tr>
<td>6</td>
<td>Huy</td>
<td>Complete drug, inventory, prescription and dispensing workflows. Check stock and payment; enforce cancellation and dispensing permissions and prevent duplicate dispensing.</td>
<td>Backend — Pharmacy: drugs, stock and prescriptions</td>
<td>2026-10-01</td>
<td>2026-10-04</td>
<td>12</td>
<td><strong>Awaiting integration</strong><br>Drug and dispensing workflows exist; Gateway role checks and the paid prescription journey remain open.</td>
</tr>
<tr>
<td>7</td>
<td>Lộc</td>
<td>Complete charges, invoices and payment recording. Collect the correct examination, lab and prescription charges; ensure accurate amounts and prevent duplicate payments.</td>
<td>Backend — Billing: charges, invoices and payments</td>
<td>2026-10-01</td>
<td>2026-10-05</td>
<td>12</td>
<td><strong>In progress</strong><br>Charges, payments and tests exist; event publication evidence and consumer acceptance remain incomplete.</td>
</tr>
<tr>
<td>8</td>
<td>Huy</td>
<td>Complete reports and verify data sources. Provide supported filters and reconcilable figures; identify unavailable data clearly.</td>
<td>Backend — Report: reporting</td>
<td>2026-10-04</td>
<td>2026-10-07</td>
<td>10</td>
<td><strong>Awaiting integration</strong><br>Report APIs exist; source coverage and data completeness await confirmation.</td>
</tr>
<tr>
<td>9</td>
<td>Lộc</td>
<td>Complete in-app notifications and recipient access checks. Deliver to the correct recipient, protect patient information and prevent duplicate delivery on retry.</td>
<td>Backend — Notification: notifications</td>
<td>2026-10-04</td>
<td>2026-10-08</td>
<td>10</td>
<td><strong>Awaiting integration</strong><br>Notification APIs exist; actual event delivery and recipient permissions await integrated acceptance.</td>
</tr>
<tr>
<td>10</td>
<td>Huy, Lộc; Vinh coordinates; Hoàng Anh supports Gateway</td>
<td>Complete Support packaging and service connections. Start Core, Clinical, Lab, Pharmacy, Billing and Support with correct routing and data connections.</td>
<td>Backend — Support and integration configuration</td>
<td>2026-10-05</td>
<td>2026-10-09</td>
<td>10</td>
<td><strong>Not started</strong><br>No Support host module exists; Report and Notification remain separate services.</td>
</tr>
<tr>
<td>11</td>
<td>Each business owner</td>
<td>Test backend workflows, permissions and failure recovery. Cover successful requests, forbidden access, invalid states, duplicates, connection failures and retries; fix discovered defects.</td>
<td>Backend — business workflow testing</td>
<td>2026-10-07</td>
<td>2026-10-11</td>
<td>11</td>
<td><strong>In progress</strong><br>Business and recovery tests exist; system-wide integration testing remains open.</td>
</tr>
<tr>
<td>12</td>
<td>Vinh coordinates; Hoàng Anh supports accounts</td>
<td>Review login, role-based menus, layout and small-screen behavior. Handle session expiry and ensure usable navigation on phones.</td>
<td>Frontend — login, navigation and shared layout</td>
<td>2026-10-01</td>
<td>2026-10-05</td>
<td>7</td>
<td><strong>In progress</strong><br>Layout, navigation and login exist; real-session and role-based navigation checks remain open.</td>
</tr>
<tr>
<td>13</td>
<td>Hoàng Anh</td>
<td>Complete staff, department and patient screens. Support authorized search, pagination, viewing and updates, with input validation and API error handling.</td>
<td>Frontend — /organization; /patients</td>
<td>2026-10-03</td>
<td>2026-10-06</td>
<td>11</td>
<td><strong>Awaiting integration</strong><br>Management pages and actions exist; verification against Core through Gateway remains open.</td>
</tr>
<tr>
<td>14</td>
<td>Vinh</td>
<td>Complete appointment, check-in and medical-record screens. Run the examination workflow through Gateway and show correct states and payment conditions.</td>
<td>Frontend — /appointments; /records</td>
<td>2026-10-03</td>
<td>2026-10-07</td>
<td>11</td>
<td><strong>Awaiting integration</strong><br>Appointment and record screens exist; payment-dependent examination actions await acceptance.</td>
</tr>
<tr>
<td>15</td>
<td>Vinh</td>
<td>Complete lab-order and result screens. Display orders and update states and results for the correct record and authorized roles.</td>
<td>Frontend — /lab; /records</td>
<td>2026-10-04</td>
<td>2026-10-07</td>
<td>11</td>
<td><strong>Awaiting integration</strong><br>Screens and local tests exist; the paid lab flow through Gateway remains unverified.</td>
</tr>
<tr>
<td>16</td>
<td>Huy</td>
<td>Complete drug, stock and dispensing screens. Search drugs, view stock and process prescriptions according to backend states and server-provided prices.</td>
<td>Frontend — /pharmacy</td>
<td>2026-10-03</td>
<td>2026-10-07</td>
<td>11</td>
<td><strong>Awaiting integration</strong><br>Drug, stock and prescription screens exist; real-role and paid dispensing checks remain open.</td>
</tr>
<tr>
<td>17</td>
<td>Lộc</td>
<td>Complete invoice and payment screens. Select the correct charges, view invoices and record payments; prevent repeated submissions and show clear errors.</td>
<td>Frontend — /billing</td>
<td>2026-10-03</td>
<td>2026-10-06</td>
<td>12</td>
<td><strong>Awaiting integration</strong><br>Invoice and payment screens exist; source-based charge discovery through Gateway remains unavailable.</td>
</tr>
<tr>
<td>18</td>
<td>Huy</td>
<td>Complete report screens. Match filters and figures to the backend and handle loading, empty and error states.</td>
<td>Frontend — /reports</td>
<td>2026-10-05</td>
<td>2026-10-08</td>
<td>10</td>
<td><strong>Awaiting integration</strong><br>Report screens and local tests exist; actual figures and source coverage remain to be reconciled.</td>
</tr>
<tr>
<td>19</td>
<td>Lộc</td>
<td>Complete notification screens. Show lists and details to the correct recipient and expose only backend-supported actions.</td>
<td>Frontend — /notifications</td>
<td>2026-10-05</td>
<td>2026-10-09</td>
<td>10</td>
<td><strong>Awaiting integration</strong><br>List, detail and send screens exist; recipient checks with real sessions remain open.</td>
</tr>
<tr>
<td>20</td>
<td>Each business owner</td>
<td>Test screens by role and business workflow. Use real APIs, verify permissions and error handling, and record defects.</td>
<td>Frontend — role-based screen testing</td>
<td>2026-10-07</td>
<td>2026-10-11</td>
<td>11</td>
<td><strong>In progress</strong><br>Local screen tests exist; complete role coverage and real-API journeys remain unverified.</td>
</tr>
<tr>
<td>21</td>
<td>Vinh coordinates; all members review workflows</td>
<td>Review the existing mobile app, agree screens and roles, and configure connections. Confirm the runtime environment and use Gateway for all requests.</td>
<td>Mobile — app configuration and screen scope</td>
<td>2026-10-01</td>
<td>2026-10-04</td>
<td>6</td>
<td><strong>In progress</strong><br>Folder scaffolding and a loading component exist; app configuration and Gateway connectivity are missing.</td>
</tr>
<tr>
<td>22</td>
<td>Vinh coordinates shared work; Hoàng Anh supports authentication</td>
<td>Complete login, secure session storage and role-based navigation. Handle logout and session expiry and restrict access to authorized screens.</td>
<td>Mobile — login, logout and navigation</td>
<td>2026-10-02</td>
<td>2026-10-06</td>
<td>8</td>
<td><strong>Not started</strong><br>Mobile login, session storage and navigation have not been implemented.</td>
</tr>
<tr>
<td>23</td>
<td>Hoàng Anh</td>
<td>Complete patient search and information screens. Use authorized APIs and roles and protect personal information.</td>
<td>Mobile — patient information</td>
<td>2026-10-04</td>
<td>2026-10-07</td>
<td>10</td>
<td><strong>Not started</strong><br>Mobile patient screens and data handling have not been implemented.</td>
</tr>
<tr>
<td>24</td>
<td>Vinh</td>
<td>Complete appointment and examination information screens. Show authorized schedules, states and records; use only supported update APIs.</td>
<td>Mobile — appointments and examination information</td>
<td>2026-10-04</td>
<td>2026-10-08</td>
<td>10</td>
<td><strong>Not started</strong><br>Mobile appointment and examination screens have not been implemented.</td>
</tr>
<tr>
<td>25</td>
<td>Vinh</td>
<td>Complete lab-result screens. Link results to the correct patient and record and handle refresh, empty and error states.</td>
<td>Mobile — lab results</td>
<td>2026-10-05</td>
<td>2026-10-08</td>
<td>10</td>
<td><strong>Not started</strong><br>Mobile lab-result screens have not been implemented.</td>
</tr>
<tr>
<td>26</td>
<td>Huy</td>
<td>Complete drug and prescription information screens. Display medicines, instructions and prescription states according to backend data and user permissions.</td>
<td>Mobile — drugs and prescriptions</td>
<td>2026-10-04</td>
<td>2026-10-08</td>
<td>10</td>
<td><strong>Not started</strong><br>Mobile drug and prescription screens have not been implemented.</td>
</tr>
<tr>
<td>27</td>
<td>Lộc</td>
<td>Complete invoice and payment-status screens. Show correct charges, amounts and states; expose payment actions only when supported by the backend.</td>
<td>Mobile — invoices and payment status</td>
<td>2026-10-04</td>
<td>2026-10-07</td>
<td>11</td>
<td><strong>Not started</strong><br>Mobile invoice and payment screens have not been implemented.</td>
</tr>
<tr>
<td>28</td>
<td>Lộc</td>
<td>Complete user notification screens. Display notifications for the correct recipient and prevent access to another account’s notifications.</td>
<td>Mobile — notifications</td>
<td>2026-10-05</td>
<td>2026-10-09</td>
<td>10</td>
<td><strong>Not started</strong><br>Mobile notification screens have not been implemented.</td>
</tr>
<tr>
<td>29</td>
<td>Each business owner tests; Vinh packages the app</td>
<td>Test mobile on devices and prepare an Android installation package. Check screen sizes, keyboards, slow or lost connections and session expiry; verify installation and startup.</td>
<td>Mobile — device testing and Android package</td>
<td>2026-10-07</td>
<td>2026-10-11</td>
<td>11</td>
<td><strong>Not started</strong><br>No complete app, device testing or Android package is available for acceptance.</td>
</tr>
<tr>
<td>30</td>
<td>All members; Hoàng Anh prepares accounts</td>
<td>Prepare role-based accounts, sample data and shared test scenarios. Use consistent data across backend, web and mobile without real personal information.</td>
<td>Backend / Frontend / Mobile — accounts and test data</td>
<td>2026-10-06</td>
<td>2026-10-09</td>
<td>10</td>
<td><strong>In progress</strong><br>Backend/web sample data and test fixtures exist; a shared set covering mobile is not ready.</td>
</tr>
<tr>
<td>31</td>
<td>All members; Vinh coordinates</td>
<td>Test the complete check-in, examination, lab, payment and dispensing journey. Run through Gateway on web and agreed mobile screens and reconcile states, amounts and data.</td>
<td>Backend / Frontend / Mobile — examination-to-dispensing workflow</td>
<td>2026-10-08</td>
<td>2026-10-12</td>
<td>11</td>
<td><strong>Awaiting integration</strong><br>Actual payment journeys depend on pending Gateway work; mobile workflows are not implemented.</td>
</tr>
<tr>
<td>32</td>
<td>All business owners</td>
<td>Verify reports, notifications, permissions and consistency across applications. Reconcile figures and states, protect data and prevent duplicate effects when events are retried.</td>
<td>Backend / Frontend / Mobile — reports, notifications and permissions</td>
<td>2026-10-08</td>
<td>2026-10-12</td>
<td>12</td>
<td><strong>Awaiting integration</strong><br>Cross-application data, permissions and events have not passed integrated acceptance.</td>
</tr>
<tr>
<td>33</td>
<td>Each business owner</td>
<td>Fix integration defects and retest affected workflows. Resolve login, examination, payment, dispensing and security blockers and record remaining limitations.</td>
<td>Backend / Frontend / Mobile — defect fixes and regression testing</td>
<td>2026-10-09</td>
<td>2026-10-13</td>
<td>11</td>
<td><strong>In progress</strong><br>Local fixes and regression tests exist; integration issues and dependencies remain to be checked.</td>
</tr>
<tr>
<td>34</td>
<td>All members; Vinh consolidates</td>
<td>Complete installation and user guides, project reports and presentation materials. Document working functions, configuration, demo accounts and presentation scenarios.</td>
<td>Documentation — installation, usage, reports and presentation</td>
<td>2026-10-09</td>
<td>2026-10-12</td>
<td>12</td>
<td><strong>In progress</strong><br>Design documents and guides exist; handover materials need the final delivery results.</td>
</tr>
<tr>
<td>35</td>
<td>All members</td>
<td>Package, perform final acceptance and hand over the project. Rerun main scenarios, verify the installation package and environment, and deliver source, configuration, documentation and remaining limitations.</td>
<td>Backend / Frontend / Mobile — acceptance and handover</td>
<td>2026-10-10</td>
<td>2026-10-14</td>
<td>11</td>
<td><strong>Not accepted</strong><br>System-wide acceptance and the complete handover package are not yet available.</td>
</tr>
</tbody>
</table>
