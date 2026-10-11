# MediFlow Review 1 — Báo cáo tiêu chuẩn (Tiếng Việt)

| Hạng mục tài liệu | Giá trị |
|---|---|
| Dự án | Hệ thống quản lý bệnh viện MediFlow |
| Đợt đánh giá | Review 1 — Xác định vấn đề và Đặc tả yêu cầu khách hàng |
| Phiên bản | Tiêu chuẩn |
| Ngôn ngữ | Tiếng Việt |
| Đường cơ sở nguồn | Mã nguồn và cấu hình hiện tại của kho mã |
| Mục đích sử dụng | Đánh giá học thuật, chuyển đổi sang Word và thẩm định dự án |

## Phần A — Xác định vấn đề

### A1. Giới thiệu đề tài và bối cảnh

MediFlow là nền tảng quản lý bệnh viện được thiết kế để kết nối các hoạt động hành chính, lâm sàng, chẩn đoán, dược, tài chính và vận hành chính của một cơ sở y tế. Dự án giải quyết một vấn đề phổ biến tại bệnh viện và phòng khám: thông tin quan trọng thường bị phân tán giữa biểu mẫu giấy, bảng tính độc lập, ứng dụng riêng của từng khoa và các kênh trao đổi không chính thức. Sự phân mảnh này khiến hành trình điều trị của người bệnh khó theo dõi, đồng thời gây nhập liệu lặp, ra quyết định chậm, hồ sơ thiếu nhất quán và trách nhiệm giải trình yếu.

Mã nguồn hiện tại triển khai một hệ thống hướng web dựa trên các dịch vụ backend có thể triển khai độc lập. Nhân viên sử dụng ứng dụng web Next.js thông qua một API gateway trung tâm. Mỗi dịch vụ nghiệp vụ sở hữu dữ liệu của mình và giao tiếp với dịch vụ khác bằng REST có kiểm soát hoặc sự kiện RabbitMQ. PostgreSQL cung cấp vùng lưu trữ tách biệt cho từng dịch vụ. Eureka hỗ trợ khám phá dịch vụ, còn luồng sự kiện dựa trên outbox giúp tăng độ tin cậy khi phân phối.

MediFlow bao phủ mười ngữ cảnh nghiệp vụ đã được triển khai:

1. Organization — Tổ chức
2. Patient — Người bệnh
3. Clinical — Lâm sàng
4. Laboratory — Xét nghiệm
5. Pharmacy — Dược
6. Billing — Viện phí
7. Notification — Thông báo
8. Report — Báo cáo
9. Inpatient — Nội trú
10. Surgery — Phẫu thuật

Giải pháp phục vụ chín vai trò hệ thống: `ADMIN`, `DOCTOR`, `NURSE`, `PHARMACIST`, `CASHIER`, `LAB_TECH`, `MANAGER`, `PATIENT` và `SYSTEM`. Các vai trò này đại diện cho người dùng vận hành cũng như các tiến trình tự động nền.

### A2. Xác định vấn đề

Dự án tập trung xử lý các vấn đề vận hành sau.

| Vấn đề | Ảnh hưởng vận hành hiện tại | Phản hồi cần có từ hệ thống |
|---|---|---|
| Thông tin người bệnh phân mảnh | Nhân viên không thể có góc nhìn đầy đủ và kịp thời về người bệnh | Duy trì một định danh người bệnh nhất quán và liên kết hồ sơ được phép bằng UUID |
| Điều phối lịch khám thủ công | Có thể xảy ra trùng lịch, bỏ lỡ lịch và tiếp nhận chậm | Cung cấp đăng ký lịch khám, theo dõi trạng thái và bàn giao sang lượt khám |
| Hồ sơ lâm sàng rời rạc | Chẩn đoán, quan sát và chỉ định có thể không đến khoa tiếp theo một cách tin cậy | Ghi nhận lượt khám và phát hành yêu cầu xuống các bộ phận liên quan có kiểm soát |
| Khó theo dõi thời gian trả kết quả xét nghiệm | Bác sĩ có thể không biết xét nghiệm đang ở trạng thái yêu cầu, lấy mẫu, xử lý hay hoàn tất | Theo dõi vòng đời chỉ định xét nghiệm và công bố kết quả đã xác minh |
| Đơn thuốc tách rời tồn kho | Thuốc có thể được kê mà không rõ trạng thái cấp phát hoặc tồn kho | Xác nhận đơn thuốc, kiểm tra tồn kho, cấp thuốc và ghi nhận biến động kho |
| Theo dõi tài chính thiếu nhất quán | Phí, hóa đơn, thanh toán, tạm ứng, biên lai và hoàn tiền có thể được đối soát thủ công | Tập trung hồ sơ tài chính trong ngữ cảnh Billing và cung cấp trạng thái có thể kiểm toán |
| Điều phối nội trú yếu | Nhập viện, xác nhận tài chính, phân giường, lưu trú và xuất viện có thể được theo dõi riêng rẽ | Cung cấp một quy trình nội trú có trạng thái với các bước chuyển rõ ràng |
| Điều phối phẫu thuật yếu | Sẵn sàng, xác nhận, xếp lịch, thực hiện và kết quả có thể được trao đổi không chính thức | Cung cấp vòng đời phẫu thuật có thể truy vết với các điểm kiểm soát thẩm quyền |
| Trao đổi thông tin chậm | Người bệnh và nhân viên có thể không nhận được thay đổi trạng thái quan trọng kịp thời | Sinh thông báo từ sự kiện nghiệp vụ và lưu trạng thái gửi |
| Khả năng quan sát quản trị hạn chế | Nhà quản lý có thể phụ thuộc vào báo cáo tổng hợp thủ công | Xây dựng mô hình đọc báo cáo từ các sự kiện vận hành đáng tin cậy |
| Kiểm soát truy cập chưa đủ | Dữ liệu y tế và tài chính nhạy cảm có thể bị lộ cho người không có thẩm quyền | Thực thi phân quyền theo vai trò tại mọi endpoint backend |
| Khả năng kiểm toán thấp | Khó xác định ai đã thay đổi hồ sơ và vào thời điểm nào | Lưu định danh, thời gian, trạng thái và lịch sử sự kiện phù hợp cho kiểm toán |

### A3. Mục tiêu dự án

Mục tiêu chính là cung cấp một quy trình số hóa thống nhất, an toàn và có thể truy vết cho hành trình của người bệnh qua các khoa trong bệnh viện.

Các mục tiêu cụ thể gồm:

- Thiết lập một luồng vận hành xuyên suốt từ đăng ký đến khám chữa bệnh, chẩn đoán, dùng thuốc, viện phí, nhập viện, phẫu thuật, xuất viện, thông báo và báo cáo.
- Trao quyền sở hữu dữ liệu và quy tắc nghiệp vụ rõ ràng cho từng bounded context.
- Giảm nhập liệu trùng bằng cách trao đổi định danh và thông tin được phép qua các hợp đồng tường minh.
- Thực thi phân quyền theo vai trò tại các endpoint backend được truy cập qua gateway.
- Ghi nhận thay đổi trạng thái và phát hành domain event cho các quy trình phụ thuộc.
- Hỗ trợ trách nhiệm tài chính thông qua phí, hóa đơn, tạm ứng, thanh toán, biên lai và hoàn tiền.
- Cải thiện phối hợp chuyên môn qua các quy trình lượt khám, xét nghiệm, đơn thuốc, nội trú và phẫu thuật.
- Hỗ trợ xử lý nền tin cậy bằng outbox, consumer idempotent, retry và dead-letter queue.
- Cung cấp cho nhà quản lý thông tin vận hành tổng hợp mà không cho phép logic báo cáo thay đổi dữ liệu nguồn.
- Duy trì kiến trúc mô-đun để từng bounded context có thể được phát triển, kiểm thử và triển khai riêng.

### A4. Phạm vi dự án

#### A4.1. Phạm vi bao gồm

| Khu vực phạm vi | Chức năng bao gồm |
|---|---|
| Organization | Người dùng, nhân viên, khoa, vai trò, cơ sở, phòng, giường và dữ liệu tham chiếu tổ chức |
| Patient | Hồ sơ người bệnh, định danh, thông tin liên hệ, lịch khám và hồ sơ phía người bệnh |
| Clinical | Lượt khám, quan sát, chẩn đoán, ghi chú lâm sàng, yêu cầu xét nghiệm và đơn thuốc |
| Laboratory | Danh mục xét nghiệm, phiếu xét nghiệm, mẫu bệnh phẩm, trạng thái xử lý, kết quả và công bố kết quả |
| Pharmacy | Danh mục thuốc, tồn kho, biến động kho, xác minh đơn thuốc và cấp phát |
| Billing | Phí dịch vụ, hóa đơn, tạm ứng, thanh toán, biên lai, hoàn tiền và xác nhận tài chính |
| Notification | Yêu cầu thông báo từ sự kiện, mẫu thông báo, lần gửi và trạng thái gửi |
| Report | Mô hình đọc được nuôi bằng sự kiện và báo cáo vận hành dành cho quản lý |
| Inpatient | Yêu cầu nhập viện, xác nhận, phân giường, đợt nằm viện, chuyển giường, theo dõi chăm sóc và xuất viện |
| Surgery | Yêu cầu phẫu thuật, kiểm tra sẵn sàng, xác nhận, xếp lịch, thực hiện và ghi nhận kết quả |
| Nền tảng dùng chung | API gateway, khám phá dịch vụ, cơ sở dữ liệu riêng theo dịch vụ, messaging, quy ước bảo mật và hợp đồng dùng chung |
| Ứng dụng web | Các trang theo vai trò gọi nền tảng qua đường dẫn cùng nguồn `/api/*` |

#### A4.2. Ngoài phạm vi hoặc chưa được chứng minh hoàn tất

- Tích hợp production với định danh quốc gia, bảo hiểm y tế, ngân hàng, cổng thanh toán hoặc hệ thống chính phủ.
- Khả năng tương tác đã được chứng nhận với phòng xét nghiệm ngoài, thiết bị y tế, PACS hoặc hồ sơ sức khỏe điện tử bên thứ ba.
- Hợp đồng cung cấp dịch vụ production với nhà cung cấp SMS, email hoặc push notification.
- Một ứng dụng Flutter hoàn chỉnh có thể chạy; kho mã hiện chỉ có gói loader dùng chung.
- Hỗ trợ quyết định lâm sàng, chẩn đoán tự động, gợi ý kê đơn hoặc quyết định điều trị bằng AI.
- SLO production, chính sách lưu trữ, mục tiêu khôi phục thảm họa hoặc quy trình quản trị lâm sàng đã được chốt.
- Chứng nhận pháp quy, chứng nhận khả năng tiếp cận, chứng nhận kiểm thử xâm nhập hoặc chứng nhận thiết bị y tế.

### A5. Đối tượng sử dụng và chức năng theo vai trò

| Vai trò | Trách nhiệm chính | Chức năng hệ thống tiêu biểu |
|---|---|---|
| `ADMIN` | Cấu hình và quản trị nền tảng | Quản lý người dùng, vai trò, khoa, cơ sở, dữ liệu tham chiếu và cấu hình hệ thống; xem trạng thái quản trị |
| `DOCTOR` | Chẩn đoán và chỉ đạo điều trị | Xem thông tin người bệnh; mở/cập nhật lượt khám; nhập quan sát và chẩn đoán; chỉ định xét nghiệm; kê đơn; yêu cầu nhập viện hoặc phẫu thuật; xem kết quả |
| `NURSE` | Điều phối và ghi nhận chăm sóc | Hỗ trợ tiếp nhận; ghi dấu hiệu/quan sát; hỗ trợ lượt khám; quản lý chăm sóc nội trú, thao tác giường, chuyển giường và chuẩn bị xuất viện |
| `PHARMACIST` | Xác minh và cấp thuốc | Xem đơn đã được duyệt; kiểm tra thuốc và số lượng; quản lý tồn kho; ghi nhận cấp phát và biến động kho |
| `CASHIER` | Xử lý giao dịch tài chính người bệnh | Xem phí và hóa đơn; nhận tạm ứng/thanh toán; phát hành biên lai; xử lý hoàn tiền được phép; xác nhận trạng thái tài chính |
| `LAB_TECH` | Thực hiện xét nghiệm chẩn đoán | Tiếp nhận phiếu xét nghiệm; quản lý mẫu; ghi trạng thái xử lý và kết quả; xác minh và công bố kết quả |
| `MANAGER` | Theo dõi vận hành bệnh viện | Xem báo cáo vận hành và tài chính, hoạt động dịch vụ, tải công việc, xu hướng và chỉ số quản trị |
| `PATIENT` | Truy cập dịch vụ và thông tin cá nhân | Đăng ký/cập nhật hồ sơ; yêu cầu và xem lịch khám; xem kết quả, đơn thuốc, hóa đơn, biên lai, thông báo và trạng thái chăm sóc được phép |
| `SYSTEM` | Thực hiện tự động hóa đáng tin cậy | Phát và nhận sự kiện; thử lại khi gửi thất bại; cập nhật mô hình đọc; khởi tạo thông báo; duy trì idempotency và khả năng truy vết |

### A6. Giải pháp đề xuất

MediFlow đề xuất một nền tảng bệnh viện mô-đun, trong đó mỗi năng lực nghiệp vụ được triển khai dưới dạng một dịch vụ Spring Boot độc lập. Các dịch vụ được truy cập qua một gateway và đăng ký qua Eureka. Mỗi dịch vụ sở hữu một cơ sở dữ liệu PostgreSQL riêng, ngăn việc truy cập trực tiếp cơ sở dữ liệu chéo dịch vụ. Khi một dịch vụ cần thông tin có thẩm quyền từ dịch vụ khác để hoàn tất yêu cầu, nó dùng hợp đồng REST đã định nghĩa. Khi một thay đổi trạng thái đã hoàn tất cần thông báo cho nhiều thành phần hạ nguồn, dịch vụ phát sự kiện qua RabbitMQ.

| Thành phần giải pháp | Mục đích | Lợi ích kỳ vọng |
|---|---|---|
| Ứng dụng web Next.js | Cung cấp quy trình theo vai trò trong giao diện thống nhất | Nhân viên truy cập nhanh hơn và giảm màn hình rời rạc |
| API gateway | Cung cấp một điểm vào có kiểm soát | Định tuyến và ngữ cảnh xác thực nhất quán, giảm phụ thuộc phía client |
| Các dịch vụ độc lập | Tách quyền sở hữu nghiệp vụ và triển khai | Ranh giới rõ, thay đổi an toàn và kiểm thử tập trung |
| Cơ sở dữ liệu PostgreSQL tách biệt | Giữ mỗi dịch vụ là nguồn có thẩm quyền cho hồ sơ của mình | Giảm coupling ngoài ý muốn và làm rõ trách nhiệm |
| Hợp đồng REST | Truy xuất thông tin cần ngay để xử lý command | Xác minh kịp thời với dịch vụ sở hữu |
| Sự kiện RabbitMQ | Lan truyền sự kiện nghiệp vụ đã hoàn tất | Tách rời thông báo, báo cáo và phản ứng liên khoa |
| Transactional outbox | Phối hợp thay đổi cơ sở dữ liệu với phát hành sự kiện | Giảm nguy cơ mất sự kiện sau khi commit trạng thái |
| Consumer idempotent | Ngăn phân phối lặp tạo ra tác động nghiệp vụ lặp | Retry và khôi phục an toàn hơn |
| Phân quyền theo vai trò | Hạn chế từng thao tác được bảo vệ | Bảo mật tốt hơn và phân tách nhiệm vụ |
| Kiểm thử tự động | Xác minh quy tắc, hợp đồng, persistence và tích hợp | Bằng chứng lặp lại được rằng hành vi vẫn ổn định |

### A7. Các giới hạn hiện tại và phần chưa thực hiện

| Giới hạn | Trạng thái hiện tại | Hệ quả đối với dự án |
|---|---|---|
| Ứng dụng di động | Chỉ có gói Flutter loader tái sử dụng; chưa có ứng dụng mobile gốc hoàn chỉnh | Chưa thể nghiệm thu mobile như một kênh cung cấp đầy đủ |
| Bảo hiểm ngoài hệ thống | Chưa chứng minh hợp đồng production với bên bảo hiểm | Kiểm tra quyền lợi, gửi yêu cầu và hoàn trả nằm ngoài phạm vi đã trình diễn |
| Hệ thống thanh toán ngoài | Có quy trình tài chính nội bộ nhưng chưa chứng minh tích hợp ngân hàng/cổng thanh toán | Thanh toán điện tử production cần thêm tích hợp và công việc tuân thủ |
| Định danh quốc gia | Chưa có tích hợp định danh quốc gia đã chứng nhận | Định danh người bệnh vẫn phụ thuộc dữ liệu đăng ký ở cấp dự án |
| Tích hợp xét nghiệm/thiết bị ngoài | Có vòng đời xét nghiệm nội bộ nhưng chưa chứng minh giao diện thiết bị và phòng xét nghiệm ngoài | Nhập liệu tự động từ máy và trao đổi liên nhà cung cấp cần phát triển thêm |
| Nhà cung cấp gửi thông báo | Có quy trình thông báo nhưng chưa có thỏa thuận SMS/email/push production | Hiệu năng gửi phụ thuộc tích hợp nhà cung cấp sau này |
| Bằng chứng năng lực production | Cấu hình Docker hỗ trợ phát triển và demo, không phải bảo đảm tải đã đo | Phải kiểm thử tải và định cỡ trước khi triển khai production |
| Mục tiêu khôi phục | RTO, RPO, thời hạn lưu bản sao và khôi phục đa địa điểm chưa được chốt | Mục tiêu liên tục vận hành cần stakeholder phê duyệt |
| Khả năng tiếp cận | Chưa nghiệm thu mục tiêu tuân thủ accessibility chính thức | Có thể cần đánh giá và khắc phục bổ sung |
| Quản trị lâm sàng | Có quy trình phần mềm nhưng quản trị y khoa của tổ chức nằm ngoài kho mã | Bệnh viện triển khai phải xác định chính sách, phê duyệt và trách nhiệm chuyên môn |
| Chứng nhận pháp quy | Dự án không tuyên bố đã có chứng nhận pháp quy | Vẫn bắt buộc đánh giá pháp lý và quy định trước khi dùng lâm sàng thực tế |

## Phần B — Đặc tả yêu cầu khách hàng (CRS)

### B1. Các bên liên quan và yêu cầu khách hàng

| Bên liên quan | Yêu cầu cốt lõi | Chỉ báo thành công |
|---|---|---|
| Ban quản trị bệnh viện | Quản trị người dùng, khoa, cơ sở và quyền truy cập | Cấu hình được phép luôn sẵn sàng và thay đổi có thể truy vết |
| Nhân viên lâm sàng | Xem thông tin người bệnh liên quan và ghi nhận chăm sóc hiệu quả | Lượt khám, chỉ định, chẩn đoán và trạng thái chăm sóc sẵn có cho vai trò được phép |
| Nhân viên xét nghiệm | Nhận chỉ định đầy đủ và công bố kết quả đã xác minh | Mỗi chỉ định có vòng đời xác định từ yêu cầu đến kết quả công bố |
| Nhân viên dược | Chỉ cấp phát đơn hợp lệ với tồn kho đủ | Cấp phát tạo hồ sơ thuốc và kho có thể kiểm toán |
| Nhân viên tài chính | Đối soát mọi hoạt động tài chính của người bệnh | Phí, hóa đơn, tạm ứng, thanh toán, biên lai và hoàn tiền có trạng thái nhất quán |
| Nhân viên nội trú | Điều phối nhập viện, xác nhận, giường, lưu trú và xuất viện | Không nhập viện khi chưa đạt điều kiện trạng thái và tài nguyên |
| Nhóm phẫu thuật | Điều phối sẵn sàng, xác nhận, lịch, thực hiện và kết quả | Trạng thái và điều kiện tiên quyết của phẫu thuật hiển thị và kiểm toán được |
| Nhà quản lý | Có thông tin vận hành và tài chính kịp thời | Báo cáo dùng sự kiện nguồn đáng tin cậy và không sửa dữ liệu vận hành |
| Người bệnh | Truy cập thông tin dịch vụ và cá nhân được phép | Người bệnh chỉ thấy hồ sơ được phép gắn đúng định danh |
| Vận hành kỹ thuật | Vận hành dịch vụ tin cậy và khôi phục sau lỗi tạm thời | Health, log, retry, dead-letter và tách biệt cơ sở dữ liệu quan sát được |

### B2. Chu trình xử lý dữ liệu theo đối tượng sử dụng

| Quy trình | Đối tượng | Thông tin đầu vào | Xử lý | Thông tin đầu ra | Dịch vụ sở hữu |
|---|---|---|---|---|---|
| Quản trị người dùng và vai trò | `ADMIN` | Định danh người dùng, thông tin nhân viên, vai trò, khoa | Kiểm tra duy nhất và quyền; tạo/cập nhật hồ sơ tổ chức | Hồ sơ người dùng/nhân viên, gán vai trò, thời gian kiểm toán | Organization |
| Thiết lập cơ sở và giường | `ADMIN` | Thuộc tính cơ sở, khoa, phòng, giường | Kiểm tra phân cấp và trạng thái tài nguyên; lưu dữ liệu tham chiếu | Tài nguyên tổ chức và danh mục giường khả dụng | Organization |
| Đăng ký người bệnh | `PATIENT`, nhân viên được phép | Nhân khẩu học, liên hệ, định danh | Kiểm tra trường bắt buộc và trùng lặp; cấp UUID | Hồ sơ người bệnh và xác nhận đăng ký | Patient |
| Xếp lịch khám | `PATIENT`, `NURSE`, nhân viên được phép | UUID người bệnh, khoa/bác sĩ, thời gian, lý do | Kiểm tra yêu cầu và quy tắc lịch; tạo trạng thái lịch khám | Mã lịch khám, lịch và trạng thái | Patient |
| Lượt khám lâm sàng | `DOCTOR`, `NURSE` | UUID người bệnh, lịch/tham chiếu, triệu chứng, quan sát | Mở lượt khám; ghi nhận đánh giá; kiểm tra chuyển trạng thái | Hồ sơ lượt khám, quan sát, chẩn đoán, ghi chú | Clinical |
| Yêu cầu xét nghiệm | `DOCTOR` | UUID lượt khám, UUID người bệnh, danh sách xét nghiệm, ghi chú | Kiểm tra lượt khám và yêu cầu; tạo yêu cầu phiếu xét nghiệm | Tham chiếu phiếu xét nghiệm và danh sách test | Clinical / Laboratory |
| Mẫu và xử lý xét nghiệm | `LAB_TECH` | Phiếu xét nghiệm, thông tin mẫu, số đo | Tiếp nhận; ghi lấy mẫu và xử lý; kiểm tra trường kết quả | Trạng thái mẫu, kết quả, lịch sử xử lý | Laboratory |
| Xác minh và công bố kết quả | `LAB_TECH` | Giá trị test hoàn tất, khoảng tham chiếu, nhận xét | Kiểm tra quyền và trạng thái phiếu; công bố kết quả cuối | Kết quả xét nghiệm đã công bố và sự kiện kết quả | Laboratory |
| Tạo đơn thuốc | `DOCTOR` | Lượt khám, người bệnh, mã thuốc, liều, số lượng, hướng dẫn | Kiểm tra lượt khám và dữ liệu kê đơn; xác nhận chỉ định | Đơn thuốc và các dòng thuốc | Clinical |
| Cấp phát thuốc | `PHARMACIST` | Đơn đã duyệt, tồn kho, số lượng cấp | Xác minh đơn và tồn; ghi cấp phát; giảm kho | Hồ sơ cấp phát, biến động kho, số lượng còn lại | Pharmacy |
| Xử lý phí và hóa đơn | `CASHIER`, `SYSTEM` | Sự kiện dịch vụ tính phí, UUID người bệnh, số tiền, ngữ cảnh hóa đơn | Tạo phí idempotent; gom hoặc cập nhật nghĩa vụ tài chính | Phí và hóa đơn cùng trạng thái tài chính | Billing |
| Tạm ứng và thanh toán | `CASHIER`, `PATIENT` | Tham chiếu hóa đơn/nhập viện, số tiền, phương thức | Kiểm tra số tiền và trạng thái; ghi giao dịch; cập nhật số dư | Hồ sơ thanh toán/tạm ứng, số dư mới, biên lai | Billing |
| Hoàn tiền | `CASHIER` | Giao dịch gốc, lý do, số tiền được duyệt | Kiểm tra số dư có thể hoàn và thẩm quyền; ghi đảo giao dịch | Hồ sơ hoàn tiền và vị thế tài chính đã điều chỉnh | Billing |
| Nhập viện nội trú | `DOCTOR`, `NURSE`, `CASHIER` | Yêu cầu nhập viện, người bệnh, nhu cầu lâm sàng, xác nhận tài chính | Kiểm tra yêu cầu/xác nhận; phân giường phù hợp còn trống | Hồ sơ nhập viện, phân giường, đợt nằm viện hoạt động | Inpatient |
| Chăm sóc và chuyển nội trú | `NURSE`, `DOCTOR` | Đợt nằm viện, quan sát chăm sóc, yêu cầu chuyển | Ghi hoạt động chăm sóc; kiểm tra chuyển phòng/giường; cập nhật sử dụng | Lịch sử đợt điều trị, hồ sơ chăm sóc, trạng thái giường | Inpatient |
| Xuất viện | `DOCTOR`, `NURSE`, `CASHIER` | Đợt nằm viện, quyết định chuyên môn, trạng thái tài chính | Kiểm tra điều kiện xuất viện; đóng đợt; giải phóng giường | Hồ sơ xuất viện, đợt hoàn tất, giường khả dụng | Inpatient |
| Yêu cầu và sẵn sàng phẫu thuật | `DOCTOR`, nhân viên phẫu thuật | Người bệnh, lượt khám, thủ thuật, ưu tiên, bằng chứng sẵn sàng | Kiểm tra yêu cầu và điều kiện; theo dõi checklist sẵn sàng | Yêu cầu phẫu thuật và trạng thái sẵn sàng | Surgery |
| Xếp lịch và thực hiện phẫu thuật | Nhân viên phẫu thuật được phép | Yêu cầu sẵn sàng, xác nhận tài chính, phòng/thời gian/nhóm | Kiểm tra tài nguyên và trạng thái; xếp lịch; ghi thực hiện/kết quả | Lịch, hồ sơ thủ thuật, kết quả, sự kiện vòng đời | Surgery |
| Gửi thông báo | `SYSTEM` | Domain event, tham chiếu người nhận, dữ liệu mẫu, kênh | Chọn mẫu; tạo yêu cầu gửi; thử gửi và theo dõi | Nội dung thông báo, trạng thái gửi, chi tiết lỗi | Notification |
| Báo cáo quản trị | `MANAGER`, `SYSTEM` | Domain event tin cậy và tiêu chí báo cáo | Cập nhật mô hình đọc; tổng hợp chỉ số được phép; lọc theo tiêu chí | Kết quả báo cáo vận hành và tài chính | Report |

### B3. Luồng thông tin liên khoa

| Luồng | Tác nhân kích hoạt | Đường xử lý chính | Kết quả |
|---|---|---|---|
| Đăng ký đến lịch khám | Người bệnh được đăng ký và yêu cầu khám | Patient lưu định danh và lịch; nhân viên được phép xem lịch | Lịch khám được xác nhận và gắn đúng UUID người bệnh |
| Lịch khám đến lượt khám | Lịch đạt trạng thái tiếp nhận/khám | Clinical xác minh tham chiếu người bệnh/lịch rồi mở lượt khám | Lượt khám lâm sàng có thể truy vết |
| Lượt khám đến xét nghiệm | Bác sĩ chỉ định một hoặc nhiều xét nghiệm | Clinical ghi chỉ định; Laboratory tiếp nhận và xử lý | Kết quả công bố sẵn có cho người dùng lâm sàng được phép |
| Xét nghiệm đến viện phí | Xét nghiệm tính phí đạt trạng thái đã quy ước | Sự kiện Laboratory được Billing xử lý idempotent | Một khoản phí cho hoạt động xét nghiệm hợp lệ |
| Đơn thuốc đến cấp phát | Bác sĩ xác nhận đơn thuốc | Pharmacy xác minh đơn và kho rồi ghi cấp phát | Thuốc được cấp cùng lịch sử biến động kho |
| Chăm sóc đến tài chính | Hoạt động chăm sóc tính phí hoàn tất | Dịch vụ sở hữu phát sự kiện; Billing tạo/đối soát phí và hóa đơn | Nghĩa vụ tài chính người bệnh được cập nhật |
| Yêu cầu nhập viện đến đợt nằm viện | Yêu cầu và xác nhận tài chính đạt điều kiện | Inpatient kiểm tra điều kiện và phân giường trống | Đợt nằm viện hoạt động cùng tình trạng chiếm giường |
| Đợt nằm viện đến xuất viện | Quyết định xuất viện và xác nhận cần thiết đạt điều kiện | Inpatient đóng đợt và giải phóng tài nguyên | Hồ sơ xuất viện và giường có thể tái sử dụng |
| Yêu cầu phẫu thuật đến kết quả | Đủ sẵn sàng và xác nhận tài chính | Surgery xếp tài nguyên, ghi thực hiện và công bố kết quả | Vòng đời phẫu thuật đầy đủ có thể kiểm toán |
| Domain event đến thông báo/báo cáo | Thay đổi trạng thái hợp lệ được commit | Outbox phát sự kiện; Notification và Report xử lý idempotent | Hồ sơ gửi và mô hình đọc báo cáo được cập nhật |

### B4. Yêu cầu phần cứng phía client

Các thông số sau là khuyến nghị lập kế hoạch cho phát triển và sử dụng trình duyệt thông thường, không phải bảo đảm năng lực đã được đo.

| Hồ sơ client | Tối thiểu | Khuyến nghị |
|---|---|---|
| Máy bàn/laptop nhân viên | CPU 64-bit hai lõi, RAM 4 GB, màn hình 1366×768, mạng ổn định | CPU hiện đại bốn lõi, RAM 8 GB, màn hình Full HD, mạng dây hoặc Wi-Fi tin cậy |
| Thiết bị trình duyệt người bệnh | Điện thoại, máy tính bảng hoặc máy tính gần đây với ít nhất 2 GB bộ nhớ khả dụng | Thiết bị còn nhận cập nhật bảo mật hệ điều hành/trình duyệt, RAM từ 4 GB |
| Máy phát triển | CPU 64-bit bốn lõi, RAM 16 GB, trống 30 GB | CPU tám lõi, RAM 32 GB, SSD trống từ 60 GB |
| Máy dùng chung tại tiếp nhận/xét nghiệm | Bàn phím, thiết bị trỏ, màn hình 1366×768, mạng ổn định | Màn hình Full HD, thiết bị mã vạch nếu cơ sở yêu cầu, nguồn có UPS |

### B5. Yêu cầu phần mềm phía client

| Hạng mục | Yêu cầu |
|---|---|
| Hệ điều hành | Phiên bản Windows, macOS, Linux, Android hoặc iOS còn được hỗ trợ và phù hợp trình duyệt hiện đại |
| Trình duyệt | Phiên bản hiện tại của Chrome, Edge, Firefox hoặc Safari, bật JavaScript, cookie và HTTP bảo mật |
| Hiển thị | Độ phân giải và mức zoom đủ vận hành biểu mẫu responsive và bảng dữ liệu |
| Mạng | Truy cập ổn định tới endpoint HTTPS đã triển khai; firewall/proxy cho phép lưu lượng cần thiết |
| Tài liệu | Microsoft Word, LibreOffice Writer hoặc phần mềm tương thích để chuyển đổi và chỉnh sửa báo cáo |
| Di động | Chưa cung cấp MediFlow mobile client hoàn chỉnh; truy cập bằng trình duyệt di động là giả định lập kế hoạch được hỗ trợ |

### B6. Yêu cầu phần cứng phía server

Định cỡ production thực tế cần số liệu về tải, tính sẵn sàng và lưu trữ. Các cấu hình sau chỉ hỗ trợ lập kế hoạch.

| Môi trường | CPU | Bộ nhớ | Lưu trữ | Mục đích |
|---|---:|---:|---:|---|
| Demo cho nhà phát triển | 8 lõi logic | RAM 16 GB | SSD trống 40 GB | Container cục bộ, các dịch vụ được chọn, frontend và kiểm thử |
| Tích hợp nhóm | 16 lõi logic | RAM 32 GB | SSD 150 GB | Chạy đồng thời các dịch vụ, kiểm thử chung, dữ liệu mẫu và giám sát |
| Đường cơ sở production ban đầu | Từ 24 lõi logic phân phối trên nhiều host | Từ 64 GB RAM phân phối | Từ 500 GB SSD có khả năng chịu lỗi và sao lưu | Chỉ là điểm bắt đầu; phải xác minh bằng kiểm thử hiệu năng và khôi phục |

Kế hoạch production nên tách tải cơ sở dữ liệu, messaging, ứng dụng và sao lưu khi yêu cầu về tính sẵn sàng và tuân thủ đòi hỏi. Nguồn điện, mạng, lưu trữ và năng lực host dự phòng phải được chọn theo SLO, RTO và RPO đã thống nhất.

### B7. Yêu cầu phần mềm phía server

| Lớp | Yêu cầu bám sát mã nguồn hiện tại |
|---|---|
| Runtime backend | Java 21 |
| Framework backend | Spring Boot 3.3.5 |
| Framework cloud | Spring Cloud 2023.0.3 |
| Build | Maven multi-module |
| Web client | Node.js tương thích Next.js 16.2.11 và React 19.2.4; quản lý gói bằng pnpm |
| Styling và ngôn ngữ | Tailwind CSS 4 và TypeScript 5 |
| Cơ sở dữ liệu | Triển khai tương thích PostgreSQL 16; một cơ sở dữ liệu logic cho mỗi dịch vụ |
| Messaging | Triển khai tương thích RabbitMQ 3 với khả năng quản trị phục vụ vận hành |
| Khám phá dịch vụ | Eureka Server |
| Môi trường container | Docker Engine và Docker Compose tương thích cấu hình kho mã |
| Quản lý mã nguồn | Git và quy trình kho mã từ xa đã được phê duyệt |
| Bảo mật truyền tải | Kết thúc HTTPS và quản lý chứng thư tin cậy trong production |
| Vận hành | Log, metrics, cảnh báo tập trung; sao lưu cơ sở dữ liệu; quản lý secret; đồng bộ thời gian |

### B8. Yêu cầu bảo mật, độ tin cậy và vận hành

| Khu vực yêu cầu | Yêu cầu |
|---|---|
| Ngữ cảnh xác thực | Yêu cầu phải mang định danh đã xác thực đáng tin cậy qua gateway và ranh giới dịch vụ |
| Phân quyền | Mỗi endpoint được bảo vệ phải khai báo vai trò được phép; mặc định từ chối |
| Riêng tư dữ liệu | Người dùng chỉ nhận hồ sơ cần cho vai trò và quan hệ người bệnh của mình |
| Quyền sở hữu dịch vụ | Một dịch vụ không được đọc hoặc sửa cơ sở dữ liệu của dịch vụ khác |
| Kiểm tra dữ liệu | DTO biên phải kiểm tra trường bắt buộc, định dạng, phạm vi và định danh |
| Khả năng kiểm toán | Hồ sơ quan trọng giữ UUID ổn định, trạng thái, thời gian và tham chiếu actor liên quan |
| Độ tin cậy sự kiện | Giao dịch đổi trạng thái có phát sự kiện dùng transactional outbox hoặc mẫu tin cậy tương đương |
| Chống trùng | Consumer phải xử lý thông điệp lặp mà không lặp tác động nghiệp vụ |
| Xử lý lỗi | Lỗi tạm thời dùng retry hữu hạn; thông điệp không thể khôi phục quan sát được qua dead-letter |
| Toàn vẹn tài chính | Tiền dùng biểu diễn thập phân và chuyển trạng thái tài chính phải truy vết được |
| Sao lưu và khôi phục | Cơ sở dữ liệu/cấu hình cần quy trình sao lưu và phục hồi đã kiểm thử trước production |
| Giám sát | Health, lỗi, backlog thông điệp, lỗi gửi, lưu trữ và tính sẵn sàng phải quan sát được |
| Bảo mật cấu hình | Secret production không được lưu trong mã nguồn hoặc cấu hình phát triển dạng rõ |

### B9. Tiêu chí nghiệm thu

Các yêu cầu Review 1 được xem là đã được giải pháp hiện tại thể hiện khi có thể chứng minh:

1. Cả chín vai trò có các quy trình được phép tách biệt rõ ràng.
2. Cả mười ngữ cảnh nghiệp vụ có quyền sở hữu và ranh giới dịch vụ xác định.
3. Người bệnh có thể được đăng ký và liên kết với lịch khám.
4. Bác sĩ được phép có thể tạo lượt khám, yêu cầu xét nghiệm và đơn thuốc.
5. Nhân viên xét nghiệm có thể đưa phiếu qua các trạng thái đến kết quả đã xác minh và công bố.
6. Nhân viên dược có thể xác minh đơn, cấp thuốc và cập nhật hồ sơ kho.
7. Billing có thể ghi phí, hóa đơn, tạm ứng/thanh toán, biên lai và hoàn tiền được phép.
8. Nội trú có thể tiến từ yêu cầu nhập viện qua xác nhận, phân giường, đợt nằm viện đến xuất viện.
9. Phẫu thuật có thể tiến từ yêu cầu qua sẵn sàng, xác nhận, lịch, thực hiện đến kết quả.
10. Thay đổi trạng thái hợp lệ có thể sinh thông báo và cập nhật mô hình đọc báo cáo qua sự kiện.
11. Vai trò không được phép bị từ chối tại endpoint được bảo vệ.
12. Dịch vụ không phụ thuộc vào cơ sở dữ liệu của dịch vụ khác.
13. Phân phối sự kiện lặp không tạo tác động nghiệp vụ trùng.
14. Có thể kiểm tra trạng thái lỗi và retry hoặc xử lý bằng quy trình vận hành.
15. Kiểm thử tự động bám sát mã nguồn chạy đạt trong môi trường xác minh đã thống nhất.

### B10. Thuật ngữ

| Thuật ngữ | Ý nghĩa |
|---|---|
| API gateway | Điểm định tuyến duy nhất mà client dùng để truy cập các dịch vụ backend |
| Bounded context | Khu vực nghiệp vụ có mô hình, quy tắc và quyền sở hữu dữ liệu riêng |
| CRS | Customer Requirements Specification — Đặc tả yêu cầu khách hàng |
| Dead-letter queue | Vùng giữ thông điệp không thể xử lý thành công |
| Encounter | Lượt tương tác lâm sàng với người bệnh được ghi nhận |
| Eureka | Thành phần khám phá dịch vụ của nền tảng backend |
| Financial clearance | Xác nhận điều kiện tài chính cần thiết đã đạt cho một hoạt động chăm sóc |
| Idempotency | Tính chất xử lý lặp không tạo thêm tác động nghiệp vụ |
| Outbox | Bản ghi sự kiện trong cơ sở dữ liệu để phát tin cậy sau giao dịch |
| Read model | Biểu diễn hướng truy vấn được xây từ thông tin vận hành có thẩm quyền |
| RPO | Recovery Point Objective — lượng mất dữ liệu chấp nhận được sau gián đoạn |
| RTO | Recovery Time Objective — thời gian mục tiêu để khôi phục dịch vụ |
| SLO | Service Level Objective — mục tiêu đo lường về tính sẵn sàng hoặc hiệu năng |
| UUID | Universally Unique Identifier — định danh ổn định cho hồ sơ và tham chiếu chéo dịch vụ |

---

Báo cáo này mô tả hành vi được chứng minh hoặc được hỗ trợ trực tiếp bởi đường cơ sở mã nguồn MediFlow hiện tại. Định cỡ phần cứng và kiểm soát production là khuyến nghị cần được xác minh trong môi trường bệnh viện đích.
