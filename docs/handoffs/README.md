# Registry hợp đồng MVP

Refresh **2026-10-17**, đối chiếu baseline `d80d23b2` và Vinh working-tree evidence. Registry chỉ giữ dependencies còn mở;
implementation đã merge không được giao lại. Không suy full MVP done từ owner-local tests.

## Active MVP gates

- [Lộc L3/L4 + Hoàng Anh G1 — discovery/publication ngoại trú](HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md)
  — **PARTIAL DELIVERY**. L1 discovery và L2 cohort/eligible writer V22/V23 đã merge;
  PR #401 giao actual Rabbit happy-path, packaged JVM recovery và discovery fixtures;
  source đã có unroutable/retry/quarantine/replay/lease/equal-timestamp tests.
  **Lộc đọc mục 0, làm LOC-01 → LOC-06**: duplicate new-event/historical HELD broker proof,
  NACK/timeout + fencing audit, timestamp replay, fresh L4 bundle và phối hợp paidRx.
  Exact Gateway discovery và #417 correlation fix đã có; Vinh paid EXAM/LAB_TEST slices PASS.
  Full G1 owner acceptance vẫn mở; không làm lại route, L1/L2 hoặc Lab-order đã giao.
- [Huy — Pharmacy, Report và Support](HANDOFF-HUY-MVP.md)
  — một đầu mối cho owner acceptance/activation, Billing/Rx/Report/Notification và Support.
  Outpatient filled relationship và Clinical consumer đã có; paid integration/coverage/global
  rollout vẫn theo acceptance của từng owner. Không giao lại Rx producer recordId fixture.
  Huy plan **48/48 trong môi trường phát triển hiện có** sau override ngày 2026-10-17: Support/Notification/shared wiring,
  real PG/Rabbit isolation, packaged restart và actual sáu-app EXAM/Rx paid/dispense/
  current-store rollback và inventory/rehearsal trên cold copies của chính persisted Compose
  volumes cũ đã đạt. **2.129 tests / 196 class, zero failure/error/skip** trong selection
  ghi ở mục 10–11; không cộng trùng reruns hoặc gọi Billing/Pharmacy selection là full suite.
  H6-02.3/H6-12.3 đã đóng: source stores trong phạm vi không có historical business rows,
  hai persisted broker nodes không có queues; bốn writes mới và probe bytes giữ qua chuyển đổi,
  original-volume hashes không đổi. Không còn Huy task chờ owner; global production/coverage
  gates không suy ra từ nghiệm thu local này.
- [Gateway Billing adjustment routes](HANDOFF-GATEWAY-BILLING-ADJUSTMENT-ROUTES.md)
  — Hoàng Anh: prescription-adjustment read rule còn mở; Surgery counterpart deferred.

## Active source lock

- [Vinh Clinical/Lab closeout](HANDOFF-VINH-OUTPATIENT-MVP.md)
  — V-CLOSE-01 standalone reliability, V-CLOSE-02 Rx implementation và public Lab-order
  acceptance đã có. **V-CLOSE-03 actual paid EXAM/LAB_TEST PASS**; actual paid Rx/recovery có
  thêm evidence sáu-app ở handoff Huy mục 10, global release vẫn riêng. Source/test limits và
  việc còn lại của từng owner nằm ở đầu file; đã dọn các
  backlog lịch sử lặp lại. Lượt implementation trước có 559 tests đạt; đây là evidence lịch sử.
  Fresh Gateway targeted care/Lab-order 183 tests đạt, không failure/error/skip.

[Plan MVP](../plans/05-implementation-plan.md), [contracts](../plans/03-contracts.md) và
[reliability](../plans/04-data-reliability.md) là canonical. Handoff không đổi contract hoặc
bật deployment flags. Xóa handoff khi cả producer/consumer đạt acceptance, chuyển lasting rules
vào canonical docs. Hiện còn gaps nên các handoff active được giữ và cập nhật, không xóa sớm.
Inpatient/Surgery/full V2 deferred, không là blocker đóng ngoại trú.
