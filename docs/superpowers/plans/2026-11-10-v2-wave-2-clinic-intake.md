# MediFlow v2.0 Đợt 2 — Lịch, đăng ký và hàng chờ Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lịch khám có một chính sách duy nhất, phiếu bị ảnh hưởng được xử lý có vết, và người bệnh đi qua hai lớp số rõ ràng: số quầy theo khoa và ngày, rồi số phòng khám do điều dưỡng khoa cấp cùng sinh hiệu trước khi bác sĩ khám.

**Architecture:** Clinical giữ toàn bộ trạng thái lịch, phiếu, số phòng và sinh hiệu, sau flag `mediflow.v2.clinic-intake`. Số được cấp bằng bộ đếm trong Postgres với upsert nguyên tử, sau khi đã khóa phiếu, nên không đốt số. Đổi lịch giữ nguyên `registrationId` và báo sang Billing, Notification bằng một trường mới chỉ xuất hiện khi có đổi lịch.

**Tech Stack:** Spring Boot, Flyway, PostgreSQL, Testcontainers, RabbitMQ outbox, Next.js 16, Flutter/Riverpod.

**Spec:** [`03-clinical-service-improvement-checklist.md`](../../known-limitations/03-clinical-service-improvement-checklist.md), [`02-business-flows.md`](../../plans/02-business-flows.md) mục 3.2 và 4.2, [`22-v2-contract-map.md`](../../plans/22-v2-contract-map.md), [lộ trình v2.0](2026-11-10-mediflow-v2-roadmap.md).

**Mã checklist:** CL-SCH-01..06, CL-REG-01..06, CL-QUE-01..05, IMP-ID-05, IMP-FIN-04, IMP-NOT-02. Bổ sung: CL-INK-01, CL-INK-02, CL-INK-03.

## Global Constraints

- Flag `mediflow.v2.clinic-intake` (env `MEDIFLOW_V2_CLINIC_INTAKE`), mặc định `false`. Flag tắt thì hành vi và wire của API hiện có không đổi. Riêng khóa chống chồng lịch ở Task 3 áp dụng cả khi tắt vì không đổi hợp đồng.
- API mới chỉ tồn tại khi cả `mediflow.clinical.scheduling.enabled` và `mediflow.v2.clinic-intake` bật: `@ConditionalOnExpression("${mediflow.clinical.scheduling.enabled:false} and ${mediflow.v2.clinic-intake:false}")`.
- Trường JSON mới (`roomQueueNumber`, `reschedule`) dùng `@JsonInclude(NON_NULL)`, vắng mặt khi chưa có dữ liệu. `queueNumber` giữ nguyên tên, kiểu và ý nghĩa trên wire và trong sự kiện `outpatient.registration.changed`.
- Ngày khám của mọi số lấy từ `session_date`, không lấy từ đồng hồ máy chủ. "Hôm nay" tính theo `Asia/Ho_Chi_Minh`.
- Khoa của người thao tác lấy từ `SchedulingAuthorityPort.currentDoctorAuthority` (Core trả khoa thật, dùng được cho NURSE). `authority(actor, departmentId)` chỉ vọng lại khoa được hỏi nên **không** dùng để xác định khoa của điều dưỡng.
- Core không trả lời được thì từ chối bằng lỗi 503 hiện có `SCHEDULING_AUTHORITY_UNAVAILABLE`.
- Mọi lệnh ghi mới bắt buộc `Idempotency-Key` và đi qua `IdempotentCommandPort`.
- Điều dưỡng không kê đơn, không phát thuốc. Hệ thống không tự gắn cờ bất thường cho sinh hiệu.
- `@PreAuthorize` trên mọi endpoint; mỗi endpoint có rule ở `RouteAuthorizationFilter`, route ở `SchedulingRouteConfiguration` và dòng trong `route-role-contracts.csv`.
- Không truy vấn chéo DB. Không tuyên bố tuân thủ pháp lý.
- Commit một tác giả người, không `Co-Authored-By`, không trailer AI. Không `git add` file trong `docs/documentation_docx`. Chạy test web riêng, không song song với Maven.

## Review Focus

1. Hai máy điều dưỡng cấp số cho cùng phiếu cùng lúc, khác key: một số duy nhất, không đốt số. Test `concurrentIssueWithDifferentKeysBurnsNoNumber`, Task 5.
2. Cấp số lúc 23:30 UTC, tức đã sang ngày mới ở Việt Nam: đúng ngày khám của buổi. Test `queueDateComesFromSessionNotServerClock`, Task 5.
3. Đổi lịch khi giao dịch thu phí chưa rõ kết quả: từ chối, không dịch suất. Test `rescheduleDuringPaymentLockIsRefused`, Task 9.
4. RabbitMQ giao lại sự kiện đổi lịch: Billing chỉ có một dòng đối soát. Test `redeliveredRescheduleCreatesOneSettlement`, Task 10.
5. Core mất kết nối đúng lúc cấp số: từ chối, không cấp. Test `coreOutageDeniesTicket`, Task 5.

## Hợp đồng API mới

| Phương thức, đường dẫn | Vai trò | Thân yêu cầu | Trả về |
|---|---|---|---|
| `POST /api/v2/outpatient-registrations/{id}/room-queue-ticket` | NURSE | trống | 201 `ClinicQueueTicketDTO` |
| `GET /api/v2/clinic-queue?date=&roomId=` | NURSE, DOCTOR | | 200 `List<ClinicQueueEntryDTO>` |
| `PUT /api/v2/clinic-queue-tickets/{id}/call` | NURSE, DOCTOR | `{expectedRevision}` | 200 `ClinicQueueTicketDTO` |
| `POST /api/v2/clinic-queue-tickets/{id}/adjustments` | NURSE, DOCTOR | `{expectedRevision,type,reason}` | 201 `ClinicQueueTicketDTO` |
| `POST /api/v2/outpatient-registrations/{id}/vital-signs` | NURSE | `VitalSignCreateRequest` | 201 `VitalSignObservationDTO` |
| `GET /api/v2/outpatient-registrations/{id}/vital-signs` | NURSE, DOCTOR | | 200 `List<VitalSignObservationDTO>` |
| `POST /api/v2/outpatient-registrations/{id}/reschedule` | ADMIN, NURSE | `{expectedRevision,targetSessionId,reason,patientConfirmed}` | 200 `OutpatientRegistrationDTO` |
| `GET /api/v2/consultation-sessions/{id}/impact` | ADMIN, DOCTOR | | 200 `ScheduleImpactDTO` |
| `GET /api/v2/doctor-work-shifts/{id}/impact` | ADMIN, DOCTOR | | 200 `ScheduleImpactDTO` |
| `GET /api/v2/billing/registration-settlements?kind=&page=&size=` | ADMIN, CASHIER | | 200 phân trang `RegistrationSettlementDTO` |

Mã lỗi mới: `CLINIC_DESK_SCOPE_DENIED` (`ForbiddenOperationException`); `ROOM_TICKET_EXISTS`, `TICKET_STATE_CONFLICT`, `SHIFT_HAS_LINKED_SESSIONS`, `SHIFT_CANCELLED`, `REGISTRATION_PAYMENT_IN_PROGRESS` (`DuplicateResourceException`); `ROOM_TICKET_NOT_ELIGIBLE`, `ROOM_TICKET_WRONG_DATE`, `CLINICAL_ROOM_TICKET_REQUIRED`, `SESSION_DATE_PAST`, `SESSION_SHIFT_CONFLICT`, `VITAL_SIGNS_INVALID`, `VITAL_SIGNS_REASON_REQUIRED`, `RESCHEDULE_CONFIRMATION_REQUIRED`, `RESCHEDULE_TARGET_INVALID` (`BusinessRuleException`).

## Lựa chọn của người viết kế hoạch, cần bạn xem lại

1. **Đổi lịch giữ nguyên phiếu và tiền đã thu, chỉ trong cùng khoa.** Giá khám không đổi vì cùng khoa, clearance vẫn gắn đúng phiếu đó nên không phải "chuyển sang chỉ định mới". Muốn đổi khoa thì hủy phiếu và đăng ký lại, tiền đã thu đi theo đường hoàn.
2. **Cùng ngày giữ số quầy và xác nhận đến; khác ngày cấp số quầy mới và phải xác nhận đến lại.** Có số phòng cũ thì số đó bị hủy kèm lý do, điều dưỡng khoa cấp số tiếp theo của phòng mới.
3. **Hủy ca chuyển các buổi đang mở thành `STOPPED`, không `CANCELLED`.** Theo mục 3.2: ngừng nhận mới, phiếu cũ vẫn còn trách nhiệm và được liệt kê để xử lý.
4. **Tạo buổi độc lập vẫn được phép**, nhưng dùng chung chính sách với tạo từ ca và bị từ chối nếu chồng lên ca đang hiệu lực của bác sĩ ở phòng khác.
5. **"Không đến" là trạng thái của số phòng (`ABSENT`), không phải trạng thái phiếu**, theo CL-REG-05 (không thêm enum live từ nhãn đề xuất). Hệ thống không tự trả suất đã thu; muốn trả thì hủy phiếu có lý do.
6. **Phần kết quả nguy cấp của IMP-NOT-02 chuyển sang Đợt 6**, cùng Lab và IMP-NOT-01, vì đến đó mới có dữ liệu nguy cấp. Đợt 2 làm đổi lịch, đổi bác sĩ và lời hẹn.
7. **Billing ghi nghĩa vụ hoàn và đưa lên danh sách "Cần hoàn tiền"; thu ngân hoàn bằng màn hình hoàn tiền hiện có, không tự động hoàn.** Khảo sát cho thấy hiện nay phiếu đã thu bị hủy không có bằng chứng hủy mà `LedgerRefundService` đòi, nên không hoàn được; Task 10 sửa chỗ này.
8. **Giới hạn nhập sinh hiệu là giới hạn chống gõ nhầm**, không phải ngưỡng lâm sàng: mạch 20–250, tâm thu 50–300, tâm trương 20–200 và nhỏ hơn tâm thu, nhiệt độ 30.0–45.0, nhịp thở 5–80, SpO2 50–100, cân nặng 0.5–400.
9. **ADMIN không cấp số phòng, không xem màn hình quầy điều dưỡng.** ADMIN vẫn xem danh sách phiếu của buổi như hiện nay.
10. **"Nghỉ" trong CL-SCH-03 là giờ nghỉ của buổi.** Hệ thống chưa có dữ liệu nghỉ phép, Đợt 2 không thêm.

---

### Task 1: Hợp đồng, migration và flag

**Files:**
- Create: `backend/clinical-service/src/main/resources/db/migration/V14__clinic_intake.sql`
- Create: `backend/clinical-service/src/main/java/com/mediflow/clinical/infrastructure/config/ClinicIntakeSettings.java`
- Modify: `backend/clinical-service/src/main/resources/application.yml`, `docs/plans/22-v2-contract-map.md`, `docs/plans/03-contracts.md`
- Test: `backend/clinical-service/src/test/java/com/mediflow/clinical/infrastructure/persistence/ClinicIntakeSchemaPostgresTest.java`, `.../infrastructure/config/ClinicIntakeSettingsTest.java`

**Interfaces:**
- Produces: bean `ClinicIntakeSettings` với `boolean enabled()`, đọc `mediflow.v2.clinic-intake`, mặc định `false`, luôn có mặt trong context.

Cột bảng:
- `clinic_queue_ticket`: `ticket_id` PK, `revision`, `registration_id` FK `outpatient_registration`, `session_id`, `room_id`, `department_id`, `doctor_id`, `queue_date`, `room_queue_number` (≥1), `status` CHECK `WAITING, CALLED, IN_EXAM, COMPLETED, ABSENT, CANCELLED`, `priority` boolean, `issued_by_account_id`, `issued_by_staff_id`, `issued_at`, `authority_scope_revision` bigint NULL, `called_by_account_id`, `called_at`, `cancelled_by_account_id`, `cancel_reason`, `cancelled_at`. `UNIQUE(room_id, queue_date, room_queue_number)`. Unique một phần `(registration_id) WHERE status <> 'CANCELLED'`.
- `clinic_room_queue_counter(room_id, queue_date, next_number, PK(room_id, queue_date))`.
- `department_queue_counter(department_id, queue_date, next_number, PK(department_id, queue_date))`.
- `queue_adjustment` (chỉ thêm): `adjustment_id`, `ticket_id`, `type` CHECK `CALLED, LATE, NO_SHOW, RETURNED, PRIORITY`, `reason`, `actor_account_id`, `actor_staff_id`, `created_at`.
- `vital_sign_observation` (chỉ thêm): `observation_id`, `registration_id`, `patient_id`, `department_id`, `observed_at`, `recorded_by_account_id`, `recorded_by_staff_id`, `pulse_bpm`, `systolic_mmhg`, `diastolic_mmhg`, `temperature_c NUMERIC(4,1)`, `respiratory_rate`, `spo2_percent`, `weight_kg NUMERIC(5,2)`, `not_measured_reason`.
- `appointment_reschedule_log` (chỉ thêm): `log_id`, `registration_id`, cặp `old_`/`new_` cho `session_id`, `doctor_id`, `room_id`, `session_date`, `start_time`, `actor_account_id`, `actor_staff_id`, `authority_scope_revision`, `reason`, `patient_confirmed`, `created_at`.
- Trigger chặn `UPDATE` và `DELETE` trên `queue_adjustment`, `vital_sign_observation`, `appointment_reschedule_log`.

- [ ] **Step 1: Viết test thất bại**

`ClinicIntakeSchemaPostgresTest` theo mẫu `DoctorWorkShiftPostgresTest`, gồm `roomNumberIsUniquePerRoomAndDate` (số trùng phòng, ngày ném `DuplicateKeyException`; khác ngày hoặc khác phòng thì được), `oneActiveTicketPerRegistration` (ticket `CANCELLED` không chặn ticket mới), `vitalSignsCannotBeUpdatedOrDeleted`, `queueAdjustmentsCannotBeUpdatedOrDeleted`, `rescheduleLogCannotBeUpdatedOrDeleted`. `ClinicIntakeSettingsTest.defaultsToDisabled` đọc `application.yml` qua `FileSystemResource("src/main/resources/application.yml")`.

- [ ] **Step 2: Chạy test, xác nhận thất bại**

Run: `mvn -q -pl backend/clinical-service -am test -Dtest='ClinicIntakeSchemaPostgresTest,ClinicIntakeSettingsTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL vì chưa có bảng và bean.

- [ ] **Step 3: Thêm migration, settings, khóa cấu hình `mediflow.v2.clinic-intake: ${MEDIFLOW_V2_CLINIC_INTAKE:false}`; ghi mọi API và mã lỗi ở mục "Hợp đồng API mới" vào `22-v2-contract-map.md` và `03-contracts.md`**

- [ ] **Step 4: Chạy lại, xác nhận đạt**

Run: lệnh Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/clinical-service/src docs/plans/22-v2-contract-map.md docs/plans/03-contracts.md
git commit -m "feat(clinical): add clinic intake schema and flag"
```

### Task 2: Số quầy theo khoa và ngày

**Files:**
- Modify: `.../application/port/out/OutpatientRegistrationRepositoryPort.java`, `.../infrastructure/persistence/adapter/OutpatientRegistrationPersistenceAdapter.java`, `.../application/service/OutpatientRegistrationApplicationService.java`
- Test: `.../infrastructure/persistence/DepartmentQueueNumberPostgresTest.java`, `.../application/service/RegistrationQueueNumberTest.java`

**Interfaces:**
- Produces: `int allocateDepartmentQueueNumber(UUID departmentId, LocalDate queueDate)`.

- [ ] **Step 1: Viết test thất bại**

`DepartmentQueueNumberPostgresTest`: `eachDepartmentHasItsOwnSequence` (khoa A: 1, 2; khoa B: 1); `sequenceRestartsOnAnotherDate`; `continuesAfterHighestExistingNumberWhenFlagTurnsOnMidDay` (đã có phiếu khoa A cùng ngày ở hai buổi, số lớn nhất 7 → số tiếp theo 8; phiếu ngày khác có số 50 không ảnh hưởng); `concurrentAllocationsNeverRepeat` (20 luồng, 20 số khác nhau, liên tục từ 1).
`RegistrationQueueNumberTest`: `flagOnUsesDepartmentSequence`, `flagOffKeepsSessionSequence` (gọi `allocateQueueNumber`, không gọi hàm mới), `confirmedFactStillCarriesPositiveQueueNumber`.

- [ ] **Step 2: Chạy test, xác nhận thất bại**

Run: `mvn -q -pl backend/clinical-service -am test -Dtest='DepartmentQueueNumberPostgresTest,RegistrationQueueNumberTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL vì chưa có `allocateDepartmentQueueNumber`.

- [ ] **Step 3: Cài đặt**

Trong adapter, một câu lệnh duy nhất:

```sql
INSERT INTO department_queue_counter(department_id, queue_date, next_number)
VALUES (?, ?, COALESCE((SELECT MAX(r.queue_number)
                          FROM outpatient_registration r
                          JOIN consultation_session s ON s.session_id = r.session_id
                         WHERE s.department_id = ? AND s.session_date = ?), 0) + 2)
ON CONFLICT (department_id, queue_date)
DO UPDATE SET next_number = department_queue_counter.next_number + 1
RETURNING next_number - 1
```

Trong `resolvePayment`, thay dòng cấp số: `int queue = ended ? 0 : (clinicIntake.enabled() ? registrations.allocateDepartmentQueueNumber(config.departmentId(), config.sessionDate()) : registrations.allocateQueueNumber(session.sessionId()))`.

- [ ] **Step 4: Chạy lại, xác nhận đạt**

Run: lệnh Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/clinical-service/src
git commit -m "feat(clinical): allocate counter numbers per department and date"
```

### Task 3: Một chính sách lịch và báo tác động

CL-SCH-01, 02, 03, 04, 06.

**Files:**
- Create: `.../application/service/ConsultationSessionPolicy.java`, `.../application/service/ScheduleImpactApplicationService.java`, `.../application/dto/response/ScheduleImpactDTO.java`
- Modify: `.../application/service/ConsultationSessionApplicationService.java`, `.../application/service/DoctorWorkShiftApplicationService.java`, `.../application/port/out/DoctorWorkShiftRepositoryPort.java`, `.../infrastructure/persistence/adapter/DoctorWorkShiftPersistenceAdapter.java`, `.../infrastructure/persistence/adapter/ConsultationSessionPersistenceAdapter.java`, `.../web/ConsultationSessionController.java`, `.../web/DoctorWorkShiftController.java`
- Test: `.../application/service/ConsultationSessionPolicyTest.java`, `.../application/service/ShiftImpactCascadeTest.java`, `.../infrastructure/persistence/DoctorWorkShiftPostgresTest.java`

**Interfaces:**
- Produces: `ConsultationSession ConsultationSessionPolicy.prepareNew(ConsultationSessionCreateRequest request, Optional<DoctorWorkShift> sourceShift)`; `void ConsultationSessionPolicy.requireUpdateAllowed(UUID sessionId, ConsultationSession.Configuration replacement)`; `void ConsultationSessionPolicy.requireReopenAllowed(UUID sessionId)`.
- Produces: port `List<UUID> linkedSessionIds(UUID shiftId)`, `Optional<DoctorWorkShift> findBySession(UUID sessionId)`.
- Produces: `record ScheduleImpactDTO(List<UUID> sessionIds, int heldCount, int paymentLockedCount, int confirmedCount, int consumedCount, List<AffectedRegistration> registrations)` với `AffectedRegistration(UUID registrationId, UUID patientId, UUID sessionId, OutpatientRegistrationStatus status, OutpatientReservationStatus reservationStatus, Instant checkedInAt, Integer queueNumber, Integer roomQueueNumber)`.

- [ ] **Step 1: Viết test thất bại**

`ConsultationSessionPolicyTest`, flag bật:
- `bothCreationPathsApplyTheSamePolicy`: tham số hóa cho hai đường tạo, ngày quá khứ đều nhận `SESSION_DATE_PAST`; bác sĩ không đủ điều kiện đều nhận `SCHEDULE_INELIGIBLE`.
- `standaloneSessionConflictingWithActiveShiftIsRejected`: ca ACTIVE cùng bác sĩ, chồng giờ, khác phòng → `SESSION_SHIFT_CONFLICT`.
- `standaloneSessionWithoutShiftIsAllowed`.
- `adjacentSessionsAreAllowed`: 08:00–10:00 và 10:00–12:00 cùng phòng.
- `linkedSessionCannotLeaveItsShift` qua `requireUpdateAllowed`, mã `SHIFT_SESSION_MISMATCH`.
- `stoppedSessionOfCancelledShiftCannotReopen`, mã `SHIFT_CANCELLED`.
- `flagOffKeepsExistingCreation`: ngày quá khứ không bị chặn thêm.

`ShiftImpactCascadeTest`, flag bật:
- `cancellingShiftStopsItsOpenSessions`: hai buổi OPEN thành STOPPED, lý do của ca được ghi.
- `cancellingShiftKeepsExistingRegistrations`: số phiếu và đếm suất không đổi.
- `updatingShiftAwayFromLinkedSessionIsRefused`, mã `SHIFT_HAS_LINKED_SESSIONS`.
- `impactListsAffectedRegistrations` cho buổi và cho ca.
- `impactRequiresScheduleScope`: bác sĩ thường không phải trưởng khoa nhận `SCHEDULING_SCOPE_DENIED`.
- `flagOffCancelLeavesSessionsUntouched`.

Thêm vào `DoctorWorkShiftPostgresTest`: `concurrentSessionsForSameDoctorHaveOneWinner` và `concurrentSessionsForSameRoomHaveOneWinner`.

- [ ] **Step 2: Chạy test, xác nhận thất bại**

Run: `mvn -q -pl backend/clinical-service -am test -Dtest='ConsultationSessionPolicyTest,ShiftImpactCascadeTest,DoctorWorkShiftPostgresTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL vì chưa có policy. Nếu hai test đồng thời đã xanh thì adapter đã khóa đúng và Step 3 không cần sửa phần đó.

- [ ] **Step 3: Cài đặt**

`create`, `createSession` đều gọi `prepareNew` trong lambda của `execute`. `update` gọi `requireUpdateAllowed`; `changeStatus` sang OPEN gọi `requireReopenAllowed`. Khi flag bật, `DoctorWorkShiftApplicationService.cancel` khóa ca, khóa các buổi theo thứ tự UUID, chuyển buổi OPEN sang STOPPED bằng `changeStatus(session.revision(), STOPPED, reason)` và `sessions.update(..., "STATUS_CHANGED", reason, now)`. `update` kiểm mọi buổi chưa hủy còn nằm trong cấu hình mới. Trong `requireNoOverlap`, nếu thiếu khóa, thêm `pg_advisory_xact_lock(hashtextextended(?,0))` cho `session-doctor:<id>:<date>` và `session-room:<id>:<date>` theo thứ tự chuỗi. `ScheduleImpactApplicationService` có điều kiện theo biểu thức ở Global Constraints.

- [ ] **Step 4: Chạy toàn bộ test lịch, xác nhận đạt**

Run: `mvn -q -pl backend/clinical-service -am test -Dtest='ConsultationSession*,DoctorWorkShift*,ShiftImpactCascadeTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/clinical-service/src
git commit -m "feat(clinical): unify session policy and report schedule impact"
```

### Task 4: Khóa hành vi đăng ký và phạm vi lịch cá nhân

CL-REG-01..06, CL-QUE-01. Nhiều test ở đây kỳ vọng xanh ngay vì mô tả hành vi đã có; test nào đỏ là lỗi thật và được sửa trong task này.

**Files:**
- Create: `.../application/service/RegistrationIntakeRegressionTest.java`, `.../infrastructure/persistence/RegistrationRacePostgresTest.java`
- Modify khi test đỏ: `.../application/service/OutpatientRegistrationApplicationService.java`

**Interfaces:** Không thêm.

- [ ] **Step 1: Viết test**

`RegistrationRacePostgresTest` dựng `OutpatientRegistrationApplicationService` với adapter thật, stub authority và patient: `twoCountersRaceForLastSeatOnlyOneWins` (hai bệnh nhân, buổi còn 1 suất, một 201 và một lỗi hết suất, `available_count = 0`).

`RegistrationIntakeRegressionTest`:
- `eligibilityIsRecheckedAtSubmission`: bác sĩ hết đủ điều kiện sau khi tải danh sách → `SCHEDULE_INELIGIBLE`.
- `counterRegistrationIsArrivedButPrebookedIsNot` và `paidPrebookedIsNotTreatedAsArrived` (`readyForExam=false`).
- `heldReservationExpiresAndReleasesSeat`, `paymentLockedReservationIsNotReleasedByExpiry`.
- `sameKeyReturnsSameRegistration`, `sameKeyDifferentPatientIsRejected`, `sameKeyDifferentSessionIsRejected`.
- `samePatientMayRegisterAnotherSessionSameDay`, `samePatientSameSessionIsDuplicate`.
- `departmentHeadCanViewButCannotStartAnotherDoctorsExam` (CL-QUE-01).
- `transferredDoctorLosesStartExamImmediately` (IMP-ID-05, Core trả khoa mới).

- [ ] **Step 2: Chạy test và ghi lại test đỏ**

Run: `mvn -q -pl backend/clinical-service -am test -Dtest='RegistrationIntakeRegressionTest,RegistrationRacePostgresTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: phần lớn PASS. Mỗi FAIL là một lỗi cần sửa ở Step 3.

- [ ] **Step 3: Sửa đúng các test đỏ, không đổi test**

- [ ] **Step 4: Chạy lại, xác nhận đạt**

Run: lệnh Step 2. Expected: PASS toàn bộ.

- [ ] **Step 5: Commit**

```bash
git add backend/clinical-service/src
git commit -m "test(clinical): lock registration race, retry and scope behavior"
```

### Task 5: Quầy điều dưỡng khoa cấp số phòng

CL-INK-01, IMP-ID-05.

**Files:**
- Create: `.../domain/model/ClinicQueueTicket.java`, `.../domain/model/ClinicQueueTicketStatus.java`, `.../application/port/out/ClinicQueueRepositoryPort.java`, `.../application/service/ClinicQueueApplicationService.java`, `.../application/dto/response/ClinicQueueTicketDTO.java`, `.../infrastructure/persistence/adapter/ClinicQueuePersistenceAdapter.java`, `.../web/ClinicQueueController.java`
- Modify: `.../application/service/OutpatientRegistrationApplicationService.java`, `.../application/dto/response/OutpatientRegistrationDTO.java`, `.../application/dto/response/RegistrationSummary.java`
- Test: `.../domain/model/ClinicQueueTicketTest.java`, `.../application/service/ClinicQueueIssueTest.java`, `.../infrastructure/persistence/ClinicQueuePostgresTest.java`, `.../web/ClinicQueueControllerTest.java`

**Interfaces:**
- Produces domain: `ClinicQueueTicket.issue(UUID ticketId, UUID registrationId, ConsultationSession session, int roomQueueNumber, UUID actorAccountId, UUID actorStaffId, Long scopeRevision, Instant at)`; `call(int expectedRevision, UUID actor, Instant at)`; `markAbsent(int)`; `markReturned(int)`; `prioritize(int)`; `startExam(Instant)`; `complete(Instant)`; `cancel(UUID actor, String reason, Instant at)`. Chuyển trạng thái sai ném `IllegalStateException("TICKET_STATE_CONFLICT")`, sai revision ném `REVISION_CONFLICT`.
- Produces port: `int allocateRoomQueueNumber(UUID roomId, LocalDate queueDate)`; `Optional<ClinicQueueTicket> findActiveByRegistration(UUID)`; `Optional<ClinicQueueTicket> findByIdForUpdate(UUID)`; `insert`, `update`; `Map<UUID,Integer> activeNumbers(Collection<UUID> registrationIds)`.
- Produces service: `IdempotentCommandPort.Result<ClinicQueueTicketDTO> issue(UUID registrationId, String key)`; `requireDepartmentNurse(ActorIdentity, UUID departmentId)` trả `Long` `scopeRevision`.
- Produces DTO: `ClinicQueueTicketDTO(UUID ticketId, int revision, UUID registrationId, UUID roomId, LocalDate queueDate, int roomQueueNumber, ClinicQueueTicketStatus status, boolean priority, Instant issuedAt, Instant calledAt)`. `OutpatientRegistrationDTO`, `RegistrationSummary` thêm `Integer roomQueueNumber`.

- [ ] **Step 1: Viết test thất bại**

`ClinicQueueIssueTest`:
- `issuesNextNumberOfDoctorsRoom`: phòng lấy từ buổi của phiếu.
- `resendingSameKeyReturnsSameNumber`.
- `differentKeyOnTicketedRegistrationIsRejected`, mã `ROOM_TICKET_EXISTS`.
- `requiresConfirmedArrivedAndCleared`: tham số hóa `AWAITING_PAYMENT`, chưa đến, thiếu clearance → `ROOM_TICKET_NOT_ELIGIBLE`.
- `onlyOnTheSessionDate`: ngày khác → `ROOM_TICKET_WRONG_DATE`.
- `queueDateComesFromSessionNotServerClock`: `Clock` đặt `2026-11-10T23:30:00Z`, buổi ngày `2026-11-11` → cấp được, `queueDate = 2026-11-11`.
- `receptionNurseCannotIssue`, `otherDepartmentNurseCannotIssue`, `adminCannotIssue`, mã `CLINIC_DESK_SCOPE_DENIED`; khoa lấy từ `currentDoctorAuthority`.
- `nurseTransferredMidShiftIsDeniedImmediately`.
- `coreOutageDeniesTicket`: port ném lỗi không khả dụng → 503, không gọi `allocateRoomQueueNumber`.
- `returningForResultsDoesNotIssueNewNumber`: ticket `IN_EXAM` → `ROOM_TICKET_EXISTS`.

`ClinicQueuePostgresTest`:
- `twoRoomsOfOneDepartmentNumberIndependently`.
- `twoDoctorsSharingRoomInDifferentSessionsShareSequence`.
- `sequenceRestartsNextDay`.
- `concurrentIssueWithDifferentKeysBurnsNoNumber`: hai luồng cùng phiếu, một thành công; số tiếp theo của phòng là 2.
- `concurrentIssueForDifferentPatientsNeverRepeats`.

`ClinicQueueTicketTest` phủ bảng chuyển trạng thái. `ClinicQueueControllerTest`: `controllerAbsentWhenFlagOff`, `issueRequiresIdempotencyKey`, `issueIsNurseOnly`. Thêm `roomQueueNumberIsOmittedWithoutTicket` kiểm JSON của `OutpatientRegistrationDTO`.

- [ ] **Step 2: Chạy test, xác nhận thất bại**

Run: `mvn -q -pl backend/clinical-service -am test -Dtest='ClinicQueue*' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL vì chưa có lớp.

- [ ] **Step 3: Cài đặt**

Thứ tự trong `issue`: kiểm quyền, rồi trong `execute`: khóa buổi, khóa phiếu, kiểm điều kiện, kiểm ticket đang hiệu lực, chỉ sau đó mới `allocateRoomQueueNumber`. Bộ đếm phòng dùng upsert như Task 2, giá trị khởi tạo `2`, `RETURNING next_number - 1`, không cần tra số lớn nhất vì bảng ticket là mới. Bean có điều kiện theo biểu thức ở Global Constraints, trừ phần tra số trong `toDto` của service đăng ký dùng `ObjectProvider<ClinicQueueRepositoryPort>` để chạy được khi flag tắt.

- [ ] **Step 4: Chạy lại, xác nhận đạt**

Run: lệnh Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/clinical-service/src
git commit -m "feat(clinical): issue room queue numbers at the department desk"
```

### Task 6: Hàng chờ, gọi vào khám và điều chỉnh

CL-QUE-02, 03, 05, CL-INK-03, CL-REG-05.

**Files:**
- Create: `.../application/dto/response/ClinicQueueEntryDTO.java`, `.../domain/model/ClinicQueueStage.java`, `.../application/dto/request/ClinicQueueAdjustmentRequest.java`, `.../application/dto/request/ClinicQueueCallRequest.java`
- Modify: `.../application/service/ClinicQueueApplicationService.java`, `.../application/port/out/ClinicQueueRepositoryPort.java`, `.../infrastructure/persistence/adapter/ClinicQueuePersistenceAdapter.java`, `.../web/ClinicQueueController.java`
- Test: `.../application/service/ClinicQueueBoardTest.java`, `.../application/service/ClinicQueueCallTest.java`

**Interfaces:**
- Produces: `enum ClinicQueueStage { AWAITING_PAYMENT, NOT_ARRIVED, WAITING_ROOM_TICKET, WAITING_CALL, CALLED, IN_EXAM, ABSENT }`.
- Produces: `ClinicQueueEntryDTO(UUID registrationId, UUID patientId, UUID sessionId, UUID doctorId, UUID departmentId, UUID roomId, LocalDate queueDate, Integer queueNumber, UUID ticketId, Integer ticketRevision, Integer roomQueueNumber, ClinicQueueStage stage, boolean priority, Instant checkedInAt, Instant calledAt, boolean vitalSignsRecorded, boolean readyForExam, boolean continuationRequired)`.
- Produces: `List<ClinicQueueEntryDTO> board(LocalDate date, UUID roomId)`; `Result<ClinicQueueTicketDTO> call(UUID ticketId, ClinicQueueCallRequest, String key)`; `Result<ClinicQueueTicketDTO> adjust(UUID ticketId, ClinicQueueAdjustmentRequest, String key)`.
- `ClinicQueueAdjustmentRequest.type` nhận `LATE, NO_SHOW, RETURNED, PRIORITY`; `reason` 1–500 ký tự, bắt buộc.

- [ ] **Step 1: Viết test thất bại**

`ClinicQueueBoardTest`:
- `separatesRegisteredFromAllowedToExamine`: từng tình huống cho đúng `stage`; chỉ phiếu đã có số phòng mới `WAITING_CALL`.
- `nurseSeesOnlyHerDepartmentWhateverRoomIsRequested`: `roomId` khoa khác → `CLINIC_DESK_SCOPE_DENIED`.
- `doctorSeesOnlyOwnSessions`.
- `ordersPriorityThenRoomNumberAbsentLast`.
- `finishedSessionWithOpenExamIsMarkedForContinuation`: buổi đã hết giờ, phiếu `IN_EXAM` → `continuationRequired=true`.

`ClinicQueueCallTest`:
- `nurseOfDepartmentCanCall`, `assignedDoctorCanCall`, `otherDoctorCannotCall`.
- `callRecordsCallerAndTimeAndAppendsAdjustment`.
- `recallKeepsNumberAndAppendsAnotherRow`.
- `noShowMarksAbsentWithoutReleasingSeat`: đếm suất không đổi, trạng thái phiếu vẫn `CONFIRMED`.
- `returnedKeepsOriginalNumber`.
- `priorityRequiresReasonAndRecordsActor`.
- `staleRevisionIsRejected`.
- `sameKeyReplaysSameResult`.

- [ ] **Step 2: Chạy test, xác nhận thất bại**

Run: `mvn -q -pl backend/clinical-service -am test -Dtest='ClinicQueueBoardTest,ClinicQueueCallTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL vì chưa có `board`, `call`, `adjust`.

- [ ] **Step 3: Cài đặt theo Interfaces**

Sắp xếp: có ticket trước; trong đó `ABSENT` cuối; `priority` trước; rồi `room_queue_number`. Phiếu chưa có ticket xếp theo `queueNumber`. Đọc bằng một truy vấn gộp theo danh sách buổi, không gọi từng phiếu.

- [ ] **Step 4: Chạy lại, xác nhận đạt**

Run: lệnh Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/clinical-service/src
git commit -m "feat(clinical): add clinic queue board, calling and adjustments"
```

### Task 7: Bắt đầu khám yêu cầu số phòng

**Files:**
- Modify: `.../application/service/OutpatientRegistrationApplicationService.java`
- Test: `.../application/service/StartExamRoomTicketTest.java`

**Interfaces:** Consumes `ClinicQueueRepositoryPort`, `ClinicQueueTicket.startExam`, `complete`, `cancel` (Task 5).

- [ ] **Step 1: Viết test thất bại**

- `flagOnRequiresRoomTicket`: chưa có số → `CLINICAL_ROOM_TICKET_REQUIRED`, phiếu và lịch hẹn không đổi.
- `absentTicketCannotStartExam`, mã `TICKET_STATE_CONFLICT`.
- `waitingAndCalledTicketsCanStart`, ticket thành `IN_EXAM`.
- `missingVitalSignsDoesNotBlockExam`.
- `flagOffStartsWithoutTicket`.
- `replayedStartDoesNotFail`: phiếu đã `IN_EXAM` giữ nguyên nhánh trả sớm.
- `completingExamCompletesTicket`.
- `cancellingRegistrationCancelsTicketWithReason`.

- [ ] **Step 2: Chạy test, xác nhận thất bại**

Run: `mvn -q -pl backend/clinical-service -am test -Dtest=StartExamRoomTicketTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL ở các test bật flag.

- [ ] **Step 3: Cài đặt trong `startExamination`, `completeExamination`, `cancel`, khi `clinicIntake.enabled()`**

Kiểm và cập nhật ticket trước khi đổi phiếu, trong cùng giao dịch. Wire của `PUT /api/v1/appointments/{id}/start-exam` không đổi.

- [ ] **Step 4: Chạy test Clinical liên quan**

Run: `mvn -q -pl backend/clinical-service -am test -Dtest='StartExamRoomTicketTest,Outpatient*,ClinicalCareFinance*' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/clinical-service/src
git commit -m "feat(clinical): require a room queue number before scheduled exams"
```

### Task 8: Sinh hiệu

CL-INK-02.

**Files:**
- Create: `.../domain/model/VitalSignObservation.java`, `.../application/port/out/VitalSignRepositoryPort.java`, `.../application/service/VitalSignApplicationService.java`, `.../application/dto/request/VitalSignCreateRequest.java`, `.../application/dto/response/VitalSignObservationDTO.java`, `.../infrastructure/persistence/adapter/VitalSignPersistenceAdapter.java`, `.../web/VitalSignController.java`
- Test: `.../domain/model/VitalSignObservationTest.java`, `.../application/service/VitalSignApplicationServiceTest.java`

**Interfaces:**
- Produces: `VitalSignCreateRequest(Integer pulseBpm, Integer systolicMmhg, Integer diastolicMmhg, BigDecimal temperatureC, Integer respiratoryRate, Integer spo2Percent, BigDecimal weightKg, String notMeasuredReason)`.
- Produces: `Result<VitalSignObservationDTO> record(UUID registrationId, VitalSignCreateRequest, String key)`; `List<VitalSignObservationDTO> list(UUID registrationId)`, mới nhất trước.
- Produces: `boolean existsFor(UUID registrationId)` cho `vitalSignsRecorded` ở Task 6; khi nối vào `board` dùng truy vấn gộp `Set<UUID> recordedAmong(Collection<UUID>)`.

- [ ] **Step 1: Viết test thất bại**

`VitalSignObservationTest`: `acceptsFullMeasurement`; `rejectsOutOfRange` tham số hóa đủ biên ở lựa chọn 8, mã `VITAL_SIGNS_INVALID`; `bloodPressureNeedsBothValues`; `diastolicMustBeBelowSystolic`; `missingMeasurementNeedsReason`, mã `VITAL_SIGNS_REASON_REQUIRED`; `allMissingStillNeedsReason`.

`VitalSignApplicationServiceTest`: `onlyDepartmentNurseRecords`; `receptionNurseCannotRecord`; `requiresArrivedRegistration`; `recordingAgainAddsNewRowAndKeepsOld`; `assignedDoctorCanRead`; `otherDoctorGetsNotFound`; `sameKeyReplaysSameObservation`; `noAbnormalFlagInResponse` (DTO không có trường đánh giá).

- [ ] **Step 2: Chạy test, xác nhận thất bại**

Run: `mvn -q -pl backend/clinical-service -am test -Dtest='VitalSign*' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL vì chưa có lớp.

- [ ] **Step 3: Cài đặt theo Interfaces, dùng lại `requireDepartmentNurse`, `requireAssignedDoctor`; nối `recordedAmong` vào `board`**

- [ ] **Step 4: Chạy lại, xác nhận đạt**

Run: `mvn -q -pl backend/clinical-service -am test -Dtest='VitalSign*,ClinicQueueBoardTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/clinical-service/src
git commit -m "feat(clinical): record append-only vital signs at the department desk"
```

### Task 9: Đổi bác sĩ, phòng, giờ trước khám

CL-QUE-04, CL-SCH-06.

**Files:**
- Create: `.../application/service/RegistrationRescheduleApplicationService.java`, `.../application/dto/request/RegistrationRescheduleRequest.java`
- Modify: `.../domain/model/OutpatientRegistration.java`, `.../domain/model/Appointment.java`, `.../application/event/OutpatientRegistrationFact.java`, `.../application/port/out/OutpatientRegistrationRepositoryPort.java`, `.../infrastructure/persistence/adapter/OutpatientRegistrationPersistenceAdapter.java`, `.../web/OutpatientRegistrationController.java`
- Test: `.../domain/model/OutpatientRegistrationTest.java`, `.../application/service/RegistrationRescheduleTest.java`, `.../infrastructure/persistence/RegistrationRacePostgresTest.java`, `.../infrastructure/messaging/ClinicalOutpatientRegistrationContractFixtureTest.java`

**Interfaces:**
- Produces domain: `OutpatientRegistration.reschedule(int expectedRevision, ConsultationSession target, Integer newQueueNumber, boolean clearArrival, Instant at)`; `Appointment.rescheduleScheduled(UUID doctorId, LocalDate date, LocalTime time, boolean clearArrival, Instant at)`.
- Produces event: `OutpatientRegistrationFact.RescheduleFact(UUID actorAccountId, UUID actorStaffId, String reason, UUID previousSessionId, UUID previousDoctorId, UUID previousRoomId, LocalDate previousSessionDate, LocalTime previousStartTime, Instant rescheduledAt)`, trường `reschedule` thêm cuối record, `@JsonInclude(NON_NULL)`.
- Produces: `Result<OutpatientRegistrationDTO> reschedule(UUID registrationId, RegistrationRescheduleRequest, String key)`.

- [ ] **Step 1: Viết test thất bại**

`RegistrationRescheduleTest`:
- `movesSeatBetweenSessions`: buổi cũ trả suất, buổi mới trừ suất, đúng nhóm giữ hoặc đã xác nhận.
- `targetWithoutSeatLeavesEverythingUnchanged`.
- `requiresPatientConfirmation`, mã `RESCHEDULE_CONFIRMATION_REQUIRED`.
- `targetMustBeOpenSameDepartmentAndEligible`, mã `RESCHEDULE_TARGET_INVALID`.
- `rescheduleDuringPaymentLockIsRefused`, mã `REGISTRATION_PAYMENT_IN_PROGRESS`, số suất hai buổi không đổi.
- `examStartedCannotBeRescheduled`.
- `sameDayKeepsCounterNumberAndArrival`.
- `otherDayGetsNewCounterNumberAndNeedsArrival`.
- `activeRoomTicketIsCancelledWithReason`.
- `writesOldNewActorReasonAndConfirmationToLog`.
- `doesNotTouchMedicalRecordAuthor`: không có lời gọi tới repository hồ sơ.
- `clearanceStaysBoundToSameRegistration`.
- `factCarriesRescheduleOnlyOnThisRevision`: sự kiện kế tiếp không còn `reschedule`.
- `locksSessionsInUuidOrder`.
- `flagOffEndpointIsAbsent`.

Thêm vào fixture test: `factWithoutRescheduleKeepsExactExistingJson`. Thêm vào `RegistrationRacePostgresTest`: `twoReschedulesForLastSeatOnlyOneWins`.

- [ ] **Step 2: Chạy test, xác nhận thất bại**

Run: `mvn -q -pl backend/clinical-service -am test -Dtest='RegistrationRescheduleTest,RegistrationRacePostgresTest,OutpatientRegistrationTest,ClinicalOutpatientRegistrationContractFixtureTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL vì chưa có `reschedule`.

- [ ] **Step 3: Cài đặt**

Quyền như hủy phiếu (`requireActiveActor`), `requireEligible` cho buổi đích, ghi `scopeRevision` vào log. Khóa hai buổi theo UUID tăng dần rồi khóa phiếu. Số quầy ngày mới lấy từ `allocateDepartmentQueueNumber`, chỉ khi phiếu đã `CONFIRMED`. `registration.update` với event `"RESCHEDULED"`.

- [ ] **Step 4: Chạy lại, xác nhận đạt**

Run: lệnh Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/clinical-service/src
git commit -m "feat(clinical): reschedule registrations with an auditable trail"
```

### Task 10: Billing tách void và hoàn, nhận đổi lịch

IMP-FIN-04.

**Files:**
- Create: `backend/billing-service/src/main/resources/db/migration/V34__registration_settlement.sql`, `.../billing/application/dto/response/RegistrationSettlementDTO.java`, `.../billing/application/port/in/ListRegistrationSettlementsUseCase.java`, `.../billing/application/service/ListRegistrationSettlementsService.java`, `.../billing/web/RegistrationSettlementController.java`
- Modify: `.../billing/infrastructure/persistence/adapter/OutpatientRegistrationSourceAdapter.java`, `.../billing/application/service/ConsumeOutpatientRegistrationFactService.java`, DTO sự kiện `OutpatientRegistrationChangedEvent`, `.../billing/infrastructure/config/ScheduledPaymentConfiguration.java`
- Test: `.../billing/infrastructure/persistence/RegistrationSettlementPostgresTest.java`, `.../billing/application/service/ConsumeOutpatientRegistrationFactServiceTest.java`, `.../billing/web/RegistrationSettlementControllerTest.java`

**Interfaces:**
- Consumes: `RescheduleFact` (Task 9).
- Produces bảng `outpatient_registration_settlement`: PK `(registration_id, registration_revision)`, `kind` CHECK `VOID_UNPAID, REFUND_DUE, RESCHEDULED`, `payment_request_id`, `amount`, `currency`, `source_type`, `source_id`, `actor_account_id`, `actor_staff_id`, `reason`, `previous_session_id`, `new_session_id`, `occurred_at`.
- Produces: `RegistrationSettlementDTO(UUID registrationId, long registrationRevision, String kind, UUID paymentRequestId, BigDecimal amount, BigDecimal refundedAmount, BigDecimal outstandingAmount, String currency, String sourceType, UUID sourceId, UUID actorAccountId, String reason, Instant occurredAt)`.

- [ ] **Step 1: Viết test thất bại**

`RegistrationSettlementPostgresTest`:
- `cancelledUnpaidIsVoided`: `VOID_UNPAID`, yêu cầu thanh toán `CANCELLED`.
- `cancelledPaidBecomesRefundDue`: `amount` bằng số đã thu, yêu cầu thanh toán không bị sửa, ghi đủ actor, reason, source.
- `cashierCanRefundCancelledPaidRegistration`: `outpatientRefundEligibility` trả `cancellationApplied=true` và `refundDue` bằng số đã thu.
- `refundAboveDueIsRefused`, mã `BILLING_REFUND_EXCEEDS_OUTPATIENT_DUE`.
- `unpaidCancellationHasNoRefundEvidence`.
- `examAdjustmentLookupShowsTheDueAmount`: đọc qua cổng của `GET /api/v1/billing/exam-adjustments/{id}/refunds-due` với `registrationId`.
- `labCancellationStillRequiresActorAndReason`: sự kiện `lab.request.cancelled` thiếu người hủy hoặc lý do bị từ chối.
- `partialRefundLeavesOutstanding`: `refundedAmount` tính từ `PAYMENT_TRANSACTION` loại `REFUND` trạng thái `COMPLETED`.
- `rescheduleUpdatesSourceAndKeepsRequest`: cột bác sĩ, buổi, phòng đổi; `payment_request_id` giữ nguyên; `RESCHEDULED`.
- `redeliveredRescheduleCreatesOneSettlement`.
- `rescheduleCannotChangeDepartmentOrPatient`, mã `BILLING_REGISTRATION_SOURCE_CONFLICT`.
- `changedDoctorWithoutRescheduleFactStillConflicts`.
- `rescheduleMustMatchCurrentSession`: `previousSessionId` khác buổi đang lưu → conflict.

`ConsumeOutpatientRegistrationFactServiceTest.fingerprintOfFactWithoutRescheduleIsUnchanged`: giá trị hash khớp hằng số tính từ mã hiện tại trước khi sửa. `RegistrationSettlementControllerTest`: `cashierAndAdminOnly`.

- [ ] **Step 2: Chạy test, xác nhận thất bại**

Run: `mvn -q -pl backend/billing-service -am test -Dtest='RegistrationSettlement*,ConsumeOutpatientRegistrationFactServiceTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL vì chưa có bảng và trường.

- [ ] **Step 3: Cài đặt**

`sameImmutableBinding` bỏ qua `doctorId`, `sessionId`, `roomId`, `sessionDate`, `startTime`, `endTime` chỉ khi `fact.reschedule() != null`, `previousSessionId` khớp nguồn hiện tại và trạng thái là `AWAITING_PAYMENT` hoặc `CONFIRMED`. Fingerprint chỉ nối `reschedule` khi khác null. Ghi settlement bằng `INSERT ... ON CONFLICT DO NOTHING` trong `apply`, sau `updateSource`, đọc tổng đã thu trong cùng giao dịch. Khi `REFUND_DUE`, ghi thêm trong cùng giao dịch một dòng `outpatient_cancellation_source` trạng thái `APPLIED` với `source_type=EXAM`, `source_id=registrationId` và mỗi khoản phân bổ một dòng `outpatient_cancellation_refund_due`, dùng `ON CONFLICT DO NOTHING`, để `LedgerRefundService` và tra cứu `exam-adjustments` chạy không đổi. Nếu nhánh `EXAM` của `OutpatientCancellationRepositoryAdapter.source` chỉ đọc `exam_charge_source` thì mở rộng thêm nguồn `outpatient_registration_source`. `refundedAmount` dùng cùng biểu thức phân bổ với `refund_due`.

- [ ] **Step 4: Chạy test Billing liên quan**

Run: `mvn -q -pl backend/billing-service -am test -Dtest='RegistrationSettlement*,*OutpatientRegistration*,*ScheduledPayment*' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/billing-service/src
git commit -m "feat(billing): separate voids from refunds and accept reschedules"
```

### Task 11: Thông báo đổi lịch và lời hẹn

IMP-NOT-02, phần đổi lịch và lời hẹn.

**Files:**
- Modify: `backend/notification-service/.../infrastructure/messaging/RegistrationNoticeDecoder.java`, `.../application/dto/command/RegistrationNoticeCommand.java`, `.../application/service/RegistrationNoticeService.java`
- Test: `.../application/service/RegistrationNoticeServiceTest.java`, `.../infrastructure/messaging/RegistrationNoticeDecoderTest.java`, `.../infrastructure/messaging/PatientResultNoticeDecoderTest.java`

**Interfaces:**
- Consumes: JSON `reschedule` (Task 9).
- Produces: `RegistrationNoticeCommand` thêm `boolean rescheduled`.

- [ ] **Step 1: Viết test thất bại**

- `decoderAcceptsRescheduleWithExactFields`, `decoderRejectsUnknownRescheduleField`, `decoderStillAcceptsFactWithoutReschedule`.
- `rescheduledNoticeGoesToThePatientOnce`: tiêu đề đúng `Lịch khám đã thay đổi`, loại `OUTPATIENT_REGISTRATION_RESCHEDULED`, liên kết theo `registrationId`.
- `rescheduledContentAsksToCheckNewDetailsWithoutReason`: nội dung đúng `Lịch khám của bạn đã được đổi. Vui lòng xem ngày, giờ, bác sĩ và phòng mới trong phiếu khám.`, không chứa `reason`.
- `confirmedNoticeUnchangedWithoutReschedule`.
- `followUpNoticeDoesNotClaimAReservedSeat`: nội dung lời hẹn không chứa "đã đặt" hoặc "giữ suất", có hướng dẫn liên hệ đăng ký.

- [ ] **Step 2: Chạy test, xác nhận thất bại**

Run: `mvn -q -pl backend/notification-service -am test -Dtest='RegistrationNotice*,PatientResultNoticeDecoderTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL ở các test `reschedule`. `followUpNoticeDoesNotClaimAReservedSeat` có thể xanh ngay.

- [ ] **Step 3: Cài đặt theo Interfaces**

Thêm `reschedule` vào tập trường cho phép dưới dạng tùy chọn, kiểm đủ chín trường bằng `exactFields`. Nhánh `rescheduled` xử lý trước `switch` trạng thái.

- [ ] **Step 4: Chạy lại, xác nhận đạt**

Run: lệnh Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/notification-service/src
git commit -m "feat(notification): notify patients about rescheduled visits"
```

### Task 12: Gateway

**Files:**
- Modify: `backend/gateway/src/main/java/com/mediflow/gateway/config/SchedulingRouteConfiguration.java`, `.../filter/RouteAuthorizationFilter.java`, `backend/gateway/src/test/resources/route-role-contracts.csv`
- Test: `backend/gateway/src/test/java/com/mediflow/gateway/.../RouteRoleContractTest.java`, `.../SchedulingRouteConfigurationTest.java`

**Interfaces:** Consumes bảng "Hợp đồng API mới".

- [ ] **Step 1: Thêm mười dòng contract, thêm `clinicIntakeRoutesAreRegistered` và `clinicIntakeRoutesAbsentWhenSchedulingOff`**

- [ ] **Step 2: Chạy test, xác nhận thất bại**

Run: `mvn -q -pl backend/gateway -am test -Dtest='RouteRoleContractTest,SchedulingRouteConfigurationTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL vì thiếu rule và route.

- [ ] **Step 3: Thêm rule, route; `registration-settlements` đi `scheduling-billing`, còn lại đi `scheduling-clinical`**

- [ ] **Step 4: Chạy toàn bộ test Gateway**

Run: `mvn -q -pl backend/gateway -am test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/gateway/src
git commit -m "feat(gateway): route clinic intake APIs"
```

### Task 13: Web — lịch, tiếp nhận và đổi lịch

CL-SCH-02, 05, 06, CL-REG-02..05, CL-QUE-04, IMP-FIN-04.

**Files:**
- Modify: `frontend/src/features/appointment/types.ts`, `api.ts`, `schedule-presentation.ts`, `presentation.ts`, `components/ReceptionWorkspace.tsx`, `components/DoctorCalendarWorkspace.tsx`, `components/PatientRegistrationWorkspace.tsx`, `components/ScheduleManagementWorkspace.tsx`, `components/DoctorWorkShiftsWorkspace.tsx`, `frontend/src/features/billing/api.ts`, `frontend/src/features/billing/types.ts`, `frontend/src/features/billing/components/RefundDueLookup.tsx`
- Create: `components/RescheduleDialog.tsx`, `components/ScheduleImpactPanel.tsx`
- Test: các file `*.test.ts(x)` cùng tên

**Interfaces:**
- Produces: `sessionAvailability(session | null, loadError): "AVAILABLE" | "FULL" | "NOT_OPEN" | "LOAD_ERROR"`; `registrationOutcomeLabel(item)`; `queueNumberLabel(enabled)` trả `"Số quầy"` khi bật, `"Số thứ tự"` khi tắt.
- Produces trong `appointmentApi`: `sessionImpact(id)`, `shiftImpact(id)`, `reschedule(id, body, {idempotencyKey, correlationId})`.
- Flag giao diện `MEDIFLOW_CLINIC_INTAKE_UI_ENABLED`, đọc trong page server component như `MEDIFLOW_SCHEDULING_UI_ENABLED` rồi truyền prop `clinicIntakeEnabled`.

- [ ] **Step 1: Viết test thất bại**

- `distinguishes full, not open and load error`.
- `labels cancelled, expired, absent and unresolved payment differently`.
- `prebooked paid registration is not shown as arrived`.
- `keeps the same idempotency key when retrying a failed registration` và `creates a new key after the patient changes`.
- `shows hold expiry and does not offer release while payment is locked`.
- `shows counter and room numbers with explicit labels`, `keeps old label when flag is off`.
- `reschedule requires reason and patient confirmation`, `reschedule retries with the same key`.
- `blocked session edit opens the impact panel`, `shift cancel shows affected registrations before confirming`.
- `billing lists refund due separately from voided`.
- `refund lookup accepts an exam registration source`.

- [ ] **Step 2: Chạy test, xác nhận thất bại**

Run: `cd frontend && pnpm test -- src/features/appointment`
Expected: FAIL ở các test mới.

- [ ] **Step 3: Cài đặt theo Interfaces, HTTP chỉ qua `src/lib/api.ts`, Tailwind utilities**

- [ ] **Step 4: Chạy test, typecheck, lint**

Run: `cd frontend && pnpm test -- src/features/appointment src/features/billing && pnpm typecheck && pnpm lint`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/appointment frontend/src/features/billing
git commit -m "feat(web): show schedule impact, rescheduling and clear queue numbers"
```

### Task 14: Web — quầy điều dưỡng khoa và sinh hiệu cho bác sĩ

CL-INK-01..03, CL-QUE-02, 03, 05.

**Files:**
- Create: `frontend/src/app/(dashboard)/appointments/clinic-queue/page.tsx`, `frontend/src/features/appointment/components/ClinicQueueWorkspace.tsx`, `components/VitalSignForm.tsx`, `components/VitalSignHistory.tsx`
- Modify: `frontend/src/features/appointment/types.ts`, `api.ts`, `components/OutpatientExamActions.tsx`, `frontend/src/components/layout/DashboardSidebar.tsx`
- Test: `components/ClinicQueueWorkspace.test.tsx`, `components/VitalSignForm.test.tsx`, `components/OutpatientExamActions.test.tsx`, `api.test.ts`

**Interfaces:**
- Produces trong `appointmentApi`: `clinicQueue(date, roomId?)`, `issueRoomTicket(registrationId, options)`, `callTicket(ticketId, body, options)`, `adjustTicket(ticketId, body, options)`, `createVitalSigns(registrationId, body, options)`, `listVitalSigns(registrationId)`.

- [ ] **Step 1: Viết test thất bại**

- `groups the queue by room with each room numbered independently`.
- `separates waiting for a room number from allowed to examine`.
- `issues a number and shows it`, `retry keeps the same key`.
- `shows a clear refusal for a reception nurse`.
- `call records and shows the caller time`, `absent requires a reason`, `priority requires a reason`.
- `flags open visits needing continuation`.
- `vital form requires a reason when a measure is missing`, `does not show any abnormal marker`, `rejects a comma decimal with a Vietnamese message`.
- `doctor sees vitals when examining`, `missing vitals warns but keeps start enabled`.
- `missing room number disables start with the reason`.
- `sidebar shows the desk only for NURSE and DOCTOR with the flag on`.

- [ ] **Step 2: Chạy test, xác nhận thất bại**

Run: `cd frontend && pnpm test -- src/features/appointment`
Expected: FAIL ở các test mới.

- [ ] **Step 3: Cài đặt theo Interfaces; sửa `DashboardSidebar` thuộc phạm vi dùng chung của Vinh trong `15-frontend-ownership.md`**

- [ ] **Step 4: Chạy test, typecheck, lint**

Run: `cd frontend && pnpm test -- src/features/appointment src/components/layout && pnpm typecheck && pnpm lint`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/appointment "frontend/src/app/(dashboard)/appointments/clinic-queue" frontend/src/components/layout
git commit -m "feat(web): add department nurse desk and vital signs"
```

### Task 15: Mobile

**Files:**
- Create: `mobile/lib/features/appointment/domain/clinic_queue.dart`, `data/gateway_clinic_queue_repository.dart`, `presentation/clinic_queue_page.dart`, `presentation/vital_sign_sheet.dart`, `presentation/reschedule_sheet.dart`
- Modify: `mobile/lib/core/config/app_config.dart`, `mobile/lib/features/appointment/data/gateway_outpatient_schedule_repository.dart`, `data/gateway_reception_repository.dart`, `data/gateway_schedule_management_repository.dart`, `domain/outpatient_schedule.dart`, `domain/reception.dart`, `presentation/outpatient_schedule_pages.dart`, `presentation/reception_page.dart`, `presentation/schedule_management_pages.dart`, `mobile/lib/app/router.dart`, `mobile/lib/app/modules.dart`
- Test: `mobile/test/features/appointment/...`

**Interfaces:**
- Produces: `AppConfig.clinicIntakeEnabled` từ `bool.fromEnvironment('MEDIFLOW_CLINIC_INTAKE_UI_ENABLED')`.
- Produces: `abstract class ClinicQueueRepository` có `board`, `issueTicket`, `call`, `adjust`, `recordVitals`, `listVitals`, `reschedule`, `sessionImpact`, `shiftImpact`, tất cả qua `core/network/api_client.dart`. Mỗi lệnh ghi nhận `idempotencyKey` do tầng gọi giữ.

- [ ] **Step 1: Viết test thất bại**

- `parses each queue stage and optional room number`.
- `issue and call send Idempotency-Key through api_client`.
- `reuses the key when retrying after a network failure`.
- `shows Số quầy and Số phòng khám separately when enabled`, `keeps Số thứ tự when disabled`.
- `distinguishes full, not open and load error`.
- `nurse desk lists rooms with independent numbers`.
- `vital sheet requires reason for missing measure`.
- `doctor sees missing vitals as a warning only`.
- `reschedule requires reason and patient confirmation`.
- `desk entry hidden when flag is off`.

- [ ] **Step 2: Chạy test, xác nhận thất bại**

Run: `cd mobile && flutter test test/features/appointment`
Expected: FAIL ở các test mới.

- [ ] **Step 3: Cài đặt theo Clean Architecture, `presentation → domain ← data`**

- [ ] **Step 4: Chạy test và analyze**

Run: `cd mobile && flutter test test/features/appointment && flutter analyze lib/features/appointment lib/core/config`
Expected: PASS, không có issue.

- [ ] **Step 5: Commit**

```bash
git add mobile/lib mobile/test
git commit -m "feat(mobile): add clinic queue, vital signs and rescheduling"
```

### Task 16: Bật cho demo và nghiệm thu

**Files:**
- Modify: `compose.outpatient.local.yml`
- Create: `docs/plans/24-v2-wave-2-acceptance.md`
- Modify: `backend/clinical-service/README.md` (thêm flag vào phần cấu hình)

**Interfaces:** Consumes toàn bộ task trên.

- [ ] **Step 1: Thêm `MEDIFLOW_V2_CLINIC_INTAKE: "true"` cho `clinical-service` và `MEDIFLOW_CLINIC_INTAKE_UI_ENABLED: "true"` cho `frontend` trong `compose.outpatient.local.yml`**

- [ ] **Step 2: Chạy toàn bộ test**

Run lần lượt, ghi log vào scratchpad: `mvn -q -pl backend/gateway,backend/clinical-service,backend/billing-service,backend/notification-service,backend/support-service -am test`; `cd frontend && pnpm test && pnpm typecheck && pnpm lint`; `cd mobile && flutter test`.
Expected: PASS, trừ các lỗi baseline đã biết: `OutpatientDeliveryPostgresTest.lostPublish...`, `LabTestChargeConfigurationTest.intake_requiresBothExplicitGates[4]`. Lỗi mới phải sửa trước khi đi tiếp.

- [ ] **Step 3: Dựng stack với override đổi lịch và chạy kịch bản qua Gateway thật**

Không sửa seed; kịch bản hai phòng cùng khoa dùng phòng và fixture SQL rồi dọn sau khi chạy. Mỗi dòng phải có kết quả thực tế ghi trong biên bản:
- Hai quầy tranh suất cuối: một thành công.
- Gửi lại cùng key trả cùng phiếu; cùng key khác bệnh nhân bị từ chối.
- Mỗi khoa dãy số quầy riêng, sang ngày khác bắt đầu lại.
- Bật flag giữa ngày không trùng số quầy.
- Billing và Notification vẫn nhận phiếu `CONFIRMED`.
- Hai phòng cùng khoa đánh số độc lập.
- Điều dưỡng khoa cấp số được; `demo.reception` và điều dưỡng khoa khác bị từ chối.
- Sinh hiệu đo lần hai không đè lần một; bác sĩ phụ trách đọc được.
- Thiếu số phòng không bắt đầu khám được; thiếu sinh hiệu vẫn khám được.
- Gọi vào khám ghi người gọi và thời điểm.
- Hủy ca có phiếu: buổi `STOPPED`, phiếu còn, báo tác động đúng.
- Đổi bác sĩ cùng khoa: có log, Billing `RESCHEDULED`, người bệnh nhận thông báo.
- Hủy phiếu chưa thu ra `VOID_UNPAID`; đã thu ra `REFUND_DUE`.
- Flag tắt: API mới 404, `queueNumber` cấp theo buổi như cũ.

Expected: mọi dòng đạt. Dọn fixture sau khi chạy. Ghi rõ phần tạo bằng SQL và giới hạn của bằng chứng.

- [ ] **Step 4: Viết `24-v2-wave-2-acceptance.md` theo mẫu `23-v2-wave-1-acceptance.md`, ghi rõ nếu chưa kiểm trên trình duyệt hoặc thiết bị**

- [ ] **Step 5: Commit**

```bash
git add compose.outpatient.local.yml backend/clinical-service/README.md docs/plans/24-v2-wave-2-acceptance.md
git commit -m "docs(v2): record wave 2 acceptance"
```
