# Kế hoạch code của Huy — Surgery, Pharmacy, Report theo Care–Finance V2

**Cập nhật:** 2026-09-27 · **Owner:** Huy (LQHuy0210).
**Baseline đã đọc:** nhánh Huy, commit 30e0296f6290f2f90abbcbae13e5cee33fb6fd4d.
**Loại kiểm chứng lần này:** đọc source, migration, test source và đối chiếu spec; không chạy lại backend/Docker.
**Trạng thái:** plan V2 đã viết lại; chưa triển khai code V2 trong lượt cập nhật tài liệu này.

## 1. Phạm vi, nguồn chuẩn và thay đổi so với plan cũ

Huy triển khai trong backend/pharmacy-service, backend/report-service và service mới backend/surgery-service. Clinical, Inpatient, Lab, Billing, Notification, Organization, Patient và Gateway chỉ đọc để kiểm tra hợp đồng. Root pom.xml, docker-compose.yml, scripts/init-databases.sql, Common, Eureka và CI là phần shared, chưa được giao lại cho Huy sau yêu cầu “chỉ nhiệm vụ của Huy”. Không sửa hộ producer hoặc thêm endpoint giả để vượt phụ thuộc.

Mục tiêu là các lát cắt nghiệp vụ chạy được, có migration, transaction, bảo mật, fixture và test; không đánh dấu xong bằng việc tạo đủ entity/controller. FE/mobile, commit/push và triển khai production không thuộc lượt làm lại plan này.

### 1.1. Nguồn phải dùng khi code

Thứ tự ưu tiên: [kiến trúc Care–Finance](../../architecture/mediflow-care-finance-redesign.html) → [canonical contracts](../../handoffs/care-finance/README.md) và [quy tắc tích hợp](../../ai/16-care-finance-integration-contracts.md) → spec V2 cục bộ → spec CURRENT cho tương thích. Coding tuân thủ [blueprint](../../ai/04-microservice-blueprint.md) và docs/ai.

| Nguồn mới đã đưa vào plan | Thay đổi ảnh hưởng Huy |
|---|---|
| [V2 index/maturity](../../eproject_general_plan/backend-spec/care-finance-v2/README.md), [plan sản xuất spec](2026-09-27-care-finance-v2-specs.md) | Thiếu fixture producer chặn bật tính năng, không chặn mọi code additive cục bộ. Plan sản xuất spec đã tick không có nghĩa backend đã implement. |
| [Pharmacy V2](../../eproject_general_plan/backend-spec/care-finance-v2/05-pharmacy.md) | Version 0/1; context/episode; V14; prescription clearance; admission projection; một đơn tối đa một slip; feature flag mặc định false. |
| [Report V2](../../eproject_general_plan/backend-spec/care-finance-v2/08-report.md) | Contribution/aggregate riêng; cash/liability/revenue/refund/receivable; endpoint financial/operations riêng; replay và đối soát trước chuyển client. |
| [Surgery V2 candidate](../../eproject_general_plan/backend-spec/care-finance-v2/11-surgery.md) | Có DDL, ports, DTO, sáu readiness guards, lịch chuẩn bị trước READY, override chỉ tài chính. §14 vẫn là gate trước production coding/scaffold. |
| [Inpatient Core V1](../../eproject_general_plan/backend-spec/care-finance-v2/10-inpatient.md) | Có đặc tả admission/bed/treatment/medical discharge/settlement/close; medical discharge khác administrative close. Code hiện mới foundation. |
| [Billing V2](../../eproject_general_plan/backend-spec/care-finance-v2/06-billing.md) | Account/charge/payment request/transaction/allocation/settlement; exact clearance; classified payment/refund; còn gap dữ liệu recognition/revision cho Report. |
| [Clinical V2](../../eproject_general_plan/backend-spec/care-finance-v2/03-clinical.md), [Lab V2](../../eproject_general_plan/backend-spec/care-finance-v2/04-lab.md) | Episode ngoại trú appointment-backed; completion/disposition/referral; Lab order/clearance/result version và exact order correlation. |
| [Organization V2](../../eproject_general_plan/backend-spec/care-finance-v2/01-organization.md), [Patient V2](../../eproject_general_plan/backend-spec/care-finance-v2/02-patient.md) | Service-only identity lookup, absence khác outage; không thay staff UUID bằng JWT subject. |
| [Gateway V2](../../eproject_general_plan/backend-spec/care-finance-v2/09-gateway.md), [Notification V2](../../eproject_general_plan/backend-spec/care-finance-v2/07-notification.md) | Route/role và payload hiển thị cần map với API/event Huy; không coi đặc tả route là route đã chạy. |

[Bản plan trước V2](2026-09-25-huy-surgery-pharmacy-report.legacy.md) giữ nguyên nội dung lịch sử bên dưới ghi chú archive. [Independent slices 2026-09-26](2026-09-26-huy-independent-slices.md) là bằng chứng chuẩn bị/legacy, không phải backlog V2 hiện hành. Draft cũ [10-surgery.md](../../eproject_general_plan/backend-spec/10-surgery.md) được dùng để tái sử dụng scenario, không cạnh tranh với Surgery V2 candidate.

Giữ 16 mã task cha H-01, S-01…07, P-01…03, R-01…04, X-01. Subtask mới dùng dấu chấm (P-02.1), khác subtask cũ dùng chữ (P-02a); không tự chuyển checkbox cũ sang implementation V2.

### 1.2. Nhãn lấy việc

| Nhãn | Có thể làm gì |
|---|---|
| NGAY | Làm độc lập trong scope Huy: tài liệu, characterization hoặc code đã đủ ngữ nghĩa, mặc định không bật V2. |
| LOCAL | Code/test offline sau task nội bộ được ghi rõ; không cần producer chạy live. Fixture tự viết chỉ là test nội bộ, chưa đạt gate liên service. |
| CONTRACT | Lát cắt cần chốt chính xác field/policy còn mâu thuẫn hoặc fixture owner; không đoán ID/giá/ý nghĩa tiền. Các phần khác tiếp tục được. |
| SURGERY-G0 | Chờ xử lý §14 của candidate và các gap liên quan; không tạo module rỗng để lách gate. |
| OWNER | Cần owner khác/shared integrator thực hiện; Huy chuẩn bị handoff và kiểm thử consumer, không sửa production ngoài phạm vi. |
| VERIFY | Code đã có hoặc đã viết test nhưng cần thực thi lại trên môi trường tương ứng. |
| ĐÃ CÓ | Giữ/reuse implementation hoặc thiết kế cũ, không lập lại như tính năng chưa làm. |

Checkbox chỉ đóng khi đầu ra và acceptance của đúng subtask đạt. SPEC_READY, CODE_PRESENT, SHARED_FIXTURE_PASS, LOCAL_TEST_PASS, E2E_PASS là các bằng chứng khác nhau; không suy ra lẫn nhau.

## 2. Audit backend hiện tại — bằng chứng và giới hạn

Các đường dẫn dưới đây là source tại baseline, không phải code ví dụ trong spec. Không thấy event/flag V2 trong Java/SQL/YAML runtime khi tìm toàn backend, loại trừ target và tài liệu.

| Mã | Bằng chứng source | Kết luận dùng để lập task |
|---|---|---|
| E01 | Không có backend/surgery-service; [root modules](../../../pom.xml), [Compose](../../../docker-compose.yml), [DB bootstrap](../../../scripts/init-databases.sql) có Inpatient nhưng chưa có Surgery. | Surgery chưa có domain/schema/API/consumer/test/runtime. Không ghi D01–D07 đã implement. |
| E02 | [Inpatient source](../../../backend/inpatient-service/src/main/java/com/mediflow/inpatient), [test](../../../backend/inpatient-service/src/test/java/com/mediflow/inpatient) chỉ có app/config/security/correlation/smoke; chưa có migration nghiệp vụ. | Core V1 là đặc tả mới, chưa có admission.started/closed hay surgery.requested producer. |
| E03 | [CreatePrescriptionRequest](../../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/application/dto/request/CreatePrescriptionRequest.java), [Prescription](../../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/domain/model/Prescription.java), [V1](../../../backend/pharmacy-service/src/main/resources/db/migration/V1__init.sql) | DTO recordId bắt buộc, DB record_id NOT NULL; chưa có care context/episode/version/admission. Unique slip theo prescription đã có. |
| E04 | [PrescriptionApplicationService](../../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/application/service/PrescriptionApplicationService.java), [DispenseApplicationService](../../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/application/service/DispenseApplicationService.java), [DispenseTransactionService](../../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/application/service/DispenseTransactionService.java) | Giá snapshot, reservation, actor kiểm tra từ claim, payment proof và transaction stock/slip/outbox đã có. Admission authorizer chưa có. Không viết lại engine tồn kho. |
| E05 | [Pharmacy migrations V1–V13](../../../backend/pharmacy-service/src/main/resources/db/migration), [events](../../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/application/event), [legacy fixtures](../../../backend/pharmacy-service/src/test/resources/contracts) | Đã có receipt, outbox/recovery/quarantine/fencing/causal order và V13 actor audit; events phẳng, chưa nested envelope V1/care context/dispenseId trên filled. |
| E06 | [Report RabbitConfig](../../../backend/report-service/src/main/java/com/mediflow/report/infrastructure/config/RabbitConfig.java), [AggregateUpdaterService](../../../backend/report-service/src/main/java/com/mediflow/report/application/service/AggregateUpdaterService.java), [PaymentContribution](../../../backend/report-service/src/main/java/com/mediflow/report/domain/model/PaymentContribution.java) | Đúng 5 binding legacy. Invoice-keyed contribution có APPLIED/PENDING_REVERSAL/REVERSED; không phải transaction ledger/settlement projector V2. |
| E07 | [Report migrations V1–V2](../../../backend/report-service/src/main/resources/db/migration), [ReportController](../../../backend/report-service/src/main/java/com/mediflow/report/web/ReportController.java), [JWT filter](../../../backend/report-service/src/main/java/com/mediflow/report/infrastructure/security/JwtAuthFilter.java) | Ba API daily/monthly/top-medicines cho ADMIN/MANAGER; access-token strict; chưa financial/operational tables, durable journal hay replay generations. |
| E08 | [BillingApplicationService](../../../backend/billing-service/src/main/java/com/mediflow/billing/application/service/BillingApplicationService.java), [PaymentCompletedEvent](../../../backend/billing-service/src/main/java/com/mediflow/billing/application/event/PaymentCompletedEvent.java), [migrations V1–V3](../../../backend/billing-service/src/main/resources/db/migration) | Invoice/fee saga hiện hữu, payment có prescriptionId và labTestIds thật. Chưa transactionId/classification/account/episode; chưa ledger V2/clearance/refund/settlement producers. Department hiện lấy fee đầu tiên, không phải phân bổ đa khoa. |
| E09 | [Clinical events](../../../backend/clinical-service/src/main/java/com/mediflow/clinical/application/event), [LabRequestCreatedEvent](../../../backend/lab-service/src/main/java/com/mediflow/lab/application/event/LabRequestCreatedEvent.java), [LabIntegrationService](../../../backend/lab-service/src/main/java/com/mediflow/lab/application/service/LabIntegrationService.java) | Có record-created, lab-request/result và explicit labTestIds payment handling; Lab không tạo order từ diagnosis. Chưa V2 medicalrecord.completed/disposition, admission/surgery referral hay exact-case pre-op evidence. |
| E10 | [PatientController](../../../backend/patient-service/src/main/java/com/mediflow/patient/web/PatientController.java), [PatientLookupDTO](../../../backend/patient-service/src/main/java/com/mediflow/patient/application/dto/response/PatientLookupDTO.java), [PatientControllerWebTest](../../../backend/patient-service/src/test/java/com/mediflow/patient/web/PatientControllerWebTest.java) | Đã có /patients/{id}/exists trả exists + patientId, service token và test source absence/outage/role. Bỏ blocker “chưa có endpoint Patient”; chưa chứng minh consumer Surgery/E2E. |
| E11 | [StaffController](../../../backend/organization-service/src/main/java/com/mediflow/organization/web/controller/StaffController.java), [StaffLookupDTO](../../../backend/organization-service/src/main/java/com/mediflow/organization/application/dto/response/StaffLookupDTO.java), [DepartmentController](../../../backend/organization-service/src/main/java/com/mediflow/organization/web/controller/DepartmentController.java) | Hiện có staff /exists: exists, eligibleDoctor, departmentId. Chưa /staff/{id}/lookup và /departments/{id}/lookup V2. eligibleDoctor không đủ thay active/jobTitle cho cả ê-kíp. |
| E12 | [Gateway token](../../../backend/gateway/src/main/java/com/mediflow/gateway/security/JwtTokenService.java), [routes](../../../backend/gateway/src/main/resources/application.yml), [RouteAuthorizationFilter](../../../backend/gateway/src/main/java/com/mediflow/gateway/filter/RouteAuthorizationFilter.java) | Explicit staffId/departmentId/patientId đã có; chưa Inpatient/Surgery route. Report GET hiện chỉ ADMIN/MANAGER, chưa quyền DOCTOR cho operations V2. |

**Kết luận:** chưa có D01–D12 nào hoàn tất end-to-end theo V2. D01–D07 chưa có nghiệp vụ Surgery; D08–D12 có một phần nền legacy/identity/reliability đáng tái sử dụng. Điều này không có nghĩa cả 12 vẫn trắng về thiết kế: spec mới đã thu hẹp nhiều quyết định và mở phần code local.

## 3. D01–D12 sau khi đối chiếu spec mới với code

<a id="31-decision-backlog"></a>

### 3.1. Decision backlog cập nhật

SPEC_CHOICE = spec mới đã chọn hướng nhưng không tự chứng minh approval liên owner. PARTIAL = đã rõ một phần, vẫn có gap cụ thể. CONFLICT = các nguồn chưa cùng ý nghĩa. Cột code độc lập với cột spec.

| ID | Điều mới từ spec 2026-09-27 | Code hiện có | Phần còn thiếu / xử lý trong plan |
|---|---|---|---|
| D01 | Surgery candidate chọn cả ADMISSION và OUTPATIENT; admission episode đúng admissionId. | E01: chưa Surgery. | CONFLICT: candidate buộc outpatient episode=recordId, canonical dùng appointmentId nếu có lịch; Surgery–Billing còn mô tả admission. H-01.2 khóa mapping/fixture cho từng context; không backfill ID suy đoán. |
| D02 | Candidate dùng surgeryRequestId unique; Clinical/Inpatient là referral producers, case do Surgery tạo. | E01/E02/E09: chưa producer referral hay case. | PARTIAL: chưa có wire post-case charge fact mang caseId + planned items. Không dùng cùng surgery.requested cho hai producer/ý nghĩa khác. H-01.2, S-03.1, S-04.2. |
| D03 | Có checklist rows/status/mandatory/evidence và sáu readiness guards. | E01/E09: chưa Surgery/evidence correlation. | PARTIAL: template catalogue/version, mandatory NOT_APPLICABLE, expiry/correction và exact order/case chưa đủ. H-01.3, S-05.1; không tick từ Lab result bất kỳ. |
| D04 | Schedule/team DDL, room_reference, guard eligibility và yêu cầu chống overlap đã có. | E11 chỉ doctor lookup cũ; chưa resource booking. | PARTIAL: room authority, staff-role mapping, interval/buffer/TTL, DB chống tranh slot; index thường không chặn overlap. H-01.3, S-06.1–3. |
| D05 | SPEC_CHOICE: chuẩn bị/xác nhận slot khi PREOP; READY xong mới chuyển SCHEDULED; START chỉ SCHEDULED. | E01: chưa runtime. | Đã giải được vòng READY–schedule ở thiết kế; còn API phân biệt prepare/finalize, invalidation/re-ready revision và expiry. H-01.3, S-05.4, S-06.4. |
| D06 | SPEC_CHOICE: consent ACTIVE/REVOKED; override chỉ FINANCIAL_EMERGENCY, không bỏ consent/checklist/team/room. | E01: chưa runtime. | Còn consent types/signer/witness, revoke endpoint/role, approver/self-approve/expiry và audit. H-01.3, S-05.2, S-07.3; không nhận role người duyệt tự khai. |
| D07 | Actual itemCode/priceCode/quantity; one result/case; Billing định giá. | E01: chưa runtime. | CONFLICT: chart chỉ hủy trước START nhưng enum có IN_PROGRESS_ABORTED; thiếu partial-work payload/revision/correction; completed category khác Inpatient complicationsSummary. H-01.2–3, S-07.2/4. |
| D08 | SPEC_CHOICE Pharmacy: v0 compatibility, v1 exact context; một đơn tối đa một slip; ADMISSION cần active projection đúng patient/khoa, không prepaid. | E03–E05: one-slip, reservation/payment/stock/outbox/actor đã có; chưa V2. | Không hỏi lại full vs multiple dispense cho V1. Còn medical-discharge eligibility, transfer/freshness/order và cancel/expiry/failure charge adjustment. P-02/P-03; multiple-dose/returns ngoài V1. |
| D09 | Billing target có transactionId/refund original/account/classification, settlement totals; Report có 5 nhóm chỉ tiêu riêng. | E06/E08: legacy invoice/compensation, chưa ledger V2. | PARTIAL: payload thiếu allocated earned/deposit-release/split department và settlement version/supersedes; cash gross/net, period và receivable stock/delta chưa thống nhất. H-01.5, R-03. |
| D10 | Inpatient tách medical discharge và CLOSED; Report target dùng admission.closed cho discharge/LOS, Surgery có actual times/category. | E02/E06/E09: chưa admission/surgery KPI. | PARTIAL: chốt tên administrative duration vs medical LOS, close thiếu department phải lấy exact start snapshot; thiếu bed transfer/release/capacity. R-04 không công bố occupancy bằng active admission count. |
| D11 | SPEC_CHOICE: legacy v0 giữ nguyên, nested envelope v1, feature flag false, projections mới chạy riêng và đối soát trước cutover. | E05–E07: fixtures/outbox/inbox legacy; chưa decoder V2/journal/replay. | Còn selector không double-count, semantic keys/revisions, durable replay source/retention/watermark và projection ledger riêng khi replay. H-01.4–5, P-02.4, R-01. |
| D12 | Exact purpose/target/patient/episode; expiresAt; active admission projection đã có trong target. | E04/E08 có exact legacy prescription/lab IDs; E10 Patient exists; chưa clearance/admission relationship V2. | PARTIAL: grant/revoke/freshness, early delivery, missing dependency, admission close-before-start/transfer. Patient exists không chứng minh admission thuộc patient. P-03.1–3, S-03.2/S-05.3. |

Tại baseline: chưa có shared V1 fixtures cho các flow mới, không có E2E V2 được ghi nhận. Không ghi tên/ngày owner “đã duyệt” chỉ vì spec đã merge. Khi có quyết định, thêm link canonical/PR xác nhận, producer/consumer fixture hash và test run vào §10.

### 3.2. Gap bắt buộc đưa vào task, không copy nguyên DDL/DTO mẫu

1. **Pharmacy upgrade:** V14 mẫu không bỏ record_id NOT NULL trong V1, trong khi request V2 cho null. Phải sửa cả constraint, JPA mapping và validation có điều kiện; legacy vẫn giữ yêu cầu cũ. CHECK nullable phải có IS NOT NULL rõ, tránh SQL UNKNOWN vô tình cho qua.
2. **Pharmacy request:** V2 mẫu không có prescribedDate trong khi CURRENT bắt buộc. Giữ field/ngữ nghĩa cho client v0; chốt field optional/default hoặc DTO V2 riêng trước bật writer, không lấy ngày xử lý retry làm ngày kê mới.
3. **Admission projection:** started_at NOT NULL không lưu được closed đến trước started. Cần pending durable/tombstone có đặc tả và không cho late started mở lại admission đã đóng. Event started/closed chưa đủ transfer/medical discharge/freshness.
4. **Clearance đến sớm:** FK projection → prescription/case không phải cơ chế pending. Không claim processed rồi bỏ event vì chưa có target; không đặt target giả.
5. **Report uniqueness/replay:** UNIQUE(event_id,source_type,source_id) chỉ chặn delivery trùng; chưa chặn cùng operation có eventId mới. Replay dùng live PROCESSED_EVENT sẽ bị skip; truncate bảng live trong lúc đọc/consume không phải rollout an toàn.
6. **Finance facts:** payment SERVICE_PAYMENT không mang allocated earned amount; settlement không mang recognized delta/applied deposit/revision dù phương trình Report cần. Không tính revenue bằng totalAmount/settlement total hoặc liability bằng tự suy.
7. **Surgery persistence:** candidate có bảng tiếng Việt nhưng cột tiếng Anh, khác quy tắc naming gốc; cần mapping/ngoại lệ được ghi nhận, không tự suy rằng đã có exception như Pharmacy/Report. Checklist template revision/readiness dependencies/resource lock và clearance episode tuple cũng phải đủ trong DDL cuối.
8. **Surgery events:** ready thiếu planned-time snapshot Notification cần; cancelled thiếu department/episode cho Report trực tiếp; completed category không thay được clinical summary Inpatient cần. Chốt payload tối thiểu theo consumer, không buộc Report gọi REST.
9. **Gateway RBAC:** operations Report cho DOCTOR nhưng gateway hiện chặn; target Surgery tổng quát chưa đủ NURSE checklist/consent và MANAGER schedule. Tạo acceptance theo từng route, không mở tất cả mutation cho cùng role.
10. **Migration numbering:** Pharmacy thực tế V13 → target V14; Report thực tế V2 nhưng target gọi V6; Billing thực tế V3 nhưng target gọi V8. Khoảng nhảy Flyway không tự là lỗi, cũng không chứng minh V3–V5/V4–V7 đã tồn tại. Kiểm tra nhánh/in-flight migrations, thống nhất filename trước triển khai; không sửa checksum migration cũ.

## 4. Kiến trúc, transaction và gate thực thi

~~~text
Clinical/Inpatient -- referral(stable surgeryRequestId) --> Surgery
Surgery -- post-case charge contract cần khóa(caseId, planned items) --> Billing
Billing -- exact SURGERY clearance --> Surgery --> ready/completed/cancelled facts
Inpatient -- exact admission lifecycle --> Pharmacy
Pharmacy -- context-aware prescription facts --> Billing + care/report/notification consumers
Billing + care services -- authoritative versioned facts --> Report V2 projections
~~~

Domain thuần Java; application chứa in/out-ports, DTO records, policies và transaction; web/messaging là driving adapters; persistence/REST/Rabbit ở infrastructure. Không import Java event class xuyên service, không query DB khác. ID UUID, money BigDecimal, Instant/Clock; report period theo timezone cấu hình.

- Surgery: case mutation + status/readiness/result audit + outbox atomic; inbox claim cùng effect hoặc pending durable. Referral business key khác eventId.
- Pharmacy: giữ effect-idempotency hiện hữu; stock/reservation/Rx/slip/filled outbox atomic. Receipt finalize có thể sau stock commit và phải recover được; không gộp lại transaction chỉ để giống pseudocode V2.
- Report: journal/pending + processing claim + contribution + department/hospital aggregate phải có transaction/processing-state rõ; nguồn tiền là Billing, không phải phép suy đoán của Report.
- External lookup thực hiện ngoài đoạn giữ DB lock dài; trước commit kiểm tra lại local version và freshness đã chốt. Lỗi upstream không biến thành exists=false hoặc đủ điều kiện.

| Gate | Bằng chứng cần | Không đồng nghĩa |
|---|---|---|
| G0 — local design | Invariant, schema, DTO, nullability, transition, error, rule-test rõ cho đúng slice; Surgery thêm §14 acceptance. | Không phải đã có producer hay quyền sửa shared. |
| G1 — wire | Canonical version + producer bytes/hash + consumer decode/negative tests cùng fixture, danh sách consumers bị ảnh hưởng. | Mock fixture local không phải shared fixture pass. |
| G2 — local correctness | Unit/web/architecture + migration/PG/MQ/concurrency/recovery tương ứng thực sự chạy. | Container test skip không phải G2. |
| G3 — integration/enablement | Producer runtime, Gateway role, Docker vertical slice, reconciliation/replay/rollback và handoff liên quan. | Không yêu cầu chờ mọi Dxx của tính năng khác. |

Feature flag target: mediflow.features.care-finance-v2=false ở Pharmacy/Report; mediflow.features.surgery=false cho Surgery. Chưa có các flag này trong code baseline. Tách activation theo slice/producer khi implement, mọi switch mới phải có config test; flag không làm migration Flyway ngừng chạy. Không bind rồi ACK mất V2 event khi projector bị tắt: chỉ bật binding sau gate hoặc persist journal có trạng thái pending theo thiết kế đã duyệt.

## 5. H-01 — thu hẹp quyết định, khóa hợp đồng đúng phần còn thiếu

**Trạng thái:** IN_PROGRESS; audit/plan là tài liệu đã hoàn tất, contract/approval còn mở.
**Files:** plan này; Huy handoffs đang active; đề xuất chỉnh đúng phần của spec V2/canonical tương ứng, không tạo một bộ wire contract khác trong plan.

- [x] **H-01.1 · NGAY · audit baseline:** đọc code E01–E12, cập nhật D01–D12 và maturity; lưu bản cũ. Acceptance: mọi “đã code” có source, mọi “đã test” có ngày/phạm vi; không lấy tick plan spec làm code evidence.
- [ ] **H-01.2 · CONTRACT · episode/referral/event mapping:** với Vinh/Lộc khóa D01/D02/D07 và ready/completed/cancelled field gaps. Đầu ra: mỗi context có request→case→charge→clearance→result fixture, exact source key, producer, version, consumers và pricing boundary. Test plan: một clinical intent đi qua hai producers vẫn một case/charge; appointment-backed không đổi episode khi record xuất hiện.
- [ ] **H-01.3 · SURGERY-G0 · local policy acceptance:** ghi từng lựa chọn §14, template/consent/room/team, prepare-vs-finalize schedule, invalidation, override, pre-start cancel/partial abort, naming/DDL mapping. Ưu tiên chấp nhận phần không mâu thuẫn của candidate; phần không hỗ trợ V1 phải ghi rõ reject, không triển khai nửa đường. Acceptance: transition table và negative cases đủ để viết domain tests, có người/ngày/link xác nhận thật.
- [ ] **H-01.4 · NGAY/CONTRACT · compatibility matrix:** chốt selector request/event v0-v1; V2-only fields thiếu không downgrade sang v0. Inventory cả created/filled/failed/cancelled/expired, consumers Clinical/Inpatient/Billing/Notification/Report. Acceptance: old outbox bytes không bị rewrite; raw legacy fixture và V1 envelope đều có phiên bản/nguồn rõ.
- [ ] **H-01.5 · CONTRACT · Report metric/source contract:** cùng Lộc/Vinh khóa bảng §8: cash gross/net, allocation/recognition/refund scope, settlement revision/snapshot, administrative LOS, operation correction, journal source/retention. Acceptance: expected totals fixture có đủ data thực; unsupported metric trả unavailable, không zero giả.
- [ ] **H-01.6 · NGAY/OWNER · handoff evidence:** cập nhật các handoff đã đăng ký với tiến bộ từ V2, bỏ blocker Patient endpoint đã có nhưng giữ consumer/test gate; bổ sung cụ thể missing generic Org lookup và Gateway roles. Không đóng handoff khi mới có spec; không viết production ngoài scope.

Phần tài liệu H-01.6 đã cập nhật ngày 2026-09-27 trong hai Huy handoff và registry: candidate Surgery, one-slip admission policy, missing finance/replay fields, generic Org lookup và Gateway role gaps. Checkbox còn mở cho xác nhận/fixture/test từ các owner và retirement đúng acceptance; không phải chưa viết handoff.

## 6. Surgery — service mới của Huy

Các file dưới đây thuộc backend/surgery-service, hiện chưa tồn tại. Tên class là đích triển khai, không phải bằng chứng có code. Dùng new-microservice/new-service skill đúng lúc scaffold; lượt làm plan này không scaffold.

### S-01 — module, cấu hình, security và khả năng kiểm thử

**Gate:** H-01.2/3 đạt G0 liên quan; shared integrator nhận bootstrap. **Trạng thái:** chưa code.
**Files:** pom.xml module, SurgeryServiceApplication, domain/application/web/messaging/infrastructure, application.yml, AGENTS.md, README, Dockerfile, surgery.http và tests.

- [ ] **S-01.1 · SURGERY-G0:** tạo module theo blueprint và package com.mediflow.surgery, port 8091/DB mediflow_surgery; phiên bản dependency kế thừa. Không tạo API success placeholder.
- [ ] **S-01.2 · LOCAL sau S-01.1:** cấu hình DB/Rabbit/Eureka/JWT/correlation, flag surgery=false; test profile tách hạ tầng, không secret mặc định dùng production.
- [ ] **S-01.3 · LOCAL sau S-01.1:** human access-token strict, default-deny; actor account/staff tách rõ. ApiResponse/error mapping, validation và 401/403 cho access/refresh/service/missing type.
- [ ] **S-01.4 · OWNER:** dùng [bootstrap handoff](../../handoffs/HANDOFF-SURGERY-FOUNDATION-BOOTSTRAP.md) cho root module, DB, Compose, Gateway; root chưa đăng ký thì không báo reactor build toàn repo đã hỗ trợ Surgery.
- [ ] **S-01.5 · VERIFY:** ArchitectureTest, config/context và health; chỉ thêm request .http cho endpoint thật. Acceptance: module build và auth tests pass; shared boot/Gateway ghi evidence riêng.

### S-02 — model, schema và reliable persistence

**Gate:** S-01 + policy/DDL của slice ở H-01.3. **Files:** domain/model, application/port/out, infrastructure/persistence/messaging, db/migration/V1__surgery_core.sql và tests.

- [ ] **S-02.1 · LOCAL sau G0:** SurgeryCase + CareEpisode/Status/Priority; ChecklistItem, Consent, Schedule/Team, Result, ReadinessSnapshot, Clearance, EmergencyOverride và History. Pure domain giữ transition/invariant; phân biệt case-owned data với resource locks dùng chung nhiều case.
- [ ] **S-02.2 · LOCAL sau S-02.1:** physical DDL đã map naming; UUID/FK nội bộ, unique surgeryRequestId/result/active consent, version, time/enum/nonblank/check, index query/outbox. Bổ sung template revision, authorization tuple và revision/fingerprint cần thiết đã được khóa; không copy thiếu từ DDL minh họa.
- [ ] **S-02.3 · LOCAL sau S-02.2:** ports framework-free + JPA entities/mappers/adapters; optimistic version và row lock cho command; resource lock order thống nhất S-06. ORM round-trip không làm mất audit/episode.
- [ ] **S-02.4 · LOCAL sau G0 reliability:** inbox eventId/type/version/fingerprint; cùng ID/cùng nội dung no-op, khác nội dung conflict; business command/referral key riêng. Pending early clearance giữ payload tối thiểu/reason/retry state, chưa đánh dấu APPLIED.
- [ ] **S-02.5 · LOCAL sau S-02.2:** transactional outbox + dispatcher lease/confirm/retry/quarantine/recovery; redelivery giữ eventId, ordering per aggregate hoặc revision được test. Không copy code Pharmacy xuyên module.
- [ ] **S-02.6 · VERIFY:** fresh migration, constraints, concurrent first insert, rollback case/history/outbox/inbox; crash trước commit, sau commit trước publish confirm/ACK. Acceptance: không mất committed fact, không nhân đôi business effect, không dùng lock RAM làm bảo đảm cuối.

### S-03 — REST lookup và inbound/outbound adapters

**Gate:** S-02; G1 theo từng contract, không đợi toàn bộ producers live.
**Files:** application/port/in|out, application/event, messaging/consumer/payload, infrastructure/client|config|messaging.

- [ ] **S-03.1 · CONTRACT D01/D02:** referral decoder xác minh type/producer/version và mandatory IDs, gọi cùng create use case với HTTP. Serializer post-case charge chỉ viết sau khi tên/shape/owner được khóa; không bịa event mới trong code.
- [ ] **S-03.2 · LOCAL/CONTRACT D12:** clearance decoder purpose=SURGERY, exact case/patient/episode/admission, expiry. Valid purpose khác service được ignore có chủ đích; purpose SURGERY thiếu target là lỗi. Early valid target → pending durable, malformed → bounded retry/DLQ; không fallback patient/latest case.
- [ ] **S-03.3 · LOCAL sau S-01:** Patient exists adapter theo producer thật E10: service JWT, correlation, timeout và absent/outage/malformed/echo-ID mismatch. Generic Organization adapters theo spec offline; live team eligibility vẫn chờ E11 được bổ sung, không dùng doctor-only DTO để đoán nurse/anesthesiologist.
- [ ] **S-03.4 · CONTRACT D02/D07/D11:** producer ready/completed/cancelled và charge fixture manifest; giữ category aggregate tách summary clinical, planned-time snapshot tối thiểu cho Notification. Mỗi consumer nhận bản sao cùng byte/version hoặc được ghi BLOCKED trong handoff.
- [ ] **S-03.5 · VERIFY:** fixture provenance (path, commit, SHA-256, consumer test), unknown version/producer, missing IDs, optional fields, retry/DLQ/correlation. Acceptance G1 theo event, không đánh dấu cả integration chỉ nhờ một fixture local.

### S-04 — referral → case, đọc case/list

**Gate:** S-02/03; D01/D02 đã thống nhất, checklist initialization policy ở D03.
**Files:** create/get/list in-ports, DTO/mapper, SurgeryCaseApplicationService, repositories, web/SurgeryController.

- [ ] **S-04.1 · LOCAL sau G0:** validate đúng một episode, patient/department/requester authority; HTTP actor từ claim, forwarded requester là source đã được kiểm chứng. Không dùng Patient exists thay admission relationship check.
- [ ] **S-04.2 · LOCAL sau S-04.1:** claim stable surgeryRequestId, tạo REQUESTED + checklist snapshot + history + approved charge fact atomic. Cùng key/cùng intent trả case cũ, changed business payload conflict; command timestamp/transport delivery không làm đổi fingerprint intent.
- [ ] **S-04.3 · LOCAL sau S-04.2:** GET detail/list có state/readiness reasons/planned-vs-actual/result summary, filter khoa/status/period, stable sort/page-size; PageQuery/PageResult, không REST fan-out để join tên patient cho từng row.
- [ ] **S-04.4 · VERIFY:** HTTP/event race, hai replica cùng referral, hai episode cùng patient, wrong relationship, failure outbox rollback; 400/401/403/404/409/422 theo conventions đã khóa. Acceptance: một referral → một case/charge fact, read không mutate.

### S-05 — checklist, consent, clearance và readiness

**Gate:** S-04; D03/D05/D06/D12, resource preparation từ S-06. Không tạo vòng “S-05 xong mới được làm lịch”.
**Files:** domain readiness policy/snapshot; checklist/consent/clearance commands/services/repositories; web endpoints.

- [ ] **S-05.1 · LOCAL sau D03:** khởi tạo theo procedure/template version; confirm item lưu exact evidence reference/version, actor/time. Template mới không sửa hồi tố ca cũ; stale/wrong-order/corrected evidence không hợp lệ. Mandatory NOT_APPLICABLE chỉ hợp lệ nếu policy đã chốt.
- [ ] **S-05.2 · LOCAL sau D06:** consent type/signer/witness/active/revoke audit; unique active theo type, không overwrite chữ ký. Revoke endpoint/role/DTO phải có trong accepted API trước code; transaction invalidate readiness khi còn trước START.
- [ ] **S-05.3 · LOCAL sau D12:** lưu và kiểm tra clearance tuple + expiresAt và override hợp lệ; không nhận payment.completed như quyền mổ. Revocation/freshness không được giả lập bằng grant cũ; source chưa có thì slice tương ứng chưa bật.
- [ ] **S-05.4 · LOCAL sau G0 guards:** pure AND của sáu guards; trả reason thiếu, không endpoint “set READY”. Snapshot lưu dependency revisions; lock/re-read rồi READY + history + ready outbox atomic, no-op không phát lặp. Invalidation/re-ready theo transition đã duyệt.
- [ ] **S-05.5 · VERIFY:** mỗi guard false chặn READY/START; expire/revoke/reschedule tranh START; override không bỏ consent/checklist/team/room; repeated evaluation chỉ một logical readiness transition/event.

### S-06 — tài nguyên, ê-kíp và lịch không trùng

**Gate:** S-04, D04/D05, Organization lookup contract; S-05 policy có thể viết song song.
**Files:** Schedule/Team/resource reservations, ports/adapters, schedule use case/web, migration constraints/index.

- [ ] **S-06.1 · CONTRACT:** khóa room_reference authority/type, staff teamRole↔jobTitle mapping, interval/timezone/buffer/TTL và active schedule states; không suy chuyên môn từ login role ADMIN/DOCTOR.
- [ ] **S-06.2 · LOCAL sau S-06.1:** validate start<end, đủ roles và external eligibility snapshots; không giữ DB lock khi gọi HTTP; unavailable trả 503, absent/ineligible trả domain error theo spec.
- [ ] **S-06.3 · LOCAL sau S-06.1:** chọn/document DB exclusion constraint hoặc resource-row locks + overlap query dưới cùng lock. Chặn room và mỗi staff trên nhiều case; sorted lock order tránh deadlock. Index room/time đơn thuần trong candidate không đạt acceptance.
- [ ] **S-06.4 · LOCAL sau D05:** prepare/confirm slot trong PREOP không đổi case sang SCHEDULED; explicit finalize từ READY khóa slot và đổi state. Reschedule atomic, thất bại giữ lịch cũ; đổi dependency invalidate readiness; overrun/release/TTL theo policy, không tự thả phòng đang mổ.
- [ ] **S-06.5 · VERIFY:** PG hai case tranh phòng, một staff ở hai phòng, partial/full overlap, adjacent slots theo interval đã chốt, reschedule rollback, retries và inactive resource; một winner, không mất old booking/audit.

### S-07 — start, complete, cancel và financial override

**Gate:** S-05/06 + D06/D07 và shared outcome fixtures.
**Files:** command DTO/use cases, domain transitions/result/override, persistence/history/outbox, controllers.

- [ ] **S-07.1 · LOCAL:** START chỉ SCHEDULED, lock/re-evaluate toàn guards + resource/revision; server timestamp giữ nguyên qua retry; không start từ READY hoặc dùng readiness snapshot cũ.
- [ ] **S-07.2 · LOCAL sau D07:** COMPLETE chỉ IN_PROGRESS, một result/case; actual method/outcome/times/complication category và clinical summary theo contract; performed items có itemCode/priceCode/positive quantity. Result + COMPLETED + history + resource release + event atomic; Billing mới tính giá.
- [ ] **S-07.3 · LOCAL sau D06:** FINANCIAL_EMERGENCY lưu approver, verified role, reason/time/target/episode/expiry theo policy; self-approval allow/deny rõ. Không forge consent/clearance/paid flags, không cho request tự nhận approver role.
- [ ] **S-07.4 · CONTRACT D07:** pre-start cancellation derives stage từ state, không tin stage tùy ý của client; preserves ledger refs. Partial abort sau START và result correction chỉ code nếu đã có state/payload/revision/performed-work policy, nếu chưa chốt phải reject có test và ghi ngoài V1.
- [ ] **S-07.5 · VERIFY:** complete/complete, complete/cancel, start/cancel, duplicate với eventId mới, failure khi append event; terminal state không bị đảo. Acceptance: một outcome/result/logical event và consumer Billing/Inpatient/Report xử lý cùng fixture.

## 7. Pharmacy — mở rộng từ nền đã có

### P-01 — giữ nguyên nền payment/dispense legacy đã hoàn thành

**Trạng thái:** CODE_PRESENT / DONE_LOCAL lịch sử; không triển khai lại để tính thêm tiến độ.
**Files hiện hữu:** PaymentApplicationService, DispenseApplicationService, DispenseTransactionService, PaymentReceipt, outbox/migrations và các tests.

- [x] **P-01.1 · ĐÃ CÓ:** receipt payload fingerprint/resume, terminal conditional finalize; lock Rx/slip/stock cho effect-idempotency; compensation/outbox và actor audit V13.
- [x] **P-01.2 · ĐÃ CÓ:** tests race/recovery/late compensation/rollback; strict actor từ claims, SYSTEM không UUID giả; legacy cancelled/expired fixtures.
- [ ] **P-01.3 · NGAY/VERIFY:** trước refactor P-03 chạy lại baseline, ghi số tests/skip thực; regression cả create/cancel/expiry/reconcile/admin outbox. Không đổi production Billing wire hoặc receipt policy trong task này.

### P-02 — context/schema/DTO/event additive, bắt đầu được trước producer live

**Gate:** Pharmacy V2 đã implementation-ready; xử lý gap §3.2 theo slice. **Trạng thái:** chưa code V2.
**Files:** Prescription/CareContext/CareEpisode, request/command/response/mappers, JPA entity/adapter, migrations, application/event, publisher, config và fixture tests.

- [ ] **P-02.1 · NGAY:** domain value objects/version 0/1 và config flag=false; validation matrix legacy vs v1. ADMISSION yêu cầu admissionId=careEpisodeId; OUTPATIENT không có admissionId và không tự đổi appointment episode thành recordId. Pure unit tests không cần producer/Docker.
- [ ] **P-02.2 · LOCAL sau P-02.1:** V14 additive cho columns/index/conditional constraints + PRESCRIPTION_CLEARANCE/ADMISSION_MEDICATION_CONTEXT. Upgrade V13 rows thành version0/OUTPATIENT, không bịa episode; cho record nullable đúng nhánh V2 đồng thời giữ v0 constraint. Test null CHECK loopholes, ORM, old rows/outbox/actor audit. Pending/freshness storage mở rộng ở P-03 sau khi policy rõ.
- [ ] **P-02.3 · LOCAL sau H-01.4:** backward-compatible DTO boundary: request v0 không đổi, v1 có đủ context/episode/priceCode; selected V2 mà thiếu field trả lỗi, không silently downgrade. Giữ prescribedDate và doctor authorization: DOCTOR đúng staff claim, ADMIN delegation theo rule hiện hữu, client không cung cấp giá/dispensedBy.
- [ ] **P-02.4 · LOCAL sau P-02.1:** versioned serializer/decoder tách legacy flat và V1 envelope, không rewrite committed outbox. Created/filled/failed đủ exact context, price/source refs và timestamps; filled mang dispenseId; cancelled/expired cũng phải được lập contract V2 cho đường compensation, không bỏ quên vì bảng spec chỉ liệt kê ba event.
- [ ] **P-02.5 · CONTRACT/VERIFY:** canonical payload/fixture với Billing/Clinical/Inpatient/Notification/Report; update mọi publisher path create/dispense/failure/cancel/expiry/retry. Consumer ngoài scope chưa cập nhật thì handoff BLOCKED; chưa bật V1 writer.
- [ ] **P-02.6 · VERIFY:** fresh/upgrade V13→V14, request cũ/DTO cũ, context mismatch, same patient multiple episodes, stale outbox dispatch; test flag-off giữ legacy behavior và không mở admission ngoài ý muốn.

Acceptance local: dữ liệu cũ không bị đổi ngữ nghĩa, context invalid không được persist, actor và price authority giữ nguyên. G1/G3 riêng cho OUTPATIENT V1 và ADMISSION; không cần đợi Surgery mới code phần này.

### P-03 — authorizer theo context, projection và cấp thuốc atomic

**Gate:** P-02; G1 Billing cho outpatient V1, G1 Inpatient cho admission. Không dùng một paid boolean chung.
**Files:** ReactToCareFinanceUseCase, authorization policy/port, clearance/admission commands & repositories, consumer adapters, DispenseApplicationService/TransactionService và failure/cancel/expiry paths.

- [ ] **P-03.1 · LOCAL sau P-02.1:** tách ba đường authorization: v0 receipt; v1 OUTPATIENT exact PRESCRIPTION clearance; v1 ADMISSION active admission đúng patient/khoa. Refactor trước bằng legacy characterization, chưa cấp quyền V1 khi flag tắt. One-slip-per-prescription giữ nguyên; không mở partial-dose/administration/returns.
- [ ] **P-03.2 · LOCAL/CONTRACT D12:** clearance grant lưu dedupe eventId + clearanceId/target; đúng purpose/patient/episode, expiry ngay lúc dispense. Grant không tự tạo PaymentReceipt; payment.completed legacy không unlock v1 admission hoặc bypass thiếu clearance v1. Chốt grant chỉ authorize hay trigger automated dispense trong contract trước bật.
- [ ] **P-03.3 · LOCAL/CONTRACT D08/D12:** admission.started/closed projection bằng exact admissionId; duplicates/close-before-start/late-start không reopen. Khóa patient và department relation; medical-discharge/transfer/freshness chưa có fact thì ghi unavailable/blocker, không dựa vĩnh viễn vào active=true. Pending event đã lưu khác applied marker.
- [ ] **P-03.4 · LOCAL sau P-03.1–3:** check authorization snapshot/version và expiry dưới transaction/lock phù hợp với dispense; active-context update tranh dispense cho outcome theo thứ tự commit. Stock/reservation/Rx/slip/filled outbox cùng commit; không HTTP dưới stock lock.
- [ ] **P-03.5 · LOCAL/CONTRACT D08:** cancel/expiry/failed dispense giải phóng reservation và phát exact charge-adjustment fact. Admission chưa prepaid không tạo invoiceId giả/refund intent kiểu cũ. Authorization denial không bị catch như stock failure rồi hủy đơn/bù trừ ngoài ý muốn; phân loại lỗi trước dùng lại RecordDispenseFailureService.
- [ ] **P-03.6 · VERIFY:** spec tests outpatientWithoutClearance, admissionContextMismatch, closedAdmission, duplicateClearance, repeatedCommand, stockOutboxAtomic; thêm wrong purpose/expired grant, payment+clearance race theo version, close/dispense race, pending restart, context event round-trip và DLQ.

Acceptance: một prescription chỉ một stock effect, không nhầm episode/permission; compensation là fact cho Billing quyết định, không Pharmacy tự hoàn tiền. Outpatient v0 suite phải giữ xanh.

## 8. Report — contribution/read model V2 riêng, không đổi nghĩa báo cáo cũ

### R-01 — event routing, contributions, pending và replay foundation

**Gate:** Report V2 cho phép additive offline; D09/D11 chỉ chặn phần cần data/policy chưa rõ.
**Files:** messaging/consumer, application commands/ports/projectors, domain contribution/delta, infrastructure/persistence/config, migrations và contract tests.

- [ ] **R-01.1 · NGAY:** dựng namespace/commands/interfaces V2 + flag=false, giữ 5 binding/3 API cũ; pure validation và decoder framework test harness. Unknown version/source không vào legacy bằng fallback. Chưa bind V2 live chỉ để ACK bỏ.
- [ ] **R-01.2 · LOCAL sau H-01.5:** schema riêng FINANCIAL_CONTRIBUTION/DAILY_FINANCIAL_REPORT/OPERATIONAL_CONTRIBUTION/DAILY_OPERATIONAL_REPORT theo target V6 (xác minh numbering trước code). Inbox delivery key khác semantic key: transaction/refund operation, settlement revision, result/dispense operation. eventId là provenance, không đủ unique cho nghiệp vụ. Null hospital scope không được va với department sentinel hợp lệ.
- [ ] **R-01.3 · LOCAL sau keys:** transaction claim + contribution + hai scopes department/hospital; atomic insert/upsert và lock order ổn định; no JVM mutex. Refund-before-original/close-before-start vào pending có exact original ref và retry/checkpoint, không FK lỗi lặp vô hạn hoặc invent date/department.
- [ ] **R-01.4 · CONTRACT D11:** chọn durable replay source (Report minimal journal từ activation hoặc archive/outbox retention do owner cung cấp), schema/projector version, retention/access/redaction, horizon dữ liệu có thể rebuild. Queue ACK không phải archive; lịch sử trước source activation phải có export hoặc ghi unavailable.
- [ ] **R-01.5 · LOCAL sau R-01.4:** journal/processing ledger tách RECEIVED/PENDING/APPLIED/REJECTED và generation. Replay vào generation mới hoặc projection offline riêng; không truncate live tables/xóa live inbox để “chạy lại”. Dùng cùng pure projector, không re-publish command/notification.
- [ ] **R-01.6 · VERIFY:** same event/different bytes conflict, cùng operation/new eventId no double, two valid partial transactions both apply, rollback giữa hai scopes, concurrent new scope, pending resume once sau restart, shuffled replay deterministic.
- [ ] **R-01.7 · CONTRACT/VERIFY:** watermark/live catch-up, reconcile và atomic read switch/rollback; failed replay giữ generation cũ. Legacy test redelivery không được ghi thành bằng chứng rebuild từ durable journal.

### R-02 — security hiện có, bổ sung đúng role của endpoint V2

**Trạng thái:** strict JWT đã có; endpoint V2 chưa có.
**Files:** JwtAuthFilter/SecurityConfig, report controllers/web tests, report.http.

- [x] **R-02.1 · ĐÃ CÓ:** human type=access strict, refresh/service/missing type bị reject; legacy daily/monthly/top-medicines ADMIN/MANAGER.
- [ ] **R-02.2 · NGAY/VERIFY:** giữ focused JWT tests trong mọi PR Report, không viết lại filter hoặc sửa Common/Gateway.
- [ ] **R-02.3 · LOCAL/OWNER:** financial mới ADMIN/MANAGER; operations mới ADMIN/MANAGER/DOCTOR theo target, không mở DOCTOR cho legacy revenue. Gateway rule cần Hoàng Anh sửa route cụ thể; kiểm thử direct-service và Gateway riêng.

### R-03 — projection tài chính theo fact đủ dữ liệu

**Gate:** R-01; Billing V1 fixture đúng các field dưới đây. Code từng metric độc lập được, không trả đủ dashboard bằng số giả.
**Files:** FinancialContribution/Delta, financial projector/use cases, repositories, DTO/query/controllers, fixtures.

| Chỉ tiêu | Data đã được chọn trong target | Còn phải khóa trước ghi số live |
|---|---|---|
| Cash receipts/movement | payment transactionId, classification, totalAmount, completedAt, account/episode | Gross cash không giảm vì refund hay net cash giảm? DDL cash_received vs phương trình cash-=refund phải cùng nghĩa; partial receipt phải có fact dù chưa đủ clearance. |
| Deposit liability | ADMISSION_DEPOSIT classification; tiền đặt cọc không earned | Unallocated amount và allocation/release/refund liability chính xác; không dùng toàn totalAmount nếu source đã allocate một phần. |
| Earned revenue | SERVICE_PAYMENT/settlement recognition | allocatedEarned/recognized delta, charge/allocation identity, department split; settlement/payment không ghi hai lần cùng recognition. |
| Refund/reversal | refundTransactionId, originalTransactionId, amount/currency | Source contribution/line cần đảo, partial refund, original scope vs cash refund date, deposit vs earned reversal, pending before original. |
| Outstanding receivable | persisted Billing balance/outcome | Settlement version/supersedes/order; số dư as-of khác daily flow. Monthly không cộng số dư của mọi ngày như revenue. |

- [ ] **R-03.1 · LOCAL/CONTRACT D09:** mapper/version validation cho payment/refund/settlement payload; classification unknown/missing allocation không default SERVICE_PAYMENT. Ghi expected-results fixtures Billing xác nhận, không gọi fixture tự dựng là producer evidence.
- [ ] **R-03.2 · LOCAL khi source đủ:** cash/deposit projector keyed transaction/allocation operation; BigDecimal/currency/scale, business completedAt theo timezone. Một invoice có hai giao dịch hợp lệ là hai contributions, không dùng invoiceId làm unique V2.
- [ ] **R-03.3 · CONTRACT:** recognition/liability release projector chỉ nhận amount + operation/revision rõ; không totalAmount=earned hay settlement.grossAmount=delta. Chốt department/hospital reconciliation khi account nhiều khoa.
- [ ] **R-03.4 · CONTRACT/LOCAL:** refund liên kết original theo transaction/allocation, partial amount giới hạn source fact; đảo original scope đúng contract, không ngày ingest. V2 payment.failed không tự biến thành completed refund.
- [ ] **R-03.5 · CONTRACT/LOCAL:** settlement snapshot/version replaces prior receivable contribution hoặc computes delta từ persisted prior snapshot; out-of-order revision không hạ về cũ. Không cộng completedPayments lần nữa vào cash.
- [ ] **R-03.6 · LOCAL sau metric:** GET financial/daily?from&to&departmentId và financial/monthly?year&departmentId; bounded period, availability/freshness thật, không đổi legacy DTO. Unavailable projection theo spec trả 404 REPORT_NOT_FOUND, lỗi period 400.
- [ ] **R-03.7 · VERIFY:** deposit→allocation/recognition→refund, split 60/40 department, two partial payments, refund-before-original, duplicate new eventId, settlement correction, midnight/year boundary/replay. Đối soát tất cả 5 chỉ tiêu, không chỉ revenue.

Ví dụ nghiệm thu cần Billing xác nhận bằng fact: nhận deposit 100, nhận recognition/allocation 80, refund dư 20 → gross receipts 100, net cash 80, refunds 20, liability 0, earned 80. Chỉ đạt khi source phát đủ allocation/release; đây không phải phép suy revenue từ cash.

### R-04 — metrics vận hành và API có nguồn xác định

**Gate:** R-01, từng producer fixture và D07/D08/D10/D11 tương ứng.
**Files:** OperationalContribution/Delta, event projectors, admission/surgery source projections nếu cần, query ports/repositories/controllers.

- [ ] **R-04.1 · LOCAL khi có fixture Clinical/Lab/Pharmacy:** medicalrecord.completed/disposition tách created legacy; Lab completed theo order/result version và requesting department; prescription.filled theo dispenseId/context, tách số đơn/số slip/số lượng thuốc. Không đếm created+completed thành hai lượt trong cùng metric.
- [ ] **R-04.2 · LOCAL/CONTRACT D10:** started/closed paired theo admissionId, closed thiếu department dùng stored matching start, close-before-start pending. Chốt discharge counter/administrative duration, timezone/cutoff/denominator và inpatient-days rounding; không gọi closedAt là medicalDischargedAt.
- [ ] **R-04.3 · CONTRACT D10:** bed occupancy chỉ sau bed assignment/transfer/release intervals và staffed/available capacity theo kỳ. Hiện không đủ facts: metric unavailable/deferred, không activeAdmissions/capacity giả. Không chặn admissions/administrative duration đủ source.
- [ ] **R-04.4 · LOCAL/CONTRACT D07:** completed/cancelled surgery theo result/outcome identity/revision, actual started/completed duration, complication category và stage/reason taxonomy đã khóa. Không lấy plannedAt thay actual hoặc đoán category từ text; correction/partial abort chưa hỗ trợ không âm thầm đếm như completed.
- [ ] **R-04.5 · LOCAL sau source/query semantics:** GET operations/daily và operations/surgery theo target; aggregate-only, bounded from/to, stable sort, null-department hospital scope; phân biệt no-events zero với source chưa sẵn sàng. Không REST join để fill missing event data.
- [ ] **R-04.6 · VERIFY:** role matrix R-02, no clinical payload, same source/new eventId, close-before-start, midnight/leap-day, two departments/hospital reconciliation, zero-fill đúng availability, query count/index plan có giới hạn. Không sửa legacy endpoints.

## 9. Handoff, thứ tự triển khai và nghiệm thu liên service

### 9.1. Đầu việc owner khác — không phải quyền production của Huy

| Owner | Đầu ra đang cần | Task Huy được mở | Handoff/acceptance |
|---|---|---|---|
| Vinh: Clinical/Inpatient/Lab | Exact episode/referral, lifecycle/version/freshness/relationship, medical discharge/transfer policy, exact pre-op evidence, completed/closed/result consumers | S-03–07, P-03, R-04 | [Surgery decisions](../../handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md), [Huy consumers](../../handoffs/HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md); shared fixtures + mismatch/out-of-order tests |
| Lộc: Billing/Notification | Post-case charge bridge, V1 clearance, transaction/allocation/refund/settlement data, cancel/expiry compensation, ready display/notification dedupe | S-03/07, P-02/03, R-03 | Hai handoff trên; expected finance totals và producer/consumer tests cùng bytes |
| Hoàng Anh: Organization/Patient/Gateway | Generic staff/department lookup, eligibility/room authority, route-specific roles; Patient endpoint đã có, cần reuse/test | S-01/03/06, R-02/04, X-01 | [Bootstrap](../../handoffs/HANDOFF-SURGERY-FOUNDATION-BOOTSTRAP.md); service auth, absent/outage và Gateway smoke |
| Shared integrator được giao | Surgery root registration, DB/Compose/CI/runtime wiring | S-01 và X-01 | Bootstrap handoff; reactor build, health/discovery, repeatable environment, không sửa Common nếu không thực sự cần |

Không tạo handoff trùng cho gap đã có; cập nhật requirement còn thiếu vào handoff hiện hữu và giữ [registry](../../handoffs/README.md) nhất quán. Handoff Patient vẫn OPEN trong registry dù E10 đã có code: chỉ retirement sau kiểm chứng hai phía, không tiếp tục ghi “producer endpoint chưa tồn tại”.

### 9.2. Thứ tự lấy việc mới

| Làn | Thứ tự | Có cần chờ Surgery/team toàn bộ không? |
|---|---|---|
| 1 — làm ngay | P-01.3 baseline → P-02.1 context policy/flag → H-01.4 compatibility → P-02.2/3 migration/DTO và test local | Không. Chỉ chặn phần field/policy còn gap; writer V1 vẫn off. |
| 2 — Report foundation | R-02.2 regression → R-01.1 namespace/decoder harness → H-01.5 source/metric keys → R-01.2/3 → journal/replay | Không cần Surgery cho nền hay finance; từng projector cần source tương ứng. |
| 3 — Surgery | H-01.2/3 chốt candidate + shared assignment → S-01/02 → S-03/04 → S-06 prepare + S-05 guards → S-06 finalize → S-07 | Gate §14 có thật; shared bootstrap là blocker khác quyết định nghiệp vụ. |
| 4 — integration riêng | Billing clearance → outpatient V1; Inpatient lifecycle → admission thuốc; Billing finance → Report finance; Surgery outcomes → Report surgery | Mỗi slice đạt G1/G2/G3 riêng, không chờ cả 12 D cùng đóng. |

Ba subtask code local ưu tiên ngay khi được yêu cầu triển khai tiếp: **P-02.1**, **R-01.1**, cùng **P-01.3/R-02.2** để chốt baseline test. Migration/DTO tiếp theo cần compatibility matrix cụ thể, không bật producer chỉ vì model compile.

### X-01 — kiểm thử hệ thống, rollout và báo cáo bằng chứng

**Files Huy:** tests/resources/contracts, module .http/README, plan/evidence/handoff. Shared runtime do owner thực hiện.

- [ ] **X-01.1 · NGAY/VERIFY:** xác minh Docker engine thực sự reachable; clean baseline Huy suites, đọc Surefire counts. Docker Desktop đang mở không tự chứng minh Testcontainers chạy được.
- [ ] **X-01.2 · CONTRACT/VERIFY:** manifest mỗi event có canonical ID/version, producer/consumer paths, SHA-256 và command/test output; valid/duplicate/new-eventId-same-operation/poison/missing target/unsupported version đều có case.
- [ ] **X-01.3 · VERIFY:** Docker outpatient legacy + outpatient V1 exact clearance, admission started→prescribe→dispense→charge→close; duplicate authorization/close race và failures không phá stock/ledger. Không giả producer bằng DB inserts rồi gọi đó E2E.
- [ ] **X-01.4 · VERIFY sau Surgery G0–G2:** request→checklist/consent→clearance→prepare/finalize schedule→start→complete/cancel, cùng resource race, consent revoke, expiry, financial-only override. Gateway roles và downstream consumer fixtures/runtime đúng.
- [ ] **X-01.5 · VERIFY sau R-01/03/04:** projection replay từ empty isolated generation, live catch-up và đối soát deposit/refund/settlement/operations; source outage/unavailable khác zero. Failed reconcile không chuyển read pointer.
- [ ] **X-01.6 · LOCAL/VERIFY:** outbox pending/oldest/quarantine, consumer lag/pending age/DLQ/replay progress; trace correlation metadata, không log token/full patient/consent/payload, không patientId metric labels. Ngưỡng alert phải được ghi rõ, không tuyên bố dashboard đã có chỉ từ gauge inventory.
- [ ] **X-01.7 · VERIFY:** rollout migration additive → compatible readers → G1 fixtures → từng writer/binding → shadow/reconcile → API/client cutover. Rollback bằng flag/read-generation đã test, không drop mới/xóa inbox/journal hoặc deploy binary cũ không đọc được v1 rows.
- [ ] **X-01.8 · NGAY khi đóng slice:** ghi evidence §10, test names/counts/fail/skip, migration source/target, Docker images, fixture hashes, commit, blockers/owner. Review diff đúng scope trước đề xuất PR; không commit/push trong lượt chỉ viết plan.

## 10. Tiến độ kế thừa và cách đánh dấu task sau này

### 10.1. Không làm mất kết quả trước V2

| Hạng mục cũ | Giữ trạng thái | Map sang plan hiện tại |
|---|---|---|
| P-01a–f; P-02g legacy; P-03d/g | Code/test legacy đã làm; V13 actor audit hiện có | P-01 reuse + P-02/P-03 regression. Không gọi admission V2 DONE. |
| R-02; R-01f/i legacy; R-04h/i legacy | JWT, invoice contribution, concurrency/reversal/web/retry tests đã làm | R-02 reuse; R-01/R-03 giữ compatibility và mở V2 riêng. |
| H-01a–e, S-01d, S-02a/S-03f/S-04c/d/e/S-05a design | Đã soạn draft/handoff/scenarios, chưa Surgery code | Input H-01/S tasks mới, candidate §14 vẫn còn. |
| 15 independent slices ngày 2026-09-26: S-06c, S-07e, P-02e/f, R-01a/b/c/e/g, R-03a/h, R-04g, X-01f/g/h | DONE_LOCAL ở đúng mức design/fixture legacy/evidence nêu trong tài liệu | Tái sử dụng race/transaction/finance/replay scenarios; không tính 15 tính năng V2 đã implement. |

**Lịch sử test, không phải run của baseline mới:**

- 2026-09-25: plan cũ ghi Pharmacy 207 tests, 0 failures/errors/skips, PostgreSQL/Rabbit Testcontainers; các lần bổ sung Report ghi 129 rồi 132 tests tại thời điểm tương ứng.
- 2026-09-26: independent evidence ghi Pharmacy 217 discovered, 46 skipped; Report 123 discovered, 24 skipped; 0 failures/errors, nhưng container tests chưa chạy. Focused Pharmacy architecture/web/dispense/fixture 42 pass, 0 skip.
- 2026-09-27, lượt này: **static source/spec audit và sửa tài liệu**, không chạy Maven/Docker. Không lấy test count lịch sử khác commit làm chứng nhận release hiện tại.

### 10.2. Checklist đóng mỗi task

- [ ] Ghi subtask ID, source commit, files đã sửa, rule/contract version; phân biệt code với design.
- [ ] Unit/domain/application + web/security/architecture liên quan pass; import layers, DTO boundary, actor identity đúng.
- [ ] Nếu có DB/broker: fresh/upgrade/concurrency/rollback/retry Testcontainers chạy thật, không skip critical cases.
- [ ] Producer và consumer dùng cùng fixture khi có wire change; thiếu owner ghi CONTRACT/OWNER, không đóng bằng mock.
- [ ] Migration additive, legacy API/outbox rows đọc được; flag false không tạo V2 effects hoặc ACK mất facts.
- [ ] Nếu ghi E2E_PASS: có Gateway + producers/consumers thật, dữ liệu expected/actual, reconciliation và rollback.
- [ ] git diff --check; link docs/fixture/task IDs hợp lệ; scope chỉ Huy + docs đã giao, không sửa production của owner khác.
- [ ] Cập nhật checkbox, trạng thái SPEC/CODE/FIXTURE/TEST/E2E, handoff còn mở và bước kế tiếp.

Mẫu evidence cho mỗi slice:

~~~text
Task / commit / ngày:
Source spec + quyết định owner (link, nếu cần):
Implementation files / migration from → to:
Contract / version / producer fixture path + SHA-256 / consumers:
Commands + test suites / pass / fail / skip:
DB/Rabbit images + runtime prerequisites:
Scenario → expected → observed (stock/state/inbox/outbox/contribution/aggregate):
SPEC: ... | CODE: ... | FIXTURE: ... | LOCAL_TEST: ... | E2E: ...
Blocker / owner / next task:
~~~

Khi triển khai, đánh dấu ngay subtask tương ứng, không tick toàn task cha vì một slice chuẩn bị xong. Chưa có việc code V2 nào được đánh dấu hoàn thành bởi lần làm lại plan này.
