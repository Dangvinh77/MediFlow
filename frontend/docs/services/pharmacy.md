# Frontend Service: Pharmacy

2026-10-19 signed acceptance PASS: actual issuer DOCTOR creates V1 in the browser, CASHIER public
API pays, PHARMACIST refreshes and dispenses in the browser. Payment alone leaves PENDING;
one dispense reduces stock 40→37 and terminal retry preserves one effect. Actual ADMIN creates
a separate drug and adjusts stock 5→12; known-event replay preserves payload and observed consumer
counts. Cancel/dispense role denial and keyboard/theme evidence are in
[the acceptance record](../evidence/huy-six-service/2026-10-19/README.md). This closes HF6-01/06/07/10;
the remaining Report/Notification/release gates are tracked in plan section 14.

2026-10-19 signed acceptance source procedure: obtain the exact `recordId`, `patientId` and
assigned `doctorId` from the public patient-record response after the source visit is opened.
For the isolated acceptance case, a genuine Clinical Lab order under that record supplies an
explicit `careEpisodeType`/`careEpisodeId` in its public response. This is evidence for that
source visit, not permission to infer an episode from an appointment UUID or to create an
unnecessary Lab order in production. Department/staff identifiers come from the corresponding
Core public setup responses and the signed staff relationship. `DRUG` is used only because this
disposable Billing instance explicitly allows that catalog key; it is not a frontend default.

The paid V1 browser test exposed a read-side defect: the prescription detail consulted only V0
payment receipts. The candidate now reads verified, exact-target, non-expired local V1 clearance
projections without a write lock. `paymentConfirmed` remains a local confirmation indicator;
the dispense command still checks current Billing authority after locks and can reject a stale
or revoked grant. No receipt is manufactured for V1 and payment does not auto-dispense stock.

2026-10-19 five-item follow-up: all Pharmacy commands classify HTTP 408 as uncertain, using a
feature-local helper also shared by stock. V1 retry preserves the original payload/key; drug creation
locks for catalogue reconciliation; cancel/dispense refresh only the read; outbox keeps its known
event and requires deliberate reconfirmation. Unknown replies are not interpreted as field validation
or proof of rollback. See [plan section 13](../huy-six-service-frontend-plan.md#13-đợt-năm-đầu-việc-timeout--2026-10-19)
for tests and remaining signed-browser gates.

2026-10-19 stock reconciliation follow-up: an uncertain adjustment retains its delta/unit and
correlation across failed stock reads and explicit read retries. HTTP 408/5xx/unreadable success
and network failures discard the old draft. A fresh GET does not prove the previous command's
outcome; new adjustments stay closed until the operator confirms reconciliation. That confirmation
only opens an empty draft, never resends the command or certifies backend success. State is scoped
to the current drug/session in memory. See [plan section 12](../huy-six-service-frontend-plan.md#12-giữ-kết-quả-điều-chỉnh-kho-chưa-rõ-qua-lỗi-tải-lại--2026-10-19).

2026-10-19 keyboard follow-up: adding/removing prescription lines retains a usable focus target;
server errors focus the first visible field, independent of detail order. Unknown creation outcome
focuses feedback while preserving the frozen intent; explicit new intent returns to the record field.
See [current plan, section 11](../huy-six-service-frontend-plan.md#11-keyboard-và-focus-sau-đồng-bộ-master--2026-10-19)
for local test evidence and the remaining browser acceptance gate.

**Owner:** Huy (`LQHuy0210`)  
**Backend:** `backend/pharmacy-service/**`  
**Contract context:** [`docs/ai/services/pharmacy.md`](../../../docs/ai/services/pharmacy.md)  
**Detailed task plan:** [`../pharmacy-frontend-implementation-plan.md`](../pharmacy-frontend-implementation-plan.md)

**Current execution plan (2026-10-18):** [Huy FE — six-service MVP closeout](../huy-six-service-frontend-plan.md).
It tracks remaining OUTPATIENT V1 work and real Gateway acceptance. The
[previous themed-UI plan](../huy-frontend-implementation-plan.md) and PH-FE plan retain history.

2026-10-18 B10/B20: feature-local session boundaries reset drafts/results for account changes,
including two accounts with the same role. Old reads/mutation callbacks cannot update the new
workspace. Create-drug unknown outcomes lock until catalog reconciliation; stock unknown outcomes
refresh and discard the old delta. Server 403 closes local retry/mutation controls, including Enter
on outbox confirmation; transient read failures keep explicit retry. These UX guards do not replace
backend authorization. The shared transport's global late-401 race remains in the single handoff.
See plan sections 9/10 and [browser evidence](../evidence/huy-six-service/2026-10-18/README.md).

2026-10-18: `/pharmacy/prescriptions/new` now defaults to OUTPATIENT V1. Its dynamic server route
reads `MEDIFLOW_PHARMACY_PRESCRIPTION_MODE` on each request: unset/`outpatient` renders V1;
`legacy` explicitly renders the standalone V0 compatibility form; any other value closes creation.
Set this in the frontend server environment and restart the server when changing it. This setting
does not enable backend flags or change an MVP backend into a legacy backend. A rejected V1 call
never falls back to V0. Report has a separate, still-default-closed gate documented in its guide.

The form requires confirmed record/patient/staff/department/episode IDs and an exact price catalog
code. It does not infer an episode from record/appointment/account IDs or default the price code.
Until a public Clinical entrypoint supplies those values explicitly, operators must obtain verified
values from the source workflow; missing identifiers remain a runtime dependency, not guessed input.
The public outpatient use case requires a record even though the broad V1 DTO permits null.

V1 dispatch uses the shared POST `idempotencyKey` option. Each intent holds a frozen request and
canonical UUID in memory. An uncertain response locks editing; explicit retry retains the same
snapshot/key. Starting a new draft requires the user to check the old result first. Success, session
change, mode change and unmount discard the old intent; no new intent/PII storage is introduced.
Prescription detail now offers an explicit fresh GET for payment state before dispensing. All
amounts and payment/terminal decisions continue to come from the server. These are local FE changes;
signed-role, paid-source and Gateway/Support browser acceptance remain open in the single handoff.

2026-10-12 implementation checkpoint: themed catalog/local navigation and compact prescription lookup
are implemented; pending dispense/payment proof labels are independent and nullable `recordId` is supported.
The Huy plan records passing local tests/build and isolated browser smoke; real Gateway role smoke remains open.

2026-10-16 checkpoint: drug detail/create/stock and ADMIN outbox now use the approved theme and
explicit request/error states. Drug flows add UUID/DTO guards, field focus, synchronous duplicate-submit
locks and server-returned stock. Conflict/unknown stock outcomes refresh before a new command;
outbox confirmation supports Escape/cancel and distinguishes replayed=false. Drug tests: 40/40;
outbox tests: 11/11. Browser fixture smoke covers create/detail/adjust and mobile layouts. Signed
actor/Gateway/paid-source integration stays open in the [single Huy handoff](../../../docs/handoffs/HANDOFF-HUY-MVP.md).

2026-10-16 prescription continuation: P-05 is FE-local complete; P-06 has all four local subtasks
complete but remains open for real-role Gateway smoke. V0 create uses the shared themed controls,
line-level validation/focus, duplicate-submit locking and the exact server prescription ID. Cancel
and bodyless dispense use labelled inline confirmations with Escape/return-focus, server results
and a fresh read after conflict or an unknown outcome. A failed follow-up read preserves successful
mutation evidence and retries only the read. Added tests: 51; full FE suite: 262/262 across 34 files;
typecheck/lint/production build pass. Isolated browser fixtures cover create/cancel/dispense and
desktop/mobile themes; they do not prove signed identity, Gateway roles or outpatient V1 activation.
See [checkpoint 14](../huy-frontend-implementation-plan.md#14-checkpoint-kê-đơn-và-terminal-actions--2026-10-16).

## Writable frontend scope

- `frontend/src/features/pharmacy/**`
- `frontend/src/app/(dashboard)/pharmacy/**`
- `frontend/docs/pharmacy-frontend-implementation-plan.md`

## Current UI baseline

Pharmacy currently has the deepest frontend implementation: drug catalog/detail/create/stock
adjustment; prescription create/lookup/detail; cancel and dispense actions; and an ADMIN outbox
replay screen. Route gates distinguish catalog/detail access, pharmacist inventory actions, doctor
prescription creation, terminal actions, and ADMIN replay.

## Owner queue

- Continue the exact next unfinished task in the detailed Pharmacy plan; update its evidence in the
  same PR.
- `FE-PHARMACY-CONTRACT` — recheck live controller/DTO enums before every mutation slice.
- `FE-PHARMACY-TEST` — add focused validation, permission, terminal-state, and API error tests after
  the shared test harness exists.
- `FE-PHARMACY-RELEASE` — capture successful typecheck/lint/build and role smoke-test evidence.

## Handoffs and blockers

- Staff-scoped end-to-end behavior needs a signed, stable `staffId`. Producer: Gateway and
  Organization / Hoàng Anh. Acceptance: documented identity claim/mapping enforced by the backend.
- Prescription creation consumes Clinical identifiers. Producer: Clinical / Vinh. Never infer a
  record or patient association from the current user.
- Billing and Notification saga behavior stays in those services; Pharmacy UI displays only fields
  explicitly present in Pharmacy contracts.

## Contract and verification gate

Do not duplicate the detailed task checklist here. Follow the linked plan, preserve terminal-state
guards, and keep replay restricted to a known event ID and ADMIN UX gate.

Run `pnpm typecheck`, `pnpm lint`, `pnpm build`, focused Pharmacy tests when available, and smoke
test role-specific create/adjust/cancel/dispense/replay actions affected by the change.
