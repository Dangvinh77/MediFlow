# MediFlow v2.0 Đợt 3 — Vòng đời lượt khám và nhiều lần khám trong ngày Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Một lượt khám có đường đi trọn vẹn: tiếp tục đúng lượt khi có kết quả, kết thúc có lý do mà không cần chẩn đoán giả, quay lại sau khi đã xong thì tạo lượt mới có liên kết, và không phát sinh phí khám lần hai.

**Architecture:** Clinical giữ toàn bộ thay đổi. `medical_record` thêm trạng thái kết thúc không kết luận và liên kết lượt trước; `outpatient_registration` thêm liên kết quay lại. Lab không đổi: hủy hoặc hoãn chỉ định đi qua API hủy có sẵn của Lab, Clinical chỉ đọc trạng thái. Billing không đổi hành vi, chỉ thêm test khóa việc không tính phí lần hai.

**Tech Stack:** Spring Boot, Flyway, PostgreSQL, Testcontainers, RabbitMQ outbox, Next.js 16, Flutter/Riverpod.

**Spec:** [`03-clinical-service-improvement-checklist.md`](../../known-limitations/03-clinical-service-improvement-checklist.md), [`02-business-flows.md`](../../plans/02-business-flows.md) mục 5 và 7, [lộ trình](2026-11-10-mediflow-v2-roadmap.md).

**Mã checklist:** CL-EXM-01..05, CL-INT-01..04, DAY-01..06, COL-03..05, IMP-FIN-01.

## Global Constraints

- Lộ trình không đặt flag cho Đợt 3. Mọi trường wire mới dùng `@JsonInclude(NON_NULL)`, vắng mặt khi chưa có dữ liệu. Trạng thái mới chỉ sinh ra từ endpoint mới.
- `/api/v2/*` mới phải có `@PreAuthorize`, rule ở `RouteAuthorizationFilter`, route ở `SchedulingRouteConfiguration` và dòng trong `route-role-contracts.csv`.
- Mọi lệnh ghi mới bắt buộc `Idempotency-Key` và đi qua `IdempotentCommandPort`.
- Không sửa trạng thái Lab từ Clinical. Không truy vấn chéo DB.
- Lượt đã kết thúc là bất biến: không mở lại, không gắn nội dung của lượt mới vào lượt cũ.
- Không copy chẩn đoán, đơn thuốc hay chỉ định cũ sang lượt mới.
- Migration mới của Clinical là `V17`; phải cập nhật số ghim trong `ConsumerInboxPostgresTest`.
- "Hôm nay" và ngày khám tính theo `Asia/Ho_Chi_Minh`.
- Web chỉ gọi qua `src/lib/api.ts`; mobile chỉ qua `core/network/api_client.dart`.
- Commit một tác giả người, không `Co-Authored-By`, không trailer AI. Không `git add` file trong `docs/documentation_docx`. Chạy test web riêng, không song song với Maven.

## Kết quả đối chiếu source

| Mã | Hiện trạng | Việc của đợt này |
|---|---|---|
| CL-EXM-01 | `startExam` khóa appointment, phát lại an toàn khi đã `IN_EXAM`, có unique index một record cho một appointment | Test tranh chấp thật |
| CL-EXM-02 | Web và mobile có đường mở hồ sơ, nhưng bác sĩ không có danh sách lượt đang mở qua nhiều ngày | Thêm danh sách |
| CL-EXM-03 | Web và mobile chỉ cho 3 hướng xử trí ngoại trú, backend chặn nội trú theo flag | Test giữ hành vi |
| CL-EXM-04 | `complete` bắt buộc có chẩn đoán, không có đường kết thúc nào khác | **Thiếu, làm mới** |
| CL-EXM-05 | `complete` đổi record, appointment, registration trong một giao dịch | Test rollback |
| CL-INT-01 | Web chỉ có liên kết sang Lab, hồ sơ không hiện trạng thái từng chỉ định | Thêm danh sách từ nguồn |
| CL-INT-02 | Guard chặn đúng, nhưng bác sĩ gặp lỗi mà không có nút hủy/hoãn ngay tại hồ sơ | **Thiếu đường xử lý** |
| CL-INT-03 | Lượt đặt lịch luôn cần clearance, cấm emergency override | Test giữ hành vi |
| CL-INT-04 | Hồ sơ chưa tách rõ "đã kê" với "đã cấp" | Thêm nhãn |
| DAY-01, 02 | `complete` và `update` không xét ngày, chỉ cần `IN_EXAM` | Test giữ hành vi |
| DAY-03 | `NEW_VISIT` không được mang liên kết; `FOLLOW_UP` buộc cùng khoa và tính giá tái khám | **Thiếu liên kết** |
| DAY-04, 05 | Chỉ chặn trùng trong cùng buổi, không ai biết bệnh nhân còn lượt mở | **Thiếu cảnh báo** |
| DAY-06 | Lịch sử hiện từng hồ sơ riêng, không gộp | Test giữ hành vi |
| COL-03, 04 | Kiểm nguồn đúng người, đổi bác sĩ cần lý do và đồng ý | Test nguồn sai người, lời hẹn quá hạn |
| COL-05 | Có panel lời hẹn, nhưng hồ sơ không hiện lượt này nối từ lượt nào | Thêm liên kết hai chiều |
| IMP-FIN-01 | Billing có đúng một đường ghi cho `appointment.status.changed`; lượt đặt lịch thu qua phiếu | Test và ghi cấu hình thực tế |

**Lỗi phát hiện khi đối chiếu:** `startExam` lấy `LocalDate.now(clock)` với đồng hồ UTC, nên ngày khám lệch một ngày khi bắt đầu trước 07:00 giờ Việt Nam.

## Review Focus

1. Bác sĩ bấm "Bắt đầu khám" ở hai thiết bị cùng lúc: một record duy nhất. Test `concurrentStartCreatesOneRecord`, Task 3.
2. Bệnh nhân bỏ về khi còn xét nghiệm đã thu tiền đang chờ: không đóng được cho tới khi chỉ định được hủy hoặc có kết quả. Test `closingIsBlockedByPendingLabOrder`, Task 2.
3. Kết quả về sau khi lượt đã đóng: không mở lại, không ghi đè. Test `resultAfterClosureDoesNotReopenRecord`, Task 2.
4. Bệnh nhân quay lại chọn nhầm hồ sơ của người khác: từ chối. Test `returnSourceOfAnotherPatientIsRefused`, Task 4.
5. Đổi trạng thái lượt không sinh thêm phí khám: Test `scheduledLifecycleIssuesNoExamCharge`, Task 6.

## Hợp đồng API mới

| Phương thức, đường dẫn | Vai trò | Thân yêu cầu | Trả về |
|---|---|---|---|
| `PUT /api/v2/records/{id}/close-without-conclusion` | DOCTOR | `{reason,note}` | 200 `MedicalRecordDTO` |
| `GET /api/v2/records/{id}/lab-orders` | ADMIN, DOCTOR, NURSE | | 200 `List<RecordLabOrderDTO>` |
| `GET /api/v2/patients/{patientId}/open-visits` | ADMIN, DOCTOR, NURSE | | 200 `List<OpenVisitDTO>` |
| `GET /api/v2/doctors/me/open-visits` | DOCTOR | | 200 `List<OpenVisitDTO>`, cũ nhất trước, tối đa 100 |

- `RecordLabOrderDTO(sourceOrderId, testId, labType, status, orderedAt)`. Lab không trả lời được thì 503 `LAB_LIFECYCLE_UNAVAILABLE`.
- `OpenVisitDTO(registrationId, recordId, doctorId, departmentId, sessionDate, startedAt)`. Chỉ gồm lượt `IN_EXAM` có record `OPEN`, không có nội dung chuyên môn.
- `reason` thuộc `RecordClosureReason`: `PATIENT_LEFT`, `PATIENT_REFUSED_EXAM`, `PATIENT_REFUSED_ORDER`, `OTHER`. `note` bắt buộc, 1–1000 ký tự, ghi cả dặn dò đã đưa.
- `RegistrationCreateRequest` thêm `returnSourceRecordId`, `returnReason`, `independentVisitReason`, tùy chọn.
- `MedicalRecordDTO` thêm `previousRecordId`, `closureReason`, `closureNote`, `closedAt`.
- `status` của record có thêm giá trị `CLOSED_WITHOUT_CONCLUSION`.

Mã lỗi mới: `RETURN_SOURCE_INVALID` (`BusinessRuleException`), `OPEN_VISIT_EXISTS` (`DuplicateResourceException`).

## Lựa chọn của người viết kế hoạch, cần bạn xem lại

1. **Bỏ về là trạng thái riêng `CLOSED_WITHOUT_CONCLUSION`, không dùng `COMPLETED`.** Hồ sơ không có chẩn đoán thì không được tính là đã kết luận. Appointment và phiếu vẫn là `COMPLETED` vì lượt đã kết thúc và đã dùng suất. Phí khám đã thu không tự hoàn.
2. **"Hoãn" chỉ định là hủy có lý do bên Lab, ghi "Hoãn sang lần khám sau: ...".** Lab không có trạng thái hoãn; thêm trạng thái sẽ đụng Billing và Report. Lần sau bác sĩ chỉ định mới. Tiền đã thu đi theo luồng hủy có sẵn.
3. **Đã có lượt mở cùng khoa thì phải nhập lý do mới đăng ký thêm.** Khác khoa chỉ cảnh báo. Người dùng đã chốt giữ quy tắc này ngày 10/11/2026. Đây là biện pháp chống nhầm của dự án, không phải quy định của Việt Nam: Thông tư 39/2024/TT-BYT Điều 4b và Quyết định 1313/QĐ-BYT không đặt rào chắn tại quầy. Mục đích là tránh đăng ký và thu phí khám lần hai khi bệnh nhân chỉ cần quay lại buồng khám đang mở.
4. **Quay lại không có lời hẹn dùng `NEW_VISIT` kèm liên kết**, được phép khác khoa, tính giá khám mới. Nhân viên muốn tính giá tái khám thì chọn `FOLLOW_UP` như hiện nay. **Giới hạn đã biết:** Thông tư 39/2024/TT-BYT Điều 4b khoản 3, 4 tính 30% giá một lần khám từ lần thứ hai trong cùng lần đến hoặc khi quay lại trong ngày, tổng tối đa 2 lần giá. MediFlow dùng BHYT mô phỏng với tỷ lệ tự quy định nên Đợt 3 không áp dụng quy tắc này.
5. **Lượt đóng không kết luận không được làm nguồn của `FOLLOW_UP`,** chỉ làm nguồn liên kết quay lại, vì chưa có chẩn đoán để tái đánh giá.
6. **Không thêm flag,** theo lộ trình. Quy tắc lý do ở mục 3 có hiệu lực ngay khi triển khai.
7. **Giữ nguyên:** bác sĩ bất kỳ vẫn hủy được chỉ định qua API Lab có sẵn. Việc siết quyền này thuộc Đợt 6, khi Lab được làm lại quy trình.

---

### Task 1: Migration và domain

**Files:**
- Create: `backend/clinical-service/src/main/resources/db/migration/V17__exam_lifecycle.sql`, `.../domain/model/RecordClosureReason.java`
- Modify: `MedicalRecord.java`, `MedicalRecordStatus.java`, `MedicalRecordPersistenceAdapter.java`, `OutpatientRegistration.java`, `OutpatientRegistrationPersistenceAdapter.java`, DTO và mapper của record, `ConsumerInboxPostgresTest.java`
- Test: `MedicalRecordClosureTest.java`, `ExamLifecycleSchemaPostgresTest.java`

**Interfaces:**
- Produces: `MedicalRecordStatus.CLOSED_WITHOUT_CONCLUSION`; `record MedicalRecord.Closure(RecordClosureReason reason, String note, UUID closedByStaffId, Instant closedAt)`; `void MedicalRecord.closeWithoutConclusion(RecordClosureReason, String note, UUID staffId, Instant at)`; `MedicalRecord.openForAppointment(..., UUID previousRecordId)`; `OutpatientRegistration.ReturnLink(UUID sourceRecordId, String returnReason, String independentVisitReason)`.

- [ ] **Step 1: Viết test đỏ** `closeRecordsReasonNoteActorAndTime`, `closeNeedsNoDiagnosis`, `closedRecordIsImmutable`, `closeRejectsBlankNote`, `closeRejectsNoteOver1000`; schema `closedRecordRequiresAllClosureFields`, `closedRecordCannotCarryDisposition`, `existingStatusesStillValid`.
- [ ] **Step 2: Chạy, xác nhận đỏ.** `mvn -q -pl backend/clinical-service -am test -Dtest='MedicalRecordClosureTest,ExamLifecycleSchemaPostgresTest' -Dsurefire.failIfNoSpecifiedTests=false`. Expected: lỗi biên dịch vì thiếu kiểu mới.
- [ ] **Step 3: `V17`.** Thay `ck_medical_record_status` bằng ba giá trị. Thêm `previous_record_id` (FK `medical_record`), `closure_reason`, `closure_note`, `closed_by_staff_id`, `closed_at`. Ràng buộc: `CLOSED_WITHOUT_CONCLUSION` khi và chỉ khi đủ cả bốn cột đóng, `disposition` và `completed_at` là NULL. Thêm vào `outpatient_registration`: `return_source_record_id`, `return_reason`, `independent_visit_reason`.
- [ ] **Step 4: Domain, adapter, DTO.** `restore` nhận thêm `Closure` và `previousRecordId` qua overload mới; `requireOpen` chặn mọi trạng thái khác `OPEN`. Nâng ghim `ConsumerInboxPostgresTest` lên 17.
- [ ] **Step 5: Chạy lại, xác nhận xanh.** Lệnh như Step 2.
- [ ] **Step 6: Commit** `feat(clinical): add exam closure and visit link fields`.

### Task 2: Kết thúc lượt không kết luận

**Files:**
- Modify: `ClinicalCareFinanceApplicationService.java`, `OutpatientRegistrationApplicationService.java`, `RecordAccessApplicationService.java`, persistence đọc kết quả của bệnh nhân
- Create: `web/ExamClosureController.java`, `application/dto/request/CloseRecordRequest.java`
- Test: `ExamClosureTest.java`, `ExamClosureControllerTest.java`, `ExamClosureMigrationPostgresTest.java`

**Interfaces:**
- Consumes: `MedicalRecord.closeWithoutConclusion` từ Task 1.
- Produces: `Result<MedicalRecordDTO> closeWithoutConclusion(UUID recordId, CloseRecordRequest request, String key)`.

- [ ] **Step 1: Viết test đỏ** `assignedDoctorClosesRecordWithoutDiagnosis`, `otherDoctorIsRefused`, `adminIsRefused`, `closingMovesAppointmentAndRegistrationToCompleted`, `closingCompletesRoomTicket`, `closingIsBlockedByPendingLabOrder` (mã `CLINICAL_LAB_ORDER_PENDING`), `closingIsAllowedOnceOrdersAreCancelled`, `closingPublishesStatusChangeButNoRecordCompleted`, `resultAfterClosureDoesNotReopenRecord`, `retrySameKeyReturnsStoredResult`, `closedRecordIsNotWritable` (`writeBlockedReason`), `closedRecordIsHiddenFromPatientResults`, `onlyContractTwoCanClose`, và 401/403 ở controller test.
- [ ] **Step 2: Chạy, xác nhận đỏ.**
- [ ] **Step 3: Cài đặt.** Dùng lại khóa, `labCompletionGuard`, `completeExamination`, `completeScheduled`, `appendStatusChanged` của `complete`. Không append `MEDICAL_RECORD_COMPLETED`. Khai báo route, rule và dòng csv.
- [ ] **Step 4: Chạy lại, xác nhận xanh;** chạy `RouteRoleContractTest`.
- [ ] **Step 5: Commit** `feat(clinical): close a visit without a conclusion`.

### Task 3: Tiếp tục đúng lượt, nhiều ngày, nhiều thiết bị

**Files:**
- Modify: `ClinicalCareFinanceApplicationService.java`
- Test: `ExamLifecycleRegressionTest.java`, `ExamStartConcurrencyPostgresTest.java`

- [ ] **Step 1: Viết test đỏ cho lỗi ngày** `examinationDateIsClinicDate`: đồng hồ `2026-11-10T23:30:00Z` cho `examinationDate` là `2026-11-11`.
- [ ] **Step 2: Chạy, xác nhận đỏ. Sửa** bằng `LocalDate.ofInstant(now, ZoneId.of("Asia/Ho_Chi_Minh"))`. Chạy lại, xanh.
- [ ] **Step 3: Thêm test giữ hành vi** `concurrentStartCreatesOneRecord`, `repeatStartReturnsSameRecord`, `nextDayContinuesSameRecord`, `nextDayCompletionKeepsOriginalDate`, `shiftChangeCreatesNoNewRecord`, `failedCompletionLeavesAllThreeUnchanged`, `completedRecordCannotBeStartedAgain`, `admissionDispositionRefusedWhenDeferred`, `scheduledStartNeedsClearance`, `scheduledStartRefusesEmergencyOverride`. Test nào đỏ thì đó là lỗi thật: sửa code, ghi ruling.
- [ ] **Step 4: Commit** `fix(clinical): use the clinic date for examinations`.

### Task 4: Quay lại có liên kết và lượt đang mở

**Files:**
- Modify: `RegistrationCreateRequest.java`, `OutpatientRegistrationApplicationService.java`, `ClinicalCareFinanceApplicationService.java`, `OutpatientRegistrationRepositoryPort.java` và adapter
- Create: `application/service/OpenVisitApplicationService.java`, `web/OpenVisitController.java`, `application/dto/response/OpenVisitDTO.java`
- Test: `RegistrationReturnLinkTest.java`, `OpenVisitApplicationServiceTest.java`, `OpenVisitPostgresTest.java`, `OpenVisitControllerTest.java`

**Interfaces:**
- Consumes: `ReturnLink`, `openForAppointment(..., previousRecordId)` từ Task 1.
- Produces: `List<OpenVisitDTO> forPatient(UUID)`, `List<OpenVisitDTO> forCurrentDoctor()`; port `findOpenVisitsByPatient(UUID)`, `findOpenVisitsByDoctor(UUID, int limit)`.

- [ ] **Step 1: Viết test đỏ** `returnLinkStoredOnNewVisit`, `returnSourceOfAnotherPatientIsRefused`, `openRecordIsNotAReturnSource`, `closedRecordIsAReturnSource`, `returnReasonRequiredWithSource`, `returnSourceMayBeAnotherDepartment`, `closedRecordIsNotAFollowUpSource`, `sameDepartmentOpenVisitNeedsReason` (`OPEN_VISIT_EXISTS`), `otherDepartmentOpenVisitDoesNotBlock`, `reasonAllowsIndependentVisit`, `startCopiesReturnSourceToRecord`, `startCopiesFollowUpSourceToRecord`, `newRecordCopiesNoDiagnosis`, `followUpSourceOfAnotherPatientIsRefused`, `overdueRecommendationStillRegisters`, `doctorListsOwnOpenVisitsOldestFirst`, `patientListHasNoClinicalContent`.
- [ ] **Step 2: Chạy, xác nhận đỏ.**
- [ ] **Step 3: Cài đặt.** Kiểm tra lượt mở nằm trong `idempotency.execute`, sau khi khóa session. Constructor cũ của request được giữ. Khai báo route, rule, csv.
- [ ] **Step 4: Chạy lại, xác nhận xanh.**
- [ ] **Step 5: Commit** `feat(clinical): link return visits and list open visits`.

### Task 5: Chỉ định Lab của hồ sơ

**Files:**
- Create: `application/service/RecordLabOrderApplicationService.java`, `application/dto/response/RecordLabOrderDTO.java`, `web/RecordLabOrderController.java`
- Test: `RecordLabOrderApplicationServiceTest.java`, `RecordLabOrderControllerTest.java`

- [ ] **Step 1: Viết test đỏ** `listsLiveStatusForEachOrder`, `labOutageIsUnavailableNotEmpty`, `orderBoundToAnotherRecordIsRejected`, `readFollowsRecordAccessPolicy`.
- [ ] **Step 2: Chạy, xác nhận đỏ.**
- [ ] **Step 3: Cài đặt** bằng `ClinicalLabOrderRepositoryPort` và `LabOrderLifecyclePort`, kiểm tra binding như `ClinicalLabCompletionGuard`, quyền đọc qua `RecordReadGuard`. Khai báo route, rule, csv.
- [ ] **Step 4: Chạy lại, xác nhận xanh.**
- [ ] **Step 5: Commit** `feat(clinical): show lab order status on the record`.

### Task 6: Khóa tính phí

**Files:**
- Test: `backend/billing-service/src/test/java/com/mediflow/billing/ExamLifecycleChargeTest.java`

- [ ] **Step 1: Viết test** `scheduledLifecycleIssuesNoExamCharge` (`READY_FOR_EXAM→IN_EXAM`, `IN_EXAM→COMPLETED`, sự kiện giao lại), `medicalRecordCreatedIsInformationalWithV1Issuer`, `returnVisitIsChargedOnceAsItsOwnSource`. Test nào đỏ là lỗi thật: sửa, ghi ruling.
- [ ] **Step 2: Ghi cấu hình thực tế** của `MEDIFLOW_BILLING_EXAM_CHARGE_CONSUMER_ENABLED` và các compose vào tài liệu nghiệm thu ở Task 10.
- [ ] **Step 3: Commit** `test(billing): lock one exam charge across visit lifecycle`.

### Task 7: Web — hồ sơ khám

**Files:**
- Create: `frontend/src/features/medical-record/components/CloseRecordForm.tsx`, `RecordLabOrdersPanel.tsx`, các file test tương ứng
- Modify: `MedicalRecordDetail.tsx`, `CompleteRecordForm.tsx`, `features/medical-record/api.ts`, `types.ts`

- [ ] **Step 1: Viết test đỏ** `closes with reason and note`, `close needs a note`, `close offers no diagnosis field`, `shows each order status from source`, `outage shows unavailable not empty`, `cancel needs a reason`, `postpone prefixes the reason`, `pending error points to the orders panel`, `closed record shows reason and no write actions`, `shows link to previous record`, `offers no admission or transfer`, `prescribed is not shown as dispensed`.
- [ ] **Step 2: Chạy, xác nhận đỏ.** `cd frontend && npx vitest run src/features/medical-record`.
- [ ] **Step 3: Cài đặt.** Hủy và hoãn gọi `labApi.cancel` với lý do. Bổ sung nhãn trạng thái mới ở mọi `Record<...>` có `status`.
- [ ] **Step 4: Chạy lại, xác nhận xanh;** typecheck.
- [ ] **Step 5: Commit** `feat(web): close visits and manage lab orders on the record`.

### Task 8: Web — tiếp nhận và danh sách bác sĩ

**Files:**
- Create: `frontend/src/features/appointment/components/OpenVisitNotice.tsx`, `DoctorOpenVisitsPanel.tsx`, các file test tương ứng
- Modify: `PatientRegistrationWorkspace.tsx` và form đăng ký, `AppointmentWorkspace`, `OutpatientExamActions.tsx`, `features/appointment/api.ts`, `types.ts`

- [ ] **Step 1: Viết test đỏ** `warns about an open visit`, `same department asks for a reason`, `sends independentVisitReason`, `return visit needs source and reason`, `doctor list opens the same record`, `in exam offers continue and never start`, `open visit outage does not block registration`.
- [ ] **Step 2: Chạy, xác nhận đỏ. Step 3: Cài đặt. Step 4: Chạy lại, xanh.**
- [ ] **Step 5: Commit** `feat(web): warn about open visits and link return visits`.

### Task 9: Mobile

**Files:**
- Create: `mobile/lib/features/medical_record/presentation/close_record_sheet.dart`, `record_lab_orders_section.dart`, `mobile/lib/features/appointment/presentation/open_visit_notice.dart`
- Modify: `medical_record_page.dart`, domain và gateway của `medical_record`, `reception_page.dart`, `appointment_page.dart`, repository của `appointment`
- Test: `mobile/test/features/medical_record/exam_lifecycle_test.dart`, `mobile/test/features/appointment/open_visit_test.dart`

- [ ] **Step 1: Viết test đỏ** cùng các hành vi của Task 7 và 8, trừ phần không có trên mobile.
- [ ] **Step 2: Chạy, xác nhận đỏ.** `flutter test test/features/medical_record/exam_lifecycle_test.dart test/features/appointment/open_visit_test.dart`.
- [ ] **Step 3: Cài đặt** theo `presentation → domain ← data`, khóa idempotency qua cơ chế như `ClinicCommandKeys`.
- [ ] **Step 4: Chạy lại, xanh;** `flutter analyze` không thêm vấn đề mới.
- [ ] **Step 5: Commit** `feat(mobile): close visits, lab orders and open visit notices`.

### Task 10: Nghiệm thu

**Files:**
- Create: `docs/plans/25-v2-wave-3-acceptance.md`

- [ ] **Step 1: Chạy toàn bộ test** backend (`-Dmaven.test.failure.ignore=true`), web, typecheck, lint, mobile.
- [ ] **Step 2: Nghiệm thu qua Gateway trên DB sạch.** Sáng khám, chiều có kết quả vẫn cùng record; sang ngày sau vẫn cùng lượt; start lặp không tạo record thứ hai; hoàn tất rồi quay lại tạo lượt mới có liên kết; bỏ về đóng được khi không có chỉ định treo, bị chặn khi có, đóng được sau khi hủy; đăng ký cùng khoa khi còn lượt mở cần lý do; khác khoa thì không; Billing chỉ có một EXAM charge cho mỗi lượt.
- [ ] **Step 3: Ghi kết quả thật và giới hạn của bằng chứng.**
- [ ] **Step 4: Commit** `docs(v2): record wave 3 acceptance`.
