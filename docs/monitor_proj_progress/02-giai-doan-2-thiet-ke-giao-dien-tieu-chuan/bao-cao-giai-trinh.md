# BÁO CÁO GIẢI TRÌNH GIAI ĐOẠN 2

## Thiết kế Giao diện và Tiêu chuẩn

**Dự án:** MediFlow  
**Kỳ báo cáo:** 10/08/2026 - 23/08/2026  
**Ngày lập giải trình:** 02/09/2026  
**Tình trạng:** Hoàn thành một phần; thiếu bộ GUI/wireframe có thể duyệt và chưa đóng gói Design v1.0.

## 1. Căn cứ và mục tiêu phải giải trình

Kế hoạch của giáo viên yêu cầu quy chuẩn giao diện, luồng xử lý, kiến trúc sơ bộ, Database Design và quy trình nghiệp vụ chi tiết. Kế hoạch MediFlow chia thành GUI Standards, wireframe/mockup, ERD + DDL, sơ đồ microservices, Business Flow và tài liệu Design v1.0.

Báo cáo Giai đoạn 2 phải giải trình:

1. Một bộ GUI Standards dùng chung cho Web và Mobile gồm những quy tắc nào?
2. Những màn hình/luồng nào đã có wireframe hoặc mockup, ai duyệt và file nguồn ở đâu?
3. ERD có đủ theo database-per-service; DDL có thể triển khai và tái tạo database chưa?
4. Các microservice, gateway, discovery, database và message broker liên hệ thế nào?
5. Sự kiện nào được publish/subscribe, payload/version/idempotency/failure handling ra sao?
6. Business Flow nào đã mô tả happy path, alternative path, lỗi và bù trừ?
7. Các thiết kế GUI, API, database và event có truy vết về cùng Use Case không?

## 2. Đối chiếu kế hoạch với tình trạng thực tế

| STT | Cam kết | Hạn | Đầu ra phải có | Tình trạng tại 02/09 | Bằng chứng/thiếu hụt |
|---|---|---:|---|---|---|
| 2.1 | GUI Standards | 14/08 | Bố cục, màu, font, component/state | Bản khung văn bản | Chưa có token/component catalog được duyệt |
| 2.2 | Wireframe/mockup màn hình chính | 18/08 | File nguồn + bản export | Chưa hoàn thành | Không tìm thấy artifact hình ảnh/Figma; Web chỉ có 3 trang demo, Mobile chưa có mã Dart |
| 2.3 | ERD + DDL | 20/08 | ERD và script DDL | ERD khá đầy đủ; DDL chủ yếu ở mức spec | Có 11 sơ đồ; 8 spec có DDL nhúng; chỉ pharmacy có migration SQL thật |
| 2.4 | Kiến trúc hệ thống sơ bộ | 21/08 | Sơ đồ microservices | Đã thiết kế, chưa đóng gói | Có gateway, Eureka, RabbitMQ, database-per-service và bounded context trong hồ sơ hiện hữu |
| 2.5 | Business Flow | 22/08 | Quy trình nghiệp vụ chi tiết | Có một phần trong sơ đồ/spec | Cần baseline các luồng khám, xét nghiệm, cấp phát thuốc, thanh toán và thông báo |
| 2.6 | Tổng hợp Design v1.0 | 23/08 | `giai-doan-2-design.md` | Chưa có bản chính thức | Tài liệu rải ở `docs/ai`, backend spec và ERD |

## 3. Phần GUI cần giải trình

### 3.1 GUI Standards

Hồ sơ phải nêu hệ thống màu, typography, spacing/grid, breakpoint, header/sidebar/footer, điều hướng, biểu mẫu, bảng dữ liệu, modal, notification và quy tắc accessibility. Mỗi component phải có trạng thái normal, hover/focus, disabled, loading, empty, success và error. Quy tắc phải chỉ rõ phần dùng chung và khác biệt giữa Web Next.js với Mobile Flutter.

### 3.2 Wireframe và mockup

Danh mục tối thiểu phải phủ đăng nhập, dashboard, bệnh nhân, lịch hẹn/tiếp nhận, khám và hồ sơ, xét nghiệm, đơn thuốc/cấp phát, hóa đơn/thanh toán, thông báo và báo cáo. Mỗi luồng phải thể hiện actor, điểm bắt đầu/kết thúc, validation, lỗi, quyền truy cập và liên kết Use Case/API.

Kết luận phạm vi: code Web/Mobile hoàn chỉnh thuộc kế hoạch tháng 10. Giai đoạn 2 chỉ bắt buộc hoàn thiện thiết kế giao diện, nhưng thiết kế này hiện vẫn trễ vì chưa có bộ wireframe/mockup có thể kiểm duyệt cho cả hai nền tảng.

## 4. Phần Database, kiến trúc và events cần giải trình

### 4.1 ERD và DDL

- Mỗi microservice sở hữu database/schema riêng; liên kết sang context khác chỉ lưu UUID, không tạo quan hệ JPA/cross-service DB.
- Mỗi bảng phải có PK/FK nội bộ, unique/check/index, quy tắc audit, trạng thái và quy tắc xóa/lưu giữ.
- DDL phải có migration theo thứ tự, chạy sạch trên database mới và có chiến lược rollback/forward-fix.
- Báo cáo phải xử lý chênh lệch hiện tại: README ERD ghi 25 bảng nghiệp vụ, trong khi tám backend spec mô tả 19 bảng nghiệp vụ cộng 5 bảng khử trùng lặp, tổng 24 bảng.

### 4.2 Kiến trúc microservices

Sơ đồ phải thể hiện client Web/Mobile gọi gateway; gateway thực hiện routing/xác thực; discovery quản lý địa chỉ dịch vụ; mỗi business service sở hữu dữ liệu; RabbitMQ vận chuyển integration event; luồng đồng bộ chỉ dùng khi cần dữ liệu tức thời và phải có timeout/circuit breaker. Mỗi service cần ghi trách nhiệm, API công khai, database, event phát/nhận và dependency.

### 4.3 Luồng sự kiện

Event catalog hiện hành có 18 event chuẩn và mô hình saga thanh toán. Báo cáo thiết kế phải có ma trận producer-consumer, trigger, payload schema, version, correlation/causation ID, idempotency key, retry, dead-letter queue và cách xử lý sự kiện đến sai thứ tự. Cần thống nhất điểm mâu thuẫn `prescription.dispense.failed`: event map có nhưng catalog chuẩn chưa liệt kê.

Lưu ý trạng thái thực thi: tại thời điểm kiểm kê, mã Java chưa có publisher dùng `RabbitTemplate` và chưa có consumer dùng `@RabbitListener`. Vì vậy chỉ được báo cáo là “đã thiết kế”, không được ghi “đã triển khai”.

## 5. Phần đã làm được gửi kiểm tra ngay

- [GUI Standards/wireframe bản khung](../gui-standards-wireframes-draft.md).
- [Audit ERD/DDL/events và ma trận publish-subscribe](../erd-ddl-events-audit.md).
- [Bộ 11 ERD](../../eproject_general_plan/erd/README.md) có nguồn Mermaid và bản xuất SVG/PNG.
- [Backend implementation-ready specs](../../eproject_general_plan/backend-spec/README.md).
- [Kiến trúc, API, event, bảo mật, frontend và Flutter blueprint](../../ai/README.md).

## 6. Nguyên nhân trễ và hành động bù tiến độ

Nguyên nhân có thể xác nhận là nhóm phát triển nhiều tài liệu nền nhưng chưa quản lý deliverable theo một Design v1.0 có owner, reviewer và gate. GUI không có artifact nguồn; DDL chưa chuyển hết thành migration; event catalog và số bảng còn mâu thuẫn. Việc triển khai code sớm ở một số module không thay thế nghĩa vụ chốt thiết kế.

| Ưu tiên | Hành động | Chủ trì | Người rà soát | Điều kiện hoàn tất |
|---|---|---|---|---|
| P0 | Chốt GUI Standards dùng chung | Hoàng Anh | Phúc | Có token, component, state, responsive và accessibility |
| P0 | Hoàn thiện wireframe Web/Mobile | Hoàng Anh, Phúc | Vinh | Đủ screen inventory và flow chính, có file nguồn + export |
| P0 | Chốt service inventory và topology | Vinh | Cả nhóm | Một sơ đồ và một bảng trách nhiệm thống nhất |
| P0 | Đồng bộ ERD, số bảng và DDL | Huy, Vinh | Phúc | ERD khớp spec; migration chạy sạch cho từng service |
| P0 | Chốt event catalog và failure policy | Vinh | Huy | Không còn event ngoài catalog; producer-consumer rõ ràng |
| P1 | Hoàn thiện Business Flow | Cả nhóm | Vinh | Happy/alternate/error/compensation path đầy đủ |
| P1 | Đóng gói Design v1.0 | Vinh | Cả nhóm | Có version, traceability, review log và chữ ký |

Lịch bù chi tiết dùng chung nằm tại [lộ trình bù tiến độ](../recovery-roadmap.md).

## 7. Xác nhận trước khi nộp

| Vai trò | Người xác nhận | Phạm vi | Ngày | Kết quả |
|---|---|---|---|---|
| Nhóm trưởng/Backend | Phạm Đăng Vinh | Kiến trúc, API, event và gói Design |  |  |
| Frontend/Mobile | Trần Hoàng Anh | GUI Standards, wireframe và luồng giao diện |  |  |
| Database/QA | Lê Quang Huy | ERD, DDL, NFR và khả năng kiểm thử |  |  |
| Database/Frontend | Nguyễn Hoàng Phúc | Mockup, schema và rà soát truy vết |  |  |

## 8. Kết luận đề nghị giáo viên xem xét

Nhóm đã có nền tảng kiến trúc, backend spec và ERD đủ để giáo viên đánh giá hướng thiết kế, nhưng chưa đủ điều kiện công nhận Giai đoạn 2 hoàn thành. Nhóm xin nộp các bản nháp hiện có, xử lý các mâu thuẫn và bổ sung GUI/wireframe, migration, Business Flow trước khi baseline Design v1.0.

