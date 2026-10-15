# Thiết kế hệ thống MediFlow tối giản

## Mục tiêu

Hoàn thiện một luồng ngoại trú có phối hợp nhiều service và bằng chứng xử lý lỗi từng phần.
Giảm việc bằng giảm phạm vi nghiệm thu, không chỉ đóng gói 10 nghiệp vụ vào 6 JVM.

## Sáu service đích

| Service | Nghiệp vụ tối thiểu | Module nguồn | Owner |
|---|---|---|---|
| core-service | Khoa, staff, account, phòng tham chiếu, patient identity | Organization + Patient | Hoàng Anh |
| clinical-service | Appointment, khám, diagnosis, chỉ định và completion ngoại trú | Clinical | Vinh |
| lab-service | Request, trạng thái, kết quả xét nghiệm | Lab | Vinh |
| pharmacy-service | Drug, stock reservation, prescription, dispense | Pharmacy | Huy |
| billing-service | Charge, invoice/request, payment, clearance và compensation/refund | Billing | Lộc |
| support-service | Report read model + in-app notification | Report + Notification | Huy/Report, Lộc/Notification |

Support do Huy điều phối đóng gói; việc đó không chuyển ownership Notification cho Huy.
Hoàng Anh giữ Gateway; Vinh điều phối shared build/CI và frontend shared scope khi được giao.
Core/Support giữ module riêng trong cùng deployment, không trộn domain/entity hoặc quyền DB.

## Topology

    Web / Mobile → Gateway
                     ├─ Core
                     ├─ Clinical
                     ├─ Lab
                     ├─ Pharmacy
                     ├─ Billing
                     └─ Support
    Service discovery: Eureka
    Domain facts: các producer → RabbitMQ → các consumer có queue riêng
    Mỗi context owns dữ liệu; không service nào đọc SQL của service khác.

Pharmacy và Billing phải độc lập. Đây là ranh giới chính cho demo thanh toán thành công nhưng
cấp thuốc lỗi, redelivery, crash recovery, compensation và eventual consistency của báo cáo.

## CURRENT khác TARGET

Source hiện có 10 business modules; Core/Support host chưa được tạo trong lượt viết tài liệu.
Đây là baseline của lượt thiết kế 2026-10-14, không mô tả source mới nhất: rà soát
2026-10-15 tại `5411acfa` đã có `backend/core-service` sau PR #368; chưa có
`backend/support-service`. Sự hiện diện Core không chứng minh đã nghiệm thu sáu deployments.
Clinical/Lab có các use case V1/EXAM/LAB clearance và Billing có issuer opt-in; flags/producer
publication phải nghiệm thu lại. Pharmacy có V0 public/receipt saga và V1 outpatient internal
creation/dispense; không coi internal service/test là public V1 workflow đã chạy.

Report có các kernel cash/refund/rebuild/publication local/gated, gồm V25/V26; không tạo lại
chúng hoặc bật toàn bộ chỉ để làm báo cáo MVP. Notification có nhiều care projections đã code;
MVP chỉ cần các intent ngoại trú có nguồn hợp lệ. Gateway policy status phải chạy test lại.

## Phạm vi bắt buộc

- Identity và login có signed JWT; role/ownership kiểm ở Gateway và downstream.
- Ngoại trú, xét nghiệm và cấp thuốc theo [luồng nghiệp vụ](02-business-flows.md).
- Billing là owner duy nhất của financial effects; Pharmacy là owner của stock.
- RabbitMQ, durable outbox/inbox, bounded retry/DLQ, correlation và concurrency tests.
- Report nhận facts; Notification không cấp quyền và không chặn nghiệp vụ nguồn.
- Một profile nghiệm thu rõ ràng, không tự động trộn V0 và V1 để tránh lỗi.

## Phần hoãn

Master cập nhật ngày 2026-10-14 đã bổ sung producer placement/capacity của Inpatient,
lookup placement và migration V3. Giữ nguyên code, fixtures và migration này; đó là tiến
triển của context hoãn, không mở lại scope nội trú hoặc tự đóng task consumer Pharma/Report.
Các tài liệu placement nhập từ master chỉ là tham chiếu cho lần tái kích hoạt sau MVP.

| Phần | Cách xử lý hiện tại |
|---|---|
| Inpatient | Giữ module, migration và dữ liệu; không chạy trong profile MVP, không admission referral từ MVP |
| Surgery | Giữ module/policy/reliability/test; không chạy, không đưa consent/team/schedule vào MVP |
| Admission medication/deposit/top-up/settlement | Giữ code đã có; không producer/consumer/job activation trong MVP |
| Full financial/operational Report V2 | Không làm mọi KPI/correction/archive/publication để đóng MVP |
| Full history before V2 | Bảo toàn evidence đã có, không fabricate; không điều kiện nghiệm thu toàn MVP |
| Email/SMS/reminder nâng cao | Không bắt buộc; in-app đủ |
| Database consolidation | Không copy/merge DB, không đánh lại migration versions |
| Mobile expansion/new shared UI | Chỉ regression gateway contracts; không task mới để đóng backend |

Hoãn không có nghĩa rule đã đạt, contract đã đầy đủ hay code được phép xóa. Khôi phục phạm vi
này cần task mới, source audit và policy/contract acceptance. Các flags phải thật sự tắt;
một global care-finance flag không được vô tình bật Inpatient/Surgery.

## Tổ chức code và database

Giữ packages và blueprint domain/application/infrastructure từng module. Hai host mới chỉ
composition/config; dùng plain module libraries, không import BOOT-INF executable JAR.
Có thể giữ standalone launcher ở legacy test profile, không coi nó là service thứ bảy của MVP.

Giữ database cũ: Core dùng hai datasource Organization/Patient; Support dùng hai datasource
Report/Notification. Clinical/Lab/Pharmacy/Billing giữ DB hiện tại. Có 8 context stores active,
không hứa 6 DB; DB Inpatient/Surgery giữ nguyên nhưng không nối trong MVP. Flyway/transaction
manager/repository scan theo module. Không xây dual-write hay D2 schema migration trong backlog.

Core/Support chia cùng failure domain. Tách queue/pool/transaction budget để giảm starvation,
nhưng JVM crash/OOM vẫn ảnh hưởng cả hai module. Cần thử tải vừa đủ demo, không xây platform
bulkhead/scheduler framework mới nếu cấu hình có sẵn đã đáp ứng.

## Không thêm công nghệ

Giữ Java/Spring/Maven/PostgreSQL/RabbitMQ/Eureka/Gateway và stack FE hiện tại. Không thêm Kafka,
Kubernetes, distributed XA, event sourcing toàn hệ thống, cổng thanh toán ngoài hoặc Redis
chỉ để chứng minh phân tán. Đây là đồ án có phạm vi nghiệm thu, không chứng nhận vận hành y tế thật.
