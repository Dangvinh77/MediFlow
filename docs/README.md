# MediFlow — tài liệu hệ thống thông tin bệnh viện cho ngoại trú

**Tên mô tả thống nhất:** MediFlow: A Microservices-Based Hospital Information System for Outpatient Care Management.

MediFlow giữ định hướng HIS, triển khai MVP ngoại trú cho phòng khám đa khoa hoặc khoa khám bệnh ngoại trú của bệnh viện. Phạm vi gồm định danh/tổ chức, lịch hẹn/tiếp nhận, khám và hồ sơ, Lab, kê đơn/cấp thuốc, viện phí, báo cáo và thông báo in-app. Nội trú, giường bệnh và phẫu thuật hoãn; giữ nguyên source/data để phát triển sau. Không dùng phạm vi yêu cầu làm bằng chứng mọi tính năng đã nghiệm thu.

Bộ tài liệu chính hiện nằm trong [docs/plans](plans/README.md).

- [Thiết kế hệ thống](plans/01-system-design.md)
- [Luồng nghiệp vụ](plans/02-business-flows.md)
- [Hợp đồng tích hợp](plans/03-contracts.md)
- [Dữ liệu và độ tin cậy](plans/04-data-reliability.md)
- [Plan 18 task và 72 subtask](plans/05-implementation-plan.md)
- [Nghiệm thu và vận hành](plans/06-acceptance-runbook.md)
- [Quy tắc coding](ai/README.md)

Đích có 6 business deployment ngoại trú: Core, Clinical, Lab, Pharmacy, Billing, Support. Core đã chứa Organization/Patient; Report/Notification còn là ứng dụng riêng, Support host chưa có. Gateway/Eureka là hạ tầng, common là thư viện. 8 context stores active vẫn tách biệt; không gộp DB để đạt số 6.

Inpatient/Surgery và V2 nâng cao tạm hoãn.
Code, database và các thay đổi FE được giữ nguyên. Các đường dẫn cũ chỉ là chỉ dẫn đọc,
không một bộ spec/backlog thứ hai hoặc bằng chứng đã triển khai topology mới.
