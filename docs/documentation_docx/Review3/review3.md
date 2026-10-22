# MediFlow — Review 3

**Document type: report template with pre-filled remarks.** All `PASS` values below are entered at the author's request, including items not yet implemented or verified. They are placeholders, not evidence of successful execution or a completed acceptance sign-off. The descriptions state the intended checks, not observed results. Actual results remain in the project test reports, runtime evidence and handoffs.

**Scope:** outpatient care management, covering Organization, Patient, Clinical, Laboratory, Pharmacy, Billing, Report and Notification. Inpatient admission, bed management and surgery are deferred. Web GUI and Flutter mobile GUI are included; Flutter web screenshots do not establish native Android/iOS execution.

## Testing document

| No. | Feature Tested | Remarks |
|---|---|---|
| 1 | Authentication — sign in with a valid username and password. | PASS |
| 2 | Authentication — reject invalid credentials and validate required login fields. | PASS |
| 3 | Authentication — reject locked or inactive accounts. | PASS |
| 4 | Session management — sign out, clear the session and prevent access to protected pages after logout. | PASS |
| 5 | Session management — handle expired or invalid tokens and require authentication again. | PASS |
| 6 | Authorization — enforce access for Administrator, Manager, Doctor, Nurse, Laboratory Technician, Pharmacist, Cashier and Patient. | PASS |
| 7 | Authorization — reject direct access to restricted routes and API operations, including manually entered URLs. | PASS |
| 8 | Authorization — prevent a patient from reading another patient's protected information. | PASS |
| 9 | Account management — create a staff-linked account and assign a permitted role. | PASS |
| 10 | Account management — reject duplicate usernames and invalid identity references. | PASS |
| 11 | Account management — update account status and verify the effect of role or account-status changes on authorization. | PASS |
| 12 | Department management — list, search and view department details. | PASS |
| 13 | Department management — create or update department information with required-field validation. | PASS |
| 14 | Department management — validate department type, active status and department-head eligibility. | PASS |
| 15 | Staff management — list, search and view staff details. | PASS |
| 16 | Staff management — create or update staff information, including identity number, contact information and professional fields. | PASS |
| 17 | Staff management — validate duplicate identity information and incompatible job titles or assignments. | PASS |
| 18 | Staff management — transfer staff between departments while preserving a valid current assignment. | PASS |
| 19 | Patient management — list, search, paginate and open patient records. | PASS |
| 20 | Patient management — register a patient using valid demographic and contact information. | PASS |
| 21 | Patient management — update patient information and reload the persisted values. | PASS |
| 22 | Patient management — validate required fields, dates and identifiers, and handle duplicate identities according to the contract. | PASS |
| 23 | Appointment management — list appointments and filter by supported date, status or department criteria. | PASS |
| 24 | Appointment management — create an appointment with valid patient, doctor, department, date and time references. | PASS |
| 25 | Appointment management — reject invalid references, invalid scheduling data and prohibited scheduling conflicts. | PASS |
| 26 | Appointment management — view appointment details and its current care lifecycle. | PASS |
| 27 | Appointment management — update or cancel an appointment only when its current state permits the operation. | PASS |
| 28 | Reception — check in a patient and display the updated appointment state. | PASS |
| 29 | Examination payment gate — prevent examination from starting before the exact examination charge is cleared. | PASS |
| 30 | Examination payment gate — allow examination only after current Billing authorization for the correct patient and care episode. | PASS |
| 31 | Examination — start an examination and create the associated medical record atomically. | PASS |
| 32 | Examination — handle repeated or concurrent start requests without creating duplicate medical records. | PASS |
| 33 | Medical records — list or search supported records and open the correct patient's record. | PASS |
| 34 | Medical records — record clinical information, vital signs and diagnosis using the supported fields. | PASS |
| 35 | Medical records — preserve changes after reload and reject invalid updates or unauthorized modifications. | PASS |
| 36 | Medical records — complete an outpatient examination only when its business prerequisites are satisfied. | PASS |
| 37 | Laboratory ordering — select a supported laboratory test and create an order from a valid clinical record. | PASS |
| 38 | Laboratory ordering — preserve source order, patient, requesting department and care-episode references. | PASS |
| 39 | Laboratory queue — list, paginate, filter and open laboratory requests. | PASS |
| 40 | Laboratory payment gate — create one charge for a laboratory request despite duplicate event delivery. | PASS |
| 41 | Laboratory payment gate — reject execution without valid, current authorization for the exact test. | PASS |
| 42 | Laboratory execution — start an eligible paid test and persist the execution state. | PASS |
| 43 | Laboratory results — enter indicators, values, units, reference ranges and a conclusion. | PASS |
| 44 | Laboratory results — reject invalid result data and prohibited changes to terminal results. | PASS |
| 45 | Laboratory results — publish completed results and expose them to the authorized clinical workflow. | PASS |
| 46 | Laboratory cancellation — apply permitted cancellation without reopening the source or duplicating financial adjustments. | PASS |
| 47 | Drug catalogue — list, search, paginate and view drug details. | PASS |
| 48 | Drug catalogue — validate supported drug information and active availability for prescribing. | PASS |
| 49 | Inventory — record a supported stock increase and reload the resulting stock balance. | PASS |
| 50 | Inventory — reject invalid quantities and prevent negative available stock. | PASS |
| 51 | Prescription management — create a prescription linked to the correct patient, clinical record and care episode. | PASS |
| 52 | Prescription management — validate drug selection, quantity, dosage, frequency and usage instructions. | PASS |
| 53 | Prescription pricing — use server-authoritative prices and retain the accepted price snapshot. | PASS |
| 54 | Prescription reservation — handle insufficient stock for a multi-drug prescription without partial reservation effects. | PASS |
| 55 | Prescription lookup — find a prescription by its supported identifier and display its lines and lifecycle state. | PASS |
| 56 | Prescription payment gate — require current authorization for the exact prescription before dispensing. | PASS |
| 57 | Dispensing — confirm an eligible prescription and record the fulfilled state and stock movement. | PASS |
| 58 | Dispensing — handle repeated commands or duplicate payment events without double dispensing or double stock deduction. | PASS |
| 59 | Prescription lifecycle — handle cancellation, expiry and late payment without reopening a terminal prescription. | PASS |
| 60 | Billing discovery — locate examination, laboratory and prescription payment requests using the correct source and care-episode references. | PASS |
| 61 | Billing calculation — display requested, paid and remaining amounts with the correct currency and decimal handling. | PASS |
| 62 | Payment collection — record a valid supported payment and return the payment receipt. | PASS |
| 63 | Payment collection — reject zero, negative, excessive or otherwise invalid payment amounts according to the contract. | PASS |
| 64 | Payment idempotency — repeat the same payment intent without creating an additional financial effect. | PASS |
| 65 | Billing refresh — reload the authoritative paid status and remaining amount after a successful payment. | PASS |
| 66 | Billing separation — distinguish legacy invoices from outpatient payment requests and ledger records. | PASS |
| 67 | Billing allocation — prevent over-allocation and allocation to the wrong patient or care account. | PASS |
| 68 | Adjustments — apply a permitted cancellation or prescription adjustment with its original source and audit references. | PASS |
| 69 | Refunds — calculate refundable amounts, record an authorized refund and prevent over-refunding or duplicate refund effects. | PASS |
| 70 | Refund and execution concurrency — serialize examination or laboratory start with refund and cancellation decisions. | PASS |
| 71 | Financial audit — preserve original payment links, histories and accepted writes through retries. | PASS |
| 72 | Reports — open the supported report workspace and apply supported reporting-period and department filters. | PASS |
| 73 | Reports — distinguish cash collection, earned revenue, adjustments and refunds according to the report contract. | PASS |
| 74 | Reports — process repeated events without counting the same business contribution twice. | PASS |
| 75 | Reports — distinguish an unavailable or uncovered report from a verified zero-value report. | PASS |
| 76 | Reports — retain stable acceptance ordering and detect conflicting event content during replay. | PASS |
| 77 | Notifications — list and open in-app notifications for the authenticated recipient. | PASS |
| 78 | Notifications — display appointment reminders and supported care-related notification details. | PASS |
| 79 | Notifications — prevent cross-recipient access and duplicate delivery effects during event replay. | PASS |
| 80 | Identity presentation — display the correct patient, doctor and department names instead of using UUID as the only visible label. | PASS |
| 81 | Identity presentation — retain the original identifiers in commands and show an honest fallback when a permitted name lookup fails. | PASS |
| 82 | Web GUI — display shared landing, login, navigation and access-denied pages. | PASS |
| 83 | Web GUI — provide role-appropriate menus, page links and visible actions. | PASS |
| 84 | Web GUI — handle loading, empty, validation, not-found and request-error states with supported retry behavior. | PASS |
| 85 | Web GUI — maintain readable forms, lists and dialogs at the supported desktop and narrow-screen sizes. | PASS |
| 86 | Web GUI — preserve readable text, contrast, keyboard access and labels in supported themes. | PASS |
| 87 | Mobile GUI — authenticate, retain the permitted session and sign out through the configured Gateway. | PASS |
| 88 | Mobile GUI — display the navigation and workspace appropriate to each of the eight roles. | PASS |
| 89 | Mobile GUI — browse supported department, staff and account screens. | PASS |
| 90 | Mobile GUI — browse, search and view patients and use supported patient forms. | PASS |
| 91 | Mobile GUI — browse appointments, change the supported date filter and open the available appointment workflow. | PASS |
| 92 | Mobile GUI — browse laboratory requests and filter by the supported lifecycle status. | PASS |
| 93 | Mobile GUI — integrate supported clinical-record, pharmacy and report workspaces with their actual repositories. | PASS |
| 94 | Mobile GUI — discover a payment request, submit a valid payment and refresh its authoritative balance. | PASS |
| 95 | Mobile GUI — list and open the authenticated patient's notifications. | PASS |
| 96 | Mobile GUI — handle unavailable workspaces, connection failures and small-screen scrolling without presenting unfinished features as completed. | PASS |
| 97 | Session isolation — discard stale responses and cached identity information after a record change, logout or account switch. | PASS |
| 98 | Gateway integration — send web and mobile business requests through the Gateway with the required authentication. | PASS |
| 99 | Gateway security — reject unsupported nested routes, wrong-role requests and invalid token types. | PASS |
| 100 | Integration — propagate examination and laboratory charge requests and prescription payment events between the intended contexts. | PASS |
| 101 | Event reliability — use durable outbox/inbox processing and preserve event identity during retry and redelivery. | PASS |
| 102 | Event reliability — handle poison messages, bounded retry, dead-letter routing and unroutable publication. | PASS |
| 103 | Recovery — resume pending pharmacy work after service interruption without losing or duplicating its business effect. | PASS |
| 104 | Recovery — preserve accepted records and financial effects across database, broker and application restart. | PASS |
| 105 | Transaction integrity — roll back failed multi-step operations without leaving partial clinical, stock or financial state. | PASS |
| 106 | Core reliability — handle repeated staff-creation requests and event publication failures without duplicate identities. | PASS |
| 107 | Deployment — start Core, Clinical, Lab, Pharmacy, Billing and Support with Gateway and Eureka. | PASS |
| 108 | Data boundaries — retain separate context-owned stores and use supported API/event contracts for cross-context data. | PASS |
| 109 | Backup and rollback — validate restoration or current-store rollback while retaining accepted writes and audit history. | PASS |
| 110 | Scope control — keep deferred inpatient and surgery routes, jobs and business actions outside the outpatient MVP acceptance scope. | PASS |

## Final checklist

The second-column title follows the supplied Word template. The entries cover the project aspects to review across requirements, design, implementation and Review 3 evidence; they do not assert that every aspect has already been tested.

| No. | Review 01 aspect tested | Suggestion / Remarks |
|---|---|---|
| 1 | Project title and problem statement describe outpatient care management consistently. | PASS |
| 2 | Project objectives identify the intended users and the operational problems addressed by MediFlow. | PASS |
| 3 | Project scope includes the eight active business contexts and identifies inpatient care and surgery as deferred. | PASS |
| 4 | Functional requirements cover the responsibilities and access boundaries of all eight user roles. | PASS |
| 5 | Non-functional requirements address security, reliability, usability, maintainability and operational recovery. | PASS |
| 6 | Use-case diagrams and descriptions match the active role workflows and business scope. | PASS |
| 7 | The architecture distinguishes six business deployments, eight business contexts, infrastructure and shared libraries. | PASS |
| 8 | Service boundaries, responsibilities and dependencies are consistent across the architecture and implementation documents. | PASS |
| 9 | Sequence diagrams describe appointment, examination, laboratory, prescription, payment and dispensing interactions. | PASS |
| 10 | Class and component diagrams reflect the intended responsibilities and dependency direction. | PASS |
| 11 | Data-flow diagrams describe the main clinical, stock, financial, reporting and notification information flows. | PASS |
| 12 | Entity relationship diagrams and table descriptions match context-owned persistence boundaries. | PASS |
| 13 | Database identifiers, field types, constraints and migrations are documented consistently. | PASS |
| 14 | No cross-context database relationship or direct database lookup is introduced to replace a public service contract. | PASS |
| 15 | API contracts define supported request fields, response fields, validation rules and stable errors. | PASS |
| 16 | Gateway routing, authentication, session handling and role authorization match the intended access model. | PASS |
| 17 | Account, department, staff and patient management cover the required outpatient identity information. | PASS |
| 18 | Appointment booking, reception, examination eligibility and clinical-record creation form a consistent care lifecycle. | PASS |
| 19 | Laboratory ordering, payment authorization, execution, results and cancellation follow the documented lifecycle. | PASS |
| 20 | Prescription pricing, reservation, payment authorization and dispensing preserve stock and financial consistency. | PASS |
| 21 | Billing handles charge discovery, payments, balances, adjustments and refunds with the required audit links. | PASS |
| 22 | Reports distinguish covered data, unavailable data, cash collection and earned activity according to the contract. | PASS |
| 23 | In-app notifications are addressed to the correct recipients and preserve recipient-only access. | PASS |
| 24 | Idempotency, concurrency control and transaction boundaries prevent duplicate or partial business effects. | PASS |
| 25 | Event publication, delivery, replay and failure handling preserve durable business state. | PASS |
| 26 | Web navigation and visible actions are appropriate for Administrator, Manager, Doctor, Nurse, Laboratory Technician, Pharmacist, Cashier and Patient. | PASS |
| 27 | Shared web pages, forms, validation feedback and error states use consistent presentation. | PASS |
| 28 | Patient, doctor and department references display readable names where lookup is available and authorized. | PASS |
| 29 | Web GUI layouts remain readable at supported viewport sizes and in supported themes. | PASS |
| 30 | Mobile architecture, Gateway access, session handling and navigation follow the Flutter project rules. | PASS |
| 31 | Mobile role workspaces are evaluated individually, including supported screens and unfinished or unavailable areas. | PASS |
| 32 | Mobile forms, lists, filters, details and feedback are usable at a phone-sized viewport. | PASS |
| 33 | Native Android/iOS execution evidence is identified separately from Flutter web evidence. | PASS |
| 34 | Demonstration data includes linked accounts, staff, departments, patients, appointments, clinical records, tests, prescriptions and charges. | PASS |
| 35 | Seed data is confined to the isolated demo environment and does not fabricate medical results or financial authorization. | PASS |
| 36 | Backend, web and mobile verification records identify the tested revision, commands, passed cases, failures and skips. | PASS |
| 37 | Integration and recovery evidence uses the required real database and broker rather than relying only on mocked tests. | PASS |
| 38 | Screenshots show common pages, role-specific pages and representative interactions for web and mobile. | PASS |
| 39 | Screenshot captions, numbering and references match the corresponding role, platform and observed runtime state. | PASS |
| 40 | Known issues and unfinished features are recorded in handoffs with owners and observable acceptance criteria. | PASS |
| 41 | The Testing document covers valid, invalid, unauthorized, duplicate, concurrent and failure scenarios. | PASS |
| 42 | The final checklist aligns the report with the approved MVP instead of claiming completion of deferred features. | PASS |
| 43 | Build, configuration and startup instructions describe the environment needed to reproduce the demonstration. | PASS |
| 44 | Database persistence, backup and restart instructions allow the demo data to be retained for later review. | PASS |
| 45 | The final report uses consistent terminology, readable figures, section titles and page references. | PASS |
| 46 | Personal credentials, access tokens and private runtime manifests are excluded from shared report assets and source control. | PASS |
| 47 | Requirements, implementation, test evidence and screenshots can be traced to the same supported workflow. | PASS |
| 48 | Actual acceptance decisions are reconciled against evidence before replacing this pre-filled template with a signed final result. | PASS |

## Evidence reconciliation before final submission

1. The Review 3 capture inventory currently contains 79 images: 51 web and 28 Flutter web images.
2. The captured runtime included a laboratory start rejection (`LAB_CLEARANCE_REQUIRED`); the completed laboratory workflow was not demonstrated by those screenshots.
3. The captured outpatient report lookup returned `REPORT_NOT_FOUND`; this does not establish a successful populated report.
4. Some mobile workspaces were placeholders or unavailable. Flutter web does not establish native Android/iOS acceptance.
5. Some patient, doctor and department references still displayed UUIDs. The display-name correction is recorded in `frontend/docs/handoffs/HANDOFF-IDENTITY-CONSUMERS-DISPLAY-NAMES-REVIEW3.md`.
6. The web payment screenshot showed a successful receipt while its summary still displayed pre-payment values; the payment was checked separately through Billing.
7. These observations remain open or limited regardless of the pre-filled `PASS` text. The template does not supersede the actual test logs, current owner handoffs or acceptance runbook.
