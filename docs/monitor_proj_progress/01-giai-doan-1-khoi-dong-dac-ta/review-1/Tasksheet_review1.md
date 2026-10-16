# MediFlow — Task Sheet

**Ngày lập:** 16/10/2026. **Thời gian thực hiện dự kiến:** 17/10/2026–24/10/2026. **Ngày kết thúc dự án dự kiến:** 24/10/2026.

Planned Start Date là ngày bắt đầu dự kiến; hạn hoàn thành của từng việc nằm trong Contents. Actual Start Date và Actual Days để “—” cho đến khi thực hiện thực tế. Trạng thái ban đầu là Chưa bắt đầu. Các màn hình mobile được triển khai theo API và quyền truy cập đã thống nhất.

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
<td>Cả nhóm; Vinh tổng hợp</td>
<td>Rà soát chức năng còn thiếu, thống nhất dữ liệu trao đổi và thứ tự tích hợp.<br>Danh sách việc cần sửa, API, quyền và dữ liệu kiểm thử được thống nhất cho web và mobile.<br>Hạn hoàn thành: 2026-10-17.</td>
<td>Backend / Frontend / Mobile — thống nhất chức năng và API</td>
<td>2026-10-17</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>2</td>
<td>Hoàng Anh</td>
<td>Hoàn thiện Core, quản lý tài khoản, nhân sự, khoa phòng và người bệnh.<br>Đăng nhập và quản lý hồ sơ đúng quyền; dữ liệu Organization và Patient được quản lý độc lập.<br>Hạn hoàn thành: 2026-10-18.</td>
<td>Backend — Core: Organization, Patient</td>
<td>2026-10-17</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>3</td>
<td>Hoàng Anh</td>
<td>Hoàn thiện định tuyến Gateway và kiểm tra danh tính người gọi.<br>Các API được phép sử dụng truy cập được qua Gateway; từ chối yêu cầu sai quyền hoặc hết phiên.<br>Hạn hoàn thành: 2026-10-19.</td>
<td>Backend — Gateway: đăng nhập, định tuyến, phân quyền</td>
<td>2026-10-18</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>4</td>
<td>Vinh</td>
<td>Hoàn thiện lịch khám, tiếp nhận, bắt đầu khám và kết thúc khám.<br>Luồng khám chuyển trạng thái đúng; kiểm tra điều kiện thanh toán trước thao tác cần thu phí.<br>Hạn hoàn thành: 2026-10-19.</td>
<td>Backend — Clinical: lịch khám, hồ sơ khám</td>
<td>2026-10-17</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>5</td>
<td>Vinh</td>
<td>Hoàn thiện chỉ định, tiếp nhận mẫu và trả kết quả xét nghiệm.<br>Chỉ định liên kết đúng hồ sơ; kết quả và trạng thái được đồng bộ; không tạo trùng khi gửi lại.<br>Hạn hoàn thành: 2026-10-20.</td>
<td>Backend — Lab: chỉ định, mẫu, kết quả</td>
<td>2026-10-18</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>6</td>
<td>Huy</td>
<td>Hoàn thiện quản lý thuốc, tồn kho, đơn thuốc và cấp thuốc.<br>Kiểm tra tồn kho và thanh toán; cấp hoặc hủy đúng quyền, không cấp trùng.<br>Hạn hoàn thành: 2026-10-20.</td>
<td>Backend — Pharmacy: thuốc, tồn kho, đơn thuốc</td>
<td>2026-10-17</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>7</td>
<td>Lộc</td>
<td>Hoàn thiện khoản phí, hóa đơn và ghi nhận thanh toán.<br>Thu đúng khoản phí của lượt khám, xét nghiệm và đơn thuốc; tính tiền chính xác, không ghi nhận trùng.<br>Hạn hoàn thành: 2026-10-20.</td>
<td>Backend — Billing: khoản phí, hóa đơn, thanh toán</td>
<td>2026-10-17</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>8</td>
<td>Huy</td>
<td>Hoàn thiện báo cáo và kiểm tra nguồn dữ liệu.<br>Báo cáo có bộ lọc phù hợp, số liệu đối chiếu được; thể hiện rõ khi dữ liệu chưa có.<br>Hạn hoàn thành: 2026-10-21.</td>
<td>Backend — Report: báo cáo</td>
<td>2026-10-19</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>9</td>
<td>Lộc</td>
<td>Hoàn thiện thông báo trong hệ thống và quyền xem của người nhận.<br>Thông báo đến đúng người; không lộ thông tin người bệnh khác; xử lý gửi lại không tạo trùng.<br>Hạn hoàn thành: 2026-10-21.</td>
<td>Backend — Notification: thông báo</td>
<td>2026-10-19</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>10</td>
<td>Huy, Lộc; Vinh điều phối; Hoàng Anh phối hợp Gateway</td>
<td>Hoàn thiện đóng gói Support và cấu hình kết nối các dịch vụ.<br>Core, Clinical, Lab, Pharmacy, Billing và Support khởi chạy, định tuyến và kết nối dữ liệu đúng cấu hình.<br>Hạn hoàn thành: 2026-10-21.</td>
<td>Backend — Support và cấu hình tích hợp</td>
<td>2026-10-20</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>11</td>
<td>Từng thành viên theo nghiệp vụ</td>
<td>Kiểm thử backend, phân quyền và phục hồi khi có lỗi.<br>Kiểm tra thành công, sai quyền, sai trạng thái, gửi trùng, mất kết nối và xử lý lại; sửa lỗi phát hiện.<br>Hạn hoàn thành: 2026-10-22.</td>
<td>Backend — kiểm thử các nghiệp vụ</td>
<td>2026-10-21</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>12</td>
<td>Vinh điều phối; Hoàng Anh phối hợp tài khoản</td>
<td>Rà soát đăng nhập, phân quyền menu, bố cục và hiển thị trên điện thoại.<br>Menu đúng vai trò; xử lý hết phiên; bố cục sử dụng được trên màn hình nhỏ.<br>Hạn hoàn thành: 2026-10-18.</td>
<td>Frontend — đăng nhập, menu, bố cục chung</td>
<td>2026-10-17</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>13</td>
<td>Hoàng Anh</td>
<td>Hoàn thiện giao diện nhân sự, khoa phòng và người bệnh.<br>Tìm kiếm, phân trang, xem và cập nhật đúng quyền; kiểm tra dữ liệu nhập và lỗi API.<br>Hạn hoàn thành: 2026-10-20.</td>
<td>Frontend — /organization; /patients</td>
<td>2026-10-18</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>14</td>
<td>Vinh</td>
<td>Hoàn thiện giao diện lịch khám, tiếp nhận và hồ sơ khám.<br>Thực hiện được luồng khám qua Gateway; hiển thị đúng trạng thái và điều kiện thanh toán.<br>Hạn hoàn thành: 2026-10-20.</td>
<td>Frontend — /appointments; /records</td>
<td>2026-10-18</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>15</td>
<td>Vinh</td>
<td>Hoàn thiện giao diện chỉ định và kết quả xét nghiệm.<br>Hiển thị chỉ định, cập nhật trạng thái và kết quả đúng hồ sơ, đúng quyền.<br>Hạn hoàn thành: 2026-10-21.</td>
<td>Frontend — /lab; /records</td>
<td>2026-10-19</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>16</td>
<td>Huy</td>
<td>Hoàn thiện giao diện thuốc, tồn kho và cấp thuốc.<br>Tìm thuốc, xem tồn, xử lý đơn và cấp thuốc theo trạng thái backend; dùng giá do máy chủ cung cấp.<br>Hạn hoàn thành: 2026-10-21.</td>
<td>Frontend — /pharmacy</td>
<td>2026-10-18</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>17</td>
<td>Lộc</td>
<td>Hoàn thiện giao diện hóa đơn và thanh toán.<br>Chọn đúng khoản phí, xem hóa đơn và ghi nhận thanh toán; ngăn thao tác lặp và hiển thị lỗi rõ ràng.<br>Hạn hoàn thành: 2026-10-21.</td>
<td>Frontend — /billing</td>
<td>2026-10-18</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>18</td>
<td>Huy</td>
<td>Hoàn thiện giao diện báo cáo.<br>Bộ lọc và số liệu khớp backend; có trạng thái đang tải, không có dữ liệu và lỗi.<br>Hạn hoàn thành: 2026-10-21.</td>
<td>Frontend — /reports</td>
<td>2026-10-20</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>19</td>
<td>Lộc</td>
<td>Hoàn thiện giao diện thông báo.<br>Danh sách và chi tiết đúng người nhận; thao tác chỉ xuất hiện khi backend hỗ trợ.<br>Hạn hoàn thành: 2026-10-21.</td>
<td>Frontend — /notifications</td>
<td>2026-10-20</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>20</td>
<td>Từng thành viên theo nghiệp vụ</td>
<td>Kiểm thử giao diện theo vai trò và luồng nghiệp vụ.<br>Các trang chính sử dụng API thật, xử lý lỗi và phân quyền đúng; ghi lại lỗi cần sửa.<br>Hạn hoàn thành: 2026-10-22.</td>
<td>Frontend — kiểm thử các trang theo vai trò</td>
<td>2026-10-21</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>21</td>
<td>Vinh điều phối; cả nhóm rà soát nghiệp vụ</td>
<td>Rà soát ứng dụng hiện có, chốt màn hình và chuẩn bị cấu hình kết nối.<br>Danh sách màn hình, vai trò và môi trường chạy được thống nhất; ứng dụng kết nối qua Gateway.<br>Hạn hoàn thành: 2026-10-18.</td>
<td>Mobile — cấu hình ứng dụng, danh sách màn hình</td>
<td>2026-10-17</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>22</td>
<td>Vinh điều phối phần chung; Hoàng Anh phối hợp xác thực</td>
<td>Hoàn thiện đăng nhập, lưu phiên an toàn và điều hướng theo quyền.<br>Đăng nhập, đăng xuất và hết phiên được xử lý; không hiển thị màn hình ngoài quyền được cấp.<br>Hạn hoàn thành: 2026-10-19.</td>
<td>Mobile — đăng nhập, đăng xuất, điều hướng</td>
<td>2026-10-18</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>23</td>
<td>Hoàng Anh</td>
<td>Hoàn thiện tra cứu và hiển thị thông tin người bệnh.<br>Xem, tìm kiếm hồ sơ theo vai trò và API được cấp; bảo vệ dữ liệu cá nhân.<br>Hạn hoàn thành: 2026-10-20.</td>
<td>Mobile — thông tin người bệnh</td>
<td>2026-10-19</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>24</td>
<td>Vinh</td>
<td>Hoàn thiện màn hình lịch khám và thông tin khám.<br>Xem lịch, trạng thái và thông tin khám theo quyền; thao tác cập nhật chỉ dùng API được hỗ trợ.<br>Hạn hoàn thành: 2026-10-21.</td>
<td>Mobile — lịch khám, thông tin khám</td>
<td>2026-10-19</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>25</td>
<td>Vinh</td>
<td>Hoàn thiện màn hình kết quả xét nghiệm.<br>Kết quả gắn đúng người bệnh và hồ sơ; xử lý tải lại, dữ liệu trống và lỗi.<br>Hạn hoàn thành: 2026-10-21.</td>
<td>Mobile — kết quả xét nghiệm</td>
<td>2026-10-20</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>26</td>
<td>Huy</td>
<td>Hoàn thiện màn hình thông tin thuốc và đơn thuốc.<br>Hiển thị thuốc, hướng dẫn và trạng thái đơn theo dữ liệu backend và quyền người dùng.<br>Hạn hoàn thành: 2026-10-21.</td>
<td>Mobile — thuốc, đơn thuốc</td>
<td>2026-10-19</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>27</td>
<td>Lộc</td>
<td>Hoàn thiện màn hình hóa đơn và trạng thái thanh toán.<br>Xem đúng khoản phí, số tiền và trạng thái; chỉ cung cấp thao tác thanh toán đã có backend hỗ trợ.<br>Hạn hoàn thành: 2026-10-21.</td>
<td>Mobile — hóa đơn, trạng thái thanh toán</td>
<td>2026-10-19</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>28</td>
<td>Lộc</td>
<td>Hoàn thiện màn hình thông báo của người dùng.<br>Hiển thị đúng người nhận; không truy cập thông báo của tài khoản khác.<br>Hạn hoàn thành: 2026-10-21.</td>
<td>Mobile — thông báo</td>
<td>2026-10-20</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>29</td>
<td>Từng thành viên kiểm tra nghiệp vụ; Vinh tổng hợp bản cài</td>
<td>Kiểm thử mobile trên thiết bị và chuẩn bị bản cài Android.<br>Kiểm tra kích thước màn hình, bàn phím, mạng chậm, mất mạng và hết phiên; cài và chạy được bản thử nghiệm.<br>Hạn hoàn thành: 2026-10-22.</td>
<td>Mobile — kiểm thử thiết bị, bản cài Android</td>
<td>2026-10-21</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>30</td>
<td>Cả nhóm; Hoàng Anh chuẩn bị tài khoản</td>
<td>Chuẩn bị tài khoản theo vai trò, dữ liệu mẫu và kịch bản kiểm thử chung.<br>Có dữ liệu thống nhất để kiểm tra backend, web và mobile; không dùng dữ liệu cá nhân thật.<br>Hạn hoàn thành: 2026-10-21.</td>
<td>Backend / Frontend / Mobile — tài khoản, dữ liệu kiểm thử</td>
<td>2026-10-20</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>31</td>
<td>Cả nhóm; Vinh điều phối</td>
<td>Kiểm thử xuyên suốt tiếp nhận, khám, xét nghiệm, thanh toán và cấp thuốc.<br>Chạy luồng thực tế qua Gateway trên web và các màn hình mobile đã thống nhất; đối chiếu trạng thái, tiền và dữ liệu.<br>Hạn hoàn thành: 2026-10-22.</td>
<td>Backend / Frontend / Mobile — luồng khám đến cấp thuốc</td>
<td>2026-10-22</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>32</td>
<td>Cả nhóm theo nghiệp vụ</td>
<td>Kiểm tra báo cáo, thông báo, quyền truy cập và tính nhất quán giữa các ứng dụng.<br>Số liệu và trạng thái khớp nhau; không lộ dữ liệu; sự kiện gửi lại không tạo nghiệp vụ trùng.<br>Hạn hoàn thành: 2026-10-23.</td>
<td>Backend / Frontend / Mobile — báo cáo, thông báo, phân quyền</td>
<td>2026-10-22</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>33</td>
<td>Từng thành viên theo nghiệp vụ</td>
<td>Sửa lỗi tích hợp và kiểm thử lại các luồng bị ảnh hưởng.<br>Đóng lỗi chặn đăng nhập, luồng khám, thanh toán, cấp thuốc và lỗi bảo mật; ghi rõ hạn chế còn lại.<br>Hạn hoàn thành: 2026-10-23.</td>
<td>Backend / Frontend / Mobile — sửa lỗi, kiểm thử lại</td>
<td>2026-10-23</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>34</td>
<td>Cả nhóm; Vinh tổng hợp</td>
<td>Hoàn thiện hướng dẫn cài đặt, sử dụng, báo cáo và nội dung trình bày.<br>Hướng dẫn phản ánh đúng chức năng chạy được; có cấu hình, tài khoản demo và kịch bản trình bày.<br>Hạn hoàn thành: 2026-10-24.</td>
<td>Tài liệu — cài đặt, sử dụng, báo cáo, trình bày</td>
<td>2026-10-23</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
<tr>
<td>35</td>
<td>Cả nhóm</td>
<td>Đóng gói, nghiệm thu cuối và bàn giao dự án.<br>Chạy lại kịch bản chính, kiểm tra bản cài và môi trường; bàn giao mã nguồn, cấu hình, tài liệu và danh sách hạn chế.<br>Hạn hoàn thành: 2026-10-24.</td>
<td>Backend / Frontend / Mobile — nghiệm thu, bàn giao</td>
<td>2026-10-24</td>
<td>—</td>
<td>—</td>
<td>Chưa bắt đầu</td>
</tr>
</tbody>
</table>
