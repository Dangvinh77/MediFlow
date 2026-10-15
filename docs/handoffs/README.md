# Registry hợp đồng MVP

Handoff/backlog lịch sử đã thay bằng [plan MVP](../plans/05-implementation-plan.md).
Đây không là xác nhận mọi integration gap đã được giải quyết.

[Contracts canonical](../plans/03-contracts.md) quy định source truth và modes.
Mỗi gap MVP ghi trong task: producer, consumer, missing behavior/field, owner và acceptance
fixture/test. Không copy payload thành nhiều file handoff. Không vượt service ownership.
Inpatient/Surgery/full V2 deferred, không trở thành blocker để đóng luồng ngoại trú.

## Active source lock

- [Vinh Clinical/Lab closeout, source lock and owner handoffs — 2026-10-15](HANDOFF-VINH-OUTPATIENT-MVP.md) — owned implementation/test evidence; M6 toàn nhóm và end-to-end acceptance còn mở. V-CLOSE-01 đã có standalone recovery proof; Vinh còn V-CLOSE-02/03; không làm lại Lab authority/inbox đã merge.
  H-VINH-01: Hoàng Anh/Gateway-Core; H-VINH-02: Huy/Pharmacy-Report + Vinh/Clinical consumer;
  H-VINH-03: Lộc/Billing. Từng handoff có source gap, producer/consumer, fixture/test và điều kiện đóng.
- [Pharmacy Core identity lookup retarget](HANDOFF-PHARMACY-CORE-LOOKUPS.md) — Huy-owned caller update after Organization and Patient source modules move into Core.

## Deferred compatibility

- [Surgery references to Core](HANDOFF-SURGERY-CORE-PATHS.md) — Huy-owned caller and fixture path update when Surgery is reactivated; outside outpatient MVP acceptance.
