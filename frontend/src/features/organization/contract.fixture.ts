import type {
  AccountDTO,
  ChangeStaffDepartmentRequest,
  CreateAccountRequest,
  CreateDepartmentRequest,
  CreateStaffRequest,
  DepartmentDTO,
  StaffDTO,
  UpdateDepartmentRequest,
  UpdateStaffRequest,
  UpdateAccountStatusRequest,
} from "./types";

/** Static TypeScript fixtures only; they do not replace a real-data Gateway smoke test. */
export const departmentContractFixture = {
  departmentId: "00000000-0000-4000-8000-000000000001",
  departmentName: "Clinical",
  abbreviation: "CLN",
  departmentType: "CLINICAL",
  departmentHeadId: null,
  location: null,
  active: true,
  createdAt: "2026-10-12T09:00:00Z",
  updatedAt: "2026-10-12T09:00:00Z",
} satisfies DepartmentDTO;

export const staffContractFixture = {
  identityNumber: "012345678901",
  staffId: "00000000-0000-4000-8000-000000000002",
  fullName: "Nguyen Van A",
  departmentId: departmentContractFixture.departmentId,
  jobTitle: "DOCTOR",
  specialization: null,
  licenseNumber: null,
  phoneNumber: null,
  email: null,
  active: true,
  createdAt: "2026-10-12T09:00:00Z",
  updatedAt: "2026-10-12T09:00:00Z",
} satisfies StaffDTO;

export const accountContractFixture = {
  accountId: "00000000-0000-4000-8000-000000000003",
  username: "patient.demo",
  staffId: null,
  patientId: "00000000-0000-4000-8000-000000000004",
  role: "PATIENT",
  isActive: false,
  lastLoginAt: null,
  createdAt: "2026-10-12T09:00:00Z",
  updatedAt: "2026-10-12T09:00:00Z",
} satisfies AccountDTO;

export const createDepartmentRequestFixture = {
  departmentName: "Clinical",
  abbreviation: "CLN",
  departmentType: "CLINICAL",
  location: null,
} satisfies CreateDepartmentRequest;

/** Explicit null clears the department head on PUT /departments/{id}. */
export const updateDepartmentRequestFixture = {
  departmentName: "Clinical",
  departmentType: "CLINICAL",
  location: null,
  departmentHeadId: null,
  active: null,
} satisfies UpdateDepartmentRequest;

export const createStaffRequestFixture = {
  identityNumber: "012345678901",
  fullName: "Nguyen Van A",
  departmentId: departmentContractFixture.departmentId,
  jobTitle: "DOCTOR",
  specialization: null,
  licenseNumber: null,
  phoneNumber: null,
  email: null,
} satisfies CreateStaffRequest;

export const updateStaffRequestFixture = {
  fullName: "Nguyen Van A",
  jobTitle: "DOCTOR",
  specialization: null,
  licenseNumber: null,
  phoneNumber: null,
  email: null,
} satisfies UpdateStaffRequest;

export const createAccountRequestFixture = {
  username: "patient.demo",
  password: "correct-horse-battery",
  staffId: null,
  patientId: accountContractFixture.patientId,
  role: "PATIENT",
} satisfies CreateAccountRequest;

export const changeStaffDepartmentRequestFixture = {
  newDepartmentId: "00000000-0000-4000-8000-000000000005",
} satisfies ChangeStaffDepartmentRequest;

export const lockAccountRequestFixture = { isActive: false } satisfies UpdateAccountStatusRequest;
