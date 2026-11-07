# MediFlow: A Microservices-Based Hospital Information System for Outpatient Care Management

MediFlow is an academic hospital information system for multi-specialty clinics and hospital
outpatient departments. It covers the outpatient journey from registration to payment and
dispensing, with a Spring Boot microservice backend, a Next.js web client and a Flutter mobile
client that both go through one API Gateway.

> **Academic demo.** MediFlow is not production-ready and is not a certified or compliant
> hospital system. Inpatient care and surgery are out of scope: their modules are kept in the
> repository but are not part of the default build profile or demo.

- Repository: `git@github.com:Dangvinh77/MediFlow.git`
- Design documentation: [`docs/README.md`](docs/README.md)
- Coding standards: [`docs/ai/README.md`](docs/ai/README.md)

## Implemented features

| Area | What is implemented |
|---|---|
| Organization | Departments (clinical, paraclinical, administrative), staff, staff accounts and security, department-head appointment with history, workplace rooms (consultation rooms per clinical department, department offices, single workplaces for lab, pharmacy, cashier, nursing and administration) |
| Patients | Patient records, patient accounts issued by nurses, email welcome, forgot/reset password |
| Appointments | Doctor shifts and consultation sessions per department, booking, reception and check-in with session capacity, doctor calendar |
| Examination | Outpatient medical records, diagnoses, lab orders from a record, prescribing |
| Laboratory | Test orders, processing and results |
| Pharmacy | Drug catalogue, prescriptions and outpatient dispensing |
| Billing | Invoices, payment requests, cashier payments, refunds, scheduled payments, revenue view |
| Reports | Operational and reconciled outpatient reports |
| Notifications | In-app notifications, sending notifications, SMTP email delivery, local demo mailbox |
| Security | Gateway login backed by Core accounts, JWT, role checks on every endpoint (`@PreAuthorize`) and at the Gateway |

## Architecture

- **Backend:** Java 21, Spring Boot, Maven multi-module build (13 modules).
- **Messaging and storage:** RabbitMQ events between contexts. Each context owns its own
  PostgreSQL database; contexts reference each other only by UUID, with no cross-context
  database access.
- **Discovery and entry point:** Eureka and Spring Cloud Gateway. Clients call the Gateway's
  `/api/*` paths only, never a service port directly.
- **Default deployment:** six business applications (Core, Clinical, Lab, Pharmacy, Billing,
  Support) plus Eureka and Gateway.

| Module | Port | Role |
|---|---:|---|
| `eureka-server` | 8761 | Service discovery |
| `gateway` | 8080 | `/api/*` entry point, login and route authorization |
| `core-service` | 8089 | Organization and Patient contexts (`/api/v1/org`, `/api/v1/patients`), separate stores |
| `clinical-service` | 8082 | Appointments and medical records (`/api/v1/appointments`, `/api/v1/records`) |
| `lab-service` | 8084 | Laboratory (`/api/v1/lab`) |
| `pharmacy-service` | 8085 | Pharmacy (`/api/v1/pharmacy`) |
| `billing-service` | 8086 | Billing (`/api/v1/billing`) |
| `support-service` | 8087 | Hosts Report and Notification (`/api/v1/reports`, `/api/v1/notifications`) |
| `report-service`, `notification-service` | 8088, 8087 | Context libraries used by Support; standalone launchers via the `legacy-support` Compose profile |
| `inpatient-service`, `surgery-service` | 8090, 8091 | Out of scope; `deferred` Compose profile only |
| `common` | — | Shared library (response envelope, pagination, base exceptions) |

Every service follows the same clean-architecture layout
([`docs/ai/04-microservice-blueprint.md`](docs/ai/04-microservice-blueprint.md)):

```
src/main/java/com/mediflow/<context>/
├── domain/          pure Java model and rules, no Spring or JPA
├── application/     use cases with port/in and port/out
└── infrastructure/  web, persistence, messaging, clients, security, config
```

Dependencies point inward only: `infrastructure → application → domain`.

## Prerequisites

| Tool | Used for |
|---|---|
| JDK 21 and Maven 3.9+ | Backend build and tests |
| Docker | PostgreSQL, RabbitMQ, Testcontainers integration tests and the demo launcher |
| Python 3 | Demo launcher (database restore and seeding) |
| Node.js 20+ and pnpm | Web client |
| Flutter stable (Dart ^3.13) | Mobile client |

## Quick start on Windows (demo launcher)

The scripts in [`scripts/run/`](scripts/run/) build the backend, restore the synthetic demo
database and start everything:

```bash
scripts\run\start-backend.bat
```

```bash
scripts\run\start-frontend.bat
```

Wait for `READY` from the backend before starting the frontend. The demo Gateway listens on
port `28080`; the web client is on `http://<machine-ip>:3000`. Demo login accounts are listed in
`scripts/run/TAI_KHOAN_DEMO.txt`.

| Option | Effect |
|---|---|
| no option | Disposable demo session; data is discarded and applications stop after two hours |
| `-Fast` | Keep demo data between runs and reuse a verified build |
| `-Rebuild` | Force a Maven rebuild |
| `-ResetData` | Recreate the retained demo data |
| `-LanAddress <ip>` | Serve on that LAN address so phones on the same Wi-Fi can connect |
| `-LocalOnly` | Bind to `127.0.0.1` only |
| `-StartupParallelism 1` | Start applications one at a time on low-memory machines |

`stop-backend.bat` stops a retained (`-Fast`) session and keeps its data;
`clean-docker.bat -Preview` shows which project containers and volumes would be removed.
Email delivery reads SMTP settings from a gitignored `smtp.local.env` at the repository root.

## Manual setup

### Infrastructure

```bash
docker compose up -d postgres rabbitmq
```

PostgreSQL runs on `5432` and [`scripts/init-databases.sql`](scripts/init-databases.sql)
creates one database per context. RabbitMQ runs on `5672`, with its management UI on
http://localhost:15672. Services read `MEDIFLOW_DB_USER`, `MEDIFLOW_DB_PASSWORD`,
`MEDIFLOW_RABBIT_HOST`, `MEDIFLOW_RABBIT_USER` and `MEDIFLOW_RABBIT_PASSWORD`; put local
overrides in a gitignored `application-local.yml`.

### Backend

```bash
mvn -q -DskipTests install
```

```bash
mvn -pl backend/core-service -am test
```

Start order: `eureka-server` → `gateway` → business services, for example
`java -jar backend/core-service/target/core-service-0.0.1-SNAPSHOT.jar`. Configure the shared
JWT secret and database credentials the same way for every service. Service-specific notes are
in each module's README, for example the [Support runbook](backend/support-service/README.md).

### Web client (Next.js)

Next.js 16 App Router, TypeScript, Tailwind CSS v4. Feature folders under `frontend/src/features/`
follow the backend contexts; all HTTP goes through `src/lib/api.ts`.

```bash
cd frontend
pnpm install
pnpm dev
```

`pnpm dev` serves http://localhost:3000 and proxies `/api/*` to the Gateway on `8080`. Checks:
`pnpm typecheck`, `pnpm lint`, `pnpm test`. Rules: [`docs/ai/12-frontend.md`](docs/ai/12-frontend.md).

### Mobile client (Flutter)

Flutter with Clean Architecture and Riverpod, talking to the real Gateway only (no mock data).
From `mobile/` on Windows:

```bash
./tool/run_web.ps1
```

```bash
./tool/check.ps1
```

`run_web.ps1` serves http://localhost:4300 with a same-origin proxy to the Gateway; `check.ps1`
runs formatting, analysis, tests and a web build. Device setup and feature flags:
[`mobile/README.md`](mobile/README.md). Rules: [`docs/ai/14-flutter.md`](docs/ai/14-flutter.md).

## Authentication

`POST /api/v1/auth/login` on the Gateway verifies the account against Core's Organization context
and returns signed tokens. There is no stub or default administrator login; use an account
provisioned for your environment (or a demo account from the launcher).

## Repository layout

```
├── backend/          Maven modules listed above
├── frontend/         Next.js web client
├── mobile/           Flutter mobile client
├── docs/             design documents, plans, contracts and coding standards (docs/ai)
├── scripts/          bootstrap, git hooks, database init, demo launcher (run/, demo_25_10/)
├── docker-compose.yml
└── pom.xml           root Maven reactor
```

## Contributing

- Run the bootstrap once on a new clone. It also sets `core.hooksPath` to `scripts/git-hooks`,
  which allows only the authors in [`scripts/allowed-contributors.json`](scripts/allowed-contributors.json)
  and rejects co-author trailers:

  ```bash
  powershell -ExecutionPolicy Bypass -File scripts/bootstrap.ps1
  ```

  On macOS/Linux use `bash scripts/bootstrap.sh`; on an existing clone, `scripts\setup-hooks.bat`
  or `bash scripts/setup-hooks.sh`.
- Workflow: Conventional Commits and focused per-service pull requests
  ([`docs/ai/10-git-workflow.md`](docs/ai/10-git-workflow.md)).
- AI coding assistants read [`AGENTS.md`](AGENTS.md) (Claude Code reads `CLAUDE.md`, which imports
  it). Shared agents, commands and skills live in `.claude/`, `.codex/` and `.agents/skills/`.
  Optional code-navigation tools are described in [`docs/ai/13-codebase-tools.md`](docs/ai/13-codebase-tools.md).
