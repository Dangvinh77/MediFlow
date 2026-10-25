# MediFlow: A Microservices-Based Hospital Information System for Outpatient Care Management

<!-- commit-activity:start -->
## Commit activity

Changelog updated through **2026-10-26 01:17:19 Asia/Saigon** · **998 unique commits**

| Contributor | Commits | Active days | Avg/active day | Peak date | Peak hour | Latest commit |
|---|---:|---:|---:|---|---|---|
| Harori | 625 | 59 | 10.59 | 2026-09-27 (35) | 18:00 (87) | 2026-10-26 01:17:19 |
| LQHuy0210 | 219 | 38 | 5.76 | 2026-09-13 (27) | 08:00 (34) | 2026-10-25 17:15:17 |
| TranHoangAnh94 | 87 | 30 | 2.90 | 2026-10-22 (9) | 23:00 (16) | 2026-10-23 10:09:29 |
| locgit-89 | 67 | 19 | 3.53 | 2026-10-16 (11) | 15:00 (19) | 2026-10-24 00:49:06 |

![Commits by day](docs/assets/commit-activity-by-day.svg?v=55632e01eae7)

![Commits — last 72 hours](docs/assets/commit-activity-by-hour.svg?v=8a304b1dcd2c)

_Source: `.changelog/entries.jsonl`; this is repository changelog data, not GitHub Insights._
<!-- commit-activity:end -->

## Project overview

**MediFlow** is a distributed hospital information system designed to coordinate outpatient workflows, including patient registration, appointments, medical examinations, laboratory testing, prescriptions, billing, dispensing, reporting and in-app notifications. Inpatient care and surgery management are reserved for future development; their existing source and data are preserved.

**Định hướng:** Hệ thống thông tin bệnh viện dựa trên microservices, tập trung quản lý khám chữa bệnh ngoại trú. MVP hướng tới phòng khám đa khoa có nhiều bộ phận hoặc khoa khám bệnh ngoại trú của bệnh viện. Phòng mạch nhỏ có thể dùng một phần chức năng; việc vận hành nhiều service và broker cần được cân nhắc theo nhu cầu. Đây là phạm vi thiết kế của đồ án, chưa phải xác nhận triển khai vận hành tại cơ sở y tế.

| | |
|---|---|
| **Project title** | MediFlow: A Microservices-Based Hospital Information System for Outpatient Care Management |
| **Current scope** | Outpatient Healthcare Management; HIS with an outpatient MVP |
| **Intended setting** | Multi-specialty clinics and hospital outpatient departments |
| **Architecture** | Microservices, Gateway, context-owned PostgreSQL stores, RabbitMQ events and Eureka discovery |
| **Clients** | Next.js web; Flutter foundation using the real Gateway |
| **Deferred** | Admission, beds, inpatient treatment and surgery; full advanced Report/Billing scope |

### Outpatient MVP scope

“In MVP” means included in requirements. It does **not** mean every API, screen, cross-service gate or recovery scenario has passed release acceptance. Current evidence and remaining dependencies are in the [implementation plan](docs/plans/05-implementation-plan.md), [handoffs](docs/handoffs/README.md) and [frontend workboard](frontend/docs/frontend-workboard.md).

| Capability | MVP scope |
|---|---|
| Patients, doctors, staff and departments | Included — Core, retaining separate Organization/Patient authority |
| Appointments and reception/check-in | Included — Clinical |
| Examination, diagnoses and outpatient records | Included — Clinical |
| Laboratory orders, processing and results | Included — Lab |
| Prescribing and medication dispensing | Included — Clinical/Pharmacy contracts |
| Examination, test and medication fees | Included — Billing; authorization/clearance gates still apply |
| Reporting and notifications | Included — outpatient read models and in-app notifications |
| Admission, beds and inpatient treatment | Deferred; preserve existing source/data |
| Surgery | Deferred; preserve existing source/data |

### Deployment target and current source

The MVP targets **six business deployments**: Core, Clinical, Lab, Pharmacy, Billing and Support. Gateway and Eureka are infrastructure; common is a library. Six deployments do not imply six databases: the eight active business contexts keep their own stores.

The root Maven reactor lists 13 modules. Core contains Organization and Patient packages with separate persistence boundaries. Support composes the Report and Notification plain libraries with separate stores, transactions, security and worker budgets; their standalone launchers remain available for rollback. Compose defaults to six business deployments and Gateway forwards both Support API groups without changing public paths. Inpatient and Surgery remain under the deferred Compose profile. On 2026-10-17, real PostgreSQL/RabbitMQ, packaged Support restart and an actual six-app EXAM/Rx payment/dispense/current-store rollback test passed. The subsequent cold-copy inventory/rehearsal of the existing local Compose volumes also passed, preserving four new writes and original-volume fingerprints; default activation gates remain OFF. Other environments require their own historical-data reconciliation. See the [Support runbook](backend/support-service/README.md).

The repository also includes a shared AI coding framework so contributors using Claude Code, Codex, Cursor, or other AGENTS.md-compatible tools follow the same engineering standards after a `git pull`.

> Repo: `git@github.com:Dangvinh77/MediFlow.git`

> ⚠️ **DEMO / WORK IN PROGRESS.** This is an academic demonstration project. The code
> (services, authentication, frontend, configs) is **subject to change** and is **not
> production-ready** — integration flags and runtime acceptance must be verified, and some
> contexts are deferred or incomplete. MediFlow belongs to the HIS/HMS domain, but it should not be treated as
> a complete, compliant hospital information system for real-world deployment.
>
> ⚠️ **DEMO / ĐANG PHÁT TRIỂN.** Đây là đồ án demo phục vụ học tập. Toàn bộ code (service, auth
> ở gateway, frontend, cấu hình) **có thể thay đổi** và **chưa dùng cho production** — các gate và
> luồng tích hợp phải được nghiệm thu, một số bounded context đã hoãn hoặc chưa hoàn thiện.
> MediFlow thuộc nhóm HIS/HMS nhưng chưa phải hệ thống thông tin
> bệnh viện hoàn chỉnh, tuân thủ đầy đủ để triển khai thực tế.

- **Design docs (authoritative):** [`docs/README.md`](docs/README.md) — simplified system design, contracts, plan and acceptance.
- **AI coding rules (single source of truth):** [`docs/ai/`](docs/ai/README.md).
- **Current structure and target:** see the deployment section above and [system design](docs/plans/01-system-design.md). Core and Support composition and the isolated six-app EXAM/Rx acceptance are present; historical-data rollout and separate owner workflow acceptance remain distinct.
- **Platform and infrastructure:** API gateway, Netflix Eureka, RabbitMQ, and PostgreSQL with database-per-service ownership.

---

## Quick start (any new machine)

```bash
git clone git@github.com:Dangvinh77/MediFlow.git && cd MediFlow
# Windows:
powershell -ExecutionPolicy Bypass -File scripts/bootstrap.ps1
# macOS/Linux:
bash scripts/bootstrap.sh
```

> **Git hooks — four-contributor policy.** The repo ships hooks under `scripts/git-hooks/`.
> `prepare-commit-msg` strips AI attribution trailers; `commit-msg` rejects co-authors and any
> author identity outside [`scripts/allowed-contributors.json`](scripts/allowed-contributors.json).
> Git only runs
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
| Git hooks | everyone | only 4 allowed contributors, no co-author; setup: `scripts/setup-hooks.bat` — bootstrap does it automatically |
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
> The graph MCP is optional in both clients. When absent, agents fall back to `docs/ai/`, `docs/plans/`, live source and text search.

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

Start the shared infrastructure; fresh volumes initialize ten context databases, including the two deferred stores:

```bash
docker compose up -d postgres rabbitmq
```

That starts **PostgreSQL** on `5432` (running [`scripts/init-databases.sql`](scripts/init-databases.sql), which creates
`mediflow_organization`, `mediflow_patient`, `mediflow_clinical`, `mediflow_lab`,
`mediflow_pharmacy`, `mediflow_billing`, `mediflow_notification`, `mediflow_report`, plus
`mediflow_inpatient` and `mediflow_surgery` deferred databases) and
**RabbitMQ** on `5672` with its management UI at http://localhost:15672 (`guest`/`guest`).

The checked-in Compose file builds Core and Support and selects six business deployments by default. Report/Notification standalone launchers require `legacy-support`; Inpatient/Surgery require `deferred`. New outpatient integration flags remain opt-in. Follow the [Support runbook](backend/support-service/README.md) before switching existing stores or activating delivery.

| | |
|---|---|
| `docker compose ps` | check both are healthy |
| `docker compose down` | stop, keep the data |
| `docker compose down -v` | stop and **wipe** every database |

> 🇻🇳 **Chưa dùng PostgreSQL bao giờ?** Đọc [`docs/huong-dan-postgres.md`](docs/plans/huong-dan-postgres.md) —
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

The root reactor has **13 Maven modules**, including common, Eureka, Gateway, six default business deployments, two compatibility library/launcher modules and two deferred modules. Organization/Patient live inside Core; Support hosts Report/Notification. The module count includes libraries and preserved launchers, so it exceeds the default running business service count.

| Module | Default port | Stores / public Gateway paths | MVP position |
|---|---:|---|---|
| eureka-server | 8761 | Service discovery | Infrastructure |
| gateway | 8080 | /api/* entry point and auth facade | Infrastructure |
| core-service | 8089 | Organization/Patient stores; /api/v1/org, /api/v1/patients | Core deployment |
| clinical-service | 8082 | Clinical store; /api/v1/appointments, /api/v1/records | Outpatient |
| lab-service | 8084 | Lab store; /api/v1/lab | Outpatient |
| pharmacy-service | 8085 | Pharmacy store; /api/v1/pharmacy | Outpatient |
| billing-service | 8086 | Billing store; /api/v1/billing | Outpatient |
| support-service | 8087 | Separate Report/Notification stores; /api/v1/reports, /api/v1/notifications | Support deployment |
| notification-service | 8087 | Notification library + original store | Standalone rollback only (`legacy-support`) |
| report-service | 8088 | Report library + original store | Standalone rollback only (`legacy-support`) |
| inpatient-service | 8090 | Preserved Inpatient store/code | Deferred from MVP |
| surgery-service | 8091 | Preserved Surgery store/code | Deferred from MVP |

The common library has no runtime port. Public paths above describe Gateway routes; clients call Gateway, never service ports directly.

```bash
mvn -q -DskipTests install                   # build all modules
mvn -pl backend/core-service -am test         # test one service (+ its deps)
mvn -pl backend/core-service -am verify       # integration tests (needs Docker)
```

**Start order:** `eureka-server` (8761) → `gateway` (8080) → any business service.
Build dependencies first, then launch the selected executable JAR, for example `java -jar backend/core-service/target/core-service-0.0.1-SNAPSHOT.jar`, or run its application class from the IDE. Configure shared JWT secrets and context database credentials consistently; consult each module README and the [acceptance runbook](docs/plans/06-acceptance-runbook.md).

### Frontend (Next.js)

```bash
cd frontend
pnpm install
pnpm dev            # http://localhost:3000  (proxies /api/* to the gateway on :8080)
```

See [`docs/ai/12-frontend.md`](docs/ai/12-frontend.md). Full-stack order: PostgreSQL/RabbitMQ → Eureka → Core and Gateway → required business services → web client. Gateway login verifies real Core Organization accounts.

### Mobile (Flutter)

The Flutter client uses the real backend only. From mobile/ on Windows:

```powershell
./tool/run_web.ps1    # http://localhost:4300, same-origin proxy to actual Gateway
./tool/check.ps1      # format, analyzer, app/loader tests and web build
```

[Mobile README](mobile/README.md) covers device Gateway URLs, Android/iOS/Windows toolchains and current feature coverage. [Contribution instructions](mobile/docs/contributing.md) divide contexts among all four owners. The foundation includes login, role-aware navigation and read-only Appointment/Lab slices; other workspaces remain unfinished. Native hosts being present does not certify native releases.

### Authentication

POST /api/v1/auth/login is a real Gateway facade: it verifies an account through Core's Organization authority and returns signed tokens. Use an account provisioned for your environment; there is no documented universal admin/admin123 or stub login. A missing upstream is an availability error, not a reason to bypass authentication.

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
├── docs/README.md                ← simplified MVP documentation entry point
├── docs/plans/01..06 *.md              ← architecture, flows, contracts, data, 18-task plan, acceptance
├── docs/ai/README.md             ← coding rules; remaining old-path files are short entry pointers
├── .claude/                      ← vendored Claude tooling (agents, commands, skills, hooks, settings)
├── .codex/                       ← Codex config, agents, and hooks
├── .agents/skills/               ← team-shared Codex workflows
├── scripts/                      ← bootstrap.ps1 / .sh + tool setup scripts
├── backend/                      ← current Maven modules; six business deployments remain the MVP target
│   ├── common/                   ← shared lib (envelope, pagination, base exceptions)
│   ├── eureka-server/            ← service registry
│   ├── gateway/                  ← API gateway (JWT, routing)
│   ├── core-service/             ← Organization + Patient, separate persistence boundaries
│   ├── clinical-service/         ← Khoa Khám bệnh (appointments + records)
│   ├── lab-service/              ← Khoa Xét nghiệm
│   ├── pharmacy-service/         ← Khoa Dược
│   ├── billing-service/          ← Phòng Viện phí
│   ├── support-service/          ← Report + Notification host
│   ├── notification-service/ report-service/ ← context libraries + rollback launchers
│   └── inpatient-service/ surgery-service/   ← preserved, deferred from MVP
├── mobile/                       ← Flutter mobile app (Clean Architecture, Riverpod)
│   ├── AGENTS.md                 ← nested mobile instructions
│   └── lib/
│       ├── app/                  ← app config, GoRouter, DI
│       ├── core/                 ← shared Gateway network, config, domain types and widgets
│       └── features/             ← auth + 9 feature folders; ownership follows bounded contexts
├── frontend/                     ← Next.js web client (App Router, TS, Tailwind, pnpm)
```

Each business context keeps its own clean-architecture layers; Core co-hosts two contexts without merging their persistence boundaries (see
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
