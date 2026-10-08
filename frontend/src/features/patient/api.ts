import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type { CreatePatientRequest, PatientDTO, UpdatePatientRequest } from "./types";

export interface PatientSearchParams {
  keyword?: string;
  page?: number;
  size?: number;
}

export const patientApi = {
  getById: (patientId: string) =>
    api.get<PatientDTO>(`/v1/patients/${encodeURIComponent(patientId)}`),
  search: (params: PatientSearchParams = {}) => {
    const query = new URLSearchParams({
      page: String(params.page ?? 0),
      size: String(params.size ?? 20),
    });
    if (params.keyword) query.set("keyword", params.keyword);
    return api.get<PageResult<PatientDTO>>(`/v1/patients?${query}`);
  },
  create: (body: CreatePatientRequest) => api.post<PatientDTO>("/v1/patients", body),
  update: (patientId: string, body: UpdatePatientRequest) =>
    api.put<PatientDTO>(`/v1/patients/${encodeURIComponent(patientId)}`, body),
  delete: (patientId: string) =>
    api.delEmpty(`/v1/patients/${encodeURIComponent(patientId)}`),
};
