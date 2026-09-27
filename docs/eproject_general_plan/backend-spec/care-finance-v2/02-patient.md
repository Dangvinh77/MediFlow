# 02 — patient-service — Care & Finance V2 target

**Owner:** Hoàng Anh (`TranHoangAnh94`)

**Module:** `backend/patient-service`

**Base path:** `/api/v1/patients`

**Status:** phase-1 existence lookup implementation-ready; insurance/contact expansion deferred

## 1. Sources and boundary

- [`mediflow-care-finance-redesign.html`](../../../architecture/mediflow-care-finance-redesign.html)
- [`CONTRACT-IDENTITY-LOOKUP-01`](../../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md)
- [CURRENT Patient spec](../02-patient.md)

Patient owns canonical patient UUIDs and demographics. Other services store only `patientId` and
explicit permitted snapshots. Patient never calculates insurance benefit, deposit, clearance,
patient liability or settlement.

## 2. Migration boundary

Phase 1 adds no profile column. Insurance-summary and emergency-contact shapes are not locked and
must not be invented in this spec. They require a later additive migration and contract. Existing
patient CRUD/events remain compatible; `payment.completed` stays log-only behavior.

## 3. DTO and port

```java
public record PatientLookupDTO(boolean exists, UUID patientId) {}

public interface ReadPatientIdentityUseCase {
    PatientLookupDTO exists(UUID patientId);
}
```

`patientId` always echoes the requested canonical UUID, including when `exists=false`. The public
patient DTO is not returned by the internal endpoint and no demographic field is exposed
unnecessarily.

## 4. Endpoint and authorization

| Method | Path | Principal | Result |
|---|---|---|---|
| GET | `/api/v1/patients/{id}/exists` | `type=service`, `role=SYSTEM` | `PatientLookupDTO` |
| GET | `/api/v1/patients/{id}` | existing human role matrix | CURRENT public Patient DTO |

The internal endpoint requires a short-lived signed service token and correlation ID. Human access
and refresh tokens are rejected. Gateway does not expose the internal route as a public bypass.

## 5. Application algorithm

1. Validate UUID and service principal.
2. Execute an existence projection, not a full demographic read.
3. Return the same requested UUID and the authoritative existence boolean.
4. Preserve repository errors as `503 PATIENT_LOOKUP_UNAVAILABLE`.
5. Never search by name, identity number, phone or latest care record as a fallback.

## 6. Error contract

| Code | HTTP | Condition |
|---|---:|---|
| `PATIENT_INVALID_SERVICE_TOKEN` | 401 | invalid service credential |
| `PATIENT_FORBIDDEN_PRINCIPAL` | 403 | wrong token type/role |
| `PATIENT_VALIDATION_ERROR` | 400 | malformed UUID |
| `PATIENT_LOOKUP_UNAVAILABLE` | 503 | producer cannot determine existence |

Confirmed absence is `200` with `exists=false`; consumers map only that response to local not-found.

## 7. Events

Existing `patient.created` and `patient.updated` remain unchanged. No care-finance event originates
from Patient. A later profile-contract version may add insurance/contact snapshots only after data
classification, consent and field definitions are approved.

## 8. Required tests

| Rule | Required test |
|---|---|
| existing patient returns exact UUID | `exists_existing_returnsCanonicalId` |
| missing patient is explicit | `exists_missing_returnsFalse` |
| repository failure is not absence | `exists_repositoryFailure_returns503` |
| human token rejected | `exists_accessToken_returns403` |
| SYSTEM service accepted | `exists_systemService_returns200` |
| public read contract unchanged | `getPatient_currentContractRegression` |
| correlation preserved | `exists_preservesCorrelationId` |

Fixtures are consumed by Clinical, Lab, Pharmacy, Billing, Inpatient and Surgery.

## 9. Rollout and Definition of Done

1. Add the projection port/controller/security rule.
2. Add producer JSON fixture and consumer deserialization tests.
3. Prove missing vs outage behavior through integration tests.
4. Keep all insurance/contact changes outside this phase.

Done means identity validation is stable, minimal and service-authenticated without cross-service DB
access or accidental expansion of patient data.
