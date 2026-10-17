# MediFlow Review 1 — Báo cáo tiêu chuẩn (Tiếng Việt)

| Hạng mục | Nội dung |
|---|---|
| Dự án | MediFlow — Hệ thống thông tin bệnh viện dựa trên microservices, tập trung quản lý khám chữa bệnh ngoại trú |
| Đợt đánh giá | Review 1 — Xác định vấn đề và đặc tả yêu cầu khách hàng |
| Phiên bản | Standard — cập nhật 17/10/2026 |
| Cơ sở yêu cầu | Thiết kế MVP trong `docs/plans/`, đối chiếu cấu trúc repository |
| Trạng thái | Đặc tả yêu cầu; không phải biên bản xác nhận mọi chức năng đã hoàn tất |

## Phần A — Xác định vấn đề

### A1. Giới thiệu và bối cảnh

Tên tiếng Anh thống nhất: **MediFlow: A Microservices-Based Hospital Information System for Outpatient Care Management**. Quy mô hướng tới là phòng khám đa khoa hoặc khoa khám bệnh ngoại trú của bệnh viện. Nội trú, giường bệnh và phẫu thuật dành cho phát triển sau; giữ source/data hiện có. Đây là phạm vi đồ án, chưa xác nhận hệ thống bệnh viện hoàn chỉnh đã sẵn sàng vận hành.

MediFlow hỗ trợ phối hợp các bộ phận trong hành trình khám ngoại trú: tiếp nhận người bệnh, khám, xét nghiệm, kê đơn, thanh toán và cấp thuốc. Hồ sơ dùng chung định danh người bệnh nhưng mỗi bộ phận chịu trách nhiệm về dữ liệu và quyết định nghiệp vụ của mình. Web và Flutter truy cập backend qua API Gateway. Mobile hiện có nền tảng đăng nhập thật và danh sách Lịch hẹn/Lab chỉ đọc; các context mobile khác còn đang phát triển.

Đồ án tập trung vào một luồng ngoại trú có thể kiểm tra xuyên suốt, kể cả khi thông điệp bị gửi lại hoặc một dịch vụ tạm ngừng. Phạm vi Review 1 bám theo mục tiêu sáu service; không đưa nội trú và phẫu thuật vào yêu cầu bắt buộc của MVP.

### A2. Vấn đề cần giải quyết

- Thông tin người bệnh, lịch khám và hồ sơ khám rời rạc gây nhập lại dữ liệu và khó theo dõi tiến trình.
- Bác sĩ, phòng xét nghiệm và nhà thuốc thiếu trạng thái bàn giao thống nhất, dễ bỏ sót chỉ định hoặc kết quả.
- Trạng thái thanh toán không gắn rõ với từng lượt khám, xét nghiệm hoặc đơn thuốc, dẫn đến khó kiểm soát quyền thực hiện dịch vụ.
- Tồn kho, giữ thuốc và cấp thuốc thiếu phối hợp có thể gây cấp trùng hoặc cấp khi không đủ thuốc.
- Thanh toán thành công nhưng cấp thuốc lỗi cần được theo dõi và xử lý bù trừ, thay vì mất dấu giao dịch.
- Báo cáo và thông báo cập nhật chậm; người dùng khó phân biệt chưa có dữ liệu với số liệu bằng không.
- Dữ liệu y tế và tài chính cần giới hạn truy cập theo vai trò, phạm vi công việc và quyền sở hữu hồ sơ.

### A3. Mục tiêu

1. Liên kết định danh, lịch khám, lượt khám, xét nghiệm, đơn thuốc và thanh toán bằng tham chiếu rõ ràng.
2. Bảo đảm hành động khám, thực hiện xét nghiệm và cấp thuốc tuân thủ điều kiện nghiệp vụ và tài chính tương ứng.
3. Tránh thu tiền hoặc xuất kho lặp khi yêu cầu hay sự kiện được xử lý lại.
4. Cập nhật báo cáo và thông báo từ sự kiện, đồng thời giữ khả năng truy vết lỗi và khôi phục xử lý.
5. Có bộ tiêu chí nghiệm thu cho luồng thành công, từ chối truy cập, gửi lặp và lỗi từng phần.

### A4. Phạm vi và cấu trúc hệ thống

#### A4.1. Sáu service đích

| Service | Trách nhiệm trong MVP | Ngữ cảnh/module nguồn |
|---|---|---|
| Core | Tài khoản, nhân viên, khoa, dữ liệu phòng tham chiếu và định danh người bệnh | Organization + Patient |
| Clinical | Lịch khám, check-in, hồ sơ khám, chẩn đoán, chỉ định và hoàn tất ngoại trú | Clinical |
| Lab | Tiếp nhận yêu cầu xét nghiệm, theo dõi xử lý và trả kết quả | Lab |
| Pharmacy | Danh mục thuốc, tồn kho, đơn thuốc, giữ thuốc và cấp thuốc | Pharmacy |
| Billing | Khoản phí, hóa đơn/yêu cầu thanh toán, thanh toán, quyền thực hiện dịch vụ và bù trừ/hoàn tiền | Billing |
| Support | Mô hình đọc báo cáo và thông báo trong ứng dụng | Report + Notification |

Gateway và Eureka là hạ tầng, `common` là thư viện dùng chung. Core và Support cùng đóng gói nhiều module nhưng vẫn giữ ranh giới dữ liệu và nghiệp vụ của từng ngữ cảnh. Thiết kế giữ tám kho dữ liệu ngữ cảnh hoạt động; sáu service không đồng nghĩa sáu database.

#### A4.2. Ngoài phạm vi MVP

- Nhập viện, giường bệnh, điều trị nội trú, quyết toán nội trú và phẫu thuật. Mã nguồn và dữ liệu đã có được bảo toàn cho giai đoạn sau.
- Toàn bộ báo cáo tài chính/vận hành V2, điều chỉnh sau quyết toán và các KPI nâng cao.
- Tích hợp ngân hàng, cổng thanh toán ngoài, bảo hiểm, thiết bị y tế hoặc hệ thống quốc gia.
- Email/SMS và nhắc lịch nâng cao; thông báo trong ứng dụng đủ cho MVP.
- Mở rộng ứng dụng mobile hoặc hợp nhất database để hoàn thành nghiệm thu backend.

### A5. Người dùng và bên liên quan

Tám vai trò người dùng gồm `ADMIN`, `DOCTOR`, `NURSE`, `PHARMACIST`, `CASHIER`, `LAB_TECH`, `MANAGER` và `PATIENT`. `SYSTEM` là danh tính dịch vụ cho xử lý tự động, không phải tài khoản để người dùng đăng nhập. Chức năng theo từng vai trò được liệt kê tại B1.

Bên liên quan gồm người bệnh, nhân viên tiếp nhận, bác sĩ, phòng xét nghiệm, nhà thuốc, bộ phận thu phí, quản lý và nhóm phát triển. Các bộ phận thống nhất yêu cầu và kiểm tra kết quả trong phạm vi được phân quyền.

### A6. Giải pháp đề xuất

Ứng dụng web Next.js/TypeScript/Tailwind truy cập backend qua Gateway. Backend dùng Java 21, Spring Boot, Maven, PostgreSQL, RabbitMQ và Eureka. Mỗi ngữ cảnh sở hữu dữ liệu; không đọc trực tiếp database của ngữ cảnh khác. REST phục vụ thông tin cần ngay; sự kiện phục vụ bàn giao và cập nhật bất đồng bộ. Outbox, chống xử lý trùng, retry có giới hạn và hàng đợi lỗi hỗ trợ khôi phục khi có sự cố.

Luồng chính: **định danh → check-in → thanh toán khám → khám → xét nghiệm nếu cần → kê đơn nếu cần → thanh toán thuốc → cấp thuốc → hoàn tất ngoại trú → cập nhật báo cáo/thông báo**. Nếu có chỉ định xét nghiệm, phí và quyền thực hiện được kiểm tra trước hành động xét nghiệm được bảo vệ.

### A7. Hiện trạng và giới hạn bằng chứng

Repository đã có `backend/core-service`; Organization và Patient đã nằm trong Core, giữ package và ranh giới lưu trữ riêng; không còn hai module standalone trong root Maven reactor. Root reactor hiện có 12 module Maven, gồm common/hạ tầng và cả ứng dụng hoãn. Chưa có thư mục `backend/support-service`; Report và Notification còn ở module riêng. Source vẫn giữ Inpatient và Surgery. Vì vậy topology sáu service là đích triển khai, chưa phải xác nhận sáu deployment đã được nghiệm thu.

Các API, giao diện và luồng có cờ kích hoạt phải được kiểm tra theo hợp đồng thực tế và profile nghiệm thu. Báo cáo này không xác nhận mọi chức năng dưới đây đã được cung cấp, và không dùng kết quả kiểm thử lịch sử làm kết quả mới.

## Phần B — Customer Requirements Specification (CRS)

### B1. Functional Requirements — Yêu cầu chức năng theo vai trò

Các chức năng dưới đây là yêu cầu của MVP. Mỗi thao tác phải tuân thủ quyền endpoint, phạm vi chuyên môn và quyền truy cập hồ sơ. Vai trò không tự cấp quyền thao tác trên mọi dữ liệu. Danh sách này không bổ sung API hay mở rộng quyền mặc định.

#### ADMIN — Quản trị viên

- Quản lý tài khoản, vai trò, trạng thái hoạt động và khóa tài khoản.
- Quản lý khoa, nhân viên và dữ liệu tham chiếu phục vụ khám ngoại trú.
- Liên kết tài khoản với nhân viên hoặc người bệnh qua nguồn định danh hợp lệ.
- Tra cứu trạng thái hệ thống, lịch sử và lỗi xử lý trong phạm vi quản trị được cho phép.
- Thực hiện thao tác vận hành phục hồi được phân quyền, có lưu dấu; không bỏ qua điều kiện tài chính hoặc chuyên môn.

#### DOCTOR — Bác sĩ

- Xem lịch khám và hồ sơ người bệnh trong phạm vi được giao.
- Bắt đầu khám khi có quyền `EXAM` hợp lệ cho đúng lượt khám; ngoại lệ cấp cứu chỉ theo cơ chế đã được hỗ trợ và có audit.
- Ghi nhận thông tin khám, chẩn đoán và hướng xử trí ngoại trú.
- Tạo chỉ định xét nghiệm tường minh và xem kết quả trả về cho hồ sơ tương ứng.
- Lập nội dung kê đơn; đơn thuốc, giá, tồn kho và giữ thuốc được xác thực và quản lý tại Pharmacy.
- Theo dõi kết quả xét nghiệm, trạng thái cấp thuốc và hoàn tất hồ sơ ngoại trú khi đủ điều kiện.

#### NURSE — Điều dưỡng / hỗ trợ tiếp nhận

- Tra cứu, tạo và cập nhật thông tin người bệnh trong phạm vi được cấp quyền.
- Hỗ trợ đăng ký lịch khám và theo dõi lịch theo khoa.
- Thực hiện check-in, cập nhật trạng thái tiếp nhận được phép và bàn giao người bệnh cho bác sĩ.
- Xem thông tin lượt khám và tiến độ xử lý phục vụ phối hợp chăm sóc.
- Check-in không tự cấp quyền bắt đầu khám và không thay thế bước xác nhận tài chính.

#### LAB_TECH — Kỹ thuật viên xét nghiệm

- Xem các yêu cầu xét nghiệm đã được tạo hợp lệ và thông tin liên quan được phép truy cập.
- Bắt đầu xử lý xét nghiệm khi quyền `LAB_TEST` còn hiệu lực và khớp đúng test.
- Cập nhật trạng thái xử lý, thực hiện hủy khi quy trình cho phép.
- Ghi kết quả không rỗng với ngày thực hiện hợp lệ và hoàn tất xét nghiệm.
- Công bố kết quả cho các bộ phận liên quan qua quy trình của Lab; không sửa kết quả đã ở trạng thái kết thúc trong MVP.

#### PHARMACIST — Dược sĩ

- Quản lý và tra cứu danh mục thuốc, tồn kho, số lượng khả dụng và hạn dùng.
- Tiếp nhận nội dung kê đơn của bác sĩ, xác minh người bệnh và ngữ cảnh khám ngoại trú.
- Tạo/xử lý đơn thuốc được phép với giá từ danh mục máy chủ và giữ thuốc theo từng dòng.
- Kiểm tra quyền `PRESCRIPTION` cho đúng đơn, thời hạn giữ thuốc và tồn kho trước khi cấp.
- Xác nhận cấp thuốc; chỉ xuất kho một lần khi tất cả dòng đáp ứng điều kiện.
- Hủy hoặc xử lý hết hạn theo quy trình, giải phóng thuốc đã giữ và chuyển kết quả lỗi cho Billing khi cần điều chỉnh tài chính.
- Biên lai thanh toán không tự động thay cho quyền cấp thuốc trong luồng MVP đích.

#### CASHIER — Thu ngân

- Tra cứu các khoản phí thuộc đúng lượt khám/người bệnh được phép phục vụ.
- Lập hóa đơn hoặc yêu cầu thanh toán từ các khoản phí hợp lệ của cùng tài khoản lượt khám.
- Ghi nhận thanh toán và tra cứu trạng thái giao dịch, chứng từ tương ứng.
- Theo dõi xác nhận điều kiện tài chính cho khám, xét nghiệm và cấp thuốc theo đúng đối tượng; không cấp quyền thủ công bằng một cờ “đã trả” chung cho người bệnh.
- Xử lý bù trừ/hoàn tiền theo quy trình và thẩm quyền của Billing khi hủy hoặc cấp thuốc lỗi.
- Tra cứu lịch sử giao dịch để đối soát; không sửa hoặc xóa lịch sử nhằm làm khớp số dư.

#### MANAGER — Quản lý

- Xem báo cáo lượt khám, xét nghiệm, đơn thuốc và số lượng thuốc đã cấp.
- Xem số tiền đã thu, khoản hoàn và tiền thu ròng theo nguồn dữ liệu được cung cấp.
- Lọc báo cáo theo các tiêu chí có trong hợp đồng báo cáo được hỗ trợ.
- Phân biệt số liệu chưa có nguồn bao phủ với giá trị bằng không, và tiền thu với doanh thu được ghi nhận.
- Theo dõi tình trạng cập nhật báo cáo; không chỉnh dữ liệu nghiệp vụ nguồn từ màn hình báo cáo.

#### PATIENT — Người bệnh

- Đăng nhập và truy cập thông tin gắn với định danh người bệnh của mình.
- Xem và cập nhật thông tin cá nhân được phép.
- Yêu cầu, xem và theo dõi lịch khám của mình theo quy trình được hỗ trợ.
- Xem thông tin khám, kết quả xét nghiệm và đơn thuốc được phép công bố cho mình.
- Xem khoản phí, hóa đơn, trạng thái thanh toán và cấp thuốc của mình theo hợp đồng được cung cấp.
- Xem thông báo trong ứng dụng dành cho mình; không xem hồ sơ hoặc thông báo của người bệnh khác.

#### SYSTEM — Danh tính dịch vụ

- Chuyển tiếp các sự kiện ngoại trú hợp lệ giữa các ngữ cảnh, giữ định danh và khả năng truy vết.
- Tự động tạo khoản phí/quyền thực hiện dịch vụ từ nguồn nghiệp vụ hợp lệ theo profile được chọn.
- Xử lý gửi lặp, retry và khôi phục công việc đang chờ mà không thu tiền hoặc xuất kho hai lần.
- Cập nhật mô hình đọc báo cáo và tạo thông báo trong ứng dụng cho đúng người nhận.
- Đưa thông điệp không xử lý được sau số lần thử giới hạn vào hàng đợi lỗi để kiểm tra.
- Không dùng token dịch vụ thay tài khoản người dùng cho thao tác yêu cầu danh tính con người.

### B2. Client Hardware Requirements — Phần cứng máy khách

Đây là cấu hình tham khảo cho truy cập trình duyệt, không phải số đo tải đã nghiệm thu.

| Thiết bị | Tối thiểu tham khảo | Khuyến nghị |
|---|---|---|
| Máy nhân viên / quầy tiếp nhận / Lab | CPU 64-bit 2 nhân, RAM 4 GB, màn hình 1366×768, mạng ổn định | CPU 4 nhân, RAM 8 GB, Full HD, mạng dây hoặc Wi-Fi ổn định |
| Thiết bị người bệnh | Điện thoại, máy tính bảng hoặc máy tính chạy trình duyệt phù hợp | Thiết bị còn được cập nhật bảo mật, RAM 4 GB trở lên |
| Máy phát triển chạy cả hệ thống demo | CPU 8 luồng, RAM 16 GB, SSD trống 40 GB | RAM 32 GB và SSD trống từ 60 GB |

### B3. Server Hardware Requirements — Phần cứng máy chủ

| Môi trường | CPU | RAM | Lưu trữ |
|---|---|---|---|
| Demo phát triển cục bộ | 8 luồng xử lý | 16 GB | SSD trống 40 GB |
| Tích hợp nhóm | 16 luồng xử lý | 32 GB | SSD 150 GB |

Cấu hình trên dùng để lập kế hoạch cho ứng dụng, PostgreSQL và RabbitMQ. Năng lực thực tế phụ thuộc dịch vụ bật, bộ dữ liệu và tải kiểm thử. Cấu hình production, dự phòng và dung lượng backup phải được xác định bằng đo tải và thử phục hồi; Review 1 không cam kết công suất production.

### B4. Yêu cầu phần mềm, bảo mật và độ tin cậy

- Máy khách dùng trình duyệt có JavaScript và truy cập HTTPS; môi trường phát triển dùng Node.js/pnpm phù hợp với package/lockfile của frontend.
- Backend dùng Java 21, Spring Boot/Maven, PostgreSQL, RabbitMQ, Eureka và Gateway theo cấu hình repository. Phiên bản phụ thuộc lấy từ các manifest hiện hành.
- Xác thực JWT và kiểm tra quyền tại Gateway lẫn service; mặc định từ chối. Người bệnh chỉ truy cập tài nguyên thuộc quyền sở hữu đã xác minh.
- Tham chiếu liên ngữ cảnh bằng UUID; không truy cập SQL chéo ngữ cảnh. Core/Support đồng triển khai vẫn giữ datasource và transaction riêng theo module.
- Tiền dùng số thập phân chính xác. Phí, thanh toán, hoàn tiền và xuất kho cần lịch sử truy vết và chống xử lý trùng.
- Lưu thay đổi quan trọng và outbox trong cùng giao dịch; consumer chống trùng, retry có giới hạn và hỗ trợ hàng đợi lỗi.
- Khi nguồn quyền hoặc dữ liệu cần thiết không khả dụng, không tự giả định đã thanh toán, có quyền hoặc có số liệu bằng không.
- Bảo vệ bí mật cấu hình, hạn chế dữ liệu nhạy cảm trong log, kiểm tra backup/restore. Mục tiêu hiệu năng cần được đo và chốt trong môi trường nghiệm thu.

### B5. Tiêu chí nghiệm thu

1. Đăng nhập, từ chối tài khoản bị khóa, sai vai trò, sai loại token và truy cập chéo người bệnh.
2. Đăng ký người bệnh, lịch khám, check-in; chưa có quyền khám thì không bắt đầu khám, có đúng quyền mới được thực hiện.
3. Tạo chỉ định xét nghiệm; thiếu/sai/hết hạn quyền thì không xử lý; kết quả hợp lệ được bàn giao về Clinical.
4. Tạo đơn với giá máy chủ và giữ thuốc; thanh toán đúng đối tượng; cấp thuốc đúng một lần, không xuất kho âm hoặc xuất một phần đơn.
5. Gửi lại yêu cầu thanh toán/sự kiện không tạo khoản thu, khoản phí hoặc hiệu ứng kho trùng.
6. Dừng rồi khởi động lại Pharmacy và thử lỗi sau commit; công việc được tiếp tục mà không mất hoặc lặp hiệu ứng.
7. Hủy/hết hạn/cấp thuốc lỗi và thanh toán đến muộn giữ đúng trạng thái kết thúc; Billing bù trừ một lần theo mode, không báo đã hoàn khi chưa có bằng chứng hoàn tiền.
8. Báo cáo và thông báo cập nhật bất đồng bộ, đúng người nhận; replay báo cáo không gửi lại thông báo.
9. Chạy profile sáu service cùng hạ tầng; Inpatient/Surgery tắt trong MVP; bảo toàn dữ liệu và tác động đã ghi khi rollback.

Kết quả phải gắn với commit, profile/cờ kích hoạt, fixture và bằng chứng kiểm thử thực tế theo [runbook nghiệm thu](../../../plans/06-acceptance-runbook.md). Danh sách trên là tiêu chí cần chứng minh, không phải kết quả đã đạt.

## Tài liệu tham chiếu

- [Thiết kế hệ thống và phạm vi MVP](../../../plans/01-system-design.md)
- [Luồng nghiệp vụ](../../../plans/02-business-flows.md)
- [Hợp đồng tích hợp và RBAC](../../../plans/03-contracts.md)
- [Kế hoạch triển khai](../../../plans/05-implementation-plan.md)
- [Nghiệm thu và vận hành](../../../plans/06-acceptance-runbook.md)
