# AGENTS.md — MediFlow

> **This file is the single entry point for every coding agent.** It follows the
> [AGENTS.md standard](https://agents.md/) (Linux Foundation / Agentic AI Foundation), read natively
> by Codex, Cursor, Copilot, Gemini CLI, Aider, Windsurf, Zed and others. Claude Code reads
> `CLAUDE.md`, which imports this file — so there is **one** set of instructions, not one per tool.
>
> **The rules themselves live in [`docs/ai/`](docs/ai/README.md).** This file points at them.
> Never copy a rule here; edit it in `docs/ai/*` once and every tool sees it on the next pull.

## ⚡ First thing every session

Before writing ANY code, read the changelog to understand what has changed:

```bash
node scripts/changelog.js --summary   # tổng quan: số commit, theo type, theo tác giả
node scripts/changelog.js             # tất cả commits (table view)
node scripts/changelog.js --limit 5   # 5 commits gần nhất
node scripts/changelog.js --files     # chi tiết từng file + tác giả + số dòng
```

> **Nếu dùng Codex / Cursor / Copilot / Gemini CLI / Aider / Windsurf / Zed:**
> các tool này đều đọc AGENTS.md này native. Hãy luôn chạy lệnh trên trước khi
> code để nắm context đầy đủ.

## Project

**MediFlow: A Microservices-Based Hospital Information System for Outpatient Care Management.**
The academic MVP targets multi-specialty clinics and hospital outpatient departments.
Inpatient care and surgery remain deferred; preserving their source does not activate them.
Backend, web and mobile clients share this scope:

- **Backend** — Spring Boot/Maven monorepo with 13 reactor modules. Core contains Organization
  and Patient packages with separate persistence boundaries; Support composes Report/Notification
  plain libraries with separate stores. Compose defaults to 6 business deployments:
  Core, Clinical, Lab, Pharmacy, Billing, Support. Eight active contexts plus deferred
  Inpatient/Surgery must not be confused with the number of deployment applications.
  Inpatient/Surgery are deferred, not deleted. Gateway/Eureka are infrastructure; common is a library.
  PostgreSQL context-owned stores and RabbitMQ remain. Packaged DB/broker acceptance and cutover
  are separate from the configured six-deployment topology; see backend/support-service/README.md.
- **Frontend** — `frontend/`: Next.js 16 App Router, TypeScript, Tailwind v4, pnpm.
- **Mobile** — `mobile/`: Flutter real-Gateway foundation; see `mobile/README.md` for current
  read-only slices, unfinished context workspaces and native toolchain limits.

Authoritative business design and scope: [`docs/README.md`](docs/README.md).
Coding standards: [`docs/ai/README.md`](docs/ai/README.md). Old-path documents are compatibility
pointers, not additional specs or backlogs.

## Developer ownership

| Developer | Owned backend modules |
|---|---|
| Vinh (`Dangvinh77` / `Harori`) | `clinical-service`, `lab-service`, `billing-service`, `notification-service`, deferred `inpatient-service` |
| Huy (`LQHuy0210`) | `pharmacy-service`, `report-service`, deferred `surgery-service` |
| Hoàng Anh (`TranHoangAnh94`) | Organization/Patient packages in `core-service`, `gateway` |

From 2026-10-18, per user assignment, Vinh takes over Billing and Notification from Lộc.
Vinh owns backend verification across modules, including test-only fixes; production fixes
outside Vinh's modules require an owner handoff.

Frontend ownership mirrors these bounded contexts. The exact feature and route paths are canonical
in [`docs/ai/15-frontend-ownership.md`](docs/ai/15-frontend-ownership.md).

- Production changes are limited to the active developer's owned modules unless the user explicitly
  grants a task-scoped override naming another module or shared path.
- Other services may be inspected read-only to verify API and event contracts. If a required
  producer contract is missing, write a `HANDOFF` document and register it in
  [`docs/handoffs/README.md`](docs/handoffs/README.md); do not edit its service, infer identifiers,
  query its database, or add temporary cross-service coupling. Delete a handoff after both sides
  pass its acceptance criteria and move any lasting rule into the canonical contract/service docs.
- `backend/common`, `backend/eureka-server`, root build files, `.github`, and repository scripts are
  shared. Change them only when the task explicitly assigns that shared work.
- Vinh may coordinate, review, and merge across the repository; this does not silently expand
  Harori's production write scope beyond Clinical, Lab, Billing and Notification.
- Frontend shared-scope exception: Vinh coordinates and has default write authority for the shared
  frontend paths defined in [`docs/ai/15-frontend-ownership.md`](docs/ai/15-frontend-ownership.md).
  This exception does not grant default authority over another owner's feature or route.
- Subagents inherit the same ownership boundary. Delegation cannot bypass it.

Each owned backend module has a nested `AGENTS.md` that identifies its owner and local verification.

## Read before you write code

**Backend (Java — `*-service/`, `gateway/`, `common/`):**
1. [`docs/README.md`](docs/README.md) and [`docs/ai/README.md`](docs/ai/README.md) — scope and coding rules.
2. [`docs/plans/01-system-design.md`](docs/plans/01-system-design.md) — CURRENT/TARGET and ownership.
3. [`docs/02-business-flows.md`](docs/plans/02-business-flows.md) — business invariants.
4. [`docs/03-contracts.md`](docs/plans/03-contracts.md) and [`docs/04-data-reliability.md`](docs/plans/04-data-reliability.md).
5. [`docs/05-implementation-plan.md`](docs/plans/05-implementation-plan.md),
   [`docs/06-acceptance-runbook.md`](docs/plans/06-acceptance-runbook.md), then the module's nearest AGENTS.md.

**Actually writing the code?** Lock the live DTO/controller/serializer/migration/producer fixtures
in M6-02 before changing contracts. Missing wire fields or authority are not inferred from the
new design. The retired spec paths point to this MVP; they do not authorize deferred work.

**Frontend (`frontend/` — `.ts`, `.tsx`, `.css`):**
1. [`docs/ai/12-frontend.md`](docs/ai/12-frontend.md) — the frontend blueprint, authoritative.
2. [`docs/ai/15-frontend-ownership.md`](docs/ai/15-frontend-ownership.md) — canonical frontend ownership and shared-scope matrix.
3. [`frontend/docs/frontend-workboard.md`](frontend/docs/frontend-workboard.md) — dated route state and next task IDs.
4. [`docs/ai/05-api-conventions.md`](docs/ai/05-api-conventions.md) — the contracts your TS types mirror.

**Flutter mobile (`mobile/` — `.dart`, `pubspec.yaml`):**
1. [`docs/ai/README.md`](docs/ai/README.md) — the golden rules.
2. [`docs/ai/14-flutter.md`](docs/ai/14-flutter.md) — Flutter blueprint, **mandatory**.
3. [`docs/ai/05-api-conventions.md`](docs/ai/05-api-conventions.md) — API contracts.
4. The Flutter rules govern `mobile/` only. Inside `mobile/`, follow Clean Architecture:
   `presentation → domain ← data`. Never call service ports directly — use the gateway.

There is a nested [`frontend/AGENTS.md`](frontend/AGENTS.md); per the AGENTS.md spec the **nearest
file wins**, so it takes precedence when you work inside that folder.

## Backend hard rules (detail in `docs/ai/`)

- **Clean architecture.** `infrastructure → application → domain`, inward only. No framework imports in `domain`; no Spring Data / infrastructure imports in `application`. Ports in `application`, adapters in `infrastructure` (`04`).
- One data authority per context; Core/Support have explicit modular co-hosting exceptions in
  `docs/plans/01-system-design.md`. **No cross-context DB access** — references are bare `UUID`s, never JPA relations.
- Copy the blueprint (`04`) for every service. The layout is not negotiable.
- DB/Java/JSON naming follows `08` (including its Pharmacy/Report/Surgery English exception); class names and URLs are English.
- Change state → publish an event. Need another context's data to finish this request → resilient REST (`01`, `06`).
- Every endpoint declares roles via `@PreAuthorize` (`07`). Default deny.
- Money = `BigDecimal`, ids = `UUID`, dates = `LocalDate`/`Instant`. Constructor injection. DTO records cross boundaries; entities never do.
- Every business rule in a design doc must be implemented **and tested** (`09`).

## Frontend hard rules (detail in `12-frontend.md`)

- **Feature-based structure.** `src/features/<bounded-context>/` follows logical contexts,
  not Core/Support deployment names; existing feature folders remain separate.
- Call the **gateway** only, via same-origin `/api/*`. Never a service port (`:8081`…) directly.
- All HTTP goes through `src/lib/api.ts`. No raw `fetch` in components.
- Shared envelope types in `src/lib/types.ts`; per-feature DTOs in `src/features/<ctx>/types.ts` mirror each service's exact wire names (`12`, `08`).
- Tailwind utilities only — no CSS-in-JS, no component library.
- Hiding UI by role is UX, not security. The backend enforces authorization, always.

**The backend rules do not apply to TypeScript.** Do not create `domain/application/infrastructure`
trees, ports, or adapters in `frontend/`. The only thing that crosses is the wire contract.

## Scaffolding

- New backend service: `.claude/skills/new-microservice/SKILL.md` + `docs/ai/04-microservice-blueprint.md`, then register the module in the root `pom.xml`.
- New frontend feature: follow the folder contract in `docs/ai/12-frontend.md`.

## Build & run

```bash
docker compose up -d postgres rabbitmq # shared infrastructure; 8 active + 2 deferred context DBs on fresh init
mvn -q -DskipTests install            # build all modules
java -jar backend/core-service/target/core-service-0.0.1-SNAPSHOT.jar # after build; or launch selected app in IDE
cd frontend && pnpm install && pnpm dev
cd mobile && flutter pub get && flutter run  # device config: mobile/README.md; Windows web: tool/run_web.ps1

# Optional: install AI codebase tools (one-time per machine)
scripts/setup-codebase-memory.sh      # codebase-memory-mcp (macOS/Linux)
scripts\setup-codebase-memory.bat     # codebase-memory-mcp (Windows)
scripts/setup-understand-anything.sh  # Understand-Anything (macOS/Linux)
scripts\setup-understand-anything.bat # Understand-Anything (Windows)
scripts\setup-tools.bat               # both at once (Windows)

# New clone? Enable git hooks (single-author policy). core.hooksPath is local to
# each machine and does NOT travel with `git clone` — bootstrap does this automatically.
git config core.hooksPath scripts/git-hooks   # or: scripts\setup-hooks.bat / bash scripts/setup-hooks.sh
```

Start order: `eureka-server` (8761) → `gateway` (8080) → implemented business services
(Core 8089; Clinical/Lab/Pharmacy/Billing/Notification/Report 8082,8084–8088) → `pnpm dev` (3000) →
`flutter run`. Inpatient/Surgery (8090/8091) remain outside the MVP start profile.

## Git workflow

[`docs/ai/10-git-workflow.md`](docs/ai/10-git-workflow.md) — Conventional Commits, focused per-service PRs, pre-merge checklist.

**Commit identity rule:** every commit must have exactly one human author listed in
[`scripts/allowed-contributors.json`](scripts/allowed-contributors.json). AI agents and subagents
must never add `Co-Authored-By`, AI session trailers, or themselves as author/co-author. Do not
impersonate another contributor by changing Git identity; use the identity of the developer who owns
the current task. The local hooks and the `Commit policy` workflow enforce this rule.

## Note on tooling availability

Two optional AI code-intelligence tools can speed up exploration:

- **codebase-memory-mcp** — fast graph queries for agents. When installed + indexed, agents prefer
  `search_graph`/`trace_path`/`get_code_snippet` over Grep/Glob/Read. Docs: [`13-codebase-tools.md`](docs/ai/13-codebase-tools.md).
- **Understand-Anything** — interactive knowledge-graph dashboard with `/understand`, `/understand-chat`,
  `/understand-dashboard`, and 5 more slash commands. Docs: [`13-codebase-tools.md`](docs/ai/13-codebase-tools.md).

Both are **optional**. Without them, Grep/Glob/Read + `docs/README.md` and live source/fixtures
are your source of truth. Do not resurrect retired admission/surgery/full-V2 backlogs for MVP.
