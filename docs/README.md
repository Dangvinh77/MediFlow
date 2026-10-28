# MediFlow — tài liệu hệ thống thông tin bệnh viện cho ngoại trú

Phân công mới 27/10: Vinh phụ trách toàn bộ backend và frontend Billing/Notification trước đây
của Lộc; Huy giữ FE Pharmacy/Report, Hoàng Anh giữ FE Organization/Patient.
[Đánh giá phần đã xong, dependency và nghiệm thu còn thiếu](plans/17-backend-ownership-and-blocker-audit-2026-10-27.md).
[Đóng nghiệm thu backend và kiểm tra phục hồi thanh toán](plans/18-backend-closeout-2026-10-27.md).

Quy tắc nghiệp vụ cập nhật 25/10/2026: đọc [luồng khám, tái khám và BHYT mô phỏng](plans/02-business-flows.md)
trước khi triển khai hoặc viết kịch bản bảo vệ. [Kế hoạch điều chỉnh](plans/07-outpatient-workflow-implementation.md)
phân biệt rõ yêu cầu mới với binary/demo hiện tại; BHYT giả định còn hạn, hỗ trợ theo tỷ lệ do dự án chọn.

**Tên mô tả thống nhất:** MediFlow: A Microservices-Based Hospital Information System for Outpatient Care Management.

MediFlow giữ định hướng HIS, triển khai MVP ngoại trú cho phòng khám đa khoa hoặc khoa khám bệnh ngoại trú của bệnh viện. Phạm vi gồm định danh/tổ chức, lịch hẹn/tiếp nhận, khám và hồ sơ, Lab, kê đơn/cấp thuốc, viện phí, báo cáo và thông báo in-app. Nội trú, giường bệnh và phẫu thuật hoãn; giữ nguyên source/data để phát triển sau. Không dùng phạm vi yêu cầu làm bằng chứng mọi tính năng đã nghiệm thu.

Bộ tài liệu chính hiện nằm trong [docs/plans](plans/README.md).

Sau chuẩn hóa 26/10, xem [registry bàn giao hiện hành](handoffs/README.md) và
[biên bản xử lý tài liệu](plans/08-documentation-cleanup-2026-10-26.md).
Checkpoint nằm trong `handoffs/history`, nội trú/Surgery trong `handoffs/deferred`;
di chuyển tài liệu không tự đóng các acceptance còn thiếu.

- [Thiết kế hệ thống](plans/01-system-design.md)
- [Luồng nghiệp vụ](plans/02-business-flows.md)
- [Hợp đồng tích hợp](plans/03-contracts.md)
- [WF-01: đối chiếu source và khoảng trống hợp đồng](plans/09-outpatient-source-lock-2026-10-26.md)
- [Hợp đồng lịch khám/giữ suất và ma trận nghiệm thu](plans/10-scheduling-contract-acceptance.md)
- [Source NURSE onboarding đã triển khai](plans/11-patient-onboarding-source-acceptance-2026-10-27.md)
- [Tiếp tục nghiệm thu và dọn checklist 27/10](plans/13-onboarding-closeout-2026-10-27.md)
- [Phục hồi onboarding và Lab trong lượt mở](plans/14-onboarding-recovery-acceptance-2026-10-27.md)
- [Dữ liệu và độ tin cậy](plans/04-data-reliability.md)
- [Plan 18 task và 72 subtask](plans/05-implementation-plan.md)
- [Nghiệm thu và vận hành](plans/06-acceptance-runbook.md)
- [Chạy và test ngoại trú V2 theo role — 26/10](../scripts/demo_25_10/MediFlow_Ngoai_Tru_V2_Test_2026-10-26.md)
- [Quy tắc coding](ai/README.md)

Compose mặc định có 6 business deployment ngoại trú: Core, Clinical, Lab, Pharmacy, Billing, Support. Core chứa Organization/Patient; [Support host](../backend/support-service/README.md) nhúng hai plain libraries Report/Notification, giữ launchers cũ để rollback. Gateway/Eureka là hạ tầng, common là thư viện. 8 context stores active vẫn tách biệt; không gộp DB để đạt số 6. Ngày 2026-10-17 đã đạt real PG/Rabbit, packaged Support restart và luồng EXAM/Rx thật trong sáu apps qua Gateway, gồm current-store rollback giữ accepted writes. Sau đó đã cold-copy chính persisted Compose volumes hiện có, inventory hai Rabbit nodes cũ và đạt Support cutover/rollback/broker restart giữ bốn writes mới; original-volume hashes không đổi. Huy 48/48 cho môi trường local đã quan sát, không tự bật activation gates hoặc ký thay dữ liệu môi trường khác. Evidence ở [runbook Huy mục 8–9](plans/huy-mvp-cutover-runbook.md#9-local-persisted-volume-inventory-and-cutover--2026-10-17).

Inpatient/Surgery và V2 nâng cao tạm hoãn.
Code, database và các thay đổi FE được giữ nguyên. Các đường dẫn cũ chỉ là chỉ dẫn đọc,
không một bộ spec/backlog thứ hai hoặc bằng chứng đã triển khai topology mới.

Checkpoint mới: [hoàn thiện trước vòng thử trực tiếp](plans/15-demo-readiness-closeout-2026-10-27.md),
[bằng chứng broker/UI/frontend](acceptance/demo-readiness-2026-10-27/README.md) và
[runbook cho Vinh, Huy, Hoàng Anh](plans/16-three-developer-demo-acceptance-2026-10-27.md).
