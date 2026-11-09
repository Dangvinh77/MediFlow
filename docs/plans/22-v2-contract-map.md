# MediFlow v2.0 — bản đồ hợp đồng

> Lập ngày 10/11/2026 từ source tại `3f3dcb8a`. Phục vụ IMP-CON-01, MIG-03 và Đợt 0 trong
> [roadmap v2.0](../superpowers/plans/2026-11-10-mediflow-v2-roadmap.md).
> Tài liệu này là bản đồ làm việc. Wire truth vẫn là [03-contracts.md](03-contracts.md) và fixture trong source.
> Các dòng `TARGET` chưa được triển khai.

## Quy tắc mã định danh (IMP-ID-01)

- `careEpisodeId` luôn là mã lượt khám: `registrationId = appointmentId = careEpisodeId = EXAM sourceId`.
- Quá trình điều trị dài hạn dùng `treatmentCourseId`, aggregate `TreatmentCourse`. Không gửi mã này vào
  bất kỳ field `careEpisodeId` nào và không dùng nó làm khóa thanh toán.

## Ranh giới Lab trong v2.0 (LAB-INT-05)

v2.0 là nhập tay có template và phiên bản. Không kết nối máy xét nghiệm, không có QC chuyên sâu, không
làm nuôi cấy đầy đủ hay kháng sinh đồ. FHIR chỉ là nguồn tham khảo mô hình; không tuyên bố tương thích FHIR.

## Sự kiện hiện có bị ảnh hưởng

| Routing key | Producer | Consumer trong MVP | Ràng buộc đang có | Đợt | Loại thay đổi |
|---|---|---|---|---|---|
| `lab.result.created` | Lab (`LabApplicationService:434`) | Clinical, Notification, Report (Outpatient, Operational), Billing (`FeeAccrualService` sinh phí LAB khi nhận event; Đợt 6 phải xác minh mode nào bật đường này để phát hành không tạo phí sai) | Clinical bắt `resultVersion == 1`; Report khai báo `RESULT_VERSION_ONE_ONLY`; Inpatient deferred | 6 | Đổi thời điểm phát sang lúc phát hành; giữ nguyên schema |
| `lab.request.created` | Lab | Billing | Tạo planned charge | 5 | Thêm field `serviceCode` có compatibility test |
| `lab.request.cancelled` | Lab | Billing | Exact-source | 2 | Xác minh actor/reason, không đổi nghĩa |
| `medicalrecord.created` | Clinical | Billing, Lab, Report | Mode quyết định có charge hay không | 3 | Không đổi; khóa dedupe |
| `medicalrecord.completed` | Clinical | Notification, Report | Dùng cho thống kê ngoại trú | 3, 4 | Thêm field tùy chọn có compatibility test |
| `appointment.status.changed` | Clinical | Billing | Mode quyết định có charge hay không | 3 | Không đổi; khóa dedupe |
| `followup.recommendation.created` | Clinical | Notification | Hướng người bệnh liên hệ đăng ký | 2 | Không đổi nghĩa |

## Sự kiện TARGET

| Routing key | Producer | Consumer | Thứ tự bật | Đợt |
|---|---|---|---|---|
| `lab.result.amended` | Lab | Clinical, Notification, Report | Mọi consumer đã triển khai, rồi mới bật `mediflow.v2.lab-amendment` | 6 |

Dedupe theo `labId` + `resultVersion`. Bản cũ đến muộn không ghi đè bản mới. Đổi `resultVersion` không tạo charge.

## REST hiện có bị ảnh hưởng

| Method và path | Service | Client đang dùng | Đợt | Loại thay đổi |
|---|---|---|---|---|
| `GET /api/v1/records/{id}` | Clinical | Web, mobile | 1 | Thêm kiểm quyền và lưu vết sau flag |
| `GET /api/v1/records/patient/{patientId}` | Clinical | Web, mobile | 1 | Thêm kiểm quyền và lưu vết sau flag |
| `PUT /api/v1/records/{id}`, `POST .../diagnoses` | Clinical | Web, mobile | 1 | Giữ guard; bổ sung nhánh ngoài MVP |
| `PUT /api/v1/records/{id}/complete` | Clinical | Web, mobile | 3 | Thêm đường kết thúc có lý do |
| `GET /api/v1/records/{id}/prescription-context` | Clinical | Pharmacy | 4 | Giữ theo `recordId`, khóa fixture |
| `PUT /api/v1/appointments/{id}/start-exam` | Clinical | Web, mobile | 2, 3 | Đợt 2: yêu cầu có số phòng khám sau flag; Đợt 3: kiểm replay; không đổi wire |
| `GET /api/v2/records/{recordId}/follow-up-recommendation` | Clinical | Web, mobile | 1 | Thống nhất căn cứ đọc |
| `/api/v2/consultation-sessions/**`, `/api/v2/doctor-work-shifts/**` | Clinical | Web, mobile | 2 | Thêm phản hồi tác động |
| `PUT /api/v1/lab/{id}/results` | Lab | Web, mobile | 6 | `result_workflow_version = 0` giữ hành vi cũ; luồng mới dùng API TARGET |
| `PUT /api/v1/lab/{id}/status` | Lab | Web và mobile không gọi | 6 | Giữ chặn với test mới |
| `POST /api/v1/lab` | Lab | Web, mobile | 5 | Thêm `serviceCode` |
| `GET /api/v1/lab/mine` | Lab | Mobile, web bệnh nhân | 6 | Chỉ trả bản đã phát hành |
| `queueNumber` trong `/api/v2/outpatient-registrations/**` và `OutpatientRegistrationFact` | Clinical | Web, mobile, Billing, Notification | 2 | Sau flag cấp theo khoa và ngày khám; vẫn là số nguyên từ 1, không đổi wire |

## REST TARGET

Mỗi dòng cần route Gateway, rule ở `RouteAuthorizationFilter` và test ở `RouteRoleContractTest`.

| Method và path | Service | Đợt |
|---|---|---|
| `GET /api/v2/records/{id}/capabilities` | Clinical | 1 |
| `POST /api/v2/records/access-declarations` | Clinical | 1 |
| `POST /api/v2/outpatient-registrations/{id}/room-queue-ticket` | Clinical | 2 |
| `GET /api/v2/clinic-queue` | Clinical | 2 |
| `PUT /api/v2/clinic-queue-tickets/{id}/call` | Clinical | 2 |
| `POST /api/v2/outpatient-registrations/{id}/vital-signs` | Clinical | 2 |
| `GET /api/v2/outpatient-registrations/{id}/vital-signs` | Clinical | 2 |
| `POST /api/v2/treatment-courses` | Clinical | 4 |
| `GET /api/v2/patients/{patientId}/treatment-courses` | Clinical | 4 |
| `POST /api/v2/treatment-courses/{id}/visits` | Clinical | 4 |
| `PUT /api/v2/treatment-courses/{id}/closure` | Clinical | 4 |
| `POST /api/v2/records/{id}/amendments` | Clinical | 4 |
| `PUT /api/v2/records/{id}/amendments/{amendmentId}/decision` | Clinical | 4 |
| `POST /api/v2/records/{id}/handovers` | Clinical | 4 |
| `PUT /api/v2/lab/{id}/report/draft` | Lab | 6 |
| `POST /api/v2/lab/{id}/report/submission` | Lab | 6 |
| `POST /api/v2/lab/{id}/report/return` | Lab | 6 |
| `POST /api/v2/lab/{id}/report/release` | Lab | 6 |
| `POST /api/v2/lab/{id}/report/amendments` | Lab | 6 |
| `POST /api/v2/lab/{id}/specimens` | Lab | 6 |
| `PUT /api/v2/lab/specimens/{id}/decision` | Lab | 6 |
| `POST /api/v2/lab/{id}/critical-notices/{noticeId}/acknowledgement` | Lab | 6 |

## Authority

| Hợp đồng | Hiện có | TARGET | Đợt |
|---|---|---|---|
| `SchedulingAuthority` (Core) sang `SchedulingAuthoritySnapshot` (Clinical) | `canManageSchedules`, `scopeRevision` | Thêm `canReleaseLabResults` cho Lab qua snapshot riêng; không mượn capability lịch | 1 |
| `GET /api/v1/org/me/capabilities` | Có | Thêm capability duyệt Lab | 1 |

## Client

| Client | File DTO chính | Đợt phải cập nhật |
|---|---|---|
| Web Lab | `frontend/src/features/lab/types.ts` | 5, 6 |
| Web hồ sơ | `frontend/src/features/medical-record/types.ts` | 1, 3, 4 |
| Mobile Lab | `mobile/lib/features/lab/data/lab_test_dto.dart` | 5, 6 |
| Mobile hồ sơ | `mobile/lib/features/medical_record/data/medical_record_dto.dart` | 1, 3, 4 |

## Khảo sát dữ liệu (MIG-01)

Script chỉ đọc: `scripts/v2/profile-clinical-lab-data.sql`.

**Chưa có số liệu:** máy hiện không có volume `mediflow-pgdata` nên chưa có dữ liệu demo để khảo sát.
Phải dựng stack demo, chạy script và ghi số liệu vào đây trước khi bắt đầu migration ở Đợt 4.
