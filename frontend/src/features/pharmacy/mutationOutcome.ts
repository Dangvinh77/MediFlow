import type { ApiRequestError } from "@/lib/api";

/** These statuses cannot establish whether a Pharmacy command committed. */
export function hasUncertainMutationStatus(error: ApiRequestError): boolean {
  return error.status < 400 || error.status === 408 || error.status >= 500;
}
