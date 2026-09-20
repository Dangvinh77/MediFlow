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
  location: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface StaffDTO {
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
