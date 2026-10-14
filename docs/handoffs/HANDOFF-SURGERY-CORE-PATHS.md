# HANDOFF — Deferred Surgery references to Core

**Owner:** Huy / Surgery  
**Status:** Deferred with Surgery; not an outpatient MVP acceptance blocker.

Organization and Patient source modules have moved into `backend/core-service`. Surgery's
`OrganizationFeignClient` and `PatientFeignClient` still resolve the removed Eureka names.
When Surgery is reactivated, retarget those clients to `core-service` while preserving their
paths, JWT service identity and strict response handling. Keep the logical event producer
`organization-service` in Surgery's decoder until its wire contract is versioned.

Surgery tests also read Organization fixtures from removed `../organization-service` paths.
The same fixture bytes now live under `../core-service/src/test/resources/contracts/`.

**Reproduced 2026-10-14:** `mvn -q -pl backend/surgery-service
-Dtest=SurgeryAuthorityChangeDecoderTest test` ends with 35 `NoSuchFile` errors for the
old Organization fixture path. Restore the test gate when this context resumes.
