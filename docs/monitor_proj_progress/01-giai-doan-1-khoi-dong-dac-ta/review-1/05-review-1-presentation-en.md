# MediFlow Review 1 — Presentation Report (English)

| Presentation item | Value |
|---|---|
| Topic | Problem Definition and Customer Requirements Specification |
| Suggested duration | 12–18 minutes plus questions |
| Structure | 10 presentation sections |
| Source baseline | MediFlow repository commit `1cc014e` |
| Audience | Review panel and project stakeholders |

## Section 1 — Project Introduction

**Core message:** MediFlow connects the patient's journey across hospital departments while keeping each department's data ownership clear.

| Item | Summary |
|---|---|
| Project | Hospital management platform |
| Delivery channel | Next.js web application through a central API gateway |
| Backend | Ten Spring Boot business services |
| Data | Isolated PostgreSQL database per service |
| Integration | REST for immediate authority checks; RabbitMQ events for completed facts |
| Users | Nine roles covering staff, patients, managers, and automation |

MediFlow is not only a collection of forms. It models how information moves from registration to care, testing, medication, finance, admission, surgery, communication, and management reporting.

*Presenter cue: Start with a simple staff-assisted patient story: registration, appointment, consultation, laboratory test, prescription, payment, and follow-up. Explain that every implemented handoff has an owning service.*

## Section 2 — Problem Definition

| Current problem | Operational consequence | MediFlow response |
|---|---|---|
| Paper and disconnected applications | Repeated entry and inconsistent records | Stable patient identifiers and explicit service contracts |
| Manual departmental handoff | Delayed orders, results, and decisions | Controlled workflow states and events |
| Prescription separated from stock | Dispensing risk and inaccurate inventory | Prescription validation and inventory movement |
| Services separated from finance | Missed or duplicate charges | Idempotent billable events and centralized Billing ownership |
| Weak admission/surgery coordination | Resource conflict and unclear readiness | Clearance, allocation, readiness, and schedule gates |
| Manual management reporting | Slow and disputed figures | Event-fed read models |
| Inconsistent access rules | Privacy and accountability risk | Backend role authorization and default deny |

*Presenter cue: Emphasize that the problem is not a lack of data; it is fragmented data without reliable ownership, timing, or accountability.*

## Section 3 — Objectives and Scope

### Objectives

- Trace the patient journey end to end.
- Give every important record one authoritative service.
- Enforce role-based separation of duties.
- Automate safe cross-department handoff.
- Protect financial and inventory integrity.
- Support failure recovery without duplicate business effects.
- Provide management information without modifying source records.

### Implemented business scope

| Service | Main responsibility |
|---|---|
| Organization | Staff, roles, departments, facilities, rooms |
| Patient | Staff-operated profiles and identifiers |
| Clinical | Appointments, medical records, diagnoses, disposition/references; examination requires EXAM clearance or audited override |
| Laboratory | Authorized test requests, specimens, processing, verified results, and publication; processing requires LAB_TEST clearance or audited override |
| Pharmacy | Pharmacy-owned prescriptions, payment-proof-controlled dispensing, medicines, and stock |
| Billing | Charges, invoices, deposits, payments, receipts, refunds, settlement, and service clearance |
| Notification | Event-based notification processing and delivery status |
| Report | Operational and financial read models |
| Inpatient | Admission, clearance, owned beds, stays, treatment, transfers, medical discharge, settlement, and administrative close |
| Surgery | Request, readiness, clearance, schedule, execution, recovery, outcome, and charge handoff |

*Presenter cue: Explain the final business boundaries: Inpatient owns the stay and beds, Surgery owns the surgical case, and Billing supplies the financial decisions required by both workflows.*

## Section 4 — Users and Functions

| Role | Main functions |
|---|---|
| `ADMIN` | Configure Organization data and permitted Inpatient bed records |
| `DOCTOR` | Write clearance-controlled Clinical records; request Laboratory tests; create Pharmacy prescriptions; perform permitted Inpatient and Surgery actions |
| `NURSE` | Create/update appointments, read Clinical records, and perform permitted Inpatient bed/admit/treatment-observation operations |
| `LAB_TECH` | Process existing proof-authorized tests; record, verify, and publish results; cannot create tests |
| `PHARMACIST` | Validate prescriptions, dispense medicines, control stock |
| `CASHIER` | Handle charges, invoices, deposits, payments, receipts, authorized refunds, settlement, and service clearance |
| `MANAGER` | Review operational and financial reports |
| `PATIENT` | Use authorized self-service for owned profile, appointments, results, prescriptions, invoices, receipts, payments, and notifications |
| `SYSTEM` | Publish/consume events, retry failures, update reports, create notifications |

*Presenter cue: Explain that hiding a button is only user experience; the backend independently checks roles at protected endpoints.*

## Section 5 — Proposed Solution Architecture

| Component | Role in the solution |
|---|---|
| Browser client | Presents role-aware workflows |
| API gateway | Provides one controlled client entry point |
| Eureka | Registers and discovers backend services |
| Business services | Own rules and records by bounded context |
| PostgreSQL | Provides a separate logical database for each service |
| RabbitMQ | Distributes completed business events |
| Transactional outbox | Prevents committed changes from losing their event intent |
| Idempotent consumer | Prevents retries from repeating business effects |
| Retry/dead-letter handling | Makes temporary and terminal failures observable |

Architecture rule: `infrastructure → application → domain`. The domain does not depend on Spring, transport, or persistence. Services exchange DTOs and UUID references, never database relations.

*Presenter cue: Contrast REST and events: REST answers “what must I confirm now?”, while an event announces “what has already happened?”.*

## Section 6 — Customer Data-Processing Cycles

| Workflow | Actor | Input information | Processing | Output information | Owning service |
|---|---|---|---|---|---|
| Staff-operated registration | `ADMIN`, `NURSE` | Demographics, contact, identifiers | Validate identity data | Patient profile | Patient |
| Appointment creation | `ADMIN`, `NURSE` | Patient UUID, service/time | Validate scheduling data | Appointment | Clinical |
| Clinical examination | `ADMIN`, `DOCTOR` | Patient/appointment, findings, EXAM clearance/override | Validate role, context, state, and proof; write record/diagnosis | Medical record and disposition | Clinical |
| Lab test creation | `ADMIN`, `DOCTOR` | Patient/record, department, test type/date | Create test directly; no Clinical order contract | Lab test | Laboratory |
| Lab processing | `ADMIN`, `LAB_TECH` | Existing test, specimen/values, LAB_TEST clearance/override | Validate lifecycle and proof; process, verify, publish | Result and result event | Laboratory |
| Prescription creation | `ADMIN`, `DOCTOR` | Clinical reference and medicine lines | Pharmacy validates/prices and creates its prescription | Pharmacy prescription | Pharmacy |
| Dispensing | `ADMIN`, `PHARMACIST` | Pharmacy prescription, durable payment proof, stock | Validate proof and stock; dispense | Dispense and stock state | Pharmacy |
| Financial processing | Authorized Billing staff | Charge/invoice/payment or exact deposit/refund/settlement/clearance purpose | Validate authority, state, amount, idempotency, totals, and purpose | Auditable financial record and clearance decision | Billing |
| Inpatient lifecycle | Authorized staff per operation | Admission, bed, deposit proof/override, treatment, discharge, and settlement/close proof | Apply explicit authority, proof, and state rules | Admission, treatment, discharge, close, and bed state | Inpatient |
| Surgery lifecycle | Authorized surgery staff | Request, readiness, clearance, schedule, procedure, recovery, and outcome | Validate prerequisites; execute lifecycle; publish outcome and charge handoff | Auditable case, outcome, and charge event | Surgery |
| Notification delivery | `SYSTEM`, `PATIENT` read | Approved events and recipient identity | Deliver idempotently; enforce owned reads | Delivery status/owned notification | Notification |
| Reporting | `SYSTEM`, `MANAGER` | Approved events and report criteria | Update/query projection | Authorized report | Report |

*Presenter cue: Read one row using the five-part pattern: actor, input, process, output, owner. State that the same pattern is applied to every major workflow in the full reports.*

## Section 7 — Main Cross-Department Flows

1. Staff-operated Patient registration → Clinical appointment → clinical encounter.
2. Clinical test request with explicit test code/order ID → Laboratory acceptance → clearance/override → processing/result.
3. Doctor creates a Pharmacy-owned prescription → durable payment proof → dispense → stock movement.
4. Billable care event → charge/invoice → deposit/payment/receipt → refund or settlement/service clearance as required.
5. Local Inpatient admission → bed → matching deposit clearance/override → explicit admit → treatment → medical discharge → settlement/override → administrative close.
6. Surgery request → readiness → clearance → schedule → execution → recovery/outcome → charge handoff.
7. Completed domain event → notification delivery and report projection update.

Key controls:

- Stable UUID correlation across services.
- Owning-service validation before state change.
- `BigDecimal` for money.
- Outbox publication after committed changes.
- Idempotent event consumption.
- Bounded retry and visible dead-letter processing.

*Presenter cue: Use Inpatient and Surgery to show how clinical ownership, resource control, and Billing clearance cooperate without sharing databases.*

## Section 8 — Hardware and Software Requirements

### Client

| Category | Minimum planning baseline | Recommended |
|---|---|---|
| Staff device | 64-bit dual-core CPU, 4 GB RAM, 1366×768 | Modern 4-core CPU, 8 GB RAM, Full HD |
| Browser | Current Chrome, Edge, Firefox, or Safari | Vendor-supported OS and browser with security updates |
| Network | Stable access to deployed HTTPS endpoint | Reliable wired/Wi-Fi connection |
| Development | 4-core CPU, 16 GB RAM, 30 GB free SSD | 8-core CPU, 32 GB RAM, 60+ GB free SSD |

### Server and platform

| Category | Source-aligned baseline |
|---|---|
| Backend | Java 21, Spring Boot 3.3.5, Spring Cloud 2023.0.3, Maven |
| Frontend | Next.js 16.2.11, React 19.2.4, TypeScript 5, Tailwind CSS 4, pnpm |
| Infrastructure | PostgreSQL 16-compatible, RabbitMQ 3-compatible, Eureka, Docker Compose |
| Integration environment | 16 logical cores, 32 GB RAM, 150 GB SSD planning profile |
| Initial production planning | 24+ logical cores, 64+ GB RAM, 500+ GB resilient SSD across suitable hosts |

These hardware values are planning recommendations. Production sizing requires load, availability, retention, and recovery tests.

*Presenter cue: Do not present the production numbers as guarantees. State clearly that they are starting points awaiting measured workload evidence.*

## Section 9 — Limitations and Remaining Work

| Limitation | Current status |
|---|---|
| Native mobile application | Only a Flutter loader package exists; no complete runnable mobile app |
| Patient self-service | Direct patient access is currently limited; complete profile, appointment, result, prescription, invoice, receipt, and payment access needs secure end-to-end acceptance |
| Inpatient departmental integration | Core admission, bed, treatment, and discharge operations exist; the complete finance, Pharmacy, Surgery, Report, and Notification handoff still needs integrated acceptance |
| Surgery lifecycle | Request/preparation/scheduling are partially demonstrable; readiness-to-outcome and charge handoff still need end-to-end acceptance |
| Clinical-to-Lab order | Automated handoff is incomplete; Lab tests are currently created directly by `ADMIN`/`DOCTOR` |
| Billing completeness | Basic invoice/payment is demonstrable; deposit, refund, settlement, clearance, and care-workflow handoffs still need complete acceptance |
| Mandatory proofs | EXAM clearance, LAB_TEST clearance, Pharmacy payment proof, and Inpatient deposit/settlement proof are required unless an allowed audited override exists |
| Insurance and national identity | No certified production integration is demonstrated |
| Bank/payment gateway | Internal finance exists; commercial provider integration is not proven complete |
| Medical devices/external labs/PACS/EHR | No certified external interoperability is claimed |
| SMS/email/push provider | Internal notification workflow exists; production vendor contract is pending |
| Production SLO, RTO, RPO, retention | Must be agreed and validated for the target hospital |
| Capacity and disaster recovery | Require load, failure, backup, and restore testing |
| Accessibility and regulation | Formal targets and certifications remain governance work requiring customer approval |
| Automated diagnosis/treatment | Explicitly outside current scope; clinical decisions remain human |

*Presenter cue: Frame limitations as Review 1 completion gaps while keeping the requirements focused on the complete final hospital workflow.*

## Section 10 — Acceptance and Conclusion

Review 1 can be demonstrated through the following acceptance story:

1. As `ADMIN`/`NURSE`, register a patient in Patient and schedule an appointment in Clinical.
2. Open a Clinical record as `ADMIN`/`DOCTOR` only with valid encounter state and matching EXAM clearance/override.
3. Transfer a Clinical test request with explicit test code/order ID; require LAB_TEST clearance/override before Laboratory processing/publication.
4. Create the prescription through Pharmacy and require durable payment proof before dispensing/stock update.
5. Demonstrate traceable charge/invoice/payment/receipt plus authorized deposit, refund, settlement, and service clearance.
6. Demonstrate Inpatient bed + deposit proof, explicit admit, treatment/transfer, medical discharge, settlement/override-controlled close, and departmental handoffs.
7. Demonstrate Surgery request, readiness, clearance, scheduling, execution/cancellation, recovery, outcome, and charge handoff.
8. Generate notification and reporting updates from committed events.
9. Reject unauthorized operations and repeated business effects.
10. Execute the agreed automated verification suite successfully.

**Conclusion:** MediFlow provides a source-aligned foundation for coordinated hospital operations. Its value is not only the number of modules, but the controlled movement of information between authorized actors and owning services.

*Presenter cue: Close with three phrases: “one clear owner for each record,” “one traceable path for each implemented workflow,” and “one explicit gate for unfinished integration.” Then invite questions.*

### Quick Questions and Answers

| Likely question | Short answer |
|---|---|
| Why microservices? | The hospital domains have distinct rules and ownership; independent services keep those boundaries explicit. |
| Why both REST and events? | REST confirms information needed now; events distribute facts that already happened. |
| Is mobile complete? | No. Only a shared Flutter loader package is present; responsive web is the current delivery assumption. |
| What is the status of Inpatient and Surgery? | The final system includes complete Inpatient and Surgery lifecycles. At Review 1, core Inpatient operations are demonstrable, while departmental Inpatient handoffs and the full Surgery readiness-to-outcome flow still require integrated acceptance. |
| Is production capacity guaranteed? | No. Hardware figures are planning baselines and require representative testing. |
| How are duplicate charges prevented? | Billing consumers use event identity/idempotency so repeated delivery does not repeat the business effect. |
