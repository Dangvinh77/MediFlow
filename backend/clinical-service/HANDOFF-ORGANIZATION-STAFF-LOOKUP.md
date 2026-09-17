# HANDOFF — Organization staff lookup contract (PR1)

> **Consumer action required:** update Clinical's staff lookup projection and service-to-service
> authentication before deploying the Organization PR1 changes.

- **Producer:** `organization-service` — Hoàng Anh (`TranHoangAnh94`)
- **Consumer:** `clinical-service` — Vinh (`Dangvinh77` / `Harori`)
- **Endpoint:** `GET /api/v1/org/staff/{id}/exists`
- **Purpose:** validate that a doctor exists, is active, and belongs to the authoritative department
- **Organization implementation:** PR1, pending commit/deployment

## Wire contract

The endpoint returns the shared `ApiResponse` envelope. The `data` object is:

```json
{
  "exists": true,
  "eligibleDoctor": true,
  "departmentId": "2f4d8d4a-8c2c-4f5d-8ed0-bcf9159e5bd1"
}
```

The three confirmed lookup states are:

| State | `exists` | `eligibleDoctor` | `departmentId` |
|---|---:|---:|---|
| Staff is missing | `false` | `false` | `null` |
| Staff exists but is not eligible | `true` | `false` | `null` |
| Active doctor exists | `true` | `true` | authoritative UUID |

`eligibleDoctor` means `status == ACTIVE` and `jobTitle == DOCTOR`. Organization deliberately
does not expose a department for an ineligible staff member, so a consumer cannot accidentally
use a nurse or inactive doctor as the appointment doctor.

A missing or ineligible staff member is a confirmed negative lookup and returns HTTP 200. Timeout,
connection failure, circuit-open, invalid envelope, malformed data, and HTTP 5xx remain upstream
failures and must not be converted into a negative lookup.

## Required Clinical changes

### 1. Update the Feign DTO

In `infrastructure/client/StaffExistsResponse.java`, add:

```java
boolean eligibleDoctor
```

Keep `departmentId` as the JSON field name. The existing `@JsonAlias("maKhoa")` can remain only
for backward-compatible fixtures; new Organization responses use `departmentId`.

`OrganizationFeignClient` already projects the outer type as:

```java
ApiResponse<StaffExistsResponse>
```

No URL change is required.

### 2. Update `StaffLookupAdapter`

The adapter should map the response as follows:

```text
response == null
  or success == false
  or data == null                 -> UpstreamUnavailableException

data.exists == false              -> Optional.empty()
data.exists == true
  and data.eligibleDoctor == false -> Optional.empty()

data.eligibleDoctor == true
  and departmentId != null         -> Optional.of(departmentId)

eligibleDoctor == true
  and departmentId == null          -> UpstreamUnavailableException
```

Do not treat an ineligible doctor as an upstream outage. Preserve the existing classifier behavior
for transport errors, timeout, circuit-open, malformed envelope, and HTTP 5xx.

### 3. Use a service credential, not the caller's token

The Organization lookup accepts a JWT with a `SYSTEM` role. Clinical must stop forwarding the
human caller's `Authorization` header from `AuthorizationForwardingInterceptor`.

The internal request must carry a service JWT equivalent to:

```text
sub = clinical-service
role = SYSTEM
```

The token must be signed with the configured shared HS256 secret, have a bounded expiration, and be
provided through the agreed service-credential mechanism. Do not trust an unsigned custom header.
Correlation propagation through `X-Correlation-Id` remains unchanged.

### 4. Add/adjust tests

Required Clinical tests:

- `StaffExistsResponse` deserializes the new `eligibleDoctor` field.
- Extra additive JSON fields are ignored.
- Missing staff maps to `Optional.empty()`.
- Existing but ineligible staff maps to `Optional.empty()`.
- Eligible doctor with department maps to that department.
- Eligible doctor without department is an upstream contract failure.
- Service JWT is sent instead of the human caller token.
- Timeout, connection failure, circuit-open, invalid envelope, and HTTP 5xx remain
  `UpstreamUnavailableException`.
- Appointment creation rejects an ineligible doctor as `DOCTOR_NOT_FOUND_REMOTE` or the agreed
  domain error, not as an upstream outage.

## Rollout order

1. Merge Organization PR1 and deploy only when the shared `MEDIFLOW_JWT_SECRET` and service-token
   arrangement are available.
2. Merge the Clinical DTO/adapter/interceptor changes.
3. Deploy the two services together or ensure the Clinical service token is available before
   enabling the Organization endpoint.
4. Run the Clinical contract and appointment validation tests.

The Organization endpoint is internal and must not be exposed as a normal client-facing lookup.
Gateway routing and service-token issuance are separate integration tasks owned by the Gateway/
platform side.

## Acceptance checklist

- [ ] Feign DTO contains `eligibleDoctor`.
- [ ] Missing, ineligible, and eligible states are distinct in Clinical.
- [ ] Upstream failures are not converted to `Optional.empty()`.
- [ ] Clinical sends a JWT with `role=SYSTEM` and does not forward a human token.
- [ ] `X-Correlation-Id` is propagated.
- [ ] Contract, adapter, security, and appointment tests pass.
- [ ] Clinical owner confirms the projection is enabled after Organization PR1 is merged.
