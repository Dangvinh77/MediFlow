# Registry hợp đồng MVP

Handoff/backlog lịch sử đã thay bằng [plan MVP](../plans/05-implementation-plan.md).
Đây không là xác nhận mọi integration gap đã được giải quyết.

## Active MVP gates

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

- [Vinh Clinical/Lab closeout, source lock and owner handoffs — 2026-10-15](HANDOFF-VINH-OUTPATIENT-MVP.md) — owned implementation/test evidence; M6 toàn nhóm và end-to-end acceptance còn mở. V-CLOSE-01 đã có standalone recovery proof; Vinh còn V-CLOSE-02/03; không làm lại Lab authority/inbox đã merge.
  H-VINH-01: Hoàng Anh/Gateway-Core; H-VINH-02: Huy/Pharmacy-Report + Vinh/Clinical consumer;
  H-VINH-03: Lộc/Billing. Từng handoff có source gap, producer/consumer, fixture/test và điều kiện đóng.
  Baseline tiếp theo `050b5916` đã merge PR #375/#376/#378; xem owner delivery queue trong
  closeout. P0 mới xác nhận: Billing chưa expose source → EXAM/LAB_TEST paymentRequestId,
  nên hai paid public slices chưa drive được qua HTTP; Lộc giao discovery contract,
  Hoàng Anh mở exact Gateway rule tương ứng. Rx producer và Lab-order route vẫn mở.
  Lượt tiếp tục đã sửa Clinical numeric-timestamp precision trong durable outbox, thêm
  consumer JVM crash/restart proof và hai packaged API slices với fixture clearance được
  ghi rõ; không dùng các proof này thay actual Billing payment hoặc Report/Rx acceptance.
  Kết quả cuối batch: Clinical 311 + 3, Lab 216 + 2 + 1 = 533 tests, 0 failures/errors/skips;
  independent static review không còn finding. Owner delivery queue vẫn mở.
- [Gateway allowlist missing for two live Billing read endpoints](HANDOFF-GATEWAY-BILLING-ADJUSTMENT-ROUTES.md) — Hoàng Anh-owned allowlist rule for `prescription-adjustments` and `surgery-cancellations`, found while building their frontend.
