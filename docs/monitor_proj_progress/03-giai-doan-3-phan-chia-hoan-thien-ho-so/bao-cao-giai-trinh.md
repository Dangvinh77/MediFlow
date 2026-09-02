# BÁO CÁO GIẢI TRÌNH GIAI ĐOẠN 3

## Phân chia chi tiết và Hoàn thiện hồ sơ thiết kế

**Dự án:** MediFlow  
**Kỳ báo cáo:** 24/08/2026 - 31/08/2026  
**Ngày lập giải trình:** 02/09/2026  
**Tình trạng:** Chưa hoàn thành; đang thực hiện kế hoạch bù và đóng gói hồ sơ.

## 1. Căn cứ và mục tiêu phải giải trình

Kế hoạch của giáo viên yêu cầu bảng phân công chi tiết cho giai đoạn code và kiểm duyệt toàn bộ gói tài liệu để bảo đảm đồng bộ, nhất quán. Kế hoạch MediFlow cụ thể hóa thành năm bước: phân công, rà soát chéo, chỉnh sửa v2.0, Final Review và nộp hồ sơ ngày 31/08/2026.

Báo cáo Giai đoạn 3 phải trả lời:

1. Mỗi task code thuộc service/feature nào, ai là owner, reviewer và deadline?
2. Definition of Done và bằng chứng nghiệm thu của từng task là gì?
3. SRS, GUI, API, Database, events và test đã được rà soát chéo theo ma trận nào?
4. Những sai lệch nào đã phát hiện, ai xử lý, trạng thái đóng/mở và ảnh hưởng ra sao?
5. Tài liệu v2.0 khác v1.0 ở điểm nào và ai phê duyệt?
6. Final Review đã kiểm tra đủ nội dung, định dạng, link, version và quality gate chưa?
7. Hồ sơ đã nộp qua kênh nào, lúc nào, ai nộp và có biên nhận gì?

## 2. Đối chiếu kế hoạch với tình trạng thực tế

| STT | Cam kết | Hạn | Đầu ra phải có | Tình trạng tại 02/09 | Bằng chứng/thiếu hụt |
|---|---|---:|---|---|---|
| 3.1 | Phân công chi tiết giai đoạn code | 27/08 | Bảng phân công | Chưa có file chính thức | Kế hoạch có vai trò chung nhưng chưa có task/service, reviewer, DoD và dependency |
| 3.2 | Rà soát SRS ↔ Design ↔ DB ↔ GUI | 28/08 | Biên bản rà soát | Chưa có biên bản đã ký | Audit ngày 02/09 mới ghi nhận các mâu thuẫn tồn tại |
| 3.3 | Chỉnh sửa sau rà soát | 29/08 | Tài liệu v2.0 | Chưa có baseline v2.0 | Chưa có change log và danh sách issue đã đóng |
| 3.4 | Final Review toàn bộ gói | 30/08 | Gói hoàn chỉnh | Chưa đạt quality gate | SRS/Design chính thức thiếu; build và lint còn lỗi |
| 3.5 | Nộp cho giáo viên | 31/08 | Hồ sơ + biên nhận | Chưa có bằng chứng nộp | Chưa ghi kênh, thời điểm, người nộp, link/biên nhận và commit |

## 3. Bảng phân công code phải chứa thông tin gì

Mỗi dòng công việc cần có: mã task, bounded context/service/feature, mô tả đầu ra theo lát cắt nghiệp vụ, owner, reviewer, người hỗ trợ, ngày bắt đầu, deadline, dependency, rủi ro, tiêu chí chấp nhận, test bắt buộc, tài liệu liên quan và trạng thái. Không chỉ giao theo chức danh chung; phải đủ chi tiết để xác định ai chịu trách nhiệm khi task bị chặn hoặc không qua gate.

Phân công sơ bộ trong kế hoạch:

| Thành viên | Vai trò đã cam kết | Phạm vi cần cụ thể hóa ở bảng code |
|---|---|---|
| Phạm Đăng Vinh | Nhóm trưởng / Backend Lead | Kiến trúc, gateway, service owner, tích hợp và đóng gói hồ sơ |
| Trần Hoàng Anh | Frontend Lead | Next.js, Flutter, GUI Standards và tích hợp API |
| Lê Quang Huy | Database / QA Lead | Migration, dữ liệu, test strategy và quality gate |
| Nguyễn Hoàng Phúc | Database / Frontend | Schema, GUI, test và giám sát/peer review |

## 4. Rà soát chéo và truy vết phải giải trình thế nào

Hồ sơ cần một ma trận `Use Case → actor/role → screen → API → service → table → event → test`. Mỗi hàng phải chỉ ra artifact và version cụ thể, trạng thái phù hợp/chưa phù hợp, issue sửa và người xác nhận. Các điểm bắt buộc xử lý trước Final Review gồm:

- actor kế hoạch ba nhóm tổng quát so với chín vai trò RBAC hiện hành;
- 25 bảng trong README ERD so với 24 bảng tổng hợp từ backend spec;
- event `prescription.dispense.failed` có trong event map nhưng thiếu trong catalog chuẩn;
- kế hoạch/README và cấu trúc service thực tế chưa đồng nhất tên/phạm vi;
- GUI chưa có bộ wireframe/mockup chính thức cho Web/Mobile;
- bảy service chưa có migration SQL thật; event publisher/consumer chưa triển khai;
- Maven test và frontend lint chưa đạt tại lần kiểm tra 02/09.

## 5. Vì sao sang tháng 9 vẫn chưa có hồ sơ hoàn chỉnh

Các nguyên nhân có bằng chứng trong repository:

1. Bốn deliverable đóng gói theo kế hoạch chưa được tạo đúng hạn: SRS v1.0, Design v1.0, bảng phân công code và biên bản Final Review.
2. Nội dung được làm rải rác trong `docs/ai`, backend spec và ERD, nhưng thiếu owner/reviewer chịu trách nhiệm baseline từng gói.
3. Checklist tháng 8 không được cập nhật thành cơ chế quality gate, nên các mâu thuẫn actor, service, bảng và event kéo dài đến lúc nộp.
4. GUI/wireframe và biểu mẫu không có manifest/file nguồn có thể kiểm tra.
5. Trạng thái code không đủ để xác nhận thiết kế đã đồng bộ: backend test và frontend lint đang lỗi; Mobile chưa có project chạy được.

Các lý do ngoài repository như lịch học, lịch cá nhân, thay đổi nhân sự hoặc trao đổi miệng với giáo viên phải có biên bản/xác nhận mới được bổ sung; báo cáo không suy đoán.

## 6. Phần đã làm được gửi kiểm tra ngay

- [Báo cáo tổng hợp tiến độ](../README.md).
- [Kiểm kê source bằng codebase-memory-mcp và quality gate](../source-audit.md).
- [SRS phục hồi bản nháp](../giai-doan-1-srs.md).
- [GUI Standards/wireframe bản khung](../gui-standards-wireframes-draft.md).
- [Audit ERD/DDL/events](../erd-ddl-events-audit.md).
- [Lộ trình bù tiến độ](../recovery-roadmap.md).
- [Manifest và checklist hồ sơ nộp](../submission-checklist.md).

## 7. Kế hoạch đóng hồ sơ và Definition of Done

| Gate | Chủ trì | Bằng chứng bắt buộc | Chỉ được đóng khi |
|---|---|---|---|
| SRS gate | Vinh | SRS v1.0, actor map, Use Case, NFR, form manifest | Có review log và chữ ký nhóm |
| GUI gate | Hoàng Anh | Standards, screen inventory, wireframe/mockup Web/Mobile | Có file nguồn/export và mapping Use Case |
| Data/event gate | Huy, Vinh | ERD, migration, event catalog, producer-consumer matrix | Không còn mâu thuẫn; migration chạy sạch |
| Traceability gate | Phúc | Ma trận SRS-GUI-API-DB-event-test | Mọi requirement có owner và test/acceptance |
| Code-readiness gate | Cả nhóm | Maven test, frontend typecheck/lint/build, mobile scaffold/gate | Các lệnh bắt buộc đạt hoặc exception được giáo viên chấp thuận |
| Final Review | Vinh | Manifest, version, change log, link check, chữ ký | Tất cả gate trước đã đóng |
| Submission | Vinh | Kênh, thời gian, người nộp, link/biên nhận, commit | Có bằng chứng truy xuất được |

Lịch ngày cụ thể và thứ tự xử lý nằm tại [lộ trình bù tiến độ](../recovery-roadmap.md).

## 8. Biên bản xác nhận và bằng chứng nộp

| Vai trò | Người ký | Phạm vi duyệt | Ngày | Kết quả |
|---|---|---|---|---|
| Nhóm trưởng/Backend | Phạm Đăng Vinh | Scope, architecture, API, gói nộp |  |  |
| Frontend/Mobile | Trần Hoàng Anh | GUI Standards và Web/Mobile flow |  |  |
| Database/QA | Lê Quang Huy | ERD, DDL, NFR, testability |  |  |
| Database/Frontend | Nguyễn Hoàng Phúc | Traceability và Final Review |  |  |

- Kênh nộp: ____________________
- Thời điểm nộp: ____________________
- Người nộp: ____________________
- Link/biên nhận: ____________________
- Phiên bản/commit: ____________________

## 9. Kết luận đề nghị giáo viên xem xét

Nhóm xác nhận chưa hoàn thành hồ sơ thiết kế đúng hạn 31/08. Nhóm xin gửi ngay toàn bộ bản nháp và bằng chứng kỹ thuật hiện có để giáo viên kiểm tra tiến độ thực tế; đồng thời thực hiện lộ trình bù theo các gate ở trên. Chỉ sau khi xử lý mâu thuẫn, ký duyệt và lưu bằng chứng nộp, Giai đoạn 3 mới được đánh dấu hoàn thành.

