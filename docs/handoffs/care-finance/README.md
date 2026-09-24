# Care & Finance cross-service handoffs

Đây là registry canonical cho các contract phát sinh từ
[`mediflow-care-finance-redesign.html`](../../architecture/mediflow-care-finance-redesign.html).
Payload chỉ được định nghĩa trong các file dưới đây. Service docs và `AGENTS.md` phải link tới đây,
không copy một bản contract khác.

## Contract registry

| ID | Status | Owners | Nội dung |
|---|---|---|---|
| [`CONTRACT-CARE-BILLING-01`](CONTRACT-CARE-BILLING-01.md) | DESIGN_READY; current outpatient contracts partially implemented | Vinh + Lộc + Huy | care episode, charge, purpose-scoped clearance, deposit, settlement |
| [`CONTRACT-INPATIENT-SURGERY-01`](CONTRACT-INPATIENT-SURGERY-01.md) | DESIGN_READY; blocked by new modules | Vinh + Huy | admission referral, surgery workflow, inpatient medication |
| [`CONTRACT-SURGERY-BILLING-01`](CONTRACT-SURGERY-BILLING-01.md) | DESIGN_READY; blocked by new modules/ledger | Huy + Lộc | procedure charge, surgery clearance, cancellation/refund |
| [`CONTRACT-IDENTITY-LOOKUP-01`](CONTRACT-IDENTITY-LOOKUP-01.md) | PARTIAL | Hoàng Anh + all consumers | patient/staff/department identity, JWT service auth, Gateway routes |
| [`CONTRACT-CARE-PROJECTIONS-01`](CONTRACT-CARE-PROJECTIONS-01.md) | DESIGN_READY | Lộc + Huy + event producers | Notification templates and Report projections |

## Service reading matrix

| Service | Must read before integration changes |
|---|---|
| Gateway | IDENTITY-LOOKUP |
| Organization | IDENTITY-LOOKUP |
| Patient | IDENTITY-LOOKUP |
| Clinical | CARE-BILLING, INPATIENT-SURGERY, IDENTITY-LOOKUP, CARE-PROJECTIONS |
| Lab | CARE-BILLING, IDENTITY-LOOKUP, CARE-PROJECTIONS |
| Pharmacy | CARE-BILLING, INPATIENT-SURGERY, SURGERY-BILLING, CARE-PROJECTIONS |
| Billing | CARE-BILLING, SURGERY-BILLING, CARE-PROJECTIONS |
| Notification | CARE-PROJECTIONS, IDENTITY-LOOKUP |
| Report | CARE-PROJECTIONS |
| Inpatient (planned) | all five contracts |
| Surgery (planned) | all five contracts except outpatient-only parts of CARE-BILLING |

## Existing handoffs retained

Các handoff đã triển khai vẫn có hiệu lực và được link thay vì viết lại:

- [Billing → Lab payment references](../../../backend/billing-service/HANDOFF-LAB-PAYMENT-COMPLETED.md)
- [Billing ↔ Pharmacy saga](../../../backend/billing-service/HANDOFF.md)
- [Pharmacy → Clinical prescription correlation](../../../backend/pharmacy-service/HANDOFF-CLINICAL-PRESCRIPTION-FILLED.md)
- [Organization → Clinical staff lookup](../../../backend/organization-service/HANDOFF-CLINICAL-STAFF-LOOKUP.md)
- [Patient → Clinical patient lookup](../../../backend/patient-service/HANDOFF-CLINICAL-PATIENT-LOOKUP.md)
- [Gateway → Organization account verification](../../../backend/gateway/HANDOFF-ORGANIZATION-ACCOUNT-VERIFY.md)
- [Notification patient JWT claim](../../../backend/notification-service/HANDOFF-NOTIFICATION-PATIENT-JWT-CLAIM.md)

## Update rule

Một PR đổi contract phải cập nhật registry status, canonical handoff, producer fixture/test và consumer
fixture/test. Nếu không có quyền sửa consumer, PR giữ status chưa `IMPLEMENTED`, ghi blocker và tag
đúng owner; không dùng default field hoặc suy luận ID để “cho chạy trước”.
