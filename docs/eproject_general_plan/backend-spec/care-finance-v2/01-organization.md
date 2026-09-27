# 01 — organization-service — Care & Finance V2 target

**Owner:** Hoàng Anh (`TranHoangAnh94`)

**Module:** `backend/organization-service`

**Base paths:** `/api/v1/org`, `/api/v1/auth`

**Status:** phase-1 identity lookup implementation-ready; additive to CURRENT CRUD/auth contracts

## 1. Sources and boundary

- [`mediflow-care-finance-redesign.html`](../../../architecture/mediflow-care-finance-redesign.html)
- [`CONTRACT-IDENTITY-LOOKUP-01`](../../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md)
- [CURRENT Organization spec](../01-organization.md)

Organization remains the authority for account, staff and department identity. It validates active
membership and job eligibility for Clinical, Inpatient and Surgery. It does not own patient data,
beds, care episodes, surgery cases or money.

## 2. Migration boundary

No V2 business table is required. Reuse authoritative `staff`, `department` and `account` rows.
Add only indexes required by measured lookup plans; never duplicate identity into a V2 table.
Existing `/exists` doctor compatibility remains unchanged. The new generic `/lookup` endpoints are
service-only and distinguish confirmed absence from producer failure.

## 3. DTOs and ports

```java
public record StaffIdentityLookupDTO(
    boolean exists,
    boolean active,
    String jobTitle,
    UUID departmentId
) {}

public record DepartmentLookupDTO(
    boolean exists,
    boolean active,
    UUID departmentId,
    String departmentName,
    String departmentType
) {}

public interface ReadOrganizationIdentityUseCase {
    StaffIdentityLookupDTO lookupStaff(UUID staffId);
    DepartmentLookupDTO lookupDepartment(UUID departmentId);
}
```

An absent UUID returns `200 ApiResponse` with `exists=false` and nullable descriptive fields. An
unexpected persistence failure returns the normal `5xx` envelope; it must not be mapped to absence.

## 4. Endpoints and authorization

| Method | Path | Principal | Result |
|---|---|---|---|
| GET | `/api/v1/org/staff/{id}/lookup` | `type=service`, `role=SYSTEM` | `StaffIdentityLookupDTO` |
| GET | `/api/v1/org/departments/{id}/lookup` | `type=service`, `role=SYSTEM` | `DepartmentLookupDTO` |
| GET | `/api/v1/org/staff/{id}/exists` | compatibility service callers | existing doctor lookup |

The security filter requires a signed short-lived JWT, `type=service`, `role=SYSTEM`, non-empty
service subject and correlation ID. Forwarded human access/refresh tokens are rejected. Controllers
use `@PreAuthorize("hasRole('SYSTEM')")`; default deny remains active.

## 5. Application algorithms

### Staff lookup

1. Validate UUID and service principal.
2. Read the staff projection once with its authoritative department ID.
3. If absent, return `exists=false`.
4. Return `active=true` only when the staff status is active; do not infer eligibility from job title.
5. Preserve `X-Correlation-Id` in response and logs without personal payload logging.

### Department lookup

1. Read by UUID and return confirmed absence explicitly.
2. Project active status, stable ID, name and type from the authoritative row.
3. A deactivated department remains readable for historical validation but reports `active=false`.

Staff transfer or deactivation never rewrites historical foreign UUIDs in other services.

## 6. Error contract

| Code | HTTP | Condition |
|---|---:|---|
| `ORG_INVALID_SERVICE_TOKEN` | 401 | missing/invalid service credential |
| `ORG_FORBIDDEN_PRINCIPAL` | 403 | human or non-SYSTEM principal |
| `ORG_VALIDATION_ERROR` | 400 | malformed UUID/path |
| `ORG_LOOKUP_UNAVAILABLE` | 503 | persistence or dependency unavailable |

Confirmed absence is a successful lookup, not an error.

## 7. Required tests

| Rule | Required test |
|---|---|
| active staff returns canonical department | `lookupStaff_active_returnsIdentityProjection` |
| inactive staff remains distinguishable | `lookupStaff_inactive_returnsExistsButInactive` |
| missing staff is not outage | `lookupStaff_missing_returnsExistsFalse` |
| lookup failure is not absence | `lookupStaff_repositoryFailure_returns503` |
| human access token rejected | `lookup_accessToken_returns403` |
| service token must be SYSTEM | `lookup_nonSystemService_returns403` |
| correlation preserved | `lookup_preservesCorrelationId` |
| compatibility endpoint unchanged | `doctorExists_contractRegression` |

Contract fixtures are consumed by Clinical, Inpatient and Surgery. Tests must assert the common
`ApiResponse` envelope and exact JSON field names.

## 8. Rollout and Definition of Done

1. Add DTOs, in-port and repository projections without changing existing CRUD responses.
2. Add service-only routes and security tests.
3. Publish versioned fixtures in consumer contract tests.
4. Enable consumers only after absence/outage and token tests pass.

Done means both lookup routes are authenticated, correlation-safe, fixture-locked and do not expose
or mutate care/financial data.
