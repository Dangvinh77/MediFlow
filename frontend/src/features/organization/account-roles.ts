import type { AccountRole, JobTitle } from "./types";

export const accountRoleLabels: Record<AccountRole, string> = {
  ADMIN: "Quản trị viên", DOCTOR: "Bác sĩ", NURSE: "Điều dưỡng",
  PHARMACIST: "Dược sĩ", CASHIER: "Thu ngân", LAB_TECH: "Kỹ thuật viên xét nghiệm",
  MANAGER: "Quản lý", PATIENT: "Bệnh nhân", SYSTEM: "Hệ thống",
};

export const roleForJobTitle: Record<JobTitle, AccountRole> = {
  DOCTOR: "DOCTOR", NURSE: "NURSE", TECHNICIAN: "LAB_TECH",
  PHARMACIST: "PHARMACIST", CASHIER: "CASHIER", MANAGER: "MANAGER",
  ADMINISTRATIVE: "ADMIN",
};
