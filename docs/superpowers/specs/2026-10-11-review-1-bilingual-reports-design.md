# Review 1 Bilingual Reports Design

**Date:** 11 October 2026

**Source baseline:** `1cc014e`

**Status:** Approved for specification authoring

## Purpose

Create a source-aligned Review 1 reporting package for MediFlow in English and Vietnamese. The package must support three uses: formal Review 1 submission, detailed evaluator review, and oral presentation. Every report must be easy to transfer to Microsoft Word without requiring diagram rendering or complex Markdown extensions.

## Deliverables

Create the following folder:

`docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/`

Create seven Markdown files:

1. `01-review-1-standard-en.md`
2. `02-review-1-standard-vi.md`
3. `03-review-1-detailed-en.md`
4. `04-review-1-detailed-vi.md`
5. `05-review-1-presentation-en.md`
6. `06-review-1-presentation-vi.md`
7. `07-standard-vs-detailed-comparison.md`

The English and Vietnamese files in each pair must be structurally equivalent. The comparison file must be bilingual.

## Source of Truth

The reports will use the current repository as the implementation baseline and the existing Phase 1 source-aligned documents as supporting evidence. The reports must reflect:

- the Next.js web client and Flutter mobile client;
- the API gateway and Eureka service discovery;
- ten implemented business contexts: Organization, Patient, Clinical, Lab, Pharmacy, Billing, Notification, Report, Inpatient, and Surgery;
- one PostgreSQL database per business service;
- synchronous REST authority checks and RabbitMQ integration events;
- transactional outbox, idempotent consumers, bounded retries, and dead-letter queues where implemented;
- the source-defined roles `ADMIN`, `DOCTOR`, `NURSE`, `PHARMACIST`, `CASHIER`, `LAB_TECH`, `MANAGER`, `PATIENT`, and `SYSTEM`;
- the latest documented full-suite result of 3,612 tests with zero failures, errors, or skips.

The reports must distinguish implemented behavior from customer decisions or production targets that remain unconfirmed.

## Shared Content Model

Every report contains two primary parts.

### Part A — Problem Definition

1. Project introduction and hospital context.
2. Problem identification.
3. Project objectives.
4. Included and excluded scope.
5. Users and functions for Administrator, Doctor, Nurse, Laboratory Technician, Pharmacist, Cashier, Manager, Patient, and the internal System actor.
6. Proposed MediFlow solution.
7. Current project limitations and functions not yet completed.

### Part B — Customer Requirement Specification

1. Customer and stakeholder requirements.
2. Data-processing cycles for every user type.
3. Cross-department workflows.
4. Client hardware requirements.
5. Client software requirements.
6. Server hardware requirements.
7. Server and infrastructure software requirements.
8. Security, reliability, performance, backup, and operational requirements.
9. Acceptance checklist and glossary.

## Data-Cycle Table Contract

The principal CRS table uses these columns:

| Workflow | Actor | Input information | Processing | Output information | Owning service |
|---|---|---|---|---|---|

The Vietnamese equivalent uses:

| Quy trình | Đối tượng | Thông tin đầu vào | Xử lý | Thông tin đầu ra | Dịch vụ sở hữu |
|---|---|---|---|---|---|

Each row describes one coherent user goal. Inputs and outputs are business information, not Java entities or database tables. The owning service column preserves bounded-context authority and prevents the report from implying cross-service database access.

## Required Cross-Department Workflows

At minimum, the package covers:

1. patient registration and appointment booking;
2. appointment-to-clinical-encounter processing;
3. clinical laboratory ordering and result publication;
4. laboratory-result-to-billing charge processing;
5. prescription authorization and pharmacy dispensing;
6. invoice, payment, deposit, receipt, and refund processing;
7. admission request, financial clearance, bed allocation, stay, and discharge;
8. surgery request, readiness, financial clearance, scheduling, execution, and outcome;
9. event-driven notification delivery;
10. event-driven operational reporting.

## Report Variants

### Standard edition

- Target length after Word conversion: 15–20 pages per language.
- Primary use: formal Review 1 submission.
- Consolidate actor responsibilities and select the essential data-cycle rows.
- Include enough technical requirements to make client and server deployment credible without becoming an implementation manual.
- Use concise explanations followed by tables.

### Detailed edition

- Target length after Word conversion: 25–35 pages per language.
- Primary use: evaluator review, team reference, and later traceability.
- Give every actor a separate data-cycle subsection.
- Include normal flow, important alternatives or failures, service ownership, requirement identifiers, assumptions, limitations, and acceptance evidence.
- Expand hardware and software requirements into development, minimum deployment, and recommended deployment profiles where appropriate.

### Presentation edition

- Target length after Word conversion: 8–12 pages per language.
- Primary use: oral presentation and slide preparation.
- Use short sections, limited tables, and high-value talking points.
- Organize content so that each major section can become one or two slides.
- Add concise presenter cues without embedding a full speech script.

### Comparison edition

- Compare the Standard and Detailed editions in both English and Vietnamese.
- Cover audience, purpose, target length, content depth, actor coverage, workflow depth, technical requirements, advantages, limitations, preparation effort, and recommended use.
- Include a section-by-section mapping so an evaluator can see what the Detailed edition adds.

## Word-Friendly Formatting

- Use one H1 title and numbered H2/H3 sections.
- Use simple Markdown tables with short cell content.
- Prefer multiple small tables over one excessively wide table.
- Avoid Mermaid, HTML layout, nested tables, collapsible blocks, and syntax that depends on GitHub rendering.
- Use bold labels sparingly and keep list nesting to two levels.
- Keep title metadata in a small two-column table.
- Use consistent terminology so Word find/replace and heading-based table-of-contents generation remain reliable.
- Keep each paragraph focused on one idea and generally below 120 words.

## Hardware and Software Requirement Model

The reports will separate requirements by environment and avoid presenting unsupported production targets as facts.

### Client

- End-user workstation or laptop profile.
- Mobile-device profile.
- Browser, operating system, network, screen, and storage expectations.
- Optional devices such as a printer or barcode reader must be labeled as optional unless source establishes a dependency.

### Server

- Development or demonstration profile.
- Minimum small-deployment profile.
- Recommended scalable deployment profile.
- Java runtime, Node.js or pnpm tooling, Flutter tooling where applicable, PostgreSQL, RabbitMQ, Docker, service discovery, gateway, storage, backup, TLS, monitoring, and network requirements.
- Quantitative capacity values must be described as planning recommendations, not measured production guarantees.

## Limitations Section

The reports will separate three types of limitation:

1. **Not finalized:** production SLOs, RTO/RPO, retention periods, accessibility target, and customer governance decisions.
2. **External dependency not integrated:** insurance, payment gateway, national identity, external laboratory, medical-device, SMS, or email vendor contracts unless current source proves otherwise.
3. **Demonstration constraint:** local Docker topology and baseline resource recommendations are not a production capacity certification.

The reports must not describe an implemented service as missing merely because an external integration or production policy is incomplete.

## Consistency Rules

- English and Vietnamese paired reports must have the same section order and equivalent factual content.
- Role names and service names remain in source-defined English form inside both languages where changing them would break traceability.
- Requirement identifiers remain identical across languages.
- Hardware recommendations, counts, and source baseline values must match across all variants.
- The Presentation edition may omit detail but must not contradict the Standard or Detailed editions.
- The comparison file must use the final reports rather than planned content as its evidence.

## Validation

1. Confirm all seven files exist and all local links resolve.
2. Check English files for unintended Vietnamese prose and Vietnamese files for untranslated section headings.
3. Compare paired heading trees and requirement identifiers.
4. Verify all nine roles and ten business contexts appear where required.
5. Verify every required cross-department workflow appears in both Detailed editions.
6. Verify hardware and software tables distinguish client and server requirements.
7. Search for placeholders such as `TODO`, `TBD`, and empty template fields.
8. Run `git diff --check` and confirm no application source, DOCX, or PDF file changed.

## Non-Goals

- Creating or modifying Word or PDF files.
- Changing application source code or canonical `docs/ai` rules.
- Adding new business capabilities that are not supported by source or clearly labeled as future work.
- Producing infrastructure sizing guarantees without measured workload data.
