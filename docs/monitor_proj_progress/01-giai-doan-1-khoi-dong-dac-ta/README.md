# Phase 1 — Project Initiation and Requirements Specification

This folder contains the Phase 1 evidence package for MediFlow. It records the project problem, customer requirements, user roles, initial business forms, system boundaries, and the architecture baseline that the team will use in later phases.

The package was reconciled with repository source at commit `3f10ea6` on 10 October 2026. It describes the system that is implemented in source, not the earlier eight-service concept.

## Phase 1 objective

The teaching plan requires the team to:

1. identify the operational problem addressed by the project;
2. analyze customer and stakeholder requirements;
3. define the responsibilities of administrators, customers, staff, and specialists;
4. prepare the initial business forms and acceptance baseline.

## Documents

| File | Purpose |
|---|---|
| [Customer requirements and initial forms](giai-doan-1-crs-bieu-mau.md) | The primary Phase 1 specification: scope, stakeholders, roles, functional requirements, use cases, forms, non-functional requirements, and acceptance criteria. |
| [Source-alignment report](bao-cao-giai-trinh.md) | Evidence showing how this package was reconciled with the current backend, frontend, mobile, database, security, and integration implementation. |
| [Refresh design](phase-1-source-aligned-refresh-design.md) | Design decisions and boundaries for this documentation refresh. |
| [Refresh plan](phase-1-source-aligned-refresh-plan.md) | The implementation and verification plan used for the refresh. |

## Current system baseline

MediFlow is a hospital management platform with three client channels and ten implemented business contexts:

- Web application: Next.js App Router, TypeScript, and Tailwind CSS.
- Mobile application: Flutter.
- API access: the gateway is the only public backend entry point.
- Service discovery: Eureka.
- Synchronous integration: versioned REST APIs when a current authority decision is required.
- Asynchronous integration: RabbitMQ events with transactional outbox dispatch, idempotent consumers, bounded retries, and dead-letter queues.
- Data ownership: one PostgreSQL database per business service; no cross-service table access.
- Business services: Organization, Patient, Clinical, Lab, Pharmacy, Billing, Notification, Report, Inpatient, and Surgery.

The source defines these application roles: `ADMIN`, `DOCTOR`, `NURSE`, `PHARMACIST`, `CASHIER`, `LAB_TECH`, `MANAGER`, `PATIENT`, and `SYSTEM`.

## Mermaid assets

Every diagram in this folder is maintained as Mermaid source. SVG is the primary format for Markdown and browser viewing; PNG is supplied for compatibility with office applications and submission systems.

| Diagram | Mermaid source | SVG | PNG |
|---|---|---|---|
| System architecture | [`.mmd`](assets/mediflow-system-architecture.mmd) | [`.svg`](assets/mediflow-system-architecture.svg) | [`.png`](assets/mediflow-system-architecture.png) |
| REST, event, outbox, and idempotency flow | [`.mmd`](assets/mediflow-integration-and-saga-flow.mmd) | [`.svg`](assets/mediflow-integration-and-saga-flow.svg) | [`.png`](assets/mediflow-integration-and-saga-flow.png) |

### System architecture

![MediFlow system architecture](assets/mediflow-system-architecture.svg)

### Integration and saga flow

![MediFlow integration and saga flow](assets/mediflow-integration-and-saga-flow.svg)

## Diagram regeneration

Use the repository's Pretty Mermaid tooling or any Mermaid-compatible renderer. The committed outputs use a print-safe white background, blue connectors, and DejaVu Sans text.

Example:

```powershell
$node = "C:\Users\VIP\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe"
$render = "C:\Users\VIP\.codex\skills\pretty-mermaid\scripts\render.mjs"

& $node $render `
  --input assets/mediflow-system-architecture.mmd `
  --output assets/mediflow-system-architecture.svg `
  --font "DejaVu Sans" `
  --bg "#ffffff" --fg "#111827" --muted "#334155" `
  --line "#64748b" --accent "#2563eb" `
  --surface "#f8fafc" --border "#94a3b8" --padding 40
```

Render PNG separately with the same palette and a width of 2400 pixels.

## Scope boundary

This refresh changes only Markdown and diagram assets in this Phase 1 folder. It intentionally does not modify generated Word or PDF submissions, application source code, canonical files under `docs/ai`, or later project phases.
