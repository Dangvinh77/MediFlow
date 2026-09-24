# Care Finance Service Documentation Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Propagate the approved care-finance redesign into every service document and establish canonical cross-service handoffs that prevent producers and consumers from changing independently.

**Architecture:** Keep `docs/architecture/mediflow-care-finance-redesign.html` as the approved business/architecture source. Add one integration-contract index and focused handoff documents, then make every affected service doc and nested `AGENTS.md` point to the exact contracts it must read. Existing runtime code remains unchanged in this documentation-only slice.

**Tech Stack:** Markdown, HTML source reference, Mermaid source references, Git link validation, repository commit policy.

---

### Task 1: Establish the canonical integration contract registry

**Files:**
- Create: `docs/ai/16-care-finance-integration-contracts.md`
- Create: `docs/handoffs/care-finance/README.md`
- Modify: `docs/ai/README.md`
- Modify: `AGENTS.md`

- [ ] **Step 1:** Define precedence, contract lifecycle, compatibility rules, owner/consumer responsibilities, and the mandatory producer-consumer change checklist.
- [ ] **Step 2:** Map every service to the handoff documents it must read before changing integration code.
- [ ] **Step 3:** Link the registry from the root AI documentation index and root agent entry point.
- [ ] **Step 4:** Verify every registry link resolves.
- [ ] **Step 5:** Commit the registry and root documentation changes.

### Task 2: Write focused cross-service handoffs

**Files:**
- Create: `docs/handoffs/care-finance/CONTRACT-CARE-BILLING-01.md`
- Create: `docs/handoffs/care-finance/CONTRACT-INPATIENT-SURGERY-01.md`
- Create: `docs/handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md`
- Create: `docs/handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md`
- Create: `docs/handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md`

- [ ] **Step 1:** Define producer, consumer, owner, current status, payload fields, invariants, versioning, idempotency, DLQ behavior, and acceptance criteria for each contract.
- [ ] **Step 2:** Mark contracts for not-yet-created Inpatient/Surgery modules as design-ready and implementation-blocked until their modules are scaffolded.
- [ ] **Step 3:** Cross-link existing completed handoffs instead of duplicating their payload definitions.
- [ ] **Step 4:** Verify contract IDs and event names match the approved HTML architecture.
- [ ] **Step 5:** Commit the handoff documents.

### Task 3: Align the shared architecture and event catalog

**Files:**
- Modify: `docs/ai/00-project-overview.md`
- Modify: `docs/ai/01-architecture.md`
- Modify: `docs/ai/06-events-rabbitmq.md`
- Modify: `docs/eproject_general_plan/backend-spec/00-overview.md`

- [ ] **Step 1:** Replace the obsolete nine-service description with the current ten business bounded contexts plus infrastructure.
- [ ] **Step 2:** Add Inpatient and Surgery as planned modules without claiming they already exist in Maven or Docker Compose.
- [ ] **Step 3:** Update the event catalog with care episodes, purpose-scoped financial clearance, deposits, surgery, refunds, and settlement.
- [ ] **Step 4:** Document migration compatibility for existing unversioned routing keys and current fee/invoice tables.
- [ ] **Step 5:** Commit the shared architecture updates.

### Task 4: Align all per-service bounded-context documents

**Files:**
- Modify: `docs/ai/services/organization.md`
- Modify: `docs/ai/services/patient.md`
- Modify: `docs/ai/services/clinical.md`
- Modify: `docs/ai/services/lab.md`
- Modify: `docs/ai/services/pharmacy.md`
- Modify: `docs/ai/services/billing.md`
- Modify: `docs/ai/services/notification.md`
- Modify: `docs/ai/services/report.md`
- Modify: `docs/ai/services/gateway.md`
- Create: `docs/ai/services/inpatient.md`
- Create: `docs/ai/services/surgery.md`

- [ ] **Step 1:** Add an explicit care-finance alignment section to every current service doc with owned data, produced/consumed events, synchronous dependencies, and required handoffs.
- [ ] **Step 2:** Correct known contradictions: Lab charging at result time, patient-wide invoice grouping, deposit-as-revenue, and payment clearance without target references.
- [ ] **Step 3:** Define complete planned bounded contexts for Inpatient and Surgery, including state machines and implementation blockers.
- [ ] **Step 4:** Verify that no document authorizes cross-service database access or identifier inference.
- [ ] **Step 5:** Commit the per-service documentation updates.

### Task 5: Make handoff reading mandatory at service entry points

**Files:**
- Modify: `backend/organization-service/AGENTS.md`
- Modify: `backend/patient-service/AGENTS.md`
- Modify: `backend/clinical-service/AGENTS.md`
- Modify: `backend/lab-service/AGENTS.md`
- Modify: `backend/pharmacy-service/AGENTS.md`
- Modify: `backend/billing-service/AGENTS.md`
- Modify: `backend/notification-service/AGENTS.md`
- Modify: `backend/report-service/AGENTS.md`
- Modify: `backend/gateway/AGENTS.md`

- [ ] **Step 1:** Add links to the central registry and only the contracts relevant to each service.
- [ ] **Step 2:** Require producer DTO/schema/fixture changes and consumer fixture/test updates in the same PR or an explicitly blocked handoff.
- [ ] **Step 3:** Preserve each owner boundary and local verification command.
- [ ] **Step 4:** Validate all relative links from each nested `AGENTS.md`.
- [ ] **Step 5:** Commit the agent-entry-point updates.

### Task 6: Validate and integrate

**Files:**
- Validate all modified Markdown files and links.

- [ ] **Step 1:** Scan for placeholders, contradictory event names, missing owners, and broken relative links.
- [ ] **Step 2:** Confirm the working tree contains documentation changes only.
- [ ] **Step 3:** Run Git whitespace and commit-policy checks.
- [ ] **Step 4:** Push the branch, create a focused PR, enable auto-merge, and sync local `master` after merge.
