# Nghiệm thu MediFlow v2.0 — Đợt 1: quyền đọc hồ sơ

Ngày chạy: 10/11/2026. Nhánh `develop/mediflow-v2.0`. Stack dựng bằng Docker Compose, flag
`MEDIFLOW_V2_RECORD_ACCESS_POLICY=true` ở Clinical, Lab, Pharmacy.

Kết quả: **40/40 kiểm tra đạt** cho mục 5 của
[`02-medical-record-read-access.md`](../known-limitations/02-medical-record-read-access.md).

## Cách chạy và giới hạn của bằng chứng

- Mọi yêu cầu đọc, ghi, khai báo đi qua Gateway thật ở cổng 8080, dùng token đăng nhập thật. Lab và
  Pharmacy gọi Clinical bằng Feign qua Eureka.
- **Dữ liệu fixture tạo bằng SQL**, không qua luồng tiếp nhận: appointment, medical record, diagnosis,
  consultation session, registration và giấy hẹn tái khám. Sau khi chạy đã xóa hết.
- Seed chỉ có một bác sĩ mỗi khoa, nên `demo.doctor4` và `demo.doctor5` được tạm chuyển sang Khoa Tim
  mạch bằng SQL rồi trả lại. Khoa Ngoại tổng quát đóng vai khoa thứ hai thay cho tiêu hóa.
- Riêng kịch bản 3 cần giấy hẹn tái khám, nên lần chạy này bật thêm
  `MEDIFLOW_GATEWAY_SCHEDULING_V2_ENABLED` và `MEDIFLOW_CLINICAL_SCHEDULING_ENABLED` bằng file override
  ngoài repo. Compose mặc định vẫn tắt hai cờ này.
- **Chưa kiểm thử trên trình duyệt hay thiết bị.** Web và mobile mới được kiểm bằng test tự động.
- Core báo `unhealthy` vì health check SMTP thất bại trong môi trường này. Đăng nhập và các API Core
  vẫn hoạt động; việc này không thuộc Đợt 1.

## Vai trong fixture

| Ký hiệu | Tài khoản | Khoa khi chạy | Quan hệ với bệnh nhân P |
|---|---|---|---|
| B | `demo.doctor3` | Tim mạch | Tác giả R1, lượt đã hoàn tất |
| C | `demo.doctor4` | Tim mạch (tạm) | Tác giả R2, lượt `IN_EXAM` |
| D | `demo.doctor5` | Tim mạch (tạm) | Cùng khoa, không phụ trách lượt nào |
| E | `demo.doctor7` | Ngoại tổng quát | Tác giả R3, lượt đã hoàn tất |
| F, G | `demo.doctor8`, `demo.doctor6` | Răng Hàm Mặt, Mắt | Không có quan hệ |
| A | `demo.admin` | Hành chính | Không có quan hệ |

## Kết quả

| Kịch bản của tài liệu 02 | Kiểm tra | Kết quả thực tế |
|---|---|---|
| C hoặc D tra cứu P ở khoa | C, D gọi danh sách | 200, thấy R1 và R2 dù khác tác giả |
| C mở R1, không sửa được | GET R1; capabilities; PUT R1 | 200; `canWriteContent=false`, `RECORD_COMPLETED`; 422 `CLINICAL_V0_WRITE_DISABLED` |
| Đối chứng quyền ghi | C sửa R2 của mình đang khám | 200, nội dung đổi |
| Bác sĩ khác sửa lượt đang khám | D sửa R2 | 403 `CLINICAL_DOCTOR_NOT_ASSIGNED` |
| C mở giấy hẹn nguồn của R1 | GET follow-up của R1; của R2 chưa có giấy | 200 đúng `sourceRecordId`; 404 `RESOURCE_NOT_FOUND`, khác với từ chối quyền |
| Không có căn cứ thì không lộ R3 | G gọi chi tiết R3; danh sách | 403 `RECORD_ACCESS_PURPOSE_REQUIRED` cả hai |
| Có nhiệm vụ thì đọc được, không sửa được | G khai báo `CONSULTATION`; GET R3; PUT R3; capabilities | 201; 200; 422; `DECLARED_PURPOSE`, không ghi được |
| B sửa R1 đã hoàn tất | PUT R1; capabilities; đọc DB | 422; `RECORD_COMPLETED`; nội dung không đổi |
| A tra cứu P | Chưa khai báo; thiếu lý do; có lý do; danh sách | 403; 400 `INVALID_REQUEST`; 201; 200 đủ R1, R2, R3 với 2 khoa, 3 bác sĩ |
| Đổi id trên URL | F gọi R1 và theo P; capabilities; API nội bộ | 403; 403; 200 với `canRead=false`, không ghi được; Gateway trả 404 cho `read-decision` |
| Staff chuyển khoa | D dùng token cũ; đăng nhập lại | Trước 200, sau 401 `AUTH_SESSION_REVOKED`; đăng nhập lại 403 |
| Staff bị vô hiệu hóa | C dùng token cũ | 403 `RECORD_ACCESS_DENIED` |
| Nhật ký đọc và từ chối | Truy vấn `record_access_log` | Có đủ dòng cho phép và từ chối, xem bên dưới |

Điều dưỡng và tiếp nhận:

| Kiểm tra | Kết quả |
|---|---|
| Điều dưỡng Tim mạch tra P | 200, chỉ 2 hồ sơ của khoa mình, không có R3 |
| Điều dưỡng khoa khác | 403 `RECORD_ACCESS_PURPOSE_REQUIRED` |
| Điều dưỡng khai báo mục đích khác `EMERGENCY` | 400 `INVALID_REQUEST` |
| `demo.reception` đọc hồ sơ | 403 `RECORD_ACCESS_PURPOSE_REQUIRED` |

Lab và Pharmacy với flag bật:

| Kiểm tra | Lab `GET /lab/patient/{id}` | Pharmacy `GET /pharmacy/prescriptions?patientId=` |
|---|---|---|
| Bác sĩ không quan hệ | 403 `RECORD_ACCESS_PURPOSE_REQUIRED` | 403 `RECORD_ACCESS_PURPOSE_REQUIRED` |
| Bác sĩ có căn cứ | 200 | 200 |
| Vai trò nghiệp vụ | `LAB_TECH` đọc `GET /lab`, 200 | `PHARMACIST`, 200 |

## Nhật ký `record_access_log` của lần chạy trước khi dọn

Đếm theo vai trò, hành động, kết quả, căn cứ và mục đích ở lần chạy 36/39, là lần gần nhất đã lưu đủ
bảng này. Lần 40/40 chỉ thêm một lượt PUT và không đổi cấu trúc nhật ký; tổng ở lần đó có cả dòng cho
phép và từ chối.

| Vai trò | Hành động | Kết quả | Căn cứ | Mục đích | Số dòng |
|---|---|---|---|---|---|
| ADMIN | DECLARE | ALLOWED | | RECORD_MANAGEMENT | 1 |
| ADMIN | LIST | ALLOWED | DECLARED_PURPOSE | | 1 |
| ADMIN | LIST | DENIED | | | 1 |
| DOCTOR | DECLARE | ALLOWED | | CONSULTATION | 1 |
| DOCTOR | LIST | ALLOWED | CARE_ASSIGNMENT | | 1 |
| DOCTOR | LIST | ALLOWED | DEPARTMENT_ENCOUNTER | | 1 |
| DOCTOR | LIST | DENIED | | | 2 |
| DOCTOR | READ | ALLOWED | AUTHOR | | 2 |
| DOCTOR | READ | ALLOWED | CARE_ASSIGNMENT | | 4 |
| DOCTOR | READ | ALLOWED | DECLARED_PURPOSE | | 1 |
| DOCTOR | READ | ALLOWED | DEPARTMENT_ENCOUNTER | | 1 |
| DOCTOR | READ | DENIED | | | 6 |
| NURSE | LIST | ALLOWED | DEPARTMENT_ENCOUNTER | | 1 |
| NURSE | LIST | DENIED | | | 2 |

Bảng chỉ có các cột định danh, hành động, kết quả, căn cứ, mục đích và thời điểm; không có cột nội dung
lâm sàng hay token.

## Khác biệt so với cách diễn đạt của tài liệu 02

- Bác sĩ đang có lượt khám với P được đọc hồ sơ của P ở mọi khoa. Đây là quy tắc đã duyệt
  (`CARE_ASSIGNMENT`), nên C và D thấy cả R3. Kịch bản "không có quyền liên khoa" được chạy với bác sĩ
  không có quan hệ. Bộ lọc theo khoa áp dụng cho điều dưỡng.
- Sửa hồ sơ của lượt không còn `IN_EXAM` trả `CLINICAL_V0_WRITE_DISABLED`. Tên mã là mã cũ, hành vi chặn
  là đúng.

## Lỗi phát hiện khi nghiệm thu và đã sửa

- `capabilities`, `access-declarations` và API cấp quyền Lab nằm trong route chỉ bật cùng lịch khám V2,
  nên với compose mặc định người dùng nhận 404 và không khai báo được mục đích. Đã tách sang
  `RecordAccessRouteConfiguration`, luôn bật. Test: `RecordAccessRouteConfigurationTest`.
- Bộ phận Tiếp nhận mới trong seed thiếu phòng làm việc, vi phạm quy tắc mỗi đơn vị chức năng có một
  phòng. Đã thêm `room-15-reception`.

## Test tự động

| Phạm vi | Kết quả |
|---|---|
| Gateway | 1819 đạt |
| Core | 517 đạt |
| Clinical | 564 đạt |
| Lab | 287 đạt |
| Pharmacy | 892, 1 lỗi có từ trước: `DispenseApplicationServiceTest.dispense_expiredDrug_doesNotReduceStock` |
| Web `pnpm test` | 1434 đạt |
| Mobile `flutter test` | 625 đạt |
