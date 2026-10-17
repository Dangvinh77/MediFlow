# Plan triển khai MVP sáu service

Ngày 2026-10-14. Plan này thay plan bảo toàn cả 10 nghiệp vụ gồm 36 task/144 subtask.
Mục tiêu mới là giảm phạm vi nghiệm thu ngoại trú; không lấy ít checkbox làm bằng chứng code
đơn giản hơn. Có 18 task chính, 72 subtask; ưu tiên reuse code và đóng gap, không viết lại service.

Baseline viết plan ngày 2026-10-14: mọi task là TODO; lượt viết docs không chạy nghiệm thu
backend và không chuyển topology. Rà soát Vinh ngày 2026-10-15 đã có implementation/test evidence
trong [source lock và closeout](../handoffs/HANDOFF-VINH-OUTPATIENT-MVP.md#current-owner-queue).
Các task toàn nhóm dưới đây vẫn mở; checkbox không thay thế phần evidence theo owner.
Lượt reliability tiếp theo thêm Lab PG/Rabbit publisher/listener acceptance, broker application
restart và publish-confirm/DB-rollback windows cho Clinical/Lab; xem phạm vi và full-run evidence
trong cùng closeout. Lượt packaged recovery sau đó thêm actual `java -jar` kill/restart cho
outbox Clinical/Lab qua module-local `process-recovery` profile. Không đánh integrated
Gateway/Billing slices đã đạt. Batch tiếp tục từ `050b5916` bổ sung Clinical/Lab consumer
JVM crash-after-effect-before-ACK với actual attachment/clearance + inbox trên PG/Rabbit thật;
đây là contract-fixture recovery, không actual paid E2E. Xem counts/command/giới hạn trong
closeout; không dùng test lịch sử làm verification bản hiện tại.
Chỉ đóng task khi bốn subtask + điều kiện đóng có evidence. User scope docs không cấp quyền
Huy sửa production Gateway/shared/Clinical/Lab/Billing/Notification.

Refresh 2026-10-17: người dùng giao Huy trực tiếp hoàn thiện Billing/Notification liên quan
và Support, gồm shared POM/Dockerfile/Compose/CI/Gateway cần thiết. Phần Huy đạt **48/48 cho local hiện có**
trong plan riêng, với real-store/broker isolation, packaged Support và actual sáu-app EXAM/Rx
payment/dispense/current-store rollback. [Evidence](huy-mvp-cutover-runbook.md#8-integrated-backend-closeout--2026-10-17)
áp dụng các phần liên quan M6-06/08/10/12–18; không tự tick 72 checkbox toàn team hoặc nghiệm
thu lại paid Lab. H6-02.3/H6-12.3 đã inventory và rehearsal trên cold copies của actual
persisted local volumes: nguồn trong phạm vi không có historical business rows, cả hai Rabbit
nodes không có queues; giữ bốn writes mới/probe bytes qua chuyển đổi và original-volume hashes
không đổi. [Evidence/giới hạn local](huy-mvp-cutover-runbook.md#9-local-persisted-volume-inventory-and-cutover--2026-10-17)
và [một handoff Huy](../handoffs/HANDOFF-HUY-MVP.md) ghi kết quả, không cấp production activation.

## P0 Scope và baseline

### M6-01 Chốt scope và quy tắc

Owner: Vinh điều phối, cả 4 owner. Dependency: không.

- [ ] M6-01.1 Xác nhận 6 deployments, ngoại trú, deferred Inpatient/Surgery/full V2.
- [ ] M6-01.2 Giao Core/Support/shared/Gateway paths rõ ràng, giữ module ownership.
- [ ] M6-01.3 Align agent/README/source guides còn ngoài docs với bộ mới; không đổi UI/DTO.
- [ ] M6-01.4 Khóa demo acceptance, CURRENT/TARGET và định nghĩa completion.

Đóng khi: không ai tiếp tục giao backlog deferred để đóng MVP; source ownership không bị mở rộng.

### M6-02 Khóa source contracts và tìm gap thật

Owner: từng owner, Vinh tổng hợp. Dependency: M6-01.

- [ ] M6-02.1 Refresh commit/diff, bảo toàn FE đang sửa; chạy baseline tests có counts/skips.
- [ ] M6-02.2 Khóa API/roles/errors/event bytes/queues/JWT subject và profile V0/V1.
- [ ] M6-02.3 Map gap của V1 outpatient driving API/authority/publication, không invent producer fields.
- [ ] M6-02.4 Chọn fixture nguồn và golden scenarios; deferred tests không trở thành live MVP task.

Đóng khi: có manifest đủ để code/test từng gap; không coi local mock/provider test là real flow.

Evidence Vinh 2026-10-15: Clinical/Lab source lock đã gồm PR #366/#370, persisted Lab order,
signed Lab-only context và event/operation/fingerprint inbox. Không giao lại hai implementation
này từ checklist cũ. Open gap register theo owner nằm ở H-VINH-01/02/03 bên dưới; xem
[closeout](../handoffs/HANDOFF-VINH-OUTPATIENT-MVP.md) để lấy flags, fixtures và verification.

### M6-03 Chuẩn bị hai host gọn

Owner: shared-build owner + Hoàng Anh/Core + Huy/Lộc/Support. Dependency: M6-01, M6-02.

- [ ] M6-03.1 Tách plain context libraries/launchers cho Core và Support, giữ packages/SQL bytes.
- [ ] M6-03.2 Explicit scans/beans/advices/security, không allow-bean-overriding.
- [ ] M6-03.3 Wire per-module datasource/EMF/Flyway/transaction manager; không copy DB.
- [ ] M6-03.4 Package smoke và cấu hình CI/target profile, legacy profile phục vụ rollback.

Đóng khi: Core/Support artifacts chạy thật, không phải chỉ classpath IDE và không 7 runtime apps.

## P1 Core và Support

### M6-04 Core identity và account

Owner: Hoàng Anh. Dependency: M6-03.

- [ ] M6-04.1 Compose Organization/Patient public routes và module persistence độc lập.
- [ ] M6-04.2 Giữ BCrypt/locked account/role constraints/identity uniqueness/UUIDs.
- [ ] M6-04.3 Test lookup confirmed absence/unavailable/malformed, signed caller scope.
- [ ] M6-04.4 Regression CRUD/verify/department/staff rules, bảo toàn dữ liệu và events.

Đóng khi: Core thay được hai app cũ mà không sửa semantics identity hoặc quyền.

### M6-05 Clients và login upstream

Owner: Hoàng Anh/Gateway, Vinh/Clinical, Huy/Pharmacy. Dependency: M6-04.

- [ ] M6-05.1 Retarget Patient/Organization clients tới Core, giữ logical service JWT identity.
- [ ] M6-05.2 Retarget Gateway OrganizationAuthClient, không chỉ sửa route.
- [ ] M6-05.3 Giữ PharmacyClinical exact record/context lookup và timeout/fail-closed.
- [ ] M6-05.4 Test login/correlation/unavailable và old/new destination parity.

Đóng khi: MVP không remote call tới retired names, không đoán patient/doctor/episode.

Refresh 2026-10-16: Clinical profile retarget `core-service` và packaged actual Core lookup đã có.
Full Core/Gateway parity và global M6-05 vẫn cần owner evidence; G1 discovery là dependency riêng
ở [closeout](../handoffs/HANDOFF-VINH-OUTPATIENT-MVP.md).
Pharmacy caller vẫn theo handoff của Huy đã đăng ký, không chuyển thành việc Vinh.

### M6-06 Support composition và quyền

Owner: Huy/Report, Lộc/Notification. Dependency: M6-03.

- [ ] M6-06.1 Compose Report/Notification API, tách datasource/tx/queue/listener budgets.
- [ ] M6-06.2 Giữ JWT human types, ADMIN/MANAGER/report roles và PATIENT ownership.
- [ ] M6-06.3 Arch tests deny Report producer DB/Feign/contact repository coupling.
- [ ] M6-06.4 Test package/startup và một module timeout không chiếm toàn worker pool.

Đóng khi: một host nhưng hai module vẫn độc lập về dữ liệu/authority; failure domain được ghi rõ.

### M6-07 In app notifications tối thiểu

Owner: Lộc. Dependency: M6-02, M6-06.

- [ ] M6-07.1 Chọn intents appointment/payment-request/receipt/filled/refund đủ fixture nguồn.
- [ ] M6-07.2 Recipient dùng exact patient/account mapping, không contacts hoặc account giả.
- [ ] M6-07.3 Atomic intent/inbox/history, duplicate/restart không tạo logical notice hai lần.
- [ ] M6-07.4 Test owner-only reads và status pending/sent/failed đúng thực tế.

Đóng khi: in-app hoạt động qua Gateway; không cần email/SMS/Surgery template để đóng task.

### M6-08 Report ngoại trú tối thiểu

Owner: Huy. Dependency: M6-02, M6-06.

- [ ] M6-08.1 Reuse mappers/stores cho visit/Lab/Rx/items và cash/refund có evidence.
- [ ] M6-08.2 Dedupe event + operation, conflicting bytes reject; refund linked đúng source.
- [ ] M6-08.3 Reader/query rõ cash vs earned và coverage; không bật all-five/history publication.
- [ ] M6-08.4 Duplicate/out-of-order/rebuild tests và API roles, không gửi notification khi replay.

Đóng khi: metrics đọc được có nguồn đối soát; absence không là zero và không gắn nhãn revenue sai.

## P2 Luồng ngoại trú

### M6-09 Clinical và EXAM gate

Owner: Vinh; Lộc verify producer/consumer. Dependency: M6-02, M6-05, M6-12.

- [ ] M6-09.1 Check-in/status/exam source đúng fixture và episode; không double charge record.
- [ ] M6-09.2 Start exam chỉ exact clearance hoặc audited override đã hỗ trợ.
- [ ] M6-09.3 Appointment/record transaction atomic, completion diagnosis/disposition ngoại trú.
- [ ] M6-09.4 PG/Rabbit/Gateway slice check-in → payment → exam, wrong-target/duplicate denial.

Đóng khi: V1 EXAM chạy real slice; không bật admission referral để hoàn tất Clinical.

Vinh 2026-10-15: source M6-09.1–3 và durable inbox/outbox đã có; M6-09.4 chưa đóng.
Lượt packaged API slice đã phát hiện/sửa lỗi numeric timestamp mất nano giây khi validate
flat event trong durable outbox; regression dùng actual Rabbit converter cho bốn event
types. Core/Gateway/Clinical check-in race, exact unpaid/actor denial và một exam record
đã có packaged proof với synthetic contract clearance, chưa phải real Billing payment.
Billing producer/fixture và real slice: [H-VINH-03](../handoffs/HANDOFF-VINH-OUTPATIENT-MVP.md#h-vinh-03--lộc--billing-phối-hợp-slice-với-vinh).
Refresh `c8024921`, 2026-10-16: [owner handoff](../handoffs/HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md)
L1 discovery và L2 selective cohort/writer V22/V23 đã merge. Fresh targeted Billing 61 tests
đạt (0 failure/error/skip), gồm discovery + migration/cohort; chưa là actual Rabbit delivery.
Không giao lại L1/L2. PR #401 giao partial Rabbit/JVM recovery; còn Lộc L3 failure/ordering + L4 full artifacts,
Hoàng Anh G1 exact GET discovery qua Gateway. Allowlist hiện chưa có GET by-source.
Vinh chạy V-CLOSE-03 actual paid EXAM/LAB_TEST/Rx sau producer/Gateway evidence; không SQL
discovery/synthetic grants và không tự tick M6-09.4/M6-11.4/M6-16.
Refresh `71e2684d` + Vinh continuation 2026-10-16: Huy đã giao explicit recordId/dispense
relationship và API/PG proof; Clinical đã implement V1 Rx consumer, per-dispense inbox/effect,
precise decimal fingerprint, delayed fill vào completed outpatient record và PG/Rabbit tests.
Public Gateway Lab-order acceptance dùng route Hoàng Anh đã thêm ở `0a8636cd`;
current evidence ở Vinh closeout. Actual paid slice vẫn chờ handoff Lộc/G1:
[H-VINH-02](../handoffs/HANDOFF-VINH-OUTPATIENT-MVP.md#h-vinh-02--huy--pharmacy--report-vinh-nhận-consumer-clinical).

### M6-10 Pharmacy ngoại trú và stock

Owner: Huy; Vinh/Lộc verify contracts. Dependency: M6-02, M6-05, M6-12.

- [ ] M6-10.1 Hoàn thiện real outpatient creation context/identity và driving API nếu baseline thiếu.
- [ ] M6-10.2 Reuse reservation/price snapshot/HELD lifecycle; publish có consumer acceptance.
- [ ] M6-10.3 V1 dispense authenticated actor + exact clearance, atomic stock/slip/outbox và terminal replay.
- [ ] M6-10.4 PG stock races, expired reservations, API idempotency và V0 regression riêng.

Đóng khi: public V1 outpatient flow thật; không fake medication authority hoặc auto-dispense V1 receipt.

### M6-11 Lab request và result

Owner: Vinh; Lộc verify Billing. Dependency: M6-02, M6-05, M6-12.

- [ ] M6-11.1 Explicit request/source/episode, V1 charge tại request thay vì kết quả.
- [ ] M6-11.2 Exact LAB_TEST target/expiry gate trước start và result recording.
- [ ] M6-11.3 Immutable completion/resultVersion, Clinical/Report consumers cùng fixture.
- [ ] M6-11.4 E2E request → pay → result và duplicate/mismatch/finalized negative tests.

Đóng khi: Lab slice thật; không amendment/correction chain hoặc guessing labTestIds.

Vinh 2026-10-15: Lab order authority/lookup/request và immutable V1 result đã code; không
coi M6-11.4 E2E đã đạt từ unit/persistence tests. Refresh 2026-10-16: public Lab-order route
và packaged Gateway acceptance đã có; chỉ còn Gateway discovery G1 tại
[H-VINH-01](../handoffs/HANDOFF-VINH-OUTPATIENT-MVP.md#h-vinh-01--hoàng-anh--gateway-discovery).
Billing exact LAB_TEST slice: [H-VINH-03](../handoffs/HANDOFF-VINH-OUTPATIENT-MVP.md#h-vinh-03--lộc--billing-phối-hợp-slice-với-vinh).
Report consume cùng result fixture: H-VINH-02. Producer/consumer/owner/fixtures/điều kiện đóng
ghi tại từng handoff; không copy payload hoặc đoán fields ở plan.
Packaged Core/Clinical/Lab slice đã đạt persisted order → request → fixture clearance →
immutable result → actual Clinical attachment, bao gồm source mismatch, unpaid denial,
duplicate effect/broker drain và terminal mutation denial. Clinical prerequisite dùng audited
override; Lab grant là fixture. Public normal-payment flow và Report acceptance vẫn mở.

### M6-12 Billing selected charge payment và clearance

Owner: Lộc; Vinh/Huy consulted. Dependency: M6-02, M6-05.

- [ ] M6-12.1 Reuse EXAM/LAB/Rx issuers, exact account/source/price và mode selection.
- [ ] M6-12.2 Complete payment idempotently, allocations/transaction/outbox atomic.
- [ ] M6-12.3 Publish exact-purpose/target grants chỉ khi consumer fixtures/PG/Rabbit acceptance đạt.
- [ ] M6-12.4 Reuse linked refund/compensation, reject over-allocation/cross-account/wrong-source.

Đóng khi: V1 ngoại trú coherent; admission/deposit/settlement/full insurance không task bắt buộc.

**Remaining P0, consumer Clinical/Lab + Billing FE (refresh `c8024921`):**
L1 source-bound discovery GET và L2 selective eligible writer đã có, targeted DB/API tests đạt.
Còn exact Gateway discovery allowlist (Hoàng Anh G1), L3 failure/ordering và full producer artifacts
(Lộc L3/L4; partial Rabbit/JVM recovery đã có PR #401), rồi paid consumer slices (Vinh). Không giao lại endpoint hoặc suy DB flag
là broker delivery. Acceptance tại [handoff](../handoffs/HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md).

### M6-13 Saga và lỗi phân tán

Owner: Huy + Lộc; Vinh/Support verify. Dependency: M6-09, M6-10, M6-11, M6-12.

- [ ] M6-13.1 Pharmacy unavailable → pending delivery → recover, không mất payment effect.
- [ ] M6-13.2 Crash sau stock commit trước receipt/ACK, replay không xuất lần hai.
- [ ] M6-13.3 Stock/expiry/late-payment failure → terminal fact → one compensation đúng mode.
- [ ] M6-13.4 Bounded retry/DLQ/conflict tests trên PostgreSQL/RabbitMQ thật, no fake exactly-once.

Đóng khi: có proof effects một lần cho duplicate/race/crash và financial state đối soát.

## P3 Chốt profile và nghiệm thu

### M6-14 Tách deferred khỏi MVP runtime

Owner: Vinh/shared + owner modules + Hoàng Anh/Gateway. Dependency: M6-03, M6-12.

- [ ] M6-14.1 Profile MVP không start Inpatient/Surgery apps, không admission/surgery route.
- [ ] M6-14.2 Tắt deferred issuers/listeners/schedulers/publication theo từng flag, không global bypass.
- [ ] M6-14.3 Giữ code/DDL/data/HELD/pending evidence; không purge deferred để demo sạch.
- [ ] M6-14.4 Startup/dependency tests không gọi deferred service; messages giữ đúng quarantine policy.

Đóng khi: hoãn thật ở runtime và không ACK-drop historical financial facts.

Vinh 2026-10-15: Clinical/Lab đã có profile/guards và Clinical HELD/quarantine; còn full-runtime
deferred-route/dependency acceptance với Gateway owner và packaged six-service profile.
Không giao Inpatient/Surgery features để đóng task này.

### M6-15 Gateway và frontend smoke

Owner: Hoàng Anh/Gateway + từng FE owner; Vinh shared. Dependency: M6-04, M6-05, M6-06, M6-07, M6-08, M6-09, M6-10, M6-11, M6-12, M6-14.

- [ ] M6-15.1 Routes Core/Clinical/Lab/Pharmacy/Billing/Support, exact role × path × method denial.
- [ ] M6-15.2 Service-only lookups và refresh/service/human JWT tests, không broaden permission.
- [ ] M6-15.3 Existing FE URLs/DTO/themes giữ nguyên; scope surgery/inpatient không thuộc MVP navigation.
- [ ] M6-15.4 Login và flow smoke qua Gateway; không raw fetch/direct service ports.

Đóng khi: Gateway suite và client smoke đạt, không yêu cầu redesign FE hoặc phát triển mobile mới.

### M6-16 Nghiệm thu end to end

Owner: cả 4 owner. Dependency: M6-13, M6-15.

- [ ] M6-16.1 Run full happy path với seed isolated, real DB/broker và packaged apps.
- [ ] M6-16.2 Run fault/security/concurrency/coverage matrix ở tài liệu nghiệm thu.
- [ ] M6-16.3 Reconcile IDs/source effects/stock/ledger/Report/history, zero unexpected diff.
- [ ] M6-16.4 Record test counts/failed/skipped, flags/profile và demo evidence đủ tái tạo.

Đóng khi: tất cả acceptance bắt buộc đạt; skipped không là pass, fixture không là dữ liệu production.

### M6-17 Rollout và rollback không chuyển dữ liệu

Owner: coordinators + operator được giao. Dependency: M6-16.

- [ ] M6-17.1 Backup/restore clone, per-deployment credentials và pending cursors trước switch.
- [ ] M6-17.2 Stop/drain old Core/Support writers/jobs; start new host cùng stores, no dual writers.
- [ ] M6-17.3 Switch callers/routes, smoke/reconcile và observe lag/resources theo budget đã chọn.
- [ ] M6-17.4 Rehearse rollback cùng current DB; preserve accepted payments/messages/deliveries.

Đóng khi: six-service profile hoạt động, không cần D2/copy DB và rollback không mất writes.

### M6-18 CI và bàn giao

Owner: shared CI owner + cả 4 owner. Dependency: M6-16, M6-17.

- [ ] M6-18.1 CI chạy module/host/contract/migration regression và required checks cho MVP.
- [ ] M6-18.2 Kiểm tra profile chỉ 6 business applications, Gateway/Eureka riêng.
- [ ] M6-18.3 Update README/agent entry pointers/workboard còn ngoài docs theo scope được giao.
- [ ] M6-18.4 Chốt evidence + owner acceptance, báo completed MVP riêng deferred readiness.

Đóng khi: teammate clone chạy/review được, không tiếp tục giao task ngoài scope từ docs lịch sử.

## Thứ tự và phân công

M6-01 → 02 → 03 → Core 04–05 và Support 06–08 → Billing 12 →
Clinical 09 / Pharmacy 10 / Lab 11 → 13, 14 → 15 → 16 → 17 → 18.
Các task độc lập có thể thực hiện theo owner; delegation không mở scope. Demo không được bỏ
kiểm thử luồng lỗi vì chạy deadline. Production/backend chưa thay đổi trong lượt viết plan.

Huy tập trung M6-06/08/10/13, phối hợp đóng gói Support, không tiếp tục Surgery.
Vinh tập trung Clinical/Lab, shared integration và profile, không tiếp tục Inpatient.
Lộc tập trung Billing/Rx compensation và in-app, không full admission settlement.
Hoàng Anh tập trung Core/auth/Gateway, không redesign identity/mobile.

Không bắt buộc đưa toàn bộ deferred modules ra khỏi root reactor: giữ test compile và kiểm tra
regression còn cần. Chỉ runtime/deployment/profile và acceptance phạm vi phải đúng MVP.
