# Bàn giao hoãn ngoài MVP ngoại trú

- [Billing admission deposit/settlement](billing-inpatient-deposit-settlement.md): owner backend
  hiện tại Vinh; nội dung owner Lộc bên dưới là lịch sử. Không làm insurer settlement từ BHYT mô phỏng.
- [Shared FE → Surgery idempotency](frontend-surgery-idempotency.md): Vinh/shared → Huy/Surgery,
  chưa đóng consumer acceptance; không chặn workflow ngoại trú chỉ vì Surgery chưa nghiệm thu.

DEFERRED không phải DONE. Giữ code/data/migrations/HELD và gates OFF. Việc tái kích hoạt cần
phạm vi mới, source lock và producer/consumer acceptance theo [registry](../README.md).
