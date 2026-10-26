import { isUuid } from "@/lib/validation";

/** Selects the inbox URL only; the backend verifies the JWT and patient ownership. */
export function readNotificationPatientId(token: string | null): string | null {
  if (!token) return null;
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const payload = JSON.parse(atob(parts[1].replace(/-/g, "+").replace(/_/g, "/"))) as Record<string, unknown>;
    if (payload.role !== "PATIENT" || payload.type !== "access" || payload.staffId != null) return null;
    // sub is an accountId and must never be used as a fallback patientId.
    return typeof payload.patientId === "string" && isUuid(payload.patientId) ? payload.patientId : null;
  } catch {
    return null;
  }
}
