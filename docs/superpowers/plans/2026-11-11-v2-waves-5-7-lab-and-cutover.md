# MediFlow v2.0 Đợt 5–7 gộp — Danh mục, template, duyệt Lab và cutover

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline, có giao việc theo bảng phân tuyến bên dưới). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hoàn thành các đợt còn lại của roadmap v2.0 (Đợt 5, 6, 7), 4 lỗi nhỏ còn để sau của Đợt 4 và dọn test, trong **một lần code**. Sau đó chỉ có **một lần audit và một lượt sửa ở cuối**.

**Architecture:** Lab làm chủ danh mục, template, mẫu bệnh phẩm và báo cáo kết quả. Clinical chọn mã dịch vụ. Billing giữ giá qua `priceCode`. Luồng cũ sống song song qua cột phiên bản quy trình và ba cờ `mediflow.v2.lab-catalog`, `mediflow.v2.lab-review`, `mediflow.v2.lab-amendment` (mặc định `false`). Thứ tự an toàn: bên nhận hiểu `lab.result.amended` trước, bên phát bật sau.

**Tech Stack:** Spring Boot/Maven, Flyway/PostgreSQL, RabbitMQ outbox/inbox, Next.js 16, Flutter/Riverpod.

**Spec:** [`docs/known-limitations/03-clinical-service-improvement-checklist.md`](../../known-limitations/03-clinical-service-improvement-checklist.md) phần 14–17 và 18–21; roadmap [`2026-11-10-mediflow-v2-roadmap.md`](2026-11-10-mediflow-v2-roadmap.md) Đợt 5–7.

## Global Constraints

- Mọi ràng buộc của roadmap vẫn áp dụng. Trong đó có: `@PreAuthorize` trên mọi endpoint; rule ở `RouteAuthorizationFilter` cùng dòng ở `RouteRoleContractTest`; không truy vấn chéo DB; Inpatient/Surgery vẫn deferred.
- Tự duyệt Lab bị cấm, không có cờ ngoại lệ. ADMIN không phát hành kết quả.
- Không tuyên bố tuân thủ pháp lý, tương thích FHIR hay chữ ký số. Khoảng tham chiếu demo mang nhãn "chưa được chuyên môn duyệt".
- Ngoài phạm vi: kết quả từng phần, nuôi cấy/kháng sinh đồ, kết nối máy, chữ ký số (roadmap, quyết định 9).
- Commit có đúng một tác giả người, không có `Co-Authored-By` hay trailer AI. Không `git add` file trong `docs/documentation_docx`. Không đụng nhóm thay đổi "routing nhiều model" chưa commit.
- Không chạy test web song song với Maven.

## Hiện trạng đã đối chiếu (11/11/2026)

| Phát hiện | Vị trí | Hệ quả |
|---|---|---|
| Không có danh mục hay template. Xét nghiệm định danh bằng `labType` tự do (`VARCHAR(50)`) và `priceCode` | `LabTest.java:15-39`, `lab-service V1__init.sql:6`, `CreateClinicalLabOrderRequest.java:9-12` | Đợt 5 là việc thật |
| `recordValidatedResults` đặt thẳng `COMPLETED` và tăng `resultVersion` | `LabTest.java:296-316` | Đợt 6 tách báo cáo ra aggregate riêng |
| `lab_result.value` và `reference_range` là `VARCHAR(50)` | `V1__init.sql:19-25` | Giá trị theo template nằm ở bảng snapshot mới, bảng cũ giữ nguyên |
| Clinical, Notification, Report đều chỉ nhận `resultVersion == 1` | `ClinicalQueueConsumer.java:150-151`, `LabResultCreatedConsumer.java:43`, `PatientResultNoticeDecoder.java:34`, `LabOperationalContributionMapper.java:57`, `ReportCapabilityApplicationService.java:26` | Đính chính đi bằng sự kiện mới `lab.result.amended`. `lab.result.created` giữ nguyên hợp đồng bản 1 |
| Organization đã có capability `LAB_RESULT_RELEASE` và `canReleaseLabResults` | `V21__staff_capability_grant.sql:5`, `StaffCapabilityGrantController.java:64` | Lab chỉ cần client kiểm quyền, không phải sửa Core |
| Gateway chưa có route `/api/v2/lab/**` | `gateway application.yml:30-33`, `SchedulingRouteConfiguration.java` | Phải thêm route, rule và dòng contract |
| Billing tính phí Lab một lần theo yêu cầu (`lab.request.created`), giá lấy qua `priceCode` | `LabTestChargeDecoder.java:39`, `CareFinancePriceCatalogAdapter.java:21-25` | Panel đã là một charge. IMP-FIN-02/03 chủ yếu cần test giữ hành vi |
| Billing legacy tính phí theo `labType` khi nhận `lab.result.created` | `FeeAccrualService.java:42,80` | Phải chứng minh `lab.result.amended` không sinh phí |
| Lab migration cao nhất là V5 | `lab-service/src/main/resources/db/migration` | Đợt 5 bắt đầu ở V6 |

## Lựa chọn của người viết kế hoạch, cần bạn xem lại

1. **Lab làm chủ danh mục.** Mỗi mục danh mục giữ `serviceCode`, nhóm hiển thị, loại bệnh phẩm, template và `priceCode`. Clinical đọc danh mục qua `GET /api/v2/lab/catalog`. Khi cờ bật, chỉ định gửi `serviceCode`. `labType` vẫn được ghi bằng tên dịch vụ để hợp đồng cũ không đổi.
2. **Một chỉ định panel vẫn là một `LabTest` và một charge.** Số chỉ số không tạo thêm charge.
3. **Giá trị theo template lưu ở bảng mới** `lab_report_value`. Bảng `lab_result` cũ chỉ đọc cho dữ liệu cũ. Khi phát hành, Lab vẫn ghi một bản sao dạng chuỗi vào `results` của sự kiện để bên nhận cũ đọc được.
4. **Mẫu bệnh phẩm** do kỹ thuật viên Lab nhận hoặc từ chối. Lấy lại mẫu không tạo chỉ định hay charge mới.
5. **Phát hành cần capability `LAB_RESULT_RELEASE` hỏi Organization ngay lúc phát hành.** Không kiểm được quyền thì từ chối. Người nhập hay người gửi duyệt không được phát hành.
6. **Giá trị nguy cấp:** ngưỡng nằm trong `lab_reference_range`. Khi phát hành có giá trị vượt ngưỡng, Lab tạo `critical_notice` gửi bác sĩ chỉ định. Bác sĩ xác nhận đã nhận qua API riêng. Thông báo in-app không được coi là bằng chứng đã xử trí.
7. **Đợt 7 không backfill kết quả cũ vào template.** Kết quả cũ hiển thị nguyên văn với nhãn "chưa gắn template" (LAB-INT-04).

## Câu hỏi cần bạn chọn: dọn test

Kết quả rà (11/11/2026):
- **Không có test chết.** Cả 13 module đều nằm trong `pom.xml`. Không có test bị `@Disabled` hay `skip`. Chưa thấy test nào trỏ tới lớp đã xóa.
- **"Test của src cũ" thực chất là hai module deferred:**
  - `inpatient-service` có 29 file, khoảng 137 test.
  - `surgery-service` có 81 file, khoảng 655 test.
  - Thêm 4 test web của `features/inpatient`.
- **Test luồng v0 và legacy** ở Lab, Clinical, Pharmacy, Report, Notification là **guard chặn đường cũ**, không phải test thừa. Xóa chúng sẽ mất bảo vệ.

Phương án ở Task C1 tùy theo lựa chọn của bạn (xem cuối tài liệu).

## Phân tuyến model và giao việc

Theo `.claude/rules/model-routing.md`. Codex chạy bằng `codex exec` (workspace-write, không commit) trong worktree riêng, dùng cấu hình `.codex/config.toml`. Phiên chính duyệt diff rồi tự commit.

| Mức | Ai làm | Task |
|---|---|---|
| 0 Khảo sát | `fast-explorer` (Haiku) | Đối chiếu đầu task khi cần tìm khắp nhiều module |
| 1 Việc thường | `routine-coder` (Sonnet) | C1 dọn test; 5.4 seed demo; 5.6 Gateway; 7.3 runbook và tài liệu |
| 2 Lõi nghiệp vụ | Phiên chính (Opus) | 5.1–5.3, 5.5, 6.1–6.7, 4M, 7.1, 7.2 |
| Giao Codex | Codex desktop/CLI | 5.7 web danh mục và form template; 6.8 web nháp, duyệt, phát hành; 5.8/6.9 mobile |
| Review cuối | `code-reviewer` trên Opus | Một lần cho toàn nhánh |
| 3 Leo thang | `fable-advisor` | Chỉ khi Opus bế tắc **và** bạn duyệt |

Nếu Codex hết credit giữa chừng, `routine-coder` hoặc phiên chính làm tiếp và ghi ruling vào ledger.

## Review Focus

- **Hai người cùng sửa một nháp:** bản lưu sau phải bị từ chối nếu `draftRevision` đã cũ (6.2, `LabReportDraftConcurrencyTest`).
- **Quyền phát hành bị thu hồi khi báo cáo đang chờ duyệt:** lần phát hành sau bị 403 (6.3, `LabReportReleaseAuthorityTest`).
- **`lab.result.amended` bản 3 đến trước bản 2:** bản 2 đến sau không ghi đè bản 3 (6.5, `LabResultRevisionOrderingTest` ở Clinical và Report).
- **Template đổi phiên bản khi phiếu đang làm:** phiếu giữ phiên bản đã gán (5.2, `TemplateVersionPinningTest`).
- **Kết quả cũ không có template:** hiển thị nguyên văn với nhãn "chưa gắn template" trên web và mobile (7.1, `LegacyLabResultRenderingTest`).

---

## Phần C — Dọn test (làm trước, vì giảm thời gian chạy cho mọi task sau)

### Task C1: Dọn test theo phương án bạn chọn

**Người làm:** `routine-coder`. Phiên chính kiểm diff.

- [ ] **Step 1:** Làm theo phương án đã chọn:
  - **(A, khuyến nghị)** Đưa `inpatient-service` và `surgery-service` ra khỏi `<modules>` mặc định của `pom.xml`, chuyển vào profile `deferred` (`mvn -P deferred test`). Code và test được giữ nguyên.
  - **(B)** Xóa `src/test` của hai module deferred và 4 test web inpatient.
- [ ] **Step 2:** Chạy `mvn -q -DskipTests install` rồi `mvn -q test -pl backend/clinical-service -am -Dtest=StartExamRoomTicketTest -Dsurefire.failIfNoSpecifiedTests=false`. Expected: build xanh, không còn chạy test Inpatient/Surgery.
- [ ] **Step 3:** Cập nhật `AGENTS.md` (mục Build & run) và CI (`.github/workflows/*`) nếu có tham chiếu hai module này.
- [ ] **Step 4:** Commit `chore(build): move deferred inpatient and surgery tests out of the default run` (hoặc `test: remove deferred inpatient and surgery tests` nếu chọn B).

## Phần 4M — Lỗi nhỏ còn lại của Đợt 4

### Task 4M: Bốn lỗi nhỏ

**Files:** `RecordAmendmentApplicationService`, `TreatmentCourseApplicationService`, `ClinicalCareFinanceApplicationService` (lời hẹn tái khám), `frontend/.../RecordHandoverPanel.tsx` cùng widget mobile tương ứng.

- [ ] **Step 1: Viết bốn test đỏ:**
  - `RecordAmendmentApplicationServiceTest.headCanRevokeAnAssignment`: `DELETE /api/v2/records/{id}/amendments/{amendmentId}/assignment` trả 204, người được giao mất quyền quyết định.
  - `TreatmentCourseApplicationServiceTest.headOfAnotherDepartmentIsNotHead`.
  - `FollowUpTest.preferredDoctorIsTheResponsibleDoctorAfterHandover`.
  - `RecordHandoverPanel.test.tsx` "shows the read-only wording only after acceptance".
- [ ] **Step 2:** Sửa cho đủ bốn test. Endpoint thu hồi phân công cần thêm route Gateway và dòng `RouteRoleContractTest`.
- [ ] **Step 3:** Commit `fix(clinical,web,mobile): close wave 4 deferred minors`.

## Phần 5 — Danh mục và template (cờ `mediflow.v2.lab-catalog`)

### Task 5.1: Schema và domain danh mục, template

**Files:**
- Create: `lab-service/.../db/migration/V6__lab_catalog_and_templates.sql`
- Create: `domain/model/LabServiceCatalogItem.java`, `LabResultTemplate.java`, `LabTemplateField.java`, `LabReferenceRange.java`, `LabValueType.java` (`QUANTITATIVE`, `QUALITATIVE`, `SEMI_QUANTITATIVE`, `TEXT`)
- Test: `LabTemplateValidationTest`, `TemplateVersionPinningTest`, `LabCatalogPostgresTest`

**Interfaces — Produces:**
- `LabResultTemplate.validate(List<LabValueInput>) → List<LabValueViolation>`
- `LabValueInput(String fieldCode, String operator, String value, String noResultReason)`
- `LabReferenceRange.flag(BigDecimal) → LabFlag` (`LOW`, `NORMAL`, `HIGH`, `CRITICAL_LOW`, `CRITICAL_HIGH`, `UNDETERMINED`)

**Bảng:**
- `lab_service_catalog`: `service_code` UNIQUE, `name` ≤50, `display_group`, `specimen_type`, `preparation`, `turnaround_hours`, `price_code`, `template_code`, `active`.
- `lab_panel_item`
- `lab_result_template`: khóa `(template_code, version)`.
- `lab_template_field`: `field_code`, `label`, `position`, `required`, `value_type`, `unit`, `allowed_options` JSONB, `allows_operator`.
- `lab_reference_range`: `sex`, `age_min_days`, `age_max_days`, `low`, `high`, `critical_low`, `critical_high`, `reviewed` boolean.
- `lab_test` thêm `service_code`, `template_code`, `template_version` (nullable).

- [ ] **Step 1: Test đỏ:**
  - CBC thiếu `HGB` bắt buộc trả vi phạm `REQUIRED_MISSING`.
  - `"< 0.5"` hợp lệ khi `allows_operator`, sai khi không.
  - Giá trị định tính ngoài `allowed_options` trả `OPTION_INVALID`.
  - Thiếu khoảng tham chiếu cho tuổi và giới thì `flag` trả `UNDETERMINED`, không trả `NORMAL`.
  - Mã trùng trả `DUPLICATE_FIELD`.
  - Thiếu kết quả mà có `noResultReason` thì hợp lệ, không coi là 0.
  - Tạo template v2 thì phiếu đã gán v1 vẫn đọc v1.
- [ ] **Step 2:** Viết code và migration cho tới khi test xanh. Chạy `mvn -q test -pl backend/lab-service -am -Dtest='LabTemplate*,TemplateVersion*,LabCatalog*'`.
- [ ] **Step 3:** Commit `feat(lab): catalog, panels and versioned result templates`.

### Task 5.2: API danh mục và gắn template khi tạo xét nghiệm

**Files:** `web/LabCatalogController.java` (`/api/v2/lab/catalog`), `LabRequestCreationApplicationService`, `LabClinicalOrderContextAdapter`, `LabTest.createV2`.

- [ ] **Step 1: Test đỏ:**
  - `GET /api/v2/lab/catalog?group=` cho phép ADMIN, DOCTOR, NURSE, LAB_TECH; PATIENT bị 403.
  - Cờ bật và context chỉ định có `serviceCode`: `LabTest` ghi `service_code`, `template_code`, phiên bản đang hoạt động, còn `priceCode` lấy từ danh mục.
  - `serviceCode` không có hoặc đã ngừng: trả `LAB_SERVICE_UNKNOWN`.
  - Cờ tắt: hành vi cũ giữ nguyên.
- [ ] **Step 2:** Viết code tới khi xanh. Commit `feat(lab): serve the catalog and pin templates on new tests`.

### Task 5.3: Clinical chỉ định theo mã dịch vụ

**Files:** `CreateClinicalLabOrderRequest` (thêm `serviceCode`, tùy chọn), `ClinicalLabOrderApplicationService`, `ClinicalLabOrder`, migration Clinical V26 thêm `service_code`, context controller trả `serviceCode`.

- [ ] **Step 1: Test đỏ:**
  - Cờ bật mà thiếu `serviceCode` trả 422 `CLINICAL_LAB_SERVICE_REQUIRED`.
  - Có `serviceCode` thì context Lab nhận đúng mã.
  - Cờ tắt thì request cũ vẫn chạy.
  - Bump pin `ConsumerInboxPostgresTest` lên "26".
- [ ] **Step 2:** Viết code tới khi xanh. Commit `feat(clinical): order lab tests by catalog service code`.

### Task 5.4: Seed danh mục demo

**Người làm:** `routine-coder`.

- [ ] **Step 1:** Seed các dịch vụ:
  - CBC dạng panel: WBC, RBC, HGB, HCT, PLT.
  - Glucose: định lượng, có operator.
  - HBsAg: định tính, gồm âm tính, dương tính và chưa xác định.
  - Tổng phân tích nước tiểu: hỗn hợp.
  - Soi nhuộm vi sinh cơ bản: TEXT.

  Mỗi khoảng tham chiếu có `reviewed=false`. `priceCode` phải trùng mã giá có sẵn trong `billing.care-finance`.
- [ ] **Step 2:** Test seed `LabDemoCatalogSeedTest`. Mọi `priceCode` của danh mục phải có trong cấu hình giá Billing demo; đọc file cấu hình, không gọi chéo service.
- [ ] **Step 3:** Commit `chore(lab): seed demo catalog marked as not clinically reviewed`.

### Task 5.5: Billing giữ đúng một charge cho panel

- [ ] **Step 1: Test giữ hành vi** `LabTestChargeConsumerTest.panelWithManyAnalytesIsOneCharge`. Test thêm `FeeAccrualServiceTest.resultWithManyRowsAddsNoExtraCharge`. Expected: PASS ngay. Nếu đỏ thì sửa.
- [ ] **Step 2:** Commit `test(billing): pin one charge per panel`.

### Task 5.6: Gateway cho `/api/v2/lab/**`

**Người làm:** `routine-coder`. Phiên chính duyệt rule quyền.

- [ ] **Step 1:** Thêm route `/api/v2/lab/**` tới lab-service trong `SchedulingRouteConfiguration`. Thêm rule ở `RouteAuthorizationFilter` cho mọi endpoint của 5.2, 6.x và 4M. Thêm dòng ở `RouteRoleContractTest`.
- [ ] **Step 2:** Chạy `mvn -q test -pl backend/gateway`. Commit `feat(gateway): route v2 lab endpoints`.

### Task 5.7: Web danh mục và form theo template

**Người làm:** Codex (worktree `../v2-w57-web`). Owner: Vinh (Clinical/Lab).

- [ ] **Brief cho Codex:**
  - `CreateSourceLabRequest` chọn dịch vụ theo nhóm từ `GET /api/v2/lab/catalog`.
  - `LabActions` sinh trường từ template: đơn vị và khoảng tham chiếu chỉ đọc; operator chỉ hiện khi `allowsOperator`; định tính là select; ô "lý do không có kết quả".
  - Cờ tắt hoặc endpoint trả 404 thì giữ form cũ.
  - Test ở `CreateSourceLabRequest.test.tsx` và `LabActions.test.tsx`.
- [ ] **Phiên chính:** chạy `pnpm --dir frontend test -- lab`, `pnpm typecheck`, `pnpm lint`, rồi commit `feat(web): catalog lab orders and template result form`.

### Task 5.8: Mobile danh mục và form template

**Người làm:** Codex, cùng worktree với 5.7 sau khi 5.7 xong.

Brief tương tự 5.7 cho `mobile/lib/features/lab/` và chỗ tạo chỉ định ở `medical_record`. Kèm test widget và repository. Phiên chính chạy `flutter test`, `flutter analyze`, rồi commit `feat(mobile): catalog lab orders and template result form`.

## Phần 6 — Mẫu bệnh phẩm, nháp, duyệt, phát hành, đính chính (cờ `lab-review`, `lab-amendment`)

### Task 6.1: Bên nhận hiểu `lab.result.amended` trước (Clinical, Notification, Report)

**Hợp đồng `lab.result.amended` v1** (envelope `DomainEventEnvelope`, producer `lab-service`):
- `labId`, `patientId`, `recordId`, `departmentId`, `careEpisodeType`, `careEpisodeId`
- `resultVersion` (≥2), `previousResultVersion`, `amendmentReason`, `results`, `values`, `conclusion`
- `releasedBy`, `releasedAt`, `templateCode`, `templateVersion`

`lab.result.created` thêm các field tùy chọn `values`, `templateCode`, `templateVersion`, `reportId`, `releasedBy`. `resultVersion` vẫn bằng 1.

- [ ] **Step 1: Khóa fixture.** Thêm fixture JSON cho cả hai sự kiện vào `docs/plans/22-v2-contract-map.md` và test fixture ở từng bên nhận.
- [ ] **Step 2: Test đỏ:**
  - Clinical `LabResultRevisionOrderingTest`: bản 2 rồi bản 3 thì hiện bản 3; bản 3 rồi bản 2 thì vẫn bản 3; replay bản 2 không tạo dòng mới; tài liệu giữ lịch sử revision.
  - Notification `PatientResultAmendedNoticeTest`: một thông báo cho mỗi `(labId, resultVersion)`, nội dung dẫn tới bản hiện hành.
  - Report `LabOperationalContributionMapperTest.amendmentIsNotANewTest`: revision không tăng số test.
  - Billing `FeeAccrualServiceTest.amendedResultAddsNoCharge`.
- [ ] **Step 3:** Viết code tới khi xanh. Mỗi bên dedupe theo `labId:resultVersion` và bỏ qua revision cũ hơn bản đang giữ. Report đổi capability thành `RESULT_REVISIONS`. Migration Clinical V27 thêm `attached_result.result_version` nếu chưa có.
- [ ] **Step 4:** Commit `feat(clinical,support,billing): accept lab result amendments`.

### Task 6.2: `LabReport` nháp và gửi duyệt

**Files:**
- Create: migration `V7__lab_report_workflow.sql`. Bảng `lab_report` gồm `status` (`DRAFT`, `PENDING_REVIEW`, `RELEASED`), `draft_revision`, `entered_by`, `submitted_by`, `returned_by`, `return_reason`, `released_by`, `released_at`, `result_version`. Bảng `lab_report_value`, `lab_report_revision`. `lab_test.result_workflow_version` mặc định 0.
- Create: `domain/model/LabReport.java`
- Create: `web/LabReportController.java` với `PUT /api/v2/lab/{id}/report/draft`, `POST .../submission`, `POST .../return`
- Test: `LabReportTest`, `LabReportDraftConcurrencyTest`, `LabReportControllerTest`

- [ ] **Step 1: Test đỏ:**
  - Lưu nháp không đổi `LabTest` khỏi `IN_PROGRESS` và không ghi outbox.
  - `draftRevision` cũ trả 409 `LAB_REPORT_STALE`.
  - Gửi duyệt chạy validate template; thiếu bắt buộc trả 422 `LAB_REPORT_INVALID`.
  - Sửa bản `PENDING_REVIEW` trả 409.
  - Trả lại thiếu lý do trả 422; có lý do thì về `DRAFT`.
  - Chỉ LAB_TECH được lưu và gửi; ADMIN bị 403.
- [ ] **Step 2:** Viết code tới khi xanh. Commit `feat(lab): draft and submit lab reports for review`.

### Task 6.3: Phát hành có kiểm quyền

**Files:** `LabReleaseAuthorityPort` cùng adapter Feign tới Organization `labReleaseAuthority`, `POST /api/v2/lab/{id}/report/release`.

- [ ] **Step 1: Test đỏ:**
  - Người nhập hoặc người gửi phát hành: 403 `LAB_SELF_RELEASE`.
  - Không có capability: 403 `LAB_RELEASE_DENIED`.
  - Organization timeout: 403, không cho qua.
  - Capability bị thu hồi sau khi gửi duyệt: 403 (`LabReportReleaseAuthorityTest`).
  - ADMIN: 403.
  - Phát hành thành công: `LabTest` sang `COMPLETED`, một outbox `lab.result.created` bản 1 có `values` và `releasedBy`; báo cáo bị khóa.
  - Cờ `lab-review` tắt: endpoint cũ `PUT /api/v1/lab/{id}/results` giữ nguyên hành vi. Cờ bật: endpoint cũ trả 409 `LAB_REVIEW_REQUIRED` với test có `result_workflow_version=1`.
- [ ] **Step 2:** Viết code tới khi xanh. Commit `feat(lab): release reports only by an authorised second person`.

### Task 6.4: Mẫu bệnh phẩm

**Files:** `Specimen` (migration V8): `accession` UNIQUE, `specimen_type`, `collected_by/at`, `received_by/at`, `decision` (`ACCEPTED`, `REJECTED`), `rejection_reason`, `replaces_specimen_id`. Endpoint `POST /api/v2/lab/{id}/specimens` và `PUT /api/v2/lab/specimens/{id}/decision`.

- [ ] **Step 1: Test đỏ:**
  - Từ chối thiếu lý do trả 422.
  - Lấy lại mẫu liên kết `replaces_specimen_id`, không tạo `LabTest` hay outbox mới.
  - Một mẫu gắn được nhiều test cùng bệnh nhân; khác bệnh nhân trả 422.
  - Khi cờ `lab-review` bật, chưa có mẫu `ACCEPTED` thì chưa gửi duyệt được.
- [ ] **Step 2:** Viết code tới khi xanh. Commit `feat(lab): track specimens, rejection and recollection`.

### Task 6.5: Đính chính kết quả đã phát hành (cờ `lab-amendment`)

**Files:** `POST /api/v2/lab/{id}/report/amendments` (tạo bản nháp revision mới có lý do), rồi submission và release như 6.2–6.3. Release revision ≥2 phát `lab.result.amended`.

- [ ] **Step 1: Test đỏ:**
  - Bản 1 không bị sửa; revision 2 giữ bản 1 trong `lab_report_revision`.
  - Thiếu lý do trả 422.
  - Cờ tắt trả 404.
  - Release revision 2 ghi đúng một outbox `lab.result.amended` có `previousResultVersion=1`.
  - Tự duyệt bị cấm như ở 6.3.
- [ ] **Step 2:** Viết code tới khi xanh. Commit `feat(lab): amend released results through a new reviewed revision`.

### Task 6.6: Giá trị nguy cấp và xác nhận

**Files:** bảng `critical_notice` (Lab V9), endpoint `POST /api/v2/lab/{id}/critical-notices/{noticeId}/acknowledgement` (DOCTOR là bác sĩ chỉ định hoặc bác sĩ phụ trách), danh sách `GET /api/v2/lab/critical-notices?status=OPEN`. Notification nhận `lab.critical.raised` và báo cho bác sĩ.

- [ ] **Step 1: Test đỏ:**
  - Phát hành có giá trị vượt `critical_high` tạo đúng một notice `OPEN`.
  - Cờ `HIGH` thường không tạo notice.
  - Bác sĩ khác xác nhận: 403.
  - Xác nhận lần hai trả cùng kết quả (idempotent).
  - Notification gửi tới bác sĩ, không gửi bệnh nhân.
- [ ] **Step 2:** Viết code tới khi xanh. Thêm route Gateway (nhờ `routine-coder`, như 5.6). Commit `feat(lab,support): critical result notices with acknowledgement`.

### Task 6.7: Clinical, bệnh nhân và in ấn chỉ thấy bản đã phát hành

- [ ] **Step 1: Test đỏ:**
  - `GET /api/v1/lab/mine` và tài liệu của hồ sơ không lộ nháp hay bản chờ duyệt.
  - Tài liệu hiện bản hiện hành, kèm người phát hành và các bản trước có nhãn "đã đính chính".
  - Hàng việc Lab `GET /api/v2/lab/worklist` có dịch vụ, nhóm, trạng thái mẫu, trạng thái báo cáo và hạn trả (LAB-WF-01).
- [ ] **Step 2:** Viết code tới khi xanh. Commit `feat(lab,clinical): show only released revisions and a lab worklist`.

### Task 6.8: Web luồng duyệt

**Người làm:** Codex (worktree `../v2-w6-web`).

- [ ] **Brief cho Codex:**
  - Hàng việc Lab có lọc theo nhóm.
  - Màn mẫu bệnh phẩm.
  - Ba nút Lưu nháp, Gửi duyệt, Phát hành. Nút hiện theo quyền và theo người; 403 là thông báo, không phải lỗi chung.
  - Trả lại có lý do. Đính chính có lý do.
  - Màn xác nhận nguy cấp cho bác sĩ.
  - Tài liệu hồ sơ hiện revision.
  - Màn ADMIN cấp quyền phát hành Lab gọi API capability có sẵn (Wave 1 hoãn phần UI này sang Đợt 6).
  - Test cho từng component.
- [ ] **Phiên chính:** chạy test, typecheck, lint, rồi commit `feat(web): lab review, release, amendment and critical notices`.

### Task 6.9: Mobile luồng duyệt

**Người làm:** Codex, sau 6.8. Phạm vi theo slice mobile hiện có: bệnh nhân chỉ thấy bản đã phát hành, bác sĩ xác nhận nguy cấp, nhân viên Lab xem hàng việc. Phiên chính chạy `flutter test`, `flutter analyze`, rồi commit.

## Phần 7 — Tổng kết client, dữ liệu cũ, cutover

### Task 7.1: Dữ liệu cũ và quét client

- [ ] **Step 1: Test đỏ:**
  - `LegacyLabResultRenderingTest` ở Lab: kết quả không có template trả `templateCode=null` và nguyên văn chuỗi.
  - Test web và mobile hiện nhãn "chưa gắn template".
- [ ] **Step 2:** `fast-explorer` rà mọi route web và màn mobile gọi API Lab hoặc Clinical đã đổi (IMP-APP-01/02). Phiên chính sửa những chỗ lệch hợp đồng. Bên khác được sửa nếu có trong scope; ngoài scope thì ghi HANDOFF.
- [ ] **Step 3:** Commit `fix(web,mobile): render legacy lab results and align clients`.

### Task 7.2: Migration không backfill nghiệp vụ

- [ ] **Step 1: Test** `LabWorkflowMigrationPostgresTest`:
  - V6–V9 chạy trên DB có dữ liệu V5.
  - `lab_test` cũ có `result_workflow_version=0` và giữ nguyên `COMPLETED`.
  - Outbox không thêm dòng nào.
  - Không có thông báo hàng loạt.
- [ ] **Step 2:** Commit `test(lab): migrations keep legacy results untouched`.

### Task 7.3: Runbook bật cờ và tài liệu

**Người làm:** `routine-coder`. Phiên chính duyệt.

- [ ] **Step 1:** Viết `docs/plans/27-v2-cutover-runbook.md`:
  - **Thứ tự bật cờ:** consumer trước, sau đó `lab-catalog`, rồi `lab-review`, rồi `lab-amendment`.
  - Cách quan sát lỗi: quarantine outbox và DLQ.
  - Phương án dừng: tắt cờ; không xóa kết quả đã phát hành.
  - Client cũ khi chạy với backend mới.
- [ ] **Step 2:** Cập nhật `docs/plans/02-business-flows.md`, `03-contracts.md`, `docs/known-limitations/README.md` và ghi chú Đợt 5–7 trong checklist.
- [ ] **Step 3:** Commit `docs(v2): cutover runbook and wave 5-7 contracts`.

## Phần V — Xác minh, audit một lần, sửa một lượt (bước cuối)

### Task V1: Chạy toàn bộ

- [ ] **Step 1:** Chạy `mvn -q test` (sau C1 thì không còn chạy module deferred). Kết quả ghi vào log trong workspace.
- [ ] **Step 2:** Chạy web test (riêng, không song song với Maven), typecheck và lint. Chạy `flutter test` và `flutter analyze`.
- [ ] **Step 3: Nghiệm thu qua Gateway** trên DB sạch (`mediflow-w2`, clock image, seed, cờ bật). Các kịch bản:
  - Chỉ định CBC theo mã, đủ một charge.
  - Nhận mẫu, từ chối mẫu, lấy lại mẫu.
  - Lưu nháp: không có sự kiện, Clinical chưa thấy kết quả.
  - Gửi duyệt thiếu HGB: 422.
  - Tự phát hành: 403. ADMIN phát hành: 403.
  - LAB_TECH thứ hai có capability phát hành: 200; Clinical thấy kết quả; hoàn tất lượt được.
  - Đính chính: Clinical thấy bản 2; bản 1 còn trong lịch sử; Billing không thêm charge.
  - Giá trị nguy cấp: bác sĩ nhận và xác nhận.
  - Tắt ba cờ: luồng cũ chạy như trước.

  Ghi kết quả vào `docs/plans/27-v2-wave-5-7-acceptance.md`.

### Task V2: Review độc lập một lần

- [ ] **Step 1:** Dùng `review-package` để tạo gói diff. Gửi `code-reviewer` (Opus) review kèm mục Review Focus và các dòng `Ruling:` trong ledger.
- [ ] **Step 2:** Chấm lại mức độ theo tác động thật. Critical và Important vào lượt sửa; Minor ghi `minor (deferred)`.

### Task V3: Lượt sửa duy nhất

- [ ] **Step 1:** Mỗi lỗi làm đúng thứ tự: test đỏ → sửa → test xanh.
- [ ] **Step 2:** Chạy lại toàn bộ suite có liên quan. Nếu lỗi chạm luồng chéo service, chạy lại nghiệm thu Gateway.
- [ ] **Step 3:** Commit theo service. Cập nhật tài liệu nghiệm thu với bảng "Sửa sau review độc lập".
- [ ] **Step 4:** Xóa workspace kế hoạch. Hỏi bạn trước khi push.
