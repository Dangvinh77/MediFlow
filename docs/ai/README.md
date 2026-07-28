# AI Coding Rules — Single Source of Truth

> **This folder is the ONE canonical source of coding standards for this project.**
> Claude Code (`CLAUDE.md`), Codex (`AGENTS.md`) and Cursor (`.cursor/rules/`) all point here.
> Edit rules HERE — never duplicate them in the tool-specific entry files.

## How to use

- **Humans:** read `00` → `10` in order once, then keep `04-microservice-blueprint.md` open while coding.
- **AI assistants:** these files are loaded as context. Follow them exactly. When a rule and the design docs (`docs/eproject_general_plan/*.html`) disagree, the design docs win for *what* to build; these files win for *how* to build it.

## Index

| # | File | What it governs |
|---|------|-----------------|
| 00 | [00-project-overview.md](00-project-overview.md) | Domain, the 9 services, glossary |
| 01 | [01-architecture.md](01-architecture.md) | System topology, communication patterns |
| 02 | [02-tech-stack.md](02-tech-stack.md) | Pinned versions, libraries, build tool |
| 03 | [03-coding-standards.md](03-coding-standards.md) | Java style, naming, error handling |
| 04 | [04-microservice-blueprint.md](04-microservice-blueprint.md) | **The mandatory package & layer layout every service copies** |
| 05 | [05-api-conventions.md](05-api-conventions.md) | REST paths, DTOs, responses, validation |
| 06 | [06-events-rabbitmq.md](06-events-rabbitmq.md) | Event naming, exchanges, publish/subscribe |
| 07 | [07-security-rbac.md](07-security-rbac.md) | JWT, roles, endpoint authorization |
| 08 | [08-persistence-naming.md](08-persistence-naming.md) | DB naming (VN snake_case) ↔ Java/JSON mapping |
| 09 | [09-testing.md](09-testing.md) | Test layers, coverage bar, tools |
| 10 | [10-git-workflow.md](10-git-workflow.md) | Branches, commits, PRs |
| 11 | [11-ide-setup.md](11-ide-setup.md) | IntelliJ / VS Code (Cursor) / NetBeans setup for a mixed-IDE team |
| 12 | [12-frontend.md](12-frontend.md) | Next.js frontend — structure, gateway/auth wiring, conventions |
| 13 | [13-codebase-tools.md](13-codebase-tools.md) | Optional AI tools: codebase-memory-mcp + Understand-Anything |
| — | [services/](services/) | Per-service bounded context, data model, endpoints, events |

## Golden rules (the 30-second version)

1. **One bounded context per service.** Never reach into another service's database.
2. **Copy the blueprint** (`04`) for every new service — package layout is not negotiable.
3. **DB in Vietnamese snake_case, Java/JSON in camelCase.** Map explicitly (`08`).
4. **Change data → publish an event.** Cross-service reads are REST; cross-service reactions are events (`06`).
5. **Every endpoint declares its roles** (`07`). No endpoint is open except gateway `/auth/**` and `/actuator/health`.
6. **Money is `BigDecimal`**, IDs are `UUID`, times are `Instant`/`LocalDate` — never `float`/`double`/`String` for these.
