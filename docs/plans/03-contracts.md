# Hợp đồng tích hợp tối thiểu

## Một nguồn wire truth

Tài liệu này quyết định phạm vi và quy tắc tích hợp. Exact field names/types/nullability/roles/
errors lấy từ live controllers, DTOs, serializers và producer fixtures ở source, sau đó khóa
manifest M6-02. Không dùng thiết kế mới để khẳng định endpoint/event chưa code đã tồn tại.
Không đổi wire tiếng Việt/Anh vì đổi deployment name; breaking change có version riêng.

## Modes

| Mode | Sử dụng |
|---|---|
| COMPATIBILITY V0 | Giữ các API/saga/event đang có để regression và rollback; không mở rộng ý nghĩa paid |
| MVP OUTPATIENT V1 | Profile đích cho exact EXAM/LAB_TEST/PRESCRIPTION clearance và source-bound operations |
| DEFERRED | Admission/Surgery/full V2, không producer/listener/job/route activation trong MVP |

Mỗi command/aggregate/delivery chọn một mode rõ ràng. Không thử V1 rồi fallback V0 khi thiếu
permission/provider. Không chạy hai issuer cùng charge source. MVP V1 chỉ công bố đạt khi
driving API, real authority, producer/consumer cùng bytes và PG/Rabbit/Gateway tests đạt.
Nếu chưa đủ, ghi task mở; không đổi demo thành V0 mà vẫn gọi toàn bộ MVP V1 hoàn thành.

## Gateway và routes

| Prefix giữ nguyên | Target deployment | Source owner |
|---|---|---|
| /api/v1/auth/** | Gateway; upstream account verification ở Core | Hoàng Anh |
| /api/v1/org/**, /api/v1/patients/** | Core | Hoàng Anh |
| /api/v1/appointments/**, /api/v1/records/** | Clinical | Vinh |
| /api/v1/lab/** | Lab | Vinh |
| /api/v1/pharmacy/** | Pharmacy | Huy |
| /api/v1/billing/** | Billing | Lộc |
| /api/v1/reports/**, /api/v1/notifications/** | Support | Huy/Lộc |
| /api/v1/inpatient/**, /api/v1/surgery/** | Không route trong profile MVP | Deferred |

Prefix không thay exact endpoint/role matrix. Gateway phải deny method/path ngoài manifest,
không OR roles của hai module co-host. Account verify và exists/lookup là service-only,
không public human API. Giữ ApiResponse/PageResult và correlation như source.

## REST dependencies

Organization hiện yêu cầu `departmentHeadId` trong request
`PUT /api/v1/org/departments/{id}`: UUID để bổ nhiệm, `null` để bỏ trưởng khoa;
thiếu field là request không hợp lệ, không có nghĩa giữ nguyên. Trưởng khoa phải là bác sĩ
đang hoạt động trong cùng khoa; không chuyển khoa của trưởng khoa trước khi bỏ/đổi bổ nhiệm.
Giữ nguyên hợp đồng này khi đóng gói Core; kiểm tra consumer theo DTO/test nguồn master.

Clinical/Pharmacy gọi Core để xác minh patient/staff/department bằng signed service credential.
Pharmacy gọi Clinical prescription-context lookup với exact recordId, patient, prescriber,
department và visit identity. Gateway OrganizationAuthClient phải đổi upstream discovery về Core.
Report không Feign tới producer; Notification MVP không tự thêm contact lookup vì chỉ in-app.

Confirmed absence khác timeout/5xx/malformed; unavailable không là exists=false hoặc default
permission. Có timeout/circuit breaker/bounded retry, không retry write tạo effect mới.
Discovery name khác logical JWT subject; giữ allowlist/module caller identities compatibility.

Pharmacy dùng service subject `pharmacy-service`, role SYSTEM, TTL tối đa 60 giây cho đúng
`GET /api/v1/billing/financial-clearances/{id}/lookup`; đây không là quyền gọi Billing human APIs.
Current PRESCRIPTION eligibility phải đối soát exact request/charge/allocation/payment cùng
patient/account/episode. `grantedAt` phải giữ instant từ original grant envelope khi còn bằng
chứng đó: làm tròn TIMESTAMPTZ không được biến cùng một source thành authorization khác.
Không sửa original bytes hoặc nới so khớp để bỏ qua revoked/expired/refunded/mismatched grants.

## Event manifest

Đây là routing/semantics manifest, không schema mới. Khóa schema bằng fixtures tại M6-02.

| Fact | Logical producer | Consumer/action trong MVP |
|---|---|---|
| patient.created | patient-service | Notification compatibility khi mapping hợp lệ |
| appointment.created | clinical-service | In-app notice |
| appointment.status.changed | clinical-service | EXAM issuer theo mode; không double-charge với record |
| medicalrecord.created | clinical-service | Billing compatibility/EXAM source dedupe, Report compatibility |
| medicalrecord.completed | clinical-service | Report outpatient count, Notification nếu fixture đã có |
| lab.request.created | lab-service | V1 Billing planned charge |
| lab.result.created | lab-service | Clinical result, Report Lab count, optional in-app notice |
| prescription.created | pharmacy-service | Billing Rx charge/request |
| prescription.filled | pharmacy-service | Billing saga completion, Clinical attachment, Report items/count, in-app |
| prescription.dispense.failed | pharmacy-service | Billing adjustment/compensation, không auto refund cả hai mode |
| invoice.created | billing-service | Private payment-request notice đúng mode |
| payment.completed | billing-service | V0 Rx saga hoặc V1 cash evidence; V1 không care authorization |
| financial.clearance.granted | billing-service | Exact EXAM/LAB_TEST/PRESCRIPTION authorization |
| payment.failed | billing-service | V0 compensation/report reversal/notice theo source |
| payment.refunded | billing-service | V1 completed cash refund projection/notice khi activated |
| stock.low | pharmacy-service | Ops/in-app nếu thuộc manifest, không MVP prerequisite |
| notification.sent | notification-service | Delivery fact, không unlock care |

ADMISSION_DEPOSIT/SURGERY clearance và admission/surgery/settlement/topup facts không bật ở
MVP. Không xóa shared queues hoặc historical messages: consumer phải phân loại deferred facts
trước effect, giữ quarantine/HELD theo policy hiện có, không ACK-drop financial evidence.

## Identity và idempotency

Patient/staff/account/department IDs có authoritative origin. Episode identity chọn khi mở
outpatient account, không thay khi record xuất hiện. Không lookup “latest record” hoặc dùng
patientId để chọn mọi khoản tiền/đơn thuốc.

V1 envelope: eventId, eventType, integer version, occurredAt, correlationId, producer, payload.
Routing key không tự thêm .v1; schema version không là result/operation revision.
Logical producer vẫn patient-service/clinical-service/pharmacy-service/... dù app co-host.
Giữ event IDs và raw bytes của outbox cũ. Optional additive field có compatibility test;
rename/type/meaning change phải version và consumer fixtures đồng bộ.

Claim eventId và effect cùng transaction. Business-operation key chống same operation/new eventId.
Same key/different bytes là conflict; source ID/purpose/episode mismatch không được silently accept.
Không bảo đảm broker exactly-once; phải chứng minh effects không lặp bằng state/locks/receipts.

Notification `payment.completed` V1 giữ SHA-256 exact inbound bytes ở `receipt_raw_hash`;
`payload_hash` normalized cũ được giữ để tương thích old reader. Hai hash và history/outbox
commit cùng transaction. Historical V1 raw-NULL fail closed; chỉ offline import từ archive
original producer có provenance mới được bổ sung raw identity, không học từ replay đầu tiên.
Coverage/archive và rollback phải theo
[Notification upgrade runbook](../../backend/notification-service/docs/receipt-byte-identity.md).
Rollback binary cũ giữ data compatibility nhưng không cung cấp raw-byte enforcement mới.

### Clinical nhận outpatient prescription.filled V1

Producer bytes khóa tại
`backend/pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription.filled.outpatient.v1.json`.
Clinical chỉ nhận envelope V1 này trong mode `outpatient-mvp`: logical producer
`pharmacy-service`, careContext `OUTPATIENT`, sourceType `PRESCRIPTION`, sourceId bằng
prescriptionId, admissionId null, occurredAt bằng filledAt. Required target fields là
recordId/patientId/departmentId, `OUTPATIENT_VISIT`/careEpisodeId và dispenseId.
Không suy recordId từ prescriptionId hoặc dispenseId. Clinical khóa record rồi đối chiếu
patient/khoa/episode; episode là appointmentId của record, hoặc recordId với walk-in.

Delivery key là eventId; operation key là `clinical:prescription:v1:<dispenseId>`.
Inbox claim và attached result commit cùng transaction, giữ exact raw bytes và fingerprint
toàn bộ business snapshot, kể cả items/price chưa dùng trong Clinical projection. Price decimals
V1 phải giữ chính xác; không đổi fingerprint đã persist của V0/Lab/clearance cũ.
Same eventId phải cùng raw bytes; new eventId/cùng dispense chỉ no-op khi business snapshot
khớp. Prescription đã gắn bởi dispense khác hoặc effect lịch sử thiếu fingerprint là conflict;
không tự đánh dấu delivery mới thành công.

Sự kiện đến muộn có thể append reference vào record đã hoàn tất ngoại trú mà không mở lại
record, thay diagnosis/disposition hoặc phát lại completion. Known replay vẫn no-op sau
completion; new fill vào disposition ADMISSION/TRANSFER bị reject trong MVP.
Flat V0 tiếp tục quarantine trong MVP và giữ behavior compatibility ngoài MVP;
không đổi default mode/gates. Đây là consumer contract acceptance, chưa là real paid Rx E2E.

## RBAC

Role constants giữ ADMIN, DOCTOR, NURSE, PHARMACIST, CASHIER, LAB_TECH, MANAGER, PATIENT, SYSTEM.
Role không tự thay clinical authority. Mọi endpoint có @PreAuthorize theo live manifest.
Human APIs chỉ signed access tokens; refresh/service không dùng thay human. PATIENT ownership
kiểm trong use case; internal-only lookups cần authenticated service subject/scope. Secrets external.

## Contract evidence và gap register

Không giữ handoff cũ như task bắt buộc. Một gap MVP mới phải ghi ngay trong M6 task:
producer, consumer, thiếu field/permission/behavior, owner, acceptance fixture và test.
Task không tự cấp quyền sửa service owner khác. Deferred gap không chặn MVP trừ khi code còn
vô tình phụ thuộc nó. Chuyển module/hợp đồng phải update cả hai phía hoặc giữ gate OFF.

Surgery/Inpatient contract implementation và fixtures trong source vẫn tồn tại, nhưng không
là scope mới của tài liệu này. Không sửa payload của chúng theo manifest ngoại trú.

### Producer Inpatient được nhập từ master ngày 2026-10-14 — DEFERRED

- `GET /api/v1/inpatient/admissions/{id}/placement-lookup` trả snapshot coherent với
  `exists`, `admissionId`, `patientId`, `admissionStatus`, `medicationWindowOpen`,
  `placementRevision`, `placement`, `observedAt`. `placement` có thể null; department
  của placement là khoa của giường, không tự suy ra từ khoa chủ quản admission.
  Lookup không phải lease hay quyền cấp thuốc cuối cùng; lookup admission cũ vẫn giữ nguyên.
- `admission.placement.changed` V1 dùng `(admissionId, placementRevision)`:
  ASSIGNED có current, TRANSFERRED có previous/current nguyên tử, RELEASED có previous.
  Same-bed no-op không sinh fact; gap giữ pending/unavailable, duplicate cùng bytes
  idempotent, cùng key khác bytes là conflict.
- `bed.capacity.changed` V1 dùng `(bedId, capacityRevision)` và effective snapshot.
  Eligible khi active và không OUT_OF_SERVICE; AVAILABLE/OCCUPIED không tạo capacity revision.
  Migration V3 chỉ tạo baseline tại `updated_at` hiện hữu, không bịa lịch sử trước baseline.
- Fixtures nguồn: `backend/inpatient-service/src/test/resources/contracts/admission-placement-v1/`
  và `backend/inpatient-service/src/test/resources/contracts/bed-capacity-v1/`.
  Consumer phải nghiệm thu cùng bytes trước khi mở gates. Report vẫn sở hữu interval bucketing,
  coverage, replay/publication; producer đã có không chứng minh consumer đã hoàn thành.

Không bật admission medication hoặc occupancy V2 trong MVP chỉ vì merge producer này.
