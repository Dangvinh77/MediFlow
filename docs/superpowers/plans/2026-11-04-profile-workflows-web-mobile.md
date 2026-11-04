# Profile workflows web/mobile implementation plan

> For agentic workers: use subagent-driven-development with bounded file ownership;
> review contract/spec compliance, then quality and integration before PR.

**Goal:** Finish the user-approved profile-centered account, prescribing and nurse
reception workflows on web and Flutter, then open a focused PR.

**Architecture:** Keep account authority in Organization and existing Patient email
sync; narrow nurse operations to linked PATIENT targets. Compose features at app
routes, never cross-import sibling features. Mobile uses presentation/domain/data
and shared Gateway transport. Preserve existing unrelated working-tree changes.

**Tech stack:** Spring Boot/Maven, Next/TypeScript/Vitest, Flutter/Riverpod.

## Authorized scope

User explicitly requests completion of the previously reviewed staff/patient
account profiles, email/username synchronization, embedded doctor prescribing,
nurse reception optimization and corresponding mobile implementation. Vinh acts
under the task-scoped assignment for Organization/Patient/Pharmacy web changes,
existing backend assignment and entire-mobile assignment. Shared app composition
is included. Notification target enrichment and a new consolidated patient results
API were audit recommendations, not part of the named interaction changes.

## Tasks

- [ ] Backend: lock exact profile-account lookup response; staff ADMIN lookup,
  patient ADMIN/NURSE lookup/reset/mail status/retry, idempotency, target checks,
  no secrets, Gateway matching, controller/service/security regressions.
- [ ] Web: read account status in staff/patient profiles, conditional issue/reset,
  email correction in profile, mail metadata/retry, no secret display, save returns
  to profile, preserve security retries and stale session guards.
- [ ] Web: embedded prescription workspace composed at record route; lock source,
  refresh after create, links back to authorized source records; preserve draft,
  server authority, closed-record and role restrictions.
- [ ] Mobile: mirror account panel and commands in staff/patient profiles through
  domain/repository; callback composition at app boundary; tests for permissions,
  no secrets, duplicate/uncertain commands and session changes.
- [ ] Mobile: embed prescribing in record using source context and domain writers;
  no cross-feature imports; tests for context and callbacks.
- [ ] Reception: retain web simplification already implemented; add native V2
  session-based reception with source patient, capacity/revision, idempotent
  registration, receipt/next patient, and follow-up validation as applicable.
- [ ] Review source/backends and tests; run focused suites then frontend typecheck,
  lint/build and mobile analyze/test/build as applicable. Record runtime limits.
- [ ] Stage only task files, commit with existing allowed human identity, push
  branch and create PR; leave unrelated demo scripts/artifact deletions untouched.

## Verification

Security tests must reject nurse targets other than linked PATIENT accounts and
never return password/hash/mail body. Email changes reuse the audited command;
Patient sync status must distinguish pending from done. Prescribing validates
authoritative context before writes. Reception must not issue another registration
after confirmed success or generate a new key on ambiguous retries. Client state
and late responses must be discarded when identity or source changes.
