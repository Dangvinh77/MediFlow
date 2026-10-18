# Registry bàn giao backend MVP

Refresh **2026-10-18**, source master `025ef39e`, đã merge PR #427 (`71a2d6d1`).
Theo phân công người dùng: phần cần code giao đúng dev; **Vinh kiểm thử toàn bộ backend**,
gồm module regression, phân quyền, integration và recovery. Phân công test không tự mở
quyền sửa production của dev khác.

## Sau kiểm thử thực tế

[Bảng giao việc cuối 18/10](BACKEND-FINAL-ASSIGNMENTS-2026-10-18.md) là phân công hiện hành.
[Evidence](../acceptance/backend-2026-10-18/README.md): regression 6.854 tests có một failure,
Gateway acceptance 11 cases có bốn failures. Lỗi mới
[V-TEST-BUG-01/02](HANDOFF-VINH-BACKEND-TEST-FAILURES.md) giao Vinh sửa test clock Report và publication config của Pharmacy opt-in harness;
chưa là bằng chứng production bug để giao Huy. Các kết quả selected/skip/rerun được ghi riêng.

## Cần code

| Dev | Task đã xác nhận còn mở | Handoff |
|---|---|---|
| Hoàng Anh | HA-CODE-01 exact GET prescription adjustment refunds-due; HA-CODE-02 exact POST transaction refunds tại Gateway và đồng bộ route-role fixture | [Gateway Billing refunds](HANDOFF-GATEWAY-BILLING-ADJUSTMENT-ROUTES.md) |

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
