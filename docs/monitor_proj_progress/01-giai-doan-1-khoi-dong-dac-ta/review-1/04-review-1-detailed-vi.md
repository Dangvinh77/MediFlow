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

MediFlow được thiết kế như một nền tảng quy trình bệnh viện kết nối các khu vực này nhưng vẫn duy trì quyền sở hữu dữ liệu rõ ràng. Kho mã hiện tại triển khai frontend trên trình duyệt và backend microservice Spring Boot. Hệ thống hoàn chỉnh gồm mười dịch vụ nghiệp vụ, được hỗ trợ bởi API gateway, khám phá dịch vụ Eureka, cơ sở dữ liệu PostgreSQL và messaging RabbitMQ.

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
| Clinical | Appointment, medical record, diagnosis, disposition và admission reference; examination cần EXAM clearance khớp hoặc override audit | Appointment, medical record, diagnosis, disposition/reference |
| Laboratory | Vòng đời test được tiếp nhận từ yêu cầu Clinical tường minh, mẫu, kết quả, xác minh và công bố; xử lý bảo vệ cần LAB_TEST clearance khớp hoặc override audit | Lab test, mẫu, kết quả, sự kiện công bố |
| Pharmacy | Danh mục thuốc, tạo đơn do Pharmacy sở hữu, tồn kho, kiểm tra payment proof bền vững và cấp phát | Đơn, thuốc, biến động kho, hồ sơ cấp phát |
| Billing | Charge, invoice, deposit, payment, receipt, refund, settlement và service clearance theo mục đích | Hồ sơ charge, invoice, payment, refund, settlement và clearance kiểm toán được |
| Notification | Chọn mẫu, xử lý người nhận/kênh, lần gửi | Yêu cầu và trạng thái gửi thông báo |
| Report | Mô hình đọc từ sự kiện và báo cáo quản trị có lọc | Projection vận hành và tài chính |
| Inpatient | Admission request, financial clearance, sở hữu/phân giường, active stay, treatment/transfer, medical discharge, settlement và administrative close | Admission, giường, đợt, treatment entry, trạng thái xuất viện/đóng |
| Surgery | Request, readiness, financial clearance, scheduling, execution, recovery, outcome và charge handoff | Surgery case, bằng chứng readiness, schedule, procedure, recovery, outcome và charge event |

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
| `DOCTOR` | Medical record/diagnosis Clinical, Laboratory test request, tạo đơn do Pharmacy sở hữu, admission và Surgery request | Bệnh sử, kết quả, thuốc, admission, Surgery readiness và outcome | Không sửa kho hoặc tài chính đã quyết toán; request phải dùng hợp đồng của dịch vụ sở hữu |
| `NURSE` | Tạo/status patient/appointment được phép, read access và observation trong treatment/bed/transfer Inpatient | Appointment, clinical record, đợt nội trú | Không ghi Clinical record/diagnosis, tạo Lab test/admission, medical discharge, administrative close hoặc duyệt tài chính |
| `PHARMACIST` | Cấp phát và biến động tồn kho | Đơn được duyệt và tồn kho | Không viết lại đơn lâm sàng nguồn |
| `CASHIER` | Tạm ứng, thanh toán, biên lai, hoàn tiền được phép, xác nhận tài chính | Phí, hóa đơn, số dư, yêu cầu clearance | Không sửa kết quả lâm sàng hoặc tạo chỉ định |
| `LAB_TECH` | Start/cancel/status/result/publication cho Lab test đã tồn tại | Test Laboratory và ngữ cảnh patient được phép | Không tạo Lab test hoặc thay đổi ngữ cảnh lâm sàng nguồn |
| `MANAGER` | Tiêu chí báo cáo và góc nhìn quản trị được phép | Thông tin vận hành/tài chính tổng hợp | Báo cáo không được sửa hồ sơ nguồn |
| `PATIENT` | Tự phục vụ hồ sơ, appointment, result, prescription, invoice, receipt, payment và notification được phép | Chỉ dữ liệu thuộc người bệnh hoặc được chia sẻ tường minh | Không truy cập dữ liệu người bệnh khác hoặc thực hiện thao tác lâm sàng/tài chính dành cho nhân viên |
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

### A10. Giới hạn, rủi ro và công việc còn lại

| Mã | Giới hạn/rủi ro | Giảm thiểu hiện tại | Phần cần hoàn tất |
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
| LR-11 | Tích hợp liên khoa Inpatient chưa hoàn tất | Có thể trình diễn độc lập các thao tác admission, giường, treatment và discharge cốt lõi | Hoàn tất và kiểm chứng bàn giao với tài chính, Pharmacy, Surgery, Report và Notification |
| LR-12 | Vòng đời Surgery chưa hoàn tất | Một số thao tác request, chuẩn bị, cancel, read và schedule tạo nền tảng một phần | Hoàn tất readiness, clearance, execution, recovery, outcome, charge handoff và nghiệm thu đầu-cuối |
| LR-13 | Chuyển tự động Clinical-to-Lab order chưa hoàn tất | `ADMIN`/`DOCTOR` hiện tạo Lab test trực tiếp trong Laboratory | Hoàn tất bàn giao test-code/order-ID tường minh và kiểm chứng đầu-cuối |
| LR-14 | Vòng đời tài chính chưa hoàn tất | Có thể trình diễn invoice và payment cơ bản | Hoàn tất deposit, refund, settlement, service clearance, bàn giao luồng chăm sóc và bằng chứng nghiệm thu |

## Phần B — Đặc tả yêu cầu khách hàng (CRS)

### B1. Quy ước và mức ưu tiên yêu cầu

Các từ **phải**, **nên** và **có thể** biểu thị yêu cầu bắt buộc, khuyến nghị và tùy chọn. Mã yêu cầu là tham chiếu ổn định cho đánh giá và nghiệm thu sau này. Mức ưu tiên dùng `Must`, `Should` hoặc `Could`.

### B2. Chu trình xử lý dữ liệu theo loại người dùng

Bảng tóm tắt yêu cầu được tiếp nối bởi một mục riêng cho từng actor và mô tả hành vi đầy đủ của hệ thống đích. Khoảng trống Review 1 được ghi riêng trong phần giới hạn và bằng chứng nghiệm thu.

| Mã | Ưu tiên | Yêu cầu | Dịch vụ sở hữu | Người dùng chính |
|---|---|---|---|---|
| FR-ORG-01 | Must | Hệ thống phải quản lý tham chiếu nhân viên/người dùng và vai trò | Organization | `ADMIN` |
| FR-ORG-02 | Must | Hệ thống phải quản lý khoa, cơ sở và phòng | Organization | `ADMIN` |
| FR-INP-00 | Must | Hệ thống phải quản lý giường do Inpatient sở hữu qua route quản trị được phép | Inpatient | `ADMIN`, `MANAGER` |
| FR-PAT-01 | Must | Hệ thống phải đăng ký người bệnh với UUID ổn định và nhân khẩu học hợp lệ | Patient | `ADMIN`, `NURSE` |
| FR-CLI-00 | Must | Hệ thống phải tạo/theo dõi lịch khám gắn đúng người bệnh | Clinical | `ADMIN`, `NURSE` |
| FR-CLI-01 | Must | Ghi Clinical record/diagnosis phải cần `ADMIN`/`DOCTOR`, trạng thái encounter hợp lệ và EXAM clearance khớp hoặc emergency override audit | Clinical | `ADMIN`, `DOCTOR` |
| FR-LAB-00 | Must | `ADMIN`/`DOCTOR` gửi Clinical test request tường minh với test code và order ID do producer tạo; Laboratory kiểm tra request và tạo test của mình | Laboratory | `ADMIN`, `DOCTOR` |
| FR-PHA-00 | Must | `ADMIN`/`DOCTOR` tạo đơn do Pharmacy sở hữu qua Pharmacy | Pharmacy | `ADMIN`, `DOCTOR` |
| FR-LAB-01 | Must | Xử lý Laboratory phải thực thi quy tắc vòng đời và cần LAB_TEST clearance khớp hoặc override audit | Laboratory | `ADMIN`, `LAB_TECH` |
| FR-LAB-02 | Must | Hệ thống phải lưu mẫu, kết quả, xác minh và công bố; bác sĩ chỉ đọc kết quả, không thực hiện thao tác ghi/công bố | Laboratory | `ADMIN`, `LAB_TECH` |
| FR-PHA-01 | Must | Cấp phát phải xác minh đơn Pharmacy, payment proof bền vững và tồn kho | Pharmacy | `ADMIN`, `PHARMACIST` |
| FR-PHA-02 | Must | Hệ thống phải ghi biến động kho và cấp phát một cách nhất quán | Pharmacy | `PHARMACIST` |
| FR-BIL-01 | Must | Charge, invoice, payment và receipt phải truy vết được và dùng trạng thái tài chính kiểm toán được | Billing | Nhân viên Billing được phép |
| FR-BIL-02 | Must | Deposit, refund, settlement và service clearance phải được phân quyền, idempotent, append-only khi cần và gắn đúng mục đích nghiệp vụ | Billing | `ADMIN`, `CASHIER` |
| FR-NOT-01 | Must | Hệ thống phải tạo yêu cầu thông báo từ domain event hợp lệ | Notification | `SYSTEM` |
| FR-NOT-02 | Should | Hệ thống nên lưu lần gửi, trạng thái và lý do lỗi | Notification | `SYSTEM`, nhân viên hỗ trợ |
| FR-REP-01 | Must | Hệ thống phải cập nhật projection báo cáo từ sự kiện tin cậy | Report | `SYSTEM` |
| FR-REP-02 | Must | Hệ thống phải cung cấp báo cáo có lọc cho quản lý được phép | Report | `MANAGER` |
| FR-INP-01 | Must | Hệ thống phải tạo/hủy admission qua thao tác được phân quyền | Inpatient | `ADMIN`, `DOCTOR` |
| FR-INP-02 | Must | Hệ thống phải quản lý giường, active stay, treatment, transfer và chuyển trạng thái discharge được phép | Inpatient | `ADMIN`, `NURSE`, `DOCTOR` theo từng thao tác |
| FR-INP-03 | Must | Medical discharge và administrative close phải là hai chuyển trạng thái có quyền riêng | Inpatient | `ADMIN`, `DOCTOR` cho medical discharge; `ADMIN`, `CASHIER` cho close |
| FR-SUR-01 | Must | Surgery request, readiness, financial clearance, scheduling, execution, recovery, outcome và cancellation phải tuân theo chuyển trạng thái được phân quyền | Surgery | Nhân viên Surgery được phép |
| FR-SUR-02 | Must | Surgery case hoàn tất hoặc hủy phải phát outcome và charge handoff cần thiết mà không vượt quyền Clinical/Billing | Surgery | Nhân viên Surgery được phép, `SYSTEM` |

#### B2.1 Quản trị viên

**Trách nhiệm:** Quản trị dữ liệu Organization và thực hiện thao tác được cấp quyền rõ ràng ở dịch vụ khác. **Giới hạn:** `ADMIN` không thay thế quyết định lâm sàng; Organization không sở hữu lịch khám hoặc giường.

| Mã yêu cầu | Luồng và xử lý | Đầu vào | Đầu ra | Dịch vụ sở hữu | Nhánh thay thế hoặc lỗi |
|---|---|---|---|---|---|
| CRS-ADM-01 | Quản lý nhân viên, vai trò, khoa, cơ sở, phòng; kiểm tra role, duy nhất và phân cấp | Dữ liệu tổ chức được phép | Tham chiếu tổ chức cập nhật | Organization | Dữ liệu sai/trùng bị từ chối, không đổi một phần |
| CRS-ADM-02 | Quản lý danh mục giường khi được phép; kiểm tra tham chiếu và trạng thái | Tham chiếu phòng/khoa và dữ liệu giường | Hồ sơ giường do Inpatient sở hữu | Inpatient | Quan hệ sai hoặc chuyển trạng thái cấm bị từ chối |

#### B2.2 Bác sĩ

**Trách nhiệm:** Ghi Clinical record với EXAM proof, gửi Laboratory test request, tạo đơn do Pharmacy sở hữu và thao tác Inpatient/Surgery được phép. **Giới hạn:** Không sửa kho hoặc tài chính đã quyết toán và phải dùng hợp đồng của từng dịch vụ sở hữu.

| Mã yêu cầu | Luồng và xử lý | Đầu vào | Đầu ra | Dịch vụ sở hữu | Nhánh thay thế hoặc lỗi |
|---|---|---|---|---|---|
| CRS-DOC-01 | Khám và ghi record; kiểm tra trạng thái, role và proof | Appointment, phát hiện, EXAM clearance khớp hoặc override audit | Medical record, diagnosis, disposition/reference | Clinical | Proof thiếu/sai hoặc trạng thái encounter không hợp lệ sẽ chặn examination |
| CRS-DOC-02 | Gửi Laboratory test request; kiểm tra quyền lâm sàng, sau đó Laboratory kiểm tra order và tạo test của mình | Patient/record, khoa, test code/ngày, order ID do producer tạo | Lab test được tiếp nhận ở trạng thái đầu | Laboratory | Order identity sai/trùng hoặc ngữ cảnh không được phép bị từ chối |
| CRS-DOC-03 | Tạo đơn Pharmacy; Pharmacy kiểm tra thuốc/giá server và tạo đơn | Tham chiếu clinical record, dòng thuốc/hướng dẫn | Đơn Pharmacy và pending slip | Pharmacy | Dòng rỗng/sai bị từ chối |
| CRS-DOC-04 | Tạo hoặc medical discharge admission; áp dụng quy tắc Inpatient theo route | Patient/encounter hoặc admission hoạt động | Admission chờ hoặc đã medical discharge | Inpatient | Thiếu điều kiện admission hoặc chuyển trạng thái bị cấm sẽ bị từ chối |

#### B2.3 Điều dưỡng

**Trách nhiệm:** Tạo patient/appointment, cập nhật appointment/check-in, đọc Clinical record và thao tác bed/admit/treatment observation/transfer Inpatient được phép. **Giới hạn:** Không ghi Clinical record/diagnosis, tạo Lab test/admission, medical discharge hoặc administrative close.

| Mã yêu cầu | Luồng và xử lý | Đầu vào | Đầu ra | Dịch vụ sở hữu | Nhánh thay thế hoặc lỗi |
|---|---|---|---|---|---|
| CRS-NUR-01 | Đăng ký người bệnh; kiểm tra và tạo patient | Nhân khẩu học và định danh | UUID/hồ sơ người bệnh | Patient | Dữ liệu sai hoặc trùng bị từ chối |
| CRS-NUR-02 | Tạo lịch khám; kiểm tra và tạo appointment | UUID người bệnh và dữ liệu lịch | UUID/trạng thái lịch | Clinical | Tham chiếu hoặc trạng thái lịch sai bị từ chối |
| CRS-NUR-03 | Phân/chuyển giường, admit tường minh hoặc thêm observation Inpatient; thực thi role, proof và trạng thái route | Admission, giường, deposit clearance/override khớp, treatment | Admission, giường hoặc lịch sử điều trị cập nhật | Inpatient | Thiếu proof/giường, stale hoặc chuyển cấm bị từ chối |

#### B2.4 Kỹ thuật viên xét nghiệm

**Trách nhiệm:** Start/cancel test đã có, quản lý mẫu, ghi status/result, xác minh và công bố. **Giới hạn:** Không tạo Lab test hoặc thay đổi ngữ cảnh lâm sàng.

| Mã yêu cầu | Luồng và xử lý | Đầu vào | Đầu ra | Dịch vụ sở hữu | Nhánh thay thế hoặc lỗi |
|---|---|---|---|---|---|
| CRS-LAB-01 | Xử lý/công bố test đã có; kiểm tra vòng đời, proof và dữ liệu kết quả bắt buộc | Lab test, mẫu, giá trị, LAB_TEST clearance khớp hoặc override audit | Kết quả công bố | Laboratory | Sai trạng thái/dữ liệu hoặc proof thiếu/sai sẽ chặn xử lý/công bố |

#### B2.5 Dược sĩ

**Trách nhiệm:** Xác minh đơn Pharmacy, payment proof bền vững, cấp thuốc và biến động kho. **Giới hạn:** Không viết lại ý định kê đơn do bác sĩ tạo.

| Mã yêu cầu | Luồng và xử lý | Đầu vào | Đầu ra | Dịch vụ sở hữu | Nhánh thay thế hoặc lỗi |
|---|---|---|---|---|---|
| CRS-PHA-01 | Cấp đơn được phép; kiểm tra proof/đơn/kho và lưu biến động | Đơn Pharmacy, payment proof bền vững, thuốc, số lượng | Hồ sơ cấp phát và số dư kho | Pharmacy | Thiếu proof, đơn sai hoặc thiếu tồn làm cấp phát bị từ chối |

#### B2.6 Thu ngân

**Trách nhiệm:** Xử lý charge, invoice, deposit, payment, receipt, refund được duyệt, settlement, service clearance và administrative close Inpatient được phép. **Giới hạn:** Không thực hiện medical discharge hoặc thay đổi quyết định lâm sàng.

| Mã yêu cầu | Luồng và xử lý | Đầu vào | Đầu ra | Dịch vụ sở hữu | Nhánh thay thế hoặc lỗi |
|---|---|---|---|---|---|
| CRS-CAS-01 | Tạo và thanh toán invoice; kiểm tra trạng thái tài chính và lưu payment/receipt | Ngữ cảnh patient/source, charge, invoice và payment | Trạng thái invoice, payment và receipt | Billing | Số tiền, quyền hoặc trạng thái sai bị từ chối |
| CRS-CAS-02 | Xử lý deposit/refund/settlement/clearance; kiểm tra quyền, idempotency, append-only, tổng và mục đích | Mục đích nghiệp vụ, giao dịch gốc khi cần, số tiền và tham chiếu chăm sóc/admission | Hồ sơ deposit, refund, settlement hoặc clearance | Billing | Mục đích, target, số tiền, giới hạn tích lũy hoặc quyền sai bị từ chối |
| CRS-CAS-03 | Administrative close admission; kiểm tra điều kiện đóng Inpatient | Medical discharge cùng settlement khớp hoặc override được duyệt | Admission đóng | Inpatient | Thiếu settlement/override hoặc admission stale bị từ chối |

#### B2.7 Nhà quản lý

**Trách nhiệm:** Truy vấn báo cáo được phép và quản lý giường Inpatient khi route cho phép. **Giới hạn:** Báo cáo chỉ đọc, không sửa dữ liệu nguồn.

| Mã yêu cầu | Luồng và xử lý | Đầu vào | Đầu ra | Dịch vụ sở hữu | Nhánh thay thế hoặc lỗi |
|---|---|---|---|---|---|
| CRS-MAN-01 | Truy vấn báo cáo quản trị từ projection sự kiện | Loại báo cáo, bộ lọc, phạm vi quyền | Báo cáo vận hành/tài chính | Report | Phạm vi trái quyền bị từ chối; độ trễ projection được báo vận hành |
| CRS-MAN-02 | Tạo/cập nhật giường Inpatient; kiểm tra Manager role, tham chiếu và quy tắc giường | Tham chiếu khoa/phòng và dữ liệu giường | Hồ sơ giường Inpatient | Inpatient | Tham chiếu/trạng thái sai bị từ chối |

#### B2.8 Người bệnh

**Trách nhiệm:** Dùng tự phục vụ được phân quyền cho hồ sơ, appointment, result, prescription, invoice, receipt, payment và notification của chính người bệnh. **Giới hạn:** Không truy cập dữ liệu người bệnh khác hoặc thực hiện thao tác lâm sàng/tài chính dành cho nhân viên.

| Mã yêu cầu | Luồng và xử lý | Đầu vào | Đầu ra | Dịch vụ sở hữu | Nhánh thay thế hoặc lỗi |
|---|---|---|---|---|---|
| CRS-PAT-01 | Dùng tự phục vụ được phân quyền; thực thi quyền sở hữu và hiển thị hồ sơ, appointment, result, prescription, invoice, receipt, payment và notification được phép | Định danh người bệnh đã xác minh và ngữ cảnh sở hữu/quan hệ | View tự phục vụ cá nhân và request được phép | Dịch vụ sở hữu từng record | Truy cập chéo người bệnh và mutation dành cho nhân viên bị từ chối |

#### B2.9 Tác nhân hệ thống nội bộ

**Trách nhiệm:** Phát outbox, xử lý idempotent, retry hữu hạn, cập nhật projection và tạo thông báo. **Giới hạn:** Không tự tạo định danh hoặc bỏ qua quyết định dịch vụ sở hữu.

| Mã yêu cầu | Luồng và xử lý | Đầu vào | Đầu ra | Dịch vụ sở hữu | Nhánh thay thế hoặc lỗi |
|---|---|---|---|---|---|
| CRS-SYS-01 | Phát đúng domain event đã được dịch vụ sở hữu duyệt và commit | Outbox của owner và event/correlation identity | Bằng chứng broker delivery | Dịch vụ producer | Retry lỗi transport mà không tạo trạng thái nghiệp vụ |
| CRS-SYS-02 | Xử lý notification event theo idempotency và thử gửi | Event/correlation identity và dữ liệu người nhận | Delivery record | Notification | Retry lỗi tạm thời; lỗi cuối quan sát được |
| CRS-SYS-03 | Xử lý reporting event theo idempotency và cập nhật read model | Event/correlation identity và projection data | Reporting projection | Report | Retry/dead-letter mà không sửa hồ sơ nguồn |

### B3. Chu trình xử lý dữ liệu theo vai trò

| Quy trình | Đối tượng | Thông tin đầu vào | Xử lý | Thông tin đầu ra | Dịch vụ sở hữu |
|---|---|---|---|---|---|
| Quản trị nền tảng | `ADMIN` | Định danh nhân viên, vai trò, khoa, dữ liệu cơ sở/tài nguyên | Kiểm tra quyền và duy nhất; lưu trạng thái tổ chức | Tham chiếu người dùng/tài nguyên và dữ liệu kiểm toán | Organization |
| Đăng ký người bệnh do nhân viên thực hiện | `ADMIN`, `NURSE` | Nhân khẩu học, liên hệ, định danh, đầu vào đồng thuận | Kiểm tra trường và trùng; cấp UUID | Hồ sơ và xác nhận người bệnh | Patient |
| Quản lý lịch khám | `ADMIN`, `NURSE` | UUID người bệnh, dịch vụ/bác sĩ, thời gian, lý do | Kiểm tra điều kiện lịch; tạo/cập nhật trạng thái | Lịch khám và trạng thái hiển thị | Clinical |
| Khám lâm sàng | `ADMIN`, `DOCTOR` | Patient/appointment, phát hiện, EXAM clearance hoặc override audit | Kiểm tra role, ngữ cảnh và proof; ghi record/diagnosis | Medical record và disposition/reference | Clinical |
| Clinical request và tạo Laboratory test | `ADMIN`, `DOCTOR` | Patient/record, khoa, test code/ngày, order ID | Chuyển order tường minh; Laboratory kiểm tra và tạo test của mình | Lab test được tiếp nhận ở trạng thái đầu | Laboratory |
| Xử lý chẩn đoán | `ADMIN`, `LAB_TECH` | Test đã có, mẫu/giá trị, LAB_TEST clearance hoặc override audit | Thực thi vòng đời và proof | Kết quả xác minh và sự kiện công bố | Laboratory |
| Tạo đơn thuốc | `ADMIN`, `DOCTOR` | Tham chiếu record, dòng thuốc, hướng dẫn | Pharmacy kiểm tra và tạo đơn của mình | Đơn Pharmacy và pending slip | Pharmacy |
| Cấp phát thuốc | `ADMIN`, `PHARMACIST` | Đơn Pharmacy, payment proof, số lượng, tồn | Kiểm tra proof/đơn/kho; lưu biến động | Hồ sơ cấp phát và vị thế kho mới | Pharmacy |
| Xử lý invoice và payment | Nhân viên Billing được phép | Patient/source, charge, invoice và payment | Kiểm tra trạng thái tài chính; tạo invoice; ghi payment/receipt | Trạng thái invoice, payment và receipt | Billing |
| Deposit/refund/settlement/clearance | `ADMIN`, `CASHIER` | Mục đích nghiệp vụ, target chăm sóc/admission, giao dịch gốc khi cần và số tiền | Áp dụng quyền, append-only, idempotency, tổng và mục đích | Hồ sơ deposit, refund, settlement hoặc clearance | Billing |
| Vòng đời Inpatient | `ADMIN`, `DOCTOR`, `NURSE` hoặc `CASHIER` theo thao tác | Admission, giường, clearance, treatment, medical-discharge, settlement hoặc close | Áp dụng role, proof và chuyển trạng thái của thao tác | Hồ sơ admission, giường, treatment, discharge và close | Inpatient |
| Vòng đời Surgery | Nhân viên Surgery được phép | Request, readiness, clearance, schedule, procedure, recovery, outcome hoặc cancellation | Áp dụng quyền, điều kiện và chuyển trạng thái; phát outcome/charge handoff | Surgery case và outcome kiểm toán được | Surgery |
| Báo cáo quản trị | `MANAGER` | Loại báo cáo, thời gian/bộ lọc, phạm vi quyền | Truy vấn projection; tổng hợp/định dạng | Báo cáo vận hành hoặc tài chính | Report |
| Tự động hóa Notification | `SYSTEM` | Sự kiện đã duyệt, message key, dữ liệu người nhận | Xử lý idempotent, chọn mẫu, thử gửi | Trạng thái gửi và bằng chứng vận hành | Notification |
| Tự động hóa Report | `SYSTEM` | Sự kiện đã duyệt, message key, dữ liệu projection | Xử lý idempotent và cập nhật read model | Projection báo cáo cập nhật | Report |

### B4. Luồng đăng ký, lịch khám và lượt khám

| Bước | Đối tượng/nguồn | Đầu vào | Xử lý và quy tắc | Đầu ra/đối tượng nhận |
|---:|---|---|---|---|
| 1 | `ADMIN` hoặc `NURSE` | Nhân khẩu học và liên hệ | Patient kiểm tra dữ liệu bắt buộc và tính duy nhất | UUID và hồ sơ người bệnh |
| 2 | `ADMIN` hoặc `NURSE` | UUID người bệnh, dịch vụ/thời gian | Clinical kiểm tra lịch và ghi trạng thái | UUID/trạng thái lịch khám |
| 3 | Điều dưỡng/tiếp nhận | Lịch và thông tin đến khám | Chuyển lịch qua trạng thái check-in hợp lệ | Ngữ cảnh lịch sẵn sàng |
| 4 | `ADMIN`/`DOCTOR` | Patient/appointment và EXAM clearance khớp hoặc override audit | Clinical kiểm tra role, ngữ cảnh và proof; mở medical record | UUID medical record |
| 5 | `ADMIN`/`DOCTOR` | Triệu chứng, phát hiện, ghi chú | Clinical kiểm tra và ghi thông tin y khoa | Record và diagnosis cập nhật |
| 6 | `DOCTOR` | Đánh giá và chẩn đoán | Clinical ghi disposition/reference; command Lab/Pharmacy sau đó đi tới owner tương ứng | Diagnosis và disposition/reference |

### B5. Luồng yêu cầu và trả kết quả Laboratory

Luồng cuối cùng chuyển test request từ Clinical sang Laboratory với test code tường minh và order ID do producer tạo. Laboratory kiểm tra yêu cầu, tạo và sở hữu vòng đời test, rồi công bố kết quả đã xác minh.

| Bước | Thông tin đầu vào | Xử lý | Thông tin đầu ra | Kiểm soát |
|---:|---|---|---|---|
| 1 | Tham chiếu patient/record, khoa, test code/ngày, order ID do producer tạo | Clinical kiểm tra quyền; Laboratory kiểm tra order tường minh và tạo test | UUID Lab test và trạng thái đầu | Order identity sai/trùng hoặc ngữ cảnh không được phép bị từ chối |
| 2 | Test đã có cùng LAB_TEST clearance hoặc emergency override audit | Laboratory kiểm tra vòng đời và proof chính xác | Ngữ cảnh xử lý được phép | Proof thiếu/sai chặn xử lý |
| 3 | Test được phép và dữ liệu mẫu | `ADMIN`/`LAB_TECH` ghi trạng thái xử lý | Mẫu/status time | Kiểm tra chuyển trạng thái |
| 4 | Giá trị đo, tham chiếu, nhận xét | `ADMIN`/`LAB_TECH` ghi kết quả và kiểm tra đầy đủ | Kết quả nháp/hoàn tất | Trường/đơn vị/khoảng theo định nghĩa |
| 5 | Kết quả hoàn tất | Công bố được phép đổi trạng thái cuối | Kết quả công bố và event | Chỉ sửa theo correction kiểm soát |
| 6 | Sự kiện kết quả | Consumer đủ điều kiện xử lý hợp đồng đã duyệt idempotent | Notification, reporting projection và bàn giao kết quả tính phí | Delivery trùng là no-op; retry/dead-letter quan sát được |

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
| 1 | Yêu cầu charge/invoice được phép | Patient/source và dữ liệu mục tính phí | Billing kiểm tra ngữ cảnh và tạo charge/invoice | Invoice mở và trạng thái tài khoản |
| 2 | `CASHIER` | Invoice cùng số tiền/phương thức người trả cung cấp | Kiểm tra quyền, số tiền, trạng thái; ghi payment | Invoice đã/đang thanh toán và receipt |
| 3 | Payment request được phép | Mục đích nghiệp vụ chính xác và số tiền thập phân | Áp dụng payment append-only/idempotent | Payment, receipt và clearance theo mục đích |
| 4 | Deposit request được phép | Admission target và số tiền yêu cầu | Ghi deposit, không xem tiền giữ hộ là doanh thu | Deposit và trạng thái tài chính admission |
| 5 | Refund request được phép | Giao dịch gốc completed, lý do và số tiền | Thực thi quyền, giới hạn tích lũy và reversal append-only | Refund record và số dư cập nhật |
| 6 | Settlement/clearance request | Mục đích/target và tổng đã lưu | Kiểm tra quyết toán đủ hoặc ngoại lệ được duyệt; phát quyết định | Settlement hoặc service-clearance state |

Hồ sơ tài chính dùng `BigDecimal`, không dùng số dấu phẩy động nhị phân. Charge, invoice, deposit, payment, receipt, refund, settlement và clearance phải kiểm toán được, idempotent khi cần và gắn đúng mục đích nghiệp vụ.

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

### B9. Vòng đời Surgery

Bảng dưới mô tả hành trình Surgery đầy đủ của hệ thống cuối cùng. Phần giới hạn và bằng chứng nghiệm thu xác định những nội dung còn phải hoàn tất, kiểm chứng đầu-cuối tại Review 1.

| Bước | Đối tượng | Đầu vào | Xử lý | Đầu ra |
|---:|---|---|---|---|
| 1 | `DOCTOR` | Người bệnh, lượt khám, chỉ định, thủ thuật yêu cầu | Kiểm tra quyền và tham chiếu lâm sàng; tạo Surgery request | Surgery case liên kết người bệnh/lượt khám |
| 2 | Nhóm Surgery được phép | Checklist, consent, assessment, thiết bị và bằng chứng readiness | Kiểm tra bằng chứng bắt buộc; ghi quyết định readiness | Case ready, blocked hoặc cancelled cùng lý do |
| 3 | Billing authority và `CASHIER` | Tham chiếu Surgery, khoản thu dự kiến, ngữ cảnh deposit/payment | Đánh giá yêu cầu tài chính; phát clearance hoặc ngoại lệ được duyệt | Quyết định financial clearance |
| 4 | Người xếp lịch được phép | Case đã clearance, phòng/thời gian/nhóm, thiết bị và revision | Kiểm tra xung đột/tài nguyên; xác nhận lịch | Surgery schedule đã xác nhận và nhóm/tài nguyên được phân |
| 5 | Nhóm Surgery được phép | Thủ thuật đã xếp và thông tin trong mổ | Start procedure, ghi sự kiện chính, complete hoặc cancel qua chuyển trạng thái kiểm soát | Execution record, tài nguyên sử dụng và trạng thái thủ thuật |
| 6 | Nhóm Surgery được phép và `SYSTEM` | Phát hiện, recovery, outcome và tài nguyên tính phí | Ghi recovery/outcome; phát sự kiện chăm sóc/khoản thu đủ điều kiện idempotent | Outcome, recovery state, notification, report và bàn giao Billing |

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
- **Lỗi/ngoại lệ:** Mỗi owner từ chối đầu vào sai độc lập; tự phục vụ của `PATIENT` chỉ được phép với chính hồ sơ thuộc quyền của người đó.

#### Quy trình 2 — Appointment đến lượt khám

- **Kích hoạt:** Quy trình nhân viên được phép đạt trạng thái tư vấn hợp lệ.
- **Thẩm quyền chính:** Clinical.
- **Đầu vào:** Appointment do Clinical sở hữu và tham chiếu UUID người bệnh.
- **Xử lý:** Kiểm tra appointment/actor rồi tạo hoặc cập nhật encounter.
- **Đầu ra:** Encounter, quan sát, chẩn đoán và chỉ định tiếp theo được phép.
- **Lỗi/ngoại lệ:** Clinical từ chối trạng thái, role hoặc tham chiếu sai.

#### Quy trình 3 — Clinical test request và Laboratory result

- **Kích hoạt:** `ADMIN` hoặc `DOCTOR` gửi test request được phép để Laboratory tiếp nhận với test code và order ID tường minh.
- **Thẩm quyền chính:** Clinical sở hữu quyết định chăm sóc; Laboratory sở hữu vòng đời test và kết quả.
- **Đầu vào:** Tham chiếu patient/record, khoa, test code/ngày, order ID, LAB_TEST clearance khớp hoặc override audit, mẫu và giá trị.
- **Xử lý:** Laboratory kiểm tra request, proof, vòng đời và dữ liệu trước khi xử lý, xác minh và công bố kết quả.
- **Đầu ra:** Kết quả Laboratory công bố và sự kiện đủ điều kiện.
- **Lỗi/ngoại lệ:** Proof thiếu/sai, order identity trùng/sai, kết quả thiếu hoặc trạng thái sai sẽ chặn xử lý/công bố.

#### Quy trình 4 — Kết quả Laboratory đến khoản phí Billing

- **Kích hoạt:** Sự kiện xét nghiệm tính phí đủ điều kiện được duyệt và phát.
- **Thẩm quyền chính:** Billing sở hữu phí; Laboratory sở hữu kết quả nguồn.
- **Đầu vào:** Event ID, tham chiếu order/result, patient, item và hợp đồng số tiền thập phân.
- **Xử lý:** Billing xử lý hợp đồng charge đã duyệt theo cách idempotent và liên kết charge đúng patient, nguồn và mục đích dịch vụ.
- **Đầu ra:** Ledger charge, cập nhật tài khoản và trạng thái invoice truy vết được.
- **Lỗi/ngoại lệ:** Dữ liệu hợp đồng sai bị từ chối; delivery trùng là no-op và lỗi phân phối đi qua retry/dead-letter.

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
- **Đầu vào:** Dữ liệu charge/invoice/payment hoặc mục đích deposit/refund/settlement/clearance chính xác cùng target chăm sóc/admission.
- **Xử lý:** Kiểm tra quyền, trạng thái, số tiền, giao dịch gốc khi cần, idempotency, append-only, tổng và mục đích nghiệp vụ.
- **Đầu ra:** Hồ sơ invoice, payment, receipt, deposit, refund, settlement hoặc clearance kiểm toán được.
- **Lỗi/ngoại lệ:** Quyền, mục đích, target, số tiền, giới hạn tích lũy hoặc trạng thái sai bị từ chối.

#### Quy trình 7 — Nhập viện và xuất viện Inpatient

- **Kích hoạt:** Yêu cầu Inpatient được phép được gửi.
- **Thẩm quyền chính:** Inpatient.
- **Đầu vào:** Admission, active bed, deposit clearance/override khớp, treatment, discharge summary, settlement/close override.
- **Xử lý:** Áp dụng role: create/assign, `/admit` tường minh, treatment, medical discharge, rồi administrative close có settlement gate.
- **Đầu ra:** Trạng thái requested/admitted/treatment/medical-discharge/closed và sự kiện liên khoa cần thiết.
- **Lỗi/ngoại lệ:** Thiếu bed/clearance/settlement, stale state hoặc chuyển trạng thái bị cấm sẽ bị từ chối.

#### Quy trình 8 — Vòng đời Surgery

- **Kích hoạt:** Surgery request được phép được Clinical chuyển sang.
- **Thẩm quyền chính:** Surgery sở hữu case/vòng đời; Clinical và Billing giữ thẩm quyền bằng chứng lâm sàng/tài chính.
- **Đầu vào:** Yêu cầu được duyệt, bằng chứng readiness/consent, financial clearance, phòng/thời gian/nhóm/thiết bị, procedure, recovery và outcome.
- **Xử lý:** Kiểm tra điều kiện; xác nhận lịch; start và complete hoặc cancel; ghi recovery/outcome; phát notification, report và charge handoff đủ điều kiện.
- **Đầu ra:** Surgery case, schedule, execution record, recovery state, outcome và charge event kiểm toán được.
- **Lỗi/ngoại lệ:** Thiếu quyền, readiness, clearance, tài nguyên hoặc chuyển trạng thái sai sẽ chặn tiến trình và ghi lý do.

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
| Lab test, mẫu và kết quả | Laboratory | Hợp đồng Clinical request và result event | `ADMIN`/`DOCTOR` request; Laboratory tạo; clearance/override; trạng thái kết quả |
| Đơn, tồn kho và cấp phát | Pharmacy | Hợp đồng đơn/cấp phát/kho | Doctor actor, Pharmacy ownership, payment proof, biến động nhất quán |
| Phí, hóa đơn, giao dịch, biên lai, hoàn tiền, clearance | Billing | Hợp đồng/sự kiện trạng thái tài chính | Tiền thập phân, idempotency, kiểm toán |
| Gửi thông báo | Notification | Chỉ truy vấn vận hành | Riêng tư người nhận, trạng thái, bằng chứng retry |
| Projection báo cáo | Report | Truy vấn quản lý được phép | Chỉ đọc, idempotency sự kiện |
| Nhập viện, đợt, giường, xuất viện | Inpatient | Tham chiếu/sự kiện kiểm soát | Cổng clearance và chuyển trạng thái |
| Surgery case, readiness, schedule, execution, recovery và outcome | Surgery | Hợp đồng Surgery được phân quyền và outcome event | Quyền, readiness, financial clearance, tài nguyên, chuyển trạng thái và audit |

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
| API-08 | Thay đổi hợp đồng phải giữ kỳ vọng hiện có của consumer hoặc cung cấp hợp đồng migration tường minh |

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
| Khả năng liên vận | Hợp đồng API/event phải kiểm thử producer/consumer | Contract/integration test |
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
| Công cụ tài liệu | Microsoft Word, LibreOffice Writer hoặc công cụ khác hỗ trợ định dạng báo cáo |
| Trạng thái mobile | Giả định web responsive; chưa cung cấp mobile client native hoàn chỉnh |

#### B17.2. Phần mềm server và phát triển

| Hạng mục | Đường cơ sở theo mã nguồn |
|---|---|
| Java | 21 |
| Spring Boot | 3.3.5 |
| Spring Cloud | 2023.0.3 |
| Build backend | Maven multi-module |
| Frontend | Next.js 16.2.11, React 19.2.4, TypeScript 5, Tailwind CSS 4, pnpm |
| Cơ sở dữ liệu | PostgreSQL 16, cơ sở dữ liệu logic tách theo dịch vụ |
| Messaging | RabbitMQ 3 với hỗ trợ quản trị vận hành |
| Discovery | Eureka Server |
| Container | Docker Engine và Docker Compose hỗ trợ định nghĩa kho mã |
| Dịch vụ nền production | HTTPS certificate, secret management, log/metrics/alert tập trung, backup, đồng bộ thời gian |

### B18. Ma trận truy vết và nghiệm thu

Đường cơ sở Maven tổng đã ghi nhận là **3.612 test, 0 failure, 0 error và 0 skip**. Đây là bằng chứng kỹ thuật hỗ trợ từ lần chạy đã ghi nhận, không chứng minh khách hàng đã nghiệm thu mọi quy trình hoặc tích hợp bên ngoài.

| Mã yêu cầu | Actor hoặc quy trình | Dịch vụ sở hữu | Khu vực bằng chứng nguồn | Phương pháp nghiệm thu |
|---|---|---|---|---|
| AC-01A / FR-PAT-01 | Đăng ký do nhân viên thực hiện | Patient | Controller/tài liệu Patient | Trình diễn `ADMIN`/`NURSE` đăng ký |
| AC-01B / FR-CLI-00 | Tạo appointment | Clinical | Controller/tài liệu appointment Clinical | Trình diễn `ADMIN`/`NURSE` tạo lịch |
| AC-02 / FR-CLI-01 | Lượt khám lâm sàng | Clinical | Controller/domain test và finance-contract test Clinical | Trình diễn ghi theo role, trạng thái encounter hợp lệ và EXAM clearance/override bắt buộc |
| AC-03 / FR-LAB-00..01 | Clinical request và vòng đời Laboratory | Laboratory | Controller/service test và order-contract test Lab | Chuyển test code/order ID; yêu cầu LAB_TEST clearance/override; công bố kết quả đã xác minh |
| AC-04 / FR-PHA-00..01 | Đơn Pharmacy và cấp phát | Pharmacy | Controller/payment-proof/inventory test Pharmacy | Tạo qua Pharmacy; cần payment proof và tồn trước cấp |
| AC-05 / FR-BIL-01 | Charge, invoice, payment và receipt | Billing | API/domain test Billing | Trình diễn tạo, thanh toán, receipt và cập nhật tài khoản truy vết được |
| AC-06 / FR-BIL-02 | Deposit, refund, settlement và clearance | Billing | Domain, contract và integration test Billing | Kiểm tra quyền, mục đích, tổng, idempotency, append-only và bàn giao luồng chăm sóc |
| AC-07 / FR-INP-01..03 | Vòng đời Inpatient | Inpatient | Controller, domain, event và integration test Inpatient | Trình diễn từ admission đến administrative close cùng giường, deposit, settlement và bàn giao liên khoa |
| AC-08 / FR-SUR-01..02 | Vòng đời Surgery | Surgery | Controller, domain, event và integration test Surgery | Trình diễn request, readiness, clearance, schedule, execution, recovery, outcome và charge handoff |
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

Báo cáo chi tiết mô tả đầy đủ hệ thống MediFlow cuối cùng và ghi riêng giới hạn Review 1 cùng bằng chứng nghiệm thu. Vẫn cần khách hàng phê duyệt chính sách triển khai, tích hợp ngoài, quản trị lâm sàng, mục tiêu năng lực, thời hạn lưu giữ và mục tiêu khôi phục.
