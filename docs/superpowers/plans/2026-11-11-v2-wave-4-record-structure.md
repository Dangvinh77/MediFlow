# MediFlow v2.0 Đợt 4 — Cấu trúc hồ sơ, quá trình điều trị và đính chính Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hồ sơ bệnh nhân là điểm tra cứu có dòng thời gian rõ khoa, bác sĩ, loại lượt và nguồn; nội dung khám có cấu trúc; nhiều lượt được nhóm thành quá trình điều trị khi bác sĩ chọn; lượt đang mở bàn giao được; hội chẩn ghi theo từng tác giả; hồ sơ đã kết thúc chỉ sửa bằng đính chính có duyệt, bản gốc không đổi; dị ứng của bệnh nhân hiện ở mọi lượt.

**Architecture:** Clinical giữ toàn bộ dữ liệu mới. `medical_record` thêm các mục nội dung khám và bác sĩ phụ trách hiện tại. Quá trình điều trị, bàn giao, hội chẩn, đính chính và sổ dị ứng là bảng riêng, chỉ thêm, không ghi đè nội dung gốc. Trưởng khoa lấy từ `canManageSchedules` sẵn có. Core Organization chỉ thêm một năng lực cấp được `MEDICAL_RECORD_REVIEW` vào `staff_capability_grant` cho người rà soát hồ sơ cấp bệnh viện (phòng Kế hoạch tổng hợp), theo đúng mẫu `LAB_RESULT_RELEASE`. Pharmacy và Report không đổi code; chỉ thêm test khóa ranh giới. `careEpisodeId`, `recordId` và khóa tính phí giữ nguyên nghĩa.

**Tech Stack:** Spring Boot, Flyway, PostgreSQL, Testcontainers, Next.js 16, Flutter/Riverpod.

**Spec:** [`03-clinical-service-improvement-checklist.md`](../../known-limitations/03-clinical-service-improvement-checklist.md) mục 1, 3, 5, 9, 11, 13, 16 và 18–21; [lộ trình](2026-11-10-mediflow-v2-roadmap.md) Đợt 4.

**Mã checklist:** REC-01, REC-03, REC-04, REC-05, REC-06, AMD-02, AMD-04, AMD-05, AMD-06, COL-01, COL-02, DOC-01..04, UX-01, UX-04, VIEW-03, MIG-02, IMP-ID-04, IMP-RX-01, IMP-RX-02, IMP-REP-01.

## Global Constraints

- Flag theo lộ trình, mặc định `false`: `mediflow.v2.treatment-course` (quá trình điều trị) và `mediflow.v2.record-amendment` (đính chính, bàn giao, hội chẩn). Controller của các tính năng này dùng `@ConditionalOnProperty(prefix = "mediflow.v2", name = "...", havingValue = "true")`. Nội dung có cấu trúc, dòng thời gian và danh sách tài liệu là thay đổi cộng thêm, không có flag.
- Mọi trường wire mới dùng `@JsonInclude(NON_NULL)`. Không đổi nghĩa trường đã phát hành: `doctorId` của record và của `medicalrecord.completed` vẫn là bác sĩ mở hồ sơ; `careEpisodeId` vẫn là mã lượt.
- `/api/v2/*` mới phải có `@PreAuthorize`, rule ở `RouteAuthorizationFilter`, route ở `SchedulingRouteConfiguration` và dòng trong `route-role-contracts.csv`.
- Mọi lệnh ghi mới bắt buộc `Idempotency-Key` và đi qua `IdempotentCommandPort`, theo mẫu `ExamClosureApplicationService`.
- Đọc dữ liệu mới của một hồ sơ dùng `RecordReadGuard.requireRecord`; đọc theo bệnh nhân dùng `requirePatient`, như `MedicalRecordApplicationService.byPatient`.
- Không sao chép chẩn đoán, đơn thuốc, chỉ định của lượt cũ sang lượt mới. Không gộp hồi tố các lượt vào quá trình điều trị.
- Không có từ "chữ ký", "ký số", "hợp pháp" ở UI và tài liệu cho phần xác nhận đính chính; dùng "xác nhận nội bộ".
- Migration Clinical bắt đầu từ `V19` và đi theo thứ tự task: `V19` nội dung, `V20` quá trình, `V21` bàn giao và hội chẩn, `V22` đính chính, `V23` sổ dị ứng. Nâng số ghim trong `ConsumerInboxPostgresTest` ở mỗi task có migration. Migration Organization tiếp theo là `V22`.
- Ngày tính theo `Asia/Ho_Chi_Minh`. Web chỉ gọi qua `src/lib/api.ts`, feature không import lẫn nhau; mobile chỉ qua `core/network/api_client.dart`.
- Commit một tác giả người, không `Co-Authored-By`, không trailer AI. Không `git add` file trong `docs/documentation_docx`. Chạy test web riêng, không song song với Maven.

## Kết quả đối chiếu source

| Mã | Hiện trạng | Việc của đợt này |
|---|---|---|
| REC-01, UX-01 | `MedicalRecordTable` là bảng phẳng theo bệnh nhân, không có khoa, loại lượt, lọc, phân trang | Endpoint dòng thời gian, màn mới |
| REC-03 | Có `previousRecordId` từ Đợt 3 và nguồn lời hẹn | Hiện trên dòng thời gian |
| REC-04, REC-05, MIG-02 | Chưa có quá trình điều trị | **Làm mới** |
| REC-06, IMP-ID-04 | Record chỉ có `symptoms`, chẩn đoán, hướng xử trí. Core Patient chỉ có thông tin hành chính, không có dị ứng hay tiền sử. Sinh hiệu ở `vital_sign_observation` của Clinical (Đợt 2) | Thêm mục nội dung khám, ghi bảng authority; sổ dị ứng cấp bệnh nhân ở Clinical (Task 7A) |
| COL-01 | Record gắn một `doctorId`; mọi guard ghi kiểm bác sĩ của appointment | **Làm mới**, đổi guard ghi sang bác sĩ phụ trách |
| COL-02 | Chưa có phiếu hội chẩn | **Làm mới** |
| AMD-02, 04, 06 | Hồ sơ đã kết thúc bất biến, không có đường đính chính. Organization chỉ cho cấp `LAB_RESULT_RELEASE` | **Làm mới**; thêm năng lực `MEDICAL_RECORD_REVIEW` (Task 6A) |
| AMD-05 | `attached_result` có `created_at` nhưng không API nào trả | Endpoint tài liệu, đánh dấu "bổ sung sau khi kết thúc" |
| DOC-01, 03 | Hồ sơ có panel chỉ định Lab và vùng kê đơn, không có danh sách tài liệu theo lượt | Danh sách tài liệu chỉ đọc, dẫn về nguồn |
| DOC-02, UX-04 | Panel lời hẹn đã tách 403 với 404 (Đợt 1). Panel khác chưa kiểm | Test bốn trạng thái cho mọi panel mới |
| DOC-04 | Liên kết lượt trước có, không ghi rõ là lịch sử | Nhãn "Lượt trước — chỉ tham khảo", không có nút sao chép |
| VIEW-03 | Không có bản in hay xuất hồ sơ ở web và mobile | Không thêm; ghi quy tắc cho khi thêm |
| IMP-RX-01 | `PrescriptionContextReadAdapter` trả `r.doctor_id`; Pharmacy chỉ cho bác sĩ đó kê | Trả bác sĩ phụ trách hiện tại |
| IMP-RX-02 | Pharmacy không nghe sự kiện hồ sơ | Test khóa: đính chính không phát sự kiện, không chạm đơn |
| IMP-REP-01 | Report đếm theo `recordId` từ `medicalrecord.completed` | Test khóa: quá trình và bàn giao không phát sự kiện, hoàn tất vẫn một lần |

## Review Focus

1. Bàn giao xong, bác sĩ cũ còn mở tab và bấm lưu: bị từ chối `CLINICAL_DOCTOR_NOT_ASSIGNED`. Test `previousDoctorCannotWriteAfterHandover`, Task 4.
2. Người gửi đính chính tự duyệt, kể cả khi là trưởng khoa hoặc người rà soát cấp bệnh viện: 403. Trưởng khoa tự gửi thì người rà soát duyệt được, không phải chờ. Test `authorCannotDecideOwnAmendment`, `reviewerWhoIsAuthorCannotDecide`, `recordReviewerApprovesHeadAuthoredAmendment`, Task 6.
3. Gắn lượt của bệnh nhân khác vào quá trình điều trị: từ chối. Test `visitOfAnotherPatientIsRefused`, Task 3.
4. Clinical không trả lời danh sách tài liệu: hiện lỗi và nút thử lại, không hiện "Chưa có tài liệu". Test `documentsOutageShowsUnavailableNotEmpty`, Task 10.
5. Đính chính được duyệt không đổi bản gốc, `careEpisodeId`, `doctorId` và không phát sự kiện. Test `approvedAmendmentLeavesOriginalAndContractFieldsUnchanged`, Task 6.

## Hợp đồng API mới

| Phương thức, đường dẫn | Vai trò | Flag | Thân yêu cầu | Trả về |
|---|---|---|---|---|
| `PUT /api/v1/records/{id}` (mở rộng) | ADMIN, DOCTOR | | thêm `chiefComplaint`, `presentIllness`, `pastHistory`, `allergyNote`, `physicalExam`, `advice` | 200 `MedicalRecordDTO` |
| `GET /api/v2/patients/{patientId}/timeline?page&size&departmentId&status` | ADMIN, DOCTOR, NURSE | | | 200 `PageResult<TimelineEntryDTO>`, mới nhất trước, `size` tối đa 50 |
| `GET /api/v2/records/{id}/documents` | ADMIN, DOCTOR, NURSE | | | 200 `List<RecordDocumentDTO>` |
| `POST /api/v2/treatment-courses` | DOCTOR | treatment-course | `{patientId, problem, openingReason, recordId}` | 201 `TreatmentCourseDTO` |
| `GET /api/v2/patients/{patientId}/treatment-courses` | ADMIN, DOCTOR, NURSE | treatment-course | | 200 `List<TreatmentCourseDTO>` |
| `POST /api/v2/treatment-courses/{id}/visits` | DOCTOR | treatment-course | `{recordId}` | 200 `TreatmentCourseDTO` |
| `PUT /api/v2/treatment-courses/{id}/closure` | DOCTOR | treatment-course | `{decision, note}` | 200 `TreatmentCourseDTO` |
| `POST /api/v2/records/{id}/handovers` | DOCTOR | record-amendment | `{toDoctorId, reason, pendingItems}` | 201 `RecordHandoverDTO` |
| `PUT /api/v2/records/{id}/handovers/{handoverId}/acceptance` | DOCTOR | record-amendment | | 200 `RecordHandoverDTO` |
| `GET /api/v2/records/{id}/handovers` | ADMIN, DOCTOR, NURSE | record-amendment | | 200 `List<RecordHandoverDTO>` |
| `POST /api/v2/records/{id}/consultation-notes` | DOCTOR | record-amendment | `{opinion}` | 201 `ConsultationNoteDTO` |
| `GET /api/v2/records/{id}/consultation-notes` | ADMIN, DOCTOR, NURSE | record-amendment | | 200 `List<ConsultationNoteDTO>` |
| `POST /api/v2/records/{id}/amendments` | DOCTOR | record-amendment | `{section, amendedText, reason}` | 201 `RecordAmendmentDTO` |
| `PUT /api/v2/records/{id}/amendments/{amendmentId}/decision` | DOCTOR | record-amendment | `{decision, note}` | 200 `RecordAmendmentDTO` |
| `GET /api/v2/records/{id}/amendments` | ADMIN, DOCTOR, NURSE | record-amendment | | 200 `List<RecordAmendmentDTO>` |
| `POST /api/v2/records/{id}/amendment-assignments` | DOCTOR | record-amendment | `{assigneeStaffId, reason}` | 201 `AmendmentAssignmentDTO` |
| `GET /api/v2/amendments/pending` | DOCTOR | record-amendment | | 200 `List<RecordAmendmentDTO>` người gọi được duyệt, cũ nhất trước |
| `GET /api/v2/patients/{patientId}/allergies` | ADMIN, DOCTOR, NURSE | | | 200 `PatientAllergiesDTO` |
| `POST /api/v2/patients/{patientId}/allergies` | DOCTOR, NURSE | | `{substance, reaction, severity, sourceRecordId}` | 201 `PatientAllergyDTO` |
| `PUT /api/v2/patients/{patientId}/allergies/{allergyId}/status` | DOCTOR | | `{status, reason}` | 200 `PatientAllergyDTO` |
| `GET /api/v2/org/record-review-authority?actorAccountId=` (nội bộ, chỉ `clinical-service`) | hệ thống | | | 200 `RecordReviewAuthority(actorAccountId, staffId, canReviewRecords)`; Gateway chặn từ ngoài |

- Sáu mục nội dung: mỗi mục tối đa 4000 ký tự. `null` là giữ nguyên, chuỗi rỗng là xóa. Chỉ ghi được khi record `OPEN`, cùng guard với `symptoms`.
- `MedicalRecordDTO` thêm sáu mục trên và `responsibleDoctorId` (chỉ có khi khác `doctorId`).
- `TimelineEntryDTO(recordId, examinationDate, departmentId, doctorId, responsibleDoctorId, visitKind, status, disposition, previousRecordId, treatmentCourseIds)`. `visitKind`: `FOLLOW_UP` khi phiếu là `FOLLOW_UP`; `RETURN` khi có `previousRecordId` mà không phải tái khám; `NEW` khi có phiếu; vắng mặt với hồ sơ cũ không có phiếu. `treatmentCourseIds` vắng mặt khi flag tắt.
- `RecordDocumentDTO(type, referenceId, summary, attachedAt, afterConclusion)`. `type` là `LAB` hoặc `PRESCRIPTION`. `afterConclusion` là `true` khi `attachedAt` sau `completedAt` hoặc `closedAt`.
- `TreatmentCourseDTO(courseId, patientId, departmentId, problem, openingReason, responsibleDoctorId, status, recordIds, openedAt, lastVisitDate, reviewDue, closureDecision, closureNote, closedAt)`. `status`: `OPEN`, `CLOSED`. `decision`: `RESOLVED`, `TRANSFERRED`, `PATIENT_STOPPED`, `OTHER`. `reviewDue` là `true` khi `OPEN` và lượt cuối cách hôm nay hơn 180 ngày.
- `RecordHandoverDTO(handoverId, fromDoctorId, toDoctorId, reason, pendingItems, status, requestedAt, acceptedAt)`. `status`: `PENDING`, `ACCEPTED`.
- `ConsultationNoteDTO(noteId, authorStaffId, opinion, createdAt)`.
- `RecordAmendmentDTO(amendmentId, section, originalText, amendedText, reason, authorStaffId, assignedByStaffId, status, requestedAt, decidedByStaffId, decisionNote, decidedAt, contentHash)`. `section`: `SYMPTOMS`, `CHIEF_COMPLAINT`, `PRESENT_ILLNESS`, `PAST_HISTORY`, `ALLERGY_NOTE`, `PHYSICAL_EXAM`, `ADVICE`, `DISPOSITION_NOTE`, `CLOSURE_NOTE`, `DIAGNOSIS`. `status`: `PENDING`, `APPROVED`, `REJECTED`. `contentHash` là SHA-256 hex của `section + "\n" + originalText + "\n" + amendedText + "\n" + reason`, chỉ có khi `APPROVED`.
- `PatientAllergyDTO(allergyId, substance, reaction, severity, status, recordedByStaffId, recordedAt, sourceRecordId, statusReason, statusChangedByStaffId, statusChangedAt)`. `severity`: `MILD`, `MODERATE`, `SEVERE`, `UNKNOWN`. `status`: `ACTIVE`, `RESOLVED`, `ENTERED_IN_ERROR`. `substance` 1..200 ký tự, văn bản tự do; `reaction` tối đa 500.
- `PatientAllergiesDTO(active, inactive)`: hai danh sách `PatientAllergyDTO`. Danh sách `active` rỗng nghĩa là "Chưa ghi nhận dị ứng", không phải "Không dị ứng".
- `RecordCapabilitiesDTO` thêm `canHandover`, `canAcceptHandover`, `canAddConsultationNote`, `canAmend`, `canDecideAmendments`; vắng mặt khi flag `record-amendment` tắt.

Mã lỗi mới: `RECORD_CONTENT_INVALID`, `ALLERGY_INVALID`, `TREATMENT_COURSE_INVALID`, `TREATMENT_COURSE_CLOSED`, `HANDOVER_INVALID`, `HANDOVER_PENDING_EXISTS`, `AMENDMENT_INVALID`, `AMENDMENT_RECORD_OPEN`, `AMENDMENT_ALREADY_DECIDED` (đều `BusinessRuleException`, 422), `AMENDMENT_SELF_DECISION`, `AMENDMENT_AUTHOR_NOT_ELIGIBLE` (`ForbiddenOperationException`, 403).

## Lựa chọn của người viết kế hoạch, cần bạn xem lại

1. **Trưởng khoa là bác sĩ có `canManageSchedules = true` ở khoa của hồ sơ.** Giá trị này đã tính từ bổ nhiệm `department_head_appointment` (`JdbcSchedulingAuthorityRepository.java:50-53`), nên Core không phải sửa.
2. **Tiền sử và ghi chú dị ứng vẫn ghi theo từng lượt, nhưng có thêm sổ dị ứng cấp bệnh nhân do Clinical giữ** (sửa sau đối chiếu thực tế). Mẫu bệnh án có mục tiền sử và dị ứng trong từng bệnh án, còn HIS dùng thông tin dị ứng đã ghi để cảnh báo khi kê đơn; chỉ ghi theo lượt thì bác sĩ lượt sau không thấy. Sổ dị ứng: điều dưỡng hoặc bác sĩ thêm, chỉ bác sĩ đổi trạng thái kèm lý do, không xóa, giữ người và thời điểm. Hồ sơ hiện dị ứng đang hoạt động ở đầu trang, cạnh chỗ kê đơn. Chưa có danh mục dị nguyên mã hóa nên chưa tự đối chiếu với thuốc; form kê đơn ở feature Pharmacy chưa hiện sổ này, cần handoff cho người sở hữu Pharmacy web. Lab không nhận thêm dữ liệu; snapshot khi phát hành thuộc Đợt 6.
3. **Bàn giao chỉ trong cùng khoa, người nhận phải chấp nhận** (giữ sau đối chiếu). Chuyển người bệnh sang khoa khác dùng phiếu bàn giao chuyển khoa riêng; với ngoại trú, khám chuyên khoa khác là một lượt mới, Đợt 3 đã cho phép mở lượt khác khoa không cần lý do. Ý kiến của bác sĩ khoa khác đi qua hội chẩn (Task 5). Sau khi chấp nhận, người nhận là bác sĩ phụ trách: ghi hồ sơ, chỉ định, kê đơn, kết thúc lượt. Bác sĩ cũ chỉ còn đọc. `doctorId` của hồ sơ và của sự kiện hoàn tất vẫn là bác sĩ mở hồ sơ, nên Report không đếm hai lần. Prescription-context trả bác sĩ phụ trách hiện tại, vì đó là người được kê đơn.
4. **Chỉ đính chính hồ sơ đã kết thúc** (`COMPLETED`, `CLOSED_WITHOUT_CONCLUSION`). Hồ sơ đang mở thì sửa trực tiếp như hiện nay. Đính chính chẩn đoán là văn bản mô tả thay đổi, không sửa bảng chẩn đoán.
5. **Người gửi đính chính là bác sĩ phụ trách hồ sơ.** Nếu bác sĩ đó không còn hoạt động trong khoa (`StaffLookupPort.departmentOf` trả rỗng hoặc khác khoa), trưởng khoa phân công một bác sĩ khác trong khoa. Người được phân công gửi bằng danh tính của mình, bản đính chính ghi cả người phân công.
6. **Người duyệt là trưởng khoa của hồ sơ, hoặc người rà soát hồ sơ cấp bệnh viện; luôn khác người gửi** (sửa sau đối chiếu thực tế). Quy chế hồ sơ bệnh án điện tử thực tế có hai cấp trên bác sĩ: trưởng khoa kiểm tra và xác nhận, rồi tổ kiểm tra hồ sơ ở phòng Kế hoạch-Nghiệp vụ trả hồ sơ về khoa để sửa, Giám đốc hoặc người được ủy quyền ký trước khi lưu. Vì vậy trưởng khoa tự gửi đính chính thì không chờ trưởng khoa khác mà đưa lên người rà soát. Người rà soát là bác sĩ đang hoạt động được ADMIN cấp năng lực `MEDICAL_RECORD_REVIEW` (đại diện phòng Kế hoạch tổng hợp hoặc người được Giám đốc ủy quyền), có thể thu hồi, duyệt được hồ sơ mọi khoa. Vai trò ADMIN tự nó không duyệt được: ADMIN cấp quyền nhưng không phải bác sĩ nên không nhận được năng lực này. Trưởng khoa vẫn là người duyệt thường ngày; người rà soát thấy mọi bản chờ trong `GET /api/v2/amendments/pending`.
7. **Xác nhận nội bộ khi duyệt** lưu người duyệt, thời điểm và hash SHA-256 của nội dung. UI ghi "Đã xác nhận nội bộ", kèm 12 ký tự đầu của hash.
8. **Quá trình điều trị do bác sĩ mở từ một lượt và gắn thêm lượt thủ công.** Một lượt có thể thuộc nhiều quá trình. Lượt gắn vào phải cùng bệnh nhân, được phép khác khoa. Không tự đóng; quá 180 ngày không có lượt mới thì hiện "Cần rà soát". Người đóng là bác sĩ phụ trách quá trình hoặc trưởng khoa của quá trình.
9. **Hội chẩn là ghi chú chỉ thêm theo từng tác giả,** do bác sĩ đọc được hồ sơ thêm khi hồ sơ còn mở. Kết luận cuối vẫn do bác sĩ phụ trách ghi ở chẩn đoán.
10. **Không thêm bản in hay xuất hồ sơ ở v2.0.** Quy tắc cho lần thêm sau được ghi vào `docs/plans/03-contracts.md`.
11. **Bàn giao và hội chẩn đi chung flag `record-amendment`.** Lộ trình chỉ có hai flag cho Đợt 4; cả ba tính năng đều là bảng riêng không đụng nội dung gốc.

**Nguồn đối chiếu cho mục 2, 3, 6 (2026-11-11):**
- Quy chế hồ sơ bệnh án điện tử, Quyết định 159/QĐ-TTYT, TTYT Mỹ Tú, 26/9/2025 ([benhandientu.moh.gov.vn](https://benhandientu.moh.gov.vn)): phiếu đã ký không sửa được nếu chưa hủy ký; trưởng khoa kiểm tra, xác nhận rồi chuyển phòng Kế hoạch-Nghiệp vụ; tổ kiểm tra trả hồ sơ về khoa sửa trong 48 giờ; Giám đốc hoặc người được ủy quyền ký trước khi lưu.
- [Thông tư 13/2025/TT-BYT](https://thuvienphapluat.vn) về hồ sơ bệnh án điện tử, hiệu lực 21/7/2025, thay Thông tư 46/2018.
- [Đặc tả phiếu bàn giao người bệnh chuyển khoa (dành cho bác sĩ)](https://hsba.kcb.vn/rendered-reference-specifications/D32-PH-047.pdf), tham chiếu Quyết định 5937/QĐ-BYT phụ lục 5.
- [Tổng quan hệ thống về hỗ trợ quyết định cho dị ứng thuốc](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6231757/) và [SOP kê đơn trên HIS của NHS](https://wwl.nhs.uk/media/FOI/October%202023/9248%20-%20Medication%20Prescribing%20with%20the%20HIS%20System%20SOP%2026.pdf): dị ứng ghi ở cấp bệnh nhân và được xem trước khi kê đơn.

---

### Task 1: Nội dung khám có cấu trúc và bảng authority

**Files:**
- Create: `backend/clinical-service/src/main/resources/db/migration/V19__record_clinical_content.sql`, `.../domain/model/ClinicalContent.java`
- Modify: `MedicalRecord.java`, `MedicalRecordJpaEntity.java`, `MedicalRecordPersistenceAdapter.java`, `UpdateRecordRequest.java`, `MedicalRecordDTO.java`, `ClinicalDtoMapper.java`, `MedicalRecordApplicationService.java`, `ConsumerInboxPostgresTest.java`, `docs/plans/01-system-design.md`
- Test: `MedicalRecordContentTest.java`, `ExamLifecycleSchemaPostgresTest.java`, `MedicalRecordControllerTest.java`

**Interfaces:**
- Produces: `record ClinicalContent(String chiefComplaint, String presentIllness, String pastHistory, String allergyNote, String physicalExam, String advice)` với `ClinicalContent.EMPTY` và `ClinicalContent merge(ClinicalContent change)` (null giữ nguyên, rỗng thành null); `MedicalRecord.getContent()`; `void MedicalRecord.update(String symptoms, ClinicalContent change)`.

- [ ] **Step 1: Viết test đỏ** `contentSectionsAreStored`, `nullKeepsSectionEmptyClears`, `sectionOver4000IsRefused` (mã `RECORD_CONTENT_INVALID`), `closedRecordContentIsImmutable`, `oldUpdateWithOnlySymptomsKeepsContent`; schema `recordContentColumnsExist`; controller `updateAcceptsContentSections` và `dtoOmitsEmptySections`.
- [ ] **Step 2: Chạy, xác nhận đỏ.** `mvn -q -pl backend/clinical-service -am test -Dtest='MedicalRecordContentTest,ExamLifecycleSchemaPostgresTest,MedicalRecordControllerTest' -Dsurefire.failIfNoSpecifiedTests=false`. Expected: lỗi biên dịch.
- [ ] **Step 3: `V19`** thêm sáu cột `TEXT` nullable vào `medical_record` với `CHECK (length(x) <= 4000)`. Domain, adapter, request (constructor một tham số cũ được giữ), DTO (`NON_NULL`). Nâng ghim lên 19.
- [ ] **Step 4: Bảng authority** trong `01-system-design.md`: Core Patient giữ định danh, hành chính, BHYT; Clinical giữ nội dung khám theo lượt (sáu mục), sinh hiệu, chẩn đoán; Lab giữ kết quả. Ghi rõ `allergyNote` là ghi chú của lượt, sổ dị ứng cấp bệnh nhân thuộc Clinical (Task 7A).
- [ ] **Step 5: Chạy lại, xác nhận xanh.** Lệnh như Step 2.
- [ ] **Step 6: Commit** `feat(clinical): record structured examination content`.

### Task 2: Dòng thời gian và tài liệu của lượt

**Files:**
- Create: `application/service/PatientTimelineApplicationService.java`, `application/dto/response/TimelineEntryDTO.java`, `application/dto/response/RecordDocumentDTO.java`, `web/PatientTimelineController.java`, port `PatientTimelineReadPort.java` và adapter JDBC
- Modify: `ExternalResultRepositoryPort.java` và adapter, gateway route, filter, csv
- Test: `PatientTimelineApplicationServiceTest.java`, `PatientTimelinePostgresTest.java`, `PatientTimelineControllerTest.java`, `RecordDocumentsTest.java`

**Interfaces:**
- Produces: `PageResult<TimelineEntryDTO> timeline(UUID patientId, int page, int size, UUID departmentId, String status)`; `List<RecordDocumentDTO> documents(UUID recordId)`; port `List<AttachedDocument> findByRecord(UUID recordId)` với `record AttachedDocument(String type, UUID referenceId, String summary, Instant attachedAt)`.

- [ ] **Step 1: Viết test đỏ** `newestFirstWithDepartmentDoctorKindAndStatus`, `followUpRegistrationIsFollowUp`, `previousRecordWithoutFollowUpIsReturn`, `legacyRecordHasNoKind`, `filtersByDepartmentAndStatus`, `sizeIsCappedAt50`, `nurseSeesOnlyOwnDepartment` (cùng quy tắc `byPatient`), `deniedReaderGets403`, `responsibleDoctorShownOnlyWhenDifferent`; tài liệu `documentAttachedAfterCompletionIsMarked`, `documentBeforeCompletionIsNotMarked`, `documentsNeedRecordRead`; controller 401/403 cho CASHIER, PATIENT.
- [ ] **Step 2: Chạy, xác nhận đỏ.**
- [ ] **Step 3: Cài đặt.** Một câu SQL `medical_record LEFT JOIN outpatient_registration ON registration_id = appointment_id`, sắp `examination_date DESC, created_at DESC, record_id`. `treatmentCourseIds` để trống ở task này. Khai báo route, rule, csv.
- [ ] **Step 4: Chạy lại, xác nhận xanh;** chạy `RouteRoleContractTest`, `SchedulingRouteConfigurationTest`.
- [ ] **Step 5: Commit** `feat(clinical): add patient visit timeline and record documents`.

### Task 3: Quá trình điều trị

**Files:**
- Create: `V20__treatment_course.sql`, `domain/model/TreatmentCourse.java`, `domain/model/TreatmentCourseDecision.java`, `application/port/out/TreatmentCourseRepositoryPort.java` và adapter, `application/service/TreatmentCourseApplicationService.java`, `web/TreatmentCourseController.java`, request/response DTO
- Modify: `PatientTimelineReadPort` adapter (điền `treatmentCourseIds` khi flag bật), `application.yml` (`mediflow.v2.treatment-course`), gateway, `ConsumerInboxPostgresTest.java`
- Test: `TreatmentCourseTest.java`, `TreatmentCourseApplicationServiceTest.java`, `TreatmentCoursePostgresTest.java`, `TreatmentCourseControllerTest.java`

**Interfaces:**
- Produces: `TreatmentCourse.open(UUID patientId, UUID departmentId, String problem, String openingReason, UUID doctorId, UUID firstRecordId, Instant at)`, `addVisit(UUID recordId, UUID staffId, Instant at)`, `close(TreatmentCourseDecision, String note, UUID staffId, Instant at)`, `boolean reviewDue(LocalDate today, LocalDate lastVisitDate)`; service `open`, `forPatient`, `addVisit`, `close`, mỗi lệnh ghi trả `IdempotentCommandPort.Result<TreatmentCourseDTO>`.

- [ ] **Step 1: Viết test đỏ** `openNeedsProblemReasonAndFirstVisit`, `departmentComesFromFirstVisit`, `visitOfAnotherPatientIsRefused`, `visitFromAnotherDepartmentIsAllowed`, `sameVisitTwiceIsIdempotent`, `oneVisitMayBelongToTwoCourses`, `twoOpenCoursesForOnePatient`, `closeNeedsDecisionAndNote`, `closedCourseRefusesVisits` (`TREATMENT_COURSE_CLOSED`), `onlyResponsibleDoctorOrHeadCloses`, `noAutomaticClosure`, `reviewDueAfter180Days`, `reviewNotDueAt180Days`, `courseEmitsNoEvent` (outbox không được gọi), `timelineListsCourseIds`; schema `visitUniquePerCourse`; controller flag tắt thì 404, 401/403.
- [ ] **Step 2: Chạy, xác nhận đỏ.**
- [ ] **Step 3: `V20`** tạo `treatment_course` (khóa `course_id`, `patient_id`, `department_id`, `problem VARCHAR(500)`, `opening_reason VARCHAR(1000)`, `responsible_doctor_id`, `status`, các cột đóng với CHECK đủ-hoặc-không) và `treatment_course_visit(course_id, record_id FK medical_record, added_by_staff_id, added_at, PRIMARY KEY(course_id, record_id))`. Không backfill. Nâng ghim lên 20.
- [ ] **Step 4: Cài đặt service, controller, route, rule, csv.** Người mở phải là bác sĩ phụ trách của lượt đầu. Người gắn lượt phải đọc được lượt đó.
- [ ] **Step 5: Chạy lại, xác nhận xanh.**
- [ ] **Step 6: Commit** `feat(clinical): group visits into treatment courses`.

### Task 4: Bàn giao và bác sĩ phụ trách

**Files:**
- Create: `V21__record_collaboration.sql`, `domain/model/RecordHandover.java`, port và adapter, `application/service/RecordHandoverApplicationService.java`, `web/RecordHandoverController.java`, DTO
- Modify: `MedicalRecord.java` (thêm `responsibleDoctorId`), adapter, mapper, mọi guard ghi theo hồ sơ (`ClinicalCareFinanceApplicationService.java:188,327,351,384`, `ClinicalLabOrderApplicationService.java:109`, `MedicalRecordApplicationService.java:207,224`), `RecordAccessApplicationService.java`, `RecordReadPolicy` (căn cứ được giao gồm bác sĩ phụ trách), `findOpenVisitsByDoctor`, `PrescriptionContextReadAdapter.java`, `application.yml` (`mediflow.v2.record-amendment`), gateway
- Test: `RecordHandoverTest.java`, `RecordHandoverApplicationServiceTest.java`, `RecordCollaborationPostgresTest.java`, `RecordHandoverControllerTest.java`, `PrescriptionContextPostgresTest.java`, Pharmacy `PrescribingContextServiceTest.java`

**Interfaces:**
- Produces: `UUID MedicalRecord.responsibleDoctorId()` (người nhận bàn giao, mặc định `doctorId`); `void MedicalRecord.handOverTo(UUID doctorId, Instant at)`; service `request`, `accept`, `list`.

- [ ] **Step 1: Viết test đỏ** `responsibleDoctorRequestsHandover`, `receiverMustBeSameDepartment` (`HANDOVER_INVALID`), `selfHandoverIsRefused`, `reasonAndPendingItemsRequired`, `onlyOnePendingHandover` (`HANDOVER_PENDING_EXISTS`), `onlyReceiverAccepts`, `acceptingMakesReceiverResponsible`, `previousDoctorCannotWriteAfterHandover`, `receiverCanCompleteAndOrderLab`, `closedRecordCannotBeHandedOver`, `recordDoctorIdUnchangedAfterHandover`, `completedEventKeepsOpeningDoctor`, `receiverSeesVisitInOwnOpenList`, `prescriptionContextReturnsResponsibleDoctor`; Pharmacy `receiverPrescribesAfterHandover`, `previousDoctorCannotPrescribeAfterHandover`; controller flag tắt 404, 401/403.
- [ ] **Step 2: Chạy, xác nhận đỏ.**
- [ ] **Step 3: `V21`** thêm `responsible_doctor_id UUID` vào `medical_record`; tạo `record_handover(handover_id, record_id FK, from_doctor_id, to_doctor_id, reason VARCHAR(500), pending_items VARCHAR(2000), status, requested_at, accepted_at)` với unique một `PENDING` mỗi hồ sơ; tạo `consultation_note` dùng ở Task 5. Prescription-context đọc `COALESCE(r.responsible_doctor_id, r.doctor_id)`. Nâng ghim lên 21.
- [ ] **Step 4: Đổi guard ghi** sang `record.responsibleDoctorId()` và khoa của hồ sơ. Guard bắt đầu khám (`:164`) giữ bác sĩ của appointment.
- [ ] **Step 5: Chạy lại, xác nhận xanh;** chạy cả `ExamClosureTest`, `ClinicalLabOrderApplicationServiceTest`, `MedicalRecordApplicationServiceTest` và test Pharmacy.
- [ ] **Step 6: Commit** `feat(clinical): hand over an open visit to another doctor`.

### Task 5: Ghi chú hội chẩn

**Files:**
- Create: `domain/model/ConsultationNote.java`, port và adapter, `application/service/ConsultationNoteApplicationService.java`, `web/ConsultationNoteController.java`, DTO
- Modify: gateway
- Test: `ConsultationNoteApplicationServiceTest.java`, `ConsultationNoteControllerTest.java`

**Interfaces:**
- Produces: service `add(UUID recordId, ConsultationNoteRequest, String key)`, `list(UUID recordId)`.

- [ ] **Step 1: Viết test đỏ** `doctorWhoCanReadAddsNote`, `noteKeepsRealAuthor`, `noteIsNeverEdited` (không có port sửa), `closedRecordRefusesNotes`, `opinionRequiredMax4000`, `nurseCannotAdd`, `notesOrderedOldestFirst`, `noteDoesNotChangeDiagnosis`; controller flag tắt 404, 401/403.
- [ ] **Step 2: Chạy, xác nhận đỏ.**
- [ ] **Step 3: Cài đặt.** Bảng `consultation_note(note_id, record_id FK, author_staff_id, opinion TEXT CHECK 1..4000, created_at)` đã tạo ở `V21`. Route, rule, csv.
- [ ] **Step 4: Chạy lại, xác nhận xanh.**
- [ ] **Step 5: Commit** `feat(clinical): keep consultation opinions per author`.

### Task 6A: Năng lực rà soát hồ sơ cấp bệnh viện (Core Organization)

**Files:**
- Create: `backend/core-service/src/main/resources/db/organization-migration/V22__medical_record_review_capability.sql`, `.../organization/application/dto/response/RecordReviewAuthority.java`; Clinical `application/port/out/RecordReviewAuthorityPort.java`, `infrastructure/client/ClinicalRecordReviewAuthorityAdapter.java`
- Modify: `StaffCapabilityGrantService.java`, `StaffCapabilityGrantController.java`, gateway `SchedulingRouteAuthorizationTest.java` (thêm path vào danh sách bị chặn), cấu hình service-principal của Core nếu cần cho `clinical-service`
- Test: `StaffCapabilityGrantServiceTest.java`, `StaffCapabilityGrantControllerTest.java`, `StaffCapabilityGrantPostgresTest.java` hoặc test schema Organization tương ứng, Clinical `ClinicalRecordReviewAuthorityAdapterTest.java`

**Interfaces:**
- Produces: hằng `MEDICAL_RECORD_REVIEW`; `RecordReviewAuthority StaffCapabilityGrantService.recordReviewAuthority(UUID actorAccountId)`; `GET /api/v2/org/record-review-authority?actorAccountId=` với `@PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE') and principal == 'clinical-service'")`, `Cache-Control: no-store`; Clinical `boolean RecordReviewAuthorityPort.canReviewRecords(UUID accountId, UUID correlationId)`, hết giờ hoặc lỗi thì ném lỗi tạm thời, không trả `false` im lặng.

- [ ] **Step 1: Viết test đỏ** `grantsRecordReviewToActiveDoctor`, `recordReviewRefusedForNurseLabTechOrInactiveStaff`, `labReleaseStillOnlyForLabTech`, `oneActiveGrantPerStaffAndCapability`, `revokedGrantGivesNoAuthority`, `authorityEndpointOnlyForClinicalService` (principal `lab-service` bị 403), `checkConstraintAcceptsRecordReview`; gateway `internalAndUnknownRoutesRemainDeniedAtThePublicBoundary` có `/api/v2/org/record-review-authority`; Clinical adapter `timeoutIsNotTreatedAsNoAuthority`.
- [ ] **Step 2: Chạy, xác nhận đỏ.** `mvn -q -pl backend/core-service,backend/clinical-service,backend/gateway -am test -Dtest='StaffCapabilityGrant*,ClinicalRecordReviewAuthorityAdapterTest,SchedulingRouteAuthorizationTest' -Dsurefire.failIfNoSpecifiedTests=false`. Expected: lỗi biên dịch hoặc fail.
- [ ] **Step 3: `V22`** đổi CHECK của `staff_capability_grant.capability` thành `IN ('LAB_RESULT_RELEASE', 'MEDICAL_RECORD_REVIEW')`. Service dùng bảng năng lực sang vai trò được nhận: `LAB_RESULT_RELEASE` → LAB_TECH, `MEDICAL_RECORD_REVIEW` → DOCTOR; chỉ ADMIN cấp và thu hồi như hiện nay. Endpoint nội bộ theo mẫu `lab-release-authority`. Adapter Clinical theo mẫu `ClinicalSchedulingAuthorityAdapter`.
- [ ] **Step 4: Chạy lại, xác nhận xanh;** chạy toàn bộ `backend/core-service`.
- [ ] **Step 5: Commit** `feat(org): grant hospital-level medical record review`.

### Task 6: Đính chính có duyệt

**Files:**
- Create: `V22__record_amendment.sql`, `domain/model/RecordAmendment.java`, `domain/model/AmendmentSection.java`, `domain/model/AmendmentAssignment.java`, port và adapter, `application/service/RecordAmendmentApplicationService.java`, `web/RecordAmendmentController.java`, DTO
- Modify: gateway, `ConsumerInboxPostgresTest.java`
- Test: `RecordAmendmentTest.java`, `RecordAmendmentApplicationServiceTest.java`, `RecordAmendmentPostgresTest.java`, `RecordAmendmentControllerTest.java`

**Interfaces:**
- Consumes: `responsibleDoctorId()` từ Task 4; `SchedulingAuthorityPort.authority(accountId, departmentId, correlationId).canManageSchedules()` cho trưởng khoa; `RecordReviewAuthorityPort.canReviewRecords(UUID accountId, UUID correlationId)` từ Task 6A.
- Produces: `RecordAmendment.request(UUID recordId, AmendmentSection, String originalText, String amendedText, String reason, UUID authorStaffId, UUID assignedByStaffId, Instant at)`, `approve(UUID staffId, String note, Instant at)`, `reject(UUID staffId, String note, Instant at)`, `String contentHash()`.

- [ ] **Step 1: Viết test đỏ** `openRecordIsNotAmended` (`AMENDMENT_RECORD_OPEN`), `amendmentSnapshotsOriginalText`, `diagnosisSectionSnapshotsDiagnosisList`, `reasonAndTextRequired`, `onlyResponsibleDoctorRequests`, `departedAuthorNeedsAssignment` (`AMENDMENT_AUTHOR_NOT_ELIGIBLE`), `headAssignsDoctorWhenAuthorDeparted`, `assignmentRefusedWhileAuthorActive`, `assigneeRequestsWithOwnIdentity`, `headApproves`, `authorCannotDecideOwnAmendment` (`AMENDMENT_SELF_DECISION`), `recordReviewerApprovesHeadAuthoredAmendment`, `recordReviewerFromOtherDepartmentMayDecide`, `reviewerWhoIsAuthorCannotDecide`, `revokedReviewerCannotDecide`, `adminWithoutCapabilityCannotDecide`, `otherDepartmentHeadCannotDecide`, `pendingQueueListsOnlyWhatCallerMayDecide` (trưởng khoa thấy khoa mình trừ bản tự gửi; người rà soát thấy mọi khoa trừ bản tự gửi), `decisionIsFinal` (`AMENDMENT_ALREADY_DECIDED`), `approvalStoresHashActorAndTime`, `approvedAmendmentLeavesOriginalAndContractFieldsUnchanged` (nội dung, `doctorId`, `appointmentId`, `completedAt` không đổi; outbox không được gọi), `approvedAmendmentTouchesNoAttachedResult`, `authorityTimeoutRefusesDecision`, `reviewAuthorityTimeoutRefusesDecision`; schema `decisionColumnsAllOrNothing`; controller flag tắt 404, 401/403.
- [ ] **Step 2: Chạy, xác nhận đỏ.**
- [ ] **Step 3: `V22`** tạo `record_amendment` (đủ cột của DTO, `original_text TEXT`, `amended_text TEXT CHECK 1..4000`, `reason VARCHAR(1000)`, CHECK cột quyết định đủ-hoặc-không, `content_hash CHAR(64)` chỉ khi `APPROVED`) và `record_amendment_assignment(assignment_id, record_id FK, assignee_staff_id, assigned_by_staff_id, reason, created_at)`. Nâng ghim lên 22.
- [ ] **Step 4: Cài đặt.** Khóa hồ sơ khi tạo đính chính để snapshot nhất quán. Quyết định khóa dòng đính chính. Route, rule, csv.
- [ ] **Step 5: Chạy lại, xác nhận xanh.**
- [ ] **Step 6: Commit** `feat(clinical): amend a finished record through department or record review`.

### Task 7: Capability và khóa ranh giới Pharmacy, Report

**Files:**
- Modify: `RecordCapabilitiesDTO.java`, `RecordAccessApplicationService.java`, `docs/plans/03-contracts.md`
- Test: `RecordCapabilitiesTest.java`, Report `OutpatientReportFactReceiverTest.java`, Pharmacy `CarePrescriptionCreationServiceTest.java`

- [ ] **Step 1: Viết test đỏ** `capabilitiesAbsentWhenAmendmentFlagOff`, `responsibleDoctorCanHandoverAndAddNote`, `receiverOfPendingHandoverCanAccept`, `finishedRecordAuthorCanAmend`, `headCanDecideAmendments`, `recordReviewerCanDecideAmendments`, `nurseHasNoCollaborationCapability`; Report `sameRecordCompletionDeliveredTwiceCountsOnce`; Pharmacy `prescriptionKeepsRecordIdNotCourseId`. Test Report hoặc Pharmacy nào xanh ngay là test khóa hành vi có sẵn: ghi vào ledger, không coi là lỗi.
- [ ] **Step 2: Chạy, xác nhận đỏ ở phần capability.**
- [ ] **Step 3: Cài đặt capability.** Ghi vào `03-contracts.md`: quá trình, bàn giao, hội chẩn, đính chính không phát sự kiện; đơn thuốc giữ `recordId`; khi đính chính ảnh hưởng điều trị, bác sĩ kê đơn thay thế theo quy trình Pharmacy, hệ thống không tự sửa đơn hay hoàn kho; quy tắc in/xuất (VIEW-03) cho lần thêm sau: tách bản một lượt với lịch sử, giữ tác giả, thời điểm, đính chính, kiểm quyền xuất riêng.
- [ ] **Step 4: Chạy lại, xác nhận xanh;** chạy toàn bộ `backend/clinical-service`, `backend/gateway`, `backend/pharmacy-service`, `backend/report-service`.
- [ ] **Step 5: Commit** `feat(clinical): expose collaboration capabilities and pin boundaries`.

### Task 7A: Sổ dị ứng cấp bệnh nhân

**Files:**
- Create: `V23__patient_allergy.sql`, `domain/model/PatientAllergy.java`, `domain/model/AllergySeverity.java`, `domain/model/AllergyStatus.java`, port và adapter, `application/service/PatientAllergyApplicationService.java`, `web/PatientAllergyController.java`, DTO
- Modify: gateway route, filter, csv; `ConsumerInboxPostgresTest.java`; `docs/plans/01-system-design.md` (bảng authority)
- Test: `PatientAllergyTest.java`, `PatientAllergyApplicationServiceTest.java`, `PatientAllergyPostgresTest.java`, `PatientAllergyControllerTest.java`

**Interfaces:**
- Produces: service `list(UUID patientId)`, `add(UUID patientId, AddAllergyRequest, String key)`, `changeStatus(UUID patientId, UUID allergyId, AllergyStatusRequest, String key)`; đọc qua `RecordReadGuard.requirePatient`.

- [ ] **Step 1: Viết test đỏ** `nurseAndDoctorAddAllergy`, `substanceRequiredMax200` (`ALLERGY_INVALID`), `sourceRecordMustBelongToPatient`, `onlyDoctorChangesStatus`, `statusChangeNeedsReason`, `allergyIsNeverDeleted` (không có port xóa), `enteredInErrorMovesToInactive`, `activeListedFirstNewestFirst`, `nurseOfAnotherDepartmentCannotRead` (theo `requirePatient`), `duplicateActiveSubstanceIsRefused` (so khớp không phân biệt hoa thường, bỏ khoảng trắng thừa), `addReplaysWithSameKey`; schema `allergyStatusColumnsAllOrNothing`; controller 401/403.
- [ ] **Step 2: Chạy, xác nhận đỏ.**
- [ ] **Step 3: `V23`** tạo `patient_allergy(allergy_id, patient_id, substance VARCHAR(200), substance_key VARCHAR(200), reaction VARCHAR(500), severity, status, recorded_by_staff_id, recorded_at, source_record_id FK nullable, status_reason VARCHAR(500), status_changed_by_staff_id, status_changed_at)` với unique một `ACTIVE` cho mỗi `(patient_id, substance_key)`. Nâng ghim lên 23. Route, rule, csv.
- [ ] **Step 4: Chạy lại, xác nhận xanh;** chạy toàn bộ `backend/clinical-service` và `backend/gateway`.
- [ ] **Step 5: Commit** `feat(clinical): keep a patient-level allergy list`.

### Task 8: Web — dòng thời gian bệnh nhân

**Files:**
- Create: `frontend/src/features/medical-record/components/PatientTimeline.tsx` và test
- Modify: `features/medical-record/api.ts`, `types.ts`, `app/(dashboard)/records/page.tsx`
- Test: `PatientTimeline.test.tsx`, `api.test.ts`

- [ ] **Step 1: Viết test đỏ** `showsDateDepartmentDoctorKindAndStatus`, `returnVisitLinksToItsSource`, `filtersByDepartmentAndStatus`, `pagesThroughResults`, `loadingEmptyErrorAndDenied` (403 hiện "Bạn không có quyền xem hồ sơ của bệnh nhân này", 503 hiện lỗi và "Thử lại", danh sách rỗng hiện "Chưa có lượt khám"), `changingPatientDropsStaleResponse`, `timelineOffersNoEditAction`.
- [ ] **Step 2: Chạy, xác nhận đỏ.** `pnpm exec vitest run src/features/medical-record`.
- [ ] **Step 3: Cài đặt.** Tên khoa và bác sĩ lấy qua cách `MedicalRecordTable` đang dùng. Nhãn loại lượt: Khám mới, Tái khám, Quay lại, Không rõ. Thay `MedicalRecordTable` ở trang records.
- [ ] **Step 4: Chạy lại, xác nhận xanh;** `pnpm run typecheck`, `pnpm run lint`.
- [ ] **Step 5: Commit** `feat(web): show the patient visit timeline`.

### Task 9: Web — nội dung khám và tài liệu của lượt

**Files:**
- Create: `components/RecordDocumentsPanel.tsx`, `components/ClinicalContentFields.tsx`, `components/PatientAllergyPanel.tsx` và test
- Modify: `EditMedicalRecordForm.tsx`, `MedicalRecordDetail.tsx`, `ExaminationRecordLink.tsx` hoặc phần hiện lượt trước, `api.ts`, `types.ts`
- Test: `RecordDocumentsPanel.test.tsx`, `MedicalRecordForms.test.tsx`, `MedicalRecordDetail.test.tsx`

- [ ] **Step 1: Viết test đỏ** `editSendsOnlyChangedSections`, `detailShowsSectionsInOrder`, `emptySectionsAreHidden`, `documentsShowTypeTimeAndSourceLink`, `documentAfterConclusionIsLabelled` ("Bổ sung sau khi kết thúc"), `documentsOutageShowsUnavailableNotEmpty`, `documentsDeniedShowsNoPermission`, `previousVisitIsMarkedReferenceOnly` ("Lượt trước — chỉ tham khảo"), `previousVisitOffersNoCopy`; sổ dị ứng `activeAllergiesShownAtTopOfRecord`, `noActiveAllergyReadsNotRecordedNotNone` ("Chưa ghi nhận dị ứng"), `allergyOutageShowsUnavailableNotEmpty`, `nurseAddsAllergy`, `doctorMarksEnteredInErrorWithReason`, `inactiveAllergiesCollapsed`.
- [ ] **Step 2: Chạy, xác nhận đỏ.**
- [ ] **Step 3: Cài đặt.** Tài liệu là danh sách chỉ đọc; link Lab và đơn thuốc trỏ về trang nguồn có sẵn, do route truyền vào nếu thuộc feature khác.
- [ ] **Step 4: Chạy lại, xác nhận xanh;** typecheck, lint.
- [ ] **Step 5: Commit** `feat(web): structured examination content and visit documents`.

### Task 10: Web — quá trình điều trị, bàn giao, hội chẩn, đính chính

**Files:**
- Create: `components/TreatmentCoursesPanel.tsx`, `components/RecordHandoverPanel.tsx`, `components/ConsultationNotesPanel.tsx`, `components/RecordAmendmentsPanel.tsx` và test
- Modify: `PatientTimeline.tsx` (nhóm theo quá trình), `MedicalRecordDetail.tsx`, `api.ts`, `types.ts`
- Test: bốn file test tương ứng

- [ ] **Step 1: Viết test đỏ** `coursesHiddenWhenFeatureOff` (404), `openCourseFromVisit`, `addVisitToCourse`, `reviewDueIsShown` ("Cần rà soát"), `closeCourseNeedsDecisionAndNote`, `handoverNeedsReceiverReasonAndPendingItems`, `receiverAcceptsHandover`, `formerDoctorSeesReadOnlyAfterHandover`, `notesShowAuthorAndTime`, `noteFormOnlyWithCapability`, `amendmentShowsOriginalBesideAmended`, `amendmentNeedsReasonAndText`, `headDecidesAmendment`, `authorSeesNoDecisionButtons`, `approvedAmendmentShowsInternalConfirmation` ("Đã xác nhận nội bộ" và 12 ký tự hash; không có chữ "chữ ký"), `everyCommandSendsIdempotencyKey`, `uncertainOutcomeKeepsKeyForRetry`, và bốn trạng thái cho mỗi panel.
- [ ] **Step 2: Chạy, xác nhận đỏ.**
- [ ] **Step 3: Cài đặt** theo mẫu `CloseRecordForm` (`prepareCommandIntent`, `useCommand`). Nút hiện theo capability.
- [ ] **Step 4: Chạy lại, xác nhận xanh;** toàn bộ `pnpm exec vitest run`, typecheck, lint.
- [ ] **Step 5: Commit** `feat(web): treatment courses, handover, consultation and amendments`.

### Task 11: Mobile — dòng thời gian, nội dung khám, tài liệu

**Files:**
- Create: `mobile/lib/features/medical_record/domain/patient_timeline.dart`, `data/gateway_patient_timeline_repository.dart`, `presentation/patient_timeline_section.dart`, `presentation/record_documents_section.dart`
- Modify: `domain/medical_record.dart`, `data/medical_record_dto.dart`, `presentation/medical_record_page.dart`, `app/dependencies.dart`
- Test: `mobile/test/features/medical_record/patient_timeline_test.dart`, `record_content_test.dart`

- [ ] **Step 1: Viết test đỏ** tương đương Task 8 và 9: timeline có khoa, bác sĩ, loại lượt, trạng thái, lọc, trang tiếp; bốn trạng thái; dto đọc sáu mục và `responsibleDoctorId`; sửa chỉ gửi mục đổi; tài liệu bổ sung sau khi kết thúc có nhãn; lỗi nguồn không hiện rỗng; lượt trước ghi "chỉ tham khảo"; sổ dị ứng hiện ở đầu hồ sơ, rỗng ghi "Chưa ghi nhận dị ứng", lỗi nguồn không hiện rỗng; gateway gọi đúng path.
- [ ] **Step 2: Chạy, xác nhận đỏ.** `flutter test test/features/medical_record`.
- [ ] **Step 3: Cài đặt** với provider nullable như `examLifecycleRepositoryProvider`, override ở `app/dependencies.dart`.
- [ ] **Step 4: Chạy lại, xác nhận xanh;** `flutter analyze` không thêm vấn đề.
- [ ] **Step 5: Commit** `feat(mobile): visit timeline, structured content and documents`.

### Task 12: Mobile — quá trình điều trị, bàn giao, hội chẩn, đính chính

**Files:**
- Create: `domain/record_collaboration.dart`, `data/gateway_record_collaboration_repository.dart`, `presentation/treatment_courses_section.dart`, `presentation/record_collaboration_section.dart`
- Modify: `presentation/medical_record_page.dart`, `app/dependencies.dart`
- Test: `mobile/test/features/medical_record/record_collaboration_test.dart`

- [ ] **Step 1: Viết test đỏ** tương đương Task 10, gồm `featureOffHidesSections`, key idempotency giữ nguyên khi gửi lại sau lỗi mạng, "Đã xác nhận nội bộ" không có chữ "chữ ký", bác sĩ cũ chỉ đọc sau bàn giao, người rà soát thấy danh sách chờ duyệt.
- [ ] **Step 2: Chạy, xác nhận đỏ.**
- [ ] **Step 3: Cài đặt.**
- [ ] **Step 4: Chạy lại, xác nhận xanh;** toàn bộ `flutter test`, `flutter analyze`.
- [ ] **Step 5: Commit** `feat(mobile): treatment courses, handover, consultation and amendments`.

### Task 13: Nghiệm thu

**Files:**
- Create: `docs/plans/26-v2-wave-4-acceptance.md`
- Modify: `docs/known-limitations/03-clinical-service-improvement-checklist.md` (ghi chú thực hiện cho các mã của đợt), `docs/plans/02-business-flows.md` (quá trình điều trị, bàn giao, đính chính)

- [ ] **Step 1: Chạy toàn bộ test** backend, web (riêng), mobile, typecheck, lint, analyze. Ghi số.
- [ ] **Step 2: Nghiệm thu qua Gateway trên DB sạch,** bật hai flag, tài khoản demo thật:
  - ghi sáu mục nội dung rồi đọc lại;
  - dòng thời gian có khoa, bác sĩ, loại lượt; điều dưỡng chỉ thấy khoa mình;
  - mở quá trình, gắn lượt thứ hai, gắn lượt của bệnh nhân khác bị từ chối, đóng có quyết định;
  - bàn giao, người nhận chấp nhận, bác sĩ cũ ghi bị 403, người nhận kê đơn qua Pharmacy thành công;
  - hai bác sĩ ghi hội chẩn, mỗi ghi chú đúng tác giả;
  - đính chính hồ sơ đã hoàn tất: bản gốc không đổi, người gửi tự duyệt bị 403, trưởng khoa duyệt có hash;
  - trưởng khoa tự gửi đính chính: ADMIN duyệt bị 403; ADMIN cấp `MEDICAL_RECORD_REVIEW` cho một bác sĩ khoa khác, bác sĩ đó duyệt được; thu hồi xong thì bị 403;
  - điều dưỡng thêm dị ứng ở lượt này, bác sĩ mở lượt sau thấy ngay ở đầu hồ sơ; đánh dấu nhập nhầm cần lý do và vẫn còn trong lịch sử;
  - kết quả Lab về sau khi hoàn tất hiện "bổ sung sau khi kết thúc";
  - mỗi lượt vẫn đúng một dòng phí `EXAM`, `careEpisodeId` của chỉ định Lab không đổi;
  - tắt flag thì endpoint mới trả 404 và màn hình ẩn phần tương ứng.
- [ ] **Step 3: Ghi tài liệu** kết quả, giới hạn của bằng chứng và cấu hình flag.
- [ ] **Step 4: Commit** `docs(v2): record wave 4 acceptance`.
