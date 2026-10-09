# Surgery pre-op mutation HTTP batch — 2026-10-07

Baseline: `e541d56`. Scope: Surgery and documentation only; Gateway/other owners/shared code remain read-only.
This ledger decomposes existing S-05.1.2/S-05.2.2/S-04.6/S-02.3/S-07.6 criteria, not ten new global IDs.
No whole contract closure or production activation follows from local tests. Global backlog remains 62.
Status: **10/10 LOCAL checkpoints accepted (2026-10-08)**. Fresh full Surgery regression is
908/908 PASS, 77 XML reports, 0 failure/error/skip, including all fifteen pre-op PostgreSQL cases.
This supersedes the dated Docker-blocked evidence below, not production authority/multi-service gates.

## Selected ten execution subtasks

- [x] PREOP-01 — strict English checklist request, required optimistic revisions and evidence/status validation.
- [x] PREOP-02 — strict typed consent request; signer and evidence are untrusted references, recorder only from JWT.
- [x] PREOP-03 — separate authorized pre-op in-port and mandatory clinical/legal authority out-port; no permissive provider.
- [x] PREOP-04 — preflight outside transactions; proof binds exact context, command and recorder; freshness checked after case lock. Application port-order/proof tests pass; actual transaction/rollback proof stays PREOP-09.
- [x] PREOP-05 — checklist PUT boundary, explicit ADMIN/DOCTOR/NURSE roles and mandatory bounded idempotency key.
- [x] PREOP-06 — consent POST boundary with the same roles, trusted signed staff identity and stable replay; no revoke mapping.
- [x] PREOP-07 — independent default-off API/business gates; explicit wiring fails startup without authority.
- [x] PREOP-08 — application and configuration tests for denial, outage, invalid/expired proof, stale case and activation.
- [x] PREOP-09 — web security/validation tests plus real PostgreSQL HTTP mutation/replay/rollback/invalidation evidence; actual 15/15 PG PASS on 2026-10-08.
- [x] PREOP-10 — matching README/.http/spec/handoff/plan and honest discovered verification results, including the unverified DB/full-module boundary.

## Boundary decisions before implementation

PUT `/api/v1/surgery/cases/{id}/checklist` changes exactly one pinned item using case/snapshot/item revisions.
POST `/api/v1/surgery/cases/{id}/consents` records one SURGERY or ANESTHESIA consent.
Unknown authority/actor/role/readiness fields fail validation. Every attempt, including receipt replay,
must obtain current authorization for the exact case and action. Missing source authority is not approval.
The production provider must validate evidence/case/order/source revisions and the approved signer,
guardian/witness/document/recorder rules; no rules or positive defaults are seeded by this batch.
Proof freshness is an observation fence, not a clinical validity TTL or distributed source lease.
Pre-op revoke roles/policy remain unresolved, so no revoke HTTP route is added.
Existing transactional kernels keep receipts, audit and exact readiness invalidation/release/HELD facts.
All V1–V8 migrations and outbound bytes remain unchanged. No automatic commit/push.

## Verification

Latest verification 2026-10-08: full clean module 908/908 PASS, 09:23:08–09:28:19 Asia/Bangkok.
Terminal fixture compares seeded consent/case/history/resource/outbox before/after instead of
expecting zero consent on a scheduled case. Details and remaining gates:
[follow-up](2026-10-08-huy-ready-task-completion.md). Failed Docker runs below are history.

Fresh clean focused run: **114 tests / 7 reports, zero failures/errors/skips**, Maven exit 0,
2026-10-07 **18:31:06–18:31:25 Asia/Bangkok**. Counts are discovered XML results, not source annotations.

| Suite | Tests | Result |
|---|---:|---|
| AuthorizedSurgeryPreopServiceTest | 25 | PASS |
| SurgeryPreopApiConfigurationTest | 12 | PASS |
| SurgeryPreopMutationApiTest | 68 | PASS |
| Business/API flag-disabled web classes | 2 | PASS |
| Existing ArchitectureTest + SurgeryArchitectureTest | 7 | PASS |

The new PostgreSQL class compiles and defines 15 real DB/HTTP cases: child/audit/receipt persistence,
same-key replay/conflict, two typed consents and Location, revoked authority on fresh/replay, source
outage/mismatched proof, staff/role/N/A denial, exact SCHEDULED invalidation/release/HELD bytes,
SQL failure rollback/retry, proof expiry after real receipt completion rollback/retry, terminal denial.
Mocks are restricted to test-only authority/Clock; kernels, repositories, reservations, held capture
and transaction adapter remain real. Existing SCHEDULED prerequisites are seeded through domain/ports,
not claimed as a real clinical readiness evaluator or the full request-to-completion vertical slice.

**PostgreSQL is NOT VERIFIED:** attempt at 18:25 failed before container/test cases executed
(`Could not find a valid Docker environment`). Docker was stopped; a normal hidden startup was
attempted without reset or data/config edits. Backend log at 18:26 reports startup failure at
`initializing Inference manager ... Docker/run/dockerInference ... The file cannot be accessed by the system`.
Pending diagnostic CLI calls were stopped, not Docker itself. The user was asked to restore
`Engine running`. No H2 substitution, skip-based success or old 740-test run is used as new proof.

Earlier focused test setup failures (Mockito stubbing executing a prior Answer with a matcher
placeholder; incomplete web helper syntax) were corrected, then the clean 114 run passed. During
review, PG fixture joins/history baselines/consent IDs/monotonic time were corrected against the actual
owned DDL; those fifteen assertions still need their first actual container execution.

Commands (sequential, no target races):

```powershell
mvn -q -pl backend/surgery-service -am clean '-Dtest=AuthorizedSurgeryPreopServiceTest,SurgeryPreopApiConfigurationTest,SurgeryPreopMutationApiTest,SurgeryPreopMutationApiBusinessFlagDisabledTest,SurgeryPreopMutationApiFeatureFlagDisabledTest,SurgeryArchitectureTest,ArchitectureTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dlogging.level.root=ERROR' test
# PENDING: rerun after Docker engine is healthy; investigate any actual assertion failure before closing PREOP-09
mvn -q -pl backend/surgery-service -am '-Dtest=SurgeryPreopMutationHttpPostgresTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dapi.version=1.44' '-Dlogging.level.root=ERROR' test
mvn -q -pl backend/surgery-service -am '-Dapi.version=1.44' '-Dlogging.level.root=ERROR' clean test
```

No root-reactor or multi-service/Gateway acceptance is claimed. Eight edited documentation files
have no missing local links; diff whitespace passes. V7 SHA256 remains
`28d36076f35670646c52b27c23914af5632e6c63dd7c83804eaf3c394a5062c8`;
V8 remains `541c8c7bf6c334ad6ac12b843489ab80cf1f2fd3481c5e01ebe9713b9e000843`.
No whole global IDs are closed; fixed selection stays 18/50 and global backlog 62.
Pre-existing uncommitted changelog line is preserved. No commit/push or owner/shared production edit.
