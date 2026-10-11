# MediFlow Review 1 — Standard Report (English)

| Document item | Value |
|---|---|
| Project | MediFlow Hospital Management System |
| Review | Review 1 — Problem Definition and Customer Requirements Specification |
| Version | Standard |
| Language | English |
| Source baseline | Repository commit `1cc014e` source code and configuration |
| Intended use | Academic review, Word conversion, and project evaluation |

## Part A — Problem Definition

### A1. Project Introduction and Context

MediFlow is a hospital management platform designed to connect the main administrative, clinical, diagnostic, pharmaceutical, financial, and operational activities of a healthcare facility. The project addresses a common problem in hospitals and clinics: important information is often distributed across paper forms, independent spreadsheets, departmental applications, and informal communication channels. This fragmentation makes a patient's journey difficult to follow and creates repeated data entry, delayed decisions, inconsistent records, and weak accountability.

The current source code implements a web-oriented system built around independently deployable backend services. Staff members use a Next.js web client through a central API gateway. Each business service owns its data and communicates with other services through controlled REST calls or RabbitMQ events. PostgreSQL provides isolated persistence for each service. Eureka supports service discovery, and an outbox-based event flow improves delivery reliability.

MediFlow covers ten implemented business contexts:

1. Organization
2. Patient
3. Clinical
4. Laboratory
5. Pharmacy
6. Billing
7. Notification
8. Report
9. Inpatient care
10. Surgery

The solution is intended for nine system roles: `ADMIN`, `DOCTOR`, `NURSE`, `PHARMACIST`, `CASHIER`, `LAB_TECH`, `MANAGER`, `PATIENT`, and `SYSTEM`. These roles represent operational users as well as automated background processing.

### A2. Problem Identification

The project focuses on the following operational problems.

| Problem | Existing operational impact | Required system response |
|---|---|---|
| Fragmented patient information | Staff cannot obtain a complete and timely view of a patient | Maintain a consistent patient identity and connect authorized records by UUID |
| Manual appointment coordination | Double booking, missed appointments, and slow check-in can occur | Provide appointment registration, status tracking, and encounter handoff |
| Disconnected clinical documentation | Diagnoses, observations, and orders may not reach the next department reliably | Record clinical encounters and publish controlled downstream requests |
| Slow laboratory turnaround visibility | Clinicians may not know whether a test is ordered, collected, processed, or completed | Track the laboratory order lifecycle and publish verified results |
| Prescription and stock separation | Medicines may be prescribed without clear dispensing or inventory status | Authorize prescriptions, validate stock, dispense medicine, and record movements |
| Inconsistent financial tracking | Charges, invoices, payments, deposits, receipts, and refunds may be reconciled manually | Centralize financial records in the Billing context and expose auditable status |
| Weak inpatient coordination | Admission, financial clearance, bed allocation, care stay, and discharge may be tracked separately | Provide one stateful inpatient workflow with explicit transitions |
| Weak surgery coordination | Readiness, clearance, scheduling, execution, and outcomes may be communicated informally | Provide a traceable surgery lifecycle with authorization checkpoints |
| Delayed communication | Patients and staff may not receive important status changes promptly | Generate notifications from domain events and retain delivery status |
| Limited management visibility | Managers may depend on manually assembled reports | Build reporting read models from trusted operational events |
| Insufficient access control | Sensitive medical and financial data may be exposed to unauthorized users | Enforce role-based access at every backend endpoint |
| Poor auditability | It may be difficult to determine who changed a record and when | Retain identifiers, timestamps, statuses, and event histories suitable for audit |

### A3. Project Objectives

The primary objective is to provide a coherent, secure, and traceable digital workflow for a patient's journey across hospital departments.

Specific objectives are:

- Establish traceable local workflows from registration to care, diagnostics, medication, billing, inpatient care, notification, and reporting, while documenting gates that prevent unapproved end-to-end activation.
- Give each bounded context clear ownership of its data and business rules.
- Reduce duplicate entry by exchanging identifiers and authorized information through explicit contracts.
- Enforce role-based authorization at the gateway-facing backend endpoints.
- Record state changes and publish domain events for dependent workflows.
- Support financial accountability through charges, invoices, deposits, payments, receipts, and refunds.
- Improve clinical coordination through appointment, encounter, laboratory, prescription, and locally implemented inpatient workflows; keep unfinished Surgery and care-finance integrations explicit.
- Provide reliable background processing through outbox publication, idempotent consumers, retry handling, and dead-letter queues.
- Supply managers with consolidated operational information without allowing reporting logic to modify source-of-truth records.
- Maintain a modular architecture that can be developed, tested, and deployed by bounded context.

### A4. Project Scope

#### A4.1 Included scope

| Scope area | Included capabilities |
|---|---|
| Organization | Users, staff, departments, roles, facilities, rooms, and organization reference data |
| Patient | Patient profiles, identifiers, contact information, and demographic records |
| Clinical | Appointments, encounters, observations, diagnoses, clinical notes, laboratory requests, and prescriptions |
| Laboratory | Test catalogue, laboratory orders, specimens, processing states, results, and result publication |
| Pharmacy | Medicine catalogue, inventory, stock movement, prescription validation, and dispensing |
| Billing | Billable charges, invoices, deposits, payments, receipts, refunds, and financial clearance |
| Notification | Event-driven notification requests, templates, delivery attempts, and delivery status |
| Report | Event-fed read models and management-oriented operational reports |
| Inpatient | Locally complete Core V1 for admissions, bed ownership/allocation, stays, transfers, treatment tracking, and discharge; cross-service event activation remains gated |
| Surgery | Feature-gated foundation and selected local routes; business, creation, pre-op, and lifecycle APIs default to disabled, and the complete end-to-end lifecycle is not yet active |
| Shared platform | API gateway, service discovery, per-service databases, messaging, security conventions, and shared contracts |
| Web client | Role-aware pages that call the platform through same-origin `/api/*` routes |

#### A4.2 Excluded or not yet proven complete

- Production integration with national identity, health insurance, banking, payment gateways, or government systems.
- Certified interoperability with external laboratories, medical devices, PACS, or third-party electronic health records.
- Production delivery contracts with SMS, email, or push-notification vendors.
- A complete runnable Flutter mobile application; the repository currently contains only a shared loader package.
- Clinical decision support, automated diagnosis, prescription recommendation, or AI-assisted treatment decisions.
- A finalized production service-level objective, retention policy, disaster-recovery target, or clinical governance process.
- Formal regulatory certification, accessibility certification, penetration-test certification, or medical-device certification.

### A5. Users and Functions by Role

| Role | Main responsibilities | Representative system functions |
|---|---|---|
| `ADMIN` | Configure and govern the platform | Manage users, roles, departments, facilities, organization reference data, and permitted Inpatient bed records; review administrative status |
| `DOCTOR` | Diagnose and direct care | Review patient information; update encounters; enter observations/diagnoses; request tests; prescribe; create permitted admission requests; prepare future Surgery context without implying an active referral lifecycle |
| `NURSE` | Coordinate and document patient care | Create permitted patient/appointment records; support check-in; record observations; assist encounters; perform permitted inpatient care and bed operations |
| `PHARMACIST` | Validate and dispense medication | Review authorized prescriptions; verify medicines and quantities; manage stock; record dispensing and inventory movement |
| `CASHIER` | Process patient financial transactions | Review charges and invoices; receive deposits and payments; issue receipts; process authorized refunds; confirm financial status |
| `LAB_TECH` | Perform diagnostic testing | Accept laboratory orders; manage specimens; record processing states and results; verify and publish results |
| `MANAGER` | Monitor hospital operations | View operational and financial reports, service activity, workloads, trends, and management indicators |
| `PATIENT` | Access the currently exposed patient channel | Read owned notifications through the implemented authorization boundary; self-service registration, appointments, clinical results, prescriptions, and financial records remain future-facing requirements |
| `SYSTEM` | Execute trusted automation | Publish and consume events; retry failed delivery; update read models; initiate notifications; preserve idempotency and traceability |

### A6. Proposed Solution

MediFlow proposes a modular hospital platform in which each business capability is implemented as an independent Spring Boot service. The services are accessed through one gateway and registered through Eureka. Each service owns a separate PostgreSQL database, preventing direct cross-service database access. When one service requires authoritative information from another, it uses a defined REST contract. When a completed state change must inform multiple downstream consumers, it publishes an event through RabbitMQ.

| Solution element | Purpose | Expected benefit |
|---|---|---|
| Next.js web client | Provide role-aware workflows in a consistent user interface | Faster staff access and fewer disconnected screens |
| API gateway | Expose one controlled entry point | Consistent routing, authentication context, and reduced client coupling |
| Independent services | Separate business ownership and deployment | Clear boundaries, safer change, and focused testing |
| Isolated PostgreSQL databases | Keep each service authoritative for its records | Reduced accidental coupling and clearer accountability |
| REST contracts | Resolve information required immediately for a command | Timely validation using the owning service |
| RabbitMQ events | Propagate completed business facts | Decoupled notifications, reporting, and cross-department reactions |
| Transactional outbox | Coordinate database changes with event publication | Lower risk of losing events after a committed state change |
| Idempotent consumers | Prevent repeated delivery from creating repeated business effects | Safer retries and recovery |
| Role-based authorization | Restrict every protected operation | Better privacy and separation of duties |
| Automated tests | Verify rules, contracts, persistence, and integrations | Repeatable evidence that implemented behavior remains stable |

### A7. Current Limitations and Unimplemented Areas

| Limitation | Current position | Project consequence |
|---|---|---|
| Mobile application | Only a reusable Flutter loader package is present; no complete root mobile app is available | Mobile workflows cannot yet be accepted as a full delivery channel |
| Patient self-service | Current `PATIENT` authorization is limited to owned notification reads | Registration, appointment, clinical-result, prescription, invoice, receipt, and payment self-service require future authorized endpoints |
| Surgery activation | Surgery contains a feature-gated foundation and selected local routes, while business and lifecycle flags default to off | A complete request-to-outcome workflow cannot be accepted as active system behavior |
| Inpatient integration | Inpatient Core V1 APIs are local and the public route is available, but RabbitMQ producers/consumers default to off | Admission, finance, Surgery, Pharmacy, Report, and Notification event chains remain gated pending contract acceptance |
| External insurance | No production insurer contract is proven complete | Eligibility, claim submission, and reimbursement remain outside demonstrated scope |
| External payment systems | Internal financial workflow exists, but bank/payment-gateway integration is not proven complete | Production electronic payment requires additional integration and compliance work |
| National identity | No certified national identity integration is demonstrated | Patient identity still depends on project-level registration data |
| External laboratory/device integration | Internal laboratory lifecycle exists, but device and external-lab interfaces are not proven | Automated instrument ingestion and cross-provider exchange need future work |
| Vendor notification delivery | Notification workflow exists, but production SMS/email/push vendor agreements are not established | Delivery performance depends on future provider integration |
| Production capacity evidence | Docker configuration supports development and demonstration, not a measured capacity guarantee | Load testing and sizing must precede production deployment |
| Recovery objectives | RTO, RPO, backup retention, and multi-site recovery are not finalized | Operational continuity targets require stakeholder approval |
| Accessibility | No formal accessibility conformance target has been accepted | Additional assessment and remediation may be required |
| Clinical governance | Software workflows exist, but institutional medical governance is external to the repository | Hospital policies, approvals, and clinical accountability must be defined by the deploying organization |
| Regulatory certification | No regulatory certification is claimed | Legal and regulatory review remains mandatory before live clinical use |

## Part B — Customer Requirements Specification (CRS)

### B1. Stakeholders and Customer Requirements

| Stakeholder | Core requirement | Success indicator |
|---|---|---|
| Hospital administration | Govern users, departments, facilities, and access | Authorized configuration is available and changes are traceable |
| Clinical staff | See relevant patient information and document care efficiently | Encounters, orders, diagnoses, and care states are available to permitted roles |
| Laboratory staff | Receive complete orders and publish verified results | Each order has an identifiable lifecycle from request to published result |
| Pharmacy staff | Dispense only valid prescriptions with sufficient stock | Dispensing creates auditable medication and inventory records |
| Finance staff | Reconcile all patient financial activity | Charges, invoices, deposits, payments, receipts, and refunds have consistent states |
| Inpatient staff | Coordinate admission, clearance, bed use, stay, and discharge | No admission proceeds without required state and resource checks |
| Surgery team | Prepare for readiness, clearance, scheduling, execution, and outcome workflows | Only feature-gated foundation/local route behavior is currently demonstrable; the full lifecycle remains future acceptance work |
| Managers | Obtain timely operational and financial insight | Reports use trusted source events and do not modify operational data |
| Patients | Access permitted personal information | The current implemented `PATIENT` boundary exposes owned notification reads; broader self-service is a stated future requirement |
| Technical operations | Operate services reliably and recover from transient failures | Health, logs, retries, dead-letter handling, and database isolation are observable |

### B2. Data-Processing Cycle by User Role

| Workflow | Actor | Input information | Processing | Output information | Owning service |
|---|---|---|---|---|---|
| User and role administration | `ADMIN` | User identity, staff details, assigned roles, department | Validate uniqueness and permissions; create or update organization records | User/staff record, role assignment, audit timestamps | Organization |
| Organization setup | `ADMIN` | Facility, department, room, and organization attributes | Validate hierarchy and persist organization reference data | Available organization and room references | Organization |
| Bed catalogue setup | `ADMIN`, `MANAGER` | Department/room references and bed attributes | Validate references and bed state; persist the Inpatient-owned resource | Bed catalogue and availability state | Inpatient |
| Patient registration | `ADMIN`, `NURSE` | Demographics, contact data, identifiers | Validate required fields and duplicate constraints; assign UUID | Patient profile and registration confirmation | Patient |
| Appointment scheduling | `ADMIN`, `NURSE` | Patient UUID, department/provider, requested time, reason | Validate request and scheduling rules; create appointment state | Appointment identifier, schedule, and status | Clinical |
| Clinical encounter | `DOCTOR`, `NURSE` | Patient UUID, appointment/reference, symptoms, observations | Open encounter; document assessment; validate state transitions | Encounter record, observations, diagnoses, notes | Clinical |
| Laboratory request authoring | `DOCTOR` | Encounter UUID, patient UUID, requested tests, clinical notes | Validate encounter and request; create the clinical request | Clinical laboratory-request reference | Clinical |
| Laboratory order acceptance | `LAB_TECH` | Clinical request reference and requested tests | Validate the inbound request and create the laboratory-owned lifecycle | Laboratory order reference and initial status | Laboratory |
| Specimen and test processing | `LAB_TECH` | Laboratory order, specimen information, measurements | Accept order; record collection and processing; validate result fields | Specimen status, test results, processing history | Laboratory |
| Result verification and publication | `LAB_TECH` | Completed test values, ranges, comments | Verify authority and order status; publish final result | Published laboratory result and result event | Laboratory |
| Prescription creation | `DOCTOR` | Encounter, patient, medicine identifiers, dose, quantity, instructions | Validate encounter and prescription data; authorize clinical instruction | Prescription and medication lines | Clinical |
| Medicine dispensing | `PHARMACIST` | Authorized prescription, stock levels, dispense quantity | Verify prescription and stock; record dispense; reduce inventory | Dispense record, stock movement, remaining quantity | Pharmacy |
| Charge and invoice handling | `CASHIER`, `SYSTEM` | Billable service event, patient UUID, amounts, invoice context | Create idempotent charge; group or update financial obligation | Charge and invoice with financial status | Billing |
| Deposit and payment | `CASHIER` | Invoice/admission reference, payer-provided amount and payment method | Validate amount and state; record transaction; update balance | Payment/deposit record, updated balance, receipt | Billing |
| Refund | `CASHIER` | Original transaction, reason, approved refund amount | Check refundable balance and authorization; record reversal | Refund record and adjusted financial position | Billing |
| Inpatient admission creation | `ADMIN`, `DOCTOR` | Patient, clinical need, and admission details | Validate references and create the local Core V1 admission | Pending admission | Inpatient |
| Bed assignment and transfer | `ADMIN`, `NURSE` | Admission and requested/target bed | Validate availability and perform the bed transition | Updated admission and bed occupancy | Inpatient |
| Inpatient treatment | `ADMIN`, `DOCTOR`, `NURSE` | Active admission and treatment/observation information | Append or correct permitted treatment entries | Updated treatment history | Inpatient |
| Medical discharge | `ADMIN`, `DOCTOR` | Active admission and clinical discharge information | Apply medical-discharge transition | Medically discharged admission | Inpatient |
| Administrative close | `ADMIN`, `CASHIER` | Medically discharged admission and local close information | Apply the permitted administrative close | Closed admission and released resources | Inpatient |
| Surgery local foundation | Authorized staff under disabled feature flags | Case/pre-op/schedule inputs for selected implemented routes | Execute only when the required feature gates and authority provider are enabled | Local case/pre-op/draft-schedule result; no complete outcome event | Surgery |
| Notification delivery | `SYSTEM` | Domain event, recipient reference, template data, channel | Select template; create delivery request; attempt and track delivery | Notification content, delivery status, failure details | Notification |
| Management reporting | `MANAGER`, `SYSTEM` | Trusted domain events and report criteria | Update read models; aggregate permitted measures; filter by criteria | Operational and financial report output | Report |

### B3. Cross-Department Information Flows

| Flow | Trigger | Main processing path | Result |
|---|---|---|---|
| Registration to appointment | Authorized staff register a patient and schedule care | Patient stores identity; Clinical owns and stores the appointment using the patient UUID | Confirmed appointment associated with the correct patient UUID |
| Appointment to encounter | Appointment reaches check-in/consultation state | Clinical validates the patient/appointment reference and opens an encounter | Traceable clinical encounter |
| Encounter to laboratory | Doctor orders one or more tests | Clinical records the order; Laboratory accepts and processes it | Published result available to permitted clinical users |
| Laboratory to billing | A billable test reaches the contractually defined state | Laboratory event is consumed idempotently by Billing | One charge for the eligible laboratory activity |
| Prescription to dispensing | Doctor authorizes prescription | Pharmacy validates prescription and stock, then records dispense | Medication supplied with inventory movement history |
| Care to finance | A billable care activity is completed | Owning service publishes event; Billing creates/reconciles charge and invoice | Updated patient financial obligation |
| Admission to active stay | Local admission and bed prerequisites are satisfied | Inpatient Core V1 verifies the local state and allocates an available bed; event-driven financial activation remains gated | Active local inpatient stay with bed occupancy |
| Active stay to discharge | Medical discharge and administrative-close conditions are satisfied | Authorized Inpatient roles apply medical discharge, administrative close, and bed release | Closed local admission and reusable bed |
| Surgery target lifecycle | Future readiness and financial-clearance contracts are approved | Feature-gated Surgery slices would be extended through scheduling, execution, and outcome publication | Not currently an accepted end-to-end workflow |
| Domain event to notification/report | An eligible state change is committed | Outbox publishes event; Notification and Report consume idempotently | Delivery record and updated reporting read model |

### B4. Client Hardware Requirements

The following are planning recommendations for development and normal browser use. They are not measured capacity guarantees.

| Client profile | Minimum | Recommended |
|---|---|---|
| Staff desktop/laptop | 64-bit dual-core CPU, 4 GB RAM, 1366×768 display, stable network | Modern 4-core CPU, 8 GB RAM, Full HD display, wired or reliable Wi-Fi |
| Patient browser device | Recent smartphone, tablet, or computer with at least 2 GB available memory | Device receiving current OS/browser security updates, 4 GB or more memory |
| Development workstation | 64-bit 4-core CPU, 16 GB RAM, 30 GB free storage | 8-core CPU, 32 GB RAM, SSD with 60 GB or more free storage |
| Shared reception/lab terminal | Keyboard, pointing device, 1366×768 display, reliable network | Full HD display, barcode-capable peripherals where locally required, UPS-backed power |

### B5. Client Software Requirements

| Item | Requirement |
|---|---|
| Operating system | A currently supported Windows, macOS, Linux, Android, or iOS release suitable for a modern browser |
| Browser | A current release of Chrome, Edge, Firefox, or Safari with JavaScript, cookies, and secure HTTP enabled |
| Display | Browser zoom and resolution sufficient to operate responsive forms and data tables |
| Network | Reliable access to the deployed HTTPS endpoint; local firewall/proxy must permit required traffic |
| Documents | Microsoft Word, LibreOffice Writer, or compatible software for report conversion and editing |
| Mobile | No complete MediFlow mobile client is currently delivered; mobile browser access is the supported planning assumption |

### B6. Server Hardware Requirements

Actual production sizing requires load, availability, and retention measurements. The profiles below support planning only.

| Environment | CPU | Memory | Storage | Intended use |
|---|---:|---:|---:|---|
| Developer demonstration | 8 logical cores | 16 GB RAM | 40 GB free SSD | Local containers, selected services, frontend, and tests |
| Team integration | 16 logical cores | 32 GB RAM | 150 GB SSD | Concurrent services, shared testing, sample data, and monitoring |
| Initial production baseline | 24+ logical cores distributed across hosts | 64+ GB RAM distributed | 500+ GB resilient SSD plus backups | Starting point only; must be validated by performance and recovery tests |

Production planning should separate database, messaging, application, and backup workloads where availability and compliance require it. Redundant power, network, storage, and host capacity should be selected from the agreed SLO, RTO, and RPO.

### B7. Server Software Requirements

| Layer | Current source-aligned requirement |
|---|---|
| Backend runtime | Java 21 |
| Backend framework | Spring Boot 3.3.5 |
| Cloud framework | Spring Cloud 2023.0.3 |
| Build | Maven multi-module build |
| Web client | Node.js runtime compatible with Next.js 16.2.11 and React 19.2.4; pnpm package management |
| Styling and language | Tailwind CSS 4 and TypeScript 5 |
| Database | PostgreSQL 16-compatible deployment; one logical database per service |
| Messaging | RabbitMQ 3-compatible deployment with management capability for operations |
| Service discovery | Eureka Server |
| Container environment | Docker Engine and Docker Compose compatible with the repository configuration |
| Source control | Git and an approved remote repository workflow |
| Transport security | HTTPS termination and trusted certificate management in production |
| Operations | Centralized logs, metrics, alerts, database backups, secret management, and time synchronization |

### B8. Security, Reliability, and Operational Requirements

| Requirement area | Requirement |
|---|---|
| Authentication context | Requests must carry a trusted authenticated identity through the gateway and service boundary |
| Authorization | Every protected endpoint must declare allowed roles; access is denied by default |
| Data privacy | Users receive only records required by their role and patient relationship |
| Service ownership | A service must not read or modify another service's database |
| Data validation | Boundary DTOs must validate required fields, formats, ranges, and identifiers |
| Auditability | Important records retain stable UUIDs, status, timestamps, and relevant actor references |
| Event reliability | State-changing transactions that publish events use a transactional outbox or equivalent reliable pattern |
| Duplicate protection | Consumers must handle repeated messages without repeating business effects |
| Failure handling | Transient failures use bounded retries; unrecoverable messages are observable through dead-letter handling |
| Financial integrity | Monetary values use decimal representation and financial transitions must be traceable |
| Backup and recovery | Databases and configuration require tested backup and restore procedures before production use |
| Monitoring | Health, errors, message backlog, delivery failure, storage, and service availability must be observable |
| Configuration security | Production secrets must not be stored in source control or plain development configuration |

### B9. Acceptance Criteria

The Review 1 requirements are considered represented by the current solution when the following can be demonstrated:

1. All nine roles have clearly separated permitted workflows.
2. All ten business contexts have defined ownership and accessible service boundaries.
3. A patient can be registered and associated with an appointment.
4. An authorized clinician can create an encounter, laboratory request, and prescription.
5. Laboratory staff can progress an order to a verified, published result.
6. Pharmacy staff can validate a prescription, dispense medicine, and update stock records.
7. Billing can record eligible charges, invoices, deposits/payments, receipts, and authorized refunds.
8. Inpatient Core V1 can demonstrate its local admission, bed, treatment, medical-discharge, and administrative-close APIs; cross-service event activation is recorded as gated.
9. Surgery can demonstrate only the selected local, feature-gated routes with default-off flags; the complete request-to-outcome lifecycle is excluded from current acceptance.
10. Eligible state changes can generate notifications and update reporting read models through events.
11. Unauthorized roles are rejected at protected endpoints.
12. Services do not depend on another service's database.
13. Repeated event delivery does not create duplicate business effects.
14. Failure states can be inspected and retried or handled through an operational process.
15. Source-aligned automated tests pass in the agreed verification environment.

### B10. Glossary

| Term | Meaning |
|---|---|
| API gateway | The single client-facing routing entry point for backend services |
| Bounded context | A business area with its own model, rules, and data ownership |
| CRS | Customer Requirements Specification |
| Dead-letter queue | A holding area for messages that cannot be processed successfully |
| Encounter | A documented episode of clinical interaction with a patient |
| Eureka | Service-discovery component used by the backend platform |
| Financial clearance | Confirmation that the required financial condition is satisfied for a care action |
| Idempotency | The property that repeated processing produces no additional business effect |
| Outbox | A database-backed record of events to be published reliably after a transaction |
| Read model | A query-oriented representation built from authoritative operational information |
| RPO | Recovery Point Objective: the acceptable amount of data loss after disruption |
| RTO | Recovery Time Objective: the target time to restore service after disruption |
| SLO | Service Level Objective for measurable availability or performance |
| UUID | Universally Unique Identifier used for stable record and cross-service references |

---

This report describes the behavior demonstrated or directly supported by the current MediFlow source baseline. Hardware sizing and production controls are recommendations that require validation in the target hospital environment.
