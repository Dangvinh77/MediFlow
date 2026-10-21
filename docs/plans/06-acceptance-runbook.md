# Nghiệm thu và vận hành MVP

## Điều kiện chung

Tài liệu là tiêu chí cần chạy, không là kết quả đã đạt. Dùng PostgreSQL/RabbitMQ thật cho
transaction/race/crash cases; unit mocks không chứng minh database locking hay broker recovery.
Seed fixtures chỉ trong isolated test/demo environment, không thay dữ liệu thật hoặc lịch sử.
Không xóa volumes, xóa inbox/outbox hay HELD để dựng lại demo.

## Test matrix

| ID | Tình huống | Kết quả bắt buộc |
|---|---|---|
| T01 | Core login/locked account/SYSTEM human/duplicate identity | RBAC/BCrypt/unique và stable errors |
| T02 | Human/refresh/wrong caller gọi lookup; Core unavailable | Denied hoặc unavailable, không guessed IDs |
| T03 | Check-in chưa EXAM clearance | Không exam; exact grant mới unlock |
| T04 | Concurrent start exam/record creation rollback | Một record, appointment+record atomic |
| T05 | Lab thiếu/wrong/expired clearance hoặc sửa terminal result | Denied, không result/charge effect |
| T06 | lab.request.created duplicate | Một charge, result không tạo phí V1 lần đầu |
| T07 | Rx price client/catalogue đổi, multi-drug không đủ tồn | Snapshot server; no partial reservation/effect |
| T08 | Hai dispense/duplicate payment delivery | Một stock effect/slip/outbox và no negative stock |
| T09 | Pharmacy down rồi restart, commit trước ACK/finalize | Durable pending resume, không mất/double effect |
| T10 | Cancel/expiry/stock failure rồi late payment | Terminal không reopen; compensation một lần đúng mode |
| T11 | Payment command/allocations/refund races/wrong episode | Không duplicate/over-allocate/over-refund/cross account |
| T12 | V1 receipt hoặc grant tới Pharmacy | Receipt không auto-dispense, exact authorization riêng |
| T13 | Report redelivery/same operation new eventId/conflicting bytes | One contribution hoặc conflict, không double count |
| T14 | Cash/refund/history thiếu | Cash khác earned, original link đúng, unavailable khác zero |
| T15 | Report replay và in-app delivery/PATIENT other owner | Không resend/starvation, owner-only reads |
| T16 | Poison/broker restart/unroutable publish/DB rollback | Bounded retry/DLQ/confirm/atomicity, IDs/bytes giữ |
| T17 | Inpatient/Surgery off/global flags kết hợp | Không route/call/job deferred; no financial ACK-drop |
| T18 | Six-service startup/rollback sau accepted writes | 6 apps + infra, data/effects giữ, không dual writer |
| T19 | Fake nested Gateway path, wrong role/token, FE smoke | Default deny; frontend chỉ Gateway, UI/themes giữ |
| T20 | Refund trước/sau EXAM/LAB start; stale local grant, Billing unavailable; emergency/legacy thiếu start receipt | Exact durable start serialized với refund; mọi hoàn EXAM/LAB cần applied cancellation và remaining due; stale/unavailable không unlock; audited override riêng; retry receipt cũ vẫn kiểm authority hiện tại |
| T21 | Hủy Lab/EXAM trước create, duplicate/conflicting cancellation, paid/unpaid | Terminal source không reopen/collect; một adjustment, audit nguyên vẹn, không giả giao dịch hoàn |
| T22 | Refresh thiếu exp, account bị khóa/đổi quyền, Core unavailable | Không mint từ claims cũ; active identity hiện tại hoặc fail closed |
| T23 | Report hai acceptance cùng timestamp, clock thay đổi khi chờ khóa | Latest theo durable acceptance order; receipt cũ và coverage history giữ |
| T24 | Tạo staff rồi broker lỗi/network retry cùng idempotency key | Commit+outbox atomic; retry cùng staff ID, changed payload conflict, relay retry giữ event ID |

## Demo tối thiểu

1. Đăng nhập theo role, tạo/chọn patient và appointment đúng khoa.
2. Check-in; chứng minh chưa trả EXAM thì chưa bắt đầu khám; trả phí và khám.
3. Chỉ định Lab, chứng minh gate đúng test, trả phí, nhập kết quả, Clinical thấy result.
4. Tạo Rx, giá/reservation từ server; Billing nhận source, thu tiền, Pharmacy cấp thuốc.
5. Report/notification cập nhật qua events, không producer SQL hoặc synchronous business writes.
6. Tắt Pharmacy trong test environment, gửi payment, bật lại và chứng minh effect một lần.
7. Deliver same event hai lần; rồi một stock/expiry failure, đối soát compensation của Billing.
8. Chứng minh một request sai role và poison message vào đúng DLQ.
9. Trình bày phạm vi deferred rõ ràng; không nói đã hoàn thiện HIS/nội trú/phẫu thuật.

## Verification

Chạy source suites và host suites theo M6 plan. Core/Support source và default Compose topology
đã có. Refresh 2026-10-17: real PG/Rabbit isolation, packaged Support restart, actual sáu-app
EXAM/Rx paid/dispense và current-store rollback đã đạt; [commands/counts/source IDs/hashes](huy-mvp-cutover-runbook.md#8-integrated-backend-closeout--2026-10-17).
Lượt tiếp theo đã inventory và rehearsal trên cold copies của actual persisted local Compose
volumes: bốn writes mới/probe bytes giữ qua cutover/rollback/broker restart, original-volume
hashes không đổi. [Evidence local](huy-mvp-cutover-runbook.md#9-local-persisted-volume-inventory-and-cutover--2026-10-17).
Huy 48/48 cho local đã quan sát; global M6/paid Lab/FE/production acceptance không suy ra từ slice này.

    mvn -q -pl backend/clinical-service,backend/lab-service,backend/pharmacy-service,backend/billing-service -am test
    mvn -q -pl backend/gateway -am test
    mvn -q -pl backend/support-service -am -Psupport-acceptance verify
    mvn -q -DskipTests package
    mvn -q -pl backend/support-service -am -Psix-service-acceptance -Dtest=__NoUnitTests__ -Dsurefire.failIfNoSpecifiedTests=false verify
    mvn verify
    cd frontend
    pnpm typecheck
    pnpm lint
    pnpm test

Ghi rõ tests run/failed/skipped, container versions, fixture/source commit, flags, raw event
fingerprints và assertions. Không lấy số test của lượt lịch sử làm result mới. FE commands
chỉ trong scope cần smoke/regression; không bắt hoàn thiện mobile mới để đóng backend.

## Rollout

Giữ database/migrations cũ. Dựng clone bằng backup đã kiểm chứng, chạy packaged profiles và
test matrix trước production switch. Profile chưa có là implementation task, không tài liệu
vận hành có thể copy chạy ngay.

Chuyển Core trước, rồi Support. Clinical/Lab/Pharmacy/Billing giữ deployment nhưng retarget
đúng callers. Tắt Inpatient/Surgery trong profile MVP, không xóa source/stores. Gateway auth
client upstream phải chuyển cùng Core route. Giữ logical JWT/event producer identities.

Mỗi nhóm: block/drain write, stop old jobs/listeners/publishers, bảo đảm old credential không
còn writer, rồi mới start new host. Dùng credentials riêng; revoke old không revoke new.
Không chạy hai implementation cùng writer để “canary” command traffic trên cùng DB.

Smoke login/role/lookup/charge/payment/dispense, compare cursor/stock/ledger/report/notification.
Duyệt budget queue lag/latency/memory theo baseline workload trước rollout, không bịa SLA số.

## Rollback

Stop/drain new host, giữ current DB và accepted effects, switch legacy launcher/route/caller
profile đã test. Không restore snapshot trước rollout lên DB đã nhận thêm payments/events.
Resume pending state với original IDs/bytes. Nếu schema change không backward-compatible,
dừng rollout/fix-forward theo operator approval; không tự sửa checksum hoặc drop tables.

Rebuild Report generation không là deployment rollback. Notification đã gửi không được coi
như chưa gửi. Backup/restore phải có evidence, không chỉ tồn tại ZIP/dump.

## Bàn giao

Báo cáo MVP completion theo 18 task/72 subtask, test matrix và runtime 6 apps; không tỷ lệ
dựa trên backlog deferred. Các phần còn hoãn không là “bug đã fix” hoặc “100% toàn bộ dự án”.
Không có claim bệnh viện production/legal compliance từ demo học phần.
