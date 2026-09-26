# HANDOFF — Surgery implementation decisions

- **Status:** `OPEN`
- **Coordinator:** Huy (`LQHuy0210`), Surgery owner
- **Owners needed:** Clinical/Inpatient — Vinh; Billing/Notification — Lộc; Organization/Patient/Gateway — Hoàng Anh
- **Purpose:** close the business and cross-service decisions required before the implementation-ready Surgery spec and `surgery-service` code.
- **Canonical contracts:** [`INPATIENT-SURGERY`](care-finance/CONTRACT-INPATIENT-SURGERY-01.md), [`SURGERY-BILLING`](care-finance/CONTRACT-SURGERY-BILLING-01.md), [`CARE-BILLING`](care-finance/CONTRACT-CARE-BILLING-01.md), [`IDENTITY-LOOKUP`](care-finance/CONTRACT-IDENTITY-LOOKUP-01.md), [`CARE-PROJECTIONS`](care-finance/CONTRACT-CARE-PROJECTIONS-01.md).

## Decisions required

| Owner(s) | Decision / contract to lock | Acceptance evidence |
|---|---|---|
| Vinh + Huy + Lộc | **Care episode for surgery:** `surgery.requested` accepts `recordId` or `admissionId`, while the current Surgery–Billing clearance describes an admission. Decide whether record-only outpatient cases must first become admissions, or define the OUTPATIENT_VISIT surgery clearance target and charge. | One canonical request and clearance fixture for each supported episode; exact target IDs and rejection case for a mismatched episode. |
| Vinh + Huy | **Referral producer and identity:** Clinical and/or Inpatient may publish `surgery.requested`. Define which producer owns each referral path and ensure `surgeryRequestId` is globally stable if the same clinical intent crosses both services. | Producer mapping, payload fixture, and duplicate/concurrent request acceptance test that creates at most one case. |
| Huy + Vinh | **Pre-op checklist:** identify which checklist codes are mandatory, how templates vary by procedure, and which service supplies each external fact/evidence. Keep source order/case references exact. | Versioned template or explicit checklist command contract plus fixtures for complete, missing, stale, and mismatched evidence. |
| Huy + Hoàng Anh | **Operating room identity:** Surgery owns schedule-conflict enforcement, but the room master and validity lookup are not assigned. Decide the owner/API and inactive-room behavior, or explicitly assign Surgery its own room catalog. | Room lookup/ownership contract and concurrent same-room/time booking test case. |
| Hoàng Anh + Huy | **Team eligibility:** Organization lookup provides active state, job title, and department; define the authoritative eligibility rule for surgeon, anesthesiologist, and nurse assignments. | Role/job-title mapping and lookup fixtures for eligible, ineligible, inactive, absent, and unavailable staff. |
| Huy + Lộc + Vinh | **Emergency override:** decide whether surgery readiness can bypass any financial/selected guard, exact approval claims/audit fields, and whether bypassed care creates a receivable. Consent and team cannot be fabricated. | Explicit allow/deny policy and versioned audit/event fixtures; if unsupported, record that no override path exists in V1. |
| Huy + Vinh + Lộc | **Cancellation/result boundary:** clarify how a case cancelled `IN_PROGRESS_ABORTED` records performed items and how Billing reconciles partial work; define which cancellation facts Report counts. | One partial-abort fixture with actual performed items, zero performed items, and an idempotent Billing/Report outcome. |
| Huy + Vinh + Lộc | **Inpatient medication:** `careContext=ADMISSION` and exact `admissionId` are required, but dispense authorization and compensation policy are not fully specified. Decide whether inpatient doses are dispensed on prescription, administration record, or another exact clearance/fact. | Contract fixture for eligible dispense, wrong admission, billing failure, and duplicate delivery; keep OUTPATIENT pay-before-dispense unchanged. |
| Lộc + Huy | **Report financial facts:** identify the Billing events/fields that classify cash, deposit liability, earned revenue, refunds, and outstanding receivable; define source transaction keys and original-contribution references. | Same-version Billing producer and Report consumer fixtures, with deposit and refund replay expected totals. |

## Boundaries while open

- Keep the related canonical contracts at `DESIGN_READY`/`BLOCKED`; do not mark a producer or consumer implemented without shared fixtures and tests.
- Do not query another service's database, infer `admissionId` from `patientId`, invent a room/staff identifier, assume a price, or treat a payment event as general surgery clearance.
- Huy may prepare domain tests and non-binding schema alternatives, but production behavior that depends on an undecided row above remains gated.
- The [Huy plan decision log](../superpowers/plans/2026-09-25-huy-surgery-pharmacy-report.md) tracks D01–D12 with approval date/link, fixture and live status. In particular D02's post-case charge source, D05's READY-versus-SCHEDULED ordering, D10's Report metrics, D11's replay compatibility and D12's admission relationship/clearance freshness are **not** resolved by the summary rows above. The [Surgery spec draft](../eproject_general_plan/backend-spec/10-surgery.md) contains rule and contract inventories only, not accepted behavior.

## Close criteria

1. Every row above and every applicable D01–D12 decision in the Huy plan is either decided in its canonical contract/service doc or explicitly excluded from V1, with real approver/date/link evidence.
2. Producer/consumer owners agree on event/API fixtures and version.
3. Huy publishes the implementation-ready Surgery spec with DDL, DTOs, ports, use-case algorithms, and business-rule-to-test mapping.
4. The handoff is removed from [`active handoffs`](README.md) in the same change that records the lasting decisions in canonical docs.
