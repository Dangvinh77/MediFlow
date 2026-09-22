# MediFlow Care and Finance Architecture HTML Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce one self-contained Vietnamese HTML reference that redesigns MediFlow around staged outpatient payments, inpatient deposits, surgery readiness, and discharge settlement while preserving distributed-service ownership.

**Architecture:** The artifact separates BA process views from developer contracts, uses inline SVG diagrams so it opens offline, and distinguishes existing behavior from proposed changes. Clinical and Lab remain Vinh's contexts; two new bounded contexts, Inpatient and Surgery, are added without introducing medical-image storage.

**Tech Stack:** Static HTML5, CSS, vanilla JavaScript, inline SVG, Spring Boot/RabbitMQ contract terminology from the repository.

---

### Task 1: Lock evidence and decisions

**Files:**
- Create: `docs/architecture/mediflow-care-finance-redesign.html`
- Reference only: `E:/DEV/Coding_Resource/Project/e_PROJECT/Semester4/tam-phuc-kien-truc-doi-chieu-mediflow.html`
- Reference only: `docs/ai/services/*.md`
- Reference only: `docs/eproject_general_plan/backend-spec/*.md`

- [ ] **Step 1: Record evidence boundaries**

State that Tâm Phúc supports payment-before-test, deposits, additional collections, insurance reconciliation, and final settlement, but does not define inpatient, surgery, or image storage.

- [ ] **Step 2: Record approved scope**

Keep the eight current business services, add `inpatient-service` and `surgery-service`, and explicitly exclude PACS/RIS, DICOM, CT/MRI/X-ray storage, and full insurance adjudication.

- [ ] **Step 3: Record current MediFlow gaps**

Cover Clinical's missing examination-payment gate, Lab's unenforced `paid` flag, Billing's result-time Lab fee, invoice-wide paid boolean, lack of deposits/partial payments/settlement ledger, and Report's cash-versus-revenue ambiguity.

### Task 2: Build the BA view

**Files:**
- Modify: `docs/architecture/mediflow-care-finance-redesign.html`

- [ ] **Step 1: Add actor and terminology sections**

Define patient, receptionist, cashier, doctor, nurse, lab technician, pharmacist, admission clerk, surgeon, anesthetist, manager, and system roles together with visit, care episode, charge, deposit, payment, settlement, and financial clearance.

- [ ] **Step 2: Add end-to-end processes**

Document registration and consultation payment, lab order and payment, outpatient prescription and dispensing, admission and deposit, surgery readiness, inpatient charge accrual, medical discharge, settlement, additional collection/refund, and administrative close.

- [ ] **Step 3: Add exceptions**

Document emergency debt, cancellation, duplicate events, payment after cancellation, insufficient deposit, failed dispensing, failed surgery readiness, and clinical discharge before financial close.

### Task 3: Build developer architecture and diagrams

**Files:**
- Modify: `docs/architecture/mediflow-care-finance-redesign.html`

- [ ] **Step 1: Add service landscape SVG**

Show Gateway, RabbitMQ, the eight current business services, and the two new services with ownership and synchronous/asynchronous boundaries.

- [ ] **Step 2: Add workflow SVGs**

Create diagrams for outpatient staged payment, inpatient/surgery lifecycle, and financial settlement.

- [ ] **Step 3: Add ERD SVGs**

Create detailed ERDs for Billing, Inpatient, Surgery, and cross-context UUID references without cross-database foreign keys.

- [ ] **Step 4: Add state machines**

Specify appointment/examination clearance, Lab payment and execution, admission, surgery case, billing account, payment transaction, and settlement transitions.

### Task 4: Define RabbitMQ integration contracts

**Files:**
- Modify: `docs/architecture/mediflow-care-finance-redesign.html`

- [ ] **Step 1: Define the common envelope**

Require `eventId`, `eventType`, `version`, `occurredAt`, `correlationId`, producer, and payload; require outbox publishing, idempotent consumers, retry, DLQ, and schema compatibility.

- [ ] **Step 2: Define event catalog**

Specify producers, consumers, purpose, minimum payload, idempotency key, and ordering assumptions for Clinical, Lab, Pharmacy, Billing, Inpatient, Surgery, Notification, and Report events.

- [ ] **Step 3: Define financial reference rules**

Require `careEpisodeType`, `careEpisodeId`, `chargeIds`, and explicit domain references such as `appointmentId`, `labTestIds`, `prescriptionId`, `admissionId`, and `surgeryCaseId`; prohibit consumers from inferring identifiers.

### Task 5: Re-plan developer ownership and delivery

**Files:**
- Modify: `docs/architecture/mediflow-care-finance-redesign.html`

- [ ] **Step 1: Show existing versus changed versus new work**

For every service, label unchanged responsibilities, required modifications, and new production scope.

- [ ] **Step 2: Assign work to four developers**

Keep Vinh on Clinical/Lab and assign Inpatient coordination, Huy on Pharmacy/Report and Surgery implementation, Hoàng Anh on Organization/Patient/Gateway plus identity and routes, and Lộc on Billing/Notification plus the financial ledger. Mark shared contracts as coordinated tasks rather than silent ownership expansion.

- [ ] **Step 3: Add phased backlog**

Order delivery as contracts, Billing ledger, outpatient gates, Inpatient, Surgery, reporting/notification, frontend/mobile, and full-system tests with explicit dependencies and acceptance criteria.

### Task 6: Validate the artifact

**Files:**
- Verify: `docs/architecture/mediflow-care-finance-redesign.html`

- [ ] **Step 1: Run structural validation**

Parse the HTML and assert one `main`, unique IDs, working internal anchors, at least six SVG diagrams, tables with headers, and no external asset dependency.

- [ ] **Step 2: Inspect visually**

Open the local file at desktop and mobile widths; check clipping, navigation, print layout, diagram readability, dark/light contrast, and Vietnamese text rendering.

- [ ] **Step 3: Run content checks**

Search for placeholders, verify every current and new service appears in ownership and event sections, ensure no imaging service is introduced, and confirm emergency treatment is never blocked by payment.

- [ ] **Step 4: Commit the documentation**

Run `git diff --check`, then commit using `docs(architecture): redesign care and payment workflows` with the configured human author and no co-author trailer.
