# Hồ sơ giám sát tiến độ MediFlow

**Ngày rà soát:** 02/09/2026  
**Mốc dữ liệu:** commit `eaccf0955ec37616dde8027efee0a721b91e20d2` trên nhánh `master`  
**Mục đích:** gửi ngay phần đã làm được, chỉ rõ phần còn thiếu và chốt lộ trình bù tiến độ.

## Kết luận điều hành

| Hạng mục | Hạn | Kết luận tại 02/09 | Bằng chứng chính |
|---|---:|---|---|
| SRS v1.0 | 09/08 | **Chưa hoàn thành/chưa được chốt** | Không tồn tại `docs/plan/giai-doan-1-srs.md`; Use Case và NFR nằm rải rác trong backend spec/coding rules |
| GUI Standards + wireframe/mockup | 18/08 | **Chưa hoàn thành** | Không có file GUI Standards, wireframe hay mockup; Web mới có 3 trang demo, Mobile không có mã Dart |
| ERD + DDL | 20/08 | **ERD đã có; DDL mới hoàn tất ở mức đặc tả** | Có 11 sơ đồ, mỗi sơ đồ có `.mmd/.svg/.png`; 8 spec có DDL nhúng, nhưng chỉ pharmacy có Flyway SQL thật |
| Kiến trúc microservices | 21/08 | **Đã thiết kế tương đối đầy đủ, chưa được đóng gói thành hồ sơ giai đoạn 2** | Có topology, 8 bounded context, gateway, Eureka, RabbitMQ và saga trong `docs/ai` + ERD |
| Hồ sơ thiết kế hoàn chỉnh | 31/08 | **Chưa nộp được theo Definition of Done** | Thiếu 4 file đầu ra theo kế hoạch; checklist tháng 8 vẫn để trống; tài liệu còn mâu thuẫn; build đang đỏ |

## Trả lời trực tiếp bốn câu hỏi

### 1. SRS, Use Case và NFR

File chính thức `giai-doan-1-srs.md` **không tồn tại trước lần rà soát này**, nên không thể xác nhận “đã hoàn thành”. Danh sách chức năng có thể khôi phục từ 56 endpoint đã đặc tả; RBAC có 9 vai trò hiện hành. Tuy nhiên kế hoạch cũ vẫn ghi 3 vai trò tổng quát `Administrator/Customer/Staff-Expert`, nên danh sách actor chưa được phê duyệt nhất quán.

NFR đã có nền tảng về JWT/RBAC, tách database, timeout, circuit breaker, idempotency, DLQ, correlation id và rate limit. Các chỉ tiêu nghiệm thu quan trọng như thời gian đáp ứng, mức sẵn sàng, RTO/RPO, tải đồng thời, dung lượng và lưu giữ log **chưa có số đo được phê duyệt**. Bản SRS phục hồi để nhóm review nằm tại [giai-doan-1-srs.md](giai-doan-1-srs.md).

### 2. GUI Web và Mobile

**Chưa hoàn thiện trên cả hai nền tảng.**

- Web Next.js có trang chủ, đăng nhập và danh sách bệnh nhân; 7 file TypeScript/TSX + 1 CSS. Các feature còn lại chỉ có `.gitkeep`. `typecheck` đạt, nhưng `lint` lỗi tại trang bệnh nhân nên chưa qua quality gate.
- Mobile Flutter chỉ có cây thư mục rỗng bằng `.gitkeep`; không có `pubspec.yaml`, `main.dart` hay file `.dart`, nên chưa phải ứng dụng có thể build.
- Theo kế hoạch, code Web/Mobile hoàn chỉnh thuộc tháng 10; phần **đã trễ ngày 18/08** là GUI Standards và wireframe/mockup. Bản khung bù tiến độ nằm tại [gui-standards-wireframes-draft.md](gui-standards-wireframes-draft.md).

### 3. ERD, DDL và events

ERD đã được dựng khá đầy đủ về góc nhìn: tổng quan, quy trình khám, saga thanh toán, vòng đời bệnh nhân, tổ chức theo khoa, event map, dược, báo cáo, RBAC và hồ sơ bệnh án. Tuy vậy chưa thể coi toàn bộ “ERD + DDL” là bản final vì:

- README ERD ghi **25 bảng nghiệp vụ**, trong khi 8 backend spec hiện khai báo **19 bảng nghiệp vụ + 5 bảng khử trùng lặp = 24 bảng tổng**;
- chỉ `pharmacy-service` có migration SQL thật (5 bảng); 7 service còn lại chưa có Flyway SQL;
- event map có `prescription.dispense.failed`, nhưng catalog chuẩn 18 event chưa liệt kê event này;
- mã chạy hiện có 0 publisher, 0 `@RabbitListener` và 0 `@PreAuthorize` trong business service.

Chi tiết và ma trận publish/subscribe nằm tại [erd-ddl-events-audit.md](erd-ddl-events-audit.md).

### 4. Vì sao sang tháng 9 vẫn chưa có hồ sơ hoàn chỉnh

Từ bằng chứng trong repository, nguyên nhân trực tiếp là **không có cơ chế đóng gói và quality gate cho deliverable tháng 8**: bốn file đầu ra theo kế hoạch chưa được tạo, checklist không được cập nhật, nỗ lực tháng 8 phân tán vào tooling/service spec/ERD, trong khi GUI và SRS không có artifact độc lập. Việc rà soát chéo đã không xảy ra hoặc không được ghi nhận, nên mâu thuẫn actor, tên bảng, số bảng và event vẫn còn. Ngoài ra build hiện tại thất bại, vì vậy nhóm chưa có cơ sở kỹ thuật để ký xác nhận hồ sơ “đã đồng bộ với code”.

Đây là kết luận từ lịch sử Git và nội dung repo; các nguyên nhân ngoài repo (lịch học, phân công thực tế, trao đổi với giáo viên) cần nhóm xác nhận riêng, không được suy đoán trong báo cáo này.

## Gói gửi kiểm tra ngay

### Ba bộ báo cáo giải trình theo giai đoạn

1. [Giai đoạn 1 - Khởi động và Đặc tả yêu cầu](01-giai-doan-1-khoi-dong-dac-ta/README.md).
2. [Giai đoạn 2 - Thiết kế Giao diện và Tiêu chuẩn](02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/README.md).
3. [Giai đoạn 3 - Phân chia chi tiết và Hoàn thiện hồ sơ](03-giai-doan-3-phan-chia-hoan-thien-ho-so/README.md).

### Tài liệu minh chứng dùng chung

1. [source-audit.md](source-audit.md) — kiểm kê toàn bộ mã nguồn bằng codebase-memory-mcp và kiểm tra trực tiếp.
2. [giai-doan-1-srs.md](giai-doan-1-srs.md) — bản nháp phục hồi SRS, Use Case và NFR để review/chốt.
3. [gui-standards-wireframes-draft.md](gui-standards-wireframes-draft.md) — chuẩn GUI và wireframe văn bản tối thiểu.
4. [erd-ddl-events-audit.md](erd-ddl-events-audit.md) — trạng thái ERD/DDL, catalog events và các điểm mâu thuẫn.
5. [recovery-roadmap.md](recovery-roadmap.md) — lộ trình bù tiến độ theo ngày, owner, đầu ra và gate.
6. [submission-checklist.md](submission-checklist.md) — manifest hồ sơ nộp và điều kiện ký duyệt.

## Các tài sản đã có có thể gửi kèm ngay

- Kế hoạch: [`docs/plan/README.md`](../plan/README.md) và [`KeHoachDuAnMediFlow.docx`](../plan/KeHoachDuAnMediFlow.docx).
- Backend spec: [`docs/eproject_general_plan/backend-spec/`](../eproject_general_plan/backend-spec/README.md).
- Bộ ERD: [`docs/eproject_general_plan/erd/`](../eproject_general_plan/erd/README.md).
- Kiến trúc và chuẩn kỹ thuật: [`docs/ai/`](../ai/README.md).

## Quy ước trạng thái

- **Đã hoàn thành:** có artifact, nội dung nhất quán, có người duyệt và vượt quality gate.
- **Bản nháp:** có nội dung dùng để review nhưng còn quyết định mở hoặc chưa duyệt.
- **Đã thiết kế:** có hợp đồng/sơ đồ/spec nhưng chưa có mã chạy.
- **Đã triển khai:** có mã và test/build tương ứng vượt qua.
