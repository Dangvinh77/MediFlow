# Kế hoạch code của Huy — Surgery, Pharmacy, Report theo Care–Finance V2

**Cập nhật:** 2026-09-29 · **Owner:** Huy (LQHuy0210).
**Baseline đã đọc:** nhánh Huy tại commit `6686f9e` (đã chứa Surgery foundation/domain `4a4b098` và master `dd92727`). Changelog và source được rà lại trước khi sửa plan.
**Loại kiểm chứng gần nhất (2026-09-29):** `TESTCONTAINERS_RYUK_DISABLED=true mvn -f backend/surgery-service/pom.xml '-Dapi.version=1.44' test` đạt **89 tests, 0 failures/errors/skips**; PostgreSQL 16.14 Testcontainers chạy thật. Năm subtask tiếp theo có local unit/PG evidence ở S-02.6, S-03.3.2.1, S-05.4.3, S-06.3.1 và S-06.3.2. Chưa kiểm thử Rabbit/Gateway/root reactor.
**Trạng thái:** S-02.2 và S-03.3.1 DONE/LOCAL. S-02.3.1–.5, persistence S-05.1.1, và các phần kiểm tra local của S-02.6/S-03.3.2.1/S-05.4.3/S-06.3 có bằng chứng test. Parent workflows vẫn PARTIAL do còn application orchestration, owner contracts/fixtures, concurrency/failure matrix rộng hơn và Rabbit delivery. Không suy từ adapter/module test sang workflow Surgery production; feature flags vẫn false.

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
| [Surgery V2 candidate](../../eproject_general_plan/backend-spec/care-finance-v2/11-surgery.md) | DDL/DTO là candidate, không copy nguyên mẫu. Huy-local choices tại handoff đã mở domain/persistence nội bộ; V1 không có emergency override. Chỉ cạnh tích hợp chưa rõ mới chờ §14/fixture tương ứng. |
| [Inpatient Core V1](../../eproject_general_plan/backend-spec/care-finance-v2/10-inpatient.md) | Đã có application service, migration, admission events và Surgery consumers; chưa chứng minh referral/relationship lookup hoặc shared Surgery fixtures. Medical discharge khác administrative close. |
| [Billing V2](../../eproject_general_plan/backend-spec/care-finance-v2/06-billing.md) | Account/charge/payment request/transaction/allocation/settlement; exact clearance; classified payment/refund; còn gap dữ liệu recognition/revision cho Report. |
| [Clinical V2](../../eproject_general_plan/backend-spec/care-finance-v2/03-clinical.md), [Lab V2](../../eproject_general_plan/backend-spec/care-finance-v2/04-lab.md) | Episode ngoại trú appointment-backed; completion/disposition/referral; Lab order/clearance/result version và exact order correlation. |
| [Organization V2](../../eproject_general_plan/backend-spec/care-finance-v2/01-organization.md), [Patient V2](../../eproject_general_plan/backend-spec/care-finance-v2/02-patient.md) | Service-only identity lookup, absence khác outage; không thay staff UUID bằng JWT subject. |
| [Gateway V2](../../eproject_general_plan/backend-spec/care-finance-v2/09-gateway.md), [Notification V2](../../eproject_general_plan/backend-spec/care-finance-v2/07-notification.md) | Route/role và payload hiển thị cần map với API/event Huy; không coi đặc tả route là route đã chạy. |

[Bản plan trước V2](2026-09-25-huy-surgery-pharmacy-report.legacy.md) giữ nguyên nội dung lịch sử bên dưới ghi chú archive. [Independent slices 2026-09-26](2026-09-26-huy-independent-slices.md) là bằng chứng chuẩn bị/legacy, không phải backlog V2 hiện hành. Draft cũ [10-surgery.md](../../eproject_general_plan/backend-spec/10-surgery.md) được dùng để tái sử dụng scenario, không cạnh tranh với Surgery V2 candidate.

Giữ 16 mã task cha H-01, S-01…07, P-01…03, R-01…04, X-01. Subtask mới dùng dấu chấm (P-02.1), khác subtask cũ dùng chữ (P-02a); không tự chuyển checkbox cũ sang implementation V2.

### 1.1a. Inventory các file plan có Surgery đã đối chiếu

| File | Vai trò khi lấy việc |
|---|---|
| Plan hiện tại, [§6 Surgery](#surgery-backlog) | **Backlog code duy nhất đang active** cho S-01…S-07; giữ ID và evidence đã có. |
| [Plan legacy 2026-09-25](2026-09-25-huy-surgery-pharmacy-report.legacy.md) | Lịch sử task chữ cái, D01–D12 và scenario; không thực thi song song với backlog này. |
| [Independent slices 2026-09-26](2026-09-26-huy-independent-slices.md) | Tái sử dụng khảo sát resource locks, duplicate/race scenarios; DONE_LOCAL là chuẩn bị tại thời điểm đó. |
| [V2 specs plan 2026-09-27](2026-09-27-care-finance-v2-specs.md) | Theo dõi sản xuất spec, không phải checklist implementation. |
| [Service-doc alignment 2026-09-24](2026-09-24-care-finance-service-doc-alignment.md) | Theo dõi căn chỉnh docs/contracts; tick không chứng minh API/event đã chạy. |
| [Architecture HTML plan 2026-09-22](2026-09-22-mediflow-care-finance-architecture-html.md) | Nguồn kiến trúc/quy trình và phân công, không thay test acceptance của code. |

Đối chiếu thêm cả [working draft Surgery](../../eproject_general_plan/backend-spec/10-surgery.md), [V2 candidate](../../eproject_general_plan/backend-spec/care-finance-v2/11-surgery.md), [bounded-context rules](../../ai/services/surgery.md), [decision handoff](../../handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md) và [bootstrap handoff](../../handoffs/HANDOFF-SURGERY-FOUNDATION-BOOTSTRAP.md). Nếu candidate khác quyết định Huy đã ghi, áp dụng quyết định **chỉ trong phạm vi local**; wire contract phải được cập nhật cùng owner trước G1.

### 1.2. Nhãn lấy việc

| Nhãn | Có thể làm gì |
|---|---|
| NGAY | Làm độc lập trong scope Huy: tài liệu, characterization hoặc code đã đủ ngữ nghĩa, mặc định không bật V2. |
| LOCAL | Code/test offline sau task nội bộ được ghi rõ; không cần producer chạy live. Fixture tự viết chỉ là test nội bộ, chưa đạt gate liên service. |
| CONTRACT | Lát cắt cần chốt chính xác field/policy còn mâu thuẫn hoặc fixture owner; không đoán ID/giá/ý nghĩa tiền. Các phần khác tiếp tục được. |
| SURGERY-G0 | Huy-local defaults §14 đã được chọn, nên có thể làm domain/schema nội bộ theo các quyết định đó; các cạnh REST/event và fields do service khác sở hữu vẫn chờ handoff/fixture. |
| OWNER | Cần owner khác/shared integrator thực hiện; Huy chuẩn bị handoff và kiểm thử consumer, không sửa production ngoài phạm vi. |
| VERIFY | Code đã có hoặc đã viết test nhưng cần thực thi lại trên môi trường tương ứng. |
| ĐÃ CÓ | Giữ/reuse implementation hoặc thiết kế cũ, không lập lại như tính năng chưa làm. |
| DEFERRED V1 | Chủ động ngoài V1; không tính là blocker hoàn tất V1 và không tạo endpoint giả. Test từ chối vẫn thuộc V1. |

Checkbox chỉ đóng khi đầu ra và acceptance của đúng subtask đạt. SPEC_READY, CODE_PRESENT, SHARED_FIXTURE_PASS, LOCAL_TEST_PASS, E2E_PASS là các bằng chứng khác nhau; không suy ra lẫn nhau.

## 2. Audit backend hiện tại — bằng chứng và giới hạn

Các đường dẫn dưới đây là source, không phải code ví dụ trong spec. E01/E02/E09 và các nhận định liên quan Surgery được cập nhật tại `6686f9e`; phần Pharmacy/Report giữ lịch sử audit và bổ sung foundation đã có. Có code V2 ở một số context **không** chứng minh các cạnh Surgery đã tương thích hoặc đang bật.

| Mã | Bằng chứng source | Kết luận dùng để lập task |
|---|---|---|
| E01 | [Surgery module](../../../backend/surgery-service), [domain](../../../backend/surgery-service/src/main/java/com/mediflow/surgery/domain/model), [tests](../../../backend/surgery-service/src/test/java/com/mediflow/surgery); chưa đăng ký ở [root modules](../../../pom.xml), [Compose](../../../docker-compose.yml), [DB bootstrap](../../../scripts/init-databases.sql). | Platform, CareEpisode, SurgeryCase, ReadinessSnapshot, history và 30 tests đã có. Chưa business migration/application service/API/persistence/messaging. Readiness hiện nhận booleans, chưa tự kiểm chứng evidence. |
| E02 | [InpatientApplicationService](../../../backend/inpatient-service/src/main/java/com/mediflow/inpatient/application/service/InpatientApplicationService.java), [V1 migration](../../../backend/inpatient-service/src/main/resources/db/migration/V1__inpatient_core.sql), [InpatientEventConsumer](../../../backend/inpatient-service/src/main/java/com/mediflow/inpatient/infrastructure/messaging/consumer/InpatientEventConsumer.java). | Đã có admission.started/closed và Surgery ready/completed/cancelled consumers. Consumer yêu cầu admissionId và external-order reference tồn tại; chưa thấy surgery.requested producer/shared fixture. Cần chốt reference registration, OUTPATIENT not-applicable và late-event policy; không coi human GET admission là service-auth lookup. |
| E03 | [CreatePrescriptionRequest](../../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/application/dto/request/CreatePrescriptionRequest.java), [Prescription](../../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/domain/model/Prescription.java), [V1](../../../backend/pharmacy-service/src/main/resources/db/migration/V1__init.sql) | DTO recordId bắt buộc, DB record_id NOT NULL; chưa có care context/episode/version/admission. Unique slip theo prescription đã có. |
| E04 | [PrescriptionApplicationService](../../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/application/service/PrescriptionApplicationService.java), [DispenseApplicationService](../../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/application/service/DispenseApplicationService.java), [DispenseTransactionService](../../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/application/service/DispenseTransactionService.java) | Giá snapshot, reservation, actor kiểm tra từ claim, payment proof và transaction stock/slip/outbox đã có. Admission authorizer chưa có. Không viết lại engine tồn kho. |
| E05 | [Pharmacy migrations V1–V13](../../../backend/pharmacy-service/src/main/resources/db/migration), [events](../../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/application/event), [legacy fixtures](../../../backend/pharmacy-service/src/test/resources/contracts) | Đã có receipt, outbox/recovery/quarantine/fencing/causal order và V13 actor audit; events phẳng, chưa nested envelope V1/care context/dispenseId trên filled. |
| E06 | [Report RabbitConfig](../../../backend/report-service/src/main/java/com/mediflow/report/infrastructure/config/RabbitConfig.java), [AggregateUpdaterService](../../../backend/report-service/src/main/java/com/mediflow/report/application/service/AggregateUpdaterService.java), [PaymentContribution](../../../backend/report-service/src/main/java/com/mediflow/report/domain/model/PaymentContribution.java) | Đúng 5 binding legacy. Invoice-keyed contribution có APPLIED/PENDING_REVERSAL/REVERSED; không phải transaction ledger/settlement projector V2. |
| E07 | [Report migrations V1–V2](../../../backend/report-service/src/main/resources/db/migration), [ReportController](../../../backend/report-service/src/main/java/com/mediflow/report/web/ReportController.java), [JWT filter](../../../backend/report-service/src/main/java/com/mediflow/report/infrastructure/security/JwtAuthFilter.java) | Ba API daily/monthly/top-medicines cho ADMIN/MANAGER; access-token strict; chưa financial/operational tables, durable journal hay replay generations. |
| E08 | [BillingApplicationService](../../../backend/billing-service/src/main/java/com/mediflow/billing/application/service/BillingApplicationService.java), [PaymentCompletedEvent](../../../backend/billing-service/src/main/java/com/mediflow/billing/application/event/PaymentCompletedEvent.java), [migrations V1–V3](../../../backend/billing-service/src/main/resources/db/migration) | Invoice/fee saga hiện hữu, payment có prescriptionId và labTestIds thật. Chưa transactionId/classification/account/episode; chưa ledger V2/clearance/refund/settlement producers. Department hiện lấy fee đầu tiên, không phải phân bổ đa khoa. |
| E09 | [Clinical source](../../../backend/clinical-service/src/main/java/com/mediflow/clinical), [Lab source](../../../backend/lab-service/src/main/java/com/mediflow/lab). | Đã có Clinical medicalrecord.completed/admission.requested, Lab request-time V2 và financial.clearance handling. Chưa thấy surgery.requested producer hoặc contract exact-case pre-op evidence; không lấy Lab result bất kỳ làm checklist đạt. |
| E10 | [PatientController](../../../backend/patient-service/src/main/java/com/mediflow/patient/web/PatientController.java), [PatientLookupDTO](../../../backend/patient-service/src/main/java/com/mediflow/patient/application/dto/response/PatientLookupDTO.java), [PatientControllerWebTest](../../../backend/patient-service/src/test/java/com/mediflow/patient/web/PatientControllerWebTest.java) | Đã có /patients/{id}/exists trả exists + patientId, service token và test source absence/outage/role. Bỏ blocker “chưa có endpoint Patient”; chưa chứng minh consumer Surgery/E2E. |
| E11 | [StaffController](../../../backend/organization-service/src/main/java/com/mediflow/organization/web/controller/StaffController.java), [StaffLookupDTO](../../../backend/organization-service/src/main/java/com/mediflow/organization/application/dto/response/StaffLookupDTO.java), [DepartmentController](../../../backend/organization-service/src/main/java/com/mediflow/organization/web/controller/DepartmentController.java) | Hiện có staff /exists: exists, eligibleDoctor, departmentId. Chưa /staff/{id}/lookup và /departments/{id}/lookup V2. eligibleDoctor không đủ thay active/jobTitle cho cả ê-kíp. |
| E12 | [Gateway token](../../../backend/gateway/src/main/java/com/mediflow/gateway/security/JwtTokenService.java), [routes](../../../backend/gateway/src/main/resources/application.yml), [RouteAuthorizationFilter](../../../backend/gateway/src/main/java/com/mediflow/gateway/filter/RouteAuthorizationFilter.java) | Explicit staffId/departmentId/patientId đã có; chưa Inpatient/Surgery route. Report GET hiện chỉ ADMIN/MANAGER, chưa quyền DOCTOR cho operations V2. |

**Kết luận:** chưa có bằng chứng D01–D12 hoàn tất end-to-end cho các flow của Huy. D01/D05/D06/D07 đã có một phần domain Surgery, chưa có durable workflow. Pharmacy có context value objects; Report có decoder/harness V1 offline bên cạnh legacy. Không dùng kết luận “chưa có service/V2 nào” để lập lại phần đã làm.

## 3. D01–D12 sau khi đối chiếu spec mới với code

<a id="31-decision-backlog"></a>

### 3.1. Decision backlog cập nhật

SPEC_CHOICE = spec mới đã chọn hướng nhưng không tự chứng minh approval liên owner. PARTIAL = đã rõ một phần, vẫn có gap cụ thể. CONFLICT = các nguồn chưa cùng ý nghĩa. Cột code độc lập với cột spec.

| ID | Điều mới từ spec 2026-09-27 | Code hiện có | Phần còn thiếu / xử lý trong plan |
|---|---|---|---|
| D01 | Surgery candidate chọn cả ADMISSION và OUTPATIENT; admission episode đúng admissionId. | CareEpisode kiểm tra exact admission identity, chưa kiểm chứng referral relationship. | Local mapping đã căn chỉnh candidate theo canonical (appointment khi có lịch, nếu không record); wire producer/Surgery–Billing cho outpatient và shared fixtures vẫn OPEN ở H-01.2. Không suy đoán ID. |
| D02 | Candidate dùng surgeryRequestId unique; Clinical/Inpatient là referral producers, case do Surgery tạo. | Có domain create; chưa persisted case/referral producer. Inpatient đã có outcome consumer. | PARTIAL: thiếu post-case charge fact và reference registration bên Inpatient. Không dùng cùng surgery.requested cho hai producer/ý nghĩa. H-01.2, S-03.1/.6, S-04.2. |
| D03 | Có checklist rows/status/mandatory/evidence và sáu readiness guards. | Chỉ ReadinessSnapshot boolean; chưa checklist/template/evidence correlation. | PARTIAL: Huy chọn template version/snapshot; Vinh còn xác nhận mandatory set, evidence source, NOT_APPLICABLE, expiry/correction. S-05.1, không tick từ Lab result bất kỳ. |
| D04 | Schedule/team DDL, room_reference, guard eligibility và yêu cầu chống overlap đã có. | E11 chỉ doctor lookup cũ; chưa resource booking. | PARTIAL: Huy chọn không tạo room master trùng và interval UTC `[start,end)` không buffer; Organization room source/job-title mapping còn thiếu, DB phải chống tranh slot. H-01.3.3, S-06.1–3. |
| D05 | Prepare PREOP → READY → finalize SCHEDULED → START. | Có domain transitions/invalidate, chưa booking hay dependency revisions. | Huy đã chọn prepare/finalize, invalidation và chỉ reserve khi finalize; S-05.4, S-06.4 hiện thực bằng DB/transaction. |
| D06 | Candidate có typed consent và financial-only override. | Domain AND hai consent booleans; chưa consent records. | Huy chọn override disabled V1; S-07.3 chỉ kiểm chứng không bypass. Consent policy liên clinical vẫn theo H-01.3.3/S-05.2. |
| D07 | Actual itemCode/priceCode/quantity; one result/case; Billing định giá. | Có complete/pre-start-cancel transition; chưa result/outcome persistence. | Partial abort/correction ngoài V1; Billing reconciliation và completed category vs summary còn chờ contract. H-01.2–3, S-07.2/4. |
| D08 | SPEC_CHOICE Pharmacy: v0 compatibility, v1 exact context; một đơn tối đa một slip; ADMISSION cần active projection đúng patient/khoa, không prepaid. | E03–E05: legacy engine đã có; P-02.1 thêm context value objects/flag, chưa V2 persistence/writer. | Không hỏi lại full vs multiple dispense cho V1. Còn medical-discharge eligibility, transfer/freshness/order và cancel/expiry/failure charge adjustment. P-02/P-03; multiple-dose/returns ngoài V1. |
| D09 | Billing target có transactionId/refund original/account/classification, settlement totals; Report có 5 nhóm chỉ tiêu riêng. | E06/E08: legacy invoice/compensation, chưa ledger V2. | PARTIAL: payload thiếu allocated earned/deposit-release/split department và settlement version/supersedes; cash gross/net, period và receivable stock/delta chưa thống nhất. H-01.5, R-03. |
| D10 | Inpatient tách medical discharge và CLOSED; Report target dùng admission.closed cho discharge/LOS, Surgery có actual times/category. | E02 đã có admission facts; Report chưa KPI này, Surgery chưa persisted outcomes. | PARTIAL: chốt tên administrative duration vs medical LOS, close thiếu department phải lấy exact start snapshot; thiếu contract bed transfer/release/capacity cho Report. R-04 không công bố occupancy bằng active admission count. |
| D11 | SPEC_CHOICE: legacy v0 giữ nguyên, nested envelope v1, feature flag false, projections mới chạy riêng và đối soát trước cutover. | Legacy reliability + offline Report V1 decoder/harness đã có; chưa durable V2 journal/replay hoặc Surgery messaging. | Còn selector không double-count, semantic keys/revisions, durable replay source/retention/watermark và projection ledger riêng khi replay. H-01.4–5, P-02.4, R-01. |
| D12 | Exact purpose/target/patient/episode; expiresAt; active admission projection đã có trong target. | Clinical/Lab có clearance consumers; Surgery chưa exact clearance/relationship adapter, Billing chưa Surgery producer. Patient exists đã có. | PARTIAL: grant/revoke/freshness, early delivery, missing dependency, admission close-before-start/transfer. Patient exists không chứng minh admission thuộc patient. P-03.1–3, S-03.2/S-05.3. |

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

Surgery đã bind ba flag mặc định false: `mediflow.features.surgery.enabled`, `mediflow.surgery.messaging.producer.enabled`, `mediflow.surgery.messaging.consumers.enabled`. Hiện chưa có business workflow nên **chưa** chứng minh flag bảo vệ endpoint/worker thật; S-01.6 phải làm điều đó. Pharmacy/Report đã có foundation flag theo các task đã tick; kiểm tra đúng property của từng module khi mở slice. Flag không ngăn Flyway chạy. Không bind rồi ACK mất event khi consumer bị tắt; producer-off phải giữ outbox chưa gửi, không đánh dấu published.

## 5. H-01 — thu hẹp quyết định, khóa hợp đồng đúng phần còn thiếu

**Trạng thái:** IN_PROGRESS; audit/plan là tài liệu đã hoàn tất, contract/approval còn mở.
**Files:** plan này; Huy handoffs đang active; đề xuất chỉnh đúng phần của spec V2/canonical tương ứng, không tạo một bộ wire contract khác trong plan.

- [x] **H-01.1 · NGAY · audit baseline:** đọc code E01–E12, cập nhật D01–D12 và maturity; lưu bản cũ. Acceptance: mọi “đã code” có source, mọi “đã test” có ngày/phạm vi; không lấy tick plan spec làm code evidence.
- [ ] **H-01.2 · CONTRACT · episode/referral/event mapping:** với Vinh/Lộc khóa D01/D02/D07 và ready/completed/cancelled field gaps. Đầu ra: mỗi context có request→case→charge→clearance→result fixture, exact source key, producer, version, consumers và pricing boundary. Test plan: một clinical intent đi qua hai producers vẫn một case/charge; appointment-backed không đổi episode khi record xuất hiện.
  - [x] **H-01.2.1 · DONE/LOCAL:** đối chiếu và ghi trong [handoff G0 chung](../../handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md) các xung đột episode outpatient, producer/referral key, post-case charge bridge, clearance scope và field gaps của ready/completed/cancelled.
  - [ ] **H-01.2.2 · CONTRACT:** Vinh/Lộc/Huy chốt mapping và cùng-version producer/consumer fixtures; handoff còn OPEN cho tới khi canonical contract/spec và test links được cập nhật.
- [ ] **H-01.3 · SURGERY-G0 · policy + external acceptance:** Huy-local V1 defaults đã được chọn theo ủy quyền và có thể dùng cho domain work. Các phần giao cắt vẫn cần Vinh/Lộc/Hoàng Anh xác nhận; không bật wire integration hoặc đánh dấu G0 hoàn tất trước canonical updates/fixtures.
  - [x] **H-01.3.1 · DONE/LOCAL:** lập đủ 8 lựa chọn §14 thành proposal có mặc định an toàn, owner và evidence cần thiết trong [handoff G0 chung](../../handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md); không ghi proposal là approval.
  - [x] **H-01.3.2 · DONE/LOCAL by delegation (2026-09-28):** chọn Huy-owned defaults cho episode, checklist template ownership, typed consent, room reference, team roles, disabled V1 override, pre-start-only cancellation, code/quantity-only items và READY/schedule invalidation; xem [handoff G0 chung](../../handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md). Đây là delegated implementation decision, không giả lập xác nhận cá nhân hay cross-owner approval.
  - [ ] **H-01.3.3 · CONTRACT/OWNER:** Vinh/Lộc/Hoàng Anh xác nhận clinical evidence/consent, episode/referral/charge/clearance/event fields, Organization room/staff lookup và fixtures; cập nhật canonical docs. Handoff vẫn OPEN cho tới acceptance này.
- [ ] **H-01.4 · NGAY/CONTRACT · compatibility matrix:** chốt selector request/event v0-v1; V2-only fields thiếu không downgrade sang v0. Inventory cả created/filled/failed/cancelled/expired, consumers Clinical/Inpatient/Billing/Notification/Report. Acceptance: old outbox bytes không bị rewrite; raw legacy fixture và V1 envelope đều có phiên bản/nguồn rõ.
  - [x] **H-01.4.1 · DONE/LOCAL:** inventory source hiện tại cho đủ 5 Pharmacy events và consumers trong repo. `created`/`dispense.failed`/`cancelled`/`expired` chỉ có Billing; `filled` có Billing, Clinical, Inpatient, Notification và Report. Tất cả producer DTO hiện tại là flat legacy, không có `version`; các fixture legacy hiện hữu được giữ nguyên.
  - [x] **H-01.4.2 · DONE/LOCAL:** đối chiếu V1 harness: Report decoder offline yêu cầu envelope `version=1`, producer và source ID; `prescription.filled` V1 yêu cầu `dispenseId`, trong khi wire legacy hiện chỉ có `prescriptionId`. Decoder chưa bind Rabbit và không thay đổi outbox/consumer legacy.
  - [ ] **H-01.4.3 · CONTRACT:** owner cần chốt request selector V0/V1 và V1 payload/source key cho đủ năm event; V2-only field thiếu phải reject, không fallback. H-01.4.1/2 là audit code-local, không phải producer/consumer contract pass. Handoff: [Huy care-finance consumers](../../handoffs/HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md).
- [ ] **H-01.5 · CONTRACT · Report metric/source contract:** cùng Lộc/Vinh khóa bảng §8: cash gross/net, allocation/recognition/refund scope, settlement revision/snapshot, administrative LOS, operation correction, journal source/retention. Acceptance: expected totals fixture có đủ data thực; unsupported metric trả unavailable, không zero giả.
  - [x] **H-01.5.1 · DONE/LOCAL:** lập gap inventory từ spec V2, canonical contracts và producer code hiện có. Cash/recognition/refund/receivable chưa đủ source facts; administrative LOS/operation correction chưa đủ thống nhất; Report chưa có durable replay journal. Các metric phụ thuộc gap phải unavailable, không project số suy diễn.
  - [ ] **H-01.5.2 · CONTRACT:** Lộc/Vinh xác nhận semantics, source identity/revision/retention và cùng producer fixture có expected totals; H-01.5.1 không thay owner approval. Handoff: [Huy care-finance consumers](../../handoffs/HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md).
- [ ] **H-01.6 · NGAY/OWNER · handoff evidence:** cập nhật các handoff đã đăng ký với tiến bộ từ V2, bỏ blocker Patient endpoint đã có nhưng giữ consumer/test gate; bổ sung cụ thể missing generic Org lookup và Gateway roles. Không đóng handoff khi mới có spec; không viết production ngoài scope.

Phần tài liệu H-01.6 đã cập nhật ngày 2026-09-27 trong hai Huy handoff và registry: candidate Surgery, one-slip admission policy, missing finance/replay fields, generic Org lookup và Gateway role gaps. Checkbox còn mở cho xác nhận/fixture/test từ các owner và retirement đúng acceptance; không phải chưa viết handoff.

## 6. Surgery — backlog code chi tiết của Huy

<a id="surgery-backlog"></a>

**Phạm vi:** chỉ `backend/surgery-service` và docs/contracts do Huy phụ trách. Đường dẫn dưới đây tương đối với module; Java dưới `src/main/java/com/mediflow/surgery`, tests dưới `src/test/java/com/mediflow/surgery`. Tên lớp mới là đầu ra cần tạo, không phải tuyên bố source đã tồn tại.

**Đã có:** platform, domain models/rehydration, V1 core migration, JPA/JDBC adapters, receipts/inbox/outbox persistence và Patient lookup; lượt hiện tại bổ sung command receipt helper, begin-preop/checklist/consent application paths và released-schedule draft revision. **Chưa có:** business controllers, prepare-schedule use case/API, readiness engine, referral/charge/clearance integrations, Rabbit dispatcher và Organization live adapter. `ReadinessSnapshot` vẫn không tự chứng minh clinical/Organization/finance authority.

**Cách đếm gần nhất:** số lượng 62 subtask/nhóm trạng thái tại 2026-09-28 là ảnh chụp lịch sử và chưa được tính lại sau các lượt triển khai tiếp theo. Các task OPEN gồm LOCAL, CONTRACT, OWNER và VERIFY; **không** có nghĩa chúng đều độc lập hoặc là tính năng mới. Trạng thái hiện hành được ghi theo từng child ID và evidence §10.

### 6.0. Quy ước triển khai và cách lấy task

Mỗi subtask phải có: production output đúng layer → rule/transaction → test âm và retry/race liên quan → evidence ở §10. Thêm class rỗng hoặc viết test chưa chạy không đóng task. Nếu một subtask có nhánh LOCAL và CONTRACT, chỉ tick parent khi cả nhánh nằm trong V1 đã đạt; ghi phần đã xong bằng child ID/evidence.

| Lát cắt | Có thể làm độc lập | Chỉ chờ đúng phần nào |
|---|---|---|
| Model + persistence + reliability | S-02.1.2/.4/.5, S-02.2–6 theo thứ tự dependency; Patient adapter S-03.3.1 | Không chờ Billing/Gateway cho unit/PG/MQ tests nội bộ. Chưa đóng G1 bằng fixture tự viết. |
| Create/query + checklist/consent + draft schedule | S-04, S-05.1/.2, S-06.2 sau nền lưu trữ; test bằng port doubles | Referral/relationship proof, catalogue y khoa, quyền consent và Organization eligibility trước khi bật các command tương ứng. |
| READY + finalize + START | Engine/revision/transaction/race có thể code LOCAL sau models | Exact clearance, evidence freshness, role matrix và active room/staff lookup phải đạt G1 trước workflow thực. |
| COMPLETE/CANCEL + event delivery | Local outcome/audit/resource-release tests sau S-07 prerequisites | Wire outcome/charge, Inpatient reference, Billing reconciliation, Notification/Report fixtures trước delivery thật. |
| Root/Compose/Gateway và bật tích hợp | Huy cung cấp cấu hình, requests, test evidence | S-01.4/X-01 do shared owners; không chặn module-local implementation. |

**Quyết định kỹ thuật local cho backlog này — chưa phải implementation:**

- Giữ domain thuần Java; command in-port, repository/lookup/clock/event out-port ở application. Domain event nội bộ không phải wire event; serializer wire chỉ có sau G1.
- Dùng row lock ở PostgreSQL cho case và từng resource; không chọn lease/TTL giữ chỗ trong V1. Resource lock row là mutex kỹ thuật chứa UUID tham chiếu, **không** là bản sao room/staff master. Chi tiết S-06.3.
- Tách business revision `phienBan` khỏi technical optimistic-lock version; mọi mutation làm thay đổi readiness dependency phải tăng revision tương ứng. Không vừa domain increment vừa JPA tự increment cùng một cột.
- HTTP mutation cần command receipt: `Idempotency-Key` và expected case revision (`phienBanKyVong` trong DTO sau khi cập nhật API spec). Khóa receipt theo actor/operation/case; create dùng stable `surgeryRequestId` để dedupe cả HTTP/event. Xác thực/quyền trước replay; receipt cùng payload được replay trước kiểm tra state/revision mới. Cùng key khác payload → conflict, không gọi lại external side effect.
- Mốc thời gian do server `Clock` ghi khi command được chấp nhận; retry giữ nguyên kết quả/thời gian. Actual surgical times là dữ liệu nghiệp vụ riêng, phải validate và audit, không thay `occurredAt` bằng thời gian client tùy ý.
- V1 không emergency override, post-start abort, result correction, tính giá, TTL tự giải phóng ca đang mổ hoặc UI quản trị catalogue mới. Không tạo API success giả cho chức năng chưa tích hợp.

### S-01 — platform đã có, bảo vệ activation và test harness

**Đầu vào:** foundation hiện hữu. **Đầu ra:** module build/test độc lập; flags chặn được workflow thật; bootstrap shared có acceptance riêng. Không dựng lại module.

- [x] **S-01.1 · DONE/LOCAL:** module/POM kế thừa dependency versions, package blueprint, port 8091/DB `mediflow_surgery`; không endpoint placeholder.
- [x] **S-01.2 · DONE/LOCAL:** DB/Rabbit/Eureka/JWT/correlation/config properties và test profile foundation đã có.
- [x] **S-01.3 · DONE/LOCAL:** JWT human access-token, account UUID tách staff identity, authentication/default-deny và error handling foundation. Chưa có business controller nên chưa chứng minh role từng command.
- [ ] **S-01.4 · OWNER:** [bootstrap handoff](../../handoffs/HANDOFF-SURGERY-FOUNDATION-BOOTSTRAP.md) cho root module, DB, Compose, Gateway. Acceptance: root reactor có module, DB riêng, health/service discovery, route + role từng endpoint; Huy chỉ đóng khi có evidence owner. Không tự sửa bốn phạm vi shared này.
- [x] **S-01.5 · DONE/LOCAL:** suite module pass 89 tests (0 failure/error/skip; 2026-09-29), gồm PostgreSQL Testcontainers; không phải Rabbit/Gateway/root-reactor hoặc G3 evidence.
- [ ] **S-01.6 · LOCAL cùng S-04/S-03/S-02.5:** nối ba flag vào business adapters/command activation, listener registration và outbox dispatcher. Khi surgery disabled không mở mutation; consumer disabled không nhận rồi ACK bỏ event; producer disabled giữ outbox pending. Test context cho tổ hợp flag, không tự bật bằng profile production/dev mặc định.
- [ ] **S-01.7 · PARTIAL/LOCAL sau từng slice:** ArchUnit chặn domain → application/infrastructure/I/O, application → adapter/JPA/web/messaging và layer cycles; PostgreSQL 16 Testcontainers đã có schema/JPA/reliability/race tests. Còn Rabbit fixture, reuse fixture, module Dockerfile và README/.http **chỉ khi** có API thật; không phụ thuộc root registration để chạy suite module.

### S-02 — domain, schema và reliability foundation

**Đầu vào:** S-01, Huy-local choices H-01.3.2. **Không phải đợi toàn bộ S-03.** Schema core không phụ thuộc clearance wire chưa chốt; phần contract-specific thêm migration tiếp theo, không sửa checksum đã phát hành.
**Files:** `domain/model` (gồm enums), `domain/exception`, `application/port/out`, `infrastructure/persistence` (entity/repository/mapper/adapter theo blueprint), `infrastructure/messaging`, `src/main/resources/db/migration`. Không thêm cây package khác blueprint.

- [ ] **S-02.1 · LOCAL/CONTRACT — hoàn thiện aggregate và value objects:** từng child dưới đây có unit tests; không kéo JPA/validation annotations vào domain.
  - [x] **S-02.1.1 · DONE/LOCAL:** CareEpisode exact admission identity, initial lifecycle, boolean readiness AND, START recheck, invalidation, pre-start cancel; 13 domain tests. Chưa có readiness evidence/persistence/result invariant.
  - [x] **S-02.1.2 · DONE/LOCAL (2026-09-28):** `SurgeryChecklistTemplate/ItemDefinition/Snapshot/Item` immutable; template revision được chụp vào case, empty template/snapshot không hợp lệ, NOT_APPLICABLE không thỏa mục bắt buộc, không seed catalogue y khoa. `SurgeryConsentRecord` giữ hai loại consent và append-only sign/revoke audit; signer category chỉ là dữ liệu, chưa xác minh thẩm quyền. `SurgerySchedule/TeamAssignment` giữ room UUID, revision, `[start,end)`, duplicate staff bị từ chối và slot liền kề không overlap. Tests `SurgeryLocalModelsTest`; không có Organization lookup/reservation DB.
  - [ ] **S-02.1.3 · CONTRACT D12 — clearance value object:** sau S-03.2.1 khóa tuple/version/expiry, giữ source grant/revision và validity, phân biệt absent/pending/expired/revoked. Kiểm tra wrong target/episode/patient và out-of-order grant; không coi payment.completed hoặc boolean paid là clearance. Engine có thể test với port double trước khi decoder live tồn tại.
  - [x] **S-02.1.4 · DONE/LOCAL (2026-09-28):** `SurgeryCase.restore` khôi phục state, mốc thời gian, readiness, status history, business revision và append-only revision audit; validate chuỗi transition/revision/status để từ chối DB snapshot không nhất quán, không sinh lại history khi restore. `recordBusinessMutation` tăng business revision và audit khi application đổi child. Tách business revision khỏi technical JPA `@Version` (chưa có adapter). `ReadinessSnapshot` giữ dependency IDs/revisions, validUntil và lý do thiếu guard; expiry đúng boundary làm snapshot hết hiệu lực. `SurgeryAuditActor` tách account UUID, staff UUID đã được adapter xác thực, hoặc producer hệ thống; transition/consent audit mang correlationId, không tạo UUID giả. Tests round-trip, revision child mutation, corrupted history, dependency/reason/expiry.
  - [x] **S-02.1.5 · DONE/LOCAL (2026-09-28):** `SurgeryPerformedItem/SurgeryResult` bất biến; actual end phải sau start và thời điểm ghi nhận không trước khi kết thúc; procedure/method/outcome/category là code string, số lượng dương, line ID duy nhất. Không có amount hoặc chức năng correction/partial-abort. Kết quả mang actor/correlation audit. Tests invalid quantity/time/duplicate line; unique result/case còn cần unique constraint ở S-02.2.
- [x] **S-02.2 · DONE/LOCAL (2026-09-28) — core migration:** V1 đã có case/history, checklist/consent, schedule/team/resource mutex/reservation, readiness/result, receipt/inbox/outbox và mapping JPA English camelCase ↔ SQL English snake_case theo ngoại lệ Huy trong `docs/ai/08`. Fresh PostgreSQL 16 migrate + Hibernate validate + constraint tests pass, gồm cancelled actor/reason, active consent, result uniqueness/quantity, schedule interval. Không seed cross-service master/paid facts. Clearance-specific schema chỉ thêm migration mới sau contract; không sửa V1 khi đã phát hành.
- [ ] **S-02.3 · PARTIAL/LOCAL sau S-02.2 — ports/JPA/transaction boundary:** case repository/JPA mapper `restore`, checklist/consent/result child persistence, schedule histories và begin-preop receipt transaction đã có PostgreSQL 16 round-trip/revision/replay/rollback tests. Port không lộ entity. Còn use-case transaction orchestration cho các command khác, concurrent first-referral retry/load-winner, cross-command lock protocol, bounded retry và crash/failure matrix; PageQuery/PageResult chỉ thêm khi query có yêu cầu. Technical version conflict → retry/conflict rõ, không lost update. Transaction phải bao case + child mutation + history + receipt + approved outbox intent.
  - [x] **S-02.3.1 · LOCAL PG PASS (2026-09-29):** immutable template/snapshot create+read; snapshot khởi tạo revision 0/PENDING, khóa case khi gắn, đối chiếu từng item với đúng template revision. Integration test `checklistTemplateSnapshotAndItemHistory_roundTripWithOptimisticRevision` chạy trên PostgreSQL xác nhận round-trip template/snapshot và lưu item history với revision tăng; stale revision bị từ chối, không nhân đôi history. Workflow checklist/receipt được kiểm chứng riêng ở S-05.1.1; evidence/source policy vẫn mở.
  - [x] **S-02.3.2 · LOCAL PG PASS (2026-09-29):** consent sign/revoke persistence, append-only audit, same-command readback, case lock và ACTIVE comparison; PostgreSQL test xác nhận duplicate active bị constraint chặn, audit SIGNED/REVOKED còn nguyên, re-consent sau revoke được lưu. Signer authority/revoke authorization vẫn chờ Vinh ở S-05.2.2.
  - [x] **S-02.3.3 · LOCAL PG PASS (2026-09-29):** immutable result + performed-item adapter; chỉ create khi IN_PROGRESS, lưu actual times/actor/correlation/items cùng transaction, identical replay trả lại kết quả, payload thay thế bị conflict. PostgreSQL test xác nhận hai performed items được đọc lại. COMPLETE/outbox orchestration vẫn ở S-07.2.1.
  - [x] **S-02.3.4 · LOCAL PG PASS (2026-09-29):** đọc từng schedule revision cùng đúng staff assignments từ history; integration test xác nhận revision cũ/mới giữ team riêng và reservation cũ vẫn RELEASED. Cross-command lock/release orchestration còn ở S-06.4/6.5.
  - [x] **S-02.3.5 · LOCAL UNIT+PG PASS (2026-09-29):** fingerprint SHA-256 length-prefixed, versioned binary receipt codec, replay identity checks; begin-preop claim/mutation/status history/case save/receipt cùng transaction. PostgreSQL test chứng minh rollback cả case và receipt khi lỗi trước outer commit, rồi commit + same-key replay chỉ có một receipt APPLIED. Race giữa concurrent claim và các failure points khác vẫn cần kiểm chứng.
- [ ] **S-02.4 · PARTIAL/LOCAL sau S-02.2 — inbox và command receipts:** durable adapter, fingerprint/bytes + semantic dedupe/quarantine, pending defer và receipt replay/conflict đã có PostgreSQL tests. Còn business consumer/authorization trước replay, early event→case creation→reprocess, consumer race/ACK và crash matrix. Không xóa pending vì quá hạn retry mà không quarantine/audit.
- [ ] **S-02.5 · PARTIAL/LOCAL sau S-02.2 — outbox:** lưu serialized bytes, claim lease + attempt fencing, causal order, bounded backoff/quarantine, expired-lease recovery và returned retry đã có PostgreSQL tests. Còn generic dispatcher, Rabbit confirm/return ordering, real broker tests và approved G1 wire bytes. Không gọi Rabbit trong case DB transaction hoặc import code Pharmacy xuyên module.
- [ ] **S-02.6 · PARTIAL/VERIFY theo từng adapter — PG/MQ failure matrix:** đã pass fresh schema + ORM restore, receipt/outbox rollback, inbox replay và lease cũ, child checklist/consent/result rows + audit rollback cùng transaction, two-worker same-room và reversed-team-order races, adjacent/contained slot, IN_USE overrun. Còn concurrent first-referral insert, Rabbit lost confirm/ACK, restart reprocess, cancel/reschedule terminal races và bounded deadlock retry; không suy suite subset là hoàn tất task.

### S-03 — identity lookup, referral, clearance và event contracts

**Đầu vào:** Patient adapter có thể bắt đầu từ S-01; generic decoder/fixture harness có thể làm cùng S-02. Chỉ adapter gắn business effect mới cần persistence tương ứng. Mỗi contract đạt G1 riêng, không đợi tất cả service cùng live.
**Files:** `application/port/in|out`, `application/event`, `infrastructure/client`, driving consumers ở `messaging/consumer`, driven publisher/payload ở `infrastructure/messaging`, `src/test/resources/contracts`.

- [ ] **S-03.1 · CONTRACT D01/D02 — referral và charge bridge:** khóa producer/path, selected episode, stable surgeryRequestId, requester authority, planned codes/quantities và fingerprint clinical intent. HTTP/event cùng gọi create in-port; delivery timestamp/correlation không làm đổi intent. Referral khác post-case charge fact; không tự đặt event name/routing key hoặc để hai producer cùng tạo charge. Acceptance: outpatient appointment/walk-in/admission, same intent từ hai paths, mismatch và duplicate fixture; ghi version/producer/consumers.
- [ ] **S-03.2 · LOCAL + CONTRACT D12 — clearance:** hai bước riêng:
  - [ ] **S-03.2.1 · CONTRACT Lộc/Vinh:** khóa `purpose=SURGERY`, exact target/episode/admission/patient, grant ID/revision, validity interval, revoke/supersede và freshness semantics. Chưa có revoke producer không tự tạo subscription/routing key. Đưa policy expiry/revoke/late grant và test bytes vào canonical contract.
  - [ ] **S-03.2.2 · LOCAL sau S-03.2.1/S-02.4:** decoder reject sai type/producer/version; purpose khác được classified not-applicable, SURGERY thiếu target là invalid. Early valid target → durable pending; mismatch không chuyển sang latest case. Old grant không hồi sinh quyền sau revoke/new revision; expiry theo Clock tại READY/START. Test absence, before/at/after expiry, out-of-order và replay.
- [ ] **S-03.3 · LOCAL + CONTRACT — REST adapters có resilience, không DB join:**
  - [x] **S-03.3.1 · DONE/LOCAL (2026-09-28) — Patient:** adapter dùng contract thật `/patients/{id}/exists`, service JWT ngắn hạn `type=service`/`role=SYSTEM`, correlation và Feign timeouts. Validate envelope/echo patientId; false/404 phân biệt với timeout/5xx/malformed/mismatch. Unit + HTTP stub tests pass; chưa gọi qua Gateway/prod deployment.
  - [ ] **S-03.3.2 · LOCAL port/stub; CONTRACT adapter live — Organization:** port trả typed room/staff/department lookup state + source/time/revision nếu contract có. Tách inactive/absent/unknown/outage; không map doctor-only `eligibleDoctor` thành quyền mọi ê-kíp. Hoàng Anh cung cấp generic lookup, room authority, job-title values; Vinh chốt required role/cardinality và assignment policy. Không triển khai lookup giả trong module khác.
    - [x] **S-03.3.2.1 · LOCAL UNIT PASS / LIVE CONTRACT OPEN (2026-09-29):** Surgery-local `OrganizationLookupPort` và typed snapshot cho ROOM/STAFF/DEPARTMENT (ACTIVE/INACTIVE/NOT_FOUND/UNKNOWN, observedAt, optional source revision/job-title code); tests xác nhận mọi state hợp lệ, normalization metadata và fail-fast cho identity/observation/job-title sai. Không định nghĩa HTTP path, producer fields hay live adapter; adapter chỉ mở sau contract owner.
  - [ ] **S-03.3.3 · CONTRACT Vinh — care relationship:** exact referral/admission belongs-to patient/department và eligibility state/freshness từ producer-authoritative fact hoặc service-auth lookup được thống nhất. Patient exists/human GET admission không thay được proof này. Unit/contract tests sai patient/khoa/context, closed/discharged admission và upstream outage; không tìm admission qua patientId.
- [ ] **S-03.4 · CONTRACT D02/D07/D11 — outbound manifest:** mỗi charge/ready/completed/cancelled fact ghi schema, source/semantic key/revision, consumer matrix và SHA-256 fixture. READY mang immutable planned-time/schedule revision nhưng **chưa phải reserved SCHEDULED**; chốt với Notification cách biểu diễn provisional, invalidation/re-ready/reschedule. Completed tách controlled category khỏi clinical summary; aggregate Report không persist/log narrative. Nếu cần hạn chế transport summary, chốt version/channel cùng owner trước publish, không tự fork payload cùng version. Cancelled đủ department/episode/stage/operation identity; V1 không phát override/partial-abort/correction facts.
- [ ] **S-03.5 · VERIFY từng contract:** producer serialization và consumer decode cùng raw bytes/hash/commit; fixtures valid/missing/null/wrong IDs/producer/unknown version, semantic duplicate có eventId mới, early/late/order reversal. Invalid/unsupported được classified, bounded retry/quarantine/DLQ; transient outage retry khác permanent malformed. Log correlation + safe source IDs, không payload lâm sàng/token. Không tick G1 bằng fixture tự viết hoặc decoder test đơn phía Huy.
- [ ] **S-03.6 · CONTRACT/OWNER Vinh — nối với consumer Inpatient đã có:** thống nhất đường đăng ký external-order reference `SURGERY ↔ surgeryCaseId ↔ admissionId ↔ surgeryRequestId` trước ready/completed/cancelled. Current consumer bắt buộc admissionId/reference; cần fixture OUTPATIENT → not-applicable thay vì malformed, late fact sau discharge, duplicate/out-of-order terminal và reference chưa tới. Vinh sửa consumer/reference path; Huy cung cấp source fixture/negative tests trong [handoff G0](../../handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md). Không tạo thêm event “case-created” tự phát hoặc ghi vào DB Inpatient.

### S-04 — create/referral, query và bắt đầu pre-op

**Đầu vào:** core persistence S-02.2/.3/.4, template snapshot S-02.1.2; identity ports S-03.3. Local application/web tests dùng doubles; command thật còn chờ S-03.1/.3 authority/charge contract. Không đợi READY/booking hoàn chỉnh mới viết create/query.
**Files:** command/query DTO records, create/get/list/begin-preop in-ports, application services/mappers, repositories, driving HTTP adapters ở `web` theo blueprint (không `infrastructure/web`).

- [ ] **S-04.1 · LOCAL + CONTRACT — input/authorization boundary:** phân biệt HTTP actor từ trusted access-token và system referral producer từ envelope; account UUID không phải staff UUID. ADMIN delegation phải explicit/audited, DOCTOR requester theo policy/claim; không body-supplied actor/role. Validate selected episode và procedure/template revision, planned items codes/positive quantities, authority proofs qua S-03.3. Appointment-backed giữ appointment episode ngay cả khi record xuất hiện. Lookup ngoài lock, commit kiểm tra lại local revision/freshness; upstream unavailable không success.
- [ ] **S-04.2 · LOCAL sau S-04.1 — transactional create:** claim stable surgeryRequestId, fingerprint business intent, tạo case REQUESTED + pinned checklist snapshot + creation history + receipt và approved charge outbox intent trong một transaction. Hai channel/replica đồng thời cùng intent trả cùng case; khác intent → conflict. Chưa khóa charge wire thì chỉ kiểm thử local domain intent, **không bật create thật với nhánh âm thầm bỏ charge**. Sau commit kích pending-event retry qua durable worker, không in-memory callback làm nguồn duy nhất. Acceptance: rollback mọi record khi một append thất bại.
- [ ] **S-04.3 · LOCAL sau S-04.2 — read/query:** chốt và test object/department visibility matrix trong API spec, áp dụng cùng policy cho detail/list; không suy quyền sở hữu case chỉ từ filter hoặc department claim. Detail trả IDs/context/state/revision, thiếu readiness gì, planned khác actual, consent/checklist summary, result theo quyền; không trả chữ ký/raw evidence/narrative ở list. Filter explicit khoa/status/requested-period hoặc scheduled-period, UTC inclusive start/exclusive end, page 0/size 20/max 100; stable sort requestedAt + caseId. Repository projection/pagination không REST fan-out hay N+1 load full histories; read không mutate, empty list không phải upstream error. Test cross-department/ID tampering không lộ case trái scope đã chọn.
- [ ] **S-04.4 · VERIFY — create/read/web:** same key same/different payload; event/HTTP first-insert race; same patient khác episode; wrong association; template chưa được phê duyệt; failure outbox rollback. Web tests success/envelope/validation, unauthenticated/forbidden/not-found/conflict và upstream unavailable theo API conventions; assert 401/403 không chạy use case. Idempotent replay vẫn kiểm tra quyền và không gọi lại lookups/charge producer.
- [x] **S-04.5 · LOCAL TEST PASS / API VERIFY OPEN:** begin-preop `REQUESTED→PREOP_IN_PROGRESS` yêu cầu actor HUMAN + expected revision; unit và PostgreSQL test xác nhận same-key replay, status/history/receipt atomic commit, rollback trước commit và chặn system actor trước side effects. Chưa có controller/.http/Gateway; evaluate-readiness, schedule-finalize và consent-revoke vẫn chỉ là route proposal; mọi business flag giữ false.
- [ ] **S-04.6 · LOCAL sau S-04.3/.5 — command API conventions:** chuẩn hóa Idempotency-Key/expected revision, conflict vs invalid transition vs upstream unavailable; immutable request/response DTO với English camelCase trên Java và wire theo `docs/ai/08`. Missing resource không thành success/null; retry cùng key trả cùng domain outcome. Chốt status/envelope và cập nhật .http đúng API đã chạy; không expose state setters/JPA entities hoặc nhận ready=true.

### S-05 — pre-op checklist, consent, clearance và readiness thật

**Đầu vào:** S-04 create/preop, models/persistence S-02; draft schedule S-06.2, không phụ thuộc finalize S-06.4. Có thể code pure policies/commands local trước fixtures; chỉ rule do owner khác sở hữu mới chờ CONTRACT.
**Files:** checklist/consent/clearance/readiness models, policies, command/query services, repositories/ports, web DTOs và immutable evidence snapshots.

- [ ] **S-05.1 · LOCAL + CONTRACT — checklist evidence:**
  - [x] **S-05.1.1 · LOCAL UNIT+PG PASS / CONTRACT OPEN:** item revision, snapshot revision, append-only `preop_checklist_item_history`, optimistic update theo item/snapshot/case revision và receipt cùng transaction; chỉ đổi ở REQUESTED/PREOP hoặc invalidate READY/SCHEDULED; N/A fail-closed khi chưa có clinical policy. PostgreSQL test xác nhận item/snapshot revision + history và stale write không tạo history trùng; unit tests xác nhận receipt path khi ghi FAILED và fail-closed cho N/A. Acceptable evidence/source policy (Vinh), unknown/duplicate/cross-case matrix và rollback ở workflow checklist vẫn mở.
  - [ ] **S-05.1.2 · CONTRACT Vinh; LOCAL policy engine:** medical mandatory set, source/order/result revision, expiry/correction và NOT_APPLICABLE authority có fixture riêng. Manual attestation chỉ cho item/policy cho phép, không biến mọi item thành nút tick. Kết quả Lab/Pharmacy cần exact case/order/patient/episode; thiếu correlation/missing/stale/corrected evidence → not-ready. Không auto-complete checklist từ result event chung; consumer chỉ bind sau G1.
- [ ] **S-05.2 · LOCAL + CONTRACT — typed consents:**
  - [x] **S-05.2.1 · CODE COMPLETE/VERIFY OPEN (2026-09-28):** thêm application sign/revoke commands theo SURGERY/ANESTHESIA, actor recorder tách signer, same-key replay và payload conflict; active unique index vẫn là hàng rào; revoke append audit. READY/SCHEDULED consent change invalidates snapshot và release đúng schedule revision trong transaction. Unit tests xác nhận typed signer/recorder, từ chối sai case và giải phóng đúng schedule revision khi SCHEDULED; signer authority/revoke authorization vẫn fail-closed ở API gate đến S-05.2.2.
  - [ ] **S-05.2.2 · CONTRACT Vinh:** chốt signer/guardian/witness, document/evidence reference, expiry, ai được attestation/revoke và xử lý audit sau START. Coarse controller role không thay quyền ký hoặc quan hệ người giám hộ. Missing/unverifiable authority fail-closed; test forged actor/role, signer không hợp lệ, cross-case revoke, inactive/expired consent. Không suy đồng ý từ clinical note hoặc một consent dùng cho hai type.
- [ ] **S-05.3 · LOCAL sau S-03.2/S-02.1.3 — clearance projection/policy:** lưu exact source grant/target tuple/revision và effective/expiry; grant/revoke/expire invalidates readiness đúng case theo policy. Guard dùng Clock tại evaluation và START, không chỉ lúc nhận event. Pending early grant không đủ quyền mổ, grant cũ không revive superseded/revoked one. V1 không financial override. Recheck freshness theo contract; local DB lock **không** hứa khóa trạng thái Billing ở service khác.
- [ ] **S-05.4 · LOCAL — evaluate/invalidate:** engine độc lập; READY transaction cần S-05.1–3/S-06.2. Shared invalidation helper hiện được gọi từ checklist/consent; test mới xác nhận checklist mutation khi SCHEDULED trả case về PREOP, xóa active snapshot và nhả đúng schedule revision. Invalidation race/rollback tích hợp với mọi writer vẫn mở. Invalidation helper .3 có thể làm ngay sau core persistence/resource locks, không đợi READY evaluator, nên draft schedule không tạo vòng dependency.
  - [ ] **S-05.4.1 · LOCAL engine — sáu nhóm guard:** chỉ định hợp lệ, mandatory checklist, cả hai typed consents, eligible team, room/time schedule hợp lệ, exact finance đều đạt (bảy boolean hiện tại vì consent tách hai loại). Build reason codes + dependency revisions, validUntil=min expiry có nghĩa, không TTL tự nghĩ cho dữ liệu không có contract. External eligibility snapshots lấy trước transaction; lock/re-read local dependencies để loại stale evaluation. Không public API “set READY”.
  - [ ] **S-05.4.2 · LOCAL persistence; CONTRACT event — READY:** PREOP_IN_PROGRESS→READY, immutable snapshot + time/history/receipt + ready fact cùng transaction sau khi đủ guards. Cùng dependencies/evaluation retry không phát lại; readiness revision mới sau invalidation là fact mới theo S-03.4. Thiếu guards trả structured not-ready reasons, không làm case READY một phần.
  - [ ] **S-05.4.3 · LOCAL sau S-02.3/S-06.3 — invalidation dùng chung:** checklist/consent/clearance/team/room/time đổi làm READY hoặc SCHEDULED→PREOP_IN_PROGRESS; giữ snapshot/history cũ, clear active snapshot, release đúng reservations dưới resource locks S-06.3. Expiry worker quét bounded batch theo validUntil và dùng cùng command/lock protocol; READY/START vẫn tự check Clock, không phụ thuộc worker kịp chạy. Failed validation không mutate; nếu phát hiện snapshot đã stale và cần invalidate, transaction commit outcome này trước adapter map lỗi — không throw để rollback mất invalidation. IN_PROGRESS không hồi quy PREOP hoặc tự thả tài nguyên; post-start evidence handling theo clinical policy, không biến thành abort tự động. Invalidation notification wire vẫn theo S-03.4, không tự đặt event key.
- [ ] **S-05.5 · VERIFY — truth table + races:** mỗi boolean false riêng (đặc biệt thiếu ANESTHESIA consent), nhiều missing reasons, foreign-case snapshot, exact expiry boundary, stale dependency revision, repeated evaluation. PG race evaluate↔checklist/revoke/reschedule, START↔revoke/expiry, invalidation↔finalize; assert state/snapshot/history/booking/outbox cùng một kết quả hợp lệ. External source không có revision/freshness contract thì ghi integration gate chưa đạt, không gọi local race test là distributed guarantee.

### S-06 — draft schedule, resource locks và finalized booking

**Đầu vào:** case/preop + schedule models S-02/S-04. **Thứ tự không vòng lặp:** S-06.1/.2 draft → S-05.4 READY → S-06.4 finalize. S-06.3 resource lock engine làm ngay sau S-02.2, không cần Organization chạy thật.
**Files:** Schedule/Team/ResourceReservation models, scheduling ports/services, resource-lock/reservation JPA adapters, schema/indexes và schedule web endpoints.

- [ ] **S-06.1 · LOCAL defaults + CONTRACT eligibility:** ghi rõ UTC half-open `[start,end)`, no implicit buffer, only finalized reservation, no TTL release. Room UUID do authoritative owner; staff role enum local đã chọn, nhưng role↔jobTitle/required cardinality/allowable multiple assignments do Vinh/Hoàng Anh xác nhận. Test không dùng ADMIN/DOCTOR login role để suy chuyên môn; unknown role mapping/room state fail-closed.
- [ ] **S-06.2 · PARTIAL/LOCAL sau S-02.1.2/S-04.5 — prepare schedule:** draft revision/room/time/team + append history persistence đã có round-trip PostgreSQL test; PREOP-only, không reserve khi save. Còn Organization eligibility evidence/required role policy, PUT use case/API và contract tests authoritative echo-ID. Lookup timeout/5xx là unavailable, inactive/absent/ineligible là typed rejection; không giữ DB lock qua HTTP.
  - [x] **S-06.2.1 · LOCAL PG PASS / CONTRACT OPEN:** schedule adapter cho phép ghi revision draft mới sau `RELEASED`, giữ nguyên history schedule/team và không tái sử dụng reservation cũ. PostgreSQL integration test xác nhận lịch sử team từng revision và reservation cũ còn RELEASED; chưa có prepare use case/API vì Organization room/job-title/eligibility contract còn OPEN.
- [ ] **S-06.3 · LOCAL sau S-02.2 — database resource-lock engine:**
  - [ ] **S-06.3.1 · PARTIAL/LOCAL — schema/lock protocol:** mutex unique ROOM/STAFF + sorted union old/new keys, case row lock trước resource lock đã có trong adapter và schema. Reservation mang `scheduleId + revision`; stale release bị chặn, không thả booking mới. PostgreSQL two-worker test mới gửi cùng hai staff theo thứ tự đầu vào ngược nhau; chỉ một case thắng và nhận đủ ba reservation. Còn áp dụng protocol này ở mọi finalize/reschedule/invalidate/cancel/complete/start command; reference UUID không external FK hoặc room master.
  - [ ] **S-06.3.2 · PARTIAL/LOCAL — overlap + active use:** query half-open overlap cho room + từng staff, IN_USE overrun chặn ca kế dù planned end đã qua; PostgreSQL tests cho same-room race, cross-room same-staff, adjacent, fully contained room interval và overrun pass. Còn full multi-staff overlap matrix, bounded retry lock timeout/deadlock và command receipt trong finalize orchestration.
- [ ] **S-06.4 · LOCAL sau S-05.4/S-06.3 — finalize/reschedule/release:**
  - [ ] **S-06.4.1 · LOCAL — finalize:** explicit command chỉ READY + exact schedule/readiness/dependency revisions. Revalidate eligible/fresh và slot dưới locks; tạo reservations cho room + toàn team, READY→SCHEDULED + audit/receipt atomic. Cạnh tranh thua không để partial team booking. Không gọi ready event thành bằng chứng đã reserve.
  - [ ] **S-06.4.2 · LOCAL — reschedule:** verify replacement draft/authority trước commit; khi thành công, invalidate về PREOP, release old bookings và lưu new draft revision atomically, phải READY/finalize lại. Đây **không** phải đổi booking ngay rồi vẫn giữ SCHEDULED. Bất kỳ validation/DB failure giữ nguyên schedule/snapshot/booking cũ. Cancel/invalidation chỉ release đúng case+schedule revision, không xóa lịch sử hoặc reservation của case khác.
- [ ] **S-06.5 · VERIFY — real PostgreSQL two-worker tests:** same room cùng/partial/contained interval, khác room cùng staff, adjacent allowed, nhiều staff reverse order, concurrent first mutex insert, finalize↔cancel, reschedule↔finalize và rollback. Một winner với full resources, không partial booking; no deadlock không giới hạn; stale release không đụng new revision. START next case bị chặn khi previous case overrun; chỉ COMPLETE release IN_USE trong V1.

### S-07 — start, complete, pre-start cancel; không override V1

**Đầu vào:** persisted guards/schedule S-05/06; receipt/history/outbox S-02. Local command tests không chờ consumer runtime, nhưng live delivery chờ S-03.4/.6 và G1.
**Files:** lifecycle in-ports/DTO/services, result/items and cancellation persistence, web controllers, event mappers và end-to-end module tests.

- [ ] **S-07.1 · LOCAL sau S-05.4/S-06.4 — START:** chỉ SCHEDULED, exact expected revision/receipt. Lấy authoritative snapshots theo freshness policy ngoài lock, sau đó case/resources lock + re-read consent/checklist/clearance/schedule revisions và Clock; reservation phải thuộc đúng case/revision, room/staff không IN_USE bởi case khác. IN_PROGRESS + start time/audit + resource IN_USE atomic. Retry giữ start time; START từ READY, expired grant hoặc revoked anesthesia bị chặn. Failed guard xử lý invalidation theo S-05.4.3, không trả success hoặc mất audit do rollback.
- [ ] **S-07.2 · LOCAL + CONTRACT — COMPLETE:**
  - [ ] **S-07.2.1 · LOCAL sau S-02.1.5/S-07.1:** validate IN_PROGRESS, actual times/recorded time, actual procedure/method/outcome/category, one result/case và positive performed quantities. Lưu result/items + COMPLETED + history/receipt + release reservations/IN_USE + approved completed outbox atomically. Sửa result/second completion khác payload conflict; không tạo correction ngầm.
  - [ ] **S-07.2.2 · CONTRACT Lộc/Vinh:** chốt item identity khi nhiều item dùng cùng priceCode, planned↔actual reconciliation, supported catalogue và clinical summary/category. Không tự gộp line làm mất nghiệp vụ, tính tiền, sửa invoice, gọi adjustment là refund hoặc emit zero-price khi mã chưa biết. Fixture consumer Billing/Inpatient/Report cùng operation/source identity; Report không lưu clinical narrative.
- [ ] **S-07.3 · LOCAL — chứng minh không financial override trong V1:** không endpoint/DTO flag/role shortcut hoặc fallback cho phép thiếu clearance; ADMIN cũng không bypass. Tests request tự khai approver/override/paid không thể READY/START, missing consent luôn block. **DEFERRED V1:** FINANCIAL_EMERGENCY implementation (approver/self-approval/expiry/receivable) chỉ mở bằng task phiên bản sau với Vinh/Lộc; không giữ nó như blocker V1.
- [ ] **S-07.4 · LOCAL + CONTRACT — pre-start CANCEL:** REQUESTED/PREOP/READY/SCHEDULED mới được cancel; derive cancellationStage từ prior persisted state, actor/reason/operationId do trusted command context. CANCELLED + invalidate active readiness + release đúng reservations + history/receipt + approved cancelled fact atomic; preserve source/ledger refs. Same command replay stable, different payload conflict. IN_PROGRESS/COMPLETED reject; post-start abort/result correction **DEFERRED V1**, không thêm state/payload ngoài contract. Billing adjustment consumer acceptance theo S-03.4, không direct Billing DB/HTTP refund.
- [ ] **S-07.5 · VERIFY — terminal races + consumer acceptance:** complete↔complete, start↔cancel, complete↔cancel (cancel sau START luôn reject), revoke↔start, result save↔outbox failure, stale resource release. Assert một committed outcome/result, correct timestamp/audit, no residual bookings; retry/new delivery eventId không nhân side effect. Same-byte shared fixtures qua Billing/Inpatient/Notification/Report; owner chưa test thì local command có thể DONE_LOCAL nhưng G1/G3 vẫn OPEN.
- [ ] **S-07.6 · VERIFY — module vertical slice + handoff:** Testcontainers request→create→preop→checklist/consents→draft→READY→finalize→START→COMPLETE và pre-start CANCEL ở từng state; cả OUTPATIENT/ADMISSION với port fixtures, sau đó actual producers/Gateway khi G3 sẵn sàng. Flag-off, auth/roles, restart/pending/outbox recovery và duplicate replay đều có evidence. Module test không được ghi là end-to-end liên service. Cập nhật README/.http/spec maturity/handoff registry theo trạng thái thật; chỉ retire handoff sau cả hai phía đạt acceptance.

### 6.8. Thứ tự thực thi và kế thừa test scenarios

Ba lát cắt đầu theo thứ tự dependency (không cần đợi thành viên khác để làm phần local):

1. **Domain models — DONE/LOCAL:** S-02.1.2 + S-02.1.4 + S-02.1.5 đã có invariant, restore/revision audit và pure-domain tests.
2. **Core persistence — V1 schema DONE, repository PARTIAL:** S-02.2 migration/constraints và case JPA/draft schedule round-trip đã chạy PostgreSQL. Checklist, consent, result adapters, schedule-history readback, begin-preop receipt transaction và rollback child rows/audit cũng đã qua PostgreSQL 16 verification trong module; tiếp tục transaction orchestration cho các command còn lại, MQ failure matrix và concurrent referral retry. Không đưa clearance wire chưa chốt vào DDL như fact đã duyệt.
3. **Reliability + identity nền — PARTIAL trừ Patient DONE:** S-02.4/.5 durable storage tests và S-03.3.1 Patient adapter/HTTP stub đã có; tiếp theo nối create/preop/query, consumer/publisher runtime và failure matrix còn mở, tránh controller “chạy được” nhưng mất event hoặc thiếu authority.

Thứ tự tiếp: S-04 → S-05.1/.2 và S-06.2 (độc lập nhau) → S-05.3/.4 khi clearance contract sẵn sàng → S-06.4 → S-07. S-06.3 chạy cùng giai đoạn persistence; S-03 fixtures/owner responses được thu thập suốt quá trình. CONTRACT chưa đạt thì dừng đúng activation edge, không bỏ fake success để đi tiếp.

| Scenario source đã có | Task phải mang sang executable tests | Bằng chứng đóng |
|---|---|---|
| Draft Surgery: business rules `SUR-BR-*` | S-04 exact referral/context; S-05 readiness; S-06 resource exclusivity; S-07 outcomes | Rule → named test method → run result; rule không supported V1 ghi deferred, không âm thầm bỏ. |
| Draft Surgery: foundation/contract/idempotency `SUR-FND-*`, `SUR-CON-*`, `SUR-IDEM-*` | S-01 flags/auth; S-02 receipts/inbox/outbox; S-03 provenance/negative fixtures | Local test tách shared fixture và owner consumer test. |
| Independent slices: `SUR-RACE-01…07` | S-05.5, S-06.5, S-07.5 | Real DB transactions/two workers + assertion state/history/booking/outbox. Post-start CANCEL scenario theo V1 là rejection, không triển khai abort. |
| Candidate §10 rule/test table, §13 DoD + §14 decisions | Gắn từng rule vào S-02…07; Huy defaults ở handoff ưu tiên cho local V1 | Không đánh dấu code-ready chỉ vì candidate có pseudocode/DDL. |
| Gaps mới của Inpatient/Notification | S-03.4/.6 | Registration/order/outpatient-ignore + provisional READY/reschedule/invalidation fixtures cùng owner. |

**Mẫu evidence cho mỗi subtask:** ID; commit/file/test; command + ngày; số pass/fail/skip; local/shared fixture hash; gate đạt/chưa đạt; handoff còn mở. Task cha chỉ DONE khi mọi child trong V1 hoàn tất; OWNER/CONTRACT pending không bị che bởi một module build xanh.

### 6.9. Checklist bàn giao code theo layer và lệnh kiểm chứng

Tên mới dưới đây là mục tiêu triển khai, cần thống nhất với spec của slice trước khi tạo file. Reuse `SurgeryCase`, `CareEpisode`, `ReadinessSnapshot` và history hiện có, không dựng aggregate thứ hai cạnh chúng.

| Layer / nơi đặt | Đầu ra cần thấy khi review slice | Task |
|---|---|---|
| `domain/model`, `domain/exception` | ChecklistTemplate/ChecklistItem, SurgeryConsent, SurgerySchedule/TeamAssignment, ResourceReservation, SurgeryResult/PerformedItem; policies/invariants và restore thuần Java. Enum ở model, không framework/I/O. | S-02.1, S-05/06/07 |
| `application/port/in` + `application/dto` | Create/Get/List/BeginPreop, UpdateChecklist, Record/RevokeConsent, Prepare/FinalizeSchedule, EvaluateReadiness, Start/Complete/Cancel use cases; records với expected revision/command identity theo đúng operation. | S-04…07 |
| `application/port/out` | Case/template/consent/schedule/result repositories, ResourceReservation/Lock, Inbox/Outbox/CommandReceipt ports, Patient/CareRelationship/Staff/Room lookup ports; không Spring Page/JPA Entity/Rabbit Message trên chữ ký. | S-02/03/06 |
| `application/service`, `application/mapper`, `application/event` | Orchestration ngắn theo use case, transaction boundaries rõ; preflight remote ngoài DB lock, domain quyết định state, mapper DTO/domain; approved event intent atomic với mutation. Không một service khổng lồ kiêm HTTP/SQL/Rabbit. | S-02.3, S-04…07 |
| `web`, `messaging/consumer` | Driving adapters chỉ auth/validate/decode→in-port→map result; mọi HTTP method có `@PreAuthorize`, listener classify permanent/transient/not-applicable và giữ correlation. | S-01.6, S-03/04 |
| `infrastructure/persistence`, `infrastructure/client`, `infrastructure/messaging`, `infrastructure/config` | Driven adapters thực thi ports, physical naming mapping, SQL locks/lease fencing, REST resilience, versioned serialization; không gọi xuyên DB hoặc import Java class service khác. | S-02/03/06 |

**Chạy kiểm chứng khi thực thi code, không phải evidence đã chạy ở lượt sửa plan:**

| Phạm vi | Lệnh / điều kiện | Điều kiện ghi PASS |
|---|---|---|
| Module Surgery đầy đủ | `mvn -f backend/surgery-service/pom.xml test` | Đọc Surefire từng suite và tổng failure/error/skip; không chỉ exit code. Lần này chỉ có historical 30-test evidence. |
| Domain/layer regression | `mvn -f backend/surgery-service/pom.xml "-Dtest=CareEpisodeTest,SurgeryCaseTest,ArchitectureTest" test` và thêm suite mới của slice | Tests mới thật sự được discover; models checklist/consent/schedule/result có unit coverage, không chỉ 13 tests cũ. |
| PG/Rabbit/concurrency | Module suite trên với Docker reachable, Testcontainers PostgreSQL/Rabbit đúng dependencies POM hiện hữu | Migrations, transaction/race/recovery suites thực sự chạy; không đóng task khi critical suite skipped. Test mới dùng tên/config được Surefire discover, không đặt `*IT` rồi bỏ quên lifecycle runner. |
| Common dependency cục bộ nếu thiếu | `mvn -pl backend/common -am -DskipTests install`, sau đó chạy module Surgery | Chỉ build/cài artifact từ source hiện tại, không sửa Common/root POM hoặc tự đổi version để vượt lỗi dependency. |
| Root reactor | `mvn -pl backend/surgery-service -am test` **sau S-01.4 registration** | Trước registration không dùng lỗi module-not-found để kết luận Surgery domain hỏng hoặc claim root build đã pass. |
| Contract và G3 | Shared fixture hash + producer/consumer test links; actual Gateway/owner runtime theo X-01.4 | Stub/module vertical slice chỉ LOCAL_TEST_PASS, không SHARED_FIXTURE_PASS/E2E_PASS. |

Sau mỗi lát cắt: `git diff --check`, kiểm imports/ownership, cập nhật đúng child checkbox và evidence. Không cộng test lịch sử khác commit vào tổng test mới; không đổi trạng thái OWNER/CONTRACT chỉ vì module build xanh.

## 7. Pharmacy — mở rộng từ nền đã có

### P-01 — giữ nguyên nền payment/dispense legacy đã hoàn thành

**Trạng thái:** CODE_PRESENT / DONE_LOCAL lịch sử; không triển khai lại để tính thêm tiến độ.
**Files hiện hữu:** PaymentApplicationService, DispenseApplicationService, DispenseTransactionService, PaymentReceipt, outbox/migrations và các tests.

- [x] **P-01.1 · ĐÃ CÓ:** receipt payload fingerprint/resume, terminal conditional finalize; lock Rx/slip/stock cho effect-idempotency; compensation/outbox và actor audit V13.
- [x] **P-01.2 · ĐÃ CÓ:** tests race/recovery/late compensation/rollback; strict actor từ claims, SYSTEM không UUID giả; legacy cancelled/expired fixtures.
- [x] **P-01.3 · DONE/VERIFY:** baseline trước refactor P-03 và lần regression sau thay đổi đã chạy, có số tests/skip tại §10.1. Unit/web cho create/cancel/expiry/admin outbox chạy; DB/Rabbit concurrency/reconcile vẫn chưa xác minh vì Docker engine không reachable. Không đổi Billing wire hoặc receipt policy.

### P-02 — context/schema/DTO/event additive, bắt đầu được trước producer live

**Gate:** Pharmacy V2 đã implementation-ready; xử lý gap §3.2 theo slice. **Trạng thái:** context/flag foundation đã có; chưa V2 schema/API/writer.
**Files:** Prescription/CareContext/CareEpisode, request/command/response/mappers, JPA entity/adapter, migrations, application/event, publisher, config và fixture tests.

- [x] **P-02.1 · DONE/LOCAL:** domain value objects/version 0/1, flag mặc định false và validation matrix legacy vs v1 đã có pure tests. ADMISSION yêu cầu admissionId=careEpisodeId; OUTPATIENT không có admissionId/không suy careEpisodeId từ recordId. Chưa thay request/DB/writer.
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

- [x] **R-01.1 · DONE/LOCAL:** đã dựng namespace/commands/port V2, flag mặc định false và decoder harness offline; unknown version/type/producer/source bị reject, V2 envelope không fallback vào consumer legacy. Giữ nguyên 5 binding/3 API; chưa bind V2 live. Surgery source identity chưa được thêm do D07/D11 chưa khóa.
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
- [x] **R-02.2 · DONE/VERIFY:** Report JwtAuthFilterTest chạy 4/4, 0 skip/fail/error trong full suite; không sửa filter, Common hay Gateway.
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
| 3 — Surgery | Nền đã có → S-02 models/restore/core persistence/reliability → S-04 create/preop/query → S-06 draft + S-05 guards → READY → finalize → S-07; chi tiết ba lát cắt đầu ở §6.8 | S-03 contract/fixture theo từng edge; không chờ toàn bộ owner để code/test local. Chỉ bật integration sau G1/G2 và shared bootstrap tương ứng. |
| 4 — integration riêng | Billing clearance → outpatient V1; Inpatient lifecycle → admission thuốc; Billing finance → Report finance; Surgery outcomes → Report surgery | Mỗi slice đạt G1/G2/G3 riêng, không chờ cả 12 D cùng đóng. |

Đã hoàn tất local P-01.3, P-02.1, R-02.2, R-01.1 và các audit H-01.4.1/2, H-01.5.1 ngày 2026-09-28; xem evidence §10.1. H-01.2.1/H-01.3.1 proposals đã được gom vào handoff chung; H-01.3.2 Huy-local defaults được quyết định theo ủy quyền ngày 2026-09-28. H-01.2.2/H-01.3.3 vẫn chờ owner confirmations/fixtures. Surgery platform/domain + V1 migration/persistence/reliability nền hiện có; suite hiện đạt 89 tests với PostgreSQL-backed cases. Root reactor/Rabbit/Gateway/shared integration chưa xác minh. Không còn subtask code “râu ria” Pharmacy/Report có thể đóng an toàn nếu thiếu contract. Report finance không phải gate để tiếp tục Surgery; phần Surgery-local tiến theo thứ tự §6.8, integration edges vẫn theo handoff.

### X-01 — kiểm thử hệ thống, rollout và báo cáo bằng chứng

**Files Huy:** tests/resources/contracts, module .http/README, plan/evidence/handoff. Shared runtime do owner thực hiện.

- [ ] **X-01.1 · PARTIAL/VERIFY:** Docker engine hiện reachable và Surgery suite 67/67 có PostgreSQL Testcontainers thực; xem §10.1. Còn clean Pharmacy/Report baseline và shared root reactor trước khi đóng X-01.1.
- [ ] **X-01.2 · CONTRACT/VERIFY:** manifest mỗi event có canonical ID/version, producer/consumer paths, SHA-256 và command/test output; valid/duplicate/new-eventId-same-operation/poison/missing target/unsupported version đều có case.
- [ ] **X-01.3 · VERIFY:** Docker outpatient legacy + outpatient V1 exact clearance, admission started→prescribe→dispense→charge→close; duplicate authorization/close race và failures không phá stock/ledger. Không giả producer bằng DB inserts rồi gọi đó E2E.
- [ ] **X-01.4 · VERIFY sau Surgery G0–G2:** request→preop→checklist/hai consents→clearance→draft→READY→finalize→start→complete; cancel chỉ trước START. Kiểm resource race/overrun, consent revoke, expiry và mọi thử bypass financial override đều bị chặn trong V1. Gateway roles và downstream consumer fixtures/runtime đúng.
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

**Evidence implementation slice 2026-09-28**, source commit f69dd1d:

- Trước thay đổi (baseline): Pharmacy **217 tests / 46 skipped / 0 failures / 0 errors**; Report **123 / 24 / 0 / 0**.
- Sau thay đổi, lệnh mvn -q -pl backend/pharmacy-service,backend/report-service -am clean test: Pharmacy **228 / 46 / 0 / 0**; Report **134 / 24 / 0 / 0**. Tổng **362 discovered**, 70 skipped, 0 failure/error. 11 test mới ở mỗi module.
- Report JwtAuthFilterTest: **4/4 pass**, 0 skipped/failure/error. Testcontainers suites bị skip gồm PostgreSQL/Rabbit integration, reconciliation và migration checks; Docker binary tồn tại nhưng không khởi chạy được (Access Denied), Testcontainers báo không có Docker environment hợp lệ.
- Pharmacy thêm CareEpisode, PrescriptionCareContext, enum version/context/type; ma trận v0/v1 được unit-test và mediflow.features.care-finance-v2 bind mặc định false. Không đổi schema, API, outbox hoặc authorization legacy.
- Report thêm command/port namespace, feature property, offline envelope decoder kiểm version=1, routing key, producer và canonical source ID; versioned envelope không được parse như event phẳng legacy. Decoder hiện chỉ nhận 8 event có producer/source field đã xác định từ spec; surgery.completed/surgery.cancelled vẫn reject đến khi D07/D11 khóa source key.
- Report giữ nguyên năm routing bindings và ba API; V2 flag vẫn false, không tạo live listener, projector, migration hoặc endpoint.
- Build scope chỉ Pharmacy/Report cùng dependencies trong Maven reactor; không sửa Common/Gateway hay producer ngoài scope. git diff --check sạch.
- **H-01.4/5 audit 2026-09-28:** xác nhận actual Pharmacy legacy event shapes/consumer bindings và đối chiếu offline V1 decoder/source key; đồng thời ghi rõ Report metric/replay source gaps. Chỉ các subtask `.1/.2` nêu trên được DONE/LOCAL; chưa có V1 producer bytes, owner approval hoặc shared expected-totals fixture, nên parent H-01.4/5 vẫn OPEN.
- **H-01.2/3 + domain core 2026-09-28:** [handoff Surgery G0](../../handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md) ghi mapping proposals, tám Huy-local V1 decisions theo ủy quyền, residual owners và evidence cần cung cấp. H-01.3.2 và S-02.1.1 DONE/LOCAL; H-01.2.2/H-01.3.3 vẫn OPEN cho owner fixtures/contract updates. S-02.1.1 ban đầu có 13 domain tests; các model/revision/restore tests mới được ghi riêng bên dưới. Shared registration vẫn ở bootstrap handoff.

**Evidence lượt làm chi tiết plan 2026-09-28**, baseline `6686f9e`:

- Đã đối chiếu sáu file plan ở §1.1a, hai Surgery specs, architecture HTML, blueprint/integration rules, active handoffs và source Surgery/producer-consumer liên quan.
- Cập nhật S-01…S-07 theo models → persistence/reliability → use cases → adapters → tests; giữ nguyên các checkbox đã hoàn tất. Bổ sung rehydration, command receipt, dependency revisions, resource lock order/overrun, invalidation transaction và Inpatient reference/outpatient gaps.
- Đây là **DOCS_ONLY / STATIC_AUDIT** của lượt sửa plan trước, không thêm production code, không chạy Maven/Docker và không ghi thêm task code DONE tại thời điểm đó; suite Surgery 30 tests là evidence lịch sử của commit `4a4b098`, không phải test run trong lượt sửa plan ấy.
- Kiểm tra tài liệu: 138 local links trong 7 file cập nhật hợp lệ; không trùng/mất mã task Surgery, giữ nguyên trạng thái 38 ID cũ; không conflict markers và `git diff --check` sạch. Đây là document validation, không business test evidence.

**Evidence triển khai S-02.1.2/.4/.5 2026-09-28**, source workspace sau baseline `6686f9e`:

- Files chính: Surgery domain models trong `backend/surgery-service/src/main/java/com/mediflow/surgery/domain/model/`; `SurgeryCase`, `SurgeryStateChange`, `ReadinessSnapshot`; tests `SurgeryLocalModelsTest`, `SurgeryCaseRestoreTest` và cập nhật `SurgeryCaseTest`.
- Lệnh `mvn -f backend/surgery-service/pom.xml test`: **41 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. Trong đó 24 domain và 17 platform/config tests. `git diff --check` sạch.
- Chỉ đóng S-02.1.2/.4/.5 DONE/LOCAL. Chưa chạy PostgreSQL/Testcontainers, Rabbit, root reactor hoặc Gateway; do đó S-02.2 trở đi và shared bootstrap vẫn OPEN. Handoff cross-owner/fixtures không bị thay bằng local doubles.

**Evidence triển khai 10 lát cắt Surgery tiếp theo 2026-09-28**, trên cùng source workspace chưa commit/push:

| ID | Kết quả code và giới hạn | Trạng thái |
|---|---|---|
| S-01.7 | ArchUnit 4 rules; PostgreSQL integration fixtures/schema/JPA/reliability/race. Rabbit fixture, reusable fixture và API/Dockerfile còn chờ đúng thời điểm. | PARTIAL |
| S-02.2 | `V1__surgery_core.sql`: full core tables/index/FK/CHECK, PostgreSQL 16 fresh migrate và Hibernate validate; constraint tests. Không dùng dữ liệu master service khác. | DONE/LOCAL |
| S-02.3 | Case JPA restore/history, optimistic version, checklist/consent/result/schedule child history và begin-preop receipt transaction đã pass PostgreSQL 16 tests; orchestration các command khác, concurrent referral retry và full failure matrix còn thiếu. | PARTIAL |
| S-02.4 | Command receipt và inbox durable; PostgreSQL replay/conflict, semantic key, pending defer, rollback tests. Consumer/ACK và authorization boundary còn thiếu. | PARTIAL |
| S-02.5 | Outbox durable với bytes/causal order/lease/fencing/retry/returned state; PostgreSQL test. Rabbit dispatcher + confirm/return orchestration chưa có. | PARTIAL |
| S-02.6 | Fresh schema, ORM restore, rollback receipt/outbox/child rows + audit, inbox replay, expired attempt, two-worker same-room/reversed-team race, adjacent/contained và IN_USE overrun đã test; MQ/crash/command matrix còn thiếu. | PARTIAL |
| S-03.3.1 | Patient service-token lookup `/patients/{id}/exists`, echo/envelope validation, outage vs absence và HTTP stub tests. Chưa chạy qua Gateway. | DONE/LOCAL |
| S-06.2 | Draft schedule + team/revision histories persisted trong PREOP, không reserve; Organization eligibility và PUT command chưa có. | PARTIAL |
| S-06.3.1 | Case-first + sorted resource mutex locking, revision-scoped release/START và PostgreSQL reverse-team-order race; chưa nối mọi command writer. | PARTIAL |
| S-06.3.2 | Half-open overlap cho room/staff, cross-room same-staff, adjacent/contained intervals, IN_USE overrun và partial-booking rejection; chưa full matrix/bounded deadlock retry. | PARTIAL |

- Lệnh kiểm chứng tại 2026-09-29: `TESTCONTAINERS_RYUK_DISABLED=true mvn -f backend/surgery-service/pom.xml '-Dapi.version=1.44' test` → **89 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. `-Dapi.version=1.44` cần trên Docker Desktop 29 với Testcontainers 1.19.8 hiện có; không có tham số này, Docker daemon từ chối client API 1.32 và các container tests có thể bị skip, nên kết quả ấy **không** được xem là PG evidence. Docker thực tế chạy PostgreSQL 16.14; không có Rabbit container trong lượt này. `TESTCONTAINERS_RYUK_DISABLED` chỉ đặt trong tiến trình test.
- `git diff --check` sạch. Handoff owner H-01.2/3, shared bootstrap, Billing clearance, Organization room/team và Vinh relationship proof vẫn OPEN. Không sửa service/DB của owner khác và không kích hoạt Surgery business flags.

**Evidence triển khai 5 code slice kế tiếp 2026-09-28:**

- S-02.3.1 checklist template/snapshot persistence, S-02.3.2 consent + append-only audit, S-02.3.3 immutable result/items, S-02.3.4 schedule-history readback/team assignments, và S-03.3.2.1 Organization lookup port đã có code. Các subtask được đánh dấu CODE COMPLETE/VERIFY OPEN; không tính là task đã kiểm chứng.
- Chạy `mvn -f backend/surgery-service/pom.xml '-Dapi.version=1.44' -DskipTests compile -q` → **BUILD SUCCESS**. Không chạy unit/PostgreSQL tests trong lượt này; cần testcontainers sau đó để đóng phần VERIFY.
- Organization port chỉ định nghĩa model nội bộ typed; không có HTTP client, URL hoặc wire contract. Handoff owner/eligibility vẫn OPEN. Parent S-02.3 và S-03.3.2 vẫn PARTIAL/OPEN.

**Evidence lượt tiếp tục triển khai 5 lát cắt 2026-09-28:**

- S-02.3.5: deterministic command fingerprint/receipt codec; S-04.5: begin-preop transaction; S-05.1.1: checklist item/snapshot revisions và append-only item history; S-05.2.1: typed consent sign/revoke với pre-start readiness invalidation và exact reservation release; S-06.2.1: schedule persistence cho revision mới sau trạng thái RELEASED, giữ lịch sử.
- `git diff --check` sạch. Đã thử `mvn -f backend/surgery-service/pom.xml -DskipTests test-compile -q`, nhưng Maven không tải được Spring Boot parent POM `3.3.5` do network bị từ chối; vì vậy compile và test đều **NOT VERIFIED**, không phải BUILD SUCCESS. Không sửa POM/root/shared files để lách dependency issue.
- Tại thời điểm ghi evidence lịch sử này, business routes/controllers chưa được tạo; mọi `mediflow.features.surgery.enabled` mặc định vẫn `false`. S-03 owner contracts (Clinical/Organization/Billing), signer authority và same-byte fixtures vẫn OPEN; đây không phải wire/API approval. Trạng thái kiểm thử đã được cập nhật ở evidence mới hơn bên dưới.

**Evidence kiểm thử 5 lát cắt trước 2026-09-29:**

- Bổ sung `SurgeryCommandReceiptsTest`, `SurgeryPreopApplicationServiceTest`, `SurgeryChecklistApplicationServiceTest`, `SurgeryConsentApplicationServiceTest`; mở rộng `SurgeryCasePersistenceIntegrationTest` để kiểm tra revision lịch mới sau RELEASED và bảo toàn lịch sử/reservation. Làm cho `ServiceTokenFactoryTest` dùng cùng clock cố định với token fixture để tránh phụ thuộc thời gian chạy.
- Chạy `TESTCONTAINERS_RYUK_DISABLED=true mvn -f backend/surgery-service/pom.xml '-Dapi.version=1.44' test` → **78 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. Testcontainers kết nối Docker Desktop qua Npipe; PostgreSQL 16.14 chạy thật và Flyway V1 được áp dụng. Ryuk chỉ bị tắt trong tiến trình test này do Docker Desktop/API tương thích; không thay đổi cấu hình môi trường lâu dài.
- Bằng chứng 78-test ban đầu chỉ đóng phần unit + persistence lịch lúc đó; chưa kiểm chứng 5 adapter/transaction slices mới thêm, controller/Gateway/API E2E hoặc owner contracts. Business flags tiếp tục mặc định `false`.

**Evidence batch persistence 5 subtask Surgery ngày 2026-09-29:**

- Mở rộng `SurgeryCasePersistenceIntegrationTest` bằng PostgreSQL cases `checklistTemplateSnapshotAndItemHistory_roundTripWithOptimisticRevision`, `consentPersistence_signsRevokesAndReconsentsWithoutReplacingAudit`, `resultPersistence_roundTripsPerformedItemsAndRejectsReplacement`, `beginPreopPersistence_commitsCaseAndReceiptTogetherAndReplaysAfterCommit`; đồng thời schedule-history test xác nhận team theo từng revision và reservation cũ RELEASED. Bằng chứng này bao phủ persistence thuộc S-02.3.1–.5, S-05.1.1, S-04.5 và S-06.2.1; không tuyên bố hoàn tất workflow/API của các task cha.
- Chạy lại lệnh trên: **82/82 pass, 0 skip**; log xác nhận Docker Desktop + PostgreSQL 16.14, Flyway V1 migrate thành công. `git diff --check` sạch sau cập nhật plan. Không chạy Rabbit, Gateway, root reactor; không sửa module/shared scope khác và Surgery production feature flag mặc định vẫn `false`.
- Còn mở: signer/evidence/Organization/Clinical/Billing owner contracts, concurrent receipt/referral retry matrix, fault injection ở các workflow khác, checklist/consent/result/outbox command orchestration, HTTP/Gateway/E2E và Rabbit delivery. Các phần này không được đóng theo kết quả module suite.

**Evidence batch 5 task local tiếp theo ngày 2026-09-29:**

- S-03.3.2.1: thêm `OrganizationLookupPortTest` cho mọi typed state, metadata normalization và validation của lookup snapshot; live adapter/HTTP contract vẫn chờ Organization owner.
- S-05.4.3: thêm application test checklist update ở SCHEDULED, xác nhận về PREOP, clear active readiness snapshot và release đúng `scheduleId + revision`; test mock không phải DB/race proof.
- S-02.6: PostgreSQL rollback test ném lỗi sau các child writes và xác nhận checklist template/snapshot, consent/audit, result/performed items đều rollback cùng transaction.
- S-06.3.1/.2: PostgreSQL tests xác nhận hai ca dùng chung ê-kíp với input order ngược chỉ tạo một booking hoàn chỉnh (room + hai staff), và lịch phòng bị chứa hoàn toàn vẫn conflict dù ê-kíp khác.
- Chạy `TESTCONTAINERS_RYUK_DISABLED=true mvn -f backend/surgery-service/pom.xml '-Dapi.version=1.44' test` → **89 tests, 0 failures/errors/skips; BUILD SUCCESS**, PostgreSQL 16.14/Flyway V1 thật. Chưa chạy Rabbit/Gateway/root reactor; feature flag production giữ false. S-02.6/S-06.3 vẫn PARTIAL vì race/failure matrix toàn diện và nối vào mọi command chưa xong.

### 10.2. Checklist đóng mỗi task

- [ ] Ghi subtask ID, source commit, files đã sửa, rule/contract version; phân biệt code với design.
- [ ] Unit/domain/application + web/security/architecture liên quan pass; import layers, DTO boundary, actor identity đúng.
- [ ] Nếu có DB/broker: fresh/upgrade/concurrency/rollback/retry Testcontainers chạy thật, không skip critical cases.
- [ ] Producer và consumer dùng cùng fixture khi có wire change; thiếu owner ghi CONTRACT/OWNER, không đóng bằng mock.
- [ ] Migration additive, legacy API/outbox rows đọc được; flag false không tạo V2 effects hoặc ACK mất facts.
- [ ] Nếu ghi E2E_PASS: có Gateway + producers/consumers thật, dữ liệu expected/actual, reconciliation và rollback.
- [ ] git diff --check; link docs/fixture/task IDs hợp lệ; scope chỉ Huy + docs đã giao, không sửa production của owner khác.
- [ ] Cập nhật checkbox, trạng thái SPEC/CODE/FIXTURE/TEST/E2E, handoff còn mở và bước kế tiếp.

**Slices đã đóng trong lượt 2026-09-28:** P-01.3, P-02.1, R-02.2, R-01.1 — CODE=LOCAL, TEST=PASS cho test chạy được; E2E=NOT_RUN do Docker unreachable. V2 producer/consumer contract không được đánh dấu IMPLEMENTED.

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
