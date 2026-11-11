# MediFlow v2.0 Đợt 4 — Nghiệm thu cấu trúc hồ sơ, quá trình điều trị và đính chính

Ngày chạy: 2026-11-11 (đồng hồ dịch vụ lùi 12 giờ). Nhánh: `develop/mediflow-v2.0`.
Kế hoạch: [`2026-11-11-v2-wave-4-record-structure.md`](../superpowers/plans/2026-11-11-v2-wave-4-record-structure.md).

## Cờ

| Cờ | Mặc định | Phạm vi |
|---|---|---|
| `mediflow.v2.treatment-course` (`MEDIFLOW_V2_TREATMENTCOURSE`) | `false` | Quá trình điều trị |
| `mediflow.v2.record-amendment` (`MEDIFLOW_V2_RECORDAMENDMENT`) | `false` | Bàn giao, hội chẩn, đính chính, năm cờ cộng tác trong `capabilities` |

Dòng thời gian, nội dung khám có cấu trúc, danh sách tài liệu, sổ dị ứng và năng lực `MEDICAL_RECORD_REVIEW`
của Organization không có cờ.

## Test tự động

| Bộ | Kết quả |
|---|---|
| Backend (`mvn test`, toàn bộ module) | 9298 test, 1 lỗi: `LabTestChargeConfigurationTest.intake_requiresBothExplicitGates[4]`, lỗi nền đã ghi ở Đợt 3 |
| Web (`vitest run`) | 1574/1575. `LabDetail.test.tsx` "does not let an old pending mutation…" hỏng khi chạy cả bộ; chạy riêng file 23/23 |
| Typecheck web | Sạch |
| Lint web | 0 lỗi |
| Mobile (`flutter test`) | 740/740 |
| `flutter analyze` | 3 info có từ trước, không thêm vấn đề mới |

## Nghiệm thu qua Gateway trên DB sạch

Compose project riêng `mediflow-w2`, `down -v` rồi dựng lại, seed demo, bật hai cờ. Mọi lệnh nghiệp vụ đi qua Gateway
với tài khoản demo thật. Sau lượt sửa: bật cờ 23/23; sau đó khởi động lại Clinical không có cờ, 2/2.

| Kịch bản | Kết quả |
|---|---|
| Ghi sáu mục nội dung rồi đọc lại | 200, đúng cả sáu mục và triệu chứng |
| Dòng thời gian có khoa, bác sĩ, loại lượt | 200, `visitKind`, `departmentId`, `doctorId` đúng |
| Điều dưỡng khoa Ngoại xem dòng thời gian | Chỉ có lượt khoa Ngoại |
| Mở quá trình, gắn lượt thứ hai | 201, 200, `recordIds` có lượt thứ hai |
| Gắn lượt của bệnh nhân khác | 422 `TREATMENT_COURSE_INVALID` |
| Đóng không có quyết định, rồi có quyết định | 400; 200 `RESOLVED` |
| Bàn giao, người nhận chấp nhận | 201, 200; `responsibleDoctorId` là người nhận, `doctorId` vẫn là bác sĩ mở |
| Bác sĩ cũ ghi, người nhận ghi | 403 `CLINICAL_DOCTOR_NOT_ASSIGNED`; 200 |
| Người nhận kê đơn ngoại trú qua Pharmacy | 201 |
| Người nhận hoàn tất lượt | 200 (trước lượt sửa: 404, xem dưới) |
| `careEpisodeId` của chỉ định Lab sau bàn giao | Không đổi, vẫn là mã lượt |
| Hai bác sĩ ghi hội chẩn | 201, 201; mỗi ghi chú đúng tác giả |
| Gửi đính chính hồ sơ đã hoàn tất | 201; cột gốc trong DB không đổi |
| Người gửi tự duyệt | 403 `AMENDMENT_SELF_DECISION` |
| Trưởng khoa duyệt | 200, có `contentHash` |
| Trưởng khoa tự gửi, ADMIN duyệt | 403 `AUTH_FORBIDDEN` |
| Bác sĩ khoa khác chưa được cấp quyền duyệt | 403 `AMENDMENT_DECISION_DENIED` |
| ADMIN cấp `MEDICAL_RECORD_REVIEW`, bác sĩ đó duyệt | 201, 200 |
| Thu hồi quyền rồi duyệt đính chính khác | 200; 403 `AMENDMENT_DECISION_DENIED` |
| Điều dưỡng thêm dị ứng, bác sĩ lượt sau xem | 201; có trong `active` |
| Đánh dấu nhập nhầm thiếu lý do, rồi có lý do | 422 `ALLERGY_INVALID`; 200, vẫn còn trong danh sách với `ENTERED_IN_ERROR` |
| Tài liệu Lab của lượt | Có kết quả Lab; kết quả Lab trả trước khi hoàn tất không bị đánh dấu `afterConclusion` |
| Phí khám | Mỗi lượt đúng 1 dòng `EXAM` trong `charge` |
| Tắt cờ: handovers, consultation-notes, amendments, amendments/pending, treatment-courses | Cả năm 404 |
| Tắt cờ: `capabilities` | 200, không có năm cờ cộng tác |

## Sửa sau review độc lập

| Vấn đề | Cách sửa | Bằng chứng |
|---|---|---|
| Người nhận bàn giao không hoàn tất hay đóng được lượt: phiếu đăng ký vẫn đòi bác sĩ của buổi khám (404). Lần chạy đầu không kiểm mã trả về nên không thấy | Phiếu đăng ký kết thúc theo bác sĩ phụ trách của hồ sơ | `StartExamRoomTicketTest.handoverReceiverCompletesTheRegistration`, `sessionDoctorCannotCompleteAfterHandingOver`; Gateway 200 |
| Sửa riêng một mục nội dung (web, mobile) xóa mất triệu chứng | `symptoms` theo quy tắc chung: `null` giữ, chuỗi rỗng xóa; web và mobile gửi `""` khi xóa | `MedicalRecordContentTest.nullSymptomsKeepWhenOnlyASectionChanges`, `emptySymptomsClear`; test web và mobile |
| Bàn giao gửi nhầm người thì treo mãi | Yêu cầu mới của bác sĩ phụ trách thay yêu cầu đang chờ (`SUPERSEDED`, migration V24); web và mobile hiện "Đã thay bằng yêu cầu mới" | `RecordHandoverApplicationServiceTest.newRequestSupersedesThePendingOne`, `RecordCollaborationPostgresTest`, `RecordCapabilitiesTest`, test web và mobile |
| Điều dưỡng thấy mã lượt khoa khác trong quá trình điều trị | Lọc `recordIds` và `lastVisitDate` theo khoa của điều dưỡng | `TreatmentCourseApplicationServiceTest.nurseSeesOnlyVisitsOfHerDepartmentInACourse` |
| Hàng chờ đính chính trả nội dung lâm sàng mà không ghi nhật ký đọc | Mỗi hồ sơ trả về ghi một dòng `READ`, cơ sở `AMENDMENT_REVIEW` | `RecordAmendmentApplicationServiceTest.pendingQueueLogsAReviewReadForEachListedRecord` |
| Phát hiện khi chạy lại Gateway: kết quả Lab trả trước khi hoàn tất bị đánh dấu "bổ sung sau khi kết thúc" vì Clinical nhận sự kiện chậm hơn lúc kết thúc | Lưu `occurredAt` của `lab.result.created` vào `attached_result.source_at` (migration V25) và so thời điểm đó | `PatientTimelinePostgresTest.documentsUseTheTimeTheSourceProducedThem`, `ClinicalIntegrationServiceTest.onLabResultCreated_keepsTheTimeLabReportedTheResult`; Gateway đạt |

Sau khi sửa: Clinical 1091/1091, web 1577/1577 (typecheck, lint sạch), mobile 741/741.

## Giới hạn của bằng chứng

- Trường hợp kết quả Lab về **sau** khi hoàn tất (đánh dấu "bổ sung sau khi kết thúc") chỉ được kiểm bằng test tự
  động. Qua Gateway, hoàn tất bị chặn khi còn chỉ định chưa có kết quả, nên kịch bản chỉ kiểm được chiều ngược lại.
- Seed demo không có trưởng khoa Nội. Kịch bản bổ nhiệm một bác sĩ qua `PUT /api/v2/org/departments/{id}/head-appointments`
  trước khi chạy.
- Kho thuốc demo trống. Kịch bản tạo một thuốc qua `POST /api/v1/pharmacy/drugs`. Đơn phải theo hợp đồng ngoại trú
  v1 với mã giá `RX-DEMO`, đúng như web gửi.
- Lab và Pharmacy chạy với đồng hồ lùi 12 giờ và tính ngày theo UTC, nên ngày chỉ định và ngày kê đơn gửi theo ngày
  UTC đã lùi. Pharmacy cần cùng độ lùi với Core thì token mới còn hạn. Đây là cấu hình của bộ chạy thử.
- Core báo `unhealthy` vì kiểm tra mail không có SMTP; không ảnh hưởng nghiệp vụ.
- Giao diện web và mobile chỉ được kiểm bằng test tự động, chưa chạy tay trên trình duyệt hay thiết bị.
