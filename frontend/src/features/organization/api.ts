import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type {
  AccountDTO,
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
  resetPassword: (accountId: string, newPassword: string) =>
    api.put<boolean>(`/v1/org/accounts/${encodeURIComponent(accountId)}/password`, { newPassword }),
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
