# Dữ liệu và độ tin cậy

## Giữ database và migration hiện tại

MVP giảm deployments và phạm vi nghiệp vụ, không chuyển 10 DB thành 6 DB. Core owns hai stores
Organization/Patient; Support owns hai stores Report/Notification; bốn service còn lại giữ stores
riêng. DB Inpatient/Surgery và dữ liệu đã có không xóa hoặc sửa để dựng demo.

Mỗi module co-host có datasource/EMF/repository scan/Flyway/transaction manager đúng scope.
Plain context libraries không kéo Application/legacy config vào broad component scan.
Không dùng bean override hoặc @Primary manager để che wiring lỗi. Giữ checksum/version
migration và native SQL semantics, ddl-auto=validate; không Flyway repair/renumber để cho qua.

Không cross-service joins, JPA references hoặc import repositories/domain entities xuyên context.
Core/Support co-host không cho Report đọc contacts hoặc ledger. common chỉ generic envelopes,
pagination/errors/security constants; không dồn domain models vào common.

## Transaction

Aggregate state, outbox và command receipt commit cùng transaction. Consumer claim/effect cùng
transaction. Phân biệt local transaction với cross-service workflow; không distributed @Transactional.
Giữ V0 REQUIRES_NEW terminal failure behavior và V1 rollback/failure kernels đúng mode.

Money BigDecimal, UUID identifiers, Instant/LocalDate. Giữ immutable price snapshot/original
payment/refund linkage; không regenerate IDs/timestamps khi thay host. DB uniqueness là guard
cuối cho requests/slips/charge source/active resources, không chỉ kiểm tra trong Java.

## RabbitMQ

Giữ topic exchange mediflow.events và DLX/DLQ đang dùng. Từng logical consumer có queue riêng:
report.q khác notification.q; không gộp thành support.q. Các queue chuyên biệt giữ container
factory/concurrency/retry/prefetch đúng module.

Outbox publish có confirm/return và lease/fencing theo implementation hiện có; retry original
bytes/eventId. Publisher không nói thành công khi unroutable. Crash sau publish trước mark có
thể gửi lại; consumer phải idempotent. Không publish trực tiếp trong transaction critical.

Transient retry bounded/backoff. Poison/schema/target conflict chuyển đúng DLQ/quarantine với
correlation và lý do redacted; không poison-loop, không nuốt lỗi tài chính. HELD remains HELD:
việc giảm service count không là authority release. Không reset inbox để chạy demo lần nữa.

## Pharmacy và Billing

Stock locks theo drug UUID order; recheck expiry/authority sau chờ locks. Reservation/slip/
stock/outbox atomic, không âm kho, không cấp hai lần. Receipt RECEIVED có thể resume đúng bytes;
terminal outcomes không bị stale writer ghi đè. Scheduler lease/job cursor giữ đúng DB.

Billing source charge, payment command, allocation và refund có lock/unique/business key riêng.
Payment receipt không là clearance universal. Compensation là financial operation có evidence,
không update boolean tùy ý hay giả rằng bank refund thành công. UI phải hiện pending khi cần.

## Support tối thiểu

Tách Report listener/rebuild executor và Notification listener/delivery worker ở cấu hình.
Không Report rebuild trong Notification transaction hoặc ngược lại. Đo queue lag/memory/pool
trong một workload demo đã khóa; không xây resilience platform mới ngoài nhu cầu chứng minh.

Chỉ đọc/công bố metrics có supported source evidence. Cash và refunded amounts không gọi earned.
Final-settlement-only earned policy giữ nguyên cho kernel deferred; MVP không cần financial
publication all-five/history conversion. Coverage thiếu phải unavailable, không zero giả.
Rebuild isolated chỉ áp dụng Report data, không chạy lại business commands hay gửi notifications.

## Quan sát và phục hồi

Giữ correlation qua Gateway/REST/event; log eventId/sourceId/operationId và status không PII/token.
Có health và metrics được bảo vệ. Sau restart, pending outbox/inbox/job/receipt được xử lý tiếp,
không xóa state để tránh lỗi. Phải chạy PG/Rabbit tests thật cho races/crash/redelivery.

Rollback deployment phải preserve accepted writes và external effects. Vì MVP giữ DB cũ,
không có data-copy rollback. Stop/drain old host rồi mới bật new host cùng dữ liệu; không hai
implementations cùng writer/jobs. Backup/restore clone trước rollout, không xóa volumes.
