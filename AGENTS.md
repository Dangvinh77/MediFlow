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

**MediFlow** — a hospital management system. Two halves:

- **Backend** — Spring Boot microservices, Maven multi-module monorepo. 8 business services
  (patient, appointment, medical-record, lab, pharmacy, billing, notification, report) plus
  `gateway`, `eureka-server`, `common`. PostgreSQL (one DB per service) + RabbitMQ.
- **Frontend** — `frontend/`: Next.js 16 App Router, TypeScript, Tailwind v4, pnpm.

Authoritative business design: `docs/eproject_general_plan/*.html`. Coding standards: `docs/ai/`.

## Read before you write code

**Backend (Java — `*-service/`, `gateway/`, `common/`):**
1. [`docs/ai/README.md`](docs/ai/README.md) — the golden rules.
2. [`docs/ai/04-microservice-blueprint.md`](docs/ai/04-microservice-blueprint.md) — mandatory package layout.
3. `docs/ai/services/<service>.md` — the bounded context you are touching.
4. As needed: `01-architecture`, `03-coding-standards`, `05-api-conventions`, `06-events-rabbitmq`, `07-security-rbac`, `08-persistence-naming`, `09-testing`.

**Actually writing the code?** Read [`docs/eproject_general_plan/backend-spec/`](docs/eproject_general_plan/backend-spec/README.md) —
implementation-ready specs: DDL, domain invariants, port signatures, DTOs with validation, use-case
algorithms, event payloads, and a business-rule → test-case table per service. Start with
[`00-overview.md`](docs/eproject_general_plan/backend-spec/00-overview.md) (shared contracts), then the one service you
are building. Those specs give the *what*; `docs/ai/` still governs the *how*.

**Frontend (`frontend/` — `.ts`, `.tsx`, `.css`):**
1. [`docs/ai/12-frontend.md`](docs/ai/12-frontend.md) — the frontend blueprint, authoritative.
2. [`docs/ai/05-api-conventions.md`](docs/ai/05-api-conventions.md) — the contracts your TS types mirror.

There is a nested [`frontend/AGENTS.md`](frontend/AGENTS.md); per the AGENTS.md spec the **nearest
file wins**, so it takes precedence when you work inside that folder.

## Backend hard rules (detail in `docs/ai/`)

- **Clean architecture.** `infrastructure → application → domain`, inward only. No framework imports in `domain`; no Spring Data / infrastructure imports in `application`. Ports in `application`, adapters in `infrastructure` (`04`).
- One bounded context per service; **no cross-service DB access** — references are bare `UUID`s, never JPA relations.
- Copy the blueprint (`04`) for every service. The layout is not negotiable.
- DB = Vietnamese snake_case; Java/JSON fields = Vietnamese camelCase; class names and URLs = English (`08`).
- Change state → publish an event. Need another context's data to finish this request → resilient REST (`01`, `06`).
- Every endpoint declares roles via `@PreAuthorize` (`07`). Default deny.
- Money = `BigDecimal`, ids = `UUID`, dates = `LocalDate`/`Instant`. Constructor injection. DTO records cross boundaries; entities never do.
- Every business rule in a design doc must be implemented **and tested** (`09`).

## Frontend hard rules (detail in `12-frontend.md`)

- **Feature-based structure.** `src/features/<bounded-context>/` mirrors the backend services 1:1.
- Call the **gateway** only, via same-origin `/api/*`. Never a service port (`:8081`…) directly.
- All HTTP goes through `src/lib/api.ts`. No raw `fetch` in components.
- Shared envelope types in `src/lib/types.ts`; per-feature DTOs in `src/features/<ctx>/types.ts`, Vietnamese camelCase.
- Tailwind utilities only — no CSS-in-JS, no component library.
- Hiding UI by role is UX, not security. The backend enforces authorization, always.

**The backend rules do not apply to TypeScript.** Do not create `domain/application/infrastructure`
trees, ports, or adapters in `frontend/`. The only thing that crosses is the wire contract.

## Scaffolding

- New backend service: `.claude/skills/new-microservice/SKILL.md` + `docs/ai/04-microservice-blueprint.md`, then register the module in the root `pom.xml`.
- New frontend feature: follow the folder contract in `docs/ai/12-frontend.md`.

## Build & run

```bash
docker compose up -d                  # PostgreSQL (8 DBs) + RabbitMQ
mvn -q -DskipTests install            # build all modules
mvn -pl backend/<module> -am spring-boot:run  # run one service
cd frontend && pnpm install && pnpm dev

# Optional: install AI codebase tools (one-time per machine)
scripts/setup-codebase-memory.sh      # codebase-memory-mcp (macOS/Linux)
scripts\setup-codebase-memory.bat     # codebase-memory-mcp (Windows)
scripts/setup-understand-anything.sh  # Understand-Anything (macOS/Linux)
scripts\setup-understand-anything.bat # Understand-Anything (Windows)
scripts\setup-tools.bat               # both at once (Windows)
```

Start order: `eureka-server` (8761) → `gateway` (8080) → business services (8081–8088) → `pnpm dev` (3000).

## Git workflow

[`docs/ai/10-git-workflow.md`](docs/ai/10-git-workflow.md) — Conventional Commits, focused per-service PRs, pre-merge checklist.

## Note on tooling availability

Two optional AI code-intelligence tools can speed up exploration:

- **codebase-memory-mcp** — fast graph queries for agents. When installed + indexed, agents prefer
  `search_graph`/`trace_path`/`get_code_snippet` over Grep/Glob/Read. Docs: [`13-codebase-tools.md`](docs/ai/13-codebase-tools.md).
- **Understand-Anything** — interactive knowledge-graph dashboard with `/understand`, `/understand-chat`,
  `/understand-dashboard`, and 5 more slash commands. Docs: [`13-codebase-tools.md`](docs/ai/13-codebase-tools.md).

Both are **optional**. Without them, Grep/Glob/Read + `docs/eproject_general_plan/*.html` are your source of truth.
