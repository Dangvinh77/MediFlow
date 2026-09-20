import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type { DepartmentDTO, StaffDTO } from "./types";

export const organizationApi = {
  departments: (activeOnly = true) =>
    api.get<DepartmentDTO[]>(`/v1/org/departments?activeOnly=${activeOnly}`),

  staff: (page = 0, size = 20) =>
    api.get<PageResult<StaffDTO>>(`/v1/org/staff?page=${page}&size=${size}`),
};
