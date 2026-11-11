import { api } from "@/lib/api";
import type { StaffCapability, StaffCapabilityGrantDTO } from "./types";

export const capabilityApi = {
  list: (staffId: string) =>
    api.get<StaffCapabilityGrantDTO[]>(`/v2/org/staff/${encodeURIComponent(staffId)}/capability-grants`),
  grant: (staffId: string, capability: StaffCapability, reason: string) =>
    api.post<StaffCapabilityGrantDTO>(`/v2/org/staff/${encodeURIComponent(staffId)}/capability-grants`, { capability, reason }),
  revoke: (grantId: string, reason: string) =>
    api.put<StaffCapabilityGrantDTO>(`/v2/org/capability-grants/${encodeURIComponent(grantId)}/revocation`, { reason }),
};
