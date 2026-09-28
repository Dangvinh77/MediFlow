# HANDOFF — Surgery G0 decisions (H-01.2 / H-01.3)

- **Status:** `OPEN` — Huy-delegated local defaults are recorded below; cross-owner contract confirmations and shared fixtures are still pending.
- **Coordinator / service owner:** Huy (`LQHuy0210`).
- **Owners needed:** Vinh — Clinical/Inpatient; Lộc — Billing/Notification; Hoàng Anh — Organization/Patient/Gateway; Huy — Surgery policy and acceptance.
- **Purpose:** close the episode/referral/event mapping and owner-policy gate for cross-service Surgery behavior. The local domain core and delegated defaults do not satisfy producer/consumer contract acceptance.
- **Updated:** 2026-09-28, audited against working baseline `dd797f9`; platform foundation and initial case/episode domain now exist, with no business API/schema/event or shared registration implied.
- **Canonical sources:** [Care–Finance architecture](../architecture/mediflow-care-finance-redesign.html), [Surgery V2 candidate](../eproject_general_plan/backend-spec/care-finance-v2/11-surgery.md), [CARE-BILLING](care-finance/CONTRACT-CARE-BILLING-01.md), [INPATIENT-SURGERY](care-finance/CONTRACT-INPATIENT-SURGERY-01.md), [SURGERY-BILLING](care-finance/CONTRACT-SURGERY-BILLING-01.md), [IDENTITY-LOOKUP](care-finance/CONTRACT-IDENTITY-LOOKUP-01.md), [CARE-PROJECTIONS](care-finance/CONTRACT-CARE-PROJECTIONS-01.md).

## Gate status

The V2 candidate has enough detail to review, but remains a **candidate**, not an approved cross-service implementation contract. On 2026-09-28 Huy explicitly delegated selection of Surgery-owned V1 defaults; those local choices are recorded in the decision table below and guide Huy-owned domain work. They do not approve another owner's API/job-title/clinical evidence/catalog behavior, nor the wire contracts. Shared root/Gateway/DB/Compose registration remains separately assigned in the bootstrap handoff.

Earlier rows marked as proposals remain proposals. Rows explicitly marked `HUY-DECIDED` record only the local choices delegated to the implementation agent on 2026-09-28; they are not cross-owner approvals. A checkbox or local test never means that Vinh, Lộc or Hoàng Anh approved a contract.

## H-01.2 — episode, referral, charge and event mapping

### A. Episode and referral identity

| Path | Current conflict / Huy-selected V1 mapping | Confirmation needed | Acceptance evidence |
|---|---|---|---|
| Outpatient surgery | `CARE-BILLING` selects `appointmentId` when an appointment exists, otherwise `recordId`; the Surgery candidate currently maps every outpatient episode to `recordId`. **Huy chose:** use the canonical Billing rule; carry `recordId` as clinical context when distinct, never silently replace the selected episode ID. | Vinh + Lộc confirm producer fields and canonical contract. | One request fixture for appointment-backed and walk-in surgery, with exact `careEpisodeType/id`, and mismatch rejection. Update the Surgery candidate and CARE-BILLING/SURGERY-BILLING contract consistently. |
| Admission surgery | **Huy chose:** `careEpisodeType=ADMISSION`, `careEpisodeId=admissionId`; optional `recordId` is context only and cannot select the admission. | Vinh + Lộc confirm producer/clearance fields and canonical contract. | One admission request/clearance fixture proving exact `admissionId`, patient and department match. |
| Referral producer | Architecture allows Clinical/Inpatient to produce `surgery.requested`; producer-to-path ownership and globally stable `surgeryRequestId` are not fixed. **Huy chose as target:** Clinical owns outpatient referrals and Inpatient owns admission referrals; if the same intent can be emitted on both paths, both preserve one shared `surgeryRequestId`. | Vinh confirms producer/path ownership and ability to preserve one business key across duplicate intent. | Producer/path matrix, same-intent duplicate fixture and concurrent create test: at most one case and one charge intent. `eventId` remains delivery identity, not the business key. |

### B. Charge bridge and clearance

There is a contract naming/ownership conflict to resolve: the architecture lists `surgery.requested.v1` from Clinical/Inpatient to Surgery **and Billing**, while `CONTRACT-SURGERY-BILLING-01` describes a Surgery-originated request carrying `surgeryCaseId` and planned items. Those are different facts because Billing cannot use a Surgery case ID before the case exists.

**Huy chose:** keep referral-to-Surgery and post-case charge intent as two semantically distinct facts; do not reuse `surgery.requested` for both meanings. The post-case charge fact should identify `surgeryCaseId` as stable `sourceId`, selected care episode, department, procedure and planned `{itemCode, priceCode, quantity}` lines. Surgery sends codes/quantities only; Billing owns catalog validity and all prices/amounts. Event name, envelope version, routing key and whether Billing may also observe the original referral remain for owner confirmation; do not invent them in code.

The clearance path must be exact and purpose-specific: `purpose=SURGERY`, matching `surgeryCaseId`, patient and selected episode; admission target is required for inpatient surgery. The current Surgery–Billing contract describes admission clearance, while the Surgery candidate supports outpatient cases too.

**Huy chose:** clearance must be purpose-specific (`purpose=SURGERY`) and match exact `surgeryCaseId`, patient and selected episode; admission additionally requires exact `admissionId`. **Still needed from Lộc (and Vinh for episode fields):** confirm outpatient/admission target shape and charge source contract, then provide canonical producer bytes. Acceptance is one same-byte producer/consumer fixture for each supported episode plus wrong-case/wrong-episode rejection. Unknown `priceCode` is a contract/catalog error, never zero-priced.

### C. Surgery event facts and consumer fields

Every event fixture must include the common envelope (`eventId`, `eventType`, `version`, `occurredAt`, `correlationId`, `producer`) and exact producer-owned source/business keys. Owner teams must decide whether corrections are new revisions of an operation or a replacement fact; `eventId` alone does not make a semantic operation unique.

| Event | Candidate/current gap | Required owner decision and fixture |
|---|---|---|
| `surgery.ready` | Candidate has case/admission/record/patient, schedule and readiness snapshot IDs plus `readyAt`; Notification needs a planned-time snapshot, which is absent from the proposed payload. | Huy + Lộc: include the exact planned start/end (or an explicitly agreed immutable schedule snapshot), override indicator and revision/source key. Test notification consumer from the same fixture; no REST lookup to recover the times. |
| `surgery.completed` | `INPATIENT-SURGERY` carries a clinical complications summary; Report needs a stable category. Candidate has performed item/price codes and actual times but correction/revision identity is not defined. | Huy + Vinh + Lộc: agree result/operation ID and correction revision, department/episode fields, actual start/end, performed lines and a controlled `complicationsCategory` separate from any clinical summary. Same bytes must support Inpatient, Billing and Report without exposing clinical summary to aggregate-only Report. |
| `surgery.cancelled` | Candidate has case/admission/record/patient, stage/reason/actor/time, but department/episode and a semantic cancellation key/revision are not complete for Report and idempotent Billing adjustment. | Huy + Vinh + Lộc: include exact episode/department, cancellation operation identity, stage and reason taxonomy. Fixture must show Billing adjustment and Report count without patient-level clinical data. |

## H-01.3 — Huy-delegated local decisions and remaining owner policy inputs

The eight rows below are the candidate's explicit decision gate. The local V1 selections are implementation decisions delegated by Huy; they are not cross-owner approval. Named owners must still confirm their fields and fixtures before the candidate becomes implementation-ready for integrated workflows.

| # | Decision | Huy-selected V1 default | Owner(s) / evidence required | Status |
|---:|---|---|---|---|
| 1 | Episode mapping | **Huy choice:** outpatient selects `appointmentId` when present, otherwise `recordId`; keep a distinct `recordId` as clinical context only. Admission selects exact `admissionId`; never infer by patient/latest record. | Huy choice recorded; Vinh + Lộc still confirm producer fields and provide outpatient/admission + mismatch fixtures. | HUY-DECIDED; JOINT CONTRACT OPEN |
| 2 | Mandatory checklist catalogue source | **Huy choice:** Surgery owns immutable, versioned templates keyed by procedure; cases snapshot template revision/items and template edits never rewrite existing cases. | Vinh must confirm medical mandatory set, exact codes and acceptable evidence sources; versioned sample + complete/missing/stale/mismatch fixtures. | HUY-DECIDED; CLINICAL INPUT OPEN |
| 3 | Consent types and signers | **Huy choice:** V1 models distinct `SURGERY` and `ANESTHESIA` consents with append-only signing/revocation audit; both must be active for READY. JWT-derived actor is the recorder; request body cannot assert actor/role. Guardian authority, witness and who may attest remain fail-closed until clinical/legal policy is confirmed. | Vinh confirms signer/guardian/witness and revocation authority; provide sign/revoke/expiry authorization fixtures. | HUY-DECIDED; CLINICAL POLICY OPEN |
| 4 | Operating-room reference authority | **Huy choice:** do not create a duplicate room master. Store an opaque stable room UUID; require the authoritative owner lookup to confirm room is active before schedule finalization. Failure/unavailable is not treated as active. Surgery owns overlap prevention. | Hoàng Anh confirms room source/API, identifiers and inactive/absent/unavailable semantics; same-room concurrency fixture. | HUY-DECIDED; ORG CONTRACT OPEN |
| 5 | Team-role eligibility | **Huy choice:** V1 roles are `PRIMARY_SURGEON`, `ASSISTANT_SURGEON`, `ANESTHESIOLOGIST`, `OR_NURSE`; login role never proves clinical eligibility. Require active staff lookup plus approved job-title mapping; unknown/inactive/unavailable fails closed. | Hoàng Anh supplies authoritative job-title values and lookup contract; Vinh confirms clinical role meaning; eligible/ineligible fixtures. | HUY-DECIDED; ORG/CLINICAL CONTRACT OPEN |
| 6 | Emergency override | **Huy choice:** disable override in V1; no override endpoint or state transition is exposed. If enabled in a later version, it may bypass only financial clearance and must never bypass clinical/readiness guards. | Vinh + Lộc confirm any future approver-role, self-approval and receivable behavior before a later-version implementation. | HUY-DECIDED; FUTURE CONTRACT OPEN |
| 7 | Cancellation stage / partial abort | **Huy choice:** V1 permits cancellation only before `IN_PROGRESS`; derive stage from persisted state and audit the actor/reason. No `IN_PROGRESS_ABORTED` path in V1; do not call adjustment a refund. | Vinh + Lộc confirm clinical outcome and Billing adjustment/refund semantics before any post-start abort version. | HUY-DECIDED; FUTURE CONTRACT OPEN |
| 8 | Planned/performed item catalogue | **Huy choice:** Surgery persists procedure/item/price codes and positive quantities only; never accepts/calculates amount. Billing owns catalog validity and all prices. | Lộc confirms accepted codes, catalog validation and planned/performed reconciliation; Vinh confirms clinical procedure coding. Same-version catalog fixture required before charge integration. | HUY-DECIDED; BILLING/CLINICAL CONTRACT OPEN |

These are decisions delegated by Huy to the implementation agent on 2026-09-28, not a claim that Huy personally reviewed each row and not approval by Vinh, Lộc or Hoàng Anh. They authorize Huy-owned implementation defaults only. The handoff remains OPEN until the listed external fields and fixtures are confirmed and canonical contracts/specs are updated.

### Additional local state transition choice

**Huy choice:** retain the candidate sequence: prepare/validate schedule during `PREOP_IN_PROGRESS`, evaluate all readiness guards into `READY`, then explicitly finalize `READY → SCHEDULED`; START accepts only `SCHEDULED`. Any checklist, consent, team, clearance or schedule mutation invalidates the prior readiness snapshot and requires re-evaluation; preserve the old snapshot/history. V1 uses UTC instants and half-open `[start,end)` intervals (`end > start`), no implicit preparation buffer, and only finalized schedules reserve room/team resources. Cancellation before START releases that reservation. Hoàng Anh's room/staff lookup and same-resource concurrency fixtures remain required before integration is enabled.

## Owner response checklist

Reply by editing this handoff (or link a canonical contract/spec PR) with the real owner, date and evidence. Do not check a row using a mock-only fixture or another service's database state.

| Owner | Required response | Status / link / date |
|---|---|---|
| Vinh — Clinical/Inpatient | Confirm outpatient/admission referral ownership and stable request identity; validate required checklist/evidence sources and clinical result fields; approve/defer partial-abort semantics. | `OPEN` — fill in after review |
| Lộc — Billing/Notification | Confirm distinct post-case charge fact, price-code contract, outpatient/admission SURGERY clearance and adjustment/refund semantics; confirm ready planned-time consumer fields. | `OPEN` — fill in after review |
| Hoàng Anh — Organization | Confirm room master/lookup owner and active-state behavior; provide authoritative job-title values for team-role mapping. Gateway/build assignments remain tracked in [Surgery foundation bootstrap](HANDOFF-SURGERY-FOUNDATION-BOOTSTRAP.md). | `OPEN` — fill in after review |
| Huy — Surgery | Choose the Surgery-owned V1 defaults in §14, episode mapping, charge/clearance intent and READY/schedule lifecycle. | `LOCAL CHOICES RECORDED BY DELEGATION — 2026-09-28; joint fixtures/other-owner confirmations remain OPEN` |

## Implementation boundaries and close criteria

- This is the single active cross-owner handoff for H-01.2/H-01.3; the existing registry entry points here. Do not create a second handoff for the same Surgery decision set.
- While open, Huy may implement internal behavior covered by the delegated local decisions above. Do not enable or invent unconfirmed producer payloads, event names/routing keys, price codes, Organization identifiers/job-title mappings or clinical evidence rules. Keep cross-service adapters/integration disabled until the named owners supply canonical contracts and fixtures. Do not edit producer-owned modules, query another service database, or modify root/shared/Gateway production files.
- The separate [Surgery foundation bootstrap handoff](HANDOFF-SURGERY-FOUNDATION-BOOTSTRAP.md) tracks explicit shared-build and Gateway ownership; H-01.2/3 approval does not grant Huy authority over those files.
- Close H-01.2/3 only after: (1) all applicable rows above have real owner decisions in canonical contract/spec docs, (2) Huy-local choices are recorded and remaining cross-owner policy fields are confirmed, (3) same-version producer/consumer fixtures and required mismatch/duplicate tests are linked, and (4) this file and the registry are retired/updated in the same change that moves lasting rules to canonical docs.
- D08 Pharmacy admission and D09 Report finance remain tracked in [Huy care-finance consumers](HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md); they are not prerequisites for starting Surgery G0, except where their exact event fields directly participate in a Surgery contract row above.
