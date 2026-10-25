# Quy định chức danh, chức vụ và độ tin cậy Core

Nguồn nghiệp vụ hiện hành: [02](../../docs/plans/02-business-flows.md); hợp đồng [03](../../docs/plans/03-contracts.md).
Tài liệu này giữ quy tắc/runbook Core; không tự mở lịch khám tại Core hoặc thay snapshot BHYT.


Ngày cập nhật: 21/10/2026. Owner: Hoàng Anh. Phạm vi: Organization/Patient.

## Chức danh chuyên môn và chức vụ quản lý

`Staff.jobTitle` là chức danh chuyên môn (`DOCTOR`, `NURSE`, …).
`Department.departmentHeadId` là việc bổ nhiệm một nhân viên làm trưởng khoa.
Trưởng khoa không phải một giá trị riêng trong `JobTitle`. Bổ nhiệm/miễn nhiệm không tự đổi
chức danh chuyên môn, quyền tài khoản hoặc chuyên khoa.

**Bác sĩ đang giữ chức trưởng khoa phải được miễn nhiệm hoặc thay thế trước khi đổi sang
`NURSE` hay chức danh khác không phải `DOCTOR`.** Hệ thống từ chối toàn bộ yêu cầu đổi
chức danh khi vẫn còn bổ nhiệm; không tự bỏ trưởng khoa trong thao tác sửa nhân viên.
Sau khi bỏ bổ nhiệm, cho phép đổi chức danh theo các điều kiện hiện hành.

| Chuyển đổi | Quy định | Xử lý hiện có trong Core |
|---|---|---|
| Bác sĩ trưởng khoa → bác sĩ | Cho phép miễn nhiệm chức vụ theo quyết định có thẩm quyền; vẫn là `DOCTOR` | Cập nhật khoa với `departmentHeadId: null`, hoặc bổ nhiệm người khác thay thế |
| Bác sĩ → bác sĩ trưởng khoa | Cho phép khi đáp ứng điều kiện bổ nhiệm | Người được chọn phải là `DOCTOR`, đang hoạt động và thuộc chính khoa đó; điều kiện được kiểm tra cả khi giữ nguyên UUID trưởng khoa |
| Bác sĩ → điều dưỡng | Đây là đổi chức danh chuyên môn, không phải giáng chức quản lý | Chặn nếu còn là trưởng khoa. Sau miễn nhiệm/thay thế, cho phép cập nhật `jobTitle: NURSE` theo validation hiện hành |
| Điều dưỡng → bác sĩ | Đây là đổi chức danh chuyên môn, không phải thăng chức quản lý | Cho phép cập nhật `jobTitle: DOCTOR` khi dữ liệu hợp lệ, gồm số giấy phép bắt buộc; không tự bổ nhiệm trưởng khoa |
| Điều dưỡng trưởng → điều dưỡng | Cho phép miễn nhiệm chức vụ theo quy định nghiệp vụ | Core hiện chưa có mô hình điều dưỡng trưởng riêng; không dùng `departmentHeadId` để bổ nhiệm `NURSE` |
| Bác sĩ khoa A → bác sĩ khoa B | Điều chuyển không đổi chức danh; phải đáp ứng yêu cầu chuyên môn và khoa đích đang hoạt động | Nếu đang là trưởng khoa A, phải bỏ/thay bổ nhiệm trước. Core kiểm tra khoa đích hoạt động; chưa có mô hình đối chiếu năng lực chuyên môn giữa các khoa |

Quyết định bổ nhiệm, miễn nhiệm và hồ sơ thẩm định năng lực hiện chưa có entity/quy trình phê
duyệt riêng. Bảng trên không khẳng định hệ thống đã lưu hoặc phê duyệt các quyết định này.
Quyền API giữ nguyên theo controller hiện hành.

Luồng thực hiện qua API sẵn có:

1. `PUT /api/v1/org/departments/{id}` với đầy đủ dữ liệu khoa và `departmentHeadId: null`
   để bỏ bổ nhiệm, hoặc UUID của bác sĩ đủ điều kiện để thay thế. Thiếu field này vẫn bị từ chối.
2. Sau khi thao tác trên thành công, gọi `PUT /api/v1/org/staff/{id}` để đổi `jobTitle`.
3. Nếu bước 2 chạy khi người đó vẫn là trưởng khoa, nhận lỗi nghiệp vụ
   `422 DEPARTMENT_HEAD_INVALID` (`InvalidDepartmentHeadException`); dữ liệu nhân viên và bổ nhiệm không bị thay đổi bởi yêu cầu đó.

## Bất biến và xử lý đồng thời

- Trưởng khoa phải là bác sĩ đang hoạt động trong cùng khoa. Đổi chức danh và điều chuyển
  phải kiểm tra bổ nhiệm trước khi ghi.
- Không có nhân viên hoạt động thuộc khoa đã ngừng hoạt động. Tạo nhân viên và điều chuyển
  vào khoa phải kiểm tra trạng thái khoa trong cùng transaction với việc ghi nhân viên.
- Các thao tác trên dùng khóa ghi tại hàng khoa đến khi commit. Cập nhật/điều chuyển nhân viên
  khóa hàng nhân viên trước; điều chuyển khóa các khoa theo thứ tự UUID để tránh khóa chéo.
  Cập nhật khoa giữ khóa khoa và đọc nhân viên theo MVCC, không lấy khóa nhân viên theo chiều ngược lại.
- Nếu ngừng khoa commit trước, tạo/điều chuyển vào bị từ chối. Nếu nhân viên được tạo/điều
  chuyển vào commit trước, ngừng khoa bị từ chối vì còn nhân viên hoạt động.
- Chưa có use case kích hoạt lại nhân viên trong source hiện hành. Nếu bổ sung, phải áp dụng
  cùng khóa khoa và kiểm tra lại trạng thái sau khi chờ khóa, không gọi trực tiếp `Staff.activate()` rồi lưu.
- Luồng bổ nhiệm và đổi chức danh dùng cùng khóa khoa: luồng chạy sau phải kiểm tra lại
  dữ liệu đã commit, không dựa vào kết quả đọc trước khi chờ khóa.

## Patient transactional outbox — HA-20-01

Hồ sơ Patient và dòng `patient_event_outbox` được ghi trong cùng transaction
`patientTransactionManager`, trên datasource Patient. Lỗi lưu outbox làm rollback cả hồ sơ.
API thành công nghĩa là hồ sơ và ý định gửi sự kiện đã commit; không có nghĩa Notification đã gửi xong.

Migration bổ sung `db/patient-migration/V2__patient_event_outbox.sql`; giữ nguyên migration cũ.
Relay gửi đúng `mediflow.events`, `patient.created`/`patient.updated`, dùng serializer Rabbit hiện hành
để giữ wire contract. Payload được lưu dạng `TEXT` và gửi lại nguyên bytes UTF-8, không sinh lại
`eventId`, `occurredAt` hoặc `correlationId`. Không thêm CCCD vào event.

Relay lấy từng hàng bằng `FOR UPDATE SKIP LOCKED`, giữ khóa đến khi xác nhận và commit.
Chỉ đánh dấu `PUBLISHED` khi nhận publisher ack và không có mandatory return. Nếu broker lỗi,
nack, timeout hoặc unroutable thì tăng số lần thử, giãn cách và giữ nguyên payload.
Crash hoặc lỗi DB sau confirm nhưng trước commit có thể gây gửi trùng; consumer phải dedupe
bằng `eventId` với effect trong cùng transaction của consumer.

| Cấu hình | Mặc định | Ý nghĩa |
|---|---|---|
| `mediflow.patient.outbox.scheduling-enabled` | `true` | Bật relay tự động; `false` chỉ dừng lịch gửi, vẫn ghi outbox |
| `mediflow.patient.outbox.delay-ms` | `5000` | Khoảng nghỉ giữa các lượt, mỗi lượt tối đa 20 sự kiện |
| `mediflow.patient.outbox.maximum-attempts` | `8` | Số lần gửi lỗi tối đa trước `QUARANTINED` |

Backoff tính bằng giây: `min(300, 2^min(attempts, 8))`; chờ confirm tối đa 5 giây mỗi lần gửi.
`last_error` chỉ lưu tên loại lỗi, không lưu exception message chứa dữ liệu bệnh nhân.
Không xóa pending/quarantined để làm sạch demo. Dòng `PUBLISHED` cũng được giữ để đối soát;
chính sách lưu trữ/xóa lịch sử chưa được tự động hóa trong thay đổi này.

Phục hồi quarantine sau khi sửa kết nối/binding/consumer: kiểm tra `event_id`, trạng thái,
`attempts`, `last_error`; dừng/drain worker khi cần; người vận hành được phép đưa **đúng sự kiện
đã kiểm tra** về `PENDING`, đặt lại số lần thử và lịch gửi. Giữ nguyên `event_id`, payload,
routing key và correlation. Đây là thao tác vận hành trên DB Patient, không phải API public mới.
Không tự replay tất cả sự kiện vì có thể đã có effect ở consumer.

## Kiểm thử và bàn giao

Chạy `mvn -q -pl backend/core-service -am test` với Docker. Regression nằm trong
`OrganizationServiceIntegrationTest`, `PatientServiceIntegrationTest` và test publisher.
Các test tích hợp sử dụng PostgreSQL 16 và RabbitMQ thật, không skip khi thiếu Docker.

Kết quả local ngày 21/10/2026 trên nhánh `codex/core-audit-reliability`: lệnh full Core
trả exit code 0; **205 tests / 38 suites, 0 failures, 0 errors, 0 skipped**. Trong đó
Organization integration 7/7, Patient integration 7/7 và Staff controller contract 18/18.
Docker Engine 28.5.1; images `postgres:16-alpine`, `rabbitmq:3.13-management-alpine`.

Các tình huống được bổ sung: chặn đổi chức danh khi còn trưởng khoa, cho đổi sau miễn nhiệm,
kiểm tra lại UUID trưởng khoa giữ nguyên, hai thứ tự bổ nhiệm/đổi chức danh, hai thứ tự
ngừng khoa/tạo nhân viên và ngừng khoa/điều chuyển, rollback hồ sơ khi outbox lỗi,
broker outage lúc tạo/cập nhật, khởi tạo lại worker, gửi lại nguyên event sau lỗi ở bước mark,
mandatory return và quarantine có giới hạn.

Test lỗi sau confirm dùng fault injection tại bước ghi trạng thái outbox, không tuyên bố đã
kill/restart toàn bộ JVM hay hoàn tất acceptance packaged. Vinh còn phải chạy Gateway/Core/
Notification trên revision cuối, xác minh consumer dedupe của welcome notification với event
gửi trùng và nghiệm thu packaged workflow. Handoff vẫn mở đến khi các gate đó đạt.

Tham chiếu: [hợp đồng](../../docs/plans/03-contracts.md),
[độ tin cậy](../../docs/plans/04-data-reliability.md),
[handoff audit](../../docs/handoffs/HANDOFF-HA-BACKEND-AUDIT-2026-10-20.md).
