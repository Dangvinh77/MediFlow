import { ApiRequestError } from "@/lib/api";
import { getToken } from "@/lib/auth";

// The transport clears the rejected bearer before throwing a 401. Still handle
// that expiry, but never let an old request redirect a newer signed-in account.
export function isRequestSessionCurrent(token: string | null, cause?: unknown): boolean {
  const current = getToken();
  return current === token
    || (current === null && cause instanceof ApiRequestError && cause.status === 401);
}
