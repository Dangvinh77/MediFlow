# Nghiệm thu consumer Patient welcome — 28/10/2026

Tiếp nối PR #550 và H-02. Core PG đã kiểm create/update khi broker outage, worker
reconstruction và lỗi sau confirm trước mark; Organization races đã qua. Phần còn
thiếu là actual Core producer bytes tới Notification và replay sau restart Support.

- [x] Trong six-service acceptance, bắt `patient.created` từ broker khi public Core
  command tạo bệnh nhân. Không giả event ID/payload hoặc đọc DB Core để làm consumer.
- [x] Xác nhận một welcome và processed marker trước rollback/restart Support hiện có.
- [x] Sau Support restart, phát lại nguyên body/properties, chờ queue ACK, so bản ghi
  welcome/marker và không có notification.sent mới. Ghi hash và counts, không PII.
- [x] Chạy lại six-service profile: exit 0, không fail/error/skip. Không thêm dead letter.
  Cập nhật [manifest và H-02 closeout](../acceptance/backend-closeout-2026-10-27/README.md#h-02--đã-đóng).

Không thêm API, thay production sender hoặc thay gate. Không gọi worker reconstruction
ở Core là kill Core JVM. Nghiệm thu này kiểm producer recovery và consumer restart ở
hai lát có ranh giới rõ; không ký thay identity recovery hoặc UX của ba developer.
