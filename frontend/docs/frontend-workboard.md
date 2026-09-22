# Frontend Workboard

> Current frontend routing and ownership view, checked 2026-09-22 against baseline
> `origin/master` at `256d25f`. Refresh the date and commit when route or contract state changes.
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
route pages are present for these areas.

Pharmacy is the deepest current frontend workflow. Its visible route tree includes drug catalog,
drug detail/create/stock actions, prescription create/lookup/detail/cancel/dispense actions, and a
known-event outbox replay screen. The remaining bounded contexts are mostly base read flows: their
next expansion must begin with a live backend contract audit. This board records implementation
state only; it does not assert endpoint or DTO details that have not been verified from the owner
backend.

## Owner queue

| Task ID | Owner | Scope | Current state | Next action |
|---|---|---|---|---|
| `FE-VINH-01` | Vinh | appointments | List and role-gated detail route present | `IMPLEMENT`: select one appointment create/update/status vertical slice after its live request contract is checked. |
| `FE-VINH-02` | Vinh | records | Base read route and feature present | `VERIFY-CONTRACT`: audit the live medical-record contract before adding mutations or detail flows. |
| `FE-VINH-03` | Vinh | lab | Base read route and feature present | `VERIFY-CONTRACT`: audit the live lab request/result contract before adding mutations or detail flows. |
| `FE-HUY-01` | Huy | pharmacy | Deeper workflows present | `VERIFY-CONTRACT`: recheck the current pharmacy controller/DTO/test contract before extending the workflow; use `HANDOFF` for missing producer behavior. |
| `FE-HUY-02` | Huy | reports | Base read route and feature present | `VERIFY-CONTRACT`: audit the report DTO, filters, roles, and empty/error behavior before adding drill-downs. |
| `FE-HOANGANH-01` | Hoàng Anh | organization | Base read route and feature present | `VERIFY-CONTRACT`: audit the live organization contract before adding staff/department mutations or cross-context composition. |
| `FE-HOANGANH-02` | Hoàng Anh | patients | Spec-backed base read route; the current Patient service has only its application bootstrap and configuration | `BLOCKED`: implement the live Patient controller/DTO contract, then align frontend types before adding write flows. This also resolves [`HANDOFF-patient-login-landing.md`](../../docs/HANDOFF-patient-login-landing.md). |
| `FE-HOANGANH-03` | Hoàng Anh | Gateway identity liaison | Shared login/session integration present | `VERIFY-CONTRACT`; create `HANDOFF` when a Gateway identity claim or endpoint must change. Shared auth/session files require explicit shared task scope. |
| `FE-LOC-01` | Lộc | billing | Base read route and feature present | `VERIFY-CONTRACT`: audit the live billing lookup contract before adding payment or invoice actions. |
| `FE-LOC-02` | Lộc | notifications | Base read route and feature present | `VERIFY-CONTRACT`: audit the live notification lookup contract before adding read-state or delivery controls. |

## Shared gate for every queue item

Before an `IMPLEMENT` task starts, confirm the owner backend controller/DTO/tests, gateway path,
roles, envelope, and error codes. If the producer contract is absent or needs another owner's
change, stop at `HANDOFF`/`BLOCKED` and document the exact contract instead of guessing. Shared
changes to app shell/layout/loading/global files, auth/session, generic `lib` helpers, components,
packages, or config require explicit assignment in the task.
