# Thiết kế MediFlow — hệ thống thông tin bệnh viện cho ngoại trú

## Định vị sản phẩm

**MediFlow: A Microservices-Based Hospital Information System for Outpatient Care Management.**

MediFlow là hệ thống thông tin bệnh viện phân tán, với phạm vi triển khai đồ án tập trung vào quản lý khám chữa bệnh ngoại trú. Quy mô hướng tới là phòng khám đa khoa hoặc khoa khám bệnh ngoại trú của bệnh viện: có tiếp nhận, bác sĩ/khoa, Lab, quầy thuốc, thu ngân và quản lý. Phòng mạch nhỏ có thể sử dụng một phần chức năng nhưng chi phí vận hành microservices/broker cần được cân nhắc; đây là định hướng thiết kế, chưa phải kết luận triển khai thực tế.

MVP bao gồm quản lý bệnh nhân và tổ chức, lịch hẹn/tiếp nhận, khám/hồ sơ/diagnosis, xét nghiệm, kê đơn/cấp thuốc, thu phí khám/xét nghiệm/thuốc, báo cáo ngoại trú và thông báo in-app. Admission, giường bệnh, điều trị nội trú và phẫu thuật dành cho giai đoạn sau; không xóa code/data đã có. Mô tả “HIS cho ngoại trú” không chứng nhận hệ thống quản lý toàn bộ bệnh viện đã hoàn chỉnh.

Phạm vi có trong MVP khác với tính năng đã nghiệm thu: implementation, gate, contract và acceptance vẫn theo tài liệu 03/05/06 và handoff owner.

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

Rà source ngày 2026-10-17: root Maven reactor có 13 module gồm common, Eureka, Gateway, sáu host nghiệp vụ (Core, Clinical, Lab, Pharmacy, Billing, Support), hai module Report/Notification xuất cả plain library và launcher rollback, cùng Inpatient/Surgery hoãn. Organization và Patient nằm trong backend/core-service, giữ package/domain và hai datasource riêng; không còn hai module Maven standalone trong reactor. Tám context active vẫn là Organization, Patient, Clinical, Lab, Pharmacy, Billing, Notification và Report; số thư mục/module không phải số ứng dụng triển khai mặc định.

[Support host](../../backend/support-service/README.md) đã được tạo theo giao quyền trực tiếp của người dùng: import hai module entry points, hai DS/EMF/Flyway/tx/pools tách biệt, scoped JWT/security/advice, host default deny và chặn deferred features. Gateway giữ nguyên public paths, mặc định trỏ Report/Notification tới Support. Compose dùng Core và Support, đặt standalone Report/Notification dưới profile legacy-support, Inpatient/Surgery dưới deferred. Ngày 2026-10-17 đã đạt hai real PG stores/Rabbit isolation, packaged migration/restart và actual EXAM/Rx payment/dispense trong sáu business apps qua Gateway; current-store rollback giữ cả write mới. Lượt tiếp theo đã inventory/cutover/rollback trên cold copies của actual persisted local Compose volumes, giữ bốn writes mới/probe bytes và original-volume fingerprints. Huy 48/48 cho local đã quan sát; không ký thay dữ liệu/coverage của môi trường khác. Lab ở bài sáu-app chỉ startup/discovery. [Evidence/giới hạn](huy-mvp-cutover-runbook.md#9-local-persisted-volume-inventory-and-cutover--2026-10-17); default activation gates không đổi.

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
