# Thông báo phiếu đăng ký ngoại trú V2

Consumer nhận `outpatient.registration.changed` version 1 từ `clinical-service` theo
[hợp đồng A.7](../../../docs/plans/03-contracts.md#opd-scheduling-v2). API đọc thông báo
và kiểm patient ownership giữ nguyên V1. Thông báo không cấp quyền khám hoặc chứng minh thu tiền.

Flag mặc định tắt: `MEDIFLOW_NOTIFICATION_OUTPATIENT_REGISTRATION_CONSUMER_ENABLED=false`.
Cùng flag áp dụng cho standalone Notification và Support host. Chỉ bật sau khi nghiệm thu
producer bytes và toàn luồng; cấu hình này không tự bật lịch khám hoặc Billing.

| Trạng thái nguồn | Nội dung cho người bệnh |
|---|---|
| `AWAITING_PAYMENT` | Đăng ký đang chờ thanh toán, chưa xác nhận lịch |
| `CONFIRMED` | Phiếu khám đã xác nhận; xem phòng/bác sĩ/khung giờ/số thứ tự trên phiếu |
| `CANCELLED` | Phiếu đăng ký đã hủy, không tuyên bố đã hoàn tiền |
| `EXPIRED` | Hết hạn giữ suất, liên hệ quầy tiếp nhận |
| `RECONCILIATION_REQUIRED` | Cần đối soát, không thanh toán lại khi chưa rõ kết quả |
| `IN_EXAM`, `COMPLETED` | Cập nhật cursor, không tự tạo thêm thông báo xác nhận |

Queue riêng `notification.outpatient-registration.q` dùng exchange `mediflow.events`;
không thêm listener cạnh tranh vào `notification.q`. Poison/conflict đi tới DLX hiện có
với routing key `notification.dead-letter`, giữ trong `notification.dlq`.
Lỗi tạm thời retry hữu hạn theo `notificationListenerFactory`; hết retry giữ evidence tại DLQ.

Trong một transaction Notification: claim `eventId` và SHA-256 exact body, khóa cursor
theo `registrationId`, đối chiếu hash snapshot của từng revision, cập nhật cursor, ghi
history in-app và held `notification.sent` outbox. Không đọc database Clinical/Core.
Delivery lặp cùng bytes và operation revision lặp cùng snapshot không gửi lại.
Cùng eventId khác raw bytes hoặc cùng revision khác snapshot bị từ chối.

Revision bị thiếu phải retry/đối soát, không đoán trạng thái trung gian. Fact terminal đến
trước create để lại tombstone; create/confirmation cũ không gửi thông báo muộn. Khi DLQ có
revision gap: giữ original body/eventId, phục hồi các fact thiếu từ Clinical outbox/archive
có provenance, replay theo thứ tự rồi replay body gốc. Không sửa trực tiếp cursor/inbox,
không tạo UUID mới để vượt dedupe và không lấy Report rebuild làm nguồn gửi thông báo.

Migration V6 chỉ thêm hai bảng cursor/revision, giữ V1–V5 và lịch sử thông báo cũ.
Khi rollout, khai báo binding/consumer trước khi nhận đăng ký V2. Khi rollback, tắt ingress
V2 và giữ worker biết V2 cho tới khi đã xử lý hết payment pending; không xóa queue hoặc volumes.

Kiểm thử: `mvn -q -pl backend/notification-service -am test`. Các case mới dùng PostgreSQL/
RabbitMQ thật cho replay, conflict rollback, tombstone, revision gap, concurrent delivery và DLQ.
Kết quả producer/consumer cùng bytes được ghi riêng; dữ liệu giả trong consumer test không
được gọi là golden fixture của Clinical.
