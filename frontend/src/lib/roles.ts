export const ROLES = {
  ADMIN: "ADMIN",
  DOCTOR: "DOCTOR",
  NURSE: "NURSE",
  PHARMACIST: "PHARMACIST",
  CASHIER: "CASHIER",
  LAB_TECH: "LAB_TECH",
  MANAGER: "MANAGER",
  PATIENT: "PATIENT",
  SYSTEM: "SYSTEM",
} as const;

export type Role = (typeof ROLES)[keyof typeof ROLES];

export function isRole(value: string): value is Role {
  return Object.values(ROLES).some((role) => role === value);
}
