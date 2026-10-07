# Kế hoạch code của Huy — Surgery, Pharmacy, Report theo Care–Finance V2

**Cập nhật:** 2026-10-06 · **Owner:** Huy (LQHuy0210).
**Baseline hiện tại:** nhánh Huy, HEAD `ea60ea4` sau đồng bộ master + working tree dependency liên service và lifecycle Surgery chưa commit. Baseline `3103fa1`/`6686f9e`/`7121330` và các test runs cũ là lịch sử ở §10, không chứng nhận code hiện tại.
**Phạm vi lượt hiện tại:** user đã giao task-scoped override để Huy thực hiện dependency thiếu ở service khác. Hai lát cắt hiện tại: Gateway/Organization/Inpatient authority và Billing payment/clearance + Notification receipt + Surgery grant projection; shared root/Common/Compose không đổi. Xem [cross-service execution/evidence](2026-10-05-huy-cross-service-dependencies.md). Báo cáo đánh giá Pharmacy/Report: [V2 completion assessment](2026-10-02-huy-v2-completion-assessment.md).
**CURRENT:** Pharmacy có V14–V18, admission/clearance offline, held writer và các internal outpatient create/dispense/cancel/expiry/stock-failure transactions. Report có V6–V10 operational kernel/journal, finite replay, pending admission evidence và gated aggregate snapshot API/read coverage. Public V1 Pharmacy vẫn fail-closed; live V2 listeners/flags/held delivery đều OFF. Financial V2, admission eligibility/metrics và live catch-up/cutover chưa hoàn tất. Không đánh đồng code/module PASS với G1/G3 hoặc V2 production DONE.
Surgery đã có generic identity adapters, Organization room/explicit surgical-capability authority, Inpatient exact-admission lookup và offline exact financial-grant projection (V2 migration). Billing có held V1 producer cho 5 clearance purposes và classified receipts; Notification có gated private IN_APP receipt transaction. Canonical fixtures dùng chung producer/consumer. Required team composition, referral relationship, revoke/supersede/READY/START guards, financial issuance/reconciliation và full workflow vẫn mở; business/intake/publication mặc định OFF.

## 1. Phạm vi, nguồn chuẩn và thay đổi so với plan cũ

Phạm vi mặc định của Huy là backend/pharmacy-service, backend/report-service và backend/surgery-service. Ngày 2026-10-05 user cho phép triển khai luôn các dependency thiếu trong Clinical, Inpatient, Lab, Billing, Notification, Organization, Patient và Gateway liên quan plan này; ownership lâu dài không đổi. Root pom.xml, docker-compose.yml, scripts/init-databases.sql, Common, Eureka và CI vẫn là shared chưa giao trong lượt này. Không thêm endpoint giả, truy cập DB xuyên service hoặc đoán reference để vượt phụ thuộc.

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
| [Clinical canonical](../../eproject_general_plan/backend-spec/03-clinical.md), [Lab canonical](../../eproject_general_plan/backend-spec/04-lab.md) | Episode ngoại trú appointment-backed; completion/disposition/referral; Lab order/clearance/result version và exact order correlation. |
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

Đối chiếu thêm cả [working draft Surgery](../../eproject_general_plan/backend-spec/10-surgery.md), [V2 candidate](../../eproject_general_plan/backend-spec/care-finance-v2/11-surgery.md), [bounded-context rules](../../ai/services/surgery.md), [decision handoff](../../handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md) và [canonical runtime verification](../../ai/services/surgery.md). Nếu candidate khác quyết định Huy đã ghi, áp dụng quyết định **chỉ trong phạm vi local**; wire contract phải được cập nhật cùng owner trước G1.

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
| E11 · refreshed 2026-10-04 | [StaffController](../../../backend/organization-service/src/main/java/com/mediflow/organization/web/controller/StaffController.java), [DepartmentController](../../../backend/organization-service/src/main/java/com/mediflow/organization/web/controller/DepartmentController.java), [identity contract](../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md) | Đã có service-only `/staff/{id}/lookup` và `/departments/{id}/lookup` trả active/jobTitle/departmentId theo contract. `/staff/{id}/exists` cũ vẫn doctor-specific. Chưa có room lookup hoặc role↔jobTitle policy; không dùng `eligibleDoctor` thay hai phần này. |
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
| D04 | Schedule/team DDL, room_reference, guard eligibility và yêu cầu chống overlap đã có. | E11 đã có generic staff/department lookup; Surgery có booking persistence và consumer adapter cục bộ, chưa room lookup. | PARTIAL: Huy chọn không tạo room master trùng và interval UTC `[start,end)` không buffer; Organization room source/job-title mapping còn thiếu, không bật finalize thật. H-01.3.3, S-06.1–3. |
| D05 | Prepare PREOP → READY → finalize SCHEDULED → START. | Có domain transitions/invalidate, chưa booking hay dependency revisions. | Huy đã chọn prepare/finalize, invalidation và chỉ reserve khi finalize; S-05.4, S-06.4 hiện thực bằng DB/transaction. |
| D06 | Candidate có typed consent và financial-only override. | Domain AND hai consent booleans; chưa consent records. | Huy chọn override disabled V1; S-07.3 chỉ kiểm chứng không bypass. Consent policy liên clinical vẫn theo H-01.3.3/S-05.2. |
| D07 | Actual itemCode/priceCode/quantity; one result/case; Billing định giá. | Có complete/pre-start-cancel transition; chưa result/outcome persistence. | Partial abort/correction ngoài V1; Billing reconciliation và completed category vs summary còn chờ contract. H-01.2–3, S-07.2/4. |
| D08 | SPEC_CHOICE Pharmacy: v0 compatibility, v1 exact context; một đơn tối đa một slip; ADMISSION cần active projection đúng patient/khoa, không prepaid. | V14/V15 context persistence, offline admission lifecycle + V1 event codec/fixtures đã pass; chưa V1 runtime writer/authorizer. | Không hỏi lại full vs multiple dispense. Medical-discharge eligibility, transfer/freshness và terminal charge adjustment cần owner contract. P-02/P-03 local wiring còn OPEN; multiple-dose/returns ngoài V1. |
| D09 | Billing target có transactionId/refund original/account/classification, settlement totals; Report có 5 nhóm chỉ tiêu riêng. | E06/E08: legacy invoice/compensation, chưa ledger V2. | PARTIAL: payload thiếu allocated earned/deposit-release/split department và settlement version/supersedes; cash gross/net, period và receivable stock/delta chưa thống nhất. H-01.5, R-03. |
| D10 | Inpatient tách medical discharge và CLOSED; Report target dùng admission.closed cho discharge/LOS, Surgery có actual times/category. | E02 đã có admission facts; Report chưa KPI này, Surgery chưa persisted outcomes. | PARTIAL: chốt tên administrative duration vs medical LOS, close thiếu department phải lấy exact start snapshot; thiếu contract bed transfer/release/capacity cho Report. R-04 không công bố occupancy bằng active admission count. |
| D11 | SPEC_CHOICE: legacy v0 giữ nguyên, nested envelope v1, feature flag false, projections mới chạy riêng và đối soát trước cutover. | Pharmacy offline V1 codec; Report typed operational shadow kernel + durable minimal journal/claims có PG evidence. Chưa live source mapper/replay hoặc Surgery messaging. | Còn producer mapping/corrections, source horizon/retention/watermark, isolated generation/pending/catch-up và read cutover. H-01.4–5, P-02.5, R-01. |
| D12 | Exact purpose/target/patient/episode; expiresAt; active admission projection đã có trong target. | Billing held SURGERY/PRESCRIPTION grant producer + same-byte dual-gated consumers/PG/MQ; Surgery exact admission/Organization authority đã có. | PARTIAL: grant-only không thay revoke/supersede/freshness authority tại READY/START; referral/care relationship, transfer và full workflow race vẫn OPEN. P-03.1–3, S-03.2/S-05.3. |

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

Surgery có ba flag mặc định false: `mediflow.features.surgery.enabled`, `mediflow.surgery.messaging.producer.enabled`, `mediflow.surgery.messaging.consumers.enabled`. Tất cả controller hiện hữu require business gate; publisher/dispatcher require business+producer, clearance listener/queue/durable worker require business+consumer. Context tests phủ đủ 8 tổ hợp mỗi messaging gate, không bind rồi ACK mất event khi disabled và không mark outbox published khi producer off. Business outcome writer chưa được mở. Flag không ngăn Flyway chạy. Actual runtime verification và tiến độ batch 50 ở [execution ledger](2026-10-05-huy-50-task-execution.md), không thay full workflow G3.

## 5. H-01 — thu hẹp quyết định, khóa hợp đồng đúng phần còn thiếu

**Trạng thái:** IN_PROGRESS; audit/plan là tài liệu đã hoàn tất, contract/approval còn mở.
**Files:** plan này; Huy handoffs đang active; đề xuất chỉnh đúng phần của spec V2/canonical tương ứng, không tạo một bộ wire contract khác trong plan.

- [x] **H-01.1 · NGAY · audit baseline:** đọc code E01–E12, cập nhật D01–D12 và maturity; lưu bản cũ. Acceptance: mọi “đã code” có source, mọi “đã test” có ngày/phạm vi; không lấy tick plan spec làm code evidence.
- [ ] **H-01.2 · CONTRACT · episode/referral/event mapping:** với Vinh/Lộc khóa D01/D02/D07 và ready/completed/cancelled field gaps. Đầu ra: mỗi context có request→case→charge→clearance→result fixture, exact source key, producer, version, consumers và pricing boundary. Test plan: một clinical intent đi qua hai producers vẫn một case/charge; appointment-backed không đổi episode khi record xuất hiện.
  - [x] **H-01.2.1 · DONE/LOCAL:** đối chiếu và ghi trong [handoff G0 chung](../../handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md) các xung đột episode outpatient, producer/referral key, post-case charge bridge, clearance scope và field gaps của ready/completed/cancelled.
  - [ ] **H-01.2.2 · CONTRACT:** Vinh/Lộc/Huy chốt mapping và cùng-version producer/consumer fixtures; handoff còn OPEN cho tới khi canonical contract/spec và test links được cập nhật.
  - [x] **H-01.2.3 · DONE / HUY PRODUCER CONTRACT (2026-10-07):** tên/version/routing đã cố định: `surgery.case.created` cho planned charge sau tạo ca, `surgery.completed` cho actual reconciliation sau mổ; envelope version 1, producer `surgery-service`, exchange `mediflow.events`, routing key trùng eventType không hậu tố. Có fixture admission/outpatient cùng hai tình huống completion bổ sung (planned-difference/unknown-price). 78 focused serializer/Rabbit/PostgreSQL/architecture tests PASS, không failure/error/skip; [evidence](2026-10-07-huy-outbound-contracts.md#follow-up-close-huys-charge-event-naming-for-lộc). Lộc dùng [canonical contract](../../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md) và fixture sau pull; không còn chờ Huy đặt tên event. H-01.2.2/Billing effects/live delivery vẫn mở.
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
- [x] **H-01.6 · DONE/HANDOFF (2026-10-04):** cập nhật các handoff đã đăng ký theo producer code: Patient service-only exists đã có, Organization staff/department lookup và Surgery consumer cục bộ đã có; room/job-title vẫn OPEN trong Surgery decision handoff, Gateway Surgery route/Report DOCTOR role vẫn OPEN trong bootstrap/Huy care-finance handoff. Không đóng handoff tổng hay sửa production ngoài scope.

Phần tài liệu H-01.6 đã cập nhật ngày 2026-09-27 trong hai Huy handoff và registry: candidate Surgery, one-slip admission policy, missing finance/replay fields, generic Org lookup và Gateway role gaps. Checkbox còn mở cho xác nhận/fixture/test từ các owner và retirement đúng acceptance; không phải chưa viết handoff.

## 6. Surgery — backlog code chi tiết của Huy

<a id="surgery-backlog"></a>

**Phạm vi:** chỉ `backend/surgery-service` và docs/contracts do Huy phụ trách. Đường dẫn dưới đây tương đối với module; Java dưới `src/main/java/com/mediflow/surgery`, tests dưới `src/test/java/com/mediflow/surgery`. Tên lớp mới là đầu ra cần tạo, không phải tuyên bố source đã tồn tại.

**Đã có:** platform, domain models/rehydration, V1 core migration, JPA/JDBC adapters, receipts/inbox/outbox persistence, Patient/Organization staff/department HTTP lookup, generic Rabbit dispatcher, begin-preop/cancel controllers, checklist/consent và prepare-schedule application paths. **Chưa có:** create/referral/charge, prepare-schedule API, đủ readiness/START/COMPLETE workflow, clearance/room/eligibility authority và approved business event wire. `ReadinessSnapshot` vẫn không tự chứng minh clinical/Organization/finance authority.

**Cách đếm 2026-10-04:** 160 checklist *lá* thuộc H/S/P/R/X, 89 DONE và 71 OPEN (55,6% theo checklist, **không** phải độ sẵn sàng production). Nếu đếm cả 46 task cha sẽ phóng đại backlog; task cha chỉ đóng khi toàn bộ con/acceptance đạt. Mốc 80% cần thêm 39 task lá thật sự hoàn thành. X-01.1 vừa đóng là task cha của X-01.1.1 đã DONE nên không làm tăng số task lá đã xong. Các task OPEN gồm LOCAL, CONTRACT, OWNER và VERIFY, không độc lập hoặc tương đương kích thước; đặc biệt không tick mục liên service khi thiếu acceptance của owner. Trạng thái hiện hành ghi theo child ID và evidence §10.

### 6.0. Quy ước triển khai và cách lấy task

Mỗi subtask phải có: production output đúng layer → rule/transaction → test âm và retry/race liên quan → evidence ở §10. Thêm class rỗng hoặc viết test chưa chạy không đóng task. Nếu một subtask có nhánh LOCAL và CONTRACT, chỉ tick parent khi cả nhánh nằm trong V1 đã đạt; ghi phần đã xong bằng child ID/evidence.

| Lát cắt | Có thể làm độc lập | Chỉ chờ đúng phần nào |
|---|---|---|
| Model + persistence + reliability | S-02.1.2/.4/.5, S-02.2–6 theo thứ tự dependency; Patient adapter S-03.3.1 | Không chờ Billing/Gateway cho unit/PG/MQ tests nội bộ. Chưa đóng G1 bằng fixture tự viết. |
| Create/query + checklist/consent + draft schedule | S-04, S-05.1/.2, S-06.2 sau nền lưu trữ; test bằng port doubles | Referral/relationship proof, catalogue y khoa, quyền consent và Organization eligibility trước khi bật các command tương ứng. |
| READY + finalize + START | Engine/revision/transaction/race có thể code LOCAL sau models | Exact clearance, evidence freshness, role matrix và active room/staff lookup phải đạt G1 trước workflow thực. |
| COMPLETE/CANCEL + event delivery | Local outcome/audit/resource-release tests sau S-07 prerequisites | Wire outcome/charge, Inpatient reference, Billing reconciliation, Notification/Report fixtures trước delivery thật. |
| Root/Compose/Gateway và bật tích hợp | Gateway được giao trong task-scoped override 2026-10-05; route/roles và HTTP transport test đã có | Root/Compose hiện hữu không sửa; health/discovery và actual-service E2E S-01.4/X-01 vẫn cần kiểm chứng riêng. |

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
- [x] **S-01.4 · DONE/ACTUAL RUNTIME (2026-10-05):** shared reactor/DB/Compose foundation giữ nguyên; Gateway discovery route và exact roles đã được kiểm bằng packaged Eureka/Gateway/Surgery/Organization, hai DB riêng + Rabbit thật. 3 Failsafe cases pass: health/discovery/path/correlation, 401/403/404 cả Gateway/downstream, non-root Surgery container, scoped read + prepare draft + revoked capability denial. Bootstrap handoff đã retire; [quy tắc lâu dài](../../ai/services/gateway.md) và [lệnh chạy/evidence](2026-10-05-huy-50-task-execution.md). Không suy thành toàn bộ workflow G3.
- [x] **S-01.5 · DONE/VERIFY:** Surgery 166/166 tests (0 failure/error/skip; 2026-10-05), gồm PostgreSQL/RabbitMQ thật. Root reactor pass 1.696/1.696 trước lần rà soát Organization cuối; đây chưa phải actual-service Gateway/G3 evidence.
- [x] **S-01.6 · DONE/ACTIVATION GUARDS (2026-10-05):** toàn bộ controller hiện hữu require business gate; clearance queue/listener/durable worker chỉ được tạo khi business+consumer true; dispatcher/publisher require business+producer. Tất cả 8 tổ hợp consumer và 8 tổ hợp producer có context tests; disabled consumer không đăng ký rồi ACK mất event, producer-off không đánh dấu outbox đã gửi. Defaults false; không tự bật cutover hay release held Billing rows. Mọi adapter mới phải giữ cùng gate.
  - [x] **S-01.6.1 · LOCAL (2026-09-29):** dispatcher, Rabbit publisher và scheduler chỉ được tạo khi đồng thời `features.surgery.enabled=true` và `messaging.producer.enabled=true`; mặc định cả hai false. Context tests xác nhận thiếu một trong hai gate thì không có dispatcher. Chưa có listener/business event writer nên consumer gate chưa có adapter để bảo vệ; chưa bật writer.
  - [x] **S-01.6.2 · LOCAL (2026-10-01; mở rộng 2026-10-05):** business feature tests bảo vệ pre-op/cancel/query/schedule controller khi business gate off. Consumer registration + producer dispatch đã có riêng 8 context cases mỗi gate; xem parent S-01.6 đã kiểm chứng, không suy từ property binding.
- [x] **S-01.7 · DONE/HARNESS (2026-10-05):** blueprint/5 ArchUnit rules, real PostgreSQL 16/RabbitMQ tests, reusable canonical Billing/Organization fixtures, module Dockerfile non-root, README và .http khớp 5 route thật. Full Surgery 291 tests, 0 failure/error/skip; explicit Failsafe runtime profile có 3 actual multi-service tests pass. Harness không thay full lifecycle/producer contract acceptance.
  - [x] **S-01.7.1 · LOCAL ARCHITECTURE+DOCS PASS (2026-09-29):** ArchUnit kiểm tra domain purity, application không chạm adapter/SQL, driving adapters không bypass application, không phụ thuộc service nghiệp vụ khác và không có package-layer cycle (5 rules). README phản ánh đúng trạng thái Rabbit transport generic đang gate-off, không có event-specific publisher/consumer/API. Focused ArchitectureTest 5/5 và full module 124/124 pass; `.http`/Dockerfile API chưa tạo vì chưa có endpoint thật.

### S-02 — domain, schema và reliability foundation

**Đầu vào:** S-01, Huy-local choices H-01.3.2. **Không phải đợi toàn bộ S-03.** Schema core không phụ thuộc clearance wire chưa chốt; phần contract-specific thêm migration tiếp theo, không sửa checksum đã phát hành.
**Files:** `domain/model` (gồm enums), `domain/exception`, `application/port/out`, `infrastructure/persistence` (entity/repository/mapper/adapter theo blueprint), `infrastructure/messaging`, `src/main/resources/db/migration`. Không thêm cây package khác blueprint.

- [ ] **S-02.1 · LOCAL/CONTRACT — hoàn thiện aggregate và value objects:** từng child dưới đây có unit tests; không kéo JPA/validation annotations vào domain.
  - [x] **S-02.1.1 · DONE/LOCAL:** CareEpisode exact admission identity, initial lifecycle, boolean readiness AND, START recheck, invalidation, pre-start cancel; 13 domain tests. Chưa có readiness evidence/persistence/result invariant.
  - [x] **S-02.1.2 · DONE/LOCAL (2026-09-28):** `SurgeryChecklistTemplate/ItemDefinition/Snapshot/Item` immutable; template revision được chụp vào case, empty template/snapshot không hợp lệ, NOT_APPLICABLE không thỏa mục bắt buộc, không seed catalogue y khoa. `SurgeryConsentRecord` giữ hai loại consent và append-only sign/revoke audit; signer category chỉ là dữ liệu, chưa xác minh thẩm quyền. `SurgerySchedule/TeamAssignment` giữ room UUID, revision, `[start,end)`, duplicate staff bị từ chối và slot liền kề không overlap. Tests `SurgeryLocalModelsTest`; không có Organization lookup/reservation DB.
  - [ ] **S-02.1.3 · CONTRACT D12 — clearance value object:** sau S-03.2.1 khóa tuple/version/expiry, giữ source grant/revision và validity, phân biệt absent/pending/expired/revoked. Kiểm tra wrong target/episode/patient và out-of-order grant; không coi payment.completed hoặc boolean paid là clearance. Engine có thể test với port double trước khi decoder live tồn tại.
    - [x] **S-02.1.3a · CODE/LOCAL TEST:** immutable `SurgeryFinancialClearance` giữ exact case/patient/episode/admission/grant tuple, amount/currency và grant/expiry Instant; expiry exclusive, precision nanosecond. Đây là grant validity, chưa là tổng hợp quyền sau revoke/supersede.
  - [x] **S-02.1.4 · DONE/LOCAL (2026-09-28):** `SurgeryCase.restore` khôi phục state, mốc thời gian, readiness, status history, business revision và append-only revision audit; validate chuỗi transition/revision/status để từ chối DB snapshot không nhất quán, không sinh lại history khi restore. `recordBusinessMutation` tăng business revision và audit khi application đổi child. Tách business revision khỏi technical JPA `@Version` (chưa có adapter). `ReadinessSnapshot` giữ dependency IDs/revisions, validUntil và lý do thiếu guard; expiry đúng boundary làm snapshot hết hiệu lực. `SurgeryAuditActor` tách account UUID, staff UUID đã được adapter xác thực, hoặc producer hệ thống; transition/consent audit mang correlationId, không tạo UUID giả. Tests round-trip, revision child mutation, corrupted history, dependency/reason/expiry.
  - [x] **S-02.1.5 · DONE/LOCAL (2026-09-28):** `SurgeryPerformedItem/SurgeryResult` bất biến; actual end phải sau start và thời điểm ghi nhận không trước khi kết thúc; procedure/method/outcome/category là code string, số lượng dương, line ID duy nhất. Không có amount hoặc chức năng correction/partial-abort. Kết quả mang actor/correlation audit. Tests invalid quantity/time/duplicate line; unique result/case còn cần unique constraint ở S-02.2.
- [x] **S-02.2 · DONE/LOCAL (2026-09-28) — core migration:** V1 đã có case/history, checklist/consent, schedule/team/resource mutex/reservation, readiness/result, receipt/inbox/outbox và mapping JPA English camelCase ↔ SQL English snake_case theo ngoại lệ Huy trong `docs/ai/08`. Fresh PostgreSQL 16 migrate + Hibernate validate + constraint tests pass, gồm cancelled actor/reason, active consent, result uniqueness/quantity, schedule interval. Không seed cross-service master/paid facts. Clearance-specific schema chỉ thêm migration mới sau contract; không sửa V1 khi đã phát hành.
- [ ] **S-02.3 · PARTIAL/LOCAL sau S-02.2 — ports/JPA/transaction boundary:** case repository/JPA mapper `restore`, checklist/consent/result child persistence, schedule histories và begin-preop receipt transaction đã có PostgreSQL 16 round-trip/revision/replay/rollback tests. Port không lộ entity. Còn use-case transaction orchestration cho các command khác, concurrent first-referral retry/load-winner, cross-command lock protocol, bounded retry và crash/failure matrix; PageQuery/PageResult chỉ thêm khi query có yêu cầu. Technical version conflict → retry/conflict rõ, không lost update. Transaction phải bao case + child mutation + history + receipt + approved outbox intent.
  - [x] **S-02.3.1 · LOCAL PG PASS (2026-09-29):** immutable template/snapshot create+read; snapshot khởi tạo revision 0/PENDING, khóa case khi gắn, đối chiếu từng item với đúng template revision. Integration test `checklistTemplateSnapshotAndItemHistory_roundTripWithOptimisticRevision` chạy trên PostgreSQL xác nhận round-trip template/snapshot và lưu item history với revision tăng; stale revision bị từ chối, không nhân đôi history. Workflow checklist/receipt được kiểm chứng riêng ở S-05.1.1; evidence/source policy vẫn mở.
  - [x] **S-02.3.2 · LOCAL PG PASS (2026-09-29):** consent sign/revoke persistence, append-only audit, same-command readback, case lock và ACTIVE comparison; PostgreSQL test xác nhận duplicate active bị constraint chặn, audit SIGNED/REVOKED còn nguyên, re-consent sau revoke được lưu. Signer authority/revoke authorization vẫn chờ Vinh ở S-05.2.2.
  - [x] **S-02.3.3 · LOCAL PG PASS (2026-09-29):** immutable result + performed-item adapter; chỉ create khi IN_PROGRESS, lưu actual times/actor/correlation/items cùng transaction, identical replay trả lại kết quả, payload thay thế bị conflict. PostgreSQL test xác nhận hai performed items được đọc lại. COMPLETE/outbox orchestration vẫn ở S-07.2.1.
  - [x] **S-02.3.4 · LOCAL PG PASS (2026-09-29):** đọc từng schedule revision cùng đúng staff assignments từ history; integration test xác nhận revision cũ/mới giữ team riêng và reservation cũ vẫn RELEASED. Cross-command lock/release orchestration còn ở S-06.4/6.5.
  - [x] **S-02.3.5 · LOCAL UNIT+PG PASS (2026-09-29):** fingerprint SHA-256 length-prefixed, versioned binary receipt codec, replay identity checks; begin-preop claim/mutation/status history/case save/receipt cùng transaction. PostgreSQL test chứng minh rollback cả case và receipt khi lỗi trước outer commit, rồi commit + same-key replay chỉ có một receipt APPLIED. Race giữa concurrent claim và các failure points khác vẫn cần kiểm chứng.
- [ ] **S-02.4 · PARTIAL/PG+MQ (2026-10-05):** inbox/receipt durable có fingerprint, semantic dedupe/quarantine và replay/conflict. Clearance listener ACK chỉ sau committed APPLIED/REPLAYED/DEFERRED; early grant giữ PostgreSQL PENDING, bounded worker đọc lại raw bytes sau restart, lỗi xử lý defer 60s và không reopen terminal winner. Actual Rabbit/PostgreSQL tests phủ duplicate/early/wrong target/DLQ. Referral→create→pending recovery và full command/crash matrix vẫn OPEN; không xóa pending hay tự mổ từ grant.
- [ ] **S-02.5 · PARTIAL/LOCAL sau S-02.2 — outbox:** lưu serialized bytes, claim lease + attempt fencing, causal order, bounded backoff/quarantine, expired-lease recovery và returned retry đã có PostgreSQL tests. Gated generic dispatcher đã tách claim/network/result thành transaction riêng; Rabbit adapter dùng correlated confirm + mandatory return, không rewrite payload. Còn approved G1 wire bytes, producer command/outbox append cho event cụ thể và end-to-end dispatch/restart evidence. Không gọi Rabbit trong case DB transaction hoặc import code Pharmacy xuyên module.
  - [x] **S-02.5.1 · LOCAL Rabbit PASS (2026-09-29):** dispatcher success/nack/return unit cases; RabbitMQ Testcontainers xác nhận publisher ACK nhận được, nguyên byte JSON tới queue không đổi, correlation/version headers được set, và unroutable mandatory message được phân loại RETURNED. Scheduler/adapter vẫn off theo feature gates; đây không phải producer/consumer contract pass.
  - [x] **S-02.5.2 · LOCAL FAILURE-CLASSIFICATION PASS (2026-09-29):** publisher chuyển confirm timeout và Rabbit `AmqpException` (ví dụ broker không kết nối) thành retryable `SurgeryEventPublishException` với thông điệp an toàn; dispatcher gọi outbox retry, không mark PUBLISHED. Mock publisher tests pass; actual broker outage/process restart replay vẫn là VERIFY mở ở S-02.6.
- [ ] **S-02.6 · PARTIAL/VERIFY theo từng adapter — PG/MQ failure matrix:** đã pass fresh schema + ORM restore, receipt/outbox rollback, inbox replay và lease cũ, child checklist/consent/result rows + audit rollback cùng transaction, two-worker same-room và reversed-team-order races, adjacent/contained slot, IN_USE overrun, Rabbit ACK/mandatory return và retry từ Rabbit endpoint unreachable qua dispatcher mới. Bổ sung 2026-10-05: dừng/khởi động lại đúng broker container Testcontainers, pending bytes recovery sau confirm, crash sau confirm/trước DB mark với same-event replay và stale-token fence; PG expiry timeout/rollback/two-worker/cancel race. Còn crash bằng JVM process riêng, concurrent first-referral insert, full finalize/START/cancel/reschedule matrix và bounded deadlock retry toàn workflow; không suy suite subset là hoàn tất task.
  - [x] **S-02.6.1 · LOCAL PG+Rabbit RECOVERY PASS (2026-09-29):** Testcontainers nối PostgreSQL outbox thật với RabbitMQ thật: lượt đầu gửi tới TCP endpoint Rabbit unreachable/connection-refused, xác nhận publisher phân loại transient failure và row vẫn PENDING với backoff/attempt; dựng dispatcher instance mới trỏ tới RabbitMQ thật, retry sau backoff, nhận đúng persisted bytes và chỉ chuyển PUBLISHED sau confirm. Đây là worker-instance recovery sau endpoint outage, chưa mô phỏng dừng/khởi động lại broker container hoặc JVM process.

### S-03 — identity lookup, referral, clearance và event contracts

**Đầu vào:** Patient adapter có thể bắt đầu từ S-01; generic decoder/fixture harness có thể làm cùng S-02. Chỉ adapter gắn business effect mới cần persistence tương ứng. Mỗi contract đạt G1 riêng, không đợi tất cả service cùng live.
**Files:** `application/port/in|out`, `application/event`, `infrastructure/client`, driving consumers ở `messaging/consumer`, driven publisher/payload ở `infrastructure/messaging`, `src/test/resources/contracts`.

- [ ] **S-03.1 · CONTRACT D01/D02 — referral và charge bridge:** khóa producer/path, selected episode, stable surgeryRequestId, requester authority, planned codes/quantities và fingerprint clinical intent. HTTP/event cùng gọi create in-port; delivery timestamp/correlation không làm đổi intent. Referral khác post-case charge fact; tên/version/routing đã khóa ở H-01.2.3 (`surgery.case.created`, V1), không để hai producer cùng tạo charge. Acceptance còn mở: outpatient appointment/walk-in/admission referral authority, same intent từ hai paths, mismatch và duplicate fixture; Billing effects/creation runtime không đóng theo producer naming.
- [ ] **S-03.2 · LOCAL + CONTRACT D12 — clearance:** hai bước riêng:
  - [ ] **S-03.2.1 · CONTRACT Lộc/Vinh:** khóa `purpose=SURGERY`, exact target/episode/admission/patient, grant ID/revision, validity interval, revoke/supersede và freshness semantics. Chưa có revoke producer không tự tạo subscription/routing key. Đưa policy expiry/revoke/late grant và test bytes vào canonical contract.
  - [ ] **S-03.2.2 · PARTIAL/LIVE-ADAPTER GATED (2026-10-05):** strict Billing V1 decoder + dual-gated queue/listener/worker đã có. Validate full tuple của mọi purpose trước phân loại not-applicable; malformed/canonical UUID/version/producer sai → dedicated DLQ, transient failures bounded retry. Early SURGERY grant lưu durable pending, duplicate không lặp proof/invalidation; kiểm thử thật PostgreSQL/RabbitMQ. Grant-only chưa chứng minh revoke/supersede hay READY/START; flags default false, held Billing rows không được mở.
    - [x] **S-03.2.2a · CODE/FIXTURE/LOCAL TEST:** offline Surgery-only decoder đọc trực tiếp Billing `clearance-surgery.json`, strict V1/producer/target/numeric money/grant time; early event lưu PENDING, wrong patient quarantine, concurrent replay/immutable clearance-ID conflict có PG tests. Chưa bind queue; classification other-purpose, worker retry, revoke/supersede và READY/START giữ mở ở task cha.
- [ ] **S-03.3 · LOCAL + CONTRACT — REST adapters có resilience, không DB join:**
  - [x] **S-03.3.1 · DONE/LOCAL (2026-09-28) — Patient:** adapter dùng contract thật `/patients/{id}/exists`, service JWT ngắn hạn `type=service`/`role=SYSTEM`, correlation và Feign timeouts. Validate envelope/echo patientId; false/404 phân biệt với timeout/5xx/malformed/mismatch. Unit + HTTP stub tests pass; chưa gọi qua Gateway/prod deployment.
- [x] **S-03.3.2 · DONE/IDENTITY AUTHORITY (2026-10-05):** Surgery dùng service JWT ngắn hạn/correlation với generic staff/department và exact operating-room/capability endpoints. Giữ source IDs/revision/observation, validate envelope/echo/freshness/interval; room và capability phải đúng case department. Missing/outage/old-producer 404 → 503; inactive/revoked hoặc foreign department → denial không mutation. Canonical producer fixtures đọc cùng bytes; actual packaged Organization→Surgery Feign/Eureka + Gateway read/draft/revoke test pass. Required clinical composition/cardinality là task riêng S-06.1.2, không được suy từ job title.
    - [x] **S-03.3.2.1 · LOCAL UNIT PASS (2026-09-29; live lookup tiếp nối ở .2):** Surgery-local `OrganizationLookupPort` và typed snapshot cho ROOM/STAFF/DEPARTMENT (ACTIVE/INACTIVE/NOT_FOUND/UNKNOWN, observedAt, optional source revision/job-title code); tests xác nhận mọi state hợp lệ, normalization metadata và fail-fast cho identity/observation/job-title sai. Tại thời điểm này chưa có HTTP path, producer fields hay live adapter; xem .2 cho hợp đồng đã được bổ sung.
    - [x] **S-03.3.2.2 · LOCAL HTTP CONSUMER PASS / JOINT FIXTURE OPEN (2026-10-04):** `OrganizationLookupAdapter` gọi đúng hai endpoint service-only đã khóa, ký service JWT và truyền correlation, kiểm tra envelope/header/echo, phân biệt active/inactive/`exists=false`/404 với 5xx/phản hồi sai, giữ `jobTitle` và `staffDepartmentId` từ producer. Room lookup dừng an toàn vì chưa có contract; chưa quyết định role↔jobTitle. HTTP stub Surgery-local và application tests pass; không tự nhận là shared producer/consumer fixture hoặc live E2E. Parent S-03.3.2 vẫn OPEN.
  - [ ] **S-03.3.3 · PARTIAL CODE/FIXTURE — care relationship:** Inpatient đã có service-only exact admission lookup với patient/khoa/source-record/status/eligibility/row revision/observedAt. Surgery draft kiểm exact patient/khoa và medical window trước lock/mutation. Không dùng human GET hay tìm admission qua patientId. Outpatient/referral relationship, surgery request registration và bed placement vẫn chưa được chứng minh bởi lookup admission này; task giữ mở.
- [ ] **S-03.4 · CONTRACT D02/D07/D11 — outbound manifest:** mỗi charge/ready/completed/cancelled fact ghi schema, source/semantic key/revision, consumer matrix và SHA-256 fixture. READY mang immutable planned-time/schedule revision nhưng **chưa phải reserved SCHEDULED**; chốt với Notification cách biểu diễn provisional, invalidation/re-ready/reschedule. Completed tách controlled category khỏi clinical summary; aggregate Report không persist/log narrative. Nếu cần hạn chế transport summary, chốt version/channel cùng owner trước publish, không tự fork payload cùng version. Cancelled đủ department/episode/stage/operation identity; V1 không phát override/partial-abort/correction facts.
- [ ] **S-03.5 · PARTIAL/VERIFY từng contract:** Organization authority V1 đã có actual producer serialization + cùng raw fixture decoder/PG/Rabbit intake/job/worker/retry/DLQ pass (2026-10-06); các contract khác vẫn cần proof riêng. Producer serialization và consumer decode cùng raw bytes/hash/commit; fixtures valid/missing/null/wrong IDs/producer/unknown version, semantic duplicate có eventId mới, early/late/order reversal. Invalid/unsupported được classified, bounded retry/quarantine/DLQ; transient outage retry khác permanent malformed. Log correlation + safe source IDs, không payload lâm sàng/token. Không tick G1 bằng fixture tự viết hoặc decoder test đơn phía Huy.
- [ ] **S-03.6 · CONTRACT/OWNER Vinh — nối với consumer Inpatient đã có:** thống nhất đường đăng ký external-order reference `SURGERY ↔ surgeryCaseId ↔ admissionId ↔ surgeryRequestId` trước ready/completed/cancelled. Current consumer bắt buộc admissionId/reference; cần fixture OUTPATIENT → not-applicable thay vì malformed, late fact sau discharge, duplicate/out-of-order terminal và reference chưa tới. Vinh sửa consumer/reference path; Huy cung cấp source fixture/negative tests trong [handoff G0](../../handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md). Không tạo thêm event “case-created” tự phát hoặc ghi vào DB Inpatient.

### S-04 — create/referral, query và bắt đầu pre-op

**Đầu vào:** core persistence S-02.2/.3/.4, template snapshot S-02.1.2; identity ports S-03.3. Local application/web tests dùng doubles; command thật còn chờ S-03.1/.3 authority/charge contract. Không đợi READY/booking hoàn chỉnh mới viết create/query.
**Files:** command/query DTO records, create/get/list/begin-preop in-ports, application services/mappers, repositories, driving HTTP adapters ở `web` theo blueprint (không `infrastructure/web`).

- [ ] **S-04.1 · LOCAL + CONTRACT — input/authorization boundary:** phân biệt HTTP actor từ trusted access-token và system referral producer từ envelope; account UUID không phải staff UUID. ADMIN delegation phải explicit/audited, DOCTOR requester theo policy/claim; không body-supplied actor/role. Validate selected episode và procedure/template revision, planned items codes/positive quantities, authority proofs qua S-03.3. Appointment-backed giữ appointment episode ngay cả khi record xuất hiện. Lookup ngoài lock, commit kiểm tra lại local revision/freshness; upstream unavailable không success.
- [ ] **S-04.2 · LOCAL sau S-04.1 — transactional create:** claim stable surgeryRequestId, fingerprint business intent, tạo case REQUESTED + pinned checklist snapshot + creation history + receipt và approved charge outbox intent trong một transaction. Hai channel/replica đồng thời cùng intent trả cùng case; khác intent → conflict. Chưa khóa charge wire thì chỉ kiểm thử local domain intent, **không bật create thật với nhánh âm thầm bỏ charge**. Sau commit kích pending-event retry qua durable worker, không in-memory callback làm nguồn duy nhất. Acceptance: rollback mọi record khi một append thất bại.
- [x] **S-04.3 · DONE/READ+QUERY (2026-10-05):** list/detail ports + immutable redacted DTO/MapStruct/persistence projections, repeatable-read coherent response. ADMIN/MANAGER đọc toàn khoa; DOCTOR/NURSE dùng signed staffId + fresh current Organization department, không tin filter/department claim. Foreign detail opaque 404, filter ngoại khoa empty; outage/stale authority 503. UTC half-open filters, planned overlap, page0/20/max100 và stable requestedAt DESC/caseId ASC; không per-row REST/history N+1. Planned tách actual items; snapshotValidNow chỉ cached evidence. Unit/web/PG và actual Organization/Gateway read tests pass.
- [ ] **S-04.4 · VERIFY — create/read/web:** same key same/different payload; event/HTTP first-insert race; same patient khác episode; wrong association; template chưa được phê duyệt; failure outbox rollback. Web tests success/envelope/validation, unauthenticated/forbidden/not-found/conflict và upstream unavailable theo API conventions; assert 401/403 không chạy use case. Idempotent replay vẫn kiểm tra quyền và không gọi lại lookups/charge producer.
- [x] **S-04.5 · DONE/LOCAL — begin-preop command/API:** `REQUESTED→PREOP_IN_PROGRESS` yêu cầu verified human actor, expected case revision và idempotency receipt; chuyển đổi + status history + receipt cùng transaction. Thêm `POST /api/v1/surgery/cases/{id}/preop` cho ADMIN/DOCTOR, request chỉ nhận revision, actor lấy từ access token, response dùng envelope; `.http` khớp route. Test direct API/authorization/validation và application pass 24/24; missing case trả 404 `SURGERY_CASE_NOT_FOUND`. Feature flag vẫn mặc định false; Gateway/root chưa route. PostgreSQL regression sau DTO/API change đã chạy trong full Surgery suite 2026-10-04.
  - [x] **S-04.5.1 · POSTGRES PASS (2026-10-04):** `beginPreopPersistence_commitsCaseAndReceiptTogetherAndReplaysAfterCommit` và rollback case chạy trên PostgreSQL 16 Testcontainers sau đổi command actor DTO; full Surgery suite 151/151, 0 fail/error/skip với `-Dapi.version=1.44`.
- [x] **S-04.6 · DONE/CURRENT API CONVENTIONS (2026-10-05):** 3 mutation routes dùng trusted actor, English immutable DTO, reject unknown actor/ready/amount, Idempotency-Key<=160 và expected revisions. Original matching receipt replay; changed intent/revision 409, illegal transition422, missing404, unavailable503, typed auth401/403, invalid400 và generic redacted500 giữ correlation. .http/Gateway roles khớp runtime; không setter/JPA entity/free-form READY. API chưa triển khai phải kế thừa conventions, không được xem mục này là đã có create/START/COMPLETE.

### S-05 — pre-op checklist, consent, clearance và readiness thật

**Đầu vào:** S-04 create/preop, models/persistence S-02; draft schedule S-06.2, không phụ thuộc finalize S-06.4. Có thể code pure policies/commands local trước fixtures; chỉ rule do owner khác sở hữu mới chờ CONTRACT.
**Files:** checklist/consent/clearance/readiness models, policies, command/query services, repositories/ports, web DTOs và immutable evidence snapshots.

- [ ] **S-05.1 · LOCAL + CONTRACT — checklist evidence:**
  - [x] **S-05.1.1 · LOCAL UNIT+PG PASS / CONTRACT OPEN:** item revision, snapshot revision, append-only `preop_checklist_item_history`, optimistic update theo item/snapshot/case revision và receipt cùng transaction; chỉ đổi ở REQUESTED/PREOP hoặc invalidate READY/SCHEDULED; N/A fail-closed khi chưa có clinical policy. PostgreSQL test xác nhận item/snapshot revision + history và stale write không tạo history trùng; unit tests xác nhận receipt path khi ghi FAILED và fail-closed cho N/A. Acceptable evidence/source policy (Vinh), unknown/duplicate/cross-case matrix và rollback ở workflow checklist vẫn mở.
  - [ ] **S-05.1.2 · CONTRACT Vinh; LOCAL policy engine:** medical mandatory set, source/order/result revision, expiry/correction và NOT_APPLICABLE authority có fixture riêng. Manual attestation chỉ cho item/policy cho phép, không biến mọi item thành nút tick. Kết quả Lab/Pharmacy cần exact case/order/patient/episode; thiếu correlation/missing/stale/corrected evidence → not-ready. Không auto-complete checklist từ result event chung; consumer chỉ bind sau G1.
- [ ] **S-05.2 · LOCAL + CONTRACT — typed consents:**
  - [x] **S-05.2.1 · CODE COMPLETE/VERIFY OPEN (2026-09-28):** thêm application sign/revoke commands theo SURGERY/ANESTHESIA, actor recorder tách signer, same-key replay và payload conflict; active unique index vẫn là hàng rào; revoke append audit. READY/SCHEDULED consent change invalidates snapshot và release đúng schedule revision trong transaction. Unit tests xác nhận typed signer/recorder, từ chối sai case và giải phóng đúng schedule revision khi SCHEDULED; signer authority/revoke authorization vẫn fail-closed ở API gate đến S-05.2.2.
  - [ ] **S-05.2.2 · CONTRACT Vinh:** chốt signer/guardian/witness, document/evidence reference, expiry, ai được attestation/revoke và xử lý audit sau START. Coarse controller role không thay quyền ký hoặc quan hệ người giám hộ. Missing/unverifiable authority fail-closed; test forged actor/role, signer không hợp lệ, cross-case revoke, inactive/expired consent. Không suy đồng ý từ clinical note hoặc một consent dùng cho hai type.
- [ ] **S-05.3 · PARTIAL/BOTH-SIDES IMPLEMENTED (2026-10-06) — clearance projection/policy:** Billing đã có gated current-clearance lookup (net payments/refunds, exact ledger target, revoked/expiry); Surgery có strict service Feign adapter và mandatory lookup trước internal READY/finalize/START mutation locks, recheck freshness sau lock wait. Không chờ Lộc code lookup. Còn revoke/supersede event writer, production transaction separation/race fence và full lifecycle authority/API integration. Pending early grant không đủ quyền mổ, V1 không financial override; local DB lock **không** hứa khóa trạng thái Billing. Bằng chứng mới tại [execution ledger](2026-10-05-huy-50-task-execution.md#financial-authority-both-sides).
  - [x] **S-05.3a · CODE/LOCAL TEST:** V2 additive table + inbox/proof transaction, exact-case lock, same-grant replay không thêm proof, immutable grant conflict quarantine, inbox-finalize failure rollback/retry và exact nanosecond round-trip. Không tự READY/START; full revocation/readiness policy ở task cha chưa đóng.
  - [x] **S-05.3b · CODE/LOCAL TEST:** grant mới invalidates READY/SCHEDULED về PREOP, chỉ release đúng schedule/revision; unchanged grant không invalidate lần nữa, late grant không rollback IN_PROGRESS/terminal. 6 application tests + 2 PG scheduled/rollback tests; audit dùng `FINANCIAL_CLEARANCE_CHANGED` trong giới hạn 64 ký tự. Proof/inbox/case/resources commit hoặc rollback cùng nhau.
- [ ] **S-05.4 · LOCAL — evaluate/invalidate:** engine độc lập; READY transaction cần S-05.1–3/S-06.2. Shared invalidation helper được gọi từ checklist/consent; application test xác nhận checklist mutation khi SCHEDULED trả case về PREOP, xóa active snapshot và nhả đúng schedule revision; PostgreSQL integration mới xác nhận consent revoke cũng commit invalidation, consent audit, receipt và release chính xác reservation trong cùng luồng. Invalidation race/rollback tích hợp với mọi writer vẫn mở. Invalidation helper .3 có thể làm ngay sau core persistence/resource locks, không đợi READY evaluator, nên draft schedule không tạo vòng dependency.
  - [ ] **S-05.4.1 · LOCAL engine — sáu nhóm guard:** chỉ định hợp lệ, mandatory checklist, cả hai typed consents, eligible team, room/time schedule hợp lệ, exact finance đều đạt (bảy boolean hiện tại vì consent tách hai loại). Build reason codes + dependency revisions, validUntil=min expiry có nghĩa, không TTL tự nghĩ cho dữ liệu không có contract. External eligibility snapshots lấy trước transaction; lock/re-read local dependencies để loại stale evaluation. Không public API “set READY”.
    - [x] **S-05.4.1.1 · LOCAL DOMAIN UNIT PASS (2026-09-29):** readiness model tests từng guard false độc lập (indication, checklist, hai consent, team, schedule, finance), exact blocking reason và `isReady=false`; test snapshot expiry/dependency revisions cũng có. Chưa đóng application evidence aggregation, external freshness policy hoặc transactional READY.
  - [ ] **S-05.4.2 · LOCAL persistence; CONTRACT event — READY:** PREOP_IN_PROGRESS→READY, immutable snapshot + time/history/receipt + ready fact cùng transaction sau khi đủ guards. Cùng dependencies/evaluation retry không phát lại; readiness revision mới sau invalidation là fact mới theo S-03.4. Thiếu guards trả structured not-ready reasons, không làm case READY một phần.
  - [ ] **S-05.4.3 · PARTIAL/LOCAL sau S-02.3/S-06.3 — invalidation dùng chung:** checklist/consent/clearance/reschedule đã có shared protocol kiểm exact case/schedule/revision snapshot rồi đưa READY/SCHEDULED về PREOP, giữ history và nhả đúng booking trong cùng transaction. Bổ sung 2026-10-05: expiry worker riêng default OFF, bounded 1..100, Clock sau case lock, exact snapshot/validUntil, 5s transaction timeout; audit + release + case rollback cùng nhau, durable retry 5..300s và stale/replacement/started/terminal fences. 33 expiry tests gồm 11 PG thật pass. Bổ sung 2026-10-06: Organization room/capability hint qua strict V1 decoder + atomic inbox/source/V5 jobs, worker pin snapshot/schedule, three-gated Rabbit/DLQ, semantic replay/conflict và retry bounded; 19 actual PG/Rabbit cases pass. Full finalize/START fresh-authority/source-revision reconciliation, other external changes và invalidation notification wire tại S-03.4 vẫn OPEN; không tự đặt event key hoặc clinical TTL, không tự nhả IN_USE. READY/START vẫn phải tự check Clock.
    - [x] **S-05.4.3.1 · LOCAL UNIT+PG PASS:** checklist mutation và consent sign/revoke gọi cùng `SurgeryReadinessInvalidation`; ở SCHEDULED, clear active snapshot và release chính xác `scheduleId + revision`, giữ snapshot/history cũ; case/schedule/reservation/receipt và consent audit cùng transaction. Unit evidence: `updateChecklistItem_whenScheduled_invalidatesAndReleasesExactScheduleRevision`, `signConsent_whileScheduled_invalidatesReadinessAndReleasesExactScheduleRevision`; PostgreSQL evidence: `revokingConsentFromScheduledCase_invalidatesReadinessAndReleasesExactReservation` (đã chạy 2026-09-29). Các writer còn lại, multi-writer race và rollback matrix vẫn OPEN.
- [ ] **S-05.5 · PARTIAL/VERIFY — truth table + races:** truth table domain và expiry boundary/stale snapshot đã có. Bổ sung 2026-10-05: PG exact deadline/no TTL, two expiry workers, cancel↔expiry race, committed START/cancellation winner, snapshot replacement, audit rollback/resource retention và lock timeout/recovery. Còn PG race evaluate↔checklist/revoke/reschedule, real START command↔revoke/expiry và invalidation↔finalize; state/snapshot/history/booking/outbox phải cùng một kết quả hợp lệ. External source không có revision/freshness contract thì ghi integration gate chưa đạt, không gọi local race test là distributed guarantee.

### S-06 — draft schedule, resource locks và finalized booking

**Đầu vào:** case/preop + schedule models S-02/S-04. **Thứ tự không vòng lặp:** S-06.1/.2 draft → S-05.4 READY → S-06.4 finalize. S-06.3 resource lock engine làm ngay sau S-02.2, không cần Organization chạy thật.
**Files:** Schedule/Team/ResourceReservation models, scheduling ports/services, resource-lock/reservation JPA adapters, schema/indexes và schedule web endpoints.

- [ ] **S-06.1 · PARTIAL — LOCAL defaults PASS / CONTRACT eligibility OPEN:** các mặc định thời gian/reservation ở child 6.1.1 đã được implement và test; chưa bật finalize/API hoặc suy eligibility khi role↔jobTitle/required cardinality/room authority chưa được owner xác nhận.
  - [x] **S-06.1.1 · LOCAL UNIT+PG PASS:** dùng `Instant` UTC và khoảng half-open `[start,end)` (`endsAt > startsAt`), không thêm buffer ngầm; chỉ reserve từ case READY với schedule DRAFT khớp revision/nội dung và chuyển reservation + schedule FINALIZED trong một transaction; không có TTL job tự giải phóng booking, IN_USE vẫn chặn dù planned end đã qua. Bằng chứng: `SurgeryLocalModelsTest.schedule_usesHalfOpenIntervalsAndRejectsDuplicateStaff` và PostgreSQL `adjacentSlotIsAllowedButInUseOverrunBlocksFollowingSlot` (đã chạy 2026-09-29).
  - [ ] **S-06.1.2 · PARTIAL CODE/FIXTURE — Organization/Clinical eligibility:** room UUID/active-state authority và staff explicit capability đã có producer/consumer; doctor role cần DOCTOR + license, OR_NURSE cần NURSE nhưng không đủ nếu chưa có explicit grant. Required cardinality/multiple assignments/clinical team composition chưa được tự suy từ generic job title; finalize/START tiếp tục gated.
- [ ] **S-06.2 · PARTIAL/API+AUTHORITY (2026-10-05):** PUT draft đã có exact roles, DTO/receipt/revision/validation/error tests và transactional save/history/audit. Department/room/capability/admission lookups trước locks; room và từng capability phải đúng case department, full interval, active và fresh cả sau lock wait. Draft/replacement không reserve hoặc tự READY. Required-role/cardinality policy tại S-06.1.2 vẫn OPEN; lịch nháp không chứng minh đã đủ đội mổ.
  - [x] **S-06.2.1 · LOCAL PG PASS / CONTRACT OPEN:** schedule adapter cho phép ghi revision draft mới sau `RELEASED`, giữ nguyên history schedule/team và không tái sử dụng reservation cũ. PostgreSQL integration xác nhận row lịch trở về `DRAFT`, team-history của các revision được giữ nguyên, reservation revision cũ vẫn RELEASED và revision mới chưa reserve; chưa có finalize/API vì Organization room/job-title/eligibility contract còn OPEN.
  - [x] **S-06.2.2 · LOCAL UNIT+PG PASS (2026-09-29):** `PrepareSurgeryScheduleUseCase` + application service tạo/revise DRAFT với idempotent receipt, expected case/schedule revisions, active Department/Room/Staff snapshots, audit/revision và transaction PostgreSQL; 10 unit cases phủ success, stale revisions, receipt replay/payload conflict, lookup mismatch/unavailable/inactive và đảm bảo lookup trước row lock. PostgreSQL commit test xác nhận DRAFT/team/revision/history/receipt cùng commit, case vẫn PREOP và không có reservation; fault-injection test xác nhận audit failure rollback schedule/history/case revision/pending receipt. Chưa có controller/API/live Organization adapter; role/job-title mapping và finalization vẫn blocked theo contract.
- [ ] **S-06.3 · LOCAL sau S-02.2 — database resource-lock engine:**
  - [x] **S-06.3.1 · LOCAL LOCK PROTOCOL DONE (2026-10-06):** mutex unique ROOM/STAFF + sorted union old/new keys, case row lock trước resource lock; reservation mang `scheduleId + revision`, stale release không thả booking mới. Tất cả production reservation writes nằm trong một adapter; internal finalize/START/COMPLETE mới và reschedule/invalidate/cancel hiện hữu cùng dùng protocol này. START so exact set phòng/toàn ê-kíp, không chỉ count, và chặn foreign IN_USE. PG tests phủ reverse-team-order/same-room winner, lifecycle đầy đủ, adjacent overrun, forged booking set và rollback COMPLETE. Đây chỉ đóng schema/lock protocol LOCAL, không đóng clinical authority, public lifecycle, bounded deadlock retry/full distributed race matrix ở S-06.3.2/S-02.6; reference UUID vẫn không external FK hoặc duplicate room master.
  - [ ] **S-06.3.2 · PARTIAL/LOCAL — overlap + active use:** query half-open overlap cho room + từng staff, IN_USE overrun chặn ca kế dù planned end đã qua; PostgreSQL tests cho same-room race, cross-room same-staff, adjacent, fully contained room interval, overrun và lịch nhiều staff có một người trùng trong interval bị chứa hoàn toàn (booking thua không để lại partial reservation). Còn race matrix đa tổ hợp, bounded retry lock timeout/deadlock và command receipt trong finalize orchestration.
- [ ] **S-06.4 · LOCAL sau S-05.4/S-06.3 — finalize/reschedule/release:**
  - [ ] **S-06.4.1 · LOCAL — finalize:** explicit command chỉ READY + exact schedule/readiness/dependency revisions. Revalidate eligible/fresh và slot dưới locks; tạo reservations cho room + toàn team, READY→SCHEDULED + audit/receipt atomic. Cạnh tranh thua không để partial team booking. Không gọi ready event thành bằng chứng đã reserve.
  - [x] **S-06.4.2 · DONE/RESCHEDULE (2026-10-05):** replacement authority và expected case/schedule revision được kiểm trước mutation, freshness rechecked sau case lock. READY/SCHEDULED invalidate về PREOP, release exact old booked revision, persist replacement DRAFT/history/case audit/receipt atomic; không giữ SCHEDULED hay reserve lịch mới. Replay original; stale readiness dependency/foreign schedule conflict không release. Unit + actual PostgreSQL success/audit-write rollback/replay/two-replacement race pass, không lost update/partial booking. Cần re-READY/finalize qua task riêng.
- [ ] **S-06.5 · VERIFY — real PostgreSQL two-worker tests:** same room cùng/partial/contained interval, khác room cùng staff, adjacent allowed, nhiều staff reverse order, concurrent first mutex insert, finalize↔cancel, reschedule↔finalize và rollback. Một winner với full resources, không partial booking; no deadlock không giới hạn; stale release không đụng new revision. START next case bị chặn khi previous case overrun; chỉ COMPLETE release IN_USE trong V1.

### S-07 — start, complete, pre-start cancel; không override V1

**Outbound update 2026-10-07:** typed V1 case.created/READY/invalidation/COMPLETED/CANCELLED,
ten producer fixtures and V7 HELD transaction capture are implemented. All seven invalidation
paths capture the exact old snapshot/schedule after the final case save. This closes the missing
Huy wire shape/local capture portion, not referral creation, public lifecycle, clinical policies,
Billing/Notification workflow or Inpatient reference/outpatient/late acceptance. Current evidence:
[outbound execution](2026-10-07-huy-outbound-contracts.md). Parent/combined checkboxes stay open.

**Đầu vào:** persisted guards/schedule S-05/06; receipt/history/outbox S-02. Local command tests không chờ consumer runtime, nhưng live delivery chờ S-03.4/.6 và G1.
**Files:** lifecycle in-ports/DTO/services, result/items and cancellation persistence, web controllers, event mappers và end-to-end module tests.

- [ ] **S-07.1 · LOCAL sau S-05.4/S-06.4 — START:** chỉ SCHEDULED, exact expected revision/receipt. Lấy authoritative snapshots theo freshness policy ngoài lock, sau đó case/resources lock + re-read consent/checklist/clearance/schedule revisions và Clock; reservation phải thuộc đúng case/revision, room/staff không IN_USE bởi case khác. IN_PROGRESS + start time/audit + resource IN_USE atomic. Retry giữ start time; START từ READY, expired grant hoặc revoked anesthesia bị chặn. Failed guard xử lý invalidation theo S-05.4.3, không trả success hoặc mất audit do rollback.
- [ ] **S-07.2 · LOCAL + CONTRACT — COMPLETE:**
  - [ ] **S-07.2.1 · LOCAL sau S-02.1.5/S-07.1:** validate IN_PROGRESS, actual times/recorded time, actual procedure/method/outcome/category, one result/case và positive performed quantities. Lưu result/items + COMPLETED + history/receipt + release reservations/IN_USE + approved completed outbox atomically. Sửa result/second completion khác payload conflict; không tạo correction ngầm.
  - [ ] **S-07.2.2 · CONTRACT Lộc/Vinh:** chốt item identity khi nhiều item dùng cùng priceCode, planned↔actual reconciliation, supported catalogue và clinical summary/category. Không tự gộp line làm mất nghiệp vụ, tính tiền, sửa invoice, gọi adjustment là refund hoặc emit zero-price khi mã chưa biết. Fixture consumer Billing/Inpatient/Report cùng operation/source identity; Report không lưu clinical narrative.
- [ ] **S-07.3 · PARTIAL/LOCAL — chứng minh không financial override trong V1:** không endpoint/DTO flag/role shortcut hoặc fallback cho phép thiếu clearance; ADMIN cũng không bypass. Domain test xác nhận thiếu từng consent hoặc financial clearance đều block. Còn controller/request authorization tests khi READY/START APIs tồn tại; không kết luận từ absence-of-API là security acceptance. **DEFERRED V1:** FINANCIAL_EMERGENCY implementation (approver/self-approval/expiry/receivable) chỉ mở bằng task phiên bản sau với Vinh/Lộc; không giữ nó như blocker V1.
  - [x] **S-07.3.1 · LOCAL UNIT PASS (2026-09-29):** `readinessSnapshot_requiresBothTypedConsentsAndFinancialClearance` xác nhận thiếu Surgery consent, thiếu Anesthesia consent hoặc thiếu financial clearance mỗi trường hợp đều tạo reason riêng và `isReady=false`. Chưa phải authorization/controller test và không chứng minh toàn bộ workflow.
- [ ] **S-07.4 · LOCAL + CONTRACT — pre-start CANCEL:** REQUESTED/PREOP/READY/SCHEDULED mới được cancel; derive cancellationStage từ prior persisted state, actor/reason/operationId do trusted command context. CANCELLED + invalidate active readiness + release đúng reservations + history/receipt + approved cancelled fact atomic; preserve source/ledger refs. Same command replay stable, different payload conflict. IN_PROGRESS/COMPLETED reject; post-start abort/result correction **DEFERRED V1**, không thêm state/payload ngoài contract. Billing adjustment consumer acceptance theo S-03.4, không direct Billing DB/HTTP refund.
  - [x] **S-07.4.1 · DONE/LOCAL CODE + API:** thêm `CancelSurgeryUseCase`, transactional service và feature-gated `POST /api/v1/surgery/cases/{id}/cancel` cho ADMIN/DOCTOR. Request English DTO mang `expectedCaseRevision`/`reason`; `Idempotency-Key` bắt buộc (tối đa 160 ký tự); actor lấy từ JWT đã xác thực. Hủy SCHEDULED phải khớp chính xác `scheduleId + revision` trong readiness dependency trước khi release. Audit giữ prior status để derive cancellation stage; active readiness pointer clear nhưng snapshot history được giữ. Không phát event hay tạo financial side effect. Unit, HTTP/security, architecture và PostgreSQL success/rollback tests PASS (2026-10-04).
  - [ ] **S-07.4.2 · LOCAL CAPTURE IMPLEMENTED / CONSUMER OPEN:** V7 cancelled wire identity/payload và atomic HELD capture bổ sung 2026-10-07, giữ exact retry/stage/actor. Billing/Inpatient/Notification runtime acceptance và G1/G3 vẫn mở; HELD không phải đã phát.
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
- [x] **P-01.3 · DONE/VERIFY:** baseline trước refactor P-03 và regression sau V19 đã chạy: Pharmacy full suite 404/404, 0 fail/error/skip (2026-10-04), gồm PostgreSQL/RabbitMQ Testcontainers. Không đổi Billing wire hoặc receipt policy.

### P-02 — context/schema/DTO/event additive, bắt đầu được trước producer live

**Gate:** Pharmacy V2 đã implementation-ready; xử lý gap §3.2 theo slice. **Trạng thái:** context/flag/schema foundation và request DTO boundary đã có; V1 writer/projections chưa có và vẫn fail-closed.
**Files:** Prescription/CareContext/CareEpisode, request/command/response/mappers, JPA entity/adapter, migrations, application/event, publisher, config và fixture tests.

- [x] **P-02.1 · DONE/LOCAL:** domain value objects/version 0/1, flag mặc định false và validation matrix legacy vs v1 đã có pure tests. ADMISSION yêu cầu admissionId=careEpisodeId; OUTPATIENT không có admissionId/không suy careEpisodeId từ recordId. Chưa thay request/DB/writer.
- [ ] **P-02.2 · PARTIAL/LOCAL:** V14 care metadata/compatibility, V15 admission context/event ledger, V16 clearance/target fence và V19 medical-discharge projection đã pass PostgreSQL. Transfer/freshness schema/policy và live V1 writer vẫn OPEN; không đồng nhất migration PASS với activation.
  - [x] **P-02.2.1 · LOCAL SCHEMA+ORM PASS (2026-09-29):** V14 additive migration, domain/persistence mapping cho context version 0/1, V13 legacy-row preservation, outpatient/admission exact-tuple constraints và PostgreSQL migration/round-trip tests. V1 writer/API/event, clearance grant và active-admission projections vẫn tắt/chưa có; xem §10.1.
  - [x] **P-02.2.2 · LOCAL V15 POSTGRES PASS (2026-10-01):** admission projection không FK tới prescription/DB khác, closure tombstone trước start, paired constraints, event/source fingerprints, row lock + version check và nanosecond source time preservation. `AdmissionMedicationContextPostgresTest` 5/5 pass; migration fresh/legacy compatibility xanh. Đây là offline projection, chưa live dispense authorization.
  - [x] **P-02.2.3 · LOCAL POSTGRES PASS (2026-10-02):** V16 immutable early grant, exact time/expiry, event/semantic dedupe, pending/verified, concurrency/rollback đã chạy thật. Fresh/legacy upgrade tới V18 giữ compatibility. Source approval/activation không nằm trong checkbox này.
  - [ ] **P-02.2.4 · CONTRACT sau D08/D12:** eligibility/revocation-specific state/schema theo medical-discharge/transfer/freshness và revoke/supersede được owner chốt; không biến start/close projection thành permission live.
- [x] **P-02.3 · DONE/LOCAL DTO boundary:** backward-compatible request v0 giữ nguyên; v1 chọn rõ bằng `careContractVersion=1` và bắt buộc đủ care context/episode/priceCode; thiếu hoặc sai tuple trả validation error, không silently downgrade. `prescribedDate`, doctor claim authorization và admin delegation không đổi; DTO không có giá hoặc `dispensedBy`. V1 request qua application hiện trả `PHARMACY_CARE_FINANCE_V2_UNAVAILABLE` trước bất kỳ stock/event side effect nào cho tới khi projection/writer sẵn sàng.
  - [x] **P-02.3.1 · LOCAL UNIT PASS (2026-10-01):** 5 DTO validation cases xác nhận V0 backward compatibility, valid outpatient V1 không cần recordId, selected/missing V1 metadata và V1 metadata thiếu selector không bị hiểu thành V0, admissionId phải khớp careEpisodeId; application test xác nhận V1 fail-closed trước stock/persistence/outbox. Cùng regression suite kiểm payment path bên dưới: 24 tests, 0 failure/error/skip.
- [x] **P-02.4 · DONE/LOCAL OFFLINE CODEC:** V1 DTO/codec riêng cho created/filled/failed/cancelled/expired đã có; đủ exact context/episode, priced snapshot/total/source/time, filled yêu cầu dispenseId. Reject unknown version/producer/routing, flat V0 và invalid identity/total; không rewrite outbox V0. `PrescriptionCareEventCodecTest` 17/17 pass cả outpatient/admission và 5 proposal fixture bytes. Publisher wiring/owner acceptance vẫn P-02.5.
- [ ] **P-02.5 · PARTIAL LOCAL/CONTRACT:** internal held create/dispense/cancel/expiry/stock-failure đã nối mutation transaction; canonical fixture approval, public adapters và live delivery còn mở. Consumer ngoài scope cần handoff acceptance.
  - [x] **P-02.5.1 · LOCAL PROPOSAL FIXTURES PASS (2026-10-01):** năm fixture V1 tại Pharmacy `contracts/care-finance-v1/`, round-trip qua real codec và README nêu rõ alternative scenarios/NOT LIVE. Active handoff đã yêu cầu consumer owners đọc cùng bytes. Không ghi là canonical cross-owner acceptance hoặc live producer tests.
  - [x] **P-02.5.2 · LOCAL CODE/UNIT PASS (2026-10-02):** V17 nullable historical name + explicit terminal ISO business time (không backfill/updated_at fallback); pure factory cho đủ năm lifecycle và locked Rx→slip capture hook bắt buộc caller transaction. Filled identity/state/time và terminal reason đến từ persisted evidence; caller không truyền dispenseId/price/name. Held V1 outbox writer giữ immutable bytes/ID, một created/một terminal semantic key; DB check + claim + lease guard không cho publish, admin replay không bật hold. Legacy defaults/wire unchanged. Đây là hook chưa được gọi bởi lifecycle workflow, không phải atomic V1 stock implementation.
  - [x] **P-02.5.3 · LOCAL POSTGRES PASS (2026-10-02):** writer prerequisite/idempotency/conflicts, V0 compatibility, replay/lease/retention hold, rollback/retry/mandatory transaction và exact lifecycle/name reload đã chạy thật. V1 delivery vẫn bị DB hold.
  - [x] **P-02.5.4 · LOCAL HELD CREATION:** V18 command receipt/fence + length-framed actor/intent SHA-256; DOCTOR self/ADMIN delegation kiểm trước replay; sorted stock locks, fresh expiry/availability, server price/name snapshots, Rx/reservations/slip/held CREATED/receipt chung transaction. Same key/concurrent retry giữ original ID, changed intent conflict; outbox error rollback toàn bộ. Internal only, không tự chứng nhận patient/episode authority hoặc bật public API.
  - [ ] **P-02.5.5 · CONTRACT/ACTIVATION:** owner-approved five-event bytes, Billing adjustment và identity/episode authority; sau G1 mới nối public create/terminal adapters, binding/dispatch và reviewed hold-release migration. Không enable rows bằng tay.
- [x] **P-02.6 · LOCAL REGRESSION/POSTGRES (2026-10-02):** fresh/legacy upgrade tới V18, old DTO/context constraints, V0 dispatch/lease/held fences và flag-off regressions chạy thật. Public V1/admission vẫn fail-closed; không phải G1/G3 acceptance.

Acceptance local: dữ liệu cũ không bị đổi ngữ nghĩa, context invalid không được persist, actor và price authority giữ nguyên. G1/G3 riêng cho OUTPATIENT V1 và ADMISSION; không cần đợi Surgery mới code phần này.

### P-03 — authorizer theo context, projection và cấp thuốc atomic

**Gate:** P-02; G1 Billing cho outpatient V1, G1 Inpatient cho admission. Không dùng một paid boolean chung.
**Files:** ReactToCareFinanceUseCase, authorization policy/port, clearance/admission commands & repositories, consumer adapters, DispenseApplicationService/TransactionService và failure/cancel/expiry paths.

- [ ] **P-03.1 · PARTIAL/LOCAL sau P-02.1:** V0 receipt path giữ nguyên; V1 outpatient có internal clearance/stock/held writer nhưng chưa public/runtime activation. V1 admission cần eligible context đúng patient/khoa; offline projector chưa medical-discharge/transfer/freshness/dispense wiring. Compatibility payment/executor chỉ V0; one-slip-per-prescription không đổi.
  - [x] **P-03.1.1 · LOCAL UNIT PASS (2026-10-01):** `PaymentApplicationService` từ chối V1 outpatient và admission trước receipt claim, processed-event claim hoặc dispense; legacy V0 regression vẫn pass. Exact clearance/admission authorization tiếp tục OPEN theo D08/D12.
  - [x] **P-03.1.2 · LOCAL UNIT PASS (2026-10-02):** locked V0 dispense executor từ chối mọi prescription V1 trước slip/stock/event effects, kể cả caller có legacy receipt hoặc bypass orchestrator. Không đổi receipt V0 và chưa cấp quyền V1.
  - [ ] **P-03.1.3 · CONTRACT + LOCAL WIRING sau D08:** admission create/dispense authorizer với exact eligible tuple và medical-discharge/transfer/freshness policy; không dùng outpatient grant hoặc active=true inference.
- [ ] **P-03.2 · PARTIAL/LOCAL/CONTRACT D12:** clearance domain/decoder/transaction storage/pending và local authorization primitive đã code và pass PostgreSQL. Huy chọn AUTHORIZE ONLY (không auto-dispense/PaymentReceipt). Immutable grant time/expiry/revocation producer approval và live writer/binding còn OPEN.
  - [x] **P-03.2.1 · LOCAL UNIT PASS (2026-10-02):** exact purpose/target/patient/episode, V0/admission reject, immutable grant IDs/snapshot, expiry-exclusive/future grant, numeric money precision, strict duplicate-key/trailing JSON, early pending, duplicate delivery và terminal-prescription denial. 25 domain/application/decoder cases pass; inline consumer examples không phải fixture Billing đã duyệt.
  - [x] **P-03.2.2 · LOCAL POSTGRES PASS (2026-10-02):** pending→late exact match, expiry/patient mismatch, delivery/semantic/nanos conflicts, concurrency, rollback/retry và mandatory transaction chạy thật. Inline grants không phải Billing producer fixture.
  - [ ] **P-03.2.3 · PARTIAL/GATED ACTUAL MQ (2026-10-05):** Pharmacy đã đọc cùng raw PRESCRIPTION bytes của Billing, dual-gated listener (care-finance-v2 + clearance-consumer), strict routing/type/producer/full other-purpose tuple, canonical UUID/payment method và dedicated durable DLQ. PostgreSQL/Rabbit tests phủ duplicate early PENDING, other-purpose no-effect, mismatch rollback/DLQ, storage failure bounded retry và retained-byte replay. Không auto-dispense, không mở held outbox. Request issuance/revoke/cutover và public V1 writer acceptance vẫn OPEN.
    - [x] **P-03.2.3a · PRODUCER/FIXTURE/LOCAL TEST (override):** Billing payment transaction sinh exact PRESCRIPTION clearance sau full payment và held V1 outbox; Pharmacy decoder đọc cùng producer fixture. Request issuance, revoke, live intake/denial/DLQ/cutover ở task cha vẫn mở.
- [ ] **P-03.3 · LOCAL/CONTRACT D08/D12:** `admission.started`, `discharge.medically.approved` và `admission.closed` projection bằng exact admissionId; duplicates/out-of-order/late-start không reopen. Khóa patient và department relation; medical discharge chấm dứt eligibility theo fact producer, còn transfer/freshness chưa có fact nên live admission authority vẫn fail-closed. Pending event đã lưu khác applied marker.
  - [x] **P-03.3.1 · LOCAL DOMAIN/DECODER/STORE PASS (2026-10-01):** 15 tests domain/application/actual Inpatient fixture/PG chứng minh exact tuple, duplicate/conflict, close-before-start/reload và start+close concurrent kết thúc CLOSED. Closure được persist như tombstone, không phải claimed-then-dropped pending. Chưa mở queue/authorization; medical-discharge/transfer/freshness vẫn OPEN.
  - [ ] **P-03.3.2 · CONTRACT/LOCAL POLICY:** medical discharge đã khóa và projection local ở .3; Vinh còn chốt transfer/freshness/reconciliation, Huy còn live consumer và close/discharge↔dispense race, không active=true vô thời hạn.
  - [x] **P-03.3.3 · LOCAL PRODUCER-BYTE + POSTGRES PASS (2026-10-04):** Pharmacy decode trực tiếp fixture `discharge.medically.approved.v1.json` của Inpatient, lưu fact singleton/tombstone qua V19 và giữ source instant chính xác. Domain từ chối conflicting discharge, sai patient, discharge trước start hoặc sau close; `requireActive` từ chối ngay sau medical discharge kể cả close chưa tới, late start không reopen. Unit/decoder/PG tests pass; chưa bind Rabbit hay bật admission create/dispense và không coi start department là placement authority sau transfer.
- [ ] **P-03.4 · LOCAL sau P-03.1–3:** check authorization snapshot/version và expiry dưới transaction/lock phù hợp với dispense; active-context update tranh dispense cho outcome theo thứ tự commit. Stock/reservation/Rx/slip/filled outbox cùng commit; không HTTP dưới stock lock.
  - [x] **P-03.4.1 · LOCAL PRIMITIVE UNIT PASS (2026-10-02):** clearance check bắt buộc caller transaction, clock đọc sau target/grant lock waits; chưa token/API độc lập và chưa wiring vào V1 stock/outbox. V0 executor khóa đủ stock/reservation rồi dùng một fresh timestamp kiểm toàn bộ TTL trước effect; expiry-during-wait unit case pass. P-03.4 parent vẫn OPEN cho atomic V1 workflow/real race.
  - [x] **P-03.4.2 · LOCAL INTERNAL CODE / UNIT PASS (2026-10-02):** internal outpatient executor nối clearance→stock/reservation→Rx/slip exact business time→held FILLED trong một transaction. Staff/account command bắt buộc, không auto-dispense; sorted whole-stock locks và fresh temporal checks sau grant lock/write waits, validate mọi line trước effect. Retry yêu cầu matching persisted proof + held FILLED, không event ID mới/trừ kho lại. Legacy receipt/filled/failure/refund không được gọi. Chưa public/API/payment/listener activation; create/admission/terminal failure paths còn OPEN.
  - [x] **P-03.4.3 · LOCAL POSTGRES PASS (2026-10-02):** actual outpatient commit, writer-error rollback/retry, nanos reload, concurrency, denial/missing proof và internal create→exact grant→dispense chạy thật. Module-local grant seed không phải Billing wire E2E.
  - [ ] **P-03.4.4 · VERIFY sau admission authority/live G1:** admission close/transfer/discharge tranh dispense + actor/API/queue restart/DLQ qua producer/consumer thật; outpatient PG subset không đóng mục này.
- [ ] **P-03.5 · LOCAL/CONTRACT D08:** cancel/expiry/failed dispense giải phóng reservation và phát exact charge-adjustment fact. Admission chưa prepaid không tạo invoiceId giả/refund intent kiểu cũ. Authorization denial không bị catch như stock failure rồi hủy đơn/bù trừ ngoài ý muốn; phân loại lỗi trước dùng lại RecordDispenseFailureService.
  - [x] **P-03.5.1 · LOCAL UNIT PASS (2026-10-02):** typed `DispenseAuthorizationException` bỏ qua legacy failure/compensation writer; denied V1 không release reservation, mark FAILED hoặc phát refund intent. Business stock failure V0 vẫn giữ invoice từ durable receipt. V1 lifecycle/charge-adjustment paths và owner semantics còn OPEN.
  - [x] **P-03.5.2 · LOCAL UNIT PASS (2026-10-02):** chặn direct V1 cancel/failure/late-payment compensation trước slip/reservation/claim/outbox V0; expiry job V0 skip V1 không effect. Terminal transitions lưu explicit lifecycleAt riêng và validate timestamp trước state mutation; factory từ chối audit-time fallback. Không tự sinh invoice/refund hoặc mở quyền dispense. Full V1 workflow/adjustment acceptance còn OPEN.
  - [x] **P-03.5.3 · LOCAL HELD CANCEL/EXPIRY:** author-owned cancel + whole-order expiry sau lock waits; reservations/Rx/slip/held terminal cùng commit, retry cần matching proof, changed cancellation actor/reason conflict. No stock increment/refund. PG success/rollback/nanos retry/cancel-dispense race; expiry không dùng stale batch clock.
  - [x] **P-03.5.4 · LOCAL STOCK FAILURE:** REQUIRES_NEW command sau rollback re-lock/re-authorize/re-evaluate definitive current shortage/expiry, không nhận exception text/reason tùy ý. Healthy stock/terminal winner không bị stale failure overwrite; missing grant/structure không FAILED. Whole release/proof/held failed atomic, writer failure rollback; không V0 receipt/refund/decrement.
  - [ ] **P-03.5.5 · CONTRACT/G1:** Billing/consumers duyệt adjustment semantics cho held cancelled/expired/failed; admission live terminal/late compensation sau D08. Local failure fact không chứng minh đã hoàn tiền.
- [ ] **P-03.6 · VERIFY:** spec tests outpatientWithoutClearance, admissionContextMismatch, closedAdmission, duplicateClearance, repeatedCommand, stockOutboxAtomic; thêm wrong purpose/expired grant, payment+clearance race theo version, close/dispense race, pending restart, context event round-trip và DLQ.

Acceptance: một prescription chỉ một stock effect, không nhầm episode/permission; compensation là fact cho Billing quyết định, không Pharmacy tự hoàn tiền. Outpatient v0 suite phải giữ xanh.

## 8. Report — contribution/read model V2 riêng, không đổi nghĩa báo cáo cũ

### R-01 — event routing, contributions, pending và replay foundation

**Gate:** Report V2 cho phép additive offline; D09/D11 chỉ chặn phần cần data/policy chưa rõ.
**Files:** messaging/consumer, application commands/ports/projectors, domain contribution/delta, infrastructure/persistence/config, migrations và contract tests.

- [x] **R-01.1 · DONE/LOCAL:** đã dựng namespace/commands/port V2, flag mặc định false và decoder harness offline; unknown version/type/producer/source bị reject, V2 envelope không fallback vào consumer legacy. Giữ nguyên 5 binding/3 API; chưa bind V2 live. Surgery source identity chưa được thêm do D07/D11 chưa khóa.
  - [x] **R-01.1.1 · LOCAL FIXTURE-BYTES PASS (2026-09-29):** decoder test đọc trực tiếp fixture bytes hiện có của `medicalrecord.completed.v1` (Clinical) và `lab.result.created.v1` (Lab), xác nhận producer/source IDs và disposition/resultVersion qua cùng decoder V2. Không sao chép payload vào fixture giả và không sửa service producer; chưa phải owner-approved shared fixture/hash hoặc G1 consumer sign-off.
  - [x] **R-01.1.2 · LOCAL ADMISSION-FIXTURE PASS (2026-10-01):** Report decoder đọc trực tiếp `lab.result.created.admission.v1.json` từ Lab và giữ đúng `labId`, `careEpisodeType=ADMISSION`, `careEpisodeId` khác chính xác với `recordId`; 11 decoder tests pass. Đây chỉ là same-byte local decoder evidence, chưa phải projection effect/owner G1 acceptance.
  - [x] **R-01.1.3 · LOCAL INPATIENT-LIFECYCLE FIXTURE PASS (2026-10-01):** decoder đọc trực tiếp Inpatient `admission.started.v1.json` và `admission.closed.v1.json`, xác nhận cùng admission source ID, producer/type và payload timestamps/settlement ID từ đúng bytes. Decoder suite hiện 12 tests pass. Chưa có Report projection, D10 LOS/department-transfer semantics hoặc owner-approved replay fixture.
- [ ] **R-01.2 · PARTIAL/LOCAL sau H-01.5:** V6/V7 đã chạy PostgreSQL, tách event provenance và business key; hospital NULL scope không sentinel. Operational global source/revision/metric key ngăn changed department đếm lần hai; financial allocation keys không đổi. Parent còn H-01.5 producer source/revision/correction và financial expected totals.
  - [x] **R-01.2.1 · LOCAL SCHEMA/STATIC PASS (2026-10-01):** `V6__care_finance_projections.sql` thêm bốn bảng V2; unique semantic keys không dựa riêng vào eventId; daily department/hospital scope dùng `NULLS NOT DISTINCT`, không dùng UUID sentinel; source revision phải dương và episode tuple được kiểm tra. `ReportMigrationSchemaTest` 3/3 pass. Đây là Huy-local persistence shape, không phải producer contract approval.
  - [x] **R-01.2.2 · LOCAL POSTGRES PASS (2026-10-01):** `ReportMigrationPostgresTest` 5/5 pass V6/V7 fresh/compatibility, business uniqueness/revision/null-vs-zero scope và cả hai hướng incomplete episode tuple. Fix SQL CHECK UNKNOWN loophole khi có episodeId nhưng thiếu type. Owner source/revision mapping và producer expected totals là gate riêng, chưa đóng.
  - [ ] **R-01.2.3 · CONTRACT/MAPPING:** Lộc/Vinh xác nhận exact transaction/refund/settlement/result/dispense IDs, revision/supersedes, first-operation vs correction và expected totals. Typed kernel input không thay cho actual accepted producer mapper.
- [ ] **R-01.3 · LOCAL sau keys:** transaction claim + contribution + hai scopes department/hospital; atomic insert/upsert và lock order ổn định; no JVM mutex. Refund-before-original/close-before-start vào pending có exact original ref và retry/checkpoint, không FK lỗi lặp vô hạn hoặc invent date/department.
  - [x] **R-01.3.1 · LOCAL OPERATIONAL KERNEL PASS (2026-10-01):** typed immutable contribution/batch command + in/out port/service/JDBC writer commit minimal journal, metric claims, semantic insert và hai scopes atomically; stable key/scope order, multi-metric batch, no JVM mutex. Same key/different fact conflict, correction revision >1 fail-closed. Source mapper/listener, financial writer và admission pairing/pending vẫn OPEN.
  - [ ] **R-01.3.2 · SOURCE/LOCAL:** financial contribution/pending refund-before-original từ approved transaction/allocation references và equations; pending admission evidence đã có nhưng metric application còn R-04.2.3.
- [ ] **R-01.4 · CONTRACT D11:** chọn durable replay source (Report minimal journal từ activation hoặc archive/outbox retention do owner cung cấp), schema/projector version, retention/access/redaction, horizon dữ liệu có thể rebuild. Queue ACK không phải archive; lịch sử trước source activation phải có export hoặc ghi unavailable.
  - [x] **R-01.4.1 · HUY LOCAL SOURCE CHOICE/STORE PASS (2026-10-01):** minimal accepted-input `operational_event_journal` từ activation, projector version 1, normalized envelope+projection fingerprints; chỉ lưu metadata/source ref và aggregate snapshot, không raw clinical/results. Không public API/automatic purge; historical export/retention approval và finite watermark/catch-up còn OPEN. Không gọi durable storage là replay đã DONE.
  - [ ] **R-01.4.2 · CONTRACT/OWNER:** accepted source export/historical horizon + redacted journal retention/access/backup agreement; không tự purge hoặc tuyên bố no-events trước activation.
- [ ] **R-01.5 · LOCAL sau R-01.4:** journal/processing ledger tách RECEIVED/PENDING/APPLIED/REJECTED và generation. Replay vào generation mới hoặc projection offline riêng; không truncate live tables/xóa live inbox để “chạy lại”. Dùng cùng pure projector, không re-publish command/notification.
  - [x] **R-01.5.1 · LOCAL CODE/UNIT/STATIC PASS (2026-10-02):** V8 finite generation + immutable copied manifest + isolated semantic facts/scopes. Start materializes statement-visible committed journal set (không MAX sequence/wall-clock watermark). Bounded 1..500 batch dưới DB generation lock; fact/effect/applied/progress chung transaction, processing failure rollback để resume. Live/replay dùng cùng pure scope planner và V7-compatible canonical snapshot codec; exact source time/fingerprint/version validation. Chỉ replay accepted revision-1 inputs; không raw clinical payload/republish/read cutover. 13 codec/service + một migration static case pass. Pending finance/admission journal states không nằm trong slice này.
  - [x] **R-01.5.2 · LOCAL POSTGRES PASS (2026-10-02):** finite set/late/in-flight exclusion, bounded resume, dedupe, rollback/retry, concurrent workers, corrupt snapshot, failed reconciliation và isolated generations/mandatory transaction chạy thật. Không phải live catch-up/historical completeness.
  - [ ] **R-01.5.3 · SOURCE/LOCAL:** financial/reversal pending journal/processing ledger và accepted correction projection sau D09/D11; V8 chỉ rebuild accepted operational revision-1 inputs.
- [ ] **R-01.6 · VERIFY:** same event/different bytes conflict, cùng operation/new eventId no double, two valid partial transactions both apply, rollback giữa hai scopes, concurrent new scope, pending resume once sau restart, shuffled replay deterministic.
  - [x] **R-01.6.1 · LOCAL OPERATIONAL POSTGRES PASS (2026-10-01):** 7 PG tests duplicate event/source/new ID, conflicting envelope/department/nanosecond, rollback injected ở scope thứ hai, concurrent same/different business sources, batch prescription metrics, journal privacy/minimal snapshot. 5 domain/application tests cũng pass. Chưa phải replay, two financial partial payments hoặc pending lifecycle test.
  - [ ] **R-01.6.2 · VERIFY sau approved sources:** partial financial payments/refund pending, correction replay/shuffle và cross-owner metric totals; không đóng theo operational revision-1 subset.
- [ ] **R-01.7 · CONTRACT/VERIFY:** watermark/live catch-up, reconcile và atomic read switch/rollback; failed replay giữ generation cũ. Legacy test redelivery không được ghi thành bằng chứng rebuild từ durable journal.
  - [x] **R-01.7.1 · LOCAL RECONCILE POSTGRES PASS (2026-10-02):** bidirectional manifest facts + derived scopes/progress; mismatch→FAILED, match→VERIFIED đã chạy thật. Không chứng nhận historical completeness/live catch-up/source availability/D11.
  - [x] **R-01.7.2 · LOCAL READ GATE:** V10 empty accepted-coverage publication + immutable VERIFIED generation reader. Missing publication/partial metrics/wrong zone/outside history/BUILDING/FAILED → unavailable. VERIFIED replay không tự tạo coverage; snapshotOnly/reconciledAt rõ, legacy reader không đổi.
  - [ ] **R-01.7.3 · CONTRACT + LOCAL ACTIVATION:** live catch-up/final fence, controlled publish/switch/rollback workflow, retention/export completeness và approved coverage manifest. Read-only V10 không thay activation workflow; không insert publication bằng tay.

### R-02 — security hiện có, bổ sung đúng role của endpoint V2

**Trạng thái:** strict JWT và hai gated operations snapshot endpoint đã có; Gateway exact operations DOCTOR rules/tests được bổ sung 2026-10-05. Financial source/API và live activation còn mở.
**Files:** JwtAuthFilter/SecurityConfig, report controllers/web tests, report.http.

- [x] **R-02.1 · ĐÃ CÓ:** human type=access strict, refresh/service/missing type bị reject; legacy daily/monthly/top-medicines ADMIN/MANAGER.
- [x] **R-02.2 · DONE/VERIFY:** Report JwtAuthFilterTest chạy 4/4, 0 skip/fail/error trong full suite; không sửa filter, Common hay Gateway.
- [ ] **R-02.3 · PARTIAL:** operations mới ADMIN/MANAGER/DOCTOR được kiểm thử direct-service và Gateway; legacy revenue vẫn ADMIN/MANAGER. Financial ADMIN/MANAGER API còn chờ source/projection equations, không đóng parent theo Gateway subset.
  - [x] **R-02.3.1 · LOCAL DIRECT-SERVICE API/AUTH:** gated operations daily/surgery, ADMIN/MANAGER/DOCTOR; all-other-role/unauthenticated deny trước use case; period 400 REPORT_VALIDATION_ERROR, missing coverage 404 REPORT_NOT_FOUND; correlation + aggregate-only DTO + report.http. Không mở DOCTOR cho legacy revenue.
  - [ ] **R-02.3.2 · PARTIAL (2026-10-05 override):** Gateway DOCTOR chỉ được GET hai endpoint `/operations/daily` và `/operations/surgery`, tests giữ deny legacy revenue. Financial ADMIN/MANAGER APIs vẫn mở khi source/projection equations chưa đủ dữ liệu.

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
  - [x] **R-04.1.1 · LOCAL LAB MAPPING UNIT PASS (2026-10-02):** 10 cases dùng actual Lab fixture bytes, exact labId/resultVersion/requesting department/episode/completedAt; date theo configured report zone, không delivery/performedDate fallback. Chỉ source revision 1; actual admission fixture resultVersion=3 reject, không đổi thành envelope version=1. Clinical/Pharmacy thiếu accepted revision mapping và correction semantics còn OPEN.
  - [x] **R-04.1.2 · LOCAL POSTGRES PASS (2026-10-02):** actual Lab bytes→mapper→kernel/two scopes/journal, semantic duplicate/new event ID và revision-3 rejection/no claim chạy thật. Owner acceptance/live listener vẫn mở.
  - [ ] **R-04.1.3 · LOCAL MAPPERS IMPLEMENTED / RUNTIME OPEN (2026-10-07):** Clinical completion và Pharmacy fill actual producer bytes có mapper và canonical immutable singleton operation revision 1, không envelope-version fallback hoặc inferred episode. PG effects/rebuild và V11 source-payload conflict fence được kiểm ở [execution](2026-10-07-huy-outbound-contracts.md). Live listeners/cutover và imported-result/correction acceptance vẫn mở.
- [ ] **R-04.2 · LOCAL/CONTRACT D10:** started/closed paired theo admissionId, closed thiếu department dùng stored matching start, close-before-start pending. Chốt discharge counter/administrative duration, timezone/cutoff/denominator và inpatient-days rounding; không gọi closedAt là medicalDischargedAt.
  - [x] **R-04.2.1 · LOCAL EVIDENCE CODE / UNIT PASS (2026-10-02):** V9 minimal STARTED/CLOSED fact + event fingerprint ledger, actual producer-byte mapper, exact admission/patient pairing và durable close-before-start. Late start không reopen; changed patient/department/nanos/proof hoặc close<start conflict. Không fallback envelope time/source revision, không contribution/count/LOS/occupancy hoặc listener/API mới. 15 domain/application cases; metric mapping/semantics parent còn OPEN.
  - [x] **R-04.2.2 · LOCAL POSTGRES PASS (2026-10-02):** pending/reload/dedupe/conflicts/nanos/patient+chronology rollback/caller rollback/race/mandatory transaction và V8→V9 upgrade/constraints chạy thật. Source business revision/correction/discharge acceptance không đóng theo DB tests.
  - [ ] **R-04.2.3 · CONTRACT/METRIC:** Vinh chốt initial operation/revision/correction + medical vs administrative discharge/time rounding; Huy nối admission/discharge/inpatient-days contributions từ exact paired evidence sau đó.
- [ ] **R-04.3 · CONTRACT D10:** bed occupancy chỉ sau bed assignment/transfer/release intervals và staffed/available capacity theo kỳ. Hiện không đủ facts: metric unavailable/deferred, không activeAdmissions/capacity giả. Không chặn admissions/administrative duration đủ source.
- [ ] **R-04.4 · LOCAL/CONTRACT D07:** completed/cancelled surgery theo result/outcome identity/revision, actual started/completed duration, complication category và stage/reason taxonomy đã khóa. Không lấy plannedAt thay actual hoặc đoán category từ text; correction/partial abort chưa hỗ trợ không âm thầm đếm như completed.
  - [x] **R-04.4.1 · LOCAL UNIT+POSTGRES PASS (2026-10-07):** Surgery outcome mapper đọc actual producer bytes; canonical result/cancellation revision/time/category và V11 raw-payload hash fence. Duration floor actual whole minutes; không raw patient/narrative vào journal. PG dedupe/conflict/rebuild và hồi quy ba module 920 tests không failure/error/skip ở [execution](2026-10-07-huy-outbound-contracts.md), không tự bật listener/publication; parent R-04.4 còn mở cho runtime/correction acceptance.
- [ ] **R-04.5 · LOCAL sau source/query semantics:** GET operations/daily và operations/surgery theo target; aggregate-only, bounded from/to, stable sort, null-department hospital scope; phân biệt no-events zero với source chưa sẵn sàng. Không REST join để fill missing event data.
  - [x] **R-04.5.1 · LOCAL FINITE SNAPSHOT READ/API:** hai gated GET, accepted date/zone/metric coverage + immutable generation, hospital NULL/exact department, stable days/camelCase counters, max 366 days, leap-day zero-fill chỉ khi coverage đủ. Migration/replay không tạo publication. PG: snapshot không đọc thêm live facts sau freeze, unavailable khác zero.
  - [ ] **R-04.5.2 · SOURCE/LOCAL ACTIVATION:** accepted source maps/corrections + inpatient-days/LOS, surgery categories và staffed-bed metrics; live catch-up/read publication workflow/Gateway trước full target cutover. Basic snapshot counters không đóng mọi KPI của parent.
- [ ] **R-04.6 · VERIFY:** role matrix R-02, no clinical payload, same source/new eventId, close-before-start, midnight/leap-day, two departments/hospital reconciliation, zero-fill đúng availability, query count/index plan có giới hạn. Không sửa legacy endpoints.

## 9. Handoff, thứ tự triển khai và nghiệm thu liên service

### 9.1. Dependency liên service — task-scoped override 2026-10-05

User đã giao Huy xử lý dependency còn thiếu tại các service này. Owner dài hạn giữ nguyên;
không tiếp tục chặn code chỉ vì khác owner. Bảng dưới ghi dependency/acceptance, không thay
ủy quyền bằng yêu cầu xin phép lại. [Báo cáo triển khai](2026-10-05-huy-cross-service-dependencies.md)
phân biệt phần đã code/test với luồng chưa hoàn tất.

| Owner | Đầu ra đang cần | Task Huy được mở | Handoff/acceptance |
|---|---|---|---|
| Vinh: Clinical/Inpatient/Lab | Exact episode/referral, lifecycle/version/freshness/relationship, medical discharge/transfer policy, exact pre-op evidence, completed/closed/result consumers | S-03–07, P-03, R-04 | [Surgery decisions](../../handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md), [Huy consumers](../../handoffs/HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md); shared fixtures + mismatch/out-of-order tests |
| Lộc: Billing/Notification | Post-case charge bridge, V1 clearance, transaction/allocation/refund/settlement data, cancel/expiry compensation, ready display/notification dedupe | S-03/07, P-02/03, R-03 | Hai handoff trên; expected finance totals và producer/consumer tests cùng bytes |
| Hoàng Anh: Organization/Patient/Gateway | Generic staff/department lookup, eligibility/room authority, route-specific roles; Patient endpoint đã có, cần reuse/test | S-01/03/06, R-02/04, X-01 | [Runtime verification](../../ai/services/surgery.md); service auth, absent/outage và Gateway smoke |
| Shared integrator được giao | Surgery root registration, DB/Compose/CI/runtime wiring | S-01 và X-01 | Bootstrap handoff; reactor build, health/discovery, repeatable environment, không sửa Common nếu không thực sự cần |

Không tạo handoff trùng cho gap đã có; cập nhật requirement còn thiếu vào handoff hiện hữu và giữ [registry](../../handoffs/README.md) nhất quán. Handoff Patient vẫn OPEN trong registry dù E10 đã có code: chỉ retirement sau kiểm chứng hai phía, không tiếp tục ghi “producer endpoint chưa tồn tại”.

### 9.2. Thứ tự lấy việc mới

| Làn | Thứ tự | Có cần chờ Surgery/team toàn bộ không? |
|---|---|---|
| 1 — Surgery trước | Các lát cắt S-02/S-04/S-05/S-06/S-07 còn làm độc lập; xử lý theo §6.8 và dừng riêng tại đúng edge OWNER/CONTRACT/API. | Không chờ mọi owner để hoàn thành local code/test; không tuyên bố workflow production xong khi thiếu authority/contract/API. |
| 2 — Pharmacy | V14/V15/DTO/offline admission/V1 codec+proposal fixtures đã có. Tiếp P-03.2 clearance → P-03.4 atomic authorization → P-02.5 writer/lifecycle → rollout. | Local code còn có thể tiếp tục; activation chờ clearance/eligibility/adjustment và same-byte owner approval. |
| 3 — Report | V6/V7/decoder/typed operational kernel/minimal journal đã có. Tiếp actual source mappers/pending → isolated replay → financial facts/queries → availability-aware API. | Không gọi journal/contribution kernel là live V2/rebuild. Source revision/correction/finance/LOS/cutover cần đúng contract. |
| 4 — integration riêng | Billing clearance → outpatient V1; Inpatient lifecycle → admission thuốc; Billing finance → Report finance; Surgery outcomes → Report surgery | Mỗi slice đạt G1/G2/G3 riêng, không chờ cả 12 D cùng đóng. |

Execution order từ 2026-10-05: thực hiện dependency thiếu theo override, nối consumer Huy và kiểm chứng từng vertical slice trước activation. Gateway/Organization/Inpatient + Surgery draft là slice mới đầu tiên; Billing finance/clearance, referral/placement và full workflow còn mở. Lịch sử 2026-10-02 bổ sung clearance V16/local authorization và offline Lab source mapper; failure Docker lúc đó đã được kiểm lại bằng PG/Rabbit baseline 2026-10-04. V2 flags/bindings/API chưa bật.

Continuation mới nhất 2026-10-02: V17 held writer/capture đã nối internal outpatient stock transaction;
V9 Report lưu/ghép admission evidence bền vững trước metric mapping. V8 finite replay giữ nguyên.
Parent P-02.5/P-03/R-01/R-04 vẫn OPEN cho producer/consumer approvals và live activation.
Continuation hoàn thiện ngày 2026-10-02 có V18 internal outpatient create, cancel/expiry/stock-failure
và V10 gated accepted-coverage snapshot reads; PG/Rabbit suite hiện được chạy thật khi Docker bật lại.
Còn admission eligibility/wiring; Report source revisions/corrections/finance/medical-discharge,
retention/export/live catch-up/controlled publication và đủ target KPI. Không đổi Surgery/shared.

### X-01 — kiểm thử hệ thống, rollout và báo cáo bằng chứng

**Files:** tests/resources/contracts, module .http/README, plan/evidence/handoff; Gateway/Organization/Inpatient thuộc task-scoped override 2026-10-05. Shared root/Compose/Common không sửa.

- [x] **X-01.1 · MODULE + REACTOR REGRESSION PASS (2026-10-05):** Pharmacy 404/404, Surgery 166/166, Report 244/244, Gateway 32/32, Inpatient 97/97 và Organization 107/107 trong root `test` pass **1.696/1.696**, 0 failure/error/skip (305 suites). Organization được chạy lại riêng sau rà soát cuối; không cộng hai lượt thành số test một reactor. Actual-service Gateway và E2E vẫn theo X-01.3/.4/S-01.4; [bằng chứng](2026-10-05-huy-cross-service-dependencies.md).
  - [x] **X-01.1.1 · PHARMACY/REPORT FULL REGRESSION PASS (2026-10-04):** 404/244 tests, 0 failure/error/skip với PostgreSQL/RabbitMQ thật. Root reactor 1.635/1.635 pass; chưa có G3/E2E V2.
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
- **H-01.2/3 + domain core 2026-09-28:** [handoff Surgery G0](../../handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md) ghi mapping proposals, tám Huy-local V1 decisions theo ủy quyền, residual owners và evidence cần cung cấp. H-01.3.2 và S-02.1.1 DONE/LOCAL; H-01.2.2/H-01.3.3 vẫn OPEN cho owner fixtures/contract updates. S-02.1.1 ban đầu có 13 domain tests; các model/revision/restore tests mới được ghi riêng bên dưới. Bootstrap handoff đã retire 2026-10-05 sau actual runtime acceptance; facts ở canonical Surgery/Gateway docs, không còn owner blocker này.

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
| S-02.5 | Outbox durable với bytes/causal order/lease/fencing/retry/returned state; gated generic dispatcher và Rabbit correlated confirm/mandatory-return transport tests có thật. Event-specific producer bytes/G1/restart evidence chưa có. | PARTIAL |
| S-02.6 | Fresh schema, ORM restore, rollback receipt/outbox/child rows + audit, inbox replay, expired attempt, two-worker same-room/reversed-team race, adjacent/contained, IN_USE overrun, Rabbit ACK/return đã test; outage/restart/command matrix còn thiếu. | PARTIAL |
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

**Evidence batch local validation tiếp theo ngày 2026-09-29:**

- S-04.1/S-04.4 local boundary: thêm `beginPreop_appliedReceiptReplay_returnsOriginalOutcomeWithoutRelockingCase`, `beginPreop_staleCaseRevision_rejectsWithoutReadingClockOrCompletingReceipt`, và `beginPreop_conflictingIdempotencyReceipt_rejectsBeforeCaseLookup`. Replay không đọc lại/ghi lại aggregate; stale revision và receipt conflict không tạo side effect.
- S-04.1 checklist input/actor: thêm `updateChecklistItem_systemActor_rejectsBeforeReceiptClaim`, `updateChecklistItem_satisfiedWithoutEvidence_rejectsBeforeReceiptClaim`, `updateChecklistItem_evidenceRevisionWithoutReference_rejectsBeforeReceiptClaim`, và `updateChecklistItem_staleSnapshotRevision_rejectsWithoutWritingHistoryOrReceipt`. Request sai hình dạng bằng chứng được validate trước receipt claim; N/A vẫn fail-closed.
- S-05.2.1 local lifecycle boundary: thêm `signConsent_systemRecorder_rejectsBeforeReceiptClaim`, `signConsent_duplicateActiveType_rejectsWithoutAppendingAnotherConsent`, và `revokeConsent_afterStart_rejectsBeforeLoadingConsentOrReleasingResources`. Đây chỉ là kiểm chứng Huy-local; quyền signer/guardian/revoke và audit policy sau START vẫn chờ Vinh, không phải API authorization evidence.
- Module test đạt **99/99, 0 failure/error/skip; BUILD SUCCESS** với Docker Desktop, PostgreSQL 16.14 và Flyway V1 thật. `git diff --check` sạch. S-04 vẫn chưa có create/query/controller/Gateway; S-05 vẫn chưa có clearance/readiness workflow; consent owner contract, Rabbit, cross-service fixtures và root reactor chưa chạy. Business flag vẫn `false`.

**Evidence follow-up S-02.6/S-06.3.2 ngày 2026-09-29:**

- Thêm PostgreSQL test `reservationDatabaseFailureAfterRoomInsert_rollsBackEveryResourceAndKeepsDraft`: trigger test-only gây lỗi khi ghi reservation STAFF sau khi adapter đã insert ROOM; kiểm tra không còn reservation/mutex nào, schedule vẫn `DRAFT`, case vẫn `READY`. Trigger/function được dọn trong `finally`; không thay schema/migration.
- Test tập trung pass trên PostgreSQL 16.14; chạy lại toàn module đạt **100/100, 0 failure/error/skip; BUILD SUCCESS**. `git diff --check` sạch. Đây là bằng chứng rollback của reservation adapter, không đóng S-02.6/S-06.3: retry deadlock, các race finalize/cancel/reschedule và orchestration mọi command vẫn còn.

**Evidence follow-up S-05.4.3/S-06.3.2 ngày 2026-09-29:**

- Thêm `revokingConsentFromScheduledCase_invalidatesReadinessAndReleasesExactReservation`: đi qua consent use case và PostgreSQL adapters; xác nhận SCHEDULED→PREOP, snapshot active/readyAt được xóa nhưng snapshot history còn, đúng schedule revision cùng cả room/staff reservations được RELEASED, consent audit có SIGNED/REVOKED và command receipt APPLIED.
- Thêm `anyOverlappingStaffInContainedMultiStaffBooking_rejectsWholeReservation`: dù lịch ứng viên khác room và interval nằm hoàn toàn trong interval đã reserve, chỉ cần một staff trong team trùng thì booking bị từ chối và không có partial reservations; booking hiện hữu vẫn nguyên vẹn.
- Hai test mới và toàn suite chạy với PostgreSQL 16.14 Testcontainers: **102/102, 0 failure/error/skip**; `git diff --check` sạch. S-05.4.3/S-06.3.2 vẫn PARTIAL; các writer khác, race/retry matrix và orchestration finalize/expiry còn phải làm. Không chạy Rabbit/Gateway/root reactor; feature flag Surgery mặc định vẫn `false`.

**Evidence follow-up S-06.2.2 ngày 2026-09-29:**

- Thêm `PrepareSurgeryScheduleUseCase` và `SurgeryScheduleApplicationService`: xác minh lookup Organization theo identity yêu cầu trước khi khóa case, kiểm tra PREOP + expected case/schedule revisions, lưu draft revision/team/history cùng business audit và command receipt trong transaction; không tạo reservation hoặc phát event.
- Thêm 10 application tests, gồm same-key replay trả kết quả cũ trước Organization lookup và payload conflict bị chặn; PostgreSQL integration `schedulePreparation_commitsDraftCaseRevisionAndReceiptWithoutReservations` và `schedulePreparation_caseAuditFailureRollsBackDraftHistoryAndReceipt` (fault injection sau khi draft đã được ghi). Sửa bug reschedule: row lịch `RELEASED` được chuyển lại `DRAFT` khi lưu revision nháp mới; integration assertion xác nhận reservation revision cũ vẫn RELEASED và revision mới chưa có active reservation. Thêm domain truth tests cho đủ bảy readiness guards, consent/finance V1 và expiry/dependency snapshots. Toàn module đạt **116/116, 0 failure/error/skip; BUILD SUCCESS** trên PostgreSQL 16.14 Testcontainers; `git diff --check` sạch. S-06.2 vẫn PARTIAL: Organization live contract/adapter, role-job-title eligibility, controller/API và contract echo tests còn mở; S-07.3.1 chỉ là domain evidence, authorization/workflow checks còn mở. Không chạy Rabbit/Gateway/root reactor, feature flag vẫn `false`.

**Evidence follow-up S-01.6/S-02.5 ngày 2026-09-29:**

- Thêm `SurgeryOutboxDispatcher`: claim lease trong transaction riêng, publish sau commit, rồi ghi PUBLISHED hoặc bounded retry/RETURNED trong transaction khác. `RabbitSurgeryEventPublisherAdapter` gửi đúng payload byte đã persist qua durable topic exchange, correlated publisher confirm, mandatory-return detection, messageId/correlationId và event headers.
- Cấu hình scheduler/publisher chỉ tồn tại khi cả `mediflow.features.surgery.enabled` và `mediflow.surgery.messaging.producer.enabled` cùng bật; mặc định đều false. Không thêm consumer, event-specific serializer, endpoint hay producer command; wire contracts chưa approved nên không bật cờ.
- Thêm dispatcher unit tests cho idle/success/nack/return, configuration tests cho thiếu từng gate, và RabbitMQ 3.13-alpine Testcontainers kiểm tra ACK, exact payload bytes, headers và mandatory return. Chạy full suite `mvn -q -f backend/surgery-service/pom.xml '-Dapi.version=1.44' test` → **123/123, 0 failure/error/skip; BUILD SUCCESS**, PostgreSQL 16.14 + RabbitMQ Testcontainers thật. `git diff --check` sạch.
- Còn mở: producer payload/semantic contract + same-byte consumer fixtures, Rabbit timeout/outage/restart replay, consumer-side ACK/DLQ, Gateway/root reactor; Surgery producer and consumer flags remain false. Đây mới là transport nền, không phải tích hợp event nghiệp vụ đạt G1.

**Evidence follow-up S-01.7.1 ngày 2026-09-29:**

- Mở rộng `SurgeryArchitectureTest` với các ranh giới Clean Architecture: domain không phụ thuộc framework/I/O/layer ngoài; application không đi ra adapter/JPA/SQL; HTTP/event driving adapters không gọi thẳng persistence/client/publisher/domain; không tham chiếu package service nghiệp vụ khác; các package slice không có cycle.
- Sửa README đã lỗi thời để nêu generic Rabbit publisher/dispatcher có tồn tại nhưng vẫn gate-off; chưa có business API, event-specific publisher hay consumer. Ghi đúng phạm vi test module so với root/Gateway.
- `mvn -q -f backend/surgery-service/pom.xml '-Dapi.version=1.44' -Dtest=SurgeryArchitectureTest test` → **5/5 rules pass**. Chạy lại full `mvn -q -f backend/surgery-service/pom.xml '-Dapi.version=1.44' test` → **124/124, 0 failure/error/skip**, PostgreSQL 16.14 + RabbitMQ 3.13-alpine Testcontainers. S-01.7 vẫn PARTIAL: `.http` và service Dockerfile chỉ tạo khi API thật tồn tại; shared bootstrap chưa thuộc Huy.

**Evidence follow-up S-02.5.2 ngày 2026-09-29:**

- `RabbitSurgeryEventPublisherAdapter` giờ ánh xạ confirm timeout và Spring AMQP connection/publish exception sang lỗi transport retryable có reason text cố định; không đưa broker exception message vào persisted retry reason/log. Dispatcher giữ event chưa publish và gọi retry thay vì xác nhận thành công.
- Thêm unit cases: confirm không đến → timeout; broker connect failure → publisher exception an toàn; dispatcher failure → retry không mark published. Focused `SurgeryOutboxDispatcherTest,RabbitSurgeryEventPublisherAdapterUnitTest` pass **7 tests, 0 failure/error/skip**. Existing PostgreSQL reliability test tiếp tục chứng minh lease fencing, retry backoff và pending eligibility; chưa mô phỏng container broker outage + restart process, nên S-02.6 vẫn PARTIAL.
- Chạy lại full `mvn -q -f backend/surgery-service/pom.xml '-Dapi.version=1.44' test` sau lát cắt này → **127/127, 0 failure/error/skip**, với PostgreSQL 16.14 và RabbitMQ 3.13-alpine Testcontainers.

**Evidence follow-up S-02.6.1 ngày 2026-09-29:**

- Thêm `SurgeryOutboxDispatchRecoveryIntegrationTest` dùng PostgreSQL 16 + RabbitMQ 3.13 Testcontainers. Test ghi event bytes vào outbox, gửi lần đầu tới một local TCP port không có broker để nhận connection-refused, kiểm tra row `PENDING`, attempt count và `next_attempt_at`; tạo dispatcher mới sau backoff, gửi tới Rabbit container thật, so sánh exact payload/correlation và xác nhận row thành `PUBLISHED` sau publisher confirm.
- Test recovery pass 1/1. Chạy lại full suite sau phiên bản endpoint-failure test này → **128/128, 0 failure/error/skip** với PostgreSQL 16.14 và RabbitMQ 3.13-alpine thật. Đây xác nhận connection-refused retry và worker-instance recreation; chưa dừng/khởi động lại broker container hoặc JVM process.

**Evidence Pharmacy P-02.2.1 ngày 2026-09-29:**

- Thêm V14 additive migration cho `care_contract_version`, `care_episode_type`, `care_episode_id`, `admission_id`, `price_code`; `record_id` nullable có điều kiện theo context. Constraints buộc V0 giữ record ID/OUTPATIENT và không có V1 IDs; V1 phải có episode/type/price code, outpatient không mang admission ID, admission phải có `admission_id = care_episode_id`. Thêm episode/admission indexes.
- Domain/JPA mapping round-trip cả V1 outpatient và admission. PostgreSQL tests xác nhận V13 row giữ semantics V0, tuple hợp lệ được lưu, và null episode/blank price/mismatched admission/V0 record null bị từ chối. Không thêm clearance/admission projection tables vì D08/D12 semantics còn mở.
- Chạy `mvn -q -pl backend/pharmacy-service -am '-Dapi.version=1.44' test` → Surefire aggregate **234 tests, 0 failures, 0 errors, 0 skipped**; PostgreSQL 16.14 Testcontainers chạy thật. V1 writer/API/event và feature flag activation không nằm trong lát cắt này.

**Evidence Pharmacy P-02.3/P-03.1.1 ngày 2026-10-01:**

- `CreatePrescriptionRequest` giữ constructor/wire V0, thêm selector `careContractVersion=1` và cross-field validation bắt buộc episode/context/priceCode; V1 admission yêu cầu `admissionId = careEpisodeId`. V1 request chưa được ghi: application trả `PHARMACY_CARE_FINANCE_V2_UNAVAILABLE` trước bất kỳ stock, prescription, reservation, slip hay outbox side effect nào.
- `payment.completed` compatibility path chỉ tiếp tục cho contract version 0. V1 outpatient/admission bị từ chối trước receipt/processed-event claim và không gọi dispense; đây là fence an toàn, chưa phải implementation của clearance/admission authorization.
- Chạy `mvn -f backend/pharmacy-service/pom.xml "-Dtest=PaymentApplicationServiceTest,PrescriptionApplicationServiceTest,CreatePrescriptionRequestValidationTest" test` → **24 tests, 0 failures/errors/skips; BUILD SUCCESS**. Sau chỉnh sửa cuối DTO/Jackson, thêm regression qua `PrescriptionControllerTest`: focused total **40 tests, 0 failures/errors/skips**. Final full `mvn -f backend/pharmacy-service/pom.xml test` → **238 discovered, 0 failures/errors, 48 Docker-dependent skips; BUILD SUCCESS**. Docker CLI/daemon không khả dụng; DB/Rabbit/Testcontainers chưa được xác minh ở lượt này.

**Evidence Report R-01.1.1 ngày 2026-09-29:**

- Mở source fixture bytes trực tiếp từ Clinical `medicalrecord.completed.v1.json` và Lab `lab.result.created.v1.json`; decoder tests dùng đúng bytes trên đĩa, xác nhận producer/source identity cùng disposition/resultVersion. Không thay đổi producer modules hay sao chép fixtures thành payload giả.
- Chạy `mvn -q -pl backend/report-service -am '-Dapi.version=1.44' test` → Surefire aggregate **145 tests, 0 failures, 0 errors, 0 skipped**. Đây là local decoder/fixture-byte compatibility evidence, không phải canonical fixture approval, consumer test của owner hoặc G1/G3.

**Evidence Report R-01.1.2 ngày 2026-10-01:**

- `CareFinanceEnvelopeDecoderTest` đọc trực tiếp `backend/lab-service/src/test/resources/contracts/lab.result.created.admission.v1.json`, không copy payload; xác nhận canonical `labId`, admission episode và `recordId` riêng biệt.
- `mvn -f backend/report-service/pom.xml "-Dtest=CareFinanceEnvelopeDecoderTest" test` → **11 tests, 0 failures/errors/skips; BUILD SUCCESS**. Decoder này vẫn offline; không thêm Report Rabbit binding, projection mutation, D10 semantic rule hay bật V2 flag.

**Evidence Report R-01.1.3 ngày 2026-10-01:**

- Thêm decoder test đọc trực tiếp hai fixture từ `backend/inpatient-service/src/test/resources/contracts/`; xác nhận `admission.started`/`admission.closed` đều giữ cùng `admissionId` chính xác, producer/type và timestamp/settlement fields theo bytes của Inpatient.
- `mvn -f backend/report-service/pom.xml "-Dtest=CareFinanceEnvelopeDecoderTest" test` → **12 tests, 0 failures/errors/skips; BUILD SUCCESS**. Đây là local byte-compatibility evidence; không xác nhận transfer/department history, LOS, projection ordering hoặc owner G1.

**Evidence Report R-01.2.1 ngày 2026-10-01:**

- Thêm additive `V6__care_finance_projections.sql` cho FINANCIAL_CONTRIBUTION, DAILY_FINANCIAL_REPORT, OPERATIONAL_CONTRIBUTION và DAILY_OPERATIONAL_REPORT. `event_id` chỉ là provenance; uniqueness dùng source type/id/revision cùng contribution/metric và department scope. Daily scope dùng `NULLS NOT DISTINCT`, nên hospital `NULL` không xung đột với UUID zero hợp lệ. Không thêm JPA writer/listener/API hoặc đổi feature flag.
- `ReportMigrationSchemaTest`: **3/3 pass**. Sau khi thêm Inpatient fixture test, final full `mvn -f backend/report-service/pom.xml test`: **142 tests, 0 failures/errors, 27 Docker-dependent skips; BUILD SUCCESS**. `ReportMigrationPostgresTest` bị skip vì Testcontainers không tìm được Docker daemon; do đó câu SQL V6, các index/constraints và migration upgrade thực tế **chưa được thực thi trên PostgreSQL**. `CareFinanceEnvelopeDecoderTest` riêng có **12/12 pass**.
- Huy-local schema shape không đóng H-01.5: exact producer source IDs, settlement/refund/result revisions, supersedes/correction rules, totals/scope policy và durable replay/source horizon vẫn cần contract/owner acceptance. Migration numbering theo V6 target spec; code hiện có chỉ V1/V2 và không sửa migration lịch sử.

**Evidence Surgery S-07.4.1 ngày 2026-10-01:**

- Thêm command/service local cho cancel trước START; command hủy SCHEDULED phải khớp schedule dependency của readiness snapshot rồi nhả đúng reservation revision; audit transition giữ trạng thái trước hủy và active readiness pointer được clear trong khi immutable snapshot row vẫn còn. Không phát `surgery.cancelled` hoặc tự tạo Billing adjustment.
- Thêm feature-gated `POST /api/v1/surgery/cases/{id}/cancel`, English request/response DTOs, ADMIN/DOCTOR authorization, verified JWT actor, required `Idempotency-Key`, common response/error envelopes và `surgery.http`. Default feature flag vẫn false.
- `mvn -f backend/surgery-service/pom.xml "-Dtest=SurgeryCancellationApplicationServiceTest,SurgeryCaseTest" test` → **14 tests, 0 failures/errors/skips; BUILD SUCCESS**. Endpoint/auth/use-case/domain/architecture focused rerun `-Dtest=SurgeryServiceSmokeTest,SurgeryCancellationApplicationServiceTest,SurgeryCaseTest,SurgeryArchitectureTest` → **28 tests, 0 failures/errors/skips; BUILD SUCCESS**.
- Full module `mvn -f backend/surgery-service/pom.xml test` → **141 tests, 0 failures/errors, 32 skipped; BUILD SUCCESS**; tất cả skipped là Docker-dependent Rabbit/PostgreSQL suites. PostgreSQL success/rollback tests mới đã compile nhưng chưa execute vì không có Docker daemon.
- PostgreSQL tests mới cho successful cancellation và rollback sau release đã được biên dịch nhưng **chưa chạy**: `SurgeryCasePersistenceIntegrationTest` phát hiện không có Docker environment và skip **21/21** tests. Do đó chưa ghi PG transaction/rollback PASS; chạy lại nhóm Testcontainers khi Docker daemon khả dụng. `git diff --check` sạch tại lượt này.
- Trạng thái: CODE=LOCAL PARTIAL | UNIT/API/AUTH/ARCHITECTURE=PASS | POSTGRES=NOT_RUN | CANCELLED EVENT WIRE/OWNER CONTRACT=OPEN.

**Evidence Surgery S-04.5 API update ngày 2026-10-01:**

- Added a feature-gated begin-preop HTTP adapter at `POST /api/v1/surgery/cases/{id}/preop`, accepts only `expectedCaseRevision`, requires bounded `Idempotency-Key`, derives actor from verified access-token details and emits the standard response envelope. Added English request/response DTOs and the matching live `.http` request. Application in-port now carries application-layer `SurgeryActorIdentity`; only the application service builds the domain audit actor. Missing case maps to `SURGERY_CASE_NOT_FOUND`/404; stale revision or receipt conflict remains 409.
- Focused `mvn -f backend/surgery-service/pom.xml "-Dtest=SurgeryPreopApplicationServiceTest,SurgeryServiceSmokeTest,SurgeryArchitectureTest" test` → **24 tests, 0 failures/errors/skips** (5 application, 14 smoke/API/security, 5 architecture). `SurgeryBusinessFeatureGateTest` → **1/1 pass**. Full `mvn -f backend/surgery-service/pom.xml test` → **147 tests, 0 failures/errors, 32 skipped; BUILD SUCCESS**. All skipped tests require Docker/Testcontainers; PostgreSQL pre-op/cancellation transaction behavior remains NOT_RUN.

**Evidence lượt tiếp Pharmacy/Report V2 ngày 2026-10-01 (working tree Huy, chưa commit/push):**

- Pharmacy: P-02.2.2/P-03.3.1 thêm domain/port/usecase/decoder/JDBC admission lifecycle và V15;
  P-02.4/P-02.5.1 thêm V1 DTO/codec + năm proposal fixtures. POM chỉ module Pharmacy align
  Testcontainers 1.20.6 với Report; root/shared không đổi.
- Report: R-01.2.2/R-01.3.1/R-01.4.1/R-01.6.1 thêm typed operational contributions/batch,
  transaction service/port/JDBC, V7 minimal journal/claims/global source key và test scopes/privacy.
  Sửa V6 CHECK episodeId-without-type loophole trước khi release migration.
- Docker Desktop engine 29.6.2; PostgreSQL `postgres:16-alpine`, RabbitMQ
  `rabbitmq:3.13-management-alpine`. Engine min API 1.40, dùng explicit API 1.44 để chạy tests.
- `mvn -q -f backend/pharmacy-service/pom.xml -Dapi.version=1.44 test` → **274 tests,
  0 failures/errors/skips, exit 0**. Full regression có PG/migration/concurrency/Rabbit/security/
  architecture, không chỉ domain unit. Codec 17/17, admission local slice 15/15.
- `mvn -q -f backend/report-service/pom.xml -Dapi.version=1.44 test` → **163 tests,
  0 failures/errors/skips, exit 0**. Operational PG 7/7, domain/application 5/5;
  migration PG 5/5; legacy Rabbit/cross-layer/security/architecture cũng pass.
- SQL/runtime scenarios: closure tombstone→late start→CLOSED after reload; conflicting patient/event
  rollback claim; same source/new event ID→one contribution; changed department/source nanos→conflict;
  failure at second aggregate scope→zero journal/claim/contribution/scopes, retry→one effect;
  concurrent new sources→hospital=sum(departments); journal snapshot excludes clinical/results text.
- **SPEC/CODE=LOCAL, LOCAL_TEST=PASS; CROSS_OWNER_ACCEPTANCE/E2E_V2=NOT_RUN.** Không bind queue,
  bật flag, thay V0 publisher/outbox hoặc expose V2 API. Không gọi 437 local regression tests là
  437 tests của workflow V2 đã hoạt động.
- **Phần Huy còn phải code:** exact clearance/pending/expiry authorizer, V1 create/dispense/lifecycle
  outbox wiring và phân loại authorization-vs-stock failure; Report actual source mappers,
  admission pairing/pending, financial writer khi đủ facts, isolated replay/generation/catch-up,
  availability-aware queries/API/rollout. Không đẩy các việc local này thành blocker của team.
- **Phần cần owner phối hợp:** Billing classified receipt/clearance/recognition/refund/settlement
  fixtures + expected totals; Inpatient medical-discharge/transfer/freshness và administrative-duration
  contract; producer corrections/revisions/cutover; Gateway operations DOCTOR role; consumer đọc cùng
  Pharmacy V1 proposal bytes. Active handoff giữ OPEN.

**Evidence tiếp tục V2 ngày 2026-10-02 (working tree Huy, chưa commit/push):**

- Pharmacy P-02.2.3/P-03.2: thêm `PrescriptionClearance`, decoder/in-port/out-port/projection service,
  JDBC immutable grants + target fences/event claims và V16. Early grant lưu PENDING không FK tới
  prescription; exact matching được kiểm lại trước dùng. Huy quyết định AUTHORIZE ONLY, không
  PaymentReceipt/auto-dispense hoặc suy amount từ Rx total. Source time lưu ISO nanoseconds.
- P-03.1.2/.4.1/.5.1: V0 executor chặn V1, typed denial không gọi legacy compensation; V0 TTL
  kiểm sau khi khóa toàn bộ stock/reservations. Local clearance authorization phải join transaction,
  đọc clock sau lock waits; chưa gọi nó là V1 stock/outbox atomic workflow đã hoạt động.
- Report R-04.1.1/.2: offline Lab mapper từ actual producer bytes/resultVersion=1, exact episode
  và completedAt/report timezone. Actual admission fixture resultVersion=3 bị từ chối; không đoán
  source revision từ envelope hoặc sửa producer ngoài scope. Thêm 10 mapping tests và hai PG cases.
- Docker Desktop 4.83.0/engine client 29.6.2 đã được thử khởi động nhưng backend dừng do socket
  `dockerInference` không truy cập được. Không reset/xóa Docker data hoặc đổi hệ thống để lách lỗi.
  PostgreSQL/Rabbit suites trong lượt này bị skip; full runtime/E2E V2 **NOT VERIFIED**.
- Lệnh `mvn -q -f backend/pharmacy-service/pom.xml -Dapi.version=1.44 test`: **306 discovered,
  245 pass, 61 skip, 0 failures/errors, exit 0**. Lệnh tương tự với Report: **166 discovered,
  130 pass, 36 skip, 0 failures/errors, exit 0**. Tổng 375 pass/97 skip từ XML run cuối.
  Skip gồm DB/Rabbit/migration/concurrency; exit 0 không phải full runtime PASS. Architecture,
  web/security/domain/application chạy được đều pass. `git diff --check` sạch.
- Actual Lab fixture SHA-256: `lab.result.created.v1.json` =
  `fdf18735f9f3757fac49c75cad0643c16a663690a42a753554f1b95f6bfb1b89`;
  `lab.result.created.admission.v1.json` =
  `9f70e99e25f615cd54aa44f24826ab45ffe52904a7a28cf33aa5ab28d041ebe7`.
  Đây là same-byte local mapping evidence, không producer-owner sign-off.
- Handoff giữ OPEN cho Billing clearance fixture + grant-time/revocation/cutover, Vinh admission
  eligibility và Lab imported-result/correction revision; Report Clinical/Pharmacy source mapping,
  admission pairing, finance writer, replay/generation/catch-up/read switch và APIs vẫn còn local work.
- **CODE=LOCAL, UNIT_TEST=PASS; POSTGRES/RABBIT/OWNER_ACCEPTANCE/E2E_V2=OPEN/NOT_RUN.** Không bật
  V2 flag/listener/writer/API, không sửa Surgery/shared/producer service, không commit/push.

### 10.2. Checklist đóng mỗi task

**Evidence held-writer/replay continuation 2026-10-02 (working tree, chưa commit/push):**

- P-02.5.2/.3, P-03.5.2: V17 names/exact terminal ISO time, pure factory/capture/held writer và
  legacy lifecycle version fences. V0 API/event bytes không đổi; new V0 lines chỉ thêm stored name.
  Hook bắt buộc join mutation transaction nhưng chưa được stock/lifecycle workflow gọi. DB hold
  là intentional activation fence; không admin replay/flag nào được mở V1 trong schema này.
- R-01.5.1/.2, R-01.7.1: V8 finite manifest từ minimal journal, shared planner/codec, generation lock,
  bounded resume và fact/scope reconciliation. VERIFIED không đổi nguồn đọc. Không truncate live
  tables/inbox, raw payload retention, command republish hoặc fake financial/revision mapping.
- Verify trước continuation internal outpatient/V9: `mvn -q -pl backend/pharmacy-service,backend/report-service -am -Dapi.version=1.44 verify`
  **exit 0** (build cùng common hiện tại, không dựa vào installed common cũ). XML Pharmacy: **338
  discovered, 268 pass, 70 skip, 0 failure/error**. XML Report: **187 discovered, 144 pass, 43 skip,
  0 failure/error**. Hai service tổng **412 pass/113 skip**; architecture/web/domain/application
  chạy được pass, `git diff --check` sạch. PostgreSQL/Rabbit suites skip vì Docker unreachable,
  gồm 8 writer + 1 lifecycle reload và 7 replay PG cases mới: **DB/RABBIT/OWNER/E2E VERIFY OPEN**.
  Không dùng exit 0, regression lịch sử hoặc mocked JDBC làm bằng chứng real PostgreSQL/replay.
- Handoff giữ OPEN; tại mốc trước continuation phần Huy local còn atomic V1 orchestration, Report source/pairing/finance,
  catch-up/read switch/query/API. Không commit/push hoặc sửa production của owner khác.

**Evidence continuation internal outpatient / admission pending — 2026-10-02 (mới nhất):**

- P-03.4.2/.3: `CareDispenseTransactionService` + internal in-port nối clearance và whole-order
  stock/reservation/lifecycle/held outbox trong caller business transaction; existing V0 APIs,
  payment consumer và wire không đổi. Factory/capture dùng persisted immutable evidence, retry
  kiểm cả held filled proof; không stock effect/claim/failure/refund V0 khi denied. 12 executor +
  1 held-read adapter unit cases pass; sáu actual PostgreSQL atomic/rollback/reload/race cases skip.
- R-04.2.1/.2: V9 minimal admission facts + delivery fingerprint/row fence, pure actual-byte mapper,
  domain pairing và internal transaction. 15 domain/application + 1 static schema cases pass; tám
  PostgreSQL pending/reload/conflict/rollback/race cases và hai upgrade/constraint cases skip.
  Business revision không được lấy từ event version; chưa metric contribution/medical LOS/occupancy.
- Verify code cuối: `mvn -q -pl backend/pharmacy-service,backend/report-service -am -Dapi.version=1.44 verify`
  **exit 0**, build cùng common trong reactor. Pharmacy **357 discovered / 281 pass / 76 skip**;
  Report **213 discovered / 160 pass / 53 skip**; tổng **441 pass / 129 skip / 0 failure/error**.
  Log local ignored: `backend/report-service/target/v2-continuation-verification.log` và XML module
  surefire reports. Resource V9 cuối được copy vào target; `git diff --check` sạch.
- Docker CLI có nhưng engine pipe `dockerDesktopLinuxEngine` không tồn tại; Testcontainers không có
  environment hợp lệ. PG/Rabbit/owner/E2E gates **VERIFY OPEN**, gồm 16 PG cases mới trong lượt này.
  Không coi unit/mock/static/exit 0 là real DB acceptance. Không reset Docker hoặc xóa dữ liệu.
- Cập nhật service docs/spec/README và active Huy handoff. Public V2 API/listener/flags/cutover vẫn
  OFF; còn V1 creation/admission/cancel/expiry/failure, producer fixture approvals, Report finance/
  source revisions/medical-discharge semantics/catch-up/read API. Không đổi Surgery/shared/owner
  khác và không commit/push trong lượt này.

**Evidence clean completion/regression — 2026-10-02 (mới nhất):**

- HEAD `ea11597` + working tree Huy, chưa commit/push. V18 creation receipt + internal V1
  cancel/expiry/REQUIRES_NEW stock-failure; V10 empty accepted snapshot publication + gated
  aggregate read API. No Surgery/shared/producer change trong lượt này.
- `mvn -q -pl backend/pharmacy-service,backend/report-service -am '-Dapi.version=1.44' clean verify`
  exit 0: **Pharmacy 395/395, Report 244/244; tổng 639 pass, 0 failures/errors/skips**.
  Fresh XML sau clean; Docker 29.6.2 + PostgreSQL 16-alpine/RabbitMQ module suites chạy thật.
  CareDispensePostgresTest 18/18, held writer PG 8/8, operational replay/read PG 11/11;
  admission evidence, clearance, upgrade, legacy Rabbit/security/architecture đều không skip.
- Runtime issues đã sửa trước final pass: local FK whitelist trong migration assertion, duplicate
  test declaration và cancel retry dùng exact lifecycle ISO proof thay rounded audit timestamp.
  Log local `backend/report-service/target/v2-release-check.log`; không root/Gateway/Surgery/E2E V2.
- **CODE/LOCAL_DB_BROKER_REGRESSION=PASS; OWNER/G1/G3/LIVE_ACTIVATION=OPEN.** Public V1 Pharmacy,
  V2 listeners/flags và held delivery vẫn OFF; VERIFIED không tự publish coverage. Full target
  admission/finance/source metrics/live catch-up vẫn có code phải làm sau authoritative inputs.
- [Báo cáo đánh giá](2026-10-02-huy-v2-completion-assessment.md): P/R 46/74 leaf local done,
  28 open; full plan 85/158 leaf. Không dùng counts như phần trăm production readiness.

**Evidence Pharmacy medical-discharge slice — 2026-10-04 (working tree Huy, chưa commit/push):**

- P-03.3.3 + P-02.2: V19 additive migration lưu medical discharge riêng administrative close;
  decoder đọc trực tiếp Inpatient producer fixture `discharge.medically.approved.v1.json`.
  Domain/Persistence giữ exact source instant, claim event + fact cùng transaction, singleton
  duplicate idempotent và conflict rollback. Late start không mở lại admission đã discharge.
- Migration fresh V1→V19 và V18 row→V19 có test thật; không backfill discharge cho row cũ.
  `mvn -q -pl backend/pharmacy-service -am test` → **404/404 pass, 0 fail/error/skip** với
  Docker 29.6.2, PostgreSQL 16.14 và RabbitMQ 3.13 Testcontainers. Local fixture-byte + DB
  acceptance đạt; live Rabbit binding, transfer/freshness, admission create/dispense authority,
  Billing/owner G1 và E2E vẫn OPEN. `git diff --check` sạch.

**Evidence Surgery local verification — 2026-10-04 (working tree Huy, chưa commit/push):**

- `mvn -q -pl backend/surgery-service -am '-Dapi.version=1.44' test` → **151/151 pass,
  0 fail/error/skip**; PostgreSQL 16.14 và RabbitMQ 3.13 Testcontainers thật. Không dùng
  lượt thiếu Docker API override (32 skip) làm bằng chứng.
- S-04.5.1 begin-preop commit/replay và rollback đã chạy lại; S-07.4.1 cancellation
  scheduled-case release đúng revision và domain-failure rollback đã chạy trên PostgreSQL.
  Organization staff/department HTTP consumer local cũng nằm trong suite; room/eligibility,
  cancelled event wire, root/Gateway và G1/G3 vẫn OPEN.

**Evidence Report regression — 2026-10-04 (working tree Huy, chưa commit/push):**

- `mvn -q -pl backend/report-service -am '-Dapi.version=1.44' test` → **244/244 pass,
  0 fail/error/skip**; PostgreSQL 16 và RabbitMQ 3.13 Testcontainers thật. Đây là kiểm thử
  module hiện có, không đóng financial source/Gateway/live catch-up còn thiếu.

**Evidence root reactor regression — 2026-10-04 (working tree Huy, chưa commit/push):**

- `mvn -q '-Dapi.version=1.44' test` exit 0; Surefire XML của 11 business/platform modules:
  **1.635 tests, 0 failures, 0 errors, 0 skipped** (296 suites). PostgreSQL/RabbitMQ
  Testcontainers chạy thật ở các module tích hợp. Gateway 25 tests xanh, nhưng chưa có
  Surgery route/role smoke hoặc V2 cross-service G3; X-01.1 chỉ là regression baseline.

**Evidence dependency override — 2026-10-05 (baseline `7121330`, chưa commit/push):**

- Đã thực hiện lát cắt Gateway/Organization/Inpatient và Surgery consumer theo ủy quyền user;
  không phải đóng toàn bộ dependency hoặc 50 task. Room/grant là authority thật, không seed/
  suy luận qualification; admission lookup không thay referral/placement. Main leaves S-03.3.2,
  S-03.3.3, S-06.1.2 và R-02.3.2 giữ PARTIAL cho acceptance còn thiếu.
- Root reactor 1.696/1.696 pass, zero failure/error/skip; real PG16.14/Rabbit3.13.
  Producer fixtures dùng trực tiếp trong Surgery consumer HTTP tests; Gateway transport test
  dùng HTTP stub, không gắn nhãn actual-service E2E. Organization được kiểm lại riêng sau
  deactivation/mapping review cuối: **109/109**, zero failure/error/skip, exit 0. Manifest, flags, migration/rollback/race và residual backlog
  ở [báo cáo dependency](2026-10-05-huy-cross-service-dependencies.md).

**Evidence dependency override, batch 2 — 2026-10-05 (baseline `3103fa1`, chưa commit/push):**

- Billing đã có gated payment command thật cho persisted request, receipts từng installment,
  bounded allocations, exact full-payment grant và immutable held outbox (V5/V6). 7 producer
  fixtures được đọc trực tiếp ở Clinical/Lab/Inpatient/Pharmacy/Surgery/Notification; không
  gọi generic `payment.completed` là clearance. Notification có IN_APP receipt/inbox/outbox
  transaction và signed-patient history privacy; V2 migration, intake/publication OFF.
- Surgery có immutable exact grant value object, decoder/inbox/proof transaction (V2), pending
  early delivery, mismatch/conflict quarantine và nanosecond expiry. Đóng các local leaves
  S-02.1.3a/S-03.2.2a/S-05.3a/P-03.2.3a; task cha giữ mở cho revoke/READY/START/live/cutover.
- Root reactor sau đồng bộ master: **1.759 tests / 313 suites**, exit 0, zero failure/error/skip,
  PG16.14/Rabbit3.13 thật. Đây là lượt trước bổ sung Gateway role correction cuối và kiểm thử
  Surgery invalidation bổ sung; không cộng những lượt chồng nhau để gọi là một root reactor.
- Gateway role correction cuối đã kiểm lại full module: **135/135**, exit 0, zero failure/error/
  skip. Clinical/Lab queue/detail/start/cancel/referral và appointment check-in/start-exam đã
  khớp controller, legacy update/status không đổi quyền. Real-service deployment smoke vẫn mở;
  route được cho qua tới discovery rồi 503 không phải actual-service E2E.
- Surgery đã kiểm lại full module sau invalidation review: **188 tests / 34 suites**, exit 0,
  zero failure/error/skip, PG/Rabbit thật. Sửa mã audit vượt 64 ký tự, thêm 6 application cases
  và 2 PG cases chứng minh release resources + invalidate + inbox/proof cùng transaction.
  Không coi đây là revoke/supersede hoặc live READY/START authority đã hoàn tất.
- Request/charge issuer, catalogue/reconciliation, revoke/refund/top-up/settlement, referral/
  placement, clinical policy authority, Report financial projector và actual-service E2E còn
  là backlog triển khai trong phạm vi override, **không chờ owner viết code**. Các flags OFF.

- [ ] Ghi subtask ID, source commit, files đã sửa, rule/contract version; phân biệt code với design.
- [ ] Unit/domain/application + web/security/architecture liên quan pass; import layers, DTO boundary, actor identity đúng.
- [ ] Nếu có DB/broker: fresh/upgrade/concurrency/rollback/retry Testcontainers chạy thật, không skip critical cases.
- [ ] Producer và consumer dùng cùng fixture khi có wire change; thiếu owner ghi CONTRACT/OWNER, không đóng bằng mock.
- [ ] Migration additive, legacy API/outbox rows đọc được; flag false không tạo V2 effects hoặc ACK mất facts.
- [ ] Nếu ghi E2E_PASS: có Gateway + producers/consumers thật, dữ liệu expected/actual, reconciliation và rollback.
- [ ] git diff --check; link docs/fixture/task IDs hợp lệ; scope Huy + docs + dependency liên service được user giao 2026-10-05, không đổi quyền owner dài hạn hoặc sửa shared root/Common/Compose ngoài scope.
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

### Execution update — 2026-10-05, requested batch of 50

Selection/implementation/test evidence lives in [the fixed 50-item ledger](2026-10-05-huy-50-task-execution.md).
**7 of those 50 are accepted; 43 remain open**, including partial adapters. Main backlog has
**74 non-overlapping unfinished items** after these completions (105 checked / 211 coded entries;
106 unchecked entries include parent/child overlap). This is not a claim that all 50 were completed.

Accepted: S-01.4, S-01.6, S-01.7, S-03.3.2, S-04.3, S-04.6, S-06.4.2.
Surgery module at 2026-10-05: 326 tests, zero failures/errors/skips; actual packaged runtime profile:
3 passed. Gateway and Pharmacy root regression: 162 and 423 respectively. Root test passed before
the final Surgery-only fences; the entire Surgery suite and fresh packaged runtime were then rerun,
including the expiry/reliability follow-up below. The full root reactor was not rerun in this follow-up.
Do not add multiple runs together or substitute these counts for full clinical lifecycle acceptance.

Partial progress: strict gated Surgery/Pharmacy grant intake, early durable recovery, bounded
retry/DLQ/rollback and source authority. Missing referral/charge/revoke/supersede/clinical policy,
full readiness/finalization/START/COMPLETE and remaining workflow contracts stay open. Production
flags default off, held outputs remain held, no fabricated medical rules/prices/owner approval.
Bootstrap handoff retired after actual service acceptance; durable facts moved to canonical docs,
all incoming links updated. No commit/push in this batch; existing uncommitted work preserved.

### Follow-up — readiness expiry/reliability, 2026-10-05

- S-05.4.3/S-05.5: triển khai 3 expiry in-ports/services, driven JDBC port/adapter, worker riêng
  dual-gated và Flyway V4 additive. Candidate exact case/snapshot, Clock sau case lock, exclusive
  validUntil; không tự đoán clinical TTL. PREOP + SYSTEM audit + exact booking release atomic.
  Stale/replacement/started/terminal winners không bị sửa; IN_USE không tự thả vì thời gian.
- Operational retry durable 5..300s, bounded batch 1..100 và transaction timeout 5s. Thêm 33 tests,
  trong đó 11 PG thật: deadline/no validity, audit rollback, two-worker winner, cancellation race,
  committed START/cancel winner, replacement fence, lock timeout/recovery và failed-case backoff
  không chặn healthy case. Đây không phải full READY/finalize/START command hoặc clinical policy pass.
- S-02.6/S-06.5 reliability evidence: thêm 2 PG/Rabbit tests dừng/khởi động lại broker container thật
  và crash-after-confirm-before-DB-mark simulation. Same event ID/bytes được retry sau lease, stale
  token không thể mark winner. At-least-once có duplicate là đúng, không tuyên bố exactly-once hay
  crash JVM riêng. Test harness đọc lại cổng Docker sau restart và chờ AMQP thực sự sẵn sàng.
- Full Surgery/common test run exit 0: **326 Surgery tests, 0 failure/error/skip**; fresh packaged
  runtime profile **3/3**, 0 failure/error/skip. 35 tests mới đã nằm trong 326, không cộng các lượt rerun.
  `git diff --check` sạch. README/service doc/V2 spec/50-item ledger đã cập nhật.
- Không tick thêm task cha: **7/50 accepted, 43 selected open; main plan 74 non-overlapping open**.
  Full invalidation outbound, referral/charge/revoke/clinical-policy và lifecycle acceptance vẫn mở.
  Flags default OFF; held outputs giữ nguyên. Không sửa shared paths, commit hoặc push trong lượt này.

### Follow-up — Organization authority invalidation, 2026-10-06

- Hoàn thiện lát cắt của S-03.5/S-05.4.3: 4 in-ports/services, domain hint + strict wire decoder,
  V5 immutable source evidence + durable jobs, exact case/snapshot/schedule fences và worker/retry.
  Intake ACK chỉ sau inbox/evidence/jobs commit; mutation riêng kiểm tra lại dưới case lock rồi
  PREOP + SYSTEM audit + exact booking release + job completion cùng transaction.
- Organization chỉ thêm 2 producer event fixtures và serializer tests trong phạm vi dependency
  được user giao; không đổi production wire hoặc ownership. Surgery đọc đúng các raw fixture đó.
  Event replay và new-event semantic replay không lặp effect; contradictory source revision dùng
  quarantine/DLQ. Queue/listener/worker có 3 gates đều default OFF; batch 1..100, timeout 5s,
  operational backoff 5..300s; không grant quyền hoặc tự READY/START.
- Full Surgery/common reactor **416 Surgery tests, 0 failure/error/skip**, gồm **90 tests mới**
  (19 PG/Rabbit thật). Full Organization **112/112**, gồm 2 producer tests mới. Không cộng rerun
  hoặc historical 326/408 vào tổng. Bộ PG/Rabbit phủ duplicate/order reversal, exact targeting,
  rollback intake/audit/final marker, hai worker, stale snapshot/schedule, START winner giữ IN_USE,
  transient failure đúng 3 attempts rồi DLQ/replay, durable retry cap và completed-work fence.
- Đây chưa phải actual producer→consumer multi-service event E2E hay existing-volume migration
  acceptance. READY/finalize/START vẫn cần fresh authority/source-revision reconciliation, clinical
  policies và approved notification wire. Không tick các task cha theo subset.
- Sau đóng gói lại current apps, runtime acceptance thật qua Eureka/Gateway/Organization/Surgery
  với owned PG/Rabbit biệt lập đạt **3/3, 0 failure/error/skip**. Chỉ read/draft/revoke/security và
  correlation được chứng minh; không gọi đây là clinical/event lifecycle E2E. Không rerun root.
- [50-item ledger](2026-10-05-huy-50-task-execution.md) cập nhật; **7/50 accepted, 43 selected open**,
  main plan vẫn **74 non-overlapping open**. Không commit/push, không đổi shared paths/held output.

### Follow-up — migration dữ liệu cũ và authority event runtime, 2026-10-06 — VERIFY PENDING

- Bổ sung verification cho **S-02.6/S-03.5/S-05.4.3**, không tick hoàn tất task cha. Test nâng cấp
  PostgreSQL từ từng V1/V2/V3/V4 lên V5 đối chiếu mọi row trước nâng cấp: case SCHEDULED,
  snapshot/history, reservation RESERVED, receipt và pending inbox/outbox bytes, attempts/backoff;
  financial/expiry rows nếu version đã có. Flyway validate và migrate lần hai không đổi dữ liệu;
  không tự tạo authority evidence hoặc invalidation jobs.
- Runtime profile có thêm 3 tình huống bằng current packaged app JVMs: Organization đổi phòng chỉ
  invalidates đúng ca; revoke exact staff-role + replay không lặp effect; Surgery JVM dừng trước
  producer event rồi khởi động lại xử lý message durable. Seed chỉ là Surgery-owned test prerequisite,
  không thay readiness/clinical/referral workflow. Không đọc DB service khác hoặc bật production flags.
- Test sources **compile PASS**; 9 suite không cần Docker **100/100 PASS, 0 failure/error/skip**.
  Docker engine chưa chạy, lần test upgrade lỗi trước khi body thực thi: **4 upgrade cases và
  runtime profile 6 scenarios chưa được xác minh**. Không dùng kết quả 3/3 lịch sử để chốt chúng.
  Khi Docker chạy, thực hiện focused upgrade → full Surgery → package current dependencies →
  explicit runtime acceptance theo README; sửa lỗi nếu xuất hiện rồi mới ghi DB/runtime PASS.
- Chi tiết ở [50-item execution ledger](2026-10-05-huy-50-task-execution.md).
  **7/50 accepted, 43 selected open; main plan 74 non-overlapping open** chưa đổi tại thời điểm journal này.

### Follow-up — 15 Surgery IDs, 2026-10-06

Đã triển khai lát cắt local cho **S-02.3, S-02.6, S-03.5, S-05.1.2, S-05.2.2,
S-05.4.1, S-05.4.2, S-05.5, S-06.1.2, S-06.3.1, S-06.3.2, S-06.4.1,
S-07.1, S-07.2.1, S-07.3**. Trạng thái/đầu ra/điều kiện còn lại của từng ID nằm ở
[bảng 15 mục trong execution ledger](2026-10-05-huy-50-task-execution.md#surgery-15-follow-up).

- Ba policy thuần domain cần cấu hình có phiên bản: checklist evidence (source/exact context,
  correction/expiry/manual/N/A), consent authority theo type (recorder khác signer,
  witness/document/guardian/revoke), team cardinality theo procedure. Không tự điền clinical/legal
  rules, không lấy login role hoặc generic job title làm năng lực mổ.
- Engine readiness kiểm 7 guard độc lập; thiếu/unverifiable/stale/expired evidence không đạt.
  Exact case/patient/department/episode/schedule/source business revision; validUntil là min explicit
  expiry và bị chặn bởi grant thật, giữ nanosecond qua V6 precision shadow mà không rewrite dữ liệu cũ.
- Bốn command nội bộ evaluate/finalize/START/COMPLETE có case-first/sorted resource locks, local
  dependency re-read và Clock sau waits. Failed START/finalize commit denial + PREOP/audit/exact
  release, không throw làm rollback audit. START so exact booking set và foreign IN_USE; COMPLETE
  giữ line IDs riêng, immutable result, replay original ID/time, result/items/history/release/receipt
  cùng transaction. External preflight ngoài locks nhưng vẫn trong transaction; còn phải tách pha
  trước production wiring. Không có production lifecycle bean, authority adapter hoặc endpoint mới.
- V6 `surgery_lifecycle_intent` chỉ HELD, private payload, không dispatcher/routing domain event.
  READY/COMPLETED local intent không thay approved outbound bytes; production producer gate không
  phát bảng này. Approval/translation phải là thay đổi riêng sau cùng fixture/consumer acceptance.
- **S-06.3.1 đã đóng LOCAL** vì mọi reservation writer hiện đi qua cùng case-first/sorted mutex
  protocol và được PG kiểm chứng. 14 ID còn lại là PARTIAL với acceptance cụ thể, không tick giả.
  **8/50 accepted, 42 selected open; main plan 73 non-overlapping open** (không cộng parent/child).
- Final Surgery regression **506/506 PASS**, 57 reports mới, zero failure/error/skip; gồm 90 tests
  mới so với baseline 416 (29 engine + 28 policy + 17 application + 10 PG lifecycle + 5 migration
  upgrade + 1 registration-boundary smoke). First run 498 PASS trước 8 test bổ sung; không cộng
  rerun. Intermediate run lỗi 5 migration cases do concurrent Maven compilation thay class fixture;
  full Surefire chạy lại từ compiled output hiện tại đã exit 0, không còn report stale/error.
  Packaged runtime **6/6 PASS**,
  zero failure/error/skip: current jars, actual Eureka/Gateway/Organization/Surgery, PG/Rabbit riêng,
  room-targeting/staff-replay/consumer-JVM restart và read/draft/security. Không chứng nhận full
  clinical lifecycle mới. V1–V5→V6 existing-data upgrade 5/5 PASS, checksum/rows/bytes giữ nguyên,
  không backfill và migrate lần hai no-op. Kết quả/lệnh chạy và giới hạn nằm trong ledger.
  Không đổi shared production paths, không bật live workflow, không commit/push; giữ toàn bộ
  code chưa commit trước lượt này. Handoff decision chung được cập nhật, không tạo handoff trùng.
