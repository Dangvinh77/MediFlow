# MediFlow — thiết kế MVP ngoại trú

Cập nhật nghiệp vụ ngày 2026-10-25; giữ baseline kiến trúc sáu deployments từ 14/10.
Đây là bộ tài liệu đích mới, thay các thiết kế, spec, plan và handoff cũ trong thư mục docs.
Code chưa được chuyển thành 6 service chỉ bằng việc thay tài liệu.

Định vị thống nhất: **MediFlow: A Microservices-Based Hospital Information System for Outpatient Care Management**. Giữ định hướng HIS; phạm vi đồ án là phòng khám đa khoa hoặc khoa khám bệnh ngoại trú của bệnh viện, không phải toàn bộ quy trình nội trú/phẫu thuật. Xem [định vị và CURRENT/TARGET](01-system-design.md).

Source đã có Core chứa Organization/Patient và Support chứa Report/Notification. Scheduling/quote và slice NURSE onboarding đã có implementation; trạng thái source, packaged API và UI được ghi riêng trong các biên bản. Không tự đóng gate nghiệm thu chỉ vì có code.

## Đọc gì

Tiếp tục sau merge PR #546: [phục hồi onboarding và đọc Lab trong lượt mở](14-onboarding-recovery-acceptance-2026-10-27.md),
[bằng chứng 107 + 99 Gateway observations](../acceptance/onboarding-recovery-2026-10-27/README.md).

| Tài liệu | Nội dung |
|---|---|
| [Thiết kế hệ thống](01-system-design.md) | 6 service, phạm vi MVP, ownership, CURRENT và TARGET |
| [Luồng nghiệp vụ](02-business-flows.md) | Khám ngoại trú, Lab, thuốc, thanh toán và các trường hợp lỗi |
| [Hợp đồng tích hợp](03-contracts.md) | REST, event, identifier, quyền và tương thích với code |
| [Dữ liệu và độ tin cậy](04-data-reliability.md) | Database, transaction, outbox/inbox, concurrency và recovery |
| [Plan triển khai baseline](05-implementation-plan.md) | 18 task/72 subtask và evidence kỹ thuật lịch sử |
| [Điều chỉnh nghiệp vụ 25/10](07-outpatient-workflow-implementation.md) | WF-01–11: lịch khám, tiếp nhận, tái khám, BHYT mô phỏng, cleanup tài liệu |
| [Chuẩn hóa tài liệu 26/10](08-documentation-cleanup-2026-10-26.md) | Phân loại current/history/deferred, registry hiện hành và danh sách file di chuyển/xóa |
| [WF-01 source audit 26/10](09-outpatient-source-lock-2026-10-26.md) | API/DTO/event/gates hiện có, fingerprints và G-01–11 còn cần khóa hợp đồng |
| [Nghiệm thu hợp đồng lịch khám/giữ suất](10-scheduling-contract-acceptance.md) | Wire trong 03, JSON Schema/ví dụ và SC-01–20; implementation có gate, trạng thái runtime/UI có bằng chứng riêng |
| [Source ONB 27/10](11-patient-onboarding-source-acceptance-2026-10-27.md) | ONB-01–05 đã merge; module/frontend tests và giới hạn nghiệm thu |
| [Dọn mã đã hoàn thành](12-unused-code-cleanup-2026-10-27.md) | Biên bản cleanup PR #545; giữ Surgery/Inpatient và các cổng mở rộng |
| [Tiếp tục nghiệm thu ONB](13-onboarding-closeout-2026-10-27.md) | Candidate mới, runner login muộn, hồi quy và dọn checklist đã xong |
| [Plan backend riêng Huy](2026-10-14-huy-backend-mvp-adjustment-plan.md) | Phân rã M6: 12 task/48 subtask cho Pharmacy, Report và phối hợp Support; Surgery hoãn |
| [Nghiệm thu và vận hành](06-acceptance-runbook.md) | Test matrix, demo, triển khai, rollback và bằng chứng |
| [Quy tắc coding](../ai/README.md) | Backend, FE/mobile, ownership và Git |

Tài liệu 01–06 và quy tắc coding chứa nội dung canonical; 07 là kế hoạch thực hiện và sổ xử lý tài liệu phái sinh, không tạo nguồn contract thứ hai. Các file ở đường dẫn cũ còn
được AGENTS/README/skill tham chiếu, nếu tồn tại, là chỉ dẫn ngắn tới bộ này, không phải một
bản spec hoặc backlog thứ hai. Không dùng checklist lịch sử để giao thêm việc ngoài MVP.

Plan riêng Huy là workboard triển khai phái sinh từ M6, không nguồn kiến trúc/contract mới;
48 subtask ở đó không cộng vào 72 subtask để tính tiến độ toàn team.

Evidence phần Huy: [baseline/source manifest](huy-backend-mvp-baseline.md) và
[migration/fixture hashes](huy-backend-mvp-protected-files.json); đây không nguồn schema thứ hai.

Chuẩn bị chuyển đổi phần Huy: [inventory chỉ đọc, cutover và rollback](huy-mvp-cutover-runbook.md).
Checklist này không là bằng chứng deployment hoặc cấp quyền sửa host/shared modules.

Kiểm chứng batch 6: [10 phần việc và kết quả PostgreSQL/RabbitMQ](huy-mvp-batch-6-acceptance.md).
Tiếp nối batch 7: [current Billing clearance và selective delivery](huy-backend-mvp-baseline.md#12-current-clearance-and-selective-outpatient-delivery),
và [batch 8 runtime acceptance](huy-mvp-batch-8-acceptance.md). Plan Huy có các checkpoint lịch sử riêng; kết quả local mới hơn xem runbook cutover mục 8–9.
Producer rollout và nghiệm thu nghiệp vụ 25/10 vẫn có gate riêng.

## Quyết định phạm vi

MVP gồm Core, Clinical, Lab, Pharmacy, Billing và Support. Gateway/Eureka là hạ tầng, không
tính vào 6 service nghiệp vụ. Tạm dừng Inpatient/Surgery, không xóa code hoặc dữ liệu của chúng.
Notification chỉ cần in-app; Report chỉ cần số liệu ngoại trú theo sự kiện và đối soát thu/hoàn tiền.

Không gộp database trong đợt này, không nâng framework, không thiết kế lại UI, không xây toàn bộ
Report/Billing V2, không phục hồi toàn bộ lịch sử trước V2 để đạt điều kiện MVP. Nguồn lịch sử
không có vẫn phải ghi unavailable; không tạo dữ liệu hoặc biến thiếu dữ liệu thành zero.

## Hiện trạng và thẩm quyền

Baseline đọc source: Huy, commit c110671bd2eaa7f1e948879162506df106cbf27d, có thay đổi FE chưa commit.
Baseline không là xác nhận master remote mới nhất. Mọi task bắt đầu phải refresh source/test.
Quyết định giảm scope được ghi nhận từ người dùng; quyền sửa production module/shared paths vẫn
theo owner hoặc giao scope rõ ràng. Không tự push, thay database hoặc mở feature gate.

Chỉ đánh dấu triển khai xong khi code, producer/consumer, database/broker và Gateway acceptance
đều đạt. Bộ tài liệu này không chứng nhận backend hiện tại đã hoàn thiện hoặc các gate đã bật.

## Thay tài liệu cũ

Đã kiểm kê 374 file cũ; rà tài liệu nghiệp vụ/spec/contract, source và tài liệu Word/PDF trước
khi viết lại. Các báo cáo theo giai đoạn là bằng chứng lịch sử, không là backlog hiện tại.
Bản sao đầy đủ được lưu ngoài repo và kiểm tra hash từng file trước khi thay thế, kể cả file
chưa commit. Tài liệu trong frontend/docs và backend/**/README.md không thuộc phạm vi xóa này.

## Lượt thử chung tiếp theo

[Phân công backend/frontend và đánh giá blocker 27/10](17-backend-ownership-and-blocker-audit-2026-10-27.md)
thay phân công backend cũ: Vinh phụ trách toàn backend, FE của Lộc chuyển Vinh quản lý.
[Lượt đóng backend](18-backend-closeout-2026-10-27.md) ghi kết quả chạy lại và lỗi
receipt timestamp tìm được ở PostgreSQL; đọc kết quả từng profile, không cộng test lặp.

[Plan 15 đã hoàn tất phần kỹ thuật](15-demo-readiness-closeout-2026-10-27.md),
[bằng chứng](../acceptance/demo-readiness-2026-10-27/README.md),
[runbook ba developer](16-three-developer-demo-acceptance-2026-10-27.md).
Checklist baseline 18/72 và các checkpoint 11–14 giữ vai trò lịch sử; không cộng chúng
thành backlog mới. ONB/WF/SC và handoff chưa có đủ nghiệm thu vẫn giữ trạng thái hiện hành.

[Patient welcome replay 28/10](19-patient-welcome-replay-closeout-2026-10-28.md) đóng H-02
sau actual Core producer / Support consumer restart; giữ riêng giới hạn SMTP và UX.
