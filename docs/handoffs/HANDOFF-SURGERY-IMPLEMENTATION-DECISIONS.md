# HANDOFF — Surgery G0 decisions (H-01.2 / H-01.3)

- **Status:** `OPEN` — shared proposal and decision checklist prepared; no cross-owner approvals or shared fixtures are recorded yet.
- **Coordinator / service owner:** Huy (`LQHuy0210`).
- **Owners needed:** Vinh — Clinical/Inpatient; Lộc — Billing/Notification; Hoàng Anh — Organization/Patient/Gateway; Huy — Surgery policy and acceptance.
- **Purpose:** close the episode/referral/event mapping and owner-policy gate before creating the Surgery service scaffold.
- **Updated:** 2026-09-28, audited against working baseline `f69dd1d`; Pharmacy/Report local foundation is unrelated to Surgery approval.
- **Canonical sources:** [Care–Finance architecture](../architecture/mediflow-care-finance-redesign.html), [Surgery V2 candidate](../eproject_general_plan/backend-spec/care-finance-v2/11-surgery.md), [CARE-BILLING](care-finance/CONTRACT-CARE-BILLING-01.md), [INPATIENT-SURGERY](care-finance/CONTRACT-INPATIENT-SURGERY-01.md), [SURGERY-BILLING](care-finance/CONTRACT-SURGERY-BILLING-01.md), [IDENTITY-LOOKUP](care-finance/CONTRACT-IDENTITY-LOOKUP-01.md), [CARE-PROJECTIONS](care-finance/CONTRACT-CARE-PROJECTIONS-01.md).

## Gate status

The V2 candidate has enough detail to review, but remains a **candidate**, not an approved implementation contract. The Huy plan requires H-01.2 and H-01.3 plus confirmation that the shared-build integrator has accepted the bootstrap handoff before S-01.1. Do not scaffold or represent any row below as approved until its named owners record a decision here and update the canonical contract/spec as needed.

Huy has prepared proposed defaults to accelerate review. They are proposals only. A checkbox, local test, or this handoff does not mean that Vinh, Lộc, Hoàng Anh, or Huy approved a business decision.

## H-01.2 — episode, referral, charge and event mapping

### A. Episode and referral identity

| Path | Current conflict / proposed V1 mapping | Confirmation needed | Acceptance evidence |
|---|---|---|---|
| Outpatient surgery | `CARE-BILLING` selects `appointmentId` when an appointment exists, otherwise `recordId`; the Surgery candidate currently maps every outpatient episode to `recordId`. **Proposal:** use the canonical Billing rule; carry `recordId` as a clinical reference when distinct, not as a silent replacement for the selected episode ID. | Huy + Vinh + Lộc | One request fixture for appointment-backed and walk-in surgery, with exact `careEpisodeType/id`, and mismatch rejection. Update the Surgery candidate and CARE-BILLING/SURGERY-BILLING contract consistently. |
| Admission surgery | `careEpisodeType=ADMISSION`, `careEpisodeId=admissionId`; optional `recordId` is context only and cannot select the admission. | Huy + Vinh + Lộc | One admission request/clearance fixture proving exact `admissionId`, patient and department match. |
| Referral producer | Architecture allows Clinical/Inpatient to produce `surgery.requested`; the producer-to-path ownership and globally stable `surgeryRequestId` are not fixed. **Proposal:** Clinical owns outpatient referrals and Inpatient owns admission referrals; if the same intent can be emitted on both paths, both must preserve one shared `surgeryRequestId`. | Vinh + Huy | Producer/path matrix, same-intent duplicate fixture and concurrent create test: at most one case and one charge intent. `eventId` remains delivery identity, not the business key. |

### B. Charge bridge and clearance

There is a contract naming/ownership conflict to resolve: the architecture lists `surgery.requested.v1` from Clinical/Inpatient to Surgery **and Billing**, while `CONTRACT-SURGERY-BILLING-01` describes a Surgery-originated request carrying `surgeryCaseId` and planned items. Those are different facts because Billing cannot use a Surgery case ID before the case exists.

**Proposal:** keep referral-to-Surgery and post-case charge intent as two semantically distinct facts. Do not reuse `surgery.requested` for both meanings. The post-case charge fact should identify `surgeryCaseId` as the stable `sourceId`, selected care episode, department, procedure and planned `{itemCode, priceCode, quantity}` lines. Surgery sends codes/quantities only; Billing owns catalog validity and all prices/amounts. Event name, envelope version and whether Billing may also observe the original referral remain for owner decision; do not invent a routing key in code.

The clearance path must be exact and purpose-specific: `purpose=SURGERY`, matching `surgeryCaseId`, patient and selected episode; admission target is required for inpatient surgery. The current Surgery–Billing contract describes admission clearance, while the Surgery candidate supports outpatient cases too.

**Decision requested from Lộc + Huy (and Vinh for episode fields):** confirm the outpatient `SURGERY` clearance target and charge source contract, and provide canonical producer bytes. Acceptance is one same-byte producer/consumer fixture for each supported episode plus wrong-case/wrong-episode rejection. Unknown `priceCode` is a contract/catalog error, never zero-priced.

### C. Surgery event facts and consumer fields

Every event fixture must include the common envelope (`eventId`, `eventType`, `version`, `occurredAt`, `correlationId`, `producer`) and exact producer-owned source/business keys. Owner teams must decide whether corrections are new revisions of an operation or a replacement fact; `eventId` alone does not make a semantic operation unique.

| Event | Candidate/current gap | Required owner decision and fixture |
|---|---|---|
| `surgery.ready` | Candidate has case/admission/record/patient, schedule and readiness snapshot IDs plus `readyAt`; Notification needs a planned-time snapshot, which is absent from the proposed payload. | Huy + Lộc: include the exact planned start/end (or an explicitly agreed immutable schedule snapshot), override indicator and revision/source key. Test notification consumer from the same fixture; no REST lookup to recover the times. |
| `surgery.completed` | `INPATIENT-SURGERY` carries a clinical complications summary; Report needs a stable category. Candidate has performed item/price codes and actual times but correction/revision identity is not defined. | Huy + Vinh + Lộc: agree result/operation ID and correction revision, department/episode fields, actual start/end, performed lines and a controlled `complicationsCategory` separate from any clinical summary. Same bytes must support Inpatient, Billing and Report without exposing clinical summary to aggregate-only Report. |
| `surgery.cancelled` | Candidate has case/admission/record/patient, stage/reason/actor/time, but department/episode and a semantic cancellation key/revision are not complete for Report and idempotent Billing adjustment. | Huy + Vinh + Lộc: include exact episode/department, cancellation operation identity, stage and reason taxonomy. Fixture must show Billing adjustment and Report count without patient-level clinical data. |

## H-01.3 — proposed owner-policy decisions from Surgery §14

The eight rows below are the candidate's explicit decision gate. “Proposed default” accelerates review; it is **not approval**. Huy must record an explicit accept/reject with date, and named cross-service owners must confirm their fields before the candidate becomes implementation-ready.

| # | Decision | Proposed V1 default for review | Owner(s) / evidence required | Status |
|---:|---|---|---|---|
| 1 | Episode mapping | Use the canonical outpatient appointment-or-walk-in rule above; admission uses exact `admissionId`. Never infer by patient or latest record. | Huy + Vinh + Lộc; request and clearance fixtures for outpatient/admission and mismatch rejection. | OPEN |
| 2 | Mandatory checklist catalogue source | Surgery owns immutable, versioned templates keyed by procedure; Clinical/Inpatient supplies/validates the source facts and medical mandatory set. Case snapshots keep the template revision; a template update never rewrites existing cases. Exact codes and valid evidence sources remain to be supplied by Vinh/Huy. | Huy + Vinh; versioned sample template plus complete/missing/stale/mismatched evidence fixtures. | OPEN |
| 3 | Consent types and signers | Keep consent typed and auditable (`ACTIVE`/`REVOKED`); never reduce consent to a boolean. Proposed separate surgery and anesthesia consent categories. Exact signer authority, guardian handling, witness requirement and revocation role are not inferred here. | Huy + Vinh; accepted consent types/signers and sign/revoke/expiry authorization tests. | OPEN |
| 4 | Operating-room reference authority | Use one authoritative active-room source and stable opaque room reference; Surgery owns overlap enforcement, not a second room master. The current Organization API has no room lookup. Decide whether Organization supplies a lookup or explicitly assigns a Surgery-owned room catalog. | Huy + Hoàng Anh; owner/API, inactive/absent/unavailable semantics and concurrent same-room overlap test. | OPEN |
| 5 | Team-role eligibility | Map explicit Surgery `teamRole` values to Organization job-title values; never use login role (`ADMIN`/`DOCTOR`) as clinical eligibility. Proposed roles are primary surgeon, assistant surgeon, anesthesiologist and operating-room nursing roles; exact enum/job-title mapping must be confirmed against Organization's real values. | Huy + Hoàng Anh (+ Vinh for clinical role meaning); eligible/ineligible/inactive/absent/unavailable fixtures and lookup contract. | OPEN |
| 6 | Emergency override | Candidate permits only `FINANCIAL_EMERGENCY`; it cannot bypass indication, consent, checklist, team or room/time. Proposal: keep the feature disabled until verified approver-role allow-list and Billing receivable behavior are explicit; never accept approver identity/role from request body. | Huy + Vinh + Lộc; role/self-approval policy, audit fields, financial outcome and positive/negative same-version fixture. | OPEN |
| 7 | Cancellation stage / partial abort | Derive stage from persisted state, never trust client stage. Proposal: V1 supports cancellation before START only; defer `IN_PROGRESS_ABORTED` until actual performed-item, Billing adjustment and Report correction semantics are agreed. Preserve completed payments; do not call an adjustment a refund until Billing emits a completed refund fact. | Huy + Vinh + Lộc; before-start cancellation fixture now, and an explicit partial-abort fixture before enabling that path. | OPEN |
| 8 | Planned/performed item catalogue | Billing owns price/catalog truth. Surgery stores stable procedure/item/price codes and quantity, never authoritative amount; unknown codes reject before charge/result commit. Planned-to-performed delta and catalog version/effective-date behavior remain to be agreed. | Huy + Lộc (+ Vinh for clinical procedure coding); catalog fixture, planned-vs-performed reconciliation and unknown-code rejection. | OPEN |

### Additional local state transition choice

The candidate resolves the READY/schedule cycle by preparing and confirming room/time during `PREOP_IN_PROGRESS`, evaluating all guards into `READY`, then explicitly finalizing `READY → SCHEDULED`; START accepts only `SCHEDULED`. **Huy proposal:** retain that sequence. The exact invalidation/re-ready revision, slot expiry/overrun and resource release rules still need Huy's explicit acceptance before the affected S-05/S-06 implementation slice.

## Owner response checklist

Reply by editing this handoff (or link a canonical contract/spec PR) with the real owner, date and evidence. Do not check a row using a mock-only fixture or another service's database state.

| Owner | Required response | Status / link / date |
|---|---|---|
| Vinh — Clinical/Inpatient | Confirm outpatient/admission referral ownership and stable request identity; validate required checklist/evidence sources and clinical result fields; approve/defer partial-abort semantics. | `OPEN` — fill in after review |
| Lộc — Billing/Notification | Confirm distinct post-case charge fact, price-code contract, outpatient/admission SURGERY clearance and adjustment/refund semantics; confirm ready planned-time consumer fields. | `OPEN` — fill in after review |
| Hoàng Anh — Organization | Confirm room master/lookup owner and active-state behavior; provide authoritative job-title values for team-role mapping. Gateway/build assignments remain tracked in [Surgery foundation bootstrap](HANDOFF-SURGERY-FOUNDATION-BOOTSTRAP.md). | `OPEN` — fill in after review |
| Huy — Surgery | Accept/reject each proposed §14 choice; record approver identity, date and canonical link. | `OPEN` — fill in after review |

## Implementation boundaries and close criteria

- This is the single active cross-owner handoff for H-01.2/H-01.3; the existing registry entry points here. Do not create a second handoff for the same Surgery decision set.
- While open, only source-backed audit and non-binding proposals are allowed. Do not write the Surgery scaffold, invent identifiers/event names/price codes, edit producer-owned modules, query another service database, or modify root/shared/Gateway production files.
- The separate [Surgery foundation bootstrap handoff](HANDOFF-SURGERY-FOUNDATION-BOOTSTRAP.md) tracks explicit shared-build and Gateway ownership; H-01.2/3 approval does not grant Huy authority over those files.
- Close H-01.2/3 only after: (1) all applicable rows above have real owner decisions in canonical contract/spec docs, (2) candidate §14 is explicitly accepted by Huy, (3) same-version producer/consumer fixtures and required mismatch/duplicate tests are linked, and (4) this file and the registry are retired/updated in the same change that moves lasting rules to canonical docs.
- D08 Pharmacy admission and D09 Report finance remain tracked in [Huy care-finance consumers](HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md); they are not prerequisites for starting Surgery G0, except where their exact event fields directly participate in a Surgery contract row above.
