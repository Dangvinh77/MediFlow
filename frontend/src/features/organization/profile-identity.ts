import type { Role } from "@/lib/roles";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Selects the existing detail URL for presentation. The backend verifies ownership. */
export function readProfileIdentity(token: string | null, role: Role | null) {
  if (!token || !role || role === "SYSTEM") return null;
  try {
    const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/"))) as Record<string, unknown>;
    if (payload.role !== role || payload.type !== "access" || typeof payload.sub !== "string" || !uuid.test(payload.sub)) return null;
    const claim = role === "PATIENT" ? payload.patientId : payload.staffId;
    const conflicting = role === "PATIENT" ? payload.staffId : payload.patientId;
    if (conflicting != null || (claim != null && (typeof claim !== "string" || !uuid.test(claim)))) return null;
    return { accountId: payload.sub, profileId: typeof claim === "string" ? claim : null };
  } catch {
    return null;
  }
}
