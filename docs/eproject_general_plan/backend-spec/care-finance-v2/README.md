# MediFlow care-finance V2 implementation specifications

This directory contains remaining additive TARGET contracts for the care-finance redesign,
compatibility redirects for Clinical and Lab, and the sole canonical Inpatient specification.
Always use the [canonical matrix](../README.md#canonical-implementation-entry-point) before choosing
a file from this directory.

## Precedence

1. [`mediflow-care-finance-redesign.html`](../../../architecture/mediflow-care-finance-redesign.html)
   defines the approved business workflow.
2. [`docs/handoffs/care-finance`](../../../handoffs/care-finance) defines shared wire contracts.
3. The canonical implementation file named by the matrix defines service-local DDL, ports, DTOs,
   algorithms and tests.
4. Compatibility behavior remains authoritative until the relevant Docker acceptance path passes.

V2 rollout is additive. A TARGET file never authorizes destructive data rewrites, cross-service DB
access, guessed identifiers or removal of compatibility consumers without migration evidence.

## Index and maturity

| No. | Context | Owner | Target maturity |
|---:|---|---|---|
| 01 | [Organization](01-organization.md) | Hoàng Anh | phase-1 identity lookup implementation-ready |
| 02 | [Patient](02-patient.md) | Hoàng Anh | phase-1 patient lookup implementation-ready |
| 03 | [Clinical redirect](03-clinical.md) → [canonical spec](../03-clinical.md) | Vinh | implemented; Billing runtime gate |
| 04 | [Lab redirect](04-lab.md) → [canonical spec](../04-lab.md) | Vinh | implemented; Billing runtime gate |
| 05 | [Pharmacy](05-pharmacy.md) | Huy | implementation-ready; Billing fixture gate |
| 06 | [Billing](06-billing.md) | Lộc | implementation-ready ledger target |
| 07 | [Notification](07-notification.md) | Lộc | implementation-ready after producer fixtures |
| 08 | [Report](08-report.md) | Huy | implementation-ready after producer fixtures |
| 09 | [Gateway](09-gateway.md) | Hoàng Anh | implementation-ready route target |
| 10 | [Inpatient — canonical](10-inpatient.md) | Vinh | Core V1 implemented; external messaging gated |
| 11 | [Surgery](11-surgery.md) | Huy | detailed candidate; decision gate remains |

## Shared enablement gate

A consumer may be scaffolded behind `mediflow.features.care-finance-v2=false`, but the feature is
enabled only when producer and consumer serialize the same version-1 fixture, duplicate delivery is
tested, malformed targets reach DLQ, and the relevant Docker vertical slice passes. Each service
keeps its own database and stores external aggregates as bare UUID references.
