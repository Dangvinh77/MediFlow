# BÁO CÁO GIẢI TRÌNH GIAI ĐOẠN 1

## Khởi động và Đặc tả yêu cầu

**Dự án:** MediFlow - Hệ thống quản lý phân tán bệnh viện/phòng khám  
**Kỳ báo cáo:** 03/08/2026 - 09/08/2026  
**Ngày lập giải trình:** 02/09/2026  
**Người chịu trách nhiệm tổng hợp:** Phạm Đăng Vinh - Nhóm trưởng  
**Tình trạng:** Trễ hạn; đã phục hồi bản nháp để xin ý kiến và chốt SRS v1.0.

## 1. Căn cứ và mục tiêu phải giải trình

Kế hoạch của giáo viên yêu cầu Giai đoạn 1 làm rõ vấn đề của đề tài, phân tích yêu cầu khách hàng theo tài liệu hướng dẫn, xác định vai trò người dùng và chức năng tương ứng, đồng thời hoàn thành bộ biểu mẫu ban đầu. Kế hoạch MediFlow cụ thể hóa thành bảy đầu việc từ kickoff đến SRS v1.0, hạn cuối ngày 09/08/2026.

Vì vậy báo cáo Giai đoạn 1 phải trả lời được:

1. Dự án giải quyết vấn đề nào, cho tổ chức/người dùng nào và trong phạm vi nào?
2. Ai là actor của hệ thống; mỗi actor được thực hiện chức năng nào?
3. Danh sách Use Case đã đủ, không trùng lặp và có tiêu chí chấp nhận chưa?
4. NFR của hệ thống phân tán đã có chỉ tiêu đo được chưa?
5. Biểu mẫu nghiệp vụ nào đã thu thập, nguồn nào xác nhận và còn biểu mẫu nào thiếu?
6. Ai lập, ai rà soát, ai phê duyệt SRS; phiên bản được khóa ở đâu?

## 2. Đối chiếu kế hoạch với tình trạng thực tế

| STT | Cam kết trong kế hoạch | Hạn | Đầu ra phải có | Tình trạng tại 02/09 | Bằng chứng/ghi chú |
|---|---|---:|---|---|---|
| 1.1 | Họp kickoff về mục tiêu và phạm vi | 03/08 | Biên bản họp | Chưa tìm thấy artifact độc lập | Cần bổ sung ngày họp, người dự, quyết định và action item |
| 1.2 | Xác định Problem Statement | 04/08 | Phần 1.1 SRS | Có thể phục hồi từ hồ sơ thiết kế | Đã đưa vào SRS bản nháp |
| 1.3 | Xác định vai trò người dùng | 05/08 | Bảng vai trò | Có nhưng chưa thống nhất | Kế hoạch dùng 3 vai trò tổng quát; tài liệu RBAC hiện hành dùng 9 vai trò |
| 1.4 | Phân tích yêu cầu chức năng | 06/08 | Danh sách Use Case | Có dữ liệu nguồn, chưa được ký chốt | Có thể truy vết từ 56 endpoint đặc tả |
| 1.5 | Phân tích NFR | 07/08 | Danh sách NFR | Có yêu cầu định tính, thiếu ngưỡng nghiệm thu | Đã có bảo mật, resilience, logging; thiếu SLA, tải, RTO/RPO và retention định lượng |
| 1.6 | Hoàn thành biểu mẫu ban đầu | 08/08 | Bộ biểu mẫu | Chưa có manifest bàn giao | Cần liệt kê form, nguồn cung cấp, phiên bản và trạng thái xác nhận |
| 1.7 | Tổng hợp SRS v1.0 | 09/08 | `giai-doan-1-srs.md` | Bản chính thức chưa tồn tại đúng hạn | Bản phục hồi ngày 02/09 chỉ là bản nháp để review |

## 3. Nội dung nghiệp vụ phải trình bày trong SRS

### 3.1 Vấn đề và phạm vi

- Bối cảnh vận hành hiện tại của bệnh viện/phòng khám và điểm nghẽn cần giải quyết.
- Mục tiêu đo được của MediFlow, đối tượng sử dụng và giá trị kỳ vọng.
- Phạm vi trong giai đoạn học kỳ: quản lý tổ chức, bệnh nhân, khám lâm sàng, xét nghiệm, dược, thanh toán, thông báo và báo cáo.
- Ngoài phạm vi, giả định và phụ thuộc: hạ tầng, dữ liệu khảo sát, hệ thống ngoài và trách nhiệm người dùng.
- Thuật ngữ nghiệp vụ thống nhất để tránh dùng đồng thời nhiều tên cho cùng một vai trò hoặc quy trình.

### 3.2 Actor và phân quyền

Kế hoạch khởi động nêu ba nhóm tổng quát `Administrator`, `Customer`, `Staff/Expert`. Thiết kế hiện hành đã chi tiết thành chín vai trò RBAC. Báo cáo phải có bảng ánh xạ từ vai trò tổng quát sang vai trò nghiệp vụ cụ thể, nêu rõ quyền xem/tạo/sửa/duyệt và giới hạn dữ liệu. Nhóm cần phê duyệt một danh mục actor duy nhất và dùng nhất quán trong SRS, API, GUI và kiểm thử.

### 3.3 Use Case

Mỗi Use Case tối thiểu phải có: mã, tên, actor chính/phụ, mục tiêu, tiền điều kiện, kích hoạt, luồng chính, luồng thay thế/ngoại lệ, hậu điều kiện, dữ liệu vào/ra, quy tắc nghiệp vụ và tiêu chí chấp nhận. Danh mục phải phủ đủ tám bounded context nghiệp vụ và truy vết được sang màn hình, endpoint, bảng/event và test case.

### 3.4 NFR cho hệ thống phân tán

Nhóm đã có nền tảng định tính về JWT/RBAC, database-per-service, timeout, circuit breaker, idempotency, dead-letter queue, correlation ID và rate limit. Trước khi chốt phải bổ sung ngưỡng đo cho:

- hiệu năng: percentile thời gian đáp ứng, tải đồng thời và throughput;
- khả dụng: mục tiêu uptime, timeout, số lần retry và tiêu chí degraded mode;
- phục hồi: RTO, RPO, backup và diễn tập khôi phục;
- bảo mật: vòng đời token, khóa tài khoản, audit trail và dữ liệu nhạy cảm;
- quan sát: tỷ lệ log/trace, thời gian lưu giữ, cảnh báo và correlation xuyên dịch vụ;
- khả năng mở rộng, tương thích Web/Mobile, accessibility và tiêu chí bảo trì/kiểm thử.

### 3.5 Bộ biểu mẫu ban đầu

Mỗi biểu mẫu phải ghi tên, quy trình sử dụng, người cung cấp/xác nhận, trường dữ liệu, quy tắc kiểm tra, phân quyền, phiên bản và liên kết Use Case. Nếu dùng biểu mẫu giả lập thay khảo sát thực tế, phải ghi rõ giả định để giáo viên đánh giá.

## 4. Phần đã làm được gửi kiểm tra ngay

- [SRS phục hồi - bản nháp](../giai-doan-1-srs.md), được tổng hợp từ kế hoạch, backend spec, API, RBAC và tài liệu kiến trúc.
- [Kiểm kê mã nguồn](../source-audit.md), ghi nhận 56 endpoint ở mức đặc tả, 9 vai trò RBAC hiện hành và mức triển khai của từng nền tảng.
- Các đặc tả triển khai tại [`docs/eproject_general_plan/backend-spec`](../../eproject_general_plan/backend-spec/README.md).
- Chuẩn API, bảo mật, event và kiến trúc tại [`docs/ai`](../../ai/README.md).

Các tài liệu trên là minh chứng tiến độ, chưa phải bằng chứng SRS đã được nhóm và giáo viên phê duyệt.

## 5. Nguyên nhân trễ cần trình bày minh bạch

Nguyên nhân có thể xác nhận từ repository là đầu ra bị phân tán vào backend spec, tài liệu kiến trúc và ERD nhưng không có bước đóng gói thành SRS độc lập; checklist và biên bản phê duyệt không được cập nhật. Ngoài ra, actor và NFR chưa được chốt thành một baseline có thể kiểm thử. Các nguyên nhân ngoài repository như lịch học, lịch khảo sát hoặc thay đổi phân công chỉ được bổ sung khi có xác nhận của nhóm.

## 6. Hành động bù tiến độ và điều kiện hoàn tất

| Ưu tiên | Hành động | Chủ trì | Người rà soát | Điều kiện hoàn tất |
|---|---|---|---|---|
| P0 | Chốt Problem Statement, scope và thuật ngữ | Vinh | Cả nhóm | Không còn mâu thuẫn giữa kế hoạch và SRS |
| P0 | Chốt bảng ánh xạ actor và RBAC | Vinh, Huy | Hoàng Anh | Một danh mục actor dùng chung toàn hồ sơ |
| P0 | Review Use Case theo từng bounded context | Cả nhóm | Vinh | Mỗi Use Case đủ luồng, rule và acceptance criteria |
| P0 | Định lượng NFR | Huy | Vinh | Mỗi NFR có metric, ngưỡng và cách kiểm chứng |
| P1 | Lập manifest biểu mẫu | Phúc | Huy | Mỗi form có nguồn, version, Use Case liên quan |
| P1 | Baseline SRS v1.0 và ký duyệt | Vinh | Cả nhóm | Có version, ngày duyệt, người duyệt và change log |

Lịch bù chi tiết dùng chung nằm tại [lộ trình bù tiến độ](../recovery-roadmap.md).

## 7. Xác nhận trước khi nộp

| Vai trò | Người xác nhận | Nội dung xác nhận | Ngày | Kết quả |
|---|---|---|---|---|
| Nhóm trưởng/Backend | Phạm Đăng Vinh | Scope, SRS, API và baseline |  |  |
| Frontend/Mobile | Trần Hoàng Anh | Actor, screen flow và tính khả thi giao diện |  |  |
| Database/QA | Lê Quang Huy | NFR, dữ liệu và khả năng kiểm thử |  |  |
| Database/Frontend | Nguyễn Hoàng Phúc | Biểu mẫu và truy vết |  |  |

## 8. Kết luận đề nghị giáo viên xem xét

Nhóm chưa đủ cơ sở tuyên bố Giai đoạn 1 hoàn thành đúng hạn. Nhóm xin nộp bản SRS phục hồi để giáo viên kiểm tra phạm vi, actor, Use Case và NFR; sau khi xử lý ý kiến và đủ chữ ký nội bộ, tài liệu mới được nâng thành SRS v1.0 chính thức.

