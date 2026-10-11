# MediFlow Review 1 — Báo cáo thuyết trình (Tiếng Việt)

| Hạng mục thuyết trình | Giá trị |
|---|---|
| Chủ đề | Xác định vấn đề và Đặc tả yêu cầu khách hàng |
| Thời lượng đề xuất | 12–18 phút và phần câu hỏi |
| Cấu trúc | 10 phần thuyết trình |
| Đường cơ sở nguồn | Kho mã MediFlow tại commit `1cc014e` |
| Người nghe | Hội đồng đánh giá và các bên liên quan |

## Phần 1 — Giới thiệu dự án

**Thông điệp chính:** MediFlow kết nối hành trình của người bệnh qua các khoa trong khi vẫn giữ quyền sở hữu dữ liệu rõ ràng cho từng bộ phận.

| Hạng mục | Tóm tắt |
|---|---|
| Dự án | Nền tảng quản lý bệnh viện |
| Kênh cung cấp | Ứng dụng web Next.js qua API gateway trung tâm |
| Backend | Mười dịch vụ nghiệp vụ Spring Boot |
| Dữ liệu | Cơ sở dữ liệu PostgreSQL tách riêng theo dịch vụ |
| Tích hợp | REST để kiểm tra có thẩm quyền tức thời; RabbitMQ để phát sự kiện đã hoàn tất |
| Người dùng | Chín vai trò cho nhân viên, người bệnh, quản lý và tự động hóa |

MediFlow không chỉ là tập hợp biểu mẫu. Hệ thống mô hình hóa cách thông tin đi từ đăng ký đến khám chữa bệnh, xét nghiệm, dùng thuốc, tài chính, nhập viện, phẫu thuật, truyền thông và báo cáo quản trị.

*Gợi ý trình bày: Bắt đầu bằng câu chuyện có nhân viên hỗ trợ: đăng ký, lịch khám, gặp bác sĩ, xét nghiệm, đơn, thanh toán. Giải thích rằng mỗi bàn giao đều có dịch vụ sở hữu.*

## Phần 2 — Xác định vấn đề

| Vấn đề hiện tại | Hệ quả vận hành | Phản hồi của MediFlow |
|---|---|---|
| Giấy tờ và ứng dụng rời rạc | Nhập lại và hồ sơ thiếu nhất quán | Định danh ổn định và hợp đồng dịch vụ tường minh |
| Bàn giao liên khoa thủ công | Chậm chỉ định, kết quả và quyết định | Trạng thái quy trình và sự kiện có kiểm soát |
| Đơn thuốc tách khỏi tồn kho | Rủi ro cấp phát và kho sai | Xác minh đơn và biến động tồn kho |
| Dịch vụ tách khỏi tài chính | Bỏ sót hoặc trùng phí | Sự kiện tính phí idempotent và Billing sở hữu tập trung |
| Điều phối nhập viện/phẫu thuật yếu | Xung đột tài nguyên và không rõ sẵn sàng | Cổng xác nhận, phân bổ, sẵn sàng và lịch |
| Báo cáo quản trị thủ công | Số liệu chậm và dễ tranh chấp | Read model được nuôi bằng sự kiện |
| Quy tắc truy cập không nhất quán | Rủi ro riêng tư và trách nhiệm | Phân quyền backend và mặc định từ chối |

*Gợi ý trình bày: Nhấn mạnh vấn đề không phải là thiếu dữ liệu, mà là dữ liệu phân mảnh, không có quyền sở hữu, thời điểm và trách nhiệm rõ ràng.*

## Phần 3 — Mục tiêu và phạm vi

### Mục tiêu

- Truy vết hành trình người bệnh đầu-cuối.
- Mỗi hồ sơ quan trọng có một dịch vụ có thẩm quyền.
- Thực thi phân tách nhiệm vụ theo vai trò.
- Tự động bàn giao liên khoa an toàn.
- Bảo vệ toàn vẹn tài chính và tồn kho.
- Khôi phục sau lỗi mà không lặp tác động nghiệp vụ.
- Cung cấp thông tin quản trị mà không sửa hồ sơ nguồn.

### Phạm vi nghiệp vụ của hệ thống hoàn chỉnh

| Dịch vụ | Trách nhiệm chính |
|---|---|
| Organization | Nhân viên, vai trò, khoa, cơ sở, phòng |
| Patient | Hồ sơ và định danh do nhân viên vận hành |
| Clinical | Appointment, medical record, diagnosis, disposition/reference; examination cần EXAM clearance hoặc override audit |
| Laboratory | Test request được phép, mẫu, xử lý, kết quả xác minh và công bố; xử lý cần LAB_TEST clearance hoặc override audit |
| Pharmacy | Đơn do Pharmacy sở hữu, cấp phát có payment proof, thuốc và tồn kho |
| Billing | Charge, invoice, deposit, payment, receipt, refund, settlement và service clearance |
| Notification | Xử lý thông báo theo sự kiện và trạng thái gửi |
| Report | Read model vận hành và tài chính |
| Inpatient | Admission, clearance, giường sở hữu, stay, treatment, transfer, medical discharge, settlement và administrative close |
| Surgery | Request, readiness, clearance, schedule, execution, recovery, outcome và charge handoff |

*Gợi ý trình bày: Giải thích ranh giới nghiệp vụ cuối cùng: Inpatient sở hữu đợt nằm viện/giường, Surgery sở hữu surgical case và Billing cung cấp quyết định tài chính cho cả hai quy trình.*

## Phần 4 — Đối tượng và chức năng

| Vai trò | Chức năng chính |
|---|---|
| `ADMIN` | Cấu hình dữ liệu Organization và hồ sơ giường Inpatient được phép |
| `DOCTOR` | Ghi Clinical record có clearance; yêu cầu Laboratory test; tạo đơn qua Pharmacy; thực hiện thao tác Inpatient và Surgery được phép |
| `NURSE` | Tạo/cập nhật appointment, đọc Clinical record và thao tác bed/admit/treatment observation Inpatient được phép |
| `LAB_TECH` | Xử lý test đã có và đủ proof; ghi/xác minh/công bố; không tạo test |
| `PHARMACIST` | Xác minh đơn, cấp thuốc và kiểm soát tồn kho |
| `CASHIER` | Xử lý charge, invoice, deposit, payment, receipt, refund được duyệt, settlement và service clearance |
| `MANAGER` | Xem báo cáo vận hành và tài chính |
| `PATIENT` | Dùng tự phục vụ được phân quyền cho hồ sơ, appointment, result, prescription, invoice, receipt, payment và notification thuộc mình |
| `SYSTEM` | Phát/nhận sự kiện, retry lỗi, cập nhật báo cáo, tạo thông báo |

*Gợi ý trình bày: Giải thích rằng ẩn nút chỉ là trải nghiệm người dùng; backend vẫn tự kiểm tra role tại endpoint được bảo vệ.*

## Phần 5 — Kiến trúc giải pháp đề xuất

| Thành phần | Vai trò trong giải pháp |
|---|---|
| Client trình duyệt | Hiển thị quy trình theo vai trò |
| API gateway | Cung cấp một điểm vào client có kiểm soát |
| Eureka | Đăng ký và khám phá dịch vụ backend |
| Dịch vụ nghiệp vụ | Sở hữu quy tắc và hồ sơ theo bounded context |
| PostgreSQL | Cơ sở dữ liệu logic riêng cho mỗi dịch vụ |
| RabbitMQ | Phân phối sự kiện nghiệp vụ đã hoàn tất |
| Transactional outbox | Ngăn thay đổi đã commit bị mất ý định phát sự kiện |
| Idempotent consumer | Ngăn retry lặp tác động nghiệp vụ |
| Retry/dead-letter | Làm lỗi tạm thời và lỗi cuối quan sát được |

Quy tắc kiến trúc: `infrastructure → application → domain`. Domain không phụ thuộc Spring, transport hay persistence. Dịch vụ trao đổi DTO và tham chiếu UUID, không trao đổi quan hệ cơ sở dữ liệu.

*Gợi ý trình bày: So sánh REST và sự kiện: REST trả lời “tôi cần xác nhận gì ngay bây giờ?”, còn sự kiện thông báo “điều gì đã xảy ra?”.*

## Phần 6 — Chu trình xử lý dữ liệu khách hàng

| Quy trình | Đối tượng | Thông tin đầu vào | Xử lý | Thông tin đầu ra | Dịch vụ sở hữu |
|---|---|---|---|---|---|
| Đăng ký do nhân viên thực hiện | `ADMIN`, `NURSE` | Nhân khẩu học, liên hệ, định danh | Kiểm tra dữ liệu định danh | Hồ sơ người bệnh | Patient |
| Tạo lịch khám | `ADMIN`, `NURSE` | UUID người bệnh, dịch vụ/thời gian | Kiểm tra dữ liệu lịch | Lịch khám | Clinical |
| Examination Clinical | `ADMIN`, `DOCTOR` | Patient/appointment, phát hiện, EXAM clearance/override | Kiểm tra role, ngữ cảnh, trạng thái và proof; ghi record/diagnosis | Medical record và disposition | Clinical |
| Tạo Lab test | `ADMIN`, `DOCTOR` | Patient/record, khoa, loại test/ngày | Tạo trực tiếp; không có Clinical order contract | Lab test | Laboratory |
| Xử lý Lab | `ADMIN`, `LAB_TECH` | Test đã có, mẫu/giá trị, LAB_TEST clearance/override | Kiểm tra vòng đời và proof; xử lý, xác minh, công bố | Kết quả và event | Laboratory |
| Tạo đơn | `ADMIN`, `DOCTOR` | Tham chiếu Clinical và dòng thuốc | Pharmacy kiểm tra/định giá và tạo đơn | Đơn Pharmacy | Pharmacy |
| Cấp phát | `ADMIN`, `PHARMACIST` | Đơn Pharmacy, payment proof bền vững, tồn | Kiểm tra proof/tồn, cấp | Hồ sơ cấp và kho | Pharmacy |
| Xử lý tài chính | Nhân viên Billing được phép | Charge/invoice/payment hoặc mục đích deposit/refund/settlement/clearance chính xác | Kiểm tra quyền, trạng thái, số tiền, idempotency, tổng và mục đích | Hồ sơ tài chính và quyết định clearance kiểm toán được | Billing |
| Vòng đời Inpatient | Nhân viên được phép theo thao tác | Admission, bed, deposit proof/override, treatment, discharge và settlement/close proof | Áp dụng quyền, proof và trạng thái tường minh | Admission, treatment, discharge, close và bed state | Inpatient |
| Vòng đời Surgery | Nhân viên Surgery được phép | Request, readiness, clearance, schedule, procedure, recovery và outcome | Kiểm tra điều kiện; thực hiện vòng đời; phát outcome và charge handoff | Case, outcome và charge event kiểm toán được | Surgery |
| Gửi thông báo | `SYSTEM`, `PATIENT` đọc | Event đã duyệt và định danh người nhận | Gửi idempotent; thực thi owned read | Trạng thái gửi/thông báo thuộc mình | Notification |
| Báo cáo | `SYSTEM`, `MANAGER` | Event đã duyệt và tiêu chí báo cáo | Cập nhật/truy vấn projection | Báo cáo được phép | Report |

*Gợi ý trình bày: Đọc một dòng theo mẫu năm phần: actor, input, process, output, owner. Nêu rằng báo cáo đầy đủ áp dụng cùng mẫu cho mọi quy trình lớn.*

## Phần 7 — Các luồng liên khoa chính

1. Nhân viên đăng ký trong Patient → tạo lịch trong Clinical → lượt khám lâm sàng.
2. Clinical test request với test code/order ID tường minh → Laboratory tiếp nhận → clearance/override → xử lý/kết quả.
3. Bác sĩ tạo đơn do Pharmacy sở hữu → payment proof bền vững → cấp → biến động kho.
4. Sự kiện chăm sóc tính phí → charge/invoice → deposit/payment/receipt → refund hoặc settlement/service clearance theo yêu cầu.
5. Admission Inpatient → bed → deposit clearance/override → admit tường minh → treatment → medical discharge → settlement/override → administrative close.
6. Yêu cầu Surgery → readiness → clearance → lịch → thực hiện → recovery/outcome → charge handoff.
7. Domain event đã commit → gửi thông báo và cập nhật projection báo cáo.

Kiểm soát chính:

- UUID tương quan ổn định giữa các dịch vụ.
- Dịch vụ sở hữu xác minh trước đổi trạng thái.
- `BigDecimal` cho tiền.
- Outbox phát sau thay đổi đã commit.
- Consumer sự kiện idempotent.
- Retry hữu hạn và dead-letter quan sát được.

*Gợi ý trình bày: Dùng Inpatient và Surgery để minh họa cách thẩm quyền lâm sàng, kiểm soát tài nguyên và Billing clearance phối hợp mà không chia sẻ cơ sở dữ liệu.*

## Phần 8 — Yêu cầu phần cứng và phần mềm

### Client

| Nhóm | Đường cơ sở tối thiểu | Khuyến nghị |
|---|---|---|
| Thiết bị nhân viên | CPU 64-bit hai lõi, RAM 4 GB, 1366×768 | CPU hiện đại bốn lõi, RAM 8 GB, Full HD |
| Trình duyệt | Chrome, Edge, Firefox hoặc Safari hiện tại | OS và trình duyệt còn nhận cập nhật bảo mật |
| Mạng | Truy cập ổn định endpoint HTTPS | Kết nối dây/Wi-Fi tin cậy |
| Phát triển | CPU bốn lõi, RAM 16 GB, SSD trống 30 GB | CPU tám lõi, RAM 32 GB, SSD trống từ 60 GB |

### Server và nền tảng

| Nhóm | Đường cơ sở bám sát mã nguồn |
|---|---|
| Backend | Java 21, Spring Boot 3.3.5, Spring Cloud 2023.0.3, Maven |
| Frontend | Next.js 16.2.11, React 19.2.4, TypeScript 5, Tailwind CSS 4, pnpm |
| Hạ tầng | PostgreSQL 16-compatible, RabbitMQ 3-compatible, Eureka, Docker Compose |
| Môi trường tích hợp | Cấu hình lập kế hoạch: 16 lõi logic, RAM 32 GB, SSD 150 GB |
| Production ban đầu | 24+ lõi logic, RAM 64+ GB, SSD chịu lỗi 500+ GB trên host phù hợp |

Các giá trị phần cứng là khuyến nghị lập kế hoạch. Định cỡ production cần test tải, tính sẵn sàng, lưu giữ và khôi phục.

*Gợi ý trình bày: Không trình bày số liệu production như bảo đảm. Nói rõ đây là điểm bắt đầu đang chờ bằng chứng workload được đo.*

## Phần 9 — Giới hạn và công việc còn lại

| Giới hạn | Trạng thái hiện tại |
|---|---|
| Ứng dụng mobile native | Chỉ có Flutter loader package; chưa có ứng dụng hoàn chỉnh có thể chạy |
| Tự phục vụ người bệnh | Truy cập trực tiếp hiện còn hạn chế; hồ sơ, appointment, result, prescription, invoice, receipt và payment cần nghiệm thu bảo mật đầu-cuối |
| Tích hợp liên khoa Inpatient | Có các thao tác admission, giường, treatment và discharge cốt lõi; bàn giao đầy đủ với tài chính, Pharmacy, Surgery, Report và Notification còn cần nghiệm thu tích hợp |
| Vòng đời Surgery | Request/chuẩn bị/schedule mới trình diễn một phần; readiness-to-outcome và charge handoff còn cần nghiệm thu đầu-cuối |
| Clinical-to-Lab order | Bàn giao tự động chưa hoàn tất; Lab test hiện do `ADMIN`/`DOCTOR` tạo trực tiếp |
| Mức độ hoàn thiện Billing | Invoice/payment cơ bản có thể trình diễn; deposit, refund, settlement, clearance và bàn giao luồng chăm sóc còn cần nghiệm thu đầy đủ |
| Proof bắt buộc | EXAM clearance, LAB_TEST clearance, Pharmacy payment proof và Inpatient deposit/settlement proof là bắt buộc trừ override audit hợp lệ |
| Bảo hiểm và định danh quốc gia | Chưa chứng minh tích hợp production đã chứng nhận |
| Ngân hàng/cổng thanh toán | Có tài chính nội bộ; chưa chứng minh tích hợp provider thương mại hoàn tất |
| Thiết bị y tế/lab ngoài/PACS/EHR | Không tuyên bố tương tác ngoài đã chứng nhận |
| Provider SMS/email/push | Có luồng thông báo nội bộ; hợp đồng vendor production còn chờ |
| SLO, RTO, RPO, lưu giữ production | Phải thống nhất và xác minh cho bệnh viện đích |
| Năng lực và disaster recovery | Cần load, failure, backup và restore test |
| Accessibility và pháp quy | Mục tiêu/chứng nhận chính thức là công việc quản trị cần khách hàng phê duyệt |
| Chẩn đoán/điều trị tự động | Ngoài phạm vi; quyết định lâm sàng thuộc con người |

*Gợi ý trình bày: Trình bày giới hạn như khoảng trống cần hoàn tất ở Review 1, còn phần yêu cầu tập trung vào quy trình bệnh viện cuối cùng đầy đủ.*

## Phần 10 — Nghiệm thu và kết luận

Review 1 có thể trình diễn qua câu chuyện nghiệm thu sau:

1. Với `ADMIN`/`NURSE`, đăng ký người bệnh trong Patient và tạo lịch trong Clinical.
2. Mở Clinical record bằng `ADMIN`/`DOCTOR` chỉ khi encounter hợp lệ và có EXAM clearance/override khớp.
3. Chuyển Clinical test request bằng test code/order ID tường minh; cần LAB_TEST clearance/override trước khi Laboratory xử lý/công bố.
4. Tạo đơn qua Pharmacy và cần payment proof bền vững trước cấp/kho.
5. Trình diễn charge/invoice/payment/receipt truy vết được cùng deposit, refund, settlement và service clearance được phân quyền.
6. Trình diễn gate bed + deposit, admit tường minh, medical discharge và close có settlement/override, trong khi event flag vẫn tắt.
7. Trình diễn Surgery request, readiness, clearance, scheduling, execution/cancellation, recovery, outcome và charge handoff.
8. Sinh thông báo và cập nhật báo cáo từ sự kiện đã commit.
9. Từ chối thao tác trái quyền và tác động nghiệp vụ lặp.
10. Chạy thành công bộ xác minh tự động đã thống nhất.

**Kết luận:** MediFlow cung cấp nền tảng bám sát mã nguồn cho vận hành bệnh viện phối hợp. Giá trị không chỉ nằm ở số mô-đun mà ở sự di chuyển thông tin có kiểm soát giữa actor được phép và dịch vụ sở hữu.

*Gợi ý trình bày: Kết thúc bằng ba cụm: “một chủ sở hữu cho mỗi hồ sơ”, “một đường truy vết cho mỗi quy trình” và “một gate tường minh cho tích hợp chưa hoàn tất”.*

### Câu hỏi và trả lời nhanh

| Câu hỏi có thể gặp | Trả lời ngắn |
|---|---|
| Vì sao dùng microservice? | Các domain bệnh viện có quy tắc và quyền sở hữu riêng; dịch vụ độc lập làm ranh giới rõ ràng. |
| Vì sao dùng cả REST và event? | REST xác nhận thông tin cần ngay; event phân phối sự kiện đã xảy ra. |
| Mobile đã hoàn tất chưa? | Chưa. Chỉ có Flutter loader package; web responsive là giả định kênh hiện tại. |
| Trạng thái Inpatient và Surgery là gì? | Hệ thống cuối cùng có vòng đời Inpatient và Surgery đầy đủ. Tại Review 1, các thao tác Inpatient cốt lõi có thể trình diễn; bàn giao liên khoa Inpatient và luồng Surgery từ readiness đến outcome còn cần nghiệm thu tích hợp. |
| Năng lực production được bảo đảm chưa? | Chưa. Số phần cứng là đường cơ sở lập kế hoạch và cần kiểm thử đại diện. |
| Ngăn phí trùng thế nào? | Billing dùng định danh sự kiện/idempotency để phân phối lặp không lặp tác động nghiệp vụ. |
