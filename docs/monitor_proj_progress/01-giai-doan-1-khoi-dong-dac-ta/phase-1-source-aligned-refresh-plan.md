# Phase 1 Source Aligned English Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Phase 1 package with English, source-aligned Markdown and Mermaid assets without modifying DOCX, PDF, or files outside the Phase 1 folder.

**Architecture:** Treat the indexed repository at commit `3f10ea6` as the primary evidence. Maintain two Mermaid source diagrams and render each to SVG and PNG with a print-safe light theme. Rewrite the package README, progress report, and customer requirements document around the ten implemented bounded contexts and current distributed-system behavior.

**Tech Stack:** Markdown, Mermaid, Pretty Mermaid local renderer, Git, codebase-memory graph.

---

### Task 1: Create the English Mermaid source diagrams

**Files:**
- Create: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/assets/mediflow-system-architecture.mmd`
- Create: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/assets/mediflow-integration-and-saga-flow.mmd`

- [ ] **Step 1: Author the system architecture diagram**

Use a `flowchart TB` with four layers: clients; gateway and Eureka; ten bounded contexts with one database per service; RabbitMQ and cross-cutting controls. Include Organization, Patient, Clinical, Lab, Pharmacy, Billing, Notification, Report, Inpatient, and Surgery.

- [ ] **Step 2: Author the integration flow diagram**

Use a `sequenceDiagram` that distinguishes synchronous REST authority checks from asynchronous events. Show the local transaction and outbox boundary, RabbitMQ delivery, idempotent consumer receipts, retries, DLQ, and representative pharmacy, lab, inpatient, and surgery care-finance outcomes.

- [ ] **Step 3: Validate Mermaid syntax with a temporary render**

Run the Pretty Mermaid renderer against both `.mmd` files with the committed print-safe light palette and DejaVu Sans text.

Expected: both commands exit `0` and produce non-empty SVG files.

### Task 2: Replace the rendered assets

**Files:**
- Create: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/assets/mediflow-system-architecture.svg`
- Create: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/assets/mediflow-system-architecture.png`
- Create: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/assets/mediflow-integration-and-saga-flow.svg`
- Create: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/assets/mediflow-integration-and-saga-flow.png`
- Remove: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/assets/kien-truc-tong-quan-mediflow.svg`
- Remove: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/assets/kien-truc-tong-quan-mediflow-word-safe.svg`
- Remove: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/assets/kien-truc-tong-quan-mediflow-word-safe.png`
- Remove: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/assets/luong-rest-event-va-saga.svg`
- Remove: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/assets/luong-rest-event-va-saga-word-safe.svg`
- Remove: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/assets/luong-rest-event-va-saga-word-safe.png`

- [ ] **Step 1: Render SVG outputs**

Run `render.mjs` once per diagram with the custom white, slate, and blue palette, DejaVu Sans text, generous padding, and spacing suitable for documentation.

Expected: both SVG files begin with `<svg` and contain English labels.

- [ ] **Step 2: Render PNG outputs**

Run `render.mjs` once per diagram with `--format png --width 2400` and the same custom palette and font settings used for SVG.

Expected: both PNG files open successfully and contain no clipped labels.

- [ ] **Step 3: Remove superseded assets**

Delete the six Vietnamese-named generated assets only after the new render set exists and all Markdown references are ready to migrate.

### Task 3: Rewrite the package README

**Files:**
- Modify: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/README.md`

- [ ] **Step 1: Replace the Vietnamese package guide with an English guide**

Include the Phase 1 purpose, source baseline commit, document inventory, asset inventory, Mermaid render instructions, submission order, and scope boundary that excludes DOCX/PDF refresh.

- [ ] **Step 2: Update every asset reference**

Reference the new English `.mmd`, `.svg`, and `.png` paths. Remove every reference to the superseded Vietnamese asset names.

### Task 4: Rewrite the progress and evidence report

**Files:**
- Modify: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/bao-cao-giai-trinh.md`

- [ ] **Step 1: Replace the report with an English evidence-led structure**

Cover the teacher-defined Phase 1 objectives, current conclusion, repository baseline, completed deliverables, implemented capabilities, corrections to the former report, known product decisions, risks, and acceptance checklist.

- [ ] **Step 2: Add source-backed evidence**

State the ten business services, nine RBAC roles, gateway and service authorization, RabbitMQ publishers and listeners, isolated PostgreSQL ownership, outbox and idempotency behavior, and the latest full-suite result of 3,612 tests with zero failures, errors, or skips.

- [ ] **Step 3: Avoid unsupported completion claims**

Separate implemented source behavior from requirements that still require stakeholder approval, such as production SLOs, retention periods, external payment providers, insurance integrations, and clinical-device integrations.

### Task 5: Rewrite customer requirements and initial forms

**Files:**
- Modify: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/giai-doan-1-crs-bieu-mau.md`

- [ ] **Step 1: Rewrite the problem and solution scope in English**

Describe the hospital coordination problem, source-aligned service boundaries, stakeholders, exclusions, and the gateway-only client access rule.

- [ ] **Step 2: Define actors and functional requirements**

Document the nine RBAC roles and responsibilities across Organization, Patient, Clinical, Lab, Pharmacy, Billing, Notification, Report, Inpatient, and Surgery.

- [ ] **Step 3: Refresh the use-case catalog**

Add source-aligned use cases for outpatient care, lab requests and results, prescriptions and dispensing, invoices and refunds, admission and discharge, surgery scheduling and readiness, notifications, and operational reporting.

- [ ] **Step 4: Refresh the initial business forms**

Define concise field and validation tables for patient intake, appointment, medical record, lab request/result, prescription/dispense, invoice/payment/refund, admission/deposit/discharge, surgery request/readiness/schedule/result, and notification/report filters.

- [ ] **Step 5: Refresh nonfunctional requirements**

Document JWT and RBAC, ownership checks, clean architecture, UUID and time conventions, API envelopes, idempotency, outbox dispatch, retries and DLQ, correlation IDs, health endpoints, pagination, database isolation, and test coverage.

### Task 6: Validate the complete Phase 1 package

**Files:**
- Verify all files under: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta`

- [ ] **Step 1: Inspect both PNG diagrams**

Open both images at full detail and verify readable English-only labels, correct arrows, no clipping, and a clear visual hierarchy.

- [ ] **Step 2: Scan prose and references**

Run searches for Vietnamese diacritics, old asset basenames, placeholder terms, stale eight-service statements, and outdated claims that messaging or RBAC are absent.

Expected: no stale prose or references in the three maintained documents and two Mermaid sources.

- [ ] **Step 3: Check Markdown links and file existence**

Resolve every local link from the three maintained documents and confirm each target exists.

Expected: zero broken in-scope links.

- [ ] **Step 4: Verify Git scope**

Run `git diff --check` and inspect `git status --short` plus `git diff --name-status master...HEAD`.

Expected: only files under the Phase 1 folder are changed; no DOCX or PDF is staged or modified.

- [ ] **Step 5: Commit the completed refresh**

Stage only the Phase 1 folder and commit with:

```text
docs(phase1): align English requirements with source
```
