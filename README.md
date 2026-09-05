# MediFlow — Hospital Microservices (Spring Boot Monorepo)

<!-- commit-activity:start -->
## Commit activity

Changelog updated through **2026-09-05 17:15:06 Asia/Saigon** · **128 unique commits**

| Contributor | Commits | Active days | Avg/active day | Peak date | Peak hour | Latest commit |
|---|---:|---:|---:|---|---|---|
| Harori | 108 | 14 | 7.71 | 2026-09-04 (31) | 14:00 (13) | 2026-09-05 17:15:06 |
| LQHuy0210 | 13 | 7 | 1.86 | 2026-08-13 (4) | 15:00 (4) | 2026-09-04 18:32:31 |
| locgit-89 | 5 | 2 | 2.50 | 2026-09-04 (4) | 14:00 (2) | 2026-09-04 15:37:39 |
| TranHoangAnh94 | 2 | 2 | 1.00 | 2026-08-13 (1) | 18:00 (1) | 2026-09-04 18:21:27 |

![Commits by day](docs/assets/commit-activity-by-day.svg?v=44dbe94174be)

![Commits by hour](docs/assets/commit-activity-by-hour.svg?v=295b48a75ef5)

_Source: `.changelog/entries.jsonl`; this is repository changelog data, not GitHub Insights._
<!-- commit-activity:end -->

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

- **Design docs (authoritative):** [`docs/eproject_general_plan/`](docs/eproject_general_plan/) — one HTML per service.
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

> **Git hooks — single-author policy.** The repo ships hooks under `scripts/git-hooks/`
> (`prepare-commit-msg` strips `Co-Authored-By:` lines; `commit-msg` rejects any commit that still
> carries one, so agents like Claude/Codex/Copilot never get recorded as co-authors). Git only runs
> them when `core.hooksPath` points there, and that setting is **local to each machine — it never
> travels with `git clone`**. The bootstrap above configures it automatically. On an **existing**
> clone, run once:

```bash
scripts\setup-hooks.bat      # Windows
bash scripts/setup-hooks.sh  # macOS/Linux
```

Then read **[`docs/ai/README.md`](docs/ai/README.md)** — that is the coding standard for the whole team — and set up your IDE via **[`docs/ai/11-ide-setup.md`](docs/ai/11-ide-setup.md)** (IntelliJ recommended; mixed IDEs supported).

### Prerequisites (installed manually — by design)
| Tool | Who needs it | Notes |
|------|--------------|-------|
| JDK 21 (LTS) | everyone | build/run services |
| Maven 3.9+ (or wrapper) | everyone | multi-module build |
| Git | everyone | |
| Git hooks | everyone | single-author policy (strips `Co-Authored-By`); setup: `scripts/setup-hooks.bat` — bootstrap does it automatically |
| Docker | everyone (recommended) | `docker compose up -d` gives you PostgreSQL + RabbitMQ; also required for integration tests (Testcontainers) |
| Node 20+ & pnpm | frontend work | Next.js client |
| `codebase-memory-mcp` | **optional**, Claude Code and Codex users | graph index for faster code navigation; setup: `scripts/setup-codebase-memory.bat` |
| Understand-Anything | **optional**, Claude Code users | visual codebase dashboard; setup: `scripts/setup-understand-anything.bat` |

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
   ├── .codex/                 Codex config, agents, and hooks
   ├── .agents/skills/         team-shared Codex workflows
   ├── .cursor/rules/          thin pointer, zero rules of its own
   └── .claude/rules/          path-scoped (frontend.md loads only for frontend/**)

frontend/AGENTS.md            ← nested; the spec says the NEAREST file wins
   └── frontend/CLAUDE.md      `@AGENTS.md` import
mobile/AGENTS.md              ← nested Flutter instructions for Codex and other AGENTS readers
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

**Everything essential is vendored.** Claude and Codex adapters are committed and installed by cloning:
- `.claude/agents/` — `spring-boot-engineer`, `java-architect`, `code-reviewer`, `test-engineer`
- `.claude/commands/` — `/new-service`, `/index-codebase`, `/review-pr`
- `.claude/skills/new-microservice/` — tool-agnostic scaffolder (Codex/Cursor can read it too)
- `.claude/settings.json` — shared permissions + a SessionStart reminder hook (committed)
- `.codex/agents/` — native Codex mirrors of the four Claude agents
- `.codex/config.toml` + `.codex/hooks.json` — project MCP and lifecycle configuration
- `.agents/skills/` — Codex-native `index-codebase`, `new-service`, `review-pr`, and `new-microservice` skills
- `.mcp.json` — project-scoped MCP server config (committed)

Machine-specific values never get committed: `.mcp.local.json`, `.claude/settings.local.json`, `application-local.yml`, and `.env` are gitignored. Codex personal settings belong in `~/.codex/config.toml`; Codex does not load a project `.codex/settings.local.json`.

After changing the Claude dev kit, regenerate and audit its Codex adapters:

```bash
node scripts/sync-agent-devkit.mjs --write
node scripts/sync-agent-devkit.mjs --check
```

---

## Optional AI codebase tools

Two optional tools make code exploration faster and more visual. Both are **optional** — nothing in this repo requires them.

> Full docs: [`docs/ai/13-codebase-tools.md`](docs/ai/13-codebase-tools.md).

### codebase-memory-mcp — fast graph queries for agents

A native binary MCP server that indexes the codebase into a knowledge graph. AI agents use it for sub-ms structural queries (call chains, imports, routes) instead of Grep/Glob/Read.

```bash
# Install (one-time per machine):
scripts\setup-codebase-memory.bat         # Windows
scripts/setup-codebase-memory.sh          # macOS/Linux
```

Then restart Claude Code or Codex and build the per-machine index (`.codebase-memory/` is gitignored):

- Claude Code: run `/index-codebase`.
- Codex: invoke `$index-codebase` or ask Codex to index the current repository.

> **Portability:** if the binary isn't on your PATH, copy `.mcp.local.json.example` → `.mcp.local.json` (gitignored) and point `command` to the absolute path.
>
> The graph MCP is optional in both clients. When absent, agents fall back to `docs/ai/`, `docs/eproject_general_plan/*.html`, and text search.

### Understand-Anything — visual dashboard for humans

A Claude Code plugin that analyzes the codebase with a multi-agent pipeline (tree-sitter + LLM) and produces an interactive knowledge graph dashboard with guided tours, semantic search, and diff impact analysis.

```bash
# Install (one-time per machine):
scripts\setup-understand-anything.bat     # Windows
scripts/setup-understand-anything.sh      # macOS/Linux
```

Then run `/understand` inside Claude Code. First run uses tokens (LLM analysis); subsequent runs are incremental.

| Command | What it does |
|---------|-------------|
| `/understand` | Analyze codebase → open dashboard |
| `/understand-chat How does payment work?` | Ask questions about the codebase |
| `/understand-diff` | See ripple effects of your changes |
| `/understand-onboard` | Generate onboarding guide |

### Install both at once

```bash
scripts\setup-tools.bat                   # Windows (installs both)
```

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
mvn -q -DskipTests install                   # build all modules
mvn -pl backend/patient-service -am test         # test one service (+ its deps)
mvn -pl backend/patient-service -am verify       # integration tests (needs Docker)
```

**Start order:** `eureka-server` (8761) → `gateway` (8080) → any business service.
Each is a Spring Boot app: `mvn -pl backend/<module> -am spring-boot:run`.

### Frontend (Next.js)

```bash
cd frontend
pnpm install
pnpm dev            # http://localhost:3000  (proxies /api/* to the gateway on :8080)
```

See [`docs/ai/12-frontend.md`](docs/ai/12-frontend.md). Full-stack order: eureka → gateway → patient-service → `pnpm dev`.

### Mobile (Flutter)

```bash
cd mobile
flutter pub get
flutter run                         # connected device / emulator
flutter test                        # run all tests
```

Full docs: [`docs/ai/14-flutter.md`](docs/ai/14-flutter.md).

Demo login (stub auth on the gateway — replace before real use):
`POST http://localhost:8080/api/v1/auth/login` with `{"username":"admin","password":"admin123"}`.

Add a new service with **`/new-service <name>`** in Claude or **`$new-service`** in Codex. Both adapters use the same blueprint and [`docs/ai/04-microservice-blueprint.md`](docs/ai/04-microservice-blueprint.md).

---

## Repo layout

```
MediFlow/
├── README.md                     ← you are here
├── CLAUDE.md / AGENTS.md         ← Claude / Codex entry pointers → docs/ai
├── .cursor/rules/project.mdc     ← Cursor entry pointer → docs/ai
├── .mcp.json (+ .example)        ← codebase-memory-mcp config (committed)
├── .gitignore / .editorconfig
├── docs/eproject_general_plan/                     ← authoritative design docs (one HTML per service)
│   └── backend-spec/             ← implementation specs: DDL, ports, DTOs, algorithms, test cases
├── docs/ai/                      ← SINGLE SOURCE OF TRUTH for coding standards
│   ├── 00..13 *.md               ← overview, architecture, standards, blueprint, ide-setup, codebase-tools, ...
│   └── services/*.md             ← per-service bounded context / data / events
├── .claude/                      ← vendored Claude tooling (agents, commands, skills, hooks, settings)
├── .codex/                       ← Codex config, agents, and hooks
├── .agents/skills/               ← team-shared Codex workflows
├── scripts/                      ← bootstrap.ps1 / .sh + tool setup scripts
├── backend/                      ← ALL Java microservices (common, eureka, gateway, 8 business services)
│   ├── common/                   ← shared lib (envelope, pagination, base exceptions)
│   ├── eureka-server/            ← service registry
│   ├── gateway/                  ← API gateway (JWT, routing)
│   ├── organization-service/     ← reference: departments, staff, accounts
│   ├── patient-service/          ← reference: master patient index
│   ├── clinical-service/         ← Khoa Khám bệnh (appointments + records)
│   ├── lab-service/              ← Khoa Xét nghiệm
│   ├── pharmacy-service/         ← Khoa Dược
│   ├── billing-service/          ← Phòng Viện phí
│   └── notification-service/ report-service/ ← support
├── mobile/                       ← Flutter mobile app (Clean Architecture, Riverpod)
│   ├── AGENTS.md                 ← nested mobile instructions
│   └── lib/
│       ├── app/                  ← app config, GoRouter, DI
│       ├── core/                 ← theme, network (Dio), storage
│       └── features/             ← 10 feature folders (mirror backend services)
├── frontend/                     ← Next.js web client (App Router, TS, Tailwind, pnpm)
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
