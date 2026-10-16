# MediFlow thiết kế hệ thống tối giản

Cập nhật ngày 2026-10-14 theo yêu cầu giảm khối lượng công việc và tập trung minh chứng hệ thống phân tán.
Đây là bộ tài liệu đích mới, thay các thiết kế, spec, plan và handoff cũ trong thư mục docs.
Code chưa được chuyển thành 6 service chỉ bằng việc thay tài liệu.

## Đọc gì

| Tài liệu | Nội dung |
|---|---|
| [Thiết kế hệ thống](01-system-design.md) | 6 service, phạm vi MVP, ownership, CURRENT và TARGET |
| [Luồng nghiệp vụ](02-business-flows.md) | Khám ngoại trú, Lab, thuốc, thanh toán và các trường hợp lỗi |
| [Hợp đồng tích hợp](03-contracts.md) | REST, event, identifier, quyền và tương thích với code |
| [Dữ liệu và độ tin cậy](04-data-reliability.md) | Database, transaction, outbox/inbox, concurrency và recovery |
| [Plan triển khai](05-implementation-plan.md) | 18 task chính, 72 subtask, owner, dependency và tiêu chí đóng |
| [Plan backend riêng Huy](2026-10-14-huy-backend-mvp-adjustment-plan.md) | Phân rã M6: 12 task/48 subtask cho Pharmacy, Report và phối hợp Support; Surgery hoãn |
| [Nghiệm thu và vận hành](06-acceptance-runbook.md) | Test matrix, demo, triển khai, rollback và bằng chứng |
| [Quy tắc coding](../ai/README.md) | Backend, FE/mobile, ownership và Git |

Chỉ sáu tài liệu đánh số và quy tắc coding chứa nội dung canonical. Các file ở đường dẫn cũ còn
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
và [batch 8 runtime acceptance](huy-mvp-batch-8-acceptance.md). Plan Huy đạt 37/48 subtask;
producer rollout/Support và nghiệm thu chung vẫn có gate riêng.

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
