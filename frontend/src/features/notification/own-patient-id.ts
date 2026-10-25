import { isUuid } from "@/lib/validation";

/**
 * Reads the patientId claim already inside the signed-in PATIENT's own access token, purely so
 * the UI can skip asking them to paste their own id. This never establishes authority: the
 * backend independently enforces BR-N6 on every request regardless of what this returns.
 */
export function decodeOwnPatientId(token: string | null): string | null {
  if (!token) return null;
  const payload = token.split(".")[1];
  if (!payload) return null;
  try {
    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
    const claims: unknown = JSON.parse(atob(padded));
    const patientId = (claims as { patientId?: unknown })?.patientId;
    return typeof patientId === "string" && isUuid(patientId) ? patientId : null;
  } catch {
    return null;
  }
}
