# Nghiệm thu MediFlow v2.0 — Đợt 2: tiếp nhận tại khoa

Ngày chạy: 10/11/2026. Nhánh `develop/mediflow-v2.0`. Stack dựng bằng Docker Compose từ
`docker-compose.yml`, `compose.demo.local.yml` và `compose.outpatient.local.yml`.

Kết quả: **29/29 kiểm tra đạt** (3 khi tắt cờ, 26 khi bật cờ) trên cơ sở dữ liệu sạch.

## Cách chạy và giới hạn của bằng chứng

- Mọi yêu cầu nghiệp vụ đi qua Gateway thật ở cổng 8080, dùng token đăng nhập thật. Đăng ký, thu tiền,
  cấp số, gọi khám, ghi sinh hiệu, đổi lịch và hủy đều qua API. SQL chỉ dùng để đọc kết quả.
- **Một fixture tạo bằng SQL:** seed mỗi khoa chỉ có một bác sĩ, nên `demo.staff05` được chuyển sang
  Khoa Nội để có hai bác sĩ, hai phòng cùng khoa. Cả project Compose dùng volume riêng và đã xóa sau khi chạy.
- **Đồng hồ kiểm thử:** ca khám chỉ được nằm trong 07:00–17:00 và lần chạy diễn ra sau 19:00, nên Core,
  Gateway, Clinical, Billing và Support chạy trong image phụ có `libfaketime` lùi 6 giờ. Image phụ và file
  override nằm ngoài repo, không có thay đổi mã nguồn.
- Bật cờ giữa ngày được mô phỏng bằng cách khởi động lại Clinical từ `MEDIFLOW_V2_CLINIC_INTAKE=false`
  sang `true` khi đã có phiếu trong ngày.
- Seed có 10 bệnh nhân, nên một số bệnh nhân được dùng lại ở các buổi khác nhau.
- **Chưa kiểm thử trên trình duyệt hay thiết bị.** Web và mobile mới được kiểm bằng test tự động.
- Core báo `unhealthy` vì health check SMTP thất bại trong môi trường này; đăng nhập và API Core vẫn
  hoạt động. Việc này không thuộc Đợt 2.

## Kết quả

Vai: Bác sĩ `demo.staff03` phòng Nội 1, `demo.staff05` phòng Nội 2; điều dưỡng Khoa Nội `demo.staff04`;
điều dưỡng Khoa Ngoại `demo.staff22`; điều dưỡng tiếp nhận `demo.staff27` (`demo.reception`).

| Kịch bản | Kết quả thực tế |
|---|---|
| Hai quầy tranh suất cuối | 201 và 409 `SESSION_CAPACITY_EXHAUSTED` |
| Gửi lại cùng key | 201, cùng phiếu, `Idempotency-Replayed: true` |
| Cùng key, khác bệnh nhân | 409 `IDEMPOTENCY_CONFLICT` |
| Mỗi khoa dãy số quầy riêng | Nội 2 và 3, Ngoại 1 |
| Sang ngày khác bắt đầu lại | Nội ngày mai số 1 |
| Bật cờ giữa ngày không trùng số quầy | Trước khi bật số theo buổi 1 và 1; số quầy đầu tiên sau khi bật là 2; không có dòng trùng trong khoa |
| Billing và Notification vẫn nhận phiếu `CONFIRMED` | Billing `CONFIRMED`; Notification có 3 bản ghi |
| Hai phòng cùng khoa đánh số độc lập | Nội 1 số 1, Nội 2 số 1 |
| Điều dưỡng khoa cấp số | 201; gửi lại cùng key trả cùng phiếu |
| `demo.reception` và điều dưỡng khoa khác | 403 `CLINIC_DESK_SCOPE_DENIED` cả hai |
| Sinh hiệu đo lần hai không đè lần một | 201, 201; bác sĩ phụ trách đọc được 200 với mạch 80 và 92 |
| Thiếu số phòng không bắt đầu khám được | 422 `CLINICAL_ROOM_TICKET_REQUIRED` |
| Thiếu sinh hiệu vẫn khám được | 200 |
| Gọi vào khám ghi người gọi và thời điểm | 200; DB có người gọi, thời điểm, trạng thái `CALLED` |
| Hủy ca có phiếu | Tác động báo `confirmedCount=1`, 1 phiếu; hủy 200; buổi `STOPPED`; phiếu vẫn `CONFIRMED` |
| Đổi lịch thiếu đồng ý của bệnh nhân | 422 `RESCHEDULE_CONFIRMATION_REQUIRED` |
| Đổi bác sĩ cùng khoa | 200; 1 dòng log; Billing `RESCHEDULED` 50000.00; thông báo từ 3 lên 4 |
| Hủy phiếu chưa thu | 200; `VOID_UNPAID` 0.00 |
| Hủy phiếu đã thu | 200; `REFUND_DUE` 50000.00 |
| Cờ tắt | `queueNumber` theo buổi; API mới 404 `RESOURCE_NOT_FOUND` |

## Lỗi nghiệm thu phát hiện và đã sửa

| Lỗi | Sửa | Bằng chứng |
|---|---|---|
| Cờ tắt, đường dẫn mới trả 500 thay vì 404 | `GlobalExceptionHandler` xử lý route không tồn tại | `routeThatIsSwitchedOff_isNotFoundInsteadOfServerError` đỏ rồi xanh |
| Hủy phiếu đặt lịch luôn 500: ràng buộc chỉ cho phép ghi thông tin hủy ở contract 1 | Migration `V16` mở cho contract 2, vẫn yêu cầu đủ cả bộ thông tin | `AppointmentCancellationAuditPostgresTest` đỏ rồi xanh |

Ba test migration ghim số phiên bản cũ đã được cập nhật theo `V16` của Clinical và `V34` của Billing.

## Kết quả test tự động

| Phần | Kết quả |
|---|---|
| Backend (Gateway, Clinical, Billing, Notification, Support và module phụ thuộc) | Đạt, trừ `LabTestChargeConfigurationTest.intake_requiresBothExplicitGates[4]` đã biết từ trước |
| Web | 1488/1488; typecheck đạt; lint 0 lỗi, 3 cảnh báo có sẵn |
| Mobile | 648/648 |

## Ghi nhận chưa xử lý

Tạo buổi khám ngoài 07:00–17:00 qua API trả 500 thay vì lỗi 4xx. Lỗi có từ 26/10, web và mobile đã chặn
ở biểu mẫu, không thuộc phạm vi Đợt 2.
