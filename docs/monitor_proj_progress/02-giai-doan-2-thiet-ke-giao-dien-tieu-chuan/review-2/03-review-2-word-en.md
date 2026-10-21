# MediFlow — Review 2: Architecture, Data and Interface Design
> **Word layout option.** A4 with 25 mm margins. Use the PNG images below. Large diagrams use consecutive panels; repeated nodes connect the panels. The [selection guide](word-compact/README.md) provides both paper orientations and the complete overview. Architecture 01 groups deployments and stores; use the original for individual connectors.


[Vietnamese edition](04-review-2-word-vi.md)

| Item | Description |
|---|---|
| Project | MediFlow: A Microservices-Based Hospital Information System for Outpatient Care Management |
| Review | Review 2 |
| Baseline | 2026-10-19 · source commit `26232b54813d` + this documentation revision |
| Scope | Outpatient MVP; retained inpatient/surgery source remains deferred |
| Status | Source-backed design review; not a runtime acceptance certificate |

This report follows Review 1’s bilingual report format and updates its historical snapshot. Review 1 still mentions 12 modules and no Support host: the current root reactor has **13 modules**, default Compose has **6 business deployments** and **8 active context stores**. Gateway/Eureka are infrastructure, common is a library; Report/Notification are composed into Support and retain rollback launchers.

Status convention: **SOURCE** = present in source/config; **GATED** = requires the correct profile/contract/activation; **DEFERRED** = retained outside the MVP. Diagrams describe the stated contract mode and do not assert that every path passed on the current environment. Backend suites and live databases were not rerun for this documentation task.

## Diagram conventions

All diagrams use **MediFlow Sky/Teal**: white background, slate text `#0f172a`, blue nodes `#e0f2fe`, borders `#0284c7`, and teal accents `#ccfbf1`. Every Mermaid block carries the same configuration. Solid architecture edges represent direct requests; dashed edges represent messaging/discovery. ERDs show store-local foreign keys; cross-context UUID references are documented separately.

This Word option uses PNG images to preserve text, technology logos, and actors. Mermaid sources and SVGs are linked beside each figure. The Sky/Teal theme and original logos are retained. SVG labels use HTML, so check rendering before inserting SVG directly into Word.

## 1. Architecture Design

### 1.1. System / Application Architecture

### System architecture — one backend boundary, six business services

**Part 1/4 · A4 landscape**

![System architecture — one backend boundary, six business services — Part 1/4](word-compact/panels/png/01-en-part-01.png)

[Mermaid](word-compact/panels/mermaid/01-en-part-01.mmd) · [SVG](word-compact/panels/svg/01-en-part-01.svg)

**Part 2/4 · A4 landscape**

![System architecture — one backend boundary, six business services — Part 2/4](word-compact/panels/png/01-en-part-02.png)

[Mermaid](word-compact/panels/mermaid/01-en-part-02.mmd) · [SVG](word-compact/panels/svg/01-en-part-02.svg)

**Part 3/4 · A4 landscape**

![System architecture — one backend boundary, six business services — Part 3/4](word-compact/panels/png/01-en-part-03.png)

[Mermaid](word-compact/panels/mermaid/01-en-part-03.mmd) · [SVG](word-compact/panels/svg/01-en-part-03.svg)

**Part 4/4 · A4 landscape**

![System architecture — one backend boundary, six business services — Part 4/4](word-compact/panels/png/01-en-part-04.png)

[Mermaid](word-compact/panels/mermaid/01-en-part-04.mmd) · [SVG](word-compact/panels/svg/01-en-part-04.svg)

[Original SVG](assets/svg/01-en.svg) · [Portrait PNG](word-compact/portrait/png/01-en.png) · [Landscape PNG](word-compact/landscape/png/01-en.png)

One Server side boundary groups six business applications; it does not mean one backend process. Core and Support each compose two contexts with separate persistence authorities. Broker edges show configured producer/consumer capabilities, not all-to-all delivery. Next.js web hosting and same-origin /api rewrite are deployment details: frontend/next.config.ts still configures the rewrite and production standalone output. They are not a second business backend. Docker Compose orchestrates local infrastructure; no service ports or deferred Inpatient/Surgery are shown.

### Technology and tooling architecture

**A4 portrait**

![Technology and tooling architecture](word-compact/portrait/png/02-en.png)

[Mermaid](word-compact/portrait/mermaid/02-en.mmd) · [SVG](word-compact/portrait/svg/02-en.svg)

[Original SVG](assets/svg/02-en.svg) · [Portrait PNG](word-compact/portrait/png/02-en.png) · [Landscape PNG](word-compact/landscape/png/02-en.png)
Libraries without an individual logo in the bundled set are named below; they are not extra deployments. Spring represents Boot/Cloud/Security/Data; Flutter represents its mobile ecosystem. Redis, Kafka, Kubernetes and external payment providers are not added to the architecture.

| Layer | Stack verified in source | Evidence |
|---|---|---|
| Backend | Java 21; Spring Boot 3.3.5; Spring Cloud 2023.0.3; Maven; Spring Security; Spring Data JPA/Hibernate; Flyway; MapStruct; Lombok; JJWT 0.12.6; springdoc 2.6.0 | [pom.xml](../../../../pom.xml) |
| Web | Next.js 16.2.11; React 19.2.4; TypeScript 5; Tailwind CSS 4; Node.js/pnpm; Vitest 4; ESLint 9; Testing Library | [frontend/package.json](../../../../frontend/package.json) |
| Mobile | Flutter; Dart ^3.13.0; Riverpod ^3.4.3; GoRouter ^18.1.0; Dio ^5.11.1; flutter_secure_storage ^11.2.0 | [mobile/pubspec.yaml](../../../../mobile/pubspec.yaml) |
| Infrastructure | PostgreSQL 16-alpine; RabbitMQ 3-management-alpine; Docker Compose; Eureka; Gateway | [docker-compose.yml](../../../../docker-compose.yml) |
| Verification | JUnit / Spring Boot Test / Testcontainers; Git / GitHub Actions | [backend/support-service/pom.xml](../../../../backend/support-service/pom.xml) |

Versions with `^` are manifest constraints, not installed runtime measurements. Technology names/logos do not certify production deployment.

### Application architecture — request path and inward dependencies

**Part 1/2 · A4 landscape**

![Application architecture — request path and inward dependencies — Part 1/2](word-compact/panels/png/03-en-part-01.png)

[Mermaid](word-compact/panels/mermaid/03-en-part-01.mmd) · [SVG](word-compact/panels/svg/03-en-part-01.svg)

**Part 2/2 · A4 portrait**

![Application architecture — request path and inward dependencies — Part 2/2](word-compact/panels/png/03-en-part-02.png)

[Mermaid](word-compact/panels/mermaid/03-en-part-02.mmd) · [SVG](word-compact/panels/svg/03-en-part-02.svg)

[Original SVG](assets/svg/03-en.svg) · [Portrait PNG](word-compact/portrait/png/03-en.png) · [Landscape PNG](word-compact/landscape/png/03-en.png)

Solid edges name runtime requests/results or rule evaluation; dotted implements edges name static interface realization, not HTTP. The import rule is explicit in the bottom note. The same pattern applies within each context; Core/Support composition does not merge the context databases. A local commit is not a distributed transaction. The diagram abstracts web hosting while retaining the configured same-origin gateway route.

### 1.2. Use Cases Diagrams

One diagram per human role. Common authentication goals are separate; business tasks require a valid session. Use cases describe goals, not the order of a visit.

<!-- role-usecases:start -->
### Detailed use cases — one diagram per role

Figures 29–36 follow the supervisor’s presentation: one human actor per diagram, solid arrows to primary tasks, dashed `«include»` arrows for required supporting behavior, and dashed `«extend»` arrows from a conditional extension to its base task. An extension may have its own included subflow. Arrow direction is relational, not chronological. Sign-in and token renewal are independent goals. A valid session is a precondition of protected business tasks. Blue ovals are primary tasks; teal ovals are supporting/extension tasks. These are source capabilities, not proof of UI completeness or production enablement.

**Scope correction:** PATIENT currently has authentication/refresh and own-notification access. No appointment or laboratory self-service is claimed. SYSTEM is a machine identity and has no human-role diagram. All role permissions below are intersected with the Gateway route matrix and downstream guards.

#### ADMIN — Administrator tasks

**A4 portrait**

![ADMIN — Administrator tasks](word-compact/portrait/png/29-en.png)

[Mermaid](word-compact/portrait/mermaid/29-en.mmd) · [SVG](word-compact/portrait/svg/29-en.svg)

[Original SVG](assets/svg/29-en.svg) · [Portrait PNG](word-compact/portrait/png/29-en.png) · [Landscape PNG](word-compact/landscape/png/29-en.png)

This diagram focuses on organization/account administration. ADMIN also has explicit permissions in the other seven context diagrams; it does not bypass business guards. No account deletion or arbitrary permission editor is invented. Sign-in and renewal are independent goals, not an include/extend pair. Staff business commands require a valid authenticated session. Preconditions and field validation are described in the sequence/specification, not expanded as screen-step use cases.

Extension conditions: None.

Source evidence: [AccountController](../../../../backend/core-service/src/main/java/com/mediflow/organization/web/controller/AccountController.java) · [StaffController](../../../../backend/core-service/src/main/java/com/mediflow/organization/web/controller/StaffController.java) · [DepartmentController](../../../../backend/core-service/src/main/java/com/mediflow/organization/web/controller/DepartmentController.java) · [ReportController](../../../../backend/report-service/src/main/java/com/mediflow/report/web/ReportController.java) · [NotificationController](../../../../backend/notification-service/src/main/java/com/mediflow/notification/web/NotificationController.java) · [RouteAuthorizationFilter](../../../../backend/gateway/src/main/java/com/mediflow/gateway/filter/RouteAuthorizationFilter.java) · [AuthController](../../../../backend/gateway/src/main/java/com/mediflow/gateway/auth/AuthController.java)

#### MANAGER — Manager tasks

**A4 portrait**

![MANAGER — Manager tasks](word-compact/portrait/png/30-en.png)

[Mermaid](word-compact/portrait/mermaid/30-en.mmd) · [SVG](word-compact/portrait/svg/30-en.svg)

[Original SVG](assets/svg/30-en.svg) · [Portrait PNG](word-compact/portrait/png/30-en.png) · [Landscape PNG](word-compact/landscape/png/30-en.png)

Read-only management tasks: daily/monthly reports, top medicines, department revenue, appointment/lab lists, staff and department directories. MANAGER cannot create accounts, update staff or record clinical/financial commands. Snapshot reports remain subject to their runtime availability and coverage. Sign-in and renewal are independent goals, not an include/extend pair. Staff business commands require a valid authenticated session. Preconditions and field validation are described in the sequence/specification, not expanded as screen-step use cases.

Extension conditions: E1: Inspect report coverage — coverage inspection requested; E2: Filter appointments by department — department filter selected

Source evidence: [OutpatientReportController](../../../../backend/report-service/src/main/java/com/mediflow/report/web/OutpatientReportController.java) · [FinancialSnapshotReportController](../../../../backend/report-service/src/main/java/com/mediflow/report/web/FinancialSnapshotReportController.java) · [ReportController](../../../../backend/report-service/src/main/java/com/mediflow/report/web/ReportController.java) · [RevenueController](../../../../backend/billing-service/src/main/java/com/mediflow/billing/web/RevenueController.java) · [AppointmentController](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/web/AppointmentController.java) · [StaffController](../../../../backend/core-service/src/main/java/com/mediflow/organization/web/controller/StaffController.java) · [AuthController](../../../../backend/gateway/src/main/java/com/mediflow/gateway/auth/AuthController.java)

#### DOCTOR — Doctor tasks

**A4 portrait**

![DOCTOR — Doctor tasks](word-compact/portrait/png/31-en.png)

[Mermaid](word-compact/portrait/mermaid/31-en.mmd) · [SVG](word-compact/portrait/svg/31-en.svg)

[Original SVG](assets/svg/31-en.svg) · [Portrait PNG](word-compact/portrait/png/31-en.png) · [Landscape PNG](word-compact/landscape/png/31-en.png)

Clinical authority and patient/context checks still apply. [V1] requires care-finance-v2 enabled; start-exam needs exact EXAM clearance or the supported audited override. AppointmentController additionally authorizes appointment reads/updates/status; operational report reads and lab cancellation are also source-authorized. A doctor cannot perform nurse check-in, pharmacy dispensing or cashier payment. Sign-in and renewal are independent goals, not an include/extend pair. Staff business commands require a valid authenticated session. Preconditions and field validation are described in the sequence/specification, not expanded as screen-step use cases.

Extension conditions: E1: Order lab test — test clinically indicated; E2: Issue prescription — medication clinically indicated

Source evidence: [AppointmentController](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/web/AppointmentController.java) · [MedicalRecordController](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/web/MedicalRecordController.java) · [ClinicalCareFinanceController](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/web/ClinicalCareFinanceController.java) · [ClinicalLabOrderController](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/web/ClinicalLabOrderController.java) · [LabController](../../../../backend/lab-service/src/main/java/com/mediflow/lab/web/LabController.java) · [PrescriptionController](../../../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/web/PrescriptionController.java) · [AuthController](../../../../backend/gateway/src/main/java/com/mediflow/gateway/auth/AuthController.java)

#### NURSE — Nurse tasks

**A4 portrait**

![NURSE — Nurse tasks](word-compact/portrait/png/32-en.png)

[Mermaid](word-compact/portrait/mermaid/32-en.mmd) · [SVG](word-compact/portrait/svg/32-en.svg)

[Original SVG](assets/svg/32-en.svg) · [Portrait PNG](word-compact/portrait/png/32-en.png) · [Landscape PNG](word-compact/landscape/png/32-en.png)

[V1] check-in requires care-finance-v2 enabled and does not start an examination. NURSE can query lab list/detail and notification lists by patient, but cannot write diagnoses, lab results, prescriptions or payments; individual notification detail is not granted to NURSE. There is no separate RECEPTIONIST role. Sign-in and renewal are independent goals, not an include/extend pair. Staff business commands require a valid authenticated session. Preconditions and field validation are described in the sequence/specification, not expanded as screen-step use cases.

Extension conditions: None.

Source evidence: [PatientController](../../../../backend/core-service/src/main/java/com/mediflow/patient/web/PatientController.java) · [AppointmentController](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/web/AppointmentController.java) · [ClinicalCareFinanceController](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/web/ClinicalCareFinanceController.java) · [MedicalRecordController](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/web/MedicalRecordController.java) · [LabController](../../../../backend/lab-service/src/main/java/com/mediflow/lab/web/LabController.java) · [NotificationController](../../../../backend/notification-service/src/main/java/com/mediflow/notification/web/NotificationController.java) · [AuthController](../../../../backend/gateway/src/main/java/com/mediflow/gateway/auth/AuthController.java)

#### LAB_TECH — Laboratory technician tasks

**A4 portrait**

![LAB_TECH — Laboratory technician tasks](word-compact/portrait/png/33-en.png)

[Mermaid](word-compact/portrait/mermaid/33-en.mmd) · [SVG](word-compact/portrait/svg/33-en.svg)

[Original SVG](assets/svg/33-en.svg) · [Portrait PNG](word-compact/portrait/png/33-en.png) · [Landscape PNG](word-compact/landscape/png/33-en.png)

Start/cancel remain subject to lifecycle and feature availability; exact LAB_TEST clearance applies to the V1 protected action. LAB_TECH can search/read tests and write results/status, but cannot create an indication, prescribe or collect payment. Filtering is an optional extension of the worklist. Sign-in and renewal are independent goals, not an include/extend pair. Staff business commands require a valid authenticated session. Preconditions and field validation are described in the sequence/specification, not expanded as screen-step use cases.

Extension conditions: E1: Filter tests by status — status filter selected

Source evidence: [LabController](../../../../backend/lab-service/src/main/java/com/mediflow/lab/web/LabController.java) · [AuthController](../../../../backend/gateway/src/main/java/com/mediflow/gateway/auth/AuthController.java)

#### PHARMACIST — Pharmacist tasks

**A4 landscape**

![PHARMACIST — Pharmacist tasks](word-compact/landscape/png/34-en.png)

[Mermaid](word-compact/landscape/mermaid/34-en.mmd) · [SVG](word-compact/landscape/svg/34-en.svg)

[Original SVG](assets/svg/34-en.svg) · [Portrait PNG](word-compact/portrait/png/34-en.png) · [Landscape PNG](word-compact/landscape/png/34-en.png)

The public outpatient dispense action resolves the signed actor and V1 lifecycle; exact PRESCRIPTION clearance, stock and reservation guards apply. It is explicit pharmacist action, not automatic dispensing on payment. PHARMACIST cannot create/cancel prescriptions; expiration is system lifecycle behavior rather than a human task. Sign-in and renewal are independent goals, not an include/extend pair. Staff business commands require a valid authenticated session. Preconditions and field validation are described in the sequence/specification, not expanded as screen-step use cases.

Extension conditions: E1: Filter medicine catalogue — catalogue filter selected

Source evidence: [DrugController](../../../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/web/DrugController.java) · [PrescriptionController](../../../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/web/PrescriptionController.java) · [AuthController](../../../../backend/gateway/src/main/java/com/mediflow/gateway/auth/AuthController.java)

#### CASHIER — Cashier tasks

**A4 landscape**

![CASHIER — Cashier tasks](word-compact/landscape/png/35-en.png)

[Mermaid](word-compact/landscape/mermaid/35-en.mmd) · [SVG](word-compact/landscape/svg/35-en.svg)

[Original SVG](assets/svg/35-en.svg) · [Portrait PNG](word-compact/portrait/png/35-en.png) · [Landscape PNG](word-compact/landscape/png/35-en.png)

This main diagram uses V1 ledger payment requests. Refund is an independent later operation on a refundable payment; it does not extend the act of paying. Legacy invoice commands remain source-supported compatibility operations outside the main V1 journey. Repeating a command preserves intent and must not collect money twice.

Extension conditions: None.

Source evidence: [InvoiceController](../../../../backend/billing-service/src/main/java/com/mediflow/billing/web/InvoiceController.java) · [PaymentRequestDiscoveryController](../../../../backend/billing-service/src/main/java/com/mediflow/billing/web/PaymentRequestDiscoveryController.java) · [LedgerPaymentController](../../../../backend/billing-service/src/main/java/com/mediflow/billing/web/LedgerPaymentController.java) · [LedgerRefundController](../../../../backend/billing-service/src/main/java/com/mediflow/billing/web/LedgerRefundController.java) · [LedgerRefundService](../../../../backend/billing-service/src/main/java/com/mediflow/billing/application/service/LedgerRefundService.java) · [AuthController](../../../../backend/gateway/src/main/java/com/mediflow/gateway/auth/AuthController.java)

#### PATIENT — Patient tasks

**A4 landscape**

![PATIENT — Patient tasks](word-compact/landscape/png/36-en.png)

[Mermaid](word-compact/landscape/mermaid/36-en.mmd) · [SVG](word-compact/landscape/svg/36-en.svg)

[Original SVG](assets/svg/36-en.svg) · [Portrait PNG](word-compact/portrait/png/36-en.png) · [Landscape PNG](word-compact/landscape/png/36-en.png)

This intentionally reflects the narrow current source permission surface. PATIENT cannot directly create/read appointments, retrieve patient records, lab results, prescriptions or invoices through the current Gateway/controller matrix. Self-registration and profile editing are not exposed. Those are future gaps, not implemented use cases. Notification detail checks ownership independently. Sign-in and renewal are independent goals, not an include/extend pair. Staff business commands require a valid authenticated session. Preconditions and field validation are described in the sequence/specification, not expanded as screen-step use cases.

Extension conditions: E1: Read notification detail — an owned notification is selected

Source evidence: [AuthController](../../../../backend/gateway/src/main/java/com/mediflow/gateway/auth/AuthController.java) · [NotificationController](../../../../backend/notification-service/src/main/java/com/mediflow/notification/web/NotificationController.java) · [RouteAuthorizationFilter](../../../../backend/gateway/src/main/java/com/mediflow/gateway/filter/RouteAuthorizationFilter.java)

<!-- role-usecases:end -->

### 1.3. Sequences Diagrams

<!-- word-sequence-excerpts:start -->
Word panels preserve the exact conditions enclosing each message. If a panel shows only one alternative branch, it uses `opt` with that branch condition to mark a guarded excerpt; read the complete figure for the full `alt` and other outcomes.
<!-- word-sequence-excerpts:end -->

The main journey uses the V1 outpatient contract consistently. Read in this order: schedule → arrive/check in → pay exam fee → consultation → indicated Lab/payment/results → doctor conclusion → optional prescription → payment/explicit dispense. Figures 06 and 04 are independent authentication scenarios. Each actor uses its own authorized session; abbreviated service API lifelines route through Gateway. V1 feature/acceptance gates still apply. Reports and notifications update independently and do not block care.

### Sign in — validation, rejected credentials and recovery

**Part 1/2 · A4 portrait**

![Sign in — validation, rejected credentials and recovery — Part 1/2](word-compact/panels/png/06-en-part-01.png)

[Mermaid](word-compact/panels/mermaid/06-en-part-01.mmd) · [SVG](word-compact/panels/svg/06-en-part-01.svg)

**Part 2/2 · A4 portrait**

![Sign in — validation, rejected credentials and recovery — Part 2/2](word-compact/panels/png/06-en-part-02.png)

[Mermaid](word-compact/panels/mermaid/06-en-part-02.mmd) · [SVG](word-compact/panels/svg/06-en-part-02.svg)

[Original SVG](assets/svg/06-en.svg) · [Portrait PNG](word-compact/portrait/png/06-en.png) · [Landscape PNG](word-compact/landscape/png/06-en.png)

Gateway issues tokens after Core verifies the account; Core does not send a token directly to the client. Renewal is figure 04, an independent use case. Invalid request error shape follows validation handling; login success uses LoginResponse rather than an invented universal envelope.

### Renew session — a separate authentication use case

**A4 portrait**

![Renew session — a separate authentication use case](word-compact/portrait/png/04-en.png)

[Mermaid](word-compact/portrait/mermaid/04-en.mmd) · [SVG](word-compact/portrait/svg/04-en.svg)

[Original SVG](assets/svg/04-en.svg) · [Portrait PNG](word-compact/portrait/png/04-en.png) · [Landscape PNG](word-compact/landscape/png/04-en.png)

Refresh validates the signed claims locally in Gateway; it does not call Core again or rotate a refresh token in the current implementation. This diagram describes the endpoint contract, not proof of automatic client refresh.

### Schedule appointment before arrival

**Part 1/2 · A4 landscape**

![Schedule appointment before arrival — Part 1/2](word-compact/panels/png/05-en-part-01.png)

[Mermaid](word-compact/panels/mermaid/05-en-part-01.mmd) · [SVG](word-compact/panels/svg/05-en-part-01.svg)

**Part 2/2 · A4 landscape**

![Schedule appointment before arrival — Part 2/2](word-compact/panels/png/05-en-part-02.png)

[Mermaid](word-compact/panels/mermaid/05-en-part-02.mmd) · [SVG](word-compact/panels/svg/05-en-part-02.svg)

[Original SVG](assets/svg/05-en.svg) · [Portrait PNG](word-compact/portrait/png/05-en.png) · [Landscape PNG](word-compact/landscape/png/05-en.png)

PATIENT requests scheduling through the nurse; no patient self-service API is invented. New patients are registered in Core first, outside this sequence. Only a successful saved appointment is a valid input to check-in (figure 07). Scheduling itself is not proof of payment or permission to start consultation.

### Arrival → exam payment → start consultation (V1)

**Part 1/3 · A4 landscape**

![Arrival → exam payment → start consultation (V1) — Part 1/3](word-compact/panels/png/07-en-part-01.png)

[Mermaid](word-compact/panels/mermaid/07-en-part-01.mmd) · [SVG](word-compact/panels/svg/07-en-part-01.svg)

**Part 2/3 · A4 portrait**

![Arrival → exam payment → start consultation (V1) — Part 2/3](word-compact/panels/png/07-en-part-02.png)

[Mermaid](word-compact/panels/mermaid/07-en-part-02.mmd) · [SVG](word-compact/panels/svg/07-en-part-02.svg)

**Part 3/3 · A4 landscape**

![Arrival → exam payment → start consultation (V1) — Part 3/3](word-compact/panels/png/07-en-part-03.png)

[Mermaid](word-compact/panels/mermaid/07-en-part-03.mmd) · [SVG](word-compact/panels/svg/07-en-part-03.svg)

[Original SVG](assets/svg/07-en.svg) · [Portrait PNG](word-compact/portrait/png/07-en.png) · [Landscape PNG](word-compact/landscape/png/07-en.png)

Normal V1 outpatient path. Check-in precedes fee collection, and fee authorization precedes examination. Audited emergency override remains a separately authorized source capability, not a fallback for ordinary unpaid visits. Actors use their own sessions; API lifelines abbreviate Gateway transport only. Event arrows are asynchronous and duplicate delivery does not create a second fee.

### Lab order → payment → test → doctor reads results (V1)

**Part 1/4 · A4 portrait**

![Lab order → payment → test → doctor reads results (V1) — Part 1/4](word-compact/panels/png/08-en-part-01.png)

[Mermaid](word-compact/panels/mermaid/08-en-part-01.mmd) · [SVG](word-compact/panels/svg/08-en-part-01.svg)

**Part 2/4 · A4 landscape**

![Lab order → payment → test → doctor reads results (V1) — Part 2/4](word-compact/panels/png/08-en-part-02.png)

[Mermaid](word-compact/panels/mermaid/08-en-part-02.mmd) · [SVG](word-compact/panels/svg/08-en-part-02.svg)

**Part 3/4 · A4 portrait**

![Lab order → payment → test → doctor reads results (V1) — Part 3/4](word-compact/panels/png/08-en-part-03.png)

[Mermaid](word-compact/panels/mermaid/08-en-part-03.mmd) · [SVG](word-compact/panels/svg/08-en-part-03.svg)

**Part 4/4 · A4 landscape**

![Lab order → payment → test → doctor reads results (V1) — Part 4/4](word-compact/panels/png/08-en-part-04.png)

[Mermaid](word-compact/panels/mermaid/08-en-part-04.mmd) · [SVG](word-compact/panels/svg/08-en-part-04.svg)

[Original SVG](assets/svg/08-en.svg) · [Portrait PNG](word-compact/portrait/png/08-en.png) · [Landscape PNG](word-compact/landscape/png/08-en.png)

The payment reference imports the complete success/failure payment subflow, not an unconditional successful payment. Starting and entering results both enforce current authority. The technician records results; the doctor interprets them in figure 37. Support projections do not block this path and are omitted. A failed result cannot produce the completion event.

### Doctor explains consultation outcome to patient

**Part 1/2 · A4 portrait**

![Doctor explains consultation outcome to patient — Part 1/2](word-compact/panels/png/37-en-part-01.png)

[Mermaid](word-compact/panels/mermaid/37-en-part-01.mmd) · [SVG](word-compact/panels/svg/37-en-part-01.svg)

**Part 2/2 · A4 portrait**

![Doctor explains consultation outcome to patient — Part 2/2](word-compact/panels/png/37-en-part-02.png)

[Mermaid](word-compact/panels/mermaid/37-en-part-02.mmd) · [SVG](word-compact/panels/svg/37-en-part-02.svg)

[Original SVG](assets/svg/37-en.svg) · [Portrait PNG](word-compact/portrait/png/37-en.png) · [Landscape PNG](word-compact/landscape/png/37-en.png)

The actor reads Lab results through its authorized API; asynchronous Clinical attachment may arrive later. This figure models professional explanation to the patient, not a new software result-delivery endpoint. Medication fulfilment is a separate Pharmacy/Billing process and is not proof of consultation-result delivery. The clinical completion command uses source lifecycle rules; it is not made dependent on a fabricated dispense condition.

### Create prescription — an optional separate workflow

**Part 1/2 · A4 portrait**

![Create prescription — an optional separate workflow — Part 1/2](word-compact/panels/png/38-en-part-01.png)

[Mermaid](word-compact/panels/mermaid/38-en-part-01.mmd) · [SVG](word-compact/panels/svg/38-en-part-01.svg)

**Part 2/2 · A4 landscape**

![Create prescription — an optional separate workflow — Part 2/2](word-compact/panels/png/38-en-part-02.png)

[Mermaid](word-compact/panels/mermaid/38-en-part-02.mmd) · [SVG](word-compact/panels/svg/38-en-part-02.svg)

[Original SVG](assets/svg/38-en.svg) · [Portrait PNG](word-compact/portrait/png/38-en.png) · [Landscape PNG](word-compact/landscape/png/38-en.png)

Prices come from server catalogue snapshots, not client-supplied amounts. Source context is validated rather than inferred from a diagnosis string or arbitrary UUID. Retry semantics follow the actual endpoint; this diagram does not invent a universal prescription idempotency key.

### Prescription payment and explicit pharmacist dispensing (V1)

**Part 1/3 · A4 portrait**

![Prescription payment and explicit pharmacist dispensing (V1) — Part 1/3](word-compact/panels/png/09-en-part-01.png)

[Mermaid](word-compact/panels/mermaid/09-en-part-01.mmd) · [SVG](word-compact/panels/svg/09-en-part-01.svg)

**Part 2/3 · A4 portrait**

![Prescription payment and explicit pharmacist dispensing (V1) — Part 2/3](word-compact/panels/png/09-en-part-02.png)

[Mermaid](word-compact/panels/mermaid/09-en-part-02.mmd) · [SVG](word-compact/panels/svg/09-en-part-02.svg)

**Part 3/3 · A4 landscape**

![Prescription payment and explicit pharmacist dispensing (V1) — Part 3/3](word-compact/panels/png/09-en-part-03.png)

[Mermaid](word-compact/panels/mermaid/09-en-part-03.mmd) · [SVG](word-compact/panels/svg/09-en-part-03.svg)

[Original SVG](assets/svg/09-en.svg) · [Portrait PNG](word-compact/portrait/png/09-en.png) · [Landscape PNG](word-compact/landscape/png/09-en.png)

The V0 automatic payment.completed saga is a legacy compatibility mechanism, not the main V1 journey. Pharmacist command, current target clearance, lifecycle and locked stock evidence are mandatory. Physical handover is shown as a human action after persisted confirmation; the source does not prove receipt by the patient.

### 1.4. Class Diagrams

Domain classes are grouped by deployed service. Core/Support preserve their internal context boundaries; Pharmacy/Billing share a figure but remain two independent services. Attributes are selective and must match source; dotted integration dependencies are not database relationships.

### Core service — Organization and Patient modules

**A4 landscape**

![Core service — Organization and Patient modules](word-compact/landscape/png/39-en.png)

[Mermaid](word-compact/landscape/mermaid/39-en.mmd) · [SVG](word-compact/landscape/svg/39-en.svg)

[Original SVG](assets/svg/39-en.svg) · [Portrait PNG](word-compact/portrait/png/39-en.png) · [Landscape PNG](word-compact/landscape/png/39-en.png)

Both modules run in Core but retain separate Organization/Patient stores. Identity references are optional and depend on account role; the drawing does not create a cross-database foreign key. Infrastructure classes are excluded from this domain overview.

### Clinical service — domain classes

**Part 1/2 · A4 landscape**

![Clinical service — domain classes — Part 1/2](word-compact/panels/png/10-en-part-01.png)

[Mermaid](word-compact/panels/mermaid/10-en-part-01.mmd) · [SVG](word-compact/panels/svg/10-en-part-01.svg)

**Part 2/2 · A4 landscape**

![Clinical service — domain classes — Part 2/2](word-compact/panels/png/10-en-part-02.png)

[Mermaid](word-compact/panels/mermaid/10-en-part-02.mmd) · [SVG](word-compact/panels/svg/10-en-part-02.svg)

[Original SVG](assets/svg/10-en.svg) · [Portrait PNG](word-compact/portrait/png/10-en.png) · [Landscape PNG](word-compact/landscape/png/10-en.png)

All classes in this figure belong to Clinical. LabTest/LabResult move to figure 40. Dashed UUID references do not imply JPA navigation. Appointment-backed records are distinct from preserved legacy records without an appointment. Selected attributes/methods are copied from source; no generated getters are listed.

### Lab service — tests, results and financial authority

**A4 landscape**

![Lab service — tests, results and financial authority](word-compact/landscape/png/40-en.png)

[Mermaid](word-compact/landscape/mermaid/40-en.mmd) · [SVG](word-compact/landscape/svg/40-en.svg)

[Original SVG](assets/svg/40-en.svg) · [Portrait PNG](word-compact/portrait/png/40-en.png) · [Landscape PNG](word-compact/landscape/png/40-en.png)

Clinical record and indication are external UUID references, not Clinical objects inside Lab. Lab stores its own received clearance evidence; Billing remains the authority for financial effects. Results are empty before completion and must be nonempty when completed.

### Pharmacy and Billing — separate services, explicit contracts

**Part 1/3 · A4 landscape**

![Pharmacy and Billing — separate services, explicit contracts — Part 1/3](word-compact/panels/png/11-en-part-01.png)

[Mermaid](word-compact/panels/mermaid/11-en-part-01.mmd) · [SVG](word-compact/panels/svg/11-en-part-01.svg)

**Part 2/3 · A4 landscape**

![Pharmacy and Billing — separate services, explicit contracts — Part 2/3](word-compact/panels/png/11-en-part-02.png)

[Mermaid](word-compact/panels/mermaid/11-en-part-02.mmd) · [SVG](word-compact/panels/svg/11-en-part-02.svg)

**Part 3/3 · A4 portrait**

![Pharmacy and Billing — separate services, explicit contracts — Part 3/3](word-compact/panels/png/11-en-part-03.png)

[Mermaid](word-compact/panels/mermaid/11-en-part-03.mmd) · [SVG](word-compact/panels/svg/11-en-part-03.svg)

[Original SVG](assets/svg/11-en.svg) · [Portrait PNG](word-compact/portrait/png/11-en.png) · [Landscape PNG](word-compact/landscape/png/11-en.png)

Namespaces are two independent service boundaries, each with its own database. Cross-boundary dependencies denote event/UUID contracts, never an entity relationship or shared transaction. The V1 class view intentionally uses ledger models; legacy Invoice remains in the database dictionary and compatibility source. StockReservation/DispenseSlip are created with a prescription in the represented successful creation workflow.

### Support service — separate reporting and notification models

**A4 portrait**

![Support service — separate reporting and notification models](word-compact/portrait/png/41-en.png)

[Mermaid](word-compact/portrait/mermaid/41-en.mmd) · [SVG](word-compact/portrait/svg/41-en.svg)

[Original SVG](assets/svg/41-en.svg) · [Portrait PNG](word-compact/portrait/png/41-en.png) · [Landscape PNG](word-compact/landscape/png/41-en.png)

Report and Notification are composed in Support but have separate databases and transaction managers. There is no invented domain association between independent projections. Domain events update these models asynchronously; they are not required participants in the consultation/result/dispense request path.

## 2. Data Flow Diagram

Consistency rule: EXAM, LAB_TEST and PRESCRIPTION are distinct payment targets. A stored payment is not an immediate guarantee of event delivery. Clinical outcome explanation and physical medicine handover are human interactions; neither implies a new patient self-service endpoint. The older V0 invoice/saga tables and algorithms remain compatibility references, outside the main V1 sequences.

The DFD represents business data, not endpoint contracts or cross-service transactions. Context process P0 decomposes into P1–P6 at Level 1; implementation routes all actor requests through clients/Gateway.

### DFD Level 0 — system boundary

**A4 landscape**

![DFD Level 0 — system boundary](word-compact/landscape/png/12-en.png)

[Mermaid](word-compact/landscape/mermaid/12-en.mmd) · [SVG](word-compact/landscape/svg/12-en.svg)

[Original SVG](assets/svg/12-en.svg) · [Portrait PNG](word-compact/portrait/png/12-en.png) · [Landscape PNG](word-compact/landscape/png/12-en.png)

### DFD Level 1 — six processes and eight stores

**Part 1/7 · A4 landscape**

![DFD Level 1 — six processes and eight stores — Part 1/7](word-compact/panels/png/13-en-part-01.png)

[Mermaid](word-compact/panels/mermaid/13-en-part-01.mmd) · [SVG](word-compact/panels/svg/13-en-part-01.svg)

**Part 2/7 · A4 landscape**

![DFD Level 1 — six processes and eight stores — Part 2/7](word-compact/panels/png/13-en-part-02.png)

[Mermaid](word-compact/panels/mermaid/13-en-part-02.mmd) · [SVG](word-compact/panels/svg/13-en-part-02.svg)

**Part 3/7 · A4 landscape**

![DFD Level 1 — six processes and eight stores — Part 3/7](word-compact/panels/png/13-en-part-03.png)

[Mermaid](word-compact/panels/mermaid/13-en-part-03.mmd) · [SVG](word-compact/panels/svg/13-en-part-03.svg)

**Part 4/7 · A4 landscape**

![DFD Level 1 — six processes and eight stores — Part 4/7](word-compact/panels/png/13-en-part-04.png)

[Mermaid](word-compact/panels/mermaid/13-en-part-04.mmd) · [SVG](word-compact/panels/svg/13-en-part-04.svg)

**Part 5/7 · A4 landscape**

![DFD Level 1 — six processes and eight stores — Part 5/7](word-compact/panels/png/13-en-part-05.png)

[Mermaid](word-compact/panels/mermaid/13-en-part-05.mmd) · [SVG](word-compact/panels/svg/13-en-part-05.svg)

**Part 6/7 · A4 landscape**

![DFD Level 1 — six processes and eight stores — Part 6/7](word-compact/panels/png/13-en-part-06.png)

[Mermaid](word-compact/panels/mermaid/13-en-part-06.mmd) · [SVG](word-compact/panels/svg/13-en-part-06.svg)

**Part 7/7 · A4 portrait**

![DFD Level 1 — six processes and eight stores — Part 7/7](word-compact/panels/png/13-en-part-07.png)

[Mermaid](word-compact/panels/mermaid/13-en-part-07.mmd) · [SVG](word-compact/panels/svg/13-en-part-07.svg)

[Original SVG](assets/svg/13-en.svg) · [Portrait PNG](word-compact/portrait/png/13-en.png) · [Landscape PNG](word-compact/landscape/png/13-en.png)

### DFD Level 2 — V1 payment and service authorization

**Part 1/3 · A4 portrait**

![DFD Level 2 — V1 payment and service authorization — Part 1/3](word-compact/panels/png/14-en-part-01.png)

[Mermaid](word-compact/panels/mermaid/14-en-part-01.mmd) · [SVG](word-compact/panels/svg/14-en-part-01.svg)

**Part 2/3 · A4 portrait**

![DFD Level 2 — V1 payment and service authorization — Part 2/3](word-compact/panels/png/14-en-part-02.png)

[Mermaid](word-compact/panels/mermaid/14-en-part-02.mmd) · [SVG](word-compact/panels/svg/14-en-part-02.svg)

**Part 3/3 · A4 portrait**

![DFD Level 2 — V1 payment and service authorization — Part 3/3](word-compact/panels/png/14-en-part-03.png)

[Mermaid](word-compact/panels/mermaid/14-en-part-03.mmd) · [SVG](word-compact/panels/svg/14-en-part-03.svg)

[Original SVG](assets/svg/14-en.svg) · [Portrait PNG](word-compact/portrait/png/14-en.png) · [Landscape PNG](word-compact/landscape/png/14-en.png)

## 3. Entity Relationship Diagram

The following ERDs are physical slices of each database derived from migrations. Crow’s feet denote local FKs, with no invented cross-database links. UUID fields naming another context’s object are logical references. Each diagram selects core tables; section 4 inventories every CREATE TABLE across eight contexts, including gated/deferred extension infrastructure.

### ERD — Organization

**A4 portrait**

![ERD — Organization](word-compact/portrait/png/15-en.png)

[Mermaid](word-compact/portrait/mermaid/15-en.mmd) · [SVG](word-compact/portrait/svg/15-en.svg)

[Original SVG](assets/svg/15-en.svg) · [Portrait PNG](word-compact/portrait/png/15-en.png) · [Landscape PNG](word-compact/landscape/png/15-en.png)

### ERD — Patient

**A4 landscape**

![ERD — Patient](word-compact/landscape/png/16-en.png)

[Mermaid](word-compact/landscape/mermaid/16-en.mmd) · [SVG](word-compact/landscape/svg/16-en.svg)

[Original SVG](assets/svg/16-en.svg) · [Portrait PNG](word-compact/portrait/png/16-en.png) · [Landscape PNG](word-compact/landscape/png/16-en.png)

### ERD — Clinical

**A4 portrait**

![ERD — Clinical](word-compact/portrait/png/17-en.png)

[Mermaid](word-compact/portrait/mermaid/17-en.mmd) · [SVG](word-compact/portrait/svg/17-en.svg)

[Original SVG](assets/svg/17-en.svg) · [Portrait PNG](word-compact/portrait/png/17-en.png) · [Landscape PNG](word-compact/landscape/png/17-en.png)

### ERD — Lab

**A4 portrait**

![ERD — Lab](word-compact/portrait/png/18-en.png)

[Mermaid](word-compact/portrait/mermaid/18-en.mmd) · [SVG](word-compact/portrait/svg/18-en.svg)

[Original SVG](assets/svg/18-en.svg) · [Portrait PNG](word-compact/portrait/png/18-en.png) · [Landscape PNG](word-compact/landscape/png/18-en.png)

### ERD — Pharmacy

**A4 landscape**

![ERD — Pharmacy](word-compact/landscape/png/19-en.png)

[Mermaid](word-compact/landscape/mermaid/19-en.mmd) · [SVG](word-compact/landscape/svg/19-en.svg)

[Original SVG](assets/svg/19-en.svg) · [Portrait PNG](word-compact/portrait/png/19-en.png) · [Landscape PNG](word-compact/landscape/png/19-en.png)

### ERD — Billing

**Part 1/4 · A4 portrait**

![ERD — Billing — Part 1/4](word-compact/panels/png/20-en-part-01.png)

[Mermaid](word-compact/panels/mermaid/20-en-part-01.mmd) · [SVG](word-compact/panels/svg/20-en-part-01.svg)

**Part 2/4 · A4 landscape**

![ERD — Billing — Part 2/4](word-compact/panels/png/20-en-part-02.png)

[Mermaid](word-compact/panels/mermaid/20-en-part-02.mmd) · [SVG](word-compact/panels/svg/20-en-part-02.svg)

**Part 3/4 · A4 landscape**

![ERD — Billing — Part 3/4](word-compact/panels/png/20-en-part-03.png)

[Mermaid](word-compact/panels/mermaid/20-en-part-03.mmd) · [SVG](word-compact/panels/svg/20-en-part-03.svg)

**Part 4/4 · A4 portrait**

![ERD — Billing — Part 4/4](word-compact/panels/png/20-en-part-04.png)

[Mermaid](word-compact/panels/mermaid/20-en-part-04.mmd) · [SVG](word-compact/panels/svg/20-en-part-04.svg)

[Original SVG](assets/svg/20-en.svg) · [Portrait PNG](word-compact/portrait/png/20-en.png) · [Landscape PNG](word-compact/landscape/png/20-en.png)

### ERD — Notification

**A4 portrait**

![ERD — Notification](word-compact/portrait/png/21-en.png)

[Mermaid](word-compact/portrait/mermaid/21-en.mmd) · [SVG](word-compact/portrait/svg/21-en.svg)

[Original SVG](assets/svg/21-en.svg) · [Portrait PNG](word-compact/portrait/png/21-en.png) · [Landscape PNG](word-compact/landscape/png/21-en.png)

### ERD — Report

**A4 landscape**

![ERD — Report](word-compact/landscape/png/22-en.png)

[Mermaid](word-compact/landscape/mermaid/22-en.mmd) · [SVG](word-compact/landscape/svg/22-en.svg)

[Original SVG](assets/svg/22-en.svg) · [Portrait PNG](word-compact/portrait/png/22-en.png) · [Landscape PNG](word-compact/landscape/png/22-en.png)
Additional unique-index cardinality: an appointment has at most one medical_record (`uq_medical_record_appointment`); a prescription has at most one dispense_slip; a financial_clearance belongs to one payment_request. Report is a read model with source UUIDs and no FK to Billing/Pharmacy. PAYMENT_REQUEST.invoice_id must not be inferred as an FK to the V0 INVOICE table.

| Logical reference | Authority | Exchange |
|---|---|---|
| `patient_id` | Core / Patient | REST identity lookup; event payload UUID |
| `doctor_id / department_id` | Core / Organization | REST authority lookup; signed actor context |
| `record_id / care_episode_id` | Clinical | Exact outpatient context contract / event |
| `test_id / source_order_id` | Lab / Clinical | Explicit order / result contracts |
| `prescription_id` | Pharmacy | Prescription facts / target lookup |
| `clearance_id / payment_request_id` | Billing | Purpose + exact target + expiry; event/lookup |

## 4. Table Design

### 4.1. Database Tables

Unquoted PostgreSQL identifiers fold to lowercase even when migrations use uppercase. This inventory reads migrations by V number and records each table’s creation and ALTER TABLE migrations. It inventories source, not a live DB: unapplied tables and inactive features may appear. Flyway’s automatically created `flyway_schema_history` is excluded from business table counts.

| Context | Database | Declared tables | Migrations |
|---|---|---:|---|
| Organization | `mediflow_organization` | 7 | 4 |
| Patient | `mediflow_patient` | 1 | 1 |
| Clinical | `mediflow_clinical` | 12 | 7 |
| Lab | `mediflow_lab` | 8 | 3 |
| Pharmacy | `mediflow_pharmacy` | 17 | 20 |
| Billing | `mediflow_billing` | 42 | 23 |
| Notification | `mediflow_notification` | 7 | 4 |
| Report | `mediflow_report` | 58 | 24 |

**Not every table is active in the MVP.** Clinical retains admission_referral; Billing/Pharmacy/Report/Notification retain advanced or inpatient/surgery structures. The inventory records source existence; activation follows profiles, not table names.

#### Organization — `mediflow_organization`

| Table | PK / identity | CREATE migration | ALTER migrations |
|---|---|---|---|
| `department` | `department_id` | [V1__init.sql](../../../../backend/core-service/src/main/resources/db/organization-migration/V1__init.sql) | [V1](../../../../backend/core-service/src/main/resources/db/organization-migration/V1__init.sql) |
| `staff` | `staff_id` | [V1__init.sql](../../../../backend/core-service/src/main/resources/db/organization-migration/V1__init.sql) | — |
| `account` | `account_id` | [V1__init.sql](../../../../backend/core-service/src/main/resources/db/organization-migration/V1__init.sql) | [V2](../../../../backend/core-service/src/main/resources/db/organization-migration/V2__account_patient_identity.sql) |
| `room` | `room_id` | [V3__room_lookup.sql](../../../../backend/core-service/src/main/resources/db/organization-migration/V3__room_lookup.sql) | — |
| `operating_room` | `room_id` | [V4__surgery_reference_authority.sql](../../../../backend/core-service/src/main/resources/db/organization-migration/V4__surgery_reference_authority.sql) | — |
| `surgical_capability` | `staff_id, team_role` | [V4__surgery_reference_authority.sql](../../../../backend/core-service/src/main/resources/db/organization-migration/V4__surgery_reference_authority.sql) | — |
| `surgery_authority_outbox` | `event_id` | [V4__surgery_reference_authority.sql](../../../../backend/core-service/src/main/resources/db/organization-migration/V4__surgery_reference_authority.sql) | — |

#### Patient — `mediflow_patient`

| Table | PK / identity | CREATE migration | ALTER migrations |
|---|---|---|---|
| `patient` | `patient_id` | [V1__init_patient.sql](../../../../backend/core-service/src/main/resources/db/patient-migration/V1__init_patient.sql) | — |

#### Clinical — `mediflow_clinical`

| Table | PK / identity | CREATE migration | ALTER migrations |
|---|---|---|---|
| `appointment` | `appointment_id` | [V1__init.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V1__init.sql) | [V4](../../../../backend/clinical-service/src/main/resources/db/migration/V4__care_finance_v2.sql) |
| `medical_record` | `record_id` | [V1__init.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V1__init.sql) | [V4](../../../../backend/clinical-service/src/main/resources/db/migration/V4__care_finance_v2.sql) |
| `diagnosis` | `diagnosis_id` | [V1__init.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V1__init.sql) | — |
| `attached_result` | `record_id, type, reference_id` | [V2__attached_result.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V2__attached_result.sql) | — |
| `processed_event` | `event_id` | [V3__processed_event.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V3__processed_event.sql) | — |
| `exam_clearance` | `clearance_id` | [V4__care_finance_v2.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V4__care_finance_v2.sql) | — |
| `clinical_emergency_override` | `override_id` | [V4__care_finance_v2.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V4__care_finance_v2.sql) | — |
| `admission_referral` | `admission_request_id` | [V4__care_finance_v2.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V4__care_finance_v2.sql) | — |
| `clinical_outbox_event` | `event_id` | [V4__care_finance_v2.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V4__care_finance_v2.sql) | [V5](../../../../backend/clinical-service/src/main/resources/db/migration/V5__clinical_outbox_delivery_state.sql) |
| `clinical_lab_order` | `source_order_id` | [V6__clinical_lab_orders.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V6__clinical_lab_orders.sql) | — |
| `consumer_inbox_delivery` | `consumer_name, event_id` | [V7__consumer_inbox.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V7__consumer_inbox.sql) | — |
| `consumer_inbox_operation` | `consumer_name, operation_key` | [V7__consumer_inbox.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V7__consumer_inbox.sql) | — |

#### Lab — `mediflow_lab`

| Table | PK / identity | CREATE migration | ALTER migrations |
|---|---|---|---|
| `lab_test` | `test_id` | [V1__init.sql](../../../../backend/lab-service/src/main/resources/db/migration/V1__init.sql) | [V2](../../../../backend/lab-service/src/main/resources/db/migration/V2__care_finance_v2.sql) |
| `lab_result` | `result_id` | [V1__init.sql](../../../../backend/lab-service/src/main/resources/db/migration/V1__init.sql) | — |
| `processed_event` | `event_id` | [V1__init.sql](../../../../backend/lab-service/src/main/resources/db/migration/V1__init.sql) | — |
| `lab_financial_clearance` | `clearance_target_id` | [V2__care_finance_v2.sql](../../../../backend/lab-service/src/main/resources/db/migration/V2__care_finance_v2.sql) | — |
| `lab_emergency_override` | `override_id` | [V2__care_finance_v2.sql](../../../../backend/lab-service/src/main/resources/db/migration/V2__care_finance_v2.sql) | — |
| `lab_outbox_event` | `event_id` | [V2__care_finance_v2.sql](../../../../backend/lab-service/src/main/resources/db/migration/V2__care_finance_v2.sql) | — |
| `consumer_inbox_delivery` | `consumer_name, event_id` | [V3__consumer_inbox.sql](../../../../backend/lab-service/src/main/resources/db/migration/V3__consumer_inbox.sql) | — |
| `consumer_inbox_operation` | `consumer_name, operation_key` | [V3__consumer_inbox.sql](../../../../backend/lab-service/src/main/resources/db/migration/V3__consumer_inbox.sql) | — |

#### Pharmacy — `mediflow_pharmacy`

| Table | PK / identity | CREATE migration | ALTER migrations |
|---|---|---|---|
| `drug` | `drug_id` | [V1__init.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V1__init.sql) | — |
| `prescription` | `prescription_id` | [V1__init.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V1__init.sql) | [V4](../../../../backend/pharmacy-service/src/main/resources/db/migration/V4__prescription_lifecycle_and_reservation_audit.sql), [V14](../../../../backend/pharmacy-service/src/main/resources/db/migration/V14__prescription_care_context.sql), [V17](../../../../backend/pharmacy-service/src/main/resources/db/migration/V17__held_prescription_care_events.sql) |
| `prescription_line` | `line_id` | [V1__init.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V1__init.sql) | [V17](../../../../backend/pharmacy-service/src/main/resources/db/migration/V17__held_prescription_care_events.sql) |
| `dispense_slip` | `dispense_id` | [V1__init.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V1__init.sql) | [V4](../../../../backend/pharmacy-service/src/main/resources/db/migration/V4__prescription_lifecycle_and_reservation_audit.sql), [V13](../../../../backend/pharmacy-service/src/main/resources/db/migration/V13__dispense_actor_audit_type.sql), [V17](../../../../backend/pharmacy-service/src/main/resources/db/migration/V17__held_prescription_care_events.sql) |
| `processed_event` | `event_id` | [V1__init.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V1__init.sql) | — |
| `stock_reservation` | `reservation_id` | [V2__stock_reservation.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V2__stock_reservation.sql) | [V3](../../../../backend/pharmacy-service/src/main/resources/db/migration/V3__unique_prescription_drug_reservation.sql), [V4](../../../../backend/pharmacy-service/src/main/resources/db/migration/V4__prescription_lifecycle_and_reservation_audit.sql) |
| `pharmacy_event_outbox` | `event_id` | [V5__pharmacy_event_outbox.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V5__pharmacy_event_outbox.sql) | [V7](../../../../backend/pharmacy-service/src/main/resources/db/migration/V7__pharmacy_event_outbox_lease.sql), [V9](../../../../backend/pharmacy-service/src/main/resources/db/migration/V9__pharmacy_event_outbox_quarantine.sql), [V12](../../../../backend/pharmacy-service/src/main/resources/db/migration/V12__pharmacy_outbox_causal_order.sql), [V17](../../../../backend/pharmacy-service/src/main/resources/db/migration/V17__held_prescription_care_events.sql), [V20](../../../../backend/pharmacy-service/src/main/resources/db/migration/V20__selective_outpatient_delivery_cohort.sql) |
| `payment_receipt` | `receipt_id` | [V6__payment_receipt.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V6__payment_receipt.sql) | — |
| `stock_adjustment` | `adjustment_id` | [V8__stock_adjustment_audit.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V8__stock_adjustment_audit.sql) | — |
| `pharmacy_scheduler_lease` | `job_name` | [V10__pharmacy_scheduler_cursor_lease.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V10__pharmacy_scheduler_cursor_lease.sql) | [V11](../../../../backend/pharmacy-service/src/main/resources/db/migration/V11__pharmacy_scheduler_fencing_token.sql) |
| `admission_medication_context` | `admission_id` | [V15__admission_medication_context.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V15__admission_medication_context.sql) | [V19](../../../../backend/pharmacy-service/src/main/resources/db/migration/V19__admission_medical_discharge.sql) |
| `admission_medication_event` | `event_id` | [V15__admission_medication_context.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V15__admission_medication_context.sql) | — |
| `prescription_clearance_target` | `prescription_id` | [V16__prescription_clearance.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V16__prescription_clearance.sql) | — |
| `prescription_clearance` | `clearance_id` | [V16__prescription_clearance.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V16__prescription_clearance.sql) | — |
| `prescription_clearance_event` | `event_id` | [V16__prescription_clearance.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V16__prescription_clearance.sql) | — |
| `care_prescription_creation` | `command_id` | [V18__held_care_creation_receipt.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V18__held_care_creation_receipt.sql) | — |
| `pharmacy_outpatient_delivery_cohort` | `prescription_id` | [V20__selective_outpatient_delivery_cohort.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V20__selective_outpatient_delivery_cohort.sql) | — |

#### Billing — `mediflow_billing`

| Table | PK / identity | CREATE migration | ALTER migrations |
|---|---|---|---|
| `fee` | `fee_id` | [V1__init.sql](../../../../backend/billing-service/src/main/resources/db/migration/V1__init.sql) | — |
| `invoice` | `invoice_id` | [V1__init.sql](../../../../backend/billing-service/src/main/resources/db/migration/V1__init.sql) | [V3](../../../../backend/billing-service/src/main/resources/db/migration/V3__drop_invoice_dispense_id.sql) |
| `processed_event` | `event_id` | [V1__init.sql](../../../../backend/billing-service/src/main/resources/db/migration/V1__init.sql) | — |
| `lab_test_type` | `lab_id` | [V1__init.sql](../../../../backend/billing-service/src/main/resources/db/migration/V1__init.sql) | — |
| `billing_event_outbox` | `event_id` | [V2__billing_event_outbox.sql](../../../../backend/billing-service/src/main/resources/db/migration/V2__billing_event_outbox.sql) | [V5](../../../../backend/billing-service/src/main/resources/db/migration/V5__ledger_payment_authority.sql), [V6](../../../../backend/billing-service/src/main/resources/db/migration/V6__ledger_event_delivery_fence.sql), [V22](../../../../backend/billing-service/src/main/resources/db/migration/V22__outpatient_publication_cohort.sql) |
| `billing_account` | `account_id` | [V4__care_finance_ledger.sql](../../../../backend/billing-service/src/main/resources/db/migration/V4__care_finance_ledger.sql) | — |
| `charge` | `charge_id` | [V4__care_finance_ledger.sql](../../../../backend/billing-service/src/main/resources/db/migration/V4__care_finance_ledger.sql) | [V7](../../../../backend/billing-service/src/main/resources/db/migration/V7__surgery_charge_reconciliation.sql), [V9](../../../../backend/billing-service/src/main/resources/db/migration/V9__surgery_planned_request_issuance.sql) |
| `payment_request` | `payment_request_id` | [V4__care_finance_ledger.sql](../../../../backend/billing-service/src/main/resources/db/migration/V4__care_finance_ledger.sql) | — |
| `payment_request_charge` | `payment_request_id, charge_id` | [V4__care_finance_ledger.sql](../../../../backend/billing-service/src/main/resources/db/migration/V4__care_finance_ledger.sql) | — |
| `payment_transaction` | `transaction_id` | [V4__care_finance_ledger.sql](../../../../backend/billing-service/src/main/resources/db/migration/V4__care_finance_ledger.sql) | [V5](../../../../backend/billing-service/src/main/resources/db/migration/V5__ledger_payment_authority.sql) |
| `payment_allocation` | `allocation_id` | [V4__care_finance_ledger.sql](../../../../backend/billing-service/src/main/resources/db/migration/V4__care_finance_ledger.sql) | — |
| `financial_clearance` | `clearance_id` | [V4__care_finance_ledger.sql](../../../../backend/billing-service/src/main/resources/db/migration/V4__care_finance_ledger.sql) | — |
| `insurance_adjustment` | `adjustment_id` | [V4__care_finance_ledger.sql](../../../../backend/billing-service/src/main/resources/db/migration/V4__care_finance_ledger.sql) | — |
| `settlement` | `settlement_id` | [V4__care_finance_ledger.sql](../../../../backend/billing-service/src/main/resources/db/migration/V4__care_finance_ledger.sql) | — |
| `payment_request_target` | `payment_request_id` | [V5__ledger_payment_authority.sql](../../../../backend/billing-service/src/main/resources/db/migration/V5__ledger_payment_authority.sql) | — |
| `ledger_refund_evidence` | `refund_transaction_id` | [V8__ledger_refund_evidence.sql](../../../../backend/billing-service/src/main/resources/db/migration/V8__ledger_refund_evidence.sql) | — |
| `surgery_charge_delivery` | `event_id` | [V9__surgery_planned_request_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V9__surgery_planned_request_issuance.sql) | [V9](../../../../backend/billing-service/src/main/resources/db/migration/V9__surgery_planned_request_issuance.sql) |
| `surgery_charge_source` | `surgery_case_id` | [V9__surgery_planned_request_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V9__surgery_planned_request_issuance.sql) | [V11](../../../../backend/billing-service/src/main/resources/db/migration/V11__surgery_requested_at_exact_time.sql) |
| `surgery_charge_item` | `surgery_case_id,item_code` | [V9__surgery_planned_request_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V9__surgery_planned_request_issuance.sql) | — |
| `surgery_cancellation_source` | `surgery_case_id` | [V10__surgery_cancellation_adjustment.sql](../../../../backend/billing-service/src/main/resources/db/migration/V10__surgery_cancellation_adjustment.sql) | — |
| `surgery_cancellation_delivery` | `event_id` | [V10__surgery_cancellation_adjustment.sql](../../../../backend/billing-service/src/main/resources/db/migration/V10__surgery_cancellation_adjustment.sql) | — |
| `surgery_cancellation_refund_due` | `cancellation_id,original_transaction_id,charge_id` | [V10__surgery_cancellation_adjustment.sql](../../../../backend/billing-service/src/main/resources/db/migration/V10__surgery_cancellation_adjustment.sql) | — |
| `lab_test_charge_delivery` | `event_id` | [V12__lab_test_charge_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V12__lab_test_charge_issuance.sql) | [V12](../../../../backend/billing-service/src/main/resources/db/migration/V12__lab_test_charge_issuance.sql) |
| `lab_test_charge_source` | `lab_id` | [V12__lab_test_charge_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V12__lab_test_charge_issuance.sql) | — |
| `admission_deposit_delivery` | `event_id` | [V13__admission_deposit_request_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V13__admission_deposit_request_issuance.sql) | [V13](../../../../backend/billing-service/src/main/resources/db/migration/V13__admission_deposit_request_issuance.sql) |
| `admission_deposit_source` | `admission_id` | [V13__admission_deposit_request_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V13__admission_deposit_request_issuance.sql) | — |
| `admission_reconciliation` | `settlement_id` | [V14__admission_reconciliation_snapshots.sql](../../../../backend/billing-service/src/main/resources/db/migration/V14__admission_reconciliation_snapshots.sql) | — |
| `admission_charge_close` | `admission_id` | [V15__admission_medical_charge_close.sql](../../../../backend/billing-service/src/main/resources/db/migration/V15__admission_medical_charge_close.sql) | — |
| `admission_charge_close_delivery` | `event_id` | [V15__admission_medical_charge_close.sql](../../../../backend/billing-service/src/main/resources/db/migration/V15__admission_medical_charge_close.sql) | — |
| `deposit_allocation_operation` | `allocation_operation_id` | [V16__admission_deposit_allocation.sql](../../../../backend/billing-service/src/main/resources/db/migration/V16__admission_deposit_allocation.sql) | — |
| `surgery_completion_source` | `surgery_case_id` | [V17__surgery_completion_proof.sql](../../../../backend/billing-service/src/main/resources/db/migration/V17__surgery_completion_proof.sql) | — |
| `surgery_completion_delivery` | `event_id` | [V17__surgery_completion_proof.sql](../../../../backend/billing-service/src/main/resources/db/migration/V17__surgery_completion_proof.sql) | — |
| `final_admission_settlement_operation` | `recognition_operation_id` | [V18__final_admission_settlement_operation.sql](../../../../backend/billing-service/src/main/resources/db/migration/V18__final_admission_settlement_operation.sql) | — |
| `prescription_charge_source` | `prescription_id` | [V19__prescription_charge_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V19__prescription_charge_issuance.sql) | [V20](../../../../backend/billing-service/src/main/resources/db/migration/V20__prescription_terminal_adjustment.sql) |
| `prescription_charge_item` | `prescription_id,drug_id` | [V19__prescription_charge_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V19__prescription_charge_issuance.sql) | — |
| `prescription_charge_delivery` | `event_id` | [V19__prescription_charge_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V19__prescription_charge_issuance.sql) | — |
| `prescription_terminal_source` | `prescription_id` | [V20__prescription_terminal_adjustment.sql](../../../../backend/billing-service/src/main/resources/db/migration/V20__prescription_terminal_adjustment.sql) | — |
| `prescription_terminal_delivery` | `event_id` | [V20__prescription_terminal_adjustment.sql](../../../../backend/billing-service/src/main/resources/db/migration/V20__prescription_terminal_adjustment.sql) | — |
| `prescription_terminal_refund_due` | `prescription_id, original_transaction_id, charge_id` | [V20__prescription_terminal_adjustment.sql](../../../../backend/billing-service/src/main/resources/db/migration/V20__prescription_terminal_adjustment.sql) | — |
| `exam_charge_delivery` | `event_id` | [V21__exam_charge_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V21__exam_charge_issuance.sql) | [V21](../../../../backend/billing-service/src/main/resources/db/migration/V21__exam_charge_issuance.sql) |
| `exam_charge_source` | `appointment_id` | [V21__exam_charge_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V21__exam_charge_issuance.sql) | — |
| `billing_outpatient_publication_cohort` | `payment_request_id` | [V22__outpatient_publication_cohort.sql](../../../../backend/billing-service/src/main/resources/db/migration/V22__outpatient_publication_cohort.sql) | — |

#### Notification — `mediflow_notification`

| Table | PK / identity | CREATE migration | ALTER migrations |
|---|---|---|---|
| `notification` | `notification_id` | [V1__init.sql](../../../../backend/notification-service/src/main/resources/db/migration/V1__init.sql) | [V2](../../../../backend/notification-service/src/main/resources/db/migration/V2__care_receipt_delivery.sql) |
| `processed_event` | `event_id` | [V1__init.sql](../../../../backend/notification-service/src/main/resources/db/migration/V1__init.sql) | [V2](../../../../backend/notification-service/src/main/resources/db/migration/V2__care_receipt_delivery.sql) |
| `notification_event_outbox` | `event_id` | [V2__care_receipt_delivery.sql](../../../../backend/notification-service/src/main/resources/db/migration/V2__care_receipt_delivery.sql) | — |
| `surgery_notice_case` | `surgery_case_id` | [V3__surgery_reminder_evidence.sql](../../../../backend/notification-service/src/main/resources/db/migration/V3__surgery_reminder_evidence.sql) | — |
| `surgery_notice_source` | `event_type,source_id` | [V3__surgery_reminder_evidence.sql](../../../../backend/notification-service/src/main/resources/db/migration/V3__surgery_reminder_evidence.sql) | — |
| `surgery_notice_snapshot` | `snapshot_id` | [V3__surgery_reminder_evidence.sql](../../../../backend/notification-service/src/main/resources/db/migration/V3__surgery_reminder_evidence.sql) | — |
| `care_notification_source` | `event_type,source_id` | [V4__care_notification_source.sql](../../../../backend/notification-service/src/main/resources/db/migration/V4__care_notification_source.sql) | — |

#### Report — `mediflow_report`

| Table | PK / identity | CREATE migration | ALTER migrations |
|---|---|---|---|
| `daily_visit_report` | `report_id` | [V1__init.sql](../../../../backend/report-service/src/main/resources/db/migration/V1__init.sql) | — |
| `monthly_revenue_report` | `report_id` | [V1__init.sql](../../../../backend/report-service/src/main/resources/db/migration/V1__init.sql) | — |
| `drug_statistic` | `statistic_id` | [V1__init.sql](../../../../backend/report-service/src/main/resources/db/migration/V1__init.sql) | — |
| `processed_event` | `event_id` | [V1__init.sql](../../../../backend/report-service/src/main/resources/db/migration/V1__init.sql) | — |
| `payment_contribution` | `invoice_id` | [V1__init.sql](../../../../backend/report-service/src/main/resources/db/migration/V1__init.sql) | [V2](../../../../backend/report-service/src/main/resources/db/migration/V2__enforce_pending_payment_scope.sql) |
| `financial_contribution` | `contribution_id` | [V6__care_finance_projections.sql](../../../../backend/report-service/src/main/resources/db/migration/V6__care_finance_projections.sql) | — |
| `daily_financial_report` | `report_id` | [V6__care_finance_projections.sql](../../../../backend/report-service/src/main/resources/db/migration/V6__care_finance_projections.sql) | — |
| `operational_contribution` | `contribution_id` | [V6__care_finance_projections.sql](../../../../backend/report-service/src/main/resources/db/migration/V6__care_finance_projections.sql) | [V7](../../../../backend/report-service/src/main/resources/db/migration/V7__operational_journal.sql) |
| `daily_operational_report` | `report_id` | [V6__care_finance_projections.sql](../../../../backend/report-service/src/main/resources/db/migration/V6__care_finance_projections.sql) | [V7](../../../../backend/report-service/src/main/resources/db/migration/V7__operational_journal.sql), [V21](../../../../backend/report-service/src/main/resources/db/migration/V21__medical_discharge_report_projection.sql) |
| `operational_event_journal` | `event_id` | [V7__operational_journal.sql](../../../../backend/report-service/src/main/resources/db/migration/V7__operational_journal.sql) | — |
| `operational_delivery` | `event_id, metric_type` | [V7__operational_journal.sql](../../../../backend/report-service/src/main/resources/db/migration/V7__operational_journal.sql) | — |
| `operational_replay_generation` | `generation_id` | [V8__isolated_operational_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V8__isolated_operational_replay.sql) | — |
| `operational_replay_input` | `generation_id, event_id` | [V8__isolated_operational_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V8__isolated_operational_replay.sql) | — |
| `operational_replay_contribution` | `generation_id, source_type, source_id, source_revision, metric_type` | [V8__isolated_operational_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V8__isolated_operational_replay.sql) | — |
| `operational_replay_scope` | `see DDL` | [V8__isolated_operational_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V8__isolated_operational_replay.sql) | — |
| `report_admission_target` | `admission_id` | [V9__pending_admission_report_evidence.sql](../../../../backend/report-service/src/main/resources/db/migration/V9__pending_admission_report_evidence.sql) | — |
| `report_admission_delivery` | `event_id` | [V9__pending_admission_report_evidence.sql](../../../../backend/report-service/src/main/resources/db/migration/V9__pending_admission_report_evidence.sql) | — |
| `report_admission_fact` | `admission_id, fact_type` | [V9__pending_admission_report_evidence.sql](../../../../backend/report-service/src/main/resources/db/migration/V9__pending_admission_report_evidence.sql) | — |
| `operational_report_publication` | `report_kind` | [V10__operational_snapshot_publication.sql](../../../../backend/report-service/src/main/resources/db/migration/V10__operational_snapshot_publication.sql) | — |
| `operational_source_snapshot` | `source_type,source_id,source_revision,event_type` | [V11__operational_source_fingerprints.sql](../../../../backend/report-service/src/main/resources/db/migration/V11__operational_source_fingerprints.sql) | — |
| `report_cash_receipt` | `transaction_id` | [V12__offline_gross_cash_receipts.sql](../../../../backend/report-service/src/main/resources/db/migration/V12__offline_gross_cash_receipts.sql) | — |
| `report_cash_delivery` | `event_id` | [V12__offline_gross_cash_receipts.sql](../../../../backend/report-service/src/main/resources/db/migration/V12__offline_gross_cash_receipts.sql) | — |
| `report_gross_cash_daily` | `scope_id` | [V12__offline_gross_cash_receipts.sql](../../../../backend/report-service/src/main/resources/db/migration/V12__offline_gross_cash_receipts.sql) | — |
| `cash_replay_generation` | `generation_id` | [V13__isolated_cash_receipt_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V13__isolated_cash_receipt_replay.sql) | — |
| `cash_replay_input` | `generation_id,transaction_id` | [V13__isolated_cash_receipt_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V13__isolated_cash_receipt_replay.sql) | — |
| `cash_replay_receipt` | `generation_id,transaction_id` | [V13__isolated_cash_receipt_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V13__isolated_cash_receipt_replay.sql) | — |
| `cash_replay_scope` | `see DDL` | [V13__isolated_cash_receipt_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V13__isolated_cash_receipt_replay.sql) | — |
| `report_cash_refund` | `refund_transaction_id` | [V15__completed_cash_refund_evidence.sql](../../../../backend/report-service/src/main/resources/db/migration/V15__completed_cash_refund_evidence.sql) | — |
| `report_cash_refund_delivery` | `event_id` | [V15__completed_cash_refund_evidence.sql](../../../../backend/report-service/src/main/resources/db/migration/V15__completed_cash_refund_evidence.sql) | — |
| `report_refund_cash_daily` | `scope_id` | [V15__completed_cash_refund_evidence.sql](../../../../backend/report-service/src/main/resources/db/migration/V15__completed_cash_refund_evidence.sql) | — |
| `prescription_fill_receipt` | `prescription_id` | [V16__prescription_fill_source_receipt.sql](../../../../backend/report-service/src/main/resources/db/migration/V16__prescription_fill_source_receipt.sql) | — |
| `refund_replay_generation` | `generation_id` | [V17__isolated_cash_refund_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V17__isolated_cash_refund_replay.sql) | — |
| `refund_replay_input` | `generation_id,refund_transaction_id` | [V17__isolated_cash_refund_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V17__isolated_cash_refund_replay.sql) | — |
| `refund_replay_fact` | `generation_id,refund_transaction_id` | [V17__isolated_cash_refund_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V17__isolated_cash_refund_replay.sql) | — |
| `refund_replay_scope` | `see DDL` | [V17__isolated_cash_refund_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V17__isolated_cash_refund_replay.sql) | — |
| `report_settlement_snapshot` | `settlement_id` | [V19__settlement_receivable_stock.sql](../../../../backend/report-service/src/main/resources/db/migration/V19__settlement_receivable_stock.sql) | — |
| `report_settlement_delivery` | `event_id` | [V19__settlement_receivable_stock.sql](../../../../backend/report-service/src/main/resources/db/migration/V19__settlement_receivable_stock.sql) | — |
| `report_receivable_current` | `account_id` | [V19__settlement_receivable_stock.sql](../../../../backend/report-service/src/main/resources/db/migration/V19__settlement_receivable_stock.sql) | — |
| `report_receivable_stock` | `see DDL` | [V19__settlement_receivable_stock.sql](../../../../backend/report-service/src/main/resources/db/migration/V19__settlement_receivable_stock.sql) | — |
| `financial_report_generation` | `generation_id` | [V20__financial_snapshot_read_boundary.sql](../../../../backend/report-service/src/main/resources/db/migration/V20__financial_snapshot_read_boundary.sql) | — |
| `financial_report_day` | `see DDL` | [V20__financial_snapshot_read_boundary.sql](../../../../backend/report-service/src/main/resources/db/migration/V20__financial_snapshot_read_boundary.sql) | — |
| `financial_report_publication` | `currency,report_zone` | [V20__financial_snapshot_read_boundary.sql](../../../../backend/report-service/src/main/resources/db/migration/V20__financial_snapshot_read_boundary.sql) | — |
| `report_medical_discharge` | `admission_id` | [V21__medical_discharge_report_projection.sql](../../../../backend/report-service/src/main/resources/db/migration/V21__medical_discharge_report_projection.sql) | — |
| `report_medical_discharge_delivery` | `event_id` | [V21__medical_discharge_report_projection.sql](../../../../backend/report-service/src/main/resources/db/migration/V21__medical_discharge_report_projection.sql) | — |
| `report_final_recognition` | `recognition_operation_id` | [V22__final_settlement_recognition.sql](../../../../backend/report-service/src/main/resources/db/migration/V22__final_settlement_recognition.sql) | — |
| `report_final_recognition_delivery` | `event_id` | [V22__final_settlement_recognition.sql](../../../../backend/report-service/src/main/resources/db/migration/V22__final_settlement_recognition.sql) | — |
| `report_final_recognition_contribution` | `see DDL` | [V22__final_settlement_recognition.sql](../../../../backend/report-service/src/main/resources/db/migration/V22__final_settlement_recognition.sql) | — |
| `report_final_deposit_release` | `recognition_operation_id,original_transaction_id` | [V22__final_settlement_recognition.sql](../../../../backend/report-service/src/main/resources/db/migration/V22__final_settlement_recognition.sql) | — |
| `report_final_recognition_daily` | `see DDL` | [V22__final_settlement_recognition.sql](../../../../backend/report-service/src/main/resources/db/migration/V22__final_settlement_recognition.sql) | — |
| `financial_source_rebuild` | `generation_id` | [V23__finite_financial_source_rebuild.sql](../../../../backend/report-service/src/main/resources/db/migration/V23__finite_financial_source_rebuild.sql) | — |
| `financial_source_rebuild_input` | `generation_id,source_kind,source_id` | [V23__finite_financial_source_rebuild.sql](../../../../backend/report-service/src/main/resources/db/migration/V23__finite_financial_source_rebuild.sql) | — |
| `financial_source_rebuild_day` | `see DDL` | [V23__finite_financial_source_rebuild.sql](../../../../backend/report-service/src/main/resources/db/migration/V23__finite_financial_source_rebuild.sql) | — |
| `financial_archive_restore` | `archive_fingerprint` | [V24__financial_archive_restore_provenance.sql](../../../../backend/report-service/src/main/resources/db/migration/V24__financial_archive_restore_provenance.sql) | — |
| `financial_generation_acceptance` | `generation_id` | [V25__controlled_financial_publication.sql](../../../../backend/report-service/src/main/resources/db/migration/V25__controlled_financial_publication.sql) | — |
| `financial_publication_audit` | `command_id` | [V25__controlled_financial_publication.sql](../../../../backend/report-service/src/main/resources/db/migration/V25__controlled_financial_publication.sql) | — |
| `operational_generation_acceptance` | `generation_id` | [V26__controlled_operational_publication.sql](../../../../backend/report-service/src/main/resources/db/migration/V26__controlled_operational_publication.sql) | — |
| `operational_publication_audit` | `command_id` | [V26__controlled_operational_publication.sql](../../../../backend/report-service/src/main/resources/db/migration/V26__controlled_operational_publication.sql) | — |
| `report_outpatient_coverage` | `coverage_id` | [V27__outpatient_read_coverage.sql](../../../../backend/report-service/src/main/resources/db/migration/V27__outpatient_read_coverage.sql) | — |

### 4.2. Core table column dictionary

The following tables represent the outpatient journey. Column definitions are extracted from CREATE and ADD COLUMN; dropped columns are removed. This is not a full SQL execution engine: ALTER TYPE/DEFAULT, constraints/indexes and conditional SQL must also be read in linked migrations; do not reconstruct production DDL from this summary. Removed fields such as `invoice.dispense_id` are excluded.

#### Organization.account

| Column | Declared type and column constraints |
|---|---|
| `account_id` | `UUID PRIMARY KEY` |
| `username` | `VARCHAR(50) NOT NULL` |
| `password_hash` | `VARCHAR(255) NOT NULL` |
| `staff_id` | `UUID` |
| `role` | `VARCHAR(20) NOT NULL` |
| `is_active` | `BOOLEAN NOT NULL DEFAULT TRUE` |
| `last_login_at` | `TIMESTAMPTZ` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |
| `patient_id` | `UUID` |

#### Organization.staff

| Column | Declared type and column constraints |
|---|---|
| `staff_id` | `UUID PRIMARY KEY` |
| `full_name` | `VARCHAR(100) NOT NULL` |
| `department_id` | `UUID NOT NULL` |
| `job_title` | `VARCHAR(20) NOT NULL` |
| `specialization` | `VARCHAR(100)` |
| `license_number` | `VARCHAR(50)` |
| `phone_number` | `VARCHAR(15)` |
| `email` | `VARCHAR(100)` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |

#### Organization.department

| Column | Declared type and column constraints |
|---|---|
| `department_id` | `UUID PRIMARY KEY` |
| `department_name` | `VARCHAR(100) NOT NULL` |
| `abbreviation` | `VARCHAR(20) NOT NULL` |
| `department_type` | `VARCHAR(20) NOT NULL` |
| `department_head_id` | `UUID` |
| `location` | `VARCHAR(255)` |
| `is_active` | `BOOLEAN NOT NULL DEFAULT TRUE` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |

#### Patient.patient

| Column | Declared type and column constraints |
|---|---|
| `patient_id` | `UUID PRIMARY KEY` |
| `full_name` | `VARCHAR(100) NOT NULL` |
| `date_of_birth` | `DATE NOT NULL` |
| `gender` | `VARCHAR(1) NOT NULL` |
| `identity_number` | `VARCHAR(20) NOT NULL` |
| `address` | `VARCHAR(255)` |
| `phone_number` | `VARCHAR(15)` |
| `email` | `VARCHAR(100)` |
| `health_insurance_number` | `VARCHAR(20)` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |

#### Clinical.appointment

| Column | Declared type and column constraints |
|---|---|
| `appointment_id` | `UUID PRIMARY KEY` |
| `patient_id` | `UUID NOT NULL` |
| `doctor_id` | `UUID NOT NULL` |
| `department_id` | `UUID NOT NULL` |
| `appointment_date` | `DATE NOT NULL` |
| `appointment_time` | `TIME NOT NULL` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'PENDING'` |
| `reason` | `TEXT` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |
| `care_contract_version` | `SMALLINT NOT NULL DEFAULT 0` |
| `exam_clearance_id` | `UUID` |
| `exam_clearance_at` | `TIMESTAMPTZ` |
| `emergency_override_id` | `UUID` |
| `exam_price_code` | `VARCHAR(64)` |
| `checked_in_at` | `TIMESTAMPTZ` |
| `examination_started_at` | `TIMESTAMPTZ` |
| `completed_at` | `TIMESTAMPTZ` |

#### Clinical.medical_record

| Column | Declared type and column constraints |
|---|---|
| `record_id` | `UUID PRIMARY KEY` |
| `patient_id` | `UUID NOT NULL` |
| `doctor_id` | `UUID NOT NULL` |
| `department_id` | `UUID NOT NULL` |
| `examination_date` | `DATE NOT NULL` |
| `symptoms` | `TEXT` |
| `appointment_id` | `UUID REFERENCES appointment(appointment_id)` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'OPEN'` |
| `disposition` | `VARCHAR(32)` |
| `disposition_note` | `TEXT` |
| `completed_at` | `TIMESTAMPTZ` |

#### Clinical.diagnosis

| Column | Declared type and column constraints |
|---|---|
| `diagnosis_id` | `UUID PRIMARY KEY` |
| `record_id` | `UUID NOT NULL REFERENCES medical_record(record_id) ON DELETE CASCADE` |
| `diagnosis_name` | `VARCHAR(255) NOT NULL` |
| `description` | `TEXT` |
| `icd_code` | `VARCHAR(10)` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |

#### Clinical.clinical_lab_order

| Column | Declared type and column constraints |
|---|---|
| `source_order_id` | `UUID PRIMARY KEY` |
| `record_id` | `UUID NOT NULL REFERENCES medical_record(record_id)` |
| `patient_id` | `UUID NOT NULL` |
| `requesting_doctor_id` | `UUID NOT NULL` |
| `requesting_department_id` | `UUID NOT NULL` |
| `care_episode_type` | `VARCHAR(32) NOT NULL CHECK (care_episode_type = 'OUTPATIENT_VISIT')` |
| `care_episode_id` | `UUID NOT NULL REFERENCES appointment(appointment_id)` |
| `lab_type` | `VARCHAR(50) NOT NULL CHECK (length(btrim(lab_type)) > 0)` |
| `requested_date` | `DATE NOT NULL` |
| `price_code` | `VARCHAR(64) NOT NULL CHECK (length(btrim(price_code)) > 0)` |
| `ordered_at` | `TIMESTAMPTZ NOT NULL` |

#### Lab.lab_test

| Column | Declared type and column constraints |
|---|---|
| `test_id` | `UUID PRIMARY KEY` |
| `record_id` | `UUID NOT NULL` |
| `patient_id` | `UUID NOT NULL` |
| `requesting_department_id` | `UUID NOT NULL` |
| `test_type` | `VARCHAR(50) NOT NULL` |
| `requested_date` | `DATE NOT NULL` |
| `performed_date` | `DATE` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'PENDING'` |
| `conclusion` | `TEXT` |
| `is_paid` | `BOOLEAN NOT NULL DEFAULT false` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |
| `care_contract_version` | `SMALLINT NOT NULL DEFAULT 0` |
| `care_episode_type` | `VARCHAR(32)` |
| `care_episode_id` | `UUID` |
| `source_order_id` | `UUID` |
| `price_code` | `VARCHAR(64)` |
| `clearance_id` | `UUID` |
| `clearance_granted_at` | `TIMESTAMPTZ` |
| `emergency_override_id` | `UUID` |
| `result_version` | `INTEGER NOT NULL DEFAULT 0` |

#### Lab.lab_result

| Column | Declared type and column constraints |
|---|---|
| `result_id` | `UUID PRIMARY KEY` |
| `test_id` | `UUID NOT NULL REFERENCES lab_test(test_id) ON DELETE CASCADE` |
| `indicator` | `VARCHAR(100) NOT NULL` |
| `value` | `VARCHAR(50) NOT NULL` |
| `unit` | `VARCHAR(20)` |
| `reference_range` | `VARCHAR(50)` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |

#### Pharmacy.drug

| Column | Declared type and column constraints |
|---|---|
| `drug_id` | `UUID PRIMARY KEY` |
| `drug_name` | `VARCHAR(150) NOT NULL` |
| `active_ingredient` | `VARCHAR(150)` |
| `unit` | `VARCHAR(20) NOT NULL` |
| `price` | `DECIMAL(15,2) NOT NULL` |
| `stock_quantity` | `INT NOT NULL DEFAULT 0` |
| `expiry_date` | `DATE NOT NULL` |
| `manufacturer` | `VARCHAR(150)` |
| `low_stock_threshold` | `INT NOT NULL DEFAULT 10` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |

#### Pharmacy.prescription

| Column | Declared type and column constraints |
|---|---|
| `prescription_id` | `UUID PRIMARY KEY` |
| `record_id` | `UUID` |
| `patient_id` | `UUID NOT NULL` |
| `doctor_id` | `UUID NOT NULL` |
| `department_id` | `UUID NOT NULL` |
| `prescribed_date` | `DATE NOT NULL` |
| `total_amount` | `DECIMAL(15,2) NOT NULL DEFAULT 0` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |
| `status` | `VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'` |
| `cancelled_at` | `TIMESTAMPTZ` |
| `cancelled_by` | `UUID` |
| `cancellation_reason` | `VARCHAR(500)` |
| `care_contract_version` | `SMALLINT NOT NULL DEFAULT 0` |
| `care_context` | `VARCHAR(20) NOT NULL DEFAULT 'OUTPATIENT'` |
| `care_episode_type` | `VARCHAR(32)` |
| `care_episode_id` | `UUID` |
| `admission_id` | `UUID` |
| `price_code` | `VARCHAR(64)` |
| `lifecycle_at_iso` | `VARCHAR(35)` |

#### Pharmacy.prescription_line

| Column | Declared type and column constraints |
|---|---|
| `line_id` | `UUID PRIMARY KEY` |
| `prescription_id` | `UUID NOT NULL REFERENCES PRESCRIPTION(prescription_id) ON DELETE CASCADE` |
| `drug_id` | `UUID NOT NULL REFERENCES DRUG(drug_id)` |
| `quantity` | `INT NOT NULL` |
| `unit_price` | `DECIMAL(15,2) NOT NULL` |
| `dosage` | `VARCHAR(255)` |
| `line_total` | `DECIMAL(15,2) NOT NULL` |
| `drug_name_snapshot` | `VARCHAR(150)` |

#### Pharmacy.stock_reservation

| Column | Declared type and column constraints |
|---|---|
| `reservation_id` | `UUID PRIMARY KEY` |
| `drug_id` | `UUID NOT NULL REFERENCES DRUG(drug_id)` |
| `prescription_id` | `UUID NOT NULL REFERENCES PRESCRIPTION(prescription_id)` |
| `quantity` | `INT NOT NULL` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'RESERVED'` |
| `expires_at` | `TIMESTAMPTZ NOT NULL` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |
| `release_reason` | `VARCHAR(50)` |
| `released_at` | `TIMESTAMPTZ` |
| `released_by` | `UUID` |

#### Pharmacy.dispense_slip

| Column | Declared type and column constraints |
|---|---|
| `dispense_id` | `UUID PRIMARY KEY` |
| `prescription_id` | `UUID NOT NULL UNIQUE REFERENCES PRESCRIPTION(prescription_id)` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'PENDING'` |
| `dispensed_at` | `TIMESTAMPTZ` |
| `dispensed_by` | `UUID` |
| `failure_reason` | `VARCHAR(500)` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |
| `dispensed_actor_type` | `VARCHAR(30)` |
| `lifecycle_at_iso` | `VARCHAR(35)` |

#### Billing.invoice

| Column | Declared type and column constraints |
|---|---|
| `invoice_id` | `UUID PRIMARY KEY` |
| `patient_id` | `UUID NOT NULL` |
| `created_date` | `DATE NOT NULL` |
| `total_amount` | `DECIMAL(15,2) NOT NULL` |
| `is_paid` | `BOOLEAN NOT NULL DEFAULT false` |
| `payment_method` | `VARCHAR(20)` |
| `prescription_id` | `UUID` |
| `saga_status` | `VARCHAR(20) NOT NULL DEFAULT 'NONE'` |
| `paid_at` | `TIMESTAMPTZ` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |

#### Billing.billing_account

| Column | Declared type and column constraints |
|---|---|
| `account_id` | `UUID PRIMARY KEY` |
| `patient_id` | `UUID NOT NULL` |
| `department_id` | `UUID NOT NULL` |
| `care_episode_type` | `VARCHAR(32) NOT NULL` |
| `care_episode_id` | `UUID NOT NULL` |
| `status` | `VARCHAR(32) NOT NULL DEFAULT 'OPEN'` |
| `currency` | `CHAR(3) NOT NULL DEFAULT 'VND'` |
| `version` | `BIGINT NOT NULL DEFAULT 0` |
| `opened_at` | `TIMESTAMPTZ NOT NULL` |
| `charge_closed_at` | `TIMESTAMPTZ` |
| `closed_at` | `TIMESTAMPTZ` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |

#### Billing.charge

| Column | Declared type and column constraints |
|---|---|
| `charge_id` | `UUID PRIMARY KEY` |
| `account_id` | `UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id)` |
| `patient_id` | `UUID NOT NULL` |
| `department_id` | `UUID NOT NULL` |
| `source_type` | `VARCHAR(32) NOT NULL` |
| `source_id` | `UUID NOT NULL` |
| `price_code` | `VARCHAR(64) NOT NULL` |
| `description` | `VARCHAR(255) NOT NULL` |
| `quantity` | `NUMERIC(19,4) NOT NULL DEFAULT 1 CHECK (quantity > 0)` |
| `unit_amount` | `DECIMAL(19,2) NOT NULL CHECK (unit_amount >= 0)` |
| `gross_amount` | `DECIMAL(19,2) NOT NULL CHECK (gross_amount >= 0)` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'POSTED'` |
| `void_reason` | `VARCHAR(500)` |
| `incurred_at` | `TIMESTAMPTZ NOT NULL` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `reconciled_result_id` | `UUID` |

#### Billing.payment_request

| Column | Declared type and column constraints |
|---|---|
| `payment_request_id` | `UUID PRIMARY KEY` |
| `invoice_id` | `UUID NOT NULL UNIQUE` |
| `account_id` | `UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id)` |
| `purpose` | `VARCHAR(32) NOT NULL` |
| `status` | `VARCHAR(24) NOT NULL DEFAULT 'PENDING'` |
| `requested_amount` | `DECIMAL(19,2) NOT NULL CHECK (requested_amount >= 0)` |
| `currency` | `CHAR(3) NOT NULL DEFAULT 'VND'` |
| `expires_at` | `TIMESTAMPTZ` |
| `created_by` | `UUID` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `completed_at` | `TIMESTAMPTZ` |

#### Billing.payment_transaction

| Column | Declared type and column constraints |
|---|---|
| `transaction_id` | `UUID PRIMARY KEY` |
| `account_id` | `UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id)` |
| `payment_request_id` | `UUID REFERENCES PAYMENT_REQUEST(payment_request_id)` |
| `transaction_type` | `VARCHAR(20) NOT NULL` |
| `classification` | `VARCHAR(24) NOT NULL` |
| `status` | `VARCHAR(20) NOT NULL` |
| `amount` | `DECIMAL(19,2) NOT NULL CHECK (amount > 0)` |
| `currency` | `CHAR(3) NOT NULL DEFAULT 'VND'` |
| `payment_method` | `VARCHAR(20) NOT NULL` |
| `provider_reference` | `VARCHAR(120)` |
| `idempotency_key` | `VARCHAR(120) NOT NULL UNIQUE` |
| `original_transaction_id` | `UUID REFERENCES PAYMENT_TRANSACTION(transaction_id)` |
| `completed_at` | `TIMESTAMPTZ` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `actor_account_id` | `UUID` |

#### Billing.payment_allocation

| Column | Declared type and column constraints |
|---|---|
| `allocation_id` | `UUID PRIMARY KEY` |
| `transaction_id` | `UUID NOT NULL REFERENCES PAYMENT_TRANSACTION(transaction_id)` |
| `charge_id` | `UUID NOT NULL REFERENCES CHARGE(charge_id)` |
| `amount` | `DECIMAL(19,2) NOT NULL CHECK (amount > 0)` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |

#### Billing.financial_clearance

| Column | Declared type and column constraints |
|---|---|
| `clearance_id` | `UUID PRIMARY KEY` |
| `account_id` | `UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id)` |
| `payment_request_id` | `UUID NOT NULL REFERENCES PAYMENT_REQUEST(payment_request_id)` |
| `invoice_id` | `UUID NOT NULL` |
| `patient_id` | `UUID NOT NULL` |
| `purpose` | `VARCHAR(32) NOT NULL` |
| `care_episode_type` | `VARCHAR(32) NOT NULL` |
| `care_episode_id` | `UUID NOT NULL` |
| `appointment_id` | `UUID` |
| `record_id` | `UUID` |
| `lab_test_ids` | `UUID[] NOT NULL DEFAULT '{}'` |
| `prescription_id` | `UUID` |
| `admission_id` | `UUID` |
| `surgery_case_id` | `UUID` |
| `amount` | `DECIMAL(19,2) NOT NULL CHECK (amount >= 0)` |
| `currency` | `CHAR(3) NOT NULL DEFAULT 'VND'` |
| `payment_method` | `VARCHAR(20) NOT NULL` |
| `emergency_override` | `BOOLEAN NOT NULL DEFAULT FALSE` |
| `expires_at` | `TIMESTAMPTZ` |
| `granted_at` | `TIMESTAMPTZ NOT NULL` |
| `revoked_at` | `TIMESTAMPTZ` |

#### Notification.notification

| Column | Declared type and column constraints |
|---|---|
| `notification_id` | `UUID PRIMARY KEY` |
| `patient_id` | `UUID NOT NULL` |
| `title` | `VARCHAR(255) NOT NULL` |
| `content` | `TEXT NOT NULL` |
| `channel` | `VARCHAR(10) NOT NULL` |
| `recipient_address` | `VARCHAR(150)` |
| `status` | `VARCHAR(10) NOT NULL DEFAULT 'PENDING'` |
| `failure_reason` | `VARCHAR(255)` |
| `retry_count` | `INT NOT NULL DEFAULT 0` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `sent_at` | `TIMESTAMPTZ` |
| `template_key` | `VARCHAR(64)` |
| `source_event_id` | `UUID` |
| `source_event_type` | `VARCHAR(100)` |
| `source_id` | `UUID` |
| `correlation_id` | `VARCHAR(120)` |
| `sensitivity` | `VARCHAR(32) NOT NULL DEFAULT 'STANDARD'` |

#### Report.daily_visit_report

| Column | Declared type and column constraints |
|---|---|
| `report_id` | `UUID PRIMARY KEY` |
| `report_date` | `DATE NOT NULL` |
| `department_id` | `UUID` |
| `visit_count` | `INT NOT NULL DEFAULT 0` |
| `lab_count` | `INT NOT NULL DEFAULT 0` |
| `prescription_count` | `INT NOT NULL DEFAULT 0` |
| `revenue` | `DECIMAL(15,2) NOT NULL DEFAULT 0` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |

#### Report.monthly_revenue_report

| Column | Declared type and column constraints |
|---|---|
| `report_id` | `UUID PRIMARY KEY` |
| `month` | `INT NOT NULL` |
| `year` | `INT NOT NULL` |
| `department_id` | `UUID` |
| `total_revenue` | `DECIMAL(15,2) NOT NULL DEFAULT 0` |
| `invoice_count` | `INT NOT NULL DEFAULT 0` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |

#### Report.drug_statistic

| Column | Declared type and column constraints |
|---|---|
| `statistic_id` | `UUID PRIMARY KEY` |
| `drug_id` | `UUID NOT NULL` |
| `drug_name` | `VARCHAR(150) NOT NULL` |
| `report_date` | `DATE NOT NULL` |
| `department_id` | `UUID` |
| `dispensed_quantity` | `INT NOT NULL DEFAULT 0` |
| `updated_at` | `TIMESTAMPTZ` |

### 4.3. Constraints and persistence strategy

- UUIDs are business identifiers; cross-context references have no JPA relationship/FK. Organization.account.patient_id remains a logical Patient reference even inside the Core JVM.
- Money uses BigDecimal and DDL-specific NUMERIC/DECIMAL: V0 commonly `(15,2)`, ledger `(19,2)`. This report does not normalize all precision to one type.
- Appointment has a patient/date PENDING uniqueness guard; medical_record has a unique appointment; prescription_line quantity > 0; stock/price are nonnegative; reservation has prescription/drug uniqueness.
- Billing has unique charge `(source_type, source_id, price_code)` and `payment_transaction.idempotency_key`; FKs/allocations stay within Billing.
- Business timestamps use TIMESTAMPTZ/Instant, dates use DATE/LocalDate. Lab result.value is VARCHAR, preserving textual results and reference_range.
- Outbox commits with state; inbox/receipt commits with effects. Retries preserve eventId/bytes; poison messages reach DLQ after bounded retry; HELD rows are not automatically released.
- Released Flyway migrations are append-only. Indexes and multi-column CHECKs live in the linked DDL, beyond the column summaries above.

### 4.4. Database Diagram

### Database deployment: one development server, eight authorities

**A4 landscape**

![Database deployment: one development server, eight authorities](word-compact/landscape/png/23-en.png)

[Mermaid](word-compact/landscape/mermaid/23-en.mmd) · [SVG](word-compact/landscape/svg/23-en.svg)

[Original SVG](assets/svg/23-en.svg) · [Portrait PNG](word-compact/portrait/png/23-en.png) · [Landscape PNG](word-compact/landscape/png/23-en.png)

## 5. SiteMap

The sitemap is derived from `frontend/src/app/**/page.tsx` and `mobile/lib/app/router.dart`. Route existence does not certify workflow acceptance; dynamic `[id]` segments preserve App Router names. Inpatient routes are retained outside the MVP; no Surgery route is inferred without source.

### Web sitemap by route group

**A4 landscape**

![Web sitemap by route group](word-compact/landscape/png/24-en.png)

[Mermaid](word-compact/landscape/mermaid/24-en.mmd) · [SVG](word-compact/landscape/svg/24-en.svg)

[Original SVG](assets/svg/24-en.svg) · [Portrait PNG](word-compact/portrait/png/24-en.png) · [Landscape PNG](word-compact/landscape/png/24-en.png)

| Web route | Source file | Scope |
|---|---|---|
| `/appointments/[appointmentId]/edit` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/appointments/%5BappointmentId%5D/edit/page.tsx) | SOURCE / subject to gates and policy |
| `/appointments/[appointmentId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/appointments/%5BappointmentId%5D/page.tsx) | SOURCE / subject to gates and policy |
| `/appointments/new` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/appointments/new/page.tsx) | SOURCE / subject to gates and policy |
| `/appointments` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/appointments/page.tsx) | SOURCE / subject to gates and policy |
| `/billing/[invoiceId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/billing/%5BinvoiceId%5D/page.tsx) | SOURCE / subject to gates and policy |
| `/billing/admin/outbox` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/billing/admin/outbox/page.tsx) | SOURCE / subject to gates and policy |
| `/billing/new` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/billing/new/page.tsx) | SOURCE / subject to gates and policy |
| `/billing` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/billing/page.tsx) | SOURCE / subject to gates and policy |
| `/billing/payment-requests/[requestId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/billing/payment-requests/%5BrequestId%5D/page.tsx) | SOURCE / subject to gates and policy |
| `/billing/payment-requests` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/billing/payment-requests/page.tsx) | SOURCE / subject to gates and policy |
| `/billing/revenue` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/billing/revenue/page.tsx) | SOURCE / subject to gates and policy |
| `/inpatient/[admissionId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/inpatient/%5BadmissionId%5D/page.tsx) | DEFERRED |
| `/inpatient/beds` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/inpatient/beds/page.tsx) | DEFERRED |
| `/inpatient/new` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/inpatient/new/page.tsx) | DEFERRED |
| `/inpatient` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/inpatient/page.tsx) | DEFERRED |
| `/lab/[testId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/lab/%5BtestId%5D/page.tsx) | SOURCE / subject to gates and policy |
| `/lab/new` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/lab/new/page.tsx) | SOURCE / subject to gates and policy |
| `/lab` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/lab/page.tsx) | SOURCE / subject to gates and policy |
| `/notifications/[notificationId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/notifications/%5BnotificationId%5D/page.tsx) | SOURCE / subject to gates and policy |
| `/notifications` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/notifications/page.tsx) | SOURCE / subject to gates and policy |
| `/notifications/send` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/notifications/send/page.tsx) | SOURCE / subject to gates and policy |
| `/organization/accounts` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/organization/accounts/page.tsx) | SOURCE / subject to gates and policy |
| `/organization/departments/[departmentId]/edit` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/organization/departments/%5BdepartmentId%5D/edit/page.tsx) | SOURCE / subject to gates and policy |
| `/organization/departments/[departmentId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/organization/departments/%5BdepartmentId%5D/page.tsx) | SOURCE / subject to gates and policy |
| `/organization/departments/new` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/organization/departments/new/page.tsx) | SOURCE / subject to gates and policy |
| `/organization` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/organization/page.tsx) | SOURCE / subject to gates and policy |
| `/organization/staff/[staffId]/edit` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/organization/staff/%5BstaffId%5D/edit/page.tsx) | SOURCE / subject to gates and policy |
| `/organization/staff/[staffId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/organization/staff/%5BstaffId%5D/page.tsx) | SOURCE / subject to gates and policy |
| `/organization/staff/new` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/organization/staff/new/page.tsx) | SOURCE / subject to gates and policy |
| `/patients/[patientId]/edit` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/patients/%5BpatientId%5D/edit/page.tsx) | SOURCE / subject to gates and policy |
| `/patients/[patientId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/patients/%5BpatientId%5D/page.tsx) | SOURCE / subject to gates and policy |
| `/patients/new` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/patients/new/page.tsx) | SOURCE / subject to gates and policy |
| `/patients` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/patients/page.tsx) | SOURCE / subject to gates and policy |
| `/pharmacy/admin/outbox` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/pharmacy/admin/outbox/page.tsx) | SOURCE / subject to gates and policy |
| `/pharmacy/drugs/[drugId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/pharmacy/drugs/%5BdrugId%5D/page.tsx) | SOURCE / subject to gates and policy |
| `/pharmacy/drugs/new` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/pharmacy/drugs/new/page.tsx) | SOURCE / subject to gates and policy |
| `/pharmacy/drugs` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/pharmacy/drugs/page.tsx) | SOURCE / subject to gates and policy |
| `/pharmacy` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/pharmacy/page.tsx) | SOURCE / subject to gates and policy |
| `/pharmacy/prescriptions/[prescriptionId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/pharmacy/prescriptions/%5BprescriptionId%5D/page.tsx) | SOURCE / subject to gates and policy |
| `/pharmacy/prescriptions/new` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/pharmacy/prescriptions/new/page.tsx) | SOURCE / subject to gates and policy |
| `/pharmacy/prescriptions` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/pharmacy/prescriptions/page.tsx) | SOURCE / subject to gates and policy |
| `/records/[recordId]/edit` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/records/%5BrecordId%5D/edit/page.tsx) | SOURCE / subject to gates and policy |
| `/records/[recordId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/records/%5BrecordId%5D/page.tsx) | SOURCE / subject to gates and policy |
| `/records/new` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/records/new/page.tsx) | SOURCE / subject to gates and policy |
| `/records` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/records/page.tsx) | SOURCE / subject to gates and policy |
| `/reports/outpatient` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/reports/outpatient/page.tsx) | SOURCE / subject to gates and policy |
| `/reports` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/reports/page.tsx) | SOURCE / subject to gates and policy |
| `/loading-test` | [page.tsx](../../../../frontend/src/app/loading-test/page.tsx) | UTILITY |
| `/login` | [page.tsx](../../../../frontend/src/app/login/page.tsx) | SOURCE / subject to gates and policy |
| `/` | [page.tsx](../../../../frontend/src/app/page.tsx) | SOURCE / subject to gates and policy |

### Mobile sitemap and implementation level

**A4 portrait**

![Mobile sitemap and implementation level](word-compact/portrait/png/25-en.png)

[Mermaid](word-compact/portrait/mermaid/25-en.mmd) · [SVG](word-compact/portrait/svg/25-en.svg)

[Original SVG](assets/svg/25-en.svg) · [Portrait PNG](word-compact/portrait/png/25-en.png) · [Landscape PNG](word-compact/landscape/png/25-en.png)
Loading/error/empty/retry states must be explicit; 401 requires sign-in and does not automatically resend a mutation with unknown outcome. UI role gating serves UX only. Flutter has real login and read-only Appointment/Lab; remaining workspaces are not presented as completed native functionality.

## 6. Algorithm

### 6.1. Prescription creation and stock reservation

Primary source: `CarePrescriptionCreationService.createInternal`. Prices come from the server catalogue; client prices and invented clinical identity are not accepted.

### V1 context-checked prescription creation

**Part 1/3 · A4 portrait**

![V1 context-checked prescription creation — Part 1/3](word-compact/panels/png/26-en-part-01.png)

[Mermaid](word-compact/panels/mermaid/26-en-part-01.mmd) · [SVG](word-compact/panels/svg/26-en-part-01.svg)

**Part 2/3 · A4 portrait**

![V1 context-checked prescription creation — Part 2/3](word-compact/panels/png/26-en-part-02.png)

[Mermaid](word-compact/panels/mermaid/26-en-part-02.mmd) · [SVG](word-compact/panels/svg/26-en-part-02.svg)

**Part 3/3 · A4 landscape**

![V1 context-checked prescription creation — Part 3/3](word-compact/panels/png/26-en-part-03.png)

[Mermaid](word-compact/panels/mermaid/26-en-part-03.mmd) · [SVG](word-compact/panels/svg/26-en-part-03.svg)

[Original SVG](assets/svg/26-en.svg) · [Portrait PNG](word-compact/portrait/png/26-en.png) · [Landscape PNG](word-compact/landscape/png/26-en.png)
Formulae: `available = drug.stock_quantity − Σ quantity(RESERVED)`; `line_total = quantity × server_unit_price`; `total_amount = Σ line_total`. Reservation does not reduce physical stock. Sorting n drug lines costs O(n log n) CPU; reservation rows read and database lock waits are not bounded by n alone.

### 6.2. Idempotent payment recording

Source: `LedgerPaymentService.complete`. Lock the idempotency key, then account/request; replay compares requestId, actor, amount, currency, method and provider reference. Partial payment does not grant clearance; full payment with a non-SETTLEMENT purpose grants only the exact target.

### Payment and clearance algorithm

**Part 1/3 · A4 portrait**

![Payment and clearance algorithm — Part 1/3](word-compact/panels/png/27-en-part-01.png)

[Mermaid](word-compact/panels/mermaid/27-en-part-01.mmd) · [SVG](word-compact/panels/svg/27-en-part-01.svg)

**Part 2/3 · A4 portrait**

![Payment and clearance algorithm — Part 2/3](word-compact/panels/png/27-en-part-02.png)

[Mermaid](word-compact/panels/mermaid/27-en-part-02.mmd) · [SVG](word-compact/panels/svg/27-en-part-02.svg)

**Part 3/3 · A4 landscape**

![Payment and clearance algorithm — Part 3/3](word-compact/panels/png/27-en-part-03.png)

[Mermaid](word-compact/panels/mermaid/27-en-part-03.mmd) · [SVG](word-compact/panels/svg/27-en-part-03.svg)

[Original SVG](assets/svg/27-en.svg) · [Portrait PNG](word-compact/portrait/png/27-en.png) · [Landscape PNG](word-compact/landscape/png/27-en.png)

### 6.3. Event delivery, deduplication and recovery

### Outbox / inbox recovery algorithm

**A4 portrait**

![Outbox / inbox recovery algorithm](word-compact/portrait/png/28-en.png)

[Mermaid](word-compact/portrait/mermaid/28-en.mmd) · [SVG](word-compact/portrait/svg/28-en.svg)

[Original SVG](assets/svg/28-en.svg) · [Portrait PNG](word-compact/portrait/png/28-en.png) · [Landscape PNG](word-compact/landscape/png/28-en.png)
Publisher confirms do not create exactly-once delivery. A crash after commit but before ACK must return the committed outcome without a second stock/payment effect. A crash before commit rolls back the local effect. HELD/quarantined rows require the relevant policy/gate, not a retry timer.

### 6.4. Coverage-aware reporting

Accept a fact with valid source → verify receipt/fingerprint → update projection in a local transaction → query scope/date/department → verify coverage → return a measurement or unavailable. `cash received − completed cash refunds = net cash` uses covered facts/intervals only; payment is not inferred as earned revenue. Report replay/rebuild does not resend business notifications.

## 7. Source traceability and review limits

| Area | Verification source |
|---|---|
| Topology / scope | [docs/plans/01-system-design.md](../../../plans/01-system-design.md) |
| Business invariants | [docs/plans/02-business-flows.md](../../../plans/02-business-flows.md) |
| Contract modes / wire / RBAC | [docs/plans/03-contracts.md](../../../plans/03-contracts.md) |
| Atomicity / reliability | [docs/plans/04-data-reliability.md](../../../plans/04-data-reliability.md) |
| Acceptance gates | [docs/plans/06-acceptance-runbook.md](../../../plans/06-acceptance-runbook.md) |
| Compose deployment | [docker-compose.yml](../../../../docker-compose.yml) |
| Core host | [backend/core-service/README.md](../../../../backend/core-service/README.md) |
| Support host | [backend/support-service/README.md](../../../../backend/support-service/README.md) |
| Clinical aggregate | [backend/clinical-service/src/main/java/com/mediflow/clinical/domain/model/Appointment.java](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/domain/model/Appointment.java) |
| Medical record | [backend/clinical-service/src/main/java/com/mediflow/clinical/domain/model/MedicalRecord.java](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/domain/model/MedicalRecord.java) |
| Lab aggregate | [backend/lab-service/src/main/java/com/mediflow/lab/domain/model/LabTest.java](../../../../backend/lab-service/src/main/java/com/mediflow/lab/domain/model/LabTest.java) |
| Stock reservation algorithm | [backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/application/service/CarePrescriptionCreationService.java](../../../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/application/service/CarePrescriptionCreationService.java) |
| Payment algorithm | [backend/billing-service/src/main/java/com/mediflow/billing/application/service/LedgerPaymentService.java](../../../../backend/billing-service/src/main/java/com/mediflow/billing/application/service/LedgerPaymentService.java) |
| Frontend route state | [frontend/docs/frontend-workboard.md](../../../../frontend/docs/frontend-workboard.md) |
| Web API boundary | [frontend/src/lib/api.ts](../../../../frontend/src/lib/api.ts) |
| Mobile scope | [mobile/README.md](../../../../mobile/README.md) |
| Mobile routes | [mobile/lib/app/router.dart](../../../../mobile/lib/app/router.dart) |
| Review 1 baseline | [docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/01-review-1-standard-en.md](../../01-giai-doan-1-khoi-dong-dac-ta/review-1/01-review-1-standard-en.md) |

This review reads source/config/migrations and cross-checks the graph index; SQL marked parse_partial is read directly from files. Graph node/edge counts do not prove runtime integration. Acceptance logs have their own dates/profiles; default flags are not enabled for this report. This revision changes documentation and assets only; production behavior and activation flags are unchanged.

Assets: [Mermaid source](assets/mermaid/), [SVG](assets/svg/), [logos](assets/logos/), [theme configuration](assets/mermaid-config.json). Logos identify their respective projects, sourced from [Devicon](https://github.com/devicons/devicon/tree/v2.17.0); the stick-figure actor is an SVG authored for this report.

<!-- supervisor-evidence:start -->
### Additional source evidence

Supervisor revision: figures 04/05 replace the former overviews with independent refresh/booking sequences. Use-case overviews are replaced by the eight role diagrams 29–36. Figures 37–41 add outcome, prescribing and service-grouped class models. Readability panels are ordered extracts of a complete figure: they retain guards, and message numbering continues across sequence panels.

| Concern | Source |
|---|---|
| Sign-in / refresh | [AuthController.java](../../../../backend/gateway/src/main/java/com/mediflow/gateway/auth/AuthController.java) |
| Check-in / fee authority / exam start | [ClinicalCareFinanceApplicationService.java](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/ClinicalCareFinanceApplicationService.java) |
| Persisted laboratory indication | [ClinicalLabOrder.java](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/domain/model/ClinicalLabOrder.java) |
| Lab authorization / result commands | [LabController.java](../../../../backend/lab-service/src/main/java/com/mediflow/lab/web/LabController.java) |
| Explicit V1 dispense transaction | [CareDispenseTransactionService.java](../../../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/application/service/CareDispenseTransactionService.java) |
| Web hosting / gateway rewrite | [next.config.ts](../../../../frontend/next.config.ts) |
<!-- supervisor-evidence:end -->
