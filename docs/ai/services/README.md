# Per-service design index

Read the bounded-context document before changing a service. For any REST/event integration, also
read [`16-care-finance-integration-contracts.md`](../16-care-finance-integration-contracts.md) and
the contracts listed in the service's nested `AGENTS.md`.

| Service | Owner | Runtime status | Service doc |
|---|---|---|---|
| Gateway | Hoàng Anh | implemented | [gateway](gateway.md) |
| Organization | Hoàng Anh | implemented | [organization](organization.md) |
| Patient | Hoàng Anh | implemented/in progress | [patient](patient.md) |
| Clinical | Vinh | implemented | [clinical](clinical.md) |
| Lab | Vinh | implemented | [lab](lab.md) |
| Pharmacy | Huy | implemented | [pharmacy](pharmacy.md) |
| Billing | Lộc | implemented; ledger redesign planned | [billing](billing.md) |
| Notification | Lộc | implemented | [notification](notification.md) |
| Report | Huy | implemented | [report](report.md) |
| Inpatient | Vinh | planned, foundation shell only | [inpatient](inpatient.md) |
| Surgery | Huy | planned, not scaffolded | [surgery](surgery.md) |

`planned` means the bounded context does not yet have its business implementation. A preliminary
foundation may contain runtime configuration and infrastructure wiring only; do not add business
models, migrations, endpoints, DTOs, or events before its implementation-ready spec exists.
