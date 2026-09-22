# HANDOFF — Patient API blocks the post-login landing page

**Discovered:** 2026-09-22

**Producer / owner:** Patient Service — Hoàng Anh (`TranHoangAnh94`)

**Consumers:** shared frontend login flow and Patient list

**Status:** `BLOCKED`

## Observed workflow

1. Start Gateway and Organization, then sign in as `admin / admin123`.
2. Gateway authentication succeeds and the frontend stores the session.
3. `frontend/src/app/login/page.tsx` redirects the authenticated user to `/patients`.
4. The Patient page requests `GET /api/v1/patients?page=0&size=20`.
5. Gateway returns `503`; the page displays `Request failed (503)`.

At the current source baseline, `backend/patient-service` contains only
`PatientServiceApplication.java` and `application.yml`. It has no controller, application use case,
or response DTO capable of serving the landing request.

## Required producer contract

Implement the secured Patient list/search contract described by the Patient specification:

- `GET /api/v1/patients?page&size&keyword`;
- standard `ApiResponse<PageResult<PatientDTO>>` envelope;
- roles `ADMIN`, `DOCTOR`, and `NURSE`;
- stable error codes and correlation ID behavior through Gateway;
- DTO field names agreed with `frontend/src/features/patient/types.ts` or changed together in the
  same coordinated PR.

## Acceptance criteria

- Login succeeds when Organization is healthy.
- The redirect to `/patients` returns a successful empty or populated page response, not `503`.
- Patient list empty/error/retry states are verified through Gateway.
- Clinical and Lab continue referencing patients by bare UUID and do not query Patient storage.

Changing the shared default landing route is a separate explicitly assigned frontend task. It may
improve first-page resilience, but it must not be used to hide the missing Patient contract.
