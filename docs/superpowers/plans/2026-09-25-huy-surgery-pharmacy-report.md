# Kế hoạch triển khai phần Huy: Surgery, Pharmacy và Report

**Ngày lập / rà soát:** 2026-09-25 · **Baseline:** HEAD `d97ec3e`, nhánh `Huy`, kèm thay đổi local chưa commit.
**Owner:** Huy (`LQHuy0210`) · **Trạng thái:** đang triển khai từng phần; R-02 và P-01 đã xong local, P-01 đã qua PostgreSQL/RabbitMQ Testcontainers; Surgery chưa có module.

## 1. Mục tiêu, phạm vi và cách dùng plan

Xây Surgery quản lý vòng đời ca mổ; mở rộng Pharmacy cho thuốc nội trú; mở rộng Report theo care–finance. Mỗi task phải đi từ quy tắc nghiệp vụ đến transaction, contract và test, không chỉ tạo đủ controller/entity.

Phạm vi production của Huy theo quy hoạch care–finance:

- `backend/surgery-service/**`: service mới, port 8091, DB `mediflow_surgery`, API `/api/v1/surgery`.
- `backend/pharmacy-service/**`: giữ outpatient hiện hữu, bổ sung admission context và chính sách cấp phát đã được chốt.
- `backend/report-service/**`: projection chỉ nhận event, không quyết định điều trị hoặc tài chính.

Clinical, Inpatient, Lab, Billing, Notification, Organization, Patient và Gateway chỉ được đọc để kiểm tra contract. Thiếu producer/lookup thì tạo handoff, không sửa module của owner khác. Root POM, Compose, script khởi tạo DB, Common, Eureka và CI là shared scope: chỉ sửa khi được giao rõ. Dockerfile bên trong Surgery thuộc module Huy; wiring Docker/Compose dùng chung là task phối hợp.

Plan ghi nhận implementation và verification theo từng task; thay đổi ngoài scope được giữ nguyên. Frontend/mobile, triển khai lên production và di trú dữ liệu production không nằm trong phạm vi; migration/rollout vẫn phải được thiết kế và kiểm thử.

### 1.1. Nguồn sự thật

1. [Kiến trúc care–finance](../../architecture/mediflow-care-finance-redesign.html): luồng nghiệp vụ, ownership và state machine.
2. [Quy tắc tích hợp](../../ai/16-care-finance-integration-contracts.md) và các canonical contract: [Care–Billing](../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md), [Inpatient–Surgery](../../handoffs/care-finance/CONTRACT-INPATIENT-SURGERY-01.md), [Surgery–Billing](../../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md), [Identity](../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md), [Projections](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md).
3. Service design: [Surgery](../../ai/services/surgery.md), [Pharmacy](../../ai/services/pharmacy.md), [Report](../../ai/services/report.md).
4. [Blueprint](../../ai/04-microservice-blueprint.md), [API](../../ai/05-api-conventions.md), [events](../../ai/06-events-rabbitmq.md), [security](../../ai/07-security-rbac.md), [persistence](../../ai/08-persistence-naming.md), [testing](../../ai/09-testing.md).
5. Implementation baseline: [shared spec](../../eproject_general_plan/backend-spec/00-overview.md), [Pharmacy spec](../../eproject_general_plan/backend-spec/05-pharmacy.md), [Report spec](../../eproject_general_plan/backend-spec/08-report.md), code và test hiện tại.

Spec cũ không tự định nghĩa các nghiệp vụ nội trú/mổ mới. Nếu nguồn mâu thuẫn, ghi quyết định còn mở, không chọn tùy ý hoặc sửa tài liệu để hợp thức hóa code.

### 1.2. Hiện trạng và phần cần bổ sung

| Context | CURRENT đã kiểm tra | TARGET / việc còn thiếu |
|---|---|---|
| Surgery | Chỉ có service design; chưa có module, implementation-ready spec, DDL, DB/route runtime. | Referral → preparation → readiness → schedule/start → result/cancel; đủ audit, concurrency và integration. |
| Pharmacy | `CreatePrescriptionRequest.recordId` bắt buộc; payment receipt, reservation, dispense locks, compensation và outbox đã có; P-01 đã xác minh bằng PostgreSQL/RabbitMQ Testcontainers; Flyway V1–V12. | Context additive; admission authorization riêng; giữ nguyên đảm bảo kho và outpatient compatibility. Không viết lại toàn bộ stock workflow. |
| Report | 5 binding: `medicalrecord.created`, `lab.result.created`, `prescription.filled`, `payment.completed`, `payment.failed`; contribution theo invoice; Flyway V1–V2. | Journal/replay, classified financial facts và operational projections mới; không cộng deposit vào doanh thu hiện tại. |
| Phụ thuộc | Inpatient mới có foundation; nhiều producer business/ledger/clearance fixture chưa hoàn tất; Surgery route chưa có. | Phân biệt contract đã khóa, fixture đã test và producer đã phát live. |
| Local verification | R-02 strict access-token đã qua test; P-01 race/recovery/compensation và full Pharmacy suite đã chạy với PostgreSQL/RabbitMQ Testcontainers. | P-01 verification pass local; producer vẫn giữ nguyên, chưa gọi là service-wide release. |

### 1.3. Nhãn thực hiện trước khi chốt D01–D12

Nhãn đặt **ngay trên từng subtask**, độc lập với checkbox và trạng thái tiến độ. Có thể làm một phần không có nghĩa cả task đã hết blocker hoặc được bật live.

| Nhãn | Ý nghĩa / giới hạn |
|---|---|
| **[NGAY]** | Có thể sửa code/test hiện hữu hoặc làm tài liệu trong scope Huy, không cần câu trả lời D01–D12. Nếu ghi “một phần”, chỉ làm đúng phần được nêu. |
| **[CHUẨN BỊ]** | Làm ngay spec, inventory, test scenarios hoặc fixture **đề xuất**; chưa phải quyền implement behavior/schema/wire còn chưa khóa. Không tính mock/draft là G1 hay E2E thật. |
| **[NỘI BỘ]** | Không phụ thuộc 12 quyết định cho phần được chỉ rõ, nhưng phải xong spec foundation/module/technical task của Huy trước. Không gọi là code được ngay từ trạng thái repo hiện tại. |
| **[CHỜ Dxx]** | Phần implementation chính cần đúng quyết định được ghi; vẫn có thể làm bước chuẩn bị nhỏ nêu cuối dòng. Không tự chọn policy thay team. |
| **[HẠ TẦNG] / [OWNER/HẠ TẦNG]** | Chờ Docker, module/route live hoặc phân công shared/owner; đây là blocker riêng, không phải D01–D12. |
| **[ĐÃ LÀM]** | Giữ nguyên phần đã có; chỉ regression/verification khi cần. “Đã viết test” khác “đã chạy test DB”. |

**Quy tắc áp dụng:** G0–G3 và ownership vẫn giữ nguyên. Nhãn NỘI BỘ của Surgery chỉ mở **foundation kỹ thuật không chứa policy chưa chốt** sau khi H-01d ghi đủ đặc tả phần đó; riêng thao tác **tạo module mới** còn phải tuân thủ skill `new-microservice` (root registration, V1 schema, domain/API/test đúng blueprint), nên design slice hiện tại chưa cho phép scaffold shell tách rời. Không triển khai model/DDL business, bật listener nghiệp vụ, cho cấp thuốc nội trú hoặc ghi nhận finance metrics bằng giả định. Việc này không phê duyệt thay Dxx hoặc đóng handoff.

### 1.4. Bảng chọn việc không phải đợi 12 quyết định

| Task | Làm được ngay tại baseline này | Bước không cần Dxx nhưng còn dependency nội bộ / giới hạn |
|---|---|---|
| **H-01** | H-01a–d: soạn phần spec đã rõ, đánh dấu TBD; H-01e: decision/acceptance log. | Hoàn thiện foundation spec trước S-01; không đánh dấu toàn bộ H-01 xong. |
| **S-01** | S-01d handoff đã soạn DONE_LOCAL. | S-01a–c/e: module/config/auth/tests sau implementation-ready spec + scaffold skill và shared registration do owner khác thực hiện; không tạo shell rỗng từ design slice. |
| **S-02** | Chuẩn bị model alternatives và technical persistence/test design. | S-02b–g phần inbox/outbox/atomicity sau foundation; business tables và command policies vẫn chờ Dxx. |
| **S-03** | S-03f: manifest/harness; inventory fixture/validation scenarios. | S-03c transport, S-03d lookup đã khóa, S-03g web plumbing sau foundation; không mở business listeners/API. |
| **S-04** | S-04c: duplicate/conflict test scenarios; draft create/read contract. | Chưa code case workflow/schema khi D01/D02/D12 còn mở; pagination infrastructure dùng lại S-03g. |
| **S-05** | Truth table 6 guards, checklist/consent negative scenarios. | S-05e pure AND-policy/test sau foundation; chưa nối validity, state change hoặc emergency override. |
| **S-06** | S-06c: so sánh DB locking và test tranh slot; interval/resource proposals. | Production booking, buffer/TTL/eligibility vẫn chờ D04/D05. |
| **S-07** | S-07e: duplicate/race scenarios; start/result/cancel acceptance drafts. | Production transitions, actual-item/cancel/override fixtures chưa được khóa. |
| **P-01** | **P-01c–f: ép race, crash/retry, failure/compensation và kiểm tra trên PostgreSQL/RabbitMQ.** | Đã hoàn tất local, không cần Dxx; a/b đã có, không làm lại. |
| **P-02** | P-02e/f: inventory và legacy fixtures; **P-02g legacy characterization DONE_LOCAL**. | Chưa chạy migration care context hoặc đổi DTO/event khi D08/D11/D12 chưa chốt. |
| **P-03** | **P-03d outpatient transaction regression DONE_LOCAL**; **P-03g local actor/audit + JWT/correlation/late-payment regression DONE_LOCAL**. | Dùng chung evidence với P-01/P-02, không viết test trùng; admission authorizer và cross-service clearance/correlation vẫn chờ quyết định. |
| **R-01** | a/b/c/g: thiết kế/inventory; **f/i: legacy concurrency, decoder và retry regression**. | Journal migration/keys mới/cutover chưa implement trước quyết định liên quan; PG/MQ tests cần Docker. |
| **R-02** | Code đã xong; **chạy/giữ JWT regression** không cần Dxx. | Gateway smoke phụ thuộc route/runtime, không viết lại JWT filter. |
| **R-03** | a/h: bảng required facts và expected-outcome scenarios để Billing duyệt. | Chưa implement classified finance projector hoặc mở finance query mới. |
| **R-04** | **h/i phần legacy:** role, date/zero-fill/query-boundary tests; chuẩn bị query/metric specs. | Chưa thêm admission/surgery/bed metrics khi chưa đủ fixture và definition. |
| **X-01** | h: mẫu evidence; a/f/g: environment/failure/observability checklist. | New-flow E2E cần decisions + modules/producer/route/Docker; chỉ kiểm tra legacy riêng nếu môi trường sẵn sàng. |

**Ưu tiên lấy việc:** P-01c–f, P-02g legacy characterization, P-03d outpatient atomicity regression, P-03g local actor/audit regression, R-01f concurrency regression và các slice legacy R-01i/R-04h/R-04i đã hoàn tất local; H-01a/H-01d design slice và S-01d handoff cũng đã có. Tiếp theo là H-01b/c phần độc lập, quyết định D01/D02 và những quyết định cần cho V1; chỉ sau implementation-ready G0 + shared owner assignment mới scaffold S-01 theo skill. Các việc soạn spec/scenarios có thể xen kẽ; không cần chờ đủ cả 12 quyết định để tiếp tục phần độc lập. Đây là thứ tự đề xuất, không chuyển checkbox của phần còn chờ contract thành hoàn tất.

## 2. Kiến trúc thực thi và các gate

### 2.1. Ranh giới dữ liệu và luồng

```text
Clinical / Inpatient -- surgery.requested (surgeryRequestId) --> Surgery
Surgery -- [charge-source bridge CHƯA KHÓA, surgeryCaseId] --> Billing
Billing -- financial.clearance.granted (purpose=SURGERY, exact target) --> Surgery
Surgery -- surgery.ready / completed / cancelled --> consumers theo contract
Pharmacy -- prescription facts + exact care context --> Billing / Report / care consumers
Billing / Clinical / Inpatient / Surgery -- facts --> Report projections
Patient / Organization -- authenticated lookup khi cần quyết định tức thời --> Surgery
```

Không coi `surgery.requested` có sẵn `surgeryCaseId`: case do Surgery tạo sau khi nhận referral. Contract hiện có khoảng trống giữa referral và charge source; H-01/D02 phải giải quyết trước khi nối Billing.

- Domain thuần Java; application định nghĩa in-port/out-port và điều phối; `web/` và `messaging/consumer/` là driving adapters; JPA/Rabbit/REST ở infrastructure. Không để JPA entity, Spring Page hoặc AMQP Message đi vào application.
- Tham chiếu khác service là UUID và snapshot audit cần thiết; không FK/DB join xuyên service. `sub=accountId`, không phải `staffId`.
- Business mutation + history + outbox cùng transaction. Event delivery có thể lặp; phải bảo đảm effect duy nhất bằng DB, không bằng khóa trong RAM.
- Phân biệt dedupe `eventId` với business idempotency: referral ID, case operation/version, dispense operation, transaction/refund ID. Unique key chỉ chứa `eventId` không ngăn cùng nghiệp vụ được phát lại với event ID mới.
- Event mới theo envelope `{eventId,eventType,version,occurredAt,correlationId,producer,payload}`; routing key runtime không thêm một cơ chế `.v1` song song.
- Event Pharmacy/Report legacy đang dùng payload phẳng. Chuyển sang nested envelope là breaking shape: phải có decoder/version rollout và fixture tương thích, không bọc lại tất cả event trong một lần sửa.
- Money dùng `BigDecimal`; ID dùng UUID; thời gian nghiệp vụ dùng `Instant` + `Clock` testable. Ngày báo cáo lấy theo timezone cấu hình, không theo giờ máy hoặc thời điểm consumer nhận.
- Report không gọi REST để bù thiếu dữ liệu, không phát command điều khiển workflow; notification/report outage không rollback giao dịch đã commit ở producer.

### 2.2. Gate tách theo từng lát cắt

| Gate | Cần có | Cho phép / chưa cho phép |
|---|---|---|
| **G0 — spec của lát cắt** | Invariant, DTO/nullability, transition, DDL mapping, transaction, error và test matrix đã rõ; quyết định liên quan được owner xác nhận. | Có thể chuẩn bị thiết kế/test độc lập khi còn blocker. Surgery production code chỉ bắt đầu khi có implementation-ready spec cho phần đó; không tự lấp quyết định mở. |
| **G1 — wire compatibility** | Contract ID/version; fixture producer; serializer/deserializer và consumer test cùng fixture; danh sách consumers bị ảnh hưởng. | Consumer có thể được kiểm thử offline trước producer live. Không đánh dấu `IMPLEMENTED` nếu phía còn lại chưa đạt gate. |
| **G2 — local correctness** | Unit/web/architecture + PostgreSQL/Rabbit tests liên quan thực sự chạy, migration và recovery pass. | Hoàn thành phần code Huy; chưa đồng nghĩa workflow liên service đã chạy. |
| **G3 — integrated release** | Producer live, route/auth qua Gateway, E2E, replay/reconciliation, rollout/rollback và handoff liên quan được giải quyết. | Mới bật lát cắt nghiệp vụ tương ứng. Không bắt mọi tính năng phải chờ mọi handoff không liên quan. |

Trạng thái task trong plan: `TODO`, `IN_PROGRESS`, `WAITING_DECISION`, `WAITING_VERIFICATION`, `DONE_LOCAL`, `DONE_INTEGRATED`. Đây là tiến độ công việc, không thay thế status canonical contract như `DESIGN_READY`, `CONSUMER_READY`, `IMPLEMENTED`.

## 3. H-01 — khóa đặc tả và quyết định nghiệp vụ

**Trạng thái:** `IN_PROGRESS / WAITING_DECISION`. Không đánh dấu toàn bộ H-01 xong chỉ vì đã tạo handoff.
**Đầu ra:** implementation spec dự kiến `docs/eproject_general_plan/backend-spec/10-surgery.md` (kiểm tra lại số trống; `09-gateway.md` đã tồn tại); phụ lục migration Pharmacy/Report; fixture manifest và decision log. Chỉnh canonical/shared docs theo phân công, không tự coi đề xuất là hợp đồng đã duyệt.

### 3.1. Decision backlog

Các dòng sau còn mở ở mức đủ để code; các nguyên tắc đã chốt trong contract vẫn giữ nguyên.

| ID | Quyết định / khoảng trống cần chốt | Owner phối hợp | Task bị chặn và bằng chứng cần có |
|---|---|---|---|
| **D01** | Ca chỉ có `recordId` được mổ ngoại trú hay phải có admission trước clearance? Khi có cả hai ID, trường nào là episode và trường nào chỉ là referral reference? | Huy + Vinh + Lộc | S-04/05/07: request/clearance/completed/cancelled fixture cho mỗi context được hỗ trợ; sai patient/episode bị reject. |
| **D02** | Producer nào tạo referral; ổn định `surgeryRequestId`; Billing nhận `surgeryCaseId` + planned items bằng contract nào sau khi Surgery tạo case? | Huy + Vinh + Lộc | S-03/04: trace một referral → một case → một charge source. Không phát lại `surgery.requested` với producer/ý nghĩa khác hoặc tự bịa tên event. |
| **D03** | Template checklist theo procedure, mandatory codes/version, nguồn evidence, exact order/case correlation, hết hạn/correction/revocation. | Huy + Vinh/Lab | S-05: fixture đủ/thiếu/stale/wrong-order; template version đã dùng không bị sửa hồi tố. |
| **D04** | Room master/lookup thuộc ai; eligible staff-role mapping; resource reservation có TTL không, giữ chỗ lúc nào và thả lúc nào? | Huy + Hoàng Anh | S-06: room/staff lookup; lịch kề nhau/trùng/đổi lịch; tính hợp lệ khi resource bị inactive. |
| **D05** | Phân biệt resource reservation với case `SCHEDULED`; đường chuyển trạng thái khi consent/evidence/clearance hết hiệu lực hoặc đổi lịch. | Huy + Vinh | S-05/06/07: transition table, API thao tác và ready-notification/revision semantics không tạo vòng phụ thuộc. |
| **D06** | Consent type, signer/witness, revoke command/role; emergency được bỏ qua guard nào, ai duyệt, quyền self-approve và thời hạn hiệu lực. | Huy + Vinh + Lộc | S-05/07: allow/deny matrix và audit fixture; nếu chưa hỗ trợ override thì ghi rõ V1 không có đường bypass. |
| **D07** | Hủy ở từng stage; `IN_PROGRESS_ABORTED` lưu phần đã thực hiện thế nào; kết quả có correction/version không; policy giải phóng tài nguyên. | Huy + Vinh + Lộc | S-07/R-04: partial-abort/result fixture và financial adjustment outcome; không giả vờ mọi hủy đều chưa tiêu hao. |
| **D08** | Authorization thuốc nội trú, admission còn được kê/cấp lúc nào; một đơn-một lần cấp hay nhiều lần/theo liều; charge/compensation khi hủy, hết hạn, cấp thất bại. | Huy + Vinh + Lộc | P-02/03: exact admission/order/dispense key, eligible/denied fixture; không tái dùng outpatient paid flag. |
| **D09** | Billing classification, transaction/allocation/settlement version; delta hay snapshot; deposit release, partial refund và receivable adjustment. | Lộc + Huy | R-01/03: event nào đủ dữ liệu cho từng chỉ tiêu, source key và expected totals; không suy đoán từ invoice total. |
| **D10** | Thời điểm giải phóng/chuyển giường và available-bed capacity; định nghĩa LOS theo medical discharge hay administrative close; KPI thời gian/biến chứng. | Vinh + Huy | R-04: event fields và metric definition. Có admission.started/closed chưa đủ để công bố tỷ lệ sử dụng giường chính xác. |
| **D11** | Compatibility payload legacy → envelope mới; source/version cutover, kể cả payment → prescription clearance; nguồn replay lịch sử, retention, late event và correction. | Các producer/consumer, Huy điều phối phần sở hữu | S-03/P-02/03/R-01: fixture cũ/mới cùng chạy và quy tắc chống đếm đôi; phạm vi lịch sử rebuild được. |
| **D12** | Clearance expiry/revocation/freshness và late delivery trước case; cách xác nhận admission–patient–department, không chỉ existence. | Huy + Lộc + Vinh | S-03/04/05, P-03: authoritative event/lookup và error/recovery policy; không tự thêm endpoint của service khác. |

[D01–D07/D12 có nội dung liên quan trong handoff Surgery](../../handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md); [D08–D11 có yêu cầu producer/consumer trong handoff Pharmacy/Report của Huy](../../handoffs/HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md). Handoff là yêu cầu phối hợp, không tự đóng decision log hoặc thay canonical contract.

### 3.1a. Decision log và acceptance evidence (H-01e)

Log này tách **đề xuất trong tài liệu** khỏi **quyết định đã được owner xác nhận**. `—` nghĩa là **chưa có bằng chứng**, không phải chấp thuận mặc định. Chỉ điền tên người xác nhận, ngày và link tới canonical contract/service doc hoặc biên bản quyết định sau khi chính owner xác nhận; fixture phải là file producer/consumer cùng version, không phải đoạn mô tả trong plan.

| ID | Trạng thái | Người xác nhận | Ngày xác nhận | Link quyết định canonical | Fixture/test chung | Live producer/consumer |
|---|---|---|---|---|---|---|
| D01 | OPEN | — | — | — | MISSING | NO |
| D02 | OPEN | — | — | — | MISSING | NO |
| D03 | OPEN | — | — | — | MISSING | NO |
| D04 | OPEN | — | — | — | MISSING | NO |
| D05 | OPEN | — | — | — | MISSING | NO |
| D06 | OPEN | — | — | — | MISSING | NO |
| D07 | OPEN | — | — | — | MISSING | NO |
| D08 | OPEN | — | — | — | MISSING | NO |
| D09 | OPEN | — | — | — | MISSING | NO |
| D10 | OPEN | — | — | — | MISSING | NO |
| D11 | OPEN | — | — | — | MISSING | NO |
| D12 | OPEN | — | — | — | MISSING | NO |

**Quy trình cập nhật mỗi dòng:** (1) owner liên quan chọn một policy hoặc loại trừ chức năng khỏi V1 bằng văn bản; (2) sửa canonical contract/service doc, ghi người xác nhận + ngày + link; (3) producer tạo fixture có version, consumer chạy cùng fixture và negative tests; (4) chỉ ghi `LIVE` sau producer/consumer phát/nhận thật và integration smoke pass. Với quyết định chỉ là nội bộ Surgery, cột fixture ghi `NOT_APPLICABLE` kèm link unit/integration test, không điền `MISSING` thành `PASS` bằng lý luận. Một quyết định `APPROVED` không tự biến toàn H-01 hay G1/G3 thành DONE.

**Các gate độc lập cần công bố trong báo cáo:** `SPEC` = DRAFT/APPROVED; `FIXTURE` = MISSING/PRODUCER_ONLY/SHARED_TESTED; `RUNTIME` = NOT_BUILT/LOCAL_TESTED/LIVE_E2E. Hiện Surgery là **SPEC=DRAFT, FIXTURE=MISSING, RUNTIME=NOT_BUILT**. Handoff quyết định Surgery còn `OPEN`; handoff shared bootstrap/Gateway cũng còn `OPEN`. Khi mọi acceptance liên quan đạt, chuyển quy tắc bền vững vào canonical docs và gỡ handoff khỏi registry trong cùng thay đổi; không giữ handoff `COMPLETE` như nguồn sự thật thứ hai.

### 3.2. Checklist thực hiện H-01

- [ ] **[CHUẨN BỊ]** **H-01a — business model:** glossary, ownership matrix, care-episode/referral mapping; phân biệt logical entity names với DDL vật lý. Surgery mặc định Vietnamese snake_case; ngoại lệ English hiện có của service khác không tự áp dụng. **Phần được làm trước:** Làm ngay glossary/ownership và phần naming đã chốt; mapping episode/referral là bản đề xuất tới khi D01/D02 được duyệt.
  - [x] **[DONE_LOCAL 2026-09-25 — draft]** Tạo [`backend-spec/10-surgery.md`](../../eproject_general_plan/backend-spec/10-surgery.md) với glossary định danh, ownership matrix, mapping admission/record-only/both-ID ở trạng thái đề xuất, gap referral→case→charge source và cảnh báo logical model ≠ physical DDL. Đăng ký bản nháp trong spec index; chưa code Surgery.
  - [ ] **[CHỜ D01/D02]** Chốt context record-only/outpatient, producer/stable surgeryRequestId và post-case charge source bằng fixture/owner xác nhận; chỉ sau đó mới đóng H-01a và nâng spec khỏi DRAFT.
- [ ] **[CHUẨN BỊ]** **H-01b — use cases:** state-transition table gồm from/command/guard/to/history/event; tạo, chuẩn bị, consent/revoke, reservation/schedule, start, complete, cancel, query. Mỗi rule có ID và ít nhất một negative test. **Phần được làm trước:** Làm ngay bảng rule/test cho invariant đã có; để rõ ô chưa quyết định ở consent, lịch, emergency và cancellation, không khóa thay D03–D07.
  - [x] **[DONE_LOCAL 2026-09-25 — draft inventory]** Thêm §7 của [`backend-spec/10-surgery.md`](../../eproject_general_plan/backend-spec/10-surgery.md): 12 rule/transition rows có from-command-guard-to-history-event/gate và 12 negative fixtures. Chỉ rõ mâu thuẫn READY cần room/time nhưng SCHEDULED ở sau READY; chưa chọn policy thay D04/D05.
  - [ ] **[CHỜ D01–D07/D12]** Khóa từng transition, guard, error và fixture được owner duyệt; chạy rule tests rồi mới đóng H-01b.
- [ ] **[CHUẨN BỊ]** **H-01c — contracts:** exact types/nullability, DTO validation/role/error; envelope/version; event owner, business key, consumer list và fixture. Trường bắt buộc không có producer phải để blocker, không cho giá trị giả. **Phần được làm trước:** Lập manifest, envelope, role matrix và inventory contract hiện tại ngay; schema/fixture mới chỉ là draft nếu còn Dxx liên quan.
  - [x] **[DONE_LOCAL 2026-09-25 — manifest]** Thêm §8 của Surgery spec: envelope/routing convention, ID types, route/role matrix và producer–consumer lookup/event inventory; mọi DTO, nullability, error và fixture chưa chốt đều được đánh dấu rõ, không tạo payload giả.
  - [ ] **[CHỜ D01–D07/D11/D12 + PRODUCERS]** Chốt exact request/response/event schema, version, business keys, negative fixture và contract tests hai phía; chỉ sau đó đóng H-01c.
- [ ] **[CHUẨN BỊ]** **H-01d — persistence/concurrency:** aggregate boundary, unique/index/check, lock ordering, optimistic version, inbox/outbox, pending dependency và recovery; startup/migration assumptions. **Phần được làm trước:** Có thể hoàn thiện spec foundation độc lập: package/config/security, technical inbox/outbox và test strategy. DDL business/command keys vẫn chờ các Dxx của từng aggregate.
  - [x] **[DONE_LOCAL 2026-09-25 — design slice]** Bổ sung §6 của [`backend-spec/10-surgery.md`](../../eproject_general_plan/backend-spec/10-surgery.md): ranh giới lớp/transaction, version và resource-lock caveat, inbox conflict/pending, outbox confirm/lease/crash recovery, migration/startup assumptions và 6 nhóm verification. Chưa tạo migration/module hay chạy test từ thiết kế này.
  - [ ] **[CHỜ D02–D07/D11/D12 + OWNER]** Khóa aggregate/physical DDL, business key/command revision, pending expiry/retention, producer/consumer contract; có shared bootstrap rồi chạy DB/Rabbit/security tests để đóng H-01d. Không xem design slice là implementation-ready spec.
- [ ] **[NGAY]** **H-01e — review/acceptance:** ghi người xác nhận + ngày + link quyết định cho từng Dxx; trạng thái spec/fixture/live riêng biệt. Quy tắc bền vững chuyển vào canonical docs; chỉ đóng handoff khi đủ acceptance, theo lifecycle của registry. **Phần được làm trước:** Cập nhật decision log và acceptance checklist ngay; chỉ ghi xác nhận khi thực sự nhận được, không đóng handoff thay owner.
  - [x] **[DONE_LOCAL 2026-09-25 — decision log prepared]** Lập mục 3.1a cho D01–D12 với cột owner xác nhận/ngày/link/fixture/live chưa có bằng chứng, cùng quy tắc chuyển trạng thái `SPEC/FIXTURE/RUNTIME`. Không ghi tên hay ngày chốt giả; handoff vẫn OPEN.
  - [ ] **[CHỜ OWNER + EVIDENCE]** Cập nhật từng dòng khi có xác nhận thật; chuyển quy tắc sang canonical docs và gỡ active handoff chỉ sau acceptance tương ứng.

**Nghiệm thu:** người khác có thể viết test từ spec mà không phải tự chọn ý nghĩa episode, giá, clearance, trạng thái hoặc authorization. Dxx chưa chốt chỉ chặn lát cắt liên quan, không chặn P-01/R-02 hoặc việc soạn spec độc lập.

## 4. Surgery — task theo kiến trúc và business logic

Đường dẫn dưới đây tương đối với `backend/surgery-service/` (chưa tồn tại). Tên class/port là thiết kế dự kiến, phải khóa ở H-01 trước code.

### S-01 — module, configuration và security baseline

**Phụ thuộc:** G0 implementation-ready cho module theo skill scaffold; shared registration có owner riêng. **Trạng thái:** `IN_PROGRESS` chỉ S-01d handoff; chưa có module.

- [ ] **[NỘI BỘ · CHỜ G0/OWNER]** **S-01a:** tạo module/POM kế thừa version, `SurgeryServiceApplication`, package blueprint, nested `AGENTS.md`, README, Dockerfile module. Không controller placeholder trả thành công. **Giới hạn hiện tại:** Dựng shell riêng có thể độc lập về mặt business, nhưng skill `new-microservice` yêu cầu đăng ký root và V1 schema/domain/API/test khi tạo module. Vì G0 chưa implementation-ready và root thuộc shared owner, chưa scaffold module từ design slice H-01d; không suy luận policy để vượt gate.
- [ ] **[NỘI BỘ]** **S-01b:** `application.yml`, test profile, env cho DB/Rabbit/Eureka/JWT, port 8091, health; không commit secret. Swagger chỉ mở theo policy; health không phụ thuộc việc fake business data. **Phần được làm trước:** Sau S-01a và foundation spec; chỉ cấu hình module/test profile, không sửa Compose/DB bootstrap dùng chung.
- [ ] **[NỘI BỘ]** **S-01c:** `infrastructure/security`, `config/SecurityConfig`, exception envelope/correlation. Human API chỉ chấp nhận access token, role mặc định deny; service credential chỉ dùng đúng internal lookup. **Phần được làm trước:** Sau S-01a/G0 security; access-token/default-deny/correlation đã có chuẩn, không phụ thuộc emergency approval D06.
- [x] **[DONE_LOCAL 2026-09-25 — handoff prepared]** **S-01d:** đã tạo [`HANDOFF-SURGERY-FOUNDATION-BOOTSTRAP.md`](../../handoffs/HANDOFF-SURGERY-FOUNDATION-BOOTSTRAP.md) cho shared root module/DB/Compose và Gateway `/api/v1/surgery/** → lb://surgery-service`, với owner, thứ tự và test/acceptance riêng. **OPEN / OWNER:** chưa sửa shared/Gateway, chưa có route live; chờ phân công shared integrator và Hoàng Anh thực hiện/duyệt.
- [ ] **[NỘI BỘ]** **S-01e:** ArchitectureTest, context/config tests, 401/403/type tests; skeleton `surgery.http` chỉ chứa endpoint đã thực sự implement. **Phần được làm trước:** Sau S-01a–c: test architecture/auth/config được làm trước nghiệp vụ; actual DB/broker smoke còn cần hạ tầng, không thêm request cho endpoint chưa có.

**Nghiệm thu:** module build/context test đạt; import layers đúng; environment configuration hợp lệ; baseline auth không mở business API. Shared build/Compose/Gateway là acceptance riêng trong X-01, không silently bỏ qua.

### S-02 — domain, persistence và reliable transaction foundation

**Phụ thuộc:** S-01, H-01 DDL/transition/keys; không cần producer chạy live. **Trạng thái:** `IN_PROGRESS` chỉ S-02a design slice; chưa có code/migration.

**Phạm vi:** `domain/model|exception`, `application/port/out`, `infrastructure/persistence|messaging`, Flyway và tests.

- [ ] **[CHỜ D01–D07]** **S-02a:** SurgeryCase và value objects cho care reference/procedure/state; checklist/template snapshot, consent, schedule/team, result, status/readiness history. Chốt thành phần nằm trong aggregate và thành phần resource cần khóa ngoài case. **Phần được làm trước:** Có thể phác model/aggregate ngay dưới dạng draft; chưa khóa care refs, checklist, consent, schedule hoặc result production.
  - [x] **[DONE_LOCAL 2026-09-25 — model alternatives]** §9 của [`backend-spec/10-surgery.md`](../../eproject_general_plan/backend-spec/10-surgery.md) phân tách case-owned records và resource reservation xuyên ca, nêu hai phương án transaction/locking và từng quyết định Dxx. Không có domain/JPA class hay DDL nào được tạo.
  - [ ] **[CHỜ D01–D07]** Owner chốt composition, care refs, lịch/consent/result và lock order; sau đó mới tạo domain model + persistence tests.
- [ ] **[NỘI BỘ · một phần]** **S-02b:** V1 + migration tiếp theo khi cần; PK UUID, FK nội bộ, unique referral key, version, interval/check constraint và index list/filter/outbox. Không dùng tên logical `SURGERY_CASE` làm DDL mặc định. **Phần được làm trước:** Sau S-01 + H-01d chỉ làm schema kỹ thuật inbox/outbox nếu đã đặc tả riêng. DDL business/constraints chờ D01–D07; không tạo bảng business placeholder để né gate.
- [ ] **[NỘI BỘ · một phần]** **S-02c:** repository ports framework-free; JPA entity/mapper/adapter riêng. Bảo vệ concurrent mutate một case bằng version/lock; chốt ordering khóa resource chung ở S-06 để tránh deadlock. **Phần được làm trước:** Có thể làm ports/adapters cho inbox/outbox sau foundation; repositories SurgeryCase/resource và locking nghiệp vụ chờ model đã khóa, đặc biệt D02/D04/D05.
- [ ] **[NỘI BỘ · một phần]** **S-02d:** inbox lưu event identity/type/version và payload fingerprint; duplicate same → no-op, same ID/different payload → conflict. Command retry cần operation key riêng; contract key/expectedVersion do H-01 khóa. **Phần được làm trước:** EventId/fingerprint atomic dedupe có thể làm sau foundation, không cần Dxx. Business command key/revision chờ D02/D05/D07, không đặt một key chung tùy ý.
- [ ] **[NỘI BỘ · một phần]** **S-02e:** transaction application ghi mutation + audit + outbox; rollback không để inbox đã processed/outbox rời rạc. Event đến trước aggregate dùng pending durable có trạng thái, hạn xử lý và cảnh báo; lưu pending chưa có nghĩa đã áp dụng nghiệp vụ. **Phần được làm trước:** Làm transaction rollback + technical inbox/outbox tests sau foundation. Gắn mutation business và pending clearance resume/TTL vào ca thật còn chờ D02/D05/D12.
- [ ] **[NỘI BỘ]** **S-02f:** outbox dispatcher có claim/lease, publisher confirm, retry/backoff, recovery sau crash; phát lại giữ eventId. Bảo đảm thứ tự theo aggregate hoặc consumer xử lý revision; không hứa exactly-once broker. **Phần được làm trước:** Outbox claim/lease/confirm/retry/recovery kỹ thuật có thể làm sau S-01 và H-01d; chưa phát event business chưa khóa schema.
- [ ] **[NỘI BỘ · một phần]** **S-02g:** migration sạch/nâng cấp, map round-trip, concurrent first insert, transaction rollback, pending resume, lease recovery và DB constraint tests. **Phần được làm trước:** Test technical migrations/inbox/outbox sau các phần trên, cần PostgreSQL/Rabbit thật khi nghiệm thu; business migrations/pending-case tests chờ Dxx tương ứng.

**Nghiệm thu:** unique business key được DB bảo vệ; crash trước commit không có effect, sau commit/trước ACK retry không nhân đôi; infrastructure outage không bị đánh dấu là xử lý thành công. Retention inbox/outbox phải phù hợp cửa sổ replay, không xóa tùy tiện.

### S-03 — inbound/outbound contracts và delivery adapters

**Phụ thuộc:** S-02; G1 cho từng event/lookup, D01/D02/D11/D12. Phần transport tách khỏi nghiệp vụ S-04–07. **Trạng thái:** `IN_PROGRESS` chỉ S-03f manifest/test inventory; chưa có adapter/fixture.

**Phạm vi:** `messaging/consumer[/payload]`, `application/event`, in/out-ports, `infrastructure/client|messaging|config`, `web`, fixture tests.

- [ ] **[CHỜ D01/D02/D11/D12]** **S-03a:** consumer `surgery.requested` và `financial.clearance.granted`: validate routing/type/producer/version/schema, map sang command và gọi in-port; không xử lý nghiệp vụ ngay trong listener. **Phần được làm trước:** Làm ngay inventory envelope/validation scenarios; không wire listener xử lý ca thật trước schema/referral/clearance và compatibility gate.
- [ ] **[CHỜ D01/D12]** **S-03b:** clearance chỉ mở financial guard cho đúng purpose, patient, episode và case. Purpose của service khác trên topic chung được phân loại “không áp dụng”, không poison cả queue; `purpose=SURGERY` nhưng thiếu/sai target là contract error. Chốt dispatch này bằng fixture. **Phần được làm trước:** Có thể soạn negative cases purpose/patient/target đã biết ngay; accepted episode/freshness/dispatch fixture vẫn cần khóa trước production.
- [ ] **[NỘI BỘ · một phần]** **S-03c:** phân loại unknown target do event đến sớm với malformed target: pending durable/reconcile cho tình huống hợp lệ; lỗi schema/invariant bounded retry → DLQ; timeout/DB/broker outage retry hữu hạn. Chốt TTL và escalation pending, không requeue vô hạn. **Phần được làm trước:** Bounded retry/DLQ transport không cần Dxx, sau technical foundation. Chính sách early-clearance/pending TTL/resume chờ D12, không tự ACK mất nghiệp vụ.
- [ ] **[NỘI BỘ · một phần]** **S-03d:** REST ports Patient existence, Organization staff/department; dùng endpoint/ApiResponse/service JWT đã khóa, timeout/circuit breaker/correlation. `exists=false` khác 5xx/timeout/malformed. Lookup existence không đủ chứng minh staff đủ vai trò hay admission thuộc patient. **Phần được làm trước:** Patient exists + Org staff/department lookup shapes đã khóa: có thể làm adapter/auth/absence-vs-outage tests sau foundation bằng contract-based mocks. G1 vẫn cần producer fixture; eligibility/room/admission linkage chờ D04/D12.
- [ ] **[CHỜ D01/D02/D03/D05/D07/D11]** **S-03e:** ready/completed/cancelled serializer + outbox fixture. Chỉ bổ sung charge-source bridge và pre-op event sau D02/D03; không import DTO/event Java từ service khác. **Phần được làm trước:** Soạn fixture proposals ngay; không coi ready/result/charge bridge/pre-op schemas là đã được producer-consumer duyệt.
- [ ] **[CHUẨN BỊ]** **S-03f:** fixture manifest có contract/version/source fixture hash hoặc commit, producer/consumer test, trạng thái live. Test optional fields, thiếu mandatory, unsupported version, wrong producer/type và correlation xuyên outbox. **Phần được làm trước:** Lập manifest và test harness theo envelope đã chốt ngay; payload draft gắn nhãn draft, G1 không tự pass vì consumer dùng fixture tự viết.
  - [x] **[DONE_LOCAL 2026-09-25 — manifest/test requirements]** §10 của Surgery spec ghi 8 contract/lookup rows, producer owner, trạng thái fixture/hash/test/live và `SUR-CON-01..07` cho harness. Mọi fixture/hash chưa có được để `MISSING`; chưa tạo fixture thay producer hay chạy contract test.
  - [ ] **[CHỜ PRODUCER FIXTURE + S-01]** Nhận file/version/hash hoặc commit từ producer, viết harness thực thi trên module và chạy cùng fixture qua consumer; mới xét G1.
- [ ] **[NỘI BỘ · một phần]** **S-03g:** bộ contract web dùng DTO record, ApiResponse, pagination framework-free, stable error. Các business endpoint chỉ được đưa vào khi use case tương ứng đã xong. **Phần được làm trước:** ApiResponse/error/pagination/security plumbing sau foundation không cần Dxx. Business DTO/controllers chờ S-04–07 và quyết định liên quan.

**Nghiệm thu:** valid fixture đi đến đúng use case, bad fixture không mutation; pending không mất message; DLQ không chặn event tốt tiếp theo; lookup unavailable không thành “không tồn tại”. Đã deserialize fixture chưa có nghĩa luồng live hoàn tất.

### S-04 — tạo case và truy vấn đúng care episode

**Phụ thuộc:** S-02, S-03 referral/identity adapters; D01/D02/D12. **Trạng thái:** `IN_PROGRESS` chỉ S-04c/d/e design slices; chưa có case API/domain code.

**Phạm vi:** create/get/list in-ports, request/response/mapper, SurgeryCase, application services và `web/SurgeryController`.

- [ ] **[CHỜ D01/D02]** **S-04a:** `POST /cases` và referral consumer dùng cùng business use case. Nhận stable surgeryRequestId, exact refs, patient/department/requesting doctor, procedure/indication/priority; API tạo tay phải có nguồn referral theo contract, không tạo ID để né dedupe. **Phần được làm trước:** Có thể viết mô tả use case/test scenarios ngay; chưa implement create/referral command khi origin/identity/care refs chưa khóa.
- [ ] **[CHỜ D01/D12]** **S-04b:** validate context theo D01; record là clinical reference, không tự đổi account episode. Admission existence/patient/department match phải có authoritative fact/lookup đã khóa; không tìm admission active theo patient. **Phần được làm trước:** Chuẩn bị bảng refs/negative scenarios ngay; chưa tự chọn admission–patient validation contract.
- [ ] **[CHUẨN BỊ]** **S-04c:** idempotency theo referral business key: lặp cùng ý nghĩa trả case cũ; payload khác cùng key trả conflict; hai entry point đến đồng thời chỉ tạo một case. Chuẩn hóa fingerprint theo business fields, không dùng timestamp delivery làm khác biệt. **Phần được làm trước:** Soạn duplicate/conflict/concurrency scenarios ngay; key/fingerprint business và executable case persistence phải đợi D02 + S-02/S-04a.
  - [x] **[DONE_LOCAL 2026-09-25 — scenario matrix]** §11 của Surgery spec có `SUR-IDEM-01..08`, tách `eventId` với business key, bao gồm HTTP/event race, hai producer, crash trước/sau commit, changed payload và hai intent cùng patient. Chưa tự chốt fingerprint/key/status code.
  - [ ] **[CHỜ D02 + S-02/S-04a]** Chốt key/field fingerprint và chạy PostgreSQL concurrency + cross-entry tests, đếm case/history/inbox/outbox rồi mới đóng S-04c.
- [ ] **[CHỜ D02]** **S-04d:** transaction tạo case REQUESTED + history + facts được spec yêu cầu, rồi bridge charge đã khóa ở D02; Billing định giá. Không ghi paid amount vào Surgery hoặc tạo charge giả để case tiến tiếp. **Phần được làm trước:** Không nối charge source hoặc tự đặt event thay thế; có thể mô tả transaction boundary từ chuẩn outbox hiện tại.
  - [x] **[DONE_LOCAL 2026-09-25 — transaction design]** §12 của [`backend-spec/10-surgery.md`](../../eproject_general_plan/backend-spec/10-surgery.md) mô tả inbound validation trước lock, inbox/business-key claim, case + history + approved outbox trong một transaction, ACK sau commit và 5 test thất bại/retry. Charge-source event vẫn TBD D02; chưa có code/test chạy.
  - [ ] **[CHỜ D02 + S-02/S-04a]** Chốt fact/API charge-source và fixture Billing, triển khai transaction trên key/DDL đã duyệt và chạy DB/broker recovery tests trước khi đóng S-04d.
- [ ] **[CHỜ D01/D05/D07]** **S-04e:** GET detail trả identity/state/readiness reasons/schedule/result snapshot; GET list filter status/department/date với sort ổn định, pagination và giới hạn page size. Không runtime join Patient/Org để dựng report/list. **Phần được làm trước:** Có thể chuẩn bị pagination/sort/query contract draft; read DTO nghiệp vụ cần case/readiness/result model ổn định, không tạo GET giả dữ liệu.
  - [x] **[DONE_LOCAL 2026-09-25 — read contract draft]** §13 của Surgery spec chốt lại chuẩn chung `page/size/PageResult`, đề xuất filter/sort/read snapshot và 6 test cho auth, scope, pagination, nhiều episode cùng patient, unscheduled, read-only. Tên filter/date semantics/field visibility vẫn OPEN; chưa có controller/DTO.
  - [ ] **[CHỜ D01/D05/D07 + H-01c]** Owner khóa exact response/query fields, quyền xem theo khoa và semantics thời gian; sau đó implement endpoint/query index và chạy web/persistence tests.

**Nghiệm thu:** một referral → một case; wrong refs/conflicting duplicate không tạo case/outbox; GET không mutate. Tests gồm concurrency PostgreSQL, API 400/401/403/404/conflict theo conventions và hai episode của cùng patient.

### S-05 — checklist, consent, clearance và readiness

**Phụ thuộc:** S-04; D03/D05/D06/D12; S-06 resource preparation cho tích hợp readiness đầy đủ. Có thể viết guard unit tests trước. **Trạng thái:** `IN_PROGRESS` chỉ S-05a scenario matrix; chưa có checklist/readiness code.

**Phạm vi:** domain readiness policy/snapshot, checklist/consent/clearance models, update/consent/revoke use cases, repository/event ports và web adapters.

- [ ] **[CHỜ D03/D05]** **S-05a:** khởi tạo checklist theo procedure + template version; chuyển REQUESTED → PREOP_IN_PROGRESS bằng thao tác đã chốt. Mỗi item lưu mandatory/status/evidence ref+version/confirmedBy/At; đổi procedure không âm thầm giữ checklist không còn phù hợp. **Phần được làm trước:** Chuẩn bị checklist use-case/test matrix ngay; không tự chọn mandatory template hoặc thao tác đổi state.
  - [x] **[DONE_LOCAL 2026-09-25 — checklist scenarios]** §14 của Surgery spec có 8 scenario khởi tạo/retry/unknown template/version update/procedure change/mandatory/evidence/rollback, cùng danh sách quyết định D03/D05 cần có. Chưa tự đặt template hay trigger chuyển trạng thái.
  - [ ] **[CHỜ D03/D05 + S-04]** Chốt template/item codes/evidence fixture, role/command chuyển state; triển khai case checklist và chạy unit/DB/contract tests trước khi đóng S-05a.
- [ ] **[CHỜ D03]** **S-05b:** xác nhận evidence đúng patient/case/order và còn hạn; không tick item từ Lab result bất kỳ. External evidence correction/expiry phải làm guard được đánh giá lại; yêu cầu fixture nguồn thay vì đoán. **Phần được làm trước:** Liệt kê wrong-order/stale/correction tests ngay; binding evidence nguồn phải có exact correlation contract.
- [ ] **[CHỜ D06]** **S-05c:** consent lưu loại, signer/witness, signedAt, status và revoke audit; không overwrite lịch sử chữ ký. API revoke chưa có trong service design, phải khóa path/role/DTO ở D06 trước implement. **Phần được làm trước:** Có thể soạn audit/revoke scenarios; chưa chốt signer/witness/role/path thay team.
- [ ] **[CHỜ D01/D12]** **S-05d:** lưu clearance snapshot đúng tuple purpose + patient + careEpisode + surgeryCase; kiểm tra expiry khi đánh giá và START. Revocation/freshness không tự suy từ grant cũ; contract chưa cung cấp thì G3 của policy tương ứng còn blocked. **Phần được làm trước:** Negative target/expiry scenarios có thể chuẩn bị; clearance storage/apply/freshness cần context và policy.
- [ ] **[NỘI BỘ · một phần]** **S-05e:** readiness là kết quả AND của indication, mandatory checklist, active consent, eligible team, confirmed room/time và financial guard (hoặc override được phép). Trả danh sách guard thiếu, không có endpoint “set READY=true”. **Phần được làm trước:** Sau H-01 ghi invariant và S-01, có thể làm pure AND-policy + truth-table tests với sáu guard inputs, không có I/O/state transition. Cách xác nhận guard hợp lệ và override vẫn chờ D03–D06/D12.
- [ ] **[CHỜ D03/D05/D06/D12]** **S-05f:** dưới lock/version của case, tính snapshot theo checklist/consent/schedule/clearance revisions, chuyển state + history + `surgery.ready` trong một transaction. No-op update không phát ready mới; re-ready sau thay đổi dùng revision/snapshot mới nếu policy cho phép, consumer không chỉ dedupe theo caseId. **Phần được làm trước:** Được phác transaction/idempotency tests; production snapshots/re-ready semantics còn phụ thuộc decision và S-06.
- [ ] **[CHỜ D03/D05/D06/D12]** **S-05g:** consent revoke/evidence expiry/reschedule sau READY hoặc SCHEDULED phải theo transition D05; giữ lịch sử, không để START dùng readiness cũ. Expiry kiểm tra lúc đọc/command; nếu cần chủ động thông báo mất readiness thì scheduler/event cũng phải được đặc tả. **Phần được làm trước:** Chuẩn bị invalidation/race matrix ngay; chưa tự chọn rollback state hoặc phát event revocation mới.

**Nghiệm thu:** parameterized tests thiếu từng guard đều không READY/START; payment chung không mở gate; wrong purpose/case/episode/patient, expired clearance, stale evidence đều bị xử lý đúng. Consent revoke chạy đồng thời START phải cho một kết quả nhất quán theo thứ tự commit, không vượt guard.

### S-06 — reservation nguồn lực, lịch và ê-kíp

**Phụ thuộc:** S-04; D04/D05; S-03 lookup. Bước chuẩn bị tài nguyên phải có trước tích hợp READY, không bắt S-05 hoàn tất trước toàn bộ S-06. **Trạng thái:** `TODO`.

**Đề xuất cần duyệt tại D05:** tách reservation/room-time confirmation trong PREOP khỏi transition case → SCHEDULED. Khi đủ tài nguyên và các guard khác, case → READY; thao tác xác nhận lịch mới → SCHEDULED. Nếu `PUT /schedule` phục vụ hai bước, DTO/mode/guard phải rõ; không tự đảo state machine gốc.

- [ ] **[CHỜ D04]** **S-06a:** validate roomId, khoảng giờ start < end, team assignments và role requirements; staff active/jobTitle/department theo Organization và mapping đã duyệt. Không biến ADMIN/DOCTOR login role thành chuyên môn gây mê. **Phần được làm trước:** Có thể chuẩn bị negative validation cases; không hard-code room catalog hoặc staff-role mapping.
- [ ] **[CHỜ D04/D05]** **S-06b:** chọn interval semantics trong spec, đề xuất half-open `[start,end)`; định nghĩa buffer, timezone, reservation TTL và trạng thái chiếm slot. Actual surgery vượt giờ phải có policy xử lý, không âm thầm giải phóng phòng khi chưa kết thúc. **Phần được làm trước:** Soạn lựa chọn interval/buffer/TTL và examples ngay; half-open interval vẫn là đề xuất, không policy đã chốt.
- [ ] **[CHUẨN BỊ]** **S-06c:** chống overlap phòng **và từng người** giữa các case khác nhau tại DB. Lock riêng SurgeryCase không đủ. Chọn exclusion constraint hoặc resource-lock rows + overlap query; ghi rõ index/extension/lock order và retry conflict. **Phần được làm trước:** So sánh exclusion constraint với resource-row locking và phác test tranh slot ngay. Chưa gắn vào production booking trước D04/D05 và S-02.
  - [x] **[DONE_LOCAL 2026-09-26 — design slice]** Đã so sánh ba chiến lược chống overlap và ghi negative/concurrency scenarios trong [independent slices](2026-09-26-huy-independent-slices.md#s-06c--competing-roomtime-reservation-strategies). Chưa chọn DB constraint hoặc implement lịch khi D04/D05 mở.
- [ ] **[CHỜ D04/D05]** **S-06d:** application có thể lookup trước transaction; transaction re-read version, khóa resource theo thứ tự, kiểm tra availability rồi lưu schedule/team/history/outbox theo spec. Freshness của external eligibility phải được định nghĩa, không giữ DB lock qua HTTP kéo dài. **Phần được làm trước:** Có thể vẽ transaction/lookup boundary; freshness/availability/resource mutation còn cần policy.
- [ ] **[CHỜ D04/D05/D07]** **S-06e:** reschedule atomic: kiểm tra slot mới, thay reservation cũ/mới cùng transaction; nếu slot mới thất bại giữ nguyên lịch cũ. Hủy/complete giải phóng resource theo policy, giữ audit và invalidation readiness khi cần. **Phần được làm trước:** Soạn rollback-preserves-old-slot scenario ngay; release/invalidation behavior chờ schedule và terminal policy.
- [ ] **[CHỜ D04/D05]** **S-06f:** GET/list hiển thị planned vs actual time và version; retry cùng operation không tạo thêm schedule/team row. **Phần được làm trước:** Chuẩn bị query/version proposal ngay; executable DTO/use case cần schedule model và operation key đã khóa.

**Nghiệm thu:** PostgreSQL tests cho hai case tranh cùng room, cùng staff ở hai room, lịch kề nhau, chồng một phần/toàn phần và multi-resource deadlock ordering. Reschedule thất bại không mất slot cũ; outage lookup không cấp slot bằng identity giả.

### S-07 — start, complete, cancel và emergency audit

**Phụ thuộc:** S-05/S-06, D06/D07; G1 Surgery–Billing/Inpatient/Report. **Trạng thái:** `TODO`.

- [ ] **[CHỜ D05/D06/D12]** **S-07a — START:** chỉ từ state được spec cho phép; lock/re-read case, đánh giá lại guards và resource version; lưu actual startedAt/actor/history. Dùng thời gian server, retry command không đổi startedAt. **Phần được làm trước:** Viết negative START scenarios từ invariants đã chốt; chưa nối START production trước readiness/resources/freshness.
- [ ] **[CHỜ D01/D02/D04/D07]** **S-07b — COMPLETE:** từ IN_PROGRESS; validate startedAt ≤ completedAt, method/result/complications và actual performed items/quantities/codes. Transaction lưu result + terminal state + history + release resource + completed outbox. Billing mới là price authority. **Phần được làm trước:** Có thể soạn result/transaction test cases; actual items, terminal outcome và resource release cần contract.
- [ ] **[CHỜ D07]** **S-07c — CANCEL:** transition table xác định stage BEFORE_PREOP / AFTER_PREOP / BEFORE_START / IN_PROGRESS_ABORTED, reason và actor; payload stage phải khớp state, không tin stage do client gửi tùy ý. Lưu partial performed work/abort result theo D07, không tạo completed và cancelled mâu thuẫn cho cùng outcome. **Phần được làm trước:** Chuẩn bị stage/state examples; chưa quyết định partial-abort/financial side effects thay Billing.
- [ ] **[CHỜ D06]** **S-07d — EMERGENCY:** chỉ mở đường khi policy đã khóa; lưu overrideId, approvedBy, approverRole, reason, approvedAt, episode/target và guard được bypass. Lấy actor từ authenticated context, kiểm tra quyền duyệt; không nhận client tự khai role làm bằng chứng, không forge consent/payment. **Phần được làm trước:** Có thể ghi test deny khi thiếu approval ngay; không tạo đường emergency bypass tạm thời.
- [ ] **[CHUẨN BỊ]** **S-07e — duplicate/concurrent:** operation key + expected version; complete/complete chỉ một result; complete/cancel và start/cancel đồng thời một transition hợp lệ; terminal case không mutate lại. Correction sau terminal chỉ qua contract/version riêng nếu D07 cho phép. **Phần được làm trước:** Soạn duplicate/concurrent command scenarios ngay; executable outcomes/command-key checks đợi D05/D07 + S-02/S-07a–c.
  - [x] **[DONE_LOCAL 2026-09-26 — scenario slice]** Đã lập 7 race/duplicate scenarios và assertion mục tiêu trong [independent slices](2026-09-26-huy-independent-slices.md#s-07e--transitionduplicaterace-scenario-ledger); chưa có executable Surgery test hoặc outcome contract.
- [ ] **[CHỜ D02/D07]** **S-07f — financial boundary:** completion/cancel facts đủ actual items để Billing reconcile planned vs performed; unknown code là contract/catalog error, không mặc định giá 0. Surgery không sửa payment, không phát `payment.refunded`. **Phần được làm trước:** Ghi rõ boundary Billing và unknown-code test ngay; performed-item/cancel fixture chưa thể chốt.

**Nghiệm thu:** domain transition matrix + web/security + DB concurrency + producer fixture tests. Partial abort, chưa có consent, expired clearance, wrong actor và retry sau outbox publish đều có test; số logical result/cancellation/outbox không tăng do duplicate.

### 4.1. Surgery API/authorization checklist

Role bên dưới giữ nguyên service design; data scope/assignment restrictions và error codes cụ thể phải chốt trong H-01, không tự thêm quyền xuyên khoa.

| API dưới `/api/v1/surgery` | Role | Task |
|---|---|---|
| POST `/cases` | ADMIN, DOCTOR | S-04 |
| GET `/cases`, `/cases/{id}` | ADMIN, MANAGER, DOCTOR, NURSE | S-04 |
| PUT `/cases/{id}/checklist` | ADMIN, DOCTOR, NURSE | S-05 |
| POST `/cases/{id}/consents` | ADMIN, DOCTOR, NURSE | S-05 |
| Consent revoke — path/DTO chưa khóa | Chờ D06, không suy từ create | S-05 |
| PUT `/cases/{id}/schedule` | ADMIN, MANAGER, DOCTOR | S-06 |
| POST `/cases/{id}/start`, `/complete`, `/cancel` | ADMIN, DOCTOR | S-07 |

Mỗi endpoint có `@PreAuthorize`, validation, positive/negative role tests và request trong `surgery.http` qua Gateway. Audit phân biệt account actor với staff identity; không log full consent, diagnosis hay token. Endpoint thay đổi trạng thái cần stable conflict/precondition error theo API conventions.

## 5. Pharmacy — giữ nền hiện hữu, bổ sung care context

Đường dẫn tương đối với `backend/pharmacy-service/`. Không mở rộng schema/wire trước khi có compatibility contract.

### P-01 — chứng minh payment/dispense effect-idempotent

**Phụ thuộc:** độc lập với Surgery; chính sách đã chuyển vào [Pharmacy service doc](../../ai/services/pharmacy.md). Handoff race đã được đóng theo registry lifecycle. **Trạng thái:** `DONE_LOCAL`; test PostgreSQL/RabbitMQ đã chạy, không đổi Billing wire contract.

**Hiện có:** `PaymentApplicationService` claim receipt rồi xử lý nonterminal receipt; `DispenseTransactionService` khóa Rx/slip/stock, commit stock + filled outbox; receipt terminal/processed-event được lưu ở bước sau. Chính sách đã chọn là **effect-idempotent**, không yêu cầu orchestration chỉ chạy một lần.

- [x] **[ĐÃ VERIFY · UNIT]** **P-01a:** unit race test dùng hai receipt snapshots độc lập, không chia sẻ mutable domain object; expectation bám effect-idempotency. **Phần được làm trước:** Hoàn tất và regression pass.
- [x] **[ĐÃ VERIFY · POSTGRESQL]** **P-01b:** `DispenseIntegrationTest.paymentCompleted_twoConcurrentDeliveries_commitsOneDispenseAndFilledEvent` dùng receipt/outbox persistence thật và lặp 5 lần; mỗi lượt chỉ trừ kho/phát filled outbox một lần.
- [x] **[ĐÃ VERIFY · POSTGRESQL]** **P-01c:** test-only barrier sau khi cả hai receipt claims đã commit; cả hai còn thấy `RECEIVED` trước khi cùng chạy dispense. Barrier/race đã chạy thành công 5/5.
- [x] **[ĐÃ VERIFY · POSTGRESQL]** **P-01d:** fault injection sau stock/outbox commit trước receipt terminal; redelivery hoàn tất receipt mà không cấp/phát lần hai. Stale terminal write bị từ chối bằng conditional finalize. Tests: `paymentCompleted_receiptSaveFailsAfterDispense_redeliveryFinalizesSameEffect`, `paymentReceipt_saveStaleConflictingTerminalOutcome_rejectsWithoutOverwritingWinner`.
- [x] **[ĐÃ VERIFY · POSTGRESQL]** **P-01e:** concurrent late payment sau hủy chỉ ghi một compensation; same-event payload conflict bị reject; Billing contract fixture không đổi. Test: `paymentCompleted_twoConcurrentDeliveries_afterCancellation_compensatesOnce`, recovery conflict assertion và `PharmacyEventContractFixtureTest.paymentCompleted_fixtureMatchesPharmacyPayload`.
- [x] **[DONE_LOCAL]** **P-01f:** focused PostgreSQL/RabbitMQ tests và toàn Pharmacy module suite chạy thật; 207 tests, 0 failures/errors/skips, Billing fixture test pass. Handoff đã retired; policy lâu dài đã chuyển sang Pharmacy service doc.

**Nghiệm thu:** một stock decrement, một logical dispense, một filled outbox khi thành công; failure compensation tối đa một cho cùng operation; receipt terminal nhất quán; retry được sau transient failure; payload conflict không tạo effect. Mockito call count hoặc Testcontainers skipped không đáp ứng nghiệm thu.

### P-02 — schema/DTO/event care context tương thích

**Phụ thuộc:** H-01/D08/D11/D12 và G1 Care–Billing/Inpatient–Surgery. **Trạng thái:** `TODO`.

**Phạm vi:** `domain/model/Prescription`, DTO request/command/response + mapper, `PrescriptionApplicationService`, JPA entity/adapter, `application/event/Prescription*Event`, publisher/consumer fixtures.

- [ ] **[CHỜ D08/D11/D12]** **P-02a:** migration sau V12 (kiểm tra lại số khi code); thêm careContext/admissionId, index và conditional constraints. Backfill dữ liệu legacy OUTPATIENT theo baseline đã kiểm tra; không tạo admissionId từ recordId/patientId. **Phần được làm trước:** Ngay bây giờ chỉ rà schema V12, thống kê nullability từ migration và soạn migration proposal; chưa chạy migration admission khi constraint/compatibility chưa khóa.
- [ ] **[CHỜ D08/D12]** **P-02b:** validation theo context: OUTPATIENT giữ recordId và behavior cũ; ADMISSION cần exact admissionId; recordId có được null hay chỉ referral tùy D08. Vì DB/request hiện bắt buộc recordId, phải xử lý cả constraint và DTO, không chỉ thêm một field. **Phần được làm trước:** Có thể lập validation matrix và test baseline request cũ; admission nullability/authoritative refs chưa được tự chọn.
- [ ] **[CHỜ D08/D11]** **P-02c:** chính sách request cũ thiếu careContext được default OUTPATIENT chỉ ở compatibility boundary đã duyệt; không default nếu payload admission thiếu refs. Bảo toàn doctorId từ explicit staff claim và department rule hiện hữu. **Phần được làm trước:** Có thể thêm regression cho doctorId/staff claim và request legacy hiện hữu; chưa đổi default/semantics của careContext.
- [ ] **[CHỜ D08/D12]** **P-02d:** map careContext sang careEpisode fields theo producer contract. Outpatient có appointment phải dùng episode ID đã được owner chọn; recordId không mặc nhiên là episodeId của mọi đơn. **Phần được làm trước:** Chuẩn bị bảng source refs hiện tại/thiếu field; không mặc định mọi outpatient recordId là episodeId.
- [ ] **[CHUẨN BỊ]** **P-02e:** bổ sung immutable context/item snapshots cho created/filled/dispense.failed/cancelled/expired khi consumers cần reconcile; rà soát mọi publisher path, retry/outbox payload cũ. Không rewrite event đã commit trong outbox. **Phần được làm trước:** Inventory tất cả publisher paths/outbox legacy và consumers ngay; thêm care fields vào production payload chờ D08/D11/D12.
  - [x] **[DONE_LOCAL 2026-09-26 — inventory slice]** Đã đối chiếu 5 publisher paths, nội dung snapshot hiện có và khoảng trống care context trong [independent slices](2026-09-26-huy-independent-slices.md#p-02e--current-publisheroutbox-inventory-and-missing-care-context); chưa thêm field vào wire/outbox.
- [ ] **[CHUẨN BỊ]** **P-02f:** compatibility request/response/consumer decoder; fixture legacy và additive mới. Breaking nullability/envelope/semantics phải version và phối hợp readers trước writers; Billing/Clinical/Notification/Report bị ảnh hưởng đều có handoff. **Phần được làm trước:** Lưu và test fixtures legacy ngay; schemas/additive decoder mới chờ D08/D11 và G1.
  - [x] **[DONE_LOCAL 2026-09-26 — legacy fixture slice]** Đã thêm fixture cancelled/expired khớp byte với Billing, test decoder Pharmacy: 6/6 pass, 0 skip. [Evidence/compatibility gate](2026-09-26-huy-independent-slices.md#p-02f--legacy-compatibility-fixtures-and-decoder-gate); additive target vẫn chờ D08/D11/G1.
- [x] **[NGAY · characterization ngoại trú — DONE_LOCAL 2026-09-25]** **P-02g (phần chạy ngay):** giữ regression cho schema hiện tại lên V12, upgrade rows từ V4 và outbox V5, ORM round-trip, request thiếu `recordId`/lines, legacy event JSON, catalog search filter/pagination và role API. Đã chạy 46 test liên quan trên Docker/PostgreSQL/API. **Để lại:** migration từ database đã có dữ liệu V12 sang care-context mới và conditional ADMISSION constraints chờ P-02a–f cùng D08/D11/D12; Pharmacy hiện chưa có prescription list/filter API nên filter regression thuộc catalog thuốc, không tự thêm endpoint.

**Nghiệm thu:** client/event ngoại trú cũ vẫn hoạt động; admission sai refs bị từ chối; dữ liệu cũ không gắn nhầm episode; không đổi `paymentConfirmed` thành “được cấp nội trú”. Nếu cần authorization status mới, thêm field rõ nghĩa và contract riêng.

### P-03 — authorization và kê/cấp/hủy thuốc theo care context

**Phụ thuộc:** P-01 G2, P-02, D08/D12; Inpatient/Billing fixtures. **Trạng thái:** `WAITING_DECISION`.

- [ ] **[CHỜ D08/D12]** **P-03a:** application xác nhận prescription gắn đúng admission/patient/department và admission còn đủ điều kiện kê/cấp theo authoritative contract; không REST/DB lookup không được duyệt, không suy từ patient. **Phần được làm trước:** Có thể soạn matrix đúng/sai admission; không mở admission authorizer bằng contract giả.
- [ ] **[CHỜ D08/D12]** **P-03b:** tách authorization evidence khỏi stock execution: outpatient tiếp tục receipt/payment compatibility; admission dùng policy/evidence riêng. Outpatient clearance, deposit clearance hoặc boolean paid không được mở admission dispense. **Phần được làm trước:** Rà luồng payment-proof/stock boundary ngay; chưa refactor thành admission authorization implementation khi evidence chưa khóa.
- [ ] **[CHỜ D08]** **P-03c:** chốt dispensing granularity trước schema: hiện một slip/đơn; nếu V1 giữ full-dispense thì test đúng policy đó. Nếu nhiều đợt/theo liều, cần dispense operation/line identity, số lượng đã cấp/còn lại và test mới; không tự dùng prescriptionId làm dedupe cho mọi đợt. **Phần được làm trước:** Ghi hiện trạng one-slip-per-prescription và lựa chọn migration ngay; không tự chọn full-dose/partial-dose policy.
- [x] **[NGAY · outpatient — DONE_LOCAL 2026-09-25]** **P-03d (phần chạy ngay):** reuse reservation/locking/expiry/stock validation hiện có; mutation stock + Rx/slip/reservation + outbox atomic. Không giữ stock lock trong lúc chờ Billing/REST. DB/broker outage không được diễn giải thành đủ tiền hoặc đã cấp. Đã thêm PostgreSQL regression cho lỗi outbox sau khi flush: rollback giữ nguyên stock/Rx/slip/reservation, payment receipt vẫn RECEIVED và không có `prescription.filled`; `DispenseIntegrationTest` chạy 11 tests, 0 failures/errors/skips. Tích hợp admission execution vẫn chờ D08/P-02.
- [ ] **[CHỜ D08/D11]** **P-03e:** prescription facts tạo admission charge theo contract. Billing chưa xử lý xong có được cấp hay không do D08 quyết định; không tạm cho phép bằng “event đã gửi”. **Phần được làm trước:** Có thể phác failure scenarios; admission charge/dispense timing phải theo contract, không bật bằng event-sent flag.
- [ ] **[CHỜ D08]** **P-03f:** hủy/expiry trước cấp giải phóng reservation và phát fact đủ context; failure phát compensation intent thích hợp. Admission chưa thu tiền không tự nhận outpatient refund semantics; hàng đã cấp muốn trả lại cần use case riêng, không chỉ đảo status. **Phần được làm trước:** Hủy/expiry/compensation outpatient đã có được regression ngay; không áp semantics đó sang admission hoặc returns.
- [x] **[NGAY · local regression — DONE_LOCAL 2026-09-25]** **P-03g:** actor được phân loại tường minh (`STAFF` / `ACCOUNT` / `SYSTEM`), automated dispense không dùng UUID giả, JWT chỉ nhận `access`, và local correlation/late-payment regressions giữ nguyên. Migration V13 phân loại UUID zero đã biết thành `SYSTEM`, UUID cũ mơ hồ thành `LEGACY_UNKNOWN`; upgrade V12 được test. 74 targeted Pharmacy tests pass. **Còn chờ D08/D11/D12:** admission actor/source contract và end-to-end correlation xuyên Inpatient → Pharmacy → Billing/Report; phần local DONE không hàm ý tích hợp.
- [ ] **[CHỜ D11/D12]** **P-03h — outpatient clearance migration:** khi Billing đạt G1, bổ sung `financial.clearance.granted(purpose=PRESCRIPTION)` với đúng prescription/patient/episode và expiry, dùng authorization port chung nhưng không forge PaymentReceipt. Giữ `payment.completed` compatibility tới cutover D11; nếu nhận cả hai cho cùng đơn, dedupe stock/outbox theo cùng dispense operation. Việc bỏ compatibility là thay đổi rollout riêng, không nằm trong patch P-01. **Phần được làm trước:** Chuẩn bị target/multi-input dedupe scenarios ngay; production decoder/gate cần Billing G1 và cutover, không sửa P-01 cho việc này.

**Nghiệm thu:** cùng patient có hai admission và một outpatient không nhận chéo authorization/charge. Duplicate/concurrent dispense chỉ một effect theo granularity đã chọn; hết hạn, admission đóng, thiếu kho, hủy tranh cấp và recovery đều có fixture/test. Outpatient test suite giữ xanh; clearance mới và payment compatibility không cấp hai lần cùng đơn.

## 6. Report — projection tài chính/vận hành có thể rebuild

Đường dẫn tương đối với `backend/report-service/`. Mở rộng các port/projection đang có, không thay toàn bộ `AggregateUpdaterService` trong một PR. Không thêm Feign/client hoặc datasource tới service khác.

### R-01 — journal, contribution keys, version và replay foundation

**Phụ thuộc:** H-01/D09/D11, G1 Projections cho từng nguồn. **Trạng thái:** `TODO`.

**Phạm vi:** `messaging/consumer/ReportEventConsumer` + payloads, `infrastructure/config/RabbitConfig`, application in/out-ports, contribution domain, persistence và Flyway sau V2.

- [ ] **[CHUẨN BỊ]** **R-01a:** lập subscription matrix current/target gồm event type/version, source ID, operation/revision, required dimensions, timestamp dùng báo cáo và projector. Giữ 5 legacy bindings và behavior cũ tới khi cutover được duyệt. **Phần được làm trước:** Lập current/target subscription matrix ngay; target field còn thiếu ghi blocker D09/D11, không tự bịa operation/revision.
  - [x] **[DONE_LOCAL 2026-09-26 — matrix slice]** Đã ghi 5 binding hiện hữu, target facts thiếu và D09/D11 gate trong [independent slices](2026-09-26-huy-independent-slices.md#r-01a--currenttarget-subscription-matrix).
- [ ] **[CHUẨN BỊ]** **R-01b:** thiết kế durable journal chứa envelope/payload cần rebuild, schema/projector version và trạng thái applied/pending/rejected; giới hạn dữ liệu nhạy cảm, retention/access theo policy. Không coi queue đã ACK là kho replay. **Phần được làm trước:** Soạn local journal design, data minimization và replay-source options ngay; chưa deploy journal policy/migration trước D11.
  - [x] **[DONE_LOCAL 2026-09-26 — design slice]** Đã ghi journal fields, 3 replay-source options, privacy/retention gates và test cases trong [independent slices](2026-09-26-huy-independent-slices.md#r-01b--journalreplay-source-design); chưa có migration.
- [ ] **[CHUẨN BỊ]** **R-01c:** tách hai khóa: inbox unique eventId + fingerprint; contribution unique source business ID + operation kind + business revision/operation ID đã khóa. Giữ eventId làm provenance nhưng không đưa nó thành điều kiện duy nhất để ngăn semantic duplicate. **Phần được làm trước:** Rà dedupe legacy và thiết kế hai loại key ngay; target business keys/schema chờ D07/D08/D09/D11.
  - [x] **[DONE_LOCAL 2026-09-26 — key slice]** Đã tách inbox/business key và lập 4 scenarios trong [independent slices](2026-09-26-huy-independent-slices.md#r-01c--event-dedupe-versus-semantic-contribution-key); chưa chọn key target thay producer.
- [ ] **[CHỜ D07/D08/D09/D11]** **R-01d:** với finance, payment/refund transaction ID khác invoiceId; nhiều partial payments/refunds hợp lệ không bị gộp thành một. Với surgery, caseId + outcome/result revision; với dispense, key theo granularity P-03; không invent revision khi producer không có. **Phần được làm trước:** Lập source-key matrix ngay; chưa giả định result revision, dispensing granularity hoặc partial-transaction identity.
- [ ] **[CHUẨN BỊ]** **R-01e:** transaction ghi journal/inbox + contribution + aggregate updates; late prerequisite lưu pending bền vững, resume khi original fact đến. Reversal trước payment hoặc close trước admission start không lấy ngày/khoa hiện tại để đoán. **Phần được làm trước:** Phác atomic transaction/pending-reversal scenarios; code journal/projectors mới sau R-01 spec và D09/D11, admission cases cần D10.
  - [x] **[DONE_LOCAL 2026-09-26 — transaction slice]** Đã ghi atomic boundary, pending prerequisite và 4 assertions trong [independent slices](2026-09-26-huy-independent-slices.md#r-01e--atomic-projection-and-pending-prerequisite); code target vẫn chờ D09–D11.
- [x] **[NGAY · legacy projection regression — DONE_LOCAL 2026-09-25]** **R-01f:** PostgreSQL regression xác minh first-row creation cho null department/hospital scope, concurrent invoice contribution/correction/reversal và totals theo department lẫn hospital scope; event conflict không để revenue dở dang. Thêm truncate isolation trước mỗi test để kết quả độc lập khi suite chạy lặp/đổi thứ tự. `ReportPersistenceConcurrencyTest`: 17 tests và `ReportCrossLayerIntegrationTest`: 8 tests pass với PostgreSQL/RabbitMQ. **Còn chờ D09/D11:** semantics/contribution keys cho projection redesign; local regression không khóa target model.
- [ ] **[CHUẨN BỊ]** **R-01g:** replay vào projection generation mới, với processing ledger riêng theo generation; không xóa live inbox để replay. Cùng journal + projector version + report timezone phải ra cùng tổng; so sánh rồi mới chuyển read pointer theo quy trình được duyệt. **Phần được làm trước:** Thiết kế generation/processing-ledger và replay tests bằng tập legacy fixtures hữu hạn; chưa tuyên bố có replay source production trước D11.
  - [x] **[DONE_LOCAL 2026-09-26 — replay design slice]** Đã ghi checkpoint/catch-up/reconcile/read-pointer proposal và failure assertions trong [independent slices](2026-09-26-huy-independent-slices.md#r-01g--generation-based-replay-proposal); chưa triển khai replay.
- [ ] **[CHỜ D11]** **R-01h:** cutover/live replay dùng watermark/catch-up để không mất hoặc tính đôi event đến trong lúc rebuild; không publish nghiệp vụ/notification khi replay. Nêu rõ lịch sử trước khi có journal lấy ở đâu hoặc không rebuild được; không hứa rebuild toàn bộ quá khứ không có nguồn. **Phần được làm trước:** Có thể viết cutover/runbook options; activation/live catch-up phụ thuộc nguồn replay, retention và compatibility đã khóa.
- [ ] **[NGAY · một phần]** **R-01i:** migration/decoder compatibility, DB concurrency, shuffled-order replay và Rabbit retry/DLQ tests; metric lag/pending/poison/replay failure có correlation, không log full clinical payload. **Phần được làm trước:** Thêm/giữ legacy decoder/duplicate/out-of-order/retry-DLQ regression. Migration/journal/generation tests mới chờ R-01b–h; PG/MQ execution cần Docker.
  - [x] **[DONE_LOCAL 2026-09-25 — legacy regression slice]** Giữ decoder fixture của 5 event, duplicate/concurrency regression; thêm RabbitMQ/PostgreSQL test đảo thứ tự `payment.failed`/`payment.completed` trên hai invoice, redelivery không cộng lại; thiếu source ID vào DLQ không claim event/ghi projection. Recoverer test xác minh correlation được ghi log nhưng clinical payload không xuất hiện. Full Report `clean test`: 129 tests pass trên Testcontainers.
  - [ ] **[CHỜ R-01b–h/D09/D11]** Migration/decoder version của target projection, shuffled journal replay vào generation, metric lag/pending/poison/replay failure và cutover cần source/schema/semantics đã khóa. Không gọi legacy redelivery test là bằng chứng journal replay.

**Nghiệm thu:** cùng nghiệp vụ với eventId khác không đếm đôi nhưng correction/partial refund hợp lệ vẫn được xử lý; rebuild từ nguồn bền vững cho cùng tổng; legacy read APIs và payment compensation tests không regress.

### R-02 — strict human JWT, giữ regression

**Phụ thuộc:** độc lập. **Trạng thái:** `DONE_LOCAL`.

- [x] **[ĐÃ LÀM] R-02a:** `infrastructure/security/JwtAuthFilter` dùng shared claim constants và chỉ nhận `type=access`.
- [x] **[ĐÃ LÀM] R-02b:** `JwtAuthFilterTest` bao phủ access, refresh, service và thiếu type; không sửa Common/Gateway.
- [x] **[ĐÃ LÀM] R-02c:** Handoff JWT đã đóng và tài liệu Report đã cập nhật.
- [x] **[ĐÃ LÀM] R-02d:** Focused JWT test và `mvn -q -pl backend/report-service -am clean test` đã pass trong lượt triển khai trước.
- [ ] **[NGAY · regression] R-02e:** Có thể chạy/giữ focused JWT tests ngay, không cần D01–D12 hoặc Docker. Giữ test này trong regression của R-01/R-03/R-04 và smoke Gateway ở X-01; đây không phải việc implement lại R-02.

**Giới hạn evidence:** các test persistence Testcontainers bị skip trong lần chạy trên do Docker không khả dụng. Điều đó không phủ nhận test JWT đã chạy, nhưng không chứng minh DB correctness của Report.

### R-03 — financial projections theo Billing facts

**Phụ thuộc:** R-01, D09/D11, Billing producer fixtures cùng version. **Trạng thái:** `WAITING_DECISION`.

**Phạm vi:** financial contribution/value models, projector application services, repository adapters, finance DTO/query và fixtures. Read-model fields dưới đây là semantics đích, không phải permission để tự thay wire của Billing.

- [ ] **[CHUẨN BỊ]** **R-03a:** chốt mapping từng fact → cash/deposit/revenue/refund/receivable; bảng mô tả transaction key, amount/currency, delta hay snapshot, account/episode, effective date, department allocation và original source. **Phần được làm trước:** Soạn bảng metric→required fact và câu hỏi gửi Billing ngay; mapping cuối cùng chờ D09, wire/cutover chờ D11.
  - [x] **[DONE_LOCAL 2026-09-26 — mapping proposal]** Đã ghi bảng metric→fact và câu hỏi khóa với Billing trong [independent slices](2026-09-26-huy-independent-slices.md#r-03a--metric-to-billing-fact-requirements); không coi bảng là Billing contract.
- [ ] **[CHỜ D09/D11]** **R-03b:** `cashReceived` tăng theo completed cash receipt/payment transaction đủ classification; tiền deposit tăng cash và deposit liability, không tự tăng earnedRevenue. Nếu tên chỉ tiêu là gross cash received, refund không vừa giảm gross vừa được trừ lần nữa ở net. **Phần được làm trước:** Chuẩn bị expected-outcome examples từ invariant deposit≠revenue; chưa implement projector bằng payload/classification giả.
- [ ] **[CHỜ D09/D11]** **R-03c:** `earnedRevenue` chỉ nhận recognized charge/allocation/settlement fact rõ nghĩa từ Billing. Phân bổ deposit giảm liability theo fact; settlement total không mặc nhiên là delta mới hoặc toàn bộ cash mới. **Phần được làm trước:** Soạn recognition/allocation examples ngay; không suy amount/delta từ settlement khi chưa có fixture.
- [ ] **[CHỜ D09/D11]** **R-03d:** `payment.refunded` dùng refundTransactionId và originalTransactionId, phân biệt refund deposit chưa earned với reversal earned revenue. Giữ original contribution scope để đảo đúng kỳ/khoa; cash movement theo ngày refund nếu metric yêu cầu. Policy kỳ báo cáo do D09 khóa, không dùng receivedAt. **Phần được làm trước:** Chuẩn bị partial-refund/date/department cases; chưa chốt period/reversal policy thay Billing.
- [ ] **[CHỜ D09/D11]** **R-03e:** receivable/debt theo Billing outcome/adjustment; snapshot mới thay trạng thái/version cũ hoặc tính delta từ hai snapshot, không cộng lại toàn bộ balance mỗi lần settlement redelivery. **Phần được làm trước:** Soạn snapshot-vs-delta test cases; chưa chọn cách tính balance khi contract chưa rõ.
- [ ] **[CHỜ D09/D11]** **R-03f:** explicit projection path legacy vs classified, chọn theo version/classification/cutover đã khóa. Không ghi cùng tiền từ cả payment và settlement vào earned revenue hai lần; `payment.failed` legacy không tự thành refund tiền thật trong ledger mới. **Phần được làm trước:** Có thể inventory legacy path và điểm nguy cơ double-count ngay; cutover mới chưa được bật.
- [ ] **[CHỜ D09/D11]** **R-03g:** unknown classification, currency, original reference hoặc thiếu allocation dimensions → pending/quarantine/error theo contract, không suy từ paymentMethod, giá trị dương hoặc patient. Report không tự tính insurance entitlement, price hay charge. **Phần được làm trước:** Chuẩn bị unknown/missing-field negative fixtures dưới dạng proposal; required fields/disposition mới cần contract.
- [ ] **[CHUẨN BỊ]** **R-03h:** test partial payments/refunds, duplicate với eventId mới, refund-before-payment, settlement-before-allocation, multi-department allocations, late correction và calendar boundary; expose finance query chỉ khi metric/DTO semantics đã khóa. **Phần được làm trước:** Viết bảng số liệu kỳ vọng/scenarios ngay; fixtures được gọi là approved tests chỉ sau D09/D11. Finance query mới chưa mở.
  - [x] **[DONE_LOCAL 2026-09-26 — conditional fixtures]** Đã ghi ví dụ tính và 7 negative scenarios trong [independent slices](2026-09-26-huy-independent-slices.md#r-03h--expected-outcome-fixture-scenarios); Billing chưa duyệt expected results.

**Ví dụ fixture nghiệm thu đề xuất, cần Billing xác nhận:** deposit 100 → cashReceived 100, liability 100, earned 0. Billing xác nhận recognized 80 và allocation deposit 80 → liability 20, earned 80, cash không tăng thêm. Refund deposit dư 20 hoàn tất → refunds 20, liability 0, earned vẫn 80, net cash 80. Không dùng ví dụ này để tự suy allocation từ settlement tổng.

**Nghiệm thu:** totals đúng expected fixtures, event đảo thứ tự/replay không đổi kết quả; refund/cancel không được quy thành doanh thu âm khi không có fact phù hợp. Legacy report và target finance view có tên/semantics rõ, không lặng lẽ đổi nghĩa cột doanh thu cũ.

### R-04 — operational projections và read API

**Phụ thuộc:** R-01, producer fixtures, D07/D10/D11. **Trạng thái:** `TODO`; riêng bed occupancy/LOS definition còn `WAITING_DECISION`.

- [ ] **[CHỜ D11]** **R-04a:** tách visit created và visit completed/disposition. `medicalrecord.completed` không làm counter visit hiện tại tăng thêm một lần; chọn source/version key để correction không tăng visit count. **Phần được làm trước:** Rà và bảo vệ counter created legacy ngay; completed/disposition cần G1 Clinical và business revision contract, không tự dùng counter cũ.
- [ ] **[CHỜ D10/D11]** **R-04b:** admission projection nhận started/closed, liên kết theo admissionId; closed trước started lưu pending. Lưu các mốc cần metric; discharge medically approved và administrative closed là hai nghiệp vụ khác nhau. **Phần được làm trước:** Soạn started/closed/out-of-order scenarios; schema projection và actual fixture cần mốc thời gian/dimensions khóa.
- [ ] **[CHỜ D10]** **R-04c:** định nghĩa admissions/discharges/LOS với timezone, cutoff và denominator. Nếu chỉ có admittedAt/closedAt thì chỉ tính khoảng tới administrative close và ghi đúng tên, không gọi là thời gian chiếm giường/y khoa. **Phần được làm trước:** Có thể liệt kê công thức/mốc dữ liệu cần ngay; không gọi administrative duration là medical LOS khi chưa chốt.
- [ ] **[CHỜ D10]** **R-04d:** bed occupancy cần bed assignment/transfer/release intervals và available/staffed-bed capacity theo kỳ. Chưa có contract đầy đủ thì chỉ triển khai KPI admission đủ dữ liệu, giữ occupancy feature blocked; không lấy số admission active làm tỷ lệ sử dụng giường. **Phần được làm trước:** Soạn gap/handoff required bed facts ngay; không code occupancy bằng admission count.
- [ ] **[CHỜ D07/D10/D11]** **R-04e:** surgery completed/cancelled theo source outcome/result version: count, actual duration, cancellation stage/reason, complications category. Thiếu startedAt không dùng scheduledAt thay; không phân loại biến chứng bằng đoán từ free-text; partial abort theo D07. **Phần được làm trước:** Viết metric/test proposal; thiếu category/revision/actual time thì chưa implement bằng suy đoán.
- [ ] **[CHỜ D08/D11]** **R-04f:** dispense projection tách careContext/admission theo P-02/03; distinction số đơn/số lần cấp/số lượng thuốc, không gộp chúng thành cùng một counter. **Phần được làm trước:** Rà baseline prescription/item counters ngay; admission/partial-dispense metrics cần P-02/03 contracts.
- [ ] **[CHUẨN BỊ]** **R-04g:** bổ sung read use cases + DTO + controller sau query contract: range/department/context, deterministic ordering, zero-fill hợp lý, phân biệt 0 với dữ liệu chưa khả dụng. Giới hạn range/page size và index query; giữ 3 legacy endpoints. **Phần được làm trước:** Soạn DTO/query/index proposal, giữ legacy endpoints bằng regression; query mới chờ R-01 và D09/D10/D11 tùy metric.
  - [x] **[DONE_LOCAL 2026-09-26 — read design slice]** Đã ghi legacy surface, bounded query/availability proposal và test cases trong [independent slices](2026-09-26-huy-independent-slices.md#r-04g--read-apiqueryindex-proposal); chưa mở endpoint mới.
- [ ] **[NGAY · một phần]** **R-04h:** RBAC Report ADMIN/MANAGER, 401/403 và `report.http` qua Gateway; query không mở thêm clinical detail không cần thiết. Trả freshness/as-of khi contract có, không hứa real-time consistency. **Phần được làm trước:** Thêm/giữ ADMIN/MANAGER, 401/403, privacy và .http regression cho endpoint hiện có; API mới/freshness DTO chờ query contract.
  - [x] **[DONE_LOCAL 2026-09-25 — legacy API slice]** Web-slice matrix xác minh ADMIN/MANAGER được gọi cả 3 endpoint, thiếu auth nhận 401, DOCTOR nhận 403 và use case không chạy khi thiếu auth; response daily không lộ trường clinical. `report.http` dùng Gateway :8080, có mẫu 401/403, correlation và cảnh báo số liệu bất đồng bộ/chưa có freshness. `ReportControllerTest`: 13 tests pass; chưa chạy live Gateway smoke.
  - [ ] **[CHỜ R-04g/query contract]** RBAC/privacy của endpoint mới và freshness/as-of DTO chỉ làm sau khi contract metric/query và nguồn watermark được chốt; không thêm field giả vào legacy API.
- [ ] **[NGAY · một phần]** **R-04i:** tests duplicate/semantic duplicate, out-of-order, cross-midnight/timezone, case hai khoa/admission nhiều transfer nếu được hỗ trợ, dữ liệu thiếu và bounded query count. **Phần được làm trước:** Test timezone/calendar boundary, legacy duplicates/ordering/zero-fill/query bounds ngay; admission/transfer/surgery tests chờ D07/D10/D11 và schema.
  - [x] **[DONE_LOCAL 2026-09-25 — legacy regression slice]** RabbitMQ/PostgreSQL tests xác minh cùng eventId không đếm hai lần ở hai khoa/toàn viện, prescription ở hai phía mốc nửa đêm Asia/Bangkok vào đúng ngày, và characterization cùng recordId/eventId mới hiện vẫn đếm hai lần. Giữ out-of-order/zero-fill/leap-year tests; siết assertion monthly/top query chỉ gọi repository đúng số lần. Full Report `clean test`: 132 tests, 0 failures/errors/skips.
  - [ ] **[CHỜ D07/D10/D11/R-01 target projector]** Semantic dedupe/correction theo source business ID, admission transfer, surgery outcome và replay generation cần contract/schema mới. Test characterization nêu rõ legacy double-count là gap, không phải expected target behavior.

**Nghiệm thu:** mỗi metric truy ngược được về source/định nghĩa/time basis; tổng hospital/department không double-count; không công bố KPI bằng dữ liệu không đủ, không gọi REST enrichment để che thiếu fixture.

## 7. X-01 — integration, reliability và rollout

**Phụ thuộc:** G2 từng slice; G1 đầy đủ và producer live cho slice cần chạy. **Trạng thái:** `TODO`. Đây là task phối hợp, không tự trao quyền sửa module khác.

### 7.1. Checklist tích hợp

- [ ] **[OWNER/HẠ TẦNG]** **X-01a — environment:** chủ shared xác nhận root module/DB/Compose; chủ Gateway xác nhận route/auth/correlation. Test gọi qua Gateway, không dùng direct port để thay bằng chứng route. Chỉ dùng dữ liệu kiểm thử. **Phần được làm trước:** Chuẩn bị environment checklist ngay, không cần Dxx; chạy Surgery smoke cần S-01 và xác nhận của owner shared/Gateway, không tự sửa họ.
- [ ] **[CHỜ D01–D07/D11/D12]** **X-01b — Surgery happy path:** referral → case → charge source bridge → checklist/consent/resource reservation → đúng clearance → READY/SCHEDULED → START → COMPLETE; kiểm tra Inpatient/Billing/Report nhận đúng IDs/result. Kiểm chứng cả origin được D01 cho phép. **Phần được làm trước:** Soạn E2E script outline ngay; không chạy hay gọi pass bằng fixtures giả producer cho happy path hệ thống.
- [ ] **[CHỜ D01/D04–D07/D11/D12]** **X-01c — Surgery negative path:** trả phí nhưng thiếu consent vẫn không READY/start; wrong clearance; resource conflict; cancel-before-start và partial abort; Notification không nhân đôi intent; refund chỉ xảy ra khi Billing có transaction thực. **Phần được làm trước:** Soạn negative matrix ngay; actual cancel/override/refund assertions cần policy và producers live.
- [ ] **[CHỜ D08/D11/D12]** **X-01d — Pharmacy:** outpatient cũ và admission mới cùng patient, nhiều episode; prescription → đúng charge → authorized dispense; expiry/cancel/failure và retries không trừ kho/charge hai lần. **Phần được làm trước:** Outpatient baseline có thể kiểm tra riêng với producer/hạ tầng hiện có; admission E2E chờ contract/P-02/03, không gắn DONE cho toàn task.
- [ ] **[CHỜ D09/D11]** **X-01e — Finance/Report:** deposit → recognition/allocation → settlement/refund/debt; đối soát expected fixture với ledger facts, journal và report totals. Hủy mổ không tự tạo refund projection. **Phần được làm trước:** Chuẩn bị reconciliation sheet từ ví dụ đã ghi; chưa chạy finance E2E trước Billing facts và R-03.
- [ ] **[CHUẨN BỊ]** **X-01f — distributed failures:** duplicate cùng/khác eventId cho cùng nghiệp vụ; event đảo thứ tự; app crash trước/sau commit/ACK; broker gián đoạn; lookup timeout rồi hồi phục; pending/DLQ replay không double effect. **Phần được làm trước:** Soạn fault-injection plan và tận dụng regression legacy ngay; integrated new-flow failures chờ từng Dxx/slice và PG/MQ thật.
  - [x] **[DONE_LOCAL 2026-09-26 — test plan]** Đã ghi fault matrix, expected invariants và current/later split trong [independent slices](2026-09-26-huy-independent-slices.md#x-01f--distributed-fault-injection-plan); chưa chạy new-flow E2E.
- [ ] **[CHUẨN BỊ]** **X-01g — observability:** theo dõi outbox lag/retry, pending tuổi cao, DLQ, projection lag; correlation xuyên Gateway/event; log metadata không lộ token/consent/medical payload. **Phần được làm trước:** Inventory log/metric hiện hữu và soạn correlation/privacy checklist ngay; metric mới sau owner module/spec tương ứng, không sửa monitoring/shared ngoài scope.
  - [x] **[DONE_LOCAL 2026-09-26 — observability inventory]** Đã kiểm kê 4 Pharmacy gauges, Report retry/DLQ/log, gap projection-lag và privacy checklist trong [independent slices](2026-09-26-huy-independent-slices.md#x-01g--observability-and-privacy-inventory); chưa triển khai dashboard/alert.
- [ ] **[NGAY]** **X-01h — evidence/report:** lưu test names/counts/skip/fail, fixture version, commits, môi trường và kết quả từng scenario; owner/handoff còn chờ; tách DONE_LOCAL với DONE_INTEGRATED. **Phần được làm trước:** Tạo mẫu evidence/report và ghi baseline pass/skip/blocker ngay; không yêu cầu chốt D01–D12, không lấy template làm proof đã test.
  - [x] **[DONE_LOCAL 2026-09-26 — evidence template]** Đã ghi test pass thực, fixture hashes, chưa chạy tích hợp và checklist bằng chứng trong [independent slices](2026-09-26-huy-independent-slices.md#x-01h--evidence-ledger-and-current-baseline). Chưa có DONE_INTEGRATED.

### 7.2. Rollout và rollback safety

1. Deploy migration additive và readers/decoders tương thích trước; kiểm chứng DB cũ + payload legacy.
2. Producer mới chỉ bật khi downstream fixture tests đạt G1; activation/config switch phải có spec, mặc định không nhận admission/classified facts khi chưa đủ consumer.
3. Không dual-publish một nghiệp vụ cũ/mới nếu chưa có stable business identity và cơ chế chống đếm đôi. Không rewrite historical outbox payload để đổi format.
4. Mở từng lát cắt, đối soát trước/sau; Report có thể chạy projection generation mới ở chế độ so sánh trước khi đổi nguồn đọc.
5. Rollback bằng tắt writer/feature và quay read pointer/phiên bản tương thích đã test; không drop cột/bảng mới hoặc xóa journal/inbox đã nhận event. Nếu phiên bản cũ không đọc được dữ liệu mới thì cần forward fix/compatibility release, không gọi đó là rollback an toàn.
6. Production execution cần phân công/approval riêng; plan này chỉ yêu cầu runbook và kiểm thử staging/local.

**Nghiệm thu:** scenario của slice qua với DB/broker thật, không skip critical integration tests; shared/other-owner acceptance có bằng chứng. Dependency chưa xong thì ghi rõ slice blocked, không mở rộng scope tự sửa hộ.

## 8. Ma trận rule → test bắt buộc

D = domain unit; A = application unit (mock ports); W = web/security; PG = PostgreSQL Testcontainers; MQ = Rabbit integration; C = producer/consumer fixture. Mã dưới đây là tracking ID của plan, chưa thay mã rule canonical hiện có.

| Rule | Given / When → Then | Task | Test tối thiểu |
|---|---|---|---|
| SUR-01 | Cùng surgeryRequestId qua REST/event, đồng thời → một case; payload khác → conflict. | S-04 | A, W, PG, C |
| SUR-02 | Cùng patient, khác admission/episode → không nhận chéo case/clearance. | S-03/04/05 | D, A, C |
| SUR-03 | Thiếu từng readiness guard → không READY và không START. | S-05/07 | D, A, W |
| SUR-04 | Evidence sai order/case, stale hoặc corrected → không dùng checklist snapshot cũ. | S-05 | D, A, C |
| SUR-05 | Revoke consent/expire clearance/reschedule tranh START → kết quả nhất quán, không bypass guard. | S-05/06/07 | D, PG |
| SUR-06 | Hai case tranh room hoặc staff, reschedule thất bại → một booking, lịch cũ còn nguyên. | S-06 | D, PG |
| SUR-07 | Complete lặp hoặc complete/cancel concurrent → một outcome hợp lệ, một logical result. | S-07 | D, PG, C |
| SUR-08 | IN_PROGRESS_ABORTED → partial work/audit đúng fixture, không tự refund. | S-07 | D, A, C |
| SUR-09 | Emergency thiếu approver/quyền/reason/episode → reject; override hợp lệ chỉ bypass guard được duyệt. | S-05/07 | D, A, W, C |
| REL-01 | Crash/rollback trước commit; sau commit trước ACK → không mất effect, không nhân đôi outbox. | S-02/03, X-01 | PG, MQ |
| REL-02 | Schema/version/target sai → bounded retry/DLQ; event tốt phía sau vẫn xử lý. | S-03, R-01, X-01 | C, MQ |
| REL-03 | Early dependency hợp lệ → pending durable rồi resume; quá hạn có escalation. | S-02/03, R-01 | A, PG, MQ |
| PHA-01 | Hai payment deliveries cùng lúc → stock/slip/filled outbox/receipt nhất quán. | P-01 | PG |
| PHA-02 | Stock đã commit nhưng receipt chưa terminal; retry → không cấp lại, receipt phục hồi. | P-01 | A, PG |
| PHA-03 | Same eventId khác payload; cancel/expiry/failure concurrency → reject/compensate ≤1 theo operation. | P-01 | A, PG, C |
| PHA-04 | Dữ liệu/request/event legacy sau migration → semantics ngoại trú giữ nguyên. | P-02 | W, PG, C |
| PHA-05 | Sai admission hoặc outpatient/deposit authorization → không unlock nội trú. | P-03 | D, A, C |
| PHA-06 | Nhiều lần cấp nếu policy cho phép → tổng cấp không vượt đơn, không dedupe nhầm cả đơn. | P-03 | D, PG, C; chỉ áp dụng khi D08 cho phép |
| PHA-07 | Prescription clearance và payment compatibility tới đồng thời → một dispense effect; wrong target/expired clearance không mở gate. | P-03 | D, A, PG, C |
| RPT-01 | Same business fact, eventId mới → một contribution; legitimate revision/partial refund vẫn áp dụng. | R-01/03/04 | D, PG, C |
| RPT-02 | Deposit → cash + liability, earned không tăng; settlement không cộng lại cash. | R-03 | D, A, PG, C |
| RPT-03 | Partial refund/reversal trước original → pending, sau đó đảo đúng contribution/kỳ/khoa. | R-01/03 | D, PG, C |
| RPT-04 | Replay shuffled journal sang generation mới cùng live catch-up → totals như nguồn, không double. | R-01 | PG, MQ |
| RPT-05 | Missing bed facts/duration/classification → không tạo số liệu giả; closed không đồng nghĩa medical discharge. | R-03/04 | D, A, C |
| SEC-01 | Access hợp lệ; refresh/service/missing type hoặc role sai trên human API → 401/403 đúng convention. | S-01, R-02, X-01 | W |
| SEC-02 | Lookup absence khác timeout/5xx/malformed; sub/account khác staffId → không gán nhầm identity. | S-03/04/06, P-03 | A, C |
| MIG-01 | Fresh DB + upgrade có dữ liệu + old/new reader fixtures → dữ liệu không mất, old outbox vẫn đọc được. | S-02, P-02, R-01 | PG, C |

Mỗi dòng phải trỏ tới test cụ thể trong implementation spec/PR khi code. Không đóng task bằng coverage % chung hoặc chỉ một happy-path test.

## 9. Handoff, thứ tự triển khai và PR

### 9.1. Trách nhiệm phối hợp

| Owner | Đầu vào cần họ xác nhận/cung cấp | Acceptance Huy cần | Không được tự làm thay |
|---|---|---|---|
| Vinh — Clinical/Inpatient/Lab | Referral IDs/origin, admission-state/patient linkage, pre-op evidence, bed/discharge facts; consumers Surgery results. | G1 fixture matching + negative IDs/out-of-order; D01/02/03/05/07/08/10/12 được quyết định. | Sửa producer, query Inpatient/Lab DB hoặc suy order từ patient. |
| Lộc — Billing/Notification | Case charge bridge, price/item catalog, exact clearance/revocation policy, admission authorization, classified transactions/refunds/settlement, notification dedupe. | D02/06/07/08/09/11/12; expected finance totals và producer/consumer tests cùng version. | Tạo ledger/giá/refund trong Surgery/Pharmacy/Report hoặc sửa Notification. |
| Hoàng Anh — Organization/Patient/Gateway | Typed service lookup, staff eligibility mapping, room ownership, Surgery route và JWT/correlation. | D04/D06; absence/outage/unauthorized fixtures và Gateway route smoke. | Fake staffId từ sub, dùng public human endpoint như service-auth bypass. |
| Owner shared được giao rõ | Root POM, DB bootstrap, Compose/Eureka config cần thiết và CI wire checks. | Build/boot/config và integration environment reproducible. | Tự chỉnh Common/root/scripts chỉ vì cần build service mới. |

Handoff có owner, contract/version, fixture, câu hỏi cần quyết định, affected consumers và acceptance. Dùng [active registry](../../handoffs/README.md); chuẩn bị đề xuất không có nghĩa owner khác đã chấp thuận. Chỉ chuyển canonical contract sang IMPLEMENTED sau producer và required consumers cùng pass.

### 9.2. Thứ tự task không tạo vòng phụ thuộc

- **Làn độc lập hiện tại:** hoàn tất verification P-01; giữ regression R-02; soạn H-01 và fixture proposals. Docker thiếu chỉ chặn kiểm chứng DB, không chặn chuẩn bị spec.
- **Surgery foundation:** H-01 phần liên quan → S-01 → S-02 → S-03 adapters theo contract → S-04.
- **Surgery nghiệp vụ:** từ S-04, chuẩn bị checklist/consent S-05 và resource reservation S-06; tích hợp cả hai → readiness/schedule transition → S-07. Không bắt S-06 chờ READY rồi mới có tài nguyên để tính READY.
- **Pharmacy mở rộng:** D08/D11/D12 → P-02; P-01 G2 + P-02 → P-03.
- **Report:** R-01 → R-03/R-04 theo fixture từng producer; không đợi Surgery live để test finance fixtures, không bật KPI thiếu facts.
- **Tích hợp:** X-01 theo slice đã đạt G2/G1; chưa có provider thì DONE_LOCAL hoặc BLOCKED integration, không “xong hệ thống”.

PR nhỏ, tập trung **một service hoặc docs/shared assignment**:

| Nhóm PR | Nội dung | Gate merge/release |
|---|---|---|
| Docs/spec | H-01 quyết định/fixtures/spec và handoff liên quan. | Không tô DONE cho quyết định chưa duyệt. |
| Pharmacy reliability | P-01 code/test/recovery; không trộn care schema. | G2 PostgreSQL race/recovery thật. |
| Report security | R-02 đã implement local, kiểm tra diff/regression trước PR. | JWT tests; tách khỏi projection rewrite. |
| Surgery foundation | S-01/S-02; shared bootstrap PR riêng do owner được giao. | Architecture/auth/migration/reliability tests liên quan. |
| Surgery vertical slices | S-03/04 → S-05/06 theo các use case → S-07. | Mỗi slice có rule/tests/fixture, không để test cuối cùng. |
| Pharmacy context → workflow | P-02 rồi P-03. | Compatibility trước; admission workflow sau policy. |
| Report foundation → metrics | R-01, R-03, R-04 theo nguồn facts. | Replay/dedupe + expected totals/query regression. |
| Integration evidence/runbook | X-01, handoff status và rollout. | G3 của từng slice, owner khác xác nhận phần họ. |

Thứ tự này không phải quyền tự commit/push/mở PR. Thực hiện các thao tác Git đó theo yêu cầu riêng của người dùng.

## 10. Definition of Done và bảng tiến độ

### 10.1. Checklist đóng một task code

- [ ] Quyết định/contract liên quan đã khóa hoặc ghi rõ phần loại khỏi V1; không còn field/nghiệp vụ tự đoán.
- [ ] Domain/application rules và failure paths có test; kiến trúc inward, không cross-service DB/DTO imports.
- [ ] Mỗi API có role/validation/error/.http; mỗi wire change có producer/consumer fixtures và compatibility.
- [ ] Mutation/audit/outbox atomic; dedupe event và business operation đúng; concurrency/retry không mất hoặc nhân đôi effect.
- [ ] Migration clean/upgrade và queries/index được test với PostgreSQL; Rabbit reliability test chạy khi thay messaging.
- [ ] Chạy `mvn -q -pl backend/<service> -am test` và integration test/profile thực sự được POM cấu hình; không mặc định `verify` đã chạy test nếu report cho thấy skipped. Với Surgery chỉ chạy reactor selector sau shared registration.
- [ ] Kiểm tra Surefire/Failsafe results, ghi số pass/fail/skip và test names. Critical test skipped ⇒ WAITING_VERIFICATION, không DONE.
- [ ] `git diff --check`, docs/fixture links và scope diff đúng; review trước PR, không hoàn nguyên thay đổi ngoài task.
- [ ] Báo cáo rõ DONE_LOCAL vs DONE_INTEGRATED, blockers có owner và bước tiếp theo; không lấy integration chưa chạy làm bằng chứng hoàn tất.

### 10.2. Tiến độ sau lần rà soát 2026-09-25

| Task | Trạng thái thực tế | Bước tiếp theo |
|---|---|---|
| H-01 | IN_PROGRESS / WAITING_DECISION; H-01a glossary, H-01b rule/test inventory, H-01c contract manifest, H-01d technical design và H-01e decision log đều DONE_LOCAL ở mức draft. Chưa implementation-ready; chưa có Surgery DDL/fixture/test. | Owner chốt các Dxx liên quan theo log 3.1a; ưu tiên D01/D02 và D04/D05 trước V1 transitions/charge, rồi exact DTO/DDL/fixture và verification. |
| S-01–S-07 | S-01d handoff; S-02a, S-03f, S-04c/d/e và S-05a DONE_LOCAL ở mức thiết kế/scenario. S-01a–c/e và S-02–S-07 chưa có module/business code. | Shared integrator/Gateway owner nhận bootstrap; D01–D05 và các Dxx liên quan khóa V1/G0; nhận producer fixture rồi scaffold, viết và chạy tests theo gate. |
| P-01 | DONE_LOCAL; race, recovery, terminal outcome, compensation, migration, repository và RabbitMQ integration tests chạy thật. Pharmacy suite: 207 tests, 0 failures/errors/skips; `DispenseIntegrationTest`: 10 invocations, 0 failures/errors/skips. Handoff đóng và policy chuyển vào service doc. | Giữ evidence P-01; chưa đổi care-context policy. |
| P-02 | TODO; phần legacy characterization của P-02g DONE_LOCAL (46 targeted tests pass, gồm migration V12/fresh + V4/V5 upgrade, ORM, API/event/validation/search). | Khóa conditional refs/compatibility D08/D11/D12 rồi viết migration từ dữ liệu V12; không gắn episode suy đoán. |
| P-03 | WAITING_DECISION; outpatient atomicity P-03d và local actor/audit slice P-03g DONE_LOCAL (74 targeted Pharmacy tests pass). | Admission authorization/granularity và cross-service clearance/correlation vẫn cần chốt theo D08/D11/D12. |
| R-01 | TODO; R-01f PostgreSQL concurrency/isolation và R-01i legacy decoder/order/DLQ regression DONE_LOCAL. | Khóa journal/replay source, contribution keys, version và cutover legacy/classified trước redesign; hoàn tất phần R-01i còn chờ. |
| R-02 | DONE_LOCAL. | Giữ regression; auth smoke liên service ở X-01. |
| R-03 | WAITING_DECISION. | Billing cung cấp classified facts/expected totals D09. |
| R-04 | TODO; R-04h/i legacy API/reliability regression DONE_LOCAL, live Gateway smoke chưa chạy; semantic operational dedupe và bed occupancy/LOS còn WAITING_DECISION. | Làm target metric khi đủ source fixture/definition; không suy occupancy từ administrative status. |
| X-01 | TODO, phụ thuộc producer/shared/Gateway và Docker. | Chuẩn bị fixture-driven checklist; chỉ chạy/đánh dấu slice khi prerequisites đạt. |

**Lịch sử verification (2026-09-25):** `mvn -q -pl backend/pharmacy-service -am '-Dtest=DispenseIntegrationTest,PaymentReceiptTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` pass trên Testcontainers; `DispenseIntegrationTest` 10 invocations và `PaymentReceiptTest` 8 tests, 0 failures/errors/skips. Full suite `mvn -q -pl backend/pharmacy-service -am test` pass: 207 tests, 0 failures/errors/skips trên PostgreSQL/RabbitMQ Testcontainers. Lượt đầu tiên khi Docker bật làm lộ lazy mapping và timestamp-precision payload comparison; cả hai đã sửa rồi chạy lại pass.

**Review-fix verification (2026-09-25):** Pharmacy targeted suite (dispense/payment/controller/JWT/domain/migration) 74 tests pass, 0 failures/errors/skips, gồm Testcontainers PostgreSQL và V12→V13 actor backfill. Report: `ReportPersistenceConcurrencyTest` 17 tests và `ReportCrossLayerIntegrationTest` 8 tests pass, 0 failures/errors/skips; cleanup trước mỗi concurrency test giúp suite chạy ổn định và payload conflict vẫn không mutate projection.

**R-01i/R-04h legacy verification (2026-09-25):** `mvn -q -pl backend/report-service -am '-Dapi.version=1.40' clean test` pass 129 tests, 0 failures/errors/skips (Testcontainers PostgreSQL 16 + RabbitMQ 3.13); gồm `ReportCrossLayerIntegrationTest` 10 tests, `ReportControllerTest` 13 tests và log privacy/correlation assertion. Live Gateway smoke và target-projection replay/metrics chưa có evidence.

**R-04i/H-01a verification (2026-09-25):** `mvn -q -pl backend/report-service -am '-Dapi.version=1.40' clean test` pass 132 tests, 0 failures/errors/skips trên PostgreSQL 16/RabbitMQ 3.13 Testcontainers; `ReportCrossLayerIntegrationTest` tăng lên 13 tests. `backend-spec/10-surgery.md` là draft tài liệu, đã đối chiếu kiến trúc, các canonical handoff và naming; D01/D02 chưa có phê duyệt, chưa có Surgery business code.

**H-01d/S-01d design handoff (2026-09-25):** đã bổ sung technical foundation/concurrency/recovery/test matrix vào Surgery spec và mở handoff root/DB/Compose/Gateway với owner/evidence. Đây là cập nhật tài liệu; chưa có Surgery module, migration, test hay Gateway route để kiểm chứng runtime. Skill `new-microservice` yêu cầu full V1 schema/business code theo blueprint, nên không dùng nó để tạo shell/placeholder khi D01/D02 và DDL nghiệp vụ chưa được khóa.

**H-01b/H-01c/H-01e preparation (2026-09-25):** 12 transition/rule rows, 12 negative fixture requirements, API/role/event/lookup manifest và 12 dòng decision log đã được viết ở mức draft. Tình trạng Surgery vẫn SPEC=DRAFT, FIXTURE=MISSING, RUNTIME=NOT_BUILT; không có producer/consumer shared contract test hay code/test Surgery được thực thi từ các tài liệu này.

**S-02a/S-03f/S-04c preparation (2026-09-25):** thêm aggregate/resource alternatives, fixture provenance ledger + 7 harness requirements, và 8 referral idempotency/concurrency scenarios vào Surgery spec. Đây là design/test inventory, chưa phải domain model, producer fixture, migration hay PostgreSQL execution; G0/G1/G2 vẫn chưa pass.

**S-04d/S-04e/S-05a preparation (2026-09-25):** thêm transaction/failure matrix cho case creation, read/query contract draft và checklist initialization matrix. Chưa có code/test Surgery; charge-source bridge chờ D02, read DTO/scope chờ D01/D05/D07, checklist template/state trigger chờ D03/D05.

**Lịch sử verification bổ sung (2026-09-25, P-02g/P-03d):** legacy characterization set pass 46 tests (`PharmacyMigrationCompatibilityTest`, `PrescriptionPersistenceAdapterTest`, `PharmacyEventContractFixtureTest`, `PrescriptionControllerTest`, `DrugPersistenceAdapterTest`, `DrugControllerTest`, `SecurityConfigTest`); `DispenseIntegrationTest` pass 11 tests với regression mới cho rollback khi ghi `prescription.filled` outbox lỗi. Không chạy migration care-context/admission vì schema và business rules còn chờ D08/D11/D12.

**Đầu ra lần rà soát plan (trước implementation):** bổ sung nhãn thực hiện trước D01–D12 ngay tại từng subtask và bảng chọn việc ở mục 1.4; giữ nguyên 16 mã task H/S/P/R/X; bổ sung subtasks, 12 decision items, transaction/recovery boundaries, rule-test matrix và rollout gates.
