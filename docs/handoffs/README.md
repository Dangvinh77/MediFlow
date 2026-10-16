# Registry hợp đồng MVP

Handoff/backlog lịch sử đã thay bằng [plan MVP](../plans/05-implementation-plan.md).
Đây không là xác nhận mọi integration gap đã được giải quyết.

## Active MVP gates

- [Lộc — discovery và selective publication V1 ngoại trú](HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md)
  — **READY TO IMPLEMENT**, source `71e2684d`: exact source-bound discovery API, immutable
  new-request cohort, append-only migration, dispatcher/Rabbit/JVM proof; checklist L1–L4 để
  Lộc vào code ngay, không cần lập kế hoạch khác. G1 là Hoàng Anh/Gateway; Vinh nhận V-CLOSE-03
  sau producer/Gateway acceptance. API/boundary chưa tồn tại ở baseline, không phải rollout approval.

- [Handoff chung Huy — Pharmacy, Report và Support](HANDOFF-HUY-MVP.md): **một đầu mối** cho
  toàn bộ 10 subtask Huy còn mở, owner/acceptance, Billing publication, Clinical attachment,
  Notification, Gateway, Support composition, historical coverage và operator cutover/rollback.
  Gộp/thay sáu handoff/pointer backend Huy cũ; Core lookup retarget đã có acceptance, Surgery ghi deferred trong
  cùng file. Phần độc lập đạt 38/48; API/event gates vẫn OFF tới khi owner nghiệm thu đủ.

[Contracts canonical](../plans/03-contracts.md) quy định source truth và modes.
Mỗi gap MVP ghi trong task: producer, consumer, missing behavior/field, owner và acceptance
fixture/test. Không copy payload thành nhiều file handoff. Không vượt service ownership.
Inpatient/Surgery/full V2 deferred, không trở thành blocker để đóng luồng ngoại trú.

## Active source lock

- [Vinh Clinical/Lab closeout và owner handoffs — cập nhật 2026-10-16](HANDOFF-VINH-OUTPATIENT-MVP.md) — owned implementation/test evidence; M6 toàn nhóm và actual paid acceptance còn mở. V-CLOSE-01 đã có standalone recovery proof; V-CLOSE-02 đã implement Rx V1 consumer; V-CLOSE-03 còn chờ Billing/Gateway discovery và actual publication.
  H-VINH-01: Hoàng Anh/Gateway-Core; H-VINH-02: Huy/Pharmacy-Report + Vinh/Clinical consumer;
  H-VINH-03: Lộc/Billing. Từng handoff có source gap, producer/consumer, fixture/test và điều kiện đóng.
  Refresh `71e2684d`: Billing chưa expose source → EXAM/LAB_TEST paymentRequestId,
  nên hai paid public slices chưa drive được qua HTTP; Lộc giao discovery contract,
  Hoàng Anh mở exact Gateway discovery rule tương ứng. Huy đã giao outpatient Rx producer
  relationship; Hoàng Anh đã giao Lab-order route. Vinh bổ sung Rx V1 consumer và chuyển
  packaged Lab-order acceptance qua Gateway; evidence mới nhất nằm đầu closeout.
  Full owned-module regression: Clinical 339 + Lab 216 = 555 tests, 0 failures/errors/skips.
  Không dùng fixture clearance hoặc persisted HELD grant thay actual Billing payment/publication.
- [Gateway allowlist missing for two live Billing read endpoints](HANDOFF-GATEWAY-BILLING-ADJUSTMENT-ROUTES.md) — Hoàng Anh-owned allowlist rule for `prescription-adjustments` and `surgery-cancellations`, found while building their frontend.
