# Huy cross-service dependency implementation — 2026-10-05

The user explicitly authorized implementation of missing dependencies in the other developers'
services for the Huy Surgery/Pharmacy/Report plan. This is a task-scoped ownership exception for
Organization, Patient, Gateway, Clinical, Lab, Inpatient, Billing and Notification where they supply
these dependencies. It does not change their permanent ownership or approve unrelated rewrites.
Keep Huy's Git identity. Shared root/Common/infrastructure changes still require an actual need and
their assigned scope. Preserve additive migrations and existing public/event compatibility.

## Execution order

1. Gateway: route the implemented Surgery pre-op/cancel commands behind a disabled route flag;
   align the two existing Report operations GET endpoints with DOCTOR access; prove default deny,
   trusted identity/correlation propagation and downstream error preservation.
2. Organization → Surgery: provide a real operating-room authority and explicit staff surgical
   capabilities. Keep clinical qualification distinct from human login roles and generic job titles.
   Add producer fixtures and Surgery consumer tests using the same bytes before using this authority.
3. Inpatient/Clinical → Surgery/Pharmacy: exact relationship/referral/reference registration and
   placement/discharge facts, source revisions and deterministic duplicate/out-of-order behavior.
4. Billing → Surgery/Pharmacy/Report: purpose-specific clearance and explicit classified financial
   facts. Derive amounts only from the Billing ledger and catalogue, never from consumer guesses.
5. Surgery producers → Inpatient/Billing/Notification/Report: shared result/cancellation/ready
   identities and fixtures, with provisional READY distinguished from finalized reservation.
6. Verify each completed vertical slice and update the original plan and active handoffs. Keep
   unresolved acceptance open. Do not replace implementation evidence with a checkbox count.

No clinical checklist catalogue, consent/legal authority or staff qualification is seeded from a
guess. Administrative records establish explicit authority; missing records fail closed.

## Implemented slices

Baseline: `7121330` on Huy, plus its existing post-commit changelog entry. Changes below are in the
working tree, not committed or pushed. This is the first dependency batch, **not completion of all
cross-service tasks or 50 additional tasks**.

| Slice | Code and acceptance obtained | Acceptance still open |
|---|---|---|
| Gateway | Conditional `lb://surgery-service` route, exact POST preop/cancel roles; exact Report operations GET DOCTOR access without legacy revenue access; internal authority paths blocked externally; actual Gateway transport/default-deny tests | Discovery test resolves to an HTTP stub, not the real Surgery process/Eureka; full multi-service runtime/E2E |
| Organization → Surgery | Real room catalogue + explicit interval/department-scoped surgical capabilities; ADMIN compare-and-set decisions with verified actor/reason; atomic durable audit/outbox; service-only lookups; Surgery consumes actual producer fixture bytes and uses eligibility for draft preparation | Real approved reference data, clinical policy/catalogue, full finalization/START revalidation, live invalidation consumer |
| Inpatient → Surgery | Exact-admission service projection with patient/department/source record/status/row revision and observed time; Surgery draft rejects mismatched/inactive medical window before case/schedule mutation | Referral proof, current placement/transfer policy, finalization/START/medication fencing and live lifecycle activation |

Organization capability is **not inferred from account role or DOCTOR job title**. A role-compatible
staff member still needs an explicit ADMIN grant covering the entire requested interval. Department
transfer invalidates the old scoped grant; inactive staff/department, expired interval and revoked
grant fail closed. Existing rooms can be deactivated even after department type/status changes;
new or activated rooms must satisfy the clinical-department rule.

Admission `sourceRevision` is the existing admission row optimistic version (including `0`), **not**
a placement/referral/global event version. Organization revision similarly covers only the room or
capability decision. Neither field is an invented clinical authority. New lookup absence is HTTP
200 with `exists=false`; missing deployed endpoint/HTTP 404/outage/malformed or stale response is
unavailable, never positive authority or confirmed absence. Freshness: maximum observation age 30s,
future skew 5s. These lookups prepare a **non-reserving DRAFT**, not READY/finalized surgery.

### Rollout and compatibility

- `MEDIFLOW_GATEWAY_SURGERY_ENABLED=false` by default; enabling it only exposes routes for
  implemented downstream commands. Downstream `mediflow.features.surgery.enabled` remains OFF.
- `MEDIFLOW_ORGANIZATION_SURGERY_AUTHORITY_OUTBOX_ENABLED=false` by default. Decisions still
  append durable pending audit events. Enable delivery only after a durable consumer queue/binding
  is present; timeout/nack/mandatory return leaves the event pending. At-least-once delivery uses
  a stable event ID. This is not an implemented live Surgery invalidation listener.
- Organization V4 (renumbered after master's V3 room lookup during 2026-10-06 sync) is additive and unseeded: `operating_room`, `surgical_capability`,
  `surgery_authority_outbox`. Existing department/staff/accounts, APIs and generic identity
  lookups remain compatible. Inpatient needs no migration and keeps its existing public DTOs.
- New service-authority lookups require signed `type=service`, `role=SYSTEM` tokens with issuedAt,
  expiry, maximum 60s lifetime and no human role escalation. HTTP examples are in Organization
  `surgery-authority.http` and Inpatient `inpatient.http`.
- No production edits to Patient/Clinical/Lab/Billing/Notification/Pharmacy/Report in this batch;
  their current producer behavior was inspected, not fabricated. Root/Common/Compose/CI/scripts
  remain unchanged. Existing `.changelog/entries.jsonl` entry is preserved.

## Verification evidence

- Root `mvn -q '-Dapi.version=1.44' test`: exit **0**, **1,696 tests / 305 suites**, no failures,
  errors or skipped tests. This includes Gateway 32, Organization 107, Inpatient 97, Surgery 166,
  Pharmacy 404 and Report 244. All other backend module tests also pass.
- Final Organization deactivation rule and MapStruct DTO mapping review:
  `mvn -q -pl backend/organization-service -am '-Dapi.version=1.44' test` exit **0**;
  Organization **109 tests / 23 suites**, zero failures/errors/skips. This rerun includes two
  additional deactivation/create/activation rule cases and the final source wiring. Do not add
  overlapping runs together or claim the root reactor ran 1,698 tests after this targeted rerun.
- Real Docker Desktop, `postgres:16-alpine` (16.14) and `rabbitmq:3.13-alpine` ran integration
  tests. Organization fresh V1→V2→V3 and populated V2→V3 compatibility, concurrent expected-revision
  race, injected outbox failure rollback, unroutable retry/confirmed delivery are covered by
  `SurgeryAuthorityIntegrationTest` (6 cases).
- Producer semantics/fixtures: `SurgeryAuthorityServiceTest`, `SurgicalCapabilityTest`,
  `LookupAdmissionAuthorityServiceTest`; actual admission SQL/state/version by
  `AdmissionAuthorityIntegrationTest`; signed-token access, spoofed actor and outage/error checks
  by both authority controller test classes and `InpatientServiceSmokeTest`.
- Surgery `SurgeryAuthorityLookupHttpTest` sends real Feign requests to HTTP stubs serving the
  exact sibling producer fixture files. It checks token/correlation/echo/interval/revision,
  missing/inactive/active states and invalid/outage responses. `SurgeryScheduleApplicationServiceTest`
  checks admission mismatches/discharged window without mutation. Existing PostgreSQL draft,
  rollback and reservation tests pass unchanged apart from the new ports.
- `SurgeryGatewayWebTest` covers discovery routing/path/body, JWT/trusted actor/correlation,
  downstream 422 propagation, disallowed roles and unsupported commands; `GatewayWebTest`
  verifies Report operations vs legacy revenue and blocked external authority lookups.
- None of the fixture/HTTP-stub checks above is labelled real producer→consumer E2E.
- `git diff --check` passes. No commit/push was performed in this coding request.

### Same-byte fixture manifest (SHA-256)

Surgery reads these **producer files directly**; there are no copied consumer JSON variants.

| Producer fixture directory | File | SHA-256 |
|---|---|---|
| `backend/organization-service/src/test/resources/contracts/surgery-authority-v1/` | `eligibility.denied.json` | `cfa3f17adae13eadfdb2859ff7a543b541f888e9def998391bf637b597217321` |
| same | `eligibility.granted.json` | `3bda1f5c924e15eecb93eeba318f46caa3b7e9e22f2b7e76dfc016a897636063` |
| same | `eligibility.missing.json` | `aaa03e45543a120a95dd3a1dae33834ef1f8410c783adfa44f5e8a5535770440` |
| same | `room.active.json` | `3a2c437b641c1b9c6c18619742ffebcd29e2ac7c2c0b14c308690890222920b1` |
| same | `room.inactive.json` | `c97c8699502cdf9b74af0d339be055a51f2da553142fcfc290c3a694437c9361` |
| same | `room.missing.json` | `eb8bbd333c5371187e98e217bc905ec07148b9cd64df9a995c5209322074e112` |
| `backend/inpatient-service/src/test/resources/contracts/admission-authority-v1/` | `admission.active.json` | `ed82e6d2d39b3b528bb436935ef6cbd48f2731359a0907a1e0a38503745e8bf4` |
| same | `admission.discharged.json` | `ca1ba068d99a2ca4a181c36c02e5ce627ad5d9feea5a535deffaffa60bd8fbf0` |
| same | `admission.missing.json` | `ae402d4b9be39202ac2012eaa889ceded8a0c26fab3496411eab7943438988ea` |

## Second implementation batch — Billing / Notification / Surgery

### 2026-10-06 follow-up: current financial lookup implemented on both sides

The user reiterated the override to implement missing producer code as well as consumers. Billing
now supplies a gated service-only current SURGERY clearance lookup, and Surgery's internal
READY/finalize/START orchestration mandates the strict live read. Net refund, revoked or expired
authority denies despite an unchanged local grant. Exact context, correlation, service credential,
freshness and safe absence/unavailability semantics are canonical in
[SURGERY-BILLING](../../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md).
Producer fixture/use-case tests, real HTTP/PG producer tests, real Feign shared-fixture tests,
transactional consumer denial/rollback and packaged Billing runtime acceptance accompany it.
This closes the missing lookup code, not refund/issuance writers, source-revision fences or the
complete public clinical lifecycle. Those remaining engineering gaps are assigned implementation
tasks under the same override, not reasons to wait for Lộc/Vinh/Hoàng Anh to code them.

Final evidence: **233 Billing + 539 Surgery module tests**, **5 packaged-Billing/real-Surgery-client
runtime tests + 6 current Gateway/Organization/Surgery runtime tests**, all zero failure/error/skip
and final Maven exit 0 on 2026-10-06. The financial read freshness window does not invent a
30-second schedule expiry. Detailed commands/results are in the
[execution ledger](2026-10-05-huy-50-task-execution.md#financial-authority-both-sides).

Baseline is now `3103fa1` after merging the latest master without losing the earlier local slice.
All changes remain uncommitted. This batch is implementation under the user's dependency override,
not a claim that the five dependency groups or full V2 are complete.

### Billing payment and exact grants

- Opt-in `POST /api/v1/billing/payment-requests/{id}/payments`, ADMIN/CASHIER, signed access token
  account UUID, trusted actor and correlation. No public arbitrary account/charge/request issuer.
- Account/request locks and global idempotency-key lock; exact replay preserves the original
  transaction. Changed actor/request/money/method/provider under the key is a 409 conflict.
- Partial installments preserve request state and publish a classified receipt for each immutable
  completed payment. Full payment alone writes one exact-purpose clearance. Selected charges,
  patient/account/target and bounded net allocation budgets are verified inside the transaction.
- Deposits are cash/liability receipts, not allocated service revenue. INSURANCE is not a cashier
  cash method. SETTLEMENT cash is not automatically a final `settlement.completed` fact.
- V5 adds exact request targets/actor audit; V6 labels contract versions and DB-fences held V1
  events from the existing dispatcher and replay endpoint. Legacy V0 events/APIs are unchanged.
- Seven producer JSON files in `billing-service/src/test/resources/contracts/ledger-v1/` cover
  all five clearance purposes plus service/deposit receipts. Producer tests compare actual service
  serializer output; Clinical/Lab/Inpatient/Pharmacy/Surgery/Notification consume sibling files
  directly. They are not hand-authored consumer substitutes or real broker E2E.

### Notification receipts

- The existing queue handler discriminates V1 from legacy without downgrade. Gated V1 intake
  validates envelope, producer, classification, exact IDs and amount/method; malformed/disabled
  messages are rejected, temporary storage faults remain retriable.
- One DB transaction claims the event/content hash, writes a private IN_APP receipt and holds an
  immutable `notification.sent` fact. Duplicate deliveries create one history row; conflicting
  content is rejected. Outbox failure rolls back the claim/history and retry recovers.
- Deposit wording explicitly distinguishes a receipt from final settlement; partial service cash
  never says the request is fully paid. No clinical text is copied to email/SMS. Patient history
  reads require the signed patient claim, not a request-header/account-ID fallback.
- Additive V2 migration; no V1 outbound dispatcher. `MEDIFLOW_NOTIFICATION_CARE_V1_ENABLED=false`.

### Surgery exact grant storage

- Offline strict decoder and additive V2 proof table bind the grant to exact case/patient/episode/
  admission. Grant/expiry preserve nanoseconds and exclusive expiry; incoming event identity and
  immutable clearance identity are deduplicated separately.
- Missing case is durable PENDING, wrong tuple/time quarantined, conflicting clearance content
  cannot overwrite the original. Inbox/proof are atomic with rollback/retry and concurrent replay
  tests. Recording a grant never auto-readies, starts, reserves or publishes a care event.
- No broker binding, automatic pending worker, revoke/supersede projection or READY/START financial
  guard is enabled by this batch. Business flags remain OFF. Main-plan parents remain unchecked;
  only `S-02.1.3a`, `S-03.2.2a`, `S-05.3a/.3b`, `P-03.2.3a` represent completed local sub-slices.

### Clinical/Lab Gateway policy correction

The outstanding role-code handoff was implemented under the same override. Lab queue/detail,
start/cancel and Clinical admission-referral roles now mirror the controllers. Appointment
check-in/start-exam have separate exact role rules; legacy update/status are still allowed for
their old roles, with no broad PUT fallback. Gateway tests cover 99 role/route combinations,
unknown nested writes/wrong methods and signed-token boundary checks. The handoff stays PARTIAL
only for actual-service deployment smoke; an allowed-route 503 is not success E2E.

### Verification of this batch

- Root `mvn -q '-Dapi.version=1.44' test`: exit **0**, **1,759 tests / 313 suites**,
  **0 failures/errors/skips**. Every XML report was freshly written by this run. Billing 194,
  Notification 78, Surgery 180, Clinical 217, Lab 169, Inpatient 98, Pharmacy 405, Report 244,
  Gateway 34, Organization 110 and Patient 30. PostgreSQL 16.14/RabbitMQ 3.13 ran in Docker.
- After that root run, Gateway's final role correction passed its complete suite:
  `mvn -q -pl backend/gateway -am test`, exit **0**, **135 tests / 8 suites**, zero failures/
  errors/skips. Do not sum overlapping runs or claim that the root run contained these 101 later
  cases.
- After the additional Surgery review, the full module rerun
  `mvn -q -pl backend/surgery-service -am '-Dapi.version=1.44' test` exited **0** with
  **188 tests / 34 suites**, zero failures/errors/skips. Six new application cases and two new PG
  cases prove new-grant readiness invalidation/exact resource release, unchanged-grant no-op,
  no rollback of IN_PROGRESS/terminal clinical states, and atomic rollback of scheduled state/
  resources/proof/inbox on a finalization fault. This caught and fixed an overlong audit change
  code in the new path; `FINANCIAL_CLEARANCE_CHANGED` fits the existing 64-character domain/SQL
  limit. No domain constraint or already-applied migration was weakened to make the test pass.
- Billing tests prove installments, one final grant, exact targets, deposit no-allocation,
  concurrent overpayment/idempotency, outbox-failure rollback, V1 DB publication fencing and real
  API JWT/role/actor handling. Notification proves concurrent receipt dedupe, immutable event
  identity, transaction rollback/retry, privacy wording and actual signed-patient history reads.
- Surgery tests prove actual producer-byte decoding, durable early delivery, mismatch quarantine,
  immutable clearance-ID conflict, concurrent replay, inbox/proof rollback/retry and exact
  nanosecond round-trip. These are local transaction/fixture tests, not a full live financial
  permission or broker E2E certificate.
- No root/Common/Compose/CI/scripts production change, no stash removal and no commit/push.

### Billing producer fixture manifest (SHA-256)

Directory: `backend/billing-service/src/test/resources/contracts/ledger-v1/`. These files are read
directly by the corresponding consumers; no consumer-side renamed copies are used.

| File | SHA-256 |
|---|---|
| `clearance-admission.json` | `959b15699e55b617b4aa26ff2766427bd4a26872027266a916532d5335dbe805` |
| `clearance-exam.json` | `95e75d2952492f5de8b68199c4848a3fe8b188c2983fc315deb2c0364f3bb560` |
| `clearance-lab.json` | `e7cef7a8e089950a4cb34e4ca5b244b4c332c147bea35cf8e82135af4730fd51` |
| `clearance-prescription.json` | `79f0906d27db0bdc3b6e552214f54957562f198910212bf835d5196750080fda` |
| `clearance-surgery.json` | `3879a3fb43daab01cda383c9838b4035315e77e20a4d83146390342c1c28cf18` |
| `payment-deposit.json` | `afb2b0e640db9c95fb82cf856bcc72802bd2a0cdf128c3735c9c6279c4e1e550` |
| `payment-service.json` | `000ed3cf78df31a63793e1fb2be542ff11995a97051febfcffaaa3db0bb65a7e` |

### Remaining dependency work (not an ownership blocker)

These are implementation/verification tasks under the existing user override, not requests for
other owners to write the code first.

1. Clinical/Inpatient stable surgery referral producer/path and exact request identity; explicit
   order/reference registration and bedside-placement/transfer semantics. An admission's source
   record is not substituted for a surgery referral or prescription order.
2. Billing authoritative request/charge issuance, versioned catalogue, planned/performed charge
   reconciliation and expected totals; grant revocation, admission-medication authorization,
   refunds, top-up, earned recognition and final settlement facts. Grant/receipt producers are now
   present but held. Do not derive financial authority from generic `payment.completed`.
3. Shared Surgery ready/completed/cancelled event serializers and same-byte consumers in
   Inpatient/Billing/Notification/Report, with durable dedupe/out-of-order/terminal handling.
4. Full prerequisite commands (catalogue/evidence/consent/finalize/start), medication fences,
   Report financial/live publication and actual Gateway + services + broker E2E/cutover.

These combined leaves remain unchecked in the main plan. The user override permits their
implementation; missing code/evidence is tracked here instead of asking other owners for write
permission. Clinical/legal authority data still cannot be guessed or silently seeded.
