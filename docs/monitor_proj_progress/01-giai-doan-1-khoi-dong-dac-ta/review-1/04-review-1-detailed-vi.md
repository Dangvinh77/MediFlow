# MediFlow Review 1 — Báo cáo chi tiết (Tiếng Việt)

| Kiểm soát tài liệu | Giá trị |
|---|---|
| Dự án | Hệ thống quản lý bệnh viện MediFlow |
| Đợt đánh giá | Review 1 |
| Phạm vi nội dung | Phần A — Xác định vấn đề; Phần B — Đặc tả yêu cầu khách hàng |
| Phiên bản | Chi tiết |
| Ngôn ngữ | Tiếng Việt |
| Đường cơ sở | Commit `1cc014e`, cấu hình triển khai và hợp đồng đã thực hiện tại đường cơ sở đó |
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
| Organization | Tham chiếu tổ chức, nhân viên, vai trò, khoa, cơ sở và phòng | Tham chiếu nhân viên/người dùng, gán vai trò, phân cấp cơ sở, phòng |
| Patient | Đăng ký do nhân viên thực hiện, hồ sơ, định danh và liên hệ | Hồ sơ người bệnh và trạng thái nhân khẩu học |
| Clinical | Lịch khám, medical record, diagnosis, disposition và admission reference; examination cần EXAM clearance khớp hoặc override audit | Appointment, medical record, diagnosis, disposition/reference |
| Laboratory | Vòng đời test do `ADMIN`/`DOCTOR` tạo, mẫu, kết quả, xác minh; xử lý cần LAB_TEST clearance hoặc override audit | Lab test, mẫu, kết quả, sự kiện công bố |
| Pharmacy | Danh mục thuốc, tạo đơn do Pharmacy sở hữu, tồn kho, kiểm tra payment proof bền vững và cấp phát | Đơn, thuốc, biến động kho, hồ sơ cấp phát |
| Billing | Invoice read/create/pay tương thích đang hoạt động cùng ledger/deposit/refund/settlement/clearance mặc định tắt hoặc mục tiêu | Invoice/payment hoạt động và hồ sơ tài chính bị gate rõ ràng |
| Notification | Chọn mẫu, xử lý người nhận/kênh, lần gửi | Yêu cầu và trạng thái gửi thông báo |
| Report | Mô hình đọc từ sự kiện và báo cáo quản trị có lọc | Projection vận hành và tài chính |
| Inpatient | Core V1 hoàn tất cục bộ cho nhập viện, sở hữu/phân giường, đợt hoạt động, điều trị/chuyển và xuất viện; tích hợp sự kiện còn bị khóa | Admission, giường, đợt, treatment entry, trạng thái xuất viện/đóng |
| Surgery | Nền tảng feature-gated và một số route case, pre-op, cancel, read, draft schedule; vòng đời đầy đủ chưa hoạt động | Dữ liệu nền cục bộ bị gate, chưa có luồng outcome đầu-cuối được nghiệm thu |

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
| `ADMIN` | Người dùng, tham chiếu nhân viên, vai trò, khoa, cơ sở, phòng, cấu hình và hồ sơ giường Inpatient được phép | Trạng thái quản trị và dữ liệu tham chiếu | Không tự động có toàn quyền lâm sàng chỉ vì là quản trị viên |
| `DOCTOR` | Medical record/diagnosis Clinical, tạo Lab test trực tiếp, tạo đơn do Pharmacy sở hữu, admission được phép và ngữ cảnh Surgery tương lai | Bệnh sử, kết quả, thuốc và trạng thái cục bộ | Không sửa kho/tài chính hoặc hàm ý Clinical-to-Lab/Surgery integration chưa làm đang hoạt động |
| `NURSE` | Tạo/status patient/appointment được phép, read access và observation trong treatment/bed/transfer Inpatient | Appointment, clinical record, đợt nội trú | Không ghi Clinical record/diagnosis, tạo Lab test/admission, medical discharge, administrative close hoặc duyệt tài chính |
| `PHARMACIST` | Cấp phát và biến động tồn kho | Đơn được duyệt và tồn kho | Không viết lại đơn lâm sàng nguồn |
| `CASHIER` | Tạm ứng, thanh toán, biên lai, hoàn tiền được phép, xác nhận tài chính | Phí, hóa đơn, số dư, yêu cầu clearance | Không sửa kết quả lâm sàng hoặc tạo chỉ định |
| `LAB_TECH` | Start/cancel/status/result/publication cho Lab test đã tồn tại | Test Laboratory và ngữ cảnh patient được phép | Không tạo Lab test hoặc thay đổi ngữ cảnh lâm sàng nguồn |
| `MANAGER` | Tiêu chí báo cáo và góc nhìn quản trị được phép | Thông tin vận hành/tài chính tổng hợp | Báo cáo không được sửa hồ sơ nguồn |
| `PATIENT` | Không có mutation đăng ký, lịch, lâm sàng hoặc tài chính do nhân viên sở hữu | Thông báo thuộc chính mình qua ranh giới quyền Notification đã triển khai | Tự phục vụ hồ sơ, lịch, kết quả, đơn, hóa đơn, biên lai và thanh toán là yêu cầu tương lai, không được trình bày như đã làm |
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
| LR-01A | Tự phục vụ của người bệnh còn hẹp | Quyền `PATIENT` hiện giới hạn ở đọc thông báo thuộc chính mình | Thêm endpoint có quyền rõ ràng cho hồ sơ, lịch, lâm sàng và tài chính |
| LR-02 | Chưa tích hợp bảo hiểm ngoài | Luồng tài chính nội bộ vẫn tường minh | Thêm adapter bảo hiểm, eligibility, claim và reconciliation |
| LR-03 | Chưa tích hợp thanh toán ngoài | Giao dịch nội bộ do thu ngân kiểm soát | Tích hợp provider tuân thủ và đối soát settlement |
| LR-04 | Chưa chứng nhận giao diện thiết bị/lab ngoài | Vòng đời lab nội bộ/thủ công vẫn dùng được | Xác định adapter HL7/FHIR/thiết bị khi khách hàng yêu cầu |
| LR-05 | Chưa chốt nhà cung cấp thông báo | Trạng thái/lần gửi được mô hình nội bộ | Chọn provider, bảo vệ credential, đo SLO gửi |
| LR-06 | Chưa đo năng lực production | Docker hỗ trợ development/demo | Chạy load, soak, failure và recovery test |
| LR-07 | Chưa duyệt SLO/RTO/RPO | Có cơ chế tin cậy cấp ứng dụng | Chốt mục tiêu đo lường và thiết kế triển khai dự phòng |
| LR-08 | Chưa chứng nhận accessibility | Cấu trúc web responsive là nền tảng | Audit theo WCAG mục tiêu và khắc phục |
| LR-09 | Quản trị lâm sàng ngoài mã nguồn | Vai trò/cổng quy trình hạn chế thao tác | Thiết lập chủ chính sách, hội đồng rà soát, quy trình sự cố |
| LR-10 | Chưa xác lập trạng thái pháp quy | Không đưa tuyên bố chứng nhận thiếu căn cứ | Đánh giá pháp lý và tuân thủ theo khu vực triển khai |
| LR-11 | Kích hoạt chéo dịch vụ Inpatient còn bị khóa | API Core V1 cục bộ dùng được, producer/consumer RabbitMQ mặc định tắt | Hoàn tất hợp đồng same-byte và Docker acceptance trước khi kích hoạt |
| LR-12 | Surgery là nền tảng mặc định tắt | Một số route và slice domain/persistence nằm sau business/API flags | Hoàn tất authority provider, lifecycle, event, gateway và nghiệm thu chéo dịch vụ |
| LR-13 | Chưa triển khai Clinical-to-Lab order | `ADMIN`/`DOCTOR` hiện tạo test trực tiếp trong Laboratory | Xác định hợp đồng test-code/order-ID tường minh trước khi tuyên bố bàn giao tự động |
| LR-14 | Các slice tài chính migration bị gate | Invoice read/create/pay tương thích hoạt động; ledger/deposit/refund/settlement/clearance mặc định tắt hoặc chưa hoàn tất | Chỉ bật sau bằng chứng hợp đồng/nghiệm thu riêng |

## Phần B — Đặc tả yêu cầu khách hàng (CRS)

### B1. Quy ước và mức ưu tiên yêu cầu

Các từ **phải**, **nên** và **có thể** biểu thị yêu cầu bắt buộc, khuyến nghị và tùy chọn. Mã yêu cầu là tham chiếu ổn định cho đánh giá và nghiệm thu sau này. Mức ưu tiên dùng `Must`, `Should` hoặc `Could`.

### B2. Chu trình xử lý dữ liệu theo loại người dùng

Bảng tóm tắt yêu cầu được tiếp nối bởi một mục riêng cho từng actor. Quyền hiện tại được phân biệt với yêu cầu khách hàng tương lai.

| Mã | Ưu tiên | Yêu cầu | Dịch vụ sở hữu | Người dùng chính |
|---|---|---|---|---|
| FR-ORG-01 | Must | Hệ thống phải quản lý tham chiếu nhân viên/người dùng và vai trò | Organization | `ADMIN` |
| FR-ORG-02 | Must | Hệ thống phải quản lý khoa, cơ sở và phòng | Organization | `ADMIN` |
| FR-INP-00 | Must | Hệ thống phải quản lý giường do Inpatient sở hữu qua route quản trị được phép | Inpatient | `ADMIN`, `MANAGER` |
| FR-PAT-01 | Must | Hệ thống phải đăng ký người bệnh với UUID ổn định và nhân khẩu học hợp lệ | Patient | `ADMIN`, `NURSE` |
| FR-CLI-00 | Must | Hệ thống phải tạo/theo dõi lịch khám gắn đúng người bệnh | Clinical | `ADMIN`, `NURSE` |
| FR-CLI-01 | Must | Ghi Clinical record/diagnosis cần `ADMIN`/`DOCTOR` và EXAM clearance khớp hoặc emergency override audit trước examination | Clinical | `ADMIN`, `DOCTOR` |
| FR-LAB-00 | Must | `ADMIN`/`DOCTOR` tạo test do Laboratory sở hữu trực tiếp; không hàm ý hợp đồng Clinical-to-Lab hiện tại | Laboratory | `ADMIN`, `DOCTOR` |
| FR-PHA-00 | Must | `ADMIN`/`DOCTOR` tạo đơn do Pharmacy sở hữu qua Pharmacy | Pharmacy | `ADMIN`, `DOCTOR` |
| FR-LAB-01 | Must | Test Laboratory đã có chỉ chuyển trạng thái khi có LAB_TEST clearance khớp hoặc override audit | Laboratory | `ADMIN`, `LAB_TECH` |
| FR-LAB-02 | Must | Hệ thống phải lưu mẫu, kết quả, xác minh và công bố | Laboratory | `LAB_TECH`, `DOCTOR` |
| FR-PHA-01 | Must | Cấp phát phải xác minh đơn Pharmacy, payment proof bền vững và tồn kho | Pharmacy | `ADMIN`, `PHARMACIST` |
| FR-PHA-02 | Must | Hệ thống phải ghi biến động kho và cấp phát một cách nhất quán | Pharmacy | `PHARMACIST` |
| FR-BIL-01 | Must | Invoice read/create/pay tương thích đang hoạt động phải truy vết được | Billing | Nhân viên Billing được phép |
| FR-BIL-02 | Must | Ledger, deposit, refund, settlement và clearance mặc định tắt phải từ chối nếu feature/hợp đồng chưa bật | Billing | `ADMIN`, `CASHIER` khi bật |
| FR-NOT-01 | Must | Hệ thống phải tạo yêu cầu thông báo từ domain event hợp lệ | Notification | `SYSTEM` |
| FR-NOT-02 | Should | Hệ thống nên lưu lần gửi, trạng thái và lý do lỗi | Notification | `SYSTEM`, nhân viên hỗ trợ |
| FR-REP-01 | Must | Hệ thống phải cập nhật projection báo cáo từ sự kiện tin cậy | Report | `SYSTEM` |
| FR-REP-02 | Must | Hệ thống phải cung cấp báo cáo có lọc cho quản lý được phép | Report | `MANAGER` |
| FR-INP-01 | Must | Core V1 cục bộ phải tạo/hủy admission qua route được phép | Inpatient | `ADMIN`, `DOCTOR` |
| FR-INP-02 | Must | Core V1 cục bộ phải quản lý giường và chuyển trạng thái treatment/stay được phép | Inpatient | `ADMIN`, `NURSE`, `DOCTOR` theo từng route |
| FR-INP-03 | Must | Medical discharge và administrative close phải là hai chuyển trạng thái có quyền riêng | Inpatient | `ADMIN`, `DOCTOR` cho medical discharge; `ADMIN`, `CASHIER` cho close |
| FR-SUR-01 | Must | Route Surgery đã làm phải không truy cập được nếu chưa bật đủ business/API gate mặc định tắt và authority dependency | Surgery | Vai trò được phép theo route bị gate |
| FR-SUR-02 | Could | Không được nghiệm thu vòng đời đầy đủ tương lai trước khi hoàn tất route, event, authority provider, gateway policy và hợp đồng chéo dịch vụ | Surgery | Nhân viên phẫu thuật được phép trong tương lai |

#### B2.1 Quản trị viên

**Trách nhiệm:** Quản trị dữ liệu Organization và thực hiện thao tác được cấp quyền rõ ràng ở dịch vụ khác. **Giới hạn:** `ADMIN` không thay thế quyết định lâm sàng; Organization không sở hữu lịch khám hoặc giường.

| Mã yêu cầu | Luồng bình thường | Đầu vào | Xử lý | Đầu ra | Dịch vụ sở hữu | Nhánh thay thế hoặc lỗi |
|---|---|---|---|---|---|---|
| CRS-ADM-01 | Quản lý nhân viên, vai trò, khoa, cơ sở, phòng | Dữ liệu tổ chức được phép | Kiểm tra role, duy nhất, phân cấp | Tham chiếu tổ chức cập nhật | Organization | Dữ liệu sai/trùng bị từ chối, không đổi một phần |
| CRS-ADM-02 | Quản lý danh mục giường khi được phép | Tham chiếu phòng/khoa và dữ liệu giường | Kiểm tra tham chiếu/trạng thái | Hồ sơ giường do Inpatient sở hữu | Inpatient | Quan hệ sai hoặc chuyển trạng thái cấm bị từ chối |

#### B2.2 Bác sĩ

**Trách nhiệm:** Ghi Clinical record với EXAM proof, tạo Lab test trực tiếp, tạo đơn do Pharmacy sở hữu và thao tác Inpatient được phép. **Giới hạn:** Không sửa kho/tài chính hoặc tuyên bố hợp đồng Clinical-to-Lab chưa làm.

| Mã yêu cầu | Luồng bình thường | Đầu vào | Xử lý | Đầu ra | Dịch vụ sở hữu | Nhánh thay thế hoặc lỗi |
|---|---|---|---|---|---|---|
| CRS-DOC-01 | Khám và ghi record | Appointment, phát hiện, EXAM clearance khớp hoặc override audit | Kiểm tra proof/trạng thái/role | Medical record, diagnosis, disposition/reference | Clinical | `ARRIVED` thiếu proof không cho phép examination |
| CRS-DOC-02 | Tạo Lab test trực tiếp | Patient/record, khoa, loại test/ngày | Laboratory kiểm tra và tạo test của mình | Lab test trạng thái đầu | Laboratory | Không suy diễn hợp đồng Clinical-to-Lab |
| CRS-DOC-03 | Tạo đơn Pharmacy | Tham chiếu clinical record, dòng thuốc/hướng dẫn | Pharmacy kiểm tra thuốc/giá server và tạo đơn | Đơn Pharmacy và pending slip | Pharmacy | Dòng rỗng/sai bị từ chối |
| CRS-DOC-04 | Tạo hoặc medical discharge admission | Patient/encounter hoặc admission hoạt động | Áp dụng quy tắc Inpatient theo route | Admission chờ hoặc đã medical discharge | Inpatient | Không suy diễn tích hợp tài chính/sự kiện khi gate đang tắt |

#### B2.3 Điều dưỡng

**Trách nhiệm:** Tạo patient/appointment, cập nhật appointment/check-in, đọc Clinical record và thao tác bed/admit/treatment observation/transfer Inpatient được phép. **Giới hạn:** Không ghi Clinical record/diagnosis, tạo Lab test/admission, medical discharge hoặc administrative close.

| Mã yêu cầu | Luồng bình thường | Đầu vào | Xử lý | Đầu ra | Dịch vụ sở hữu | Nhánh thay thế hoặc lỗi |
|---|---|---|---|---|---|---|
| CRS-NUR-01 | Đăng ký người bệnh | Nhân khẩu học và định danh | Kiểm tra và tạo patient | UUID/hồ sơ người bệnh | Patient | Dữ liệu sai hoặc trùng bị từ chối |
| CRS-NUR-02 | Tạo lịch khám | UUID người bệnh và dữ liệu lịch | Kiểm tra và tạo appointment | UUID/trạng thái lịch | Clinical | Tham chiếu hoặc trạng thái lịch sai bị từ chối |
| CRS-NUR-03 | Phân/chuyển giường, admit tường minh hoặc thêm observation Inpatient | Admission, giường, deposit clearance/override khớp, treatment | Thực thi role/proof/trạng thái route | Admission, giường hoặc lịch sử điều trị cập nhật | Inpatient | Thiếu proof/giường, stale hoặc chuyển cấm bị từ chối |

#### B2.4 Kỹ thuật viên xét nghiệm

**Trách nhiệm:** Start/cancel test đã có, quản lý mẫu, ghi status/result, xác minh và công bố. **Giới hạn:** Không tạo Lab test hoặc thay đổi ngữ cảnh lâm sàng.

| Mã yêu cầu | Luồng bình thường | Đầu vào | Xử lý | Đầu ra | Dịch vụ sở hữu | Nhánh thay thế hoặc lỗi |
|---|---|---|---|---|---|---|
| CRS-LAB-01 | Xử lý/công bố test đã có | Lab test, LAB_TEST clearance khớp hoặc override audit, mẫu, giá trị | Kiểm tra proof, vòng đời và dữ liệu | Kết quả công bố | Laboratory | Thiếu proof, chuyển sai hoặc kết quả thiếu sẽ chặn xử lý/công bố |

#### B2.5 Dược sĩ

**Trách nhiệm:** Xác minh đơn Pharmacy, payment proof bền vững, cấp thuốc và biến động kho. **Giới hạn:** Không viết lại ý định kê đơn do bác sĩ tạo.

| Mã yêu cầu | Luồng bình thường | Đầu vào | Xử lý | Đầu ra | Dịch vụ sở hữu | Nhánh thay thế hoặc lỗi |
|---|---|---|---|---|---|---|
| CRS-PHA-01 | Cấp đơn được phép | Đơn Pharmacy, payment proof bền vững, thuốc, số lượng | Kiểm tra proof/đơn/kho; lưu biến động | Hồ sơ cấp phát và số dư kho | Pharmacy | Thiếu proof, đơn sai hoặc thiếu tồn làm cấp phát bị từ chối |

#### B2.6 Thu ngân

**Trách nhiệm:** Invoice create/pay tương thích và, chỉ khi bật, ledger/deposit/refund/settlement/clearance cùng Inpatient close. **Giới hạn:** Không dùng route mặc định tắt như hành vi phổ quát hoặc medical discharge.

| Mã yêu cầu | Luồng bình thường | Đầu vào | Xử lý | Đầu ra | Dịch vụ sở hữu | Nhánh thay thế hoặc lỗi |
|---|---|---|---|---|---|---|
| CRS-CAS-01 | Tạo/thanh toán invoice tương thích | Ngữ cảnh patient/source và dữ liệu invoice/payment | Kiểm tra trạng thái tương thích hoạt động và lưu | Trạng thái invoice/payment | Billing | Số tiền/trạng thái sai bị từ chối |
| CRS-CAS-02 | Dùng ledger operation opt-in | Feature bật và target payment/deposit/refund/settlement/clearance chính xác | Kiểm tra feature, idempotency, append-only | Hồ sơ tài chính bị gate | Billing | Feature tắt hoặc target sai bị từ chối |
| CRS-CAS-03 | Administrative close admission | Medical discharge cùng settlement khớp hoặc override được duyệt | Kiểm tra Inpatient close gate | Admission đóng | Inpatient | Thiếu settlement/override hoặc admission stale bị từ chối |

#### B2.7 Nhà quản lý

**Trách nhiệm:** Truy vấn báo cáo được phép và quản lý giường Inpatient khi route cho phép. **Giới hạn:** Báo cáo chỉ đọc, không sửa dữ liệu nguồn.

| Mã yêu cầu | Luồng bình thường | Đầu vào | Xử lý | Đầu ra | Dịch vụ sở hữu | Nhánh thay thế hoặc lỗi |
|---|---|---|---|---|---|---|
| CRS-MAN-01 | Truy vấn báo cáo quản trị | Loại báo cáo, bộ lọc, phạm vi quyền | Truy vấn projection từ sự kiện | Báo cáo vận hành/tài chính | Report | Phạm vi trái quyền bị từ chối; độ trễ projection được báo vận hành |
| CRS-MAN-02 | Tạo/cập nhật giường Inpatient | Tham chiếu khoa/phòng và dữ liệu giường | Kiểm tra Manager role, tham chiếu và quy tắc giường | Hồ sơ giường Inpatient | Inpatient | Tham chiếu/trạng thái sai bị từ chối |

#### B2.8 Người bệnh

**Trách nhiệm:** Đọc thông báo thuộc định danh người bệnh đã xác thực. **Giới hạn:** Mã nguồn hiện không cấp `PATIENT` endpoint đăng ký, tạo lịch, kết quả, đơn, hóa đơn, biên lai hoặc thanh toán.

| Mã yêu cầu | Luồng bình thường | Đầu vào | Xử lý | Đầu ra | Dịch vụ sở hữu | Nhánh thay thế hoặc lỗi |
|---|---|---|---|---|---|---|
| CRS-PAT-01 | Đọc thông báo thuộc mình | Định danh xác thực và tiêu chí truy vấn | Thực thi ownership và trả dữ liệu được phép | Danh sách/chi tiết thông báo thuộc mình | Notification | Thông báo của người khác bị từ chối hoặc che giấu |
| CRS-PAT-FUT-01 | Tự phục vụ rộng hơn trong tương lai | Hợp đồng định danh/quan hệ sẽ được duyệt | Chưa triển khai | Không có đầu ra nghiệm thu hiện tại | Dịch vụ tương lai sở hữu | Phải giữ là công việc tương lai |

#### B2.9 Tác nhân hệ thống nội bộ

**Trách nhiệm:** Phát outbox, xử lý idempotent, retry hữu hạn, cập nhật projection và tạo thông báo. **Giới hạn:** Không tự tạo định danh hoặc bỏ qua quyết định dịch vụ sở hữu.

| Mã yêu cầu | Luồng bình thường | Đầu vào | Xử lý | Đầu ra | Dịch vụ sở hữu | Nhánh thay thế hoặc lỗi |
|---|---|---|---|---|---|---|
| CRS-SYS-01 | Phát domain event đã duyệt | Outbox của owner và event/correlation identity | Phát đúng sự kiện đã commit | Bằng chứng broker delivery | Dịch vụ producer | Retry lỗi transport mà không tạo trạng thái nghiệp vụ |
| CRS-SYS-02 | Xử lý notification event | Event/correlation identity và dữ liệu người nhận | Xử lý idempotent và thử gửi | Delivery record | Notification | Retry lỗi tạm thời; lỗi cuối quan sát được |
| CRS-SYS-03 | Xử lý reporting event | Event/correlation identity và projection data | Xử lý idempotent, cập nhật read model | Reporting projection | Report | Retry/dead-letter mà không sửa hồ sơ nguồn |

### B3. Chu trình xử lý dữ liệu theo vai trò

| Quy trình | Đối tượng | Thông tin đầu vào | Xử lý | Thông tin đầu ra | Dịch vụ sở hữu |
|---|---|---|---|---|---|
| Quản trị nền tảng | `ADMIN` | Định danh nhân viên, vai trò, khoa, dữ liệu cơ sở/tài nguyên | Kiểm tra quyền và duy nhất; lưu trạng thái tổ chức | Tham chiếu người dùng/tài nguyên và dữ liệu kiểm toán | Organization |
| Đăng ký người bệnh do nhân viên thực hiện | `ADMIN`, `NURSE` | Nhân khẩu học, liên hệ, định danh, đầu vào đồng thuận | Kiểm tra trường và trùng; cấp UUID | Hồ sơ và xác nhận người bệnh | Patient |
| Quản lý lịch khám | `ADMIN`, `NURSE` | UUID người bệnh, dịch vụ/bác sĩ, thời gian, lý do | Kiểm tra điều kiện lịch; tạo/cập nhật trạng thái | Lịch khám và trạng thái hiển thị | Clinical |
| Khám lâm sàng | `ADMIN`, `DOCTOR` | Patient/appointment, phát hiện, EXAM clearance hoặc override audit | Kiểm tra proof và ghi record/diagnosis | Medical record và disposition/reference | Clinical |
| Tạo Laboratory test | `ADMIN`, `DOCTOR` | Patient/record, khoa, loại test/ngày | Tạo test do Laboratory sở hữu trực tiếp | Lab test trạng thái đầu | Laboratory |
| Xử lý chẩn đoán | `ADMIN`, `LAB_TECH` | Test đã có, LAB_TEST clearance/override, mẫu, giá trị | Thực thi proof và vòng đời | Kết quả xác minh và sự kiện công bố | Laboratory |
| Tạo đơn thuốc | `ADMIN`, `DOCTOR` | Tham chiếu record, dòng thuốc, hướng dẫn | Pharmacy kiểm tra và tạo đơn của mình | Đơn Pharmacy và pending slip | Pharmacy |
| Cấp phát thuốc | `ADMIN`, `PHARMACIST` | Đơn Pharmacy, payment proof, số lượng, tồn | Kiểm tra proof/đơn/kho; lưu biến động | Hồ sơ cấp phát và vị thế kho mới | Pharmacy |
| Invoice create/pay tương thích | Nhân viên Billing được phép | Patient/source và dữ liệu invoice/payment | Áp dụng quy tắc tương thích đang hoạt động | Trạng thái invoice/payment | Billing |
| Tài chính opt-in | `ADMIN`, `CASHIER` khi bật | Target ledger/deposit/refund/settlement/clearance chính xác | Từ chối nếu tắt; nếu bật áp dụng append-only/idempotent | Trạng thái tài chính bị gate | Billing |
| Inpatient Core V1 cục bộ | `ADMIN`, `DOCTOR`, `NURSE` hoặc `CASHIER` theo route | Admission, giường, treatment, medical-discharge hoặc close | Áp dụng role và trạng thái riêng của route | Hồ sơ admission/giường/treatment/discharge | Inpatient |
| Nền tảng Surgery bị gate | Vai trò được phép dưới các cờ mặc định tắt | Đầu vào case/pre-op/cancel/read/draft schedule được chọn | Chỉ chạy slice cục bộ đã làm và được bật | Kết quả cục bộ bị gate; không phải vòng đời đầy đủ | Surgery |
| Báo cáo quản trị | `MANAGER` | Loại báo cáo, thời gian/bộ lọc, phạm vi quyền | Truy vấn projection; tổng hợp/định dạng | Báo cáo vận hành hoặc tài chính | Report |
| Tự động hóa Notification | `SYSTEM` | Sự kiện đã duyệt, message key, dữ liệu người nhận | Xử lý idempotent, chọn mẫu, thử gửi | Trạng thái gửi và bằng chứng vận hành | Notification |
| Tự động hóa Report | `SYSTEM` | Sự kiện đã duyệt, message key, dữ liệu projection | Xử lý idempotent và cập nhật read model | Projection báo cáo cập nhật | Report |

### B4. Luồng đăng ký, lịch khám và lượt khám

| Bước | Đối tượng/nguồn | Đầu vào | Xử lý và quy tắc | Đầu ra/đối tượng nhận |
|---:|---|---|---|---|
| 1 | `ADMIN` hoặc `NURSE` | Nhân khẩu học và liên hệ | Patient kiểm tra dữ liệu bắt buộc và tính duy nhất | UUID và hồ sơ người bệnh |
| 2 | `ADMIN` hoặc `NURSE` | UUID người bệnh, dịch vụ/thời gian | Clinical kiểm tra lịch và ghi trạng thái | UUID/trạng thái lịch khám |
| 3 | Điều dưỡng/tiếp nhận | Lịch và thông tin đến khám | Chuyển lịch qua trạng thái check-in hợp lệ | Ngữ cảnh lịch sẵn sàng |
| 4 | `ADMIN`/`DOCTOR` | Patient/appointment và EXAM clearance khớp hoặc override audit | Clinical xác minh proof/ngữ cảnh và mở medical record | UUID medical record |
| 5 | `ADMIN`/`DOCTOR` | Triệu chứng, phát hiện, ghi chú | Clinical kiểm tra và ghi thông tin y khoa | Record và diagnosis cập nhật |
| 6 | `DOCTOR` | Đánh giá và chẩn đoán | Clinical ghi disposition/reference; command Lab/Pharmacy sau đó đi tới owner tương ứng | Diagnosis và disposition/reference |

### B5. Luồng tạo và trả kết quả Laboratory hiện tại

Chưa có hợp đồng Clinical-to-Lab order. Hợp đồng tương lai cần test code và order ID do producer tạo. Hiện `ADMIN` hoặc `DOCTOR` tạo trực tiếp trong Laboratory.

| Bước | Thông tin đầu vào | Xử lý | Thông tin đầu ra | Kiểm soát |
|---:|---|---|---|---|
| 1 | Tham chiếu patient/record, khoa, loại test/ngày | Laboratory kiểm tra lệnh tạo trực tiếp của `ADMIN`/`DOCTOR` | UUID Lab test và trạng thái đầu | Không suy diễn Clinical order |
| 2 | Test đã có cùng LAB_TEST clearance hoặc override audit | Laboratory kiểm tra proof trước xử lý bảo vệ | Ngữ cảnh xử lý được phép | Proof thiếu/sai chặn start/result/complete |
| 3 | Test được phép và dữ liệu mẫu | `ADMIN`/`LAB_TECH` ghi trạng thái xử lý | Mẫu/status time | Kiểm tra chuyển trạng thái |
| 4 | Giá trị đo, tham chiếu, nhận xét | `ADMIN`/`LAB_TECH` ghi kết quả và kiểm tra đầy đủ | Kết quả nháp/hoàn tất | Trường/đơn vị/khoảng theo định nghĩa |
| 5 | Kết quả hoàn tất | Công bố được phép đổi trạng thái cuối | Kết quả công bố và event | Chỉ sửa theo correction kiểm soát |
| 6 | Sự kiện kết quả | Consumer chỉ xử lý hợp đồng hoạt động đã duyệt idempotent | Notification/projection hoặc kết quả Billing tương thích hoạt động | Consumer bị gate vẫn tắt; retry/dead-letter quan sát được |

### B6. Luồng đơn thuốc đến cấp phát

| Bước | Đối tượng | Đầu vào | Xử lý | Đầu ra |
|---:|---|---|---|---|
| 1 | `ADMIN`, `DOCTOR` | Tham chiếu record, patient, UUID thuốc, liều, tần suất, thời gian, số lượng, hướng dẫn | Pharmacy kiểm tra thuốc/giá server và tạo đơn của mình | Đơn Pharmacy và pending dispense slip |
| 2 | `ADMIN`, `PHARMACIST` | UUID đơn, dòng thuốc, payment proof bền vững | Pharmacy kiểm tra proof, trạng thái đơn và thuốc | Quyết định cấp phát |
| 3 | `ADMIN`, `PHARMACIST` | Số lượng yêu cầu và tồn hiện tại | Bắt buộc số lượng hợp lệ và đủ tồn | Số lượng cấp được duyệt |
| 4 | Giao dịch Pharmacy | Hồ sơ đơn và kho | Lưu nhất quán cấp phát và biến động | Hồ sơ cấp phát và kho giảm |
| 5 | Consumer sự kiện | Sự kiện cấp phát | Billing/Notification/Report xử lý sự kiện hợp lệ idempotent | Cập nhật tài chính/truyền thông/báo cáo |

### B7. Luồng Billing và tài chính

| Bước | Đối tượng/nguồn | Đầu vào | Quy tắc xử lý | Đầu ra |
|---:|---|---|---|---|
| 1 | Yêu cầu tương thích được phép | Patient/source và dữ liệu invoice | Billing áp dụng create/read invoice hoạt động | Invoice tương thích |
| 2 | `CASHIER` | Invoice tương thích và số tiền/phương thức | Kiểm tra và áp dụng pay tương thích hoạt động | Trạng thái invoice đã trả/cập nhật |
| 3 | Ledger feature được bật | Payment-request target chính xác và số tiền | Áp dụng ledger payment append-only/idempotent | Payment/receipt và clearance theo mục đích bị gate |
| 4 | Deposit feature được bật | Admission target và số tiền đề xuất/yêu cầu | Áp dụng deposit, không xem tiền mặt là doanh thu | Deposit/payment-request state bị gate |
| 5 | Refund feature được bật | Giao dịch gốc completed, lý do, số tiền | Thực thi feature/quyền/giới hạn hoàn tích lũy | Refund append-only bị gate |
| 6 | Settlement/clearance feature | Mục đích/target và tổng persisted | Từ chối nếu thiếu/tắt; nếu có áp dụng slice đã duyệt | Settlement hoặc clearance bị gate |

Hồ sơ tài chính dùng `BigDecimal`, không dùng số dấu phẩy động nhị phân. Invoice create/pay tương thích đang hoạt động; ledger, deposit, refund, settlement và clearance không được xem là phổ quát khi flag/hợp đồng còn tắt hoặc thiếu.

### B8. Luồng nội trú

| Bước | Đối tượng | Đầu vào | Xử lý | Đầu ra |
|---:|---|---|---|---|
| 1 | `ADMIN`, `DOCTOR` | Người bệnh/lượt khám, lý do, chi tiết nhập viện | Inpatient xác minh tham chiếu và tạo admission cục bộ | Admission chờ xử lý |
| 2 | `ADMIN`, `NURSE` | Admission và nhu cầu khoa/giường | Inpatient kiểm tra và phân giường phù hợp | Admission cập nhật và active bed assignment |
| 3 | `ADMIN`, `DOCTOR`, `NURSE` | Admission chờ, active bed, deposit clearance khớp hoặc emergency override audit | Gọi `/admit` tường minh | Trạng thái `ADMITTED` |
| 4 | `ADMIN`, `DOCTOR`, `NURSE` | Stay admitted và dữ liệu treatment/observation | Thêm/sửa treatment entry được phép | Lịch sử điều trị |
| 5 | `ADMIN`, `NURSE` | Nhu cầu chuyển và giường đích | Kiểm tra và chuyển có kiểm soát | Lịch sử chuyển/sử dụng cập nhật |
| 6 | `ADMIN`, `DOCTOR` | Quyết định và summary được duyệt | Áp dụng medical-discharge transition | Trạng thái `MEDICALLY_DISCHARGED` |
| 7 | `ADMIN`, `CASHIER` | Medical discharge cùng `settlement.completed` khớp hoặc debt/emergency override được duyệt | Kiểm tra close gate và administrative close | Admission `CLOSED` và giường trống |

### B9. Luồng Surgery mục tiêu và gate hiện tại

Bảng dưới là quy trình khách hàng mục tiêu, không phải nghiệm thu đầu-cuối hiện tại. Các cờ business/API Surgery mặc định `false`. Một số route cục bộ đã có, nhưng referral, readiness, start, complete, outcome event và kích hoạt chéo dịch vụ đầy đủ vẫn còn mở.

| Bước | Đối tượng | Đầu vào | Xử lý | Đầu ra |
|---:|---|---|---|---|
| 1 | Route `DOCTOR` tương lai | Người bệnh, lượt khám, hợp đồng surgery request được duyệt | Creation chỉ chạy khi bật business/creation gate và authority provider thực | Case Surgery cục bộ; chưa có referral chain hoạt động |
| 2 | Nhóm được phép dưới gate | Bằng chứng checklist/consent được duyệt | Route pre-op được chọn chỉ ghi bằng chứng cục bộ khi bật | Bằng chứng pre-op; readiness đầy đủ còn bị gate |
| 3 | Tích hợp Billing tương lai | Tham chiếu Surgery và yêu cầu tài chính | Hợp đồng chưa hoạt động; không được tự tạo clearance | Chưa có đầu ra clearance đầu-cuối hiện tại |
| 4 | Người xếp lịch dưới gate | Case cục bộ, phòng/thời gian/nhóm, revision dự kiến | Thay draft schedule kiểm tra dependency cục bộ/authority | Draft schedule; chưa phải lịch vòng đời finalized |
| 5 | Route lifecycle tương lai | Thủ thuật đã xếp và thông tin trong mổ | Start/complete còn mở | Chưa có execution record được nghiệm thu |
| 6 | Route lifecycle/event tương lai | Phát hiện và thông tin hậu phẫu | Công bố outcome còn mở | Chưa có outcome event được nghiệm thu |

### B10. Luồng Notification và Reporting

| Consumer | Đầu vào kích hoạt | Xử lý | Đầu ra | Hành vi khi lỗi |
|---|---|---|---|---|
| Notification | Domain event hợp lệ, người nhận, dữ liệu mẫu | Chọn mẫu/kênh, tạo yêu cầu, thử gửi | Nội dung, trạng thái, thời gian, lý do lỗi | Retry hữu hạn lỗi tạm thời; lộ lỗi cuối |
| Report | Domain event tin cậy và dữ liệu tương quan | Áp dụng idempotent vào read model | Projection và chỉ số cập nhật | Retry an toàn; không sửa nguồn |

Notification và Report là consumer hạ nguồn, không trở thành nguồn có thẩm quyền cho hồ sơ lâm sàng, tài chính, dược, xét nghiệm, nội trú hoặc phẫu thuật.

### B10A. Mười hợp đồng quy trình liên khoa

#### Quy trình 1 — Nhân viên đăng ký và tạo lịch

- **Kích hoạt:** `ADMIN` hoặc `NURSE` nhận dữ liệu người bệnh và lịch hợp lệ.
- **Thẩm quyền chính:** Patient sở hữu đăng ký; Clinical sở hữu riêng appointment.
- **Đầu vào:** Nhân khẩu học, định danh, UUID người bệnh, dịch vụ/bác sĩ, thời gian.
- **Xử lý:** Tạo hồ sơ Patient rồi gọi biên appointment Clinical; không hàm ý shared transaction.
- **Đầu ra:** UUID người bệnh và UUID/trạng thái appointment.
- **Lỗi/ngoại lệ:** Mỗi owner từ chối đầu vào sai độc lập; `PATIENT` chưa được quyền tự phục vụ.

#### Quy trình 2 — Appointment đến lượt khám

- **Kích hoạt:** Quy trình nhân viên được phép đạt trạng thái tư vấn hợp lệ.
- **Thẩm quyền chính:** Clinical.
- **Đầu vào:** Appointment do Clinical sở hữu và tham chiếu UUID người bệnh.
- **Xử lý:** Kiểm tra appointment/actor rồi tạo hoặc cập nhật encounter.
- **Đầu ra:** Encounter, quan sát, chẩn đoán và chỉ định tiếp theo được phép.
- **Lỗi/ngoại lệ:** Clinical từ chối trạng thái, role hoặc tham chiếu sai.

#### Quy trình 3 — Tạo trực tiếp Laboratory test và kết quả

- **Kích hoạt:** `ADMIN` hoặc `DOCTOR` tạo trực tiếp test trong Laboratory.
- **Thẩm quyền chính:** Laboratory; chưa có hợp đồng Clinical-to-Lab order.
- **Đầu vào:** Tham chiếu patient/record, khoa, loại test/ngày, sau đó clearance/override, mẫu, giá trị.
- **Xử lý:** Laboratory tạo test; xử lý bảo vệ cần LAB_TEST clearance khớp hoặc override audit trước khi ghi/công bố.
- **Đầu ra:** Kết quả Laboratory công bố và sự kiện đủ điều kiện.
- **Lỗi/ngoại lệ:** Thiếu proof, kết quả thiếu hoặc trạng thái sai chặn xử lý/công bố.

#### Quy trình 4 — Kết quả Laboratory đến khoản phí Billing

- **Kích hoạt:** Sự kiện xét nghiệm tính phí đủ điều kiện được duyệt và phát.
- **Thẩm quyền chính:** Billing sở hữu phí; Laboratory sở hữu kết quả nguồn.
- **Đầu vào:** Event ID, tham chiếu order/result, patient, item và hợp đồng số tiền thập phân.
- **Xử lý:** Billing chỉ xử lý idempotent hợp đồng tương thích đang hoạt động hoặc opt-in đã bật/duyệt.
- **Đầu ra:** Kết quả invoice tương thích hoặc trạng thái ledger charge bị gate rõ ràng.
- **Lỗi/ngoại lệ:** Consumer mặc định tắt vẫn không hoạt động; delivery trùng là no-op khi bật.

#### Quy trình 5 — Đơn thuốc đến cấp phát

- **Kích hoạt:** `ADMIN` hoặc `DOCTOR` tạo đơn qua Pharmacy.
- **Thẩm quyền chính:** Pharmacy sở hữu đơn, cấp phát và kho; Clinical chỉ cung cấp record context/reference.
- **Đầu vào:** Tham chiếu record/patient, dòng thuốc/hướng dẫn, sau đó payment proof bền vững, số lượng, tồn.
- **Xử lý:** Pharmacy tạo đơn dùng giá server; cấp phát kiểm tra riêng payment proof và tồn trước biến động nhất quán.
- **Đầu ra:** Hồ sơ cấp phát và tồn kho cập nhật.
- **Lỗi/ngoại lệ:** Đơn sai hoặc thiếu tồn ngăn cấp phát.

#### Quy trình 6 — Hóa đơn và giao dịch tài chính

- **Kích hoạt:** Sự kiện tính phí hợp lệ hoặc yêu cầu thu ngân được phép.
- **Thẩm quyền chính:** Billing.
- **Đầu vào:** Invoice/payment tương thích hoặc target ledger/deposit/refund/settlement/clearance đã bật chính xác.
- **Xử lý:** Áp dụng invoice create/pay hoạt động; từ chối thao tác bị gate khi feature/hợp đồng tắt.
- **Đầu ra:** Trạng thái invoice/payment hoặc hồ sơ tài chính bị gate đã bật rõ ràng.
- **Lỗi/ngoại lệ:** Feature tắt/target sai bị từ chối; `PATIENT` chưa có payment endpoint.

#### Quy trình 7 — Nhập viện và xuất viện Inpatient cục bộ

- **Kích hoạt:** Command Inpatient cục bộ được phép được gửi.
- **Thẩm quyền chính:** Inpatient.
- **Đầu vào:** Admission, active bed, deposit clearance/override khớp, treatment, discharge summary, settlement/close override.
- **Xử lý:** Áp dụng role: create/assign, `/admit` tường minh, treatment, medical discharge, rồi administrative close có settlement gate.
- **Đầu ra:** Trạng thái requested/admitted/treatment/medical-discharge/closed cục bộ.
- **Lỗi/ngoại lệ:** Thiếu bed/clearance/settlement, stale hoặc cấm bị từ chối; không suy diễn event delivery đang tắt.

#### Quy trình 8 — Vòng đời Surgery mục tiêu

- **Kích hoạt:** Hợp đồng Surgery request và authority tương lai được duyệt.
- **Thẩm quyền chính:** Surgery cho case cục bộ; owner nguồn giữ bằng chứng của mình.
- **Đầu vào:** Yêu cầu, pre-op, clearance, tài nguyên lịch, dữ liệu kết quả đã duyệt.
- **Xử lý:** Hiện chỉ có một số slice cục bộ mặc định tắt; start/complete/outcome đầy đủ còn mở.
- **Đầu ra:** Nghiệm thu hiện tại giới hạn ở slice cục bộ được bật rõ ràng, không phải outcome lifecycle.
- **Lỗi/ngoại lệ:** Flag tắt từ chối truy cập; không được bỏ qua authority/hợp đồng thiếu.

#### Quy trình 9 — Notification theo sự kiện

- **Kích hoạt:** Domain event đủ điều kiện đã duyệt đến Notification.
- **Thẩm quyền chính:** Notification cho delivery record; producer giữ thẩm quyền hồ sơ nghiệp vụ.
- **Đầu vào:** Event identity, người nhận, channel/template data.
- **Xử lý:** Xử lý idempotent, render, thử gửi và ghi trạng thái.
- **Đầu ra:** Nội dung/trạng thái gửi; `PATIENT` có thể đọc thông báo thuộc mình.
- **Lỗi/ngoại lệ:** Lỗi tạm thời retry; lỗi cuối quan sát được mà không rollback giao dịch nguồn.

#### Quy trình 10 — Report theo sự kiện

- **Kích hoạt:** Domain event đủ điều kiện đã duyệt đến Report.
- **Thẩm quyền chính:** Report chỉ cho projection của mình.
- **Đầu vào:** Event/correlation identity và trường projection.
- **Xử lý:** Xử lý idempotent và cập nhật read model.
- **Đầu ra:** Dữ liệu báo cáo vận hành/tài chính được phép.
- **Lỗi/ngoại lệ:** Lỗi projection retry/dead-letter; Report không sửa hồ sơ nguồn.

### B11. Quyền sở hữu dữ liệu và yêu cầu thông tin

| Thông tin | Nguồn sự thật | Cách chia sẻ | Kiểm soát chính |
|---|---|---|---|
| Nhân viên, vai trò, khoa, cơ sở và phòng | Organization | REST/sự kiện theo định nghĩa | UUID, quyền, trạng thái hoạt động |
| Giường và tình trạng sử dụng | Inpatient | API/hợp đồng cục bộ có kiểm soát | UUID, khả dụng, quyền chuyển trạng thái |
| Hồ sơ người bệnh | Patient | Hợp đồng REST/sự kiện được phép | Khớp định danh, riêng tư, UUID ổn định |
| Lịch khám | Clinical | Hợp đồng REST/sự kiện được phép | UUID người bệnh, trạng thái lịch, quyền |
| Medical record, diagnosis, disposition/reference | Clinical | Hợp đồng/sự kiện lâm sàng kiểm soát | `ADMIN`/`DOCTOR`, EXAM proof, trạng thái |
| Lab test, mẫu và kết quả | Laboratory | API create trực tiếp và result event | `ADMIN`/`DOCTOR` tạo; clearance/override; trạng thái kết quả |
| Đơn, tồn kho và cấp phát | Pharmacy | Hợp đồng đơn/cấp phát/kho | Doctor actor, Pharmacy ownership, payment proof, biến động nhất quán |
| Phí, hóa đơn, giao dịch, biên lai, hoàn tiền, clearance | Billing | Hợp đồng/sự kiện trạng thái tài chính | Tiền thập phân, idempotency, kiểm toán |
| Gửi thông báo | Notification | Chỉ truy vấn vận hành | Riêng tư người nhận, trạng thái, bằng chứng retry |
| Projection báo cáo | Report | Truy vấn quản lý được phép | Chỉ đọc, idempotency sự kiện |
| Nhập viện, đợt, giường, xuất viện | Inpatient | Tham chiếu/sự kiện kiểm soát | Cổng clearance và chuyển trạng thái |
| Case/pre-op/draft schedule Surgery bị gate | Surgery | Hợp đồng cục bộ được chọn và bị gate | Cờ mặc định tắt, authority dependency, không nghiệm thu vòng đời đầy đủ |

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
| Phát triển/Trình diễn | 8 lõi logic | 16 GB | SSD trống 40 GB | Hạ tầng container và workload chọn lọc |
| Triển khai nhỏ tối thiểu | 16 lõi logic | 32 GB | SSD 150 GB | Cấu hình lập kế hoạch cho dịch vụ đồng thời, test, dữ liệu mẫu, giám sát cơ bản |
| Triển khai mở rộng khuyến nghị | 24+ lõi logic trên nhiều host | 64+ GB trên nhiều host | SSD chịu lỗi từ 500 GB và backup tách biệt | Chỉ là cấu hình lập kế hoạch; định cỡ lại sau test tải, lưu giữ, sẵn sàng, khôi phục |

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

Đường cơ sở Maven tổng đã ghi nhận là **3.612 test, 0 failure, 0 error và 0 skip**. Đây là bằng chứng kỹ thuật hỗ trợ từ lần chạy đã ghi nhận, không chứng minh khách hàng đã nghiệm thu mọi quy trình hoặc tích hợp mặc định tắt đang hoạt động.

| Mã yêu cầu | Actor hoặc quy trình | Dịch vụ sở hữu | Khu vực bằng chứng nguồn | Phương pháp nghiệm thu |
|---|---|---|---|---|
| AC-01A / FR-PAT-01 | Đăng ký do nhân viên thực hiện | Patient | Controller/tài liệu Patient | Trình diễn `ADMIN`/`NURSE` đăng ký |
| AC-01B / FR-CLI-00 | Tạo appointment | Clinical | Controller/tài liệu appointment Clinical | Trình diễn `ADMIN`/`NURSE` tạo lịch |
| AC-02 / FR-CLI-01 | Lượt khám lâm sàng | Clinical | Controller/domain test Clinical | Trình diễn lượt khám/chẩn đoán được phép |
| AC-03 / FR-LAB-00..01 | Tạo trực tiếp và vòng đời Lab có proof | Laboratory | Controller/quy tắc/test Lab | Tạo bằng `ADMIN`/`DOCTOR`; cần clearance/override; không tuyên bố Clinical order |
| AC-04 / FR-PHA-00..01 | Đơn Pharmacy và cấp phát | Pharmacy | Controller/payment-proof/inventory test Pharmacy | Tạo qua Pharmacy; cần payment proof và tồn trước cấp |
| AC-05 / FR-BIL-01 | Invoice tương thích | Billing | API/test invoice hiện tại | Trình diễn invoice read/create/pay hoạt động |
| AC-06 / FR-BIL-02 | Finance slice opt-in | Billing | Cấu hình feature và slice test Billing | Xác minh mặc định tắt; chỉ test slice được bật/nghiệm thu |
| AC-07 / FR-INP-01..03 | Inpatient Core V1 cục bộ | Inpatient | Controller và tài liệu trạng thái Inpatient | Trình diễn trạng thái cục bộ theo route; xác minh event flag tắt |
| AC-08 / FR-SUR-01..02 | Nền tảng Surgery bị gate | Surgery | Cấu hình, tài liệu trạng thái, gated-route test | Xác minh mặc định tắt và chỉ slice cục bộ được bật; loại vòng đời đầy đủ khỏi nghiệm thu |
| AC-09 / FR-NOT-01..02 | Gửi thông báo | Notification | Controller/consumer test Notification | Sinh/theo dõi thông báo hợp lệ và quyền sở hữu patient |
| AC-10 / FR-REP-01..02 | Projection báo cáo | Report | Consumer/query test Report | Cập nhật/truy vấn projection mà không sửa nguồn |
| AC-11 / SEC-01..03 | Thao tác bảo vệ | Từng dịch vụ sở hữu | Web test `@PreAuthorize` | Từ chối role trái quyền, gồm thao tác `PATIENT` chưa hỗ trợ |
| AC-12 / API-03, API-06 | Ranh giới dịch vụ | Từng dịch vụ sở hữu | Quy tắc kiến trúc và contract test | Xác nhận DTO/UUID và không truy cập chéo cơ sở dữ liệu |
| AC-13 / EVT-04 | Phân phối lặp | Dịch vụ consumer | Idempotency test | Lặp thông điệp không lặp tác động |
| AC-14 / EVT-05..06 | Xử lý lỗi | Dịch vụ consumer | Cấu hình/test retry/DLQ | Quan sát retry hữu hạn và đường lỗi cuối |
| AC-15 / OBJ-08 | Đường cơ sở kỹ thuật | Kho mã multi-module | Báo cáo Maven tổng đã ghi nhận | Xác nhận baseline 3.612/0/0/0, sau đó chạy suite hiện tại đã thống nhất |

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
