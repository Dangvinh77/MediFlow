# Frontend Workboard

> Current frontend routing and ownership view, checked 2026-10-06 against baseline
> `origin/master` at `a283b30`. Refresh the date and commit when route or contract state changes.
> Ownership rules live in [`docs/ai/15-frontend-ownership.md`](../../docs/ai/15-frontend-ownership.md).
> Per-service scope, queue, and handoffs live in [`services/`](services/README.md).

## Status vocabulary

- `IMPLEMENT`: bounded implementation can proceed after the owner contract check.
- `VERIFY-CONTRACT`: code exists, but the next change must confirm the live owner backend contract.
- `HANDOFF`: another owner must provide or change a producer contract; record the handoff under
  `docs/` before implementation continues.
- `BLOCKED`: a named contract or decision is missing, so implementation must wait.

## Current state

All nine service-facing route entries currently exist under `src/app/(dashboard)/`:
`organization`, `patients`, `appointments`, `records`, `lab`, `pharmacy`, `billing`,
`notifications`, and `reports`. Login is present at `/login`. The context feature folders and
route pages are present for these areas. Inpatient is now canonically assigned to Vinh and its
backend Gateway route is live, but `features/inpatient` and `/inpatient` have not been created yet.

Pharmacy is the deepest current frontend workflow. Its visible route tree includes drug catalog,
drug detail/create/stock actions, prescription create/lookup/detail/cancel/dispense actions, and a
known-event outbox replay screen. The remaining bounded contexts are mostly base read flows: their
next expansion must begin with a live backend contract audit. This board records implementation
state only; it does not assert endpoint or DTO details that have not been verified from the owner
backend.

Appointments now include the contract-aligned seven-status list/detail presentation, creation,
pending-only schedule editing, and the two legacy generic status actions (`ARRIVED`, `CANCELLED`).
Care-finance and examination states remain read-only in this frontend slice and are not exposed as
generic status mutations.

## Owner queue

Vinh's audited execution split and prepared Cloud task scopes are in the
[2026-10-03 local/Cloud backlog](../../docs/architecture/2026-10-03-vinh-local-cloud-backlog.md).
Records now include create/update/diagnosis mutations, and Lab request creation is present. Gateway
role/method code parity is implemented, but deployment smoke is still required before
result/start/cancel can be presented as a complete LAB_TECH workflow. Inpatient read pages can now
start within the canonical Vinh scope; financial and Surgery composition remains blocked by the
active contracts.

| Task ID | Owner | Scope | Current state | Next action |
|---|---|---|---|---|
| `FE-VINH-01` | Vinh | appointments | `DONE`: list/detail plus create, pending-only edit, lifecycle timestamps, and valid legacy status actions | Re-verify the live Clinical contract before adding any explicit care-finance or examination command. |
| `FE-VINH-02` | Vinh | records | `DONE`: patient lookup, record detail, create, update and add-diagnosis flows mirror the live controller roles and DTOs | Re-verify the live Clinical contract before adding care-finance completion/admission commands. |
| `FE-VINH-03` | Vinh | lab | `DONE`: queue/detail reflect all live statuses and episode filters; compatibility request creation is available to ADMIN/DOCTOR without browser payment inference | Result/start/cancel remain integration-blocked until real Gateway-to-Lab deployment smoke passes. |
| `FE-VINH-04` | Vinh | inpatient | Backend Core V1 and Gateway route exist; frontend not started | `IMPLEMENT`: admission list/detail and bed list first; keep finance/Surgery integration blocked. |
| `FE-HUY-01` | Huy | pharmacy | Deeper workflows present | `VERIFY-CONTRACT`: recheck the current pharmacy controller/DTO/test contract before extending the workflow; use `HANDOFF` for missing producer behavior. |
| `FE-HUY-02` | Huy | reports | Base read route and feature present | `VERIFY-CONTRACT`: audit the report DTO, filters, roles, and empty/error behavior before adding drill-downs. |
| `FE-HOANGANH-01` | Hoàng Anh | organization | Base read route and feature present | `VERIFY-CONTRACT`: audit the live organization contract before adding staff/department mutations or cross-context composition. |
| `FE-HOANGANH-02` | Hoàng Anh | patients | Base read route plus live Patient read/list backend contract present | `VERIFY-CONTRACT`: align the frontend DTO and empty/error/retry tests with the live controller fixture before adding write flows. |
| `FE-HOANGANH-03` | Hoàng Anh | Gateway identity liaison | Shared login/session integration present | `VERIFY-CONTRACT`; create `HANDOFF` when a Gateway identity claim or endpoint must change. Shared auth/session files require explicit shared task scope. |
| `FE-LOC-01` | Lộc | billing | Base read route and feature present | `VERIFY-CONTRACT`: audit the live billing lookup contract before adding payment or invoice actions. |
| `FE-LOC-02` | Lộc | notifications | Base read route and feature present | `VERIFY-CONTRACT`: audit the live notification lookup contract before adding read-state or delivery controls. |

## Shared gate for every queue item

Before an `IMPLEMENT` task starts, confirm the owner backend controller/DTO/tests, gateway path,
roles, envelope, and error codes. If the producer contract is absent or needs another owner's
change, stop at `HANDOFF`/`BLOCKED` and document the exact contract instead of guessing. Shared
changes to app shell/layout/loading/global files, auth/session, generic `lib` helpers, components,
packages, or config require explicit assignment in the task.
