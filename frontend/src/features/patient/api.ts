import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type { PatientDTO } from "./types";

export interface PatientSearchParams {
  keyword?: string;
  page?: number;
  size?: number;
}

export const patientApi = {
  search: (params: PatientSearchParams = {}) => {
    const query = new URLSearchParams({
      page: String(params.page ?? 0),
      size: String(params.size ?? 20),
    });
    if (params.keyword) query.set("keyword", params.keyword);
    return api.get<PageResult<PatientDTO>>(`/v1/patients?${query}`);
  },
};
