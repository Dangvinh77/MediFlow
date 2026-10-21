export type DepartmentType = "CLINICAL" | "PARACLINICAL" | "ADMINISTRATIVE";

export type JobTitle =
  | "DOCTOR"
  | "NURSE"
  | "TECHNICIAN"
  | "PHARMACIST"
  | "CASHIER"
  | "MANAGER"
  | "ADMINISTRATIVE";

export interface DepartmentDTO {
  departmentId: string;
  departmentName: string;
  abbreviation: string;
  departmentType: DepartmentType;
  departmentHeadId: string | null;
  location: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateDepartmentRequest {
  departmentName: string;
  abbreviation: string;
  departmentType: DepartmentType;
  location: string | null;
}

export interface UpdateDepartmentRequest {
  departmentName: string;
  departmentType: DepartmentType;
  location: string | null;
  departmentHeadId: string | null;
  active: boolean | null;
}

export interface ChangeStaffDepartmentRequest {
  newDepartmentId: string;
}

export interface UpdateAccountStatusRequest {
  isActive: boolean;
}

export interface CreateStaffRequest {
  identityNumber: string;
  fullName: string;
  departmentId: string;
  jobTitle: JobTitle;
  specialization: string | null;
  licenseNumber: string | null;
  phoneNumber: string | null;
  email: string | null;
}

export type UpdateStaffRequest = Omit<CreateStaffRequest, "departmentId" | "identityNumber">;

export type AccountRole =
  | "ADMIN" | "DOCTOR" | "NURSE" | "PHARMACIST" | "CASHIER"
  | "LAB_TECH" | "MANAGER" | "PATIENT" | "SYSTEM";

export interface AccountDTO {
  accountId: string;
  username: string;
  staffId: string | null;
  patientId: string | null;
  role: AccountRole;
  isActive: boolean;
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAccountRequest {
  username: string;
  password: string;
  staffId: string | null;
  patientId: string | null;
  role: AccountRole;
}

export interface StaffDTO {
  identityNumber: string | null;
  staffId: string;
  fullName: string;
  departmentId: string;
  jobTitle: JobTitle;
  specialization: string | null;
  licenseNumber: string | null;
  phoneNumber: string | null;
  email: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}
