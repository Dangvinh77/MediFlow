# MediFlow Review 1 — Báo cáo chi tiết (Tiếng Việt)

| Kiểm soát tài liệu | Giá trị |
|---|---|
| Dự án | Hệ thống quản lý bệnh viện MediFlow |
| Đợt đánh giá | Review 1 |
| Phạm vi nội dung | Phần A — Xác định vấn đề; Phần B — Đặc tả yêu cầu khách hàng |
| Phiên bản | Chi tiết |
| Ngôn ngữ | Tiếng Việt |
| Đường cơ sở | Mã nguồn, cấu hình triển khai và hợp đồng đã thực hiện trong kho mã hiện tại |
| Đối tượng đọc | Hội đồng đánh giá, giảng viên hướng dẫn, đại diện khách hàng, nhà phát triển, kiểm thử viên và người thuyết trình |
| Mục tiêu định dạng | Chuyển trực tiếp sang Word với tiêu đề và bảng ổn định |

## Phần A — Xác định vấn đề

### A1. Bối cảnh tổng quan

Bệnh viện điều phối thông tin vừa mang tính cá nhân, lâm sàng, vận hành và tài chính. Trong một đợt chăm sóc, người bệnh có thể tương tác với tiếp nhận, bác sĩ, điều dưỡng, phòng xét nghiệm, nhà thuốc, thu ngân, khoa nội trú và nhóm phẫu thuật. Mỗi khoa tạo ra thông tin mà khoa khác cần, nhưng vẫn phải chịu trách nhiệm riêng đối với quyết định chuyên môn và hồ sơ của mình.

Khi thông tin được quản lý bằng giấy, bảng tính, ứng dụng rời rạc hoặc tin nhắn không chính thức, tổ chức mất góc nhìn xuyên suốt đáng tin cậy. Nhân viên nhập lại dữ liệu, thay đổi trạng thái được báo chậm, phí có thể bị thiếu hoặc trùng, tồn kho thiếu chính xác và báo cáo quản trị phụ thuộc đối soát thủ công. Dữ liệu nhạy cảm cũng khó bảo vệ vì quy tắc truy cập giữa các công cụ không nhất quán.

MediFlow được thiết kế như một nền tảng quy trình bệnh viện kết nối các khu vực này nhưng vẫn duy trì quyền sở hữu dữ liệu rõ ràng. Kho mã hiện tại triển khai frontend trên trình duyệt và backend microservice Spring Boot. Backend có mười dịch vụ nghiệp vụ đã triển khai, được hỗ trợ bởi API gateway, khám phá dịch vụ Eureka, cơ sở dữ liệu PostgreSQL và messaging RabbitMQ.

Nguyên tắc trung tâm là mỗi dịch vụ nghiệp vụ có thẩm quyền đối với dữ liệu của mình. Các dịch vụ khác trao đổi tham chiếu UUID, chỉ dùng REST khi cần thông tin có thẩm quyền ngay lập tức và dùng sự kiện để phân phối sự kiện nghiệp vụ đã hoàn tất. Cấu trúc này hỗ trợ truy vết và giảm coupling ẩn giữa các khoa.

### A2. Phát biểu vấn đề chính thức

Khách hàng cần một hệ thống thông tin an toàn và có thể truy vết để điều phối hành trình người bệnh qua các khoa mà không tạo ra một cơ sở dữ liệu tập trung thiếu kiểm soát hoặc cho phép một khoa sửa hồ sơ có thẩm quyền của khoa khác.

Giải pháp phải trả lời năm câu hỏi cho mọi hành động quan trọng:

1. Ai được phép thực hiện hành động?
2. Thông tin đầu vào nào là bắt buộc?
3. Dịch vụ nào sở hữu quyết định nghiệp vụ và kết quả được lưu?
4. Thông tin đầu ra nào được cung cấp cho đối tượng được phép tiếp theo?
5. Hành động được truy vết, thông báo và khôi phục thế nào nếu quy trình phụ thuộc thất bại?

### A3. Động lực nghiệp vụ và các bên liên quan

| Động lực | Quan tâm của khách hàng | Phản hồi cần có |
|---|---|---|
| An toàn người bệnh | Thông tin thiếu hoặc chậm có thể ảnh hưởng chăm sóc | Đưa đúng thông tin lâm sàng cho nhân viên được phép ở đúng giai đoạn quy trình |
| Hiệu quả vận hành | Nhập lại và bàn giao thủ công tốn thời gian | Tái sử dụng định danh đáng tin cậy và tự động thông báo trạng thái đã hoàn tất |
| Toàn vẹn tài chính | Phí thiếu, trùng hoặc không giải thích được làm giảm niềm tin | Tạo sự kiện tính phí idempotent và truy vết mọi chuyển trạng thái tài chính |
| Quyền riêng tư | Hồ sơ y tế và tài chính nhạy cảm | Áp dụng định danh xác thực, phân quyền và ranh giới sở hữu dịch vụ |
| Trách nhiệm giải trình | Tranh chấp cần bằng chứng về hành động và trạng thái | Lưu định danh, actor, thời gian, chuyển trạng thái và lịch sử gửi |
| Khả năng mở rộng thay đổi | Chức năng bệnh viện thay đổi với tốc độ khác nhau | Giữ bounded context có thể kiểm thử và triển khai độc lập |
| Quan sát quản trị | Quyết định cần thông tin vận hành tổng hợp | Xây mô hình đọc báo cáo từ sự kiện nguồn có thẩm quyền |
| Khả năng phục hồi | Lỗi tạm thời không được làm mất việc một cách im lặng | Dùng outbox, xử lý idempotent, retry và dead-letter |

| Bên liên quan | Lợi ích/quan tâm | Điểm tiếp xúc chính trong hệ thống |
|---|---|---|
| Chủ sở hữu/hội đồng bệnh viện | Quản trị, kiểm soát chi phí, hiệu quả dịch vụ | Report và Organization |
| Quản trị bệnh viện | Nhân viên, khoa, cơ sở, cấu hình chính sách | Organization |
| Nhân viên lâm sàng | Bệnh sử chính xác, ghi nhận, chỉ định và kết quả | Patient và Clinical |
| Bộ phận chẩn đoán | Chỉ định đầy đủ, mẫu, kết quả, thời gian xử lý | Laboratory |
| Bộ phận dược | Đơn hợp lệ, tồn kho chính xác, bằng chứng cấp phát | Pharmacy |
| Bộ phận tài chính | Phí, hóa đơn, thanh toán, tạm ứng, biên lai, hoàn tiền | Billing |
| Vận hành khoa nội trú | Nhập viện, sử dụng giường, trạng thái chăm sóc, xuất viện | Inpatient |
| Vận hành phẫu thuật | Yêu cầu, sẵn sàng, tài nguyên, thực hiện, kết quả | Surgery |
| Người bệnh | Truy cập thông tin dịch vụ cá nhân được phép | Luồng web phía người bệnh qua gateway |
| Vận hành CNTT | Sẵn sàng, triển khai, giám sát, sao lưu, khôi phục | Nền tảng dùng chung và mọi dịch vụ |

### A4. Khoảng trống của trạng thái hiện tại

| Mã | Khoảng trống | Rủi ro nếu không xử lý | Phản hồi của MediFlow |
|---|---|---|---|
| GAP-01 | Không có tham chiếu người bệnh nhất quán giữa các khoa | Nhầm định danh hoặc hồ sơ trùng | Định danh UUID và tham chiếu có kiểm soát |
| GAP-02 | Trạng thái lịch khám tách khỏi công việc lâm sàng | Tư vấn chậm và bàn giao thiếu | Luồng lịch khám đến lượt khám |
| GAP-03 | Chỉ định/kết quả thiếu vòng đời nhìn thấy được | Xét nghiệm chậm, lặp hoặc bị bỏ sót | Trạng thái phiếu, mẫu, xử lý, xác minh và công bố |
| GAP-04 | Đơn thuốc và tồn kho không đối soát | Cấp thuốc không an toàn hoặc không khả dụng | Xác minh đơn cùng biến động kho có thể kiểm toán |
| GAP-05 | Khoản phí được tạo lại thủ công | Thất thoát hoặc tính phí hai lần | Tạo phí idempotent từ sự kiện |
| GAP-06 | Nhập viện và phân giường tách rời | Xung đột tài nguyên, không rõ tình trạng sử dụng | Xác nhận nhập viện và phân giường có kiểm soát |
| GAP-07 | Điều kiện phẫu thuật trao đổi không chính thức | Hủy lịch, xung đột tài nguyên hoặc thực hiện không an toàn | Cổng sẵn sàng và tài chính trước xếp lịch/thực hiện |
| GAP-08 | Thông báo gửi tùy ý | Bỏ lỡ cập nhật quan trọng | Theo dõi gửi dựa trên sự kiện và mẫu |
| GAP-09 | Báo cáo đọc/sửa trực tiếp dữ liệu vận hành | Vấn đề hiệu năng và toàn vẹn | Mô hình đọc báo cáo nuôi bằng sự kiện |
| GAP-10 | Khôi phục lỗi không tường minh | Mất dữ liệu im lặng hoặc lặp tác động | Outbox, retry, idempotency và quan sát dead-letter |

### A5. Mục tiêu và kết quả đo lường được

| Mã mục tiêu | Mục tiêu | Bằng chứng ở mức Review 1 |
|---|---|---|
| OBJ-01 | Truy vết quy trình người bệnh đầu-cuối | Mỗi luồng chính lưu UUID và trạng thái tham chiếu |
| OBJ-02 | Thực thi phân tách nhiệm vụ | Endpoint được bảo vệ khai báo vai trò được phép |
| OBJ-03 | Duy trì quyền sở hữu bounded context | Không dịch vụ nào đọc cơ sở dữ liệu của dịch vụ khác |
| OBJ-04 | Cải thiện bàn giao liên khoa | Hợp đồng REST/sự kiện mang định danh và thông tin tường minh |
| OBJ-05 | Bảo vệ tính nhất quán tài chính | Tiền dùng số thập phân và sự kiện lặp không tạo phí trùng |
| OBJ-06 | Hỗ trợ công việc bất đồng bộ tin cậy | Có outbox, retry, idempotency và dead-letter |
| OBJ-07 | Hỗ trợ thông tin quản trị | Reporting nhận sự kiện tin cậy vào mô hình truy vấn |
| OBJ-08 | Duy trì khả năng bảo trì | Dịch vụ theo clean architecture và quy ước kiểm thử chung |
| OBJ-09 | Cung cấp điểm vào client nhất quán | Trình duyệt dùng đường dẫn cùng nguồn `/api/*` qua gateway |
| OBJ-10 | Nêu rõ giới hạn | Năng lực production/ngoài chưa xác minh được ghi rõ thay vì giả định |

### A6. Xác định phạm vi

#### A6.1. Chức năng hệ thống trong phạm vi

| Bounded context | Trách nhiệm trong phạm vi | Hồ sơ/kết quả chính |
|---|---|---|
| Organization | Tham chiếu định danh tổ chức, nhân viên, vai trò, khoa, cơ sở, phòng, giường | Tham chiếu nhân viên/người dùng, gán vai trò, phân cấp cơ sở, tài nguyên |
| Patient | Đăng ký, hồ sơ, định danh, liên hệ, lịch khám | Hồ sơ người bệnh, lịch khám, trạng thái phía người bệnh |
| Clinical | Lượt khám, quan sát, chẩn đoán, chỉ định, đơn thuốc | Lượt khám, ghi chú, chẩn đoán, yêu cầu xét nghiệm, đơn thuốc |
| Laboratory | Danh mục test, vòng đời phiếu, mẫu, kết quả, xác minh | Phiếu, mẫu, kết quả, sự kiện công bố kết quả |
| Pharmacy | Danh mục thuốc, tồn kho, xác minh đơn, cấp phát | Thuốc, mức/biến động kho, hồ sơ cấp phát |
| Billing | Phí, hóa đơn, tạm ứng, thanh toán, biên lai, hoàn tiền, xác nhận | Phí, hóa đơn, giao dịch, biên lai, trạng thái xác nhận |
| Notification | Chọn mẫu, xử lý người nhận/kênh, lần gửi | Yêu cầu và trạng thái gửi thông báo |
| Report | Mô hình đọc từ sự kiện và báo cáo quản trị có lọc | Projection vận hành và tài chính |
| Inpatient | Nhập viện, xác nhận, giường, đợt nằm viện, chăm sóc/chuyển, xuất viện | Nhập viện, đợt điều trị, sử dụng giường, xuất viện |
| Surgery | Yêu cầu, sẵn sàng, xác nhận, lịch, thực hiện, kết quả | Yêu cầu, lịch, hồ sơ thủ thuật, kết quả |

#### A6.2. Phạm vi nền tảng dùng chung

- Gateway cung cấp ranh giới định tuyến cho client.
- Eureka khám phá dịch vụ, phục vụ đăng ký và tìm kiếm backend.
- Một cơ sở dữ liệu PostgreSQL cho mỗi dịch vụ nghiệp vụ.
- RabbitMQ vận chuyển sự kiện nghiệp vụ bất đồng bộ.
- Quy ước chung về envelope, exception, security context, event và khả năng tương tác.
- Topology container phục vụ phát triển và trình diễn.
- Kiểm thử tự động cho quy tắc domain, hành vi dịch vụ, persistence, web endpoint và hợp đồng tích hợp được thể hiện trong mã nguồn.

#### A6.3. Ngoài phạm vi hoặc chưa chứng minh hoàn tất

| Khu vực | Ranh giới |
|---|---|
| Hệ thống quốc gia | Không tuyên bố tích hợp định danh quốc gia, chính phủ hoặc bảo hiểm đã được chứng nhận |
| Hệ thống lâm sàng ngoài | Không tuyên bố tương tác PACS, EHR, phòng xét nghiệm ngoài hoặc thiết bị y tế đã chứng nhận |
| Thanh toán thương mại | Không tuyên bố hoàn tất tích hợp production với ngân hàng, thẻ hoặc cổng thanh toán |
| Nhà cung cấp thông báo | Có xử lý nội bộ; chưa thiết lập gửi production qua SMS/email/push |
| Kênh mobile | Có Flutter loader package, chưa có ứng dụng MediFlow mobile hoàn chỉnh có thể chạy |
| Quyết định lâm sàng tự động | Chẩn đoán, gợi ý kê đơn và chọn điều trị vẫn thuộc trách nhiệm con người |
| Bảo đảm production | Năng lực, độ sẵn sàng, SLO, RTO, RPO và lưu trữ cần xác minh theo triển khai |
| Chứng nhận | Chứng nhận pháp quy, accessibility, pentest và thiết bị y tế nằm ngoài bằng chứng hiện tại |

### A7. Vai trò và trách nhiệm chức năng

| Vai trò | Tạo hoặc thay đổi | Đọc hoặc xem xét | Ranh giới bị cấm/kiểm soát |
|---|---|---|---|
| `ADMIN` | Người dùng, tham chiếu nhân viên, vai trò, khoa, cơ sở, phòng, giường, cấu hình | Trạng thái quản trị và dữ liệu tham chiếu | Không tự động có toàn quyền lâm sàng chỉ vì là quản trị viên |
| `DOCTOR` | Lượt khám, chẩn đoán, chỉ định, đơn thuốc, yêu cầu nhập viện/phẫu thuật, quyết định lâm sàng | Bệnh sử, kết quả, thuốc, trạng thái nội trú/phẫu thuật | Không sửa trực tiếp tồn kho dược hoặc giao dịch tài chính đã quyết toán |
| `NURSE` | Quan sát, chăm sóc, dữ liệu hỗ trợ nhập viện/giường/chuyển/xuất viện được phép | Lịch khám, lượt khám, chỉ định, đợt nằm viện | Không tự chẩn đoán y khoa hoặc phê duyệt tài chính trái quyền |
| `PHARMACIST` | Cấp phát và biến động tồn kho | Đơn được duyệt và tồn kho | Không viết lại đơn lâm sàng nguồn |
| `CASHIER` | Tạm ứng, thanh toán, biên lai, hoàn tiền được phép, xác nhận tài chính | Phí, hóa đơn, số dư, yêu cầu clearance | Không sửa kết quả lâm sàng hoặc tạo chỉ định |
| `LAB_TECH` | Mẫu, xử lý, kết quả, trạng thái xác minh/công bố | Phiếu xét nghiệm và ngữ cảnh người bệnh được phép | Không đổi lý do lâm sàng hoặc lượt khám nguồn |
| `MANAGER` | Tiêu chí báo cáo và góc nhìn quản trị được phép | Thông tin vận hành/tài chính tổng hợp | Báo cáo không được sửa hồ sơ nguồn |
| `PATIENT` | Dữ liệu đăng ký/liên hệ/lịch khám của chính mình khi được phép | Kết quả, đơn, tài chính, thông báo, trạng thái chăm sóc được phép | Không truy cập hồ sơ người khác hoặc thao tác chỉ dành cho nhân viên |
| `SYSTEM` | Hồ sơ gửi sự kiện, retry, cập nhật read model, thông báo tự động | Dữ liệu sự kiện/vận hành được máy cấp quyền | Không tự tạo quyết định nghiệp vụ hay bỏ qua xác minh của dịch vụ sở hữu |

### A8. Kiến trúc và giải pháp đề xuất

Giải pháp dùng client trình duyệt và gateway trung tâm để tránh client phụ thuộc trực tiếp vào cổng của từng dịch vụ. Ứng dụng web tổ chức chức năng theo cùng bounded context với backend và thực hiện HTTP qua API client dùng chung. Dịch vụ backend tuân theo phụ thuộc clean architecture để quy tắc domain độc lập với Spring và adapter cơ sở dữ liệu.

REST đồng bộ được chọn khi không thể hoàn tất yêu cầu an toàn nếu thiếu xác nhận ngay từ dịch vụ có thẩm quyền. Sự kiện bất đồng bộ được chọn khi một sự kiện đã hoàn tất phải cập nhật thông báo, báo cáo, viện phí hoặc quy trình khác mà không bắt yêu cầu người dùng chờ mọi consumer.

| Lớp/thành phần | Trách nhiệm | Ràng buộc thiết kế |
|---|---|---|
| Web client | Hiển thị trang theo vai trò và đầu vào đã kiểm tra | Chỉ gọi gateway qua `/api/*` cùng nguồn |
| Gateway | Định tuyến và truyền ngữ cảnh định danh đáng tin cậy | Không sở hữu nghiệp vụ domain |
| Service API | Kiểm tra DTO, phân quyền, gọi use case | Không đưa entity qua ranh giới |
| Application | Điều phối use case và port | Không phụ thuộc implementation hạ tầng |
| Domain | Entity, value object, quy tắc và chuyển trạng thái | Không phụ thuộc Spring, persistence, transport |
| Infrastructure | Cài đặt persistence, messaging, REST client, cấu hình | Phụ thuộc hướng vào abstraction application/domain |
| PostgreSQL | Lưu trạng thái và outbox do dịch vụ sở hữu | Không truy cập bảng chéo dịch vụ |
| RabbitMQ | Phân phối sự kiện nghiệp vụ | Consumer idempotent, lỗi quan sát được |
| Eureka | Đăng ký và khám phá dịch vụ | Hỗ trợ định tuyến, không sở hữu dữ liệu nghiệp vụ |

### A9. Giả định và phụ thuộc

| Mã | Giả định/phụ thuộc | Cần xác minh |
|---|---|---|
| AD-01 | Người dùng được xác thực trước thao tác bảo vệ | Thiết kế identity provider và gateway khi triển khai |
| AD-02 | Role claim phản ánh trách nhiệm đã phê duyệt | Quản trị hành chính và rà soát quyền định kỳ |
| AD-03 | Đồng hồ mạng được đồng bộ | Cấu hình và giám sát hạ tầng |
| AD-04 | UUID trao đổi không dùng foreign key cơ sở dữ liệu chéo dịch vụ | Kiểm thử hợp đồng và tích hợp |
| AD-05 | RabbitMQ và cơ sở dữ liệu được giám sát/sao lưu | Kế hoạch vận hành production |
| AD-06 | Các khoa phê duyệt trạng thái quy trình và phân tách nhiệm vụ | Workshop nghiệm thu khách hàng |
| AD-07 | Giao diện nhà cung cấp ngoài sẽ là adapter tường minh | Lựa chọn vendor và tài liệu hợp đồng |
| AD-08 | Phần cứng production được định cỡ sau kiểm thử tải đại diện | Kế hoạch hiệu năng và mô hình workload |
| AD-09 | Lưu giữ/xóa dữ liệu tuân luật và chính sách bệnh viện | Phê duyệt pháp lý và quản trị |
| AD-10 | Quyết định lâm sàng thuộc trách nhiệm người có chuyên môn | Quản trị lâm sàng bệnh viện |

### A10. Giới hạn, rủi ro và công việc tương lai

| Mã | Giới hạn/rủi ro | Giảm thiểu hiện tại | Công việc tương lai |
|---|---|---|---|
| LR-01 | Chưa có ứng dụng mobile hoàn chỉnh | Web responsive là giả định kênh được hỗ trợ | Xây và xác minh Flutter app chỉ gọi gateway |
| LR-02 | Chưa tích hợp bảo hiểm ngoài | Luồng tài chính nội bộ vẫn tường minh | Thêm adapter bảo hiểm, eligibility, claim và reconciliation |
| LR-03 | Chưa tích hợp thanh toán ngoài | Giao dịch nội bộ do thu ngân kiểm soát | Tích hợp provider tuân thủ và đối soát settlement |
| LR-04 | Chưa chứng nhận giao diện thiết bị/lab ngoài | Vòng đời lab nội bộ/thủ công vẫn dùng được | Xác định adapter HL7/FHIR/thiết bị khi khách hàng yêu cầu |
| LR-05 | Chưa chốt nhà cung cấp thông báo | Trạng thái/lần gửi được mô hình nội bộ | Chọn provider, bảo vệ credential, đo SLO gửi |
| LR-06 | Chưa đo năng lực production | Docker hỗ trợ development/demo | Chạy load, soak, failure và recovery test |
| LR-07 | Chưa duyệt SLO/RTO/RPO | Có cơ chế tin cậy cấp ứng dụng | Chốt mục tiêu đo lường và thiết kế triển khai dự phòng |
| LR-08 | Chưa chứng nhận accessibility | Cấu trúc web responsive là nền tảng | Audit theo WCAG mục tiêu và khắc phục |
| LR-09 | Quản trị lâm sàng ngoài mã nguồn | Vai trò/cổng quy trình hạn chế thao tác | Thiết lập chủ chính sách, hội đồng rà soát, quy trình sự cố |
| LR-10 | Chưa xác lập trạng thái pháp quy | Không đưa tuyên bố chứng nhận thiếu căn cứ | Đánh giá pháp lý và tuân thủ theo khu vực triển khai |

## Phần B — Đặc tả yêu cầu khách hàng (CRS)

### B1. Quy ước và mức ưu tiên yêu cầu

Các từ **phải**, **nên** và **có thể** biểu thị yêu cầu bắt buộc, khuyến nghị và tùy chọn. Mã yêu cầu là tham chiếu ổn định cho đánh giá và nghiệm thu sau này. Mức ưu tiên dùng `Must`, `Should` hoặc `Could`.

### B2. Yêu cầu chức năng theo dịch vụ

| Mã | Ưu tiên | Yêu cầu | Dịch vụ sở hữu | Người dùng chính |
|---|---|---|---|---|
| FR-ORG-01 | Must | Hệ thống phải quản lý tham chiếu nhân viên/người dùng và vai trò | Organization | `ADMIN` |
| FR-ORG-02 | Must | Hệ thống phải quản lý khoa, cơ sở, phòng và giường | Organization | `ADMIN`, nhân viên khoa được phép |
| FR-PAT-01 | Must | Hệ thống phải đăng ký người bệnh với UUID ổn định và nhân khẩu học hợp lệ | Patient | `PATIENT`, nhân viên được phép |
| FR-PAT-02 | Must | Hệ thống phải tạo/theo dõi lịch khám gắn đúng người bệnh | Patient | `PATIENT`, `NURSE`, nhân viên được phép |
| FR-CLI-01 | Must | Hệ thống phải ghi lượt khám, quan sát, chẩn đoán và ghi chú | Clinical | `DOCTOR`, `NURSE` |
| FR-CLI-02 | Must | Hệ thống phải cho bác sĩ được phép tạo yêu cầu xét nghiệm và đơn thuốc | Clinical | `DOCTOR` |
| FR-LAB-01 | Must | Hệ thống phải đưa phiếu xét nghiệm qua vòng đời có kiểm soát | Laboratory | `LAB_TECH` |
| FR-LAB-02 | Must | Hệ thống phải lưu mẫu, kết quả, xác minh và công bố | Laboratory | `LAB_TECH`, `DOCTOR` |
| FR-PHA-01 | Must | Hệ thống phải xác minh đơn đã duyệt trước khi cấp phát | Pharmacy | `PHARMACIST` |
| FR-PHA-02 | Must | Hệ thống phải ghi biến động kho và cấp phát một cách nhất quán | Pharmacy | `PHARMACIST` |
| FR-BIL-01 | Must | Hệ thống phải tạo phí có thể truy vết, không tác động trùng | Billing | `SYSTEM`, `CASHIER` |
| FR-BIL-02 | Must | Hệ thống phải hỗ trợ hóa đơn, tạm ứng, thanh toán, biên lai, hoàn tiền | Billing | `CASHIER`, `PATIENT` |
| FR-NOT-01 | Must | Hệ thống phải tạo yêu cầu thông báo từ domain event hợp lệ | Notification | `SYSTEM` |
| FR-NOT-02 | Should | Hệ thống nên lưu lần gửi, trạng thái và lý do lỗi | Notification | `SYSTEM`, nhân viên hỗ trợ |
| FR-REP-01 | Must | Hệ thống phải cập nhật projection báo cáo từ sự kiện tin cậy | Report | `SYSTEM` |
| FR-REP-02 | Must | Hệ thống phải cung cấp báo cáo có lọc cho quản lý được phép | Report | `MANAGER` |
| FR-INP-01 | Must | Hệ thống phải kiểm soát nhập viện bằng điều kiện lâm sàng/tài chính | Inpatient | `DOCTOR`, `NURSE`, `CASHIER` |
| FR-INP-02 | Must | Hệ thống phải phân/giải phóng giường và theo dõi đợt nằm viện/chuyển | Inpatient | `NURSE`, `DOCTOR` |
| FR-INP-03 | Must | Hệ thống phải kiểm tra điều kiện xuất viện và đóng đợt | Inpatient | `DOCTOR`, `NURSE`, `CASHIER` |
| FR-SUR-01 | Must | Hệ thống phải theo dõi yêu cầu, sẵn sàng và xác nhận tài chính phẫu thuật | Surgery | `DOCTOR`, nhân viên phẫu thuật được phép |
| FR-SUR-02 | Must | Hệ thống phải xếp lịch và ghi thực hiện/kết quả | Surgery | Nhân viên phẫu thuật được phép |

### B3. Chu trình xử lý dữ liệu theo vai trò

| Quy trình | Đối tượng | Thông tin đầu vào | Xử lý | Thông tin đầu ra | Dịch vụ sở hữu |
|---|---|---|---|---|---|
| Quản trị nền tảng | `ADMIN` | Định danh nhân viên, vai trò, khoa, dữ liệu cơ sở/tài nguyên | Kiểm tra quyền và duy nhất; lưu trạng thái tổ chức | Tham chiếu người dùng/tài nguyên và dữ liệu kiểm toán | Organization |
| Đăng ký dịch vụ/người bệnh | `PATIENT`, nhân viên được phép | Nhân khẩu học, liên hệ, định danh, đầu vào đồng thuận | Kiểm tra trường và trùng; cấp UUID | Hồ sơ và xác nhận người bệnh | Patient |
| Quản lý lịch khám | `PATIENT`, `NURSE` | UUID người bệnh, dịch vụ/bác sĩ, thời gian, lý do | Kiểm tra điều kiện lịch; tạo/cập nhật trạng thái | Lịch khám và trạng thái hiển thị | Patient |
| Chăm sóc lâm sàng | `DOCTOR`, `NURSE` | Ngữ cảnh người bệnh, triệu chứng, quan sát, lịch/tham chiếu | Mở/cập nhật lượt; áp dụng quy tắc lâm sàng | Ghi chú, quan sát, chẩn đoán, chỉ định | Clinical |
| Xét nghiệm chẩn đoán | `LAB_TECH` | Phiếu, test, mẫu, giá trị đo | Chuyển vòng đời; xác minh quyền/độ đầy đủ | Kết quả xác minh và sự kiện công bố | Laboratory |
| Cấp phát thuốc | `PHARMACIST` | Đơn, thuốc, số lượng, tồn kho | Kiểm tra đơn/kho; lưu cấp phát và biến động | Hồ sơ cấp phát và vị thế kho mới | Pharmacy |
| Giao dịch tài chính | `CASHIER` | Tham chiếu phí/hóa đơn/nhập viện, số tiền, phương thức, lý do | Kiểm tra trạng thái và số thập phân; ghi giao dịch | Số dư, biên lai, hoàn tiền hoặc clearance | Billing |
| Quản lý nội trú | `DOCTOR`, `NURSE`, `CASHIER` | Nhu cầu nhập viện, clearance, giường, chăm sóc/xuất viện | Kiểm soát chuyển trạng thái; phân/giải phóng; lưu lịch sử | Hồ sơ nhập viện/đợt/chuyển/xuất viện | Inpatient |
| Quản lý phẫu thuật | `DOCTOR`, nhân viên được phép | Thủ thuật, ưu tiên, sẵn sàng, clearance, lịch/tài nguyên | Kiểm soát vòng đời; giữ lịch; ghi thực hiện/kết quả | Trạng thái, lịch, thủ thuật và kết quả | Surgery |
| Báo cáo quản trị | `MANAGER` | Loại báo cáo, thời gian/bộ lọc, phạm vi quyền | Truy vấn projection; tổng hợp/định dạng | Báo cáo vận hành hoặc tài chính | Report |
| Tự động hóa nền | `SYSTEM` | Outbox event, message key, dữ liệu người nhận/projection | Phát, xử lý idempotent, retry/dead-letter | Trạng thái gửi, trạng thái hạ nguồn, bằng chứng vận hành | Shared/Notification/Report |

### B4. Luồng đăng ký, lịch khám và lượt khám

| Bước | Đối tượng/nguồn | Đầu vào | Xử lý và quy tắc | Đầu ra/đối tượng nhận |
|---:|---|---|---|---|
| 1 | Người bệnh hoặc nhân viên được phép | Nhân khẩu học và liên hệ | Patient kiểm tra dữ liệu bắt buộc và tính duy nhất | UUID và hồ sơ người bệnh |
| 2 | Người bệnh/tiếp nhận | UUID người bệnh, dịch vụ/thời gian | Patient kiểm tra lịch và ghi trạng thái | UUID/trạng thái lịch khám |
| 3 | Điều dưỡng/tiếp nhận | Lịch và thông tin đến khám | Chuyển lịch qua trạng thái check-in hợp lệ | Ngữ cảnh lịch sẵn sàng |
| 4 | Bác sĩ/điều dưỡng | Tham chiếu người bệnh/lịch | Clinical xác nhận ngữ cảnh và mở lượt khám | UUID lượt khám |
| 5 | Bác sĩ/điều dưỡng | Triệu chứng, quan sát, ghi chú | Clinical kiểm tra và ghi thông tin chăm sóc | Lượt khám cập nhật |
| 6 | Bác sĩ | Đánh giá và chẩn đoán | Clinical lưu chẩn đoán và xác định bước chỉ định được phép | Chẩn đoán, xét nghiệm, đơn, yêu cầu nhập viện/phẫu thuật |

### B5. Luồng Clinical đến Laboratory

| Bước | Thông tin đầu vào | Xử lý | Thông tin đầu ra | Kiểm soát |
|---:|---|---|---|---|
| 1 | UUID lượt khám/người bệnh, mã test | Clinical kiểm tra lượt khám và quyền bác sĩ | Yêu cầu xét nghiệm lâm sàng | Chỉ bác sĩ được phép chỉ định |
| 2 | Yêu cầu và chi tiết test | Laboratory tiếp nhận hợp đồng đầy đủ | UUID phiếu và trạng thái đầu | Chống trùng/tương quan |
| 3 | Phiếu và dữ liệu mẫu | Ghi lấy/nhận mẫu và trạng thái xử lý | Hồ sơ mẫu và thời gian | Kiểm tra chuyển trạng thái |
| 4 | Giá trị đo, tham chiếu, nhận xét | `LAB_TECH` nhập kết quả và kiểm tra độ đầy đủ | Kết quả nháp/hoàn tất | Trường, đơn vị, khoảng theo định nghĩa |
| 5 | Kết quả hoàn tất | Xác minh được phép đổi trạng thái công bố | Kết quả công bố và sự kiện | Dữ liệu công bố chỉ sửa theo quy trình kiểm soát |
| 6 | Sự kiện kết quả | Notification, Report và Billing đủ điều kiện xử lý idempotent | Thông báo, projection, khoản phí hợp lệ | Quan sát retry/dead-letter |

### B6. Luồng đơn thuốc đến cấp phát

| Bước | Đối tượng | Đầu vào | Xử lý | Đầu ra |
|---:|---|---|---|---|
| 1 | `DOCTOR` | Lượt khám, người bệnh, UUID thuốc, liều, tần suất, thời gian, số lượng, hướng dẫn | Clinical kiểm tra đơn và trạng thái lượt khám | Đơn thuốc được phép |
| 2 | `PHARMACIST` | UUID đơn và dòng thuốc | Pharmacy kiểm tra trạng thái đơn và tham chiếu thuốc | Quyết định cấp phát |
| 3 | `PHARMACIST` | Số lượng yêu cầu và tồn hiện tại | Bắt buộc số lượng hợp lệ và đủ tồn | Số lượng cấp được duyệt |
| 4 | Giao dịch Pharmacy | Hồ sơ đơn và kho | Lưu nhất quán cấp phát và biến động | Hồ sơ cấp phát và kho giảm |
| 5 | Consumer sự kiện | Sự kiện cấp phát | Billing/Notification/Report xử lý sự kiện hợp lệ idempotent | Cập nhật tài chính/truyền thông/báo cáo |

### B7. Luồng Billing và tài chính

| Bước | Đối tượng/nguồn | Đầu vào | Quy tắc xử lý | Đầu ra |
|---:|---|---|---|---|
| 1 | Sự kiện dịch vụ vận hành | Event ID, người bệnh, hồ sơ nguồn, hạng mục, số tiền thập phân | Billing kiểm tra event/idempotency key | Phí mới hoặc nhận biết trùng |
| 2 | Billing | Phí mở và ngữ cảnh tài khoản/người bệnh | Gắn phí vào hóa đơn theo quy tắc | Hóa đơn và số dư |
| 3 | `CASHIER`/người bệnh | Tham chiếu hóa đơn/nhập viện, tiền, phương thức | Kiểm tra số dương và trạng thái cho phép | Giao dịch tạm ứng/thanh toán |
| 4 | Billing | Giao dịch thành công | Cập nhật số dư/clearance và tạo dữ liệu biên lai | Biên lai và trạng thái tài chính |
| 5 | `CASHIER` | Giao dịch gốc, lý do và số tiền được duyệt | Kiểm tra số có thể hoàn và quyền | Hoàn tiền và số dư điều chỉnh |
| 6 | `SYSTEM` | Sự kiện trạng thái tài chính | Thông báo Inpatient/Surgery/Report phụ thuộc | Cập nhật clearance hoặc báo cáo |

Hồ sơ tài chính không được dùng số dấu phẩy động nhị phân. Giá trị tiền dùng `BigDecimal` tại biên nghiệp vụ backend. Phân phối lặp cùng sự kiện tính phí không được tạo thêm khoản phí.

### B8. Luồng nội trú

| Bước | Đối tượng | Đầu vào | Xử lý | Đầu ra |
|---:|---|---|---|---|
| 1 | `DOCTOR` | Người bệnh/lượt khám, lý do, chi tiết nhập viện | Inpatient ghi yêu cầu sau xác minh tham chiếu | Nhập viện chờ xử lý |
| 2 | `CASHIER`/Billing | Tham chiếu nhập viện và điều kiện tài chính | Ghi tạm ứng/thanh toán và phát clearance | Trạng thái xác nhận tài chính |
| 3 | `NURSE`/nhân viên được phép | Nhập viện đã xác nhận, nhu cầu khoa/giường | Kiểm tra giường phù hợp còn trống và phân | Nhập viện hoạt động, giường bị chiếm |
| 4 | `NURSE`, `DOCTOR` | Đợt hoạt động, dữ liệu quan sát/chăm sóc | Ghi hoạt động trong đợt | Lịch sử chăm sóc |
| 5 | `NURSE`/nhân viên được phép | Nhu cầu chuyển và tài nguyên đích | Kiểm tra đích và thực hiện chuyển giường kiểm soát | Lịch sử chuyển, sử dụng cập nhật |
| 6 | `DOCTOR` | Quyết định và tóm tắt xuất viện | Ghi sẵn sàng xuất viện lâm sàng | Chờ hoàn tất xuất viện |
| 7 | `NURSE`, `CASHIER`, Inpatient | Hoàn tất chăm sóc và trạng thái tài chính | Kiểm tra điều kiện, đóng đợt, giải phóng giường | Hồ sơ xuất viện, giường trống |

### B9. Luồng phẫu thuật

| Bước | Đối tượng | Đầu vào | Xử lý | Đầu ra |
|---:|---|---|---|---|
| 1 | `DOCTOR` | Người bệnh, lượt khám, thủ thuật, ưu tiên, lý do | Surgery kiểm tra thông tin yêu cầu | Yêu cầu phẫu thuật |
| 2 | Nhóm chăm sóc được phép | Bằng chứng/checklist sẵn sàng | Ghi và đánh giá điều kiện | Trạng thái sẵn sàng |
| 3 | Billing/`CASHIER` | Tham chiếu và yêu cầu tài chính | Ghi giao dịch và clearance | Trạng thái xác nhận tài chính |
| 4 | Người xếp lịch được phép | Yêu cầu sẵn sàng/đã xác nhận, phòng, thời gian, nhóm | Kiểm tra trạng thái/tài nguyên; xếp lịch | Lịch phẫu thuật |
| 5 | Nhân viên phẫu thuật được phép | Thủ thuật đã xếp và thông tin trong mổ | Ghi bắt đầu, thực hiện, hoàn tất theo trạng thái cho phép | Hồ sơ thủ thuật |
| 6 | Bác sĩ được phép | Phát hiện, kết quả, thông tin hậu phẫu | Kiểm tra và ghi kết quả | Kết quả phẫu thuật và sự kiện hạ nguồn |

### B10. Luồng Notification và Reporting

| Consumer | Đầu vào kích hoạt | Xử lý | Đầu ra | Hành vi khi lỗi |
|---|---|---|---|---|
| Notification | Domain event hợp lệ, người nhận, dữ liệu mẫu | Chọn mẫu/kênh, tạo yêu cầu, thử gửi | Nội dung, trạng thái, thời gian, lý do lỗi | Retry hữu hạn lỗi tạm thời; lộ lỗi cuối |
| Report | Domain event tin cậy và dữ liệu tương quan | Áp dụng idempotent vào read model | Projection và chỉ số cập nhật | Retry an toàn; không sửa nguồn |

Notification và Report là consumer hạ nguồn, không trở thành nguồn có thẩm quyền cho hồ sơ lâm sàng, tài chính, dược, xét nghiệm, nội trú hoặc phẫu thuật.

### B11. Quyền sở hữu dữ liệu và yêu cầu thông tin

| Thông tin | Nguồn sự thật | Cách chia sẻ | Kiểm soát chính |
|---|---|---|---|
| Nhân viên, vai trò, khoa, cơ sở, phòng, giường | Organization | REST/sự kiện theo định nghĩa | UUID, quyền, trạng thái hoạt động |
| Hồ sơ người bệnh và lịch khám | Patient | Hợp đồng REST/sự kiện được phép | Khớp định danh, riêng tư, UUID ổn định |
| Lượt khám, chẩn đoán, chỉ định, đơn | Clinical | Hợp đồng/sự kiện lâm sàng kiểm soát | Quyền bác sĩ, kiểm tra trạng thái |
| Mẫu và kết quả xét nghiệm | Laboratory | Hợp đồng/sự kiện kết quả | Xác minh, công bố, kiểm soát chỉnh sửa |
| Tồn kho và cấp phát | Pharmacy | Hợp đồng/sự kiện cấp phát/kho | Quy tắc số lượng, biến động nhất quán, truy vết |
| Phí, hóa đơn, giao dịch, biên lai, hoàn tiền, clearance | Billing | Hợp đồng/sự kiện trạng thái tài chính | Tiền thập phân, idempotency, kiểm toán |
| Gửi thông báo | Notification | Chỉ truy vấn vận hành | Riêng tư người nhận, trạng thái, bằng chứng retry |
| Projection báo cáo | Report | Truy vấn quản lý được phép | Chỉ đọc, idempotency sự kiện |
| Nhập viện, đợt, giường, xuất viện | Inpatient | Tham chiếu/sự kiện kiểm soát | Cổng clearance và chuyển trạng thái |
| Yêu cầu, lịch, thực hiện, kết quả phẫu thuật | Surgery | Tham chiếu/sự kiện kiểm soát | Sẵn sàng, clearance, quyền |

### B12. Yêu cầu API và ranh giới

| Mã | Yêu cầu |
|---|---|
| API-01 | Lưu lượng client phải qua gateway bằng đường dẫn cùng nguồn `/api/*` |
| API-02 | Mọi endpoint backend được bảo vệ phải khai báo role authorization |
| API-03 | DTO đi qua ranh giới dịch vụ; persistence entity không được đi qua |
| API-04 | UUID định danh hồ sơ nghiệp vụ và tham chiếu chéo dịch vụ |
| API-05 | Lỗi validation phải xác định và phù hợp hiển thị client |
| API-06 | Dịch vụ không lộ chi tiết cơ sở dữ liệu thành hợp đồng |
| API-07 | Quyết định chéo dịch vụ tức thời dùng REST resilient với timeout/lỗi tường minh |
| API-08 | Thay đổi hợp đồng phải giữ hoặc version hóa kỳ vọng tương thích |

### B13. Yêu cầu sự kiện và bất đồng bộ

| Mã | Yêu cầu |
|---|---|
| EVT-01 | Thay đổi trạng thái đã commit cần thông tin cho context khác phải phát sự kiện |
| EVT-02 | Payload phải có event, aggregate, correlation và timestamp ổn định theo hợp đồng chung |
| EVT-03 | Trạng thái cơ sở dữ liệu và ý định phát sự kiện phải phối hợp bằng outbox hoặc tương đương |
| EVT-04 | Consumer phải ghi nhận hoặc thực thi xử lý idempotent |
| EVT-05 | Retry phải hữu hạn và quan sát được |
| EVT-06 | Thông điệp không khôi phục phải đến đường dead-letter quan sát được |
| EVT-07 | Consumer không được đoán định danh thiếu hoặc truy vấn cơ sở dữ liệu dịch vụ khác |
| EVT-08 | Lỗi Report/Notification không được rollback giao dịch có thẩm quyền đã hoàn tất |

### B14. Yêu cầu bảo mật và quyền riêng tư

| Mã | Ưu tiên | Yêu cầu |
|---|---|---|
| SEC-01 | Must | Yêu cầu bảo vệ phải mang định danh xác thực đáng tin cậy |
| SEC-02 | Must | Backend phải thực thi quyền dù UI đã ẩn hành động không có quyền |
| SEC-03 | Must | Người dùng chỉ nhận thông tin được phép theo vai trò và quan hệ hồ sơ |
| SEC-04 | Must | Secret production phải nằm ngoài source control |
| SEC-05 | Must | Lưu lượng production có dữ liệu nhạy cảm phải dùng HTTPS/TLS |
| SEC-06 | Must | Thay đổi quan trọng giữ bằng chứng actor/thời gian khi hợp đồng yêu cầu |
| SEC-07 | Should | Quyền quản trị/đặc quyền nên được rà soát định kỳ |
| SEC-08 | Should | Log nên tránh dữ liệu y tế hoặc credential không cần thiết |
| SEC-09 | Should | Dữ liệu sao lưu nên có mã hóa và quyền tương đương dữ liệu sống |
| SEC-10 | Should | Kiểm thử bảo mật nên gồm dependency, quyền, đầu vào và cấu hình |

### B15. Yêu cầu phi chức năng và vận hành

| Khu vực | Yêu cầu | Cách xác minh |
|---|---|---|
| Tính đúng | Invariant nghiệp vụ phải có kiểm thử tự động | Bằng chứng unit/integration test |
| Tính sẵn sàng | Phải chốt SLO trước production | Thiết kế giám sát và failure test |
| Hiệu năng | Thao tác người dùng nên đạt mục tiêu phản hồi dưới tải đại diện | Load/latency test |
| Khả năng mở rộng | Instance stateless nên mở rộng độc lập khi phù hợp | Kiểm thử triển khai/workload |
| Khôi phục | Phải diễn tập backup/restore và message recovery | Bài tập recovery ghi RTO/RPO |
| Khả năng bảo trì | Dịch vụ giữ hướng phụ thuộc clean architecture | Code review và architecture check |
| Quan sát | Health, log, error rate, backlog và tài nguyên phải hiển thị | Dashboard và alert test |
| Tương thích | Hợp đồng API/event phải kiểm thử producer/consumer | Contract/integration test |
| Khả dụng | Quy trình vai trò dùng thuật ngữ nhất quán và feedback có thể hành động | Đánh giá theo nhiệm vụ |
| Accessibility | Phải chọn và audit tiêu chuẩn mục tiêu | Đánh giá khả năng tiếp cận |
| Bản địa hóa | Nội dung hiển thị hỗ trợ chiến lược ngôn ngữ khách hàng | Rà soát nội dung/UI |
| Vòng đời dữ liệu | Phải duyệt lưu giữ, lưu trữ, chỉnh sửa và xóa | Rà soát quản trị và lifecycle test |

### B16. Yêu cầu phần cứng

Các giá trị là đường cơ sở lập kế hoạch, không phải bảo đảm hiệu năng.

#### B16.1. Phần cứng client

| Cấu hình | Tối thiểu | Khuyến nghị |
|---|---|---|
| Máy nhân viên | CPU 64-bit hai lõi, RAM 4 GB, màn hình 1366×768 | CPU hiện đại bốn lõi, RAM 8 GB, Full HD |
| Thiết bị người bệnh | Điện thoại/tablet/máy tính gần đây, trống 2 GB bộ nhớ | OS/trình duyệt an toàn còn hỗ trợ, RAM từ 4 GB |
| Máy lâm sàng dùng chung | Bàn phím/chuột và mạng tin cậy | Full HD, thiết bị barcode theo cơ sở, nguồn UPS |
| Máy phát triển | CPU 64-bit bốn lõi, RAM 16 GB, SSD trống 30 GB | CPU tám lõi, RAM 32 GB, SSD trống từ 60 GB |

#### B16.2. Phần cứng server

| Môi trường | Tính toán | Bộ nhớ | Lưu trữ | Ghi chú |
|---|---|---|---|---|
| Demo cục bộ | 8 lõi logic | 16 GB | SSD trống 40 GB | Hạ tầng container và workload chọn lọc |
| Tích hợp dùng chung | 16 lõi logic | 32 GB | SSD 150 GB | Dịch vụ đồng thời, test, dữ liệu mẫu, giám sát cơ bản |
| Đường cơ sở production ban đầu | 24+ lõi logic trên nhiều host | 64+ GB trên nhiều host | SSD chịu lỗi từ 500 GB và backup tách biệt | Phải định cỡ lại sau test tải, lưu giữ, sẵn sàng, khôi phục |

Thiết kế production nên tách hoặc phân phối workload ứng dụng, cơ sở dữ liệu, messaging, giám sát và sao lưu theo mục tiêu sẵn sàng và tuân thủ đã duyệt.

### B17. Yêu cầu phần mềm

#### B17.1. Phần mềm client

| Hạng mục | Yêu cầu |
|---|---|
| Trình duyệt | Chrome, Edge, Firefox hoặc Safari hiện tại, bật JavaScript, cookie, HTTPS |
| Hệ điều hành | Windows, macOS, Linux, Android hoặc iOS còn được nhà cung cấp hỗ trợ |
| Mạng | Truy cập ổn định endpoint HTTPS qua quy tắc firewall/proxy đã duyệt |
| Công cụ tài liệu | Microsoft Word, LibreOffice Writer hoặc công cụ tương thích |
| Trạng thái mobile | Giả định web responsive; chưa cung cấp mobile client native hoàn chỉnh |

#### B17.2. Phần mềm server và phát triển

| Hạng mục | Đường cơ sở theo mã nguồn |
|---|---|
| Java | 21 |
| Spring Boot | 3.3.5 |
| Spring Cloud | 2023.0.3 |
| Build backend | Maven multi-module |
| Frontend | Next.js 16.2.11, React 19.2.4, TypeScript 5, Tailwind CSS 4, pnpm |
| Cơ sở dữ liệu | Tương thích PostgreSQL 16, cơ sở dữ liệu logic tách theo dịch vụ |
| Messaging | Tương thích RabbitMQ 3 với hỗ trợ quản trị vận hành |
| Discovery | Eureka Server |
| Container | Docker Engine và Docker Compose tương thích định nghĩa kho mã |
| Dịch vụ nền production | HTTPS certificate, secret management, log/metrics/alert tập trung, backup, đồng bộ thời gian |

### B18. Ma trận truy vết và nghiệm thu

| Mã nghiệm thu | Trình diễn | Yêu cầu liên quan |
|---|---|---|
| AC-01 | Đăng ký người bệnh và tạo lịch với quyền sở hữu hợp lệ | FR-PAT-01, FR-PAT-02, SEC-03 |
| AC-02 | Mở lượt khám, ghi quan sát/chẩn đoán dưới vai trò cho phép | FR-CLI-01, SEC-01, SEC-02 |
| AC-03 | Chỉ định, xử lý, xác minh và công bố kết quả xét nghiệm | FR-CLI-02, FR-LAB-01, FR-LAB-02 |
| AC-04 | Tạo đơn và cấp thuốc đồng thời cập nhật kho | FR-CLI-02, FR-PHA-01, FR-PHA-02 |
| AC-05 | Xử lý sự kiện tính phí một lần và tạo dữ liệu phí/hóa đơn | FR-BIL-01, EVT-03, EVT-04 |
| AC-06 | Ghi tạm ứng/thanh toán, phát biên lai, hoàn tiền được phép | FR-BIL-02, SEC-02 |
| AC-07 | Đưa nhập viện qua clearance, giường, chăm sóc/chuyển và xuất viện | FR-INP-01, FR-INP-02, FR-INP-03 |
| AC-08 | Đưa phẫu thuật qua yêu cầu, sẵn sàng, clearance, lịch, thực hiện, kết quả | FR-SUR-01, FR-SUR-02 |
| AC-09 | Sinh và theo dõi thông báo từ sự kiện hợp lệ | FR-NOT-01, FR-NOT-02, EVT-05, EVT-06 |
| AC-10 | Cập nhật/truy vấn projection báo cáo mà không sửa nguồn | FR-REP-01, FR-REP-02, EVT-04 |
| AC-11 | Từ chối vai trò không được phép tại mỗi thao tác bảo vệ | SEC-01, SEC-02, SEC-03 |
| AC-12 | Chứng minh dịch vụ dùng hợp đồng thay vì truy cập chéo cơ sở dữ liệu | API-03, API-06, EVT-07 |
| AC-13 | Gửi lại sự kiện mà không tạo tác động nghiệp vụ trùng | EVT-04, FR-BIL-01 |
| AC-14 | Quan sát retry hữu hạn và dead-letter/lỗi cuối | EVT-05, EVT-06 |
| AC-15 | Chạy thành công bộ kiểm tra tự động đã thống nhất | OBJ-08 và mọi yêu cầu quy tắc đã triển khai |

### B19. Thuật ngữ

| Thuật ngữ | Định nghĩa |
|---|---|
| Bounded context | Ranh giới nghiệp vụ có mô hình, quy tắc, dịch vụ và quyền sở hữu dữ liệu riêng |
| CRS | Customer Requirements Specification — Đặc tả yêu cầu khách hàng |
| Domain event | Tuyên bố bền vững rằng thay đổi trạng thái nghiệp vụ có ý nghĩa đã hoàn tất |
| DTO | Data Transfer Object dùng tại ranh giới ứng dụng hoặc dịch vụ |
| Encounter | Lượt tương tác lâm sàng với người bệnh được ghi nhận |
| Idempotent consumer | Bộ xử lý ngăn phân phối lặp tạo lại tác động nghiệp vụ |
| Outbox | Bản ghi cơ sở dữ liệu trong giao dịch dùng để phát sự kiện tin cậy |
| Read model | Dữ liệu hướng truy vấn sinh từ sự kiện nguồn có thẩm quyền |
| REST | Tương tác resource/command đồng bộ qua HTTP |
| RPO | Recovery Point Objective — mục tiêu điểm khôi phục |
| RTO | Recovery Time Objective — mục tiêu thời gian khôi phục |
| SLO | Service Level Objective — mục tiêu cấp dịch vụ |
| UUID | Universally Unique Identifier — định danh duy nhất ổn định |

---

Báo cáo chi tiết tách biệt hành vi đã triển khai và bám sát mã nguồn khỏi khuyến nghị và công việc production tương lai. Vẫn cần khách hàng phê duyệt chính sách triển khai, tích hợp ngoài, quản trị lâm sàng, mục tiêu năng lực, thời hạn lưu giữ và mục tiêu khôi phục.
