export interface DepartmentProfile {
  description: string | null;
  phoneNumber: string | null;
  email: string | null;
}
export interface StaffProfile {
  dateOfBirth: string | null;
  gender: "MALE" | "FEMALE" | "OTHER" | null;
  address: string | null;
  hireDate: string | null;
  qualification: string | null;
}

export interface StaffDepartmentDTO {
  departmentId: string;
  departmentName: string;
}

export type DepartmentType = "CLINICAL" | "PARACLINICAL" | "ADMINISTRATIVE";

export type JobTitle =
  | "DOCTOR"
  | "NURSE"
  | "TECHNICIAN"
  | "PHARMACIST"
  | "CASHIER"
  | "MANAGER"
  | "ADMINISTRATIVE";

export interface DepartmentDTO extends DepartmentProfile {
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
  profile?: DepartmentProfile;
  departmentName: string;
  abbreviation: string;
  departmentType: DepartmentType;
  location: string | null;
}

export interface UpdateDepartmentRequest {
  profile?: DepartmentProfile;
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
  replaceActiveAccount?: boolean;
}

export interface CreateStaffRequest {
  profile?: StaffProfile;
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
  replaceActiveAccount?: boolean;
  username: string;
  password: string;
  staffId: string | null;
  patientId: string | null;
  role: AccountRole;
}

export interface StaffDTO extends StaffProfile {
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
  createdAt: string | null;
  updatedAt: string | null;
}
export interface IssuedPatientAccount {
  accountId: string;
  patientId: string;
  username: string;
  mustChangePassword: boolean;
}
