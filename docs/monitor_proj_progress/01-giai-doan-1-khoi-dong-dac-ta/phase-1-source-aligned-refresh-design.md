# Phase 1 Source Aligned English Refresh Design

## Purpose

Refresh the complete Phase 1 documentation package so that every maintained Markdown document and diagram is written in English and accurately reflects the repository at commit `3f10ea6`.

## Scope

The change is limited to `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta`.

The following documents will be rewritten in English:

- `README.md`
- `bao-cao-giai-trinh.md`
- `giai-doan-1-crs-bieu-mau.md`

The existing diagram assets will be replaced by two English Mermaid source sets:

- `mediflow-system-architecture.mmd`, `.svg`, and `.png`
- `mediflow-integration-and-saga-flow.mmd`, `.svg`, and `.png`

The previous Vietnamese-named SVG and PNG assets will be removed after every in-scope Markdown reference has been migrated.

DOCX and PDF files will not be modified. Files outside the Phase 1 folder will not be modified.

## Source Baseline

The repository source and indexed code graph at commit `3f10ea6` are the primary evidence. The refreshed package will represent:

- Web, mobile, and API clients entering through the API Gateway.
- Eureka service discovery and RabbitMQ integration messaging.
- Ten business services: Organization, Patient, Clinical, Lab, Pharmacy, Billing, Notification, Report, Inpatient, and Surgery.
- Database ownership per bounded context without cross-service database access.
- Nine RBAC roles: `ADMIN`, `DOCTOR`, `NURSE`, `PHARMACIST`, `CASHIER`, `LAB_TECH`, `MANAGER`, `PATIENT`, and `SYSTEM`.
- Implemented security enforcement through gateway authorization and service-level `@PreAuthorize` declarations.
- Implemented publishers, consumers, transactional outbox paths, idempotency receipts, retry handling, dead-letter handling, and care-finance integration tests.

## Document Design

### Package README

The README will explain the purpose of the Phase 1 package, enumerate its documents and generated assets, identify the source baseline, and provide a reproducible Mermaid render command.

### Phase 1 Progress and Evidence Report

The report will lead with the current conclusion, then cover the teacher-defined Phase 1 objectives, completed deliverables, source evidence, corrections to the previous report, risks, open decisions, and an acceptance checklist. Claims will distinguish implemented behavior from remaining documentation or product decisions.

### Customer Requirements and Initial Business Forms

The requirements document will define the problem, stakeholders, roles, system scope, context responsibilities, functional requirements, use cases, initial business forms, distributed-system requirements, and traceability to implemented source areas. Inpatient and Surgery will be treated as implemented bounded contexts rather than future placeholders.

## Diagram Design

### System Architecture

A Mermaid flowchart will show clients, gateway and discovery, all ten bounded contexts with isolated PostgreSQL ownership, RabbitMQ, and cross-cutting security, observability, and resilience concerns.

### Integration and Saga Flow

A Mermaid sequence diagram will separate synchronous authority checks from asynchronous integration events. It will show transactional state changes, outbox dispatch, RabbitMQ delivery, idempotent consumers, retry and dead-letter behavior, and representative care-finance flows for pharmacy, laboratory, inpatient, and surgery.

## Validation

- Validate every Mermaid source by rendering both SVG and PNG with the committed print-safe light palette and DejaVu Sans text.
- Inspect both rendered PNG files for clipping, ambiguous arrows, unreadable labels, and English-only visible text.
- Search the Phase 1 folder for remaining Vietnamese prose and stale asset references.
- Check all Markdown links and asset paths.
- Verify the Git diff contains no DOCX, PDF, or out-of-scope changes.

## Acceptance Criteria

- All maintained Phase 1 prose and diagram labels are English.
- The service landscape contains all ten current business services.
- Architecture, security, messaging, and workflow claims are supported by the current source.
- Mermaid sources and rendered outputs are reproducible and readable.
- DOCX/PDF artifacts and files outside the Phase 1 folder remain unchanged.
