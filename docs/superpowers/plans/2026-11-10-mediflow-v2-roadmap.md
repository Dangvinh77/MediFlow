# MediFlow v2.0 Implementation Roadmap

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Triển khai toàn bộ 117 mục của checklist Clinical/Lab trên backend, web và mobile, trên nhánh `develop/mediflow-v2.0`.

**Architecture:** Chia 8 đợt, mỗi đợt tự chạy và kiểm thử được. Thay đổi hợp đồng đi theo thứ tự: khóa fixture, nâng bên nhận, rồi mới bật bên phát qua feature flag. Dữ liệu cũ được giữ nguyên ý nghĩa bằng cột phiên bản quy trình, không ghi đè trạng thái cũ.

**Tech Stack:** Spring Boot/Maven, Flyway/PostgreSQL, RabbitMQ outbox/inbox, Next.js 16, Flutter/Riverpod.

**Spec:** [`docs/known-limitations/03-clinical-service-improvement-checklist.md`](../../known-limitations/03-clinical-service-improvement-checklist.md)

**Trạng thái:** Đã duyệt ngày 10/11/2026. Baseline: `3f3dcb8a`.

## Global Constraints

- Mỗi đợt chỉ được coi là xong khi có bằng chứng test; checkbox trong checklist là quyết định, không phải nghiệm thu.
- Clean architecture, `@PreAuthorize` trên mọi endpoint, id là `UUID`, không truy vấn chéo DB (`AGENTS.md`).
- Mỗi endpoint mới phải thêm rule theo method/path ở `RouteAuthorizationFilter` và test ở `RouteRoleContractTest`. Endpoint `/api/v2/*` còn phải khai báo route ở Gateway, vì `SchedulingRouteConfiguration` chỉ liệt kê từng path cụ thể và chưa có route `/api/v2/lab`.
- Web chỉ gọi Gateway qua `src/lib/api.ts`; mobile chỉ qua `core/network/api_client.dart`.
- Inpatient/Surgery vẫn deferred, không bật lại.
- Commit có đúng một tác giả người trong `scripts/allowed-contributors.json`, không có trailer AI (`AGENTS.md`).
- Không tuyên bố tuân thủ pháp lý, tương thích FHIR hay chữ ký hợp pháp ở bất kỳ UI hoặc tài liệu nào.

## Quyết định đã chốt ngày 10/11/2026

| Quyết định | Nội dung |
|---|---|
| Phạm vi | Làm cả 117 mục, gồm 24 mục IMP ở phần 18–21 |
| Tự duyệt Lab | Cấm. Backend từ chối khi người duyệt trùng người nhập, không có cờ ngoại lệ |
| Quá trình điều trị | Xây đầy đủ (REC-04), không dừng ở liên kết lượt trước |
| Cách làm | Duyệt kế hoạch này trước khi sửa code |

## Lựa chọn của người viết kế hoạch, cần bạn xem lại

Các mục dưới không có trong câu trả lời của bạn. Chúng thay đổi hành vi thật, nên hãy sửa ngay nếu không đồng ý.

1. **Tên aggregate là `TreatmentCourse`, không phải `CareEpisode`.** `careEpisodeId` đã phát hành và đang có nghĩa là mã lượt khám (`LabTest.java:23`, `docs/plans/03-contracts.md`). IMP-ID-01 cấm đổi nghĩa mã này. Trên UI vẫn gọi là "Quá trình điều trị".
2. **Quyền đọc hồ sơ theo liên hệ điều trị hoặc khai báo.** Có lượt được giao hoặc khoa có lượt trong 30 ngày thì đọc được ngay; không có thì người đọc chọn mục đích (`DECLARED_PURPOSE`). Mọi lần đọc đều lưu vết, không chặn tuyệt đối theo khoa. Chi tiết ở kế hoạch Đợt 1.
3. **ADMIN không đọc nội dung chuyên môn nếu không khai lý do.** Quản trị kỹ thuật không tự có quyền khai thác hồ sơ.
4. **NURSE đọc đầy đủ record của khoa mình khi khoa có lượt khám của bệnh nhân**; ngoài ra chỉ qua khai báo cấp cứu.
5. **Người duyệt đính chính hồ sơ là trưởng khoa**, dùng bổ nhiệm sẵn có ở `V15__department_head_appointments.sql`. Khi tác giả nghỉ việc, trưởng khoa phân công người xử lý và danh tính người thật vẫn được giữ.
6. **Quyền duyệt Lab là một capability `LAB_RESULT_RELEASE` do Organization cấp theo từng nhân viên**, không thêm role mới. ADMIN không tự có quyền này. Demo cần thêm một tài khoản Lab thứ hai.
7. **`LabTest` giữ `IN_PROGRESS` tới khi phát hành.** Nháp và chờ duyệt nằm ở aggregate `LabReport` riêng. Nhờ vậy `ClinicalLabCompletionGuard` vẫn chặn hoàn tất khi kết quả chưa phát hành mà không phải sửa, và Billing, Report không thấy trạng thái mới.
8. **Sự kiện:** `lab.result.created` vẫn phát một lần khi phát hành bản 1. Đính chính dùng sự kiện mới `lab.result.amended`.
9. **Ngoài phạm vi v2.0:** kết quả từng phần, nuôi cấy/kháng sinh đồ, kết nối máy xét nghiệm, chữ ký số pháp lý. AMD-04 chỉ làm xác nhận nội bộ có hash nội dung và không được gọi là chữ ký.
10. **Khoảng tham chiếu trong seed demo được gắn nhãn "chưa được chuyên môn duyệt".** Thiếu cấu hình thì hiện "chưa xác định", không tô bình thường.

## Hiện trạng đã đối chiếu trong source

| Phát hiện | Vị trí | Hệ quả |
|---|---|---|
| Hai GET hồ sơ không kiểm quan hệ điều trị và không lưu vết | `MedicalRecordApplicationService.java:136-145` | ACL-01 là việc thật |
| Guard ghi bị bỏ qua khi `outpatientMvpEnabled = false` | `MedicalRecordApplicationService.java:180-182` | ACL-02 phải kiểm cả nhánh này |
| Hồ sơ chỉ có một `doctorId`, chỉ có `symptoms`, chẩn đoán, hướng xử trí | `MedicalRecord.java` | COL-01, REC-06 cần cấu trúc mới |
| Guard chỉ định Lab còn chờ đã có khi hoàn tất | `ClinicalLabCompletionGuard.java:67-71` | CL-INT-02 chỉ cần thêm đường hủy/hoãn có lý do |
| `recordValidatedResults` đặt thẳng `COMPLETED` | `LabTest.java:311` | LAB-WF-03 là việc thật |
| `lab_result.value`, `reference_range` là `VARCHAR(50)` | `lab-service V1__init.sql:23-25` | Template mới cần bảng snapshot riêng, giữ bảng cũ |
| Clinical từ chối `resultVersion != 1` | `LabResultCreatedConsumer.java:43` | Đính chính sẽ hỏng nếu sửa Lab trước |
| Report khai báo `RESULT_VERSION_ONE_ONLY` | `ReportCapabilityApplicationService.java:26` | Report phải nâng trước khi bật đính chính |
| Authority chỉ có `canManageSchedules` | `SchedulingAuthority.java` | IMP-ID-02 cần thêm capability |
| ADMIN được ghi kết quả Lab | `LabController.java:91,101` | LAB-WF-04 phải chặn ADMIN duyệt |

Các mục còn lại mang nhãn [AUDIT] trong checklist chưa được đối chiếu ở đây. Mỗi đợt mở đầu bằng việc đối chiếu các mục của đợt đó; mục đã đúng chỉ cần test giữ hành vi.

## Các đợt triển khai

Mỗi mã thuộc đúng một đợt. Web và mobile của đợt nào làm luôn trong đợt đó, trừ phần tổng kết ở Đợt 7.

### Đợt 0 — Khóa hợp đồng và baseline

**Mã:** MIG-01, MIG-03, IMP-CON-01, IMP-ID-01, LAB-INT-05

Tác vụ chi tiết nằm ở cuối tài liệu.

### Đợt 1 — Quyền đọc, quyền ghi và lưu vết

**Mã:** REC-02, ACL-01, ACL-02, ACL-03, ACL-04, ACL-05, ACL-06, AMD-01, AMD-03, VIEW-01, VIEW-02, UX-02, IMP-ID-02, IMP-ID-03, IMP-ID-06

- **Clinical:** Migration V13 tạo `record_access_log` (actor, record, action, basis, reason, at). Thêm `RecordReadPolicy` và endpoint `GET /api/v2/records/{id}/capabilities`.
- **Organization:** Migration V21 tạo bảng cấp capability; thêm `canReleaseLabResults` vào authority snapshot.
- **Lab, Pharmacy:** Áp cùng bằng chứng quyền đọc cho tài liệu liên quan; authority timeout thì từ chối.
- **Web:** Tách vùng lịch sử chỉ đọc khỏi vùng ghi lượt đang mở; nút hiển thị theo capability; hủy phản hồi cũ khi đổi bệnh nhân.
- **Mobile:** Đọc capability, xử lý 403 riêng với không có dữ liệu.
- **Nghiệm thu:** Bác sĩ B đọc được hồ sơ của C và không sửa được; nhân viên bị vô hiệu bị từ chối dù token còn hạn; mọi lần đọc có dòng log.

### Đợt 2 — Lịch, đăng ký và hàng chờ

**Mã:** CL-SCH-01, CL-SCH-02, CL-SCH-03, CL-SCH-04, CL-SCH-05, CL-SCH-06, CL-REG-01, CL-REG-02, CL-REG-03, CL-REG-04, CL-REG-05, CL-REG-06, CL-QUE-01, CL-QUE-02, CL-QUE-03, CL-QUE-04, CL-QUE-05, IMP-ID-05, IMP-FIN-04, IMP-NOT-02

**Bổ sung ngoài 117 mục (yêu cầu 10/11/2026):** CL-INK-01 số phòng khám do quầy điều dưỡng khoa cấp, CL-INK-02 sinh hiệu, CL-INK-03 gọi vào khám. Nghiệp vụ ở [`02-business-flows.md`, mục 4.2](../../plans/02-business-flows.md).

- **Clinical:** Một chính sách duy nhất cho hai đường tạo buổi; báo tác động khi sửa/hủy ca có phiếu; bảng `queue_adjustment` và `appointment_reschedule_log`.
- **Clinical, quầy điều dưỡng khoa:** Bảng `clinic_queue_ticket` với unique `(room_id, queue_date, room_queue_number)` và bộ đếm theo phòng, ngày; bảng chỉ thêm `vital_sign_observation`. Khi bật flag, `startScheduledExam` yêu cầu có số phòng khám và `allocateQueueNumber` cấp số quầy từ bảng dãy theo khoa và ngày khám; wire của `queueNumber` không đổi.
- **Gateway:** Thêm route, rule và dòng contract cho các API `room-queue-ticket`, `clinic-queue`, `call`, `vital-signs`.
- **Billing:** Gửi exact-source/actor/reason khi hủy hoặc đổi; tách void chưa thu với hoàn đã thu.
- **Notification:** Thông báo đổi lịch đúng người và trạng thái.
- **Web, mobile:** Lịch ngày/tuần phân biệt hết suất, chưa mở và lỗi tải; hàng chờ tách đã đăng ký với được phép khám. Màn hình quầy điều dưỡng khoa theo từng phòng; phiếu và lịch bác sĩ ghi rõ "Số quầy" và "Số phòng khám"; bác sĩ xem sinh hiệu khi khám.
- **Nghiệm thu:** Hai quầy tranh suất cuối chỉ một bên thành công; gửi lại cùng key trả cùng phiếu; cùng key khác bệnh nhân bị từ chối. Mỗi khoa có dãy số quầy riêng và bắt đầu lại ở ngày khác; bật flag giữa ngày không trùng số; Billing và Notification vẫn nhận phiếu `CONFIRMED`. Hai phòng cùng khoa đánh số độc lập; người bệnh nhận số tiếp theo của phòng có bác sĩ được chỉ định; đổi sang bác sĩ ở phòng khác thì hủy số cũ và nhận số tiếp theo của phòng mới; hai điều dưỡng cấp số đồng thời không trùng; điều dưỡng khoa khác hoặc Bộ phận Tiếp nhận không cấp được; chưa có số phòng thì không bắt đầu khám; quay lại đọc kết quả không cấp số mới.

### Đợt 3 — Vòng đời lượt khám và nhiều lần khám trong ngày

**Mã:** CL-EXM-01, CL-EXM-02, CL-EXM-03, CL-EXM-04, CL-EXM-05, CL-INT-01, CL-INT-02, CL-INT-03, CL-INT-04, DAY-01, DAY-02, DAY-03, DAY-04, DAY-05, DAY-06, COL-03, COL-04, COL-05, IMP-FIN-01

- **Clinical:** Thêm loại lượt và liên kết `previous_record_id`/lý do quay lại; đường kết thúc có lý do cho từ chối chỉ định, bỏ về; đường hủy/hoãn chỉ định có lý do.
- **Billing:** Xác minh mode và issuer đang bật; khóa dedupe để không charge lại khi đổi lifecycle.
- **Web, mobile:** Quay lại đúng lượt đang mở không gọi lại start; cảnh báo khi bệnh nhân đã có lượt mở.
- **Nghiệm thu:** Sáng khám, chiều có kết quả vẫn cùng record; start lặp không tạo record thứ hai; về nhà quay lại tạo lượt mới có liên kết.

### Đợt 4 — Cấu trúc hồ sơ, quá trình điều trị và đính chính

**Mã:** REC-01, REC-03, REC-04, REC-05, REC-06, AMD-02, AMD-04, AMD-05, AMD-06, COL-01, COL-02, DOC-01, DOC-02, DOC-03, DOC-04, UX-01, UX-04, VIEW-03, MIG-02, IMP-ID-04, IMP-RX-01, IMP-RX-02, IMP-REP-01

- **Clinical:** Aggregate `TreatmentCourse` (lý do mở, vấn đề, người phụ trách, các lượt, quyết định kết thúc, cho phép song song; không tự đóng theo thời gian). `RecordAmendment`, `RecordHandover`, `ConsultationNote` là bảng riêng, không đụng nội dung gốc.
- **API:** `POST /api/v2/treatment-courses`, `GET /api/v2/patients/{patientId}/treatment-courses`, `POST /api/v2/treatment-courses/{id}/visits`, `PUT /api/v2/treatment-courses/{id}/closure`, `POST /api/v2/records/{id}/amendments`, `PUT /api/v2/records/{id}/amendments/{amendmentId}/decision`, `POST /api/v2/records/{id}/handovers`.
- **Core:** Inventory tiền sử, dị ứng, dấu hiệu sinh tồn và chọn một authority cho từng trường trước khi thêm cột.
- **Pharmacy, Report:** Giữ prescription-context theo `recordId`; quá trình điều trị không làm tăng số lượt khám.
- **Web, mobile:** Timeline có khoa, bác sĩ, loại lượt, nguồn tái khám; bốn trạng thái rỗng/đang chờ/lỗi/bị từ chối.
- **Nghiệm thu:** Đính chính không làm đổi bản gốc; `careEpisodeId` và khóa charge giữ nguyên trên mọi fixture cũ.

### Đợt 5 — Danh mục và template xét nghiệm

**Mã:** LAB-CAT-01, LAB-CAT-02, LAB-CAT-03, LAB-CAT-04, LAB-CAT-05, LAB-TPL-01, LAB-TPL-02, LAB-TPL-03, LAB-TPL-04, LAB-TPL-05, LAB-TPL-06, LAB-TPL-07, LAB-TPL-08, IMP-FIN-02

- **Lab:** Migration V6 tạo `lab_service_catalog`, `lab_panel_item`, `lab_result_template`, `lab_template_field`, `lab_reference_range`. Template có phiên bản; test mới ghi `template_id`, `template_version`.
- **Clinical:** Chỉ định chọn mã dịch vụ thay vì tên tự do.
- **Billing:** Mapping `serviceCode` sang `priceCode`; một panel là một charge dù có nhiều chỉ số.
- **Web:** Form sinh từ template; đơn vị và tham chiếu không nhập tay.
- **Nghiệm thu:** CBC thiếu chỉ số bắt buộc bị chặn; "< 0.5" hợp lệ với định lượng có operator; đổi template không đổi phiếu cũ.

### Đợt 6 — Nhận mẫu, duyệt, phát hành và trả kết quả

**Mã:** LAB-WF-01, LAB-WF-02, LAB-WF-03, LAB-WF-04, LAB-WF-05, LAB-WF-06, LAB-WF-07, LAB-INT-01, LAB-INT-02, LAB-INT-03, LAB-INT-04, IMP-LAB-01, IMP-FIN-03, IMP-NOT-01, IMP-REP-02, IMP-DOC-01, IMP-CON-02

Thứ tự bắt buộc: Clinical, Notification, Report hiểu `lab.result.amended` trước, sau đó mới bật flag `mediflow.v2.lab-amendment` ở Lab.

- **Lab:** `Specimen`; `LabReport` (`DRAFT`, `PENDING_REVIEW`, `RELEASED`) với `lab_report_revision`; cột `result_workflow_version` (0 là luồng cũ, 1 là duyệt). Bản phát hành bị khóa, đính chính tạo revision mới.
- **API:** `PUT /api/v2/lab/{id}/report/draft`, `POST .../submission`, `POST .../return`, `POST .../release`, `POST .../amendments`, `POST /api/v2/lab/{id}/specimens`, `PUT /api/v2/lab/specimens/{id}/decision`, `POST /api/v2/lab/{id}/critical-notices/{noticeId}/acknowledgement`.
- **Consumer:** Bỏ qua revision cũ đến muộn; dedupe theo `labId` + `resultVersion`; không thu thêm khi đổi `resultVersion`.
- **Web, mobile:** Nút Lưu nháp, Gửi duyệt, Phát hành; hiển thị đúng bản đã phát hành; bệnh nhân chỉ thấy bản đã phát hành.
- **Nghiệm thu:** Người nhập gọi release nhận 403; nháp không phát sự kiện; replay bản 1 sau bản 2 không ghi đè.

### Đợt 7 — Tổng kết client, chuyển dữ liệu và cutover

**Mã:** IMP-APP-01, IMP-APP-02, IMP-REL-01, IMP-REL-02

- Rà lại toàn bộ route web và màn hình mobile theo contract đã đổi.
- Backfill giữ UUID và text cũ, không phát sự kiện nghiệp vụ, không chuyển kết quả cũ về nháp.
- Runbook bật flag theo thứ tự, có phương án dừng, không xóa kết quả đã phát hành khi rollback.
- Chạy `docs/plans/06-acceptance-runbook.md` trên stack thật, kiểm client cũ với backend mới.
- Chuyển quy tắc lâu dài vào `docs/plans/*` và cập nhật `docs/known-limitations/README.md`.

## Flag theo đợt

| Flag (mặc định `false`) | Service | Đợt |
|---|---|---|
| `mediflow.v2.record-access-policy` | Clinical, Lab, Pharmacy | 1 |
| `mediflow.v2.clinic-intake` | Clinical | 2 |
| `mediflow.v2.treatment-course` | Clinical | 4 |
| `mediflow.v2.record-amendment` | Clinical | 4 |
| `mediflow.v2.lab-catalog` | Lab, Clinical | 5 |
| `mediflow.v2.lab-review` | Lab | 6 |
| `mediflow.v2.lab-amendment` | Lab | 6 |

## Review Focus

- Hai tab mở hai bệnh nhân trùng tên: mỗi tab chỉ ghi vào đúng record của nó (Đợt 1, `MedicalRecordDetail.test.tsx`).
- Authority service timeout khi đọc hoặc duyệt: phải từ chối, không cho qua (Đợt 1, `RecordReadPolicyTest`).
- Nhân viên bị thu hồi quyền duyệt khi báo cáo đang chờ duyệt: lần release sau phải bị từ chối (Đợt 6, `LabReportReleaseAuthorityTest`).
- Sự kiện đính chính đến trước sự kiện bản 1: không làm mất bản nào (Đợt 6, `LabResultRevisionOrderingTest`).
- Dữ liệu cũ có `value` dạng chữ và không có template: hiển thị nguyên văn với nhãn chưa gắn template (Đợt 7, `LegacyLabResultRenderingTest`).

Mỗi đợt sau Đợt 0 sẽ có file kế hoạch chi tiết riêng dạng `docs/superpowers/plans/YYYY-MM-DD-v2-wave-N-<tên>.md`, viết khi bắt đầu đợt đó vì phụ thuộc kết quả đối chiếu [AUDIT].

---

## Tác vụ chi tiết Đợt 0

### Task 0.1: Ghi baseline test

**Files:**
- Create: `docs/plans/21-v2-baseline-2026-11-10.md`

Không chạy web song song với backend: lần chạy song song sinh lỗi quá thời gian giả.

- [ ] **Step 1: Chạy test backend**

Run: `mvn -q test -pl backend/clinical-service,backend/lab-service,backend/gateway,backend/report-service,backend/notification-service,backend/billing-service,backend/pharmacy-service,backend/core-service -am`
Expected: ghi lại số test đạt và mọi test lỗi sẵn có; Docker đang tắt nên test Testcontainers phải được đánh dấu là chưa chạy.

- [ ] **Step 2: Chạy test web và mobile**

Run: `pnpm --dir frontend test` và `flutter test` trong `mobile/`.
Expected: ghi lại kết quả tương tự.

- [ ] **Step 3: Commit** với message `docs(v2): record test baseline`.

### Task 0.2: Khóa hành vi `lab.result.created` hiện tại ở bên nhận

**Files:**
- Create: `backend/clinical-service/src/test/java/com/mediflow/clinical/messaging/consumer/LabResultCreatedConsumerContractFixtureTest.java`

**Interfaces:**
- Consumes: `LabResultCreatedConsumer.validate(LabResultCreatedPayload)` (package-private).
- Produces: fixture mà Đợt 6 sẽ đổi kỳ vọng có chủ đích.

- [ ] **Step 1: Viết test**

```java
@Test void acceptsResultVersionOneForOutpatientVisit() {
    assertThatCode(() -> LabResultCreatedConsumer.validate(payload(1, 1, "OUTPATIENT_VISIT")))
        .doesNotThrowAnyException();
}
@Test void rejectsResultVersionTwoUntilAmendmentConsumerShips() {
    assertThatThrownBy(() -> LabResultCreatedConsumer.validate(payload(1, 2, "OUTPATIENT_VISIT")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("lab.result.created resultVersion must be 1");
}
```

- [ ] **Step 2: Chạy test**

Run: `mvn -q test -pl backend/clinical-service -am -Dtest=LabResultCreatedConsumerContractFixtureTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, vì test này ghi lại hành vi đang có.

- [ ] **Step 3: Commit** với message `test(clinical): pin lab result consumer contract`.

### Task 0.3: Bản đồ hợp đồng và quy tắc mã định danh

**Files:**
- Create: `docs/plans/22-v2-contract-map.md`
- Modify: `docs/plans/03-contracts.md`

- [ ] **Step 1: Lập bảng** mỗi DTO/event/endpoint sẽ đổi trong Đợt 1–6, gồm method/path hoặc routing key, producer, consumer, version, profile/flag và loại thay đổi (thêm field, thêm enum, đổi nghĩa).
- [ ] **Step 2: Thêm quy tắc IMP-ID-01 vào `03-contracts.md`:** `careEpisodeId` luôn là mã lượt khám; quá trình điều trị dài hạn dùng `treatmentCourseId`.
- [ ] **Step 3: Ghi ranh giới LAB-INT-05** vào cùng tài liệu: v2.0 là nhập tay có template, không kết nối máy, không tuyên bố FHIR.
- [ ] **Step 4: Commit** với message `docs(v2): map contracts and identifier rule`.

### Task 0.4: Khảo sát chất lượng dữ liệu chỉ đọc

**Files:**
- Create: `scripts/v2/profile-clinical-lab-data.sql`

- [ ] **Step 1: Viết truy vấn chỉ đọc** đếm: record không có `appointment_id`, record có `department_id` không khớp lượt, `lab_test` không có `source_order_id`, nhóm theo `care_contract_version`.
- [ ] **Step 2: Chạy trên DB demo khi Docker bật** và ghi số liệu vào `docs/plans/22-v2-contract-map.md`. Dòng thiếu dữ liệu được gắn nhãn cần rà soát, không tự suy đoán.
- [ ] **Step 3: Commit** với message `chore(v2): add read-only data profile`.

### Task 0.5: Ghi quyết định vào checklist

**Files:**
- Modify: `docs/known-limitations/03-clinical-service-improvement-checklist.md:6`, `:615`, `:740`
- Modify: `docs/known-limitations/README.md:17`

- [ ] **Step 1: Sửa ba đoạn ghi "chờ xác nhận"** thành: 24 mục phần 18–21 đã được đồng ý ngày 10/11/2026, kèm quyết định cấm tự duyệt và xây quá trình điều trị đầy đủ.
- [ ] **Step 2: Commit** với message `docs(v2): record checklist decisions`.
