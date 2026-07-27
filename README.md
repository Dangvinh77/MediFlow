# MediFlow — Hospital Microservices (Spring Boot Monorepo)

**MediFlow** is a hospital/clinic management system built as **Spring Boot microservices** (Maven multi-module, monorepo). This repo ships with a **shared AI coding framework** so everyone — whether they use **Claude Code**, **Codex**, or **Cursor**, on **IntelliJ / VS Code / NetBeans** — produces code to the same standard after a `git pull`.

> Repo: `git@github.com:Dangvinh77/MediFlow.git`

> ⚠️ **DEMO / WORK IN PROGRESS.** This is a demonstration skeleton for learning. The code
> (services, gateway stub auth, frontend, configs) is **subject to change** and is **not
> production-ready** — auth is stubbed, secrets use dev defaults, and only some modules exist.
> Expect breaking changes as the project evolves.
>
> ⚠️ **DEMO / ĐANG PHÁT TRIỂN.** Đây là bộ khung demo để học tập. Toàn bộ code (service, auth
> stub ở gateway, frontend, cấu hình) **có thể thay đổi** và **chưa dùng cho production** — auth
> mới là bản giả lập, secret dùng giá trị mặc định dev, và mới có một số module. Code sẽ còn đổi.

- **Design docs (authoritative):** [`EProject/`](EProject/) — one HTML per service.
- **AI coding rules (single source of truth):** [`docs/ai/`](docs/ai/README.md).
- **Services:** gateway, patient, appointment, medical-record, lab, pharmacy, billing, notification, report · **Infra:** Eureka + RabbitMQ.

---

## Quick start (any new machine)

```bash
git clone git@github.com:Dangvinh77/MediFlow.git && cd MediFlow
# Windows:
powershell -ExecutionPolicy Bypass -File scripts/bootstrap.ps1
# macOS/Linux:
bash scripts/bootstrap.sh
```

Then read **[`docs/ai/README.md`](docs/ai/README.md)** — that is the coding standard for the whole team — and set up your IDE via **[`docs/ai/11-ide-setup.md`](docs/ai/11-ide-setup.md)** (IntelliJ recommended; mixed IDEs supported).

### Prerequisites (installed manually — by design)
| Tool | Who needs it | Notes |
|------|--------------|-------|
| JDK 21 (LTS) | everyone | build/run services |
| Maven 3.9+ (or wrapper) | everyone | multi-module build |
| Git | everyone | |
| Docker | everyone (recommended) | `docker compose up -d` gives you PostgreSQL + RabbitMQ; also required for integration tests (Testcontainers) |
| Node 20+ & pnpm | frontend work | Next.js client |
| `codebase-memory-mcp` | **optional**, Claude Code users | graph index for faster code navigation; see below |

---

## How the shared framework works

**One source of truth, three entry points.** All coding rules live in [`docs/ai/`](docs/ai/README.md). Each assistant reads a thin pointer that references it:

```
docs/ai/**                    ← the ONLY place rules are written
   ▲
   │
AGENTS.md                     ← the single entry point (agents.md standard)
   │                            read natively by Codex, Cursor, Copilot, Gemini CLI,
   │                            Aider, Windsurf, Zed… (30+ tools, 60k+ repos)
   ├── CLAUDE.md               `@AGENTS.md` import + Claude-only extras
   ├── .cursor/rules/          thin pointer, zero rules of its own
   └── .claude/rules/          path-scoped (frontend.md loads only for frontend/**)

frontend/AGENTS.md            ← nested; the spec says the NEAREST file wins
   └── frontend/CLAUDE.md      `@AGENTS.md` import
```

**One file, every tool.** [AGENTS.md](https://agents.md/) became the cross-tool standard under the
Linux Foundation's Agentic AI Foundation (OpenAI, Anthropic, Block). Claude Code is the one holdout
— it reads `CLAUDE.md` — so per [Anthropic's own guidance](https://code.claude.com/docs/en/memory)
our `CLAUDE.md` is a one-line `@AGENTS.md` import plus Claude-specific notes. Nothing is written twice.

**Two scopes.** Root covers the backend; `frontend/` overrides for TypeScript and states plainly
that the Java rules (clean architecture, JPA, `@PreAuthorize`) do **not** apply there. Without that
separation an assistant editing a `.tsx` file happily invents a `domain/application/infrastructure`
tree in React.

Change a rule → edit `docs/ai/*` **once**. Every tool and every teammate stays in sync on the next pull. Never duplicate rules into the entry files — they are pointers, not rulebooks.

**Everything is vendored (no marketplace).** All Claude tooling is committed into the repo and installed by simply cloning:
- `.claude/agents/` — `spring-boot-engineer`, `java-architect`, `code-reviewer`, `test-engineer`
- `.claude/commands/` — `/new-service`, `/index-codebase`, `/review-pr`
- `.claude/skills/new-microservice/` — tool-agnostic scaffolder (Codex/Cursor can read it too)
- `.claude/settings.json` — shared permissions + a SessionStart reminder hook (committed)
- `.mcp.json` — project-scoped MCP server config (committed)

Machine-specific values never get committed: `.mcp.local.json`, `.claude/settings.local.json`, `application-local.yml`, `.env` are all gitignored. A `.mcp.local.json.example` shows how to override the MCP path.

---

## Codebase Memory (codebase-memory-mcp)

This gives Claude a **knowledge graph** of the code (functions, calls, routes, events) for precise navigation — far better than text search on a microservices monorepo.

> **Optional.** Nothing in this repo requires it. Without it, Claude/Codex/Cursor fall back to
> normal file search — slower and more token-hungry on a monorepo this size, but fully functional.
> `.mcp.json` is committed, so the server is *configured* for everyone; it is **not** installed for you.

**Install it first — it is a native binary, not an npm package.** Download the release for your OS
from [github.com/DeusData/codebase-memory-mcp](https://github.com/DeusData/codebase-memory-mcp)
and put it on your `PATH`. Verify with:

```bash
codebase-memory-mcp --version
```

**Portability model:** the graph index is per-machine and **not committed** (`.codebase-memory/` is gitignored). Each user builds it locally after cloning:

1. Install the binary so it resolves on `PATH` — or copy `.mcp.local.json.example` to `.mcp.local.json` (gitignored) and point `command` at its absolute path.
2. Restart Claude Code so it picks up `.mcp.json`. Check that `mcp__codebase-memory-mcp__*` tools are available; if they are absent, the binary was not found.
3. Run **`/index-codebase`** (or ask Claude to run `index_repository` on the repo root). First run: `mode: "full"`.
4. Re-index after big structural changes (or run `moderate`/`fast` for quick refreshes).

**Optional team sharing:** run the index with `persistence: true` to write a compressed `.codebase-memory/graph.db.zst`. Teammates can bootstrap from that artifact instead of a full re-index. It's gitignored by default; the team can choose to commit it (see the exception line in `.gitignore`) — trade-off: faster onboarding vs. it going stale between commits. Recommended: keep it gitignored and re-index locally.

> **Codex / Cursor users:** `codebase-memory-mcp` is Claude-side and may be unavailable to you — that's fine. Your source of truth is `docs/ai/` + `EProject/*.html`. You lose the graph tool, not the standards.

---

## Infrastructure (PostgreSQL + RabbitMQ)

The fastest path — one command, all eight databases created for you:

```bash
docker compose up -d
```

That starts **PostgreSQL** on `5432` (running [`scripts/init-databases.sql`](scripts/init-databases.sql), which creates
`mediflow_organization`, `mediflow_patient`, `mediflow_clinical`, `mediflow_lab`,
`mediflow_pharmacy`, `mediflow_billing`, `mediflow_notification`, `mediflow_report`) and
**RabbitMQ** on `5672` with its management UI at http://localhost:15672 (`guest`/`guest`).

Only the infrastructure runs in Docker — the Java services and the frontend still run from
your IDE, so hot reload and the debugger keep working.

| | |
|---|---|
| `docker compose ps` | check both are healthy |
| `docker compose down` | stop, keep the data |
| `docker compose down -v` | stop and **wipe** every database |

> 🇻🇳 **Chưa dùng PostgreSQL bao giờ?** Đọc [`docs/huong-dan-postgres.md`](docs/huong-dan-postgres.md) —
> hướng dẫn tiếng Việt từ đầu: kết nối bằng DBeaver, lệnh `psql` cần biết, SQL cơ bản, và các lỗi
> thường gặp. Không cần cài PostgreSQL vào máy.

**Prefer a local install?** Install PostgreSQL and RabbitMQ yourself, then run
`psql -U postgres -f scripts/init-databases.sql` once.

Connection defaults live in each service's `application.yml` and read env vars
`MEDIFLOW_DB_USER` / `MEDIFLOW_DB_PASSWORD` (defaults `postgres`/`postgres`) and
`MEDIFLOW_RABBIT_HOST` / `_USER` / `_PASSWORD`. Override locally in a gitignored
`application-local.yml` — never commit real credentials.

The Eureka server (8761) must also be running before any business service starts.

## Building & running

All 11 modules exist. Only `patient-service` has code in it today — the other seven are
**skeletons** (module + dependencies + config + the mandated package layout, no business logic).

Eight business services in three groups — each one exists for a reason you can state in a sentence:

| Module | Port | Database | Base path | Why it exists |
|--------|------|----------|-----------|---------------|
| `eureka-server` | 8761 | — | — | service registry |
| `gateway` | 8080 | — | routes everything below | one entry point, JWT |
| **Reference data** | | | | |
| `organization-service` | 8089 | `mediflow_organization` | `/api/v1/org` | departments, staff, accounts |
| `patient-service` | 8081 | `mediflow_patient` | `/api/v1/patients` | master patient index |
| **Departments (khoa/phòng)** | | | | |
| `clinical-service` | 8082 | `mediflow_clinical` | `/api/v1/appointments`, `/api/v1/records` | Khoa Khám bệnh |
| `lab-service` | 8084 | `mediflow_lab` | `/api/v1/lab` | Khoa Xét nghiệm |
| `pharmacy-service` | 8085 | `mediflow_pharmacy` | `/api/v1/pharmacy` | Khoa Dược |
| `billing-service` | 8086 | `mediflow_billing` | `/api/v1/billing` | Phòng Viện phí |
| **Support** | | | | |
| `notification-service` | 8087 | `mediflow_notification` | `/api/v1/notifications` | email/SMS/in-app |
| `report-service` | 8088 | `mediflow_report` | `/api/v1/reports` | read model from events |

```bash
mvn -q -DskipTests install      # build all modules
mvn -pl patient-service -am test    # test one service (+ its deps)
mvn -pl patient-service -am verify  # integration tests (needs Docker)
```

**Start order:** `eureka-server` (8761) → `gateway` (8080) → any business service.
Each is a Spring Boot app: `mvn -pl <module> -am spring-boot:run`.

### Frontend (Next.js)

```bash
cd frontend
pnpm install
pnpm dev            # http://localhost:3000  (proxies /api/* to the gateway on :8080)
```

See [`docs/ai/12-frontend.md`](docs/ai/12-frontend.md). Full-stack order: eureka → gateway → patient-service → `pnpm dev`.

Demo login (stub auth on the gateway — replace before real use):
`POST http://localhost:8080/api/v1/auth/login` with `{"username":"admin","password":"admin123"}`.

Add a new service with **`/new-service <name>`** (Claude) or by following [`.claude/skills/new-microservice/SKILL.md`](.claude/skills/new-microservice/SKILL.md) + [`docs/ai/04-microservice-blueprint.md`](docs/ai/04-microservice-blueprint.md).

---

## Repo layout

```
MediFlow/
├── README.md                     ← you are here
├── CLAUDE.md / AGENTS.md         ← Claude / Codex entry pointers → docs/ai
├── .cursor/rules/project.mdc     ← Cursor entry pointer → docs/ai
├── .mcp.json (+ .example)        ← codebase-memory-mcp config (committed)
├── .gitignore / .editorconfig
├── EProject/                     ← authoritative design docs (one HTML per service)
│   └── backend-spec/             ← implementation specs: DDL, ports, DTOs, algorithms, test cases
├── docs/ai/                      ← SINGLE SOURCE OF TRUTH for coding standards
│   ├── 00..11 *.md               ← overview, architecture, standards, blueprint, ide-setup, ...
│   └── services/*.md             ← per-service bounded context / data / events
├── .claude/                      ← vendored Claude tooling (agents, commands, skills, hooks, settings)
├── scripts/                      ← bootstrap.ps1 / bootstrap.sh
├── frontend/                     ← Next.js web client (App Router, TS, Tailwind, pnpm)
├── common/ eureka-server/ gateway/   ← shared lib + infra + API gateway
└── organization-service/ patient-service/          ← reference data
    clinical-service/ lab-service/
    pharmacy-service/ billing-service/              ← departments
    notification-service/ report-service/           ← support
```

Every business service has the same internal shape (clean architecture — see
[`docs/ai/04-microservice-blueprint.md`](docs/ai/04-microservice-blueprint.md)):

```
src/main/java/com/mediflow/<service>/
├── domain/          ← pure Java: model + rules. No Spring, no JPA.
├── application/     ← use cases; port/in + port/out. Depends on domain only.
└── infrastructure/  ← web, persistence, messaging, client, security, config.
```

Dependencies point inward only: `infrastructure → application → domain`.

## Contributing
See [`docs/ai/10-git-workflow.md`](docs/ai/10-git-workflow.md): Conventional Commits, focused per-service PRs, and the pre-merge checklist (blueprint layout, boundaries, security roles, events, tests). Re-index codebase memory after structural changes.
