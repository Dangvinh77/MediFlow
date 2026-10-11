# Care & Finance cross-service handoffs

Đây là registry canonical cho các contract phát sinh từ
[`mediflow-care-finance-redesign.html`](../../architecture/mediflow-care-finance-redesign.html).
Payload chỉ được định nghĩa trong các file dưới đây. Service docs và `AGENTS.md` phải link tới đây,
không copy một bản contract khác.

## Contract registry

| ID | Status | Owners | Nội dung |
|---|---|---|---|
| [`CONTRACT-CARE-BILLING-01`](CONTRACT-CARE-BILLING-01.md) | PARTIAL; medical charge-close, deposit allocation/refund, non-final stock and initial final writer implemented; deposit/top-up, corrections, distributed revoke and release open | Vinh + Lộc + Huy | care episode, charge, purpose-scoped clearance, deposit, settlement |
| [`CONTRACT-INPATIENT-SURGERY-01`](CONTRACT-INPATIENT-SURGERY-01.md) | PARTIAL; local Surgery V1 held producer/fixtures, admission decoding; reference/outpatient/late/live workflows open | Vinh + Huy | admission referral, surgery workflow, inpatient medication |
| [`CONTRACT-SURGERY-BILLING-01`](CONTRACT-SURGERY-BILLING-01.md) | PARTIAL_IMPLEMENTATION; planned issuance, payments/grants, refunds/cancellation and minimal canonical performed proof tested; final recognition, late financial correction, distributed fencing and release open | Huy + Lộc | procedure charge, current surgery clearance, cancellation/refund |
| [`CONTRACT-IDENTITY-LOOKUP-01`](CONTRACT-IDENTITY-LOOKUP-01.md) | PRODUCER-READY / CONSUMER-FIXTURES-PENDING | Hoàng Anh + all consumers | patient/staff/department/room identity, job-title team-role projection, JWT service auth, Gateway routes |
| [`CONTRACT-PATIENT-NOTIFICATION-01`](CONTRACT-PATIENT-NOTIFICATION-01.md) | COMPATIBILITY_LOCKED | Hoàng Anh + Lộc | flat `patient.created` payload and versioned-envelope migration gate |
| [`CONTRACT-CARE-PROJECTIONS-01`](CONTRACT-CARE-PROJECTIONS-01.md) | PARTIAL; supported initial final-only finance, bounded all-five replay and controlled available-history publication implemented; full corrections, operational KPI publication, Gateway ingress and live release remain | Lộc + Huy + event producers | Notification templates and Report projections |

## Service reading matrix

| Service | Must read before integration changes |
|---|---|
| Gateway | IDENTITY-LOOKUP |
| Organization | IDENTITY-LOOKUP |
| Patient | IDENTITY-LOOKUP, PATIENT-NOTIFICATION |
| Clinical | CARE-BILLING, INPATIENT-SURGERY, IDENTITY-LOOKUP, CARE-PROJECTIONS |
| Lab | CARE-BILLING, IDENTITY-LOOKUP, CARE-PROJECTIONS |
| Pharmacy | CARE-BILLING, INPATIENT-SURGERY, SURGERY-BILLING, CARE-PROJECTIONS |
| Billing | CARE-BILLING, SURGERY-BILLING, CARE-PROJECTIONS |
| Notification | CARE-PROJECTIONS, IDENTITY-LOOKUP, [active Notification projection handoff](../HANDOFF-NOTIFICATION-CARE-PROJECTIONS.md) |
| Report | CARE-PROJECTIONS |
| Inpatient | all five contracts |
| Surgery | all five contracts except outpatient-only parts of CARE-BILLING |

## Implementation blockers

Current 2026-10-11 supported release supersedes earlier missing financial publication and mandatory
unavailable-history blockers: later user decision allows finite available-evidence snapshots, with
explicit missing-history metadata. V25 has controlled local catch-up/final source fence, all-five
row/proof reconciliation, immutable acceptance/audit and expected-pointer publication/rollback.
All gates remain OFF. Operational full-KPI publication/Gateway ingress, post-final/operational
corrections and Pharmacy medication authority remain active target work.
[Current canonical release](CONTRACT-CARE-PROJECTIONS-01.md#supported-available-history-release-and-controlled-publication--2026-10-11).

Dated entries below retain their earlier evidence. The current decision supersedes missing finite
financial publication and mandatory unavailable-history statements, not broader target gaps.

2026-10-11 archive continuation: sealed local Report financial sources can be exported/restored
into isolated generations with strict integrity checks and immutable operator audit. The discovered
legacy local volume contains no relevant history; it is not historical no-events approval. Actual
producer historical exports/conversion, source completeness and publication remain open.
[Canonical local recovery](CONTRACT-CARE-PROJECTIONS-01.md#portable-local-financial-evidence-archive--2026-10-11).

2026-10-11 pending/history continuation: local financial contribution/original-linked pending
acceptance passes PG/Rabbit; Report writer fences are now shared account-first with stable scopes.
Pre-V2 history is explicitly required by the user. Automatic published Billing/Pharmacy outbox purge
is separately default OFF, not an archive/completeness guarantee. Historical source exports/backups,
legacy conversion, corrections and controlled publication remain OPEN.
[Canonical historical requirement](CONTRACT-CARE-PROJECTIONS-01.md#financial-account-fencing-and-historical-evidence--2026-10-11).

2026-10-11 continuation: Report V23 adds isolated finite initial five-metric source rebuilding;
accepted local manifest equality is not historical completeness, post-final corrections or V20
publication. Those gates and Pharmacy medication authority remain open; no held delivery is released.
[Canonical finite-source boundary](CONTRACT-CARE-PROJECTIONS-01.md#finite-initial-financial-source-rebuild--2026-10-11).

Latest 2026-10-10 continuation adds an initial-only final Billing writer and isolated Report earned/
original-deposit release kernel. Same-byte initial producer/consumer local acceptance passes;
corrections, full financial rebuild/accepted publication, initial deposit/top-up and live Pharmacy
medication authority remain open. Neither HELD facts nor V20 financial reports are released.
[Current initial-final contract](CONTRACT-CARE-BILLING-01.md#initial-final-settlement-and-explicit-earned-operation--2026-10-10).

Earlier 2026-10-10 prerequisite tranche: medical completed-stay projection, local medical charge-close, original-deposit
allocation/unused-cash refunds and canonical performed Surgery proof are implemented under the user
override. Earned timing is final-settlement-only. That earlier tranche did not provide a completed final writer,
five-metric recognition/reversal/replay/publication or Pharmacy live medication permission.
[Exact verified scope and limits](../../superpowers/plans/2026-10-10-medical-discharge-deposit-finalization.md).

2026-10-10 local update: CARE-BILLING/CARE-PROJECTIONS now define opt-in non-final
`settlement.reconciled` V1 and account-receivable stock with producer-service fixtures and matching
consumer tests. Registry remains PARTIAL: finalization, recognition, history/publication and release
are not supplied by this fact.

Chỉ các blocker còn mở mới tồn tại dưới dạng handoff. Danh sách canonical nằm tại
[`docs/handoffs/README.md`](../README.md). Các rollout đã hoàn thành được ghi trực tiếp vào contract
và service docs; không giữ file handoff lịch sử làm tài liệu bắt buộc.

## Update rule

Một PR đổi contract phải cập nhật registry status, canonical handoff, producer fixture/test và consumer
fixture/test. Nếu không có quyền sửa consumer, PR giữ status chưa `IMPLEMENTED`, ghi blocker và tag
đúng owner trong registry active; không dùng default field hoặc suy luận ID để “cho chạy trước”.
