import { api, type MutationOptions } from "@/lib/api";
import type { HeadAppointmentCommand, HeadAppointmentSnapshot } from "./types";
import type { PageResult } from "@/lib/types";
import type {
  AccountDTO,
  ProfileAccountDTO,
  ChangeStaffDepartmentRequest,
  CreateAccountRequest,
  CreateDepartmentRequest,
  CreateStaffRequest,
  DepartmentDTO,
  JobTitle,
  StaffDTO,
  StaffDepartmentDTO,
  UpdateAccountStatusRequest,
  UpdateDepartmentRequest,
  UpdateStaffRequest,
} from "./types";

export interface StaffSearchParams {
  keyword?: string;
  activeOnly?: boolean;
  departmentId?: string;
  jobTitle?: JobTitle;
  page?: number;
  size?: number;
}

export const organizationApi = {
  headAppointments: (departmentId: string) => api.get<HeadAppointmentSnapshot>(`/v2/org/departments/${encodeURIComponent(departmentId)}/head-appointments`),
  replaceHeadAppointment: (departmentId: string, body: HeadAppointmentCommand, options: Required<MutationOptions>) => api.put<HeadAppointmentSnapshot>(`/v2/org/departments/${encodeURIComponent(departmentId)}/head-appointments`, body, options),
  provisionPatientAccount: (patientId: string, idempotencyKey: string) =>
    api.post<import("./types").IssuedPatientAccount>("/v1/org/patient-accounts", { patientId }, { idempotencyKey }),
  profileAccount: (ownerType: "staff" | "patient", ownerId: string) =>
    api.get<ProfileAccountDTO>(`/v1/org/profile-accounts/${ownerType}/${encodeURIComponent(ownerId)}`),
  resetPatientPassword: (patientId: string, idempotencyKey: string) =>
    api.put<import("./types").MailDelivery>(`/v1/org/patient-accounts/${encodeURIComponent(patientId)}/password`, {}, { idempotencyKey }),
  patientMailDelivery: (patientId: string) =>
    api.get<import("./types").MailDelivery>(`/v1/org/patient-accounts/${encodeURIComponent(patientId)}/mail-delivery`),
  retryPatientMail: (patientId: string, messageId: string) =>
    api.post<import("./types").MailDelivery>(`/v1/org/patient-accounts/${encodeURIComponent(patientId)}/mail-delivery/${encodeURIComponent(messageId)}/retry`, {}),
  updatePatientEmail: (patientId: string, email: string, reason: string) =>
    api.put<AccountDTO>(`/v1/org/patient-accounts/${encodeURIComponent(patientId)}/email`, { email, reason }),
  updateOwnContact: (staffId: string, body: { phoneNumber: string | null; email?: string | null; address: string | null }) =>
    api.put<StaffDTO>(`/v1/org/staff/${encodeURIComponent(staffId)}/contact`, body),
  careLabel: (kind: "doctor" | "department" | "room", id: string) =>
    api.get<{ id: string; label: string }>(`/v1/org/care-labels/${kind}/${encodeURIComponent(id)}`),
  account: (accountId: string) =>
    api.get<AccountDTO>(`/v1/org/accounts/${encodeURIComponent(accountId)}`),
  accounts: (params: { keyword?: string; role?: string; active?: boolean; page?: number } = {}) => {
    const query = new URLSearchParams({ page: String(params.page ?? 0), size: "20" });
    if (params.keyword?.trim()) query.set("keyword", params.keyword.trim());
    if (params.role) query.set("role", params.role);
    if (params.active !== undefined) query.set("active", String(params.active));
    return api.get<PageResult<AccountDTO>>(`/v1/org/accounts?${query}`);
  },
  changePassword: (currentPassword: string, newPassword: string) =>
    api.put<boolean>("/v1/org/accounts/me/password", { currentPassword, newPassword }),
  resetPassword: (accountId: string, idempotencyKey: string) =>
    api.put<import("./types").MailDelivery>(`/v1/org/accounts/${encodeURIComponent(accountId)}/password`, {}, { idempotencyKey }),
  mailDelivery: (accountId: string) => api.get<import("./types").MailDelivery>(`/v1/org/accounts/${encodeURIComponent(accountId)}/mail-delivery`),
  retryMail: (accountId: string, messageId: string) => api.post<import("./types").MailDelivery>(`/v1/org/accounts/${encodeURIComponent(accountId)}/mail-delivery/${encodeURIComponent(messageId)}/retry`, {}),
  updateAccountEmail: (accountId: string, email: string, reason: string) => api.put<AccountDTO>(`/v1/org/accounts/${encodeURIComponent(accountId)}/email`, { email, reason }),
  departments: (activeOnly = true) =>
    api.get<DepartmentDTO[]>(`/v1/org/departments?activeOnly=${activeOnly}`),
  department: (departmentId: string) =>
    api.get<DepartmentDTO>(`/v1/org/departments/${encodeURIComponent(departmentId)}`),
  createDepartment: (body: CreateDepartmentRequest) =>
    api.post<DepartmentDTO>("/v1/org/departments", body),
  updateDepartment: (departmentId: string, body: UpdateDepartmentRequest) =>
    api.put<DepartmentDTO>(`/v1/org/departments/${encodeURIComponent(departmentId)}`, body),
  staff: (params: StaffSearchParams = {}) => {
    const query = new URLSearchParams({ page: String(params.page ?? 0), size: String(params.size ?? 20) });
    if (params.departmentId) query.set("departmentId", params.departmentId);
    if (params.jobTitle) query.set("jobTitle", params.jobTitle);
    if (params.keyword?.trim()) query.set("keyword", params.keyword.trim());
    if (params.activeOnly !== undefined) query.set("activeOnly", String(params.activeOnly));
    return api.get<PageResult<StaffDTO>>(`/v1/org/staff?${query}`);
  },
  staffMember: (staffId: string) =>
    api.get<StaffDTO>(`/v1/org/staff/${encodeURIComponent(staffId)}`),
  ownDepartment: (staffId: string) =>
    api.get<StaffDepartmentDTO>(`/v1/org/staff/${encodeURIComponent(staffId)}/department`),
  createStaff: (body: CreateStaffRequest, idempotencyKey: string) =>
    api.post<StaffDTO>("/v1/org/staff", body, { idempotencyKey }),
  updateStaff: (staffId: string, body: UpdateStaffRequest) =>
    api.put<StaffDTO>(`/v1/org/staff/${encodeURIComponent(staffId)}`, body),
  changeStaffDepartment: (staffId: string, newDepartmentId: string) =>
    api.put<StaffDTO>(
      `/v1/org/staff/${encodeURIComponent(staffId)}/department`,
      { newDepartmentId } satisfies ChangeStaffDepartmentRequest,
    ),
  createAccount: (body: CreateAccountRequest, idempotencyKey: string) =>
    api.post<AccountDTO>("/v1/org/accounts", body, { idempotencyKey }),
  updateAccountStatus: (accountId: string, isActive: boolean, replaceActiveAccount = false) =>
    api.put<AccountDTO>(
      `/v1/org/accounts/${encodeURIComponent(accountId)}/status`,
      { isActive, ...(replaceActiveAccount ? { replaceActiveAccount: true } : {}) } satisfies UpdateAccountStatusRequest,
    ),
};
