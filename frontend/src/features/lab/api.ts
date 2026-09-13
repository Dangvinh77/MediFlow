import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type { LabTestDTO, LabTestStatus } from "./types";

export interface LabSearchParams {
  departmentId?: string;
  status?: LabTestStatus;
  page?: number;
  size?: number;
}

export const labApi = {
  search: (params: LabSearchParams = {}) => {
    const query = new URLSearchParams({
      page: String(params.page ?? 0),
      size: String(params.size ?? 20),
    });
    if (params.departmentId) query.set("departmentId", params.departmentId);
    if (params.status) query.set("status", params.status);
    return api.get<PageResult<LabTestDTO>>(`/v1/lab?${query}`);
  },
};
