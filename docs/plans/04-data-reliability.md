# Dữ liệu và độ tin cậy

## Lịch khám, tái khám và hỗ trợ mô phỏng — đích 25/10/2026

Các yêu cầu sau chưa là xác nhận migration/guard đã tồn tại; triển khai theo
[WF-01–11](07-outpatient-workflow-implementation.md) và giữ các bất biến baseline bên dưới.

- Clinical lưu lịch, capacity/reservation, registration và idempotency receipt trong cùng store;
  update có lock/revision và unique constraint. Giữ suất, xác nhận, hết hạn và hủy phải tranh
  cùng guard; kiểm trước ở UI hoặc read-then-write không đủ chống vượt sức chứa.
- Nhân sự/khoa từ Core có authoritative UUID và trạng thái; provider lỗi fail closed cho tạo mới.
  Không tự hủy phiếu đã thu chỉ vì một lần Core timeout; đưa vào xử lý thay đổi có audit.
- TTL 15 phút dùng server clock; payment-in-progress có operation ổn định và recovery. Không
  giải phóng reservation trong khi trạng thái thu chưa xác định; có reconciliation worker/cursor
  bền vững để tránh giữ suất vô hạn. Hết deadline cần đối soát outcome trước release; late paid
  sau release là exception được ghi nhận, không ghi đè người đã chiếm suất.
- Chuyển bác sĩ/giờ lưu before/after/reason/actor/version; không mutate receipt tiền cũ. Lịch đã
  có phiếu không xóa cascade. Người bệnh đồng ý lịch mới hoặc theo luồng hoàn được duyệt.
- Tái khám giữ previous visit/record và recommendation source; cùng patient, không vòng tham chiếu,
  không copy clinical state. Lượt mới dùng episode riêng; đọc kết quả lượt cũ không tạo episode mới.
- Billing dùng BigDecimal và immutable line snapshot: catalogue version, policy version, gross,
  support rate/amount, patient payable, currency và rounding HALF_UP tới VND từng dòng. Không
  recompute payment/refund lịch sử theo catalogue hiện tại; không chỉnh migration đã phát hành.
- Hỗ trợ mô phỏng không là tiền, không phải insurer receivable. Payment/allocation so với patient
  liability; zero liability có receipt quyết định chính sách riêng. Partial payment không unlock
  dịch vụ cần thu đủ; refund bị chặn theo actual patient paid trừ previous actual refunds.
- Completion phải có quyết định nguồn về pending Lab; HTTP timeout không là “không còn chỉ định”.
  Thiết kế barrier phải khóa revision/nguồn và chống race thêm chỉ định với completion; không chỉ
  đọc danh sách Lab một lần rồi hoàn tất. Không distributed SQL/transaction giữa Clinical và Lab.
- Report nhận breakdown/visit kind từ facts đã version, giữ unknown/unavailable cho lịch sử chưa
  có trường; không backfill tỷ lệ BHYT hoặc loại tái khám phỏng đoán. Rebuild không gửi Notification.

Kiểm thử concurrency và restart phải bao gồm suất cuối, đổi ca khi đang thu, late payment,
quote expiry, hỗ trợ 100%, policy update sau thu và refund race; ma trận ở [06](06-acceptance-runbook.md).

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

TARGET schedule dùng protocol [03 phụ lục A](03-contracts.md#opd-scheduling-v2): giữ suất
`HELD` 15 phút, còn `PAYMENT_LOCKED` phải được Billing trả kết quả thu hoặc đóng operation
trước khi giải phóng. `reconcileAfter` chỉ đánh thức đối soát. Timeout hoặc missing operation
không chứng minh chưa thu; giữ khóa, retry và cảnh báo. `close-if-unpaid` và cash commit
khóa cùng operation trong Billing để chỉ một kết quả thắng. Không kéo dài transaction DB
qua lời gọi mạng; outbox và worker cursor phải sống qua restart. Xem
[SC-08–14 và sơ đồ](10-scheduling-contract-acceptance.md) để nghiệm thu. Bằng chứng
[backend 27/10](../acceptance/backend-closeout-2026-10-27/README.md) phân biệt PG/process
recovery, packaged network và phần UX/dataset còn cần nghiệm thu trực tiếp.

Clinical chuẩn hóa thời điểm tạo payment lease và resolve về microsecond trước khi lưu
và trả response, cùng độ chính xác PostgreSQL. Acquire/terminal replay phải trả cùng
receipt, kể cả khi clock Java có nanosecond; không so sánh hai receipt khác độ chính xác
rồi coi đó là operation mới hoặc lấy thêm suất.

Giữ correlation qua Gateway/REST/event; log eventId/sourceId/operationId và status không PII/token.
Có health và metrics được bảo vệ. Sau restart, pending outbox/inbox/job/receipt được xử lý tiếp,
không xóa state để tránh lỗi. Phải chạy PG/Rabbit tests thật cho races/crash/redelivery.

Rollback deployment phải preserve accepted writes và external effects. Vì MVP giữ DB cũ,
không có data-copy rollback. Stop/drain old host rồi mới bật new host cùng dữ liệu; không hai
implementations cùng writer/jobs. Backup/restore clone trước rollout, không xóa volumes.
