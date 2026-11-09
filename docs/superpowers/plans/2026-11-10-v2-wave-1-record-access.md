# MediFlow v2.0 Đợt 1 — Quyền đọc, quyền ghi và lưu vết Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mọi lần đọc hồ sơ và tài liệu liên quan đều có căn cứ được backend kiểm tra và lưu vết, quyền ghi giữ nguyên theo bác sĩ được phân công, và web, mobile thể hiện đúng quyền đó.

**Architecture:** `RecordReadPolicy` ở Clinical là nơi duy nhất quyết định quyền đọc hồ sơ. Lab và Pharmacy hỏi Clinical qua một API nội bộ tối thiểu và từ chối khi không hỏi được. Toàn bộ hành vi mới nằm sau flag `mediflow.v2.record-access-policy`, mặc định tắt.

**Tech Stack:** Spring Boot, Flyway, OpenFeign, Next.js 16, Flutter/Riverpod.

**Spec:** [`03-clinical-service-improvement-checklist.md`](../../known-limitations/03-clinical-service-improvement-checklist.md), [`02-medical-record-read-access.md`](../../known-limitations/02-medical-record-read-access.md), [roadmap v2.0](2026-11-10-mediflow-v2-roadmap.md)

**Mã checklist:** REC-02, ACL-01, ACL-02, ACL-03, ACL-04, ACL-05, ACL-06, AMD-01, AMD-03, VIEW-01, VIEW-02, UX-02, IMP-ID-02, IMP-ID-03, IMP-ID-06

## Global Constraints

- Flag tắt thì hành vi và wire của các API hiện có không đổi.
- Quyền lấy từ JWT đã xác minh và authority Core hiện hành. Không tin khoa hoặc vai trò do client gửi (ACL-06).
- Core không trả lời được thì từ chối bằng lỗi 503 hiện có `SCHEDULING_AUTHORITY_UNAVAILABLE`, không cho qua.
- Không ghi token hoặc nội dung lâm sàng vào `record_access_log`.
- Quyền đọc không tạo quyền ghi.
- Khai báo mục đích không thay cho sự đồng ý của cơ sở khi khai thác hồ sơ đã chuyển lưu trữ (Luật 15/2023, Điều 69 khoản 4). Đợt 1 chưa có trạng thái lưu trữ, `COMPLETED` không có nghĩa đã lưu trữ.
- Commit không có trailer AI. Chạy test web riêng, không song song với Maven.

## Quy tắc quyền đọc

Áp dụng khi flag bật, cho `GET /api/v1/records/{id}` và `GET /api/v1/records/patient/{patientId}`.

| Vai trò | Được đọc khi | `basis` |
|---|---|---|
| DOCTOR, hoặc ADMIN có `staffId` là bác sĩ trên lượt | Là tác giả của record đang mở (chỉ `GET /{id}`) | `AUTHOR` |
| DOCTOR, hoặc ADMIN có `staffId` là bác sĩ trên lượt | Có appointment của bệnh nhân với `doctorId` = mình, còn hiệu lực | `CARE_ASSIGNMENT` |
| DOCTOR | Khoa hiện hành của mình có appointment của bệnh nhân còn hiệu lực | `DEPARTMENT_ENCOUNTER` |
| NURSE | Khoa hiện hành của mình có appointment của bệnh nhân còn hiệu lực; chỉ thấy record của khoa đó | `DEPARTMENT_ENCOUNTER` |
| DOCTOR, ADMIN | Có khai báo còn hạn cho bệnh nhân | `DECLARED_PURPOSE` |
| NURSE | Có khai báo `EMERGENCY` còn hạn; thấy mọi khoa | `DECLARED_PURPOSE` |

- Appointment còn hiệu lực: trạng thái `PENDING`, `ARRIVED`, `AWAITING_PAYMENT`, `READY_FOR_EXAM`, `IN_EXAM`, hoặc `COMPLETED` với `completedAt` không quá 30 ngày trước thời điểm đọc. `CANCELLED` không phải căn cứ.
- Người đọc có căn cứ thấy toàn bộ nội dung. Đợt 1 không có bản tóm tắt.
- Tiếp nhận không đi qua chính sách này. NURSE kiêm tiếp nhận (`02-business-flows.md`, mục 4.1) vẫn tìm người bệnh, đăng ký khám mới và tái khám, xác nhận đến và cấp số cho mọi khoa như hiện nay, vì các việc đó chỉ dùng định danh, lịch và lời hẹn tái khám. `followUpSources` giữ nguyên, Task 6 không áp lên API này.
- Khác khoa không phải lý do từ chối với DOCTOR và ADMIN.
- Khai báo là câu hỏi lý do, không phải chờ duyệt. Sau khi khai là đọc được ngay và mọi lần đọc vẫn được ghi nhật ký.
- Hiệu lực khai báo, theo từng người và từng bệnh nhân: DOCTOR 96 giờ, ADMIN và NURSE 60 phút.
- Mục đích: DOCTOR dùng `CONSULTATION`, `CARE_CONTINUITY`, `QUALITY_REVIEW`, `EMERGENCY`. ADMIN dùng `RECORD_MANAGEMENT`, `QUALITY_REVIEW`. NURSE chỉ dùng `EMERGENCY`.
- `reason` bắt buộc với `QUALITY_REVIEW` và `RECORD_MANAGEMENT`, 10 đến 500 ký tự. Các mục đích còn lại chỉ cần chọn, `reason` tùy chọn.
- Chưa có căn cứ: DOCTOR, ADMIN, NURSE nhận 403 `RECORD_ACCESS_PURPOSE_REQUIRED`; với NURSE, web chỉ đưa ra lựa chọn cấp cứu.
- Tài khoản, nhân viên hoặc khoa không còn hoạt động, hoặc vai trò khác với Core: 403 `RECORD_ACCESS_DENIED`, khai báo cũng không mở được.

## Lựa chọn của người viết kế hoạch, cần bạn xem lại

1. **Bác sĩ có liên hệ điều trị trong 30 ngày không phải khai báo**, kể cả qua khoa của mình. Ngoài khoảng đó mới cần chọn mục đích. Mốc 30 ngày lấy từ cấu hình Epic của Salem Health.
2. **ADMIN vẫn đọc được mọi khoa theo yêu cầu ngày 09/11**, nhưng phải khai lý do trước. Nếu ADMIN cũng là bác sĩ trên lượt khám thì được đọc như bác sĩ, vì guard ghi hiện cho phép trường hợp này.
3. **Điều dưỡng đọc đầy đủ record của khoa mình khi có liên hệ điều trị**, không dùng bản tóm tắt, và chỉ khai báo được trong cấp cứu. Không gom mọi điều dưỡng vào một khoa, vì lượt khám luôn thuộc khoa chuyên môn nên quy tắc theo khoa sẽ mất tác dụng. Người chỉ làm quầy thuộc "Bộ phận Tiếp nhận", loại `ADMINISTRATIVE`; bộ phận này không có lượt khám nên không tự có quyền đọc nội dung hồ sơ. Bộ phận là nơi công tác, không phải quyền: tài khoản ở đó vẫn là role `NURSE`, xử lý cả `NEW_VISIT` và `FOLLOW_UP`. Không thêm role mới và không bỏ role `NURSE`, giữ quyết định ngày 27/10.
4. **Khai báo dùng API riêng thay vì tham số trên GET**, để lý do không xuất hiện trên URL.
5. **Giao diện cấp quyền duyệt Lab làm ở Đợt 6**, cùng lúc có người dùng quyền đó. Đợt 1 chỉ có API và test.

## Căn cứ đối chiếu thực tế (10/11/2026)

Đây là tham khảo để thiết kế, không phải chứng nhận tuân thủ. Chưa đọc được bản đầy đủ quy chế phân quyền của bệnh viện Việt Nam nào, và TT 13/2025 mới được đối chiếu qua tài liệu thứ cấp về phần phân quyền.

- [Luật 15/2023, Điều 69](https://hethongphapluat.com/luat-kham-benh-chua-benh-2023/dieu-69): người hành nghề và người trực tiếp điều trị trong cơ sở được đọc hồ sơ đang điều trị; hồ sơ đã lưu trữ cần sự đồng ý của cơ sở. [Điều 26](https://hethongphapluat.com/luat-kham-benh-chua-benh-2023/dieu-26) xếp điều dưỡng vào chức danh hành nghề.
- [TT 31/2021](https://caselaw.vn/van-ban-phap-luat/401831-thong-tu-so-31-2021-tt-byt-ngay-28-12-2021-cua-bo-truong-bo-y-te-quy-dinh-ve-hoat-dong-dieu-duong-trong-benh-vien): điều dưỡng thực hiện chỉ định, theo dõi, đánh giá và bàn giao, cả với người bệnh ngoại trú.
- [Salem Health (Epic)](https://salemhealth.org/common-ground-home/common-ground-post/epic-s-break-the-glass-function): hỏi lý do chỉ với hồ sơ mật hoặc nhân viên; miễn khi có lượt khám hoặc khoa có lượt trong 30 ngày.
- [Texas Children's (Epic)](https://texaschildrensnews.org/compliance-and-privacy-spotlight-break-the-glass-epic/): khai lý do một lần, đọc lại được trong 4 ngày; phòng tuân thủ rà soát từng lần.
- [NHS Summary Care Record](https://nhsd-proxy.openprescribing.net/services/summary-care-records-scr/information-governance-for-scr): tự khai quan hệ điều trị hoặc truy cập khẩn sinh cảnh báo cho privacy officer.
- [AMA](https://ama-assn.org/system/files/regulatory-regulatory-myths-break-glass-ehr.pdf): áp dụng hỏi lý do cho mọi hồ sơ gây gánh nặng cho người điều trị.
- [AHIMA](https://journal.ahima.org/Portals/0/archives/AHIMA%20files/Managing%20Audit%20Trails.pdf): nhật ký truy cập thuộc trách nhiệm quản lý hồ sơ.
- [Psychiatric Times](https://www.psychiatrictimes.com/view/beginners-guide-security-within-ehr): quầy tiếp đón thường chỉ thấy định danh và lịch, không thấy dữ liệu lâm sàng. Đây là thông lệ, chưa tìm được quy định bắt buộc.

## Review Focus

- Core timeout khi đọc: phải trả 503, không trả dữ liệu (`RecordReadPolicyTest.coreTimeoutFailsClosed`).
- Khai báo hết hạn đúng tại thời điểm đọc: bị từ chối (`RecordReadPolicyTest.declarationExpiringExactlyNowIsRejected`).
- Không ghi được nhật ký: không trả dữ liệu (`MedicalRecordReadAccessTest.logFailureFailsClosed`).
- Lần từ chối vẫn phải còn nhật ký dù transaction đọc rollback (`RecordAccessLogPersistenceTest.deniedEntrySurvivesCallerRollback`).
- Hai bệnh nhân trùng tên, phản hồi cũ về muộn sau khi đổi record: không hiển thị dữ liệu cũ (`MedicalRecordDetail.test.tsx`, "drops a late response after the record changes").

---

### Task 1: Lưu trữ nhật ký truy cập và khai báo mục đích

**Files:**
- Create: `backend/clinical-service/src/main/resources/db/migration/V13__record_access_policy.sql`
- Create: `backend/clinical-service/src/main/java/com/mediflow/clinical/application/port/out/RecordAccessLogPort.java`
- Create: `backend/clinical-service/src/main/java/com/mediflow/clinical/application/port/out/RecordAccessDeclarationPort.java`
- Create: `backend/clinical-service/src/main/java/com/mediflow/clinical/infrastructure/persistence/adapter/RecordAccessPersistenceAdapter.java`
- Test: `backend/clinical-service/src/test/java/com/mediflow/clinical/infrastructure/persistence/adapter/RecordAccessLogPersistenceTest.java`

**Interfaces:**
- Produces:
  - `record RecordAccessLogEntry(UUID actorAccountId, UUID actorStaffId, String actorRole, UUID patientId, UUID recordId, String action, String outcome, String basis, String purpose, String correlationId, Instant occurredAt)`
  - `RecordAccessLogPort.append(RecordAccessLogEntry entry)`, chạy `REQUIRES_NEW`
  - `RecordAccessDeclarationPort.findActive(UUID accountId, UUID patientId, Instant at): Optional<RecordAccessDeclaration>`
  - `RecordAccessDeclarationPort.save(RecordAccessDeclaration declaration)`
  - `record RecordAccessDeclaration(UUID declarationId, UUID actorAccountId, UUID actorStaffId, String actorRole, UUID patientId, String purpose, String reason, Instant declaredAt, Instant expiresAt)`

- [ ] **Step 1: Viết test**

`deniedEntrySurvivesCallerRollback`: gọi `append` trong transaction rồi đánh dấu rollback; sau đó đếm được 1 dòng. `findActiveExcludesExpiryInstant`: khai báo có `expiresAt = T`, `findActive(.., T)` trả rỗng và `findActive(.., T.minusSeconds(1))` có giá trị. Dùng Testcontainers như các test Postgres hiện có của Clinical.

- [ ] **Step 2: Chạy test để thấy lỗi**

Run: `mvn -q test -pl backend/clinical-service -am -Dtest=RecordAccessLogPersistenceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL vì chưa có các lớp.

- [ ] **Step 3: Tạo migration và adapter**

Bảng `record_access_log` với các cột theo `RecordAccessLogEntry`, `action IN ('READ','LIST','DECLARE')`, `outcome IN ('ALLOWED','DENIED')`, chỉ mục `(patient_id, occurred_at)`. Bảng `record_access_declaration` với `reason VARCHAR(500)` cho phép null, chỉ mục `(actor_account_id, patient_id, expires_at)`. Không có foreign key sang `medical_record` để giữ được dòng từ chối.

- [ ] **Step 4: Chạy lại test**, Expected: PASS.
- [ ] **Step 5: Commit** `feat(clinical): store record access log and declarations`.

### Task 2: `RecordReadPolicy`

**Files:**
- Create: `backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/RecordReadPolicy.java`
- Create: `backend/clinical-service/src/main/java/com/mediflow/clinical/application/dto/authority/RecordReadDecision.java`
- Test: `backend/clinical-service/src/test/java/com/mediflow/clinical/application/service/RecordReadPolicyTest.java`

**Interfaces:**
- Consumes: `RecordAccessDeclarationPort.findActive`, `AppointmentRepositoryPort.findByPatient`, `SchedulingAuthorityPort.authority`, `SchedulingAuthorityPort.currentDoctorAuthority`, `StaffLookupPort.departmentOf`.
- Produces:
  - `record RecordReadDecision(boolean allowed, String basis, String denialCode, UUID nurseDepartmentId)`
  - `RecordReadPolicy.forRecord(ActorIdentity actor, MedicalRecord record, Instant at, String correlationId): RecordReadDecision`
  - `RecordReadPolicy.forPatient(ActorIdentity actor, UUID patientId, UUID departmentHint, Instant at, String correlationId): RecordReadDecision`

- [ ] **Step 1: Viết test theo bảng quy tắc, dùng fixture ở mục 5 của tài liệu 02**

Dữ liệu: bệnh nhân P, R1 do B và R2 do C ở tim mạch, R3 do E ở tiêu hóa.

```java
@Test void doctorWithActiveAppointmentReadsOtherDepartmentRecord() { /* C có appointment PENDING với P; R3 → allowed, CARE_ASSIGNMENT */ }
@Test void sameDepartmentDoctorReadsThroughDepartmentEncounter() { /* D cùng khoa, khoa có appointment IN_EXAM với P → DEPARTMENT_ENCOUNTER */ }
@Test void adminAssignedAsDoctorReadsWithoutDeclaration() { /* ADMIN có staffId trên appointment → CARE_ASSIGNMENT */ }
@Test void doctorWithoutBasisNeedsPurpose() { /* denialCode = RECORD_ACCESS_PURPOSE_REQUIRED */ }
@Test void authorReadsOwnRecordWithoutAssignment() { /* forRecord → AUTHOR */ }
@Test void authorStillNeedsBasisForPatientList() { /* forPatient → RECORD_ACCESS_PURPOSE_REQUIRED */ }
@Test void appointmentCompletedExactlyThirtyDaysAgoStillCounts() { /* completedAt = at - 30 ngày → CARE_ASSIGNMENT */ }
@Test void appointmentCompletedBeyondThirtyDaysNeedsPurpose() { /* completedAt = at - 30 ngày - 1 giây → RECORD_ACCESS_PURPOSE_REQUIRED */ }
@Test void cancelledAppointmentIsNoBasis() { /* RECORD_ACCESS_PURPOSE_REQUIRED */ }
@Test void declaredDoctorReads() { /* DECLARED_PURPOSE */ }
@Test void declarationExpiringExactlyNowIsRejected() { /* findActive rỗng tại T → denied */ }
@Test void adminWithoutDeclarationIsDenied() { /* RECORD_ACCESS_PURPOSE_REQUIRED */ }
@Test void adminWithDeclarationReadsAllDepartments() { /* R1, R3 allowed */ }
@Test void nurseWithDepartmentAppointmentReadsOwnDepartment() { /* DEPARTMENT_ENCOUNTER, nurseDepartmentId = khoa của điều dưỡng */ }
@Test void nurseCannotReadOtherDepartmentWithoutEmergency() { /* R3 → RECORD_ACCESS_PURPOSE_REQUIRED */ }
@Test void nurseEmergencyDeclarationReadsOtherDepartment() { /* R3 → DECLARED_PURPOSE */ }
@Test void nurseTransferredOutLosesAccessDespiteOldAppointment() { /* khoa hiện hành từ Core khác khoa của appointment → RECORD_ACCESS_PURPOSE_REQUIRED */ }
@Test void inactiveStaffIsDeniedDespiteRoleClaim() { /* staffActive=false → RECORD_ACCESS_DENIED */ }
@Test void roleMismatchWithCoreIsDenied() { /* JWT DOCTOR, Core NURSE → RECORD_ACCESS_DENIED */ }
@Test void coreTimeoutFailsClosed() { /* UpstreamUnavailableException được ném ra */ }
```

- [ ] **Step 2: Chạy test**, `-Dtest=RecordReadPolicyTest`. Expected: FAIL vì chưa có lớp.
- [ ] **Step 3: Cài đặt** đúng theo bảng quy tắc. DOCTOR kiểm authority bằng `currentDoctorAuthority` và lấy khoa hiện hành từ snapshot đó. NURSE lấy khoa từ `staff.departmentOf(actor.staffId())` rồi kiểm bằng `authority`. ADMIN kiểm bằng `authority` với khoa của appointment nếu căn cứ là phân công, nếu không thì `record.getDepartmentId()` hoặc `departmentHint`. Thứ tự xét căn cứ theo đúng bảng quy tắc, căn cứ đầu tiên thỏa mãn được ghi vào nhật ký.
- [ ] **Step 4: Chạy test**, Expected: PASS.
- [ ] **Step 5: Commit** `feat(clinical): add record read policy`.

### Task 3: Áp chính sách vào hai API đọc hồ sơ

**Files:**
- Modify: `backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/MedicalRecordApplicationService.java:49-73,134-145`
- Modify: `backend/clinical-service/src/main/resources/application.yml`
- Test: `backend/clinical-service/src/test/java/com/mediflow/clinical/application/service/MedicalRecordReadAccessTest.java`

**Interfaces:**
- Consumes: `RecordReadPolicy`, `RecordAccessLogPort`.
- Produces: record cấu hình `RecordAccessPolicyMode(boolean enabled)`, đọc từ `mediflow.v2.record-access-policy` (env `MEDIFLOW_V2_RECORD_ACCESS_POLICY`, mặc định `false`).

- [ ] **Step 1: Viết test**

`flagOffKeepsLegacyBehaviour` (không gọi policy, không ghi log). `allowedReadWritesOneLogRow` (action `READ`, outcome `ALLOWED`, đúng basis). `deniedReadWritesLogThenThrows403`. `logFailureFailsClosed` (port ném lỗi thì không trả DTO). `listWritesOneLogRowWithNullRecordId`. `nurseListHidesOtherDepartments` (chỉ record đúng khoa, nội dung đầy đủ). `adminEmptyPatientReturnsEmptyAfterDeclarationCheck`. Thêm vào `MedicalRecordControllerTest`: `patientRoleCannotUseStaffRecordEndpoints` (403 ở cả hai GET). Thêm `ReceptionAcrossDepartmentsTest` cùng thư mục: `nurseRegistersAndChecksInForAnotherDepartmentWithFlagOn` (không gọi `RecordReadPolicy`), `receptionUnitNurseHandlesNewAndFollowUpVisits` (nhân viên thuộc khoa `ADMINISTRATIVE`, `followUpSources` vẫn trả lời hẹn) và `sameNurseStillNeedsPurposeToReadThatDepartmentsRecord` (403 `RECORD_ACCESS_PURPOSE_REQUIRED`).

- [ ] **Step 2: Chạy test**, Expected: FAIL.
- [ ] **Step 3: Cài đặt** trong `getById` và `byPatient`, thêm nhánh flag. Với `byPatient` truyền `departmentHint` là khoa của record đầu tiên. ADMIN xem bệnh nhân chưa có record thì chỉ kiểm khai báo và trả danh sách rỗng, không gọi Core vì không có khoa để kiểm và không có dữ liệu bị lộ. Từ chối dùng `ForbiddenOperationException` với `denialCode`.
- [ ] **Step 4: Chạy test** cùng `MedicalRecordControllerTest`, Expected: PASS.
- [ ] **Step 5: Commit** `feat(clinical): enforce record read policy behind flag`.

### Task 4: API khai báo mục đích, capability và route Gateway

**Files:**
- Create: `backend/clinical-service/src/main/java/com/mediflow/clinical/web/RecordAccessController.java`
- Create: `backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/RecordAccessApplicationService.java`
- Modify: `backend/gateway/src/main/java/com/mediflow/gateway/config/SchedulingRouteConfiguration.java:19-27`
- Modify: `backend/gateway/src/main/java/com/mediflow/gateway/filter/RouteAuthorizationFilter.java:133-142`
- Modify: `backend/gateway/src/test/resources/route-role-contracts.csv`
- Modify: `docs/plans/22-v2-contract-map.md`
- Test: `backend/clinical-service/src/test/java/com/mediflow/clinical/web/RecordAccessControllerTest.java`, `backend/clinical-service/src/test/java/com/mediflow/clinical/application/service/RecordCapabilitiesTest.java`

**Interfaces:**
- Produces wire:
  - `POST /api/v2/records/access-declarations`, roles ADMIN, DOCTOR, NURSE. Body `{patientId, purpose, reason}`, kiểm mục đích và `reason` theo mục Quy tắc quyền đọc. Trả 201 `{declarationId, patientId, purpose, expiresAt}`.
  - `GET /api/v2/records/{id}/capabilities`, roles ADMIN, DOCTOR, NURSE. Trả `RecordCapabilitiesDTO`.
  - `record RecordCapabilitiesDTO(UUID recordId, boolean canRead, String readBasis, boolean canWriteContent, boolean canComplete, boolean canRequestAmendment, String writeBlockedReason)`. Giá trị `writeBlockedReason`: `RECORD_COMPLETED`, `NOT_ASSIGNED_DOCTOR`, `NOT_IN_EXAM`, hoặc null.

- [ ] **Step 1: Viết test**

`nurseMayDeclareEmergencyOnly` (mục đích khác 400 `INVALID_REQUEST`). `adminCannotUseConsultationPurpose` (400 `INVALID_REQUEST`). `reasonRequiredForQualityReviewAndRecordManagement`. `doctorEmergencyNeedsNoReason`. `doctorDeclarationLastsNinetySixHours`. `adminAndNurseDeclarationsLastSixtyMinutes`. `assignedDoctorInExamCanWrite`. `otherDoctorSameDepartmentCannotWrite` (`NOT_ASSIGNED_DOCTOR`). `completedRecordCannotBeWrittenByAuthor` (`RECORD_COMPLETED`). `flagOffReportsLegacyRole` (`readBasis = LEGACY_ROLE`, `canRead = true`). `canRequestAmendment` luôn `false` ở Đợt 1.

- [ ] **Step 2: Chạy test**, Expected: FAIL.
- [ ] **Step 3: Cài đặt** controller, service, hai route Clinical, hai rule Gateway và hai dòng CSV. Thêm dòng `POST /api/v2/records/access-declarations` vào bản đồ hợp đồng.
- [ ] **Step 4: Chạy** `-Dtest='RecordAccess*,RecordCapabilitiesTest'` ở Clinical và `mvn -q test -pl backend/gateway -am -Dtest=RouteRoleContractTest -Dsurefire.failIfNoSpecifiedTests=false`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(clinical): add access declarations and record capabilities`.

### Task 5: Giữ guard ghi và tính bất biến

**Files:**
- Modify: `backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/MedicalRecordApplicationService.java:179-197`
- Test: `backend/clinical-service/src/test/java/com/mediflow/clinical/application/service/MedicalRecordWriteGuardTest.java`

- [ ] **Step 1: Viết test**

`sameDepartmentDoctorCannotUpdate`. `otherDepartmentDoctorCannotUpdate`. `previousTreatingDoctorCannotUpdateNewVisit`. `authorCannotUpdateCompletedRecord` (`CLINICAL_INVALID_STATUS_TRANSITION`). `declaredPurposeGrantsNoWrite`. `nonMvpModeWithFlagRequiresRecordAuthor` (đóng khoảng trống ở dòng 180). `recordKeepsOriginalDoctorAfterUpdate`. `secondRecordForAppointmentIsRejected` (`RECORD_DUPLICATE_APPOINTMENT`).

- [ ] **Step 2: Chạy test.** Expected: chỉ `nonMvpModeWithFlagRequiresRecordAuthor` FAIL; các test còn lại khóa hành vi đang đúng.
- [ ] **Step 3: Thêm nhánh** khi không ở chế độ MVP mà flag bật: yêu cầu `staffAuthority.requireAssignedDoctor(record.getDoctorId(), record.getDepartmentId())`.
- [ ] **Step 4: Chạy test**, Expected: PASS.
- [ ] **Step 5: Commit** `fix(clinical): require record author outside MVP mode`.

### Task 6: Thống nhất quyền đọc giấy hẹn

**Files:**
- Modify: `backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/FollowUpRecommendationApplicationService.java:106-114,181-193`
- Test: `backend/clinical-service/src/test/java/com/mediflow/clinical/application/service/FollowUpReadAccessTest.java`

- [ ] **Step 1: Viết test**

`flagOffKeepsAuthorOnlyRule`. `receivingDoctorWithAssignmentReadsSourceAdvice`. `doctorWithoutBasisGets403NotNotFound` (phải là `RECORD_ACCESS_PURPOSE_REQUIRED`). `missingAdviceStaysNotFoundForAllowedReader` (`RESOURCE_NOT_FOUND`). `patientSelfRuleUnchanged`.

- [ ] **Step 2: Chạy test**, Expected: FAIL.
- [ ] **Step 3: Khi flag bật**, nhánh DOCTOR, ADMIN, NURSE dùng `RecordReadPolicy.forRecord` và ghi log; nhánh PATIENT giữ nguyên.
- [ ] **Step 4: Chạy test**, Expected: PASS.
- [ ] **Step 5: Commit** `feat(clinical): align follow-up read access with record policy`.

### Task 7: API xác minh quyền đọc cho Lab và Pharmacy

**Files:**
- Create: `backend/clinical-service/src/main/java/com/mediflow/clinical/web/RecordReadDecisionController.java`
- Test: `backend/clinical-service/src/test/java/com/mediflow/clinical/web/RecordReadDecisionControllerTest.java`

**Interfaces:**
- Produces wire: `GET /api/v2/clinical/patients/{patientId}/read-decision?actorAccountId=&actorStaffId=&actorRole=`. Chỉ cho `ROLE_SYSTEM_SERVICE` với principal `lab-service` hoặc `pharmacy-service`. Trả `{patientId, allowed, basis, denialCode, observedAt}`. Không publish ở Gateway.

- [ ] **Step 1: Viết test**

`humanTokenIsRejected`. `otherServicePrincipalIsRejected`. `returnsDecisionWithoutClinicalContent` (body chỉ có đúng 5 field trên). `actorIdentityComesFromQueryNotFromServiceToken`.

- [ ] **Step 2: Chạy test**, Expected: FAIL.
- [ ] **Step 3: Cài đặt**, dùng `RecordReadPolicy.forPatient` và ghi log action `READ` với `recordId` null.
- [ ] **Step 4: Chạy test**, Expected: PASS.
- [ ] **Step 5: Commit** `feat(clinical): expose minimal read decision to services`.

### Task 8: Lab kiểm quyền đọc

**Files:**
- Create: `backend/lab-service/src/main/java/com/mediflow/lab/application/service/LabReadAccessService.java`
- Create: `backend/lab-service/src/main/java/com/mediflow/lab/application/port/out/ClinicalReadDecisionPort.java`
- Create: `backend/lab-service/src/main/java/com/mediflow/lab/infrastructure/client/LabClinicalReadDecisionAdapter.java`
- Modify: `backend/lab-service/src/main/java/com/mediflow/lab/web/LabController.java:44-78`
- Test: `backend/lab-service/src/test/java/com/mediflow/lab/application/service/LabReadAccessServiceTest.java`

**Interfaces:**
- Consumes: wire ở Task 7.
- Produces: `ClinicalReadDecisionPort.decide(UUID patientId, ReaderIdentity reader, String correlationId)`, `record ReaderIdentity(UUID accountId, UUID staffId, String role)`.

`LabReadAccessService` không có `@Transactional`, vì adapter Clinical của Lab từ chối chạy trong transaction.

- [ ] **Step 1: Viết test**

`labTechKeepsWorkQueueAccess` (không gọi Clinical). `doctorWithoutBasisDeniedOnGetById`. `doctorDeniedOnByPatient`. `episodeSearchChecksPatientOfResults`. `unfilteredSearchForDoctorIsForcedToOwnDepartment` (bỏ qua `departmentId` khác do client gửi). `clinicalTimeoutFailsClosed` (503 hiện có của Lab). `flagOffKeepsLegacyBehaviour`.

- [ ] **Step 2: Chạy test**, `mvn -q test -pl backend/lab-service -am -Dtest=LabReadAccessServiceTest -Dsurefire.failIfNoSpecifiedTests=false`. Expected: FAIL.
- [ ] **Step 3: Cài đặt** và chuyển ba handler GET sang service mới. Lấy `ReaderIdentity` từ JWT đã xác minh ở `JwtAuthFilter`.
- [ ] **Step 4: Chạy test** cùng `LabControllerTest` nếu có, Expected: PASS.
- [ ] **Step 5: Commit** `feat(lab): check clinical read decision for result reads`.

### Task 9: Pharmacy kiểm quyền đọc

**Files:**
- Create: `backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/application/service/PrescriptionReadAccessService.java`
- Create: `backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/application/port/out/ClinicalReadDecisionPort.java`
- Create: `backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/infrastructure/client/PharmacyClinicalReadDecisionAdapter.java`
- Modify: `backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/web/PrescriptionController.java:130-140`
- Modify: `backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/web/PrescriptionQueryController.java:28-36`
- Test: `backend/pharmacy-service/src/test/java/com/mediflow/pharmacy/application/service/PrescriptionReadAccessServiceTest.java`

- [ ] **Step 1: Viết test**

`pharmacistKeepsWorkAccess` (không gọi Clinical). `doctorDeniedOnGetById`. `searchWithPatientChecksDecision`. `doctorSearchWithoutPatientIsLimitedToOwnPrescriptions`. `clinicalTimeoutFailsClosed`. `flagOffKeepsLegacyBehaviour`.

- [ ] **Step 2: Chạy test**, Expected: FAIL.
- [ ] **Step 3: Cài đặt**, thêm tham số `doctorId` tùy chọn vào `SearchPrescriptionsUseCase` cho nhánh không có `patientId`.
- [ ] **Step 4: Chạy test**, Expected: PASS.
- [ ] **Step 5: Commit** `feat(pharmacy): check clinical read decision for prescription reads`.

### Task 10: Quyền duyệt Lab do Organization cấp

**Files:**
- Create: `backend/core-service/src/main/resources/db/organization-migration/V21__staff_capability_grant.sql`
- Create: `backend/core-service/src/main/java/com/mediflow/organization/application/service/StaffCapabilityGrantService.java`
- Create: `backend/core-service/src/main/java/com/mediflow/organization/web/controller/StaffCapabilityGrantController.java`
- Modify: `backend/core-service/src/main/java/com/mediflow/organization/application/dto/response/OwnCapabilities.java`
- Modify: `backend/gateway/src/main/java/com/mediflow/gateway/filter/RouteAuthorizationFilter.java`
- Modify: `backend/gateway/src/test/resources/route-role-contracts.csv`
- Test: `backend/core-service/src/test/java/com/mediflow/organization/application/StaffCapabilityGrantServiceTest.java`

**Interfaces:**
- Produces wire:
  - `POST /api/v2/org/staff/{staffId}/capability-grants`, ADMIN. Body `{capability, reason}`, chỉ nhận `LAB_RESULT_RELEASE`.
  - `PUT /api/v2/org/capability-grants/{grantId}/revocation`, ADMIN. Body `{reason}`.
  - `GET /api/v2/org/lab-release-authority?actorAccountId=`, chỉ `ROLE_SYSTEM_SERVICE` principal `lab-service`. Trả `{actorAccountId, accountActive, staffId, staffActive, canReleaseLabResults, observedAt}`.
  - `OwnCapabilities` thêm `boolean canReleaseLabResults` ở cuối, mặc định `false`.

- [ ] **Step 1: Viết test**

`grantsOnlyToActiveLabTech` (nhân viên không phải LAB_TECH bị từ chối). `adminAccountItselfGetsNoReleaseAuthority`. `duplicateActiveGrantIsRejected`. `revokedGrantStopsAuthorityImmediately`. `inactiveStaffHasNoAuthorityDespiteGrant`. `grantAndRevocationRecordActorAndReason`.

- [ ] **Step 2: Chạy test**, Expected: FAIL.
- [ ] **Step 3: Cài đặt** bảng `staff_capability_grant` với chỉ mục duy nhất một phần trên `(staff_id, capability) WHERE revoked_at IS NULL`, service, controller, rule Gateway.
- [ ] **Step 4: Chạy test** cùng `OrganizationDtoContractTest` và `RouteRoleContractTest`, Expected: PASS.
- [ ] **Step 5: Commit** `feat(organization): add lab result release capability grants`.

### Task 11: Web

**Files:**
- Modify: `frontend/src/features/medical-record/api.ts`
- Modify: `frontend/src/features/medical-record/types.ts`
- Modify: `frontend/src/features/medical-record/components/MedicalRecordDetail.tsx:74-372`
- Modify: `frontend/src/features/medical-record/components/MedicalRecordTable.tsx`
- Modify: `frontend/src/features/medical-record/components/FollowUpRecommendationPanel.tsx`
- Modify: `frontend/src/features/medical-record/components/ExaminationRecordLink.tsx:21-25`
- Create: `frontend/src/features/medical-record/components/RecordAccessPurposeForm.tsx`
- Test: `frontend/src/features/medical-record/components/MedicalRecordDetail.test.tsx`, `frontend/src/features/medical-record/components/RecordAccessPurposeForm.test.tsx`, `frontend/src/features/medical-record/components/MedicalRecordTable.test.tsx`

**Interfaces:**
- Consumes: wire ở Task 4.
- Produces: `medicalRecordApi.capabilities(recordId)`, `medicalRecordApi.declareAccess(body)`.

- [ ] **Step 1: Viết test**

`hides write controls when capability denies writing`. `shows the responsible doctor and read-only notice for another doctor's record`. `separates read-only history from the open-visit write area` (hai vùng có `aria-label` riêng là "Nội dung đã ghi" và "Ghi nhận lượt đang mở"; vùng đầu hiện bác sĩ phụ trách từ `doctorId` và thời điểm `updatedAt`). `shows full identity banner with patient, visit date, department and doctor` (dùng `PatientContextBanner`). `drops a late response after the record changes`. `shows purpose form on RECORD_ACCESS_PURPOSE_REQUIRED and reloads after declaring`. `shows denied state on RECORD_ACCESS_DENIED without offering purpose form`. `nurse purpose form offers emergency only`. Trong `ExaminationRecordLink.test.tsx`: `shows no record link and no retry when reading is not permitted`, vì trang lịch hẹn gọi `byPatient` và điều dưỡng ở quầy sẽ nhận 403 với khoa khác. `requires reason text only for quality review and record management`. `labels completed record as examination completed, not signed or archived`. `does not show access denial as no follow-up advice`.

- [ ] **Step 2: Chạy test**, `pnpm vitest run src/features/medical-record` trong `frontend/`. Expected: FAIL.
- [ ] **Step 3: Cài đặt**

Thay `canMutate` theo role bằng capability. Không tải được capability thì ẩn nút ghi. Banner dùng `PatientContextBanner`. Nhãn trạng thái là "Đã hoàn tất khám", kèm ghi chú "Hoàn tất khám không đồng nghĩa đã ký xác nhận hoặc chuyển lưu trữ." Nguyên nhân từ chối đọc theo `error.code`.

- [ ] **Step 4: Chạy** `pnpm vitest run src/features/medical-record`, `pnpm typecheck` và `pnpm lint`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(web): show record capabilities and access declarations`.

### Task 12: Mobile

**Files:**
- Modify: `mobile/lib/features/medical_record/domain/medical_record.dart`
- Modify: `mobile/lib/features/medical_record/data/gateway_medical_record_repository.dart`
- Modify: `mobile/lib/features/medical_record/presentation/medical_record_page.dart:725-737,851-878`
- Test: `mobile/test/medical_record_access_widget_test.dart`, `mobile/test/medical_record_capabilities_repository_test.dart`

**Interfaces:**
- Produces: `MedicalRecordRepository.capabilities(String recordId): Future<RecordCapabilities>`, `MedicalRecordRepository.declareAccess({patientId, purpose, reason})`.

- [ ] **Step 1: Viết test**

`hides edit actions when capability denies writing`. `treats unknown capability payload as read only`. `shows purpose form for RECORD_ACCESS_PURPOSE_REQUIRED`. `shows denied state for RECORD_ACCESS_DENIED`. `shows completed examination wording`. Giữ kiểm tra hiển thị ở 320px và cỡ chữ 2 như các widget test hiện có.

- [ ] **Step 2: Chạy test**, `flutter test test/medical_record_access_widget_test.dart test/medical_record_capabilities_repository_test.dart`. Expected: FAIL.
- [ ] **Step 3: Cài đặt** qua `api_client`, thay `canEditMedicalRecord` bằng capability ở trang chi tiết.
- [ ] **Step 4: Chạy** `flutter test` và `flutter analyze`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(mobile): show record capabilities and access declarations`.

### Task 13: Bật cho demo và nghiệm thu

**Files:**
- Modify: `docker-compose.yml`
- Modify: `backend/core-service/src/main/resources/demo-seed.json`
- Modify: `docs/plans/03-contracts.md`
- Create: `docs/plans/23-v2-wave-1-acceptance.md`

- [ ] **Step 1: Đặt `MEDIFLOW_V2_RECORD_ACCESS_POLICY=true`** cho Clinical, Lab, Pharmacy trong compose. Thêm vào demo seed `department-15` "Bộ phận Tiếp nhận Demo" loại `ADMINISTRATIVE`, cùng một staff `NURSE` và một account `NURSE` thuộc bộ phận này. Giữ nguyên 9 điều dưỡng hiện có.
- [ ] **Step 2: Chạy toàn bộ** test các module đã sửa, rồi `pnpm test` riêng, rồi `flutter test`. Expected: chỉ còn các lỗi đã ghi ở baseline.
- [ ] **Step 3: Dựng stack và chạy các kịch bản ở mục 5 tài liệu 02 qua Gateway**, ghi kết quả từng kịch bản, gồm truy vấn `record_access_log`.
- [ ] **Step 4: Chuyển quy tắc đọc và mã lỗi** vào `03-contracts.md`.
- [ ] **Step 5: Commit** `docs(v2): record wave 1 acceptance`.
