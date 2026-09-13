# Clinical/Lab Web Foundation Design

## Goal

Prepare the first usable web source for Harori's Clinical and Lab bounded contexts without
inventing contracts that are still owned by Patient, Organization, Pharmacy, Billing, or the
shared Flutter foundation.

## Considered approaches

1. **Read-first web foundation (selected).** Add exact DTO types, typed gateway calls, and simple
   list screens for appointments, medical records, and lab tests. This gives the team usable UI
   integration now while keeping forms and state transitions for a later focused change.
2. **Full CRUD web screens.** Add create/edit/status/result forms immediately. This exposes more
   backend behavior but is too broad while Patient and Organization lookup endpoints are absent.
3. **Contracts only.** Add types and API wrappers without screens. This is safest but gives little
   integration feedback and leaves the frontend visibly unchanged.

## Contract naming

Frontend and mobile types must mirror each service's actual wire DTO field-for-field. Vietnamese
camelCase remains valid for services that expose it. Clinical and Lab use English camelCase as
defined by their implementation-ready specs and Java response records. Shared guidance will state
this per-service rule instead of claiming every service uses Vietnamese fields.

## Web structure

- `features/appointment`: `AppointmentDTO`, query type, typed list API, and appointment table.
- `features/medical-record`: record and diagnosis DTOs, patient lookup API, and record table.
- `features/lab`: lab/result DTOs, typed list API, and lab table.
- Thin pages live at `/appointments`, `/records`, and `/lab` under the App Router dashboard group.
- All requests pass through `src/lib/api.ts` and same-origin gateway paths.
- Screens explicitly render loading, error, and empty states.
- This slice is read-only. It does not expose buttons the backend cannot yet complete end-to-end.

## Mobile boundary

No Dart source is added in this change. The repository has no `pubspec.yaml`, `main.dart`, or shared
API client. Harori's later writable feature paths are `appointment`, `medical_record`, and `lab`
after the shared Flutter shell is provided by its owner.

## Verification

- Run frontend type checking and linting.
- Run a production frontend build.
- Review the final diff against frontend structure, gateway-only access, and service ownership
  rules before opening the pull request.
