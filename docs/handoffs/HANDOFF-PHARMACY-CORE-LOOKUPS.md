# HANDOFF — Pharmacy Core identity lookups

**Owner:** Huy / Pharmacy  
**Status:** Open  
**Reason:** Core now hosts the Organization and Patient APIs; the old `organization-service`
and `patient-service` modules have been removed from the Maven reactor and source tree.

**Reproduced 2026-10-14:** `mvn -q -pl backend/pharmacy-service
-Dtest=PrescriptionIdentityHttpTest test` ends with 40 errors because the test still reads
`../patient-service/src/test/resources/contracts/patient.lookup.exists.json`.

## Required change

Retarget `PharmacyOrganizationFeignClient` and `PharmacyPatientFeignClient` to the Eureka
deployment name `core-service`. Keep the existing paths, response bytes, service-token subject,
timeouts, fail-closed behavior, and exact identity checks unchanged.

The existing pharmacy tests read lookup fixtures from the removed source paths. Copy the exact
source fixtures from `backend/core-service/src/test/resources/contracts/` into Pharmacy test
resources or update the tests to read the new Core fixture paths. Do not edit the fixture bytes.

## Acceptance

- Both Feign lookups resolve through `core-service` and still use the same public API paths.
- Tests cover confirmed absence separately from timeout, malformed response, and unavailable Core.
- `mvn -q -pl backend/pharmacy-service test` passes without reading a removed module directory.
- Pharmacy's logical JWT producer/caller identity remains `pharmacy-service`.
