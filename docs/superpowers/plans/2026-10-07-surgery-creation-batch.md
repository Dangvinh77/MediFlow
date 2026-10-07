# Surgery creation — next ten execution checks, 2026-10-07

Scope: Huy-owned Surgery and documentation only. Gateway/other owners/shared build are read-only.
All prior dirty Surgery/Report work is preserved. These are **execution checks against ten existing
plan IDs**, not ten newly invented backlog IDs or ten claims of completed integration.

## Local design

`CreateSurgeryCaseUseCase` accepts one channel-neutral typed intent and separately trusted human/system
actor. No HTTP/referral decoder, live bean or positive authority fallback is installed. A real
`SurgeryCreationAuthorityPort` must authorize every attempt, including replay, and verify the exact
patient/department/selected episode/requester relationship and approved template before first creation.
There is no inferred account-to-staff mapping, medical/legal catalogue or price.

Fingerprint V1 includes request UUID, exact episode/context, patient, department, requester,
procedure, normalized indication, priority, exact business requestedAt, pinned template revision and
sorted itemCode/priceCode/canonical quantity lines. It excludes delivery/channel/recorder/correlation.
One request cannot be reused with changed intent. Actor exclusion is not an authorization bypass:
authorize runs before the committed-receipt shortcut and before external observation.

The nonlocking receipt probe uses a separate read transaction. External authorization/observation
suspends any caller transaction. Atomic write uses the already verified bounded unit-of-work adapter:
at most three fresh attempts for PostgreSQL 55P03/40P01, 1s lock timeout and 5s transaction timeout.
V8 global request receipt is inserted/locked first, serializing concurrent first creation across
channels/replicas. A committed exact winner is replayed; a different fingerprint conflicts.
Clock is checked AFTER request-lock wait: proof must match fingerprint/template revision, be <=30s
old, <=5s future skew and strictly before its explicit source validity deadline. Fresh observation
is not a distributed referral/source lock or fabricated clinical TTL.

The write re-reads exact immutable template UUID/procedure/revision, rejects unproven historical
cases, and creates REQUESTED case/revision-0 histories, pinned PENDING checklist/items, canonical
V7 `surgery.case.created` **HELD** bytes and original receipt together. Outbox capture is mandatory;
no silent no-charge branch. UUIDs/time returned by replay stay original after restart/state change.
Existing durable inbox retry remains independent; no in-memory callback is its source of truth.
The recovery test uses a test-only, explicitly re-addressed Billing fixture during an uncommitted
creation, then a reconstructed worker after commit. This is a local visibility/recovery simulation,
NOT proof that Billing can legitimately grant an uncommitted case or acceptance of an upstream
referral. The existing decoder canonicalizes JSON field order; the test verifies equivalent input
content and byte-identical storage/retry of the decoder's accepted representation, not preservation
of incoming JSON whitespace/order. No decoder/wire change is made.
V8 adds no automatic backfill/adoption, publication, clinical defaults, FK into another database or
change to V1–V7 migrations. A committed PENDING row fails closed, never creates another case.

## Ten selected existing IDs and execution checks

| Check | Existing ID | LOCAL work | Whole-ID boundary |
|---|---|---|---|
| C01 | S-04.1 | typed intent and mandatory authorization/approval seam; exact episode/requester/template pin | real referral/requester provider and public boundary still open |
| C02 | S-03.1 | same intent fingerprint across delivery/channel, changed clinical intent conflicts | producer ownership/relationship fixtures still open |
| C03 | S-02.3 | preflight outside TX; global request lock before all creation children/audit | other-command orchestration/crash acceptance remains separate |
| C04 | S-04.2 | atomic case + pinned checklist + mandatory V7 charge fact + immutable receipt | DONE_LOCAL; not live creation |
| C05 | S-03.4 | actual creation caller captures canonical charge bytes, no prices/narrative | full downstream manifest/consumer acceptance still open |
| C06 | S-04.4 | replay/conflict, concurrent first insert, contexts, authority/template failures | public web/referral-path acceptance still open |
| C07 | S-02.6 | real PostgreSQL failure injection at each creation write boundary and recover same request | separate JVM crash/full workflow matrix still open |
| C08 | S-07.6 | module-local create→preop→pre-start cancel, immutable creation replay | full authority/lifecycle/Gateway vertical slice still open |
| C09 | X-01.2 | creation fact from actual transaction uses canonical serializer and retained bytes | cross-owner all-event manifest/consumer proof still open |
| C10 | X-01.7 | additive V1–V7→V8 upgrade preserves existing rows/retry/HELD bytes | bindings/live catch-up/publish/cutover/rollback still open |

## Verification

**PASS LOCAL execution C01–C10. Exactly one whole existing backlog ID, S-04.2, is closed LOCAL.**
The other nine IDs remain OPEN for the whole-ID boundaries in the table; no new backlog IDs,
medical/legal defaults or cross-owner acceptance are invented. Main backlog is **62 open**;
fixed selection **18/50 accepted LOCAL, 32 open**.

Final sequential current-source command:

```powershell
mvn -q -pl backend/surgery-service -am '-Dapi.version=1.44' '-Dlogging.level.root=ERROR' test
```

Exit 0, **665 tests / 64 fresh XML reports / 0 failures / 0 errors / 0 skips**. All XML timestamps
2026-10-07 11:59:23–12:03:41 Asia/Bangkok. Unit creation 19, real PostgreSQL creation 22 and
seven isolated baseline upgrades V1–V7→V8 pass. These 48 selected cases contain six prior upgrade
cases; the actual increment from 623 is **42 new cases**, never reruns.
No packaged-JVM/full-root/Gateway integration acceptance is claimed by this command.

No test-double acceptance is producer authority approval. No HELD wire is dispatched. All production
business/messaging/lifecycle gates remain off. No Gateway edit, commit or push in this batch.

Intermediate runs (not counted twice): first focused 40 cases had three Mockito re-stubbing
fixture errors, fixed with doAnswer; next focused 43/43 passed. Initial full 664/664 passed before
the additional pending-recovery check. A 665-case full rerun then caught one new test expectation
of original JSON order instead of the existing decoder's canonical retained bytes. The assertion
is repaired to verify both semantic equivalence and exact retained bytes before/after recovery;
no production decoder or contract was changed. Focused PostgreSQL 22/22 then final full 665/665
current-source reruns both exit 0.
The existing Rabbit fixture may log a scheduled query before its mocked Clock is configured;
this is pre-existing harness noise, not a new positive authority provider or a cleared CI gate.

## Provenance and scope

- Branch Huy, unchanged HEAD `3765f4f46587ae134cbbe00f661ec7e4b83eb316`; this batch and prior
  Report/lifecycle edits remain uncommitted. Four stashes untouched; no GitHub mutation.
- Docker Desktop 29.6.2, Testcontainers API override 1.44, actual PostgreSQL 16.14/RabbitMQ.
  Images `postgres:16-alpine` and `rabbitmq:3.13-alpine`; isolated Testcontainers resources only,
  no user volume/database/container reset.
- V8 SHA-256 `541c8c7bf6c334ad6ac12b843489ab80cf1f2fd3481c5e01ebe9713b9e000843`.
  V7 unchanged `28d36076f35670646c52b27c23914af5632e6c63dd7c83804eaf3c394a5062c8`;
  V1–V7 and all producer fixture bytes untouched. Runtime statement identities and source proof
  have no fabricated business revisions.
- `git diff --check` clean; no prohibited outward/framework imports in the new application files.
  Gateway/Common/root POM/Compose/scripts/CI and all other-owner module diffs are empty.
  Existing dirty Report code/docs/hook entry are preserved; not counted as new work here.
- No creation HTTP/referral adapter/provider, source lease, approved clinical/legal default,
  pricing/issuance/refund workflow, event publication, accepted read pointer or G1/G3 activation.
  The existing active Surgery handoff stays registered; only lasting LOCAL design/evidence is updated.
