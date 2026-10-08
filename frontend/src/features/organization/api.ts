import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type {
  AccountDTO,
  CreateAccountRequest,
  CreateDepartmentRequest,
  CreateStaffRequest,
  DepartmentDTO,
  JobTitle,
  StaffDTO,
  UpdateDepartmentRequest,
  UpdateStaffRequest,
} from "./types";

export interface StaffSearchParams {
  departmentId?: string;
  jobTitle?: JobTitle;
  page?: number;
  size?: number;
}

export const organizationApi = {
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
    return api.get<PageResult<StaffDTO>>(`/v1/org/staff?${query}`);
  },
  staffMember: (staffId: string) =>
    api.get<StaffDTO>(`/v1/org/staff/${encodeURIComponent(staffId)}`),
  createStaff: (body: CreateStaffRequest) => api.post<StaffDTO>("/v1/org/staff", body),
  updateStaff: (staffId: string, body: UpdateStaffRequest) =>
    api.put<StaffDTO>(`/v1/org/staff/${encodeURIComponent(staffId)}`, body),
  changeStaffDepartment: (staffId: string, newDepartmentId: string) =>
    api.put<StaffDTO>(`/v1/org/staff/${encodeURIComponent(staffId)}/department`, { newDepartmentId }),
  createAccount: (body: CreateAccountRequest) => api.post<AccountDTO>("/v1/org/accounts", body),
  updateAccountStatus: (accountId: string, isActive: boolean) =>
    api.put<AccountDTO>(`/v1/org/accounts/${encodeURIComponent(accountId)}/status`, { isActive }),
};
