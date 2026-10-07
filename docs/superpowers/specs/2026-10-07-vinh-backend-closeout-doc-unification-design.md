# Vinh backend closeout and specification unification design

**Date:** 2026-10-07  
**Owner:** Vinh (`Dangvinh77` / `Harori`)  
**Scope:** documentation and cross-service handoff cleanup for Clinical, Lab and Inpatient

## Objective

Close the independently implementable backend work owned by Vinh, keep unfinished runtime
integration visibly gated, and give developers one unambiguous implementation path when the legacy
V1 behavior and the Care-Finance target coexist.

This change does not enable feature flags or alter production code. It records the verified source
state, removes historical handoff noise, and routes every remaining dependency to its actual owner.

## Verified source baseline

The 2026-10-07 codebase-memory generation contains 22,322 nodes and 108,857 edges. Its service
architecture view reports all current routes for Clinical, Lab and Inpatient. Targeted source review
also covers the migration ranges that the graph parsed partially.

The local Maven run produced the following service evidence:

| Module | Tests | Result |
|---|---:|---|
| Clinical | 217 | no failures or errors |
| Lab | 169 | no failures or errors |
| Inpatient | 98 | 97 completed; one Testcontainers error because Docker was unavailable |

Clinical implements its compatibility CRUD plus check-in, EXAM clearance, guarded start, record
completion and admission-referral paths. Lab implements compatibility payment projection plus
episode-aware request, LAB_TEST clearance, guarded execution and immutable result publication.
Inpatient implements Core V1 APIs, persistence, outbox, disabled consumers and the service-only
admission authority lookup.

Therefore Vinh's backend is **locally complete and integration-gated**. `complete` here means no
additional production behavior can be added safely without either enabling a verified existing
contract or receiving a missing producer/consumer contract. It does not claim that the distributed
workflow is live.

## Canonical specification model

### Clinical and Lab

`docs/eproject_general_plan/backend-spec/03-clinical.md` and `04-lab.md` become the canonical,
combined implementation specifications. Each document will contain:

1. compatibility behavior that remains live;
2. additive Care-Finance schema and commands already implemented behind the feature gate;
3. one rollout-status table using `IMPLEMENTED`, `FEATURE_GATED` and `EXTERNAL_BLOCKED`;
4. the exact external dependency that prevents activation;
5. the migration rule that forbids removing compatibility paths before Docker acceptance.

The former `care-finance-v2/03-clinical.md` and `04-lab.md` remain as short compatibility redirects
to avoid breaking links in old plans, branches and PR descriptions. They no longer repeat DDL,
algorithms, payloads or test requirements.

### Inpatient

`care-finance-v2/10-inpatient.md` remains the sole implementation specification because Inpatient
has no separate legacy module or competing top-level spec. Its title and status will state that
Core V1 is implemented while external messaging stays gated. The backend-spec index will identify
this exact file as canonical so its directory name cannot be mistaken for a second version.

### Repository index

`docs/eproject_general_plan/backend-spec/README.md` gains a canonical-spec matrix. For every service
the matrix names one implementation entry point and distinguishes compatibility, target and runtime
activation. This change only consolidates Vinh-owned detailed specs; it does not rewrite another
developer's service specification.

## Handoff lifecycle cleanup

Active handoffs will contain current owner actions and acceptance criteria only. Dated audit logs,
superseded permission notes, completed rows and local implementation diaries belong in Git history
or the canonical service/contract docs.

| Handoff | Decision |
|---|---|
| Clinical/Lab financial clearance | keep; reduce to Billing request issuance, live publication, broker E2E and activation evidence |
| Inpatient deposit/settlement | keep; reduce to top-up, settlement, request issuance, reconciliation and broker E2E |
| Gateway Clinical/Lab roles | keep until real Gateway-to-service smoke passes; code changes are complete |
| Surgery implementation decisions | keep but replace the historical document with exact `surgery.requested` intake and Surgery result-event obligations |
| Huy Pharmacy/Report consumers | keep but retain only missing live bindings/projectors, placement policy and replay/cutover work |
| Notification care projections | create a new handoff for Lộc because Notification has no Clinical completion, admission, top-up, settlement or Surgery V1 bindings |

No completed handoff is retained merely as a progress report. When Gateway smoke passes, its lasting
RBAC rules move to the Gateway/Clinical/Lab service docs and the handoff is deleted.

## Remaining ownership

### Lộc — Billing and Notification

- Issue authoritative payment requests from accepted Clinical, Lab and Inpatient charge facts.
- Publish held V1 clearance rows live only after cutover acceptance.
- Implement `deposit.topup.required` and `settlement.completed` producer paths.
- Add Notification V1 bindings, exact decoders, idempotent intent persistence and privacy-safe
  templates for the approved care projection events.

### Huy — Surgery, Pharmacy and Report

- Accept a shared `surgery.requested` fixture before Clinical or Inpatient publishes it.
- Publish exact `surgery.ready`, `surgery.completed` and `surgery.cancelled` fixtures and live events.
- Connect Pharmacy admission lifecycle handling to an approved live authorization path.
- Connect Report's existing offline care-finance decoder/projection code to guarded live bindings,
  then prove replay/cutover behavior.

### Hoàng Anh — Gateway

- No remaining Clinical/Lab RBAC code gap is known.
- Participate in the real deployment smoke and preserve the exact downstream role matrix.

### Vinh — Clinical, Lab and Inpatient

- Keep all new features disabled until their registered acceptance criteria pass.
- Run the Docker vertical slices when infrastructure is available.
- Add producer code only after the matching consumer reads the same canonical fixture.
- Do not invent bed-transfer, capacity, Lab correction/import or Surgery payload contracts.

## Documentation updates

The implementation will update these sources together:

- backend-spec README and Vinh's three canonical specs;
- `docs/ai/services/clinical.md`, `lab.md` and `inpatient.md`;
- active handoff registry and care-finance contract registry;
- the retained handoff documents and the new Notification handoff;
- direct links that still treat the duplicate V2 Clinical/Lab files as independent authority.

Durable payload definitions remain in `docs/handoffs/care-finance/`. Service specs reference those
contracts and do not create a third payload copy.

## Validation

The documentation change is complete when:

1. every service has one named canonical implementation entry point;
2. Clinical/Lab redirect files contain no duplicate contract text;
3. the active handoff registry lists only current blockers;
4. every handoff names producer, consumer, required behavior and acceptance criteria;
5. relative Markdown links resolve;
6. `git diff --check` and the commit-policy verification pass;
7. no production source or feature flag changes appear in the PR.

Docker smoke remains a tracked acceptance condition rather than being reported as passed while the
Docker engine is unavailable.
