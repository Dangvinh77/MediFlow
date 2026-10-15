# Registry hợp đồng MVP

Handoff/backlog lịch sử đã thay bằng [plan MVP](../plans/05-implementation-plan.md).
Đây không là xác nhận mọi integration gap đã được giải quyết.

[Contracts canonical](../plans/03-contracts.md) quy định source truth và modes.
Mỗi gap MVP ghi trong task: producer, consumer, missing behavior/field, owner và acceptance
fixture/test. Không copy payload thành nhiều file handoff. Không vượt service ownership.
Inpatient/Surgery/full V2 deferred, không trở thành blocker để đóng luồng ngoại trú.

## Active source lock

- [Vinh Clinical/Lab outpatient MVP source lock and open dependencies](HANDOFF-VINH-OUTPATIENT-MVP.md) — partial implementation evidence; does not close M6-02, M6-09, M6-11, M6-14, or end-to-end acceptance.
- [Pharmacy Core identity lookup retarget](HANDOFF-PHARMACY-CORE-LOOKUPS.md) — Huy-owned caller update after Organization and Patient source modules move into Core.
- [Gateway allowlist missing for two live Billing read endpoints](HANDOFF-GATEWAY-BILLING-ADJUSTMENT-ROUTES.md) — Hoàng Anh-owned allowlist rule for `prescription-adjustments` and `surgery-cancellations`, found while building their frontend.

## Deferred compatibility

- [Surgery references to Core](HANDOFF-SURGERY-CORE-PATHS.md) — Huy-owned caller and fixture path update when Surgery is reactivated; outside outpatient MVP acceptance.
