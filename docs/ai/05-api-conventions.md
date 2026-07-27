# 05 — API Conventions

## URL structure

- Prefix everything with `/api/v1/`. Plural, English, kebab-case resource names.
- `patient-service` → `/api/v1/patients`
- `organization-service` → `/api/v1/org/...` (`departments`, `staff`, `accounts`)
- `clinical-service` → **two prefixes**: `/api/v1/appointments` and `/api/v1/records`
  *(one bounded context, two resources — same shape as `pharmacy`)*
- `lab-service` → `/api/v1/lab`
- `pharmacy-service` → `/api/v1/pharmacy/...`
- `billing-service` → `/api/v1/billing/...`
- `notification-service` → `/api/v1/notifications`
- `report-service` → `/api/v1/reports`

Sub-resources nest: `/api/v1/records/{id}/diagnoses`, `/api/v1/pharmacy/prescriptions/{id}/dispense`.

## HTTP methods & status

| Action | Method | Success status |
|--------|--------|----------------|
| Read one | GET | 200 |
| Read list / page | GET | 200 |
| Create | POST | 201 (+`Location`) |
| Full/partial update | PUT | 200 |
| State transition | PUT `/{id}/status` etc. | 200 |
| Delete | DELETE | 204 |

## DTOs

- Requests: `CreateXxxRequest`, `UpdateXxxRequest` — Java `record`, validated with Bean Validation.
- Responses: `XxxDTO` — Java `record`. **Never return JPA entities.**
- Field names: **Vietnamese camelCase** matching the design docs (`hoTen`, `ngaySinh`, `maBenhNhan`).

Example (patient):
```java
public record CreatePatientRequest(
    @NotBlank @Size(max = 100) String hoTen,
    @NotNull @Past LocalDate ngaySinh,
    @NotNull GioiTinh gioiTinh,
    @NotBlank @Size(max = 20) String soCmnd,
    @Size(max = 255) String diaChi,
    @Pattern(regexp = "\\d{10,15}") String soDienThoai,
    @Email String email,
    @Pattern(regexp = "\\d{2}-\\d{8}-\\d") String bhytSo   // nullable
) {}
```

## Standard response envelope

All services return the same envelope (defined once in `common`):

```json
{
  "success": true,
  "data": { },
  "error": null,
  "timestamp": "2026-07-26T10:00:00Z",
  "correlationId": "..."
}
```

Error:
```json
{
  "success": false,
  "data": null,
  "error": { "code": "PATIENT_NOT_FOUND", "message": "...", "details": [ ] },
  "timestamp": "2026-07-26T10:00:00Z",
  "correlationId": "..."
}
```

- `error.code` is a stable machine-readable constant (UPPER_SNAKE). `message` is human-facing.
- Validation failures list per-field entries in `error.details`.

## Pagination

- List endpoints accept `page` (0-based), `size` (default 20, max 100), optional `keyword`, optional `sort`.
- Return Spring `Page` mapped into the envelope's `data` with `content`, `totalElements`, `totalPages`, `number`, `size`.

## Versioning & compatibility

- Breaking change → new version prefix (`/api/v2/...`), keep v1 until consumers migrate.
- Additive fields are non-breaking; never repurpose or remove a field within a version.

## OpenAPI

- Every service exposes springdoc UI at `/swagger-ui.html` and the spec at `/v3/api-docs`.
- Annotate controllers/DTOs enough that the generated spec matches the design docs.
