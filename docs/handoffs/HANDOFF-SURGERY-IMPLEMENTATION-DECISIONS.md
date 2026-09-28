# HANDOFF — Surgery implementation decisions

- **Status:** `OPEN`
- **Coordinator:** Huy (`LQHuy0210`), Surgery owner
- **Owners needed:** Clinical/Inpatient — Vinh; Billing/Notification — Lộc; Organization/Patient/Gateway — Hoàng Anh
- **Purpose:** accept the detailed Care–Finance V2 Surgery candidate and resolve its remaining business/cross-service gaps before `surgery-service` production code.
- **Canonical contracts:** [`INPATIENT-SURGERY`](care-finance/CONTRACT-INPATIENT-SURGERY-01.md), [`SURGERY-BILLING`](care-finance/CONTRACT-SURGERY-BILLING-01.md), [`CARE-BILLING`](care-finance/CONTRACT-CARE-BILLING-01.md), [`IDENTITY-LOOKUP`](care-finance/CONTRACT-IDENTITY-LOOKUP-01.md), [`CARE-PROJECTIONS`](care-finance/CONTRACT-CARE-PROJECTIONS-01.md).

## Reassessment — 2026-09-27

The [V2 Surgery candidate](../eproject_general_plan/backend-spec/care-finance-v2/11-surgery.md)
now supplies DDL, DTOs, readiness guards and an explicit §14 acceptance gate. Its choices include
preparing a confirmed schedule during PREOP before READY/SCHEDULED, financial-only emergency
override, and one result per case. These are no longer unspecified alternatives, but the candidate
gate and shared contract tests are not satisfied merely by the documentation merge.

At code baseline `30e0296`, Surgery is absent and Inpatient has technical foundation only. Patient's
service-only existence endpoint is present; Organization still exposes the doctor-specific
`/staff/{id}/exists`, not the new generic staff/department lookups. No new Surgery/referral/clearance
producer fixture or end-to-end run was established by the static audit.

Remaining priorities are the outpatient appointment-versus-record episode conflict, referral-to-case
charge bridge, checklist/template/evidence rules, room/team authority and concurrency, consent and
invalidation, and partial-abort/result contracts. The candidate's cancellation state chart excludes
in-progress cancellation while its enum includes `IN_PROGRESS_ABORTED`; this requires an explicit
V1 policy. Producer payloads must also align planned-time data for Notification, complication
summary/category for Inpatient/Report, and cancellation dimensions for Report.

See the [revised Huy plan, D01–D12 and H/S tasks](../superpowers/plans/2026-09-25-huy-surgery-pharmacy-report.md)
for code evidence and slice-level gates. This reassessment does not record owner approval or close
any shared contract.

## Decisions required

| Owner(s) | Decision / contract to lock | Acceptance evidence |
|---|---|---|
| Vinh + Huy + Lộc | **Care episode for surgery:** `surgery.requested` accepts `recordId` or `admissionId`, while the current Surgery–Billing clearance describes an admission. Decide whether record-only outpatient cases must first become admissions, or define the OUTPATIENT_VISIT surgery clearance target and charge. | One canonical request and clearance fixture for each supported episode; exact target IDs and rejection case for a mismatched episode. |
| Vinh + Huy | **Referral producer and identity:** Clinical and/or Inpatient may publish `surgery.requested`. Define which producer owns each referral path and ensure `surgeryRequestId` is globally stable if the same clinical intent crosses both services. | Producer mapping, payload fixture, and duplicate/concurrent request acceptance test that creates at most one case. |
| Huy + Vinh | **Pre-op checklist:** identify which checklist codes are mandatory, how templates vary by procedure, and which service supplies each external fact/evidence. Keep source order/case references exact. | Versioned template or explicit checklist command contract plus fixtures for complete, missing, stale, and mismatched evidence. |
| Huy + Hoàng Anh | **Operating room identity:** Surgery owns schedule-conflict enforcement, but the room master and validity lookup are not assigned. Decide the owner/API and inactive-room behavior, or explicitly assign Surgery its own room catalog. | Room lookup/ownership contract and concurrent same-room/time booking test case. |
| Hoàng Anh + Huy | **Team eligibility:** the V2 lookup target provides active state, job title, and department, but CURRENT code only provides doctor eligibility. Implement the generic lookup and define the authoritative surgeon/anesthesiologist/nurse mapping. | Role/job-title mapping and lookup fixtures for eligible, ineligible, inactive, absent, and unavailable staff. |
| Huy + Lộc + Vinh | **Emergency override:** decide whether surgery readiness can bypass any financial/selected guard, exact approval claims/audit fields, and whether bypassed care creates a receivable. Consent and team cannot be fabricated. | Explicit allow/deny policy and versioned audit/event fixtures; if unsupported, record that no override path exists in V1. |
| Huy + Vinh + Lộc | **Cancellation/result boundary:** clarify how a case cancelled `IN_PROGRESS_ABORTED` records performed items and how Billing reconciles partial work; define which cancellation facts Report counts. | One partial-abort fixture with actual performed items, zero performed items, and an idempotent Billing/Report outcome. |
| Huy + Vinh + Lộc | **Inpatient medication:** Pharmacy V2 selects one slip per prescription and an active admission projection matching patient/department, separate from outpatient clearance. Resolve medical-discharge eligibility, transfer/freshness/out-of-order lifecycle and charge adjustments; multiple-dose administration is not the V1 target. | Same-version fixtures for eligible, wrong/closed admission, lifecycle reordering, failed/cancelled/expired dispensing and duplicate delivery; preserve version-0 outpatient compatibility. |
| Lộc + Huy | **Report financial facts:** identify the Billing events/fields that classify cash, deposit liability, earned revenue, refunds, and outstanding receivable; define source transaction keys and original-contribution references. | Same-version Billing producer and Report consumer fixtures, with deposit and refund replay expected totals. |

## Boundaries while open

- Keep the related canonical contracts at `DESIGN_READY`/`BLOCKED`; do not mark a producer or consumer implemented without shared fixtures and tests.
- Do not query another service's database, infer `admissionId` from `patientId`, invent a room/staff identifier, assume a price, or treat a payment event as general surgery clearance.
- Huy may prepare domain tests and non-binding schema alternatives, but production behavior that depends on an undecided row above remains gated.
- The [Huy plan](../superpowers/plans/2026-09-25-huy-surgery-pharmacy-report.md) separates spec choices, code evidence, shared fixtures and runtime acceptance. D05's basic schedule ordering now has a candidate answer; invalidation/re-ready semantics remain open. D02 charge source, D09/D10 metric inputs and D11/D12 replay/freshness still need the specific evidence listed there. The [older Surgery draft](../eproject_general_plan/backend-spec/10-surgery.md) remains historical preparation; the V2 candidate is the target to complete, not another blank spec to recreate.

## Close criteria

1. Every row above and every applicable D01–D12 decision in the Huy plan is either decided in its canonical contract/service doc or explicitly excluded from V1, with real approver/date/link evidence.
2. Producer/consumer owners agree on event/API fixtures and version.
3. Huy publishes the implementation-ready Surgery spec with DDL, DTOs, ports, use-case algorithms, and business-rule-to-test mapping.
4. The handoff is removed from [`active handoffs`](README.md) in the same change that records the lasting decisions in canonical docs.
