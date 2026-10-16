# Registry hợp đồng MVP

Refresh **2026-10-16**, source `c8024921`. Registry chỉ giữ dependencies còn mở;
implementation đã merge không được giao lại. Không suy full MVP done từ owner-local tests.

## Active MVP gates

- [Lộc L3/L4 + Hoàng Anh G1 — discovery/publication ngoại trú](HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md)
  — **PARTIAL DELIVERY**. L1 discovery và L2 cohort/eligible writer V22/V23 đã merge;
  fresh targeted Billing 61 tests đạt, không failure/error/skip. Còn actual dispatcher/Rabbit,
  packaged JVM recovery, full regression/artifact delivery (Lộc), exact public discovery GET
  + actual Billing smoke (Hoàng Anh). Không làm lại L1/L2 hoặc Lab-order route đã giao.
- [Huy — Pharmacy, Report và Support](HANDOFF-HUY-MVP.md)
  — một đầu mối cho owner acceptance/activation, Billing/Rx/Report/Notification và Support.
  Outpatient filled relationship và Clinical consumer đã có; paid integration/coverage/global
  rollout vẫn theo acceptance của từng owner. Không giao lại Rx producer recordId fixture.
- [Gateway Billing adjustment routes](HANDOFF-GATEWAY-BILLING-ADJUSTMENT-ROUTES.md)
  — Hoàng Anh: prescription-adjustment read rule còn mở; Surgery counterpart deferred.

## Active source lock

- [Vinh Clinical/Lab closeout](HANDOFF-VINH-OUTPATIENT-MVP.md)
  — V-CLOSE-01 standalone reliability, V-CLOSE-02 Rx implementation và public Lab-order
  acceptance đã có. **V-CLOSE-03 actual paid EXAM/LAB_TEST/Rx còn mở** sau producer/Gateway
  evidence. Source/test limits và việc còn lại của từng owner nằm ở đầu file; đã dọn các
  backlog lịch sử lặp lại. Lượt implementation trước có 559 tests đạt; đây là evidence lịch sử.
  Fresh Gateway targeted care/Lab-order 183 tests đạt, không failure/error/skip.

[Plan MVP](../plans/05-implementation-plan.md), [contracts](../plans/03-contracts.md) và
[reliability](../plans/04-data-reliability.md) là canonical. Handoff không đổi contract hoặc
bật deployment flags. Xóa handoff khi cả producer/consumer đạt acceptance, chuyển lasting rules
vào canonical docs. Hiện còn gaps nên các handoff active được giữ và cập nhật, không xóa sớm.
Inpatient/Surgery/full V2 deferred, không là blocker đóng ngoại trú.
