# Vinh — receipt thanh toán Notification V1 chưa giữ byte identity

## Điều phối hiện hành — 26/10/2026

- Registry ID: `H-07`; status: `NEEDS REVALIDATION`.
- Bàn giao hiện tại: Vinh/Notification → Vinh verification.
- [Registry và phần acceptance còn thiếu](README.md) là nguồn giao việc hiện tại.
- Các base/branch, status, owner và test counts trong checkpoint dưới giữ theo ngày;
  không là bằng chứng bug còn tồn tại hoặc task phải code lại trên HEAD.
- Nghiệp vụ mới dùng [WF-01–11](../plans/07-outpatient-workflow-implementation.md), không đóng từ evidence baseline.

## Hồ sơ phát hiện và checkpoint

Phát hiện 2026-10-19 trên base `05c7690c3a37b602e336fac4902ced7f3a1f7d89`.
Owner: **Vinh**, tiếp quản Notification từ Lộc theo phân công người dùng.
Trạng thái: **đã có implementation, chờ final runtime acceptance**; không tính thành backend
đã đóng sạch. Follow-up thêm raw hash riêng, giữ normalized hash, V5 migration và offline
archive backfill. Xem [runbook](../../backend/notification-service/docs/receipt-byte-identity.md)
và [evidence hiện hành](../acceptance/notification-v1-byte-identity-2026-10-19/README.md).
Không áp patch lịch sử bên dưới lên branch fix: regression đã được đưa vào default suite.

`CarePaymentReceiptWireHandler` hash JSON đã parse rồi serialize/sắp xếp lại, thay vì body
nguyên bản. Hai delivery cùng eventId có JSON tương đương nhưng khác whitespace/order cho
cùng fingerprint; repository ACK duplicate thay vì conflict. Contract V1 yêu cầu cùng key
khác bytes phải reject. Không phát sinh thông báo/thu tiền hai lần; lỗi ở kiểm tra integrity
và phát hiện eventId bị dùng lại.

Regression dùng PostgreSQL/RabbitMQ thật được lưu tại
[notification-v1-byte-identity-regression.patch](fixtures/notification-v1-byte-identity-regression.patch).
```powershell
git apply docs/handoffs/fixtures/notification-v1-byte-identity-regression.patch
mvn -q -pl backend/notification-service -am '-Dtest=CareReceiptIntegrationTest#sameEventWithDifferentRawBytesConflictsWithoutReplacingHistory' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Ca thêm delivery chỉ khác whitespace sau receipt hợp lệ: expected conflict, actual không có
exception (**1 test, 1 failure, 0 error, 0 skip**). Xem tên method/lệnh tái hiện trong patch
và bundle audit. Các ca cố ý đỏ không được giữ trong suite mặc định của PR sửa module khác.

## Điều kiện để sửa an toàn

Không thay đơn thuần normalized digest thành raw digest rồi kết luận hoàn tất. `PROCESSED_EVENT`
chỉ có eventId/type/hash, không có inbound body/hash-version; Notification không lưu body
đã ACK trong recovery/inbox khác. Outbox Notification là outbound delivery fact, DLQ không
là archive của receipt đã ACK. Bản thay hash thử nghiệm làm regression mới pass nhưng có
thể từ chối cả original delivery đã commit trước nâng cấp; bản thử đó đã được bỏ.

Một hash cũ không cho phép khôi phục original bytes. Không thêm fallback normalized âm thầm
vì sẽ tiếp tục chấp nhận byte mismatch. Gate care-v1 mặc định OFF không chứng minh database
đích chưa có receipt V1.

Vinh cần khóa source/gates và kiểm kê receipt V1 ở môi trường đích trước chọn cutover:

- Nếu chứng minh chưa có receipt V1/pending delivery: chuyển sang raw-byte digest, thêm
  regression byte-identical duplicate và same-eventId/different-bytes, kiểm retry/DLQ/atomicity.
- Nếu đã có receipt: lập kế hoạch backfill có provenance từ original producer archive theo
  eventId, hoặc một boundary phiên bản legacy được phê duyệt và ghi rõ contract. Billing có
  producer outbox, nhưng không được query DB Billing trực tiếp từ Notification để chữa cháy.
  Việc backfill phải là export/import có kiểm chứng, không cross-context runtime coupling.

Acceptance: original bytes replay sau restart/nâng cấp vẫn idempotent; thay một byte cùng
eventId bị conflict/DLQ; chỉ một notification/history/outbox effect; poison không retry vô hạn;
giữ V0 và các V1 projection khác. Cần evidence fresh và upgrade, không chỉ fresh DB.
