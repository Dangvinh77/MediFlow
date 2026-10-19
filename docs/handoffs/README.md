# Registry bàn giao backend MVP

Refresh **2026-10-19**, source remote master `4c19dd4a`, gồm Gateway fix PR #431
(`2805b07c`). Bản sửa test và tài liệu trong lượt recheck vẫn là local diff cho đến khi
được commit/merge; không nhầm với nguồn remote đã đóng mọi acceptance.
Theo phân công người dùng: phần cần code giao đúng dev; **Vinh kiểm thử toàn bộ backend**,
gồm module regression, phân quyền, integration và recovery. Phân công test không tự mở
quyền sửa production của dev khác.

## Sau kiểm thử thực tế

[Recheck 19/10](../acceptance/backend-recheck-2026-10-19/README.md) xác nhận Gateway
explicit refund acceptance **11/11 pass**, full Gateway **1.449/1.449 pass**, không skips.
Số liệu 4/11 đỏ ở [bundle 18/10](../acceptance/backend-2026-10-18/README.md) là lịch sử
trước fix, không phải kết quả trên master mới. Hai lỗi test Report/Pharmacy vẫn có trong
remote master và tag đóng backend; sửa local chưa commit không làm nguồn chung tự thay đổi.
[V-TEST-BUG-01/02](HANDOFF-VINH-BACKEND-TEST-FAILURES.md) do Vinh sửa test và retest;
không giao production fix cho Huy chỉ từ lỗi harness. Rerun/skip ghi riêng trong bundle mới.

## Code Gateway và phần xác minh còn lại

| Dev | Task đã xác nhận còn mở | Handoff |
|---|---|---|
| Hoàng Anh | HA-CODE-01/02 đã sửa production tại `2805b07c`; không còn yêu cầu viết lại hai route. Policy retest và Gateway suite đã pass; phần acceptance runtime rộng hơn ghi riêng, không suy từ 11 filter cases | [Gateway Billing refunds](HANDOFF-GATEWAY-BILLING-ADJUSTMENT-ROUTES.md) |

Billing controllers đã có, flags đang bật trong default Compose; không giao lại Billing code cho Lộc.
Trong source/handoffs đã rà soát, chưa xác nhận gap code MVP cụ thể khác ở Core/Clinical/Lab/
Pharmacy/Billing/Notification/Report/Support. Đây không là kết luận toàn backend đã pass;
Vinh bổ sung bug/code task cho đúng owner nếu kiểm thử phát hiện lỗi.

## Toàn bộ kiểm thử — Vinh

- [Queue Vinh V-TEST-01–06](HANDOFF-VINH-BACKEND-VERIFICATION.md): full module suites,
  packaged/distributed profiles, refund routes, tích hợp và recovery trên final revision chung.
- [Vinh Clinical/Lab closeout](HANDOFF-VINH-OUTPATIENT-MVP.md) và
  [Billing/Notification closeout](HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md):
  PR #427 đã hoàn tất owned implementation, publication/replay/timestamp, paid EXAM/Lab
  và crash recovery. Không giao lại LOC-01–06 từ checklist lịch sử.
- [Huy Pharmacy/Report/Support](HANDOFF-HUY-MVP.md): 48/48 cho môi trường local đã quan sát,
  actual sáu-app EXAM/Rx và persisted-volume rehearsal đã có ngày 17/10. Không giao lại code;
  fresh regression/integration trên final revision chuyển Vinh.

[Plan MVP](../plans/05-implementation-plan.md), [contracts](../plans/03-contracts.md),
[reliability](../plans/04-data-reliability.md) và [runbook](../plans/06-acceptance-runbook.md)
là canonical. Historical checklists không thay current source/evidence. Deployment activation
và global release cần evidence của chính môi trường đích; không tự bật flags từ phân công này.
Inpatient/Surgery/full V2 deferred. Đóng/xóa handoff code khi cả implementation và nghiệm thu
đạt, chuyển lasting rules vào canonical docs.
